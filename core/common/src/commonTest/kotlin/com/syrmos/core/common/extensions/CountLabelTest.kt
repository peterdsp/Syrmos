package com.syrmos.core.common.extensions

import com.syrmos.core.common.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

class CountLabelTest {
    private fun reports(count: Int, lang: AppLanguage) = countLabel(
        count, lang,
        en = "report" to "reports",
        el = "αναφορά" to "αναφορές",
        sq = "raport" to "raporte",
        it = "segnalazione" to "segnalazioni",
    )

    @Test
    fun one_takes_the_singular_in_every_language() {
        assertEquals("1 report", reports(1, AppLanguage.ENGLISH))
        assertEquals("1 αναφορά", reports(1, AppLanguage.GREEK))
        assertEquals("1 raport", reports(1, AppLanguage.ALBANIAN))
        assertEquals("1 segnalazione", reports(1, AppLanguage.ITALIAN))
    }

    @Test
    fun many_and_zero_take_the_plural() {
        assertEquals("3 reports", reports(3, AppLanguage.ENGLISH))
        assertEquals("0 αναφορές", reports(0, AppLanguage.GREEK))
        assertEquals("12 raporte", reports(12, AppLanguage.ALBANIAN))
        assertEquals("2 segnalazioni", reports(2, AppLanguage.ITALIAN))
    }
}
