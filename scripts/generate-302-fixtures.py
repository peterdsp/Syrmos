#!/usr/bin/env python3
"""Reference implementation + golden fixture generator for the 3.0.2 shared rules.

Writes:
  fixtures/closures/effect.json      closure window + per-station effect
  fixtures/nearby/coach.json         walk minutes, coach cases, headline, variant hash, guards
  fixtures/calendar/leave-by.json    event classifier, arrival buffer, leave-at plan

Kotlin (core/common), Swift (iosApp) and web must reproduce every expected value.
The server reuses closure_effect() via syrmos_admin/closures.py.
Run: python3 scripts/generate-302-fixtures.py
"""
from __future__ import annotations

import json
import math
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# ---------------------------------------------------------------------------
# Closures
# ---------------------------------------------------------------------------

MODE_ORDER = ["closed", "pass_through", "exit_only", "entry_only"]
UPCOMING_WINDOW_SECONDS = 24 * 3600


def closure_is_active(c: dict, now: int) -> bool:
    if c.get("status") in ("draft", "cancelled"):
        return False
    if now < c["startsAtEpochSeconds"]:
        return False
    ends = c.get("endsAtEpochSeconds")
    return ends is None or now < ends


def closure_applies(c: dict, station_id: str, line_id: str | None) -> bool:
    if station_id not in c["stationIds"]:
        return False
    lines = c.get("lineIds") or []
    return not lines or line_id is None or line_id in lines


def closure_effect(closures: list[dict], station_id: str, line_id: str | None, now: int) -> dict:
    active = [c for c in closures if closure_applies(c, station_id, line_id) and closure_is_active(c, now)]
    if not active:
        upcoming = [
            c for c in closures
            if closure_applies(c, station_id, line_id)
            and c.get("status") not in ("draft", "cancelled")
            and now < c["startsAtEpochSeconds"] <= now + UPCOMING_WINDOW_SECONDS
        ]
        upcoming.sort(key=lambda c: (c["startsAtEpochSeconds"], c["id"]))
        return {
            "active": False, "mode": None, "canBoard": True, "canAlight": True,
            "closureId": None, "endsAtEpochSeconds": None,
            "upcomingClosureId": upcoming[0]["id"] if upcoming else None,
            "upcomingStartsAtEpochSeconds": upcoming[0]["startsAtEpochSeconds"] if upcoming else None,
        }
    can_board = all(c["mode"] == "entry_only" for c in active)
    can_alight = all(c["mode"] == "exit_only" for c in active)
    if not can_board and not can_alight:
        modes = [c["mode"] for c in active]
        mode = next(m for m in MODE_ORDER if m in modes)
        if mode in ("exit_only", "entry_only"):
            mode = "closed"  # exit_only + entry_only together leave nothing open
    else:
        mode = active[0]["mode"]
    active.sort(key=lambda c: (MODE_ORDER.index(c["mode"]), c["id"]))
    primary = active[0]
    # Latest end wins; any open-ended closure means "until further notice".
    ends = [c.get("endsAtEpochSeconds") for c in active]
    ends_at = None if any(e is None for e in ends) else max(ends)
    return {
        "active": True, "mode": mode, "canBoard": can_board, "canAlight": can_alight,
        "closureId": primary["id"], "endsAtEpochSeconds": ends_at,
        "upcomingClosureId": None, "upcomingStartsAtEpochSeconds": None,
    }


T0 = 1793167200  # 2026-10-28T08:00:00+02:00 (06:00Z), Athens is UTC+2 after 25 Oct

