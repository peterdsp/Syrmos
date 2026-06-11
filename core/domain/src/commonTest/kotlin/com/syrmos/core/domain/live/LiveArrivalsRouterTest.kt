package com.syrmos.core.domain.live

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class LiveArrivalsRouterTest {

    @Test
    fun returnsNullWhenAllProvidersReturnNull() = runTest {
        val router = LiveArrivalsRouter(
            providers = listOf(
                StasyLiveArrivalsProvider(),
                OasaLiveArrivalsProvider(),
                HellenicTrainLiveArrivalsProvider(),
            )
        )
        assertNull(router.arrivals("M3_NIK", "M3"))
        assertNull(router.arrivals("A1_PIR", "A1"))
    }

    @Test
    fun skipsProvidersThatDoNotOwnTheLine() = runTest {
        val ht = HellenicTrainLiveArrivalsProvider()
        // Hellenic Train owns suburban A1-A4 only — must not be asked about M3.
        assertEquals(false, "M3" in ht.lineIds())
        // STASY owns metro and tram — must own M3.
        val stasy = StasyLiveArrivalsProvider()
        assertEquals(true, "M3" in stasy.lineIds())
    }

    @Test
    fun usesFirstNonNullResult() = runTest {
        val fake = object : LiveArrivalsProvider {
            override val sourceId = "fake"
            override fun lineIds() = setOf("M3")
            override suspend fun arrivals(stationId: String, lineId: String, limit: Int) = listOf(
                LiveArrivalsProvider.LiveArrival(minutesAway = 3, direction = "Airport"),
                LiveArrivalsProvider.LiveArrival(minutesAway = 9, direction = "Airport"),
            )
        }
        val router = LiveArrivalsRouter(providers = listOf(fake, StasyLiveArrivalsProvider()))
        val result = router.arrivals("M3_AIR", "M3")
        assertEquals(2, result?.size)
        assertEquals(3, result?.first()?.minutesAway)
    }
}
