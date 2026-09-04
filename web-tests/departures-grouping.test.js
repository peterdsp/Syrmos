'use strict';
// Grouping the station/airport departure board: consecutive departures that
// share a (line, destination) collapse into one group carrying the next few
// times, killing the repeated "Line 3 · Scheduled" rows. Pins the pure
// transform so iOS/Android can mirror it.
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const D = require(path.join(
  __dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources', 'web-departures.js'
));

const dep = (o) => Object.assign({ minutesAway: 0, time: '', source: 'scheduled', sourceLabel: 'Scheduled' }, o);
const L3 = { id: 'M3', name: 'Line 3', color: '#e64125' };

test('collapses same line+destination into one group with ordered times', () => {
  const g = D.groupDepartures([
    dep({ line: L3, destination: 'Doukissis Plakentias', minutesAway: 4, time: '12:31' }),
    dep({ line: L3, destination: 'Doukissis Plakentias', minutesAway: 12, time: '12:39' }),
    dep({ line: L3, destination: 'Doukissis Plakentias', minutesAway: 22, time: '12:49' }),
  ]);
  assert.equal(g.length, 1, 'three same-destination rows become one group');
  assert.equal(g[0].destination, 'Doukissis Plakentias');
  assert.equal(g[0].lineId, 'M3');
  assert.deepEqual(g[0].times.map((t) => t.minutesAway), [4, 12, 22]);
  assert.deepEqual(g[0].times.map((t) => t.time), ['12:31', '12:39', '12:49']);
  assert.equal(g[0].total, 3);
  assert.equal(g[0].moreCount, 0);
});

test('distinct destinations on the same line stay separate groups, soonest-first', () => {
  const g = D.groupDepartures([
    dep({ line: L3, destination: 'Airport', minutesAway: 4 }),
    dep({ line: L3, destination: 'Dimotiko Theatro', minutesAway: 6 }),
    dep({ line: L3, destination: 'Airport', minutesAway: 22 }),
  ]);
  assert.equal(g.length, 2, 'two destinations => two groups');
  assert.equal(g[0].destination, 'Airport', 'group ordered by its soonest member');
  assert.deepEqual(g[0].times.map((t) => t.minutesAway), [4, 22], 'non-adjacent members still merge');
  assert.equal(g[1].destination, 'Dimotiko Theatro');
});

test('destination key folds case and whitespace', () => {
  const g = D.groupDepartures([
    dep({ line: L3, destination: 'Airport', minutesAway: 4 }),
    dep({ line: L3, destination: 'airport ', minutesAway: 9 }),
  ]);
  assert.equal(g.length, 1);
  assert.equal(g[0].destination, 'Airport', 'keeps the first original spelling for display');
  assert.deepEqual(g[0].times.map((t) => t.minutesAway), [4, 9]);
});

test('maxTimes caps the visible times and reports the remainder', () => {
  const g = D.groupDepartures([
    dep({ line: L3, destination: 'Kifissia', minutesAway: 2 }),
    dep({ line: L3, destination: 'Kifissia', minutesAway: 9 }),
    dep({ line: L3, destination: 'Kifissia', minutesAway: 16 }),
    dep({ line: L3, destination: 'Kifissia', minutesAway: 24 }),
  ], { maxTimes: 3 });
  assert.deepEqual(g[0].times.map((t) => t.minutesAway), [2, 9, 16]);
  assert.equal(g[0].moreCount, 1);
  assert.equal(g[0].total, 4);
});

test('maxTimes <= 0 keeps every time', () => {
  const g = D.groupDepartures([
    dep({ line: L3, destination: 'Kifissia', minutesAway: 2 }),
    dep({ line: L3, destination: 'Kifissia', minutesAway: 9 }),
  ], { maxTimes: 0 });
  assert.equal(g[0].times.length, 2);
  assert.equal(g[0].moreCount, 0);
});

test('group takes the strongest confidence among its members (live wins)', () => {
  const g = D.groupDepartures([
    dep({ line: L3, destination: 'Airport', minutesAway: 8, source: 'scheduled', sourceLabel: 'Scheduled' }),
    dep({ line: L3, destination: 'Airport', minutesAway: 3, source: 'live', sourceLabel: 'Live' }),
  ]);
  assert.equal(g[0].source, 'live', 'one live ETA is never hidden behind a scheduled sibling');
  assert.equal(g[0].sourceLabel, 'Live');
});

test('different lines never merge even with the same destination', () => {
  const A2 = { id: 'A2', name: 'Line 2', color: '#e2231a' };
  const g = D.groupDepartures([
    dep({ line: L3, destination: 'Piraeus', minutesAway: 5 }),
    dep({ line: A2, destination: 'Piraeus', minutesAway: 6 }),
  ]);
  assert.equal(g.length, 2);
  assert.deepEqual(g.map((x) => x.lineId), ['M3', 'A2']);
});

test('carries serviceType so the renderer can still show the Airport pill', () => {
  const g = D.groupDepartures([
    dep({ line: L3, destination: 'Airport', minutesAway: 4, serviceType: 'airport' }),
    dep({ line: L3, destination: 'Airport', minutesAway: 14, serviceType: 'airport' }),
  ]);
  assert.equal(g[0].serviceType, 'airport');
});

test('tolerates empty / malformed input and never mutates it', () => {
  assert.deepEqual(D.groupDepartures([]), []);
  assert.deepEqual(D.groupDepartures(null), []);
  const input = [dep({ line: L3, destination: 'Kifissia', minutesAway: 2 })];
  const snapshot = JSON.stringify(input);
  D.groupDepartures(input);
  assert.equal(JSON.stringify(input), snapshot, 'input array/objects untouched');
});
