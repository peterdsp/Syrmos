'use strict';
// Golden-fixture and unit coverage for the station-complex all-directions board.
//
// The fixture in fixtures/station-board/athens-all-directions.json is
// language-neutral: the Swift `StationComplexBoardFixtureTests` and the Kotlin
// `StationComplexBoardFixtureTest` assert the same expectations against the same
// inputs, so the three clients cannot drift apart on grouping, ordering,
// deduplication or coverage.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const Board = require('../composeApp/src/wasmJsMain/resources/web-station-board.js');

const ROOT = path.join(__dirname, '..');
const fixture = JSON.parse(
  fs.readFileSync(path.join(ROOT, 'fixtures/station-board/athens-all-directions.json'), 'utf8'),
);
const registry = JSON.parse(
  fs.readFileSync(
    path.join(ROOT, 'core/data/src/commonMain/composeResources/files/seed/station-complexes.json'),
    'utf8',
  ),
);
const lines = JSON.parse(
  fs.readFileSync(path.join(ROOT, 'iosApp/iosApp/Resources/seed-schedules-v2/lines.json'), 'utf8'),
).lines;

// ---------------------------------------------------------------- the fixture

for (const c of fixture.cases) {
  test(`fixture: ${c.id}`, () => {
    const board = Board.buildBoard({
      complex: fixture.complex,
      departures: c.input.departures,
      coverage: c.input.coverage,
      windowMinutes: c.input.windowMinutes,
      maxTimesPerGroup: c.input.maxTimesPerGroup,
    });

    assert.equal(board.partial, c.expect.partial, 'partial coverage flag');
    assert.equal(board.timedGroupCount, c.expect.timedGroupCount, 'timed group count');
    assert.equal(board.groups.length, c.expect.groups.length, 'group count');

    c.expect.groups.forEach((want, i) => {
      const got = board.groups[i];
      const where = `${c.id} group ${i} (${want.destination})`;
      assert.equal(got.id, want.id, `${where}: stable group id`);
      assert.equal(got.destination, want.destination, `${where}: destination`);
      if (want.lineId !== undefined) assert.equal(got.lineId, want.lineId, `${where}: lineId`);
      if (want.stopId !== undefined) assert.equal(got.stopId, want.stopId, `${where}: boarding stop`);
      assert.deepEqual(got.times.map((t) => t.absoluteMinutes), want.times, `${where}: times`);
      if (want.nextMinutes !== undefined) {
        assert.ok(got.next, `${where}: expected a next departure`);
        assert.equal(got.next.absoluteMinutes, want.nextMinutes, `${where}: next departure`);
      } else {
        assert.equal(got.next, null, `${where}: expected no next departure`);
      }
      if (want.total !== undefined) assert.equal(got.total, want.total, `${where}: total`);
      if (want.moreCount !== undefined) assert.equal(got.moreCount, want.moreCount, `${where}: moreCount`);
      if (want.coverage !== undefined) assert.equal(got.coverage, want.coverage, `${where}: coverage`);
      if (want.source !== undefined) assert.equal(got.source, want.source, `${where}: source`);
      if (want.cancelledTimes !== undefined) {
        assert.deepEqual(got.times.map((t) => t.cancelled), want.cancelledTimes, `${where}: per-time cancellation`);
      }
      if (want.timeSources !== undefined) {
        assert.deepEqual(got.times.map((t) => t.source), want.timeSources, `${where}: per-time source`);
      }
      if (want.beyondWindow !== undefined) {
        assert.deepEqual(got.times.map((t) => t.beyondWindow), want.beyondWindow, `${where}: beyond-window flag`);
      }
    });
  });
}

// ------------------------------------------------------ the reviewed registry

test('registry: Athens joins the metro stop and all four railway platforms', () => {
  const athens = Board.complexForStop('M2_STA', registry);
  assert.ok(athens, 'M2_STA belongs to a complex');
  assert.equal(athens.id, 'CPX_ATHENS_LARISSA');
  assert.deepEqual(Board.memberStopIds(athens), ['M2_STA', 'A1_ATH', 'A3_ATH', 'A4_ATH', 'GR_ATH']);
  // Every railway platform resolves to the same complex, so opening any of them
  // opens the whole station.
  for (const id of ['A1_ATH', 'A3_ATH', 'A4_ATH', 'GR_ATH']) {
    assert.equal(Board.complexForStop(id, registry).id, 'CPX_ATHENS_LARISSA', id);
  }
});

