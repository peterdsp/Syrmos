package com.syrmos.core.model.status

import com.syrmos.core.model.schedule.SourceConfidence
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * The typed data-status / freshness contract. Provenance ([SourceConfidence])
 * and temporal condition ([Freshness]) are orthogonal; the resolved
 * [DataStatusDisplay] is structured (kind + raw ageSeconds) and never discards
 * provenance, and carries no localizable copy. The timestamps here are synthetic
 * fixtures for the freshness logic only; production departure times come from the
 * projector, ordered routes and station offsets, never from these.
 */
class DataStatusTest {

    private val now = 1_000_000L // epoch seconds, fixed test clock
    private val nowInstant = Instant.fromEpochSeconds(now)

    @Test
    fun freshLiveItem() {
        val f = freshnessFromEpoch(now - 10, now, windowSeconds = 90)
        assertIs<Freshness.Fresh>(f)
        val d = DataStatus(SourceConfidence.LIVE, f).display()
        assertEquals(DisplayKind.LIVE, d.kind)
        assertNull(d.ageSeconds)
    }

    @Test
    fun exactlyAtNinetySecondBoundaryIsFreshOneMoreIsStale() {
        assertIs<Freshness.Fresh>(freshnessFromEpoch(now - 90, now, 90))
        assertIs<Freshness.Stale>(freshnessFromEpoch(now - 91, now, 90))
    }

    @Test
    fun staleLiveItemCarriesRawPerItemAgeAndKeepsProvenance() {
        val stale = assertIs<Freshness.Stale>(freshnessFromEpoch(now - 360, now, 90))
        assertEquals(360L, stale.ageSeconds)
        val d = DataStatus(SourceConfidence.LIVE, stale).display()
        assertEquals(DisplayKind.CACHED, d.kind)
        assertEquals(360L, d.ageSeconds) // raw seconds, not a preformatted string
        // Guardrail: provenance + freshness survive the display mapping.
        assertEquals(SourceConfidence.LIVE, d.status.provenance)
        assertEquals(stale, d.status.freshness)
    }

    @Test
    fun scheduledAndEstimatedStayDistinct() {
        val sched = DataStatus(SourceConfidence.SCHEDULED, Freshness.NotApplicable).display()
        val est = DataStatus(SourceConfidence.ESTIMATED, Freshness.NotApplicable).display()
        assertEquals(DisplayKind.SCHEDULED, sched.kind)
        assertEquals(DisplayKind.ESTIMATED, est.kind)
        assertNotEquals(sched.kind, est.kind)
    }

    @Test
    fun bundledTimetableIsProvenanceNotConnectivity() {
        // The mapper consults only provenance — there is no connectivity input,
        // so "offline" here is the bundled-timetable source, not device state.
        val d = DataStatus(SourceConfidence.OFFLINE, Freshness.NotApplicable).display()
        assertEquals(DisplayKind.OFFLINE, d.kind)
        assertEquals(SourceConfidence.OFFLINE, d.status.provenance)
    }

    @Test
    fun missingOrMalformedTimestampIsUnknownAge() {
        assertIs<Freshness.UnknownAge>(freshnessFromIso(null, nowInstant))
        assertIs<Freshness.UnknownAge>(freshnessFromIso("", nowInstant))
        assertIs<Freshness.UnknownAge>(freshnessFromIso("   ", nowInstant))
        assertIs<Freshness.UnknownAge>(freshnessFromIso("not-a-timestamp", nowInstant))
        assertNull(parseIsoToEpochSeconds("2026-13-45T99:99:99Z"))
    }

    @Test
    fun liveWithUnknownAgeIsNotAPlainLiveBadge() {
        // A live source whose recency we can't vouch for must be its own state.
        val d = DataStatus(SourceConfidence.LIVE, Freshness.UnknownAge).display()
        assertEquals(DisplayKind.LIVE_UNKNOWN_AGE, d.kind)
        assertNotEquals(DisplayKind.LIVE, d.kind)
        assertEquals(SourceConfidence.LIVE, d.status.provenance) // provenance intact
    }

    @Test
    fun liveWithNotApplicableIsInvalidSoUnavailable() {
        val d = DataStatus(SourceConfidence.LIVE, Freshness.NotApplicable).display()
        assertEquals(DisplayKind.UNAVAILABLE, d.kind)
    }

