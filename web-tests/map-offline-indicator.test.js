'use strict';
// The map offline indicator: a subtle #offlinePill shown when the device is
// offline OR no live data has landed within the freshness window (online != API
// reachable), and hidden the moment live data resumes. Static-source guardrails
// (web-map is a window-IIFE) + HTML/CSS assertions, mirroring the iOS map pill
// and the Android MapScreen OfflinePill.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const js = fs.readFileSync(path.join(RES, 'web-map.js'), 'utf8');
const css = fs.readFileSync(path.join(RES, 'web-map.css'), 'utf8');
const html = fs.readFileSync(path.join(RES, 'index.html'), 'utf8');

test('the indicator combines connectivity and live-data staleness', () => {
  assert.match(js, /const LIVE_STALE_MS = 90_000;/, 'freshness window missing');
  assert.match(js, /navigator\.onLine === false/, 'must consult navigator.onLine');
  assert.match(js, /const stale = \(Date\.now\(\) - lastApiOkMs\) > LIVE_STALE_MS;/, 'staleness check missing');
  assert.match(js, /const offline = \(typeof navigator[^\n]*navigator\.onLine === false\) \|\| stale;/,
    'offline = not online OR stale');
});

test('the pill is toggled, and text is localized', () => {
  assert.match(js, /const pill = document\.getElementById\("offlinePill"\);/, 'pill lookup missing');
  assert.match(js, /pill\.hidden = !offline;/, 'pill must be toggled via hidden');
  assert.match(js, /txt\.textContent = t\("offline_snapshot"\);/, 'pill text must be localized');
});

test('online/offline events, a poll, and a fallback interval all drive it', () => {
  assert.match(js, /window\.addEventListener\("online", updateOfflineIndicator\);/, 'online listener missing');
  assert.match(js, /window\.addEventListener\("offline", updateOfflineIndicator\);/, 'offline listener missing');
  assert.match(js, /setInterval\(updateOfflineIndicator, 15_000\);/, 'fallback interval missing');
  assert.match(js, /function markApiOk\(\)/, 'markApiOk missing');
  // marked on the two live map feeds
  assert.match(js, /updateLiveTrains\(payload\.trains \|\| \[\], payload\.updatedAt\);\s*\n\s*markApiOk\(\);/,
    'trains poll must mark API ok');
});

test('css floats the pill and honours the hidden attribute', () => {
  assert.match(css, /\.offline-pill\s*\{[^}]*position:\s*fixed/, 'pill must float (fixed) over the map');
  assert.match(css, /\.offline-pill\[hidden\]\s*\{\s*display:\s*none;\s*\}/, 'hidden rule must beat display:flex');
});

test('index.html ships the pill hidden and accessible', () => {
  assert.match(html, /id="offlinePill"[^>]*role="status"[^>]*aria-live="polite"[^>]*hidden/,
    'pill must be an accessible live region, hidden by default');
});
