'use strict';
// Static-source guardrails for the web client. These encode bug classes that
// have actually shipped and been fixed, so a regression re-fails CI instead of
// reaching production. They read the source as text (no DOM/bundler needed), so
// they are robust against the 5k-line web-map.js monolith having no unit seams.
//
// Run: `node --test` from web-tests/, or `node --test web-tests/` from the repo root.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const read = (p) => fs.readFileSync(path.join(RES, p), 'utf8');

test('web-map.js: no wall-clock read from a bare `new Date()` (Athens-time rule)', () => {
  // Schedule/countdown code must derive wall-clock fields from athensNow() /
  // currentAthensParts() (Europe/Athens), never from a device-local `new Date()`.
  // The failure this guards is departures/countdowns being wrong for any viewer
  // whose device is not on Athens time. `athensNow()` deliberately returns an
  // offset Date whose LOCAL getters read Athens; a bare `new Date().getHours()`
  // is the bug.
  const forbidden = /new Date\(\s*\)\s*\.\s*(getHours|getMinutes|getSeconds|getDay|getDate|getMonth)\b/g;
  for (const file of ['web-map.js', 'web-ariadne.js']) {
    const src = read(file);
    const hits = [...src.matchAll(forbidden)].map((m) => m[0]);
    assert.deepEqual(
      hits,
      [],
      `${file} reads wall-clock from a bare new Date(): ${hits.join(', ')} — use athensNow()/currentAthensParts()`
    );
  }
});

test('web-map.css: .live-train-marker keeps position:absolute (no "trains in the sea")', () => {
  // Leaflet positions every marker icon with an absolute transform. Overriding
  // the icon to position:relative drops it into normal flow so markers stack and
  // render south of their true pixel — live trains "marching into the sea". The
  // fix is that the .live-train-marker rule stays position:absolute.
  const css = read('web-map.css');
  // Anchor at a selector boundary so we match the standalone `.live-train-marker`
  // rule, not the compound `.leaflet-marker-icon.live-train-marker` rule (whose
  // body is only `overflow: visible`).
  const m = /(?:^|[\n;}])\s*\.live-train-marker\s*\{/m.exec(css);
  assert.notEqual(m, null, 'standalone .live-train-marker rule not found in web-map.css');
  const start = m.index + m[0].length;
  const rawBlock = css.slice(start, css.indexOf('}', start));
  // Strip CSS comments so the rule's own explanatory comment (which mentions
  // "position:relative" as the bug it prevents) is not mistaken for a declaration.
  const decls = rawBlock.replace(/\/\*[\s\S]*?\*\//g, '');
  assert.match(decls, /position\s*:\s*absolute/, '.live-train-marker must set position:absolute');
  assert.doesNotMatch(
    decls,
    /position\s*:\s*relative/,
    '.live-train-marker must NOT be position:relative (drops live trains into the sea)'
  );
});

test('index.html: no hls.js CDN egress reintroduced (privacy hardening)', () => {
  // The 2.0.0 privacy pass removed a dead hls.js CDN <script>. Guard against it
  // silently returning, since every third-party CDN is an egress + privacy point.
  const html = read('index.html');
  assert.doesNotMatch(html, /hls(\.min)?\.js/i, 'index.html reintroduced an hls.js reference');
});
