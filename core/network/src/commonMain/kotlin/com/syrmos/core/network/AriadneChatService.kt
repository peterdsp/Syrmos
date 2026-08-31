package com.syrmos.core.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class AriadneChatService(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // Circuit breaker. After a cloud turn fails to produce a usable reply, whether
    // by a network error / timeout or by an offline-provider response, skip the
    // cloud for a short cooldown so the next questions during the same outage
    // answer instantly from the local grounded engine instead of each waiting out
    // the ~33 s timeout. The first question after the cooldown retries the cloud
    // with its full budget, so a viable-but-slow network is never downgraded. This
    // is preferred over a /healthz preflight because /healthz cannot see that the
    // server's LLM providers are down (its real failure mode) and would add a
    // round trip to every question without preventing that stall.
    private val breaker = CloudCircuitBreaker(cooldown = COOLDOWN)

    suspend fun chat(messages: List<AriadneChatMessage>): String? {
        // Breaker open from a recent failure: go straight to the local engine.
        if (breaker.isOpen()) return null
        return try {
            val payload = AriadneChatRequest(
                messages = messages.map {
                    AriadneChatMessagePayload(role = it.role, text = it.text)
                }
            )
            val response = httpClient.post(CHAT_URL) {
                // A SHORT connect timeout drops an unreachable Pi (or an offline
                // device) to the local engine in a few seconds instead of blocking
                // on the shared client's 15 s connect. The request timeout stays
                // above the server's nginx 30 s read cap (the backend tries up to
                // three LLM providers in sequence and emits nothing until one
                // answers), so a valid-but-slow reply is received, not cut.
                timeout {
                    connectTimeoutMillis = 3_500
                    requestTimeoutMillis = 33_000
                    socketTimeoutMillis = 33_000
                }
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(AriadneChatRequest.serializer(), payload))
            }
            val body = response.bodyAsText()
            val reply = json.decodeFromString<AriadneChatResponse>(body).cloudReplyOrNull()
            if (reply == null) breaker.recordFailure() // ok=false or provider=offline
            else breaker.recordSuccess()               // a real reply clears the breaker
            reply
        } catch (_: Exception) {
            breaker.recordFailure() // network error / timeout: cloud unreachable now
            null
        }
    }

    private companion object {
        private const val CHAT_URL = "https://api-syrmos.peterdsp.dev/api/ariadne/chat"
        private val COOLDOWN = 30.seconds
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
