'use strict';
// End-to-end web GO: plan a real route with web-planner.js over the bundled seed,
// then guide it through web-go.js to arrival. Proves the web can produce a
// GuidanceJourney the GO engine drives, the web peer of the iOS planner->GO test.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const SEED = path.join(
  __dirname, '..', 'core', 'data', 'src', 'commonMain', 'composeResources', 'files', 'seed'
);
const Planner = require(path.join(RES, 'web-planner.js'));
const GO = require(path.join(RES, 'web-go.js'));

const readJson = (p) => JSON.parse(fs.readFileSync(p, 'utf8'));
const stations = readJson(path.join(SEED, 'stations.json'));
const linesRaw = readJson(path.join(SEED, 'lines.json'));
const lines = Array.isArray(linesRaw) ? linesRaw : linesRaw.lines;

const lineById = (id) => lines.find((l) => l.id === id);

test('plans a cross-line route (M1 -> M2) with a transfer and guides to arrived', () => {
  const m1 = lineById('M1'), m2 = lineById('M2');
  assert.ok(m1 && m2, 'seed should have M1 and M2');
  const from = m1.stations[0].id;
  const to = m2.stations[m2.stations.length - 1].id;

  const journey = Planner.planDetailed(stations, lines, from, to);
  assert.ok(journey && journey.legs.length >= 2, 'M1->M2 should need at least one transfer');
  // Every leg is a real line, board == prior alight's interchange, >=2 stops.
  for (const leg of journey.legs) {
    assert.ok(lineById(leg.lineId), `leg on unknown line ${leg.lineId}`);
    assert.ok(leg.stops.length >= 2, 'leg should have >=2 stops');
  }
  assert.equal(journey.legs[0].stops[0].id, from, 'first leg boards at origin');
  assert.equal(journey.legs[journey.legs.length - 1].stops.slice(-1)[0].id, to, 'last leg alights at destination');

  // Walk it with GO: origin -> arrived, exactly one get-off alert per leg.
  let pos = { legIndex: 0, stopIndex: 0 };
  const alertsPerLeg = {};
  const totalStops = journey.legs.reduce((n, l) => n + l.stops.length, 0);
  let steps = 0;
  while (!GO.isArrived(journey, pos)) {
    if (GO.shouldAlertGetOff(journey, pos)) alertsPerLeg[pos.legIndex] = (alertsPerLeg[pos.legIndex] || 0) + 1;
    pos = GO.advance(journey, pos);
    assert.ok(++steps <= totalStops + 5, 'GO walk should converge');
  }
  for (let i = 0; i < journey.legs.length; i++) {
    assert.equal(alertsPerLeg[i] || 0, 1, `leg ${i} should alert exactly once`);
  }
  assert.equal(GO.guidance(journey, pos).kind, 'arrived');
});

test('same-line route is a single contiguous leg', () => {
  const m1 = lineById('M1');
  const from = m1.stations[0].id;
  const to = m1.stations[3].id;
  const journey = Planner.planDetailed(stations, lines, from, to);
  assert.ok(journey);
  assert.equal(journey.legs.length, 1);
  assert.equal(journey.legs[0].lineId, 'M1');
  // Board..alight ids are consecutive M1 stops.
  const m1ids = m1.stations.map((s) => s.id);
  const legIds = journey.legs[0].stops.map((s) => s.id);
  const start = m1ids.indexOf(from);
  assert.deepEqual(legIds, m1ids.slice(start, start + legIds.length));
  assert.equal(legIds[legIds.length - 1], to);
});

test('degenerate inputs return null', () => {
  const id = lineById('M1').stations[0].id;
  assert.equal(Planner.planDetailed(stations, lines, id, id), null, 'from === to');
  assert.equal(Planner.planDetailed(stations, lines, 'NOPE', id), null, 'unknown from');
  assert.equal(Planner.planDetailed(stations, lines, id, 'NOPE'), null, 'unknown to');
});

test('every planned stop id resolves to a real station', () => {
  const ids = new Set(stations.map((s) => s.id));
  for (const l of lines) for (const s of l.stations || []) ids.add(s.id);
  const j = Planner.planDetailed(stations, lines, lineById('M1').stations[0].id, lineById('M3').stations.slice(-1)[0].id);
  assert.ok(j);
  for (const leg of j.legs) for (const s of leg.stops) assert.ok(ids.has(s.id), `unknown stop ${s.id}`);
});
