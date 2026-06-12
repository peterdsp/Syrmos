"""Canonical station-name resolver shared by every feed ingester.

Lookup order, copied straight from the design spec:

1. exact stations.id match
2. exact stations.name_en (case-insensitive after normalization) match
3. station_name_aliases (source, raw_name) exact match
4. station_name_aliases (source, normalized_name) match
5. None, with an unmatched log line

The CSV at assets/station-aliases.csv is the versioned source of truth for
the alias table. ingest_aliases_from_csv() loads it into SQLite; the
resolver itself never reads the CSV.
"""
from __future__ import annotations

import csv
import re
import sqlite3
import unicodedata
from pathlib import Path

import os

ROOT = Path(os.environ.get("PIPELINE_ROOT", str(Path(__file__).resolve().parent.parent)))
ALIAS_CSV = ROOT / "assets" / "station-aliases.csv"
_MIG_DEV = ROOT / "ops" / "syrmos-api" / "migrations" / "0007_station_name_aliases.sql"
_MIG_PI = ROOT / "migrations" / "0007_station_name_aliases.sql"
ALIAS_MIGRATION = _MIG_DEV if _MIG_DEV.exists() else _MIG_PI


_PUNCT_RE = re.compile(r"[._/]+")
_WS_RE = re.compile(r"\s+")
_DASH_RE = re.compile(r"\s*[-–—]\s*")


def normalize(name: str) -> str:
    """Loose, case-insensitive normalization. Strips Greek diacritics,
    collapses whitespace, unifies dash variants and common punctuation,
    so 'Sygrou - Fix' and 'Syngrou-Fix' both fold to 'syngrou-fix'."""
    if not name:
        return ""
    text = unicodedata.normalize("NFD", name)
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    text = text.replace("ʹ", "").replace("'", "")
    text = _PUNCT_RE.sub(" ", text)
    text = _DASH_RE.sub("-", text)
    text = _WS_RE.sub(" ", text).strip().lower()
    return text


def ingest_aliases_from_csv(conn: sqlite3.Connection) -> int:
    """Replace the aliases table contents with the CSV. Idempotent."""
    conn.executescript(ALIAS_MIGRATION.read_text())
    conn.execute("DELETE FROM station_name_aliases")
    n = 0
    with ALIAS_CSV.open() as f:
        reader = csv.DictReader(f)
        for row in reader:
            if not row.get("source") or row["source"].startswith("#"):
                continue
            raw = row["raw_name"]
            sid = row["canonical_station_id"]
            if not raw or not sid:
                continue
            conn.execute(
                "INSERT OR REPLACE INTO station_name_aliases"
                " (source, raw_name, normalized_name, canonical_station_id, notes)"
                " VALUES (?, ?, ?, ?, ?)",
                (row["source"], raw, normalize(raw), sid, row.get("notes") or None),
            )
            n += 1
    conn.commit()
    return n


def resolve_station_id(conn: sqlite3.Connection, source: str, raw_name: str) -> str | None:
    """Five-step resolver returning the canonical stations.id or None."""
    if not raw_name:
        return None
    key = normalize(raw_name)

    # 1. exact id (handles cases where a feed already uses our id format)
    r = conn.execute("SELECT id FROM stations WHERE id = ?", (raw_name,)).fetchone()
    if r:
        return r[0]

    # 2. exact name_en (normalized comparison)
    for r in conn.execute("SELECT id, name_en FROM stations").fetchall():
        if normalize(r[1]) == key:
            return r[0]

    # 3. (source, raw_name) exact
    r = conn.execute(
        "SELECT canonical_station_id FROM station_name_aliases WHERE source = ? AND raw_name = ?",
        (source, raw_name),
    ).fetchone()
    if r:
        return r[0]

    # 4. (source, normalized_name)
    r = conn.execute(
        "SELECT canonical_station_id FROM station_name_aliases WHERE source = ? AND normalized_name = ?",
        (source, key),
    ).fetchone()
    if r:
        return r[0]

    return None
