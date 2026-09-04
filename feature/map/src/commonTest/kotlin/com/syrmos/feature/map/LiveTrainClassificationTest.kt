package com.syrmos.feature.map

import com.syrmos.core.model.status.LiveVehicleState
import com.syrmos.core.model.transit.LiveSuburbanTrain
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The map's honesty guarantee for real-GPS trains: a position is plotted as a
 * plain live dot ONLY while fresh; once aged it is kept but tagged STALE (so the
 * renderer de-emphasises it and shows its age), and once EXPIRED it is dropped
 * so a dead/offline feed never freezes a "live" ghost on the map. EXPIRED trains
 * must also release their line so the schedule projector fills back in - the
 * offline-fallback regression this guards.
 */
class LiveTrainClassificationTest {

    private val now = Instant.fromEpochSeconds(1_000_000)

    private fun train(id: String, lineId: String, ageSeconds: Long?): LiveSuburbanTrain =
        LiveSuburbanTrain(
            id = id,
            lineId = lineId,
            trainNumber = id,
            origin = null,
            originEn = null,
            destination = null,
            destinationEn = null,
            nextStation = null,
            nextStationEn = null,
            delayMinutes = 0,
            serviceType = "regular",
            progress = 0.0,
            speedKph = 0.0,
            latitude = 38.0,
            longitude = 23.7,
            updatedAt = if (ageSeconds == null) "" else Instant.fromEpochSeconds(now.epochSeconds - ageSeconds).toString(),
        )

    @Test
    fun freshTrainIsLiveAndCovers() {
        val c = classifyLiveTrains(listOf(train("t1", "A1", ageSeconds = 10)), now)
        assertEquals(1, c.markers.size)
        assertEquals(LiveVehicleState.LIVE, c.markers[0].state)
        assertEquals(10, c.markers[0].ageSeconds)
        assertTrue("A1" in c.coveredLineIds)
    }

    @Test
    fun agedTrainIsStaleButStillPlottedAndCovers() {
        val c = classifyLiveTrains(listOf(train("t1", "A2", ageSeconds = 300)), now)
        assertEquals(1, c.markers.size)
        assertEquals(LiveVehicleState.STALE, c.markers[0].state)
        assertEquals(300, c.markers[0].ageSeconds)
        assertTrue("A2" in c.coveredLineIds)
    }

    @Test
    fun expiredTrainIsDroppedAndReleasesItsLine() {
        val c = classifyLiveTrains(listOf(train("t1", "A3", ageSeconds = 1000)), now)
        assertTrue(c.markers.isEmpty(), "an expired ghost must not be plotted")
        assertFalse("A3" in c.coveredLineIds, "an expired line must be released to the projector")
    }

    @Test
    fun timestamplessTrainIsStaleWithNoAgeNeverLive() {
        val c = classifyLiveTrains(listOf(train("t1", "A4", ageSeconds = null)), now)
        assertEquals(1, c.markers.size)
        assertEquals(LiveVehicleState.STALE, c.markers[0].state)
        assertNull(c.markers[0].ageSeconds)
    }

    @Test
    fun mixedFleetPartitionsCorrectly() {
        val c = classifyLiveTrains(
            listOf(
                train("t1", "A1", ageSeconds = 10),    // LIVE
                train("t2", "A2", ageSeconds = 300),   // STALE
                train("t3", "A3", ageSeconds = 1000),  // EXPIRED -> dropped
                train("t4", "A4", ageSeconds = null),  // STALE (no ts)
            ),
            now,
        )
        assertEquals(listOf("A1", "A2", "A4"), c.markers.map { it.train.lineId })
        assertEquals(setOf("A1", "A2", "A4"), c.coveredLineIds)
        assertFalse("A3" in c.coveredLineIds)
    }
}
