"""Honest service status for the railway.gov.gr live telematics feed.

The suburban (Proastiakos) live feed broadcasts a GPS position for every vehicle
it can see, including trains parked in a depot/yard at 0 km/h and vehicles with
no current passenger-route assignment. Rendered raw, those appear on the map as
ordinary boardable trains: a purple cluster sitting motionless in a yard, whose
detail sheet still reads "On time". This module derives an honest status so the
clients never imply a parked vehicle is boardable.

Design principles
-----------------
1. Never hide a real passenger service. A vehicle the feed could assign to a
   passenger route (A1-A4 suburban, or IC intercity) is ALWAYS "in_service",
   even at 0 km/h, because that is a normal station dwell. Only vehicles the
   upstream could NOT assign to any corridor (line_id == UNASSIGNED_LINE, "P")
   are candidates for a parked / out-of-service classification.
2. Derive only from data the feed already carries (route assignment, speed,
   position) plus documented depot coordinates. Never invent movement.
3. One chokepoint. Deriving here (in the daemon that writes /api/trains) fixes
   the web, iOS and Android maps at once, because all three consume that feed.

Status values
-------------
    in_service      assigned to a passenger route (moving or dwelling) -> boardable
    position_only   has a live GPS position but no route assignment, and is
                    moving -> show, but clearly not a boardable service
    parked_yard     unassigned and stationary at/near a known depot/yard
    not_in_service  unassigned and stationary away from a known depot

Only in_service is boardable. parked_yard / not_in_service are withheld from the
default passenger map (SHOW_ON_MAP); position_only is shown with secondary
styling and an explicit label.

"Stale position" is a freshness concern derived from the feed's batch updatedAt
by the client DataStatus/Freshness contract, not a per-vehicle field here.

Source of depot coordinates: seed_greek_corridors.py (SKA Acharnon) and the iOS
StationCoordinates (Rentis), cross-checked 2026-08-30.
"""
from __future__ import annotations

import math

UNASSIGNED_LINE = "P"  # infer_line_id's fallback when no corridor matches
STATIONARY_KMH = 1.0   # <= this counts as not moving (GPS jitter tolerance)

# Known stabling yards / depots for Athens suburban rail. A stationary
# UNASSIGNED vehicle here is parked, not a passenger service.
DEPOTS: list[tuple[str, float, float]] = [
    ("SKA Acharnon", 38.054188, 23.732645),   # main suburban yard / control center
    ("Rentis", 37.9622619, 23.6683076),        # Piraeus-side stabling
]
DEPOT_RADIUS_KM = 1.2

# Statuses that represent a boardable passenger service.
BOARDABLE = frozenset({"in_service"})
# Statuses that belong on the default passenger map (the rest are withheld).
SHOW_ON_MAP = frozenset({"in_service", "position_only"})

SOURCE_NOTE = (
    "Live status derived from railway.gov.gr telematics (route assignment, "
    "speed, position). Parked/unassigned yard vehicles are not boardable."
)


def _haversine_km(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    r = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = math.radians(lat2 - lat1)
    dl = math.radians(lng2 - lng1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(min(1.0, math.sqrt(a)))


def near_depot(lat, lng) -> bool:
    if lat is None or lng is None:
        return False
    try:
        latf, lngf = float(lat), float(lng)
    except (TypeError, ValueError):
        return False
    if latf == 0.0 and lngf == 0.0:
        return False
    return any(
        _haversine_km(latf, lngf, dlat, dlng) <= DEPOT_RADIUS_KM
        for _name, dlat, dlng in DEPOTS
    )


def derive_status(*, line_id, speed, lat, lng) -> str:
    """Classify one live vehicle. See module docstring for the rules."""
    assigned = bool(line_id) and line_id != UNASSIGNED_LINE
    if assigned:
        # A real passenger service, moving or dwelling at a station.
        return "in_service"
    # Unassigned ("P"): the feed could not place it on a passenger corridor.
    stationary = speed is not None and speed <= STATIONARY_KMH
    if not stationary:
        return "position_only"
    return "parked_yard" if near_depot(lat, lng) else "not_in_service"


def is_boardable(status: str) -> bool:
    return status in BOARDABLE


def show_on_passenger_map(status: str) -> bool:
    return status in SHOW_ON_MAP


def annotate(train: dict) -> dict:
    """Add status + inService to a built train dict (in place) and return it."""
    status = derive_status(
        line_id=train.get("lineId"),
        speed=train.get("speed"),
        lat=train.get("lat"),
        lng=train.get("lng"),
    )
    train["status"] = status
    train["inService"] = is_boardable(status)
    return train
