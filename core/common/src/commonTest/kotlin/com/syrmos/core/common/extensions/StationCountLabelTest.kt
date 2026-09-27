package com.syrmos.core.common.extensions

import com.syrmos.core.common.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StationCountLabelTest {
    @Test
    fun the_real_count_is_written_into_every_language() {
        assertEquals("Browse all 394 stations", browseAllStationsLabel(394, AppLanguage.ENGLISH))
        assertEquals("Περιήγηση σε όλους τους 394 σταθμούς", browseAllStationsLabel(394, AppLanguage.GREEK))
        assertEquals("Shfleto të gjitha 394 stacionet", browseAllStationsLabel(394, AppLanguage.ALBANIAN))
        assertEquals("Esplora tutte le 394 stazioni", browseAllStationsLabel(394, AppLanguage.ITALIAN))
    }

    @Test
    fun before_the_stations_load_the_number_is_dropped_not_shown_as_zero() {
        assertEquals("Browse all stations", browseAllStationsLabel(0, AppLanguage.ENGLISH))
        for (lang in AppLanguage.values()) {
            assertFalse(browseAllStationsLabel(0, lang).contains("0"), lang.name)
            assertFalse(browseAllStationsLabel(0, lang).contains("{n}"), lang.name)
        }
    }
}
