'use strict';
// 3.0.2 station closures. Drives every case in fixtures/closures/effect.json
// through the web engine and asserts exact equality. The server closure_effect(),
// Kotlin StationClosures and Swift StationClosures assert the SAME cases, so the
// window and per-station effect cannot drift between clients.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const C = require(path.join(RES, 'web-closures.js'));
const fixture = JSON.parse(fs.readFileSync(
  path.join(__dirname, '..', 'fixtures', 'closures', 'effect.json'), 'utf8'
));

for (const c of fixture.cases) {
  test(`closure effect: ${c.id}`, () => {
    assert.deepEqual(C.effect(fixture.closures, c.stationId, c.lineId, c.now), c.expected);
  });
}

test('the fixture still covers every rule it was written for', () => {
  const ids = fixture.cases.map((c) => c.id);
  for (const id of ['before_start_upcoming', 'at_start_active', 'end_is_exclusive', 'draft_ignored',
    'exit_plus_entry_is_closed', 'line_scoped_other_line_unaffected', 'line_null_means_any_line']) {
    assert.ok(ids.includes(id), `fixture lost case ${id}`);
  }
});

const T0 = 1793167200; // 2026-10-28 08:00 Athens

test('lineOfStationId reads the line prefix only when the node has that line', () => {
  assert.equal(C.lineOfStationId('M2_SYN', ['M2', 'M3', 'T6']), 'M2');
  assert.equal(C.lineOfStationId('THS_AIR', ['X96']), null);
  assert.equal(C.lineOfStationId('M3_SYN', []), null);
});

test('nodeEffect: Syntagma pass-through blocks the metro, the tram keeps boarding', () => {
  const pairs = C.pairsFor(['M2_SYN', 'M3_SYN', 'T6_SYN'], ['M2', 'M3', 'T6']);
  const e = C.nodeEffect(fixture.closures, pairs, T0 + 60);
  assert.deepEqual(e.blockedLineIds.sort(), ['M2', 'M3']);
  assert.equal(e.primary.closureId, 'syn-parade');
  assert.equal(e.primary.mode, 'pass_through');
  assert.equal(e.allBlocked, false);
  assert.equal(e.allClosed, false);
  assert.equal(e.upcoming, null);
});

test('nodeEffect: metro-only Syntagma node is fully closed and greys', () => {
  const e = C.nodeEffect(fixture.closures, C.pairsFor(['M2_SYN', 'M3_SYN'], ['M2', 'M3']), T0);
  assert.equal(e.allBlocked, true);
  assert.equal(e.allClosed, true);
});

test('nodeEffect: an hour before reports the upcoming closure only', () => {
  const e = C.nodeEffect(fixture.closures, C.pairsFor(['M2_SYN'], ['M2']), T0 - 3600);
  assert.equal(e.primary, null);
  assert.equal(e.upcoming.upcomingClosureId, 'syn-parade');
  assert.equal(e.upcoming.upcomingStartsAtEpochSeconds, T0);
  assert.deepEqual(e.blockedLineIds, []);
});

test('nodeEffect: exit_only blocks boarding but the station is not greyed', () => {
  const e = C.nodeEffect(fixture.closures, C.pairsFor(['M3_NIK'], ['M3']), T0 + 60);
  assert.deepEqual(e.blockedLineIds, ['M3']);
  assert.equal(e.allBlocked, true);
  assert.equal(e.allClosed, false);
});

test('nodeEffect: no pairs is never "closed"', () => {
  const e = C.nodeEffect(fixture.closures, [], T0);
  assert.equal(e.allBlocked, false);
  assert.equal(e.allClosed, false);
});

test('filterDepartures drops blocked lines, M3_AIR follows M3', () => {
  const groups = [{ lineId: 'M2' }, { lineId: 'M3_AIR' }, { line: { id: 'T6' } }, { lineId: 'M3' }];
  assert.deepEqual(C.filterDepartures(groups, ['M2', 'M3']), [{ line: { id: 'T6' } }]);
  assert.equal(C.filterDepartures(groups, []).length, 4);
});

function ride(lineId, fromId, toId) { return { kind: 'ride', lineId, fromId, toId }; }

test('planner: no boarding at a pass-through station, riding through it is fine', () => {
  const boardAtSyn = { id: 'a', legs: [ride('M3', 'M3_SYN', 'M3_NIK')] };
  const alightAtEva = { id: 'b', legs: [ride('M3', 'M3_MON', 'M3_EVA')] };
  const through = { id: 'c', legs: [ride('M3', 'M3_MON', 'M3_KER')] }; // rides past Syntagma
  const r = C.filterOptions([boardAtSyn, alightAtEva, through], fixture.closures, T0 + 60);
  assert.deepEqual(r.options.map((o) => o.id), ['c']);
  assert.equal(r.removed, 2);
  assert.deepEqual(r.blockedStationIds.sort(), ['M3_EVA', 'M3_SYN']);
});

