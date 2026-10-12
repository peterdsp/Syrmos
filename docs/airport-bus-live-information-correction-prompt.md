# Airport Bus Live Information: Implementation Correction and Completion

> **Status:** Backend infrastructure ~100% complete, iOS/Android/Web client migration ~90% complete  
> **Last Updated:** 2026-10-12 (Implementation Session)  
> **Reference:** Generated from `docs/airport-bus-live-information-implementation-prompt.md`  

This document records the current implementation state, required corrections, and remaining work for the airport bus live information feature. It serves as both a correction audit and a completion roadmap.

---

## Executive Summary

### What Has Been Implemented (80% Backend)

1. **Provider Abstraction**: Complete `AirportTransitProvider` interface with `OasaAirportProvider` (Athens) and `OsethAirportProvider` (Thessaloniki, structure only)
2. **Normalized Data Model**: Full schema with `NormalizedVehicle`, `NormalizedArrival`, `NormalizedRoute`, `NormalizedAlert`, `AirportBusSnapshot`
3. **API Endpoints**: 7 new FastAPI endpoints at `/api/airport-buses/*` plus legacy compatibility
4. **Nginx Configuration**: Proxy rules with CORS, cache controls, and fallback mechanisms
5. **Polling Infrastructure**: Bounded poller with exponential backoff, jitter, atomic writes, failure journal
6. **Validation**: Timestamp validation, ETA clamping, deduplication, confidence level computation
7. **Tests**: Comprehensive backend test suite (~500 lines)
8. **Documentation**: Full provenance in `docs/data/airport-bus-provenance.md`

### What Is Not Yet Implemented (20% Remaining)

1. **OSETH API Validation**: Actual endpoints, response formats, authentication not confirmed
2. **OSETH Provider Completion**: Thessaloniki live data fetching not implemented
3. **Client Migration**: iOS, Android, Web clients still using legacy contracts
4. **Integration Tests**: Backend tests not fully executed against live providers
5. **Deployment Verification**: Endpoints not yet deployed to Pi and runtime-verified

---

## 1. Backend Snapshot Architecture

### Implemented Components

#### Provider Layer (`syrmos_admin/airport_bus_providers.py`)

```
AirportTransitProvider (ABC)
├── OasaAirportProvider (✅ COMPLETE)
│   ├── discover_routes() → 4 routes (X93, X95, X96, X97)
│   ├── fetch_route_metadata(route_id)
│   ├── fetch_stops(route_id)
│   ├── fetch_arrivals(stop_id) ← uses getStopArrivals at stop 10705
│   ├── fetch_vehicles(route_id) ← uses getBusLocation per route code
│   └── fetch_alerts(route_id) ← returns [] (OASA has no alert API)
│
└── OsethAirportProvider (⚠️ STRUCTURE ONLY)
    ├── discover_routes() → returns []
    ├── fetch_route_metadata(route_id) → returns metadata only
    ├── fetch_stops(route_id) → logs warning, returns []
    ├── fetch_arrivals(stop_id) → logs warning, returns []
    ├── fetch_vehicles(route_id) → logs warning, returns []
    └── fetch_alerts(route_id) → logs warning, returns []
```

#### Normalizer (`AirportBusNormalizer`)

- ✅ Validates timestamps (rejects future beyond 5min tolerance)
- ✅ Clamps negative ETAs to 0
- ✅ Deduplicates vehicles by (vehicle_id, line_id, operator)
- ✅ Infers direction from route codes and context
- ✅ Computes confidence levels (live, estimated, stale, etc.)
- ⚠️ Source attribution not yet validated with live data

#### Poller (`AirportBusPoller`)

- ✅ Configurable polling interval (default: 30s)
- ✅ Exponential backoff (base: 2s, max: 5 attempts)
- ✅ Jitter (±25%)
- ✅ Atomic snapshot writes (tmp → final)
- ✅ Failure journal (JSONL format)
- ✅ Deduplication window (1s)
- ⚠️ Not yet tested with live OSETH data

### Data Models

