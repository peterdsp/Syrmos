package com.syrmos.core.common.extensions

/**
 * Uppercase for display labels. Greek typography drops the tonos on capitals
 * ("Έτοιμος" reads "ΕΤΟΙΜΟΣ", not "ΈΤΟΙΜΟΣ"), and the platform `uppercase()`
 * keeps it; this maps the accented capitals and the dialytika-tonos forms to
 * their plain capitals after uppercasing. Other scripts pass through
 * unchanged. Shared with iOS (`String.displayUppercased()`).
 */
fun String.displayUppercase(): String {
    val upper = uppercase()
    if (upper.none { it in GREEK_ACCENTED }) return upper
    return buildString(upper.length) {
        for (ch in upper) append(GREEK_PLAIN[ch] ?: ch)
    }
}

private val GREEK_PLAIN: Map<Char, Char> = mapOf(
    'Ά' to 'Α', 'Έ' to 'Ε', 'Ή' to 'Η', 'Ί' to 'Ι', 'Ό' to 'Ο', 'Ύ' to 'Υ', 'Ώ' to 'Ω',
    'Ϊ' to 'Ι', 'Ϋ' to 'Υ',
    // Lowercase forms that a platform uppercase may leave in place.
    'ΐ' to 'Ι', 'ΰ' to 'Υ',
)
private val GREEK_ACCENTED: Set<Char> = GREEK_PLAIN.keys
