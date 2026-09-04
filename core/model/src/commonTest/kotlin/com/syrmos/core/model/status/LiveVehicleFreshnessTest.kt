package com.syrmos.core.model.status

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The real-GPS marker freshness classifier. A live vehicle must render as a
 * plain live dot ONLY within the fresh window; past it, it is de-emphasised and
 * aged (STALE) and eventually dropped (EXPIRED). Missing/unusable and far-future
 * timestamps never read as live. Fixed test clock; no real clock used.
 */
class LiveVehicleFreshnessTest {

    private val now = 1_000_000L // epoch seconds, fixed test clock
    private val nowInstant = Instant.fromEpochSeconds(now)

    @Test
    fun freshPositionIsLive() {
        val f = classifyLiveVehicle(now - 10, now)
        assertEquals(LiveVehicleState.LIVE, f.state)
        assertEquals(10, f.ageSeconds)
    }

    @Test
    fun exactlyAtFreshBoundaryIsLiveOneMoreIsStale() {
        assertEquals(LiveVehicleState.LIVE, classifyLiveVehicle(now - 90, now).state)
        assertEquals(LiveVehicleState.STALE, classifyLiveVehicle(now - 91, now).state)
    }

    @Test
    fun staleCarriesItsAge() {
        val f = classifyLiveVehicle(now - 200, now)
        assertEquals(LiveVehicleState.STALE, f.state)
        assertEquals(200, f.ageSeconds)
    }

    @Test
    fun exactlyAtExpiryIsStaleOneMoreIsExpired() {
        assertEquals(LiveVehicleState.STALE, classifyLiveVehicle(now - 600, now).state)
        assertEquals(LiveVehicleState.EXPIRED, classifyLiveVehicle(now - 601, now).state)
    }

    @Test
    fun expiredCarriesItsAge() {
        val f = classifyLiveVehicle(now - 3600, now)
        assertEquals(LiveVehicleState.EXPIRED, f.state)
        assertEquals(3600, f.ageSeconds)
    }

    @Test
    fun missingTimestampIsStaleWithNoAgeNeverLive() {
        val f = classifyLiveVehicle(null, now)
        assertEquals(LiveVehicleState.STALE, f.state)
        assertNull(f.ageSeconds)
    }

    @Test
    fun smallFutureSkewIsTreatedAsJustNowLive() {
        // within the default 120s tolerance
        val f = classifyLiveVehicle(now + 60, now)
        assertEquals(LiveVehicleState.LIVE, f.state)
        assertEquals(0, f.ageSeconds)
    }

    @Test
    fun farFutureTimestampIsStaleNotLive() {
        val f = classifyLiveVehicle(now + 100_000, now)
        assertEquals(LiveVehicleState.STALE, f.state)
        assertNull(f.ageSeconds)
    }

    @Test
    fun isoConvenienceParsesAndClassifies() {
        val fresh = classifyLiveVehicleIso(Instant.fromEpochSeconds(now - 5).toString(), nowInstant)
        assertEquals(LiveVehicleState.LIVE, fresh.state)
        val stale = classifyLiveVehicleIso(Instant.fromEpochSeconds(now - 300).toString(), nowInstant)
        assertEquals(LiveVehicleState.STALE, stale.state)
    }

    @Test
    fun isoBlankOrMalformedIsStaleWithNoAge() {
        assertEquals(LiveVehicleState.STALE, classifyLiveVehicleIso(null, nowInstant).state)
        assertEquals(LiveVehicleState.STALE, classifyLiveVehicleIso("", nowInstant).state)
        assertEquals(LiveVehicleState.STALE, classifyLiveVehicleIso("not-a-date", nowInstant).state)
        assertNull(classifyLiveVehicleIso("not-a-date", nowInstant).ageSeconds)
    }

    @Test
    fun customWindowsAreHonoured() {
        // 30s fresh, 120s expiry
        assertEquals(LiveVehicleState.LIVE, classifyLiveVehicle(now - 30, now, freshWindowSeconds = 30, expirySeconds = 120).state)
        assertEquals(LiveVehicleState.STALE, classifyLiveVehicle(now - 31, now, freshWindowSeconds = 30, expirySeconds = 120).state)
        assertEquals(LiveVehicleState.EXPIRED, classifyLiveVehicle(now - 121, now, freshWindowSeconds = 30, expirySeconds = 120).state)
    }

    @Test
    fun invalidConfigurationRejected() {
        assertFailsWith<IllegalArgumentException> { classifyLiveVehicle(now, now, freshWindowSeconds = -1) }
        assertFailsWith<IllegalArgumentException> { classifyLiveVehicle(now, now, freshWindowSeconds = 100, expirySeconds = 50) }
        assertFailsWith<IllegalArgumentException> { classifyLiveVehicle(now, now, futureSkewToleranceSeconds = -1) }
    }
}
