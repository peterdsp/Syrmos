'use strict';
// Mirrors iOS GoLocationAdvancerTests so the web live-GO advancer behaves
// identically: forward-only, threshold-gated, transfer-aware.
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const GO = require(path.join(
  __dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources', 'web-go.js'
));

// West->east line of 4 stops ~1km apart at lat 38.0 (0.012 deg lon ~ 1053m here).
const coords = {
  S0: { lat: 38.0, lon: 23.700 },
  S1: { lat: 38.0, lon: 23.712 },
  S2: { lat: 38.0, lon: 23.724 },
  S3: { lat: 38.0, lon: 23.736 },
};
const line4 = () => ({
  legs: [{ lineId: 'L', towards: 'S3', stops: [
    { id: 'S0', name: 'S0' }, { id: 'S1', name: 'S1' }, { id: 'S2', name: 'S2' }, { id: 'S3', name: 'S3' },
  ] }],
});
const p = (l, s) => ({ legIndex: l, stopIndex: s });

test('at a stop coordinate places the rider there', () => {
  assert.deepEqual(GO.advancedPosition(line4(), p(0, 0), coords, 38.0, 23.724), p(0, 2));
});

test('never moves backward on a jittery fix', () => {
  assert.deepEqual(GO.advancedPosition(line4(), p(0, 2), coords, 38.0, 23.700), p(0, 2));
});

test('between stops holds position', () => {
  assert.deepEqual(GO.advancedPosition(line4(), p(0, 1), coords, 38.0, 23.718), p(0, 1));
});

test('advances forward to nearest stop within threshold', () => {
  assert.deepEqual(GO.advancedPosition(line4(), p(0, 0), coords, 38.0, 23.735), p(0, 3));
});

test('transfer advances onto the next leg board platform', () => {
  const j = {
    legs: [
      { lineId: 'A', towards: 'X', stops: [{ id: 'A0', name: 'A0' }, { id: 'XA', name: 'X' }] },
      { lineId: 'B', towards: 'B1', stops: [{ id: 'XB', name: 'X' }, { id: 'B1', name: 'B1' }] },
    ],
  };
  const c = {
    A0: { lat: 38.0, lon: 23.700 },
    XA: { lat: 38.0, lon: 23.750 },
    XB: { lat: 38.0005, lon: 23.7502 },
    B1: { lat: 38.010, lon: 23.760 },
  };
  assert.deepEqual(GO.advancedPosition(j, p(0, 0), c, 38.0005, 23.7502), p(1, 0));
});

test('haversine known distance (~1.1km per 0.01 deg lat)', () => {
  const d = GO.haversine(38.0, 23.7, 38.01, 23.7);
  assert.ok(Math.abs(d - 1113) < 30, `got ${d}`);
});
