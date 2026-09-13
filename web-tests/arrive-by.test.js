'use strict';
// 3.0 Phase P: backward (arrive-by) scheduling + last-connection-home. Runs the
// shared golden fixture through SyrmosSchedulePlan + the feasibility engine, the
// same fixture the Kotlin SchedulePlanner test asserts.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const S = require(path.join(RES, 'web-schedule-plan.js'));
const Feasibility = require(path.join(RES, 'web-feasibility.js'));
const fixture = JSON.parse(fs.readFileSync(
  path.join(__dirname, '..', 'fixtures', 'journeys', 'arrive-by.json'), 'utf8'));
const epoch = (iso) => Math.floor(Date.parse(iso) / 1000);

for (const c of fixture.cases) {
  test(`arrive-by: ${c.id}`, () => {
    const opts = { defaultTransferSeconds: fixture.defaultTransferSeconds };
    const out = c.mode === 'lastConnection'
      ? S.lastConnection(c.option, opts, c.timetable)
      : S.assignScheduleArriveBy(c.option, Object.assign({ arriveByInstant: c.arriveByInstant }, opts), c.timetable);

    if (c.expectedNullDeparture) {
      assert.equal(out.departureInstant, null, 'no journey -> null departure');
    }
    if (c.expectedTimingKind) {
      assert.equal(out.timingKind, c.expectedTimingKind);
    }
    if (c.expectedDepartureInstant) {
      assert.equal(epoch(out.departureInstant), epoch(c.expectedDepartureInstant), 'leave-by departure');
    }
    if (c.expectedArrivalInstant) {
      assert.equal(epoch(out.arrivalInstant), epoch(c.expectedArrivalInstant), 'arrival');
    }
    if (c.expectedFeasibility) {
      const feas = Feasibility.forOption(out);
      assert.equal(feas.status, c.expectedFeasibility.status, 'feasibility status');
      assert.equal(feas.explanationCode, c.expectedFeasibility.explanationCode, 'explanation code');
      if (Object.prototype.hasOwnProperty.call(c.expectedFeasibility, 'minimumMarginSeconds')) {
        assert.equal(feas.minimumMarginSeconds, c.expectedFeasibility.minimumMarginSeconds, 'margin');
      }
    }
  });
}
