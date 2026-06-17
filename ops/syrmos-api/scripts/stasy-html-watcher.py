"""Watch STASY's per-line HTML timetable pages and refresh the DB on change.

Mirrors hellenic-train-watcher.py in shape. Each run:

1. GET each STASY page.
2. Parse with BeautifulSoup, keep only the actual timetable tables and
   their nearest heading - drops nav, footer, script tags, cookie banner,
   "last updated" timestamps and other noise that would otherwise flip the
   hash on every cosmetic change.
3. Normalize whitespace and compute a SHA-256 over the extracted text.
4. Compare to assets/stasy-html/.hashes.json.
5. On any change: save the new HTML, save the normalized text, re-run
   parse-stasy-html.py + ingest-stasy-html.py + generator.generate().
6. Append one journal line per outcome.

Idempotent: with no changes it exits in ~2 seconds.

Run nightly via the syrmos-watcher-stasy.timer systemd unit alongside the
Hellenic Train watcher.
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
CACHE_DIR = ROOT / "assets" / "stasy-html"
SNAPSHOT_DIR = CACHE_DIR / "snapshots"
HASH_FILE = CACHE_DIR / ".hashes.json"
JOURNAL = CACHE_DIR / ".watcher-journal.jsonl"

CACHE_DIR.mkdir(parents=True, exist_ok=True)
SNAPSHOT_DIR.mkdir(parents=True, exist_ok=True)

URLS = {
    "M1":   "https://www.stasy.gr/en/timetables/line-1/",
    "M2":   "https://www.stasy.gr/en/timetables/line-2/",
    "M3":   "https://www.stasy.gr/en/timetables/line-3/",
    "TRAM": "https://www.stasy.gr/en/timetables/tram/",
}

USER_AGENT = "syrmos-stasy-watcher/1.0 (+https://syrmos.peterdsp.dev)"

# Headings the STASY pages put right before the meaningful tables. Anything
# else (cookie banner, accordion menus, footer) is discarded.
SIGNAL_HEADINGS = (
    "frequency of routes",
    "first & last train departures",
    "first and last train departures",
    "trip duration",
    "schedule",
    "routes",
)
_WS_RE = re.compile(r"\s+")


def fetch_html(url: str, timeout: int = 30) -> str | None:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.read().decode("utf-8", errors="ignore")
    except (urllib.error.URLError, TimeoutError, OSError) as e:
        print(f"  fetch failed for {url}: {e}", file=sys.stderr)
        return None


def extract_relevant_sections(html: str) -> str:
    """Reduce a STASY page to only the timetable tables. Any table that
    looks like a station/minutes grid or a frequency block stays; navigation
    and chrome are dropped. We also normalize line endings inside cells."""
    soup = BeautifulSoup(html, "html.parser")

    # Strip script + style + noscript outright so a re-minified JS chunk
    # doesn't flip the hash.
    for tag in soup(["script", "style", "noscript"]):
        tag.decompose()

    chunks: list[str] = []
    for table in soup.find_all("table"):
        rows = []
        for tr in table.find_all("tr"):
            cells = [c.get_text(separator=" ", strip=True) for c in tr.find_all(["td", "th"])]
            row = " | ".join(c for c in cells if c)
            if row:
                rows.append(row)
        if not rows:
            continue
        # Keep the table only if at least one row mentions station/minutes/
        # frequency/departure language (heuristic: avoid grabbing carousel
        # tables that occasionally appear on STASY's CMS theme).
        joined = " ".join(rows).lower()
        is_signal = (
            "station" in joined
            or "stations" in joined
            or "minutes" in joined
            or "frequency" in joined
            or "first" in joined and "last" in joined
        )
        if is_signal:
            chunks.append("\n".join(rows))

    return "\n\n".join(chunks)


def normalize_text(text: str) -> str:
    return _WS_RE.sub(" ", text).strip()


def compute_hash(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def load_hashes() -> dict[str, str]:
    if HASH_FILE.exists():
        try:
            return json.loads(HASH_FILE.read_text())
        except json.JSONDecodeError:
            return {}
    return {}


def save_hashes(h: dict[str, str]) -> None:
    HASH_FILE.write_text(json.dumps(h, indent=2, sort_keys=True))


def save_snapshot(line: str, raw_html: str, normalized: str) -> tuple[Path, Path]:
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    raw_path = SNAPSHOT_DIR / f"{line.lower()}-{stamp}.html"
    norm_path = SNAPSHOT_DIR / f"{line.lower()}-{stamp}.txt"
    raw_path.write_text(raw_html)
    norm_path.write_text(normalized)
    return raw_path, norm_path


def append_journal(entry: dict) -> None:
    entry["timestamp"] = datetime.now(timezone.utc).isoformat()
    with JOURNAL.open("a") as f:
        f.write(json.dumps(entry, ensure_ascii=False) + "\n")


def run(cmd: list[str], cwd: Path | None = None, env_extra: dict | None = None) -> tuple[int, str]:
    env = os.environ.copy()
    if env_extra:
        env.update(env_extra)
    proc = subprocess.run(
        cmd, cwd=str(cwd or ROOT),
        capture_output=True, text=True, check=False, env=env,
    )
    return proc.returncode, (proc.stdout + proc.stderr)[-2000:]


def trigger_import(env_extra: dict) -> int:
    """Re-run the STASY parser + ingest + generator. Returns final rc."""
    rc, out = run([sys.executable, "scripts/parse-stasy-html.py"], env_extra=env_extra)
    if rc != 0:
        append_journal({"event": "parse_failed", "rc": rc, "tail": out})
        return rc
    rc, out = run([sys.executable, "scripts/ingest-stasy-html.py"], env_extra=env_extra)
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


def main() -> int:
    old = load_hashes()
    new: dict[str, str] = {}
    changed: list[str] = []

    for line, url in URLS.items():
        html = fetch_html(url)
        if html is None:
            new[line] = old.get(line, "")
            append_journal({"event": "fetch_failed", "line": line, "url": url})
            continue
        relevant = extract_relevant_sections(html)
        normalized = normalize_text(relevant)
        digest = compute_hash(normalized)
        new[line] = digest
        if old.get(line) != digest:
            raw_path, norm_path = save_snapshot(line, html, normalized)
            changed.append(line)
            append_journal({
                "event": "snapshot_saved", "line": line,
                "raw_html_path": str(raw_path.relative_to(ROOT)),
                "normalized_text_path": str(norm_path.relative_to(ROOT)),
                "content_hash": digest,
            })
            # Also refresh the canonical cached HTML the parser reads.
            (CACHE_DIR / f"{url.rstrip('/').split('/')[-1]}.html").write_text(html)

    save_hashes(new)

    if not changed:
        append_journal({"event": "no_change", "lines": list(URLS.keys())})
        print(f"no-op: {len(URLS)} STASY pages, all hashes match")
        return 0

    append_journal({"event": "refresh_started", "changed": changed})
    env_extra = {
        "PIPELINE_ROOT": str(ROOT),
        "SYRMOS_DB_PATH": os.environ.get("SYRMOS_DB_PATH", str(ROOT / "db" / "syrmos.db")),
    }
    rc = trigger_import(env_extra)
    if rc == 0:
        append_journal({"event": "refresh_done", "changed": changed})
        print(f"refreshed {len(changed)} STASY page(s) and republished")
    return rc


if __name__ == "__main__":
    sys.exit(main())
