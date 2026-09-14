'use strict';
// The web app is desktop only. Phones and tablets use the native iOS / Android
// apps: index.html sends handheld devices to the standalone download screen
// /get-app/ before any app stylesheet, map or script loads. (This reverses the
// 3.0 F1 "every browser gets the real app" decision.) The product page uses the
// same device rule so its "Open web app" actions become "Get the app" there.
// Executable coverage of the shared detection function plus static guardrails.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const html = fs.readFileSync(path.join(RES, 'index.html'), 'utf8');
const js = fs.readFileSync(path.join(RES, 'web-map.js'), 'utf8');
const css = fs.readFileSync(path.join(RES, 'web-map.css'), 'utf8');
const product = fs.readFileSync(path.join(RES, 'product', 'index.html'), 'utf8');
const getApp = fs.readFileSync(path.join(RES, 'get-app', 'index.html'), 'utf8');

function extractFunction(src, name) {
  const start = src.indexOf('function ' + name);
  assert.notEqual(start, -1, `function ${name} not found`);
  const bodyStart = src.indexOf('{', start);
  let depth = 0;
  for (let i = bodyStart; i < src.length; i++) {
    if (src[i] === '{') depth++;
    else if (src[i] === '}') { depth--; if (depth === 0) return src.slice(start, i + 1); }
  }
  throw new Error(`unbalanced braces extracting ${name}`);
}
const load = (src) => new Function(`${extractFunction(src, 'isHandheldDevice')}; return isHandheldDevice;`)();
const isHandheld = load(html);

