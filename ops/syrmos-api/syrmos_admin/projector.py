"""Server-side projector. Single source of truth for next departures.

Previously every client (iOS Swift, KMP/Android, KMP/Web) ran its own
projector over the band/rule data from /api/schedules/*. Each
implementation drifted, and edge cases (midnight wrap, Saturday 24/7
overnight, OASA's `sat`-tagged next-day bands) had to be fixed three
times in three languages.

This module concentrates the logic in one place. The FastAPI public
endpoint `/api/departures/next` calls into here; clients just render
what the endpoint returns. Local-bundle band projection in the apps
stays only as the offline fallback so airplane-mode launches still
show something rather than an empty list.

Inputs:
    station_id    e.g. M2_SYN, T7_DIM
    line_ids      list[str], e.g. ["M2", "M3"] (M3 auto-expanded to
                  include M3_AIR for city stations)
    direction     optional outbound or inbound filter
    now           Athens-local datetime, defaults to now
    limit         max entries returned (default 8)

Output: list of dicts matching the /api/departures/next response rows.

The implementation mirrors the iOS ScheduleProjector and KMP
ComputeDeparturesFromBandsUseCase as of commit d50f19d. When the
projector here changes, the two client-side fallback projectors should
mirror the change OR be deleted in favour of always calling the API.
"""
from __future__ import annotations

import sqlite3
from dataclasses import dataclass, asdict
from datetime import datetime, timedelta, timezone
from typing import Iterable

# Athens timezone, fixed UTC+2/+3 with DST. Use zoneinfo from stdlib.
try:
    from zoneinfo import ZoneInfo
    ATHENS = ZoneInfo("Europe/Athens")
except ImportError:  # very old Python; should never happen on the Pi
    ATHENS = timezone(timedelta(hours=2))

LINE3_AIRPORT_ONLY_STATIONS = {"M3_PAL", "M3_PEK", "M3_KRP", "M3_AER"}
NEXT_DAY_EXTENSION_CUTOFF_MIN = 5 * 60   # bands < 05:00 count as next-day extension
AIRPORT_LOOKAHEAD_DAYS = 7

HOLIDAY_DAY_TYPE = {
    (1, 1): "sun",  (5, 1): "sun",  (10, 28): "sun",  (12, 25): "sun",  (12, 26): "sun",
    (8, 15): "aug_15",
    (12, 24): "dec_24_31",  (12, 31): "dec_24_31",
    (1, 2): "sat",  (1, 6): "sat",  (11, 17): "sat",
}


@dataclass
class Departure:
    lineId: str
    line: str
    directionKey: str
    direction: str
    time: str               # "HH:MM"
    minutesAway: int
    serviceType: str        # regular | airport | late_night

    def to_dict(self):
        return asdict(self)


def project_next_departures(
    conn: sqlite3.Connection,
    station_id: str,
    line_ids: list[str],
    *,
    direction: str | None = None,
    now: datetime | None = None,
    limit: int = 8,
) -> list[dict]:
    """Top-level entry point used by /api/departures/next."""
    if now is None:
        now = datetime.now(ATHENS)
    elif now.tzinfo is None:
        now = now.replace(tzinfo=ATHENS)
    else:
        now = now.astimezone(ATHENS)

    now_minutes = now.hour * 60 + now.minute
    weekday = (now.isoweekday() % 7) + 1   # 1 = Sunday, 7 = Saturday (matches Swift Calendar)
    holiday = HOLIDAY_DAY_TYPE.get((now.month, now.day))

    expanded = _expand_line_ids(station_id, line_ids)
    bundles = {lid: _load_bundle(conn, lid) for lid in expanded}
    bundles = {k: v for k, v in bundles.items() if v is not None}

    direction_filter = _normalise_direction(direction)
    out: list[Departure] = []
    for lid in expanded:
        bundle = bundles.get(lid)
        if bundle is None:
            continue
        _project_line(
            bundle=bundle,
            line_id=lid,
            station_id=station_id,
            weekday=weekday,
            now_minutes=now_minutes,
            holiday=holiday,
            limit=limit,
            direction_filter=direction_filter,
            out=out,
            conn=conn,
        )

    out = _dedupe(out)
    out.sort(key=lambda d: d.minutesAway)
    out = out[:limit]

    # M3 city station guarantee: always surface the next Airport-bound
    # train even if it's hours away. Lookahead scans forward up to 7 days
    # so the row appears after the last airport service has run for today.
    wants_airport = (
        "M3_AIR" in expanded
        and station_id not in LINE3_AIRPORT_ONLY_STATIONS
        and direction_filter in (None, "outbound")
    )
    has_airport = any(d.serviceType == "airport" for d in out)
    if wants_airport and not has_airport:
        bundle = bundles.get("M3_AIR")
        if bundle is not None:
            la = _next_airport_lookahead(
                bundle=bundle,
                now=now,
                now_minutes=now_minutes,
                station_id=station_id,
                conn=conn,
            )
            if la is not None:
                out.append(la)

    return [d.to_dict() for d in out]


