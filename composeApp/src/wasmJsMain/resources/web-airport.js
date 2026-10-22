'use strict';
// Syrmos airport express-bus telematics (web reference implementation).
//
// The Pi's airport-bus-poller serves normalized airport bus data for all operators
// (OASA and OSETH) at /api/airport-buses. This module supports both the legacy
// OASA-only endpoint and the new unified normalized endpoint.
//
// `minutesAway`/`estimated_minutes` is the operator's own estimate for a tracked bus
// to reach the airport, so the soonest per line is genuinely the next bus a rider
// can board at the airport - a real .live source, never a timetable dressed up as live.
//
// Mirrors iOS AirportBusService/AirportServiceRows and Android AirportBusEtas
// so the three clients agree on the transform.
//
// This module is the PURE, testable core: no DOM, no network, no clock. web-map.js
// fetches the JSON and hands the payload to reduceAirportBuses; presentation
// (chips, clock time, localized copy) stays in web-map.js.
//
// UMD: usable as `require('./web-airport.js')` in node and as
// `window.SyrmosAirport` in the browser.
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.SyrmosAirport = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  // X-bus -> its city-side terminal. Proper-noun destinations; localization of
  // Piraeus/Elliniko is a web-map.js presentation concern.
  const BUS_DESTINATIONS = {
    X95: 'Syntagma',
    X93: 'Kifisos',
    X96: 'Piraeus',
    X97: 'Elliniko',
  };

  // OSETH bus destinations (Thessaloniki)
  const OSETH_DESTINATIONS = {
    X3: 'Mikra Metro',
    '2X': 'Nea Elvetia Metro',
  };

  // All destinations
  const ALL_DESTINATIONS = { ...BUS_DESTINATIONS, ...OSETH_DESTINATIONS };

  // Operator color mappings
  const OPERATOR_COLORS = {
    oasa: '#E8792B',
    oseth: '#0E7490',
  };

  // Collapse the raw feed into soonest-first live ETAs per line. Negative ETAs
  // clamp to 0 (bus at the stop). Lines with no tracked vehicle are absent.
  function reduceAirportBuses(payload) {
    if (!payload) return { updatedAt: null, etasByLine: {} };

    // If it's the new unified format
    if (payload.operators) {
      return reduceUnified(payload);
    }

    // Legacy OASA format
    return reduceLegacy(payload);
  }

  // Process legacy OASA format (backward compatible)
  function reduceLegacy(payload) {
    const etasByLine = {};
    const arrivals = (payload && Array.isArray(payload.airportArrivals)) ? payload.airportArrivals : [];
    for (const a of arrivals) {
      const line = a && typeof a.lineId === 'string' ? a.lineId : '';
      if (!line) continue;
      const mins = Math.max(0, Math.round(Number(a.minutesAway)));
      if (!Number.isFinite(mins)) continue;
      (etasByLine[line] || (etasByLine[line] = [])).push(mins);
    }
    for (const line of Object.keys(etasByLine)) etasByLine[line].sort((x, y) => x - y);
    const operatorByLine = {};
    for (const line of Object.keys(etasByLine)) operatorByLine[line] = 'oasa';
    const updatedAt = (payload && typeof payload.updatedAt === 'string' && payload.updatedAt) ? payload.updatedAt : null;
    return { updatedAt, etasByLine, operatorByLine, operators: ['oasa'] };
  }

  // Process unified format with multiple operators
  function reduceUnified(payload) {
    const etasByLine = {};
    const operatorByLine = {};
    const operators = [];
    let mostRecentTimestamp = null;

    // Process each operator
    for (const [operator, data] of Object.entries(payload.operators || {})) {
      operators.push(operator);

      // Track most recent timestamp
      if (data.updated_at && (!mostRecentTimestamp || data.updated_at > mostRecentTimestamp)) {
        mostRecentTimestamp = data.updated_at;
      }

      // Process arrivals
      const arrivals = Array.isArray(data.arrivals) ? data.arrivals : [];
      for (const a of arrivals) {
        const line = typeof a.line_id === 'string' ? a.line_id : '';
        if (!line) continue;
        const mins = Math.round(Number(a.estimated_minutes));
        if (!Number.isFinite(mins)) continue;
        // Clamp negatives to 0 (bus at the stop) to match the legacy path and
        // this module's documented behaviour, rather than dropping the row.
        (etasByLine[line] || (etasByLine[line] = [])).push(Math.max(0, mins));
        operatorByLine[line] = operator;
      }
    }

    // Sort ETAs for each line
    for (const line of Object.keys(etasByLine)) {
      etasByLine[line].sort((x, y) => x - y);
    }

    // Get most recent update timestamp
    const updatedAt = mostRecentTimestamp || payload.updated_at || null;

    return { updatedAt, etasByLine, operatorByLine, operators, sources: payload.sources || [] };
  }

  function soonest(reduced, line) {
    const etas = reduced && reduced.etasByLine ? reduced.etasByLine[line] : null;
    return (etas && etas.length) ? etas[0] : null;
  }

  // One departure-shaped row per tracked line across all operators
  function airportBusDepartures(reduced) {
    const rows = [];
    const destinations = reduced.operators?.includes('oseth') ? ALL_DESTINATIONS : BUS_DESTINATIONS;

    for (const line of Object.keys(destinations)) {
      const mins = soonest(reduced, line);
      if (mins == null) continue;
      const operator = (reduced.operatorByLine && reduced.operatorByLine[line]) || 'oasa';
      const color = OPERATOR_COLORS[operator] || OPERATOR_COLORS.oasa;
      rows.push({ line, destination: destinations[line], minutesAway: mins, operator, color });
    }
    rows.sort((a, b) => a.minutesAway - b.minutesAway);
    return rows;
  }

  // Normalized live vehicle positions for the map layer. Drops rows without a
  // finite non-zero coordinate or a line id. Direction (toAirport true/false, or
  // null when unknown) is derived from normalized model.
  function airportBusVehicles(payload) {
    if (!payload) return [];

    // If it's the new unified format
    if (payload.operators) {
      return extractVehiclesFromUnified(payload);
    }

    // Legacy OASA format
    return extractVehiclesFromLegacy(payload);
  }

  // Extract vehicles from legacy format
  function extractVehiclesFromLegacy(payload) {
    const routes = (payload && payload.routes) || {};
    const dirOf = (lineId, routeCode) => {
      const r = routes[lineId];
      if (!r) return null;
      if ((r.toAirport || []).indexOf(routeCode) !== -1) return true;
      if ((r.fromAirport || []).indexOf(routeCode) !== -1) return false;
      return null;
    };
    const out = [];
    const vehicles = (payload && Array.isArray(payload.vehicles)) ? payload.vehicles : [];
    for (const v of vehicles) {
      if (!v) continue;
      const lat = Number(v.lat);
      const lng = Number(v.lng);
      if (!Number.isFinite(lat) || !Number.isFinite(lng)) continue;
      if (lat === 0 && lng === 0) continue;
      const lineId = typeof v.lineId === 'string' ? v.lineId : '';
      if (!lineId) continue;
      out.push({
        id: String(v.vehicleId || ''),
        lineId,
        lat,
        lng,
        heading: Number(v.heading) || 0,
        toAirport: dirOf(lineId, Number(v.routeCode)),
        operator: 'oasa',
      });
    }
    return out;
  }

  // Extract vehicles from unified format
  function extractVehiclesFromUnified(payload) {
    const out = [];

    for (const [operator, data] of Object.entries(payload.operators || {})) {
      const vehicles = Array.isArray(data.vehicles) ? data.vehicles : [];
      const color = OPERATOR_COLORS[operator] || OPERATOR_COLORS.oasa;

      for (const v of vehicles) {
        if (!v) continue;
        const lat = Number(v.latitude);
        const lng = Number(v.longitude);
        if (!Number.isFinite(lat) || !Number.isFinite(lng)) continue;
        if (lat === 0 && lng === 0) continue;
        const lineId = typeof v.line_id === 'string' ? v.line_id : '';
        if (!lineId) continue;

        out.push({
          id: String(v.vehicle_id || ''),
          lineId,
          lat,
          lng,
          heading: Number(v.heading) || 0,
          toAirport: typeof v.to_airport === 'boolean' ? v.to_airport : null,
          operator: operator,
          color: color,
        });
      }
    }

    return out;
  }

  // True when a station node is an airport station (metro M3_AER or suburban
  // A1_AIR/A2_AIR, or a name that reads "airport"). Same rule as web-map's
  // isAirportStation, duplicated here so callers can gate the bus fetch without
  // reaching into the map closure.
  function isAirportStationId(id, name, nameEl) {
    if (/(^|_)(AIR|AER)/.test(id || '')) return true;
    if (/airport/i.test(name || '')) return true;
    if (/Αεροδρ/.test(nameEl || '')) return true;
    return false;
  }

  // Extract service alerts from unified format
  function airportBusAlerts(payload) {
    if (!payload || !payload.operators) return [];

    const alerts = [];
    for (const [operator, data] of Object.entries(payload.operators || {})) {
      const operatorAlerts = Array.isArray(data.alerts) ? data.alerts : [];
      for (const a of operatorAlerts) {
        if (!a) continue;
        alerts.push({
          id: a.alert_id || '',
          title: a.title || '',
          summary: a.summary || '',
          operator: operator,
          severity: a.severity || 'info',
          url: a.url || null,
        });
      }
    }
    return alerts;
  }

  // Get source information from unified format
  function getSources(payload) {
    if (!payload || !payload.sources) return [];
    return Array.isArray(payload.sources) ? payload.sources : [];
  }

  // Check if unified format has data for specified operator
  function hasOperatorData(payload, operator) {
    if (!payload || !payload.operators) return false;
    return Object.keys(payload.operators).includes(operator);
  }

  // Get routes for a specific operator from unified format
  function getRoutes(payload, operator) {
    if (!payload || !payload.operators) return [];
    const opData = payload.operators[operator];
    return (opData && Array.isArray(opData.routes)) ? opData.routes : [];
  }

  return {
    reduceAirportBuses,
    soonest,
    airportBusDepartures,
    airportBusVehicles,
    isAirportStationId,
    BUS_DESTINATIONS,
    OSETH_DESTINATIONS,
    ALL_DESTINATIONS,
    airportBusAlerts,
    getSources,
    hasOperatorData,
    getRoutes,
    OPERATOR_COLORS
  };
});
