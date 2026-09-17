'use strict';
// Finding 2 (web peer of iOS StationGroupingTests / Kotlin StationGroupingTest):
// the plan <select> shows one option per physical station. Co-located same-name
// stops collapse keeping every line; distinct stations stay separate; identity is
// name AND place.
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const G = require(path.join(
  __dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources', 'web-station-grouping.js'
));

function station(id, name, name_el, latitude, longitude, line_ids) {
  return { id, name, name_el, latitude, longitude, line_ids };
}
function kifis() {
  return [
    station('M1_KIF', 'Kifissia', 'Κηφισιά', 38.0733951, 23.8082198, ['M1']),
    station('A1_KIF', 'Kifisias', 'Κηφισίας', 38.041921, 23.8040729, ['A1']),
    station('A2_KIF', 'Kifisias', 'Κηφισίας', 38.041921, 23.8040729, ['A2']),
  ];
}

test('co-located duplicates collapse, keeping every line', () => {
  const groups = G.groups(kifis());
  assert.equal(groups.length, 2, 'three ids, two physical stations');
  const kifisias = groups.find((g) => g.name === 'Kifisias');
  assert.deepEqual(kifisias.memberIds, ['A1_KIF', 'A2_KIF'], 'every routable stop retained');
  assert.equal(kifisias.representativeId, 'A1_KIF', 'stable representative = smallest id');
  assert.ok(kifisias.lineIds.includes('A1') && kifisias.lineIds.includes('A2'), 'both lines represented');
});

test('Kifissia and Kifisias stay distinct', () => {
  const names = new Set(G.groups(kifis()).map((g) => g.name));
  assert.ok(names.has('Kifissia') && names.has('Kifisias'), 'different spelling and place must not merge');
});

test('accented and unaccented search both hit', () => {
  const kifisias = G.groups(kifis()).find((g) => g.name === 'Kifisias');
  for (const q of ['Kifis', 'kifis', 'Κηφισ', 'Κηφισίας', 'κηφισιας']) {
    assert.ok(G.matches(kifisias, G.fold(q)), `query ${q} should match`);
  }
});

test('same name far apart does not merge', () => {
  const groups = G.groups([
    station('X_DIM', 'Dimarcheio', 'Δημαρχείο', 38.0, 23.7, ['X']),
    station('Y_DIM', 'Dimarcheio', 'Δημαρχείο', 40.6, 22.9, ['Y']),
  ]);
  assert.equal(groups.length, 2, 'same name, different place = distinct stations');
});

test('reordered input yields the same groups', () => {
  const a = G.groups(kifis());
  const b = G.groups(kifis().slice().reverse());
  assert.deepEqual(new Set(a.map((g) => g.id)), new Set(b.map((g) => g.id)));
  const ka = a.find((g) => g.name === 'Kifisias');
  const kb = b.find((g) => g.name === 'Kifisias');
  assert.deepEqual(ka.memberIds, kb.memberIds, 'member set is order-independent');
  assert.equal(ka.representativeId, kb.representativeId, 'representative is stable');
});
