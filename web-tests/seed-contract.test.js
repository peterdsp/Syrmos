'use strict';
// Contract tests for the bundled seed the web client loads offline. A malformed
// coordinate or an unknown line status here is exactly the class of data bug that
// puts a station in the sea or greys out a running line, so these assertions are
// a transit-data sanity gate on the committed seed.
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

// schedules-v2/lines.json is { version, updatedAt, lines: [...] }; tolerate a
// bare array too in case the shape is ever flattened.
const linesArray = (obj) => (Array.isArray(obj) ? obj : obj.lines);

// Greece bounding box (mainland + islands, generous): Gavdos/Corfu/Evros/Kastellorizo.
const LAT = [34.6, 41.9];
const LON = [19.2, 29.8];

// The four valid line states (memory: LineStatus is a 4-state enum).
const LINE_STATUS = new Set(['operational', 'under_construction', 'suspended', 'seasonal']);

test('stations.json: every station has finite coordinates inside Greece', () => {
  const stations = readJson('stations.json');
  assert.ok(Array.isArray(stations) && stations.length > 0, 'stations.json should be a non-empty array');
  const offenders = [];
  for (const s of stations) {
    const lat = s.latitude, lon = s.longitude;
    const ok =
      typeof lat === 'number' && Number.isFinite(lat) && lat >= LAT[0] && lat <= LAT[1] &&
      typeof lon === 'number' && Number.isFinite(lon) && lon >= LON[0] && lon <= LON[1];
    if (!ok) offenders.push(`${s.id}: (${lat}, ${lon})`);
  }
  assert.deepEqual(offenders, [], `stations with bad coordinates: ${offenders.join('; ')}`);
});

test('stations.json: ids are present and unique, line_ids non-empty', () => {
  const stations = readJson('stations.json');
  const seen = new Set();
  const dupes = [];
  const emptyLines = [];
  for (const s of stations) {
    assert.ok(typeof s.id === 'string' && s.id.length > 0, `station missing id: ${JSON.stringify(s).slice(0, 80)}`);
    if (seen.has(s.id)) dupes.push(s.id);
    seen.add(s.id);
    if (!Array.isArray(s.line_ids) || s.line_ids.length === 0) emptyLines.push(s.id);
  }
  assert.deepEqual(dupes, [], `duplicate station ids: ${dupes.join(', ')}`);
  assert.deepEqual(emptyLines, [], `stations with no line_ids: ${emptyLines.join(', ')}`);
});

test('schedules-v2/lines.json: every line has a known status and a type', () => {
  const lines = readJson('schedules-v2/lines.json');
  const arr = linesArray(lines);
  assert.ok(arr.length > 0, 'lines.json should list lines');
  const badStatus = [];
  const badType = [];
  for (const l of arr) {
    if (!LINE_STATUS.has(l.status)) badStatus.push(`${l.id}: ${l.status}`);
    if (typeof l.type !== 'string' || l.type.length === 0) badType.push(String(l.id));
  }
  assert.deepEqual(badStatus, [], `lines with unknown status: ${badStatus.join('; ')}`);
  assert.deepEqual(badType, [], `lines missing type: ${badType.join(', ')}`);
});

test('schedules-v2/lines.json: line ids are unique', () => {
  const lines = readJson('schedules-v2/lines.json');
  const arr = linesArray(lines);
  const seen = new Set();
  const dupes = [];
  for (const l of arr) {
    if (seen.has(l.id)) dupes.push(l.id);
    seen.add(l.id);
  }
  assert.deepEqual(dupes, [], `duplicate line ids: ${dupes.join(', ')}`);
});
