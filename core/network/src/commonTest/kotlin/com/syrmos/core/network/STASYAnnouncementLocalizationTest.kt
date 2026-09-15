package com.syrmos.core.network

import com.syrmos.core.common.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The announcement feed publishes a Greek `title` and, in practice, empty
 * `titleEn`/`titleSq`/`titleIt` for every item. These tests pin the resulting
 * rule: show the best language we have, and only fall back to a generic label
 * when the operator published no title at all.
 */
class STASYAnnouncementLocalizationTest {
    private val untranslated = STASYAnnouncement(
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
    fun anUntranslatedAlertKeepsItsRealNameInsteadOfAGenericLabel() {
        // A Greek-only echo in titleEn/titleSq is not a translation, so both fall
        // through to the operator's own wording. Naming the alert beats "Service
        // alert", which identified nothing and made every card look identical.
        assertEquals("Κυκλοφοριακές ρυθμίσεις", untranslated.localizedTitle(AppLanguage.ENGLISH))
        assertEquals("Κυκλοφοριακές ρυθμίσεις", untranslated.localizedTitle(AppLanguage.ALBANIAN))
        assertEquals("Επίσημη ανακοίνωση", untranslated.localizedSummary(AppLanguage.ENGLISH))
    }

    @Test
    fun validItalianAndNativeGreekRemainAvailable() {
        assertEquals("Modifiche al servizio", untranslated.localizedTitle(AppLanguage.ITALIAN))
        assertEquals("Κυκλοφοριακές ρυθμίσεις", untranslated.localizedTitle(AppLanguage.GREEK))
    }

    @Test
    fun aRealTranslationStillWinsOverTheSource() {
        val translated = untranslated.copy(titleEn = "Traffic changes", titleSq = "Ndryshime trafiku")
        assertEquals("Traffic changes", translated.localizedTitle(AppLanguage.ENGLISH))
        assertEquals("Ndryshime trafiku", translated.localizedTitle(AppLanguage.ALBANIAN))
    }

    @Test
    fun albanianAndItalianBorrowEnglishBeforeTheGreekSource() {
        val englishOnly = untranslated.copy(titleEn = "Traffic changes", titleIt = "")
        assertEquals("Traffic changes", englishOnly.localizedTitle(AppLanguage.ALBANIAN))
        assertEquals("Traffic changes", englishOnly.localizedTitle(AppLanguage.ITALIAN))
    }

    @Test
    fun theGenericLabelSurvivesForAnAnnouncementWithNoTitleAtAll() {
        val empty = untranslated.copy(title = "", titleEn = "", titleSq = "", titleIt = "")
        assertEquals("Service alert", empty.localizedTitle(AppLanguage.ENGLISH))
        assertEquals("Njoftim për shërbimin", empty.localizedTitle(AppLanguage.ALBANIAN))
        assertEquals("Avviso sul servizio", empty.localizedTitle(AppLanguage.ITALIAN))

        val news = empty.copy(isServiceAlert = false)
        assertEquals("Rail announcement", news.localizedTitle(AppLanguage.ENGLISH))
    }

    @Test
    fun serviceStatusFallsBackToTheOperatorWordingToo() {
        val status = STASYServiceStatus(
            status = "alert",
            rawMessage = "Στάση εργασίας",
            rawMessageEn = "",
            rawMessageSq = "",
            rawMessageIt = "",
            serviceUntil = null,
        )
        assertEquals("Στάση εργασίας", status.localizedMessage(AppLanguage.ENGLISH))
        assertEquals("Στάση εργασίας", status.localizedMessage(AppLanguage.ITALIAN))
    }
}
