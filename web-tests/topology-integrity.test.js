'use strict';
// Integrity of the station / line graph the app (and the 3.0 journey planner)
// routes over. A dangling line reference or a station that exists on a line but
// not in the registry is exactly what makes a planner misroute or a marker
// vanish, so these are a transit-data-quality gate on the committed seed.
//
// Run: `node --test` from web-tests/, or `node --test web-tests/` from the repo root.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const SEED = path.join(
  __dirname, '..', 'core', 'data', 'src', 'commonMain', 'composeResources', 'files', 'seed'
);
const readJson = (rel) => JSON.parse(fs.readFileSync(path.join(SEED, rel), 'utf8'));
const linesArr = (obj) => (Array.isArray(obj) ? obj : obj.lines || Object.values(obj));

test('every station.line_ids entry is a known line id', () => {
  const stations = readJson('stations.json');
  const known = new Set([
    ...linesArr(readJson('lines.json')).map((l) => l.id),
    ...linesArr(readJson('schedules-v2/lines.json')).map((l) => l.id),
  ]);
  const bad = [];
  for (const s of stations) {
    for (const lid of s.line_ids || []) if (!known.has(lid)) bad.push(`${s.id} -> ${lid}`);
  }
  assert.deepEqual(bad, [], `stations referencing unknown lines: ${bad.join(', ')}`);
});

test('every line.stations[].id exists in the station registry', () => {
  const ids = new Set(readJson('stations.json').map((s) => s.id));
  const missing = [];
  for (const l of linesArr(readJson('lines.json'))) {
    for (const st of l.stations || []) if (!ids.has(st.id)) missing.push(`${l.id}:${st.id}`);
  }
  assert.deepEqual(missing, [], `line.stations ids absent from stations.json: ${missing.join(', ')}`);
});

test('a line and the station registry agree on each stop within ~800m', () => {
  // seed/lines.json carries a denormalized copy of each stop's coordinates.
  // stations.json is the canonical registry. They must not grossly disagree, or
  // the map draws the line and the marker in different places. This guard is
  // deliberately loose (~0.008 deg) to catch a gross regression, not to enforce
  // exact equality. KNOWN small deltas: six T7 tram stops (T7_DIM/PLA/EVA/GRI/
  // MIK/GIP) currently differ by up to ~0.005 deg (~490m); tracked as a data
  // follow-up. Tighten this once the two files are reconciled.
  const byId = new Map(readJson('stations.json').map((s) => [s.id, s]));
  const TOL = 0.008;
  const gross = [];
  for (const l of linesArr(readJson('lines.json'))) {
    for (const st of l.stations || []) {
      const s = byId.get(st.id);
      if (!s) continue;
      if (Math.abs(s.latitude - st.lat) > TOL || Math.abs(s.longitude - st.lng) > TOL) {
        gross.push(`${l.id}:${st.id}`);
      }
    }
  }
  assert.deepEqual(gross, [], `line vs registry coordinates grossly disagree: ${gross.join(', ')}`);
});
