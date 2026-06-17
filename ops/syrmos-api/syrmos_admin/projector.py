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

    # Skip lines marked closed for today by date_overrides — typically a
    # STASY announcement (e.g. "Δεν λειτουργούν την Παρασκευή 1η Μαΐου").
    closed_today = _closed_lines_for_date(conn, now.date().isoformat())

    direction_filter = _normalise_direction(direction)
    out: list[Departure] = []
    today_dt = _day_type(weekday, holiday)
    for lid in expanded:
        if lid in closed_today or _display_line_id(lid) in closed_today:
            continue
        if lid in SCHEDULED_TRIP_LINES and _has_scheduled_trips(conn, lid, today_dt):
            _project_scheduled_trip_departures(
                conn=conn,
                line_id=lid,
                station_id=station_id,
                day_type=today_dt,
                direction_filter=direction_filter,
                now_minutes=now_minutes,
                limit=limit,
                out=out,
            )
            continue
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
    # AND the next from-Airport train, even if they're hours away. The
    # lookahead scans forward up to 7 days so the row appears after the
    # last airport service has run for today. Check direction explicitly
    # so an outbound airport row doesn't satisfy the inbound requirement,
    # and vice versa.
    can_lookahead = (
        "M3_AIR" in expanded
        and station_id not in LINE3_AIRPORT_ONLY_STATIONS
    )
    if can_lookahead:
        bundle = bundles.get("M3_AIR")
        if bundle is not None:
            wanted_directions: list[str] = []
            if direction_filter in (None, "outbound"):
                wanted_directions.append("outbound")
            if direction_filter in (None, "inbound"):
                wanted_directions.append("inbound")
            for d_key in wanted_directions:
                has_match = any(
                    d.serviceType == "airport" and d.directionKey == d_key
                    for d in out
                )
                if has_match:
                    continue
                la = _next_airport_lookahead(
                    bundle=bundle,
                    now=now,
                    now_minutes=now_minutes,
                    station_id=station_id,
                    conn=conn,
                    direction_key=d_key,
                )
                if la is not None:
                    out.append(la)
            # Re-dedup so any same-minute city row collapses into the
            # airport-labeled row (the dedup keeps the airport label).
            out = _dedupe(out)
            out.sort(key=lambda d: d.minutesAway)
            out = out[:limit]

    return [d.to_dict() for d in out]


# --- internals ---

def _closed_lines_for_date(conn: sqlite3.Connection, date_iso: str) -> set[str]:
    """Lines that should emit no departures / no active trains for date_iso.
    Populated by the STASY announcements scraper when it detects a
    closure ("Δεν λειτουργούν την …"). Each row's payload is JSON; we
    treat any row with payload.closed == true OR payload.is_closed == true
    as a closure for that (line_id, date)."""
    closed: set[str] = set()
    try:
        rows = conn.execute(
            "SELECT line_id, payload_json FROM date_overrides WHERE override_date=?",
            (date_iso,),
        ).fetchall()
    except sqlite3.OperationalError:
        return closed
    import json as _json
    for r in rows:
        try:
            data = _json.loads(r["payload_json"] or "{}")
        except (ValueError, TypeError):
            continue
        if data.get("closed") is True or data.get("is_closed") is True:
            closed.add(r["line_id"])
    return closed


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
    # Step 1: Line 3 airport/city merge. A Line 3 train scheduled at the
    # same minute is ONE physical train in each direction — the airport
    # run is just the city train that continues past Doukissis Plakentias.
    # When a city row and an airport row share the same minute AND
    # direction, drop the city row so the Airport (outbound) / from-Airport
    # (inbound) label wins.
    airport_times_by_dir: dict[str, set[str]] = {"outbound": set(), "inbound": set()}
    for row in rows:
        if row.lineId == "M3" and row.serviceType == "airport":
            airport_times_by_dir.setdefault(row.directionKey, set()).add(row.time)
    filtered: list[Departure] = []
    for row in rows:
        if (
            row.lineId == "M3"
            and row.serviceType != "airport"
            and row.time in airport_times_by_dir.get(row.directionKey, set())
        ):
            continue
        filtered.append(row)

    # Step 2: exact-duplicate suppression. Key by (lineId, directionKey,
    # direction, time). serviceType is metadata, not identity: two
    # overlapping bands at the same minute represent ONE train, just
    # labeled differently.
    seen: set[tuple[str, str, str, str]] = set()
    out: list[Departure] = []
    for row in filtered:
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
        "SELECT day_type, time_start, time_end, headway_minutes, label, direction"
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
                "direction": (r["direction"] if "direction" in r.keys() else "both") or "both",
            }
            for r in band_rows
        ],
    }


