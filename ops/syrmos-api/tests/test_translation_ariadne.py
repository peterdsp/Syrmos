"""Translation through the Ariadne provider chain.

The free deep-translator endpoints rate limit persistently, so the keyed chain
the assistant already uses is the primary backend and the free endpoints are the
fallback. A language model will happily answer a title with a paragraph, so what
comes back has to be reduced to a translation or rejected.
"""

import asyncio
import unittest
from unittest.mock import patch

from syrmos_admin import translation
from syrmos_admin.ariadne_providers import ProviderResult, breaker_reset_all
from syrmos_admin.translation import translate_from_greek

GREEK = "Κυκλοφοριακές ρυθμίσεις στη γραμμή M3"


class FakeProvider:
    """Stands in for an OpenAICompatibleProvider without touching the network."""

    def __init__(self, name, replies):
        self.name = name
        self.replies = list(replies)
        self.calls = []

    async def complete(self, _client, system_text, messages, timeout_override=None):
        self.calls.append((system_text, messages[0]["content"]))
        reply = self.replies.pop(0) if self.replies else ""
        if isinstance(reply, ProviderResult):
            return reply
        return ProviderResult(self.name, "fake-model", True, text=reply, status=200)


def failed(name, kind="server", status=500):
    return ProviderResult(name, "fake-model", False, status=status, error_kind=kind)


class AriadneTranslationTest(unittest.TestCase):
    def setUp(self):
        breaker_reset_all()
        translation._memo.clear()
        # conftest disables the backend for every test; this suite is the one
        # that exercises it, against fake providers that never open a socket.
        translation.USE_ARIADNE = True
        translation._chain_cache = None
        # A runner that never opens a socket: the fake providers ignore the client.
        translation._loop = asyncio.new_event_loop()
        translation._http_client = object()
        # The free fallback is off by default here so no test reaches the network;
        # the two tests that are about the fallback re-patch it themselves.
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
        translation._memo.clear()

    def _with_chain(self, *providers):
        translation._chain_cache = list(providers)
        return patch.object(translation, "_get_chain", lambda: list(providers))

    def test_the_first_provider_that_answers_is_used(self):
        groq = FakeProvider("groq", ["Traffic changes on line M3"])
        cloudflare = FakeProvider("cloudflare", ["should not be reached"])
        with self._with_chain(groq, cloudflare):
            self.assertEqual(translate_from_greek(GREEK, "en"), "Traffic changes on line M3")
        self.assertEqual(len(groq.calls), 1)
        self.assertEqual(cloudflare.calls, [], "the chain stops at the first usable answer")

    def test_a_failing_provider_falls_through_to_the_next(self):
        groq = FakeProvider("groq", [failed("groq", "rate_limit", 429)])
        cloudflare = FakeProvider("cloudflare", ["Traffic changes on line M3"])
        with self._with_chain(groq, cloudflare):
            self.assertEqual(translate_from_greek(GREEK, "en"), "Traffic changes on line M3")
        self.assertEqual(len(cloudflare.calls), 1)

    def test_the_target_language_reaches_the_model(self):
        groq = FakeProvider("groq", ["Ndryshime trafiku"])
        with self._with_chain(groq):
            translate_from_greek(GREEK, "sq")
        system_text, user_text = groq.calls[0]
        self.assertIn("Albanian", system_text)
        self.assertEqual(user_text, GREEK)

    def test_identical_text_is_translated_once(self):
        groq = FakeProvider("groq", ["Traffic changes", "second call"])
        with self._with_chain(groq):
            first = translate_from_greek(GREEK, "en")
            second = translate_from_greek(GREEK, "en")
        self.assertEqual(first, second)
        self.assertEqual(len(groq.calls), 1, "a repeated string costs one request, not two")

    def test_a_chatty_or_refusing_model_is_rejected_rather_than_stored(self):
        for reply in (
            "NO_TRANSLATION",
            "Κυκλοφοριακές ρυθμίσεις",  # echoed the Greek back
            "Sure! Here is a detailed explanation of what this Greek announcement "
            "means for passengers travelling on the Athens metro network today, "
            "including some background you did not ask for at all.",
            "",
        ):
            with self.subTest(reply=reply[:30]):
                translation._memo.clear()
                groq = FakeProvider("groq", [reply])
                with self._with_chain(groq):
                    self.assertEqual(translate_from_greek(GREEK, "en"), "")

    def test_wrapping_and_labels_are_stripped(self):
        for reply, expected in (
            ('"Traffic changes"', "Traffic changes"),
            ("Translation: Traffic changes", "Traffic changes"),
            ("«Traffic changes»", "Traffic changes"),
        ):
            with self.subTest(reply=reply):
                translation._memo.clear()
                groq = FakeProvider("groq", [reply])
                with self._with_chain(groq):
                    self.assertEqual(translate_from_greek(GREEK, "en"), "Traffic changes")

    def test_an_unconfigured_chain_costs_nothing_and_defers_to_the_fallback(self):
        calls = []

        def fallback(text, target):
            calls.append((text, target))
            return "from deep-translator"

        self._no_fallback.stop()
        with patch.object(translation, "_get_chain", list), \
             patch.object(translation, "_translate_via_deep_translator", fallback):
            self.assertEqual(translate_from_greek(GREEK, "en"), "from deep-translator")
        self.assertEqual(len(calls), 1)
        self._no_fallback.start()

    def test_every_provider_failing_defers_to_the_fallback(self):
        groq = FakeProvider("groq", [failed("groq")])
        cloudflare = FakeProvider("cloudflare", [failed("cloudflare")])
        with self._with_chain(groq, cloudflare), \
             patch.object(translation, "_translate_via_deep_translator",
                          lambda *_: "from deep-translator"):
            self.assertEqual(translate_from_greek(GREEK, "en"), "from deep-translator")
        self.assertEqual(len(groq.calls), 1)
        self.assertEqual(len(cloudflare.calls), 1, "every provider is tried first")

    def test_non_greek_text_never_reaches_a_provider(self):
        groq = FakeProvider("groq", ["should not be reached"])
        with self._with_chain(groq):
            self.assertEqual(translate_from_greek("Service change", "en"), "Service change")
        self.assertEqual(groq.calls, [])


if __name__ == "__main__":
    unittest.main()
