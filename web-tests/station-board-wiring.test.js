'use strict';
// Static guardrails for the web station-complex board.
//
// The board's behaviour is covered by station-board.test.js against the shared
// fixture. These tests protect the WIRING, which no pure test can see: the
// module has to be loaded and precached, the service worker has to hand a
// returning installation the new shell, and the defects the board replaced must
// not creep back into web-map.js.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp/src/wasmJsMain/resources');
const read = (f) => fs.readFileSync(path.join(RES, f), 'utf8');
const html = read('index.html');
const map = read('web-map.js');
const sw = read('sw.js');
const css = read('web-map.css');

test('index.html loads the board module before web-map.js', () => {
  const board = html.indexOf('/web-station-board.js');
  const mapTag = html.indexOf('src="/web-map.js"');
  assert.ok(board > 0, 'web-station-board.js is loaded');
  assert.ok(board < mapTag, 'it must be defined before web-map.js calls it');
});

test('the service worker precaches the board module and the registry', () => {
  assert.match(sw, /"\/web-station-board\.js"/);
  assert.match(sw, /"\/files\/seed\/station-complexes\.json"/);
});

test('the service worker version was bumped so installed workers update', () => {
  // A returning user runs the OLD worker until the version changes. Without a
  // bump they would keep the cached single-direction shell.
  const m = /const VERSION = "v(\d+)"/.exec(sw);
  assert.ok(m, 'the worker declares a version');
  assert.ok(Number(m[1]) >= 4, `expected at least v4, found v${m[1]}`);
});

test('the single-destination hero is gone', () => {
  // The old hero chose data.deps[0] and printed deps.slice(1, 3) as an
  // unlabelled "then" tail, which is exactly what hid a second direction.
  // Comments are stripped first so the explanation of the defect is not itself
  // mistaken for the defect.
  const code = map.replace(/^\s*\/\/.*$/gm, '');
  assert.doesNotMatch(code, /data\.deps\[0\]/);
  assert.doesNotMatch(code, /deps\.slice\(1,\s*3\)/);
  assert.doesNotMatch(html, /id="heroDestination"/);
  assert.doesNotMatch(html, /id="heroCountdown"/);
});

test('station-wide source exclusivity is gone', () => {
  // buildStationDepartures used to return the published suburban timetable
  // whenever it had any rows, suppressing both metro directions.
  assert.doesNotMatch(map, /const real = realTimetableDepartures\(station\);/);
  assert.doesNotMatch(map, /realTimetableDepartures/);
  assert.match(map, /function buildStationDepartures\(station\) \{\s*\n\s*const board = buildComplexBoard\(/);
});

test('directions are never invented by result index', () => {
  // The old fallback alternated terminalA / terminalB by index parity.
  assert.doesNotMatch(map, /\(i - before\) % 2 === 0 \? line\?\.terminalB/);
  assert.ok(map.includes('lineDirectionsAt'), 'directions come from the line stop order');
  assert.ok(map.includes('tripBoarding'), 'trip destinations come from the trip stop times');
});

test('the board caps presentation, never enumeration', () => {
  // A cap applied before grouping is how a destination disappears. The only
  // caps left are per group.
  assert.doesNotMatch(map, /\.slice\(0, 10\);\s*\n\s*\}\s*\n\s*function vehicleIconFor/);
  assert.match(map, /maxTimesPerGroup/);
});

test('Ariadne answers from the same board, not a second calculation', () => {
  const start = map.indexOf('async function departuresSummary');
  assert.ok(start > 0, 'departuresSummary exists');
  const body = map.slice(start, start + 2600);
  assert.ok(body.includes('buildComplexBoard'), 'it builds the shared board');
  assert.doesNotMatch(body, /fetchApiDepartures/);
});

test('the board explicitly pins an rider-chosen station over location', () => {
  assert.match(map, /pinnedBoardNodeId/);
  const fn = map.slice(map.indexOf('function boardStation()'), map.indexOf('function refreshData()'));
  assert.ok(fn.indexOf('pinnedBoardNodeId') < fn.indexOf('userLocation'),
    'an explicit choice is consulted before location');
});

test('board rows are keyed by stable group id, never by array offset', () => {
  const start = map.indexOf('function renderRows(container)');
  const body = map.slice(start, start + 2400);
  assert.ok(body.includes('map.get(group.id)'), 'rows are looked up by group id');
  assert.ok(body.includes('el.dataset.groupId = group.id'));
});

test('the countdown tick does not rebuild or reorder rows', () => {
  const start = map.indexOf('function tickCountdowns()');
  const body = map.slice(start, map.indexOf('function openGroup('));
  assert.doesNotMatch(body, /innerHTML/);
  assert.doesNotMatch(body, /insertBefore|appendChild|sort\(/);
});

test('every board string exists in all four supported languages', () => {
  const keys = [
    'board_all_directions', 'board_all_departures', 'board_partial',
    'board_no_departure_window', 'board_unavailable', 'board_not_operating',
    'board_next_beyond', 'board_services',
  ];
  for (const key of keys) {
    const hits = map.match(new RegExp('\\n\\s+' + key + ':', 'g')) || [];
    assert.equal(hits.length, 4, `${key} must exist in en, el, sq and it (found ${hits.length})`);
  }
});

test('destinations wrap at word boundaries rather than mid-word', () => {
  // `overflow-wrap: anywhere` broke "Leianokladi" into "Leia / nokl / adi".
  const block = css.slice(css.indexOf('.board-row__dest'), css.indexOf('.board-row__dest') + 320);
  assert.match(block, /overflow-wrap:\s*break-word/);
  assert.doesNotMatch(block, /overflow-wrap:\s*anywhere/);
});
