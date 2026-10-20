package com.syrmos.core.data.sync

import com.syrmos.core.model.alerts.AlertSeverity
import com.syrmos.core.network.STASYAnnouncement

/**
 * Pure selector: which live service alerts affect an airport hub.
 *
 * The airport hub screen no longer ships a static "no feed available"
 * placeholder. Instead it intersects each live [STASYAnnouncement]'s
 * affectedLines / affectedStationIds with the hub's relevant line and station id
 * sets, so an alert like the M3 rail-works notice surfaces directly on the hub.
 *
 * Matching rules (kept deliberately simple so iOS can mirror it exactly):
 *  - only service alerts are considered,
 *  - a line id matches case-insensitively after trimming (so "m3" == "M3", and
 *    the M3 / M3_AIR split is handled by listing both ids in the hub set),
 *  - a station id matches after trimming (case-sensitive, as seeded),
 *  - results are ordered highest severity first, ties keeping feed order.
 *
 * Mirrors the iOS `AirportServiceAlerts.matches(...)`; a change here must change
 * both.
 */
object AirportServiceAlerts {
    fun matches(
        announcements: List<STASYAnnouncement>,
        lineIds: Set<String>,
        stationIds: Set<String>,
    ): List<STASYAnnouncement> {
        val lines = lineIds.asSequence()
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        val stations = stationIds.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        if (lines.isEmpty() && stations.isEmpty()) return emptyList()
        return announcements.asSequence()
            .filter { it.isServiceAlert }
            .filter { ann ->
                ann.affectedLines.any { it.trim().uppercase() in lines } ||
                    ann.affectedStationIds.any { it.trim() in stations }
            }
            // sortedByDescending is stable, so equal-severity alerts keep the
            // order the feed delivered them in.
            .sortedByDescending { AlertSeverity.fromRaw(it.severity).rank }
            .toList()
    }
}
