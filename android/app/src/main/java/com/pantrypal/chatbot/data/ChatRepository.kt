package com.pantrypal.chatbot.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject

/** Thrown when the backend rejects the request (e.g. HTTP 400) or is unreachable. */
class ChatApiException(message: String) : IOException(message)

/**
 * Talks to PantryPal's `/api/chatbot` endpoint (see backend/app/api/chatbot/route.ts).
 * The response body is streamed plain text, so [sendMessage] exposes it as a
 * [Flow] of text chunks that the caller appends to a message as they arrive.
 */
class ChatRepository(
    // 10.0.2.2 is the Android emulator's alias for the host machine's
    // localhost, where `docker compose up` / `pnpm dev` serves the backend.
    private val baseUrl: String = "http://10.0.2.2:3000",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    fun sendMessage(message: String, history: List<ChatMessage>): Flow<String> = flow {
        val requestJson = JSONObject().apply {
            put("message", message)
            if (history.isNotEmpty()) {
                put(
                    "history",
                    JSONArray().apply {
                        history.forEach { turn ->
                            put(
                                JSONObject().apply {
                                    put("role", if (turn.role == Role.USER) "user" else "assistant")
                                    put("content", turn.content)
                                }
                            )
                        }
                    },
                )
            }
        }

        val request = Request.Builder()
            .url("$baseUrl/api/chatbot")
            .post(requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val call = client.newCall(request)
        currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) call.cancel()
        }
        try {
            call.execute().use { response ->
                val source = response.body?.source()
                    ?: throw ChatApiException("Empty response from server.")

                if (!response.isSuccessful) {
                    val errorText = source.readUtf8()
                    val message = runCatching { JSONObject(errorText).getString("error") }
                        .getOrDefault("Request failed (HTTP ${response.code}).")
                    throw ChatApiException(message)
                }

                val buffer = Buffer()
                while (!source.exhausted()) {
                    val read = source.read(buffer, CHUNK_SIZE)
                    if (read == -1L) break
                    emit(buffer.readUtf8())
                }
            }
        } catch (e: IOException) {
            if (e is ChatApiException) throw e
            throw ChatApiException("Couldn't reach PantryPal. Check that the backend is running.")
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        const val CHUNK_SIZE = 8192L
    }
}
