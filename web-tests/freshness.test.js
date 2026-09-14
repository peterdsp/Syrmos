'use strict';
// 3.0 Phase N J07: unified freshness/offline presentation. Drives every case in
// fixtures/freshness/presentation.json through the web implementation and asserts
// exact equality. The Kotlin FreshnessPresentation test asserts the SAME cases.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const F = require(path.join(
  __dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources', 'web-freshness.js'
));
const fixture = JSON.parse(fs.readFileSync(
  path.join(__dirname, '..', 'fixtures', 'freshness', 'presentation.json'), 'utf8'
));

for (const c of fixture.cases) {
  test(`freshness: ${c.id} (${c.note})`, () => {
    const state = F.evaluate(c.isNetworkAvailable, c.isLive);
    assert.equal(state, c.expected.state, 'state');
    assert.equal(F.showsBanner(state), c.expected.showsBanner, 'showsBanner');
  });
}
