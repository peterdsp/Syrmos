'use strict';
// Executes the REAL sw.js event handlers in Node against a mocked Cache API +
// controllable fetch, to prove the offline behaviour directly (no browser
// needed): install precaches the shell + seed, an offline navigation is served
// from cache, an offline asset is served from cache, the live API falls back to
// a cached response or an honest 503, tiles go to a capped separate cache, and
// activate purges stale caches. This is the runtime contract the browser relies
// on, verified as a deterministic unit test.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const SW_PATH = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources', 'sw.js');

// --- Minimal, spec-faithful mocks -------------------------------------------
class ResMock {
  constructor(body, init = {}) {
    this.body = body;
    this.status = init.status || 200;
    this.ok = this.status >= 200 && this.status < 300;
    this.type = init.type || 'basic';
    this._headers = init.headers || {};
  }
  clone() { return new ResMock(this.body, { status: this.status, type: this.type, headers: this._headers }); }
  async text() { return this.body; }
}
class ReqMock {
  constructor(url, init = {}) { this.url = typeof url === 'string' ? url : url.url; this.method = init.method || 'GET'; this.mode = init.mode; }
}
const keyOf = (req) => (typeof req === 'string' ? req : req.url);
class CacheMock {
  constructor() { this.store = new Map(); }
  async match(req) { return this.store.get(keyOf(req)); }
  async put(req, res) { this.store.set(keyOf(req), res); }
  async keys() { return [...this.store.keys()].map((k) => ({ url: k })); }
  async delete(req) { return this.store.delete(keyOf(req)); }
}
function makeCaches() {
  const map = new Map();
  return {
    map,
    async open(n) { if (!map.has(n)) map.set(n, new CacheMock()); return map.get(n); },
    async keys() { return [...map.keys()]; },
    async delete(n) { return map.delete(n); },
    async match(req) { for (const c of map.values()) { const hit = await c.match(req); if (hit) return hit; } return undefined; },
  };
}

// Load a fresh SW instance with an injected fetch. `net.online` toggles
// connectivity; `net.body(url)` supplies the network response body.
function loadSW(net) {
  const handlers = {};
  const self = {
    addEventListener: (t, h) => { handlers[t] = h; },
    skipWaiting: async () => {},
    clients: { claim: async () => {} },
  };
  const caches = makeCaches();
  const fetchMock = async (req) => {
    if (!net.online) throw new Error('offline');
    const url = keyOf(req);
    const opaque = url.startsWith('https://unpkg.com') || url.includes('arcgisonline.com');
    return new ResMock(net.body ? net.body(url) : `body:${url}`, opaque ? { type: 'opaque', status: 0 } : { status: 200 });
  };
  const swText = fs.readFileSync(SW_PATH, 'utf8');
  // eslint-disable-next-line no-new-func
  new Function('self', 'caches', 'fetch', 'Response', 'Request', 'URL', swText)(
    self, caches, fetchMock, ResMock, ReqMock, URL,
  );
  return { handlers, caches, self };
}
async function install(h) { let p; h.install({ waitUntil: (x) => { p = x; } }); await p; }
async function activate(h) { let p; h.activate({ waitUntil: (x) => { p = x; } }); await p; }
async function doFetch(h, request) { let r; h.fetch({ request, respondWith: (x) => { r = x; } }); return r ? await r : undefined; }
const shellCache = (caches) => caches.map.get('syrmos-v1');
const tileCache = (caches) => caches.map.get('syrmos-tiles-v1');

// --- Tests ------------------------------------------------------------------

test('install precaches the app shell and the bundled seed', async () => {
  const { handlers, caches } = loadSW({ online: true });
  await install(handlers);
  const c = shellCache(caches);
  assert.ok(await c.match('/index.html'), 'index.html precached');
  assert.ok(await c.match('/files/seed/schedules-v2/lines.json'), 'schedules seed precached');
  assert.ok(await c.match('/files/seed/stations.json'), 'stations seed precached');
  assert.ok(await c.match('/web-map.js') || await c.match('/design-tokens.css'), 'shell assets precached');
});

test('OFFLINE navigation is served the cached app shell (no blank page)', async () => {
  const net = { online: true };
  const { handlers } = loadSW(net);
  await install(handlers);
  net.online = false; // connection drops
  const res = await doFetch(handlers, new ReqMock('http://localhost:8791/', { mode: 'navigate' }));
  assert.ok(res, 'a response was produced offline');
  assert.equal(res.ok, true, 'offline navigation returns the cached shell, not an error');
});

test('OFFLINE static asset is served from cache after a prior online load', async () => {
  const net = { online: true };
  const { handlers } = loadSW(net);
  await install(handlers);
  const url = 'http://localhost:8791/web-map.612c4bf81048.js';
  const online = await doFetch(handlers, new ReqMock(url)); // caches on first (online) load
  assert.equal(online.ok, true);
  net.online = false;
  const offline = await doFetch(handlers, new ReqMock(url));
  assert.ok(offline && offline.ok, 'the hashed bundle is served from cache offline');
});

test('live API is network-first and falls back to a cached response, else 503', async () => {
  const net = { online: true };
  const { handlers } = loadSW(net);
  await install(handlers);
  const url = 'https://api-syrmos.peterdsp.dev/api/trains?x=1';
  const live = await doFetch(handlers, new ReqMock(url));
  assert.equal(live.ok, true, 'online: live response');
  net.online = false;
  const cached = await doFetch(handlers, new ReqMock(url));
  assert.ok(cached && cached.ok, 'offline: last cached API response is served');
  const unseen = await doFetch(handlers, new ReqMock('https://api-syrmos.peterdsp.dev/api/never-fetched'));
  assert.equal(unseen.status, 503, 'offline + never cached: honest 503 the app handles');
});

test('tiles go to a capped, separate cache (opportunistic, not the shell)', async () => {
  const net = { online: true };
  const { handlers, caches } = loadSW(net);
  await install(handlers);
  const tileUrl = 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Light_Gray_Base/MapServer/tile/7/45/72';
  await doFetch(handlers, new ReqMock(tileUrl));
  assert.ok(tileCache(caches), 'a dedicated tile cache exists');
  assert.ok(await tileCache(caches).match(tileUrl), 'the viewed tile is cached');
  assert.equal(await shellCache(caches).match(tileUrl), undefined, 'tiles do not pollute the shell cache');
});

test('activate purges stale caches from a previous version', async () => {
  const { handlers, caches } = loadSW({ online: true });
  await caches.open('syrmos-v0');          // a stale prior-version cache
  await caches.open('syrmos-tiles-v0');
  await install(handlers);
  await activate(handlers);
  const keys = await caches.keys();
  assert.ok(!keys.includes('syrmos-v0'), 'stale shell cache deleted');
  assert.ok(!keys.includes('syrmos-tiles-v0'), 'stale tile cache deleted');
  assert.ok(keys.includes('syrmos-v1'), 'current cache retained');
});
