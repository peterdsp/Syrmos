package com.syrmos.core.common.map

/**
 * Pure helpers for turning an ordered stop list into a drawable route polyline.
 * Kept in commonMain so every client renders bus routes identically and the
 * behaviour is unit-testable without a map engine.
 */
object RouteGeometry {

    /**
     * A route is a loop when it starts and ends at the same terminal (the
     * Patras University bus PU1: both terminals are Kastelokampos). Compared
     * case-insensitively and trimmed; blank terminals are never a loop.
     */
    fun isLoopTerminals(terminalA: String, terminalB: String): Boolean {
        val a = terminalA.trim()
        val b = terminalB.trim()
        return a.isNotEmpty() && a.equals(b, ignoreCase = true)
    }

    /**
     * Close a loop route's polyline by returning to its first point. A loop
     * drawn straight through its ordered stops otherwise renders as an open arc
     * with a visible gap on the return leg (PU1 left roughly a kilometre
     * undrawn). Appends the first point only when [isLoop] and the ends are not
     * already coincident, so a non-loop route and an already-closed one are
     * returned unchanged. This is done in geometry, not in seed data, because
     * station_line_entity is keyed by (station_id, line_id) and a repeated stop
     * id would collide on that primary key.
     */
    fun closeLoop(points: List<LatLng>, isLoop: Boolean): List<LatLng> {
        if (!isLoop || points.size < 2) return points
        return if (points.first() == points.last()) points else points + points.first()
    }
}