# --- internals ---

def _expand_line_ids(station_id: str, line_ids: list[str]) -> list[str]:
    out: list[str] = []
    for lid in line_ids:
        if lid == "M3_AIR":
            out.append("M3_AIR")
        elif lid in ("M3", "M3A"):
            if station_id in LINE3_AIRPORT_ONLY_STATIONS:
                out.append("M3_AIR")
            else:
                out.append("M3")
                out.append("M3_AIR")
        else:
            out.append(lid)
    deduped: list[str] = []
    for lid in out:
        if lid not in deduped:
            deduped.append(lid)
    return deduped


def _normalise_direction(direction: str | None) -> str | None:
    if direction is None or not direction.strip():
        return None
    value = direction.strip().lower()
    if value not in {"outbound", "inbound"}:
        return None
    return value


def _display_line_id(line_id: str) -> str:
    if line_id == "M3_AIR":
        return "M3"
    if line_id.startswith("M3"):
        return "M3"
    return line_id


def _dedupe(rows: Iterable[Departure]) -> list[Departure]:
    # Key by (lineId, directionKey, direction, time). serviceType is metadata,
    # not identity: two overlapping bands (e.g. seeded saturday_overnight_24_7
    # and scraper sat_24mmm) at the same minute represent ONE train, just
    # labeled differently. Including direction keeps M3 city (DPL) and
    # M3_AIR (Airport) trains separate at the same minute.
    seen: set[tuple[str, str, str, str]] = set()
    out: list[Departure] = []
    for row in rows:
        key = (row.lineId, row.directionKey, row.direction, row.time)
        if key in seen:
            continue
        seen.add(key)
        out.append(row)
    return out


def _day_type(weekday: int, holiday: str | None) -> str:
    if holiday:
        return holiday
    return {
        1: "sun", 2: "mon_thu", 3: "mon_thu", 4: "mon_thu",
        5: "mon_thu", 6: "fri", 7: "sat",
    }.get(weekday, "mon_thu")


def _minutes_of_day(hhmm: str) -> int | None:
    try:
        h, m = hhmm.split(":")
        return int(h) * 60 + int(m)
    except (ValueError, AttributeError):
        return None


def _load_bundle(conn: sqlite3.Connection, line_id: str) -> dict | None:
    rule_rows = conn.execute(
        "SELECT day_type, open_time, close_time, is_24_7 FROM schedule_rules WHERE line_id=?",
        (line_id,),
    ).fetchall()
    band_rows = conn.execute(
        "SELECT day_type, time_start, time_end, headway_minutes, label"
        " FROM frequency_bands WHERE line_id=? ORDER BY day_type, time_start",
        (line_id,),
    ).fetchall()
    if not rule_rows and not band_rows:
        return None
    return {
        "rules": [
            {
                "dayType": r["day_type"],
                "openTime": r["open_time"],
                "closeTime": r["close_time"],
                "is247": bool(r["is_24_7"]),
            }
            for r in rule_rows
        ],
        "bands": [
            {
                "dayType": r["day_type"],
                "timeStart": r["time_start"],
                "timeEnd": r["time_end"],
                "headwayMinutes": r["headway_minutes"],
                "label": r["label"] or "",
            }
            for r in band_rows
        ],
    }


