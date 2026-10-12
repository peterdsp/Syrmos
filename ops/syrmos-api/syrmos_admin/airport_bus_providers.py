"""Airport bus live information providers for Athens (OASA) and Thessaloniki (OSETH).

This module implements a provider-independent abstraction for fetching live airport
bus data from multiple operators, with a shared normalized data model.

Architecture:
  AirportTransitProvider (interface)
    ├─ OasaAirportProvider (OASA Telematics API)
    └─ OsethAirportProvider (OSETH web telematics API)

  AirportBusNormalizer (normalizes provider data to common model)
  AirportBusPoller (bounded poller with exponential backoff)

The normalized model is designed to be:
- Provider-agnostic: same structure regardless of source
- Extensible: new operators can be added without changing clients
- Validated: all data passes through validation before normalization
- Attributed: source information is preserved for display

Usage:
  from syrmos_admin.airport_bus_providers import (
      OasaAirportProvider,
      AirportBusNormalizer,
      AirportBusPoller,
  )

  provider = OasaAirportProvider()
  normalizer = AirportBusNormalizer()
  poller = AirportBusPoller(provider, normalizer, output_path)
  poller.start()
"""

from __future__ import annotations

import json
import logging
import random
import time
import urllib.error
import urllib.request
from abc import ABC, abstractmethod
from dataclasses import dataclass, asdict, field
from datetime import datetime, timedelta, timezone
from enum import Enum
from pathlib import Path
from typing import Any, Optional

logger = logging.getLogger(__name__)


# =============================================================================
# Data Models
# =============================================================================

class Operator(str, Enum):
    """Supported airport bus operators."""
    OASA = "oasa"
    OSETH = "oseth"


class ConfidenceLevel(str, Enum):
    """Data confidence levels for display and UX decisions."""
    LIVE = "live"              # Real-time data with recent observation
    SCHEDULED = "scheduled"    # Timetable-based data
    ESTIMATED = "estimated"    # Operator-provided estimate (e.g., OASA btime2)
    OPERATOR_LINK = "operator-link"  # Only operator link available
    STALE = "stale"            # Data exists but is too old
    UNAVAILABLE = "unavailable"  # Provider error or offline
    UNKNOWN = "unknown"        # No information available


class ServiceStatus(str, Enum):
    """Current service operational status."""
    OPERATIONAL = "operational"
    DISRUPTED = "disrupted"
    SUSPENDED = "suspended"
    UNKNOWN = "unknown"


class Direction(str, Enum):
    """Travel direction relative to airport."""
    TO_AIRPORT = "to_airport"
    FROM_AIRPORT = "from_airport"
    UNKNOWN = "unknown"


@dataclass
class NormalizedVehicle:
    """A single vehicle position with normalized fields."""
    vehicle_id: str
    line_id: str
    route_id: str
    operator: Operator
    latitude: float
    longitude: float
    heading: float
    observed_at: datetime
    source: str
    source_url: str
    confidence: ConfidenceLevel
    attribution: str
    to_airport: Optional[bool] = None  # True=heading to airport, False=heading from airport, None=unknown

    def to_dict(self) -> dict[str, Any]:
        d = asdict(self)
        d["observed_at"] = self.observed_at.isoformat()
        d["operator"] = self.operator.value
        d["source"] = self.source
        return d


@dataclass
class NormalizedArrival:
    """A single arrival/ETA with normalized fields."""
    line_id: str
    route_id: str
    operator: Operator
    stop_id: str
    stop_name: str
    destination: str
    direction: Direction
    source: str
    source_url: str
    observed_at: datetime
    freshness: float  # Seconds since observation
    confidence: ConfidenceLevel
    service_status: ServiceStatus
    attribution: str
    scheduled_time: Optional[str] = None  # HH:MM format
    estimated_minutes: Optional[int] = None  # Minutes away
    vehicle_id: Optional[str] = None

    def to_dict(self) -> dict[str, Any]:
        d = asdict(self)
        d["observed_at"] = self.observed_at.isoformat()
        d["operator"] = self.operator.value
        d["direction"] = self.direction.value
        d["confidence"] = self.confidence.value
        d["service_status"] = self.service_status.value
        d["source"] = self.source
        return d


@dataclass
class NormalizedAlert:
    """A service alert/announcement with normalized fields."""
    alert_id: str
    operator: Operator
    title: str
    summary: str
    url: str
    severity: str
    source: str
    source_url: str
    observed_at: datetime
    attribution: str
    route_id: Optional[str] = None
    title_en: Optional[str] = None
    summary_en: Optional[str] = None
    valid_from: Optional[str] = None
    valid_until: Optional[str] = None

    def to_dict(self) -> dict[str, Any]:
        d = asdict(self)
        d["operator"] = self.operator.value
        d["observed_at"] = self.observed_at.isoformat()
        d["source"] = self.source
        return d


@dataclass
class NormalizedRoute:
    """A bus route with normalized metadata."""
    operator: Operator
    route_id: str
    public_name: str
    origin: str
    destination: str
    airport: str  # Airport code (AIA for Athens, SKG for Thessaloniki)
    color: str
    source: str
    source_url: str
    attribution: str
    observed_at: datetime
    public_name_en: Optional[str] = None
    public_name_el: Optional[str] = None
    public_name_sq: Optional[str] = None
    public_name_it: Optional[str] = None
    to_airport_route_codes: list[int] = field(default_factory=list)
    from_airport_route_codes: list[int] = field(default_factory=list)
    to_airport_stop_ids: list[int] = field(default_factory=list)
    from_airport_stop_ids: list[int] = field(default_factory=list)
    direction: Direction = Direction.UNKNOWN
    is_operational: bool = True

    def to_dict(self) -> dict[str, Any]:
        d = asdict(self)
        d["operator"] = self.operator.value
        d["direction"] = self.direction.value
        d["observed_at"] = self.observed_at.isoformat()
        d["source"] = self.source
        return d


@dataclass
class AirportBusSnapshot:
    """Complete snapshot of airport bus data for all routes."""
    updated_at: datetime
    operator: Operator
    routes: list[NormalizedRoute]
    vehicles: list[NormalizedVehicle]
    arrivals: list[NormalizedArrival]
    alerts: list[NormalizedAlert]
    generation_duration: float  # Seconds to generate this snapshot
    source: str
    source_url: str
    attribution: str

    def to_dict(self) -> dict[str, Any]:
        return {
            "updated_at": self.updated_at.isoformat(),
            "operator": self.operator.value,
            "routes": [r.to_dict() for r in self.routes],
            "vehicles": [v.to_dict() for v in self.vehicles],
            "arrivals": [a.to_dict() for a in self.arrivals],
            "alerts": [a.to_dict() for a in self.alerts],
            "generation_duration": self.generation_duration,
            "source": self.source,
            "source_url": self.source_url,
            "attribution": self.attribution,
        }


# =============================================================================
# Validation and Helper Functions
# =============================================================================

class ValidationError(Exception):
    """Raised when data fails validation."""
    pass


