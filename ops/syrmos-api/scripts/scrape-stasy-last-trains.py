#!/usr/bin/env python3
"""Scrape per-station last-train tables from stasy.gr.

STASY publishes a tabular schedule for each metro line at
https://www.stasy.gr/en/timetables/line-N/ that lists, per station and
direction, the first train, the last train, and (when applicable) the
last short-turn train with its intermediate terminus, e.g.
"LAST (UP TO OMONIA STATION)" on M1. This data is the only published
source for the actual destinations of late-night trains; the OASA
schedule_bands aggregate everything to the line terminal.

Output rows go into the `last_train_endpoints` table (migration 0017).
Each row is keyed by (line_id, day_type, direction, from_station_id,
time) so re-runs are idempotent.
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
USER_AGENT = "syrmos/stasy-last-train-scraper (+https://syrmos.peterdsp.dev)"

# Map STASY's URL slug -> our internal line_id. STASY publishes line-1, 2, 3
# (metro) and line-6, 7 (tram). The metro lines map directly; trams use the
# T prefix.
LINE_SOURCES = {
    "M1": "https://www.stasy.gr/en/timetables/line-1/",
    "M2": "https://www.stasy.gr/en/timetables/line-2/",
    "M3": "https://www.stasy.gr/en/timetables/line-3/",
    "T6": "https://www.stasy.gr/en/timetables/line-6/",
    "T7": "https://www.stasy.gr/en/timetables/line-7/",
}

# STASY's table titles encode direction. We key the desired entries on a
# normalized "outbound" / "inbound" so the projector lookup matches the
# rest of the bands schema. The first listed terminal for each line is
# treated as outbound, the second as inbound (matching SyrmosData.lines).
LINE_DIRECTIONS = {
    "M1": (("PIRAEUS", "KIFISSIA"), ("KIFISSIA", "PIRAEUS")),
    "M2": (("ANTHOUPOLI", "ELLINIKO"), ("ELLINIKO", "ANTHOUPOLI")),
    "M3": (("DIMOTIKO THEATRO", "AIRPORT"), ("AIRPORT", "DIMOTIKO THEATRO")),
    "T6": (("SYNTAGMA", "PIKRODAFNI"), ("PIKRODAFNI", "SYNTAGMA")),
    "T7": (("AKTI POSEIDONOS", "ASKLIPIIO VOULAS"), ("ASKLIPIIO VOULAS", "AKTI POSEIDONOS")),
}

# Maps STASY's all-caps station names to our station ids. Built per line
# because the same name appears under different ids when it's a transfer.
STATION_NAME_TO_ID = {
    "M1": {
        "PIRAEUS": "M1_PIR", "FALIRO": "M1_FAL", "MOSCHATO": "M1_MOS",
        "KALLITHEA": "M1_KAL", "TAVROS": "M1_TAV", "PETRALONA": "M1_PET",
        "THISSIO": "M1_THI", "MONASTIRAKI": "M1_MON", "OMONIA": "M1_OMO",
        "VICTORIA": "M1_VIC", "ATTIKI": "M1_ATT", "AGHIOS NIKOLAOS": "M1_ANI",
        "KATO PATISSIA": "M1_KPA", "AGHIOS ELEFTHERIOS": "M1_AEL",
        "ANO PATISSIA": "M1_APA", "PERISSOS": "M1_PER", "PEFKAKIA": "M1_PEF",
        "NEA IONIA": "M1_NIO", "IRAKLIO": "M1_IRK", "IRINI": "M1_IRI",
        "NERATZIOTISSA": "M1_NER", "MAROUSSI": "M1_MAR", "KAT": "M1_KAT",
        "KIFISSIA": "M1_KIF",
    },
    "M2": {
        "ANTHOUPOLI": "M2_ANT", "PERISTERI": "M2_PER", "AGHIOS ANTONIOS": "M2_AAN",
        "SEPOLIA": "M2_SEP", "ATTIKI": "M2_ATT", "LARISSA STATION": "M2_LAR",
        "METAXOURGEIO": "M2_MTX", "OMONIA": "M2_OMO", "PANEPISTIMIO": "M2_PAN",
        "SYNTAGMA": "M2_SYN", "AKROPOLI": "M2_AKR", "SYGGROU FIX": "M2_SYG",
        "NEOS KOSMOS": "M2_NKO", "AGHIOS IOANNIS": "M2_AIO", "DAFNI": "M2_DAF",
        "AGHIOS DIMITRIOS": "M2_ADI", "ILIOUPOLI": "M2_ILI", "ALIMOS": "M2_ALI",
        "ARGYROUPOLI": "M2_ARG", "ELLINIKO": "M2_ELL",
    },
    "M3": {
        "DIMOTIKO THEATRO": "M3_DIM", "AGIA VARVARA": "M3_AVA", "KORYDALLOS": "M3_KOR",
        "NIKAIA": "M3_NIK", "MANIATIKA": "M3_MAN", "PIRAEUS": "M3_PIR",
        "KERAMEIKOS": "M3_KER", "MONASTIRAKI": "M3_MON", "SYNTAGMA": "M3_SYN",
        "EVANGELISMOS": "M3_EVA", "MEGARO MOUSIKIS": "M3_MEG", "AMBELOKIPOI": "M3_AMB",
        "PANORMOU": "M3_PAN", "KATEHAKI": "M3_KAT", "ETHNIKI AMYNA": "M3_ETH",
        "HOLARGOS": "M3_HOL", "NOMISMATOKOPIO": "M3_NOM", "AGIA PARASKEVI": "M3_APA",
        "HALANDRI": "M3_HAL", "DOUKISSIS PLAKENTIAS": "M3_DPL",
        "PALLINI": "M3_PAL", "PEANIA-KANTZA": "M3_PEK", "KOROPI": "M3_KRP",
        "AIRPORT": "M3_AER",
    },
    "T6": {
        # T6 stop names are long; only seed the ones the projector currently
        # surfaces. The scraper falls back gracefully when a name doesn't
        # match by skipping the row.
        "SYNTAGMA": "T6_SYN", "PIKRODAFNI": "T6_PIK",
    },
    "T7": {
        "AKTI POSEIDONOS": "T7_AKT", "ASKLIPIIO VOULAS": "T7_ASK",
    },
}

# STASY's tables are published as a single page per line. Each table is
# scoped by a "title" attribute we can read to know the direction.
DIRECTION_TITLE_PAT = re.compile(
    r'data-original-value="DEPARTURES (?P<from>[A-Z\s]+) - (?P<to>[A-Z\s]+)"',
    re.I,
)


def fetch(url: str) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read().decode("utf-8", errors="replace")


def parse_line(line_id: str, html: str) -> list[dict]:
    """Find every "FIRST / LAST / LAST (UP TO X STATION)" table and return
    one normalized row per (direction, from_station, time)."""
    out: list[dict] = []
    # The table cells render as data-original-value="..." attributes. We
    # locate each "FROM STATION" header and walk forward until we see
    # another STATION header or run out of cells.
    cell_pat = re.compile(
        r'data-cell-id="([A-Z]+)(\d+)"[^>]*data-original-value="([^"]*)"'
    )
    # Group all cells with their (col_letter, row_number, value).
    cells = [(m.group(1), int(m.group(2)), unescape(m.group(3))) for m in cell_pat.finditer(html)]
    if not cells:
        return out

    # A table is a contiguous run of rows where row 1 contains headers. We
    # detect a table boundary whenever cell ('A', 1, ...) appears.
    tables: list[list[tuple[str, int, str]]] = []
    current: list[tuple[str, int, str]] = []
    for c in cells:
        if c[0] == "A" and c[1] == 1 and current:
            tables.append(current)
            current = []
        current.append(c)
    if current:
        tables.append(current)

    # We only care about tables whose first row contains "FIRST" and "LAST"
    # in the header — those are the first/last-train tables. Travel-time
    # tables have "STATION" and "MINUTES" as headers and are skipped.
    name_to_id = STATION_NAME_TO_ID.get(line_id, {})
    line_dirs = LINE_DIRECTIONS.get(line_id, ())
    for table in tables:
        by_row: dict[int, dict[str, str]] = {}
        for col, row, val in table:
            by_row.setdefault(row, {})[col] = val
        header = by_row.get(1, {})
        header_vals = " | ".join(header.get(c, "") for c in sorted(header))
        if "FIRST" not in header_vals.upper() or "LAST" not in header_vals.upper():
            continue
        # Direction inference: look at the first non-empty data row's
        # FROM STATION value and match against LINE_DIRECTIONS.
        row_data = [by_row[r] for r in sorted(by_row) if r > 1]
        if not row_data:
            continue
        first_station = (row_data[0].get("A") or "").strip().upper()
        last_station = (row_data[-1].get("A") or "").strip().upper()
        # STASY's last row is "ARRIVAL AT X STATION" rather than the bare
        # station name, so we substring-match the terminal against it and
        # rely on the first row's exact match for direction inference.
        direction: str | None = None
        end_station_default: str | None = None
        outbound, inbound = line_dirs
        if first_station == outbound[0] and outbound[1] in last_station:
            direction = "outbound"
            end_station_default = name_to_id.get(outbound[1])
        elif first_station == inbound[0] and inbound[1] in last_station:
            direction = "inbound"
            end_station_default = name_to_id.get(inbound[1])
        if direction is None or end_station_default is None:
            continue

        # Map header columns -> what each column represents. Column 0 is
        # "FROM STATION", column 1 is "FIRST", column 2 is "LAST", and any
        # subsequent column is a short-turn destination encoded in the
        # header like "LAST (UP TO OMONIA STATION)".
        col_kind: dict[str, tuple[str, str | None]] = {}
        for col, val in header.items():
            up = val.upper()
            if "FROM STATION" in up or "STATION" == up:
                col_kind[col] = ("from_station", None)
            elif up == "FIRST":
                col_kind[col] = ("first", None)
            elif up == "LAST":
                col_kind[col] = ("last", end_station_default)
            elif up.startswith("LAST (UP TO"):
                m = re.search(r"UP TO ([A-Z\s]+?)(?:\s*STATION)?\)", up)
                short_dest = m.group(1).strip() if m else None
                short_id = name_to_id.get(short_dest) if short_dest else None
                if short_id:
                    col_kind[col] = ("short", short_id)
                else:
                    col_kind[col] = ("ignore", None)

        for row in row_data:
            station_name = (row.get(next(c for c, k in col_kind.items() if k[0] == "from_station"), "") or "").strip().upper()
            from_id = name_to_id.get(station_name)
            if not from_id:
                continue
            for col, val in row.items():
                kind, end_id = col_kind.get(col, ("ignore", None))
                if kind in ("ignore", "from_station"):
                    continue
                time_str = (val or "").strip()
                if not re.fullmatch(r"\d{2}:\d{2}", time_str):
                    continue
                if kind == "first":
                    # We don't store FIRST rows currently — the projector
                    # uses rule.openTime + band emission for first slots.
                    continue
                if end_id is None:
                    continue
                # STASY publishes one table per line per direction with
                # one row per day_type combined. We tag everything as
                # mon_thu since the published table represents weekday
                # service. (Future enhancement: scrape per-day tables.)
                out.append({
                    "line_id": line_id,
                    "day_type": "mon_thu",
                    "direction": direction,
                    "from_station_id": from_id,
                    "time": time_str,
                    "end_station_id": end_id,
                    "label": kind,
                })
    return out


def write_rows(conn: sqlite3.Connection, rows: list[dict], source_url: str) -> int:
    now = datetime.now(timezone.utc).isoformat()
    written = 0
    for r in rows:
        conn.execute(
            """INSERT INTO last_train_endpoints
               (line_id, day_type, direction, from_station_id, time, end_station_id, label, source, fetched_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
               ON CONFLICT (line_id, day_type, direction, from_station_id, time)
               DO UPDATE SET end_station_id = excluded.end_station_id,
                             label = excluded.label,
                             source = excluded.source,
                             fetched_at = excluded.fetched_at""",
            (
                r["line_id"], r["day_type"], r["direction"], r["from_station_id"],
                r["time"], r["end_station_id"], r["label"], source_url, now,
            ),
        )
        written += 1
    conn.commit()
    return written


def main() -> int:
    if not os.path.exists(DB_PATH):
        print(f"DB not found at {DB_PATH}", file=sys.stderr)
        return 1
    conn = sqlite3.connect(DB_PATH)
    total = 0
    for line_id, url in LINE_SOURCES.items():
        try:
            html = fetch(url)
        except Exception as e:
            print(f"  {line_id}: fetch failed: {e}", file=sys.stderr)
            continue
        rows = parse_line(line_id, html)
        n = write_rows(conn, rows, url)
        total += n
        print(f"  {line_id}: {n} rows from {url}")
    print(f"done: {total} rows")
    return 0


if __name__ == "__main__":
    sys.exit(main())
