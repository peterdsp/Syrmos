package com.syrmos.core.common

import kotlin.test.Test
import kotlin.test.assertFalse

class LocalizationCatalogTest {
    @Test
    fun everySharedStringHasContentInEverySupportedLanguage() {
        AppLanguage.entries.forEach { language ->
            L.entries.forEach { key ->
                assertFalse(
                    key.text(language).isBlank(),
                    "Missing ${language.code} translation for ${key.name}",
                )
            }
        }
    }
}