test('registry: Athens exposes the real supported services, and only those', () => {
  const athens = Board.complexForStop('M2_STA', registry);
  const services = Board.resolveComplexServices(athens, lines);
  const pairs = services.map((s) => `${s.stopId}:${s.lineId}`).sort();
  assert.deepEqual(pairs, [
    'A1_ATH:A1',
    'A3_ATH:A3',
    'A4_ATH:A4',
    'GR_ATH:IC1',
    'GR_ATH:RG1',
    'M2_STA:M2',
  ]);
  // The metro stop never carries a railway service: the seed's interchange
  // union used to route A1 lookups to M2_STA.
  const metro = services.filter((s) => s.stopId === 'M2_STA').map((s) => s.lineId);
  assert.deepEqual(metro, ['M2']);
});

test('registry: reviewed splits keep genuinely distinct neighbours apart', () => {
  // Faliro, SEF and Gipedo Karaiskaki sit within 180 m but are three stations.
  assert.equal(Board.complexForStop('M1_FAL', registry), null);
  assert.equal(Board.complexForStop('T7_PEA', registry), null);
  assert.equal(Board.complexForStop('T7_GIP', registry), null);
  // SKA Acharnon stays separate from the Kato Acharnai platforms 61 m away.
  assert.equal(Board.complexForStop('GR_SKA', registry), null);
  const kato = Board.complexForStop('A1_KAT', registry);
  assert.deepEqual(Board.memberStopIds(kato), ['A1_KAT', 'A4_KAT']);
});

test('registry: every member stop of every complex really boards a line', () => {
  const byStop = new Map();
  for (const line of lines) for (const s of line.stations || []) {
    if (!byStop.has(s.id)) byStop.set(s.id, []);
    byStop.get(s.id).push(line.id);
  }
  for (const c of registry.complexes) {
    assert.ok(c.areas.length > 0, `${c.id} has boarding areas`);
    for (const area of c.areas) {
      for (const stopId of area.stopIds) {
        assert.ok((byStop.get(stopId) || []).length > 0, `${c.id}/${stopId} boards at least one line`);
      }
    }
  }
});

test('registry: no stop belongs to two complexes', () => {
  const seen = new Map();
  for (const c of registry.complexes) {
    for (const stopId of Board.memberStopIds(c)) {
      assert.equal(seen.get(stopId), undefined, `${stopId} is claimed by ${seen.get(stopId)} and ${c.id}`);
      seen.set(stopId, c.id);
    }
  }
});

// ------------------------------------------------------------- unit coverage

test('dedupe: two records for one trip collapse; two trips in one minute do not', () => {
  const out = Board.dedupeDepartures([
    { stopId: 'A1_ATH', lineId: 'A1', destination: 'Airport', tripId: 'T1', serviceDate: 'D', absoluteMinutes: 5, source: 'scheduled' },
    { stopId: 'A1_ATH', lineId: 'A1', destination: 'Airport', tripId: 'T1', serviceDate: 'D', absoluteMinutes: 7, source: 'live' },
    { stopId: 'A1_ATH', lineId: 'A1', destination: 'Airport', tripId: 'T2', serviceDate: 'D', absoluteMinutes: 7, source: 'scheduled' },
  ]);
  assert.equal(out.length, 2);
  assert.equal(out[0].tripId, 'T1');
  assert.equal(out[0].source, 'live', 'the higher-confidence record wins the time');
  assert.equal(out[0].absoluteMinutes, 7);
  assert.equal(out[1].tripId, 'T2');
});

