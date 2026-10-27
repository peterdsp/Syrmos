'use strict';
// 3.0.2 broadcasts on the web: audience filter, expiry, dedupe by id, the
// `since` cursor and the dismissed-id store (docs/3.0.2-CONTRACTS.md).
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const B = require(path.join(
  __dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources', 'web-broadcasts.js'
));

const NOW = 1793124000;
const mk = (id, audience, sent, expires) => ({
  id, audience, sentAtEpochSeconds: sent, expiresAtEpochSeconds: expires,
  title: { en: id }, body: { en: id },
});

test('audienceMatches: all, region, line and station', () => {
  const ctx = { region: 'athens', lineIds: ['M3'], stationIds: ['M2_SYN'] };
  assert.equal(B.audienceMatches('all', ctx), true);
  assert.equal(B.audienceMatches('region:athens', ctx), true);
  assert.equal(B.audienceMatches('region:thessaloniki', ctx), false);
  assert.equal(B.audienceMatches('line:M3', ctx), true);
  assert.equal(B.audienceMatches('line:M1', ctx), false);
  assert.equal(B.audienceMatches('station:M2_SYN', ctx), true);
  assert.equal(B.audienceMatches('station:M3_NIK', ctx), false);
  assert.equal(B.audienceMatches('bogus', ctx), false);
  assert.equal(B.audienceMatches('region:athens', {}), false);
});

test('visible: drops expired, dismissed and other audiences, newest first', () => {
  const list = [
    mk('old', 'all', NOW - 600, null),
    mk('new', 'all', NOW, NOW + 3600),
    mk('expired', 'all', NOW - 100, NOW),
    mk('thess', 'region:thessaloniki', NOW - 50, null),
    mk('gone', 'all', NOW - 10, null),
    mk('fav', 'line:M3', NOW - 20, null),
  ];
  const got = B.visible(list, { region: 'athens', lineIds: ['M3'] }, NOW, ['gone']);
  assert.deepEqual(got.map((b) => b.id), ['new', 'fav', 'old']);
});

test('merge: dedupes by id (incoming wins) and prunes expired', () => {
  const a = [mk('x', 'all', NOW - 10, null), mk('y', 'all', NOW - 20, NOW - 1)];
  const b = [Object.assign(mk('x', 'all', NOW - 10, null), { title: { en: 'updated' } }), { id: '', sentAtEpochSeconds: 1 }];
  const got = B.merge(a, b, NOW);
  assert.equal(got.length, 1);
  assert.equal(got[0].title.en, 'updated');
});

test('nextSince: newest sentAt held, else the fallback', () => {
  assert.equal(B.nextSince([mk('a', 'all', 10, null), mk('b', 'all', 30, null)], 0), 30);
  assert.equal(B.nextSince([], 5), 5);
  assert.equal(B.nextSince([mk('a', 'all', 3, null)], 5), 5);
});

test('dismissed store round-trips, dedupes and is capped', () => {
  assert.deepEqual(B.decodeIds(B.encodeIds(['a'], 'b')), ['a', 'b']);
  assert.deepEqual(B.decodeIds(B.encodeIds(['a', 'b'], 'a')), ['b', 'a']);
  assert.deepEqual(B.decodeIds('not json'), []);
  assert.deepEqual(B.decodeIds('{"x":1}'), []);
  const many = Array.from({ length: B.DISMISSED_CAP + 5 }, (_, i) => 'id' + i);
  const kept = B.decodeIds(B.encodeIds(many, 'last'));
  assert.equal(kept.length, B.DISMISSED_CAP);
  assert.equal(kept[kept.length - 1], 'last');
});

test('cache blob round-trips and survives garbage', () => {
  const list = [mk('a', 'all', NOW, null)];
  assert.deepEqual(B.decodeCache(B.encodeCache(list)), list);
  assert.deepEqual(B.decodeCache('garbage'), []);
  assert.deepEqual(B.decodeCache(null), []);
});

test('stationIdFromDeepLink', () => {
  assert.equal(B.stationIdFromDeepLink('syrmos://station/M2_SYN'), 'M2_SYN');
  assert.equal(B.stationIdFromDeepLink('https://evil.example/station/M2_SYN'), null);
  assert.equal(B.stationIdFromDeepLink(null), null);
});
