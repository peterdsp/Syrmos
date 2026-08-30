"""Saturday 24-hour service invariants (Athens fixed rail).

Official truth (OASA / STASY, verified 2026-08-30):
  - Metro Line 2 (M2): operates 24 hours every Saturday.
  - Metro Line 3 (M3): operates 24 hours every Saturday, CITY service only.
    The airport branch (M3_AIR) is explicitly EXCLUDED from the 24h service.
  - Tram T6 and T7: operate 24 hours every Saturday.
  - Metro Line 1 (M1): NOT 24h. Its last Saturday train leaves origin ~01:00;
    it must never be treated as an all-night line.

Sources:
  https://www.oasa.gr/en/24mmm/                     (24-hour service frequencies)
  https://www.oasa.gr/en/routes/special-bus-lines/24-hour-service-bus-lines/
  https://www.stasy.gr/en/timetables/line-2/
  https://www.stasy.gr/en/timetables/line-3/
  https://www.stasy.gr/en/timetables/tram/

Why this module exists
----------------------
Saturday 24h service continues past midnight into Sunday. The projector
(projector.active_trains / project_next_departures) represents that overnight
tail as a Saturday-day-type frequency band whose timeStart is before 05:00 (the
projector's next-day-extension cutoff, NEXT_DAY_EXTENSION_CUTOFF_MIN). At e.g.
Sunday 01:53 the projector reaches back to Saturday's < 05:00 bands and projects
them onto Sunday's clock. If that band is missing or truncated (last band ends
at 02:00 or 01:40 instead of running to the 05:30 daytime handover), the live
map and the departures list go dark overnight even though M2/M3/T6/T7 are
running. That was the reported defect: zero metro/tram vehicles at 01:53 Sunday.

The base seed (scripts.import_athens_package) already ships the correct
continuous overnight bands, but two things can still erase them on a live Pi:
  1. A stale DB seeded before those bands were added.
  2. syrmos_admin.scraper_24mmm, which replaces the sat_24mmm-labelled bands
     with whatever the OASA page currently yields; if its parse drops the
     02:00->05:30 (metro) / 01:40->05:30 (tram) continuation row, the overnight
     coverage disappears.

This module makes the invariant explicit and self-healing: after any mutation of
the Saturday bands, ensure_saturday_overnight() guarantees a continuous overnight
band from the line's first departure of the night through to the 05:30 daytime
handover, using the official published headways. The frequencies are NOT
invented; they are the OASA 24mmm values. A scraped, more specific headway is
respected when it already covers the window.
"""
from __future__ import annotations

import sqlite3

SOURCE_URL = "https://www.oasa.gr/en/24mmm/"
VERIFIED_ON = "2026-08-30"

# Where Sunday (and every non-24h day) daytime service begins. The overnight
# tail of the 24h Saturday service runs up to this handover.
OVERNIGHT_END = "05:30"

# Stable label for the authoritative overnight-continuation band. The projector
# keys off the band's timeStart (< 05:00), not this label, but a distinct label
# keeps the invariant band identifiable and separates it from the scraper's
# sat_24mmm rows (which scraper_24mmm is free to delete + rewrite).
OVERNIGHT_LABEL = "saturday_overnight_24_7"

# Official OASA 24mmm Saturday overnight service, per line:
#   line_id -> (overnight_start "HH:MM", headway_minutes)
# The band runs [overnight_start -> OVERNIGHT_END] on the 'sat' day type and,
# because its start is before 05:00, the projector continues it into Sunday.
#   M2 / M3 city : 15' throughout 00:20 -> 05:30
#   T6 / T7      : 25' throughout 00:30 -> 05:30
# M3_AIR (airport) and M1 are deliberately absent: they are NOT 24h.
SATURDAY_24H_OVERNIGHT: dict[str, tuple[str, float]] = {
    "M2": ("00:20", 15.0),
    "M3": ("00:20", 15.0),
    "T6": ("00:30", 25.0),
    "T7": ("00:30", 25.0),
}

# Lines that must NOT be treated as 24h, asserted by tests and used by callers
# that want to double-check nothing added an overnight band by mistake.
NOT_24H_LINES = ("M1", "M3_AIR")


def _to_min(hhmm: str) -> int | None:
    try:
        h, m = hhmm.split(":")
        return int(h) * 60 + int(m)
    except (ValueError, AttributeError):
        return None


def _frequency_bands_has_direction(conn: sqlite3.Connection) -> bool:
    cols = {r[1] for r in conn.execute("PRAGMA table_info(frequency_bands)")}
    return "direction" in cols


