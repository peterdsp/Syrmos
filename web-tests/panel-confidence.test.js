'use strict';
// The live-trains panel rows now carry a per-row confidence chip, mirroring the
// grouped departure cards: a boardable telematics train reads LIVE, a GPS-only
// position reads as the muted "Position only". Static-source guardrails (web-map
// is a window-IIFE, not requireable).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const js = fs.readFileSync(path.join(RES, 'web-map.js'), 'utf8');
const css = fs.readFileSync(path.join(RES, 'web-map.css'), 'utf8');

test('position_only label is localized in all four language blocks', () => {
  const count = (js.match(/\bposition_only:/g) || []).length;
  assert.equal(count, 4, `position_only should exist in all 4 language blocks, found ${count}`);
  assert.match(js, /position_only: "Solo posizione"/, 'Italian position_only missing');
  assert.match(js, /position_only: "Μόνο θέση"/, 'Greek position_only missing');
});

test('live-trains rows render a per-row confidence chip', () => {
  // the renderLiveTrains row builder classifies each train and emits a src-chip
  assert.match(js, /const notInService = train\.inService === false \|\| train\.status === "position_only";/,
    'per-train confidence classification missing');
  assert.match(js, /const chipMod = notInService \? "offline" : "live";/, 'chip mod selection missing');
  assert.match(js, /const chip = `<span class="src-chip src-chip--\$\{chipMod\}">/, 'per-row src-chip missing');
  assert.match(js, /panel-item--muted/, 'muted class for non-boardable rows missing');
});

test('simulated (projected) train rows carry a per-row Estimated chip', () => {
  // renderSimulatedTrainsInPanel: each projected metro/tram/airport row gets an
  // estimated chip, and the section-header provenance chip was dropped so it is
  // not repeated.
  const fn = js.slice(js.indexOf('function renderSimulatedTrainsInPanel'),
                       js.indexOf('function renderSimulatedTrainsInPanel') + 2800);
  assert.match(fn, /src-chip--estimated"><span class="src-chip__dot"><\/span>\$\{t\("estimated"\)\}/,
    'per-row estimated chip missing on simulated rows');
  // the header count row closes straight into the wrapper: no duplicate chip
  assert.match(fn, /trains_active", \{ n: trains\.length \}\)\}<\/div><\/div>`/,
    'the simulated section header should no longer duplicate a provenance chip');
});

test('css: panel chip spacing and muted-row styling exist', () => {
  assert.match(css, /\.panel-item__body \.src-chip\s*\{[^}]*margin-top/, 'panel chip spacing rule missing');
  assert.match(css, /\.panel-item--muted\s*\{[^}]*opacity/, 'muted-row opacity rule missing');
});
