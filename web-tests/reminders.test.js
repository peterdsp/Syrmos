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
