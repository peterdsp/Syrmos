package com.syrmos.core.domain.fares

import com.syrmos.core.model.transit.Region
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComputeFareUseCaseTest {
    private val useCase = ComputeFareUseCase()
    private fun ath(id: String, airport: Boolean = false) = FareStation(id, setOf(Region.ATHENS), isAirport = airport)
    private fun thess(id: String, suburban: Boolean = false) = FareStation(id, setOf(Region.THESSALONIKI), isSuburban = suburban)
    private fun patras(id: String) = FareStation(id, setOf(Region.PATRAS))
    private fun national(id: String) = FareStation(id, setOf(Region.NATIONAL))
    /** A Thessaloniki interchange that also sits on the national IC line. */
    private fun thessHub(id: String) = FareStation(id, setOf(Region.NATIONAL, Region.THESSALONIKI))

    @Test
    fun athens_urban_trip_is_the_integrated_ticket() {
        val q = useCase.invoke(ath("M2_SYN"), ath("M1_MON"))
        assertEquals(1.20, q.fullPriceEur)
        assertEquals(0.50, q.reducedPriceEur)
        assertEquals("OASA", q.operator)
        assertTrue(!q.dynamic)
    }

    @Test
    fun athens_trip_involving_the_airport_is_the_airport_fare() {
        val q = useCase.invoke(ath("M2_SYN"), ath("M3_AIR", airport = true))
        assertEquals(9.00, q.fullPriceEur)
        assertEquals(4.50, q.reducedPriceEur)
    }

    @Test
    fun thessaloniki_urban_vs_suburban() {
        assertEquals(0.60, useCase.invoke(thess("TM1_A"), thess("TM1_B")).fullPriceEur)
        assertEquals(0.80, useCase.invoke(thess("TP1_A"), thess("TP1_B", suburban = true)).fullPriceEur)
    }

    @Test
    fun patras_suburban_has_a_grounded_base_fare() {
        val q = useCase.invoke(patras("PA_RIO"), patras("PA_PAT"))
        assertEquals(1.40, q.fullPriceEur)
        assertEquals("Hellenic Train", q.operator)
    }

    @Test
    fun intercity_reference_fares_are_marked_dynamic_and_dated() {
        val ic = useCase.invoke(national("GR_ATH"), national("GR_THE"))
        assertTrue(ic.dynamic)
        assertNull(ic.fullPriceEur)
        assertEquals(43.00, ic.referencePriceEur)
        assertEquals("2026-08-05", ic.referenceObservedOn)
        assertTrue(ic.sourceUrl.contains("hellenictrain"))

        val routeWithoutReference = useCase.invoke(national("GR_OIN"), national("GR_THI"))
        assertTrue(routeWithoutReference.dynamic)
        assertNull(routeWithoutReference.referencePriceEur)

        // A cross-region trip with non-national station IDs remains booking-priced.
        val cross = useCase.invoke(ath("M2_SYN"), thess("TM1_A"))
        assertTrue(cross.dynamic)
        assertNull(cross.referencePriceEur)
    }

    @Test
    fun shared_local_network_wins_over_a_national_leg_at_an_interchange() {
        // GR_THE is on both IC1 (national) and the TP suburban lines. A trip to a
        // Thessaloniki-only suburban stop must be the OSETH suburban fare, not
        // intercity, because both stations share the local Thessaloniki network.
        val q = useCase.invoke(thessHub("GR_THE"), thess("TP3_SIN", suburban = true))
        assertEquals(0.80, q.fullPriceEur)
        assertTrue(!q.dynamic)
        // But the same hub -> an Athens stop (no shared local network) is intercity.
        assertTrue(useCase.invoke(thessHub("GR_THE"), ath("M2_SYN")).dynamic)
    }
}