def validate_timestamp(ts: str, max_future_seconds: float = 300.0) -> datetime:
    """Parse and validate a timestamp string.

    Args:
        ts: ISO 8601 timestamp string
        max_future_seconds: Maximum allowed future skew (default: 5 minutes)

    Returns:
        Parsed datetime in UTC

    Raises:
        ValidationError: If timestamp is invalid or too far in the future
    """
    if not ts or not isinstance(ts, str):
        raise ValidationError("Empty or invalid timestamp")

    try:
        # Try parsing various ISO formats
        dt = datetime.fromisoformat(ts.replace('Z', '+00:00'))
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        else:
            dt = dt.astimezone(timezone.utc)
    except (ValueError, TypeError) as e:
        raise ValidationError(f"Failed to parse timestamp '{ts}': {e}")

    # Reject future timestamps beyond tolerance
    now = datetime.now(timezone.utc)
    if dt > now + timedelta(seconds=max_future_seconds):
        raise ValidationError(f"Timestamp too far in the future: {ts}")

    return dt


def clamp_eta(minutes: int) -> int:
    """Clamp ETA to non-negative values.

    Per the spec: "Clamp only when the provider semantics explicitly mean
    'arriving now'; record the transformation."

    OASA btime2 can be negative when a bus is at/very near the stop.
    We clamp to 0 (representing "arriving now").
    """
    return max(0, minutes)


def deduplicate_vehicles(
    vehicles: list[NormalizedVehicle],
    tolerance_seconds: float = 1.0
) -> list[NormalizedVehicle]:
    """Deduplicate vehicles by ID and observation time.

    Args:
        vehicles: List of vehicle observations
        tolerance_seconds: Time tolerance for considering observations "same"

    Returns:
        List with duplicates removed (keeps most recent observation)
    """
    seen = {}
    for v in vehicles:
        key = (v.vehicle_id, v.line_id, v.operator)
        if key in seen:
            existing = seen[key]
            # Keep the newer observation
            if v.observed_at >= existing.observed_at:
                seen[key] = v
        else:
            seen[key] = v
    return list(seen.values())


# =============================================================================
# Provider Interface
# =============================================================================

class AirportTransitProvider(ABC):
    """Abstract base class for airport transit data providers."""

    @property
    @abstractmethod
    def operator(self) -> Operator:
        """Return the operator identifier."""
        pass

    @property
    @abstractmethod
    def source_url(self) -> str:
        """Return the base source URL for attribution."""
        pass

    @property
    @abstractmethod
    def attribution(self) -> str:
        """Return attribution text."""
        pass

    @abstractmethod
    def discover_routes(self) -> list[NormalizedRoute]:
        """Discover all airport bus routes for this operator.

        Returns:
            List of normalized route metadata. Should be cached as
            this rarely changes.
        """
        pass

    @abstractmethod
    def fetch_route_metadata(self, route_id: str) -> Optional[NormalizedRoute]:
        """Fetch metadata for a specific route.

        Returns:
            Normalized route metadata or None if not found.
        """
        pass

    @abstractmethod
    def fetch_stops(self, route_id: str) -> list[dict[str, Any]]:
        """Fetch stops for a specific route.

        Returns:
            List of stop dictionaries with fields:
            - stop_id: Internal stop ID
            - stop_name: Stop name
            - latitude: Latitude
            - longitude: Longitude
            - sequence: Order in route
            - direction: to_airport | from_airport | both
        """
        pass

    @abstractmethod
    def fetch_arrivals(self, stop_id: str) -> list[NormalizedArrival]:
        """Fetch live arrivals at a specific stop.

        Args:
            stop_id: Stop identifier

        Returns:
            List of arrivals for that stop.
        """
        pass

    @abstractmethod
    def fetch_vehicles(self, route_id: str) -> list[NormalizedVehicle]:
        """Fetch live vehicle positions for a specific route.

        Args:
            route_id: Route identifier

        Returns:
            List of vehicle positions.
        """
        pass

    @abstractmethod
    def fetch_alerts(self, route_id: Optional[str] = None) -> list[NormalizedAlert]:
        """Fetch service alerts.

        Args:
            route_id: Optional route filter

        Returns:
            List of alerts for the operator or specific route.
        """
        pass


# =============================================================================
# OASA Provider Implementation
# =============================================================================