test('dedupe: without trip ids, the exact minute keeps two real trains apart', () => {
  const out = Board.dedupeDepartures([
    { stopId: 'M2_STA', lineId: 'M2', destination: 'Elliniko', absoluteMinutes: 4, source: 'estimated' },
    { stopId: 'M2_STA', lineId: 'M2', destination: 'Elliniko', absoluteMinutes: 5, source: 'estimated' },
    { stopId: 'M2_STA', lineId: 'M2', destination: 'Elliniko', absoluteMinutes: 5, source: 'offline' },
  ]);
  assert.equal(out.length, 2, 'same minute + same destination + same stop is one departure');
  assert.deepEqual(out.map((d) => d.absoluteMinutes), [4, 5]);
  assert.equal(out[1].source, 'estimated', 'the higher-confidence duplicate survives');
});

test('dedupe: destination alone never merges two departures', () => {
  const out = Board.dedupeDepartures([
    { stopId: 'A1_ATH', lineId: 'A1', destination: 'Airport', absoluteMinutes: 5, source: 'scheduled' },
    { stopId: 'A2_ATH', lineId: 'A2', destination: 'Airport', absoluteMinutes: 5, source: 'scheduled' },
  ]);
  assert.equal(out.length, 2);
});

test('board: group ids are stable while rows reorder', () => {
  const base = {
    complex: fixture.complex,
    coverage: [],
    windowMinutes: 720,
    maxTimesPerGroup: 3,
  };
  const before = Board.buildBoard(Object.assign({}, base, {
    departures: [
      { stopId: 'M2_STA', areaId: 'metro', lineId: 'M2', destination: 'Elliniko', tripId: 'E1', absoluteMinutes: 1, source: 'estimated', boardsHere: true },
      { stopId: 'M2_STA', areaId: 'metro', lineId: 'M2', destination: 'Anthoupoli', tripId: 'A1', absoluteMinutes: 4, source: 'estimated', boardsHere: true },
    ],
  }));
  const after = Board.buildBoard(Object.assign({}, base, {
    departures: [
      { stopId: 'M2_STA', areaId: 'metro', lineId: 'M2', destination: 'Anthoupoli', tripId: 'A1', absoluteMinutes: 2, source: 'estimated', boardsHere: true },
      { stopId: 'M2_STA', areaId: 'metro', lineId: 'M2', destination: 'Elliniko', tripId: 'E2', absoluteMinutes: 9, source: 'estimated', boardsHere: true },
    ],
  }));
  assert.deepEqual(before.groups.map((g) => g.destination), ['Elliniko', 'Anthoupoli']);
  assert.deepEqual(after.groups.map((g) => g.destination), ['Anthoupoli', 'Elliniko']);
  // The rows swapped, but the identity a renderer keys on did not change.
  const idOf = (b, dest) => b.groups.find((g) => g.destination === dest).id;
  assert.equal(idOf(before, 'Elliniko'), idOf(after, 'Elliniko'));
  assert.equal(idOf(before, 'Anthoupoli'), idOf(after, 'Anthoupoli'));
});

test('board: folding matches accented and unaccented destinations, and nothing else', () => {
  assert.equal(Board.fold('Ελληνικό'), Board.fold('Ελληνικο'));
  assert.equal(Board.fold(' Airport '), 'airport');
  assert.notEqual(Board.fold('Kifissia'), Board.fold('Kifisias'));
});

test('board: localized complex and area names cover the four supported languages', () => {
  const athens = Board.complexForStop('M2_STA', registry);
  assert.equal(Board.complexName(athens, 'en'), 'Athens · Larissa Station');
  assert.equal(Board.complexName(athens, 'el'), 'Αθήνα · Σταθμός Λαρίσης');
  assert.equal(Board.complexName(athens, 'sq'), 'Athinë · Stacioni Larisa');
  assert.equal(Board.complexName(athens, 'it'), 'Atene · Stazione Larissa');
  assert.equal(Board.areaName(athens, 'rail', 'el'), 'Σιδηροδρομικός σταθμός');
  assert.equal(Board.areaName(athens, 'metro', 'it'), 'Metropolitana');
});

test('board: an empty input is an empty board, not a crash', () => {
  const board = Board.buildBoard({ complex: fixture.complex, departures: [], coverage: [] });
  assert.equal(board.groups.length, 0);
  assert.equal(board.partial, false);
  assert.equal(board.timedGroupCount, 0);
});
