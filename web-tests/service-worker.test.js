'use strict';
// The offline Service Worker: after one load, the app shell + bundled seed are
// served from cache so a reload / dropped connection never blanks the page.
// isTile() is pure and brace-extracted for executable coverage; the caching
// strategies + registration are asserted as static-source guardrails (the SW
// relies on ServiceWorker globals that node does not provide).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const sw = fs.readFileSync(path.join(RES, 'sw.js'), 'utf8');
const html = fs.readFileSync(path.join(RES, 'index.html'), 'utf8');
const map = fs.readFileSync(path.join(RES, 'web-map.js'), 'utf8');

function extractFunction(src, name) {
  const start = src.indexOf('function ' + name);
  assert.notEqual(start, -1, `function ${name} not found`);
  const bodyStart = src.indexOf('{', start);
  let depth = 0;
  for (let i = bodyStart; i < src.length; i++) {
    if (src[i] === '{') depth++;
    else if (src[i] === '}') { depth--; if (depth === 0) return src.slice(start, i + 1); }
  }
  throw new Error(`unbalanced braces extracting ${name}`);
}

const isTile = new Function(`${extractFunction(sw, 'isTile')}; return isTile;`)();

test('isTile matches basemap tile hosts, not app/api requests', () => {
  const tile = (u) => isTile(new URL(u));
  assert.equal(tile('https://server.arcgisonline.com/ArcGIS/rest/services/World_Light_Gray_Base/MapServer/tile/7/45/72'), true);
  assert.equal(tile('https://basemaps.cartocdn.com/light_all/7/72/45.png'), true);
  assert.equal(tile('https://a.tile.openstreetmap.org/7/72/45.png'), true);
  assert.equal(tile('https://api-syrmos.peterdsp.dev/api/trains'), false);
  assert.equal(tile('https://syrmos.peterdsp.dev/files/seed/stations.json'), false);
  assert.equal(tile('https://syrmos.peterdsp.dev/web-map.abc123.js'), false);
});

test('index.html registers the service worker on load', () => {
  assert.match(html, /navigator\.serviceWorker\.register\("\/sw\.js"\)/, 'SW registration missing');
  assert.match(html, /"serviceWorker" in navigator/, 'SW feature-detect missing');
});

test('SW precaches the app shell and the bundled seed', () => {
  for (const asset of [
    '"/"', '"/index.html"', '"/design-tokens.css"', '"/shapes.json"',
    '"/files/seed/stations.json"',
    '"/files/seed/schedules-v2/lines.json"',
    '"/files/seed/routes.json"',
    '"/files/seed/station-offsets.json"',
  ]) {
    assert.ok(sw.includes(asset), `precache must include ${asset}`);
  }
});

test('SW uses network-first for navigations and the live API, cache-first otherwise', () => {
  assert.match(sw, /req\.mode === "navigate"[\s\S]*?networkFirstNavigation/, 'navigations must be network-first');
  assert.match(sw, /url\.hostname === "api-syrmos\.peterdsp\.dev"[\s\S]*?networkFirstApi/, 'API must be network-first');
  assert.match(sw, /if \(isTile\(url\)\)[\s\S]*?cacheFirstTile/, 'tiles must route to the capped tile cache');
  assert.match(sw, /function cacheFirst\(/, 'cache-first fallback missing');
});

test('SW versions its caches and cleans up old ones on activate', () => {
  assert.match(sw, /const VERSION = "v\d+";/, 'versioned cache name missing');
  assert.match(sw, /caches\.delete/, 'activate must delete stale caches');
  assert.match(sw, /skipWaiting\(\)/, 'skipWaiting missing');
  assert.match(sw, /clients\.claim\(\)/, 'clients.claim missing');
});

test('tile cache is capped (opportunistic, never a bulk download)', () => {
  assert.match(sw, /const TILE_MAX = \d+;/, 'tile cap constant missing');
  assert.match(sw, /trimCache\(TILE_CACHE, TILE_MAX\)/, 'tile cache must be trimmed');
});

test('web-map.js core seed fetches degrade instead of rejecting init offline', () => {
  assert.match(map, /fetch\("\/files\/seed\/stations\.json"\)\.then\(\(r\) => r\.json\(\)\)\.catch\(\(\) => \[\]\)/, 'stations.json needs a .catch');
  assert.match(map, /fetch\("\/files\/seed\/routes\.json"\)\.then\(\(r\) => r\.json\(\)\)\.catch\(\(\) => \[\]\)/, 'routes.json needs a .catch');
  assert.match(map, /fetch\("\/files\/seed\/service_patterns\.json"\)\.then\(\(r\) => r\.json\(\)\)\.catch\(\(\) => \(\{\}\)\)/, 'service_patterns.json needs a .catch');
});