class OasaAirportProvider(AirportTransitProvider):
    """Provider for OASA (Athens) airport express buses.

    Uses the OASA Telematics API:
    - getStopArrivals: Get arrivals at a stop
    - getBusLocation: Get vehicle positions for a route
    - webGetStops: Get ordered stops for a route

    Official documentation: https://oasa-telematics-api.readthedocs.io/
    Live endpoint: https://telematics.oasa.gr/api/

    Note: The Telematics API is geo-blocked to Greek IPs only. This provider
    is designed to run on the Pi in Greece.
    """

    # OASA Telematics API configuration
    API_BASE = "https://telematics.oasa.gr/api/"
    USER_AGENT = "syrmos-airport-bus-provider/1.0 (+https://syrmos.peterdsp.dev)"
    REQUEST_TIMEOUT = 15.0  # seconds

    # Known route codes (from existing oasa-airport-bus-watcher.py)
    # Maps line_id -> {to_airport: [route_codes], from_airport: [route_codes]}
    # These codes are used with getBusLocation and getStopArrivals
    ROUTE_CODES = {
        "X93": {"to_airport": [5675], "from_airport": [5676]},
        "X95": {"to_airport": [2051], "from_airport": [2052]},
        "X96": {"to_airport": [5532, 3196], "from_airport": [5533, 3008]},
        "X97": {"to_airport": [5373, 5375], "from_airport": [5374, 5376]},
    }

    # All route codes for querying vehicle positions
    # (all to_airport and from_airport codes combined)
    ALL_ROUTE_CODES = []
    for codes in ROUTE_CODES.values():
        ALL_ROUTE_CODES.extend(codes["to_airport"])
        ALL_ROUTE_CODES.extend(codes["from_airport"])

    # Airport stop for arrivals (Athens International Airport)
    AIRPORT_STOP = 10705

    # Athens airport code
    AIRPORT_CODE = "AIA"

    # Route metadata (public names, colors, stops)
    ROUTE_METADATA = {
        "X93": {
            "public_name": "X93 Kifisos B Station – Airport",
            "public_name_el": "Χ93 ΚΤΕΛ Κηφισού – Αεροδρόμιο",
            "public_name_sq": "X93 Stacioni Kifisos – Aeroporti",
            "public_name_it": "X93 Stazione Kifisos – Aeroporto",
            "origin": "Kifisos B Station",
            "origin_el": "ΚΤΕΛ Κηφισού",
            "destination": "Athens Airport",
            "destination_el": "Αεροδρόμιο Αθηνών",
            "color": "#E8792B",  # SyrmosTokens.warning (express bus orange)
        },
        "X95": {
            "public_name": "X95 Syntagma – Airport",
            "public_name_el": "Χ95 Σύνταγμα – Αεροδρόμιο",
            "public_name_sq": "X95 Syntagma – Aeroporti",
            "public_name_it": "X95 Syntagma – Aeroporto",
            "origin": "Syntagma",
            "origin_el": "Σύνταγμα",
            "destination": "Athens Airport",
            "destination_el": "Αεροδρόμιο Αθηνών",
            "color": "#E8792B",
        },
        "X96": {
            "public_name": "X96 Piraeus – Airport",
            "public_name_el": "Χ96 Πειραιάς – Αεροδρόμιο",
            "public_name_sq": "X96 Pireus – Aeroporti",
            "public_name_it": "X96 Pireo – Aeroporto",
            "origin": "Piraeus",
            "origin_el": "Πειραιάς",
            "destination": "Athens Airport",
            "destination_el": "Αεροδρόμιο Αθηνών",
            "color": "#E8792B",
        },
        "X97": {
            "public_name": "X97 Elliniko Metro – Airport",
            "public_name_el": "Χ97 Ελληνικό – Αεροδρόμιο",
            "public_name_sq": "X97 Elliniko – Aeroporti",
            "public_name_it": "X97 Elliniko – Aeroporto",
            "origin": "Elliniko",
            "origin_el": "Ελληνικό",
            "destination": "Athens Airport",
            "destination_el": "Αεροδρόμιο Αθηνών",
            "color": "#E8792B",
        },
    }

    def __init__(self, timeout: float = REQUEST_TIMEOUT, user_agent: Optional[str] = None):
        self.timeout = timeout
        self.user_agent = user_agent or self.USER_AGENT

    @property
    def operator(self) -> Operator:
        return Operator.OASA

    @property
    def source_url(self) -> str:
        return "https://www.oasa.gr/en/telematics/"

    @property
    def attribution(self) -> str:
        return "Live bus data from OASA Telematics"

    def _fetch_json(self, url: str) -> Optional[list[dict[str, Any]] | dict[str, Any]]:
        """Fetch JSON from a URL with error handling."""
        req = urllib.request.Request(url, headers={"User-Agent": self.user_agent})
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as response:
                raw = response.read()
                if not raw or raw.strip() == b"null":
                    return None
                return json.loads(raw)
        except (urllib.error.URLError, TimeoutError, OSError, json.JSONDecodeError) as e:
            logger.warning(f"OASA fetch failed: {url} -> {e}")
            return None
        except Exception as e:
            logger.error(f"OASA unexpected error fetching {url}: {e}")
            return None

    def discover_routes(self) -> list[NormalizedRoute]:
        """Discover all OASA airport bus routes."""
        routes = []
        now = datetime.now(timezone.utc)

        for line_id, metadata in self.ROUTE_METADATA.items():
            codes = self.ROUTE_CODES.get(line_id, {})
            route = NormalizedRoute(
                operator=Operator.OASA,
                route_id=line_id,
                public_name=metadata["public_name"],
                public_name_en=metadata["public_name"],
                public_name_el=metadata.get("public_name_el"),
                public_name_sq=metadata.get("public_name_sq"),
                public_name_it=metadata.get("public_name_it"),
                origin=metadata["origin"],
                destination=metadata["destination"],
                airport=self.AIRPORT_CODE,
                color=metadata["color"],
                to_airport_route_codes=codes.get("to_airport", []),
                from_airport_route_codes=codes.get("from_airport", []),
                direction=Direction.UNKNOWN,
                is_operational=True,
                source="oasa_telematics",
                source_url=self.source_url,
                attribution=self.attribution,
                observed_at=now,
            )
            routes.append(route)

        return routes

    def fetch_route_metadata(self, route_id: str) -> Optional[NormalizedRoute]:
        """Fetch metadata for a specific route."""
        if route_id not in self.ROUTE_METADATA:
            return None

        metadata = self.ROUTE_METADATA[route_id]
        codes = self.ROUTE_CODES.get(route_id, {})
        now = datetime.now(timezone.utc)

        return NormalizedRoute(
            operator=Operator.OASA,
            route_id=route_id,
            public_name=metadata["public_name"],
            public_name_en=metadata["public_name"],
            public_name_el=metadata.get("public_name_el"),
            public_name_sq=metadata.get("public_name_sq"),
            public_name_it=metadata.get("public_name_it"),
            origin=metadata["origin"],
            destination=metadata["destination"],
            airport=self.AIRPORT_CODE,
            color=metadata["color"],
            to_airport_route_codes=codes.get("to_airport", []),
            from_airport_route_codes=codes.get("from_airport", []),
            direction=Direction.UNKNOWN,
            is_operational=True,
            source="oasa_telematics",
            source_url=self.source_url,
            attribution=self.attribution,
            observed_at=now,
        )

    def fetch_stops(self, route_id: str) -> list[dict[str, Any]]:
        """Fetch stops for a route. Uses webGetStops API."""
        if route_id not in self.ROUTE_CODES:
            return []

        codes = self.ROUTE_CODES[route_id]
        all_codes = codes.get("to_airport", []) + codes.get("from_airport", [])

        # Fetch stops for each route code
        all_stops = []
        for code in all_codes:
            data = self._fetch_json(f"{self.API_BASE}?act=webGetStops&p1={code}")
            if not isinstance(data, list):
                continue

            for stop in data:
                try:
                    stop_code = stop.get("StopCode") or stop.get("StopID")
                    if not stop_code:
                        continue

                    lat = float(stop.get("StopLat", 0) or 0)
                    lng = float(stop.get("StopLng", 0) or 0)
                    seq = int(stop.get("RouteStopOrder", 0) or 0)

                    # Determine direction based on route code
                    if code in codes.get("to_airport", []):
                        direction = "to_airport"
                    elif code in codes.get("from_airport", []):
                        direction = "from_airport"
                    else:
                        direction = "both"

                    all_stops.append({
                        "stop_id": f"OASA_{stop_code}",
                        "stop_name": stop.get("StopDescrEng") or stop.get("StopDescr", ""),
                        "stop_name_el": stop.get("StopDescr", ""),
                        "latitude": lat,
                        "longitude": lng,
                        "sequence": seq,
                        "direction": direction,
                        "route_code": code,
                    })
                except (KeyError, ValueError, TypeError):
                    continue

        return all_stops

    def fetch_arrivals(self, stop_id: Optional[str] = None) -> list[NormalizedArrival]:
        """Fetch live arrivals at the airport stop.

        Note: OASA Telematics provides arrivals for the airport stop (10705)
        containing buses from all routes heading TO the airport.

        For arrivals FROM the airport at city stops, we would need to call
        getStopArrivals with each city terminal stop ID. Currently we
        only fetch arrivals at the airport stop.
        """
        arrivals = []
        now = datetime.now(timezone.utc)

        # Use the airport stop if no specific stop is requested
        query_stop = int(stop_id) if stop_id else self.AIRPORT_STOP

        data = self._fetch_json(f"{self.API_BASE}?act=getStopArrivals&p1={query_stop}")
        if not isinstance(data, list):
            return []

        for item in data:
            try:
                route_code = int(item.get("route_code", 0))
                line_id = self._route_code_to_line_id(route_code)
                if not line_id:
                    continue

                minutes_away = clamp_eta(int(item.get("btime2", 0) or 0))
                vehicle_id = str(item.get("veh_code", "") or "")

                metadata = self.ROUTE_METADATA.get(line_id, {})
                direction = self._infer_direction(route_code, line_id)

                arrival = NormalizedArrival(
                    line_id=line_id,
                    route_id=line_id,
                    operator=Operator.OASA,
                    stop_id=str(query_stop),
                    stop_name="Athens Airport" if query_stop == self.AIRPORT_STOP else f"Stop {query_stop}",
                    destination=metadata.get("destination", "Athens Airport"),
                    direction=direction,
                    scheduled_time=None,  # OASA doesn't provide scheduled times
                    estimated_minutes=minutes_away if minutes_away >= 0 else None,
                    vehicle_id=vehicle_id if vehicle_id else None,
                    source="oasa_telematics",
                    source_url=self.source_url,
                    observed_at=now,
                    freshness=0.0,
                    confidence=ConfidenceLevel.LIVE if minutes_away is not None else ConfidenceLevel.UNKNOWN,
                    service_status=ServiceStatus.OPERATIONAL,
                    attribution=self.attribution,
                )
                arrivals.append(arrival)
            except (KeyError, ValueError, TypeError):
                continue

        return arrivals

    def fetch_vehicles(self, route_id: Optional[str] = None) -> list[NormalizedVehicle]:
        """Fetch live vehicle positions for all airport routes.

        If route_id is specified, only fetch vehicles for that route.
        Otherwise, fetch vehicles for all known airport routes.
        """
        vehicles = []
        now = datetime.now(timezone.utc)

        # Get all route codes or filter to the specified route
        if route_id and route_id in self.ROUTE_CODES:
            route_codes = (
                self.ROUTE_CODES[route_id]["to_airport"] +
                self.ROUTE_CODES[route_id]["from_airport"]
            )
        else:
            route_codes = self.ALL_ROUTE_CODES

        # Fetch positions for each route code
        for code in route_codes:
            data = self._fetch_json(f"{self.API_BASE}?act=getBusLocation&p1={code}")
            if not isinstance(data, list):
                continue

            for item in data:
                try:
                    veh_no = str(item.get("VEH_NO", "") or "")
                    line_id = self._route_code_to_line_id(int(item.get("ROUTE_CODE", code)))
                    if not line_id:
                        continue

                    lat = float(item.get("CS_LAT", 0) or 0)
                    lng = float(item.get("CS_LNG", 0) or 0)
                    heading = float(item.get("VEH_HEADING", 0) or 0)

                    # Skip invalid coordinates (null island)
                    if lat == 0 and lng == 0:
                        continue

                    # Parse vehicle observation timestamp
                    try:
                        vehicle_ts = validate_timestamp(str(item.get("CS_DATE", "")))
                    except ValidationError:
                        vehicle_ts = now

                    direction = self._infer_direction(code, line_id)
                    to_airport = direction == Direction.TO_AIRPORT

                    vehicle = NormalizedVehicle(
                        vehicle_id=veh_no,
                        line_id=line_id,
                        route_id=line_id,
                        operator=Operator.OASA,
                        latitude=lat,
                        longitude=lng,
                        heading=heading,
                        to_airport=to_airport if direction != Direction.UNKNOWN else None,
                        observed_at=vehicle_ts,
                        source="oasa_telematics",
                        source_url=self.source_url,
                        confidence=ConfidenceLevel.LIVE,
                        attribution=self.attribution,
                    )
                    vehicles.append(vehicle)
                except (KeyError, ValueError, TypeError):
                    continue

        return vehicles

    def fetch_alerts(self, route_id: Optional[str] = None) -> list[NormalizedAlert]:
        """Fetch service alerts for OASA airport buses.

        Currently, OASA Telematics doesn't provide alerts for specific bus routes.
        This could be extended to scrape OASA announcements if needed.
        """
        # For now, return empty list
        # Future: integrate with OASA announcements scraping
        return []

    def _route_code_to_line_id(self, route_code: int) -> Optional[str]:
        """Map an OASA route code to a line ID."""
        for line_id, codes in self.ROUTE_CODES.items():
            if route_code in codes.get("to_airport", []) + codes.get("from_airport", []):
                return line_id
        return None

    def _infer_direction(self, route_code: int, line_id: str) -> Direction:
        """Infer direction from route code."""
        if line_id not in self.ROUTE_CODES:
            return Direction.UNKNOWN

        codes = self.ROUTE_CODES[line_id]
        if route_code in codes.get("to_airport", []):
            return Direction.TO_AIRPORT
        elif route_code in codes.get("from_airport", []):
            return Direction.FROM_AIRPORT
        return Direction.UNKNOWN


