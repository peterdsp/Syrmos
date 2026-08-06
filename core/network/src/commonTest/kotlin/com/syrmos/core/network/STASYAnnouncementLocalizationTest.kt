package com.syrmos.core.network

import com.syrmos.core.common.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

class STASYAnnouncementLocalizationTest {
    private val announcement = STASYAnnouncement(
        id = "bad-translations",
        title = "Κυκλοφοριακές ρυθμίσεις",
        titleEn = "Κυκλοφοριακές ρυθμίσεις",
        titleSq = "Κυκλοφοριακές ρυθμίσεις",
        titleIt = "Modifiche al servizio",
        date = "",
        summary = "Επίσημη ανακοίνωση",
        summaryEn = "Επίσημη ανακοίνωση",
        summarySq = "Επίσημη ανακοίνωση",
        summaryIt = "Avviso ufficiale",
        url = "https://example.com",
        isServiceAlert = true,
    )

    @Test
    fun greekNeverLeaksIntoEnglishOrAlbanian() {
        assertEquals("Service alert", announcement.localizedTitle(AppLanguage.ENGLISH))
        assertEquals("Njoftim për shërbimin", announcement.localizedTitle(AppLanguage.ALBANIAN))
    }

    @Test
    fun validItalianAndNativeGreekRemainAvailable() {
        assertEquals("Modifiche al servizio", announcement.localizedTitle(AppLanguage.ITALIAN))
        assertEquals("Κυκλοφοριακές ρυθμίσεις", announcement.localizedTitle(AppLanguage.GREEK))
    }
}
