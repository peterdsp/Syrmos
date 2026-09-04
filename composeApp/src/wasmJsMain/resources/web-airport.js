'use strict';
// Syrmos airport express-bus telematics (web reference implementation).
//
// The Pi's oasa-airport-bus-watcher polls OASA Telematics getStopArrivals for
// the airport stop (10705) and serves live per-vehicle ETAs at
// /api/oasa-airport-buses. `minutesAway` is OASA's own btime2 estimate for a
// tracked bus to reach the airport, so the soonest per line is genuinely the
// next X-bus a rider can board at the airport - a real .live source, never a
// timetable dressed up as live. Mirrors iOS AirportBusService/AirportServiceRows
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

  // Collapse the raw feed into soonest-first live ETAs per line. Negative ETAs
  // clamp to 0 (bus at the stop). Lines with no tracked vehicle are absent.
  function reduceAirportBuses(payload) {
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
    const updatedAt = (payload && typeof payload.updatedAt === 'string' && payload.updatedAt) ? payload.updatedAt : null;
    return { updatedAt, etasByLine };
  }

  function soonest(reduced, line) {
    const etas = reduced && reduced.etasByLine ? reduced.etasByLine[line] : null;
    return (etas && etas.length) ? etas[0] : null;
  }

  // One departure-shaped row per tracked X-bus, soonest first across lines.
  // Only tracked lines appear: an untracked line is omitted rather than shown
  // with a fabricated "24/7" time (web is map-first, so a dead row is noise).
  function airportBusDepartures(reduced) {
    const rows = [];
    for (const line of Object.keys(BUS_DESTINATIONS)) {
      const mins = soonest(reduced, line);
      if (mins == null) continue;
      rows.push({ line, destination: BUS_DESTINATIONS[line], minutesAway: mins });
    }
    rows.sort((a, b) => a.minutesAway - b.minutesAway);
    return rows;
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

  return { reduceAirportBuses, soonest, airportBusDepartures, isAirportStationId, BUS_DESTINATIONS };
});
