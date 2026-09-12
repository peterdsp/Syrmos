'use strict';
// 3.0 F1: every browser gets the real app. The old user-agent blockade (which
// did document.documentElement.innerHTML = <store landing page> for any mobile
// UA) is gone; in its place a secondary, dismissible, localized store-promotion
// banner that never gates content. Static-source guardrails over the served
// resources, mirroring the other web-tests.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const html = fs.readFileSync(path.join(RES, 'index.html'), 'utf8');
const js = fs.readFileSync(path.join(RES, 'web-map.js'), 'utf8');
const css = fs.readFileSync(path.join(RES, 'web-map.css'), 'utf8');

test('the mobile user-agent blockade is fully removed', () => {
  assert.doesNotMatch(html, /document\.documentElement\.innerHTML\s*=/,
    'the document must never be replaced wholesale');
  assert.doesNotMatch(html, /Android\|iPhone\|iPad\|iPod\|webOS/,
    'no user-agent sniffing that swaps out the app');
  assert.doesNotMatch(html, /Syrmos - Download the App/,
    'the store landing-page title must be gone');
});

test('a secondary, dismissible store banner exists and is localized', () => {
  assert.match(html, /id="storeBanner"/, 'store banner element missing');
  assert.match(html, /class="store-banner"/, 'store banner class missing');
  // Starts hidden; JS decides whether to show it (never blocks first paint).
  assert.match(html, /id="storeBanner"[^>]*\shidden/, 'banner must start hidden');
  assert.match(html, /data-i18n="get_the_app"/, 'banner text must be localized');
  assert.match(html, /id="storeBannerDismiss"/, 'dismiss control missing');
  assert.match(html, /data-i18n-aria="dismiss"/, 'dismiss control must be labelled/localized');
  // Both stores still promoted (secondary), links preserved.
  assert.match(html, /apps\.apple\.com\/app\/id6777650671/, 'App Store link preserved');
  assert.match(html, /play\.google\.com\/store\/apps\/details\?id=com\.syrmos\.android/,
    'Google Play link preserved');
});

test('the banner only shows on narrow viewports and remembers dismissal', () => {
  assert.match(html, /window\.matchMedia\("\(max-width: 599px\)"\)/,
    'banner gated to phone-width viewports');
  assert.match(html, /syrmos\.storeBanner\.dismissed/, 'a persisted dismissal key is required');
  assert.match(html, /localStorage\.setItem\(STORE_BANNER_DISMISSED, "1"\)/,
    'dismissal must persist');
  assert.match(html, /banner\.hidden = dismissed\(\) \|\| !narrow\.matches;/,
    'visibility = narrow AND not dismissed');
});

test('banner has secondary styling, not a full-page takeover', () => {
  assert.match(css, /\.store-banner\s*\{/, 'store-banner styles missing');
  assert.match(css, /\.store-banner__close/, 'dismiss button styles missing');
});

test('the two banner strings exist in every one of the four locales', () => {
  for (const key of ['get_the_app', 'dismiss']) {
    const count = (js.match(new RegExp('\\b' + key + ':', 'g')) || []).length;
    assert.equal(count, 4, `"${key}" must be defined in all four language blocks (found ${count})`);
  }
});