def saturday_overnight_gap(
    conn: sqlite3.Connection, line_id: str, *, until: str = OVERNIGHT_END
) -> list[tuple[int, int]]:
    """Return uncovered minute ranges in [00:00, until) of the Saturday service.

    Coverage is computed from every 'sat' band, wrapping past-midnight bands
    (timeEnd <= timeStart) so an evening band like 22:00->00:20 contributes its
    00:00->00:20 tail. The result is the list of gaps a rider would experience
    overnight; an empty list means continuous coverage up to the handover.
    """
    end = _to_min(until) or 0
    covered = [False] * end
    for r in conn.execute(
        "SELECT time_start, time_end FROM frequency_bands"
        " WHERE line_id=? AND day_type='sat'",
        (line_id,),
    ):
        s = _to_min(r["time_start"])
        e = _to_min(r["time_end"])
        if s is None or e is None:
            continue
        if e <= s:  # wraps past midnight
            e += 24 * 60
        for t in range(s, e):
            tm = t % (24 * 60)
            if tm < end:
                covered[tm] = True
    gaps: list[tuple[int, int]] = []
    t = 0
    while t < end:
        if not covered[t]:
            st = t
            while t < end and not covered[t]:
                t += 1
            gaps.append((st, t))
        else:
            t += 1
    return gaps


def ensure_saturday_overnight(conn: sqlite3.Connection) -> list[str]:
    """Guarantee a continuous official overnight band for every 24h Saturday line.

    Idempotent. For each line in SATURDAY_24H_OVERNIGHT, (re)writes the single
    authoritative OVERNIGHT_LABEL band [start -> 05:30] at the official headway,
    replacing any earlier or truncated copy. Returns the line ids that were
    changed (empty when everything was already correct), so callers can log a
    self-heal without spamming on the common no-op path.

    This does not touch the daytime/evening bands, the airport branch, or M1.
    The scraper's sat_24mmm rows are left in place; they harmlessly overlap and
    the projector dedupes by departure slot.
    """
    has_dir = _frequency_bands_has_direction(conn)
    changed: list[str] = []
    for line_id, (start, headway) in SATURDAY_24H_OVERNIGHT.items():
        existing = conn.execute(
            "SELECT time_start, time_end, headway_minutes FROM frequency_bands"
            " WHERE line_id=? AND day_type='sat' AND label=?",
            (line_id, OVERNIGHT_LABEL),
        ).fetchall()
        # Idempotency is gated on THIS band being exactly right. The 00:00->start
        # sliver is the evening wrap band's job (saturday_evening 22:00->00:20),
        # not this overnight band's, so it must not force a rewrite here -- that
        # would make the enforcer loop forever without being able to fix it.
        already_correct = (
            len(existing) == 1
            and existing[0]["time_start"] == start
            and existing[0]["time_end"] == OVERNIGHT_END
            and abs((existing[0]["headway_minutes"] or 0) - headway) < 1e-6
        )
        if already_correct:
            continue
        # Rewrite the authoritative band. Delete the label's rows, then clear any
        # foreign band occupying the exact start slot (PK collision), then insert.
        conn.execute(
            "DELETE FROM frequency_bands WHERE line_id=? AND day_type='sat' AND label=?",
            (line_id, OVERNIGHT_LABEL),
        )
        if has_dir:
            conn.execute(
                "DELETE FROM frequency_bands"
                " WHERE line_id=? AND day_type='sat' AND time_start=? AND direction='both'",
                (line_id, start),
            )
            conn.execute(
                "INSERT INTO frequency_bands"
                "(line_id, day_type, time_start, time_end, headway_minutes, label, direction)"
                " VALUES (?, 'sat', ?, ?, ?, ?, 'both')",
                (line_id, start, OVERNIGHT_END, headway, OVERNIGHT_LABEL),
            )
        else:
            conn.execute(
                "DELETE FROM frequency_bands"
                " WHERE line_id=? AND day_type='sat' AND time_start=?",
                (line_id, start),
            )
            conn.execute(
                "INSERT INTO frequency_bands"
                "(line_id, day_type, time_start, time_end, headway_minutes, label)"
                " VALUES (?, 'sat', ?, ?, ?, ?)",
                (line_id, start, OVERNIGHT_END, headway, OVERNIGHT_LABEL),
            )
        changed.append(line_id)
    return changed


def verify_saturday_continuity(conn: sqlite3.Connection) -> dict[str, list[tuple[int, int]]]:
    """Diagnostic: map each 24h line to its overnight gaps (empty when healthy)."""
    return {
        line_id: saturday_overnight_gap(conn, line_id)
        for line_id in SATURDAY_24H_OVERNIGHT
    }


def provenance() -> dict[str, object]:
    """Machine-readable provenance for the 24h Saturday overnight service, for
    the generator manifest so clients can surface source + verification date."""
    return {
        "source": SOURCE_URL,
        "verifiedOn": VERIFIED_ON,
        "lines": sorted(SATURDAY_24H_OVERNIGHT.keys()),
        "note": (
            "Metro M2, Metro M3 (city only), Tram T6 and T7 run 24 hours every "
            "Saturday; the service continues past midnight into Sunday until the "
            "05:30 daytime handover. The M3 airport branch and Metro M1 are not 24h."
        ),
    }