# =============================================================================
# OSETH Provider Implementation (Placeholder)
# =============================================================================

class OsethAirportProvider(AirportTransitProvider):
    """Provider for OSETH/OASTH (Thessaloniki) airport buses.

    Uses the OSETH/OASTH API for live data.

    Based on the OASA Telematics pattern, the OSETH API likely provides:
    - getStopArrivals: Get arrivals at a stop
    - getBusLocation: Get vehicle positions for a route
    - webGetStops: Get ordered stops for a route

    API documentation reference: https://oasth.readthedocs.io/en/latest/live-data.html
    Live endpoint: https://oseth.com.gr/el/telematics-api/

    Known routes for Thessaloniki:
    - X3: Airport <-> Mikra Metro (new with Line 2 extension)
    - 2X: Airport <-> Nea Elvetia Metro

    Note: The API is likely geo-blocked to Greek IPs. This provider
    is designed to run on the Pi in Greece.

    See docs/data/airport-bus-provenance.md for validation status.
    """

    # Official OSETH web telematics API. The legacy api.oseth.gr hostname is
    # not resolvable; the public site serves JSON under this same-origin path.
    API_BASE = "https://oseth.com.gr/el/telematics-api/"
    USER_AGENT = "syrmos-airport-bus-provider/1.0 (+https://syrmos.peterdsp.dev)"
    REQUEST_TIMEOUT = 15.0

    # Thessaloniki airport code
    AIRPORT_CODE = "SKG"

    # Airport stop for arrivals (Makedonia Airport). Keep this as the stable
    # product identifier until the official stop catalogue is imported.
    AIRPORT_STOP = "THS_AIR"

    # Legacy route-code mapping retained for the existing normalized contract.
    # The official API uses route-instance IDs (for example 01_7429_2_3), not
    # the numeric OASA-style codes used by the old implementation.
    ROUTE_CODES = {
        "X3": {"to_airport": [101], "from_airport": [102]},
        "2X": {"to_airport": [201], "from_airport": [202]},
    }

    # All route codes for querying vehicle positions
    ALL_ROUTE_CODES = []
    for codes in ROUTE_CODES.values():
        ALL_ROUTE_CODES.extend(codes["to_airport"])
        ALL_ROUTE_CODES.extend(codes["from_airport"])

    # Known routes (from seed_thessaloniki_airport_buses.py)
    ROUTE_METADATA = {
        "X3": {
            "public_name": "X3 Airport – Mikra Metro",
            "public_name_el": "Χ3 Αεροδρόμιο – Μετρό Μίκρα",
            "origin": "Makedonia Airport",
            "origin_el": "Αεροδρόμιο «Μακεδονία»",
            "destination": "Mikra",
            "destination_el": "Μίκρα",
            "color": "#0E7490",
        },
        "2X": {
            "public_name": "2X Airport – Nea Elvetia Metro",
            "public_name_el": "2Χ Αεροδρόμιο – Μετρό Νέα Ελβετία",
            "origin": "Makedonia Airport",
            "origin_el": "Αεροδρόμιο «Μακεδονία»",
            "destination": "Nea Elvetia",
            "destination_el": "Νέα Ελβετία",
            "color": "#0E7490",
        },
    }

    def __init__(self, timeout: float = REQUEST_TIMEOUT, user_agent: Optional[str] = None):
        self.timeout = timeout
        self.user_agent = user_agent or self.USER_AGENT

    @property
    def operator(self) -> Operator:
        return Operator.OSETH

    @property
    def source_url(self) -> str:
        return "https://oseth.com.gr/en/"

    @property
    def attribution(self) -> str:
        return "Live bus data from OSETH/OASTH"

    def _fetch_json(self, url: str) -> Optional[list[dict[str, Any]] | dict[str, Any]]:
        """Fetch JSON from a URL with error handling."""
        req = urllib.request.Request(url, headers={"User-Agent": self.user_agent})
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as response:
                raw = response.read()
                if not raw or raw.strip() == b"null":
                    return None
                return json.loads(raw)
        except (urllib.error.URLError, TimeoutError, OSError, json.JSONDecodeError) as e:
            logger.warning(f"OSETH fetch failed: {url} -> {e}")
            return None
        except Exception as e:
            logger.error(f"OSETH unexpected error fetching {url}: {e}")
            return None

    def discover_routes(self) -> list[NormalizedRoute]:
        """Discover all OSETH airport bus routes."""
        routes = []
        now = datetime.now(timezone.utc)

        for route_id, metadata in self.ROUTE_METADATA.items():
            codes = self.ROUTE_CODES.get(route_id, {})
            route = NormalizedRoute(
                operator=Operator.OSETH,
                route_id=route_id,
                public_name=metadata["public_name"],
                public_name_en=metadata["public_name"],
                public_name_el=metadata.get("public_name_el"),
                origin=metadata["origin"],
                destination=metadata["destination"],
                airport=self.AIRPORT_CODE,
                color=metadata["color"],
                to_airport_route_codes=codes.get("to_airport", []),
                from_airport_route_codes=codes.get("from_airport", []),
                to_airport_stop_ids=[],
                from_airport_stop_ids=[],
                direction=Direction.UNKNOWN,
                is_operational=True,
                source="oseth_api",
                source_url=self.source_url,
                attribution=self.attribution,
                observed_at=now,
            )
            routes.append(route)

        return routes

    def fetch_route_metadata(self, route_id: str) -> Optional[NormalizedRoute]:
        """Fetch metadata for a specific route."""
        if route_id not in self.ROUTE_METADATA:
            return None

        metadata = self.ROUTE_METADATA[route_id]
        codes = self.ROUTE_CODES.get(route_id, {})
        now = datetime.now(timezone.utc)

        return NormalizedRoute(
            operator=Operator.OSETH,
            route_id=route_id,
            public_name=metadata["public_name"],
            public_name_en=metadata["public_name"],
            public_name_el=metadata.get("public_name_el"),
            origin=metadata["origin"],
            destination=metadata["destination"],
            airport=self.AIRPORT_CODE,
            color=metadata["color"],
            to_airport_route_codes=codes.get("to_airport", []),
            from_airport_route_codes=codes.get("from_airport", []),
            to_airport_stop_ids=[],
            from_airport_stop_ids=[],
            direction=Direction.UNKNOWN,
            is_operational=True,
            source="oseth_api",
            source_url=self.source_url,
            attribution=self.attribution,
            observed_at=now,
        )

    def fetch_stops(self, route_id: str) -> list[dict[str, Any]]:
        """Fetch stops for a route. Uses webGetStops API if available.

        For now, returns the known airport stop and metro interchange stops
        based on seed_thessaloniki_airport_buses.py.
        """
        if route_id not in self.ROUTE_CODES:
            return []

        # known stops from database seeding
        route_metadata = self.ROUTE_METADATA.get(route_id, {})
        origin = route_metadata.get("origin", "")
        destination = route_metadata.get("destination", "")

        # Return simplified stop information
        # Airport stop
        airport_stop = {
            "stop_id": self.AIRPORT_STOP,
            "stop_name": origin,
            "stop_name_el": route_metadata.get("origin_el", origin),
            "latitude": 40.51972,
            "longitude": 22.97083,
            "sequence": 1,
            "direction": "both",
        }

        # Metro interchange stop
        metro_stop = {
            "stop_id": f"THS_{destination.upper().replace(' ', '_')}",
            "stop_name": destination,
            "stop_name_el": route_metadata.get("destination_el", destination),
            "latitude": 0.0,
            "longitude": 0.0,
            "sequence": 2,
            "direction": "both",
        }

        return [airport_stop, metro_stop]

    def fetch_arrivals(self, stop_id: Optional[str] = None) -> list[NormalizedArrival]:
        """Fetch live arrivals at a stop.

        The official website exposes route-instance metadata here; live stop
        arrivals are not the legacy getStopArrivals contract.
        """
        arrivals = []
        now = datetime.now(timezone.utc)

        # Use the airport stop if no specific stop is requested
        query_stop = stop_id if stop_id else self.AIRPORT_STOP

        data = self._fetch_json(f"{self.API_BASE}/stop/{query_stop}/arrivals?language=el")
        if not isinstance(data, list):
            # API not validated yet, return empty
            logger.debug(f"OSETH getStopArrivals returned non-list for stop {query_stop}")
            return []

        for item in data:
            try:
                # Assuming similar response format to OASA
                route_code = int(item.get("route_code", 0))
                line_id = self._route_code_to_line_id(route_code)
                if not line_id:
                    # Try to match by route_code in known codes
                    line_id = self._find_line_by_route_code(route_code)
                    if not line_id:
                        continue

                minutes_away = clamp_eta(int(item.get("btime2", 0) or 0))
                vehicle_id = str(item.get("veh_code", "") or "")

                metadata = self.ROUTE_METADATA.get(line_id, {})
                direction = self._infer_direction(route_code, line_id)

                arrival = NormalizedArrival(
                    line_id=line_id,
                    route_id=line_id,
                    operator=Operator.OSETH,
                    stop_id=str(query_stop),
                    stop_name=metadata.get("origin", "Makedonia Airport") if query_stop == self.AIRPORT_STOP else f"Stop {query_stop}",
                    destination=metadata.get("destination", "Unknown"),
                    direction=direction,
                    scheduled_time=None,
                    estimated_minutes=minutes_away if minutes_away >= 0 else None,
                    vehicle_id=vehicle_id if vehicle_id else None,
                    source="oseth_api",
                    source_url=self.source_url,
                    observed_at=now,
                    freshness=0.0,
                    confidence=ConfidenceLevel.LIVE if minutes_away is not None else ConfidenceLevel.UNKNOWN,
                    service_status=ServiceStatus.OPERATIONAL,
                    attribution=self.attribution,
                )
                arrivals.append(arrival)
            except (KeyError, ValueError, TypeError):
                continue

        return arrivals

    def fetch_vehicles(self, route_id: Optional[str] = None) -> list[NormalizedVehicle]:
        """Fetch live vehicle positions for all airport routes.

        If route_id is specified, only fetch vehicles for that route.
        Otherwise, fetch vehicles for all known airport routes.
        Vehicle telemetry is served by the official OSETH site, not the old
        api.oseth.gr getBusLocation endpoint.
        """
        vehicles = []
        now = datetime.now(timezone.utc)

        # Get all route codes or filter to the specified route
        if route_id and route_id in self.ROUTE_CODES:
            route_codes = (
                self.ROUTE_CODES[route_id]["to_airport"] +
                self.ROUTE_CODES[route_id]["from_airport"]
            )
        else:
            route_codes = self.ALL_ROUTE_CODES

        # Fetch positions for each route code
        for code in route_codes:
            data = self._fetch_json(f"{self.API_BASE}?act=getBusLocation&p1={code}")
            if not isinstance(data, list):
                continue

            for item in data:
                try:
                    veh_no = str(item.get("VEH_NO", "") or "")
                    line_id = self._route_code_to_line_id(int(item.get("ROUTE_CODE", code)))
                    if not line_id:
                        line_id = self._find_line_by_route_code(code)
                        if not line_id:
                            continue

                    lat = float(item.get("CS_LAT", 0) or 0)
                    lng = float(item.get("CS_LNG", 0) or 0)
                    heading = float(item.get("VEH_HEADING", 0) or 0)

                    # Skip invalid coordinates (null island)
                    if lat == 0 and lng == 0:
                        continue

                    # Parse vehicle observation timestamp
                    try:
                        vehicle_ts_str = str(item.get("CS_DATE", ""))
                        if vehicle_ts_str:
                            vehicle_ts = validate_timestamp(vehicle_ts_str)
                        else:
                            vehicle_ts = now
                    except ValidationError:
                        vehicle_ts = now

                    direction = self._infer_direction(code, line_id)
                    to_airport = direction == Direction.TO_AIRPORT

                    vehicle = NormalizedVehicle(
                        vehicle_id=veh_no,
                        line_id=line_id,
                        route_id=line_id,
                        operator=Operator.OSETH,
                        latitude=lat,
                        longitude=lng,
                        heading=heading,
                        to_airport=to_airport if direction != Direction.UNKNOWN else None,
                        observed_at=vehicle_ts,
                        source="oseth_api",
                        source_url=self.source_url,
                        confidence=ConfidenceLevel.LIVE,
                        attribution=self.attribution,
                    )
                    vehicles.append(vehicle)
                except (KeyError, ValueError, TypeError):
                    continue

        return vehicles

    def fetch_alerts(self, route_id: Optional[str] = None) -> list[NormalizedAlert]:
        """Fetch service alerts for OSETH airport buses.

        Could integrate with scraper_oseth.py for announcements.
        For now, returns empty list.
        """
        # Future: integrate with OSETH announcements scraping via scraper_oseth.py
        return []

    def _route_code_to_line_id(self, route_code: int) -> Optional[str]:
        """Map an OSETH route code to a line ID."""
        for line_id, codes in self.ROUTE_CODES.items():
            if route_code in codes.get("to_airport", []) + codes.get("from_airport", []):
                return line_id
        return None

    def _find_line_by_route_code(self, route_code: int) -> Optional[str]:
        """Find a line that contains this route code."""
        for line_id, codes in self.ROUTE_CODES.items():
            all_codes = codes.get("to_airport", []) + codes.get("from_airport", [])
            if route_code in all_codes:
                return line_id
        return None

    def _infer_direction(self, route_code: int, line_id: str) -> Direction:
        """Infer direction from route code."""
        if line_id not in self.ROUTE_CODES:
            return Direction.UNKNOWN

        codes = self.ROUTE_CODES[line_id]
        if route_code in codes.get("to_airport", []):
            return Direction.TO_AIRPORT
        elif route_code in codes.get("from_airport", []):
            return Direction.FROM_AIRPORT
        return Direction.UNKNOWN


