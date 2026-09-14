'use strict';
// 3.0 Phase N J09: user-enabled leave-by reminders. Drives every case in
// fixtures/reminders/leave-by.json through the web engine and asserts exact
// equality. The Kotlin LeaveByReminderTest and iOS LeaveByReminderTests assert
// the SAME cases, so the timing and reconciliation cannot drift between clients.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const R = require(path.join(
  __dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources', 'web-reminders.js'
));
const fixture = JSON.parse(fs.readFileSync(
  path.join(__dirname, '..', 'fixtures', 'reminders', 'leave-by.json'), 'utf8'
));

for (const c of fixture.stateCases) {
  test(`leave-by state: ${c.id} (${c.note})`, () => {
    const r = { lineId: 'M1', stationId: 'M1_VIC', departureEpochSeconds: c.departureEpochSeconds, leadSeconds: c.leadSeconds };
    assert.equal(R.leaveByEpochSeconds(r), c.expected.leaveByEpochSeconds, 'leaveByEpochSeconds');
    assert.equal(R.state(r, c.now), c.expected.state, 'state');
    assert.equal(R.fireAtEpochSeconds(r, c.now), c.expected.fireAtEpochSeconds, 'fireAtEpochSeconds');
    assert.equal(R.minutesUntilLeave(r, c.now), c.expected.minutesUntilLeave, 'minutesUntilLeave');
  });
}

for (const c of fixture.reconcileCases) {
  test(`leave-by reconcile: ${c.name}`, () => {
    const got = R.reconcile(c.current, c.desired, c.now);
    assert.deepEqual(got.toSchedule.map((r) => R.reminderId(r)), c.expected.toSchedule, 'toSchedule');
    assert.deepEqual(got.toCancel.slice().sort(), c.expected.toCancel.slice().sort(), 'toCancel');
    assert.deepEqual(got.unchanged.map((r) => R.reminderId(r)), c.expected.unchanged, 'unchanged');
  });
}

test('idFor matches the documented dedup-key shape', () => {
  assert.equal(R.idFor('M1', 'M1_VIC', 1000000), 'M1|M1_VIC|1000000');
});

// ---- Saved-departure board store + persistence parity --------------------
const savedFix = JSON.parse(fs.readFileSync(
  path.join(__dirname, '..', 'fixtures', 'reminders', 'saved.json'), 'utf8'
));

test('encodeRoot is byte-parity with the shared wire form', () => {
  assert.equal(R.encodeRoot(savedFix.root), savedFix.encoded);
});

test('decodeRoot round-trips the canonical blob', () => {
  const r = R.decodeRoot(savedFix.encoded);
  assert.ok(r.ok);
  assert.deepEqual(r.value, savedFix.root);
});

test('decodeRoot: blank is a fresh empty list, newer schema unsupported, garbage corrupt', () => {
  assert.deepEqual(R.decodeRoot('').value.savedDepartures, []);
  assert.deepEqual(R.decodeRoot('   ').value.savedDepartures, []);
  const u = R.decodeRoot('{"schemaVersion":999,"savedDepartures":[]}');
  assert.ok(u.unsupported && u.foundVersion === 999);
  assert.ok(R.decodeRoot('{not json').corrupt);
});

test('store ops: save/dedupe/remove/prune/reorder mirror the Kotlin store', () => {
  const a = { lineId: 'M1', stationId: 'M1_VIC', stationName: 'Victoria', destination: 'Kifisia', scheduledTime: '08:00', departureEpochSeconds: 1000000, leadSeconds: 900, createdAt: '2026-01-01T08:00:00Z', schemaVersion: 1 };
  const b = { lineId: 'M3', stationId: 'M3_SYN', stationName: 'Syntagma', destination: 'Airport', scheduledTime: '08:10', departureEpochSeconds: 1000600, leadSeconds: 600, createdAt: '2026-01-01T08:01:00Z', schemaVersion: 1 };

  let list = R.saveDeparture([], a);
  list = R.saveDeparture(list, b);
  assert.deepEqual(list.map(R.savedId), ['M3|M3_SYN|1000600', 'M1|M1_VIC|1000000']);

  // re-save same id: dedupe, move to top, replace
  const aUpdated = Object.assign({}, a, { leadSeconds: 1200 });
  list = R.saveDeparture(list, aUpdated);
  assert.deepEqual(list.map(R.savedId), ['M1|M1_VIC|1000000', 'M3|M3_SYN|1000600']);
  assert.equal(list.find((d) => R.savedId(d) === 'M1|M1_VIC|1000000').leadSeconds, 1200);
  assert.equal(list.length, 2);

  assert.ok(R.containsDeparture(list, 'M1|M1_VIC|1000000'));
  list = R.removeDeparture(list, 'M1|M1_VIC|1000000');
  assert.ok(!R.containsDeparture(list, 'M1|M1_VIC|1000000'));

  const pruned = R.pruneDeparted([a, b], 1000300);
  assert.deepEqual(pruned.map(R.savedId), ['M3|M3_SYN|1000600']);

  assert.deepEqual(R.reorderDepartures([a, b], ['M3|M3_SYN|1000600', 'M1|M1_VIC|1000000', 'x']).map(R.savedId),
    ['M3|M3_SYN|1000600', 'M1|M1_VIC|1000000']);

  const reminders = R.toReminders([a]);
  assert.equal(R.leaveByEpochSeconds(reminders[0]), 999100);
});
