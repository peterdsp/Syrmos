'use strict';
// 3.0.2 growth: /get-app/?src=<code> carries the placement code into the store
// links (App Store campaign token ct, Play install referrer utm_source) so print
// and online placements can be measured in the store consoles. The page itself
// stores and sends nothing. docs/marketing/3.0.2-GROWTH-RESEARCH.md section 7.2.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const page = fs.readFileSync(path.join(RES, 'get-app', 'index.html'), 'utf8');
const block = page.match(/\/\/ campaign:start([\s\S]*?)\/\/ campaign:end/)[1];
const { campaignSource, storeUrls } = new Function(`${block}; return { campaignSource, storeUrls };`)();

const IOS = 'https://apps.apple.com/app/id6777650671';
const PLAY = 'https://play.google.com/store/apps/details?id=com.syrmos.android';

test('no or malformed src keeps the plain store links', () => {
  for (const search of ['', '?', '?lang=el', '?src=', '?src=%E0%A4%A', '?src=bad_code', '?src=a%20b',
    '?src=<script>', '?src=' + 'a'.repeat(33), '?src=s1&x', '?src=x%26ct%3Devil']) {
    const src = campaignSource(search);
    if (search === '?src=s1&x') { assert.equal(src, 's1'); continue; }
    assert.equal(src, null, `"${search}" must not produce a campaign code`);
    assert.deepEqual(storeUrls(src), { ios: IOS, android: PLAY });
  }
});

test('src is sanitised to [a-z0-9-]{1,32}, case-folded', () => {
  assert.equal(campaignSource('?src=s3-hst'), 's3-hst');
  assert.equal(campaignSource('?lang=el&src=S10-MEET'), 's10-meet');
  assert.equal(campaignSource('?src=' + 'a'.repeat(32)), 'a'.repeat(32));
});

test('a code becomes the App Store ct and the Play utm_source', () => {
  const u = storeUrls(campaignSource('?src=s3-hst'));
  assert.equal(u.ios, IOS + '?ct=s3-hst&mt=8');
  assert.equal(u.android, PLAY + '&referrer=utm_source%3Ds3-hst%26utm_medium%3Dprint');
  // the provider token is unknown, so it is not invented
  assert.doesNotMatch(u.ios, /pt=/);
});

test('the page applies the links on load and never phones home', () => {
  assert.match(page, /var urls = storeUrls\(campaignSource\(location\.search\)\);/);
  assert.match(page, /getElementById\("storeIos"\)\.href = urls\.ios;/);
  assert.match(page, /getElementById\("storeAndroid"\)\.href = urls\.android;/);
  assert.doesNotMatch(page, /sendBeacon|fetch\(|XMLHttpRequest/);
});