test('planner: exit_only allows alighting, entry_only allows boarding', () => {
  const alightNik = { legs: [ride('M3', 'M3_MON', 'M3_NIK')] };
  const boardNik = { legs: [ride('M3', 'M3_NIK', 'M3_MON')] };
  assert.deepEqual(C.optionViolations(alightNik, fixture.closures, T0 + 60), []);
  assert.deepEqual(C.optionViolations(boardNik, fixture.closures, T0 + 60), ['M3_NIK']);
  assert.deepEqual(C.optionViolations(boardNik, fixture.closures, T0 + 3600), []);
  assert.deepEqual(C.optionViolations(alightNik, fixture.closures, T0 + 3600), ['M3_NIK']);
});

test('planner: transfer legs are ignored and an unaffected route is kept', () => {
  const ok = { legs: [ride('M1', 'M1_KIF', 'M1_OMO'), { kind: 'transfer', fromId: 'M1_OMO', toId: 'M2_OMO' }, ride('M2', 'M2_OMO', 'M2_AKR')] };
  const r = C.filterOptions([ok], fixture.closures, T0 + 60);
  assert.equal(r.options.length, 1);
  assert.equal(r.removed, 0);
});

test('pickText: app language, then en, then el, then anything', () => {
  const o = { el: 'Με εντολή της Αστυνομίας', en: 'By police order' };
  assert.equal(C.pickText(o, 'el'), 'Με εντολή της Αστυνομίας');
  assert.equal(C.pickText(o, 'it'), 'By police order');
  assert.equal(C.pickText({ el: 'Μόνο ελληνικά' }, 'sq'), 'Μόνο ελληνικά');
  assert.equal(C.pickText({ sq: 'Vetëm shqip' }, 'en'), 'Vetëm shqip');
  assert.equal(C.pickText(null, 'en'), '');
  assert.equal(C.pickText('plain', 'en'), 'plain');
});

test('athensLabel is Europe/Athens wall-clock, independent of the device zone', () => {
  assert.equal(C.athensLabel(T0, T0, 'en'), '08:00');
  assert.equal(C.athensLabel(T0 + 8 * 3600, T0, 'en'), '16:00');
  // A different Athens day carries the date.
  assert.match(C.athensLabel(T0, T0 - 86400, 'en'), /28 Oct 08:00/);
  // 23:30Z on 27 Oct is already 01:30 on the 28th in Athens.
  assert.match(C.athensLabel(1793143800, T0, 'en'), /^01:30$/);
});

test('windowText: range, until further notice, and a future open-ended start', () => {
  const L = { range: '{from} to {to}', untilFurther: 'until further notice', fromUntilFurther: 'from {from}, until further notice' };
  assert.equal(C.windowText(T0, T0 + 8 * 3600, T0 + 60, 'en', L), '08:00 to 16:00');
  assert.equal(C.windowText(T0, null, T0 + 60, 'en', L), 'until further notice');
  assert.equal(C.windowText(T0, null, T0 - 3600, 'en', L), 'from 08:00, until further notice');
});

test('parsePayload keeps well-formed closures only', () => {
  const good = fixture.closures[0];
  const list = C.parsePayload({ closures: [good, { id: 'x' }, { ...good, id: 'bad-mode', mode: 'nope' }, null] });
  assert.deepEqual(list.map((c) => c.id), ['syn-parade']);
  assert.deepEqual(C.parsePayload(null), []);
  assert.equal(C.findClosure(fixture.closures, 'eva-works').mode, 'closed');
  assert.equal(C.findClosure(fixture.closures, 'missing'), null);
});

test('filterDeparturesAt checks each time at its own departure minute', () => {
  const C = require('../composeApp/src/wasmJsMain/resources/web-closures.js');
  const T0 = 1793167200; // 08:00 Athens
  const closures = [{ id: 'syn', stationIds: ['M2_SYN', 'M3_SYN'], lineIds: [], mode: 'pass_through',
    startsAtEpochSeconds: T0, endsAtEpochSeconds: T0 + 2 * 3600, status: 'scheduled' }];
  const pairs = [{ stationId: 'M2_SYN', lineId: 'M2' }, { stationId: 'M3_SYN', lineId: 'M3' }, { stationId: 'T6_SYN', lineId: 'T6' }];
  const groups = [
    { lineId: 'M3', times: [{ minutesAway: 5 }, { minutesAway: 15 }] },
    { lineId: 'T6', times: [{ minutesAway: 12 }] },
  ];
  // 07:50: the 07:55 train stays, the 08:05 one is already hidden; the tram is untouched.
  const before = C.filterDeparturesAt(groups, closures, pairs, T0 - 600);
  assert.deepEqual(before.map((g) => [g.lineId, g.times.map((t) => t.minutesAway)]), [['M3', [5]], ['T6', [12]]]);
  // 09:50: nothing boardable until 10:00, the 10:05 train shows again.
  const late = C.filterDeparturesAt(groups, closures, pairs, T0 + 2 * 3600 - 600);
  assert.deepEqual(late.map((g) => [g.lineId, g.times.map((t) => t.minutesAway)]), [['M3', [15]], ['T6', [12]]]);
  // M3_AIR follows M3, and no closures means no change.
  assert.equal(C.boardableAt(closures, pairs, 'M3_AIR', T0 + 60), false);
  assert.equal(C.filterDeparturesAt(groups, [], pairs, T0), groups);
});
