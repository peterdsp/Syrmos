package com.syrmos.core.common.extensions

import kotlin.test.Test
import kotlin.test.assertEquals

/** Twin of the iOS `DisplayCaseTests`. */
class DisplayCaseTest {
    @Test
    fun greekCapitalsDropTheTonos() {
        assertEquals("ΕΤΟΙΜΟΣ ΓΙΑ ΕΠΙΒΙΒΑΣΗ", "Έτοιμος για επιβίβαση".displayUppercase())
        assertEquals("ΣΕ ΚΙΝΗΣΗ", "Σε κίνηση".displayUppercase())
        assertEquals("ΑΠΟΒΙΒΑΣΗ ΣΥΝΤΟΜΑ", "Αποβίβαση σύντομα".displayUppercase())
    }

    @Test
    fun otherScriptsUppercaseAsUsual() {
        assertEquals("READY TO BOARD", "Ready to board".displayUppercase())
        assertEquals("GATI PËR TË HIPUR", "Gati për të hipur".displayUppercase())
        assertEquals("PRONTO A SALIRE", "Pronto a salire".displayUppercase())
    }
}
