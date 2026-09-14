'use strict';
// The /product/ showcase is a standalone static page. These tests lock the
// route output of the real Pages staging script, and the isolation rules that
// keep the page from booting (or overwriting) the transit app.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

const ROOT = path.join(__dirname, '..');
const RES = path.join(ROOT, 'composeApp', 'src', 'wasmJsMain', 'resources');
const PRODUCT = path.join(RES, 'product');
const html = fs.readFileSync(path.join(PRODUCT, 'index.html'), 'utf8');
const js = fs.readFileSync(path.join(PRODUCT, 'product.js'), 'utf8');
const map = fs.readFileSync(path.join(RES, 'web-map.js'), 'utf8');
const sitemap = fs.readFileSync(path.join(RES, 'sitemap.xml'), 'utf8');

function stage(withProduct) {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'syrmos-pages-'));
  const src = path.join(tmp, 'src');
  const out = path.join(tmp, 'out');
  fs.mkdirSync(src);
  fs.writeFileSync(path.join(src, 'index.html'), '<div id="map"></div><script src="/web-map.js"></script>');
  fs.writeFileSync(path.join(src, 'web-map.js'), '// app');
  fs.writeFileSync(path.join(src, 'privacy.html'), '<title>Privacy Policy</title>');
  if (withProduct) {
    fs.mkdirSync(path.join(src, 'product'));
    fs.copyFileSync(path.join(PRODUCT, 'index.html'), path.join(src, 'product', 'index.html'));
  }
  return { tmp, src, out };
}

test('staging emits a standalone product/index.html next to the app routes', () => {
  const { tmp, src, out } = stage(true);
  try {
    execFileSync('bash', [path.join(ROOT, 'scripts', 'prepare-pages-web-release.sh'), src, out], { stdio: 'pipe' });
    const product = fs.readFileSync(path.join(out, 'product', 'index.html'), 'utf8');
    const shell = fs.readFileSync(path.join(out, 'index.html'), 'utf8');
    assert.equal(product, html, 'product page ships unchanged');
    assert.notEqual(product, shell, 'product page is not a copy of the app shell');
    for (const route of ['now', 'plan', 'explore', 'departures', 'tickets', 'line', 'station']) {
      assert.equal(fs.readFileSync(path.join(out, route, 'index.html'), 'utf8'), shell, `${route} still serves the app`);
    }
    assert.ok(fs.existsSync(path.join(out, 'privacy', 'index.html')), 'privacy route still staged');
  } finally {
    fs.rmSync(tmp, { recursive: true, force: true });
  }
});

test('staging fails loudly when the product page is missing', () => {
  const { tmp, src, out } = stage(false);
  try {
    assert.throws(
      () => execFileSync('bash', [path.join(ROOT, 'scripts', 'prepare-pages-web-release.sh'), src, out], { stdio: 'pipe' }),
      /product\/index\.html is missing/,
    );
  } finally {
    fs.rmSync(tmp, { recursive: true, force: true });
  }
});

test('product is never in the app-shell route loop', () => {
  const script = fs.readFileSync(path.join(ROOT, 'scripts', 'prepare-pages-web-release.sh'), 'utf8');
  const loop = script.match(/for route in ([^;]+); do/)[1];
  assert.doesNotMatch(loop, /\bproduct\b/);
});

test('the product page does not boot the transit app or register its worker', () => {
  for (const forbidden of [/web-map\.js/, /leaflet/i, /serviceWorker\.register/, /\/files\/seed\//, /ariadne-wllama|\/llm\//, /geolocation/]) {
    assert.doesNotMatch(html, forbidden, `product HTML must not reference ${forbidden}`);
    assert.doesNotMatch(js, forbidden, `product.js must not reference ${forbidden}`);
  }
  assert.doesNotMatch(html, /<base\s/i, 'no global <base> element');
});

test('every local asset reference is root-absolute and exists', () => {
  const refs = [...html.matchAll(/(?:src|href|srcset|imagesrcset)="([^"]+)"/g)]
    .flatMap((m) => m[1].split(',').map((s) => s.trim().split(/\s+/)[0]))
    .filter((u) => u && !u.startsWith('http') && !u.startsWith('#') && !u.startsWith('mailto:'));
  assert.ok(refs.length > 10);
  for (const ref of refs) {
    assert.ok(ref.startsWith('/'), `relative reference ${ref}`);
    const clean = ref.split(/[?#]/)[0];
    // Routes, plus press.html which the staging script copies in from docs/.
    if (['/', '/privacy', '/product/'].includes(clean)) continue;
    if (clean === '/press.html') { assert.ok(fs.existsSync(path.join(ROOT, 'docs', 'press.html'))); continue; }
    assert.ok(fs.existsSync(path.join(RES, clean)), `missing asset ${ref}`);
  }
});

test('platform destinations are the verified store and web URLs', () => {
  assert.match(html, /href="https:\/\/apps\.apple\.com\/app\/id6777650671"/);
  assert.match(html, /href="https:\/\/play\.google\.com\/store\/apps\/details\?id=com\.syrmos\.android"/);
  assert.match(html, /class="btn btn--primary btn--lg" href="\/">Open web app/);
  const external = [...html.matchAll(/href="(https:[^"]+)"/g)].map((m) => m[1]);
  for (const url of external) {
    assert.ok(/^https:\/\/(apps\.apple\.com|play\.google\.com|syrmos\.peterdsp\.dev|peterdsp\.dev|github\.com\/peterdsp)/.test(url), `unexpected destination ${url}`);
  }
});

test('one H1, canonical URL, and no fabricated social proof', () => {
  assert.equal((html.match(/<h1[\s>]/g) || []).length, 1);
  assert.match(html, /<link rel="canonical" href="https:\/\/syrmos\.peterdsp\.dev\/product\/">/);
  assert.doesNotMatch(html, /aggregateRating|reviewCount|ratingValue|testimonial/i);
  assert.doesNotMatch(html, /google-analytics|gtag\(|plausible|matomo/i);
});

test('the product URL is in the sitemap', () => {
  assert.match(sitemap, /<loc>https:\/\/syrmos\.peterdsp\.dev\/product\/<\/loc>/);
});

test('the app links to the product page from its info list, in all four languages', () => {
  const count = (map.match(/\babout_syrmos:/g) || []).length;
  assert.equal(count, 4, `about_syrmos must be defined in all four language blocks (found ${count})`);
  assert.match(map, /href="\/product\/">\$\{escapeHtml\(t\("about_syrmos"\)\)\}/);
});
