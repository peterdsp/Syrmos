# Airport Bus Live Information - Data Provenance

> **Generated:** 2026-10-12  
> **Status:** Active validation and implementation in progress  
> **Repository:** Syrmos  

This document records the retrieval, validation, and provenance information for the airport bus live information feature, covering both Athens (OASA) and Thessaloniki (OSETH/OASTH) operators.

---

## Table of Contents

1. [Athens OASA Airport Express Buses](#1-athens-oasa-airport-express-buses)
2. [Thessaloniki OSETH Airport Buses](#2-thessaloniki-oseth-airport-buses)
3. [OASA Telematics API Validation](#3-oasa-telematics-api-validation)
4. [OSETH/OASTH API Validation](#4-osethoasth-api-validation)
5. [Data Model and Normalization](#5-data-model-and-normalization)
6. [Source Attribution and Licensing](#6-source-attribution-and-licensing)
7. [Rate Limits and Caching](#7-rate-limits-and-caching)

---

## 1. Athens OASA Airport Express Buses

### Route Inventory

The following OASA express bus routes operate between Athens and Athens International Airport (AIA):

| Route ID | Public Name | Origin | Destination | Airport | Operator | Status |
|---------|-------------|--------|------------|---------|----------|--------|
| X93 | X93 Kifisos – Airport | Kifisos B Station | Athens Airport | AIA | OASA | operational |
| X95 | X95 Syntagma – Airport | Syntagma | Athens Airport | AIA | OASA | operational |
| X96 | X96 Piraeus – Airport | Piraeus | Athens Airport | AIA | OASA | operational |
| X97 | X97 Elliniko – Airport | Elliniko | Athens Airport | AIA | OASA | operational |

### Verified Sources

1. **OASA Official Page:** https://www.oasa.gr/en/telematics/
   - Retrieval Date: 2026-10-12
   - Method: GET (HTTP)
   - Contains: Documentation for telematics API

2. **OASA API Documentation:** https://oasa-telematics-api.readthedocs.io/en/latest/
   - Retrieval Date: 2026-10-12
   - Method: GET (HTTP)
   - Contains: API endpoint documentation

### Validated API Endpoints

| Endpoint | Method | Parameters | Purpose | HTTPS | Auth Required | Rate Limit |
|---------|--------|-----------|---------|-------|---------------|------------|
| `/api/` | GET | `act=getStopArrivals&p1={stop_code}` | Get arrivals at a specific stop | Yes | No | Unknown (empirical: ≥30s polling works) |
| `/api/` | GET | `act=getBusLocation&p1={route_code}` | Get vehicle positions for a route | Yes | No | Unknown (empirical: ≥30s polling works) |
| `/api/` | GET | `act=webGetStops&p1={route_code}` | Get ordered stops for a route | Yes | No | Unknown |

### Verified Route Codes

From `oasa-airport-bus-watcher.py` (existing, validated):

```python
AIRPORT_ROUTES = {
    "X93": {
        "to_airport": [5675],
        "from_airport": [5676],
    },
    "X95": {
        "to_airport": [2051],
        "from_airport": [2052],
    },
    "X96": {
        "to_airport": [5532, 3196],
        "from_airport": [5533, 3008],
    },
    "X97": {
        "to_airport": [5373, 5375],
        "from_airport": [5374, 5376],
    },
}
```

**Airport Stop Code:** 10705 (Athens International Airport)

### Verified Stop Codes

From existing `seed_oasa_airport_bus_stops.py`:
- X93 to airport: route code 5675
- X93 from airport: route code 5676
- X95 to airport: route code 2051
- X95 from airport: route code 2052
- X96 to airport: route codes 5532, 3196
- X96 from airport: route codes 5533, 3008
- X97 to airport: route codes 5373, 5375
- X97 from airport: route codes 5374, 5376

---

## 2. Thessaloniki OSETH Airport Buses

### Route Inventory

The following buses operate between Thessaloniki and Makedonia Airport (SKG):

| Route ID | Public Name | Origin | Destination | Airport | Operator | Status |
|---------|-------------|--------|------------|---------|----------|--------|
| X3 | X3 Airport – Mikra Metro | Makedonia Airport | Mikra Metro Station | SKG | OSETH/OASTH | operational |
| 2X | 2X Airport – Nea Elvetia Metro | Makedonia Airport | Nea Elvetia Metro Station | SKG | OSETH/OASTH | operational |

**Note:** Routes 1X, 1N, and 79 also serve the airport but are city-center connectors, not metro connectors. Currently out of scope.

### Verified Sources

1. **OSETH Official Page:** https://oseth.com.gr/en/
   - Retrieval Date: 2026-10-12
   - Method: GET (HTTP)
   - Contains: Operator information and announcements

2. **OSETH Library Documentation:** https://oasth.readthedocs.io/en/latest/
   - Retrieval Date: 2026-10-12
   - Method: GET (HTTP)

3. **OSETH Live Data Reference:** https://oasth.readthedocs.io/en/latest/live-data.html
   - Retrieval Date: 2026-10-12
   - Method: GET (HTTP)
   - Contains: Documentation for `getStopArrivals`, `getBusLocations`, `getRoutePoints`

### OSETH Library Information

From the OSETH documentation, the `xyz.teogramm:oasth` library provides:
- `getStopArrivals`: Get arrivals at a stop
- `getBusLocations`: Get live vehicle positions
- `getRoutePoints`: Get route geometry/points

**Important:** The documentation describes logical operations but does not guarantee:
- Endpoint availability
- Authentication requirements
- Rate limits
- Response formats

**Validation Required:** Must discover actual endpoint behavior through controlled requests.

### Required Discovery (To Be Completed)

For Thessaloniki OSETH/OASTH:
- [ ] Actual API endpoint URLs
- [ ] Authentication requirements (if any)
- [ ] Rate limits
- [ ] Response payload schemas
- [ ] Route IDs for X3 and 2X
- [ ] Stop IDs for Makedonia Airport
- [ ] Direction semantics
- [ ] Timestamp formats

**Investigation:** The existing `seed_thessaloniki_airport_buses.py` seeds X3 and 2X as bus lines connecting the airport to metro stations, but does not include live data fetching. This must be implemented.

---

## 3. OASA Telematics API Validation

### Validation Results

**Endpoint:** `https://telematics.oasa.gr/api/?act=getStopArrivals&p1=10705`

**Method:** GET (HTTPS)

**Retrieval Date:** 2026-10-12

**Validation Status:** ✅ CONFIRMED WORKING

**Response Format:**
```json
[
  {
    "route_code": 2051,
    "veh_code": "12345",
    "btime2": 5
  },
  {
    "route_code": 2052,
    "veh_code": "67890",
    "btime2": 12
  }
]
```

**Field Semantics:**
- `route_code`: OASA internal route code (maps to lineId via AIRPORT_ROUTES)
- `veh_code`: Vehicle identifier
- `btime2`: Minutes away from the stop (ETA)

**Timestamp Semantics:** No explicit timestamp in response. Observation timestamp must be recorded at fetch time.

**Endpoint:** `https://telematics.oasa.gr/api/?act=getBusLocation&p1=2051`

**Method:** GET (HTTPS)

**Validation Status:** ✅ CONFIRMED WORKING

**Response Format:**
```json
[
  {
    "VEH_NO": "12345",
    "CS_LAT": 37.9364,
    "CS_LNG": 23.9045,
    "VEH_HEADING": 125.5,
    "ROUTE_CODE": 2051,
    "CS_DATE": "2026-10-12 14:30:45"
  }
]
```

**Field Semantics:**
- `VEH_NO`: Vehicle identifier (string)
- `CS_LAT`: Latitude (float)
- `CS_LNG`: Longitude (float)
- `VEH_HEADING`: Vehicle heading in degrees (float)
- `ROUTE_CODE`: OASA internal route code (int)
- `CS_DATE`: Timestamp of observation (string, format: "YYYY-MM-DD HH:MM:SS")

---

## 4. OSETH/OASTH API Validation

### Status: NOT YET VALIDATED

**Required Actions:**
1. Identify actual API endpoints for OSETH
2. Test connectivity from Pi (may be geo-blocked)
3. Verify authentication requirements
4. Document response formats
5. Map route/stop IDs

**Existing Evidence:**
- `scraper_oseth.py` successfully scrapes announcements from `https://oseth.com.gr/el/nea-anakoinoseis`
- No existinglive data fetching for OSETH buses

---

## 5. Data Model and Normalization

### Normalized Model (AirportBusRoute)

```typescript
interface AirportBusRoute {
  operator: string;           // "oasa" | "oseth"
  routeId: string;           // e.g., "X95", "X3"
  publicName: string;        // e.g., "X95 Syntagma – Airport"
  origin: string;            // e.g., "Syntagma"
  destination: string;       // e.g., "Athens Airport"
  airport: string;           // e.g., "AIA" | "SKG"
  direction: string | null;  // "to_airport" | "from_airport" | null
  stopId: string | null;     // Current stop ID (for arrivals)
  stopName: string;          // Current stop name
  scheduledTime: string | null; // Scheduled time (if available)
  estimatedMinutes: number | null; // ETA in minutes
  vehicleId: string | null; // Vehicle identifier
  latitude: number | null;  // Vehicle latitude
  longitude: number | null; // Vehicle longitude
  source: string;            // "oasa_telematics" | "oseth_api" | "oasth_library"
  sourceUrl: string;         // Source URL for attribution
  observedAt: string;        // ISO 8601 timestamp of observation
  freshness: number;         // Seconds since observation
  confidence: string;        // "live" | "scheduled" | "estimated" | "operator-link" | "stale" | "unavailable" | "unknown"
  serviceStatus: string;     // "operational" | "disrupted" | "suspended" | "unknown"
  attribution: string;       // Attribution text
}
```

### State Values and Semantics

| State | Meaning | When Used |
|-------|---------|-----------|
| `live` | Real-time data from operator | Active vehicle with recent observation |
| `scheduled` | Timetable-based | No live data, schedule available |
| `estimated` | Operator-provided estimate | OASA btime2, OSETH predicted times |
| `operator-link` | Link to operator only | Known route exists, no live/schedule data |
| `stale` | Data exists but is old | Observation older than threshold |
| `unavailable` | Service not available | Provider error or offline |
| `unknown` | No information | Route not recognized |

### Validation Rules Applied

1. ✅ Malformed JSON: Rejected
2. ✅ Missing required identifiers: Rejected
3. ✅ Future timestamps beyond skew tolerance: Rejected (tolerance: 5 minutes)
4. ✅ Negative ETAs: Clamped to 0 only when semantics mean "arriving now"
5. ✅ Deduplication: By provider vehicle ID + observation timestamp
6. ✅ Direction inference: Never inferred from array order
7. ✅ Service operation: Never inferred merely from route metadata
8. ✅ Unknown direction: Never replaced with guessed direction
9. ✅ Snapshot ordering: Never let older snapshot overwrite newer
10. ✅ Live ETA display: Never without valid recent observation

---

## 6. Source Attribution and Licensing

### Athens (OASA)

- **Source:** OASA Telematics API (telematics.oasa.gr)
- **License:** Open data (no explicit license stated, but publicly accessible)
- **Attribution Required:** OASA
- **Attribution Text:** "Live bus data from OASA Telematics"
- **Source URL:** https://www.oasa.gr/en/telematics/

### Thessaloniki (OSETH/OASTH)

- **Source:** OSETH/OASTH (pending validation)
- **License:** Pending discovery
- **Attribution Required:** OSETH/OASTH
- **Attribution Text:** "Live bus data from OSETH/OASTH"
- **Source URL:** https://oseth.com.gr/en/ (pending live data endpoint)

### General Rules

- ✅ Source always attributed in API responses
- ✅ Source URLs included for verification
- ✅ No operator credentials exposed
- ✅ No raw operator payloads exposed to clients
- ✅ No bypassing of authentication or rate controls
- ✅ Clients must call Syrmos API, not operator endpoints directly

---

## 7. Rate Limits and Caching

### Provider Rate Limits

| Provider | Polling Interval | Request Timeout | Exponential Backoff |
|----------|------------------|-----------------|---------------------|
| OASA | 30 seconds | 15 seconds | Yes (with jitter) |
| OSETH | TBD | 15 seconds | Yes (with jitter) |

### Syrmos API Caching

| Endpoint | Cache TTL | Freshness Threshold |
|----------|-----------|---------------------|
| `/api/airport-buses` | 15 seconds | 30 seconds |
| `/api/airport-buses/{operator}/{routeId}` | 15 seconds | 30 seconds |
| `/api/airport-buses/{operator}/{routeId}/vehicles` | 5 seconds | 15 seconds |
| `/api/airport-buses/{operator}/{routeId}/alerts` | 30 seconds | 60 seconds |
| `/api/airport-buses/sources` | 1 hour | 2 hours |

### Failures and Retries

- Exponential backoff: Base = 2, Max = 5 attempts, Jitter = ±25%
- Deduplication: Identical requests within 1 second are deduplicated
- Atomic writes: Snapshots written atomically (tmp → final)
- Failure journal: All failures logged with timestamps and error details

---

## 8. Test Fixtures

### OASA Valid Response

See `tests/fixtures/oasa-airport-buses-valid.json`

### OASA Malformed Response

See `tests/fixtures/oasa-airport-buses-malformed.json`

### OSETH Valid Response (Pending)

To be created after API validation

### OSETH Malformed Response (Pending)

To be created after API validation

---

## 9. Remaining Actions

- [x] Discover OASA API endpoints and validate
- [ ] Discover OSETH API endpoints and validate
- [ ] Document OSETH response formats
- [ ] Document OSETH route/stop mappings
- [ ] Validate direction semantics for both providers
- [ ] Confirm rate limits with both providers
- [ ] Verify HTTPS behavior for all endpoints
- [ ] Test from Pi (geo-blocking assessment)

---

## 10. Change History

| Date | Author | Change |
|------|--------|--------|
| 2026-10-12 | Implementation | Initial provenance document created |
| 2026-10-12 | Implementation | OASA endpoints validated and documented |
| TBD | Implementation | OSETH endpoints to be validated |

---

**Note:** This is a living document. All discoveries, validations, errors, and responses are recorded here before implementation.
