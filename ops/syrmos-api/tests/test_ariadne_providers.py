"""Tests for the Ariadne OpenAI-compatible provider chain.

Uses httpx.MockTransport so no network is touched: each test injects a
canned HTTP response (or exception) and asserts the provider classifies it
correctly (success, config error, rate limit, server error, timeout,
network, empty) and that the chain fails over and finally falls back to a
deterministic offline reply.
"""
import asyncio
import os
import unittest
from unittest import mock

import httpx

from syrmos_admin import ariadne_providers as ap


def _client(handler) -> httpx.AsyncClient:
    return httpx.AsyncClient(transport=httpx.MockTransport(handler))


def _ok_response(content="Take the M3 from Syntagma.", ptok=42, ctok=9):
    return httpx.Response(
        200,
        json={
            "choices": [{"message": {"role": "assistant", "content": content}}],
            "usage": {"prompt_tokens": ptok, "completion_tokens": ctok},
        },
    )


PROVIDER = ap.OpenAICompatibleProvider(
    name="test", base_url="https://api.example.com/v1", api_key="k", model="m",
)
SYS = "system"
MSGS = [{"role": "user", "content": "how do I get to the airport?"}]


class ProviderResultTest(unittest.IsolatedAsyncioTestCase):
    async def test_success_parses_text_and_usage(self):
        async def handler(request):
            self.assertEqual(request.url.path, "/v1/chat/completions")
            self.assertEqual(request.headers["authorization"], "Bearer k")
            self.assertIn(b'"model":"m"', request.content)
            return _ok_response()

        r = await PROVIDER.complete(_client(handler), SYS, MSGS)
        self.assertTrue(r.ok)
        self.assertEqual(r.text, "Take the M3 from Syntagma.")
        self.assertEqual(r.prompt_tokens, 42)
        self.assertEqual(r.completion_tokens, 9)
        self.assertEqual(r.status, 200)
        self.assertIsNotNone(r.latency_ms)

    async def test_403_is_config_error(self):
        async def handler(request):
            return httpx.Response(403, json={"error": {"message": "blocked at org level"}})

        r = await PROVIDER.complete(_client(handler), SYS, MSGS)
        self.assertFalse(r.ok)
        self.assertEqual(r.error_kind, "config")
        self.assertEqual(r.status, 403)

    async def test_401_is_config_error(self):
        async def handler(request):
            return httpx.Response(401, text="bad key")

        r = await PROVIDER.complete(_client(handler), SYS, MSGS)
        self.assertEqual(r.error_kind, "config")

    async def test_429_is_rate_limit(self):
        async def handler(request):
            return httpx.Response(429, text="slow down")

        r = await PROVIDER.complete(_client(handler), SYS, MSGS)
        self.assertFalse(r.ok)
        self.assertEqual(r.error_kind, "rate_limit")

    async def test_500_is_server(self):
        async def handler(request):
            return httpx.Response(500, text="boom")

        r = await PROVIDER.complete(_client(handler), SYS, MSGS)
        self.assertFalse(r.ok)
        self.assertEqual(r.error_kind, "server")

    async def test_timeout(self):
        async def handler(request):
            raise httpx.ReadTimeout("timed out", request=request)

        r = await PROVIDER.complete(_client(handler), SYS, MSGS)
        self.assertFalse(r.ok)
        self.assertEqual(r.error_kind, "timeout")

    async def test_network_error(self):
        async def handler(request):
            raise httpx.ConnectError("no route", request=request)

        r = await PROVIDER.complete(_client(handler), SYS, MSGS)
        self.assertFalse(r.ok)
        self.assertEqual(r.error_kind, "network")

    async def test_empty_choices_is_empty(self):
        async def handler(request):
            return httpx.Response(200, json={"choices": []})

        r = await PROVIDER.complete(_client(handler), SYS, MSGS)
        self.assertFalse(r.ok)
        self.assertEqual(r.error_kind, "empty")

    async def test_blank_content_is_empty(self):
        async def handler(request):
            return httpx.Response(200, json={"choices": [{"message": {"content": "   "}}]})

        r = await PROVIDER.complete(_client(handler), SYS, MSGS)
        self.assertFalse(r.ok)
        self.assertEqual(r.error_kind, "empty")


_PROVIDER_ENV_PREFIXES = ("ARIADNE_", "GROQ_", "CLOUDFLARE_", "SAMBANOVA_", "GEMINI_")


