"""Daily STASY status + announcements watcher.

Same pattern as the other watchers. Fetches the homepage status badge +
the news/announcements page, hashes the extracted content, re-runs the
parser/ingest/generator chain when anything changed.
"""
from __future__ import annotations

import hashlib
import json
import os
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

from bs4 import BeautifulSoup

ROOT = Path(os.environ.get("PIPELINE_ROOT", str(Path(__file__).resolve().parent.parent)))
CACHE_DIR = ROOT / "assets" / "stasy-announcements"
HASH_FILE = CACHE_DIR / ".hash.json"
JOURNAL = CACHE_DIR / ".watcher-journal.jsonl"

CACHE_DIR.mkdir(parents=True, exist_ok=True)

URLS = {
    "homepage": "https://www.stasy.gr/",
    "news":     "https://www.stasy.gr/νέα-ανακοινώσεις/",
}
USER_AGENT = "syrmos-stasy-announcements-watcher/1.0 (+https://syrmos.peterdsp.dev)"


def fetch(url: str) -> str | None:
    parsed = urllib.parse.urlsplit(url)
    safe = urllib.parse.urlunsplit((
        parsed.scheme, parsed.netloc,
        urllib.parse.quote(parsed.path, safe="/%"),
        parsed.query, parsed.fragment,
    ))
    req = urllib.request.Request(safe, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.read().decode("utf-8", errors="ignore")
    except (urllib.error.URLError, TimeoutError, OSError) as e:
        print(f"  fetch failed for {url}: {e}", file=sys.stderr)
        return None


def relevant_signal(html: str) -> str:
    """Strip script/style/nav so cosmetic cookie-banner toggles don't flip
    the hash. Keep only the main content text."""
    soup = BeautifulSoup(html, "html.parser")
    for tag in soup(["script", "style", "noscript"]):
        tag.decompose()
    main = soup.find("main") or soup.body
    return main.get_text(" ", strip=True) if main else ""


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
    bodies: dict[str, str] = {}
    digests: dict[str, str] = {}
    for name, url in URLS.items():
        body = fetch(url)
        if body is None:
            append_journal({"event": "fetch_failed", "page": name, "url": url})
            return 1
        bodies[name] = body
        digests[name] = hashlib.sha256(relevant_signal(body).encode()).hexdigest()

    old = {}
    if HASH_FILE.exists():
        try:
            old = json.loads(HASH_FILE.read_text())
        except json.JSONDecodeError:
            pass

    if old == digests:
        append_journal({"event": "no_change"})
        print("no-op: STASY announcements + status unchanged")
        return 0

    # Refresh the canonical cached HTML the parser reads, then chain through.
    (CACHE_DIR / "homepage.html").write_text(bodies["homepage"])
    (CACHE_DIR / "news.html").write_text(bodies["news"])
    HASH_FILE.write_text(json.dumps(digests, indent=2, sort_keys=True))
    append_journal({"event": "snapshot_saved"})

    env_extra = {
        "PIPELINE_ROOT": str(ROOT),
        "SYRMOS_DB_PATH": os.environ.get("SYRMOS_DB_PATH", str(ROOT / "db" / "syrmos.db")),
    }
    rc, out = run([sys.executable, "scripts/parse-stasy-announcements.py"], env_extra=env_extra)
    if rc != 0:
        append_journal({"event": "parse_failed", "rc": rc, "tail": out})
        return rc
    rc, out = run([sys.executable, "scripts/ingest-stasy-announcements.py"], env_extra=env_extra)
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
    print("refreshed STASY announcements + status; republished")
    return 0


if __name__ == "__main__":
    sys.exit(main())
