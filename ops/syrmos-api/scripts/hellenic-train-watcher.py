"""Watch Hellenic Train for PDF schedule changes and refresh the DB.

Workflow per run:
1. Crawl https://www.hellenictrain.gr/en/athens-suburban-and-regional-railway
2. For every PDF link on the page, GET it and compute a content hash.
3. Compare against /home/peterdsp/syrmos-api/assets/hellenic-train-pdfs/.hashes.
4. If anything new or changed: re-download into the assets dir, re-run the
   parser, re-run the train_timestamps ingest, and re-run generator.generate()
   so /api/train-timestamps.json + the manifest both bump.
5. Append a journal line per run so the admin Sync page shows recent activity.

Intended to be invoked nightly from a systemd timer. Idempotent: if nothing
changed, exits in ~2 seconds with no side effects.

Run manually (on the Pi):
    SYRMOS_DB_PATH=/home/peterdsp/syrmos-api/db/syrmos.db \\
    PIPELINE_ROOT=/home/peterdsp/syrmos-api \\
    python3 scripts/hellenic-train-watcher.py
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(os.environ.get("PIPELINE_ROOT", str(Path(__file__).resolve().parent.parent)))
PDF_DIR = ROOT / "assets" / "hellenic-train-pdfs"
PARSED_DIR = PDF_DIR / "parsed"
HASH_FILE = PDF_DIR / ".hashes.json"
JOURNAL = PDF_DIR / ".watcher-journal.jsonl"

INDEX_URL = "https://www.hellenictrain.gr/en/athens-suburban-and-regional-railway"
USER_AGENT = "syrmos-watcher/1.0 (+https://syrmos.peterdsp.dev)"


def fetch(url: str, timeout: int = 30) -> bytes | None:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.read()
    except (urllib.error.URLError, TimeoutError, OSError) as e:
        print(f"  fetch failed for {url}: {e}", file=sys.stderr)
        return None


def discover_pdfs() -> list[str]:
    """Return absolute URLs of every PDF linked from the suburban index page."""
    html = fetch(INDEX_URL)
    if not html:
        return []
    text = html.decode("utf-8", errors="ignore")
    found = sorted(set(re.findall(r'href="([^"]+\.pdf)"', text, flags=re.I)))
    out: list[str] = []
    for p in found:
        if p.startswith("//"):
            out.append("https:" + p)
        elif p.startswith("/"):
            out.append("https://www.hellenictrain.gr" + p)
        elif p.startswith("http"):
            out.append(p)
    return out


def load_hashes() -> dict[str, str]:
    if HASH_FILE.exists():
        try:
            return json.loads(HASH_FILE.read_text())
        except json.JSONDecodeError:
            return {}
    return {}


def save_hashes(h: dict[str, str]) -> None:
    HASH_FILE.write_text(json.dumps(h, indent=2, sort_keys=True))


def append_journal(entry: dict) -> None:
    entry["timestamp"] = datetime.now(timezone.utc).isoformat()
    with JOURNAL.open("a") as f:
        f.write(json.dumps(entry, ensure_ascii=False) + "\n")


def run(cmd: list[str], cwd: Path | None = None) -> tuple[int, str]:
    proc = subprocess.run(
        cmd,
        cwd=str(cwd or ROOT),
        capture_output=True,
        text=True,
        check=False,
    )
    return proc.returncode, (proc.stdout + proc.stderr)[-2000:]


def main() -> int:
    PDF_DIR.mkdir(parents=True, exist_ok=True)
    pdfs = discover_pdfs()
    if not pdfs:
        append_journal({"event": "discover_failed", "url": INDEX_URL})
        print("could not reach hellenictrain.gr — leaving DB untouched", file=sys.stderr)
        return 1

    old = load_hashes()
    new: dict[str, str] = {}
    changed_files: list[str] = []

    for url in pdfs:
        filename = os.path.basename(urllib.parse.unquote(url))
        body = fetch(url, timeout=60)
        if not body:
            new[url] = old.get(url, "")  # carry forward last good hash so we don't lose state
            continue
        digest = hashlib.sha256(body).hexdigest()
        new[url] = digest
        if old.get(url) != digest:
            target = PDF_DIR / filename
            target.write_bytes(body)
            changed_files.append(filename)

    save_hashes(new)

    if not changed_files:
        append_journal({"event": "no_change", "pdfs": len(pdfs)})
        print(f"no-op: {len(pdfs)} PDFs, all hashes match")
        return 0

    append_journal({"event": "refresh_started", "changed": changed_files})

    # Parse PDFs -> JSONL
    rc, out = run([sys.executable, "scripts/parse-hellenic-train-pdfs.py"])
    if rc != 0:
        append_journal({"event": "parse_failed", "rc": rc, "tail": out})
        return rc

    # Ingest into train_timestamps
    rc, out = run([sys.executable, "scripts/ingest-train-timestamps.py"])
    if rc != 0:
        append_journal({"event": "ingest_failed", "rc": rc, "tail": out})
        return rc

    # Bump manifest + republish JSON
    rc, out = run(
        [sys.executable, "-c",
         "import sys; sys.path.insert(0, 'ops/syrmos-api');"
         " from syrmos_admin import generator; from pathlib import Path;"
         f" generator.generate(out_dir=Path('{ROOT}/out'))"]
    )
    if rc != 0:
        append_journal({"event": "publish_failed", "rc": rc, "tail": out})
        return rc

    append_journal({"event": "refresh_done", "changed": changed_files})
    print(f"refreshed {len(changed_files)} PDF(s) and republished")
    return 0


if __name__ == "__main__":
    sys.exit(main())
