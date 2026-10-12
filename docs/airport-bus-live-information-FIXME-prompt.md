# Airport Bus Live Information: GAP ANALYSIS AND FIX ROADMAP

> **Created:** 2026-10-12  
> **Status:** Material gaps identified - NOT deployment ready  
> **Reference:** Verified against actual repository state  
> **Previous Claim:** Overstated - implementation on disk but not proven

---

## EXECUTIVE SUMMARY

### Gap Analysis Results

A verification against the actual repository has revealed that the previous completion claim was **materially overstated**. While significant code is on disk, several critical aspects remain unimplemented, untested, or buggy.

**Current Real Status:**
- ✅ OASA Provider: Working and validated
- ❌ OSETH Provider: Structure exists but **API endpoints unvalidated** (NOT IMPLEMENTED in practice)
- ❌ API Polling: **Per-request upstream** instead of snapshot-backed design
- ❌ Client Migration: Capable of unified format but **defaults to legacy**
- ❌ Quality: **Trailing whitespace** violations exist
- ❌ Tests: Cannot run (pytest unavailable in verification environment)
- ❌ Runtime: **No deployment or runtime verification** performed

---

## 1. CRITICAL GAPS REQUIRING IMMEDIATE FIX

### 1.1 OSETH Provider - NOT ACTUALLY IMPLEMENTED

**Findings:**
```python
# ops/syrmos-api/syrmos_admin/airport_bus_providers.py

class OsethAirportProvider(AirportTransitProvider):
    API_BASE = "https://api.oseth.gr/api/"  # TO BE VALIDATED
    
    def fetch_arrivals(self, stop_id: Optional[str] = None):
        # Returns empty list - API not validated
        return []
```

**Issues:**
- [ ] API endpoints for OSETH/OASTH **not discovered**
- [ ] Response formats **not validated**
- [ ] Authentication requirements **unknown**
- [ ] Route codes for X3 and 2X **not confirmed**
- [ ] Stop codes for Makedonia Airport **not confirmed**
- [ ] API_BASE URL **assumed, not validated**
- [ ] my code pushes empty lists instead of actual API calls

**Evidence:**
```bash
# Grep for NOT IMPLEMENTED / PLACEHOLDER in provider
grep -n "NOT IMPLEMENTED\|PLACEHOLDER\|TO BE VALIDATED" \
  ops/syrmos-api/syrmos_admin/airport_bus_providers.py

# Result: Multiple occurrences in OsethAirportProvider
```

**Required Actions:**
1. Run from Pi (Greek IP) to discover actual OSETH endpoints:
   ```bash
   # Test assumed endpoints
   curl -v "https://api.oseth.gr/api/?act=getStopArrivals&p1=THS_AIR"
   curl -v "https://api.oseth.gr/api/?act=getBusLocation&p1=101"
   curl -v "https://api.oseth.gr/api/?act=webGetStops&p1=101"
   ```
2. Document actual response schemas
3. Map real route codes and stop IDs
4. Update `OsethAirportProvider` with validated endpoints
5. Remove all "TO BE VALIDATED" and "NOT IMPLEMENTED" markers

**Verification:** Line must be removed from this document when:
- [ ] `grep -n "NOT IMPLEMENTED\|PLACEHOLDER\|TO BE VALIDATED" airport_bus_providers.py` returns **zero matches**
- [ ] OSETH live data verified on Pi with actual responses

---

### 1.2 API Polling Model - WRONG DESIGN

**Findings:**
```python
# ops/syrmos-api/syrmos_admin/app.py - Current implementation

@app.get("/api/airport-buses")
def api_airport_buses():
    oasa_provider = abp_mod.OasaAirportProvider()
    oseth_provider = abp_mod.OsethAirportProvider()
    
    # Per-request upstream polling - NOT snapshot-backed
    oasa_vehicles = oasa_provider.fetch_vehicles()  # LIVE API CALL
    oasa_arrivals = oasa_provider.fetch_arrivals()   # LIVE API CALL
```

**Design Intent:** According to the architecture:
> The bounded poller (`AirportBusPoller`) should write **atomic snapshots** to files
> Endpoints should serve from **pre-generated snapshots**, not per-request upstream