def _direction_streams(line_id: str, conn: sqlite3.Connection):
    if line_id == "M3_AIR":
        return [("outbound", "Airport")]
    display_id = "M3" if line_id.startswith("M3") else line_id
    row = conn.execute(
        "SELECT terminal_a, terminal_b FROM lines WHERE id=?",
        (display_id,),
    ).fetchone()
    if row is None:
        return [("outbound", "")]
    return [
        ("outbound", row["terminal_b"] or ""),
        ("inbound", row["terminal_a"] or ""),
    ]


def _station_offset_minutes(conn: sqlite3.Connection, line_id: str, direction: str, station_id: str) -> int:
    if not station_id:
        return 0
    lookup_line_id = "M3" if line_id == "M3_AIR" else line_id
    row = conn.execute(
        "SELECT minutes_from_origin FROM station_offsets"
        " WHERE line_id=? AND direction=? AND station_id=? LIMIT 1",
        (lookup_line_id, direction.lower(), station_id),
    ).fetchone()
    return int(row[0]) if row else 0


def _service_type(line_id: str, band_label: str) -> str:
    if line_id == "M3_AIR":
        return "airport"
    if "late" in band_label.lower() or "overnight" in band_label.lower():
        return "late_night"
    return "regular"


def _project_line(
    *,
    bundle: dict,
    line_id: str,
    station_id: str,
    weekday: int,
    now_minutes: int,
    holiday: str | None,
    limit: int,
    direction_filter: str | None,
    out: list[Departure],
    conn: sqlite3.Connection,
):
    today_dt = _day_type(weekday, holiday)
    # Descriptor stack: today's dt, yesterday with -24h shift (for wrap bands),
    # yesterday with no shift LIMITED to early-morning bands (for OASA's
    # 'sat 00:30 -> 05:30 saturday_overnight_24_7' style next-day extensions).
    descriptors: list[tuple[str, int, bool]] = [(today_dt, 0, False)]
    if now_minutes < 6 * 60:
        yesterday_weekday = 7 if weekday == 1 else weekday - 1
        yesterday_dt = _day_type(yesterday_weekday, None)
        descriptors.append((yesterday_dt, -24 * 60, False))
        if yesterday_dt != today_dt:
            descriptors.append((yesterday_dt, 0, True))

    streams = _direction_streams(line_id, conn)
    if direction_filter is not None:
        streams = [s for s in streams if s[0] == direction_filter]
    if not streams:
        return

    line_out: list[Departure] = []

    for dt, shift, next_day_only in descriptors:
        rule = next((r for r in bundle["rules"] if r["dayType"] == dt), None)
        if rule is None:
            continue
        if not rule["is247"]:
            open_min = _minutes_of_day(rule["openTime"])
            close_min = _minutes_of_day(rule["closeTime"])
            if open_min is not None and close_min is not None:
                effective_close = close_min + 24 * 60 if close_min <= open_min else close_min
                effective_now = now_minutes + shift
                if effective_now < open_min or effective_now > effective_close:
                    continue

        # Filter bands by dayType plus next-day-extension rule
        bands = []
        for b in bundle["bands"]:
            if b["dayType"] != dt:
                continue
            if next_day_only:
                rs = _minutes_of_day(b["timeStart"])
                if rs is None or rs >= NEXT_DAY_EXTENSION_CUTOFF_MIN:
                    continue
            bands.append(b)
        bands.sort(key=lambda b: _minutes_of_day(b["timeStart"]) or 0)

        for band in bands:
            for direction_key, direction_label in streams:
                _project_band(
                    band=band,
                    shift=shift,
                    now_minutes=now_minutes,
                    line_id=line_id,
                    direction_key=direction_key,
                    direction_label=direction_label,
                    station_id=station_id,
                    limit=limit,
                    out=line_out,
                    conn=conn,
                )

    line_out = _dedupe(line_out)
    line_out.sort(key=lambda d: d.minutesAway)
    out.extend(line_out[:limit])


