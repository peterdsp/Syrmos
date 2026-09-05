/*
 * Syrmos offline Service Worker.
 *
 * Goal: after one successful load the web app opens and runs with NO network -
 * the page shell, the hand-written JS, the bundled seed (stations / lines /
 * routes / schedules-v2 / offsets) and the map overlays all come from cache, and
 * a reload or a mid-session connectivity drop never leaves a blank screen. Live
 * data still enriches the same UI when online.
 *
 * GitHub Pages ships the app bundles content-hashed (composeApp.<hash>.js,
 * web-map.<hash>.js, web-map.<hash>.css) and rewrites index.html to point at
 * them. The SW therefore does NOT precache those by name - it runtime-caches
 * whatever the page actually loads, so a new deploy's fresh hashes are picked up
 * automatically. Navigations are network-first (an online user always gets the
 * latest index.html -> latest hashes), so there is no stale-app trap.
 */
const VERSION = "v1";
const CACHE = `syrmos-${VERSION}`;
const TILE_CACHE = `syrmos-tiles-${VERSION}`;
const TILE_MAX = 300; // opportunistic, capped: only tiles the user actually viewed

// Stable, non-content-hashed critical assets, known by name. The hashed bundles
// are intentionally absent (runtime-cached instead). One failed entry must not
// fail the whole install, so each is fetched individually.
const PRECACHE = [
  "/",
  "/index.html",
  "/manifest.webmanifest",
  "/favicon.png",
  "/design-tokens.css",
  "/web-ariadne-rag.js",
  "/web-ariadne.js",
  "/web-router.js",
  "/web-nav.js",
  "/web-go.js",
  "/web-airport.js",
  "/web-departures.js",
  "/web-planner.js",
  "/web-go-panel.js",
  "/shapes.json",
  "/files/seed/stations.json",
  "/files/seed/schedules-v2/lines.json",
  "/files/seed/routes.json",
  "/files/seed/service_patterns.json",
  "/files/seed/station-offsets.json",
  "/icons/vehicles/manifest.json",
  "/icons/stations/manifest.json",
  // Leaflet from its CDN (cross-origin; stored as an opaque response).
  "https://unpkg.com/leaflet@1.9.4/dist/leaflet.js",
  "https://unpkg.com/leaflet@1.9.4/dist/leaflet.css",
];

self.addEventListener("install", (event) => {
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE);
    await Promise.all(PRECACHE.map(async (url) => {
      try {
        const res = await fetch(new Request(url, { cache: "reload" }));
        if (res && (res.ok || res.type === "opaque")) await cache.put(url, res.clone());
      } catch (_) { /* a missing optional asset must not abort install */ }
    }));
    await self.skipWaiting();
  })());
});

self.addEventListener("activate", (event) => {
  event.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(
      keys
        .filter((k) => (k.startsWith("syrmos-") && k !== CACHE && k !== TILE_CACHE))
        .map((k) => caches.delete(k)),
    );
    await self.clients.claim();
  })());
});

self.addEventListener("fetch", (event) => {
  const req = event.request;
  if (req.method !== "GET") return;
  let url;
  try { url = new URL(req.url); } catch (_) { return; }

  // 1) HTML navigations: network-first so a new deploy lands; cached shell offline.
  if (req.mode === "navigate") {
    event.respondWith(networkFirstNavigation(req));
    return;
  }
  // 2) Live API: network-first, fall back to the last cached response (stale) or
  //    an honest 503 the app's own .catch/!res.ok handlers already expect.
  if (url.hostname === "api-syrmos.peterdsp.dev") {
    event.respondWith(networkFirstApi(req));
    return;
  }
  // 3) Map tiles: cache-first into a capped, separate cache (opportunistic - only
  //    tiles the user actually loaded; never a bulk pre-download).
  if (isTile(url)) {
    event.respondWith(cacheFirstTile(req));
    return;
  }
  // 4) Everything else (same-origin assets + seed, cross-origin Leaflet):
  //    cache-first, then network (and cache it for next time).
  event.respondWith(cacheFirst(req));
});

function isTile(url) {
  const h = url.hostname;
  return (
    h.includes("arcgisonline.com") ||
    h.includes("basemaps.cartocdn.com") ||
    h.includes("tile.openstreetmap.org") ||
    /\/tile\/\d+\//.test(url.pathname)
  );
}

async function networkFirstNavigation(req) {
  const cache = await caches.open(CACHE);
  try {
    const res = await fetch(req);
    if (res && res.ok) cache.put("/index.html", res.clone());
    return res;
  } catch (_) {
    return (await cache.match("/index.html")) || (await cache.match("/")) || Response.error();
  }
}

async function networkFirstApi(req) {
  const cache = await caches.open(CACHE);
  try {
    const res = await fetch(req);
    if (res && res.ok) cache.put(req, res.clone());
    return res;
  } catch (_) {
    const cached = await cache.match(req);
    if (cached) return cached;
    return new Response(JSON.stringify({ offline: true }), {
      status: 503,
      headers: { "Content-Type": "application/json" },
    });
  }
}

async function cacheFirst(req) {
  const cache = await caches.open(CACHE);
  const cached = await cache.match(req);
  if (cached) return cached;
  try {
    const res = await fetch(req);
    if (res && (res.ok || res.type === "opaque")) cache.put(req, res.clone());
    return res;
  } catch (_) {
    return cached || Response.error();
  }
}

async function cacheFirstTile(req) {
  const cache = await caches.open(TILE_CACHE);
  const cached = await cache.match(req);
  if (cached) return cached;
  try {
    const res = await fetch(req);
    if (res && (res.ok || res.type === "opaque")) {
      await cache.put(req, res.clone());
      trimCache(TILE_CACHE, TILE_MAX);
    }
    return res;
  } catch (_) {
    return cached || Response.error();
  }
}

async function trimCache(name, max) {
  const cache = await caches.open(name);
  const keys = await cache.keys();
  if (keys.length <= max) return;
  for (const key of keys.slice(0, keys.length - max)) await cache.delete(key);
}
