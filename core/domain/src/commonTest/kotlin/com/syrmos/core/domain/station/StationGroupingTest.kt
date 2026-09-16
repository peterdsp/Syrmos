package com.syrmos.core.domain.station

import com.syrmos.core.model.transit.Station
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Finding 2 (Android/shared peer of iOS StationGroupingTests): the selector shows
 * one row per physical station. Co-located same-name stops collapse keeping every
 * line; distinct stations stay separate; identity is name AND place.
 */
class StationGroupingTest {
    private fun station(id: String, name: String, nameEl: String, lat: Double, lon: Double, lines: List<String>) =
        Station(id = id, name = name, nameEl = nameEl, latitude = lat, longitude = lon, lineIds = lines)

    private fun kifis() = listOf(
        station("M1_KIF", "Kifissia", "Κηφισιά", 38.0733951, 23.8082198, listOf("M1")),
        station("A1_KIF", "Kifisias", "Κηφισίας", 38.0419210, 23.8040729, listOf("A1")),
        station("A2_KIF", "Kifisias", "Κηφισίας", 38.0419210, 23.8040729, listOf("A2")),
    )

    @Test
    fun colocatedDuplicatesCollapseKeepingEveryLine() {
        val groups = StationGrouping.groups(kifis())
        assertEquals(2, groups.size, "three ids, two physical stations")
        val kifisias = groups.first { it.name == "Kifisias" }
        assertEquals(listOf("A1_KIF", "A2_KIF"), kifisias.memberIds, "every routable stop retained")
        assertEquals("A1_KIF", kifisias.representativeId, "stable representative = smallest id")
        assertTrue(kifisias.lineIds.contains("A1") && kifisias.lineIds.contains("A2"), "both lines represented")
    }

    @Test
    fun kifissiaAndKifisiasStayDistinct() {
        val names = StationGrouping.groups(kifis()).map { it.name }.toSet()
        assertTrue("Kifissia" in names && "Kifisias" in names, "different spelling and place must not merge")
    }

    @Test
    fun accentedAndUnaccentedSearchBothHit() {
        val kifisias = StationGrouping.groups(kifis()).first { it.name == "Kifisias" }
        for (query in listOf("Kifis", "kifis", "Κηφισ", "Κηφισίας", "κηφισιας")) {
            assertTrue(StationGrouping.matches(kifisias, StationGrouping.fold(query)), "query $query should match")
        }
    }

    @Test
    fun sameNameFarApartDoesNotMerge() {
        val groups = StationGrouping.groups(listOf(
            station("X_DIM", "Dimarcheio", "Δημαρχείο", 38.0, 23.7, listOf("X")),
            station("Y_DIM", "Dimarcheio", "Δημαρχείο", 40.6, 22.9, listOf("Y")),
        ))
        assertEquals(2, groups.size, "same name, different place = distinct stations")
    }

    @Test
    fun reorderedInputYieldsSameGroups() {
        val a = StationGrouping.groups(kifis())
        val b = StationGrouping.groups(kifis().reversed())
        assertEquals(a.map { it.id }.toSet(), b.map { it.id }.toSet())
        val ka = a.first { it.name == "Kifisias" }
        val kb = b.first { it.name == "Kifisias" }
        assertEquals(ka.memberIds, kb.memberIds, "member set is order-independent")
        assertEquals(ka.representativeId, kb.representativeId, "representative is stable")
    }
}