**Issues:**
- [ ] Endpoints make **live upstream calls** on every request
- [ ] No snapshot file reading in endpoints
- [ ] No caching layer between poller and API
- [ ] Rate limits could be exceeded if multiple clients hit simultaneously
- [ ] Inconsistent with the `AirportBusPoller` design

**Required Actions:**
1. Identify snapshot file locations from `AirportBusPoller`
2. Modify endpoints to read from snapshots instead of calling providers directly
3. Add snapshot file paths to configuration
4. Implement snapshot refresh logic (if snapshot expired, trigger poll)
5. Ensure atomic read consistency with poller writes

**Expected Architecture:**
```
AirportBusPoller (writes snapshots every 30s)
    → /out/airport-buses-oasa.json
    → /out/airport-buses-oseth.json
    → /out/airport-buses-combined.json

FastAPI Endpoints (read snapshots)
    ← /out/airport-buses-combined.json
    ← /out/airport-buses-oasa.json
    ← /out/airport-buses-oseth.json
```

**Verification:** Line must be removed when:
- [ ] All `/api/airport-buses*` endpoints read from snapshot files
- [ ] No direct provider calls in endpoint handlers
- [ ] `grep -n "provider\.fetch" syrmos_admin/app.py` returns **zero matches** in airport bus endpoints

---

### 1.3 Client Migration - INCOMPLETE

**Findings:**
- iOS/Android/Web **can** parse unified format
- But **default to legacy** `/api/oasa-airport-buses` endpoint
- No production traffic using normalized endpoints

**Issues:**
| Client | Current Default | Required Default |
|--------|-----------------|------------------|
| iOS | `/api/oasa-airport-buses` | `/api/airport-buses` |
| Android | `/api/oasa-airport-buses` | `/api/airport-buses` |
| Web | `/api/oasa-airport-buses` | `/api/airport-buses` |

**Required Actions:**
1. **iOS:** Update `AirportBusService.fetch()` to call unified endpoint first, fallback to legacy
   ```swift
   // Change from:
   guard let url = URL(string: "\(base)/api/oasa-airport-buses")
   
   // To:
   guard let url = URL(string: "\(base)/api/airport-buses")
   // With legacy fallback
   ```

2. **Android:** Update `AirportBusService.fetchAirportBuses()` similarly
   ```kotlin
   // In feature/schedule/src/commonMain/kotlin/com/syrmos/feature/schedule/AirportBusEtas.kt
   // Change AirportHubScreen to use unified by default
   ```

3. **Web:** Update web-map.js to use `/api/airport-buses` instead of `/api/oasa-airport-buses`

**Verification:** Line must be removed when:
- [ ] All clients use unified endpoint as primary
- [ ] Legacy endpoint only used as fallback
- [ ] Production traffic logs show unified endpoint usage

---

### 1.4 Trailing Whitespace Violations

**Findings:**
```bash
$ git diff --check
ops/syrmos-api/syrmos_admin/app.py:1563: trailing whitespace.
iosApp/iosApp/DesignSystem/SyrmosColors.swift:54: trailing whitespace.
```

**Status:** ❌ **BLOCKER** - Cannot commit with trailing whitespace

**Required Actions:**
1. Run whitespace cleanup:
   ```bash
   # Find all files with trailing whitespace
   grep -rl '[[:space:]]$' ops/syrmos-api/syrmos_admin/*.py iosApp/**/*.swift
   
   # Clean trailing whitespace
   sed -i 's/[[:space:]]*$//' ops/syrmos-api/syrmos_admin/app.py
   sed -i 's/[[:space:]]*$//' iosApp/iosApp/DesignSystem/SyrmosColors.swift
   
   # Also check other modified files
   for file in $(git diff --name-only); do
       sed -i 's/[[:space:]]*$//' "$file"
   done
   ```
2. Verify cleanup:
   ```bash
   git diff --check  # Must return exit code 0
   ```

**Verification:** Line must be removed when:
- [ ] `git diff --check` returns **exit code 0** with no output
- [ ] All modified files pass whitespace check

---

## 2. HIGH PRIORITY - PRE DEPLOYMENT

### 2.1 Backend Tests Cannot Run