CLOSURES = [
    {"id": "syn-parade", "stationIds": ["M2_SYN", "M3_SYN"], "lineIds": [], "mode": "pass_through",
     "startsAtEpochSeconds": T0, "endsAtEpochSeconds": None, "status": "scheduled"},
    {"id": "eva-works", "stationIds": ["M3_EVA"], "lineIds": ["M3"], "mode": "closed",
     "startsAtEpochSeconds": T0, "endsAtEpochSeconds": T0 + 8 * 3600, "status": "active"},
    {"id": "nik-exit", "stationIds": ["M3_NIK"], "lineIds": [], "mode": "exit_only",
     "startsAtEpochSeconds": T0, "endsAtEpochSeconds": T0 + 3600, "status": "active"},
    {"id": "nik-entry", "stationIds": ["M3_NIK"], "lineIds": [], "mode": "entry_only",
     "startsAtEpochSeconds": T0 + 1800, "endsAtEpochSeconds": T0 + 7200, "status": "active"},
    {"id": "draft-x", "stationIds": ["M3_EVA"], "lineIds": [], "mode": "closed",
     "startsAtEpochSeconds": T0 - 3600, "endsAtEpochSeconds": None, "status": "draft"},
]

CLOSURE_QUERIES = [
    ("before_start_upcoming", "M2_SYN", "M2", T0 - 3600),
    ("before_window_far", "M2_SYN", "M2", T0 - 25 * 3600),
    ("at_start_active", "M2_SYN", "M2", T0),
    ("open_ended_still_active", "M3_SYN", "M3", T0 + 30 * 86400),
    ("line_scoped_other_line_unaffected", "M3_EVA", "M1", T0 + 60),
    ("line_scoped_matching_line", "M3_EVA", "M3", T0 + 60),
    ("line_null_means_any_line", "M3_EVA", None, T0 + 60),
    ("end_is_exclusive", "M3_EVA", "M3", T0 + 8 * 3600),
    ("draft_ignored", "M3_EVA", "M3", T0 - 60),
    ("exit_only_alone", "M3_NIK", "M3", T0 + 60),
    ("exit_plus_entry_is_closed", "M3_NIK", "M3", T0 + 1800),
    ("entry_only_alone", "M3_NIK", "M3", T0 + 3600),
    ("unrelated_station", "M1_KIF", "M1", T0 + 60),
]

# ---------------------------------------------------------------------------
# Nearby coach
# ---------------------------------------------------------------------------

def walk_minutes(distance_meters: float) -> int:
    return max(1, math.ceil(distance_meters * 1.3 / 80.0))


RELAX_MAX_WAIT = 8
HURRY_MAX_MARGIN = 2
HEADLINE_PRIORITY = ["stationClosed", "lastTrain", "missedLast", "relaxNextOneIsClose",
                     "hurry", "longGapTakeItEasy", "comfortable", "noService"]


def coach_case(walk: int, departures: list[dict], station_closed: bool) -> dict:
    """departures: [{minutes, isLast}] ascending, minutes from now (0 = now)."""
    if station_closed:
        return {"case": "stationClosed"}
    if not departures:
        return {"case": "noService"}
    first = departures[0]
    catchable = [d for d in departures if d["minutes"] >= walk]
    if first["minutes"] < walk:
        if not catchable:
            return {"case": "missedLast" if first.get("isLast") or departures[-1].get("isLast") else "noService",
                    "nextMinutes": first["minutes"]}
        nxt = catchable[0]
        if nxt.get("isLast"):
            return {"case": "lastTrain", "nextMinutes": nxt["minutes"], "tight": nxt["minutes"] - walk <= HURRY_MAX_MARGIN}
        wait = nxt["minutes"] - walk
        case = "relaxNextOneIsClose" if wait <= RELAX_MAX_WAIT else "longGapTakeItEasy"
        return {"case": case, "nextMinutes": first["minutes"], "followingMinutes": nxt["minutes"], "waitMinutes": wait}
    margin = first["minutes"] - walk
    if first.get("isLast"):
        return {"case": "lastTrain", "nextMinutes": first["minutes"], "tight": margin <= HURRY_MAX_MARGIN}
    if margin <= HURRY_MAX_MARGIN:
        return {"case": "hurry", "nextMinutes": first["minutes"], "marginMinutes": margin}
    return {"case": "comfortable", "nextMinutes": first["minutes"], "marginMinutes": margin}


