'use strict';
// Guardrails for Italian (it) localization on the web. The static UI dictionary
// and auto-detection were already complete, but Italian was NOT selectable (no
// pill) and several dynamic assistant/notification strings fell back to English.
// These static-source checks pin the fixes so a regression re-fails CI. They are
// deliberately source-level: web-ariadne.js / web-map.js are window-IIFEs, not
// requireable modules.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const read = (p) => fs.readFileSync(path.join(RES, p), 'utf8');

test('index.html: the language switcher offers an Italian pill', () => {
  const html = read('index.html');
  assert.match(html, /<button[^>]*data-lang="it"[^>]*>\s*IT\s*<\/button>/,
    'a data-lang="it" IT button must exist in #languagePicker so Italian is selectable');
  // and all four advertised languages are present
  for (const lang of ['en', 'el', 'sq', 'it']) {
    assert.match(html, new RegExp(`data-lang="${lang}"`), `missing language pill: ${lang}`);
  }
});

test('web-ariadne.js: help / outOfScope / clarify all handle it', () => {
  const js = read('web-ariadne.js');
  // help() and outOfScope() gained an it case
  assert.ok(/case 'it':/.test(js), "web-ariadne.js: no `case 'it':` in help/outOfScope");
  // the English help fallback must now name Italian too (it used to omit it)
  assert.match(js, /English, Greek, Albanian, or Italian/,
    'the English help blurb should advertise Italian');
  // every clarify map entry carries an it string
  const clarifyBlock = js.slice(js.indexOf('function clarify'), js.indexOf('function clarify') + 700);
  const itCount = (clarifyBlock.match(/\bit:\s*'/g) || []).length;
  assert.equal(itCount, 3, `clarify map should have 3 it entries, found ${itCount}`);
});

test('web-map.js: notification + fare strings have Italian branches', () => {
  const js = read('web-map.js');
  // what's-new modal
  assert.match(js, /lang === "it" \? "Novità in Syrmos"/, "what's-new title missing it");
  assert.match(js, /Sfoglia tutte le stazioni/, "what's-new bullets missing it");
  // severe-weather banner
  assert.match(js, /lang === "it" \? "Temporale in corso"/, 'severe-weather title missing it');
  assert.match(js, /NUMERI DI EMERGENZA/, 'severe-weather numbers header missing it');
  assert.match(js, /Tocca un numero per chiamare\./, 'severe-weather tap hint missing it');
  // fare group headers
  assert.match(js, /it: "Atene - OASA"/, 'fare group headers missing it');
  assert.match(js, /it: "Intercity \/ regionale"/, 'fare group headers missing it');
});
