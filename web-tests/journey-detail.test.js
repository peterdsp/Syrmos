'use strict';
// 3.0 S05: the web selected-journey detail timeline transform must match the
// shared golden fixture fixtures/journeys/detail.json exactly — the same ordered
// rows Kotlin JourneyDetail.timeline and iOS JourneyDetail.timeline produce. This
// is the cross-client parity guard for the S05 timeline.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const FIX = path.join(__dirname, '..', 'fixtures', 'journeys', 'detail.json');
const Detail = require(path.join(RES, 'web-journey-detail.js'));
const fixture = JSON.parse(fs.readFileSync(FIX, 'utf8'));

test('every fixture case produces the exact expected timeline rows', () => {
  for (const c of fixture.cases) {
    const rows = Detail.timeline(c.option);
    assert.deepEqual(rows, c.expectedRows, c.id);
  }
});

test('a null clock stays null (unknown), never a fabricated value', () => {
  const rows = Detail.timeline({ legs: [
    { id: 'r0', kind: 'ride', lineId: 'M2', fromId: 'A', toId: 'B', orderedStopIds: ['A', 'B'], departureInstant: null, arrivalInstant: null, timingKind: 'estimated' },
  ] });
  assert.equal(rows[0].kind, 'board');
  assert.equal(rows[0].clock, null);
  assert.equal(rows[1].kind, 'alight');
  assert.equal(rows[1].clock, null);
});

test('origin/destination/interchange node roles are assigned by position', () => {
  const rows = fixture.cases.find((c) => c.id === 'two_rides_one_transfer').expectedRows;
  assert.equal(rows.find((r) => r.kind === 'board' && r.legId === 'ride-0').node, 'origin');
  assert.equal(rows.find((r) => r.kind === 'alight' && r.legId === 'ride-0').node, 'interchange');
  assert.equal(rows.find((r) => r.kind === 'alight' && r.legId === 'ride-2').node, 'destination');
  // recompute from the transform, not just the fixture literal
  const live = Detail.timeline(fixture.cases.find((c) => c.id === 'two_rides_one_transfer').option);
  assert.deepEqual(live.map((r) => r.node).filter(Boolean), ['origin', 'interchange', 'interchange', 'interchange', 'destination']);
});

test('intermediate stops are counted, and no stops row when <= 2 ordered stops', () => {
  const rows = Detail.timeline({ legs: [
    { id: 'r0', kind: 'ride', lineId: 'M1', fromId: 'A', toId: 'D', orderedStopIds: ['A', 'B', 'C', 'D'], timingKind: 'scheduled' },
  ] });
  const stops = rows.find((r) => r.kind === 'stops');
  assert.ok(stops && stops.count === 2, 'two intermediate stops');
  const rows2 = Detail.timeline({ legs: [
    { id: 'r0', kind: 'ride', lineId: 'M1', fromId: 'A', toId: 'B', orderedStopIds: ['A', 'B'], timingKind: 'scheduled' },
  ] });
  assert.ok(!rows2.some((r) => r.kind === 'stops'), 'no stops disclosure for a 2-stop leg');
});

test('transfer seconds fall back to the arrival->departure gap when no minimum is set', () => {
  const c = fixture.cases.find((x) => x.id === 'walk_transfer_gap_seconds');
  const rows = Detail.timeline(c.option);
  const walk = rows.find((r) => r.kind === 'walk');
  assert.equal(walk.seconds, 300);
});
