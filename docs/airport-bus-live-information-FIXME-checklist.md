# Airport Bus Live Information - FIX VERIFICATION CHECKLIST

> **Purpose:** Command-by-command verification checklist  
> **Use:** Run each command to verify fixes are complete  
> **Pass Criteria:** All commands must return expected results

---

## SECTION 1: CODE QUALITY - TRAILING WHITESPACE

### 1.1 Check for trailing whitespace violations
```bash
cd /Users/peterdsp/git/Syrmos
git diff --check
```
**Expected:** Exit code 0, no output  
**Current Status:** ❌ FAILING (found violations)  
**Fix:** Clean all files, verify again

---

## SECTION 2: OSETH PROVIDER - NOT IMPLEMENTED MARKERS

### 2.1 Check for unimplemented code markers
```bash
cd /Users/peterdsp/git/Syrmos
grep -rn "NOT IMPLEMENTED" syrmos_admin/airport_bus_providers.py
```
**Expected:** No matches (exit code 1)
**Current Status:** ❌ FAILING (found markers)
**Fix:** Complete implementation or remove markers

### 2.2 Check for placeholder markers
```bash
cd /Users/peterdsp/git/Syrmos
grep -rn "PLACEHOLDER" syrmos_admin/airport_bus_providers.py
```
**Expected:** No matches
**Current Status:** ❌ FAILING

### 2.3 Check for validation markers
```bash
cd /Users/peterdsp/git/Syrmos
grep -rn "TO BE VALIDATED" syrmos_admin/airport_bus_providers.py
```
**Expected:** No matches
**Current Status:** ❌ FAILING

### 2.4 Combined check for all unimplemented markers
```bash
cd /Users/peterdsp/git/Syrmos
grep -rn "NOT IMPLEMENTED\|PLACEHOLDER\|TO BE VALIDATED" syrmos_admin/airport_bus_providers.py
```
**Expected:** No matches (exit code 1)
**Current Status:** ❌ FAILING

---

## SECTION 3: API ARCHITECTURE - SNAPSHOT vs PER-REQUEST

### 3.1 Check for per-request provider calls in endpoints
```bash
cd /Users/peterdsp/git/Syrmos
grep -n "provider\.fetch" syrmos_admin/app.py | grep -E "airport-buses|api_airport"
```
**Expected:** No matches (endpoints should read from snapshots, not call providers)
**Current Status:** ❌ FAILING (found per-request calls)
**Fix:** Switch endpoints to read from snapshot files

### 3.2 Check for snapshot file reading in endpoints
```bash
cd /Users/peterdsp/git/Syrmos
grep -n "snapshot\|\.json\|Path.*out" syrmos_admin/app.py | head -20
```
**Expected:** Evidence of snapshot file reading
**Current Status:** ❌ LACKING

---

## SECTION 4: CLIENT MIGRATION - ENDPOINT USAGE

### 4.1 Check iOS default endpoint
```bash
cd /Users/peterdsp/git/Syrmos
grep -n "api/oasa-airport-buses" iosApp/iosApp/Core/Networking/AirportBusService.swift
```
**Expected:** Only in fallback/deprecated code, not as primary
**Current Status:** ❌ PRIMARY (defaults to legacy)
**Fix:** Switch to /api/airport-buses as primary

### 4.2 Check Android default endpoint
```bash
cd /Users/peterdsp/git/Syrmos
grep -rn "api/oasa-airport-buses" core/network/ feature/schedule/ feature/map/
```
**Expected:** Only in fallback/deprecated code
**Current Status:** ❌ PRIMARY

### 4.3 Check Web default endpoint
```bash
cd /Users/peterdsp/git/Syrmos
grep -n "api/oasa-airport-buses" composeApp/src/wasmJsMain/resources/web-*.js
```
**Expected:** Only in fallback/deprecated code
**Current Status:** ❌ PRIMARY

---

## SECTION 5: BACKEND TESTS

### 5.1 Check if pytest is available
```bash
cd /Users/peterdsp/git/Syrmos/ops/syrmos-api
python3 -m pytest --version 2>&1
```
**Expected:** pytest version number
**Current Status:** ❌ LACKING (pytest not installed)
**Fix:** pip install pytest

### 5.2 Run backend tests
```bash
cd /Users/peterdsp/git/Syrmos/ops/syrmos-api
python3 -m pytest tests/test_airport_bus_providers.py -v
```
**Expected:** All tests pass, exit code 0
**Current Status:** ❌ CANNOT RUN

---

## SECTION 6: RUNTIME VERIFICATION

### 6.1 Test nginx configuration
```bash
sudo /usr/sbin/nginx -t -c ~/syrmos-proxy/nginx.conf
```
**Expected:** "syntax ok" and "test successful"
**Current Status:** ⏸️ UNKNOWN (requires Pi access)

### 6.2 Check systemd service status
```bash
sudo systemctl status syrmos-airport-bus 2>&1 | head -20
```
**Expected:** active (running)
**Current Status:** ⏸️ UNKNOWN (requires Pi access)

### 6.3 Check snapshot files exist
```bash
ls -lh ~/syrmos-api/out/airport-buses*.json
```
**Expected:** Recent snapshot files
**Current Status:** ⏸️ UNKNOWN (requires Pi access)

