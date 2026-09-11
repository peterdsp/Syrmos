'use strict';
// 3.0 Phase P: the schedule-aware planning pass assigns real scheduled instants
// so feasibility becomes real (comfortable/tight) instead of estimated. Runs the
// shared golden fixture through SyrmosSchedulePlan.assignSchedule + the feasibility
// engine, the same fixture the Kotlin SchedulePlanner test asserts.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const SchedulePlan = require(path.join(RES, 'web-schedule-plan.js'));
const Feasibility = require(path.join(RES, 'web-feasibility.js'));
const fixture = JSON.parse(fs.readFileSync(
  path.join(__dirname, '..', 'fixtures', 'journeys', 'schedule.json'), 'utf8'));

const epoch = (iso) => Math.floor(Date.parse(iso) / 1000);

for (const c of fixture.cases) {
  test(`schedule: ${c.id}`, () => {
    const scheduled = SchedulePlan.assignSchedule(
      c.option,
      { requestedInstant: c.requestedInstant, defaultTransferSeconds: fixture.defaultTransferSeconds },
      c.timetable,
    );

    if (c.expectedDepartureInstant) {
      assert.equal(epoch(scheduled.departureInstant), epoch(c.expectedDepartureInstant), 'departure instant');
    }
    if (c.expectedArrivalInstant) {
      assert.equal(epoch(scheduled.arrivalInstant), epoch(c.expectedArrivalInstant), 'arrival instant');
    }
    if (c.expectedTimingKind) {
      assert.equal(scheduled.timingKind, c.expectedTimingKind, 'option timing kind');
    }

    const feas = Feasibility.forOption(scheduled);
    assert.equal(feas.status, c.expectedFeasibility.status, 'feasibility status');
    assert.equal(feas.explanationCode, c.expectedFeasibility.explanationCode, 'explanation code');
    if (Object.prototype.hasOwnProperty.call(c.expectedFeasibility, 'minimumMarginSeconds')) {
      assert.equal(feas.minimumMarginSeconds, c.expectedFeasibility.minimumMarginSeconds, 'margin seconds');
    }
  });
}

test('the input option is not mutated', () => {
  const c = fixture.cases.find((x) => x.id === 'tight_transfer');
  const before = JSON.stringify(c.option);
  SchedulePlan.assignSchedule(c.option, { requestedInstant: c.requestedInstant }, c.timetable);
  assert.equal(JSON.stringify(c.option), before, 'assignSchedule must not mutate its input');
});
