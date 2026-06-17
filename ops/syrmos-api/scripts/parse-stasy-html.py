"""Scrape STASY per-station minutes-from-origin tables for M1, M2, M3, T6, T7.

STASY publishes per-direction "STATION | MINUTES" tables inline at
https://www.stasy.gr/en/timetables/{line-1,line-2,line-3,tram}/. The first
two tables on each page are always the outbound and inbound directions,
each row pairs a station name (English) with cumulative minutes from the
origin terminal of that direction. The third table onward is frequency
or first/last departure data the master document already covers.

Output: assets/stasy-html/parsed/station-offsets.jsonl
One JSON record per (line, direction, station, minutes_from_origin):
  {"line_id": "M2", "direction": "outbound",
   "origin": "Anthoupoli", "destination": "Elliniko",
   "stop_sequence": 5, "station_en": "Attiki",
   "minutes_from_origin": 4}

The Tram page yields four direction tables (T6 + T7 in both directions)
which we tag as separate lines via PAGE_PLAN below.
"""
from __future__ import annotations

import json
import re
import sys
import urllib.request
from pathlib import Path

from bs4 import BeautifulSoup

ROOT = Path(__file__).resolve().parent.parent
CACHE_DIR = ROOT / "assets" / "stasy-html"
OUT_DIR = CACHE_DIR / "parsed"
OUT_DIR.mkdir(parents=True, exist_ok=True)

# Per-page slug -> [(table index, line_id, direction, origin_en, destination_en), ...]
# The Tram page has both T6 (tables 0+1) and T7 (tables 2+3) in one HTML.
PAGE_PLAN: dict[str, list[tuple[int, str, str, str, str]]] = {
    "line-1": [
        (0, "M1", "outbound", "Piraeus",     "Kifissia"),
        (1, "M1", "inbound",  "Kifissia",    "Piraeus"),
    ],
    "line-2": [
        (0, "M2", "outbound", "Anthoupoli",  "Elliniko"),
        (1, "M2", "inbound",  "Elliniko",    "Anthoupoli"),
    ],
    "line-3": [
        (0, "M3", "outbound", "Dimotiko Theatro", "Airport"),
        (1, "M3", "inbound",  "Airport",          "Dimotiko Theatro"),
    ],
    "tram": [
        (0, "T6", "outbound", "Syntagma",        "Pikrodafni"),
        (1, "T6", "inbound",  "Pikrodafni",      "Syntagma"),
        (2, "T7", "outbound", "Akti Poseidonos", "Asklipiio Voulas"),
        (3, "T7", "inbound",  "Asklipiio Voulas","Akti Poseidonos"),
    ],
}

URL_TEMPLATE = "https://www.stasy.gr/en/timetables/{slug}/"
TIME_RE = re.compile(r"^\s*(\d{1,2}):(\d{2})\s*$")
INT_RE = re.compile(r"^\s*(\d{1,3})\s*$")


def fetch(slug: str) -> str:
    """Use the cached HTML if present, otherwise GET it."""
    cached = CACHE_DIR / f"{slug}.html"
    if cached.exists():
        return cached.read_text()
    url = URL_TEMPLATE.format(slug=slug)
    req = urllib.request.Request(url, headers={"User-Agent": "syrmos-stasy-fetch/1.0"})
    with urllib.request.urlopen(req, timeout=30) as r:
        html = r.read().decode("utf-8", errors="ignore")
    cached.write_text(html)
    return html


def parse_minutes(cell: str) -> int | None:
    """STASY mixes int minutes (M1/M2/M3) and H:MM strings (Tram). Normalize."""
    cell = cell.strip()
    if not cell or cell == "-":
        return 0
    m = INT_RE.match(cell)
    if m:
        return int(m.group(1))
    m = TIME_RE.match(cell)
    if m:
        return int(m.group(1)) * 60 + int(m.group(2))
    return None


def extract_offset_table(table) -> list[tuple[str, int]]:
    """Pull out [(station_en, minutes_from_origin)] from one offset table."""
    rows = table.find_all("tr")
    out: list[tuple[str, int]] = []
    for r in rows:
        cells = [c.get_text(strip=True) for c in r.find_all(["td", "th"])]
        if len(cells) < 2:
            continue
        name = cells[0]
        if not name or name.upper() in ("STATION", "STATIONS"):
            continue
        mins = parse_minutes(cells[1])
        if mins is None:
            continue
        out.append((name, mins))
    return out


def main() -> None:
    records: list[dict] = []
    for slug, plan in PAGE_PLAN.items():
        html = fetch(slug)
        soup = BeautifulSoup(html, "html.parser")
        tables = soup.find_all("table")
        for table_index, line_id, direction, origin, destination in plan:
            if table_index >= len(tables):
                print(f"  WARN: {slug} t{table_index} missing", file=sys.stderr)
                continue
            entries = extract_offset_table(tables[table_index])
            if not entries:
                print(f"  WARN: {slug} t{table_index} ({line_id} {direction}) empty", file=sys.stderr)
                continue
            for seq, (station, mins) in enumerate(entries):
                records.append({
                    "line_id": line_id,
                    "direction": direction,
                    "origin": origin,
                    "destination": destination,
                    "stop_sequence": seq,
                    "station_en": station.title() if station.isupper() else station,
                    "minutes_from_origin": mins,
                })
            print(f"  {slug} t{table_index} {line_id} {direction}: {len(entries)} stations, "
                  f"runtime {entries[-1][1]} min")

    out_path = OUT_DIR / "station-offsets.jsonl"
    with out_path.open("w") as f:
        for r in records:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"\nwrote {len(records)} offset rows -> {out_path.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
