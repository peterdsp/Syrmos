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
    # Tram lines T6 + T7 share the same STASY page; parse_line scopes by
    # the direction-title row to split them.
    "TRAM": "https://www.stasy.gr/en/timetables/tram/",
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
        "SYNTAGMA": "T6_SYN", "ZAPPIO": "T6_ZAP", "FIX": "T6_FIX",
        "KASOMOULI": "T6_KAS", "NEOS KOSMOS": "T6_NEO", "PANAGITSA": "T6_PAN",
        "AGIOS IOANNIS": "T6_AGI", "DAFNI": "T6_DAF", "AGHIA PARASKEVI": "T6_APA",
        "AEGEOU": "T6_AEG", "EDEM": "T6_EDE", "AMFITHEAS": "T6_AMF",
        "PIKRODAFNI": "T6_PIK",
    },
    "T7": {
        "AKTI POSEIDONOS": "T7_AKT", "ASKLIPIIO VOULAS": "T7_ASK",
        "S.E.F.": "T7_SEF", "NEO FALIRO": "T7_NEO", "MOSCHATO": "T7_MOS",
        "KALLITHEA": "T7_KAL", "TROCADERO": "T7_TRO", "BATIS": "T7_BAT",
        "ELLINIKO": "T7_ELL", "AGHIOS ALEXANDROS": "T7_AAL",
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

    # A table is a contiguous run of cells where the smallest data-y is the
    # header row. STASY publishes some pages (metro) with the header at
    # y=1 and others (tram) with a title row at y=1 and the actual header
    # at y=2. Split tables on every reset to column A row 1.
    tables: list[list[tuple[str, int, str]]] = []
    current: list[tuple[str, int, str]] = []
    for c in cells:
        if c[0] == "A" and c[1] == 1 and current:
            tables.append(current)
            current = []
        current.append(c)
    if current:
        tables.append(current)

    # We only care about tables whose header row contains "FIRST" + "LAST".
    # Travel-time tables (STATION/MINUTES) are skipped. The header row may
    # be y=1 (metro) or y=2 (tram), so detect it dynamically.
    for table in tables:
        by_row: dict[int, dict[str, str]] = {}
        for col, row, val in table:
            by_row.setdefault(row, {})[col] = val
        # Find the row that holds FIRST + LAST column labels.
        header_row_idx = None
        for r in sorted(by_row):
            vals = " | ".join(by_row[r].values()).upper()
            if "FIRST" in vals and "LAST" in vals:
                header_row_idx = r
                break
        if header_row_idx is None:
            continue
        header = by_row[header_row_idx]
        # Data rows are everything below the header.
        row_data = [by_row[r] for r in sorted(by_row) if r > header_row_idx]
        if not row_data:
            continue

        # Tram page lists T6 and T7 as separate tables on one URL; each
        # table's first data row's "FROM STATION" tells us which line.
        # Resolve the line_id_to_use per-table.
        line_id_to_use, name_to_id, line_dirs = _resolve_line(line_id, row_data)
        if line_id_to_use is None or name_to_id is None or not line_dirs:
            continue

        # Direction inference only needs the first row's station name —
        # the last row varies between "ARRIVAL AT X STATION" (M1) and
        # OASA footnotes ("Note: ...") on other lines. The first row is
        # always the table's starting terminal.
        first_station = (row_data[0].get("A") or "").strip().upper()
        direction: str | None = None
        end_station_default: str | None = None
        outbound, inbound = line_dirs
        if first_station == outbound[0]:
            direction = "outbound"
            end_station_default = name_to_id.get(outbound[1])
        elif first_station == inbound[0]:
            direction = "inbound"
            end_station_default = name_to_id.get(inbound[1])
        if direction is None or end_station_default is None:
            continue

        # Map header columns -> (kind, day_types, end_station_override).
        # kind: "from_station" / "first" / "last" / "short" / "ignore"
        # day_types: list of day_type strings this column applies to
        # end_station_override: short-turn terminal when kind == "short",
        #   otherwise None (last regular train uses line terminal).
        col_kind: dict[str, tuple[str, list[str], str | None]] = {}
        for col, val in header.items():
            up = val.upper()
            if "FROM STATION" in up or up == "STATION":
                col_kind[col] = ("from_station", [], None)
                continue
            if "FIRST" in up:
                col_kind[col] = ("first", _qualifier_day_types(up), None)
                continue
            if up.startswith("LAST"):
                short_m = re.search(r"UP TO ([A-Z\s]+?)(?:\s*STATION)?\)", up)
                if short_m:
                    short_dest = short_m.group(1).strip()
                    short_id = name_to_id.get(short_dest)
                    if short_id:
                        col_kind[col] = ("short", _qualifier_day_types(up), short_id)
                    else:
                        col_kind[col] = ("ignore", [], None)
                elif "AIRPORT" in up:
                    # M3 has a "LAST (AIRPORT)" column meaning the last
                    # train on the airport branch. Routed under M3_AIR.
                    col_kind[col] = ("last_airport", _qualifier_day_types(up), end_station_default)
                else:
                    col_kind[col] = ("last", _qualifier_day_types(up), end_station_default)
                continue
            col_kind[col] = ("ignore", [], None)

        from_col = next((c for c, k in col_kind.items() if k[0] == "from_station"), None)
        if from_col is None:
            continue

        for row in row_data:
            station_name = (row.get(from_col, "") or "").strip().upper()
            from_id = name_to_id.get(station_name)
            if not from_id:
                continue
            for col, val in row.items():
                kind, day_types, end_override = col_kind.get(col, ("ignore", [], None))
                if kind in ("ignore", "from_station", "first"):
                    continue
                time_str = (val or "").strip()
                if not re.fullmatch(r"\d{2}:\d{2}", time_str):
                    continue
                end_id = end_override if kind == "short" else end_station_default
                if end_id is None:
                    continue
                target_line = "M3_AIR" if kind == "last_airport" else line_id_to_use
                normalized_label = "short" if kind == "short" else "last"
                for dt in (day_types or ["mon_thu"]):
                    out.append({
                        "line_id": target_line,
                        "day_type": dt,
                        "direction": direction,
                        "from_station_id": from_id,
                        "time": time_str,
                        "end_station_id": end_id,
                        "label": normalized_label,
                    })
    return out


def _qualifier_day_types(header_upper: str) -> list[str]:
    """Parse the parenthetical on a FIRST/LAST column header and return
    the matching day_type strings. Unrecognised qualifiers (including
    bare 'FIRST' / 'LAST') default to mon_thu, which is OASA's regular
    weekday slot."""
    # Strip everything before the open paren
    m = re.search(r"\(([^)]+)\)", header_upper)
    if not m:
        return ["mon_thu"]
    q = m.group(1).strip()
    # Normalize separators
    q = q.replace("&AMP;", "&").replace("&", "&").replace("  ", " ")
    types: list[str] = []
    if "MONDAY" in q and ("THURSDAY" in q or "FRIDAY" in q):
        types.append("mon_thu")
    if "FRIDAY" in q and "MONDAY" not in q:
        types.append("fri")
    if "SUNDAY" in q:
        types.append("sun")
    if "SATURDAY" in q:
        types.append("sat")
    if "AIRPORT" in q and not types:
        # Bare "(AIRPORT)" header: use the regular weekday slot.
        types.append("mon_thu")
    return types or ["mon_thu"]


def _resolve_line(scrape_key: str, row_data: list[dict]) -> tuple[str | None, dict | None, tuple | None]:
    """Pick the line_id, station map, and direction tuple for a given
    table. For metro pages the scrape key is the line id directly. The
    tram page mixes T6 + T7 in successive tables; the first data row's
    station name decides which line this table belongs to."""
    if scrape_key in ("M1", "M2", "M3"):
        return scrape_key, STATION_NAME_TO_ID.get(scrape_key), LINE_DIRECTIONS.get(scrape_key)
    if scrape_key == "TRAM":
        first_name = (row_data[0].get("A") or "").strip().upper()
        for candidate in ("T6", "T7"):
            terms = STATION_NAME_TO_ID.get(candidate, {})
            if first_name in terms:
                return candidate, terms, LINE_DIRECTIONS.get(candidate)
    return None, None, None


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
