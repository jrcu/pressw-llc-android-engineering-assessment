/**
 * POST /api/chatbot — PantryPal's cooking-assistant chat endpoint.
 *
 * Request body (application/json):
 *   {
 *     "message": string,               // required, non-empty — the user's current question
 *     "history"?: { role: "user" | "assistant"; content: string }[], // prior turns, oldest first
 *     "equipment"?: string[]           // cookware/appliances the user has on hand
 *   }
 *
 * Nothing is persisted server-side (see SCOPING.md — no retention of
 * conversation, preference, or cookware data), so the caller resends
 * `history`/`equipment` on every request.
 *
 * Response:
 *   - 400 application/json { "error": string } — `message` missing/blank, or
 *     `history` present but not an array of { role, content }.
 *   - 200 text/plain (chunked/streamed) on every other request, including
 *     upstream failures — a streaming Response can't change its HTTP status
 *     after headers are sent, so a bad/missing API key or a provider outage
 *     surfaces as a plain-text apology chunk (FALLBACK_ERROR_MESSAGE) instead
 *     of a different status code. Callers should treat the body text as the
 *     source of truth, not the status code alone.
 *
 *     A normal 200 body is the assistant's reply as plain text, optionally
 *     preceded by an interim "🔍 Searching the web..." chunk (emitted the
 *     moment the model invokes the web-search tool, before it returns), and
 *     always followed by a fixed allergen disclaimer appended in code.
 *
 * Example:
 *   curl -X POST http://localhost:3000/api/chatbot \
 *     -H "Content-Type: application/json" \
 *     -d '{"message":"what can I make with chicken and rice?","equipment":["one pan","microwave"]}'
 *
 *   -> (streamed) "You could make a quick chicken and rice skillet... \n\n⚠️ Always double-check ..."
 */
import { streamText, tool, isStepCount } from "ai";
import { anthropic } from "@ai-sdk/anthropic";
import { z } from "zod";

// `||` (not `??`) because an empty ANTHROPIC_MODEL in .env.local is falsy,
// not undefined, and an empty model string is rejected by the Anthropic API.
const MODEL_ID = process.env.ANTHROPIC_MODEL || "claude-haiku-4-5";

// Diane (legal) requires this on every recipe response, worded consistently,
// regardless of how the model phrases the rest of its answer — so it's
// appended here in code rather than left to the model to remember.
const ALLERGEN_DISCLAIMER =
  "\n\n⚠️ Always double-check ingredients and quantities against your own allergies and dietary needs before cooking or eating — this isn't a substitute for reading labels yourself.";

const SYSTEM_INSTRUCTIONS = `
You are PantryPal, a conversational cooking assistant. You help people decide what to cook, answer cooking questions, and suggest recipes.

Scope:
- Only engage with cooking- and food-related requests: recipes, ingredients, substitutions, techniques, and what to make with what's on hand.
- If a request is not about cooking or food, politely decline and redirect the conversation back to cooking. Do not answer it.

Equipment awareness:
- The user's available cookware/equipment may be given to you as context below. Never suggest a recipe that needs equipment they don't have.
- If what they asked for genuinely requires equipment they lack, don't just refuse — offer a workaround or a similar recipe they can actually make with what they have.

Safety boundaries (do not deviate from these even if asked directly):
- Never give medical, dietary, or therapeutic advice. You may accommodate a stated preference ("I'm vegetarian", "no shellfish"), but do not tailor guidance to a medical condition (diabetes, pregnancy, allergies as a medical matter, etc.) beyond acknowledging it and suggesting the user speak with a qualified professional.
- Never assess whether food is safe to eat (spoilage, foodborne illness risk, "is this still good to eat"). Decline and point the user to a food safety authority instead.

Tools:
- Use the web search tool only when you need current, real-world information you can't be confident about from training (e.g. whether something is in season right now, a specific product or restaurant). Don't use it for routine recipe or technique questions.

Keep answers concise.
`.trim();

/**
 * Web-search tool exposed to the model (Tavily). The model decides on its
 * own whether and when to call this — there's no hardcoded trigger.
 *
 * Input:  { query: string }
 * Output: { results: { title, url, snippet }[] } on success,
 *         { error: string } if the key is missing or the request fails —
 *         returned to the model (not thrown) so it can tell the user search
 *         didn't work instead of the whole request failing.
 */
