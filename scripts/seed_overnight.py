"""Shared 24h Saturday overnight invariant for the bundled offline seeds.

This mirrors ops/syrmos-api/syrmos_admin/schedule_invariants.py on the client
side of the fence: the offline seed snapshots (schedules-v2/*.json) bundled into
the iOS, Android and web apps must carry a continuous Saturday overnight band for
the 24h lines (M2, M3 city, T6, T7). If the snapshot is baked from a momentarily
stale API, or the upstream 24mmm scrape dropped the 02:00->05:30 continuation
row, the bundle would ship a dark overnight map even with zero network. This
module guarantees the invariant so scripts/snapshot-api-to-seed.py can never
write a truncated overnight bundle, and scripts/verify-bundles.py can assert it.

Official frequencies (OASA 24mmm, verified 2026-08-30, https://www.oasa.gr/en/24mmm/):
    M2 / M3 city : 00:20 -> 05:30 @ 15'
    T6 / T7      : 00:30 -> 05:30 @ 25'
M1 and the M3 airport branch are NOT 24h and are intentionally excluded.
"""
from __future__ import annotations

SOURCE_URL = "https://www.oasa.gr/en/24mmm/"
VERIFIED_ON = "2026-08-30"
OVERNIGHT_END = "05:30"
OVERNIGHT_LABEL = "saturday_overnight_24_7"
OVERNIGHT_CUTOFF_MIN = 5 * 60  # bands starting before 05:00 are the overnight tail

# line_id -> (overnight_start, headway_minutes)
SATURDAY_24H_OVERNIGHT: dict[str, tuple[str, float]] = {
    "M2": ("00:20", 15.0),
    "M3": ("00:20", 15.0),
    "T6": ("00:30", 25.0),
    "T7": ("00:30", 25.0),
}


def _to_min(hhmm: str) -> int | None:
    try:
        h, m = hhmm.split(":")
        return int(h) * 60 + int(m)
    except (ValueError, AttributeError):
        return None


def overnight_gap(bands: list[dict], *, until: str = OVERNIGHT_END) -> list[tuple[int, int]]:
    """Uncovered minute ranges in [00:00, until) across the Saturday bands.

    Evening bands that wrap past midnight (timeEnd <= timeStart) contribute their
    after-midnight tail, so a 22:00->00:20 band covers 00:00->00:20. Empty result
    means continuous coverage up to the daytime handover.
    """
    end = _to_min(until) or 0
    covered = [False] * end
    for b in bands:
        if b.get("dayType") != "sat":
            continue
        s = _to_min(b.get("timeStart", ""))
        e = _to_min(b.get("timeEnd", ""))
        if s is None or e is None:
            continue
        if e <= s:
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


def ensure_overnight_continuity(schedule: dict) -> bool:
    """Guarantee the continuous overnight band for one line's schedule dict.

    Mutates `schedule["bands"]` in place. Returns True when it changed anything.
    For a non-24h line it is a no-op. For a 24h line it drops any Saturday band
    that starts before 05:00 (the truncated overnight fragments) and inserts the
    single authoritative OVERNIGHT_LABEL band [start -> 05:30] at the official
    headway, preserving all daytime/evening bands (which start at 05:30 or later,
    including the 22:00->00:20 wrap).
    """
    line_id = schedule.get("lineId")
    spec = SATURDAY_24H_OVERNIGHT.get(line_id)
    if spec is None:
        return False
    start, headway = spec
    bands = schedule.get("bands", [])

    kept: list[dict] = []
    removed = False
    for b in bands:
        if b.get("dayType") == "sat":
            s = _to_min(b.get("timeStart", ""))
            if s is not None and s < OVERNIGHT_CUTOFF_MIN:
                removed = True
                continue  # drop overnight fragment; we re-add the canonical band
        kept.append(b)

    canonical = {
        "dayType": "sat",
        "timeStart": start,
        "timeEnd": OVERNIGHT_END,
        "headwayMinutes": headway,
        "label": OVERNIGHT_LABEL,
        "direction": "both",
    }
    kept.append(canonical)
    # Stable order: by dayType then timeStart, matching the generator's output.
    kept.sort(key=lambda b: (b.get("dayType", ""), _to_min(b.get("timeStart", "")) or 0))

    changed = removed or (kept != bands)
    schedule["bands"] = kept
    return changed
