package com.syrmos.core.common

import kotlin.test.Test
import kotlin.test.assertEquals

class ClosureCopyTest {
    private val t0 = 1_793_167_200L // 2026-10-28 08:00 Athens

    @Test
    fun windowReadsInAthensTime() {
        assertEquals("08:00 to 16:00", ClosureCopy.window(t0, t0 + 8 * 3600, t0, AppLanguage.ENGLISH))
        assertEquals("08:00 έως 16:00", ClosureCopy.window(t0, t0 + 8 * 3600, t0, AppLanguage.GREEK))
        assertEquals("From 08:00 until further notice", ClosureCopy.window(t0, null, t0, AppLanguage.ENGLISH))
        assertEquals("Από 08:00 έως νεωτέρας", ClosureCopy.window(t0, null, t0, AppLanguage.GREEK))
        // Another Athens day carries the date.
        assertEquals("Closes at 28/10 08:00", ClosureCopy.upcoming(t0, t0 - 12 * 3600, AppLanguage.ENGLISH))
    }

    @Test
    fun replacementBusLine() {
        val bus = ReplacementBus(
            label = LocalizedText(el = "Έκτακτο λεωφορείο", en = "Replacement bus"),
            fromStop = LocalizedText(el = "Σύνταγμα (Αμαλίας)", en = "Syntagma (Amalias)"),
            toStop = LocalizedText(el = "Ευαγγελισμός", en = "Evangelismos"),
            headwayMinutes = 10,
        )
        assertEquals("Έκτακτο λεωφορείο κάθε 10', από Σύνταγμα (Αμαλίας) προς Ευαγγελισμός", ClosureCopy.replacementBus(bus, AppLanguage.GREEK))
        assertEquals("Replacement bus every 10', from Syntagma (Amalias) to Evangelismos", ClosureCopy.replacementBus(bus, AppLanguage.ENGLISH))
    }

    @Test
    fun everyModeHasAShortChipInEveryLanguage() {
        for (mode in ClosureMode.entries) for (lang in AppLanguage.entries) {
            val chip = ClosureCopy.modeChip(mode, lang)
            assertEquals(true, chip.isNotBlank() && chip.length <= 14, "$mode $lang '$chip'")
        }
    }

    @Test
    fun noDeparturesNote_inEveryLanguage() {
        assertEquals("Καμία αναχώρηση λόγω κλεισίματος", ClosureCopy.noDeparturesClosed(AppLanguage.GREEK))
        for (lang in AppLanguage.entries) assertEquals(true, ClosureCopy.noDeparturesClosed(lang).isNotBlank())
    }
}
