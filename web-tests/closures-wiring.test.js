'use strict';
// 3.0.2 closures + broadcasts wiring on the web. web-map.js is a window-IIFE, so
// these are static-source guardrails: the modules load before the map, the SW
// ships them to returning users, every new string exists in all four language
// blocks, and the sheet / map / planner / banner actually call the engines.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const read = (p) => fs.readFileSync(path.join(RES, p), 'utf8');
const map = read('web-map.js');
const html = read('index.html');
const sw = read('sw.js');

const KEYS = [
  'closure_mode_closed', 'closure_mode_pass_through', 'closure_mode_exit_only', 'closure_mode_entry_only',
  'closure_range', 'closure_until_further', 'closure_from_until_further', 'closure_upcoming',
  'closure_bus_default', 'closure_bus_every', 'closure_bus_route',
  'broadcast_label', 'broadcast_view_station',
];

test('every closure / broadcast string exists in en, el, sq and it', () => {
  for (const key of KEYS) {
    const count = (map.match(new RegExp('^\\s+' + key + ': "', 'gm')) || []).length;
    assert.equal(count, 4, `"${key}" should be in all 4 language blocks, found ${count}`);
  }
  // spot-check that the translations actually landed (not English copies)
  assert.match(map, /closure_until_further: "Έως νεωτέρας"/);
  assert.match(map, /closure_mode_pass_through: "Trenat kalojnë pa ndalur"/);
  assert.match(map, /closure_bus_default: "Bus sostitutivo"/);
});

test('every closure mode in the engine has a title key', () => {
  const C = require(path.join(RES, 'web-closures.js'));
  for (const mode of C.MODE_ORDER) assert.ok(KEYS.includes('closure_mode_' + mode), mode);
});

test('index.html loads both modules before web-map.js', () => {
  const at = (s) => html.indexOf(s);
  assert.ok(at('<script src="/web-closures.js"></script>') > 0, 'web-closures.js not loaded');
  assert.ok(at('<script src="/web-broadcasts.js"></script>') > 0, 'web-broadcasts.js not loaded');
  assert.ok(at('/web-closures.js') < at('<script src="/web-map.js">'));
  assert.ok(at('/web-broadcasts.js') < at('<script src="/web-map.js">'));
});

test('the service worker precaches the modules and the bundled closures seed', () => {
  for (const asset of ['"/web-closures.js"', '"/web-broadcasts.js"', '"/files/seed/schedules-v2/closures.json"']) {
    assert.ok(sw.includes(asset), `precache must include ${asset}`);
  }
  const v = Number(sw.match(/const VERSION = "v(\d+)";/)[1]);
  assert.ok(v >= 6, 'cache version must be bumped for 3.0.2 so returning users get the new JS');
});

test('closures are fetched from the API with seed fallback and evaluated on the client clock', () => {
  assert.match(map, /fetch\(`\$\{API\}\/api\/closures`\)/);
  assert.match(map, /fetch\("\/files\/seed\/schedules-v2\/closures\.json"\)/);
  assert.match(map, /const nowEpochSeconds = \(\) => Math\.floor\(Date\.now\(\) \/ 1000\);/);
});

test('station sheet hides blocked departures and leads with the closure card', () => {
  assert.match(map, /window\.SyrmosClosures\.filterDeparturesAt\(unfilteredRail, stationClosures, closure\.pairs, nowEpochSeconds\(\)\)/);
  assert.match(map, /stationDepartures\.innerHTML = closureHtml \+ groups\.map/);
  assert.match(map, /function closureCardHtml\(effect\)/);
});

test('map greys closed stations with the shared greyed token', () => {
  assert.match(map, /fillColor: fullyClosed \? MAP_TOKENS\.greyedColor : color/);
});

test('planner drops options that board or alight at a closed station', () => {
  assert.match(map, /window\.SyrmosClosures\.filterOptions\(shown\.options, stationClosures, nowEpochSeconds\(\)\)/);
});

test('broadcasts poll with since, and dismissals persist through try/catch storage', () => {
  assert.match(map, /\/api\/broadcasts\?since=\$\{B\.nextSince\(held, 0\)\}/);
  assert.match(map, /const readLS = \(k\) => \{ try \{ return localStorage\.getItem\(k\); \} catch \(_\) \{ return null; \} \};/);
  assert.match(map, /writeLS\(B\.DISMISSED_KEY, B\.encodeIds\(/);
  assert.match(map, /initStep\("connectClosuresAndBroadcasts", connectClosuresAndBroadcasts\)/);
});

test("closures seed fallback path points at a file that exists in the bundled seed", () => {
  const fs = require("fs");
  const path = require("path");
  const root = path.join(__dirname, "..");
  const map = fs.readFileSync(path.join(root, "composeApp/src/wasmJsMain/resources/web-map.js"), "utf8");
  const m = map.match(/fetch\("(\/files\/seed\/[^"]*closures\.json)"\)/);
  assert.ok(m, "web-map.js fetches a bundled closures.json");
  const onDisk = path.join(root, "core/data/src/commonMain/composeResources", m[1]);
  assert.ok(fs.existsSync(onDisk), `bundled seed file missing: ${onDisk}`);
});