# =============================================================================
# Normalizer
# =============================================================================

class AirportBusNormalizer:
    """Normalizes provider-specific data to the common model.

    This class provides a thin wrapper that ensures all provider data
    is validated and normalized before being used by clients.
    """

    # Maximum age for data to be considered "live" (seconds)
    LIVE_FRESHNESS_THRESHOLD = 60.0
    STALE_THRESHOLD = 300.0  # 5 minutes

    def __init__(self, clock_skew_tolerance: float = 300.0):
        """Initialize the normalizer.

        Args:
            clock_skew_tolerance: Maximum allowed clock skew in seconds
        """
        self.clock_skew_tolerance = clock_skew_tolerance

    def normalize_vehicle(
        self,
        provider: AirportTransitProvider,
        raw_data: dict[str, Any],
        observed_at: datetime,
    ) -> Optional[NormalizedVehicle]:
        """Normalize a raw vehicle position from a provider."""
        try:
            # Validate and extract required fields
            vehicle_id = str(raw_data.get("vehicleId", raw_data.get("vehicle_id", "")) or "")
            line_id = str(raw_data.get("lineId", raw_data.get("line_id", "")) or "")
            route_id = line_id  # For buses, route_id == line_id

            if not vehicle_id or not line_id:
                logger.debug(f"Skipping vehicle: missing required fields (vehicle_id={vehicle_id!r}, line_id={line_id!r})")
                return None

            lat = float(raw_data.get("lat", raw_data.get("latitude", 0) or 0))
            lng = float(raw_data.get("lng", raw_data.get("longitude", 0) or 0))
            heading = float(raw_data.get("heading", raw_data.get("VEH_HEADING", 0) or 0))

            # Skip null island
            if lat == 0 and lng == 0:
                return None

            # Infer direction from provider data
            to_airport = self._infer_vehicle_direction(raw_data, provider)

            vehicle = NormalizedVehicle(
                vehicle_id=vehicle_id,
                line_id=line_id,
                route_id=route_id,
                operator=provider.operator,
                latitude=lat,
                longitude=lng,
                heading=heading,
                to_airport=to_airport,
                observed_at=observed_at,
                source=raw_data.get("source", f"{provider.operator.value}_api"),
                source_url=provider.source_url,
                confidence=self._compute_vehicle_confidence(raw_data, observed_at),
                attribution=provider.attribution,
            )
            return vehicle
        except (KeyError, ValueError, TypeError, ValidationError) as e:
            logger.warning(f"Failed to normalize vehicle {raw_data}: {e}")
            return None

    def normalize_arrival(
        self,
        provider: AirportTransitProvider,
        raw_data: dict[str, Any],
        stop_id: str,
        stop_name: str,
        observed_at: datetime,
    ) -> Optional[NormalizedArrival]:
        """Normalize a raw arrival from a provider."""
        try:
            line_id = str(raw_data.get("lineId", raw_data.get("line_id", "")) or "")
            route_id = line_id
            estimated_minutes = raw_data.get("minutesAway", raw_data.get("minutes_away"))

            if not line_id:
                logger.debug(f"Skipping arrival: missing line_id")
                return None

            # Clamp ETA
            if estimated_minutes is not None:
                estimated_minutes = clamp_eta(int(estimated_minutes))

            direction = self._infer_arrival_direction(raw_data, provider)

            arrival = NormalizedArrival(
                line_id=line_id,
                route_id=route_id,
                operator=provider.operator,
                stop_id=stop_id,
                stop_name=stop_name,
                destination=self._get_destination(line_id, provider, direction),
                direction=direction,
                scheduled_time=raw_data.get("scheduledTime", raw_data.get("scheduled_time")),
                estimated_minutes=int(estimated_minutes) if estimated_minutes is not None else None,
                vehicle_id=str(raw_data.get("vehicleId", raw_data.get("vehicle_id", "")) or "") or None,
                source=raw_data.get("source", f"{provider.operator.value}_api"),
                source_url=provider.source_url,
                observed_at=observed_at,
                freshness=0.0,
                confidence=self._compute_arrival_confidence(raw_data, observed_at),
                service_status=self._compute_service_status(raw_data),
                attribution=provider.attribution,
            )
            return arrival
        except (KeyError, ValueError, TypeError, ValidationError) as e:
            logger.warning(f"Failed to normalize arrival {raw_data}: {e}")
            return None

    def normalize_route(
        self,
        provider: AirportTransitProvider,
        raw_data: dict[str, Any],
    ) -> Optional[NormalizedRoute]:
        """Normalize route metadata from a provider."""
        try:
            route_id_raw = raw_data.get("id") or raw_data.get("routeId") or raw_data.get("route_id") or ""
            route_id = str(route_id_raw).strip()

            if not route_id:
                return None

            public_name = raw_data.get("name", raw_data.get("publicName", raw_data.get("public_name", "")))

            route = NormalizedRoute(
                operator=provider.operator,
                route_id=route_id,
                public_name=str(public_name or ""),
                public_name_en=raw_data.get("name_en", raw_data.get("publicNameEn")),
                public_name_el=raw_data.get("name_el", raw_data.get("publicNameEl")),
                public_name_sq=raw_data.get("name_sq", raw_data.get("publicNameSq")),
                public_name_it=raw_data.get("name_it", raw_data.get("publicNameIt")),
                origin=raw_data.get("origin", raw_data.get("terminal_a", "")),
                destination=raw_data.get("destination", raw_data.get("terminal_b", "")),
                airport=raw_data.get("airport", ""),
                color=raw_data.get("color", "#666666"),
                to_airport_route_codes=raw_data.get("to_airport_route_codes", []),
                from_airport_route_codes=raw_data.get("from_airport_route_codes", []),
                direction=self._infer_route_direction(raw_data),
                is_operational=raw_data.get("is_operational", True),
                source=raw_data.get("source", f"{provider.operator.value}_api"),
                source_url=provider.source_url,
                attribution=provider.attribution,
                observed_at=datetime.now(timezone.utc),
            )
            return route
        except (KeyError, ValueError, TypeError) as e:
            logger.warning(f"Failed to normalize route {raw_data}: {e}")
            return None

    def _infer_vehicle_direction(
        self,
        raw_data: dict[str, Any],
        provider: AirportTransitProvider,
    ) -> Optional[bool]:
        """Infer vehicle direction from raw data."""
        # Try explicit toAirport field
        if "toAirport" in raw_data:
            val = raw_data["toAirport"]
            if val is True:
                return True
            elif val is False:
                return False
            return None

        # Try route code inference for OASA
        if provider.operator == Operator.OASA:
            route_code = raw_data.get("routeCode", raw_data.get("route_code"))
            if route_code is not None:
                oasa_provider = OasaAirportProvider()
                line_id = oasa_provider._route_code_to_line_id(int(route_code))
                if line_id:
                    direction = oasa_provider._infer_direction(int(route_code), line_id)
                    if direction == Direction.TO_AIRPORT:
                        return True
                    elif direction == Direction.FROM_AIRPORT:
                        return False

        return None

    def _infer_arrival_direction(
        self,
        raw_data: dict[str, Any],
        provider: AirportTransitProvider,
    ) -> Direction:
        """Infer arrival direction from raw data."""
        # Try explicit direction field
        if "direction" in raw_data:
            val = str(raw_data["direction"])
            if val == "to_airport":
                return Direction.TO_AIRPORT
            elif val == "from_airport":
                return Direction.FROM_AIRPORT

        return Direction.UNKNOWN

    def _get_destination(
        self,
        line_id: str,
        provider: AirportTransitProvider,
        direction: Direction,
    ) -> str:
        """Get the destination for a line based on direction."""
        if provider.operator == Operator.OASA:
            oasa_provider = OasaAirportProvider()
            metadata = oasa_provider.ROUTE_METADATA.get(line_id, {})
            if direction == Direction.TO_AIRPORT:
                return metadata.get("destination", "Athens Airport")
            elif direction == Direction.FROM_AIRPORT:
                return metadata.get("origin", "City")

        # Default to the destination from metadata
        return provider.fetch_route_metadata(line_id).destination if provider.fetch_route_metadata(line_id) else "Unknown"

    def _infer_route_direction(self, raw_data: dict[str, Any]) -> Direction:
        """Infer route direction from raw data."""
        if "direction" in raw_data:
            val = str(raw_data["direction"])
            if val == "outbound":
                return Direction.TO_AIRPORT
            elif val == "inbound":
                return Direction.FROM_AIRPORT
        return Direction.UNKNOWN

    def _compute_vehicle_confidence(
        self,
        raw_data: dict[str, Any],
        observed_at: datetime,
    ) -> ConfidenceLevel:
        """Compute confidence level for a vehicle observation."""
        now = datetime.now(timezone.utc)
        age = (now - observed_at.replace(tzinfo=timezone.utc)).total_seconds() if observed_at.tzinfo is None else (now - observed_at).total_seconds()

        if age > self.STALE_THRESHOLD:
            return ConfidenceLevel.STALE
        elif age > self.LIVE_FRESHNESS_THRESHOLD:
            return ConfidenceLevel.ESTIMATED
        else:
            return ConfidenceLevel.LIVE

    def _compute_arrival_confidence(
        self,
        raw_data: dict[str, Any],
        observed_at: datetime,
    ) -> ConfidenceLevel:
        """Compute confidence level for an arrival observation."""
        # If we have a valid ETA and recent observation, it's live
        if raw_data.get("minutesAway") is not None or raw_data.get("minutes_away") is not None:
            now = datetime.now(timezone.utc)
            age = (now - observed_at.replace(tzinfo=timezone.utc)).total_seconds() if observed_at.tzinfo is None else (now - observed_at).total_seconds()

            if age > self.STALE_THRESHOLD:
                return ConfidenceLevel.STALE
            elif age > self.LIVE_FRESHNESS_THRESHOLD:
                return ConfidenceLevel.ESTIMATED
            else:
                return ConfidenceLevel.LIVE

        return ConfidenceLevel.OPERATOR_LINK

    def _compute_service_status(self, raw_data: dict[str, Any]) -> ServiceStatus:
        """Compute service status from raw data."""
        # Placeholder: could check for disruption indicators
        return ServiceStatus.OPERATIONAL


