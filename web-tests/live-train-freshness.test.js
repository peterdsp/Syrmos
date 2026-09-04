'use strict';
// Real-GPS live-train marker freshness on the web map. Mirrors the KMP
// LiveVehicleFreshnessTest and iOS test_freshness_classifier_* so all three
// clients age a live position out identically (90s fresh window, 600s expiry):
// a stale batch is drawn de-emphasised, an expired one is dropped, and neither
// is ever shown as a plain pulsing "live" dot.
//
// classifyLiveBatch lives inside the web-map IIFE (not requireable), so it is
// brace-extracted from source and evaluated - genuine executable coverage of
// the shipped function, plus static-source guardrails for the marker wiring.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const js = fs.readFileSync(path.join(RES, 'web-map.js'), 'utf8');

// Pull the two window constants + the classifyLiveBatch function out of the IIFE
// by balanced-brace matching, and evaluate them in isolation.
function extractFunction(src, name) {
  const start = src.indexOf('function ' + name);
  assert.notEqual(start, -1, `function ${name} not found in source`);
  const bodyStart = src.indexOf('{', start);
  let depth = 0;
  for (let i = bodyStart; i < src.length; i++) {
    if (src[i] === '{') depth++;
    else if (src[i] === '}') {
      depth--;
      if (depth === 0) return src.slice(start, i + 1);
    }
  }
  throw new Error(`unbalanced braces extracting ${name}`);
}

const freshConst = (js.match(/const LIVE_FRESH_WINDOW_SEC = (\d+);/) || [])[1];
const expiryConst = (js.match(/const LIVE_EXPIRY_SEC = (\d+);/) || [])[1];
const classifyLiveBatch = new Function(
  `const LIVE_FRESH_WINDOW_SEC = ${freshConst};
   const LIVE_EXPIRY_SEC = ${expiryConst};
   ${extractFunction(js, 'classifyLiveBatch')}
   return classifyLiveBatch;`
)();

const NOW = 1_000_000_000; // fixed ms clock
const iso = (ageSec) => new Date(NOW - ageSec * 1000).toISOString();

test('windows match the cross-platform contract (90s fresh, 600s expiry)', () => {
  assert.equal(freshConst, '90');
  assert.equal(expiryConst, '600');
});

test('fresh batch is live', () => {
  assert.equal(classifyLiveBatch(iso(10), NOW).state, 'live');
  assert.equal(classifyLiveBatch(iso(90), NOW).state, 'live', '90s boundary still live');
});

test('aged batch is stale, past 10 min is expired', () => {
  assert.equal(classifyLiveBatch(iso(91), NOW).state, 'stale');
  assert.equal(classifyLiveBatch(iso(300), NOW).state, 'stale');
  assert.equal(classifyLiveBatch(iso(600), NOW).state, 'stale', '600s boundary still stale');
  assert.equal(classifyLiveBatch(iso(601), NOW).state, 'expired');
  assert.equal(classifyLiveBatch(iso(300), NOW).ageSec, 300);
});

test('missing / malformed / far-future timestamps never read as live', () => {
  assert.equal(classifyLiveBatch(null, NOW).state, 'unknown');
  assert.equal(classifyLiveBatch('', NOW).state, 'unknown');
  assert.equal(classifyLiveBatch('not-a-date', NOW).state, 'unknown');
  // small clock skew tolerated as just-now
  assert.equal(classifyLiveBatch(iso(-60), NOW).state, 'live');
  // far future is not trusted
  assert.equal(classifyLiveBatch(iso(-100000), NOW).state, 'unknown');
});

// --- static-source guardrails: the marker wiring uses the batch state ---

test('renderLiveTrains drops an EXPIRED batch instead of plotting ghosts', () => {
  assert.match(js, /const batch = classifyLiveBatch\(liveTrainsUpdatedAt, Date\.now\(\)\);/,
    'renderLiveTrains must classify the batch');
  assert.match(js, /if \(batch\.state === "expired"\) \{\s*return;/,
    'an expired batch must skip drawing markers');
});

test('a STALE batch mutes every marker (never pulsing live)', () => {
  assert.match(js, /const staleBatch = batch\.state !== "live";/, 'staleBatch flag missing');
  assert.match(js, /const muted = notInService \|\| staleBatch;/, 'markers must mute on a stale batch');
  // the pulse ring is only emitted when NOT muted
  assert.match(js, /\$\{muted \? "" : `<span class="live-train-marker__pulse"/, 'pulse must be gated on !muted');
});

test('a timer re-renders from the last snapshot so markers age with no new data', () => {
  assert.match(js, /if \(lastLiveTrains && lastLiveTrains\.length\) renderLiveTrains\(lastLiveTrains\);/,
    're-render timer body missing');
});
