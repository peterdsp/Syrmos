'use strict';
// The region filter chips (Athens / Thessaloniki / National / Patras) render
// from I18N keys. Those keys used to live only in the `en` block, so el, sq and
// it all fell back to English place names. This guardrail pins each key in all
// four language blocks so a chip cannot silently revert to English again.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const js = fs.readFileSync(path.join(RES, 'web-map.js'), 'utf8');

test('region chip keys are localized in every language block', () => {
  // I18N has en/el/sq/it blocks, so each region key must appear exactly 4 times.
  for (const key of ['athens', 'thessaloniki', 'national', 'patras']) {
    const count = (js.match(new RegExp('\\b' + key + ':', 'g')) || []).length;
    assert.equal(count, 4, `region key "${key}" should exist in all 4 language blocks, found ${count}`);
  }
  // spot-check the localized place names actually landed
  assert.match(js, /athens: "Αθήνα"/, 'Greek Athens missing');
  assert.match(js, /thessaloniki: "Salonicco"/, 'Italian Thessaloniki missing');
  assert.match(js, /patras: "Patra"/, 'Albanian Patras missing');
});