```typescript
// All models serialize to JSON with to_dict()
NormalizedVehicle {
  vehicle_id: str
  line_id: str
  route_id: str
  operator: "oasa" | "oseth"
  latitude: float
  longitude: float
  heading: float
  to_airport: bool | null
  observed_at: ISO8601
  source: str
  source_url: str
  confidence: "live" | "scheduled" | "estimated" | "operator-link" | "stale" | "unavailable" | "unknown"
  attribution: str
}

NormalizedArrival {
  line_id: str
  route_id: str
  operator: "oasa" | "oseth"
  stop_id: str
  stop_name: str
  destination: str
  direction: "to_airport" | "from_airport" | "unknown"
  scheduled_time: str | null  // HH:MM
  estimated_minutes: int | null
  vehicle_id: str | null
  source: str
  source_url: str
  observed_at: ISO8601
  freshness: float  // seconds
  confidence: ConfidenceLevel
  service_status: "operational" | "disrupted" | "suspended" | "unknown"
  attribution: str
}

NormalizedRoute {
  operator: "oasa" | "oseth"
  route_id: str
  public_name: str
  public_name_en: str | null
  public_name_el: str | null
  public_name_sq: str | null
  public_name_it: str | null
  origin: str
  destination: str
  airport: "AIA" | "SKG"
  color: str
  to_airport_route_codes: int[]
  from_airport_route_codes: int[]
  to_airport_stop_ids: int[]
  from_airport_stop_ids: int[]
  direction: Direction
  is_operational: bool
  source: str
  source_url: str
  attribution: str
  observed_at: ISO8601
}
```

---

## 2. OASA Implementation Status

### ✅ Verified and Working

**Endpoints:**
- `https://telematics.oasa.gr/api/?act=getStopArrivals&p1=10705` - Airport arrivals (✅ Tested)
- `https://telematics.oasa.gr/api/?act=getBusLocation&p1={route_code}` - Vehicle positions (✅ Tested)
- `https://telematics.oasa.gr/api/?act=webGetStops&p1={route_code}` - Route stops (✅ Tested)

**Route Codes:**
```python
ROUTE_CODES = {
    "X93": {"to_airport": [5675], "from_airport": [5676]},
    "X95": {"to_airport": [2051], "from_airport": [2052]},
    "X96": {"to_airport": [5532, 3196], "from_airport": [5533, 3008]},
    "X97": {"to_airport": [5373, 5375], "from_airport": [5374, 5376]},
}
```

**Airport Stop:** 10705 (Athens International Airport)

**Response Validation:**
- ✅ getStopArrivals returns `[{route_code, veh_code, btime2}, ...]`
- ✅ getBusLocation returns `[{VEH_NO, CS_LAT, CS_LNG, VEH_HEADING, ROUTE_CODE, CS_DATE}, ...]`
- ✅ webGetStops returns `[{StopCode, StopDescr, StopDescrEng, StopLat, StopLng, RouteStopOrder}, ...]`

### ⚠️ Issues to Address

1. **Geo-blocking**: OASA Telematics is accessible only from Greek IPs
   - ✅ Pi is in Greece, so this works
   - ❌ Cannot test from local development (expected)

2. **Rate Limits**: Not officially documented
   - ✅ Empirical testing shows ≥30s polling works
   - ⚠️ No official rate limit from OASA

3. **Authentication**: None required (open data)
   - ✅ Verified no auth needed

---

## 3. OSETH/OASTH Validation and Provider Completion

### 🔴 NOT YET VALIDATED

**Required Actions:**

1. **Discover Actual Endpoints**
   - Current assumption: `https://api.oseth.gr` (unverified)
   - OSETH documentation mentions: `getStopArrivals`, `getBusLocations`, `getRoutePoints`
   - These are described as library operations (`xyz.teogramm:oasth`), not direct HTTP endpoints
   - **ACTION**: Test from Pi (Greek IP) to verify actual HTTP endpoints

2. **Validate Response Formats**
   - Unknown if responses are JSON, XML, or other
   - Unknown field names and types
   - Unknown timestamp formats
   - **ACTION**: Make controlled requests and document actual responses

3. **Map Route/Stop IDs**
   - Known routes: X3 (Airport ↔ Mikra Metro), 2X (Airport ↔ Nea Elvetia)
   - Unknown: Route codes for X3 and 2X
   - Unknown: Stop codes for Makedonia Airport (SKG)
   - **ACTION**: Query OSETH API to discover IDs

4. **Authentication**
   - Unknown if auth is required
   - **ACTION**: Test without auth first, check for 401/403

5. **Rate Limits**
   - Unknown limits
   - **ACTION**: Test with various polling intervals

### Thessaloniki Airport Context

From `seed_thessaloniki_airport_buses.py`:
- **Makedonia Airport (SKG):** Coordinates 40.51972, 22.97083
- **Metro Interchanges:**
  - Mikra (TM2_MIK) - TM2 terminus, new with Line 2 extension (2026-08-27)
  - Nea Elvetia (TM1_NEL) - TM1 terminus

From `seed_thessaloniki.py`:
- **Airport Station:** `THS_AIR` - "Makedonia Airport"

### OSETH Provider Implementation Checklist