def _direction_streams(line_id: str, conn: sqlite3.Connection):
    # M3_AIR runs in both directions per STASY's separate Airport
    # timetables (Dim. Theatro→Airport and Airport→Dim. Theatro). Expose
    # both so a station like Nikaia can show "to Airport" alongside
    # "from Airport".
    if line_id == "M3_AIR":
        return [
            ("outbound", "Airport"),
            ("inbound", "Dimotiko Theatro"),
        ]
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
    # Try the line's own offsets first; fall back to M3 city offsets for
    # M3_AIR if no M3_AIR-specific row exists yet (the airport route
    # shares track up to Doukissis Plakentias with M3 city service).
    row = conn.execute(
        "SELECT minutes_from_origin FROM station_offsets"
        " WHERE line_id=? AND direction=? AND station_id=? LIMIT 1",
        (line_id, direction.lower(), station_id),
    ).fetchone()
    if row is None and line_id == "M3_AIR":
        row = conn.execute(
            "SELECT minutes_from_origin FROM station_offsets"
            " WHERE line_id='M3' AND direction=? AND station_id=? LIMIT 1",
            (direction.lower(), station_id),
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
                rule_close = close_min + 24 * 60 if close_min <= open_min else close_min
                # OASA's rule.closeTime is when the station officially
                # shuts; the bands themselves carry the truth and often
                # extend past that mark (M1 mon_thu rule says 00:30 but
                # the late_night band runs to 01:30). Honour the bands
                # so a passenger boarding at 01:25 still sees the
                # final train of the night.
                band_max_end = rule_close
                for b in bundle["bands"]:
                    if b["dayType"] != dt:
                        continue
                    rs = _minutes_of_day(b["timeStart"])
                    re = _minutes_of_day(b["timeEnd"])
                    if rs is None or re is None:
                        continue
                    band_end = re + (24 * 60 if re < rs else 0)
                    if band_end > band_max_end:
                        band_max_end = band_end
                effective_close = max(rule_close, band_max_end)
                # Convert today's wall-clock minute back into the rule's
                # own clock domain. For yesterday's overnight descriptor
                # shift = -1440 (yesterday-clock = today-clock + 1440)
                # so we *subtract* the shift to put nowMinutes back into
                # yesterday's frame. The previous formula used + shift,
                # which mapped today's 01:25 to yesterday's -1355 and
                # threw away every late-night M1 / M2 / M3 train still
                # in transit right after midnight.
                effective_now = now_minutes - shift
                # 2-hour slack on both sides so trains in transit through
                # stations DOWNSTREAM of the band's origin still emit
                # after the last slot leaves the terminus. M1 last slot
                # leaves Piraeus at 01:30; Tavros offset is 11 min so the
                # last train passes Tavros at 01:41. Without slack the
                # band is rejected at 01:38 and Tavros shows nothing
                # even though a train is 3 minutes away.
                if effective_now < open_min - 120 or effective_now > effective_close + 120:
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
            band_dir = (band.get("direction") or "both").lower()
            for direction_key, direction_label in streams:
                if band_dir != "both" and band_dir != direction_key:
                    continue
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


# Suburban lines run on a fixed published timetable (scheduled_trips), not on
# a regular headway grid. The projector picks the explicit-trips path for
# these when the line has rows in scheduled_trips for the current day_type,
# and falls back to the frequency_bands path otherwise.
SCHEDULED_TRIP_LINES = {"A1", "A2", "A3", "A4"}


def _hhmm_to_minutes(hhmm: str) -> int | None:
    if not hhmm or ":" not in hhmm:
        return None
    try:
        h, m = hhmm.split(":")
        return int(h) * 60 + int(m)
    except ValueError:
        return None


def _has_scheduled_trips(conn: sqlite3.Connection, line_id: str, day_type: str) -> bool:
    row = conn.execute(
        "SELECT 1 FROM scheduled_trips WHERE line_id=? AND day_type=? LIMIT 1",
        (line_id, day_type),
    ).fetchone()
    return row is not None


def _project_scheduled_trip_departures(
    conn: sqlite3.Connection,
    line_id: str,
    station_id: str,
    day_type: str,
    direction_filter: str | None,
    now_minutes: int,
    limit: int,
    out: list[Departure],
) -> None:
    """Departures path for suburban lines. Reads scheduled_trip_stops
    directly so the output mirrors Hellenic Train's published HH:MM."""
    rows = conn.execute(
        "SELECT s.train_no, s.direction, s.departure_time"
        " FROM scheduled_trip_stops s"
        " WHERE s.line_id=? AND s.day_type=? AND s.station_id=?"
        " ORDER BY s.departure_time",
        (line_id, day_type, station_id),
    ).fetchall()
    candidates: list[Departure] = []
    for r in rows:
        if direction_filter and r["direction"] != direction_filter:
            continue
        raw = _hhmm_to_minutes(r["departure_time"])
        if raw is None:
            continue
        delta = raw - now_minutes
        if delta < -1:
            continue
        # Resolve destination by looking up the trip's actual terminus.
        # Seed stop_sequence is in canonical (outbound) station order, so
        # outbound trains end at the highest sequence and inbound trains
        # end at the lowest. Partial-route trains (e.g. 3201 terminating
        # at Tavros) get the correct terminus from this — not the line's
        # nominal endpoint.
        if r["direction"] == "outbound":
            terminus = conn.execute(
                "SELECT station_id FROM scheduled_trip_stops"
                " WHERE line_id=? AND day_type=? AND direction=? AND train_no=?"
                " ORDER BY stop_sequence DESC LIMIT 1",
                (line_id, day_type, r["direction"], r["train_no"]),
            ).fetchone()
        else:
            terminus = conn.execute(
                "SELECT station_id FROM scheduled_trip_stops"
                " WHERE line_id=? AND day_type=? AND direction=? AND train_no=?"
                " ORDER BY stop_sequence ASC LIMIT 1",
                (line_id, day_type, r["direction"], r["train_no"]),
            ).fetchone()
        destination = _suburban_terminus_label(line_id, r["direction"], terminus["station_id"] if terminus else "")
        candidates.append(Departure(
            lineId=line_id,
            line=line_id,
            directionKey=r["direction"],
            direction=destination,
            time=r["departure_time"],
            minutesAway=max(0, delta),
            serviceType="regular",
        ))
    candidates.sort(key=lambda d: d.minutesAway)
    out.extend(candidates[:limit])


def _suburban_terminus_label(line_id: str, direction: str, last_station_id: str) -> str:
    """Human-readable destination for a suburban trip given its final
    station. Falls back to the station id when no friendly label is known."""
    aliases = {
        "A1_AIR": "Airport", "A1_PIR": "Piraeus", "A1_TAY": "Tavros",
        "A2_AIR": "Airport", "A2_ANO": "Ano Liosia",
        "A3_CHA": "Chalkida", "A3_ATH": "Athens", "A3_AYL": "Avlonas",
        "A4_KIA": "Kiato", "A4_PIR": "Piraeus", "A4_NEA": "Nea Peramos",
    }
    if last_station_id in aliases:
        return aliases[last_station_id]
    return "Outbound" if direction == "outbound" else "Inbound"


def _project_scheduled_trip_active(
    conn: sqlite3.Connection,
    line_id: str,
    day_type: str,
    now_minutes: float,
    out: list[dict],
    seen: set,
) -> None:
    """active_trains path for suburban lines."""
    rows = conn.execute(
        "SELECT train_no, direction,"
        " MIN(departure_time) AS first_time,"
        " MAX(departure_time) AS last_time"
        " FROM scheduled_trip_stops"
        " WHERE line_id=? AND day_type=?"
        " GROUP BY train_no, direction",
        (line_id, day_type),
    ).fetchall()
    for r in rows:
        # The seed encodes stop_sequence in canonical (outbound) station
        # order, so for inbound trips the first stop_sequence isn't the
        # actual trip origin. Sort the stops by departure_time instead, and
        # detect a midnight crossing by looking for a stop whose time wraps.
        edges = conn.execute(
            "SELECT departure_time FROM scheduled_trip_stops"
            " WHERE line_id=? AND day_type=? AND direction=? AND train_no=?",
            (line_id, day_type, r["direction"], r["train_no"]),
        ).fetchall()
        if not edges:
            continue
        times = [_hhmm_to_minutes(e["departure_time"]) for e in edges]
        times = [t for t in times if t is not None]
        if not times:
            continue
        # If the spread is huge, assume any time < min+12h that's smaller
        # than the previous one represents a midnight rollover.
        sorted_times = sorted(times)
        if sorted_times[-1] - sorted_times[0] > 12 * 60:
            # Looks like a midnight wrap: trips that have e.g. 21:32 and
            # 00:26 sort to 00:26 first. Re-bucket: anything below 5*60 is
            # "next day".
            adjusted = [(t + 24 * 60 if t < 5 * 60 else t) for t in times]
            start = min(adjusted)
            end = max(adjusted)
        else:
            start = min(times)
            end = max(times)
        if not (start <= now_minutes <= end):
            continue
        travel = end - start
        elapsed = now_minutes - start
        key = (line_id, r["direction"], r["train_no"])
        if key in seen:
            continue
        seen.add(key)
        out.append({
            "lineId": line_id,
            "directionKey": r["direction"],
            "originDepartureMinute": round(start, 2),
            "elapsedMinutes": round(elapsed, 2),
            "totalTravelMinutes": travel,
            "serviceType": "regular",
            "trainNo": r["train_no"],
        })


def _total_travel_minutes(conn: sqlite3.Connection, line_id: str, direction: str) -> int:
    """Maximum minutes_from_origin across all stations on this leg — i.e. the
    travel time from the first station to the terminus. Used to decide whether
    a train that already departed origin is still on the line."""
    row = conn.execute(
        "SELECT MAX(minutes_from_origin) AS m FROM station_offsets"
        " WHERE line_id=? AND direction=?",
        (line_id, direction.lower()),
    ).fetchone()
    if (row is None or row[0] is None) and line_id == "M3_AIR":
        row = conn.execute(
            "SELECT MAX(minutes_from_origin) AS m FROM station_offsets"
            " WHERE line_id='M3' AND direction=?",
            (direction.lower(),),
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

    closed_today = _closed_lines_for_date(conn, now.date().isoformat())

    out: list[dict] = []
    seen: set[tuple[str, str, int]] = set()

    today_dt_active = _day_type(weekday, holiday)
    for line_id in line_ids:
        if line_id in closed_today or _display_line_id(line_id) in closed_today:
            continue
        if line_id in SCHEDULED_TRIP_LINES and _has_scheduled_trips(conn, line_id, today_dt_active):
            _project_scheduled_trip_active(
                conn=conn,
                line_id=line_id,
                day_type=today_dt_active,
                now_minutes=now_minutes,
                out=out,
                seen=seen,
            )
            continue
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
                    rule_close = close_min + 24 * 60 if close_min <= open_min else close_min
                    # OASA publishes rule.closeTime as "station shuts"; the
                    # bands themselves describe when trains actually run and
                    # commonly extend past close (M1 mon_thu bands go to
                    # 01:30 even though the rule says 00:30). Honour the
                    # bands so any train still in transit is counted.
                    band_max_end = rule_close
                    for b in bundle["bands"]:
                        if b["dayType"] != dt:
                            continue
                        rs = _minutes_of_day(b["timeStart"])
                        re = _minutes_of_day(b["timeEnd"])
                        if rs is None or re is None:
                            continue
                        band_end = re + (24 * 60 if re < rs else 0)
                        if band_end > band_max_end:
                            band_max_end = band_end
                    effective_close = max(rule_close, band_max_end)
                    # Convert today's wall-clock minute back into the rule's
                    # clock domain. For yesterday's descriptor shift = -1440
                    # (yesterday_clock = today_clock + 1440), so we *subtract*
                    # the shift. The previous formula used `+ shift`, which
                    # mapped today's 00:35 to yesterday's -1405 and threw
                    # away every late-night M1 / M2 / M3 train still in
                    # transit right after midnight.
                    effective_now = now_minutes - shift
                    # 2-hour slack on either side so a train that departed
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

                band_dir = (band.get("direction") or "both").lower()
                for direction_key, _direction_label in streams:
                    if band_dir != "both" and band_dir != direction_key:
                        continue
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
    direction_key: str = "outbound",
) -> Departure | None:
    """Find the next M3_AIR slot for direction_key at station_id.

    direction_key="outbound" -> next train to Airport (terminus "Airport").
    direction_key="inbound"  -> next train from Airport going to Dim.
    Theatro (terminus "Dimotiko Theatro"). The serviceType stays "airport"
    so the iOS / Android / Web badge fires on inbound rows too.
    """
    offset = _station_offset_minutes(conn, "M3_AIR", direction_key, station_id)
    terminus = "Airport" if direction_key == "outbound" else "Dimotiko Theatro"
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

        # Bands can be direction-tagged. Keep "both" plus the matching
        # direction; this matches the natural projection's band filter.
        bands = sorted(
            [
                b for b in bundle["bands"]
                if b["dayType"] == dt
                and (b.get("direction", "both") in (direction_key, "both"))
            ],
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
                    directionKey=direction_key,
                    direction=terminus,
                    time=time_str,
                    minutesAway=minutes_away,
                    serviceType="airport",
                )
    return None
