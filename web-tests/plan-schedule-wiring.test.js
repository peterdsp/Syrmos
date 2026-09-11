'use strict';
// 3.0 Phase P: the web Plan workspace feeds a REAL timetable (built from the live
// departure projection) into the plan adapter, so feasibility is real rather than
// estimated. Static-source guardrails so the wiring can't silently regress.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const js = fs.readFileSync(path.join(RES, 'web-map.js'), 'utf8');
const html = fs.readFileSync(path.join(RES, 'index.html'), 'utf8');

test('web-schedule-plan is loaded before the plan adapter', () => {
  const s = html.indexOf('/web-schedule-plan.js');
  const p = html.indexOf('/web-journey-plan.js');
  assert.ok(s > 0 && p > 0 && s < p, 'schedule engine must load before the adapter');
});

test('runPlan builds a timetable from the live projection and passes it in', () => {
  assert.match(js, /function buildTimetable\(detailed\)/, 'buildTimetable missing');
  // projects each leg's OWN line directly from its bundle with a long horizon,
  // so a later leg still has a catchable departure (the old node path capped at
  // ~10 near-term slots across all lines and starved later legs).
  assert.match(js, /projectFromBundle\(bundle, nowDate, lid, out, 30\)/,
    'must project the leg line directly with a long horizon');
  // absolute instants = now + minutesAway (not the shifted athensNow epoch)
  assert.match(js, /new Date\(nowMs \+ d\.minutesAway \* 60000\)\.toISOString\(\)/,
    'departures must be real instants from Date.now()+minutesAway');
  // fed into the adapter with a requested instant
  assert.match(js, /timetable, requestedInstant: new Date\(\)\.toISOString\(\), defaultTransferSeconds: 120/,
    'plan() must receive the timetable + requested instant');
});

test('the adapter applies the schedule pass only when a timetable is supplied', () => {
  const plan = fs.readFileSync(path.join(RES, 'web-journey-plan.js'), 'utf8');
  assert.match(plan, /if \(request && request\.timetable && d\.SchedulePlan\)/,
    'schedule upgrade must be gated on a supplied timetable');
  assert.match(plan, /d\.SchedulePlan\.assignSchedule\(/, 'must call assignSchedule');
});