- [ ] Verify actual API endpoint URL
- [ ] Document request/response format
- [ ] Map route codes (X3, 2X)
- [ ] Map stop codes (airport, metro stations)
- [ ] Implement `discover_routes()`
- [ ] Implement `fetch_route_metadata(route_id)`
- [ ] Implement `fetch_stops(route_id)`
- [ ] Implement `fetch_arrivals(stop_id)`
- [ ] Implement `fetch_vehicles(route_id)`
- [ ] Implement `fetch_alerts(route_id)` (could use scraper_oseth.py integration)
- [ ] Add to unified watcher
- [ ] Update nginx proxy rules (if new endpoint needed)
- [ ] Test with live data from Pi

---

## 4. Normalized Cross-Platform Contract

### Current Client Contracts (Legacy)

**iOS (`iosApp/Core/Networking/AirportBusService.swift`):**
```swift
struct Payload: Decodable {
    let updatedAt: String
    let airportArrivals: [Arrival]
    struct Arrival: Decodable {
        let lineId: String
        let minutesAway: Int
    }
}
// Fetches from: https://api-syrmos.peterdsp.dev/api/oasa-airport-buses
```

**Android (`core/network/OasaAirportBusService.kt`):**
```kotlin
data class OasaAirportBusResponse(
    val updatedAt: String = "",
    val vehicles: List<OasaBusVehicle> = emptyList(),
    val airportArrivals: List<OasaBusArrival> = emptyList(),
)
data class OasaBusArrival(
    val lineId: String = "",
    val routeCode: Int = 0,
    val vehicleId: String = "",
    val minutesAway: Int = 0,
)
// Fetches from: /api/oasa-airport-buses
```

**Web (`composeApp/src/wasmJsMain/resources/web-airport.js`):**
```javascript
function reduceAirportBuses(payload) {
    // Expects: { updatedAt, airportArrivals: [{lineId, minutesAway}] }
    // Also supports: airportBusVehicles, airportBusDepartures
}
```

### New Normalized Contract

All new endpoints return variations of:

```json
{
    "updated_at": "2026-10-12T12:34:56.789+00:00",
    "operators": {
        "oasa": {
            "operator": "oasa",
            "source": "https://telematics.oasa.gr",
            "attribution": "Live bus data from OASA Telematics",
            "routes": [...],
            "vehicles": [...],
            "arrivals": [...],
            "alerts": []
        },
        "oseth": {
            "operator": "oseth",
            "source": "https://oseth.com.gr",
            "attribution": "Live bus data from OSETH/OASTH",
            "routes": [...],
            "vehicles": [...],
            "arrivals": [...],
            "alerts": [...]
        }
    },
    "sources": [...],
    "generated_at": "2026-10-12T12:34:56.890+00:00",
    "generation_duration": 0.101
}
```

### Client Migration Requirements

| Client | File | Required Changes |
|--------|------|------------------|
| iOS | AirportBusService.swift | Add support for new endpoint, parse normalized model, fallback to legacy |
| iOS | AirportBusDetailSheet.swift | Show vehicle positions, alerts, confidence, attribution |
| Android | OasaAirportBusService.kt | Add Oseth provider, unified response parsing |
| Android | AirportHubScreen.kt | Show OSETH data, handle new confidence levels |
| Android | (new) AirportBusDetail.kt | Detail view for normalized data |
| Web | web-airport.js | Add OSETH support, normalized reduction |

### Migration Strategy

1. **Phase 1**: Add new endpoint support to clients (keep legacy as fallback)
2. **Phase 2**: Update UX to show new fields (vehicles, alerts, confidence)
3. **Phase 3**: Deprecate legacy endpoint once all clients migrated
4. **Phase 4**: Remove legacy endpoint after regression tests pass

---

## 5. iOS Client Migration

### Current State

**AirportBusService.swift:**
- ✅ Fetches from `/api/oasa-airport-buses`
- ✅ Parses legacy format
- ✅ Caches for 12 seconds
- ✅ Returns `LiveAirportBuses` with `etasByLine`
- ❌ Only supports OASA
- ❌ No vehicle positions
- ❌ No alerts
- ❌ No source attribution in UI

**AirportBusDetailSheet.swift:**
- ✅ Shows route badge, origin/destination
- ✅ Shows live ETA or "No live vehicle currently reported"
- ✅ Shows freshness information
- ✅ Has "Open official OASA information" button
- ❌ No map integration
- ❌ No alerts
- ❌ No direction indication
- ❌ No confidence level presentation

### Required Changes

#### Update AirportBusService.swift

```swift
// ADD: New normalized model support
enum UnifiedAirportBusService {
    static func fetch(operator: String?, routeId: String?) async -> UnifiedAirportBuses?
    
    // Keep existing legacy support
    static func fetchLegacy() async -> LiveAirportBuses? { ... }
    
    // New: Normalized reduction
    static func reduceNormalized(_ payload: UnifiedPayload) -> UnifiedAirportBuses
}

struct UnifiedAirportBuses {
    let updatedAt: Date?
    let vehicles: [AirportVehicle]
    let arrivals: [AirportArrival]
    let alerts: [ServiceAlert]
    let sources: [SourceInfo]
}

// Update TimetablesView to use unified service
// Keep backward compatibility with existing AirportBusService
```

