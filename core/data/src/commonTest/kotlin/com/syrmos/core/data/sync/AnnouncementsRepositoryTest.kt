package com.syrmos.core.data.sync

import com.syrmos.core.model.alerts.AlertSeverity
import com.syrmos.core.network.STASYAnnouncement
import kotlin.test.Test
import kotlin.test.assertEquals

class AnnouncementsRepositoryTest {
    @Test
    fun derivesHighestSeverityForEveryAffectedLine() {
        val disruptions = deriveLineDisruptions(
            listOf(
                announcement("warning", listOf("M3", "T7")),
                announcement("closure", listOf("m3")),
                announcement("info", listOf("T7")),
            ),
        )

        assertEquals(AlertSeverity.CLOSURE, disruptions["M3"])
        assertEquals(AlertSeverity.WARNING, disruptions["T7"])
    }

    @Test
    fun airportBranchAlsoMarksTheBaseM3Line() {
        val disruptions = deriveLineDisruptions(
            listOf(announcement("warning", listOf("M3_AIR"))),
        )

        assertEquals(AlertSeverity.WARNING, disruptions["M3_AIR"])
        assertEquals(AlertSeverity.WARNING, disruptions["M3"])
    }

    @Test
    fun baseM3AlsoMarksTheAirportBranch() {
        val disruptions = deriveLineDisruptions(
            listOf(announcement("closure", listOf("M3"))),
        )

        assertEquals(AlertSeverity.CLOSURE, disruptions["M3"])
        assertEquals(AlertSeverity.CLOSURE, disruptions["M3_AIR"])
    }

    private fun announcement(severity: String, lines: List<String>) = STASYAnnouncement(
        id = "$severity-${lines.joinToString()}",
        title = "Alert",
        titleEn = "Alert",
        date = "",
        url = "",
        isServiceAlert = true,
        affectedLines = lines,
        severity = severity,
    )
}