def headline_index(cases: list[str]) -> int | None:
    best = None
    for i, c in enumerate(cases):
        if best is None or HEADLINE_PRIORITY.index(c) < HEADLINE_PRIORITY.index(cases[best]):
            best = i
    return best


def fnv1a32(text: str) -> int:
    h = 0x811C9DC5
    for b in text.encode("utf-8"):
        h ^= b
        h = (h * 0x01000193) & 0xFFFFFFFF
    return h


def variant_index(station_id: str, athens_date: str, variant_count: int) -> int:
    return fnv1a32(f"{station_id}|{athens_date}") % variant_count


COOLDOWN_SECONDS = 45 * 60
MAX_PER_DAY = 4
MAX_SPEED_KMH = 25.0


def guard(g: dict) -> str:
    """Returns 'allow' or the first blocking reason, in this exact order."""
    if not g["enabled"]:
        return "disabled"
    if g["goJourneyActive"]:
        return "goActive"
    h = g["athensHour"]
    if h >= 23 or h < 7:
        return "quietHours"
    if g.get("speedKmh") is not None and g["speedKmh"] > MAX_SPEED_KMH:
        return "moving"
    last = g.get("lastNotifiedForStationEpochSeconds")
    if last is not None and g["now"] - last < COOLDOWN_SECONDS:
        return "cooldown"
    if g["notificationsToday"] >= MAX_PER_DAY:
        return "dailyCap"
    return "allow"


# ---------------------------------------------------------------------------
# Calendar leave-by
# ---------------------------------------------------------------------------

FOLD = str.maketrans({
    "ά": "α", "έ": "ε", "ή": "η", "ί": "ι", "ό": "ο", "ύ": "υ", "ώ": "ω",
    "ϊ": "ι", "ΐ": "ι", "ϋ": "υ", "ΰ": "υ", "ς": "σ",
    "à": "a", "á": "a", "â": "a", "è": "e", "é": "e", "ê": "e", "ë": "e",
    "ì": "i", "í": "i", "î": "i", "ò": "o", "ó": "o", "ô": "o", "ù": "u", "ú": "u", "û": "u", "ç": "c",
})


def fold(text: str) -> str:
    return text.lower().translate(FOLD)


# Substring keywords (already folded). Token keywords must match a whole token.
CLASSES = [
    ("flight",
     ["airport", "flight", "αεροδρομιο", "πτηση", "aeroporto", "volo", "fluturim", "aeroport", "venizelos", "βενιζελοσ", "makedonia airport"],
     ["ath", "skg"]),
    ("longDistance",
     ["ferry", "πλοιο", "λιμανι", "port of piraeus", "rafina", "ραφηνα", "lavrio", "λαυριο", "intercity", "hellenic train", "τρενο", "κτελ", "ktel", "σταθμοσ λαρισησ", "larissa station"],
     ["ose", "οσε"]),
    ("venue",
     ["οακα", "oaka", "stadium", "γηπεδο", "karaiskakis", "καραισκακη", "concert", "συναυλια", "theatre", "theater", "θεατρο", "megaron", "μεγαρο", "badminton", "cinema", "σινεμα", "ηρωδειο", "herodion", "festival", "φεστιβαλ"],
     []),
    ("appointment",
     ["doctor", "γιατρ", "hospital", "νοσοκομ", "clinic", "κλινικ", "exam", "εξετασ", "interview", "συνεντευξ", "dentist", "οδοντ", "embassy", "πρεσβεια"],
     ["dr", "kep", "κεπ"]),
]

