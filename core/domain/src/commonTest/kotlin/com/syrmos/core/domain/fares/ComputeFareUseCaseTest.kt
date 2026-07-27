package com.syrmos.core.domain.fares

import com.syrmos.core.model.transit.Region
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComputeFareUseCaseTest {
    private val useCase = ComputeFareUseCase()
    private fun ath(id: String, airport: Boolean = false) = FareStation(id, Region.ATHENS, isAirport = airport)
    private fun thess(id: String, suburban: Boolean = false) = FareStation(id, Region.THESSALONIKI, isSuburban = suburban)
    private fun patras(id: String) = FareStation(id, Region.PATRAS)
    private fun national(id: String) = FareStation(id, Region.NATIONAL)

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
    fun intercity_and_cross_region_are_dynamic_no_fabricated_price() {
        // National leg.
        val ic = useCase.invoke(national("GR_ATH"), national("GR_THE"))
        assertTrue(ic.dynamic)
        assertNull(ic.fullPriceEur)
        assertTrue(ic.sourceUrl.contains("hellenictrain"))
        // Cross-region (Athens -> Thessaloniki) is also intercity.
        val cross = useCase.invoke(ath("M2_SYN"), thess("TM1_A"))
        assertTrue(cross.dynamic)
        assertNull(cross.fullPriceEur)
    }
}