class BuildChainTest(unittest.TestCase):
    def setUp(self):
        self._saved = {
            k: os.environ.pop(k)
            for k in list(os.environ)
            if k.startswith(_PROVIDER_ENV_PREFIXES)
        }

    def tearDown(self):
        for k in list(os.environ):
            if k.startswith(_PROVIDER_ENV_PREFIXES):
                os.environ.pop(k)
        os.environ.update(self._saved)

    def test_empty_when_nothing_configured(self):
        self.assertEqual(ap.build_chain(), [])

    def test_groq_only_uses_production_model(self):
        os.environ["GROQ_API_KEY"] = "gsk_x"
        chain = ap.build_chain()
        self.assertEqual([p.name for p in chain], ["groq"])
        self.assertEqual(chain[0].model, "openai/gpt-oss-120b")
        self.assertEqual(chain[0].base_url, "https://api.groq.com/openai/v1")

    def test_cloudflare_needs_both_account_and_token(self):
        os.environ["CLOUDFLARE_ACCOUNT_ID"] = "acc"
        self.assertEqual(ap.build_chain(), [])  # token missing -> not included

    def test_full_order_and_local_timeout(self):
        os.environ["ARIADNE_GROQ_API_KEY"] = "gsk_x"
        os.environ["CLOUDFLARE_ACCOUNT_ID"] = "acc"
        os.environ["CLOUDFLARE_AI_TOKEN"] = "tok"
        os.environ["ARIADNE_LOCAL_BASE_URL"] = "http://127.0.0.1:8081/v1"
        chain = ap.build_chain()
        self.assertEqual([p.name for p in chain], ["groq", "cloudflare", "local"])
        self.assertIn("accounts/acc/ai/v1", chain[1].base_url)
        self.assertEqual(chain[2].read_timeout, ap.LOCAL_READ_TIMEOUT)

    def test_hosted_http_override_is_skipped(self):
        os.environ["GROQ_API_KEY"] = "gsk_x"
        os.environ["ARIADNE_GROQ_BASE_URL"] = "http://insecure.example/v1"
        self.assertEqual(ap.build_chain(), [])  # plaintext hosted -> refused

    def test_hosted_https_is_kept(self):
        os.environ["GROQ_API_KEY"] = "gsk_x"
        self.assertEqual([p.name for p in ap.build_chain()], ["groq"])

    def test_local_loopback_http_allowed(self):
        os.environ["ARIADNE_LOCAL_BASE_URL"] = "http://127.0.0.1:8081/v1"
        self.assertEqual([p.name for p in ap.build_chain()], ["local"])

    def test_local_non_loopback_http_skipped(self):
        os.environ["ARIADNE_LOCAL_BASE_URL"] = "http://evil.example/v1"
        self.assertEqual(ap.build_chain(), [])


class ChatAsyncTest(unittest.IsolatedAsyncioTestCase):
    class _Stub:
        def __init__(self, name, ok):
            self.name = name
            self.model = name + "-model"
            self._ok = ok

        async def complete(self, client, system_text, messages, timeout_override=None):
            return ap.ProviderResult(
                self.name, self.model, self._ok,
                text=("hi from " + self.name) if self._ok else "",
                error_kind=None if self._ok else "server",
            )

    async def test_returns_first_successful_provider(self):
        from syrmos_admin import ariadne
        ap.breaker_reset_all()
        chain = [self._Stub("a", False), self._Stub("b", True), self._Stub("c", True)]
        with mock.patch.object(ariadne.providers, "build_chain", lambda: chain), \
                mock.patch.object(ariadne.providers, "get_client", lambda: None), \
                mock.patch.object(ariadne, "_get_transit_sections", lambda: []):
            out = await ariadne.chat_async([{"role": "user", "text": "hi"}])
        self.assertEqual(out["provider"], "b")
        self.assertEqual(out["reply"], "hi from b")
        self.assertEqual(out["model"], "b-model")

    async def test_all_fail_falls_back_offline(self):
        from syrmos_admin import ariadne
        ap.breaker_reset_all()
        chain = [self._Stub("a", False), self._Stub("b", False)]
        with mock.patch.object(ariadne.providers, "build_chain", lambda: chain), \
                mock.patch.object(ariadne.providers, "get_client", lambda: None), \
                mock.patch.object(ariadne, "_get_transit_sections", lambda: []):
            out = await ariadne.chat_async([{"role": "user", "text": "are you serious"}])
        self.assertEqual(out["provider"], "offline")
        self.assertIn("brain", out["reply"].lower())