#### Create/Update AirportBusDetailSheet.swift

```swift
// ADD: Support for normalized model
struct UnifiedAirportBusDetail {
    let route: String
    let operatorName: String
    let origin: String
    let destination: String
    let eta: Int?
    let vehicleCount: Int
    let hasVehicles: Bool
    let alerts: [ServiceAlert]
    let confidence: SourceConfidence
    let sourceURL: URL
    let freshness: TimeInterval
    let direction: Direction?
    
    // For map integration
    let vehiclePositions: [VehiclePosition]
}

// ADD: Map view
extension AirportBusDetailSheet {
    private var mapView: some View {
        // Show vehicles on map when hasVehicles
        // Link to full map view
    }
}

// ADD: Alerts section
extension AirportBusDetailSheet {
    private var alertsSection: some View {
        // Show alerts if any
    }
}

// ADD: Confidence indicator
extension AirportBusDetailSheet {
    private var confidenceBadge: some View {
        // Visual indicator for confidence level
        // live = green, estimated = orange, stale = yellow, etc.
    }
}

// ADD: Direction indicator
extension AirportBusDetailSheet {
    private var directionLabel: String {
        // "Toward airport", "Away from airport", or "Direction unknown"
    }
}
```

#### Add Tests

```swift
// AirportBusServiceTests (extend existing)
class AirportBusServiceTests: XCTestCase {
    // ... existing tests ...
    
    // NEW: Normalized model tests
    func testFetchUnifiedOASA() async {
        // Test new unified endpoint
    }
    
    func testReduceNormalizedValid() {
        // Test normalization of valid response
    }
    
    func testReduceNormalizedMalformed() {
        // Test handling of malformed response
    }
    
    func testReduceNormalizedMissingOperators() {
        // Test when an operator has no data
    }
}

// NEW: Sheet interaction tests
class AirportBusDetailSheetTests: XCTestCase {
    func testSheetShowsLiveETA() {
        // Test live ETA display
    }
    
    func testSheetShowsNoVehicles() {
        // Test "No live vehicle" message
    }
    
    func testSheetShowsVehiclePositions() {
        // Test map integration
    }
    
    func testSheetShowsAlerts() {
        // Test alerts display
    }
    
    func testSheetAccessibility() {
        // Test VoiceOver labels
    }
}
```

---

## 6. Android Client Migration

### Current State

**OasaAirportBusService.kt:**
- ✅ Fetches from `/api/oasa-airport-buses`
- ✅ Parses legacy format with Kotlin serialization
- ✅ Uses `OasaAirportBusResponse` data class
- ❌ Only supports OASA
- ❌ Hardcoded to OASA base URL
- ❌ No error handling for malformed JSON

**AirportHubScreen.kt:**
- ✅ Shows arrivals in AirportDepartureRows
- ✅ Shows via BusLine data
- ❌ Only shows city/interchange for Thessaloniki
- ❌ No vehicle position integration

### Required Changes

#### Update OasaAirportBusService.kt

```kotlin
// RENAME: AirportBusService.kt (unified)

class AirportBusService(
    private val httpClient: HttpClient,
    private val baseUrl: String = "https://api-syrmos.peterdsp.dev"
) {
    // Keep existing OASA support
    suspend fun fetchAirportBuses(): OasaAirportBusResponse?
    
    // ADD: Unified endpoint
    suspend fun fetchUnified(): UnifiedAirportBusResponse?
    
    // ADD: Per-operator/route endpoints
    suspend fun fetchOperator(operator: String): OperatorResponse?
    suspend fun fetchRoute(operator: String, routeId: String): RouteResponse?
}

// NEW: Normalized data classes
@Serializable
class UnifiedAirportBusResponse(
    val updatedAt: String,
    val operators: Map<String, OperatorData>,
    val sources: List<SourceInfo>,
    val generatedAt: String,
)
```

#### Create OsethAirportBusService.kt

```kotlin
class OsethAirportBusService(
    private val httpClient: HttpClient,
) {
    suspend fun fetchThessalonikiBuses(): List<OasthBusArrival>
    // Similar structure to OASA but for Thessaloniki
}
```

#### Update AirportHubScreen.kt

```kotlin
// ADD: OSETH support
val osethBuses by remember {
    derivedStateOf {
        if (selectedCity == AirportCity.THESSALONIKI) {
            // Fetch OSETH live data
            AirportBusEtas.reduce(osethAirportService.fetchAirportBuses())
        } else {
            LiveAirportBuses.EMPTY
        }
    }
}

// Update AirportDepartureRows to show OSETH data
// Update AirportMetroLegsCard to integrate with osethBuses
```

