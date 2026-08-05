"""Load OASA fare rows without deleting other operators or translations.

The guarded debug importer only replaces the OASA-owned catalogue sections.
The curated seeder remains the production source of truth.
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

    # Snapshot all curated translations before replacing the OASA rows.
    translation_cache: dict[tuple[str, str], dict] = {}
    try:
        # title_sq / notes_sq exist after migration 0015. Older deploys
        # without it skip silently.
        for row in conn.execute(
            "SELECT section, title_en, title_sq, title_it, validity_sq, validity_it,"
            " notes_el, notes_sq, notes_it FROM fare_products"
        ).fetchall():
            sec, title_en, title_sq, title_it, validity_sq, validity_it, notes_el, notes_sq, notes_it = row
            if any((title_sq, title_it, validity_sq, validity_it, notes_el, notes_sq, notes_it)):
                translation_cache[(sec, title_en)] = {
                    "title_sq": title_sq or "",
                    "title_it": title_it or "",
                    "validity_sq": validity_sq or "",
                    "validity_it": validity_it or "",
                    "notes_el": notes_el or "",
                    "notes_sq": notes_sq or "",
                    "notes_it": notes_it or "",
                }
    except sqlite3.OperationalError:
        pass

    conn.execute("DELETE FROM fare_products WHERE section IN ('single', 'offers', 'airport', 'passes')")

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

    # Reapply translations to rows whose natural key survived.
    if translation_cache:
        restored = 0
        try:
            for (sec, title_en), tr in translation_cache.items():
                cur = conn.execute(
                    "UPDATE fare_products SET title_sq = ?, title_it = ?, validity_sq = ?, validity_it = ?,"
                    " notes_el = ?, notes_sq = ?, notes_it = ?"
                    " WHERE section = ? AND title_en = ?",
                    (
                        tr["title_sq"], tr["title_it"], tr["validity_sq"], tr["validity_it"],
                        tr["notes_el"], tr["notes_sq"], tr["notes_it"], sec, title_en,
                    ),
                )
                restored += cur.rowcount
        except sqlite3.OperationalError:
            restored = 0
        print(f"restored {restored} translated fare rows")

    conn.commit()
    print(f"loaded {n} fare_products rows into {DB_PATH}")


if __name__ == "__main__":
    main()
