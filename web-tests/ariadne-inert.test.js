'use strict';
// Static-source guardrails for the Ariadne panel's keyboard-accessibility state.
//
// The panel is hidden with a CSS class plus aria-hidden="true", but aria-hidden
// alone leaves its buttons and input in the tab order, so a keyboard or screen
// reader user could Tab into the invisible panel. axe-core flagged this as a
// serious `aria-hidden-focus` violation on the live site. The fix marks the
// hidden panel `inert`, which removes the whole subtree from focus and the
// accessibility tree, and clears it when the panel opens. These tests pin that
// inert stays in lockstep with the hidden state so a regression re-fails CI.
//
// Run: `node --test` from web-tests/, or `node --test web-tests/` from the repo root.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const read = (p) => fs.readFileSync(path.join(RES, p), 'utf8');

test('index.html: the initially hidden Ariadne panel is inert', () => {
  const html = read('index.html');
  const m = /<aside\b[^>]*\bid="ariadnePanel"[^>]*>/.exec(html);
  assert.notEqual(m, null, 'index.html: no <aside id="ariadnePanel"> found');
  const tag = m[0];
  assert.match(tag, /\bariadne-panel--hidden\b/, 'panel must start hidden');
  assert.match(tag, /aria-hidden\s*=\s*"true"/, 'hidden panel must be aria-hidden');
  assert.match(tag, /\binert\b/, 'hidden panel must be inert so it leaves the tab order');
});

test('web-map.js: openPanel clears inert and closePanel sets it', () => {
  const js = read('web-map.js');

  const open = /function openPanel\(\)\s*\{([\s\S]*?)\n\s{8}\}/.exec(js);
  assert.notEqual(open, null, 'openPanel not found');
  assert.match(open[1], /removeAttribute\(\s*["']inert["']\s*\)/, 'openPanel must remove inert');
  assert.match(open[1], /setAttribute\(\s*["']aria-hidden["']\s*,\s*["']false["']\s*\)/, 'openPanel must un-hide');

  const close = /function closePanel\(\)\s*\{([\s\S]*?)\n\s{8}\}/.exec(js);
  assert.notEqual(close, null, 'closePanel not found');
  assert.match(close[1], /setAttribute\(\s*["']inert["']\s*,\s*["']["']\s*\)/, 'closePanel must set inert');
  assert.match(close[1], /setAttribute\(\s*["']aria-hidden["']\s*,\s*["']true["']\s*\)/, 'closePanel must hide');
});