def _project_band(
    *,
    band: dict,
    shift: int,
    now_minutes: int,
    line_id: str,
    direction_key: str,
    direction_label: str,
    station_id: str,
    limit: int,
    out: list[Departure],
    conn: sqlite3.Connection,
):
    raw_start = _minutes_of_day(band["timeStart"])
    raw_end = _minutes_of_day(band["timeEnd"])
    headway = band["headwayMinutes"]
    if raw_start is None or raw_end is None or headway is None or headway <= 0:
        return
    start = raw_start + shift
    # Wrap past-midnight bands so [start, end] stays monotonic.
    end = raw_end + shift + (24 * 60 if raw_end < raw_start else 0)
    if end < start:
        return
    offset = _station_offset_minutes(conn, line_id, direction_key, station_id)

    slot = float(start)
    station_slot = slot + offset
    if station_slot < now_minutes:
        skips = max(0, int((now_minutes - station_slot) / headway))
        slot = start + skips * headway
        while slot + offset < now_minutes:
            slot += headway

    added = 0
    while slot <= end and added < limit:
        slot_min = int(round(slot)) + offset
        display_min = ((slot_min % (24 * 60)) + 24 * 60) % (24 * 60)
        time_str = f"{display_min // 60:02d}:{display_min % 60:02d}"
        minutes_away = max(0, slot_min - now_minutes)
        display_line_id = _display_line_id(line_id)
        out.append(Departure(
            lineId=display_line_id,
            line=display_line_id,
            directionKey=direction_key,
            direction=direction_label,
            time=time_str,
            minutesAway=minutes_away,
            serviceType=_service_type(line_id, band["label"]),
        ))
        slot += headway
        added += 1


def _total_travel_minutes(conn: sqlite3.Connection, line_id: str, direction: str) -> int:
    """Maximum minutes_from_origin across all stations on this leg — i.e. the
    travel time from the first station to the terminus. Used to decide whether
    a train that already departed origin is still on the line."""
    lookup_line_id = "M3" if line_id == "M3_AIR" else line_id
    row = conn.execute(
        "SELECT MAX(minutes_from_origin) AS m FROM station_offsets"
        " WHERE line_id=? AND direction=?",
        (lookup_line_id, direction.lower()),
    ).fetchone()
    return int(row[0]) if row and row[0] is not None else 0


def active_trains(
    conn: sqlite3.Connection,
    line_ids: list[str],
    *,
    now: datetime | None = None,
) -> list[dict]:
    """Enumerate every train currently somewhere along its line.

    A train is active when origin_depart_minute_of_clock <= now and the train
    has not yet reached its terminus (elapsed_minutes <= total_travel_minutes).

    The output is the minimum the clients need to place a moving dot on the
    map: line + direction + when the train left its origin (absolute Athens
    minute-of-clock relative to today's midnight, can be negative if the
    train started yesterday) + the line's total travel time. The client
    combines this with its bundled station_offsets + station coords to
    interpolate the dot's position between fetches.
    """
    if now is None:
        now = datetime.now(ATHENS)
    elif now.tzinfo is None:
        now = now.replace(tzinfo=ATHENS)
    else:
        now = now.astimezone(ATHENS)

    now_minutes = now.hour * 60 + now.minute + now.second / 60.0
    weekday = (now.isoweekday() % 7) + 1
    holiday = HOLIDAY_DAY_TYPE.get((now.month, now.day))

    out: list[dict] = []
    seen: set[tuple[str, str, int]] = set()

    for line_id in line_ids:
        bundle = _load_bundle(conn, line_id)
        if bundle is None:
            continue
        streams = _direction_streams(line_id, conn)
        display_line_id = _display_line_id(line_id)

        today_dt = _day_type(weekday, holiday)
        descriptors: list[tuple[str, int, bool]] = [(today_dt, 0, False)]
        # A train that left origin yesterday and hasn't reached terminus yet
        # still counts, so always include yesterday with -24h shift. The
        # 6-hour gate the projector uses for next-departure queries is too
        # narrow here.
        yesterday_weekday = 7 if weekday == 1 else weekday - 1
        yesterday_dt = _day_type(yesterday_weekday, None)
        descriptors.append((yesterday_dt, -24 * 60, False))

        for dt, shift, _next_day_only in descriptors:
            rule = next((r for r in bundle["rules"] if r["dayType"] == dt), None)
            if rule is None:
                continue
            if not rule["is247"]:
                open_min = _minutes_of_day(rule["openTime"])
                close_min = _minutes_of_day(rule["closeTime"])
                if open_min is not None and close_min is not None:
                    effective_close = close_min + 24 * 60 if close_min <= open_min else close_min
                    effective_now = now_minutes + shift
                    # Allow some slack on either side so a train that departed
                    # right before service open or right after close is still
                    # found when interpolating its tail end.
                    if effective_now < open_min - 120 or effective_now > effective_close + 120:
                        continue

            bands = [b for b in bundle["bands"] if b["dayType"] == dt]
            for band in bands:
                raw_start = _minutes_of_day(band["timeStart"])
                raw_end = _minutes_of_day(band["timeEnd"])
                headway = band["headwayMinutes"]
                if raw_start is None or raw_end is None or headway is None or headway <= 0:
                    continue
                start = raw_start + shift
                end = raw_end + shift + (24 * 60 if raw_end < raw_start else 0)
                if end < start:
                    continue

                for direction_key, _direction_label in streams:
                    travel = _total_travel_minutes(conn, line_id, direction_key)
                    if travel <= 0:
                        continue
                    # Earliest slot that could still be active: now - travel.
                    # Snap to the band's headway grid.
                    earliest = max(start, now_minutes - travel)
                    skips = max(0, int((earliest - start) / headway))
                    slot = start + skips * headway
                    while slot <= end and slot <= now_minutes + 0.5:
                        elapsed = now_minutes - slot
                        if 0 <= elapsed <= travel:
                            key = (display_line_id, direction_key, int(round(slot)))
                            if key not in seen:
                                seen.add(key)
                                out.append({
                                    "lineId": display_line_id,
                                    "directionKey": direction_key,
                                    "originDepartureMinute": round(slot, 2),
                                    "elapsedMinutes": round(elapsed, 2),
                                    "totalTravelMinutes": travel,
                                    "serviceType": _service_type(line_id, band["label"]),
                                })
                        slot += headway

    return out