BUFFER_MINUTES = {"flight": 120, "longDistance": 25, "venue": 30, "appointment": 15, "default": 5}
FLIGHT_BUFFER_CHOICES = [90, 120, 150]
MAX_MARGIN = 20
HEADS_UP_MINUTES = 15
FLIGHT_CHECKLIST_MINUTES = 180
HORIZON_SECONDS = 48 * 3600
RESCHEDULE_THRESHOLD_SECONDS = 300


def classify(title: str, location: str) -> str:
    text = fold(f"{title} {location}")
    tokens = set(t for t in re.split(r"[^0-9a-zα-ω]+", text) if t)
    for name, subs, toks in CLASSES:
        if any(s in text for s in subs) or any(t in tokens for t in toks):
            return name
    return "default"


def reliability_margin(transfers: int, first_leg_headway: int, raining: bool, alert_on_route: bool) -> int:
    m = transfers * 2 + math.ceil(first_leg_headway / 2) + (5 if raining else 0) + (5 if alert_on_route else 0)
    return min(MAX_MARGIN, m)


def eligible(e: dict, now: int) -> str:
    if e.get("allDay"):
        return "allDay"
    if e.get("declined"):
        return "declined"
    if e["startEpochSeconds"] <= now:
        return "past"
    if e["startEpochSeconds"] > now + HORIZON_SECONDS:
        return "beyondHorizon"
    if not e.get("destinationResolved", True):
        return "unresolved"
    return "eligible"


def plan(e: dict, now: int) -> dict:
    kind = classify(e["title"], e.get("location", ""))
    buf = BUFFER_MINUTES[kind]
    if kind == "flight" and e.get("flightBufferMinutes") in FLIGHT_BUFFER_CHOICES:
        buf = e["flightBufferMinutes"]
    margin = reliability_margin(e["transfers"], e["firstLegHeadwayMinutes"], e.get("raining", False), e.get("alertOnRoute", False))
    arrive_by = e["startEpochSeconds"] - buf * 60
    leave_at = arrive_by - (e["journeyMinutes"] + margin) * 60
    if now < leave_at:
        state = "scheduled"
    elif now < arrive_by:
        state = "leaveNow"
    elif now < e["startEpochSeconds"]:
        state = "late"
    else:
        state = "past"
    fires = []
    if kind == "flight" and leave_at - FLIGHT_CHECKLIST_MINUTES * 60 > now:
        fires.append({"kind": "flightChecklist", "atEpochSeconds": leave_at - FLIGHT_CHECKLIST_MINUTES * 60})
    if leave_at - HEADS_UP_MINUTES * 60 > now:
        fires.append({"kind": "headsUp", "atEpochSeconds": leave_at - HEADS_UP_MINUTES * 60})
    if leave_at > now:
        fires.append({"kind": "leaveNow", "atEpochSeconds": leave_at})
    return {"kind": kind, "bufferMinutes": buf, "marginMinutes": margin, "arriveByEpochSeconds": arrive_by,
            "leaveAtEpochSeconds": leave_at, "state": state, "notifications": fires}


def needs_reschedule(old_leave_at: int, new_leave_at: int) -> bool:
    return abs(new_leave_at - old_leave_at) >= RESCHEDULE_THRESHOLD_SECONDS


# ---------------------------------------------------------------------------
# Writers
# ---------------------------------------------------------------------------

def write(rel: str, data: dict) -> None:
    p = ROOT / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("wrote", rel)