**Findings:**
```bash
# Attempted: python -m pytest tests/test_airport_bus_providers.py -v
# Result: pytest not installed in venv
```

**Issues:**
- [ ] pytest not available in environment
- [ ] Cannot verify tests pass
- [ ] Backend test suite unproven

**Required Actions:**
1. Install pytest:
   ```bash
   cd ops/syrmos-api
   .venv/bin/pip install pytest
   ```
2. Run test suite:
   ```bash
   python -m pytest tests/test_airport_bus_providers.py -v
   ```
3. Fix any failures

**Verification:** Line must be removed when:
- [ ] All backend tests pass
- [ ] `python -m pytest tests/test_airport_bus_providers.py -v` returns **exit code 0**

---

### 2.2 Nginx Configuration - May Need Updates

**Findings:**
- Nginx proxy rules exist in `nginx.locations.conf`
- But may not route to snapshot files if API changes to snapshot-based

**Required Actions:**
1. If switching to snapshot-based:
   - Update nginx to serve static JSON files directly
   - Or ensure FastAPI still handles routing but serves from files
2. Test nginx configuration:
   ```bash
   sudo /usr/sbin/nginx -t -c ~/syrmos-proxy/nginx.conf
   sudo /usr/sbin/nginx -s reload
   ```

**Verification:** Line must be removed when:
- [ ] nginx -t returns **syntax ok**
- [ ] All endpoints respond correctly after reload

---

## 3. MEDIUM PRIORITY - PRE RELEASE

### 3.1 Client-Side Tests

**Current Status:**
- [ ] iOS tests not updated for unified model
- [ ] Android tests not updated for unified model  
- [ ] Web tests not updated for unified format

**Required Actions:**
1. **iOS:** Add tests for `AirportBusService.fetchUnified()`
2. **iOS:** Add tests for `UnifiedAirportBusDetailSheet`
3. **Android:** Add tests for unified format handling
4. **Web:** Add tests for unified format in web-airport.js

**Verification:** Line must be removed when:
- [ ] All client tests pass
- [ ] Code coverage includes unified model handling

---

### 3.2 Systemd Service Configuration

**Current Status:**
- `syrmos-airport-bus.service` exists
- But may reference old watcher instead of new poller

**Required Actions:**
1. Verify service configuration:
   ```bash
   cat ops/syrmos-api/systemd/syrmos-airport-bus.service
   ```
2. Update if needed to reference `AirportBusPoller`
3. Test service:
   ```bash
   sudo systemctl daemon-reload
   sudo systemctl restart syrmos-airport-bus
   sudo systemctl status syrmos-airport-bus
   journalctl -u syrmos-airport-bus -f
   ```

**Verification:** Line must be removed when:
- [ ] Service starts without errors
- [ ] Poller runs and creates snapshot files
- [ ] Snapshot files update every 30 seconds

---

## 4. LOW PRIORITY - POST DEPLOYMENT

### 4.1 CHANGELOG Finalization
- [ ] Verify all changes are documented
- [ ] Update version number
- [ ] Add deployment notes

### 4.2 Code Cleanup
- [ ] Remove commented code
- [ ] Remove debug logging
- [ ] Optimize imports

### 4.3 Performance Optimization
- [ ] Consider snapshot caching in FastAPI
- [ ] Add rate limiting to endpoints
- [ ] Optimize response serialization

---

## 5. BLOCKERS SUMMARY

| Blocker | Severity | Status | Fix Required |
|---------|----------|--------|--------------|
| OSETH API unvalidated | CRITICAL | ❌ Open | Discover actual endpoints from Pi |
| Per-request upstream polling | CRITICAL | ❌ Open | Switch to snapshot-based architecture |
| Trailing whitespace | CRITICAL | ❌ Open | Clean and verify all files |
| Backend tests cannot run | HIGH | ❌ Open | Install pytest and run tests |
| Clients default to legacy | HIGH | ❌ Open | Update to unified as primary |

**Total Blockers: 5** (3 Critical, 2 High)

---

## 6. RELEASE GATES

