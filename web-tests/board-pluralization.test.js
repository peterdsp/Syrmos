'use strict';
// The station board's scope line read "1 services" on every client because the
// count word was always plural. The two mobile clients now call the shared
// BoardScopeCopy.services(count, lang); web has its own i18n table, so pin the
// same rule here. Source-level: web-map.js is a window-IIFE, not requireable.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const js = fs.readFileSync(path.join(RES, 'web-map.js'), 'utf8');

const LANGS = ['en', 'el', 'sq', 'it'];

test('every language dictionary carries a singular AND a plural services key', () => {
  const singular = js.match(/board_service_one:\s*"([^"]+)"/g) || [];
  const plural = js.match(/board_services:\s*"([^"]+)"/g) || [];
  assert.equal(plural.length, LANGS.length,
    `expected ${LANGS.length} board_services entries, found ${plural.length}`);
  assert.equal(singular.length, LANGS.length,
    `expected ${LANGS.length} board_service_one entries, found ${singular.length}`);
});

test('the singular and plural wordings actually differ in every language', () => {
  const singular = (js.match(/board_service_one:\s*"([^"]+)"/g) || [])
    .map((m) => m.match(/"([^"]+)"/)[1]);
  const plural = (js.match(/board_services:\s*"([^"]+)"/g) || [])
    .map((m) => m.match(/"([^"]+)"/)[1]);
  for (let i = 0; i < singular.length; i += 1) {
    assert.notEqual(singular[i], plural[i],
      `dictionary ${i}: singular and plural are identical ("${singular[i]}")`);
  }
});

test('each services string interpolates the count', () => {
  for (const m of js.match(/board_service(?:s|_one):\s*"([^"]+)"/g) || []) {
    const value = m.match(/"([^"]+)"/)[1];
    assert.ok(value.includes('{n}'), `missing {n} placeholder: ${value}`);
  }
});

test('the board scope line picks the singular key at exactly one group', () => {
  assert.match(js, /board\.timedGroupCount === 1 \? "board_service_one" : "board_services"/,
    'the scope line must choose the singular key when timedGroupCount is 1');
});

test('Albanian keeps its diacritic in the plural form', () => {
  assert.match(js, /board_services:\s*"\{n\} shërbime"/);
  assert.match(js, /board_service_one:\s*"\{n\} shërbim"/);
});
