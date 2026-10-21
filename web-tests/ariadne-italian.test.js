'use strict';
// Italian (it) is an offered assistant language, but the offline NLU vocabulary
// shipped EN/EL/SQ only, so Italian queries fell through to outOfScope and only
// the hosted cloud fallback could rescue them. These tests pin that common
// Italian queries are understood by the on-device parser.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const SEED = path.join(__dirname, '..', 'core', 'data', 'src', 'commonMain', 'composeResources', 'files', 'seed');
const readJson = (p) => JSON.parse(fs.readFileSync(p, 'utf8'));

global.window = global.window || {};
require(path.join(RES, 'web-ariadne.js'));
const A = global.window.SyrmosAriadne;

const stationsRaw = readJson(path.join(SEED, 'stations.json'));
const stations = Array.isArray(stationsRaw) ? stationsRaw : stationsRaw.stations;
const linesRaw = readJson(path.join(SEED, 'lines.json'));
const lines = Array.isArray(linesRaw) ? linesRaw : linesRaw.lines;
A.init({ stations: stations, lines: lines });

test('Italian queries are understood by the offline parser', () => {
  assert.ok(A && typeof A.parse === 'function', 'SyrmosAriadne.parse available');
  const cases = [
    'prossimo treno',
    'quando parte il prossimo treno',
    'quanto costa il biglietto',
    'ultimo treno',
    'a che ora e il primo treno',
    'che tempo fa',
    'mostra sulla mappa',
    'aiuto',
  ];
  for (const q of cases) {
    const r = A.parse(q);
    assert.notEqual(r.kind, 'outOfScope', `Italian not understood: "${q}" -> ${r.kind}`);
  }
});