class MalformedResponseTest(unittest.IsolatedAsyncioTestCase):
    """A 200 with an unexpected shape must degrade to a failed attempt, never
    raise out of complete() and abort the chain."""

    async def _complete(self, payload):
        async def handler(request):
            return httpx.Response(200, json=payload)
        return await PROVIDER.complete(_client(handler), SYS, MSGS)

    async def test_non_object_root(self):
        r = await self._complete(["not", "an", "object"])
        self.assertFalse(r.ok)

    async def test_choices_not_a_list(self):
        r = await self._complete({"choices": "nope"})
        self.assertFalse(r.ok)

    async def test_choice_not_a_dict(self):
        r = await self._complete({"choices": ["oops"]})
        self.assertFalse(r.ok)

    async def test_content_not_a_string(self):
        r = await self._complete({"choices": [{"message": {"content": {"x": 1}}}]})
        self.assertFalse(r.ok)

    async def test_bad_usage_does_not_crash_a_good_reply(self):
        r = await self._complete({"choices": [{"message": {"content": "M3."}}], "usage": [1, 2]})
        self.assertTrue(r.ok)
        self.assertEqual(r.text, "M3.")
        self.assertIsNone(r.prompt_tokens)


class RedactUnitTest(unittest.TestCase):
    def test_masks_bearer_and_key_shapes(self):
        s = ap._redact("Bearer gsk_abcdefghijklmnop then AIzaSy0123456789abcdefghij and sk-abcdefzz")
        self.assertNotIn("gsk_abcdefghijklmnop", s)
        self.assertNotIn("AIzaSy0123456789", s)
        self.assertIn("[REDACTED]", s)

    def test_keeps_plain_diagnostic_text(self):
        s = ap._redact("The model is blocked at the organization level")
        self.assertEqual(s, "The model is blocked at the organization level")

    def test_masks_hyphenated_openai_key(self):
        s = ap._redact("token sk-proj-abcdefghijklmnopqrstuvwxyz1234567890 end")
        self.assertNotIn("sk-proj-abcdefghijklmnopqrstuvwxyz1234567890", s)
        self.assertIn("[REDACTED]", s)


class RedactionInErrorTest(unittest.IsolatedAsyncioTestCase):
    async def test_secret_in_message_is_not_logged(self):
        # A provider echoes a key in its free-form message; with code-only
        # logging the whole message (and the key) is dropped, not just masked.
        async def handler(request):
            return httpx.Response(400, json={"error": {
                "message": "bad key gsk_supersecretkey1234567890 rejected"}})

        r = await PROVIDER.complete(_client(handler), SYS, MSGS)
        self.assertFalse(r.ok)
        self.assertNotIn("gsk_supersecretkey1234567890", r.error_detail)
        self.assertEqual(r.error_detail, "error body suppressed")


class GroundingTest(unittest.TestCase):
    def test_no_context_still_carries_abstention(self):
        from syrmos_admin import ariadne
        with mock.patch.object(ariadne, "_get_transit_sections", lambda: []):
            text = ariadne._system_text("when is the next train")
        self.assertIn("Live Syrmos transit data is unavailable", text)
        self.assertIn("hellenictrain.gr", text)

    def test_with_context_includes_grounding_and_facts(self):
        from syrmos_admin import ariadne
        with mock.patch.object(ariadne, "_get_transit_sections",
                               lambda: [("lines", "Active lines:\n- M1")]):
            text = ariadne._system_text("how do I get around")
        self.assertIn("Use ONLY these", text)
        self.assertIn("Active lines", text)


class ChainDeadlineTest(unittest.IsolatedAsyncioTestCase):
    class _SlowStub:
        name = "slow"
        model = "slow"

        async def complete(self, client, system_text, messages, timeout_override=None):
            import asyncio
            await asyncio.sleep(5)  # far longer than the (patched) deadline
            return ap.ProviderResult("slow", "slow", True, text="too late")

    async def test_deadline_forces_offline(self):
        from syrmos_admin import ariadne
        ap.breaker_reset_all()
        with mock.patch.object(ariadne.providers, "build_chain", lambda: [self._SlowStub()]), \
                mock.patch.object(ariadne.providers, "get_client", lambda: None), \
                mock.patch.object(ariadne.providers, "CHAIN_DEADLINE", 0.2), \
                mock.patch.object(ariadne.providers, "MIN_ATTEMPT_BUDGET", 0.05), \
                mock.patch.object(ariadne, "_get_transit_sections", lambda: []):
            out = await ariadne.chat_async([{"role": "user", "text": "hi"}])
        self.assertEqual(out["provider"], "offline")


