'use strict';
// Exponential backoff with jitter for the live polls, mirroring the KMP
// PollBackoffTest and iOS test_pollBackoff_*. pollBackoffMs is IIFE-internal, so
// it is brace-extracted from source and executed (genuine coverage of the
// shipped function) + static-source guardrails that the three live loops were
// converted from fixed setInterval to the self-scheduling backoff loop.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const js = fs.readFileSync(path.join(RES, 'web-map.js'), 'utf8');

function extractFunction(src, name) {
  const start = src.indexOf('function ' + name);
  assert.notEqual(start, -1, `function ${name} not found`);
  const bodyStart = src.indexOf('{', start);
  let depth = 0;
  for (let i = bodyStart; i < src.length; i++) {
    if (src[i] === '{') depth++;
    else if (src[i] === '}') { depth--; if (depth === 0) return src.slice(start, i + 1); }
  }
  throw new Error('unbalanced braces');
}
const maxConst = (js.match(/const POLL_MAX_BACKOFF_MS = ([\d_]+);/) || [])[1];
const pollBackoffMs = new Function(
  `const POLL_MAX_BACKOFF_MS = ${maxConst};\n${extractFunction(js, 'pollBackoffMs')}\nreturn pollBackoffMs;`,
)();

// random01 = 0.5 -> multiplier 1.0 (no jitter), exact delays.
const d = (f, base, max = 60000) => pollBackoffMs(f, base, max, 0.25, 0.5);

test('cap constant matches the cross-platform 60s', () => {
  assert.equal(maxConst.replace(/_/g, ''), '60000');
});

test('success waits base; failures escalate; capped', () => {
  assert.equal(d(0, 10000), 10000);
  assert.equal(d(1, 10000), 20000);
  assert.equal(d(2, 10000), 40000);
  assert.equal(d(3, 10000), 60000); // 80000 capped to 60000
  assert.equal(d(10, 10000), 60000);
});

test('jitter stays within +/-25% and spreads', () => {
  assert.equal(pollBackoffMs(2, 10000, 60000, 0.25, 0.0), 30000); // 40000 * 0.75
  assert.equal(pollBackoffMs(2, 10000, 60000, 0.25, 1.0), 50000); // 40000 * 1.25
  // base success delay is jittered too (desync): 10000 -> [7500, 12500]
  assert.equal(pollBackoffMs(0, 10000, 60000, 0.25, 0.0), 7500);
  const spread = [0, 0.2, 0.4, 0.6, 0.8, 1.0].map((r) => pollBackoffMs(2, 10000, 60000, 0.25, r));
  assert.ok(spread.every((v) => v >= 30000 && v <= 50000));
  assert.ok(new Set(spread).size > 1, 'jitter must spread, not fix, the delay');
});

test('negative failures treated as zero', () => {
  assert.equal(d(-4, 15000), 15000);
});

// --- static-source guardrails: loops use the backoff scheduler ---

test('a self-scheduling backoff loop replaced fixed setInterval polling', () => {
  assert.match(js, /function startPollLoop\(pollOnce, baseMs/, 'startPollLoop helper missing');
  assert.match(js, /setTimeout\(loop, pollBackoffMs\(failures, baseMs, maxMs\)\);/, 'loop must schedule via pollBackoffMs');
  // the three live feeds drive it, and pollOnce returns a success boolean
  assert.match(js, /startPollLoop\(pollOnce, POLL_INTERVAL_MS\);/, 'trains loop not on backoff');
  assert.match(js, /startPollLoop\(pollOnce, 15_000\);/, 'airport loop not on backoff');
  assert.match(js, /startPollLoop\(tick, 15000\);/, 'live-positions loop not on backoff');
  // trains/airport/positions pollOnce/tick now return true on success
  assert.match(js, /updateLiveTrains\(payload\.trains \|\| \[\], payload\.updatedAt\);\s*\n\s*markApiOk\(\);\s*\n\s*return true;/,
    'trains pollOnce must return true on success');
});
