'use strict';
// 3.0 Phase P: the web planning adapter composes the topology planner into ranked
// JourneyOptions with honest estimated timing and computed feasibility, over the
// real bundled seed. Proves the planner->option->feasibility->ranker wire.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const SEED = path.join(__dirname, '..', 'core', 'data', 'src', 'commonMain', 'composeResources', 'files', 'seed');
const Plan = require(path.join(RES, 'web-journey-plan.js'));

const readJson = (p) => JSON.parse(fs.readFileSync(p, 'utf8'));
const stations = readJson(path.join(SEED, 'stations.json'));
const linesRaw = readJson(path.join(SEED, 'lines.json'));
const lines = Array.isArray(linesRaw) ? linesRaw : linesRaw.lines;
const lineById = (id) => lines.find((l) => l.id === id);

test('a cross-line request yields one ranked option with a transfer, honestly estimated', () => {
  const m1 = lineById('M1'), m2 = lineById('M2');
  const from = m1.stations[0].id;
  const to = m2.stations[m2.stations.length - 1].id;
  const { options } = Plan.plan(stations, lines, { fromStationId: from, toStationId: to, ranking: 'fastest' });

  assert.equal(options.length, 1, 'one real option, not padded to three');
  const opt = options[0];
  assert.ok(opt.transferCount >= 1, 'M1->M2 needs a transfer');
  assert.ok(opt.legs.some((l) => l.kind === 'transfer'), 'has an explicit transfer leg');
  // Honest timing: no fabricated clocks.
  for (const leg of opt.legs) {
    assert.equal(leg.timingKind, 'estimated');
    assert.equal(leg.departureInstant, null, 'no invented departure clock');
    assert.equal(leg.arrivalInstant, null, 'no invented arrival clock');
  }
  assert.equal(opt.arrivalInstant, null);
  assert.ok(opt.durationSeconds > 0, 'a disclosed duration estimate');
  assert.equal(opt.walkingSeconds, null, 'walking is unknown, never invented');
  // Feasibility is unknown for a transfer we cannot time; never a fake success score.
  assert.equal(opt.feasibility.status, 'unknown');
  assert.equal(opt.feasibility.minimumMarginSeconds, null);
  assert.equal(opt.rankingBadge, 'fastest');
});

test('a same-line request is a single ride, feasibility direct', () => {
  const m1 = lineById('M1');
  const { options } = Plan.plan(stations, lines, {
    fromStationId: m1.stations[0].id, toStationId: m1.stations[3].id, ranking: 'fastest',
  });
  assert.equal(options.length, 1);
  const opt = options[0];
  assert.equal(opt.transferCount, 0);
  assert.equal(opt.legs.filter((l) => l.kind === 'ride').length, 1, 'one contiguous ride');
  assert.equal(opt.feasibility.status, 'comfortable');
  assert.equal(opt.feasibility.explanationCode, 'direct');
});

test('degenerate requests return no options, never a filler route', () => {
  const m1 = lineById('M1');
  assert.deepEqual(Plan.plan(stations, lines, { fromStationId: m1.stations[0].id, toStationId: m1.stations[0].id }).options, []);
  assert.deepEqual(Plan.plan(stations, lines, { fromStationId: 'nope', toStationId: 'nada' }).options, []);
});
