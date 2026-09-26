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
}
