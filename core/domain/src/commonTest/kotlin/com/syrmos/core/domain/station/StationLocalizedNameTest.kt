package com.syrmos.core.domain.station

import com.syrmos.core.common.AppLanguage
import com.syrmos.core.model.transit.Station
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The seed carries a Latin `name` beside `name_el` / `name_sq`, so any screen
 * rendering `station.name` shows "Nikaia" to a Greek rider. These lock the
 * per-language resolution and its fallbacks.
 */
class StationLocalizedNameTest {

    private fun station(
        name: String = "Nikaia",
        nameEl: String = "Νίκαια",
        nameSq: String? = "Nikea",
    ) = Station(
        id = "M3_NIK",
        name = name,
        nameEl = nameEl,
        nameSq = nameSq,
        latitude = 37.9660813,
        longitude = 23.6478426,
        lineIds = listOf("M3"),
    )

    @Test
    fun greek_uses_the_greek_name() {
        assertEquals("Νίκαια", station().localizedName(AppLanguage.GREEK))
    }

    @Test
    fun english_uses_the_latin_name() {
        assertEquals("Nikaia", station().localizedName(AppLanguage.ENGLISH))
    }

    @Test
    fun albanian_uses_the_albanian_name() {
        assertEquals("Nikea", station().localizedName(AppLanguage.ALBANIAN))
    }

    @Test
    fun italian_falls_back_to_latin_because_the_seed_has_no_italian_names() {
        assertEquals("Nikaia", station().localizedName(AppLanguage.ITALIAN))
    }

    @Test
    fun blank_greek_name_falls_back_to_latin_instead_of_rendering_empty() {
        assertEquals("Nikaia", station(nameEl = "").localizedName(AppLanguage.GREEK))
    }

    @Test
    fun missing_albanian_name_falls_back_to_latin() {
        assertEquals("Nikaia", station(nameSq = null).localizedName(AppLanguage.ALBANIAN))
        assertEquals("Nikaia", station(nameSq = "").localizedName(AppLanguage.ALBANIAN))
    }
}