def _next_airport_lookahead(
    *,
    bundle: dict,
    now: datetime,
    now_minutes: int,
    station_id: str,
    conn: sqlite3.Connection,
) -> Departure | None:
    offset = _station_offset_minutes(conn, "M3_AIR", "outbound", station_id)
    for day_offset in range(AIRPORT_LOOKAHEAD_DAYS):
        date = now + timedelta(days=day_offset)
        weekday = (date.isoweekday() % 7) + 1
        holiday = HOLIDAY_DAY_TYPE.get((date.month, date.day))
        dt = _day_type(weekday, holiday)
        rule = next((r for r in bundle["rules"] if r["dayType"] == dt), None)
        if rule is None:
            continue
        if not rule["is247"]:
            open_min = _minutes_of_day(rule["openTime"])
            close_min = _minutes_of_day(rule["closeTime"])
            if open_min is not None and close_min is not None:
                effective_close = close_min + 24 * 60 if close_min <= open_min else close_min
                if day_offset == 0 and now_minutes > effective_close:
                    continue

        bands = sorted(
            [b for b in bundle["bands"] if b["dayType"] == dt],
            key=lambda b: _minutes_of_day(b["timeStart"]) or 0,
        )
        for band in bands:
            raw_start = _minutes_of_day(band["timeStart"])
            raw_end = _minutes_of_day(band["timeEnd"])
            headway = band["headwayMinutes"]
            if raw_start is None or raw_end is None or headway is None or headway <= 0:
                continue
            end = raw_end + (24 * 60 if raw_end < raw_start else 0)
            slot = float(raw_start)
            if day_offset == 0 and slot + offset < now_minutes:
                skips = max(0, int((now_minutes - (slot + offset)) / headway))
                slot = raw_start + skips * headway
                while slot + offset < now_minutes:
                    slot += headway
            if slot <= end:
                total_minutes = int(round(slot)) + offset + day_offset * 24 * 60
                display_min = ((total_minutes % (24 * 60)) + 24 * 60) % (24 * 60)
                time_str = f"{display_min // 60:02d}:{display_min % 60:02d}"
                minutes_away = max(0, total_minutes - now_minutes)
                return Departure(
                    lineId="M3",
                    line="M3",
                    directionKey="outbound",
                    direction="Airport",
                    time=time_str,
                    minutesAway=minutes_away,
                    serviceType="airport",
                )
    return None
