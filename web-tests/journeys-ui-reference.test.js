'use strict';
// 3.0 F2: the frozen visual-acceptance fixture for the Journeys screens. Pins
// the invariants the prompt (section 10) fixes so no client, and no later edit,
// silently drifts the synthetic baseline into looking like real transit data.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const FIX = path.join(__dirname, '..', 'fixtures', 'journeys', 'ui-reference.json');
const ref = JSON.parse(fs.readFileSync(FIX, 'utf8'));
const LOCALES = ['en', 'el', 'sq', 'it'];

test('is explicitly test-only and Athens-timed', () => {
  assert.equal(ref.testOnly, true, 'must be flagged test-only, never bundled data');
  assert.equal(ref.serviceTimeZone, 'Europe/Athens');
  assert.equal(ref.frozenClock.instant, '2026-01-15T08:30:00+02:00', 'frozen deterministic clock');
  assert.equal(ref.frozenClock.serviceDate, '2026-01-15');
});

test('uses exactly the three synthetic station ids, localized in all four locales', () => {
  assert.deepEqual(Object.keys(ref.stations).sort(), ['test-destination', 'test-origin', 'test-transfer']);
  for (const [id, s] of Object.entries(ref.stations)) {
    for (const loc of LOCALES) {
      assert.ok(s.labels[loc] && s.labels[loc].trim(), `${id} missing ${loc} label`);
    }
  }
});

test('offers one direct and one single-transfer option, deterministic 08:42-09:12 direct', () => {
  assert.equal(ref.options.length, 2, 'exactly one direct + one transfer option');
  const direct = ref.options.find((o) => o.transferCount === 0);
  const transfer = ref.options.find((o) => o.transferCount === 1);
  assert.ok(direct && transfer, 'need one of each');
  assert.equal(direct.departureInstant, '2026-01-15T08:42:00+02:00');
  assert.equal(direct.arrivalInstant, '2026-01-15T09:12:00+02:00');
  assert.equal(direct.legs.length, 1, 'direct is a single ride leg');
  assert.equal(direct.legs[0].kind, 'ride');
  // Transfer option: ride, transfer, ride.
  assert.deepEqual(transfer.legs.map((l) => l.kind), ['ride', 'transfer', 'ride']);
});

test('feasibility is transparent, not a fabricated success percentage', () => {
  for (const o of ref.options) {
    assert.ok(['comfortable', 'tight', 'missed', 'unknown'].includes(o.feasibility.status),
      'feasibility status must be one of the four honest states');
    assert.ok(o.feasibility.explanationCode, 'feasibility carries an explanation code, not a score');
  }
  const transfer = ref.options.find((o) => o.transferCount === 1);
  assert.equal(transfer.feasibility.status, 'tight');
  assert.equal(transfer.feasibility.minimumMarginSeconds, 120, '2 min margin, shown as a duration');
});

test('null (not zero) is used for unknown timing terms', () => {
  for (const o of ref.options) {
    for (const leg of o.legs) {
      // unknown terms must be null, never 0 (which would read as a real value)
      assert.notEqual(leg.uncertaintySeconds, 0, 'unknown uncertainty must be null, not 0');
      assert.ok(['live', 'scheduled', 'estimated', 'unknown'].includes(leg.timingKind));
    }
  }
});
