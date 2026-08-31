package com.syrmos.core.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class AriadneChatService(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /// Fast endpoint-reachability probe. A short GET /healthz confirms the API is
    /// actually answering, not merely that a route exists or a TCP connect to a
    /// captive portal succeeds. When it fails, the caller uses the local engine
    /// instead of committing to the long (silent, multi-LLM) chat request.
    private suspend fun isEndpointReachable(): Boolean = try {
        val resp = httpClient.get(HEALTH_URL) {
            timeout {
                connectTimeoutMillis = 2_000
                requestTimeoutMillis = 2_500
                socketTimeoutMillis = 2_500
            }
        }
        resp.status.isSuccess()
    } catch (_: Exception) {
        false
    }

    suspend fun chat(messages: List<AriadneChatMessage>): String? {
        // Probe the endpoint first so an unreachable/blackholed server drops to the
        // local engine in ~2 s instead of stalling on the long chat timeout.
        if (!isEndpointReachable()) return null
        return try {
            val payload = AriadneChatRequest(
                messages = messages.map {
                    AriadneChatMessagePayload(role = it.role, text = it.text)
                }
            )
            val response = httpClient.post(CHAT_URL) {
                // Reachability guard without a platform network API: a SHORT connect
                // timeout means an unreachable Pi (or an offline device) drops to the
                // local grounded engine in a few seconds instead of blocking on the
                // shared client's 15 s connect. The request timeout stays long so the
                // server's multi-LLM chain still has time to answer when reachable.
                timeout {
                    connectTimeoutMillis = 3_500
                    // Above the server's nginx 30 s read cap (the backend tries up
                    // to three LLM providers in sequence and emits nothing until
                    // one answers), so a valid-but-slow reply is received, not cut.
                    requestTimeoutMillis = 33_000
                    socketTimeoutMillis = 33_000
                }
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(AriadneChatRequest.serializer(), payload))
            }
            val body = response.bodyAsText()
            json.decodeFromString<AriadneChatResponse>(body).cloudReplyOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        private const val CHAT_URL = "https://api-syrmos.peterdsp.dev/api/ariadne/chat"
        private const val HEALTH_URL = "https://api-syrmos.peterdsp.dev/healthz"
    }
}

data class AriadneChatMessage(
    val role: String,
    val text: String,
)

@Serializable
private data class AriadneChatMessagePayload(
    val role: String,
    val text: String,
)

@Serializable
private data class AriadneChatRequest(
    val messages: List<AriadneChatMessagePayload>,
)

@Serializable
internal data class AriadneChatResponse(
    val reply: String = "",
    val ok: Boolean = false,
    /** "offline" when the server's own cloud LLMs are down: not a real answer. */
    val provider: String = "",
)

/**
 * The cloud reply to surface, or null to fall through to the local engine.
 *
 * The server answers `ok=true` with `provider="offline"` and a canned
 * "I can't reach my brain right now" message whenever its OWN cloud LLM providers
 * are unreachable (which, on the current Pi, is every call). Returning that would
 * shadow the fully-capable local grounded engine, so an offline-provider reply is
 * treated as no cloud answer.
 */
internal fun AriadneChatResponse.cloudReplyOrNull(): String? =
    if (ok && provider != "offline") reply else null
