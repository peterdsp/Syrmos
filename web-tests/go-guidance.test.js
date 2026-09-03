'use strict';
// Verifies the web GO engine (web-go.js) against the cross-client golden contract
// in fixtures/go-guidance/cases.json, plus structural properties any correct
// implementation must hold.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const GO = require(path.join(
  __dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources', 'web-go.js'
));
const FIX = JSON.parse(
  fs.readFileSync(path.join(__dirname, '..', 'fixtures', 'go-guidance', 'cases.json'), 'utf8')
);

test('GO engine matches every golden fixture case', () => {
  for (const c of FIX.cases) {
    const journey = FIX.journeys[c.journey];
    assert.ok(journey, `fixture references unknown journey ${c.journey}`);
    const g = GO.guidance(journey, c.position);
    // Exact-equality against the full expected object (not a subset), so every
    // rider-facing field (stopsRemaining, nextStation, towards, ...) is part of
    // the cross-client contract.
    assert.deepEqual(g, c.expect, `[${c.name}] guidance: got ${JSON.stringify(g)}, want ${JSON.stringify(c.expect)}`);
    assert.equal(
      GO.shouldAlertGetOff(journey, c.position),
      c.alert,
      `[${c.name}] shouldAlertGetOff should be ${c.alert}`
    );
  }
});

test('advance() walks any journey from origin to arrived, alerting once per leg', () => {
  for (const [name, journey] of Object.entries(FIX.journeys)) {
    let pos = { legIndex: 0, stopIndex: 0 };
    let steps = 0;
    const alertsPerLeg = {};
    const totalStops = journey.legs.reduce((n, l) => n + l.stops.length, 0);
    while (!GO.isArrived(journey, pos)) {
      if (GO.shouldAlertGetOff(journey, pos)) {
        alertsPerLeg[pos.legIndex] = (alertsPerLeg[pos.legIndex] || 0) + 1;
      }
      pos = GO.advance(journey, pos);
      if (++steps > totalStops + 5) assert.fail(`[${name}] advance() did not converge to arrived`);
    }
    // Exactly one get-off alert should have fired on each leg (one alight per leg).
    for (let i = 0; i < journey.legs.length; i++) {
      assert.equal(alertsPerLeg[i] || 0, 1, `[${name}] leg ${i} should alert exactly once`);
    }
    // Final state is arrived at the last leg's last stop.
    const lastLeg = journey.legs[journey.legs.length - 1];
    assert.deepEqual(GO.guidance(journey, pos), {
      kind: GO.KIND.ARRIVED,
      station: lastLeg.stops[lastLeg.stops.length - 1].name,
    }, `[${name}] should end arrived`);
  }
});

test('guidance() rejects out-of-range positions', () => {
  const j = FIX.journeys.m2_direct_3;
  assert.throws(() => GO.guidance(j, { legIndex: 9, stopIndex: 0 }), RangeError);
  assert.throws(() => GO.guidance(j, { legIndex: 0, stopIndex: 9 }), RangeError);
});

test('GO engine works on a real seed line of realistic length', () => {
  // Build a single-leg journey from the committed seed's M1 stops and confirm the
  // engine boards at the origin, rides through the middle, alerts one stop before
  // the terminus, and arrives — on a full-length line, not just the toy fixtures.
  const seed = path.join(
    __dirname, '..', 'core', 'data', 'src', 'commonMain', 'composeResources', 'files', 'seed'
  );
  const lines = JSON.parse(fs.readFileSync(path.join(seed, 'lines.json'), 'utf8'));
  const arr = Array.isArray(lines) ? lines : lines.lines || Object.values(lines);
  const m1 = arr.find((l) => l.id === 'M1');
  assert.ok(m1 && Array.isArray(m1.stations) && m1.stations.length >= 5, 'seed M1 should have stops');
  const journey = {
    legs: [{ lineId: 'M1', towards: m1.stations[m1.stations.length - 1].name, stops: m1.stations }],
  };
  const n = m1.stations.length;
  assert.equal(GO.guidance(journey, { legIndex: 0, stopIndex: 0 }).kind, GO.KIND.BOARD);
  assert.equal(GO.guidance(journey, { legIndex: 0, stopIndex: 1 }).kind, GO.KIND.RIDE);
  assert.equal(GO.shouldAlertGetOff(journey, { legIndex: 0, stopIndex: n - 2 }), true);
  assert.equal(GO.guidance(journey, { legIndex: 0, stopIndex: n - 1 }).kind, GO.KIND.ARRIVED);
});
