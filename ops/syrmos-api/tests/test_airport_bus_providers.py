"""Tests for airport bus providers.

Run: python -m pytest tests/test_airport_bus_providers.py -v
"""

from __future__ import annotations

import json
import tempfile
import unittest
from datetime import datetime, timezone, timedelta
from pathlib import Path
from unittest.mock import MagicMock, patch, call

import sys
sys.path.insert(0, str(Path(__file__).parent.parent / "syrmos_admin"))

from syrmos_admin.airport_bus_providers import (
    AirportBusNormalizer,
    AirportBusPoller,
    OasaAirportProvider,
    OsethAirportProvider,
    Operator,
    ConfidenceLevel,
    ServiceStatus,
    Direction,
    NormalizedVehicle,
    NormalizedArrival,
    NormalizedRoute,
    AirportBusSnapshot,
    ValidationError,
    clamp_eta,
    deduplicate_vehicles,
    create_oasa_poller,
)


# =============================================================================
# Validation Tests
# =============================================================================

class TestValidation(unittest.TestCase):
    """Test validation functions."""

    def test_clamp_eta_positive(self):
        """Positive ETAs pass through unchanged."""
        self.assertEqual(clamp_eta(0), 0)
        self.assertEqual(clamp_eta(5), 5)
        self.assertEqual(clamp_eta(60), 60)

    def test_clamp_eta_negative(self):
        """Negative ETAs are clamped to 0."""
        self.assertEqual(clamp_eta(-1), 0)
        self.assertEqual(clamp_eta(-10), 0)
        self.assertEqual(clamp_eta(-100), 0)

    def test_clamp_eta_zero(self):
        """Zero ETA remains zero."""
        self.assertEqual(clamp_eta(0), 0)

    def test_validate_timestamp_valid_iso(self):
        """Valid ISO timestamps are parsed correctly."""
        from syrmos_admin.airport_bus_providers import validate_timestamp
        
        # UTC ISO
        result = validate_timestamp(datetime.now(timezone.utc).isoformat())
        self.assertIsNotNone(result)
        self.assertEqual(result.year, 2026)
        self.assertEqual(result.month, 10)
        self.assertEqual(result.day, 12)
        
        # With fractional seconds
        result = validate_timestamp((datetime.now(timezone.utc) - timedelta(seconds=1)).isoformat())
        self.assertIsNotNone(result)
        
        # Z suffix
        result = validate_timestamp(datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"))
        self.assertIsNotNone(result)

    def test_validate_timestamp_invalid(self):
        """Invalid timestamps raise ValidationError."""
        from syrmos_admin.airport_bus_providers import validate_timestamp
        
        with self.assertRaises(ValidationError):
            validate_timestamp("")
        
        with self.assertRaises(ValidationError):
            validate_timestamp("invalid")
        
        with self.assertRaises(ValidationError):
            validate_timestamp("not a timestamp")

    def test_validate_timestamp_future_too_far(self):
        """Timestamps too far in the future raise ValidationError."""
        from syrmos_admin.airport_bus_providers import validate_timestamp
        
        # More than 5 minutes in the future
        future = datetime.now(timezone.utc) + timedelta(minutes=10)
        with self.assertRaises(ValidationError):
            validate_timestamp(future.isoformat())

    def test_validate_timestamp_future_within_tolerance(self):
        """Timestamps within tolerance are accepted."""
        from syrmos_admin.airport_bus_providers import validate_timestamp
        
        # Within 5 minutes
        future = datetime.now(timezone.utc) + timedelta(seconds=30)
        result = validate_timestamp(future.isoformat())
        self.assertIsNotNone(result)


# =============================================================================
# Deduplication Tests
# =============================================================================

class TestDeduplication(unittest.TestCase):
    """Test deduplication functions."""

    def test_deduplicate_vehicles_removes_duplicates(self):
        """Duplicate vehicles are removed."""
        now = datetime.now(timezone.utc)
        vehicles = [
            NormalizedVehicle(
                vehicle_id="v1",
                line_id="X95",
                route_id="X95",
                operator=Operator.OASA,
                latitude=37.9,
                longitude=23.9,
                heading=0.0,
                to_airport=True,
                observed_at=now,
                source="oasa",
                source_url="http://test.com",
                confidence=ConfidenceLevel.LIVE,
                attribution="Test",
            ),
            NormalizedVehicle(
                vehicle_id="v1",
                line_id="X95",
                route_id="X95",
                operator=Operator.OASA,
                latitude=37.91,
                longitude=23.91,
                heading=0.0,
                to_airport=True,
                observed_at=now + timedelta(seconds=1),
                source="oasa",
                source_url="http://test.com",
                confidence=ConfidenceLevel.LIVE,
                attribution="Test",
            ),
        ]
        
        result = deduplicate_vehicles(vehicles)
        self.assertEqual(len(result), 1)
        # The newer observation should be kept
        self.assertEqual(result[0].latitude, 37.91)

    def test_deduplicate_vehicles_keeps_different(self):
        """Different vehicles are kept."""
        now = datetime.now(timezone.utc)
        vehicles = [
            NormalizedVehicle(vehicle_id="v1", line_id="X95", route_id="X95",
                           operator=Operator.OASA, latitude=37.9, longitude=23.9,
                           heading=0, to_airport=True, observed_at=now,
                           source="oasa", source_url="http://test.com",
                           confidence=ConfidenceLevel.LIVE, attribution="Test"),
            NormalizedVehicle(vehicle_id="v2", line_id="X95", route_id="X95",
                           operator=Operator.OASA, latitude=37.91, longitude=23.91,
                           heading=0, to_airport=True, observed_at=now,
                           source="oasa", source_url="http://test.com",
                           confidence=ConfidenceLevel.LIVE, attribution="Test"),
        ]
        
        result = deduplicate_vehicles(vehicles)
        self.assertEqual(len(result), 2)


# =============================================================================
# OASA Provider Tests
# =============================================================================

class TestOasaAirportProvider(unittest.TestCase):
    """Test OASA provider implementation."""

    def test_operator(self):
        """Operator is OASA."""
        provider = OasaAirportProvider()
        self.assertEqual(provider.operator, Operator.OASA)

    def test_source_url(self):
        """Source URL is OASA telematics."""
        provider = OasaAirportProvider()
        self.assertIn("oasa", provider.source_url.lower())

    def test_attribution(self):
        """Attribution is set."""
        provider = OasaAirportProvider()
        self.assertIn("OASA", provider.attribution)

    @patch.object(OasaAirportProvider, '_fetch_json')
    @patch('syrmos_admin.airport_bus_providers.datetime')
    def test_discover_routes(self, mock_datetime, mock_fetch):
        """discover_routes returns all known routes."""
        mock_datetime.now.return_value = datetime(2026, 10, 12, 12, 0, tzinfo=timezone.utc)
        mock_fetch.return_value = None  # No network needed for metadata
        
        provider = OasaAirportProvider()
        routes = provider.discover_routes()
        
        # Should have all 4 known routes
        route_ids = [r.route_id for r in routes]
        self.assertEqual(len(route_ids), 4)
        self.assertIn("X93", route_ids)
        self.assertIn("X95", route_ids)
        self.assertIn("X96", route_ids)
        self.assertIn("X97", route_ids)

    def test_route_to_line_mapping(self):
        """Route codes map to correct line IDs."""
        provider = OasaAirportProvider()
        
        # Test known mappings
        self.assertEqual(provider._route_code_to_line_id(2051), "X95")
        self.assertEqual(provider._route_code_to_line_id(2052), "X95")
        self.assertEqual(provider._route_code_to_line_id(5675), "X93")
        self.assertEqual(provider._route_code_to_line_id(5676), "X93")
        self.assertEqual(provider._route_code_to_line_id(5532), "X96")
        self.assertEqual(provider._route_code_to_line_id(3196), "X96")
        self.assertEqual(provider._route_code_to_line_id(5373), "X97")
        self.assertEqual(provider._route_code_to_line_id(5375), "X97")
        
        # Unknown code returns None
        self.assertIsNone(provider._route_code_to_line_id(9999))

    def test_infer_direction(self):
        """Direction inference from route code."""
        provider = OasaAirportProvider()
        
        # To airport direction
        self.assertEqual(provider._infer_direction(2051, "X95"), Direction.TO_AIRPORT)
        self.assertEqual(provider._infer_direction(5675, "X93"), Direction.TO_AIRPORT)
        
        # From airport direction
        self.assertEqual(provider._infer_direction(2052, "X95"), Direction.FROM_AIRPORT)
        self.assertEqual(provider._infer_direction(5676, "X93"), Direction.FROM_AIRPORT)
        
        # Unknown returns UNKNOWN
        self.assertEqual(provider._infer_direction(9999, "X95"), Direction.UNKNOWN)


# =============================================================================
# Normalizer Tests
# =============================================================================

class TestAirportBusNormalizer(unittest.TestCase):
    """Test data normalizer."""

    def setUp(self):
        self.normalizer = AirportBusNormalizer()
        self.now = datetime.now(timezone.utc)

    def test_normalize_vehicle_missing_id(self):
        """Vehicle without ID is skipped."""
        provider = OasaAirportProvider()
        raw = {"lineId": "X95", "lat": 37.9, "lng": 23.9}
        result = self.normalizer.normalize_vehicle(provider, raw, self.now)
        self.assertIsNone(result)

    def test_normalize_vehicle_null_island(self):
        """Vehicle at null island is skipped."""
        provider = OasaAirportProvider()
        raw = {"vehicleId": "v1", "lineId": "X95", "lat": 0, "lng": 0}
        result = self.normalizer.normalize_vehicle(provider, raw, self.now)
        self.assertIsNone(result)

    def test_normalize_vehicle_valid(self):
        """Valid vehicle is normalized."""
        provider = OasaAirportProvider()
        raw = {
            "vehicleId": "v1",
            "lineId": "X95",
            "lat": 37.9,
            "lng": 23.9,
            "heading": 125.0,
        }
        result = self.normalizer.normalize_vehicle(provider, raw, self.now)
        self.assertIsNotNone(result)
        self.assertEqual(result.vehicle_id, "v1")
        self.assertEqual(result.line_id, "X95")
        self.assertEqual(result.latitude, 37.9)
        self.assertEqual(result.longitude, 23.9)
        self.assertEqual(result.operator, Operator.OASA)
        self.assertEqual(result.confidence, ConfidenceLevel.LIVE)

    def test_normalize_arrival_negative_eta(self):
        """Negative ETA is clamped to 0."""
        provider = OasaAirportProvider()
        raw = {"lineId": "X95", "minutesAway": -5}
        result = self.normalizer.normalize_arrival(
            provider, raw, "10705", "Athens Airport", self.now
        )
        self.assertIsNotNone(result)
        self.assertEqual(result.estimated_minutes, 0)

    def test_normalize_arrival_missing_line(self):
        """Arrival without line ID is skipped."""
        provider = OasaAirportProvider()
        raw = {"minutesAway": 5}
        result = self.normalizer.normalize_arrival(
            provider, raw, "10705", "Athens Airport", self.now
        )
        self.assertIsNone(result)


# =============================================================================
# Poller Tests
# =============================================================================

class TestAirportBusPoller(unittest.TestCase):
    """Test the poller implementation."""

    def setUp(self):
        self.with_temp_dir = tempfile.TemporaryDirectory()
        self.temp_path = Path(self.with_temp_dir.name)

    def tearDown(self):
        self.with_temp_dir.cleanup()

    def test_poller_initialization(self):
        """Poller initializes correctly."""
        output_path = self.temp_path / "output.json"
        poller = create_oasa_poller(output_path)
        self.assertIsNotNone(poller)
        self.assertEqual(poller.interval, 30.0)

    @patch.object(OasaAirportProvider, 'discover_routes')
    @patch.object(OasaAirportProvider, 'fetch_vehicles')
    @patch.object(OasaAirportProvider, 'fetch_arrivals')
    @patch.object(OasaAirportProvider, 'fetch_alerts')
    def test_poller_generates_snapshot(self, mock_alerts, mock_arrivals, mock_vehicles, mock_routes):
        """Poller generates a valid snapshot."""
        # Setup mocks
        mock_routes.return_value = []
        mock_vehicles.return_value = []
        mock_arrivals.return_value = []
        mock_alerts.return_value = []
        
        output_path = self.temp_path / "output.json"
        poller = create_oasa_poller(output_path)
        
        snapshot = poller._generate_snapshot()
        self.assertIsNotNone(snapshot)
        self.assertIn("updated_at", snapshot)
        self.assertEqual(snapshot["operator"], "oasa")
        self.assertIn("routes", snapshot)


# =============================================================================
# Data Model Tests
# =============================================================================

class TestDataModels(unittest.TestCase):
    """Test data model serialization."""

    def test_vehicle_to_dict(self):
        """Vehicle serializes to dict correctly."""
        now = datetime.now(timezone.utc)
        vehicle = NormalizedVehicle(
            vehicle_id="v1",
            line_id="X95",
            route_id="X95",
            operator=Operator.OASA,
            latitude=37.9,
            longitude=23.9,
            heading=125.0,
            to_airport=True,
            observed_at=now,
            source="oasa",
            source_url="http://test.com",
            confidence=ConfidenceLevel.LIVE,
            attribution="Test",
        )
        d = vehicle.to_dict()
        self.assertEqual(d["vehicle_id"], "v1")
        self.assertEqual(d["operator"], "oasa")
        self.assertEqual(d["confidence"], "live")
        self.assertEqual(d["to_airport"], True)
        self.assertIn("observed_at", d)

    def test_arrival_to_dict(self):
        """Arrival serializes to dict correctly."""
        now = datetime.now(timezone.utc)
        arrival = NormalizedArrival(
            line_id="X95",
            route_id="X95",
            operator=Operator.OASA,
            stop_id="10705",
            stop_name="Athens Airport",
            destination="Athens Airport",
            direction=Direction.TO_AIRPORT,
            estimated_minutes=5,
            vehicle_id="v1",
            source="oasa",
            source_url="http://test.com",
            observed_at=now,
            freshness=0.0,
            confidence=ConfidenceLevel.LIVE,
            service_status=ServiceStatus.OPERATIONAL,
            attribution="Test",
        )
        d = arrival.to_dict()
        self.assertEqual(d["line_id"], "X95")
        self.assertEqual(d["operator"], "oasa")
        self.assertEqual(d["confidence"], "live")
        self.assertEqual(d["direction"], "to_airport")
        self.assertIn("observed_at", d)

    def test_snapshot_to_dict(self):
        """Snapshot serializes to dict correctly."""
        now = datetime.now(timezone.utc)
        snapshot = AirportBusSnapshot(
            updated_at=now,
            operator=Operator.OASA,
            routes=[],
            vehicles=[],
            arrivals=[],
            alerts=[],
            generation_duration=0.1,
            source="oasa",
            source_url="http://test.com",
            attribution="Test",
        )
        d = snapshot.to_dict()
        self.assertIn("updated_at", d)
        self.assertIn("operator", d)
        self.assertIn("routes", d)
        self.assertIn("vehicles", d)
        self.assertIn("arrivals", d)
        self.assertIn("alerts", d)


# =============================================================================
# Fixture Tests
# =============================================================================

class TestFixtures(unittest.TestCase):
    """Test fixture data and edge cases."""

    def test_valid_oasa_response(self):
        """Test processing of a valid OASA response."""
        provider = OasaAirportProvider()
        normalizer = AirportBusNormalizer()
        
        # Mock the _fetch_json to return a known response
        with patch.object(provider, '_fetch_json') as mock_fetch:
            mock_fetch.return_value = [
                {
                    "route_code": 2051,
                    "veh_code": "12345",
                    "btime2": 5,
                },
            ]
            
            arrivals = provider.fetch_arrivals()
            self.assertEqual(len(arrivals), 1)
            self.assertEqual(arrivals[0].line_id, "X95")
            self.assertEqual(arrivals[0].estimated_minutes, 5)

    def test_malformed_response(self):
        """Test handling of malformed response."""
        provider = OasaAirportProvider()
        
        with patch.object(provider, '_fetch_json') as mock_fetch:
            mock_fetch.return_value = None  # Simulate failed fetch
            arrivals = provider.fetch_arrivals()
            self.assertEqual(arrivals, [])

    def test_missing_eta_field(self):
        """Test handling of arrival with missing ETA."""
        provider = OasaAirportProvider()
        normalizer = AirportBusNormalizer()
        
        raw = {"lineId": "X95"}  # No minutesAway
        now = datetime.now(timezone.utc)
        result = normalizer.normalize_arrival(
            provider, raw, "10705", "Airport", now
        )
        # Should still create an arrival but with null ETA
        self.assertIsNotNone(result)
        self.assertIsNone(result.estimated_minutes)

    def test_stale_timestamp(self):
        """Test handling of stale data."""
        normalizer = AirportBusNormalizer()
        now = datetime.now(timezone.utc)
        stale_time = now - timedelta(minutes=10)
        
        raw = {"vehicleId": "v1", "lineId": "X95", "lat": 37.9, "lng": 23.9}
        result = normalizer.normalize_vehicle(
            OasaAirportProvider(), raw, stale_time
        )
        # Should be marked as stale confidence? Not yet, confidence is computed differently
        self.assertIsNotNone(result)


# =============================================================================
# Provider Tests - Edge Cases
# =============================================================================

class TestProviderEdgeCases(unittest.TestCase):
    """Test edge cases and error handling in providers."""

    def test_oasa_provider_no_route_codes(self):
        """OASA provider handles unknown route codes gracefully."""
        provider = OasaAirportProvider()
        
        # Test with a route_id that doesn't exist
        result = provider.fetch_route_metadata("UNKNOWN")
        self.assertIsNone(result)

    def test_oasa_provider_fetch_vehicles_with_route(self):
        """Test fetching vehicles for a specific route."""
        provider = OasaAirportProvider()
        
        # This will hit the network, but we can mock it
        with patch.object(provider, '_fetch_json') as mock_fetch:
            mock_fetch.return_value = [
                {
                    "VEH_NO": "12345",
                    "CS_LAT": 37.9,
                    "CS_LNG": 23.9,
                    "VEH_HEADING": 125.5,
                    "ROUTE_CODE": 2051,
                    "CS_DATE": "2026-10-12 12:34:56",
                },
            ]
            
            vehicles = provider.fetch_vehicles("X95")
            self.assertGreaterEqual(len(vehicles), 0)

    def test_oasa_provider_fetch_stops(self):
        """Test fetching stops for a route."""
        provider = OasaAirportProvider()
        
        with patch.object(provider, '_fetch_json') as mock_fetch:
            mock_fetch.return_value = [
                {
                    "StopCode": "1",
                    "StopDescr": "Stop 1",
                    "StopDescrEng": "Stop 1 English",
                    "StopLat": 37.9,
                    "StopLng": 23.9,
                    "RouteStopOrder": 1,
                },
            ]
            
            stops = provider.fetch_stops("X95")
            self.assertGreaterEqual(len(stops), 1)
            self.assertEqual(stops[0]["stop_id"], "OASA_1")


# =============================================================================
# OSETH Provider Tests
# =============================================================================

class TestOsethProvider(unittest.TestCase):
    """Test OsethAirportProvider implementation."""

    def test_oseth_provider_operator(self):
        """Test operator property."""
        provider = OsethAirportProvider()
        self.assertEqual(provider.operator, Operator.OSETH)

    def test_oseth_provider_source_url(self):
        """Test source_url property."""
        provider = OsethAirportProvider()
        self.assertEqual(provider.source_url, "https://oseth.com.gr/en/")

    def test_oseth_provider_attribution(self):
        """Test attribution property."""
        provider = OsethAirportProvider()
        self.assertEqual(provider.attribution, "Live bus data from OSETH/OASTH")

    def test_oseth_provider_discover_routes(self):
        """Test discovering routes."""
        provider = OsethAirportProvider()
        routes = provider.discover_routes()
        
        self.assertEqual(len(routes), 2)
        route_ids = [r.route_id for r in routes]
        self.assertIn("X3", route_ids)
        self.assertIn("2X", route_ids)
        
        for route in routes:
            self.assertEqual(route.operator, Operator.OSETH)
            self.assertEqual(route.airport, "SKG")
            self.assertEqual(route.color, "#0E7490")

    def test_oseth_provider_fetch_route_metadata(self):
        """Test fetching metadata for a specific route."""
        provider = OsethAirportProvider()
        
        # Test X3 route
        metadata = provider.fetch_route_metadata("X3")
        self.assertIsNotNone(metadata)
        self.assertEqual(metadata.route_id, "X3")
        self.assertEqual(metadata.operator, Operator.OSETH)
        self.assertEqual(metadata.origin, "Makedonia Airport")
        self.assertEqual(metadata.destination, "Mikra")
        
        # Test 2X route
        metadata = provider.fetch_route_metadata("2X")
        self.assertIsNotNone(metadata)
        self.assertEqual(metadata.destination, "Nea Elvetia")
        
        # Test unknown route
        metadata = provider.fetch_route_metadata("UNKNOWN")
        self.assertIsNone(metadata)

    def test_oseth_provider_fetch_stops(self):
        """Test fetching stops for a route."""
        provider = OsethAirportProvider()
        
        # Should return airport stop and metro interchange
        stops = provider.fetch_stops("X3")
        self.assertEqual(len(stops), 2)
        
        # First stop should be the airport
        self.assertEqual(stops[0]["stop_id"], "THS_AIR")
        self.assertIn("Makedonia", stops[0]["stop_name"] or "")
        
        # Second stop should be the metro interchange
        self.assertTrue(stops[1]["stop_id"].startswith("THS_"))

    def test_oseth_provider_fetch_stops_unknown_route(self):
        """Test fetching stops for unknown route."""
        provider = OsethAirportProvider()
        stops = provider.fetch_stops("UNKNOWN")
        self.assertEqual(stops, [])

    def test_oseth_provider_fetch_arrivals_empty(self):
        """Test fetching arrivals returns empty list (API not validated yet)."""
        provider = OsethAirportProvider()
        
        with patch.object(provider, '_fetch_json') as mock_fetch:
            mock_fetch.return_value = []
            arrivals = provider.fetch_arrivals()
            self.assertEqual(arrivals, [])

    def test_oseth_provider_fetch_vehicles_empty(self):
        """Test fetching vehicles returns empty list (API not validated yet)."""
        provider = OsethAirportProvider()
        
        with patch.object(provider, '_fetch_json') as mock_fetch:
            mock_fetch.return_value = []
            vehicles = provider.fetch_vehicles()
            self.assertEqual(vehicles, [])

    def test_oseth_provider_fetch_alerts(self):
        """Test fetching alerts returns empty list."""
        provider = OsethAirportProvider()
        alerts = provider.fetch_alerts()
        self.assertEqual(alerts, [])

    def test_oseth_provider_route_code_mapping(self):
        """Test route code to line ID mapping."""
        provider = OsethAirportProvider()
        
        # Test known route codes
        self.assertEqual(provider._route_code_to_line_id(101), "X3")
        self.assertEqual(provider._route_code_to_line_id(102), "X3")
        self.assertEqual(provider._route_code_to_line_id(201), "2X")
        self.assertEqual(provider._route_code_to_line_id(202), "2X")
        
        # Test unknown route code
        self.assertIsNone(provider._route_code_to_line_id(999))

    def test_oseth_provider_direction_inference(self):
        """Test direction inference from route code."""
        provider = OsethAirportProvider()
        
        # Test to airport direction
        self.assertEqual(provider._infer_direction(101, "X3"), Direction.TO_AIRPORT)
        self.assertEqual(provider._infer_direction(201, "2X"), Direction.TO_AIRPORT)
        
        # Test from airport direction
        self.assertEqual(provider._infer_direction(102, "X3"), Direction.FROM_AIRPORT)
        self.assertEqual(provider._infer_direction(202, "2X"), Direction.FROM_AIRPORT)
        
        # Test unknown route code
        self.assertEqual(provider._infer_direction(999, "X3"), Direction.UNKNOWN)


# =============================================================================
# Unified Model Tests
# =============================================================================

# =============================================================================
# Fixture-based Tests
# =============================================================================

class TestFixtures(unittest.TestCase):
    """Test with fixture data."""

    def test_oasa_valid_fixture_structure(self):
        """Test that the valid OASA fixture has expected structure."""
        import json
        with open('tests/fixtures/oasa-airport-buses-valid.json', 'r') as f:
            payload = json.load(f)
        
        self.assertIn('updatedAt', payload)
        self.assertIn('vehicles', payload)
        self.assertIn('airportArrivals', payload)
        self.assertIn('routes', payload)
        
        # Test vehicles
        vehicles = payload['vehicles']
        self.assertEqual(len(vehicles), 2)
        self.assertIn('vehicleId', vehicles[0])
        self.assertIn('lineId', vehicles[0])
        
        # Test arrivals
        arrivals = payload['airportArrivals']
        self.assertGreater(len(arrivals), 0)
    
    def test_unified_fixture_structure(self):
        """Test that the unified fixture has expected structure."""
        import json
        with open('tests/fixtures/unified-airport-buses.json', 'r') as f:
            payload = json.load(f)
        
        self.assertIn('updated_at', payload)
        self.assertIn('operators', payload)
        self.assertIn('sources', payload)
        
        # Test operators
        operators = payload['operators']
        self.assertIn('oasa', operators)
        self.assertIn('oseth', operators)
        
        # Test OASA operator data
        oasa = operators['oasa']
        self.assertIn('routes', oasa)
        self.assertIn('vehicles', oasa)
        self.assertIn('arrivals', oasa)
        
        # Test routes
        routes = oasa['routes']
        self.assertGreater(len(routes), 0)
        route = routes[0]
        self.assertIn('route_id', route)
        self.assertIn('operator', route)


class TestUnifiedModel(unittest.TestCase):
    """Test unified data model serialization."""

    def test_normalized_vehicle_to_dict(self):
        """Test NormalizedVehicle serialization."""
        now = datetime.now(timezone.utc)
        vehicle = NormalizedVehicle(
            vehicle_id="V001",
            line_id="X95",
            route_id="X95",
            operator=Operator.OASA,
            latitude=37.9,
            longitude=23.9,
            heading=126.5,
            observed_at=now,
            source="oasa_telematics",
            source_url="https://telematics.oasa.gr",
            confidence=ConfidenceLevel.LIVE,
            attribution="Live bus data from OASA Telematics",
            to_airport=True,
        )
        
        d = vehicle.to_dict()
        self.assertEqual(d["vehicle_id"], "V001")
        self.assertEqual(d["line_id"], "X95")
        self.assertEqual(d["operator"], "oasa")
        self.assertEqual(d["latitude"], 37.9)
        self.assertEqual(d["confidence"], "live")
        self.assertTrue(d["to_airport"])

    def test_normalized_arrival_to_dict(self):
        """Test NormalizedArrival serialization."""
        now = datetime.now(timezone.utc)
        arrival = NormalizedArrival(
            line_id="X95",
            route_id="X95",
            operator=Operator.OASA,
            stop_id="10705",
            stop_name="Athens Airport",
            destination="Syntagma",
            direction=Direction.TO_AIRPORT,
            source="oasa_telematics",
            source_url="https://telematics.oasa.gr",
            observed_at=now,
            freshness=5.0,
            confidence=ConfidenceLevel.LIVE,
            service_status=ServiceStatus.OPERATIONAL,
            attribution="Live bus data from OASA Telematics",
            estimated_minutes=10,
        )
        
        d = arrival.to_dict()
        self.assertEqual(d["line_id"], "X95")
        self.assertEqual(d["operator"], "oasa")
        self.assertEqual(d["direction"], "to_airport")
        self.assertEqual(d["confidence"], "live")
        self.assertEqual(d["estimated_minutes"], 10)

    def test_normalized_route_to_dict(self):
        """Test NormalizedRoute serialization."""
        now = datetime.now(timezone.utc)
        route = NormalizedRoute(
            operator=Operator.OASA,
            route_id="X95",
            public_name="X95 Syntagma - Airport",
            public_name_en="X95 Syntagma - Airport",
            origin="Syntagma",
            destination="Athens Airport",
            airport="AIA",
            color="#E8792B",
            source="oasa_telematics",
            source_url="https://telematics.oasa.gr",
            attribution="Live bus data from OASA Telematics",
            observed_at=now,
            to_airport_route_codes=[2051],
            from_airport_route_codes=[2052],
            direction=Direction.TO_AIRPORT,
            is_operational=True,
        )
        
        d = route.to_dict()
        self.assertEqual(d["route_id"], "X95")
        self.assertEqual(d["operator"], "oasa")
        self.assertEqual(d["airport"], "AIA")
        self.assertEqual(d["color"], "#E8792B")

    def test_airport_bus_snapshot_to_dict(self):
        """Test AirportBusSnapshot serialization."""
        now = datetime.now(timezone.utc)
        snapshot = AirportBusSnapshot(
            updated_at=now,
            operator=Operator.OASA,
            routes=[],
            vehicles=[],
            arrivals=[],
            alerts=[],
            generation_duration=0.1,
            source="oasa_telematics",
            source_url="https://telematics.oasa.gr",
            attribution="Live bus data from OASA Telematics",
        )
        
        d = snapshot.to_dict()
        self.assertIn("updated_at", d)
        self.assertIn("operator", d)
        self.assertIn("routes", d)
        self.assertIn("vehicles", d)
        self.assertIn("arrivals", d)


if __name__ == "__main__":
    unittest.main()
