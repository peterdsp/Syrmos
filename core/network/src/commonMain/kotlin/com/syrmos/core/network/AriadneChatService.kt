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
            val result = json.decodeFromString<AriadneChatResponse>(body)
            if (result.ok) result.reply else null
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
private data class AriadneChatResponse(
    val reply: String = "",
    val ok: Boolean = false,
)
