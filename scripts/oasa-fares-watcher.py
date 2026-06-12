"""Watch OASA's prices-of-products page and refresh fare_products on change.

Mirrors hellenic-train-watcher.py / stasy-html-watcher.py. Each run:

1. GET https://www.oasa.gr/en/tickets/prices-of-products/
2. BeautifulSoup-extract only the ticket section text (drops nav, footer,
   scripts, cookie banner so cosmetic page changes don't flip the hash).
3. Normalize whitespace, SHA-256.
4. Compare to assets/oasa-fares/.hash.json.
5. On change: re-run parse-oasa-fares.py + ingest-oasa-fares.py +
   generator.generate(). Append journal entry per outcome.

Idempotent: with no change exits in ~1s.
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

from bs4 import BeautifulSoup

ROOT = Path(os.environ.get("PIPELINE_ROOT", str(Path(__file__).resolve().parent.parent)))
CACHE_DIR = ROOT / "assets" / "oasa-fares"
SNAPSHOT_DIR = CACHE_DIR / "snapshots"
HASH_FILE = CACHE_DIR / ".hash.json"
JOURNAL = CACHE_DIR / ".watcher-journal.jsonl"

CACHE_DIR.mkdir(parents=True, exist_ok=True)
SNAPSHOT_DIR.mkdir(parents=True, exist_ok=True)

URL = "https://www.oasa.gr/en/tickets/prices-of-products/"
USER_AGENT = "syrmos-oasa-watcher/1.0 (+https://syrmos.peterdsp.dev)"
_WS_RE = re.compile(r"\s+")


def fetch_html() -> str | None:
    req = urllib.request.Request(URL, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.read().decode("utf-8", errors="ignore")
    except (urllib.error.URLError, TimeoutError, OSError) as e:
        print(f"  fetch failed: {e}", file=sys.stderr)
        return None


def extract_relevant(html: str) -> str:
    soup = BeautifulSoup(html, "html.parser")
    for tag in soup(["script", "style", "noscript", "header", "footer", "nav"]):
        tag.decompose()
    main = soup.find("main") or soup.find("article") or soup.body
    if main is None:
        return ""
    return main.get_text(" ", strip=True)


def normalize(text: str) -> str:
    return _WS_RE.sub(" ", text).strip()


def append_journal(entry: dict) -> None:
    entry["timestamp"] = datetime.now(timezone.utc).isoformat()
    with JOURNAL.open("a") as f:
        f.write(json.dumps(entry, ensure_ascii=False) + "\n")


def run(cmd: list[str], env_extra: dict | None = None) -> tuple[int, str]:
    env = os.environ.copy()
    if env_extra:
        env.update(env_extra)
    proc = subprocess.run(
        cmd, cwd=str(ROOT), capture_output=True, text=True, check=False, env=env
    )
    return proc.returncode, (proc.stdout + proc.stderr)[-2000:]


def main() -> int:
    html = fetch_html()
    if html is None:
        append_journal({"event": "fetch_failed", "url": URL})
        return 1
    relevant = extract_relevant(html)
    digest = hashlib.sha256(normalize(relevant).encode()).hexdigest()

    old = {}
    if HASH_FILE.exists():
        try:
            old = json.loads(HASH_FILE.read_text())
        except json.JSONDecodeError:
            pass

    if old.get("hash") == digest:
        append_journal({"event": "no_change"})
        print("no-op: OASA prices page unchanged")
        return 0

    # Save raw snapshot for audit; refresh the canonical cached HTML so the
    # parser reads the latest body on the next pass.
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    (SNAPSHOT_DIR / f"prices-{stamp}.html").write_text(html)
    (CACHE_DIR / "prices-of-products.html").write_text(html)
    HASH_FILE.write_text(json.dumps({"hash": digest, "fetched_at": stamp}, indent=2))
    append_journal({"event": "snapshot_saved", "hash": digest})

    env_extra = {
        "PIPELINE_ROOT": str(ROOT),
        "SYRMOS_DB_PATH": os.environ.get("SYRMOS_DB_PATH", str(ROOT / "db" / "syrmos.db")),
    }
    rc, out = run([sys.executable, "scripts/parse-oasa-fares.py"], env_extra=env_extra)
    if rc != 0:
        append_journal({"event": "parse_failed", "rc": rc, "tail": out})
        return rc
    rc, out = run([sys.executable, "scripts/ingest-oasa-fares.py"], env_extra=env_extra)
    if rc != 0:
        append_journal({"event": "ingest_failed", "rc": rc, "tail": out})
        return rc
    rc, out = run(
        [sys.executable, "-c",
         "import sys; sys.path.insert(0, 'ops/syrmos-api');"
         " from syrmos_admin import generator; from pathlib import Path;"
         f" generator.generate(out_dir=Path('{ROOT}/out'))"],
        env_extra=env_extra,
    )
    if rc != 0:
        append_journal({"event": "publish_failed", "rc": rc, "tail": out})
        return rc

    append_journal({"event": "refresh_done"})
    print("refreshed OASA fares + republished")
    return 0


if __name__ == "__main__":
    sys.exit(main())
