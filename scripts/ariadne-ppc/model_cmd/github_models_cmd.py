#!/usr/bin/env python3
"""ARIADNE_PPC_MODEL_CMD wrapper for an OpenAI-compatible, free endpoint.

Default target is GitHub Models (https://models.github.ai/inference), which is
free to call from inside GitHub Actions using the built-in GITHUB_TOKEN with
`permissions: models: read`. No account, no card, no secret to manage, nothing
runs on your machine. Stdlib only (urllib), so there is no package to install.

Reads a prompt on stdin, writes the completion on stdout. The same wrapper works
against any OpenAI chat-completions endpoint (e.g. a local Ollama server) by
pointing the env vars elsewhere.

Env:
  ARIADNE_PPC_OPENAI_BASE_URL  default https://models.github.ai/inference
  ARIADNE_PPC_OPENAI_MODEL     default openai/gpt-4o-mini
  ARIADNE_PPC_OPENAI_TOKEN     default $GITHUB_TOKEN (what Actions injects)
"""

from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.request

BASE_URL = os.environ.get("ARIADNE_PPC_OPENAI_BASE_URL", "https://models.github.ai/inference")
MODEL = os.environ.get("ARIADNE_PPC_OPENAI_MODEL", "openai/gpt-4o-mini")
TOKEN = os.environ.get("ARIADNE_PPC_OPENAI_TOKEN") or os.environ.get("GITHUB_TOKEN", "")
MAX_RETRIES = 5


def extract(payload: str) -> str:
    """Pull the assistant text out of an OpenAI chat-completions response."""
    data = json.loads(payload)
    return data["choices"][0]["message"]["content"]


def call(prompt: str) -> str:
    body = json.dumps(
        {
            "model": MODEL,
            "messages": [{"role": "user", "content": prompt}],
            "temperature": 0.2,
            "max_tokens": 8000,
        }
    ).encode("utf-8")
    req = urllib.request.Request(
        BASE_URL.rstrip("/") + "/chat/completions",
        data=body,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {TOKEN}",
            "Accept": "application/json",
        },
    )
    for attempt in range(1, MAX_RETRIES + 1):
        try:
            with urllib.request.urlopen(req, timeout=120) as resp:
                return extract(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            # Honor rate-limit backoff (free tiers cap requests per minute).
            if exc.code == 429 and attempt < MAX_RETRIES:
                wait = int(exc.headers.get("Retry-After", "0")) or min(2 ** attempt, 60)
                time.sleep(wait)
                continue
            raise RuntimeError(f"HTTP {exc.code}: {exc.read().decode('utf-8', 'replace')[:300]}")
    raise RuntimeError("exhausted retries")


def main() -> int:
    if not TOKEN:
        sys.stderr.write(
            "no token: set ARIADNE_PPC_OPENAI_TOKEN, or run inside GitHub Actions "
            "with `permissions: models: read` so GITHUB_TOKEN is available\n"
        )
        return 2
    try:
        sys.stdout.write(call(sys.stdin.read()))
    except RuntimeError as exc:
        sys.stderr.write(f"{exc}\n")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
