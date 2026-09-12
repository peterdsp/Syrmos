package com.syrmos.core.model.journey

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the 3.0 Journeys shared contract: stable wire names, a versioned/atomic
 * persistence path, null-means-unknown, and that the Kotlin models decode the
 * exact JSON shape of fixtures/journeys/ui-reference.json (so Kotlin, Swift and
 * JS can all read one fixture). Runs on every KMP target.
 */
class JourneyContractTest {

    private val instant = Instant.parse("2026-01-15T08:42:00+02:00")
    private val serviceDate = LocalDate.parse("2026-01-15")

    private fun sampleOption() = JourneyOption(
        id = "opt-1",
        requestId = "req-1",
        transferCount = 0,
        feasibility = Feasibility(FeasibilityStatus.COMFORTABLE, explanationCode = "direct_scheduled"),
        departureInstant = instant,
        arrivalInstant = Instant.parse("2026-01-15T09:12:00+02:00"),
        durationSeconds = 1800,
        legs = listOf(
            Leg(
                id = "leg-1",
                kind = LegKind.RIDE,
                fromId = "test-origin",
                toId = "test-destination",
                lineId = "TEST-A",
                orderedStopIds = listOf("test-origin", "test-transfer", "test-destination"),
                departureInstant = instant,
                serviceDate = serviceDate,
                timingKind = TimingKind.SCHEDULED,
            ),
        ),
    )

    @Test
    fun activeJourneyRoundTripsThroughPersistence() {
        val active = ActiveJourney(
            id = "go-1",
            revision = 0,
            itinerarySnapshot = sampleOption(),
            phase = JourneyPhase.READY_TO_BOARD,
            legId = "leg-1",
            startedAt = instant,
            updatedAt = instant,
            alertPreferences = AlertPreferences(getOffAlert = true),
        )
        val raw = JourneyContract.encodeActiveJourney(active)
        val decoded = JourneyContract.decodeActiveJourney(raw)
        assertTrue(decoded is JourneyContract.DecodeResult.Ok)
        assertEquals(active, decoded.value, "round-trip is lossless")
        assertEquals(JourneyContract.SCHEMA_VERSION, decoded.value.schemaVersion)
    }

    @Test
    fun savedJourneyRoundTrips() {
        val saved = SavedJourney(
            id = "saved-1",
            fromId = "test-origin",
            toId = "test-destination",
            createdAt = instant,
            label = "Home",
        )
        val decoded = JourneyContract.decodeSavedJourney(JourneyContract.encodeSavedJourney(saved))
        assertTrue(decoded is JourneyContract.DecodeResult.Ok)
        assertEquals(saved, decoded.value)
    }

    @Test
    fun enumWireNamesAreLowercaseCamel() {
        // A request JSON written by any client must decode here. Names match the
        // prompt/fixtures exactly (arriveBy, fewestChanges, stepFree ...).
        val raw = """
            {
              "fromStationId": "a", "toStationId": "b",
              "timeMode": "arriveBy",
              "requestedInstant": "2026-01-15T09:00:00+02:00",
              "ranking": "fewestChanges",
              "accessibilityPreference": "stepFree",
              "schemaVersion": 1
            }
        """.trimIndent()
        val req = JourneyContract.json.decodeFromString(JourneyRequest.serializer(), raw)
        assertEquals(TimeMode.ARRIVE_BY, req.timeMode)
        assertEquals(Ranking.FEWEST_CHANGES, req.ranking)
        assertEquals(AccessibilityPreference.STEP_FREE, req.accessibilityPreference)
        assertEquals("Europe/Athens", JourneyContract.SERVICE_TIME_ZONE)
    }

