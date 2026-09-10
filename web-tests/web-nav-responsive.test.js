'use strict';
// 3.0 F3 (web): the sidebar used to `display:none` below 960px with no
// replacement, stranding every workspace root on phones/tablets. Now the rail
// stays reachable at all widths (80px rail 600-959, bottom bar <600). Static
// guardrails over the served resources so the regression can't come back.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const html = fs.readFileSync(path.join(RES, 'index.html'), 'utf8');
const css = fs.readFileSync(path.join(RES, 'web-map.css'), 'utf8');
const js = fs.readFileSync(path.join(RES, 'web-map.js'), 'utf8');

test('the nav rail is never hidden without a replacement', () => {
  // The old `.nav-rail { display: none; }` (in the <=960 collapse) is gone.
  assert.doesNotMatch(css, /\.nav-rail\s*\{\s*display:\s*none;?\s*\}/,
    'the rail must not be hidden with no replacement');
});

test('under 600px the rail becomes a fixed bottom bar', () => {
  const compact = css.match(/@media \(max-width: 599px\)\{[\s\S]*?\n\}/) ||
                  css.match(/@media \(max-width: 599px\) \{[\s\S]*?\n\}/);
  assert.ok(compact, 'a <=599px compact block must exist');
  const block = compact[0];
  assert.match(block, /\.nav-rail\s*\{[\s\S]*position:\s*fixed/, 'bottom bar is fixed');
  assert.match(block, /bottom:\s*0/, 'anchored to the bottom');
  assert.match(block, /flex-direction:\s*row/, 'lays the items out horizontally');
  assert.match(block, /\.nav-item__label\s*\{\s*display:\s*block/, 'labels shown in the bar');
});

test('600-959px keeps an 80px left rail', () => {
  assert.match(css, /@media \(max-width: 960px\)\{?[\s\S]*?\.nav-rail\s*\{\s*flex:\s*0 0 80px/,
    'rail widened to 80px in the 600-959 band');
});

test('nav buttons carry localized workspace labels', () => {
  for (const key of ['ws_now', 'ws_plan', 'ws_explore', 'ws_departures', 'ws_tickets', 'ws_more']) {
    assert.match(html, new RegExp('data-i18n="' + key + '"'), `nav label ${key} missing in markup`);
    const count = (js.match(new RegExp('\\b' + key + ':', 'g')) || []).length;
    assert.equal(count, 4, `"${key}" must be in all four language blocks (found ${count})`);
  }
});

test('the store banner is guarded so its [hidden] toggle actually hides it', () => {
  // Without this the .store-banner{display:flex} class beat the bare [hidden]
  // attribute and the banner showed at every width.
  assert.match(css, /\.store-banner\[hidden\]\s*\{\s*display:\s*none/,
    'the [hidden] guard is required');
  assert.match(html, /classList\.toggle\("has-store-banner"/,
    'the controller reserves layout height only while the banner is shown');
});