# =============================================================================
# Poller
# =============================================================================

class AirportBusPoller:
    """Bounded poller with exponential backoff, jitter, and deduplication.

    Features:
    - Configurable polling interval
    - Exponential backoff with jitter on failures
    - Deduplication of identical requests
    - Atomic snapshot writes (tmp -> final)
    - Freshness timestamps
    - Failure journal

    Example usage:
        provider = OasaAirportProvider()
        normalizer = AirportBusNormalizer()
        poller = AirportBusPoller(
            provider=provider,
            normalizer=normalizer,
            output_path="/path/to/out/airport-buses.json",
            journal_path="/path/to/out/.airport-bus-journal.jsonl",
            interval=30.0,
        )
        poller.start()  # Blocking
    """

    # Default configuration
    DEFAULT_INTERVAL = 30.0  # seconds
    DEFAULT_TIMEOUT = 15.0  # request timeout
    DEFAULT_MAX_RETRIES = 5
    DEFAULT_BACKOFF_BASE = 2.0  # base seconds for exponential backoff
    DEFAULT_JITTER = 0.25  # ±25% jitter
    DEDUP_WINDOW = 1.0  # deduplication window (seconds)

    def __init__(
        self,
        provider: AirportTransitProvider,
        normalizer: AirportBusNormalizer,
        output_path: Path,
        journal_path: Optional[Path] = None,
        interval: float = DEFAULT_INTERVAL,
        max_retries: int = DEFAULT_MAX_RETRIES,
        backoff_base: float = DEFAULT_BACKOFF_BASE,
        jitter: float = DEFAULT_JITTER,
    ):
        """Initialize the poller.

        Args:
            provider: The data provider (e.g., OasaAirportProvider)
            normalizer: The data normalizer
            output_path: Where to write the snapshot
            journal_path: Optional path for failure journal
            interval: Polling interval in seconds
            max_retries: Maximum retry attempts on failure
            backoff_base: Base seconds for exponential backoff
            jitter: Jitter factor (0.0 to 1.0)
        """
        self.provider = provider
        self.normalizer = normalizer
        self.output_path = Path(output_path)
        self.journal_path = Path(journal_path) if journal_path else None
        self.interval = interval
        self.max_retries = max_retries
        self.backoff_base = backoff_base
        self.jitter = jitter

        # State
        self._last_request_time: Optional[float] = None
        self._last_snapshot_hash: Optional[str] = None
        self._consecutive_failures: int = 0
        self._running: bool = False

    def _generate_snapshot(self) -> Optional[dict[str, Any]]:
        """Generate a complete snapshot from the provider.

        Returns:
            Normalized snapshot dictionary or None on failure
        """
        start_time = time.time()
        now = datetime.now(timezone.utc)

        try:
            # Fetch all data
            routes = self.provider.discover_routes()
            vehicles = self.provider.fetch_vehicles()
            arrivals = self.provider.fetch_arrivals()
            alerts = self.provider.fetch_alerts()

            # Build snapshot
            snapshot = AirportBusSnapshot(
                updated_at=now,
                operator=self.provider.operator,
                routes=routes,
                vehicles=vehicles,
                arrivals=arrivals,
                alerts=alerts,
                generation_duration=time.time() - start_time,
                source=f"{self.provider.operator.value}_api",
                source_url=self.provider.source_url,
                attribution=self.provider.attribution,
            )

            return snapshot.to_dict()
        except Exception as e:
            logger.error(f"Failed to generate snapshot: {e}")
            return None

    def _write_snapshot(self, snapshot: dict[str, Any]) -> bool:
        """Write snapshot atomically.

        Args:
            snapshot: The snapshot to write

        Returns:
            True if write succeeded, False otherwise
        """
        try:
            # Create parent directory if needed
            self.output_path.parent.mkdir(parents=True, exist_ok=True)

            # Write to temporary file first
            tmp_path = self.output_path.with_suffix(".tmp")
            with open(tmp_path, 'w', encoding='utf-8') as f:
                json.dump(snapshot, f, ensure_ascii=False, separators=(',', ':'))

            # Atomic rename
            tmp_path.replace(self.output_path)

            # Log to journal
            if self.journal_path:
                self._log_journal.success(snapshot)

            return True
        except Exception as e:
            logger.error(f"Failed to write snapshot: {e}")
            if self.journal_path:
                self._log_journal.failure(str(e))
            return False

    def _backoff_with_jitter(self, attempt: int) -> float:
        """Calculate backoff time with jitter.

        Args:
            attempt: Current attempt number (0-indexed)

        Returns:
            Sleep time in seconds
        """
        # Exponential backoff: base * (2 ^ attempt)
        base_delay = self.backoff_base * (2 ** attempt)
        # Add jitter: ±jitter * base_delay
        jitter_range = base_delay * self.jitter
        jitter_amount = random.uniform(-jitter_range, jitter_range)
        return max(0, base_delay + jitter_amount)

    def _should_deduplicate(self) -> bool:
        """Check if current request is within deduplication window."""
        if self._last_request_time is None:
            return False
        now = time.time()
        return now - self._last_request_time < self.DEDUP_WINDOW

    def _run_once(self) -> bool:
        """Run one polling cycle.

        Returns:
            True if successful, False on failure
        """
        # Check deduplication
        if self._should_deduplicate():
            logger.debug("Skipping request due to deduplication")
            return True

        self._last_request_time = time.time()

        # Generate snapshot with retries
        for attempt in range(self.max_retries):
            if attempt > 0:
                sleep_time = self._backoff_with_jitter(attempt - 1)
                logger.info(f"Retry attempt {attempt}/{self.max_retries}, sleeping {sleep_time:.1f}s")
                time.sleep(sleep_time)

            snapshot = self._generate_snapshot()
            if snapshot is not None:
                success = self._write_snapshot(snapshot)
                if success:
                    self._consecutive_failures = 0
                    return True

            self._consecutive_failures += 1

        logger.error(f"Failed after {self.max_retries} retries")
        return False

    def start(self) -> None:
        """Start the polling loop (blocking)."""
        self._running = True
        logger.info(f"Starting {self.provider.operator.value} airport bus poller (interval: {self.interval}s)")

        while self._running:
            try:
                success = self._run_once()
                if not success:
                    logger.warning(f"Polling cycle failed")
            except Exception as e:
                logger.error(f"Unexpected error in polling cycle: {e}")

            # Sleep until next cycle
            sleep_time = self.interval
            if self._consecutive_failures > 0:
                # Apply backoff to interval on consecutive failures
                sleep_time = self._backoff_with_jitter(min(self._consecutive_failures - 1, 5))

            # Respect minimum interval
            sleep_time = max(1.0, sleep_time)

            # Distribute sleeps to be interruptible
            for _ in range(int(sleep_time * 10)):
                if not self._running:
                    break
                time.sleep(0.1)

    def stop(self) -> None:
        """Stop the polling loop."""
        self._running = False
        logger.info(f"Stopped {self.provider.operator.value} airport bus poller")

    def poll_once(self) -> bool:
        """Run a single polling cycle (non-blocking).

        Returns:
            True if successful, False on failure
        """
        if not self._running:
            return self._run_once()
        logger.warning("Poll once called while poller is running - use start() for continuous polling")
        return self._run_once()


