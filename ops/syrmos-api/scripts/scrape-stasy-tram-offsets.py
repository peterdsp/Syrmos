#!/usr/bin/env python3
"""One-shot scraper for T6 + T7 per-station travel times.

Parses the per-direction "STATION | MINUTES" tables on
https://www.stasy.gr/en/timetables/tram/ and upserts rows into
the `station_offsets` table. Resolves STASY's all-caps station
names to our station ids via a hand-curated map (English names
don't always match — Lambraki vs Grigoriou Lambraki, Helliniko
vs Elliniko, etc.) backed by a substring fallback.

Why: api/station-offsets ships zero rows for T7, so the projector
puts every T7 train at the line origin no matter which stop the
user is asking about. After this script T7 inbound + outbound
both carry their full 37 / 43-stop offset tables and the live
position interpolation on the map starts to track.
"""
from __future__ import annotations

import os
import re
import sqlite3
import sys
import urllib.request
from datetime import datetime, timezone
from html import unescape

DB_PATH = os.environ.get("SYRMOS_DB_PATH", "/home/peterdsp/syrmos-api/db/syrmos.db")
TRAM_URL = "https://www.stasy.gr/en/timetables/tram/"
USER_AGENT = "syrmos/stasy-tram-offsets-scraper (+https://syrmos.peterdsp.dev)"

# STASY's per-station travel-time tables key by direction-title row at y=1
# (e.g. "SYNTAGMA - PIKRODAFNI (T6)"). The actual data starts at y=3 with
# the FROM STATION column carrying the canonical station name. We match
# direction by the (origin, terminal) pair in the title.
DIRECTION_MAP: dict[tuple[str, str], tuple[str, str, str, str]] = {
    # key = (first row station name, last row station name)
    # val = (line_id, direction, origin, destination)
    ("SYNTAGMA", "PIKRODAFNI"): ("T6", "outbound", "Syntagma", "Pikrodafni"),
    ("PIKRODAFNI", "SYNTAGMA"): ("T6", "inbound", "Pikrodafni", "Syntagma"),
    ("AKTI POSIDONOS", "ASKLIPIIO VOULAS"): ("T7", "outbound", "Akti Posidonos", "Asklipiio Voulas"),
    ("ASKLIPIIO VOULAS", "AKTI POSIDONOS"): ("T7", "inbound", "Asklipiio Voulas", "Akti Posidonos"),
}

