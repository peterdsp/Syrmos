'use strict';
// Phase N J09 web runtime: drives web-reminders-runtime.js with an in-memory
// localStorage, a controllable clock and a fake timer/notify, asserting it
// schedules a leave-by timer, fires it (Notification + event), reconciles on
// change, prunes departed, and honours the opt-in. The scheduling decisions come
// from the shared engine also asserted in reminders.test.js.
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const RT = require(path.join(
  __dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources', 'web-reminders-runtime.js'
));

function fakeStorage() {
  const m = new Map();
  return {
    getItem: (k) => (m.has(k) ? m.get(k) : null),
    setItem: (k, v) => m.set(k, String(v)),
    removeItem: (k) => m.delete(k),
  };
}

// A controllable timer bank: setTimeout records {fn, at}; advance(ms) fires due ones.
function fakeTimers(clock) {
  let seq = 1;
  const pending = new Map();
  return {
    set: (fn, ms) => { const id = seq++; pending.set(id, { fn, at: clock.t + ms }); return id; },
    clear: (id) => pending.delete(id),
    advance: (ms) => {
      clock.t += ms;
      for (const [id, e] of [...pending.entries()]) {
        if (e.at <= clock.t) { pending.delete(id); e.fn(); }
      }
    },
    size: () => pending.size,
  };
}

function setup() {
  const clock = { t: 1000000 * 1000 }; // ms
  const timers = fakeTimers(clock);
  const notified = [];
  RT._env.storage = fakeStorage();
  RT._env.now = () => Math.floor(clock.t / 1000);
  RT._env.setTimeout = (fn, ms) => timers.set(fn, ms);
  RT._env.clearTimeout = (h) => timers.clear(h);
  RT._env.notify = (r) => notified.push(r);
  return { clock, timers, notified };
}

function dep(now, leadMin, minsAway) {
  return {
    lineId: 'M1', stationId: 'M1_VIC', stationName: 'Victoria', destination: 'Kifisia',
    scheduledTime: '08:00',
    departureEpochSeconds: now + minsAway * 60,
    leadSeconds: leadMin * 60,
    createdAt: '2026-01-01T08:00:00Z', schemaVersion: 1,
  };
}

test('add arms a timer that fires at leave-by, notifying once', () => {
  const { clock, timers, notified } = setup();
  const now = Math.floor(clock.t / 1000);
  // departs in 20 min, 15 min lead -> leave-by in 5 min
  RT.add(dep(now, 15, 20));
  assert.equal(RT._timerCount(), 1, 'one timer armed');
  assert.equal(RT.isEnabled(), true, 'adding opts in');

  timers.advance(4 * 60 * 1000); // +4 min: not yet
  assert.equal(notified.length, 0);
  timers.advance(1 * 60 * 1000 + 500); // +5 min total: fires
  assert.equal(notified.length, 1, 'fired at leave-by');
  assert.equal(notified[0].lineId, 'M1');
  assert.equal(RT._timerCount(), 0, 'timer consumed');
});

test('remove cancels the pending timer', () => {
  const { clock, timers, notified } = setup();
  const now = Math.floor(clock.t / 1000);
  RT.add(dep(now, 15, 20));
  const id = 'M1|M1_VIC|' + (now + 20 * 60);
  assert.equal(RT.contains(id), true);
  RT.remove(id);
  assert.equal(RT._timerCount(), 0, 'timer cancelled');
  timers.advance(10 * 60 * 1000);
  assert.equal(notified.length, 0, 'nothing fires after removal');
});

test('opt-in off cancels all timers; on re-arms', () => {
  const { clock } = setup();
  const now = Math.floor(clock.t / 1000);
  RT.add(dep(now, 15, 20));
  assert.equal(RT._timerCount(), 1);
  RT.setEnabled(false);
  assert.equal(RT._timerCount(), 0, 'disabling cancels');
  RT.setEnabled(true);
  assert.equal(RT._timerCount(), 1, 're-enabling re-arms from the board');
});

test('a leave-by already in the past is never armed', () => {
  const { clock } = setup();
  const now = Math.floor(clock.t / 1000);
  // departs in 5 min, 15 min lead -> leave-by 10 min ago
  RT.add(dep(now, 15, 5));
  assert.equal(RT._timerCount(), 0, 'no timer for a past leave-by');
});

test('sync prunes departed entries from the board', () => {
  const { clock, timers } = setup();
  const now = Math.floor(clock.t / 1000);
  RT.add(dep(now, 15, 20));
  assert.equal(RT.list().length, 1);
  timers.advance(21 * 60 * 1000); // train has now departed
  RT.syncNow();
  assert.equal(RT.list().length, 0, 'departed entry pruned');
});
