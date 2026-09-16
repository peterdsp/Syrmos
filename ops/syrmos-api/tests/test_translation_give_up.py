"""A provider that is down stays skipped for the rest of the run.

Without this, a scrape pays the whole attempt-and-backoff ladder for every item
in every language. The first deploy of the backoff code turned a scraper oneshot
into a ten-minute job because both free providers were refusing and each of ~40
strings walked the ladder twice.
"""

import asyncio
import sys
import time
import types
import unittest
from unittest.mock import patch

from syrmos_admin import translation
from syrmos_admin.ariadne_providers import ProviderResult, breaker_reset_all
from syrmos_admin.translation import translate_from_greek

GREEK = ["Κυκλοφοριακές ρυθμίσεις", "Στάση εργασίας", "Νέο δρομολόγιο", "Αλλαγή γραμμής"]


class GiveUpTest(unittest.TestCase):
    def setUp(self):
        breaker_reset_all()
        translation.reset_run_state()
        self.slept = []
        translation._sleep = self.slept.append
        translation._last_call_at = 0.0
        self.addCleanup(setattr, translation, "_sleep", time.sleep)
        self.addCleanup(translation.reset_run_state)

    @staticmethod
    def _module(translator):
        return types.SimpleNamespace(
            GoogleTranslator=translator, MyMemoryTranslator=translator
        )

    def test_a_dead_free_provider_stops_costing_requests_and_sleeps(self):
        class AlwaysRateLimited:
            calls = 0

            def __init__(self, **_kwargs):
                pass

            def translate(self, _text):
                AlwaysRateLimited.calls += 1
                raise RuntimeError("TooManyRequests: 429")

        with patch.dict(sys.modules, {"deep_translator": self._module(AlwaysRateLimited)}):
            for text in GREEK:
                self.assertEqual(translate_from_greek(text, "en"), "")

        # Both providers give up after GIVE_UP_AFTER strings, so the cost is
        # bounded by the provider count, not by the size of the feed.
        ceiling = 2 * translation.GIVE_UP_AFTER * translation._MAX_ATTEMPTS
        self.assertLessEqual(AlwaysRateLimited.calls, ceiling)
        self.assertLess(
            AlwaysRateLimited.calls,
            len(GREEK) * 2 * translation._MAX_ATTEMPTS,
            "a longer feed must not cost proportionally more requests",
        )

    def test_nothing_sleeps_once_every_provider_has_given_up(self):
        class AlwaysRateLimited:
            def __init__(self, **_kwargs):
                pass

            def translate(self, _text):
                raise RuntimeError("TooManyRequests: 429")

        with patch.dict(sys.modules, {"deep_translator": self._module(AlwaysRateLimited)}):
            for text in GREEK[:2]:
                translate_from_greek(text, "en")
            before = len(self.slept)
            for text in GREEK[2:]:
                translate_from_greek(text, "en")

        self.assertEqual(len(self.slept), before, "skipped providers wait for nothing")

    def test_a_healthy_provider_is_never_given_up_on(self):
        class Fine:
            calls = 0

            def __init__(self, **_kwargs):
                pass

            def translate(self, _text):
                Fine.calls += 1
                return "Traffic changes"

        with patch.dict(sys.modules, {"deep_translator": self._module(Fine)}):
            for text in GREEK:
                self.assertEqual(translate_from_greek(text, "en"), "Traffic changes")
        self.assertEqual(Fine.calls, len(GREEK))

    def test_an_intermittent_provider_recovers_its_budget_on_success(self):
        class FlakyOnce:
            calls = 0

            def __init__(self, **_kwargs):
                pass

            def translate(self, _text):
                FlakyOnce.calls += 1
                # Fail the very first string outright, then work.
                if FlakyOnce.calls <= translation._MAX_ATTEMPTS:
                    raise RuntimeError("TooManyRequests: 429")
                return "Traffic changes"

        with patch.dict(sys.modules, {"deep_translator": self._module(FlakyOnce)}):
            translate_from_greek(GREEK[0], "en")
            for text in GREEK[1:]:
                self.assertEqual(translate_from_greek(text, "en"), "Traffic changes")
        self.assertFalse(translation._is_dead("google"), "one bad string is not an outage")


class FakeProvider:
    def __init__(self, name, result):
        self.name = name
        self.result = result
        self.calls = 0

    async def complete(self, _client, _system, _messages, timeout_override=None):
        self.calls += 1
        return self.result


class AriadneGiveUpTest(unittest.TestCase):
    def setUp(self):
        breaker_reset_all()
        translation.reset_run_state()
        translation.USE_ARIADNE = True
        translation._loop = asyncio.new_event_loop()
        translation._http_client = object()
        self._no_fallback = patch.object(
            translation, "_translate_via_deep_translator", lambda *_: ""
        )
        self._no_fallback.start()
        self.addCleanup(self._no_fallback.stop)
        self.addCleanup(self._close)

    def _close(self):
        if translation._loop is not None:
            translation._loop.close()
        translation._loop = None
        translation._http_client = None
        translation._chain_cache = None
        translation.reset_run_state()

    def test_a_misconfigured_provider_is_dropped_after_one_string(self):
        # Exactly the live Groq case: the key works but every model is blocked at
        # the organization level, so a 403 arrives for every string.
        blocked = FakeProvider(
            "groq",
            ProviderResult("groq", "m", False, status=403, error_kind="config"),
        )
        with patch.object(translation, "_get_chain", lambda: [blocked]):
            for text in GREEK:
                self.assertEqual(translate_from_greek(text, "en"), "")
        self.assertEqual(blocked.calls, 1, "asked once, then never again this run")

    def test_a_transient_failure_is_retried_across_a_few_strings_then_capped(self):
        flaky = FakeProvider(
            "cloudflare",
            ProviderResult("cloudflare", "m", False, status=500, error_kind="server"),
        )
        with patch.object(translation, "_get_chain", lambda: [flaky]):
            for text in GREEK:
                translate_from_greek(text, "en")
        # Pinned to a number, not to the setting, so raising the setting cannot
        # quietly reintroduce the per-string cost this test exists to prevent.
        self.assertLessEqual(flaky.calls, 3, "a repeatedly failing provider is dropped")
        self.assertGreater(flaky.calls, 1, "a server blip gets more than one chance")


if __name__ == "__main__":
    unittest.main()
