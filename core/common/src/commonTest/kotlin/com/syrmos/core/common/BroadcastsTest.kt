package com.syrmos.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BroadcastsTest {
    private fun b(id: String, audience: String = "all", sent: Long = 100, expires: Long? = null) =
        Broadcast(id, LocalizedText(en = id), LocalizedText(en = id), audience, sent, expires)

    @Test
    fun audienceMatchesAllFavouritesAndRegion() {
        val lines = setOf("M3")
        val stations = setOf("M3_NIK")
        assertTrue(Broadcasts.audienceMatches("all", lines, stations, "athens"))
        assertTrue(Broadcasts.audienceMatches("line:M3", lines, stations, "athens"))
        assertFalse(Broadcasts.audienceMatches("line:M1", lines, stations, "athens"))
        assertTrue(Broadcasts.audienceMatches("station:M3_NIK", lines, stations, "athens"))
        assertTrue(Broadcasts.audienceMatches("region:athens", lines, stations, "athens"))
        assertFalse(Broadcasts.audienceMatches("region:thessaloniki", lines, stations, "athens"))
        assertFalse(Broadcasts.audienceMatches("vip:1", lines, stations, "athens"))
    }

    @Test
    fun pushTopicsFollowContract() {
        assertEquals(
            listOf("all", "region.athens", "line.M3", "station.M3_NIK"),
            Broadcasts.pushTopics(listOf("M3_NIK"), "athens"),
        )
        assertEquals(listOf("all"), Broadcasts.pushTopics(emptyList(), "national"))
    }

    @Test
    fun pollingSkipsDeliveredSeenExpiredAndDuplicates() {
        val fetched = listOf(b("a", sent = 300), b("a", sent = 300), b("pushed"), b("seen"), b("old", expires = 50), b("m1", audience = "line:M1"))
        val out = Broadcasts.toNotify(fetched, deliveredIds = setOf("pushed"), seenIds = setOf("seen"), now = 100, favoriteStationIds = setOf("M3_NIK"), region = "athens")
        assertEquals(listOf("a"), out.map { it.id })
        assertEquals(300L, Broadcasts.nextSince(10, fetched))
        assertEquals(500L, Broadcasts.nextSince(500, fetched))
    }
}
