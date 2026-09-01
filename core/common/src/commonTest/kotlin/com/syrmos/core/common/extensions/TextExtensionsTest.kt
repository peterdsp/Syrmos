package com.syrmos.core.common.extensions

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the shared search normalizer used by every station-search predicate
 * (audit #17). It must lowercase and fold Greek tonos/dialytika, final sigma,
 * and the common Latin diacritics, while leaving spaces and plain ASCII intact.
 */
class TextExtensionsTest {

    @Test
    fun foldsGreekTonos() {
        assertEquals("αττικη", "Αττική".normalizeForSearch())
        assertEquals("αγια μαρινα", "Αγία Μαρίνα".normalizeForSearch())
        assertEquals("μοναστηρακι", "Μοναστηράκι".normalizeForSearch())
    }

    @Test
    fun foldsFinalSigma() {
        assertEquals("οδοσ", "Οδός".normalizeForSearch())
        assertEquals("λεωφοροσ", "Λεωφόρος".normalizeForSearch())
    }

    @Test
    fun foldsDialytika() {
        // ϊ/ΐ -> ι, ϋ/ΰ -> υ
        assertEquals("μαι", "μαΐ".normalizeForSearch())
        assertEquals("υ", "ΰ".normalizeForSearch())
    }

    @Test
    fun foldsLatinDiacritics() {
        assertEquals("dhespoti", "Dhëspoti".normalizeForSearch())
        assertEquals("cafe", "Café".normalizeForSearch())
        assertEquals("aegais", "Aegaís".normalizeForSearch())
    }

    @Test
    fun leavesPlainAsciiAndSpacesIntact() {
        assertEquals("syntagma square", "Syntagma Square".normalizeForSearch())
        assertEquals("m1_att", "M1_ATT".normalizeForSearch())
    }

    @Test
    fun isIdempotent() {
        val once = "Αγία Παρασκευή".normalizeForSearch()
        assertEquals(once, once.normalizeForSearch())
    }
}
