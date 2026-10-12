"""Unified airport bus watcher for Athens (OASA) and Thessaloniki (OSETH).

This script uses the new provider abstraction to fetch live airport bus data
from multiple operators and writes it in both:
1. The new normalized format at /api/airport-buses
2. The legacy format at /api/oasa-airport-buses (for backward compatibility)

Run on the Pi:
    cd ~/syrmos-api && .venv/bin/python -m scripts.airport_bus_watcher

Systemd unit: TODO (create airport-bus-watcher.service)

Architecture:
    - Uses OasaAirportProvider for Athens OASA express buses (X93/X95/X96/X97)
    - Uses OsethAirportProvider for Thessaloniki OSETH web telematics
    - Writes normalized snapshot that the new API endpoints serve
    - Also writes legacy OASA format for backward compatibility
    - Bounded poller with exponential backoff and jitter
    - Atomic writes (tmp -> final)
    - Failure journal

Configuration:
    - Poll interval: 30 seconds (configurable)
    - Request timeout: 15 seconds
    - Max retries: 5 with exponential backoff
    - Jitter: ±25%
"""

from __future__ import annotations

import json
import logging
import os
import random
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(name)s: %(message)s',
)
logger = logging.getLogger(__name__)

# Add syrmos_admin to path
_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(_ROOT))

from syrmos_admin import airport_bus_providers as abp_mod

# Default configuration
DEFAULT_POLL_INTERVAL = 30.0  # seconds
DEFAULT_TIMEOUT = 15.0  # seconds
DEFAULT_MAX_RETRIES = 5
DEFAULT_BACKOFF_BASE = 2.0
DEFAULT_JITTER = 0.25

# Output paths (relative to PIPELINE_ROOT or script directory)
DEFAULT_OUTPUT_DIR = Path(__file__).resolve().parent.parent / "out"
LEGACY_OUTPUT_FILE = DEFAULT_OUTPUT_DIR / "oasa-airport-buses.json"
NORMALIZED_OUTPUT_FILE = DEFAULT_OUTPUT_DIR / "airport-buses.json"
JOURNAL_FILE = DEFAULT_OUTPUT_DIR / ".airport-bus-journal.jsonl"

# Environment overrides
PIPELINE_ROOT = Path(os.environ.get("PIPELINE_ROOT", str(_ROOT)))
OUTPUT_DIR = Path(os.environ.get("AIRPORT_BUS_OUTPUT_DIR", DEFAULT_OUTPUT_DIR))

# OASA Telematics user agent
USER_AGENT = "syrmos-airport-bus-watcher/2.0 (+https://syrmos.peterdsp.dev)"


