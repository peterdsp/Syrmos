package com.syrmos.core.common.extensions

import com.syrmos.core.common.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

class IchnosScopeLabelTest {
    @Test
    fun a_station_label_from_another_language_is_read_in_the_readers_language() {
        assertEquals("Ichnos në Florina", localizedScopeLabel("Ichnos at Florina", AppLanguage.ALBANIAN))
        assertEquals("Ichnos στο Florina", localizedScopeLabel("Ichnos në Florina", AppLanguage.GREEK))
        assertEquals("Ichnos a Florina", localizedScopeLabel("Ichnos στο Florina", AppLanguage.ITALIAN))
        assertEquals("Ichnos at Florina", localizedScopeLabel("Ichnos a Florina", AppLanguage.ENGLISH))
    }

    @Test
    fun a_label_already_in_the_readers_language_is_unchanged() {
        assertEquals("Ichnos at 1st Ag. Kosma", localizedScopeLabel("Ichnos at 1st Ag. Kosma", AppLanguage.ENGLISH))
    }

    @Test
    fun line_and_train_contexts_are_left_alone() {
        assertEquals("Kallithea to Monastiraki", localizedScopeLabel("Kallithea to Monastiraki", AppLanguage.GREEK))
        assertEquals("Train 1635", localizedScopeLabel("Train 1635", AppLanguage.ALBANIAN))
    }

    @Test
    fun a_resolvable_station_id_rebuilds_the_whole_label_in_the_readers_language() {
        // A Greek reporter submitted "Ichnos στο Καλλιθέα"; a resolvable id lets an
        // English reader see the English station name, not the Greek one.
        val names = mapOf("M1_KAL" to "Kallithea").let { m -> { id: String -> m[id] } }
        assertEquals(
            "Ichnos at Kallithea",
            resolveIchnosScopeLabel("M1_KAL", "Ichnos στο Καλλιθέα", AppLanguage.ENGLISH, names),
        )
        assertEquals(
            "Ichnos στο Kallithea",
            resolveIchnosScopeLabel("M1_KAL", "Ichnos at Kallithea", AppLanguage.GREEK) { "Kallithea" },
        )
    }

    @Test
    fun an_unresolvable_id_falls_back_to_prefix_relocalization() {
        // A hashed name id or a train/line scope resolves to null; the prefix is
        // still re-localized so the reader is not left with the reporter's prefix.
        assertEquals(
            "Ichnos në Florina",
            resolveIchnosScopeLabel("h_9f2c", "Ichnos at Florina", AppLanguage.ALBANIAN) { null },
        )
        assertEquals(
            "Train 1635",
            resolveIchnosScopeLabel("train_1635", "Train 1635", AppLanguage.ITALIAN) { null },
        )
    }
}