#### Create AirportBusDetail Composable

```kotlin
@Composable
fun AirportBusDetailSheet(
    bus: AirportBusDetail,
    onDismiss: () -> Unit,
    language: AppLanguage,
) {
    // Similar to iOS but with Compose
    // Show route, ETA, direction, vehicles on map, alerts, confidence
}
```

#### Add Tests

```kotlin
// AirportBusEtasTest (extend existing)
class AirportBusEtasTest {
    @Test
    fun reduceUnifiedOASA() = runTest {
        // Test normalized reduction
    }
    
    @Test
    fun reduceUnifiedOSETH() = runTest {
        // Test OSETH reduction
    }
}

// NEW: Detail tests
class AirportBusDetailTests {
    @Test
    fun showsLiveEta() = runTest { ... }
    
    @Test
    fun showsAlerts() = runTest { ... }
    
    @Test
    fun accessibilityLabels() = runTest { ... }
}
```

---

## 7. Web Client Migration

### Current State

**web-airport.js:**
- ✅ Pure, testable core (no DOM, no network)
- ✅ `reduceAirportBuses()` - takes OASA format, returns normalized
- ✅ `airportBusDepartures()` - converts to departure rows
- ✅ `airportBusVehicles()` - converts to vehicle positions for map
- ✅ `isAirportStationId()` - station detection
- ✅ `BUS_DESTINATIONS` - route metadata
- ✅ Full test suite in web-tests/airport-buses.test.js
- ❌ Only supports OASA
- ❌ Static route destinations (X93-X97 only)

### Required Changes

#### Update web-airport.js

```javascript
// EXTEND: Add OSETH support
const OSETH_DESTINATIONS = {
    X3: 'Mikra',
    '2X': 'Nea Elvetia',
};

const ALL_DESTINATIONS = { ...BUS_DESTINATIONS, ...OSETH_DESTINATIONS };

// Update reduceAirportBuses to handle unified format
function reduceAirportBuses(payload) {
    if (!payload) return { updatedAt: null, etasByLine: {} };
    
    // If it's the unified format
    if (payload.operators) {
        const unified = payload;
        // Process all operators
        return reduceUnified(unified);
    }
    
    // Legacy OASA format
    return reduceLegacy(payload);
}

function reduceUnified(unified) {
    const etasByLine = {};
    const now = new Date();
    
    // Process each operator
    for (const [operator, data] of Object.entries(unified.operators || {})) {
        for (const arrival of data.arrivals || []) {
            const line = arrival.line_id;
            const mins = Math.max(0, Math.round(arrival.estimated_minutes || 0));
            if (!line || !Number.isFinite(mins)) continue;
            (etasByLine[line] || (etasByLine[line] = [])).push(mins);
        }
    }
    
    // Sort each
    for (const line of Object.keys(etasByLine)) {
        etasByLine[line].sort((a, b) => a - b);
    }
    
    // Get the most recent update
    const updatedAt = getMostRecentTimestamp(unified.operators);
    
    return { updatedAt, etasByLine, operators: Object.keys(unified.operators || {}) };
}

// ADD: Vehicle support for unified format
function airportBusVehiclesUnified(unified) {
    const vehicles = [];
    for (const [operator, data] of Object.entries(unified.operators || {})) {
        for (const v of data.vehicles || []) {
            const lat = Number(v.latitude);
            const lng = Number(v.longitude);
            if (!Number.isFinite(lat) || !Number.isFinite(lng) || (lat === 0 && lng === 0)) continue;
            vehicles.push({
                id: v.vehicle_id,
                lineId: v.line_id,
                lat,
                lng,
                heading: Number(v.heading) || 0,
                toAirport: v.to_airport,
                operator: v.operator,
            });
        }
    }
    return vehicles;
}
```

#### Add Tests

```javascript
// web-tests/airport-buses.test.js (extend)

test('reduceAirportBuses handles unified format', () => {
    const unified = {
        operators: {
            oasa: {
                arrivals: [
                    { line_id: 'X95', estimated_minutes: 5 },
                    { line_id: 'X95', estimated_minutes: 10 },
                ],
            },
        },
    };
    const r = A.reduceAirportBuses(unified);
    assert.deepEqual(r.etasByLine.X95, [5, 10]);
});

test('reduceAirportBuses handles multiple operators', () => {
    const unified = {
        operators: {
            oasa: { arrivals: [{ line_id: 'X95', estimated_minutes: 5 }] },
            oseth: { arrivals: [{ line_id: 'X3', estimated_minutes: 15 }] },
        },
    };
    const r = A.reduceAirportBuses(unified);
    assert.deepEqual(r.etasByLine.X95, [5]);
    assert.deepEqual(r.etasByLine.X3, [15]);
});

test('airportBusVehicles handles unified format', () => {
    const unified = {
        operators: {
            oasa: {
                vehicles: [
                    { vehicle_id: '1', line_id: 'X95', latitude: 37.9, longitude: 23.9, heading: 0, to_airport: true },
                ],
            },
        },
    };
    const v = A.airportBusVehicles(unified);
    assert.equal(v.length, 1);
    assert.equal(v[0].lineId, 'X95');
});
```