class AirportBusWatcher:
    """Unified airport bus watcher with both new and legacy output."""

    def __init__(
        self,
        oasa_provider: Optional[abp_mod.OasaAirportProvider] = None,
        oseth_provider: Optional[abp_mod.OsethAirportProvider] = None,
        output_dir: Path = OUTPUT_DIR,
        poll_interval: float = DEFAULT_POLL_INTERVAL,
        max_retries: int = DEFAULT_MAX_RETRIES,
        backoff_base: float = DEFAULT_BACKOFF_BASE,
        jitter: float = DEFAULT_JITTER,
    ):
        self.oasa_provider = oasa_provider or abp_mod.OasaAirportProvider()
        self.oseth_provider = oseth_provider or abp_mod.OsethAirportProvider()
        self.output_dir = Path(output_dir)
        self.legacy_file = self.output_dir / "oasa-airport-buses.json"
        self.normalized_file = self.output_dir / "airport-buses.json"
        self.journal_file = self.output_dir / ".airport-bus-journal.jsonl"
        self.poll_interval = poll_interval
        self.max_retries = max_retries
        self.backoff_base = backoff_base
        self.jitter = jitter
        self._running = False
        self._consecutive_failures = 0

        # Ensure output directory exists
        self.output_dir.mkdir(parents=True, exist_ok=True)

    def _backoff_with_jitter(self, attempt: int) -> float:
        """Calculate backoff time with jitter."""
        base_delay = self.backoff_base * (2 ** attempt)
        jitter_range = base_delay * self.jitter
        jitter_amount = random.uniform(-jitter_range, jitter_range)
        return max(0, base_delay + jitter_amount)

    def _fetch_oasa_legacy(self) -> Optional[dict]:
        """Fetch OASA data in the legacy format directly from telematics API.

        This preserves the exact format that the old oasa-airport-bus-watcher
        generates, ensuring backward compatibility.
        """
        # This is the existing watcher logic, kept for backward compatibility
        AIRPORT_ROUTES = {
            "X93": {"to_airport": [5675], "from_airport": [5676]},
            "X95": {"to_airport": [2051], "from_airport": [2052]},
            "X96": {"to_airport": [5532, 3196], "from_airport": [5533, 3008]},
            "X97": {"to_airport": [5373, 5375], "from_airport": [5374, 5376]},
        }
        AIRPORT_STOP = 10705
        API = "https://telematics.oasa.gr/api/"

        def _fetch_json(url: str) -> Optional[list]:
            req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            try:
                with urllib.request.urlopen(req, timeout=DEFAULT_TIMEOUT) as r:
                    raw = r.read()
                    if not raw or raw.strip() == b"null":
                        return None
                    return json.loads(raw)
            except Exception as e:
                logger.warning(f"Legacy OASA fetch failed: {url} -> {e}")
                return None

        def _route_to_line(rc: int) -> Optional[str]:
            for line_id, dirs in AIRPORT_ROUTES.items():
                for codes in dirs.values():
                    if rc in codes:
                        return line_id
            return None

        # Fetch vehicles
        all_codes = []
        for line_id, dirs in AIRPORT_ROUTES.items():
            for codes in dirs.values():
                all_codes.extend(codes)

        vehicles = []
        for rc in all_codes:
            data = _fetch_json(f"{API}?act=getBusLocation&p1={rc}")
            if not data or not isinstance(data, list):
                continue
            for v in data:
                try:
                    vehicles.append({
                        "vehicleId": str(v.get("VEH_NO", "")),
                        "lat": float(v["CS_LAT"]),
                        "lng": float(v["CS_LNG"]),
                        "heading": float(v.get("VEH_HEADING", 0)),
                        "routeCode": int(v.get("ROUTE_CODE", rc)),
                        "lineId": _route_to_line(int(v.get("ROUTE_CODE", rc))),
                        "timestamp": v.get("CS_DATE", ""),
                    })
                except (KeyError, ValueError, TypeError):
                    continue

        # Fetch arrivals
        data = _fetch_json(f"{API}?act=getStopArrivals&p1={AIRPORT_STOP}")
        arrivals = []
        if data and isinstance(data, list):
            for a in data:
                try:
                    rc = int(a.get("route_code", 0))
                    line_id = _route_to_line(rc)
                    if not line_id:
                        continue
                    arrivals.append({
                        "lineId": line_id,
                        "routeCode": rc,
                        "vehicleId": str(a.get("veh_code", "")),
                        "minutesAway": int(a.get("btime2", 0)),
                    })
                except (ValueError, TypeError):
                    continue
            arrivals.sort(key=lambda x: x["minutesAway"])

        now = datetime.now(timezone.utc).isoformat()
        return {
            "updatedAt": now,
            "vehicles": vehicles,
            "airportArrivals": arrivals,
            "routes": {
                line_id: {
                    "toAirport": dirs["to_airport"],
                    "fromAirport": dirs["from_airport"],
                }
                for line_id, dirs in AIRPORT_ROUTES.items()
            },
        }

    def _fetch_all_data(self) -> Optional[dict]:
        """Fetch all airport bus data using the new provider abstraction."""
        start_time = time.time()
        now = datetime.now(timezone.utc)

        try:
            # Fetch OASA data
            oasa_routes = self.oasa_provider.discover_routes()
            oasa_vehicles = self.oasa_provider.fetch_vehicles()
            oasa_arrivals = self.oasa_provider.fetch_arrivals()
            oasa_alerts = self.oasa_provider.fetch_alerts()

            # Fetch OSETH data (may be empty if not implemented)
            oseth_routes = self.oseth_provider.discover_routes()
            oseth_vehicles = self.oseth_provider.fetch_vehicles()
            oseth_arrivals = self.oseth_provider.fetch_arrivals()
            oseth_alerts = self.oseth_provider.fetch_alerts()

            def route_payload(provider, route):
                """Publish route geometry with the atomic live snapshot.

                OASA exposes ordered stop coordinates rather than a shape
                endpoint.  The stop-derived polyline is therefore the
                authoritative fallback and remains usable offline. OSETH
                currently exposes the same verified airport/interchange
                catalogue through the provider.
                """
                payload = route.to_dict()
                stops = provider.fetch_stops(route.route_id)
                valid_stops = [
                    s for s in stops
                    if isinstance(s.get("latitude"), (int, float))
                    and isinstance(s.get("longitude"), (int, float))
                    and -90 <= s["latitude"] <= 90
                    and -180 <= s["longitude"] <= 180
                    and not (s["latitude"] == 0 and s["longitude"] == 0)
                ]
                valid_stops.sort(key=lambda s: (s.get("direction", ""), s.get("sequence", 0)))
                payload["stops"] = valid_stops
                official_shape = getattr(provider, "fetch_shape", lambda _route_id: [])(route.route_id)
                payload["shape"] = official_shape or [
                    {"latitude": s["latitude"], "longitude": s["longitude"], "sequence": s.get("sequence", 0)}
                    for s in valid_stops
                ]
                payload["geometry_source"] = "oseth_gtfs_shapes" if official_shape else "ordered_operator_stops"
                return payload

            return {
                "updated_at": now.isoformat(),
                "generated_at": now.isoformat(),
                "generation_duration": time.time() - start_time,
                "operators": {
                    "oasa": {
                        "operator": "oasa",
                        "source": self.oasa_provider.source_url,
                        "attribution": self.oasa_provider.attribution,
                        "routes": [route_payload(self.oasa_provider, r) for r in oasa_routes],
                        "vehicles": [v.to_dict() for v in oasa_vehicles],
                        "arrivals": [a.to_dict() for a in oasa_arrivals],
                        "alerts": [a.to_dict() for a in oasa_alerts],
                    },
                    "oseth": {
                        "operator": "oseth",
                        "source": self.oseth_provider.source_url,
                        "attribution": self.oseth_provider.attribution,
                        "routes": [route_payload(self.oseth_provider, r) for r in oseth_routes],
                        "vehicles": [v.to_dict() for v in oseth_vehicles],
                        "arrivals": [a.to_dict() for a in oseth_arrivals],
                        "alerts": [a.to_dict() for a in oseth_alerts],
                    },
                },
                "sources": [
                    {"operator": "oasa", "url": self.oasa_provider.source_url},
                    {"operator": "oseth", "url": self.oseth_provider.source_url},
                ],
            }
        except Exception as e:
            logger.error(f"Failed to fetch all data: {e}")
            return None

    def _write_legacy(self, data: dict) -> bool:
        """Write the legacy OASA format file."""
        try:
            tmp = self.legacy_file.with_suffix(".tmp")
            with open(tmp, 'w', encoding='utf-8') as f:
                json.dump(data, f, separators=(',', ':'))
            tmp.replace(self.legacy_file)
            return True
        except Exception as e:
            logger.error(f"Failed to write legacy file: {e}")
            return False

    def _write_normalized(self, data: dict) -> bool:
        """Write the normalized format file."""
        try:
            tmp = self.normalized_file.with_suffix(".tmp")
            with open(tmp, 'w', encoding='utf-8') as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            tmp.replace(self.normalized_file)
            return True
        except Exception as e:
            logger.error(f"Failed to write normalized file: {e}")
            return False

    def _log_journal(self, status: str, error: Optional[str] = None,
                    routes: int = 0, vehicles: int = 0, arrivals: int = 0,
                    duration: float = 0.0) -> None:
        """Log an entry to the journal."""
        try:
            entry = {
                "ts": datetime.now(timezone.utc).isoformat(),
                "status": status,
            }
            if error:
                entry["error"] = error
            entry["routes"] = routes
            entry["vehicles"] = vehicles
            entry["arrivals"] = arrivals
            entry["duration"] = duration

            with open(self.journal_file, 'a', encoding='utf-8') as f:
                f.write(json.dumps(entry, ensure_ascii=False) + '\n')
        except Exception:
            pass

    def _run_once(self) -> bool:
        """Run one polling cycle.

        Returns:
            True if successful, False on failure
        """
        start_time = time.time()

        # Try to use new provider first
        # But for now, we'll use the legacy OASA fetch for backward compatibility
        # and the new provider for the normalized format

        # Fetch using new provider
        normalized_data = self._fetch_all_data()

        # Fetch using legacy method for OASA (to maintain exact format)
        legacy_data = self._fetch_oasa_legacy()

        success = True

        # Write normalized format
        if normalized_data:
            if not self._write_normalized(normalized_data):
                success = False

            # Count items for journal
            routes = sum(len(op.get("routes", [])) for op in normalized_data.get("operators", {}).values())
            vehicles = sum(len(op.get("vehicles", [])) for op in normalized_data.get("operators", {}).values())
            arrivals = sum(len(op.get("arrivals", [])) for op in normalized_data.get("operators", {}).values())
            self._log_journal("success", duration=time.time() - start_time,
                           routes=routes, vehicles=vehicles, arrivals=arrivals)
        else:
            self._log_journal("failure", error="Failed to fetch normalized data")
            success = False

        # Write legacy format
        if legacy_data:
            if not self._write_legacy(legacy_data):
                success = False
        else:
            logger.warning("Legacy OASA data fetch failed")

        duration = time.time() - start_time

        if success:
            self._consecutive_failures = 0
            logger.info(f"Poll completed in {duration:.1f}s: "
                       f"{len(normalized_data.get('operators', {}).get('oasa', {}).get('routes', []))} routes, "
                       f"{len(normalized_data.get('operators', {}).get('oasa', {}).get('vehicles', []))} vehicles, "
                       f"{len(normalized_data.get('operators', {}).get('oasa', {}).get('arrivals', []))} arrivals")
        else:
            self._consecutive_failures += 1
            logger.warning(f"Poll failed after {duration:.1f}s")

        return success

    def run_once(self) -> bool:
        """Public method to run one polling cycle (non-continuous)."""
        return self._run_once()

    def start(self) -> None:
        """Start the continuous polling loop (blocking)."""
        self._running = True
        logger.info(f"Airport bus watcher starting (poll every {self.poll_interval}s)")
        logger.info(f"Output: {self.legacy_file}, {self.normalized_file}")

        while self._running:
            try:
                self._run_once()
            except Exception as e:
                logger.error(f"Unexpected error: {e}")
                self._consecutive_failures += 1

            # Calculate sleep time
            sleep_time = self.poll_interval
            if self._consecutive_failures > 0:
                # Apply backoff to interval on consecutive failures
                sleep_time = self._backoff_with_jitter(
                    min(self._consecutive_failures - 1, 5)
                )
            sleep_time = max(1.0, sleep_time)

            # Sleep in small increments for interruptibility
            for _ in range(int(sleep_time * 10)):
                if not self._running:
                    break
                time.sleep(0.1)

    def stop(self) -> None:
        """Stop the polling loop."""
        self._running = False
        logger.info("Airport bus watcher stopped")


def main():
    """Main entry point for running as a script."""
    watcher = AirportBusWatcher()
    watcher.start()


def run_once():
    """Run a single polling cycle (for testing or cron)."""
    watcher = AirportBusWatcher()
    success = watcher.run_once()
    return 0 if success else 1


if __name__ == "__main__":
    # Check if we should run once (for testing/cron)
    if "--once" in sys.argv or "--test" in sys.argv:
        sys.exit(run_once())
    else:
        main()
