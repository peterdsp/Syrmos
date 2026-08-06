import sys
import types
import unittest
from unittest.mock import patch

from syrmos_admin.translation import translate_from_greek


class TranslationValidationTest(unittest.TestCase):
    def test_unchanged_greek_uses_second_provider(self):
        class UnchangedGoogleTranslator:
            def __init__(self, **_kwargs):
                pass

            def translate(self, text):
                return text

        class AlbanianFallbackTranslator:
            def __init__(self, **_kwargs):
                pass

            def translate(self, _text):
                return "Njoftim për ndryshim shërbimi"

        fake_module = types.SimpleNamespace(
            GoogleTranslator=UnchangedGoogleTranslator,
            MyMemoryTranslator=AlbanianFallbackTranslator,
        )
        with patch.dict(sys.modules, {"deep_translator": fake_module}):
            self.assertEqual(
                translate_from_greek("Αλλαγή υπηρεσίας", "sq"),
                "Njoftim për ndryshim shërbimi",
            )

    def test_all_invalid_results_return_empty_translation(self):
        class InvalidTranslator:
            def __init__(self, **_kwargs):
                pass

            def translate(self, text):
                return text

        fake_module = types.SimpleNamespace(
            GoogleTranslator=InvalidTranslator,
            MyMemoryTranslator=InvalidTranslator,
        )
        with patch.dict(sys.modules, {"deep_translator": fake_module}):
            self.assertEqual(translate_from_greek("Αλλαγή υπηρεσίας", "en"), "")
            self.assertEqual(translate_from_greek("Αλλαγή υπηρεσίας", "sq"), "")

    def test_non_greek_source_is_preserved(self):
        self.assertEqual(
            translate_from_greek("Service change", "en"),
            "Service change",
        )


if __name__ == "__main__":
    unittest.main()
