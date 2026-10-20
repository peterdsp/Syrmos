package com.syrmos.core.data.sync

import com.syrmos.core.network.STASYAnnouncement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AirportServiceAlertsTest {

    private val athensLines = setOf("M3", "M3_AIR", "A1", "A2", "X93", "X95", "X96", "X97")
    private val athensStations = setOf("M3_AER", "A1_AIR")
    private val thessLines = setOf("TM1", "TM2", "X3", "2X", "1X", "1N", "79")
    private val thessStations = setOf("THS_AIR", "TM2_MIK", "TM1_NEL")

    @Test
    fun matchesTheLiveM3RailWorksAlertByLineAndStation() {
        // Shape of the shipped stasy-homepage-alert: M3 line, airport-side stations.
        val m3 = announcement(
            id = "stasy-homepage-alert",
            severity = "warning",
            lines = listOf("M3"),
            stations = listOf("M3_ETH", "M3_HOL", "M3_NOM"),
        )
        val result = AirportServiceAlerts.matches(listOf(m3), athensLines, athensStations)
        assertEquals(listOf("stasy-homepage-alert"), result.map { it.id })
    }

    @Test
    fun ignoresNonAirportAndNonServiceAlertNotices() {
        val busLine = announcement(id = "oseth-38", severity = "info", lines = listOf("38"))
        val general = announcement(id = "news", severity = "info", lines = listOf("M3"), isServiceAlert = false)
        val result = AirportServiceAlerts.matches(listOf(busLine, general), athensLines, athensStations)
        assertTrue(result.isEmpty())
    }

    @Test
    fun matchesByStationIdEvenWhenLineIsBlank() {
        val stationOnly = announcement(id = "terminal", severity = "closure", lines = emptyList(), stations = listOf("M3_AER"))
        val result = AirportServiceAlerts.matches(listOf(stationOnly), athensLines, athensStations)
        assertEquals(listOf("terminal"), result.map { it.id })
    }

    @Test
    fun ordersBySeverityHighestFirstKeepingFeedOrderForTies() {
        val a = announcement(id = "a", severity = "info", lines = listOf("M3"))
        val b = announcement(id = "b", severity = "closure", lines = listOf("A1"))
        val c = announcement(id = "c", severity = "warning", lines = listOf("X95"))
        val d = announcement(id = "d", severity = "info", lines = listOf("A2"))
        val result = AirportServiceAlerts.matches(listOf(a, b, c, d), athensLines, athensStations)
        assertEquals(listOf("b", "c", "a", "d"), result.map { it.id })
    }

    @Test
    fun matchesCaseInsensitivelyOnLineId() {
        val lower = announcement(id = "lower", severity = "warning", lines = listOf("m3"))
        val result = AirportServiceAlerts.matches(listOf(lower), athensLines, athensStations)
        assertEquals(listOf("lower"), result.map { it.id })
    }

    @Test
    fun thessalonikiHubMatchesItsMetroAndShuttleCodesNotAthensM3() {
        val athensM3 = announcement(id = "m3", severity = "warning", lines = listOf("M3"), stations = listOf("M3_AER"))
        val shuttle = announcement(id = "x3", severity = "warning", lines = listOf("X3"))
        val interchange = announcement(id = "mik", severity = "warning", lines = emptyList(), stations = listOf("TM2_MIK"))
        val result = AirportServiceAlerts.matches(
            listOf(athensM3, shuttle, interchange),
            thessLines,
            thessStations,
        )
        assertEquals(setOf("x3", "mik"), result.map { it.id }.toSet())
    }

    private fun announcement(
        id: String,
        severity: String,
        lines: List<String> = emptyList(),
        stations: List<String> = emptyList(),
        isServiceAlert: Boolean = true,
    ) = STASYAnnouncement(
        id = id,
        title = "Alert",
        titleEn = "Alert",
        date = "",
        url = "",
        isServiceAlert = isServiceAlert,
        affectedLines = lines,
        affectedStationIds = stations,
        severity = severity,
    )
}