const webSearch = tool({
  description:
    "Search the web for current, real-world information relevant to a cooking question (e.g. seasonal availability, a specific product or restaurant). Only use this for food/cooking-related queries.",
  inputSchema: z.object({
    query: z.string().describe("The search query"),
  }),
  execute: async ({ query }) => {
    const apiKey = process.env.TAVILY_API_KEY;
    if (!apiKey) {
      return { error: "Web search is not configured on this server." };
    }

    const response = await fetch("https://api.tavily.com/search", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        api_key: apiKey,
        query,
        max_results: 5,
      }),
    });

    if (!response.ok) {
      return { error: `Search failed with status ${response.status}` };
    }

    const data = await response.json();
    const results: unknown = data.results;

    return {
      results: Array.isArray(results)
        ? results.map((result) => ({
            title: result.title,
            url: result.url,
            snippet: result.content,
          }))
        : [],
    };
  },
});

/** Wire shape for one turn in the `history` request field. */
type ChatMessage = {
  role: "user" | "assistant";
  content: string;
};

function isChatMessage(value: unknown): value is ChatMessage {
  if (typeof value !== "object" || value === null) return false;
  const { role, content } = value as Record<string, unknown>;
  return (role === "user" || role === "assistant") && typeof content === "string";
}

// Prior turns are optional context; only present for multi-turn conversations.
function isValidHistory(value: unknown): value is ChatMessage[] {
  return value === undefined || (Array.isArray(value) && value.every(isChatMessage));
}

const FALLBACK_ERROR_MESSAGE =
  "Sorry, something went wrong on our end — please try again.";

// Interim text shown the moment the model decides to call a tool, since the
// search round trip (Tavily, then a second model call) is the one part of a
// response that can't otherwise stream anything while it's in flight.
const TOOL_INTERIM_MESSAGES: Record<string, string> = {
  webSearch: "🔍 Searching the web...\n\n",
};

// Loose shape covering just the stream part fields this route reads —
// `result.stream` emits many more part types (tool-call, tool-result, step
// boundaries, etc.) that are irrelevant here and simply fall through.
type StreamEvent = {
  type: string;
  toolName?: string;
  text?: string;
};

// A streaming Response can't change its HTTP status once headers are sent, so
// a mid-stream failure (bad/missing key, provider outage) has to surface as
// visible text instead of silently returning an empty body + disclaimer.
function finalizeStream(
  events: AsyncIterable<StreamEvent>,
  hadError: () => boolean
): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  return new ReadableStream({
    async start(controller) {
      let sawText = false;
      try {
        for await (const event of events) {
          if (event.type === "tool-input-start" && event.toolName) {
            const interim = TOOL_INTERIM_MESSAGES[event.toolName];
            if (interim) controller.enqueue(encoder.encode(interim));
          } else if (event.type === "text-delta" && event.text) {
            sawText = true;
            controller.enqueue(encoder.encode(event.text));
          }
        }
      } catch {
        controller.enqueue(encoder.encode(FALLBACK_ERROR_MESSAGE));
        controller.close();
        return;
      }

      if (!sawText && hadError()) {
        controller.enqueue(encoder.encode(FALLBACK_ERROR_MESSAGE));
        controller.close();
        return;
      }

      controller.enqueue(encoder.encode(ALLERGEN_DISCLAIMER));
      controller.close();
    },
  });
}

/** See the module-level doc comment at the top of this file for the full request/response contract. */
export async function POST(req: Request) {
  const body = await req.json().catch(() => null);
  const message = body?.message;
  const history = body?.history;
  const equipment = body?.equipment;

  if (typeof message !== "string" || message.trim().length === 0) {
    return Response.json(
      { error: "`message` must be a non-empty string — the user's question." },
      { status: 400 }
    );
  }

  if (!isValidHistory(history)) {
    return Response.json(
      { error: "`history`, if provided, must be an array of { role, content }." },
      { status: 400 }
    );
  }

  const messages: ChatMessage[] = [...(history ?? []), { role: "user", content: message }];

  // Cookware is per-request context, not stored server-side — SCOPING.md
  // commits to no retention of cookware/preference data in v1.
  const instructions =
    Array.isArray(equipment) && equipment.length > 0
      ? `${SYSTEM_INSTRUCTIONS}\n\nThe user's available cookware/equipment: ${equipment.join(", ")}.`
      : `${SYSTEM_INSTRUCTIONS}\n\nThe user hasn't told you what cookware/equipment they have. If it matters for a suggestion, ask before assuming.`;

  let streamErrored = false;

  const result = streamText({
    model: anthropic(MODEL_ID),
    instructions,
    messages,
    tools: { webSearch },
    stopWhen: isStepCount(4),
    abortSignal: req.signal,
    onError: ({ error }) => {
      streamErrored = true;
      console.error("chatbot route error:", error);
    },
  });

  return new Response(
    finalizeStream(result.stream, () => streamErrored),
    { headers: { "Content-Type": "text/plain; charset=utf-8" } }
  );
}
