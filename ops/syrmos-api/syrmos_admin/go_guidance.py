"""Syrmos GO -- live trip-guidance engine (server reference implementation).

GO is the 3.0 "Journeys" spine: once a rider is on a planned journey, tell them
the one thing that matters right now -- board, ride, get off next, change here,
arrived -- so they never have to watch for their stop. This module is the pure,
deterministic core: no DB, no network, no clock. It works fully offline from a
journey's static stop sequence; live positions only advance ``position`` faster.

It mirrors the web reference (``web-go.js``) and is validated against the same
cross-client contract in ``fixtures/go-guidance/cases.json`` so GO guidance cannot
drift between the web, iOS, Android and server implementations.

Server-side GO exists so the backend can drive proactive push updates (iOS
push-to-start / broadcast Live Activities, Android 16 ProgressStyle Live Updates):
given a tracked journey and the vehicle's current stop, the server computes the
next guidance step and pushes it, without the app being open.

A journey is ``{"legs": [{"lineId", "towards", "stops": [{"id", "name"}]}]}`` with
each leg's ``stops`` ordered from board to alight inclusive. A position is
``{"legIndex", "stopIndex"}``: the rider is AT ``legs[legIndex].stops[stopIndex]``.
"""
from __future__ import annotations

from typing import Any, Dict, Optional

BOARD = "board"
RIDE = "ride"
GET_OFF_NEXT = "getOffNext"
TRANSFER = "transfer"
ARRIVED = "arrived"


def _leg(journey: Dict[str, Any], i: int) -> Optional[Dict[str, Any]]:
    legs = journey.get("legs") or []
    return legs[i] if 0 <= i < len(legs) else None


def guidance(journey: Dict[str, Any], position: Dict[str, int]) -> Dict[str, Any]:
    """Return the rider-facing instruction for ``position``.

    Pure and total: an out-of-range position raises ``IndexError`` (a caller bug,
    not a rider state).
    """
    leg_index = position["legIndex"]
    stop_index = position["stopIndex"]
    leg = _leg(journey, leg_index)
    if leg is None:
        raise IndexError("legIndex out of range")
    stops = leg["stops"]
    if stop_index < 0 or stop_index >= len(stops):
        raise IndexError("stopIndex out of range")

    last_leg = leg_index == len(journey["legs"]) - 1
    last_stop = len(stops) - 1
    remaining = last_stop - stop_index
    here = stops[stop_index]

    if last_leg and remaining == 0:
        return {"kind": ARRIVED, "station": here["name"]}

    if remaining == 0:
        nxt = journey["legs"][leg_index + 1]
        return {
            "kind": TRANSFER,
            "atStation": here["name"],
            "toLineId": nxt["lineId"],
            "towards": nxt["towards"],
        }

    if stop_index == 0:
        return {
            "kind": BOARD,
            "lineId": leg["lineId"],
            "towards": leg["towards"],
            "stopsRemaining": remaining,
            "nextStation": stops[1]["name"],
        }

    if remaining == 1:
        nxt = None if last_leg else journey["legs"][leg_index + 1]
        return {
            "kind": GET_OFF_NEXT,
            "nextStation": stops[last_stop]["name"],
            "isDestination": last_leg,
            "transferTo": nxt["lineId"] if nxt else None,
        }

    return {
        "kind": RIDE,
        "lineId": leg["lineId"],
        "towards": leg["towards"],
        "stopsRemaining": remaining,
        "nextStation": stops[stop_index + 1]["name"],
    }


def should_alert_get_off(journey: Dict[str, Any], position: Dict[str, int]) -> bool:
    """Whether a get-off notification should fire now (rider one stop from a leg's
    alight point). Independent of the display kind so a caller can drive the push
    off one predicate; true even on a 2-stop leg where it coincides with boarding.

    Consumer note: on a 2-stop leg the guidance kind stays ``board`` while this is
    already true (board + alert), because after boarding the very next stop is the
    alight. A consumer that dedupes get-off alerts must key the dedupe on the leg's
    alight stop, not on the guidance kind becoming ``getOffNext`` (which never
    happens on a 2-stop leg), or it would drop the rider's only get-off cue.
    """
    leg = _leg(journey, position["legIndex"])
    if leg is None:
        return False
    remaining = len(leg["stops"]) - 1 - position["stopIndex"]
    return remaining == 1


def advance(journey: Dict[str, Any], position: Dict[str, int]) -> Dict[str, int]:
    """Advance one stop, rolling a leg's alight stop onto the next leg's board
    stop. Returns the same position when already at the destination."""
    leg = _leg(journey, position["legIndex"])
    if leg is None:
        raise IndexError("legIndex out of range")
    last_leg = position["legIndex"] == len(journey["legs"]) - 1
    at_leg_end = position["stopIndex"] >= len(leg["stops"]) - 1
    if at_leg_end:
        if last_leg:
            return {"legIndex": position["legIndex"], "stopIndex": position["stopIndex"]}
        return {"legIndex": position["legIndex"] + 1, "stopIndex": 0}
    return {"legIndex": position["legIndex"], "stopIndex": position["stopIndex"] + 1}


def is_arrived(journey: Dict[str, Any], position: Dict[str, int]) -> bool:
    last_leg = position["legIndex"] == len(journey["legs"]) - 1
    leg = _leg(journey, position["legIndex"])
    return bool(last_leg and leg and position["stopIndex"] == len(leg["stops"]) - 1)