    @Test
    fun decodesTheUiReferenceFixtureWireShape() {
        // A byte-for-byte copy of the transfer option from
        // fixtures/journeys/ui-reference.json. If this stops decoding, the fixture
        // and the typed contract have drifted apart.
        val raw = """
            {
              "id": "ui-ref-transfer",
              "requestId": "ui-ref",
              "rankingBadge": null,
              "departureInstant": "2026-01-15T08:45:00+02:00",
              "arrivalInstant": "2026-01-15T09:20:00+02:00",
              "durationSeconds": 2100,
              "transferCount": 1,
              "walkingSeconds": 180,
              "feasibility": { "status": "tight", "minimumMarginSeconds": 120, "explanationCode": "transfer_tight", "limitingLegId": "ui-ref-transfer-leg2" },
              "sourceSummary": { "timingKind": "scheduled", "snapshotVersion": "ui-ref-1", "statusCoverage": "known", "scheduleCoverage": "known" },
              "legs": [
                { "id": "ui-ref-transfer-leg1", "kind": "ride", "fromId": "test-origin", "toId": "test-transfer", "lineId": "TEST-A", "directionId": "test-transfer", "tripId": "t1", "orderedStopIds": ["test-origin","test-transfer"], "departureInstant": "2026-01-15T08:45:00+02:00", "arrivalInstant": "2026-01-15T09:00:00+02:00", "serviceDate": "2026-01-15", "timingKind": "scheduled", "uncertaintySeconds": null, "transferMinimumSeconds": null, "accessibility": "unknown", "sourceRef": { "sourceId": "ui-ref", "observedAt": null, "fetchedAt": "2026-01-15T08:30:00+02:00", "validUntil": null, "snapshotVersion": "ui-ref-1", "statusCoverage": "known", "scheduleCoverage": "known" } },
                { "id": "ui-ref-transfer-walk", "kind": "transfer", "fromId": "test-transfer", "toId": "test-transfer", "lineId": null, "directionId": null, "tripId": null, "orderedStopIds": ["test-transfer"], "departureInstant": "2026-01-15T09:00:00+02:00", "arrivalInstant": "2026-01-15T09:03:00+02:00", "serviceDate": "2026-01-15", "timingKind": "scheduled", "uncertaintySeconds": null, "transferMinimumSeconds": 120, "accessibility": "unknown", "sourceRef": { "sourceId": "ui-ref" } },
                { "id": "ui-ref-transfer-leg2", "kind": "ride", "fromId": "test-transfer", "toId": "test-destination", "lineId": "TEST-B", "directionId": "test-destination", "tripId": "t2", "orderedStopIds": ["test-transfer","test-destination"], "departureInstant": "2026-01-15T09:05:00+02:00", "arrivalInstant": "2026-01-15T09:20:00+02:00", "serviceDate": "2026-01-15", "timingKind": "scheduled", "accessibility": "unknown", "sourceRef": { "sourceId": "ui-ref" } }
              ]
            }
        """.trimIndent()
        val opt = JourneyContract.json.decodeFromString(JourneyOption.serializer(), raw)
        assertEquals("ui-ref-transfer", opt.id)
        assertEquals(1, opt.transferCount)
        assertEquals(listOf(LegKind.RIDE, LegKind.TRANSFER, LegKind.RIDE), opt.legs.map { it.kind })
        assertEquals(FeasibilityStatus.TIGHT, opt.feasibility.status)
        assertEquals(120, opt.feasibility.minimumMarginSeconds)
        assertNull(opt.rankingBadge, "no ranking badge unless the ranking is actually true")
    }

    @Test
    fun nullMeansUnknownIsPreservedNotCoercedToZero() {
        val leg = sampleOption().legs.first()
        assertNull(leg.uncertaintySeconds, "unknown uncertainty stays null")
        assertNull(leg.transferMinimumSeconds)
        // survives a round trip (explicitNulls keeps null distinct from 0/absent)
        val roundTripped = JourneyContract.json.decodeFromString(
            Leg.serializer(),
            JourneyContract.json.encodeToString(Leg.serializer(), leg),
        )
        assertNull(roundTripped.uncertaintySeconds)
    }

    @Test
    fun aNewerSchemaVersionRoutesToRecoveryNotACrash() {
        val raw = """{ "schemaVersion": 9999, "id": "x", "fromId": "a", "toId": "b",
            "createdAt": "2026-01-15T08:00:00+02:00" }"""
        val decoded = JourneyContract.decodeSavedJourney(raw)
        assertTrue(decoded is JourneyContract.DecodeResult.Unsupported)
        assertEquals(9999, decoded.foundVersion)
    }

    @Test
    fun corruptBlobRoutesToRecoveryAndNeverThrows() {
        val decoded = JourneyContract.decodeActiveJourney("{ not valid json ")
        assertTrue(decoded is JourneyContract.DecodeResult.Corrupt)
    }
}