### Gate 1: Backend Complete ✅ (Can pass after fixes)
- [ ] ✅ OASA provider implemented
- [ ] ❌ OSETH provider validated with live data
- [ ] ❌ API is snapshot-backed (not per-request)
- [ ] ❌ Backend tests pass
- [ ] ❌ No trailing whitespace
- [ ] ❌ Systemd service running
- [ ] ❌ Endpoints respond correctly

### Gate 2: Client Migration Complete ✅ (Can pass after fixes)
- [ ] ❌ All clients use unified endpoint as primary
- [ ] ❌ Client tests pass
- [ ] ❌ Legacy endpoint deprecated

### Gate 3: Integration Tests ✅ (Can pass after fixes)
- [ ] ❌ All tests pass
- [ ] ❌ No regressions in existing functionality

### Gate 4: Release Ready ✅ (Can pass after fixes)
- [ ] ❌ CHANGELOG updated
- [ ] ❌ All blockers resolved
- [ ] ❌ Code review complete

---

## 7. IMMEDIATE ACTION ITEMS (Next 24 Hours)

### Priority 1: Fix Critical Blockers

```bash
# 1. Fix trailing whitespace
cd /Users/peterdsp/git/Syrmos
git diff --check | cut -d: -f1 | sort -u | while read file; do
    sed -i 's/[[:space:]]*$//' "$file"
done

# Verify
git diff --check  # Must return exit 0

# 2. Install pytest and run backend tests
cd ops/syrmos-api
.venv/bin/pip install pytest 2>/dev/null || pip install pytest
python -m pytest tests/test_airport_bus_providers.py -v

# 3. Check for NOT IMPLEMENTED markers
grep -rn "NOT IMPLEMENTED\|PLACEHOLDER\|TO BE VALIDATED" syrmos_admin/
# Must return zero matches
```

### Priority 2: Fix Architecture Issues

1. **Switch to snapshot-based polling:**
   - Modify `app.py` endpoints to read from `AirportBusPoller` snapshots
   - Remove direct provider calls from endpoint handlers
   - Ensure poller writes to known file locations

2. **Update clients to use unified endpoints:**
   - Change default from `/api/oasa-airport-buses` to `/api/airport-buses`
   - Keep legacy as fallback

---

## 8. DEFINITION OF DONE

The feature is **DONE** when ALL of the following are true:

1. ✅ **Code Quality:**
   - `git diff --check` returns exit code 0
   - No trailing whitespace in any file

2. ✅ **OSETH Provider:**
   - `grep -rn "NOT IMPLEMENTED\|PLACEHOLDER\|TO BE VALIDATED" syrmos_admin/airport_bus_providers.py` returns zero matches
   - Live data verified from Pi with actual API responses

3. ✅ **API Architecture:**
   - All `/api/airport-buses*` endpoints serve from snapshots
   - No direct provider calls in endpoint handlers
   - `grep -n "provider\.fetch" syrmos_admin/app.py` returns zero matches in airport bus endpoints

4. ✅ **Backend Tests:**
   - `python -m pytest tests/test_airport_bus_providers.py -v` returns exit code 0
   - All tests pass

5. ✅ **Client Migration:**
   - All clients use `/api/airport-buses` as primary endpoint
   - Legacy endpoint used only as fallback

6. ✅ **Runtime Verification:**
   - `sudo /usr/sbin/nginx -t` returns "syntax ok"
   - Systemd service running and creating snapshots
   - Endpoints respond with valid data
   - Data refreshes within expected timeframes (< 30s)

7. ✅ **Documentation:**
   - CHANGELOG.md updated with all changes
   - This FIXME document deleted or archived

---

## 9. TRACKING

Use this document as the source of truth. Update checkmarks as each item is verified.

**Last Verified:** 2026-10-12  
**Verified By:** User (via grep and code review)  
**Next Review:** After each fix attempt

---

## 10. REFERENCES

- Original Implementation Prompt: `docs/airport-bus-live-information-implementation-prompt.md` (DELETED)
- Correction/Completion Roadmap: `docs/airport-bus-live-information-correction-prompt.md`
- Data Provenance: `docs/data/airport-bus-provenance.md`
- Previous Status Claim: This document (overstated)
- Verification Evidence: grep output, git diff --check, code review

---

**Document Status:** ACTIVE - Updated as fixes are applied