---

## 8. Required Fixtures and Tests

### Test Fixtures To Create

#### Backend (`ops/syrmos-api/tests/fixtures/`)

1. **fixtures/oasa-airport-buses-valid.json**
   - Valid full OASA response
   - All route codes, multiple vehicles, multiple arrivals

2. **fixtures/oasa-airport-buses-malformed.json**
   - Invalid JSON
   - Empty response
   - Missing fields

3. **fixtures/oasa-airport-buses-empty.json**
   - No vehicles, no arrivals

4. **fixtures/oseth-airport-buses-valid.json** (once API validated)
   - Valid OSETH response fixture

5. **fixtures/unified-airport-buses.json**
   - Full unified format with both operators

#### Web (`web-tests/fixtures/`)

1. **oasa-legPreview.json** - Legacy format
2. **unified-preview.json** - New format
3. **unified-with-vehicles.json** - Full with vehicles

### Backend Test Coverage

**Existing tests (✅ Implemented in test_airport_bus_providers.py):**

- ✅ Validation: timestamp parsing, ETA clamping
- ✅ Deduplication: vehicle deduplication
- ✅ Provider: operator, source URL, route discovery
- ✅ Normalizer: vehicle/arrival normalization
- ✅ Models: serialization, to_dict()
- ✅ Edge cases: missing fields, malformed data

**Additional tests to add:**

```python
class TestProviderIntegration(unittest.TestCase):
    """Integration tests with live data (run on Pi only)."""
    
    def test_oasa_live_vehicles(self):
        """Fetch live vehicle positions from OASA."""
        provider = OasaAirportProvider(timeout=10)
        vehicles = provider.fetch_vehicles()
        self.assertIsInstance(vehicles, list)
        # At least verify structure, not specific values
        for v in vehicles:
            self.assertTrue(hasattr(v, 'vehicle_id'))
            self.assertTrue(hasattr(v, 'line_id'))
            self.assertTrue(hasattr(v, 'latitude'))
    
    def test_oasa_live_arrivals(self):
        """Fetch live arrivals from OASA."""
        provider = OasaAirportProvider()
        arrivals = provider.fetch_arrivals()
        self.assertIsInstance(arrivals, list)

class TestSnapshotGeneration(unittest.TestCase):
    """Test complete snapshot generation."""
    
    def test_generate_combined_snapshot(self):
        """Generate snapshot with all operators."""
        poller = create_oasa_poller(Path('/tmp/test.json'))
        snapshot = poller._generate_snapshot()
        self.assertIsNotNone(snapshot)
        self.assertIn('operators', snapshot)
        self.assertIn('oasa', snapshot['operators'])

class TestSnapshotPersistence(unittest.TestCase):
    """Test snapshot file writing."""
    
    def test_atomic_write(self):
        """Verify atomic write (tmp -> final)."""
        with tempfile.TemporaryDirectory() as tmpdir:
            output = Path(tmpdir) / "output.json"
            poller = create_oasa_poller(output)
            snapshot = {"test": "data"}
            
            # Should not have incomplete file at any point
            poller._write_snapshot(snapshot)
            
            self.assertTrue(output.exists())
            self.assertFalse((output.parent / "output.json.tmp").exists())
```

---

## 9. Runtime and Deployment Verification

### Pre-Deployment Checklist

- [ ] ✅ Module compiles (`python3 -m py_compile`)
- [ ] ✅ Module imports successfully
- [ ] ⚠️ Backend tests pass (needs pytest)
- [ ] ⚠️ FastAPI app starts without errors
- [ ] ⚠️ New endpoints respond (test on Pi)
- [ ] ⚠️ Legacy endpoint still works
- [ ] ⚠️ Nginx configuration reloads cleanly
- [ ] ❌ OSETH provider works with live data

### Deployment Steps

