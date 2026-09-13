'use strict';
// 3.0 S06: the web live-GO session (SyrmosActiveJourney) must behave identically to
// the shared Kotlin ActiveJourneyStore and persist the same id-anchored session, so
// a trip in progress resumes onto the exact same stop across web, Android and iOS.
// This is the cross-client parity guard for the persistent session lifecycle.
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const AJ = require(path.join(RES, 'web-active-journey.js'));

// Two ride legs with a transfer between them. Leg A has 4 stops so an interior
// position is a genuine 'riding' state; leg B has 2. Mirrors the Kotlin test option.
function option() {
  return {
    id: 'opt-1', requestId: 'req-1', transferCount: 1,
    feasibility: { status: 'comfortable', explanationCode: 'test' },
    legs: [
      { id: 'leg-A', kind: 'ride', fromId: 's1', toId: 's4', lineId: 'line-A', orderedStopIds: ['s1', 's2', 's3', 's4'] },
      { id: 'xfer', kind: 'transfer', fromId: 's4', toId: 's4' },
      { id: 'leg-B', kind: 'ride', fromId: 's4', toId: 's5', lineId: 'line-B', orderedStopIds: ['s4', 's5'] },
    ],
  };
}
function guidance(suffix) {
  suffix = suffix || '';
  return {
    legs: [
      { lineId: 'line-A', towards: 'S4' + suffix, stops: [
        { id: 's1', name: 'S1' + suffix }, { id: 's2', name: 'S2' + suffix },
        { id: 's3', name: 'S3' + suffix }, { id: 's4', name: 'S4' + suffix }] },
      { lineId: 'line-B', towards: 'S5' + suffix, stops: [
        { id: 's4', name: 'S4' + suffix }, { id: 's5', name: 'S5' + suffix }] },
    ],
  };
}
const T0 = '2026-09-13T06:00:00.000Z';
const T1 = '2026-09-13T06:05:00.000Z';

test('start begins at the origin, ready to board', () => {
  const g = guidance();
  const a = AJ.start('go-1', option(), g, T0);
  assert.equal(a.phase, 'readyToBoard');
  assert.equal(a.legId, 'leg-A');
  assert.equal(a.confirmedStopId, 's1');
  assert.deepEqual(AJ.positionOf(a, g), { legIndex: 0, stopIndex: 0 });
});

test('advance walks every phase then arrives', () => {
  const g = guidance();
  let a = AJ.start('go-1', option(), g, T0);
  a = AJ.advance(a, g, T1); assert.equal(a.phase, 'riding'); assert.equal(a.confirmedStopId, 's2');
  a = AJ.advance(a, g, T1); assert.equal(a.phase, 'alightSoon');
  a = AJ.advance(a, g, T1); assert.equal(a.phase, 'transfer'); assert.equal(a.legId, 'leg-A'); assert.equal(a.confirmedStopId, 's4');
  a = AJ.advance(a, g, T1); assert.equal(a.phase, 'readyToBoard'); assert.equal(a.legId, 'leg-B'); assert.equal(a.confirmedStopId, 's4');
  a = AJ.advance(a, g, T1); assert.equal(a.phase, 'arrived'); assert.equal(a.confirmedStopId, 's5');
  const end = AJ.advance(a, g, T1); // past the destination is a no-op
  assert.deepEqual(AJ.positionOf(end, g), AJ.positionOf(a, g));
});

test('back steps across the leg boundary', () => {
  const g = guidance();
  let a = AJ.start('go-1', option(), g, T0);
  for (let i = 0; i < 4; i++) a = AJ.advance(a, g, T1); // (1,0)
  assert.deepEqual(AJ.positionOf(a, g), { legIndex: 1, stopIndex: 0 });
  a = AJ.back(a, g, T1);
  assert.deepEqual(AJ.positionOf(a, g), { legIndex: 0, stopIndex: 3 });
  assert.equal(a.confirmedStopId, 's4');
});

test('resumes to the same stop even when names change (language switch)', () => {
  const g = guidance('');
  let a = AJ.start('go-1', option(), g, T0);
  a = AJ.advance(a, g, T1); a = AJ.advance(a, g, T1); // (0,2)
  const decoded = AJ.decode(AJ.encode(a));
  assert.ok(decoded.ok);
  const relabelled = guidance(' (EL)');
  assert.deepEqual(AJ.positionOf(decoded.value, relabelled), { legIndex: 0, stopIndex: 2 });
  assert.equal(AJ.phaseFor(relabelled, { legIndex: 0, stopIndex: 2 }), 'alightSoon');
});

test('unknown stop falls back to leg origin, never throws', () => {
  const g = guidance();
  const a = Object.assign({}, AJ.start('go-1', option(), g, T0), { legId: 'leg-B', confirmedStopId: 'ghost' });
  assert.deepEqual(AJ.positionOf(a, g), { legIndex: 1, stopIndex: 0 });
});

test('end marks ended and is not resumable', () => {
  const g = guidance();
  const a = AJ.start('go-1', option(), g, T0);
  assert.ok(AJ.isResumable(a));
  const ended = AJ.end(a, T1);
  assert.equal(ended.phase, 'ended');
  assert.ok(AJ.isEnded(ended));
  assert.ok(!AJ.isResumable(ended));
});

test('store round-trips a live session and drops an ended one', () => {
  const mem = {};
  const storage = {
    getItem: (k) => (k in mem ? mem[k] : null),
    setItem: (k, v) => { mem[k] = String(v); },
  };
  const store = AJ.createStore(storage);
  assert.equal(store.get(), null, 'empty store has no session');
  const g = guidance();
  const a = AJ.advance(AJ.start('go-1', option(), g, T0), g, T1);
  store.set(a);
  const back = store.get();
  assert.ok(back, 'a live session reads back');
  assert.equal(back.confirmedStopId, 's2');
  store.set(AJ.end(back, T1));
  assert.equal(store.get(), null, 'an ended session is not offered for resume');
  store.set(a); store.clear();
  assert.equal(store.get(), null, 'clear removes the session');
});

test('storage key and schema version are the shared contract values', () => {
  assert.equal(AJ.STORAGE_KEY, 'syrmos.active-journey.v1');
  assert.equal(AJ.SCHEMA_VERSION, 1);
});