class ErrorSummaryTest(unittest.IsolatedAsyncioTestCase):
    """Only the provider's stable error code is logged; free-form messages and
    arbitrary bodies (which can echo prompt text or PII) are suppressed."""

    async def _detail(self, status, **kw):
        async def handler(request):
            return httpx.Response(status, **kw)
        r = await PROVIDER.complete(_client(handler), SYS, MSGS)
        return r.error_detail

    async def test_only_code_logged_not_message(self):
        detail = await self._detail(403, json={"error": {
            "code": "model_permission_blocked_org",
            "message": "user a@b.com sent this exact prompt",
        }})
        self.assertEqual(detail, "code=model_permission_blocked_org")
        self.assertNotIn("a@b.com", detail)
        self.assertNotIn("prompt", detail)

    async def test_cloudflare_numeric_code_logged(self):
        detail = await self._detail(400, json={"errors": [{"code": 7000, "message": "No route for that URI"}]})
        self.assertEqual(detail, "code=7000")
        self.assertNotIn("No route", detail)

    async def test_message_only_error_suppressed(self):
        detail = await self._detail(400, json={"error": {"message": "email a@b.com in the prompt"}})
        self.assertNotIn("a@b.com", detail)
        self.assertEqual(detail, "error body suppressed")

    async def test_unsafe_code_shape_rejected(self):
        detail = await self._detail(400, json={"error": {"code": "contains spaces and a@b.com"}})
        self.assertNotIn("a@b.com", detail)
        self.assertEqual(detail, "error body suppressed")

    async def test_plaintext_body_suppressed(self):
        detail = await self._detail(400, text="user email a@b.com password hunter2 rejected")
        self.assertNotIn("a@b.com", detail)
        self.assertNotIn("hunter2", detail)
        self.assertEqual(detail, "error body suppressed")

    async def test_json_without_error_shape_suppressed(self):
        detail = await self._detail(400, json={"detail": "contact a@b.com"})
        self.assertNotIn("a@b.com", detail)
        self.assertEqual(detail, "error body suppressed")


class GroundingDeadlineTest(unittest.IsolatedAsyncioTestCase):
    async def test_slow_grounding_is_bounded_and_falls_back(self):
        from syrmos_admin import ariadne

        def slow_sections():
            import time as _t
            _t.sleep(1.5)  # simulate a locked/slow DB, off the event loop
            return [("lines", "Active lines:\n- M1")]

        loop = asyncio.get_event_loop()
        with mock.patch.object(ariadne, "_get_transit_sections", slow_sections):
            deadline = loop.time() + 0.2
            start = loop.time()
            text = await ariadne._build_system_text(deadline, loop, "how do I get there")
            elapsed = loop.time() - start
        self.assertLess(elapsed, 1.0)  # did not wait the full 1.5s
        self.assertIn("Live Syrmos transit data is unavailable", text)


class RateLimitTest(unittest.IsolatedAsyncioTestCase):
    async def test_allows_up_to_limit_then_blocks(self):
        from syrmos_admin import app as appmod
        with mock.patch.object(appmod, "_ARIADNE_RATE_PER_MIN", 3):
            appmod._ariadne_hits.clear()
            appmod._ariadne_global.clear()
            results = [await appmod._ariadne_rate_ok("client-abc") for _ in range(4)]
        self.assertEqual(results, [True, True, True, False])

    async def test_separate_clients_are_independent(self):
        from syrmos_admin import app as appmod
        with mock.patch.object(appmod, "_ARIADNE_RATE_PER_MIN", 1):
            appmod._ariadne_hits.clear()
            appmod._ariadne_global.clear()
            self.assertTrue(await appmod._ariadne_rate_ok("client-a"))
            self.assertTrue(await appmod._ariadne_rate_ok("client-b"))
            self.assertFalse(await appmod._ariadne_rate_ok("client-a"))

    async def test_global_ceiling_blocks_across_distinct_clients(self):
        from syrmos_admin import app as appmod
        with mock.patch.object(appmod, "_ARIADNE_RATE_PER_MIN", 100), \
                mock.patch.object(appmod, "_ARIADNE_GLOBAL_PER_MIN", 3):
            appmod._ariadne_hits.clear()
            appmod._ariadne_global.clear()
            results = [await appmod._ariadne_rate_ok(f"client-{i}") for i in range(4)]
        self.assertEqual(results, [True, True, True, False])