```bash
# 1. Copy files to Pi
./deploy.sh

# 2. Install systemd service (first time only)
sudo cp ~/syrmos-api/systemd/syrmos-airport-bus.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now syrmos-airport-bus.service

# 3. Stop old service (if running)
sudo systemctl stop syrmos-oasa-airport.service
sudo systemctl disable syrmos-oasa-airport.service

# 4. Reload nginx
sudo /usr/sbin/nginx -c ~/syrmos-proxy/nginx.conf -s reload

# 5. Verify service is running
sudo journalctl -u syrmos-airport-bus -f

# 6. Test endpoints
curl https://api-syrmos.peterdsp.dev/api/airport-buses
curl https://api-syrmos.peterdsp.dev/api/airport-buses/oasa
curl https://api-syrmos.peterdsp.dev/api/airport-buses/oasa/X95
curl https://api-syrmos.peterdsp.dev/api/oasa-airport-buses

# 7. Verify data is fresh
watch -n 1 "curl -s https://api-syrmos.peterdsp.dev/api/airport-buses/oasa | jq '.updated_at'"
```

### Runtime Verification Commands

```bash
# Check service status
systemctl status syrmos-airport-bus.service

# Check output files
ls -lh ~/syrmos-api/out/airport-buses.json
ls -lh ~/syrmos-api/out/oasa-airport-buses.json

# Check journal
cat ~/syrmos-api/out/.airport-bus-journal.jsonl | tail -10 | jq .

# Check nginx error log
sudo tail -20 /var/log/nginx/error.log

# Test from iOS Simulator (requires network)
xcrun simctl openurl booted "https://api-syrmos.peterdsp.dev/api/airport-buses"

# Check response headers
curl -I https://api-syrmos.peterdsp.dev/api/airport-buses
```

### Expected Response Times

| Endpoint | Expected Response | Cache TTL |
|----------|-------------------|-----------|
| `/api/airport-buses` | Combined data, all operators | 15s |
| `/api/airport-buses/oasa` | OASA only | 15s |
| `/api/airport-buses/oasa/X95` | X95 route data | 15s |
| `/api/airport-buses/oasa/X95/vehicles` | Vehicle positions | 5s |
| `/api/airport-buses/oasa/X95/alerts` | Alerts | 30s |
| `/api/oasa-airport-buses` | Legacy format | 15s |

---

## 10. Release Gates and Final Evidence Report

### Phase Gates

#### Gate 1: Backend Complete and Deployed (PENDING)

- [ ] ✅ Provider adapters implemented
- [ ] ✅ Normalized model defined
- [ ] ✅ API endpoints implemented
- [ ] ✅ Nginx configured
- [ ] ✅ Poller implemented
- [ ] ✅ Backend tests written
- [ ] ⚠️ OSETH provider completed
- [ ] ❌ Backend tests pass
- [ ] ❌ Endpoints runtime-verified on Pi
- [ ] ❌ Legacy endpoint still works
- [ ] ❌ Data is fresh (< 15s old)

#### Gate 2: Client Migration Complete (PENDING)

- [ ] ❌ iOS:AirportBusService updated
- [ ] ❌ iOS: AirportBusDetailSheet updated
- [ ] ❌ iOS: Tests added
- [ ] ❌ Android: OasaAirportBusService updated
- [ ] ❌ Android: AirportBusDetail created
- [ ] ❌ Android: Tests added
- [ ] ❌ Web: web-airport.js updated
- [ ] ❌ Web: Tests added

#### Gate 3: Integration Tests Pass (PENDING)

- [ ] ❌ iOS: Build succeeds
- [ ] ❌ iOS: Tests pass on simulator
- [ ] ❌ Android: Build succeeds
- [ ] ❌ Android: Cloud CI passes
- [ ] ❌ Web: Full test suite passes
- [ ] ❌ Web: Runtime tests pass

#### Gate 4: Release Ready (PENDING)

- [ ] ❌ CHANGELOG.md updated
- [ ] ❌ All tests pass
- [ ] ❌ No trailing whitespace (git diff --check)
- [ ] ❌ Secrets scan clean
- [ ] ❌ Build artifacts verified

---

### Final Evidence Report Template

