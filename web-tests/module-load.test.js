'use strict';
// Every hand-written web module must actually EVALUATE, not merely parse.
//
// `node --check` and a static grep both pass on a file whose IIFE throws at
// runtime, and the browser then silently has no `window.SyrmosAriadne` at all:
// Ariadne stops answering and nothing in CI notices. Removing a block from
// web-ariadne.js once took two live functions with it exactly this way.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const RES = path.join(__dirname, '..', 'composeApp/src/wasmJsMain/resources');

// module -> the global it must publish, and a member that must be callable.
const MODULES = [
  ['web-ariadne.js', 'SyrmosAriadne', ['init', 'parse', 'help', 'outOfScope', 'clarify', 'fold', 'dayOf', 'suggestStationId']],
  ['web-station-board.js', 'SyrmosStationBoard', ['buildBoard', 'complexForStop', 'dedupeDepartures', 'tripBoarding', 'lineDirectionsAt']],
  ['web-departures.js', 'SyrmosDepartures', ['groupDepartures', 'destKey']],
  ['web-station-grouping.js', 'SyrmosStationGrouping', ['fold', 'groups', 'matches']],
  ['web-ariadne-rag.js', 'SyrmosAriadneRAG', []],
  ['web-freshness.js', 'SyrmosFreshness', []],
  ['web-accessibility.js', 'SyrmosAccessibility', []],
  ['web-disruption.js', 'SyrmosDisruption', []],
  ['web-airport.js', 'SyrmosAirport', []],
  ['web-go.js', 'SyrmosGO', []],
  ['web-planner.js', 'SyrmosPlanner', []],
];

for (const [file, globalName, members] of MODULES) {
  test(`${file} evaluates and publishes window.${globalName}`, () => {
    const source = fs.readFileSync(path.join(RES, file), 'utf8');
    const sandbox = { console, setTimeout, clearTimeout, fetch: async () => { throw new Error('no network in this test'); } };
    sandbox.window = sandbox;
    sandbox.self = sandbox;
    sandbox.globalThis = sandbox;
    vm.createContext(sandbox);
    // A throwing IIFE fails here, which is the whole point.
    vm.runInContext(source, sandbox, { filename: file });
    const published = sandbox[globalName] || (sandbox.module && sandbox.module.exports);
    assert.ok(published, `${file} did not publish ${globalName}`);
    for (const member of members) {
      assert.equal(typeof published[member], 'function', `${globalName}.${member} is missing`);
    }
  });
}
