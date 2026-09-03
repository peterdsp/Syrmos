'use strict';
// Transit-data quality gate on the committed seed. These turn real data-integrity
// checks into regression tests: a line stop that vanishes from the registry, a
// duplicated stop, or a degenerate line would otherwise ship silently.
//
// The app's `lines` come from schedules-v2/lines.json (33 lines), so these check
// that source (topology-integrity.test.js already checks the flat seed/lines.json
// used for Athens polylines).

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const SEED = path.join(
  __dirname, '..', 'core', 'data', 'src', 'commonMain', 'composeResources', 'files', 'seed'
);
const readJson = (rel) => JSON.parse(fs.readFileSync(path.join(SEED, rel), 'utf8'));
const stations = readJson('stations.json');
const registry = new Set(stations.map((s) => s.id));
const lines = readJson('schedules-v2/lines.json').lines;

// KNOWN, tracked gap: the Thessaloniki airport express lines (X3, 2X) reference
// THS_AIR (Makedonia Airport), which is defined inside those lines but is absent
// from stations.json. Generated on the Pi, so the fix is source-side. This test
// pins the gap so it cannot GROW (a new missing stop fails), while documenting
// the one exception. Remove entries here as they are reconciled.
const KNOWN_MISSING = new Set(['X3:THS_AIR', '2X:THS_AIR']);

test('schedules-v2 line stops resolve to the station registry (except tracked gaps)', () => {
  const missing = [];
  for (const l of lines) {
    for (const s of l.stations || []) {
      if (!registry.has(s.id)) missing.push(`${l.id}:${s.id}`);
    }
  }
  const unexpected = missing.filter((m) => !KNOWN_MISSING.has(m));
  assert.deepEqual(unexpected, [], `new line stops missing from the registry: ${unexpected.join(', ')}`);
  // The known gaps must all still be present as line stops (else update the list).
  const stillPresent = missing.filter((m) => KNOWN_MISSING.has(m));
  assert.equal(stillPresent.length, KNOWN_MISSING.size,
    `a tracked gap was fixed — remove it from KNOWN_MISSING: present=${stillPresent.join(', ')}`);
});

test('no schedules-v2 line has a duplicated stop id', () => {
  const dups = [];
  for (const l of lines) {
    const seen = new Set();
    for (const s of l.stations || []) {
      if (seen.has(s.id)) dups.push(`${l.id}:${s.id}`);
      seen.add(s.id);
    }
  }
  assert.deepEqual(dups, [], `lines with a duplicated stop: ${dups.join(', ')}`);
});

test('every schedules-v2 line has at least two stops (an origin and a destination)', () => {
  const degenerate = lines.filter((l) => (l.stations || []).length < 2).map((l) => l.id);
  assert.deepEqual(degenerate, [], `lines with fewer than 2 stops: ${degenerate.join(', ')}`);
});

test('every line stop carries finite coordinates inside Greece', () => {
  const LAT = [34.6, 41.9];
  const LON = [19.2, 29.8];
  const bad = [];
  for (const l of lines) {
    for (const s of l.stations || []) {
      const lat = s.lat, lon = s.lng;
      const ok = Number.isFinite(lat) && lat >= LAT[0] && lat <= LAT[1]
        && Number.isFinite(lon) && lon >= LON[0] && lon <= LON[1];
      if (!ok) bad.push(`${l.id}:${s.id} (${lat}, ${lon})`);
    }
  }
  assert.deepEqual(bad, [], `line stops with bad coordinates: ${bad.join('; ')}`);
});
