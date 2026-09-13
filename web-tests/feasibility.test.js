'use strict';
// 3.0 Phase P: connection feasibility (prompt 7.3). Drives every case in
// fixtures/journeys/feasibility.json through the web implementation and asserts
// exact equality with the golden expected object. The Kotlin FeasibilityCalculator
// test asserts the SAME fixture cases, so web and Kotlin agree by construction.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const F = require(path.join(
  __dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources', 'web-feasibility.js'
));
const fixture = JSON.parse(fs.readFileSync(
  path.join(__dirname, '..', 'fixtures', 'journeys', 'feasibility.json'), 'utf8'
));

for (const c of fixture.cases) {
  test(`feasibility: ${c.id} (${c.note})`, () => {
    const got = F.forOption(c.option, fixture.policy, c.closedLegIds || []);
    assert.equal(got.status, c.expected.status, 'status');
    assert.equal(got.minimumMarginSeconds, c.expected.minimumMarginSeconds, 'margin seconds');
    assert.equal(got.explanationCode, c.expected.explanationCode, 'explanation code');
    assert.equal(got.limitingLegId, c.expected.limitingLegId, 'limiting leg id');
  });
}

test('never emits a success percentage or a score field', () => {
  const got = F.forOption(fixture.cases[1].option, fixture.policy);
  assert.equal('successPercent' in got, false);
  assert.equal('score' in got, false);
  // the evidence is a real margin in seconds
  assert.equal(typeof got.minimumMarginSeconds, 'number');
});
