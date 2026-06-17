"""Load assets/oasa-fares/parsed/fares.jsonl into the syrmos-api DB.

Idempotent: wipes fare_products and re-inserts. Designed to be re-run by
the daily OASA watcher whenever the page hash changes.
"""
from __future__ import annotations

import json
import os
import sqlite3
import sys
from pathlib import Path

ROOT = Path(os.environ.get("PIPELINE_ROOT", str(Path(__file__).resolve().parent.parent)))
JSONL = ROOT / "assets" / "oasa-fares" / "parsed" / "fares.jsonl"
_MIG_DEV = ROOT / "ops" / "syrmos-api" / "migrations" / "0008_fare_products.sql"
_MIG_PI  = ROOT / "migrations" / "0008_fare_products.sql"
MIGRATION = _MIG_DEV if _MIG_DEV.exists() else _MIG_PI
DB_PATH = os.environ.get(
    "SYRMOS_DB_PATH",
    str(ROOT / "ops" / "syrmos-api" / "data" / "syrmos.db"),
)


def main() -> None:
    if not JSONL.exists():
        print(f"missing: {JSONL}", file=sys.stderr)
        sys.exit(1)
    conn = sqlite3.connect(DB_PATH)
    conn.execute("PRAGMA foreign_keys = ON")
    conn.executescript(MIGRATION.read_text())

    # The OASA prices page renders titles via JavaScript and the scraped
    # output drifts between Title Case and ALL CAPS across page revisions
    # and produces phantom duplicate rows (e.g. eight "DAY PASS" entries).
    # Until the parser is hardened, the source of truth for the catalogue
    # is scripts/seed_fare_products.py + seed_fare_products_albanian.py.
    # Set SYRMOS_OASA_INGEST_ENABLED=1 to opt back into the auto-ingest
    # path for debugging.
    import os
    if os.environ.get("SYRMOS_OASA_INGEST_ENABLED") != "1":
        print("ingest disabled (set SYRMOS_OASA_INGEST_ENABLED=1 to enable)")
        return

    # Snapshot Albanian translations BEFORE wiping the table. Reapplied
    # after the fresh insert so a watcher tick doesn't nuke the Sq
    # columns. Keys on (section, title_en) which is the natural identity
    # of an OASA product. New products that the scraper finds will get
    # empty Sq columns until the Albanian seed runs again.
    sq_cache: dict[tuple[str, str], dict] = {}
    try:
        # title_sq / notes_sq exist after migration 0015. Older deploys
        # without it skip silently.
        for row in conn.execute(
            "SELECT section, title_en, title_sq, notes_sq FROM fare_products"
        ).fetchall():
            sec, title_en, title_sq, notes_sq = row
            if title_sq or notes_sq:
                sq_cache[(sec, title_en)] = {
                    "title_sq": title_sq or "",
                    "notes_sq": notes_sq or "",
                }
    except sqlite3.OperationalError:
        pass

    conn.execute("DELETE FROM fare_products")

    n = 0
    with JSONL.open() as f:
        for sort_order, line in enumerate(f):
            rec = json.loads(line)
            conn.execute(
                "INSERT INTO fare_products"
                " (section, title_en, title_el, full_price_eur, discounted_price_eur,"
                "  validity, notes, tags, sort_order)"
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (
                    rec.get("section") or "single",
                    rec["title_en"],
                    rec.get("title_el") or "",
                    rec.get("full_price_eur"),
                    rec.get("discounted_price_eur"),
                    rec.get("validity") or "",
                    rec.get("notes") or "",
                    ",".join(rec.get("tags") or []),
                    sort_order,
                ),
            )
            n += 1

    # Re-apply the cached Albanian translations on rows whose key
    # survived. Rows the scraper renamed (e.g. an OASA wording tweak)
    # naturally lose their translation here; admin can either rename
    # the seed key or hand-edit the row.
    if sq_cache:
        restored = 0
        try:
            for (sec, title_en), tr in sq_cache.items():
                cur = conn.execute(
                    "UPDATE fare_products SET title_sq = ?, notes_sq = ?"
                    " WHERE section = ? AND title_en = ?",
                    (tr["title_sq"], tr["notes_sq"], sec, title_en),
                )
                restored += cur.rowcount
        except sqlite3.OperationalError:
            restored = 0
        print(f"restored {restored} Albanian translations")

    conn.commit()
    print(f"loaded {n} fare_products rows into {DB_PATH}")


if __name__ == "__main__":
    main()
