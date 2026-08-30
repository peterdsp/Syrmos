package com.syrmos.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class AriadneChatService(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun chat(messages: List<AriadneChatMessage>): String? {
        return try {
            val payload = AriadneChatRequest(
                messages = messages.map {
                    AriadneChatMessagePayload(role = it.role, text = it.text)
                }
            )
            val response = httpClient.post(CHAT_URL) {
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
