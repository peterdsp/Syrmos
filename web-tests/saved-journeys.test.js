'use strict';
// 3.0 S08 / J05: the web saved-journeys store must match the shared golden
// fixture fixtures/journeys/saved.json exactly — same ordered ids for every
// pure op, the same HARD de-dup invariant (one row per (fromId,toId)), and the
// same atomic/versioned decode outcomes. This is the cross-client parity guard:
// Kotlin SavedJourneyStore and iOS SavedJourneyStore are checked against the same
// fixture, so all three stores stay byte-parity.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const FIX = path.join(__dirname, '..', 'fixtures', 'journeys', 'saved.json');
const Saved = require(path.join(RES, 'web-saved-journeys.js'));
const fixture = JSON.parse(fs.readFileSync(FIX, 'utf8'));

const ids = (list) => list.map((s) => s.id);

test('storage key and schema version match the fixture', () => {
  assert.equal(Saved.STORAGE_KEY, fixture.storageKey);
  assert.equal(Saved.SCHEMA_VERSION, fixture.schemaVersion);
});

test('every fixture op case produces the expected ordered ids', () => {
  for (const c of fixture.cases) {
    let result;
    if (c.op === 'save') result = Saved.save(c.before, c.entry);
    else if (c.op === 'rename') result = Saved.rename(c.before, c.targetId, c.label);
    else if (c.op === 'remove') result = Saved.remove(c.before, c.targetId);
    else if (c.op === 'reorder') result = Saved.reorder(c.before, c.order);
    else throw new Error('unknown op ' + c.op);
    assert.deepEqual(ids(result), c.expectedOrder, c.id + ': order');
    if (c.expectedTop) {
      assert.deepEqual(result[0], c.expectedTop, c.id + ': top row');
    }
  }
});

test('de-dup is a hard invariant: no (fromId,toId) pair appears twice', () => {
  let list = [];
  // Save the same pair three times with different labels; still exactly one row.
  list = Saved.save(list, { id: 'x1', fromId: 'A', toId: 'B', createdAt: '2026-01-15T08:00:00+02:00', label: 'one' });
  list = Saved.save(list, { id: 'x2', fromId: 'A', toId: 'B', createdAt: '2026-01-15T08:01:00+02:00', label: 'two' });
  list = Saved.save(list, { id: 'x3', fromId: 'A', toId: 'B', createdAt: '2026-01-15T08:02:00+02:00', label: 'three' });
  assert.equal(list.length, 1, 'one row for one pair');
  assert.equal(list[0].id, 'x1', 'original id preserved across updates');
  assert.equal(list[0].label, 'three', 'latest label wins');
});

test('decode: ok / unsupported / corrupt outcomes match the fixture', () => {
  const ok = Saved.decode(fixture.decode.ok.raw);
  assert.ok(ok.ok, 'ok decodes');
  assert.deepEqual(ids(ok.value), fixture.decode.ok.expectedOrder);

  const unsup = Saved.decode(fixture.decode.unsupported.raw);
  assert.ok(unsup.unsupported, 'newer schema is unsupported');
  assert.equal(unsup.foundVersion, fixture.decode.unsupported.foundVersion);

  const corrupt = Saved.decode(fixture.decode.corrupt.raw);
  assert.ok(corrupt.corrupt, 'garbage is corrupt');

  assert.deepEqual(Saved.decode(null), { ok: true, value: [] }, 'empty store decodes to []');
  assert.deepEqual(Saved.decode(''), { ok: true, value: [] });
});

test('encode round-trips through decode to the same ids', () => {
  const list = Saved.save([], fixture.sample);
  const round = Saved.decode(Saved.encode(list));
  assert.ok(round.ok);
  assert.deepEqual(ids(round.value), ids(list));
  // Encoded root carries schemaVersion + savedJourneys, matching the empty root shape.
  assert.deepEqual(JSON.parse(Saved.encode([])), fixture.emptyRoot);
});

test('normalize drops entries with no endpoints and trims blank labels to null', () => {
  assert.equal(Saved.normalize({ id: 'a', fromId: '', toId: 'B' }), null, 'missing fromId dropped');
  assert.equal(Saved.normalize({ id: 'a', fromId: 'A' }), null, 'missing toId dropped');
  const n = Saved.normalize({ id: 'a', fromId: 'A', toId: 'B', label: '   ' });
  assert.equal(n.label, null, 'blank label becomes null');
});

test('store: a fake localStorage persists across save/rename/remove', () => {
  const mem = new Map();
  const fake = {
    getItem: (k) => (mem.has(k) ? mem.get(k) : null),
    setItem: (k, v) => mem.set(k, v),
    removeItem: (k) => mem.delete(k),
  };
  const store = Saved.createStore(fake);
  store.save({ id: 'a', fromId: 'A', toId: 'B', createdAt: '2026-01-15T08:00:00+02:00', label: 'Work' });
  store.save({ id: 'b', fromId: 'C', toId: 'D', createdAt: '2026-01-15T09:00:00+02:00', label: 'Home' });
  assert.deepEqual(ids(store.list()), ['b', 'a'], 'newest first');
  store.rename('a', 'Gym');
  assert.equal(store.list().find((s) => s.id === 'a').label, 'Gym');
  store.remove('b');
  assert.deepEqual(ids(store.list()), ['a']);
  // A fresh store over the same storage reads the persisted root back.
  assert.deepEqual(ids(Saved.createStore(fake).list()), ['a']);
});
