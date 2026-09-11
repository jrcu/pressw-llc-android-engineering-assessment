This is PantryPal's backend: a [Next.js](https://nextjs.org) app exposing a cooking-assistant chat API, built with the [Vercel AI SDK](https://ai-sdk.dev/docs).

## Setup

1. **Prerequisites:** Node.js 20+ and pnpm.
2. **Install dependencies:**
   ```bash
   pnpm install
   ```
3. **Configure environment variables:** copy `.env.example` to `.env.local` and fill in your own keys.
   ```bash
   cp .env.example .env.local
   ```
   - `ANTHROPIC_API_KEY` — required. Get one at [console.anthropic.com](https://console.anthropic.com).
   - `TAVILY_API_KEY` — required, for the web-search tool. Get one at [tavily.com](https://tavily.com).
   - `ANTHROPIC_MODEL` — optional, defaults to `claude-haiku-4-5`.
4. **Run the dev server** — either with Docker Compose (no local Node/pnpm install needed beyond step 1's prerequisites check):
   ```bash
   docker compose up
   ```
   or directly with pnpm:
   ```bash
   pnpm dev
   ```
   Either way, the chat API is available at `http://localhost:3000/api/chatbot`. The Docker setup bind-mounts the source, so edits on the host reload the same as running `pnpm dev` locally — `node_modules` and `.next` stay inside the container so they don't fight with a host install.

## Example requests

The endpoint is `POST /api/chatbot`. The response streams back as plain text (`text/plain`), so `curl -N` (disable buffering) shows it arriving in real time. See the doc comment at the top of `app/api/chatbot/route.ts` for the full request/response contract.

**Basic question:**

```bash
curl -N -X POST http://localhost:3000/api/chatbot \
  -H "Content-Type: application/json" \
  -d '{"message": "How do I know when chicken breast is done cooking?"}'
```

**With equipment context** (so recipe suggestions don't assume gear the user doesn't have):

```bash
curl -N -X POST http://localhost:3000/api/chatbot \
  -H "Content-Type: application/json" \
  -d '{
    "message": "I want to make a souffle for dessert tonight",
    "equipment": ["one saucepan", "microwave", "a hot plate"]
  }'
```

**With conversation history** (multi-turn — nothing is persisted server-side, so the client resends prior turns each time):

```bash
curl -N -X POST http://localhost:3000/api/chatbot \
  -H "Content-Type: application/json" \
  -d '{
    "message": "What about something spicier?",
    "history": [
      {"role": "user", "content": "Suggest a quick dinner"},
      {"role": "assistant", "content": "How about a stir-fry with whatever vegetables you have on hand?"}
    ]
  }'
```

A request missing `message` (or with an empty string) returns `400` with a JSON error body instead of a stream. If the model decides it needs current information (e.g. "is X in season right now?"), you'll see an interim `🔍 Searching the web...` line stream in before the answer, since it calls a web-search tool. Every response ends with a fixed allergen disclaimer, appended in code rather than left to the model.

## Learn More

To learn more about Next.js, take a look at the following resources:

- [Next.js Documentation](https://nextjs.org/docs) - learn about Next.js features and API.
- [Learn Next.js](https://nextjs.org/learn) - an interactive Next.js tutorial.

You can check out [the Next.js GitHub repository](https://github.com/vercel/next.js) - your feedback and contributions are welcome!

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) for more details.
