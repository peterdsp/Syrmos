'use strict';
// 3.0 Phase R: disruption exclusions. Drives every case in
// fixtures/journeys/disruption.json through the web implementation and asserts
// exact equality with the golden expected result. The Kotlin DisruptionExclusion
// test asserts the SAME fixture cases, so web and Kotlin agree by construction.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const D = require(path.join(
  __dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources', 'web-disruption.js'
));
const fixture = JSON.parse(fs.readFileSync(
  path.join(__dirname, '..', 'fixtures', 'journeys', 'disruption.json'), 'utf8'
));

const sorted = (a) => [...a].sort();

for (const c of fixture.suspendedLineIds) {
  test(`suspendedLineIds: ${c.id} (${c.note})`, () => {
    assert.deepEqual(sorted(D.suspendedLineIds(c.notices)), sorted(c.expected));
  });
}

for (const c of fixture.optionUsesSuspended) {
  test(`optionUsesSuspended: ${c.id} (${c.note})`, () => {
    assert.deepEqual(sorted(D.optionUsesSuspended(c.option, c.suspended)), sorted(c.expected));
  });
}

for (const c of fixture.classify) {
  test(`classify: ${c.id} (${c.note})`, () => {
    const got = D.classify(c.avoiding, c.naive, c.notices);
    assert.equal(got.kind, c.expected.kind, 'kind');
    if (c.expected.kind === 'routed') {
      assert.deepEqual(sorted(got.excludedLineIds), sorted(c.expected.excludedLineIds), 'excludedLineIds');
    } else if (c.expected.kind === 'suspended') {
      assert.deepEqual(sorted(got.affectedLineIds), sorted(c.expected.affectedLineIds), 'affectedLineIds');
      assert.deepEqual(sorted(got.noticeIds), sorted(c.expected.noticeIds), 'noticeIds');
    }
  });
}

test('never fabricates a replacement route on suspension', () => {
  const got = D.classify([], [{ legs: [{ kind: 'ride', lineId: 'M1' }] }],
    [{ id: 'c1', severity: 'closure', affectedLineIds: ['M1'] }]);
  assert.equal(got.kind, 'suspended');
  assert.equal('options' in got, false);
});
