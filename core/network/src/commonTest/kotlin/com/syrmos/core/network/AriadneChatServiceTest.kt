package com.syrmos.core.network

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The cloud-vs-local decision for Ariadne. The server returns ok=true with
 * provider="offline" and a canned "I can't reach my brain" message whenever its
 * own cloud LLMs are down; that must fall through to the local grounded engine,
 * never be shown as if it were a real answer.
 */
class AriadneChatServiceTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(body: String) = json.decodeFromString<AriadneChatResponse>(body)

    @Test
    fun offlineProviderReplyIsTreatedAsNoCloudAnswer() {
        // The exact shape the live Pi returns when its cloud providers are down.
        val body = """{"reply":"I can't reach my brain right now.","ok":true,"provider":"offline"}"""
        assertNull(
            decode(body).cloudReplyOrNull(),
            "provider=offline must fall through to the local engine, not shadow it",
        )
    }

    @Test
    fun realCloudReplyIsSurfaced() {
        val body = """{"reply":"Line A4 runs Piraeus to Kiato.","ok":true,"provider":"gemini"}"""
        assertEquals("Line A4 runs Piraeus to Kiato.", decode(body).cloudReplyOrNull())
    }

    @Test
    fun notOkIsNull() {
        val body = """{"reply":"boom","ok":false,"provider":"gemini"}"""
        assertNull(decode(body).cloudReplyOrNull())
    }

    @Test
    fun missingProviderStillSurfacesAnOkReply() {
        // Older server builds omit provider entirely; an ok reply must still show.
        val body = """{"reply":"hello","ok":true}"""
        assertEquals("hello", decode(body).cloudReplyOrNull())
    }
}
