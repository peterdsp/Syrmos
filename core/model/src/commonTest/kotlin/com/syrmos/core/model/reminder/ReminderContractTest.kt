package com.syrmos.core.model.reminder

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Persistence contract for the Phase N J09 saved-departure board. The encoded
 * blob is the cross-client wire format (web and iOS stores mirror it), so this
 * pins field order and the round-trip; keep in step with fixtures/reminders/saved.json.
 */
class ReminderContractTest {

    private val sample = SavedDeparturesRoot(
        savedDepartures = listOf(
            SavedDeparture(
                lineId = "M1", stationId = "M1_VIC", stationName = "Victoria",
                destination = "Kifisia", scheduledTime = "08:00",
                departureEpochSeconds = 1_000_000, leadSeconds = 900,
                createdAt = Instant.parse("2026-01-01T08:00:00Z"),
            ),
        ),
    )

    // The exact wire form web-reminders.js and the iOS store must also produce.
    private val encoded =
        "{\"schemaVersion\":1,\"savedDepartures\":[{" +
            "\"lineId\":\"M1\",\"stationId\":\"M1_VIC\",\"stationName\":\"Victoria\"," +
            "\"destination\":\"Kifisia\",\"scheduledTime\":\"08:00\"," +
            "\"departureEpochSeconds\":1000000,\"leadSeconds\":900," +
            "\"createdAt\":\"2026-01-01T08:00:00Z\",\"schemaVersion\":1}]}"

    @Test
    fun encode_isByteParityWireForm() {
        assertEquals(encoded, ReminderContract.encodeSavedDeparturesRoot(sample))
    }

    @Test
    fun roundTrips() {
        val out = ReminderContract.encodeSavedDeparturesRoot(sample)
        val back = ReminderContract.decodeSavedDeparturesRoot(out)
        assertTrue(back is ReminderContract.DecodeResult.Ok)
        assertEquals(sample, back.value)
    }

    @Test
    fun blankBlob_isFreshEmptyList_notCorrupt() {
        val r = ReminderContract.decodeSavedDeparturesRoot("")
        assertTrue(r is ReminderContract.DecodeResult.Ok)
        assertEquals(emptyList(), r.value.savedDepartures)
    }

    @Test
    fun newerSchema_isUnsupported_notCorrupt() {
        val r = ReminderContract.decodeSavedDeparturesRoot("{\"schemaVersion\":999,\"savedDepartures\":[]}")
        assertTrue(r is ReminderContract.DecodeResult.Unsupported)
        assertEquals(999, r.foundVersion)
    }

    @Test
    fun garbage_isCorrupt_notThrown() {
        val r = ReminderContract.decodeSavedDeparturesRoot("{not json")
        assertTrue(r is ReminderContract.DecodeResult.Corrupt)
    }
}