class CircuitBreakerTest(unittest.TestCase):
    def setUp(self):
        ap.breaker_reset_all()

    def test_opens_after_threshold_expensive_failures(self):
        with mock.patch.object(ap, "CB_THRESHOLD", 3), \
                mock.patch.object(ap, "CB_COOLDOWN", 30.0):
            self.assertTrue(ap.breaker_allows("groq", 0.0))
            ap.breaker_record("groq", False, "timeout", 1.0)
            ap.breaker_record("groq", False, "timeout", 2.0)
            self.assertTrue(ap.breaker_allows("groq", 2.0))   # not open yet
            ap.breaker_record("groq", False, "timeout", 3.0)  # 3rd -> open
            self.assertFalse(ap.breaker_allows("groq", 3.0))
            self.assertFalse(ap.breaker_allows("groq", 32.9))  # still cooling down
            self.assertTrue(ap.breaker_allows("groq", 33.1))   # cooldown elapsed

    def test_success_closes_the_breaker(self):
        with mock.patch.object(ap, "CB_THRESHOLD", 2):
            ap.breaker_record("cf", False, "server", 1.0)
            ap.breaker_record("cf", False, "server", 2.0)  # open
            self.assertFalse(ap.breaker_allows("cf", 2.0))
            ap.breaker_record("cf", True, None, 3.0)        # success closes it
            self.assertTrue(ap.breaker_allows("cf", 3.0))

    def test_config_failures_never_trip(self):
        with mock.patch.object(ap, "CB_THRESHOLD", 2):
            for t in range(5):
                ap.breaker_record("groq", False, "config", float(t))
            self.assertTrue(ap.breaker_allows("groq", 10.0))  # a 403 must not open it


_SECTIONS = [
    ("lines", "Active lines:\n- M1"),
    ("stasy", "STASY service: ok."),
    ("announcements", "Active announcements:\n- none"),
    ("fares", "Current fares:\n- 90min: EUR 1.20"),
    ("hours", "Operating hours:\n- M1 (weekday): 05:30-00:30"),
    ("frequencies", "Current frequencies:\n- M1: every 4 min"),
    ("news", "Recent rail news:\n- something happened"),
]


class ContextSelectionTest(unittest.TestCase):
    def _ctx(self, query, **kw):
        from syrmos_admin import ariadne
        return ariadne._select_context(_SECTIONS, query, **kw)

    def test_fare_query_includes_fares_not_news_or_hours(self):
        ctx = self._ctx("how much is a ticket to the airport")
        self.assertIn("Current fares", ctx)
        self.assertIn("Active lines", ctx)          # always
        self.assertIn("Active announcements", ctx)  # always
        self.assertNotIn("Recent rail news", ctx)
        self.assertNotIn("Operating hours", ctx)

    def test_schedule_query_includes_hours_and_frequencies(self):
        ctx = self._ctx("what time do trains run and how often")
        self.assertIn("Operating hours", ctx)
        self.assertIn("Current frequencies", ctx)

    def test_generic_query_gets_only_always_sections(self):
        ctx = self._ctx("hello there owl")
        self.assertIn("Active lines", ctx)
        self.assertIn("Active announcements", ctx)
        self.assertNotIn("Current fares", ctx)
        self.assertNotIn("Recent rail news", ctx)

    def test_total_is_capped(self):
        from syrmos_admin import ariadne
        big = [("lines", "L" * 5000), ("fares", "F" * 5000)]
        ctx = ariadne._select_context(big, "ticket price", max_chars=1000)
        self.assertLessEqual(len(ctx), 1000)

    def test_empty_sections_returns_none(self):
        from syrmos_admin import ariadne
        self.assertIsNone(ariadne._select_context([], "anything"))


if __name__ == "__main__":
    unittest.main()
