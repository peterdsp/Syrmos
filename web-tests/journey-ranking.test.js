'use strict';
// 3.0 Phase P: itinerary ranking + dedup + cap-at-3 (prompt 7.2). Drives every
// case in fixtures/journeys/ranking.json through the web ranker; the Kotlin
// JourneyRanker test asserts the same fixture, so both agree by construction.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const R = require(path.join(
  __dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources', 'web-journey-ranker.js'
));
const fixture = JSON.parse(fs.readFileSync(
  path.join(__dirname, '..', 'fixtures', 'journeys', 'ranking.json'), 'utf8'
));

for (const c of fixture.cases) {
  test(`ranking: ${c.id}`, () => {
    const out = R.rank(c.options, c.ranking);
    assert.deepEqual(out.map((o) => o.id), c.expectedOrder, 'ordered ids (incl. dedup + cap)');
    assert.equal(out[0].rankingBadge, c.expectedFirstBadge, 'top badge');
    // no option beyond the first carries a badge
    for (let i = 1; i < out.length; i++) assert.equal(out[i].rankingBadge, null, `option ${i} badge`);
    // never padded past three
    assert.ok(out.length <= R.MAX_OPTIONS, 'at most three options');
  });
}

test('does not mutate the input options', () => {
  const opts = fixture.cases[0].options.map((o) => Object.assign({}, o));
  const before = JSON.stringify(opts);
  R.rank(opts, 'fastest');
  assert.equal(JSON.stringify(opts), before, 'input left untouched');
});