# Explicit STASY name -> station_id overrides for cases where
# substring matching fails or is ambiguous. Empty entries fall through
# to substring matching against the stations.name_en column.
NAME_OVERRIDES: dict[tuple[str, str], str] = {
    # T6
    ("T6", "SYNTAGMA"): "T6_SYN",
    ("T6", "ZAPPIO"): "T6_ZAP",
    ("T6", "L. VOULIAGMENIS"): "T6_VOU",
    ("T6", "FIX"): "T6_FIX",
    ("T6", "KASOMOULI"): "T6_KAS",
    ("T6", "NEOS KOSMOS"): "T6_NEO",
    ("T6", "BAKNANA"): "T6_BAK",
    ("T6", "AEGEOU"): "T6_AEG",
    ("T6", "AGHIA FOTINI"): "T6_AGI",
    ("T6", "MEGALOU ALEXANDROU"): "T6_ALE",
    ("T6", "AGHIA PARASKEVI"): "T6_AGH",
    ("T6", "MEDEAS - MYKALIS"): "T6_MED",
    ("T6", "EVANGELIKI SCHOLI"): "T6_EVA",
    ("T6", "ACHILLEOS"): "T6_ACH",
    ("T6", "AMFITHEAS"): "T6_AMF",
    ("T6", "PANAGHITSA"): "T6_PAN",
    ("T6", "MOUSSON"): "T6_MOU",
    ("T6", "EDEM"): "T6_EDE",
    ("T6", "PIKRODAFNI"): "T6_PIK",
    # T7 — order matches STASY's outbound table (Akti Posidonos -> Asklipiio
    # Voulas) for clarity; entries are direction-agnostic via the key.
    ("T7", "AKTI POSIDONOS"): "T7_AKT",
    ("T7", "AGHIA TRIADA"): "T7_AGI",
    ("T7", "PLATIA IPPODAMIAS"): "T7_PL2",
    ("T7", "34OU SYNTAGMATOS"): "T7_SYN",
    ("T7", "ANDROUTSOU"): "T7_AND",
    ("T7", "SKYLITSI"): "T7_OMI",
    ("T7", "S.E.F."): "T7_PEA",
    ("T7", "NEO FALIRO"): "T7_NEO",
    ("T7", "MOSCHATO"): "T7_MOS",
    ("T7", "KALLITHEA"): "T7_KAL",
    ("T7", "TZITZIFIES"): "T7_TZI",
    ("T7", "DELTA FALIROU"): "T7_DEL",
    ("T7", "AGHIA SKEPI"): "T7_AGH",
    ("T7", "TROCADERO"): "T7_TRO",
    ("T7", "PARKO FLISVOU"): "T7_PAR",
    ("T7", "FLISVOS"): "T7_FLI",
    ("T7", "BATIS"): "T7_BAT",
    ("T7", "EDEM"): "T7_EDE",
    ("T7", "PIKRODAFNI"): "T7_PIK",
    ("T7", "MARINA ALIMOU"): "T7_MAR",
    ("T7", "KALAMAKI"): "T7_KA2",
    ("T7", "ZEFYROS"): "T7_ZEF",
    ("T7", "LOUTRA ALIMOU"): "T7_LOU",
    ("T7", "ELLINIKO"): "T7_ELL",
    ("T7", "HELLINIKO"): "T7_ELL",
    ("T7", "1ST AG. KOSMA"): "T7_STA",
    ("T7", "1ST AGHIOU KOSMA"): "T7_STA",
    ("T7", "2ND AG. KOSMA"): "T7_NDA",
    ("T7", "2ND AGHIOU KOSMA"): "T7_NDA",
    ("T7", "AGHIOS ALEXANDROS"): "T7_AG2",
    ("T7", "HELLINON OLYMPIONIKON"): "T7_EL2",
    ("T7", "KENTRO ISTIOPLOIAS"): "T7_KEN",
    ("T7", "PLATIA VERGOTI"): "T7_PL3",
    ("T7", "PARALIA GLYFADAS"): "T7_PA2",
    ("T7", "PALEO DIMARHIO"): "T7_PAL",
    ("T7", "PLATIA KATRAKI"): "T7_PL4",
    ("T7", "AGG. METAXA"): "T7_AG3",
    ("T7", "ANGELOU METAXA"): "T7_AG3",
    ("T7", "PLATIA ESPERIDON"): "T7_PL5",
    ("T7", "KOLYMVITIRIO"): "T7_KOL",
    ("T7", "ASKLIPIIO VOULAS"): "T7_ASK",
    # Footer/typo variants seen on the live page
    ("T7", "AKTI POSIDONOS"): "T7_AKT",
    ("T7", "AKTI POSEIDONOS"): "T7_AKT",
    ("T7", "DIMARHIO"): "T7_DIM",
    ("T7", "PLATIA DELIGIANNI"): "T7_PLA",
    ("T7", "EVANGELISTRIA"): "T7_EVA",
    ("T7", "LAMBRAKI"): "T7_GRI",
    ("T7", "GRIGORIOU LAMBRAKI"): "T7_GRI",
    ("T7", "MIKRAS ASIAS"): "T7_MIK",
    ("T7", "GIPEDO KARAISKAKI"): "T7_GIP",
}


