package com.syrmos.core.domain.station

import com.syrmos.core.common.AppLanguage
import com.syrmos.core.model.transit.Region

/**
 * What to tell a rider when the live departures call fails at a station outside
 * Athens, and where to send them instead.
 *
 * Athens runs off the bundled seed, so an empty board there genuinely means the
 * line is closed or today's service has ended. Everywhere else the board comes
 * from the network, and a failed call used to render the same "the line is
 * closed" sentence: a Thessaloniki rider with one bar was told their line had
 * shut down. Distinguish the two, and offer the operator's own timetable, which
 * is the only thing that still helps when our API is unreachable.
 *
 * iOS does this in `StationDetailView.externalTimetableURL`; this is the shared,
 * testable version of the same rule.
 */
object StationDepartureFallback {

    /**
     * The region a station belongs to, from the lines serving it. Mixed regions
     * resolve to [Region.ATHENS], matching iOS: a hub served by both an Athens
     * metro line and a national service is an Athens hub for this purpose.
     */
    fun regionOf(lineRegions: List<Region>): Region {
        if (lineRegions.isEmpty()) return Region.ATHENS
        val unique = lineRegions.toSet()
        return if (unique.size == 1) unique.first() else Region.ATHENS
    }

    /** True when a failed departures call deserves its own message and a link. */
    fun showsFallback(region: Region): Boolean = region != Region.ATHENS

    /** The operator whose timetable still works when ours does not. */
    fun operatorName(region: Region): String? = when (region) {
        Region.THESSALONIKI -> "OASTH"
        Region.NATIONAL, Region.PATRAS -> "Hellenic Train"
        Region.ATHENS -> null
    }

    /** That operator's public timetable page. */
    fun operatorUrl(region: Region): String? = when (region) {
        Region.THESSALONIKI -> "https://www.oasth.gr"
        Region.NATIONAL, Region.PATRAS -> "https://www.hellenictrain.gr"
        Region.ATHENS -> null
    }

    /** "Could not load departures. Check your connection." */
    fun couldNotLoad(lang: AppLanguage): String = when (lang) {
        AppLanguage.GREEK -> "Δεν ήταν δυνατή η φόρτωση δρομολογίων. Έλεγξε τη σύνδεσή σου."
        AppLanguage.ALBANIAN -> "Nuk u arritën të ngarkoheshin oraret. Kontrollo lidhjen."
        AppLanguage.ITALIAN -> "Impossibile caricare le partenze. Controlla la connessione."
        else -> "Could not load departures. Check your connection."
    }

    /** "Check timetables on <operator>", or null in Athens. */
    fun checkTimetablesOn(region: Region, lang: AppLanguage): String? {
        val operator = operatorName(region) ?: return null
        return when (lang) {
            AppLanguage.GREEK -> "Δες δρομολόγια στο $operator"
            AppLanguage.ALBANIAN -> "Shiko oraret në $operator"
            AppLanguage.ITALIAN -> "Consulta gli orari su $operator"
            else -> "Check timetables on $operator"
        }
    }
}