---

## SECTION 7: OSETH API VALIDATION

**Requires Pi (Greek IP) access:**

### 7.1 Test OSETH getStopArrivals
```bash
curl -v "https://api.oseth.gr/api/?act=getStopArrivals&p1=THS_AIR" 2>&1 | head -50
```
**Expected:** Valid JSON response with arrival data
**Current Status:** ❌ NOT VALIDATED

### 7.2 Test OSETH getBusLocation
```bash
curl -v "https://api.oseth.gr/api/?act=getBusLocation&p1=101" 2>&1 | head -50
```
**Expected:** Valid JSON response with vehicle positions
**Current Status:** ❌ NOT VALIDATED

### 7.3 Test OSETH webGetStops
```bash
curl -v "https://api.oseth.gr/api/?act=webGetStops&p1=101" 2>&1 | head -50
```
**Expected:** Valid JSON response with stop data
**Current Status:** ❌ NOT VALIDATED

---

## SECTION 8: CLIENT FEATURE VERIFICATION

### 8.1 iOS - Unified model support
```bash
cd /Users/peterdsp/git/Syrmos
grep -n "fetchUnified\|UnifiedPayload\|UnifiedAirportBuses" iosApp/iosApp/Core/Networking/AirportBusService.swift
```
**Expected:** Multiple matches showing unified support
**Current Status:** ✅ PRESENT

### 8.2 iOS - Detail sheet features
```bash
cd /Users/peterdsp/git/Syrmos
grep -n "VehicleMapView\|ServiceAlert\|confidence\|UnifiedAirportBusDetail" iosApp/iosApp/Features/Timetables/AirportBusDetailSheet.swift
```
**Expected:** Matches for all features
**Current Status:** ✅ PRESENT

### 8.3 Android - Unified model support
```bash
cd /Users/peterdsp/git/Syrmos
grep -rn "fetchUnified\|UnifiedAirportBusResponse\|UnifiedRoute" core/network/ feature/schedule/
```
**Expected:** Multiple matches
**Current Status:** ✅ PRESENT

### 8.4 Web - Unified format support
```bash
cd /Users/peterdsp/git/Syrmos
grep -n "reduceUnified\|extractVehiclesFromUnified\|OSETH_DESTINATIONS" composeApp/src/wasmJsMain/resources/web-airport.js
```
**Expected:** Multiple matches
**Current Status:** ✅ PRESENT

---

## QUICK VERIFICATION COMMAND

Run this single command to check all critical items:

```bash
cd /Users/peterdsp/git/Syrmos

echo "=== 1. Trailing Whitespace ==="
git diff --check 2>&1 || echo "FAIL: Trailing whitespace found"

echo ""
echo "=== 2. OSETH Unimplemented Markers ==="
grep -rn "NOT IMPLEMENTED\|PLACEHOLDER\|TO BE VALIDATED" syrmos_admin/airport_bus_providers.py 2>&1 || echo "PASS: No markers found"

echo ""
echo "=== 3. API Per-Request Calls ==="
grep -n "provider\.fetch" syrmos_admin/app.py | grep -c "api_airport\|airport_buses" 2>&1 && echo "FAIL: Per-request calls found" || echo "PASS: No per-request calls"

echo ""
echo "=== 4. Client Legacy Defaults ==="
echo "iOS:"
grep -n "api/oasa-airport-buses" iosApp/iosApp/Core/Networking/AirportBusService.swift | wc -l
echo "Android:"
grep -rn "api/oasa-airport-buses" core/network/ feature/schedule/ feature/map/ | wc -l
echo "Web:"
grep -n "api/oasa-airport-buses" composeApp/src/wasmJsMain/resources/web-*.js | wc -l

echo ""
echo "=== 5. Pytest Available ==="
python3 -m pytest --version 2>&1 || echo "FAIL: pytest not available"

echo ""
echo "=== SUMMARY ==="
echo "Fix all FAIL items above to achieve DONE status"
```

---

## PASS/FAIL TRACKING

| Check | Section | Current | Target | Status |
|-------|---------|---------|--------|--------|
| W1 | 1.1 | FAIL | PASS | ❌ |
| O1 | 2.1 | FAIL | PASS | ❌ |
| O2 | 2.2 | FAIL | PASS | ❌ |
| O3 | 2.3 | FAIL | PASS | ❌ |
| O4 | 2.4 | FAIL | PASS | ❌ |
| A1 | 3.1 | FAIL | PASS | ❌ |
| A2 | 3.2 | FAIL | PASS | ❌ |
| C1 | 4.1 | FAIL | PASS | ❌ |
| C2 | 4.2 | FAIL | PASS | ❌ |
| C3 | 4.3 | FAIL | PASS | ❌ |
| T1 | 5.1 | FAIL | PASS | ❌ |
| T2 | 5.2 | NA | PASS | ⏸️ |

**Total:** 11/11 checks failing or incomplete  
**Overall Status:** ❌ **NOT DONE**

---

## USAGE

1. Run commands in each section
2. Mark ✅ when pass criteria met
3. Feature is DONE when ALL checks pass
4. Delete this document when feature is DONE

---

**Created:** 2026-10-12  
**Last Run:** Never  
**Next Action:** Fix critical blockers (Sections 1-3)
