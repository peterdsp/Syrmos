import sys
import time
import types
import unittest
from unittest.mock import patch

from syrmos_admin import translation
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


class TranslationRateLimitTest(unittest.TestCase):
    """The free providers answer a burst with TooManyRequests, and the old code
    turned that straight into an empty translation. Every announcement in the
    live feed was blank for exactly this reason."""

    def setUp(self):
        self.slept = []
        translation._sleep = self.slept.append
        translation._last_call_at = 0.0
        self.addCleanup(setattr, translation, "_sleep", time.sleep)

    @staticmethod
    def _module(translator):
        return types.SimpleNamespace(
            GoogleTranslator=translator, MyMemoryTranslator=translator
        )

    def test_a_rate_limited_provider_is_retried_and_can_still_succeed(self):
        class RateLimitedTwice:
            calls = 0

            def __init__(self, **_kwargs):
                pass

            def translate(self, _text):
                RateLimitedTwice.calls += 1
                if RateLimitedTwice.calls <= 2:
                    raise RuntimeError("Server Error: You made too many requests to the server.")
                return "Traffic changes"

        with patch.dict(sys.modules, {"deep_translator": self._module(RateLimitedTwice)}):
            self.assertEqual(translate_from_greek("Κυκλοφοριακές ρυθμίσεις", "en"), "Traffic changes")
        self.assertEqual(RateLimitedTwice.calls, 3, "retried until it got an answer")
        self.assertTrue(any(s >= 1.0 for s in self.slept), "backed off between attempts")

    def test_a_provider_that_keeps_rate_limiting_gives_up_without_inventing_text(self):
        class AlwaysRateLimited:
            calls = 0

            def __init__(self, **_kwargs):
                pass

            def translate(self, _text):
                AlwaysRateLimited.calls += 1
                raise RuntimeError("TooManyRequests: 429")

        with patch.dict(sys.modules, {"deep_translator": self._module(AlwaysRateLimited)}):
            self.assertEqual(translate_from_greek("Κυκλοφοριακές ρυθμίσεις", "en"), "")
        # Both providers are tried, each up to the attempt cap, and nothing is invented.
        self.assertEqual(AlwaysRateLimited.calls, 2 * translation._MAX_ATTEMPTS)

    def test_a_real_failure_is_not_retried(self):
        class Broken:
            calls = 0

            def __init__(self, **_kwargs):
                pass

            def translate(self, _text):
                Broken.calls += 1
                raise ValueError("invalid target language")

        with patch.dict(sys.modules, {"deep_translator": self._module(Broken)}):
            self.assertEqual(translate_from_greek("Κυκλοφοριακές ρυθμίσεις", "en"), "")
        self.assertEqual(Broken.calls, 2, "one attempt per provider, no pointless backoff")

    def test_calls_are_paced_under_the_documented_limit(self):
        class Fine:
            def __init__(self, **_kwargs):
                pass

            def translate(self, _text):
                return "Traffic changes"

        with patch.dict(sys.modules, {"deep_translator": self._module(Fine)}):
            translate_from_greek("Κυκλοφοριακές ρυθμίσεις", "en")
            translate_from_greek("Κυκλοφοριακές ρυθμίσεις", "sq")
        self.assertTrue(self.slept, "second call waited rather than firing immediately")
        self.assertTrue(all(s <= translation._MIN_INTERVAL_SECONDS for s in self.slept))


if __name__ == "__main__":
    unittest.main()
