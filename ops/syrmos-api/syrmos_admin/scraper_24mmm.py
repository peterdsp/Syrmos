"""OASA 24mmm scraper — Saturday 24-hour service frequencies.

The 24mmm page (https://www.oasa.gr/en/24mmm/) publishes the Saturday-overnight
service windows for M2, M3, T6, T7. This scraper replaces the Saturday
frequency bands in the DB with whatever the page currently says, so the live
service window in the app stays in sync without an app release.

Per-date overrides (strikes, works on a specific date) are NOT here — they
do not exist on the 24mmm page. They are manual admin entries in date_overrides.

Run: python3 -m syrmos_admin.scraper_24mmm
"""
from __future__ import annotations

import html as html_mod
import re
import sqlite3
import sys
from dataclasses import dataclass
from typing import Iterable
from urllib.error import URLError
from urllib.request import Request, urlopen

from . import db as dbmod

URL = "https://www.oasa.gr/en/24mmm/"
USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 "
    "(KHTML, like Gecko) Version/17.0 Safari/605.1.15"
)
TIMEOUT_SECONDS = 25

# Lines we own on the 24mmm page. M3 has a city + airport split.
SCOPE_LINES = ("M2", "M3", "M3_AIR", "T6", "T7")


@dataclass
class Band:
    line_id: str
    time_start: str
    time_end: str
    headway_minutes: float
    label: str


def fetch_html() -> str:
    req = Request(URL, headers={"User-Agent": USER_AGENT})
    with urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
        return resp.read().decode("utf-8", errors="ignore")


_TIME_RE = re.compile(r"(\d{2}):(\d{2})")
_INTERVAL_RE = re.compile(r"(\d+)\s*['′]\s*(?:(\d+)\s*[\"″”])?")
# Block headers we recognize. T6+T7 share a single block on the page.
_LINE_BLOCKS = [
    (("M2",),       ["metro line 2 service frequencies"]),
    (("M3",),       ["metro line 3 service frequencies"]),
    (("T6", "T7"),  [
        "tram lines t6 and t7 service frequencies",
        "tram t6 and t7 service frequencies",
    ]),
]


def _parse_interval(text: str) -> float | None:
    """Parse '10′ 50″', '15′', '25\"' → headway in minutes (float)."""
    m = _INTERVAL_RE.search(text)
    if not m:
        # The page sometimes mis-types seconds as ", treat as minutes
        seconds_only = re.search(r"(\d+)\s*[\"″”]", text)
        if seconds_only:
            return float(seconds_only.group(1))
        return None
    minutes = int(m.group(1))
    seconds = int(m.group(2)) if m.group(2) else 0
    return round(minutes + seconds / 60, 3)


def _strip_html(s: str) -> str:
    """Strip tags + unescape HTML entities so &prime; becomes a real prime."""
    text = re.sub(r"<[^>]+>", " ", s)
    return html_mod.unescape(text)


def _extract_triplets(block: str) -> list[tuple[str, str, str]]:
    """Find (from_time, to_time, interval_text) triplets in a block.

    Page format: each value lives in its own table cell, so after stripping
    tags the block looks like a stream of '22:00 00:20 10' 50"' tokens.
    """
    tokens = list(_TIME_RE.finditer(block))
    triplets: list[tuple[str, str, str]] = []
    for i in range(len(tokens) - 1):
        a, b = tokens[i], tokens[i + 1]
        # Ignore time-followed-by-time pairs that aren't from/to (header row).
        if b.start() - a.end() > 80:
            continue
        # The interval text is whatever sits between this 'to' time and the
        # next 'from' time (or end of block).
        next_from = tokens[i + 2].start() if i + 2 < len(tokens) else len(block)
        interval_text = block[b.end() : next_from]
        if _INTERVAL_RE.search(interval_text):
            triplets.append((
                f"{a.group(1)}:{a.group(2)}",
                f"{b.group(1)}:{b.group(2)}",
                interval_text,
            ))
    return triplets


def parse(html: str) -> list[Band]:
    """Pull the Saturday band table per line out of the HTML."""
    text = _strip_html(html).lower()
    bands: list[Band] = []

    # Slice into per-line blocks using header positions.
    headers: list[tuple[tuple[str, ...], int]] = []
    for line_ids, needles in _LINE_BLOCKS:
        for needle in needles:
            idx = text.find(needle)
            if idx >= 0:
                headers.append((line_ids, idx))
                break
    headers.sort(key=lambda x: x[1])
    if not headers:
        return bands

    for i, (line_ids, start) in enumerate(headers):
        end = headers[i + 1][1] if i + 1 < len(headers) else len(text)
        block = text[start:end]

        # The header row contains a "22:00 to 10:00" range we want to skip; the
        # real data is after "estimated interval".
        anchor = block.find("estimated interval")
        if anchor >= 0:
            block = block[anchor:]

        for t_from, t_to, interval_text in _extract_triplets(block):
            hits = list(_INTERVAL_RE.finditer(interval_text))
            if not hits:
                continue

            # M3 block stacks city + airport intervals in the same cell.
            # If we see two distinct intervals, the first is city, the second is airport.
            if "M3" in line_ids and len(hits) >= 2:
                city = _parse_interval(hits[0].group(0))
                air = _parse_interval(hits[1].group(0))
                if city is not None:
                    bands.append(Band("M3", t_from, t_to, city, "sat_24mmm"))
                if air is not None:
                    bands.append(Band("M3_AIR", t_from, t_to, air, "sat_24mmm"))
                continue

            h = _parse_interval(hits[0].group(0))
            if h is None:
                continue
            for lid in line_ids:
                bands.append(Band(lid, t_from, t_to, h, "sat_24mmm"))

    return [b for b in bands if b.line_id in SCOPE_LINES]


def apply_bands(conn: sqlite3.Connection, bands: Iterable[Band]) -> int:
    """Replace Saturday 24mmm bands for the scoped lines."""
    cur = conn.cursor()
    cur.execute("BEGIN")
    try:
        # Wipe only the sat_24mmm-labelled rows; keep day-rule rows intact.
        cur.executemany(
            "DELETE FROM frequency_bands WHERE line_id=? AND day_type='sat' AND label='sat_24mmm'",
            [(lid,) for lid in SCOPE_LINES],
        )
        n = 0
        for b in bands:
            cur.execute(
                "INSERT OR REPLACE INTO frequency_bands"
                "(line_id, day_type, time_start, time_end, headway_minutes, label)"
                " VALUES(?,?,?,?,?,?)",
                (b.line_id, "sat", b.time_start, b.time_end, b.headway_minutes, b.label),
            )
            n += 1
        cur.execute("COMMIT")
        return n
    except Exception:
        cur.execute("ROLLBACK")
        raise


def run_once() -> int:
    """Scrape once. Returns rows written. Logs the run regardless of outcome."""
    with dbmod.connect() as conn:
        try:
            html = fetch_html()
            bands = parse(html)
            n = apply_bands(conn, bands)
            conn.execute(
                "INSERT INTO scrape_log(source, ok, rows_written) VALUES('oasa_24mmm', 1, ?)",
                (n,),
            )
            return n
        except (URLError, ValueError, RuntimeError) as e:
            conn.execute(
                "INSERT INTO scrape_log(source, ok, rows_written, error) VALUES('oasa_24mmm', 0, 0, ?)",
                (str(e),),
            )
            raise


if __name__ == "__main__":
    try:
        n = run_once()
        print(f"OK - {n} frequency bands replaced")
    except Exception as e:
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1)