def main() -> None:
    write("fixtures/closures/effect.json", {
        "schemaVersion": 1,
        "description": (
            "Station closure effect, shared by server projector, Kotlin StationClosures, Swift StationClosures and web. "
            "A closure is active when status is not draft/cancelled and startsAt <= now < endsAt (endsAt null = until further notice). "
            "It applies to a (station, line) when the station is listed and lineIds is empty, the line is listed, or line is null. "
            "canBoard = every active closure is entry_only; canAlight = every active closure is exit_only. "
            "mode = most restrictive active mode by order closed > pass_through > exit_only > entry_only, except "
            "exit_only + entry_only together report closed. closureId = the active closure with the most restrictive mode (tie: smallest id). "
            "endsAt = null if any active closure is open-ended, else the latest end. When nothing is active, the earliest "
            "non-draft closure starting within the next 24 h (now < startsAt <= now+86400) is reported as upcoming. "
            "Departures are hidden from a station board when canBoard is false; planner may not board where canBoard is false "
            "or alight where canAlight is false; pass_through trains keep running through the station."),
        "closures": CLOSURES,
        "cases": [
            {"id": cid, "stationId": sid, "lineId": lid, "now": now, "expected": closure_effect(CLOSURES, sid, lid, now)}
            for cid, sid, lid, now in CLOSURE_QUERIES
        ],
    })

    walk_cases = [{"distanceMeters": d, "expected": walk_minutes(d)} for d in [0, 30, 61, 62, 400, 615, 1000, 1500]]
    D = lambda *ms, last=None: [{"minutes": m, "isLast": (i == last)} for i, m in enumerate(ms)]
    coach_inputs = [
        ("owner_example_relax", 5, D(2, 10), False),
        ("relax_boundary_wait_8", 5, D(2, 13), False),
        ("long_gap_wait_9", 5, D(2, 14), False),
        ("hurry_margin_0", 5, D(5, 12), False),
        ("hurry_margin_2", 5, D(7, 15), False),
        ("comfortable_margin_3", 5, D(8, 15), False),
        ("last_train_catchable_tight", 5, D(6, last=0), False),
        ("last_train_catchable_relaxed", 5, D(12, last=0), False),
        ("missed_first_next_is_last", 5, D(2, 9, last=1), False),
        ("missed_last_train", 5, D(3, last=0), False),
        ("missed_all_not_last", 5, D(1, 3), False),
        ("no_departures", 5, [], False),
        ("station_closed_wins", 5, D(8, 15), True),
        ("departing_now_walk_1", 1, D(0, 6), False),
    ]
    headline_inputs = [
        ["comfortable", "relaxNextOneIsClose"],
        ["hurry", "longGapTakeItEasy", "comfortable"],
        ["comfortable", "lastTrain"],
        ["noService", "comfortable"],
        ["comfortable", "comfortable"],
        [],
    ]
    variant_inputs = [("M3_NIK", "2026-10-27", 3), ("M3_NIK", "2026-10-28", 3), ("M2_SYN", "2026-10-27", 3),
                      ("T6_SYN", "2026-12-31", 4), ("Νίκαια", "2026-10-27", 3)]
    G = lambda **kw: {"enabled": True, "goJourneyActive": False, "athensHour": 12, "speedKmh": 3.0,
                      "lastNotifiedForStationEpochSeconds": None, "notificationsToday": 0, "now": T0, **kw}
    guard_inputs = [
        ("allow_default", G()),
        ("disabled_first", G(enabled=False, goJourneyActive=True, athensHour=2)),
        ("go_active", G(goJourneyActive=True)),
        ("quiet_23", G(athensHour=23)),
        ("quiet_6", G(athensHour=6)),
        ("hour_7_allowed", G(athensHour=7)),
        ("moving_fast", G(speedKmh=40.0)),
        ("speed_25_allowed", G(speedKmh=25.0)),
        ("speed_unknown_allowed", G(speedKmh=None)),
        ("cooldown_44min", G(lastNotifiedForStationEpochSeconds=T0 - 44 * 60)),
        ("cooldown_45min_allowed", G(lastNotifiedForStationEpochSeconds=T0 - 45 * 60)),
        ("daily_cap", G(notificationsToday=4)),
    ]
    write("fixtures/nearby/coach.json", {
        "schemaVersion": 1,
        "description": (
            "Nearby next-train coach, shared by Kotlin NearbyDepartureCoach and Swift NearbyDepartureCoach. "
            "walkMinutes = max(1, ceil(distanceMeters * 1.3 / 80)). Per (line, destination) direction, departures are minutes "
            "from now ascending with an isLast flag. case rules in order: stationClosed; noService if empty; if first < walk: "
            "no catchable -> missedLast when any listed departure isLast else noService; first catchable isLast -> lastTrain "
            "(tight = margin <= 2); wait = catchable - walk, wait <= 8 -> relaxNextOneIsClose else longGapTakeItEasy. "
            "If first >= walk: isLast -> lastTrain (tight = margin <= 2); margin <= 2 -> hurry; else comfortable. "
            "Headline = index of the direction whose case ranks first in " + " > ".join(HEADLINE_PRIORITY) +
            " (tie: lowest index; empty -> null). Copy variant = fnv1a32(utf8(stationId + '|' + athensDate yyyy-MM-dd)) mod "
            "variantCount. Guards return the first blocking reason in order disabled, goActive, quietHours (hour >= 23 or < 7, "
            "Athens), moving (speed > 25 km/h; null speed allowed), cooldown (< 45 min since last for this station), dailyCap "
            "(>= 4 today), else allow. Geofence radius 1000 m, dwell 60 s, nearest 20 stations monitored."),
        "walkCases": walk_cases,
        "coachCases": [
            {"id": cid, "walkMinutes": w, "departures": deps, "stationClosed": closed, "expected": coach_case(w, deps, closed)}
            for cid, w, deps, closed in coach_inputs
        ],
        "headlineCases": [{"cases": c, "expected": headline_index(c)} for c in headline_inputs],
        "variantCases": [
            {"stationId": s, "athensDate": d, "variantCount": n, "expectedHash": fnv1a32(f"{s}|{d}"), "expected": variant_index(s, d, n)}
            for s, d, n in variant_inputs
        ],
        "guardCases": [{"id": gid, "input": g, "expected": guard(g)} for gid, g in guard_inputs],
    })

    classify_inputs = [
        ("Flight to London", ""), ("Πτήση Μιλάνο", ""), ("Volo per Roma", ""), ("Fluturim Tiranë", ""),
        ("Trip", "Athens International Airport El. Venizelos"), ("A3 602", "ATH"), ("Meeting in Athens", "Syntagma"),
        ("Ferry to Naxos", "Piraeus"), ("Πλοίο Πάρος", "Λιμάνι Ραφήνας"), ("Train to Thessaloniki", "OSE Larissa"),
        ("Συναυλία", "ΟΑΚΑ"), ("Olympiacos", "Karaiskakis Stadium"), ("Θέατρο", "Ηρώδειο"),
        ("Dr. Papadopoulos", ""), ("Οδοντίατρος", ""), ("ΚΕΠ ραντεβού", ""), ("Job interview", "Marousi"),
        ("Dinner with Maria", "Psyrri"), ("Skgames meetup", ""), ("Drinks", "Gazi"),
    ]
    E = lambda **kw: {"title": "Dinner", "location": "Psyrri", "startEpochSeconds": T0 + 6 * 3600, "journeyMinutes": 30,
                      "transfers": 0, "firstLegHeadwayMinutes": 6, "raining": False, "alertOnRoute": False, **kw}
    plan_inputs = [
        ("default_event", E(), T0),
        ("flight_default_120", E(title="Flight to Paris", location="ATH", journeyMinutes=45, transfers=1, firstLegHeadwayMinutes=36), T0),
        ("flight_buffer_150", E(title="Flight to Paris", location="ATH", journeyMinutes=45, firstLegHeadwayMinutes=36, flightBufferMinutes=150), T0),
        ("flight_buffer_invalid_falls_back", E(title="Flight", journeyMinutes=45, firstLegHeadwayMinutes=10, flightBufferMinutes=100), T0),
        ("venue_rain_alert_capped", E(title="Concert", location="OAKA", transfers=3, firstLegHeadwayMinutes=10, raining=True, alertOnRoute=True), T0),
        ("appointment", E(title="Doctor", journeyMinutes=20, firstLegHeadwayMinutes=4), T0),
        ("heads_up_already_passed", E(startEpochSeconds=T0 + 35 * 60, journeyMinutes=20, firstLegHeadwayMinutes=4), T0),
        ("leave_now_window", E(startEpochSeconds=T0 + 25 * 60, journeyMinutes=20, firstLegHeadwayMinutes=4), T0),
        ("late", E(startEpochSeconds=T0 + 3 * 60, journeyMinutes=20, firstLegHeadwayMinutes=4), T0),
    ]
    elig_inputs = [
        ("eligible", {"startEpochSeconds": T0 + 3600}),
        ("all_day", {"startEpochSeconds": T0 + 3600, "allDay": True}),
        ("declined", {"startEpochSeconds": T0 + 3600, "declined": True}),
        ("past", {"startEpochSeconds": T0}),
        ("horizon_edge_ok", {"startEpochSeconds": T0 + 48 * 3600}),
        ("beyond_horizon", {"startEpochSeconds": T0 + 48 * 3600 + 1}),
        ("unresolved", {"startEpochSeconds": T0 + 3600, "destinationResolved": False}),
    ]
    write("fixtures/calendar/leave-by.json", {
        "schemaVersion": 1,
        "description": (
            "Calendar leave-by, shared by Kotlin CalendarLeaveBy (core/common) and Swift CalendarLeaveBy. "
            "fold(text) = lowercase, then map Greek tonos/dialytika vowels to plain, final sigma to sigma, and Latin "
            "àáâèéêëìíîòóôùúûç to plain. classify(title, location): text = fold(title + ' ' + location), tokens = split on "
            "anything that is not [0-9a-zα-ω]; first class whose substring keyword is contained or token keyword is a whole "
            "token, in order flight, longDistance, venue, appointment, else default. Keyword lists are in 'keywords' below. "
            "buffer minutes: " + json.dumps(BUFFER_MINUTES) + "; flight uses flightBufferMinutes when it is one of 90/120/150. "
            "margin = min(20, transfers*2 + ceil(firstLegHeadway/2) + 5 if raining + 5 if alertOnRoute). arriveBy = start - buffer; "
            "leaveAt = arriveBy - (journeyMinutes + margin) minutes. state: now < leaveAt scheduled; < arriveBy leaveNow; "
            "< start late; else past. notifications (only those strictly after now, in this order): flightChecklist at "
            "leaveAt - 180 min (flight only), headsUp at leaveAt - 15 min, leaveNow at leaveAt. Eligibility in order: allDay, "
            "declined, past (start <= now), beyondHorizon (start > now + 48 h), unresolved destination, else eligible. "
            "Reschedule when |newLeaveAt - oldLeaveAt| >= 300 s."),
        "keywords": {name: {"substring": subs, "token": toks} for name, subs, toks in CLASSES},
        "foldCases": [{"input": s, "expected": fold(s)} for s in ["Πτήση", "ΑΕΡΟΔΡΌΜΙΟ", "Καραϊσκάκης", "Tiranë", "Ηρώδειο"]],
        "classifyCases": [{"title": t, "location": l, "expected": classify(t, l)} for t, l in classify_inputs],
        "planCases": [{"id": pid, "event": e, "now": now, "expected": plan(e, now)} for pid, e, now in plan_inputs],
        "eligibilityCases": [{"id": i, "event": e, "now": T0, "expected": eligible(e, T0)} for i, e in elig_inputs],
        "rescheduleCases": [
            {"old": T0, "new": T0 + 299, "expected": needs_reschedule(T0, T0 + 299)},
            {"old": T0, "new": T0 + 300, "expected": needs_reschedule(T0, T0 + 300)},
            {"old": T0, "new": T0 - 600, "expected": needs_reschedule(T0, T0 - 600)},
        ],
    })


if __name__ == "__main__":
    main()
