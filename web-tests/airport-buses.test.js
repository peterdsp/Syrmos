'use strict';
// Web airport express-bus telematics, mirroring iOS AirportServiceTests so the
// two clients reduce the /api/oasa-airport-buses feed identically.
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const A = require(path.join(
  __dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources', 'web-airport.js'
));

const payload = (arrivals, updatedAt = '2026-09-03T21:42:32.703144+00:00') => ({ updatedAt, airportArrivals: arrivals });

test('reduceAirportBuses groups, sorts ascending, and clamps negatives', () => {
  const r = A.reduceAirportBuses(payload([
    { lineId: 'X95', minutesAway: 19 },
    { lineId: 'X95', minutesAway: 5 },
    { lineId: 'X93', minutesAway: -3 }, // clamps to 0
    { lineId: '', minutesAway: 4 },     // dropped: no line
  ]));
  assert.deepEqual(r.etasByLine.X95, [5, 19]);
  assert.equal(A.soonest(r, 'X95'), 5);
  assert.equal(A.soonest(r, 'X93'), 0);
  assert.equal(A.soonest(r, 'X97'), null, 'untracked line absent');
  assert.equal(r.updatedAt, '2026-09-03T21:42:32.703144+00:00');
});

test('reduceAirportBuses tolerates a malformed / empty feed', () => {
  assert.deepEqual(A.reduceAirportBuses({}).etasByLine, {});
  assert.deepEqual(A.reduceAirportBuses(null).etasByLine, {});
  assert.equal(A.reduceAirportBuses({ airportArrivals: [{ minutesAway: 3 }] }).etasByLine.X95, undefined);
});

test('airportBusDepartures emits one soonest row per tracked line, sorted', () => {
  const r = A.reduceAirportBuses(payload([
    { lineId: 'X95', minutesAway: 19 },
    { lineId: 'X95', minutesAway: 5 },
    { lineId: 'X93', minutesAway: 9 },
    { lineId: 'X97', minutesAway: 2 },
  ]));
  const rows = A.airportBusDepartures(r);
  assert.deepEqual(rows.map((x) => [x.line, x.minutesAway]), [
    ['X97', 2], ['X95', 5], ['X93', 9],
  ], 'soonest-first across lines, one row per line');
  assert.equal(rows.find((x) => x.line === 'X95').destination, 'Syntagma');
  assert.equal(rows.find((x) => x.line === 'X97').destination, 'Elliniko');
});

test('airportBusDepartures never fabricates a row for an untracked line', () => {
  const r = A.reduceAirportBuses(payload([{ lineId: 'X95', minutesAway: 5 }]));
  const rows = A.airportBusDepartures(r);
  assert.equal(rows.length, 1);
  assert.equal(rows[0].line, 'X95');
});

test('isAirportStationId matches metro/suburban airport ids and names', () => {
  assert.equal(A.isAirportStationId('M3_AER'), true);
  assert.equal(A.isAirportStationId('A1_AIR'), true);
  assert.equal(A.isAirportStationId('M3_SYN'), false);
  assert.equal(A.isAirportStationId('X', 'Airport Terminal'), true);
  assert.equal(A.isAirportStationId('X', '', 'Αεροδρόμιο'), true);
});
