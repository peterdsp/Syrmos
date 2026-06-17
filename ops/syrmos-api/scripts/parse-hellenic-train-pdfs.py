"""Parse Hellenic Train suburban schedule PDFs into structured per-train
timestamps.

Source PDFs live in assets/hellenic-train-pdfs/, downloaded from
https://www.hellenictrain.gr/en/athens-suburban-and-regional-railway

Output: assets/hellenic-train-pdfs/parsed/{slug}.jsonl
One JSON record per train:
{
  "source_pdf": "PIRAEUS-ATHENS-LIOSIA-AIRPORT_MON-FRI_from_221125_0.pdf",
  "direction": "outbound",          # outbound = Piraeus->Airport, inbound = Airport->Piraeus
  "day_type": "mon_fri",
  "train_no": "1202",
  "stops": [
    {"station_en": "Piraeus", "station_el": "Πειραιάς", "time": "05:43"},
    ...
  ]
}

Conventions:
- Times in source are dot-separated 24h (4.03 = 04:03). Normalized to HH:MM.
- Empty cells = train does not call at that station.
- Trains with fewer than 2 stops are dropped (parsing noise).
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Iterable

import pdfplumber

ROOT = Path(__file__).resolve().parent.parent
PDF_DIR = ROOT / "assets" / "hellenic-train-pdfs"
OUT_DIR = PDF_DIR / "parsed"
OUT_DIR.mkdir(parents=True, exist_ok=True)

# (filename, direction, day_type) for each PDF we expect.
PDF_MANIFEST = [
    ("PIRAEUS-ATHENS-LIOSIA-AIRPORT_MON-FRI_from_221125_0.pdf",  "outbound", "mon_fri"),
    ("PIRAEUS-ATHENS-LIOSIA-AIRPORT_WEEKENDS_from_221125_0.pdf", "outbound", "weekend"),
    ("AIRPORT-LIOSIA-ATHENS-PIRAEUS_MON-FRI_from_221125_0.pdf",  "inbound",  "mon_fri"),
    ("AIRPORT-LIOSIA-ATHENS-PIRAEUS_WEEKENDS_from_221125_0.pdf", "inbound",  "weekend"),
    ("ATHENS-CHALKIDA-ATHENS_from_221125_0.pdf",                 "both",     "all"),
    ("PIRAEUS-KIATO-PIRAEUS_from_221125_0.pdf",                  "both",     "all"),
    ("KIATO-AIGIO-KIATO_from_221125_0.pdf",                      "both",     "all"),
]

TIME_RE = re.compile(r"^\s*(\d{1,2})[.:](\d{2})\s*$")
TRAIN_NO_RE = re.compile(r"^\s*(\d{3,5})\s*$")


def normalize_time(cell: str | None) -> str | None:
    if not cell:
        return None
    m = TIME_RE.match(cell.replace("\n", " "))
    if not m:
        return None
    h, mn = int(m.group(1)), int(m.group(2))
    if not (0 <= h < 30 and 0 <= mn < 60):
        return None
    return f"{h:02d}:{mn:02d}"


_FOOTNOTE_RE = re.compile(r"\s*\*?\s*\(\s*\*?\s*\d+\s*\)\s*")
_TRAILING_STAR = re.compile(r"\s*\*+\s*$")


def clean_station(cell: str | None) -> str:
    """Normalize a station label: collapse whitespace, drop footnote markers
    like *(1), *(*1), and any trailing asterisks. So `Athens*(1)` becomes
    `Athens`, `Diakopto (*2)` becomes `Diakopto`, `Kiato*` becomes `Kiato`."""
    if not cell:
        return ""
    text = cell.replace("\n", " ")
    text = _FOOTNOTE_RE.sub(" ", text)
    text = _TRAILING_STAR.sub("", text)
    return re.sub(r"\s+", " ", text).strip()


def parse_table(table: list[list[str | None]]) -> tuple[list[tuple[str, str]], list[dict]]:
    """Find the two header rows (Greek + English station names), then walk
    every subsequent row as one train. Return (stations, trains)."""
    # Locate Greek and English header rows: the row before TRAIN No.
    en_idx = None
    for i, row in enumerate(table):
        if row and row[0] and "TRAIN" in (row[0] or "").upper():
            en_idx = i
            break
    if en_idx is None or en_idx == 0:
        return [], []
    el_idx = en_idx - 1

    el_row = table[el_idx]
    en_row = table[en_idx]
    # First column is "TRAINS / ΣΥΡΜΟΙ"; station columns start at index 1.
    n = max(len(el_row), len(en_row))
    stations: list[tuple[str, str]] = []
    for c in range(1, n):
        el = clean_station(el_row[c] if c < len(el_row) else "")
        en = clean_station(en_row[c] if c < len(en_row) else "")
        if not en and not el:
            continue
        stations.append((en, el))

    trains: list[dict] = []
    for row in table[en_idx + 1:]:
        if not row or not row[0]:
            continue
        tno_m = TRAIN_NO_RE.match((row[0] or "").strip())
        if not tno_m:
            continue
        train_no = tno_m.group(1)
        stops: list[dict] = []
        for c in range(1, min(len(row), len(stations) + 1)):
            t = normalize_time(row[c])
            if t is None:
                continue
            en, el = stations[c - 1]
            stops.append({"station_en": en, "station_el": el, "time": t})
        if len(stops) >= 2:
            trains.append({"train_no": train_no, "stops": stops})
    return stations, trains


def parse_table_transposed(table: list[list[str | None]]) -> tuple[list[tuple[str, str]], list[dict]]:
    """KIATO->AIGIO layout: rows are stations, columns are trains.
    Header row contains "Δρομολόγια/Routes" with train numbers across; each
    subsequent station row pairs "El-En" name with one time per train col."""
    routes_idx = None
    for i, row in enumerate(table):
        if row and row[0] and "Δρομολόγια" in (row[0] or ""):
            routes_idx = i
            break
    if routes_idx is None:
        return [], []
    header = table[routes_idx]
    train_nos: list[str] = []
    for c in range(1, len(header)):
        cell = (header[c] or "").strip()
        if TRAIN_NO_RE.match(cell):
            train_nos.append(cell)
        else:
            train_nos.append("")
    trains_by_col: dict[int, dict] = {
        c: {"train_no": tno, "stops": []} for c, tno in enumerate(train_nos) if tno
    }
    stations: list[tuple[str, str]] = []
    # Skip the "Σταθμός/Station" header row below the route header.
    for row in table[routes_idx + 2:]:
        if not row or not row[0]:
            continue
        # Station label is in column 0; format is usually "Greek-English".
        label = clean_station(row[0])
        if not label:
            continue
        # Split by " - " or "-" between Greek/English
        parts = re.split(r"\s*-\s*", label)
        el = parts[0]
        en = parts[1] if len(parts) > 1 else parts[0]
        stations.append((en, el))
        for c, train in trains_by_col.items():
            if c >= len(row):
                continue
            t = normalize_time(row[c])
            if t is None:
                continue
            train["stops"].append({"station_en": en, "station_el": el, "time": t})
    trains = [t for t in trains_by_col.values() if len(t["stops"]) >= 2]
    return stations, trains


def parse_pdf(path: Path, direction: str, day_type: str) -> tuple[list[tuple[str, str]], list[dict]]:
    all_stations: list[tuple[str, str]] = []
    all_trains: list[dict] = []
    seen_station_keys: set[tuple[str, str]] = set()
    with pdfplumber.open(str(path)) as pdf:
        for page in pdf.pages:
            for table in page.extract_tables() or []:
                # Try standard layout first; fall back to transposed.
                stations, trains = parse_table(table)
                if not trains:
                    stations, trains = parse_table_transposed(table)
                for s in stations:
                    if s not in seen_station_keys:
                        seen_station_keys.add(s)
                        all_stations.append(s)
                for t in trains:
                    t["source_pdf"] = path.name
                    t["direction"] = direction
                    t["day_type"] = day_type
                    all_trains.append(t)
    return all_stations, all_trains


def main() -> None:
    total_trains = 0
    station_universe: set[tuple[str, str]] = set()
    summary: list[dict] = []
    for filename, direction, day_type in PDF_MANIFEST:
        path = PDF_DIR / filename
        if not path.exists():
            print(f"missing: {filename}", file=sys.stderr)
            continue
        stations, trains = parse_pdf(path, direction, day_type)
        out_path = OUT_DIR / (path.stem + ".jsonl")
        with out_path.open("w") as f:
            for t in trains:
                f.write(json.dumps(t, ensure_ascii=False) + "\n")
        total_trains += len(trains)
        for s in stations:
            station_universe.add(s)
        summary.append({
            "pdf": filename,
            "direction": direction,
            "day_type": day_type,
            "stations": len(stations),
            "trains": len(trains),
            "output": out_path.relative_to(ROOT).as_posix(),
        })
        print(f"  {filename}: {len(stations)} stations, {len(trains)} trains -> {out_path.name}")

    (OUT_DIR / "_summary.json").write_text(json.dumps({
        "total_trains": total_trains,
        "stations_seen": len(station_universe),
        "per_pdf": summary,
    }, indent=2, ensure_ascii=False))
    print(f"\nTOTAL: {total_trains} trains across {len(PDF_MANIFEST)} PDFs, "
          f"{len(station_universe)} unique stations.")


if __name__ == "__main__":
    main()
