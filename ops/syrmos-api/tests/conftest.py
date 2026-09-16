"""Test-wide safety net: no test may make a real translation request.

`translate_from_greek` now prefers the Ariadne provider chain, which is built
from environment variables. On a developer machine that happens to export
GROQ_API_KEY or CLOUDFLARE_AI_TOKEN, a test touching translation would quietly
hit a live endpoint. Disable the backend by default; the tests that exercise it
turn it back on and supply their own fake providers.
"""

import pytest

from syrmos_admin import translation


@pytest.fixture(autouse=True)
def _offline_translation():
    translation.USE_ARIADNE = False
    translation._chain_cache = []
    # Give-up state is process-lifetime by design, so it has to be cleared
    # between tests or one test's dead provider silently skips another's.
    translation.reset_run_state()
    yield
    translation.reset_run_state()