def fetch(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read().decode("utf-8", errors="replace")


def parse_tram_offsets(html: str) -> list[dict]:
    """Walk every per-station travel-time table on the tram page and emit
    upsert-ready rows for station_offsets."""
    out: list[dict] = []
    cell_pat = re.compile(
        r'data-cell-id="([A-Z]+)(\d+)"[^>]*?data-original-value="([^"]*)"'
    )
    cells = [
        (m.group(1), int(m.group(2)), unescape(m.group(3)))
        for m in cell_pat.finditer(html)
    ]
    tables: list[list[tuple[str, int, str]]] = []
    cur: list[tuple[str, int, str]] = []
    for c in cells:
        if c[0] == "A" and c[1] == 1 and cur:
            tables.append(cur)
            cur = []
        cur.append(c)
    if cur:
        tables.append(cur)

    for table in tables:
        by_row: dict[int, dict[str, str]] = {}
        for col, row, val in table:
            by_row.setdefault(row, {})[col] = val
        # Per-station travel time tables have header "STATION | MINUTES" or
        # "STATIONS | MINUTES" at y=1 (no title row). The direction is
        # implicit in the first vs last data row — STASY publishes them in
        # pairs across the page. We use (first_station, last_station) to
        # look up the line/direction.
        header = by_row.get(1, {})
        header_text = " | ".join(v.upper() for v in header.values())
        if "STATION" not in header_text or "MINUTES" not in header_text:
            continue
        rows = [by_row[r] for r in sorted(by_row) if r > 1]
        if len(rows) < 3:
            continue
        first_name = (rows[0].get("A") or "").strip().upper()
        last_name = (rows[-1].get("A") or "").strip().upper()
        meta = DIRECTION_MAP.get((first_name, last_name))
        if not meta:
            continue
        line_id, direction, origin, destination = meta

        seq_counter = 0
        for row in rows:
            station_name = (row.get("A") or "").strip().upper()
            minutes_raw = (row.get("B") or "").strip()
            # STASY publishes "00" for the origin and bare integers for the
            # rest; sometimes "0:00" / "0:02" style for T7.
            if ":" in minutes_raw:
                hh, mm = minutes_raw.split(":")
                try:
                    mins = int(hh) * 60 + int(mm)
                except ValueError:
                    continue
            else:
                try:
                    mins = int(minutes_raw)
                except ValueError:
                    continue
            station_id = NAME_OVERRIDES.get((line_id, station_name))
            if station_id is None:
                if "*" not in station_name and "NOTE" not in station_name:
                    print(f"  WARN: unmapped {line_id} station {station_name!r}", file=sys.stderr)
                continue
            out.append({
                "line_id": line_id,
                "direction": direction,
                "origin": origin,
                "destination": destination,
                "station_id": station_id,
                # Stable English name from the STASY page, title-cased so
                # it reads naturally in any downstream UI.
                "station_en": station_name.title(),
                "stop_sequence": seq_counter,
                "minutes_from_origin": mins,
            })
            seq_counter += 1
    return out


def write_rows(conn: sqlite3.Connection, rows: list[dict]) -> int:
    # station_offsets UNIQUE is (line_id, direction, stop_sequence). Wipe
    # the (line, direction) rows we're about to write first so the upsert
    # collisions are bounded to the new set and we don't leave stale rows
    # from a previous import with a different sequence.
    affected = {(r["line_id"], r["direction"]) for r in rows}
    for line_id, direction in affected:
        conn.execute(
            "DELETE FROM station_offsets WHERE line_id=? AND direction=?",
            (line_id, direction),
        )
    now = datetime.now(timezone.utc).isoformat()
    written = 0
    for r in rows:
        conn.execute(
            """INSERT INTO station_offsets
               (line_id, direction, origin, destination, stop_sequence,
                station_en, station_id, minutes_from_origin, source, fetched_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (
                r["line_id"], r["direction"], r["origin"], r["destination"],
                r["stop_sequence"], r["station_en"], r["station_id"],
                r["minutes_from_origin"], TRAM_URL, now,
            ),
        )
        written += 1
    conn.commit()
    return written


def main() -> int:
    if not os.path.exists(DB_PATH):
        print(f"DB not found at {DB_PATH}", file=sys.stderr)
        return 1
    print(f"fetching {TRAM_URL}")
    html = fetch(TRAM_URL)
    rows = parse_tram_offsets(html)
    by_line_dir: dict[tuple[str, str], int] = {}
    for r in rows:
        k = (r["line_id"], r["direction"])
        by_line_dir[k] = by_line_dir.get(k, 0) + 1
    print(f"parsed {len(rows)} rows:")
    for k, v in sorted(by_line_dir.items()):
        print(f"  {k}: {v}")
    conn = sqlite3.connect(DB_PATH)
    n = write_rows(conn, rows)
    print(f"upserted {n} rows into station_offsets")
    return 0


if __name__ == "__main__":
    sys.exit(main())
