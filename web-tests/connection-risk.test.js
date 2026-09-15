'use strict';
// 3.0 S07: the web connection-risk transform must match the shared golden fixture
// fixtures/journeys/connection-risk.json exactly — the same per-transfer rows and
// worst status Kotlin ConnectionRisk and iOS ConnectionRisk produce. Cross-client
// parity guard for the S07 risk numbers.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const FIX = path.join(__dirname, '..', 'fixtures', 'journeys', 'connection-risk.json');
const Risk = require(path.join(RES, 'web-connection-risk.js'));
const fixture = JSON.parse(fs.readFileSync(FIX, 'utf8'));

test('every fixture case yields the exact expected per-transfer risks', () => {
  for (const c of fixture.cases) {
    assert.deepEqual(Risk.risks(c.option), c.expectedRisks, c.id);
  }
});

test('worst status per case matches the fixture', () => {
  for (const c of fixture.cases) {
    assert.equal(Risk.worstStatus(c.option), c.expectedWorstStatus, c.id);
  }
});

test('margin thresholds: tight boundary is inclusive at 179s', () => {
  // gap 299, allow 120 -> margin 179 -> tight; gap 300 -> margin 180 -> comfortable
  const at = (gap) => Risk.risks({ legs: [
    { id: 'r0', kind: 'ride', lineId: 'M1', fromId: 'A', toId: 'B', arrivalInstant: '2026-01-15T08:00:00+02:00' },
    { id: 't1', kind: 'transfer', fromId: 'B', toId: 'B2', transferMinimumSeconds: 120 },
    { id: 'r2', kind: 'ride', lineId: 'M2', fromId: 'B2', toId: 'C', departureInstant: new Date(Date.parse('2026-01-15T08:00:00+02:00') + gap * 1000).toISOString() },
  ] })[0].status;
  assert.equal(at(299), 'tight');
  assert.equal(at(300), 'comfortable');
  assert.equal(at(119), 'missed');
});

test('uncertainty erodes the margin', () => {
  const r = Risk.risks({ legs: [
    { id: 'r0', kind: 'ride', lineId: 'M1', fromId: 'A', toId: 'B', arrivalInstant: '2026-01-15T08:00:00+02:00', uncertaintySeconds: 120 },
    { id: 't1', kind: 'transfer', fromId: 'B', toId: 'B2', transferMinimumSeconds: 120 },
    { id: 'r2', kind: 'ride', lineId: 'M2', fromId: 'B2', toId: 'C', departureInstant: '2026-01-15T08:05:00+02:00' },
  ] })[0];
  // gap 300, allow 120, uncertainty 120 -> margin 60 -> tight (would be comfortable at uncertainty 0)
  assert.equal(r.availableSeconds, 300);
  assert.equal(r.status, 'tight');
});

test('nextConcern picks the nearest non-comfortable upcoming transfer, or null', () => {
  const opt = fixture.cases.find((c) => c.id === 'two_transfers_worst_wins').option;
  const concern = Risk.nextConcern(opt, 0);
  assert.equal(concern.toLineId, 'M3');
  assert.equal(concern.status, 'missed');
  // From beyond the last transfer there is nothing left to worry about.
  assert.equal(Risk.nextConcern(opt, 5), null);
  // A comfortable-only trip has no concern.
  assert.equal(Risk.nextConcern(fixture.cases.find((c) => c.id === 'comfortable_transfer').option, 0), null);
});