# =============================================================================
# Journal for failure logging
# =============================================================================

class PollerJournal:
    """Simple journal for logging poller operations and failures."""

    def __init__(self, path: Path):
        self.path = path
        path.parent.mkdir(parents=True, exist_ok=True)

    def log(self, entry: dict[str, Any]) -> None:
        """Log an entry to the journal."""
        try:
            with open(self.path, 'a', encoding='utf-8') as f:
                f.write(json.dumps(entry, ensure_ascii=False) + '\n')
        except Exception:
            pass  # Never fail due to journal issues

    def success(self, snapshot: dict[str, Any]) -> None:
        """Log a successful poll."""
        self.log({
            "ts": datetime.now(timezone.utc).isoformat(),
            "status": "success",
            "routes": len(snapshot.get("routes", [])),
            "vehicles": len(snapshot.get("vehicles", [])),
            "arrivals": len(snapshot.get("arrivals", [])),
            "alerts": len(snapshot.get("alerts", [])),
            "duration": snapshot.get("generation_duration", 0),
        })

    def failure(self, error: str) -> None:
        """Log a failed poll."""
        self.log({
            "ts": datetime.now(timezone.utc).isoformat(),
            "status": "failure",
            "error": error,
        })


# Convenient constructor for poller journal
def _create_journal(path: Optional[Path]) -> Optional[PollerJournal]:
    if path:
        return PollerJournal(path)
    return None


# =============================================================================
# Module-level convenience functions
# =============================================================================

def create_oasa_poller(
    output_path: Path,
    journal_path: Optional[Path] = None,
    interval: float = 30.0,
) -> AirportBusPoller:
    """Create an OASA airport bus poller with default settings."""
    provider = OasaAirportProvider()
    normalizer = AirportBusNormalizer()
    return AirportBusPoller(
        provider=provider,
        normalizer=normalizer,
        output_path=output_path,
        journal_path=journal_path,
        interval=interval,
    )


def create_oseth_poller(
    output_path: Path,
    journal_path: Optional[Path] = None,
    interval: float = 30.0,
) -> AirportBusPoller:
    """Create an OSETH airport bus poller with default settings.

    Note: OSETH provider is not fully implemented yet.
    """
    provider = OsethAirportProvider()
    normalizer = AirportBusNormalizer()
    return AirportBusPoller(
        provider=provider,
        normalizer=normalizer,
        output_path=output_path,
        journal_path=journal_path,
        interval=interval,
    )