    @Test
    fun futureWithinToleranceIsFreshBeyondToleranceIsUnknownAge() {
        val tol = DEFAULT_FUTURE_SKEW_TOLERANCE_SECONDS
        assertIs<Freshness.Fresh>(freshnessFromEpoch(now + tol, now, futureSkewToleranceSeconds = tol)) // exactly at tolerance
        assertIs<Freshness.UnknownAge>(freshnessFromEpoch(now + tol + 1, now, futureSkewToleranceSeconds = tol)) // one past
        // A timestamp years in the future is never "fresh".
        assertIs<Freshness.UnknownAge>(freshnessFromEpoch(now + 60L * 60 * 24 * 365, now))
    }

    @Test
    fun unknownProviderIsUnavailable() {
        val d = DataStatus(SourceConfidence.UNKNOWN, Freshness.NotApplicable).display()
        assertEquals(DisplayKind.UNAVAILABLE, d.kind)
    }

    @Test
    fun emptyLiveResponseIsUnavailable() {
        // A live feed that returned no rows -> Unavailable, never a fake countdown.
        val d = DataStatus(SourceConfidence.LIVE, Freshness.Unavailable).display()
        assertEquals(DisplayKind.UNAVAILABLE, d.kind)
    }

    @Test
    fun unavailableFreshnessIsAlwaysUnavailableForEveryProvenance() {
        // "No datum" can never read as Scheduled / Estimated / Offline / Operator.
        for (prov in SourceConfidence.entries) {
            val d = DataStatus(prov, Freshness.Unavailable).display()
            assertEquals(DisplayKind.UNAVAILABLE, d.kind, "$prov + Unavailable must resolve to UNAVAILABLE")
            assertNull(d.ageSeconds)
            assertEquals(prov, d.status.provenance) // provenance still retained
        }
    }

    @Test
    fun freshnessIsPerItemNotProcessWide() {
        // Same clock, different per-item timestamps -> independent verdicts.
        assertIs<Freshness.Fresh>(freshnessFromEpoch(now - 10, now, 90))
        val b = assertIs<Freshness.Stale>(freshnessFromEpoch(now - 300, now, 90))
        assertEquals(300L, b.ageSeconds)
    }

    @Test
    fun isoTimestampsResolveToFreshOrStale() {
        val at = Instant.parse("2026-08-29T12:00:00Z")
        assertIs<Freshness.Fresh>(freshnessFromIso("2026-08-29T11:59:30Z", at, 90)) // 30s
        val stale = assertIs<Freshness.Stale>(freshnessFromIso("2026-08-29T11:54:00Z", at, 90)) // 360s
        assertEquals(360L, stale.ageSeconds)
    }

    @Test
    fun negativeWindowOrToleranceIsRejected() {
        assertFailsWith<IllegalArgumentException> { freshnessFromEpoch(now - 10, now, windowSeconds = -1) }
        assertFailsWith<IllegalArgumentException> { freshnessFromEpoch(now - 10, now, futureSkewToleranceSeconds = -1) }
        // The ISO path validates config BEFORE parsing, so a null or malformed
        // timestamp must not mask a bad window/tolerance behind an UnknownAge return.
        assertFailsWith<IllegalArgumentException> { freshnessFromIso(null, nowInstant, windowSeconds = -1) }
        assertFailsWith<IllegalArgumentException> { freshnessFromIso("not-a-timestamp", nowInstant, futureSkewToleranceSeconds = -1) }
    }

    @Test
    fun staleAgeCannotBeNegative() {
        assertFailsWith<IllegalArgumentException> { Freshness.Stale(-1) }
    }

    @Test
    fun unknownRawProvenanceFallsBackToScheduledNotUnknown() {
        // Documents an integration gap deliberately left outside this contract:
        // SourceConfidence.fromRaw maps unrecognized/absent raw strings to
        // SCHEDULED, so an "unknown provider" does NOT reach UNKNOWN via the
        // parser. Producing UNKNOWN is the caller's explicit responsibility
        // (construct SourceConfidence.UNKNOWN); changing fromRaw's fallback would
        // alter existing departure parsing and belongs to a later track.
        assertEquals(SourceConfidence.SCHEDULED, SourceConfidence.fromRaw("garbage"))
        assertEquals(SourceConfidence.SCHEDULED, SourceConfidence.fromRaw(null))
        // The contract still maps an explicit UNKNOWN to Unavailable:
        assertEquals(
            DisplayKind.UNAVAILABLE,
            DataStatus(SourceConfidence.UNKNOWN, Freshness.NotApplicable).display().kind,
        )
    }
}
