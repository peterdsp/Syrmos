'use strict';
// 3.0 Phase R: accessibility-unknown disclosure. Drives every case in
// fixtures/journeys/accessibility.json through the web implementation and asserts
// exact equality with the golden expected result. The Kotlin
// AccessibilityDisclosure test asserts the SAME fixture cases, so web and Kotlin
// agree by construction.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const A = require(path.join(
  __dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources', 'web-accessibility.js'
));
const fixture = JSON.parse(fs.readFileSync(
  path.join(__dirname, '..', 'fixtures', 'journeys', 'accessibility.json'), 'utf8'
));

for (const c of fixture.cases) {
  test(`accessibility: ${c.id} (${c.note})`, () => {
    const got = A.forOption({ legs: c.legs }, c.preference);
    assert.equal(got.confidence, c.expected.confidence, 'confidence');
    assert.equal(got.explanationCode, c.expected.explanationCode, 'explanationCode');
    assert.deepEqual(got.unknownLegIds, c.expected.unknownLegIds, 'unknownLegIds');
    assert.deepEqual(got.unavailableLegIds, c.expected.unavailableLegIds, 'unavailableLegIds');
  });
}

test('unavailable is disclosed even when an unknown leg is also present', () => {
  const got = A.forOption({ legs: [
    { id: 'r1', kind: 'ride', accessibility: 'unknown' },
    { id: 'r2', kind: 'ride', accessibility: 'unavailable' },
  ] }, 'stepFree');
  assert.equal(got.confidence, 'unavailable');
  assert.deepEqual(got.unknownLegIds, ['r1']);
  assert.deepEqual(got.unavailableLegIds, ['r2']);
});
