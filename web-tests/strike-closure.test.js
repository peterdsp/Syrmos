'use strict';
// The 30 October 2026 Hellenic Train strike, as it was actually posted to
// production, run through the web closure rule.
//
// The subtlety this pins: the closure is STATION-scoped with an empty lineIds.
// Station ids on the national network are geographic (GR_LAR, EV_ALX, PA_KAM),
// not <LINE>_<CODE>, so a line-scoped closure would resolve the "line" to GR /
// EV / PA and apply to nothing. Anyone tempted to tidy this closure up by
// naming its lines would silently un-grey 201 stations.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const closuresMod = require(path.join(
  __dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources', 'web-closures.js',
));
const payload = JSON.parse(fs.readFileSync(
  path.join(__dirname, '..', 'fixtures', 'closures', 'strike-hellenic-2026-10-30.json'), 'utf8',
));

const strike = payload.closures[0];
// Noon Athens on the strike day, comfortably inside the window.
const DURING = strike.startsAtEpochSeconds + 12 * 3600;

test('the posted strike is one full closure covering the whole rail network', () => {
  assert.equal(strike.id, 'strike-hellenic-2026-10-30');
  assert.equal(strike.mode, 'closed');
  assert.equal(strike.stationIds.length, 201);
  assert.deepEqual(strike.lineIds, [],
    'station-scoped on purpose: naming lines stops non-Athens stations greying');
});

test('it is active during the day and inert on either side', () => {
  assert.ok(closuresMod.isActive(strike, DURING));
  assert.ok(!closuresMod.isActive(strike, strike.startsAtEpochSeconds - 60));
  assert.ok(!closuresMod.isActive(strike, strike.endsAtEpochSeconds + 60));
});

test('every listed station is unboardable while it runs', () => {
  const notClosed = strike.stationIds.filter(
    (sid) => closuresMod.effect([strike], sid, null, DURING).canBoard,
  );
  assert.deepEqual(notClosed, [], `these stayed boardable: ${notClosed.slice(0, 5)}`);
});

test('geographic station ids are covered, not just the Athens LINE_CODE ones', () => {
  // GR_ / EV_ / PA_ prefixes are regions, not lines. This is the case a
  // line-scoped closure would miss.
  const geographic = strike.stationIds.filter((sid) => /^(GR|EV|PA|KI)_/.test(sid));
  assert.ok(geographic.length > 0, 'the fixture should contain national stations');
  for (const sid of geographic) {
    const lineGuess = closuresMod.lineOfStationId(sid, strike.lineIds);
    assert.equal(lineGuess, null, `${sid} must not resolve to a line`);
    assert.ok(!closuresMod.effect([strike], sid, lineGuess, DURING).canBoard, sid);
  }
});

test('a station nobody closed is untouched', () => {
  assert.ok(closuresMod.effect([strike], 'M3_SYN', 'M3', DURING).canBoard,
    'the metro is not on strike');
});

test('the reason is carried in all four languages', () => {
  for (const lang of ['en', 'el', 'sq', 'it']) {
    assert.ok((strike.reason[lang] || '').length > 20, `missing ${lang} reason`);
  }
});
