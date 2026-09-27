package com.syrmos.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A failed announcements fetch must be distinguishable from a quiet day: the
 * repository stamps the home "Live" pill on any feed it receives, and a
 * failure that surfaced as an empty feed kept the pill lit offline for as
 * long as refreshes kept failing.
 */
class STASYAnnouncementServiceFailureTest {
    @Test
    fun a_network_failure_yields_no_feed() = runTest {
        val client = HttpClient(MockEngine { throw IOException("Unable to resolve host") })
        assertNull(STASYAnnouncementService(client).fetchFeed().first())
        assertEquals(emptyList(), STASYAnnouncementService(client).fetchAnnouncements().first())
    }

    @Test
    fun a_quiet_day_is_still_a_feed() = runTest {
        val client = HttpClient(MockEngine {
            respond("""{"status":null,"announcements":[]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val feed = assertNotNull(STASYAnnouncementService(client).fetchFeed().first())
        assertEquals(0, feed.announcements.size)
    }
}