const HANDHELD = [
  ['iPhone Safari', 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1', 5],
  ['iPad legacy UA', 'Mozilla/5.0 (iPad; CPU OS 12_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/12.1 Mobile/15E148 Safari/604.1', 5],
  ['iPadOS desktop-class UA', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15', 5],
  ['Android phone Chrome', 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36', 5],
  ['Android tablet Chrome', 'Mozilla/5.0 (Linux; Android 14; SM-X710) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36', 5],
  ['Firefox Android', 'Mozilla/5.0 (Android 14; Mobile; rv:130.0) Gecko/130.0 Firefox/130.0', 5],
  ['Samsung Internet', 'Mozilla/5.0 (Linux; Android 14; SM-S921B) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/26.0 Chrome/122.0.0.0 Mobile Safari/537.36', 5],
  ['Kindle Silk', 'Mozilla/5.0 (Linux; Android 11; KFTRWI) AppleWebKit/537.36 (KHTML, like Gecko) Silk/128.1.1 like Chrome/128.0.0.0 Safari/537.36', 5],
];
const DESKTOP = [
  ['macOS Safari', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15', 0],
  ['macOS Chrome', 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36', 0],
  ['Windows Chrome', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36', 0],
  ['Windows touch laptop Edge', 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0', 10],
  ['Linux Firefox', 'Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0', 0],
  ['ChromeOS', 'Mozilla/5.0 (X11; CrOS x86_64 14541.0.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36', 1],
];

test('phones and tablets (including iPadOS desktop-class Safari) are handheld', () => {
  for (const [name, ua, touch] of HANDHELD) assert.equal(isHandheld(ua, touch), true, `${name} must be handheld`);
});

test('desktop and laptop browsers keep the web app, touchscreen or not', () => {
  for (const [name, ua, touch] of DESKTOP) assert.equal(isHandheld(ua, touch), false, `${name} must keep the web app`);
  assert.equal(isHandheld('', 0), false, 'an empty UA is not treated as handheld');
});

test('index.html redirects handheld devices to /get-app/ before loading anything', () => {
  const redirect = html.indexOf('location.replace("/get-app/")');
  assert.notEqual(redirect, -1, 'handheld redirect to /get-app/ missing');
  assert.match(html, /if \(isHandheldDevice\(navigator\.userAgent, navigator\.maxTouchPoints\)\)/);
  const firstStylesheet = html.indexOf('rel="stylesheet"');
  const firstExternalScript = html.search(/<script\s+src=/);
  assert.ok(redirect < firstStylesheet, 'redirect must run before any stylesheet is requested');
  assert.ok(redirect < firstExternalScript, 'redirect must run before any app script is requested');
  // stop() cancels resources the preload scanner already queued; it must come
  // BEFORE replace(), because stopping after it would cancel the redirect itself.
  const stop = html.indexOf('window.stop()');
  assert.ok(stop !== -1 && stop < redirect, 'window.stop() must run before location.replace("/get-app/")');
  assert.doesNotMatch(html, /document\.documentElement\.innerHTML\s*=/, 'the document is never replaced wholesale');
});

test('the product page classifies devices with the exact same rule', () => {
  assert.equal(extractFunction(product, 'isHandheldDevice'), extractFunction(html, 'isHandheldDevice').replace(/^ {12}/gm, '            '));
  const productRule = load(product);
  for (const [name, ua, touch] of [...HANDHELD, ...DESKTOP]) {
    assert.equal(productRule(ua, touch), isHandheld(ua, touch), `${name}: product page and app shell disagree`);
  }
  assert.match(product, /root\.classList\.add\("is-handheld"\)/, 'product page must flag handheld devices');
  assert.match(product, /class="btn btn--primary web-only" href="\/">Open web app/, 'header web action hidden on handheld');
});

test('the /get-app/ screen is standalone, localized and never boots the app', () => {
  assert.doesNotMatch(getApp, /isHandheldDevice|location\.replace/, 'no redirect on the download screen (no loop)');
  for (const forbidden of [/serviceWorker\.register/, /web-map\.js/, /leaflet/i, /\/files\/seed\//]) {
    assert.doesNotMatch(getApp, forbidden, `get-app must not reference ${forbidden}`);
  }
  assert.match(getApp, /href="https:\/\/apps\.apple\.com\/app\/id6777650671"/, 'App Store link');
  assert.match(getApp, /href="https:\/\/play\.google\.com\/store\/apps\/details\?id=com\.syrmos\.android"/, 'Google Play link');
  assert.match(getApp, /href="\/product\/"/, 'links to the product page');
  assert.match(getApp, /<meta name="robots" content="noindex">/);
  assert.match(getApp, /var LANG_STORAGE_KEY = "syrmos_lang";/, 'shares the web app language key');
  assert.match(js, /const LANG_STORAGE_KEY = "syrmos_lang";/, 'the web app still uses the same key');
  // Every language defines exactly the same keys, and every data-i18n key exists.
  const dict = new Function(`${getApp.match(/var I18N = (\{[\s\S]*?\n    \});/)[0]}; return I18N;`)();
  const langs = Object.keys(dict);
  assert.deepEqual(langs.sort(), ['el', 'en', 'it', 'sq']);
  const keys = Object.keys(dict.en).sort();
  for (const l of langs) assert.deepEqual(Object.keys(dict[l]).sort(), keys, `${l} is missing or has extra strings`);
  for (const m of getApp.matchAll(/data-i18n(?:-aria|-alt)?="([^"]+)"/g)) assert.ok(keys.includes(m[1]), `untranslated key ${m[1]}`);
  for (const l of langs) for (const k of keys) assert.ok(String(dict[l][k]).trim(), `${l}.${k} is empty`);
});

test('a secondary, dismissible store banner still exists for narrow desktop windows', () => {
  assert.match(html, /id="storeBanner"[^>]*\shidden/, 'banner must start hidden');
  assert.match(html, /data-i18n="get_the_app"/, 'banner text must be localized');
  assert.match(html, /id="storeBannerDismiss"/, 'dismiss control missing');
  assert.match(html, /window\.matchMedia\("\(max-width: 599px\)"\)/, 'banner gated to narrow viewports');
  assert.match(html, /localStorage\.setItem\(STORE_BANNER_DISMISSED, "1"\)/, 'dismissal must persist');
  assert.match(css, /\.store-banner\s*\{/, 'store-banner styles missing');
  for (const key of ['get_the_app', 'dismiss']) {
    const count = (js.match(new RegExp('\\b' + key + ':', 'g')) || []).length;
    assert.equal(count, 4, `"${key}" must be defined in all four language blocks (found ${count})`);
  }
});
