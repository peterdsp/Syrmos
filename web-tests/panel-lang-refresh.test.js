'use strict';
// The live-trains / simulated-trains panel renders on the simulation timer, not
// on language change, so a language flip left its rows in the old language until
// the next tick. A dedicated onLanguageChange handler now re-renders both panels
// from their cached batches. Static-source guardrail (web-map is a window-IIFE).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const js = fs.readFileSync(path.join(RES, 'web-map.js'), 'utf8');

test('a language-change handler re-renders the trains panel', () => {
  // an onLanguageChange callback that re-runs both panel renderers off the
  // cached batches
  assert.match(js, /onLanguageChange\(\(\) => \{\s*if \(lastSimulatedTrains && lastSimulatedTrains\.length\) renderSimulatedTrainsInPanel\(lastSimulatedTrains\);\s*if \(lastLiveTrains && lastLiveTrains\.length\) renderLiveTrains\(lastLiveTrains\);\s*\}\);/,
    'missing onLanguageChange handler that re-renders the simulated + live trains panel');
});