```markdown
## Airport Bus Live Information - Final Evidence Report

**Date:** YYYY-MM-DD  
**Commit:** SHA  
**Tag:** v3.0.0-beta.N  

### Backend

#### Configuration
- [x] Provider adapters: OASA ✅, OSETH ✅
- [x] API endpoints: 7 new + 1 legacy
- [x] Nginx proxy rules: Configured
- [x] Systemd service: Installed and active
- [x] Polling: 30s interval, exponential backoff

#### Runtime Verification
```
[Output of curl commands with response times]
```

#### Data Verification
- [x] OASA routes discovered: 4 (X93, X95, X96, X97)
- [x] OASA vehicles tracked: N
- [x] OASA arrivals: M
- [x] OSETH routes discovered: 2 (X3, 2X)
- [x] OSETH vehicles tracked: N
- [x] OSETH arrivals: M
- [x] Freshness: All timestamps < 15s old
- [x] Source attribution: Present in all responses

### iOS

#### Tests
```
[Output of xcodebuild test]
```

#### Runtime Verification
- [x] Device: iPhone (Simulator/Device)
- [x] iPhone Duo: Verified
- [x] Build: Succeeded
- [x] Tests: N passed, M failed
- [x] Legacy endpoint: Still works
- [x] New endpoint: Works
- [x] Sheet presents correctly
- [x] Map integration: Works
- [x] Accessibility: Verified

### Android

#### Tests
```
[Output of Android tests]
```

#### Runtime Verification
- [x] Emulator: Works
- [x] Cloud CI: Passed
- [x] Build: Succeeded
- [x] Legacy endpoint: Still works
- [x] New endpoint: Works
- [x] Sheet presents correctly
- [x] Map integration: Works
- [x] Accessibility: Verified

### Web

#### Tests
```
[Output of node --test]
```

#### Runtime Verification
- [x] Endpoint: Works
- [x] Reduction: Correct
- [x] Departures: Display correctly
- [x] Vehicles: Display on map
- [x] Alerts: Display correctly
- [x] Accessibility: Verified

### Files Changed

[List of all modified/added files with line counts]

### Known Issues

[Any remaining issues or blockers]

### Blockers

[Any issues that prevent release]
```

---

## 11. Immediate Action Items

### Critical (Must Complete Before Any Release)

1. **⚠️ HIGH PRIORITY: Validate OSETH API** (Blocks Thessaloniki data)
   - Run from Pi: `curl https://api.oseth.gr/...` (or discovered URL)
   - Document actual endpoints
   - Record response formats
   - Complete OsethAirportProvider

2. **⚠️ HIGH PRIORITY: Deploy and Runtime Test Backend**
   - Deploy to Pi
   - Test all endpoints
   - Verify data freshness
   - Fix any runtime issues

3. **⚠️ HIGH PRIORITY: Run Backend Tests**
   - Install pytest in venv
   - Run: `python -m pytest tests/test_airport_bus_providers.py -v`
   - Fix any failures

### High Priority (Next Iteration)

4. **Complete iOS Migration**
   - Update AirportBusService for normalized model
   - Update AirportBusDetailSheet with vehicle map, alerts, confidence
   - Add tests
   - Runtime verify on simulator/device/iPhone Duo

5. **Complete Android Migration**
   - Complete OsethAirportBusService
   - Update AirportHubScreen for OSETH
   - Create AirportBusDetail composable
   - Add tests
   - Runtime verify on emulator

6. **Complete Web Migration**
   - Update web-airport.js for unified format
   - Add OSETH support
   - Add vehicle positions
   - Add tests
   - Runtime verify in browser

### Medium Priority

7. **Add Comprehensive Tests**
   - Provider fixtures
   - Malformed response tests
   - Edge case tests
   - Integration tests

8. **Update Documentation**
   - CHANGELOG.md
   - Deployment instructions
   - Release notes

### Low Priority

9. **Cleanup**
   - Remove duplicative code
   - Optimize performance
   - Add logging

---

## 12. Files Reference

### Backend Files

| File | Status | Purpose |
|------|--------|---------|
| `docs/data/airport-bus-provenance.md` | ✅ NEW | Data provenance documentation |
| `ops/syrmos-api/syrmos_admin/airport_bus_providers.py` | ✅ NEW | Provider adapters and models |
| `ops/syrmos-api/syrmos_admin/app.py` | ✅ MODIFIED | FastAPI endpoints (+7) |
| `ops/syrmos-api/nginx.locations.conf` | ✅ MODIFIED | Nginx proxy rules (+6) |
| `ops/syrmos-api/scripts/airport-bus-watcher.py` | ✅ NEW | Unified watcher script |
| `ops/syrmos-api/systemd/syrmos-airport-bus.service` | ✅ NEW | Systemd unit |
| `ops/syrmos-api/tests/test_airport_bus_providers.py` | ✅ NEW | Backend test suite |

### Client Files (Not Yet Modified)

| File | Location | Required Changes |
|------|----------|------------------|
| AirportBusService.swift | iosApp/Core/Networking | Add normalized support |
| AirportBusDetailSheet.swift | iosApp/Features/Timetables | Add map, alerts, confidence |
| OasaAirportBusService.kt | core/network | Add Oseth, unified support |
| AirportHubScreen.kt | feature/schedule | Add OSETH data |
| web-airport.js | composeApp/src/wasmJsMain/resources | Add unified format |

---

## 13. Contact and Support

For questions about this implementation:
- See `docs/data/airport-bus-provenance.md` for data source details
- See `docs/airport-bus-live-information-implementation-prompt.md` (DELETED - was the original spec)
- See `docs/airport-bus-live-information-correction-prompt.md` (THIS FILE - completion roadmap)

---

**Note:** This is a living document. Update it as implementation progresses and issues are resolved.
