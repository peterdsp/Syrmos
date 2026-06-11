"""Daily upstream-source change watcher.

Hashes the official source pages and PDFs we draw schedules from. When the
hash differs from the previous run, logs to `scrape_log`. The admin UI can
then surface "Hellenic Train updated their PDFs on 2026-06-19, please
re-verify the A1 bands" without spamming people every day.

Run: python3 -m syrmos_admin.upstream_watcher
"""
from __future__ import annotations

import hashlib
import json
import sqlite3
from urllib.error import URLError
from urllib.request import Request, urlopen

from . import db as dbmod

USER_AGENT = "syrmos-watcher/1.0 (+https://syrmos.peterdsp.dev)"
TIMEOUT_SECONDS = 30

# Source URLs we depend on. Add new ones here — script handles them automatically.
SOURCES: list[tuple[str, str]] = [
    ("oasa_24mmm", "https://www.oasa.gr/en/24mmm/"),
    ("stasy_root", "https://www.stasy.gr/"),
    ("hellenic_train_overview", "https://www.hellenictrain.gr/en/athens-suburban-and-regional-railway"),
    ("hellenic_pdf_a1_a2_monfri",
     "https://www.hellenictrain.gr/sites/default/files/2025-11/PIRAEUS-ATHENS-LIOSIA-AIRPORT_MON-FRI_from_221125_0.pdf"),
    ("hellenic_pdf_a1_a2_weekend",
     "https://www.hellenictrain.gr/sites/default/files/2025-11/PIRAEUS-ATHENS-LIOSIA-AIRPORT_WEEKENDS_from_221125_0.pdf"),
    ("hellenic_pdf_a1_a2_rev_monfri",
     "https://www.hellenictrain.gr/sites/default/files/2025-11/AIRPORT-LIOSIA-ATHENS-PIRAEUS_MON-FRI_from_221125_0.pdf"),
    ("hellenic_pdf_a1_a2_rev_weekend",
     "https://www.hellenictrain.gr/sites/default/files/2025-11/AIRPORT-LIOSIA-ATHENS-PIRAEUS_WEEKENDS_from_221125_0.pdf"),
    ("hellenic_pdf_a3",
     "https://www.hellenictrain.gr/sites/default/files/2025-11/ATHENS-CHALKIDA-ATHENS_from_221125_0.pdf"),
    ("hellenic_pdf_a4",
     "https://www.hellenictrain.gr/sites/default/files/2025-11/PIRAEUS-KIATO-PIRAEUS_from_221125_0.pdf"),
]


def fetch_bytes(url: str) -> bytes:
    req = Request(url, headers={"User-Agent": USER_AGENT})
    with urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
        return resp.read()


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def previous_hashes(conn: sqlite3.Connection) -> dict[str, str]:
    row = conn.execute(
        "SELECT value FROM meta WHERE key='upstream_hashes'"
    ).fetchone()
    if not row or not row["value"]:
        return {}
    return json.loads(row["value"])


def save_hashes(conn: sqlite3.Connection, hashes: dict[str, str]) -> None:
    conn.execute(
        "INSERT OR REPLACE INTO meta(key, value) VALUES('upstream_hashes', ?)",
        (json.dumps(hashes),),
    )


def run_once() -> dict[str, str]:
    """Returns dict of {source_id: status} where status is unchanged/changed/error."""
    results: dict[str, str] = {}
    with dbmod.connect() as conn:
        previous = previous_hashes(conn)
        current = {}
        for source_id, url in SOURCES:
            try:
                body = fetch_bytes(url)
                digest = sha256(body)
                current[source_id] = digest
                prev = previous.get(source_id)
                if prev is None:
                    status = "new"
                elif prev != digest:
                    status = "changed"
                else:
                    status = "unchanged"
                conn.execute(
                    "INSERT INTO scrape_log(source, ok, rows_written, error)"
                    " VALUES(?, 1, 0, ?)",
                    (f"watch:{source_id}", status if status != "unchanged" else None),
                )
                results[source_id] = status
            except (URLError, ValueError, OSError) as e:
                conn.execute(
                    "INSERT INTO scrape_log(source, ok, rows_written, error)"
                    " VALUES(?, 0, 0, ?)",
                    (f"watch:{source_id}", str(e)),
                )
                results[source_id] = f"error: {e}"
        save_hashes(conn, current)
    return results


if __name__ == "__main__":
    r = run_once()
    changed = [k for k, v in r.items() if v in ("changed", "new")]
    for k, v in r.items():
        print(f"  {k}: {v}")
    if changed:
        print(f"\n{len(changed)} source(s) changed — review admin UI.")
