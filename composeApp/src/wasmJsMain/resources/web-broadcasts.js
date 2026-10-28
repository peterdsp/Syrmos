'use strict';
// Syrmos 3.0.2 broadcasts (web reference).
//
// The web has no push, so it reads GET /api/broadcasts?since=<epochSeconds> on
// load and on a slow poll, and shows the active ones in a dismissible banner.
// Rules from docs/3.0.2-CONTRACTS.md: a broadcast shows when its audience is
// `all`, matches the selected region, or matches a line/station the rider keeps
// (saved journeys and saved departures stand in for favourites on the web).
// Expired broadcasts never show; everything dedupes by id.
//
// Pure: the caller passes the epoch-second clock and the storage blobs.
//
// UMD: `require('./web-broadcasts.js')` in node, `window.SyrmosBroadcasts` in browser.
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.SyrmosBroadcasts = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  const DISMISSED_KEY = 'syrmos.broadcasts.dismissed.v1';
  const CACHE_KEY = 'syrmos.broadcasts.cache.v1';
  const DISMISSED_CAP = 200;

  function audienceMatches(audience, ctx) {
    const a = String(audience || '').trim();
    const c = ctx || {};
    if (a === 'all') return true;
    const i = a.indexOf(':');
    if (i < 0) return false;
    const kind = a.slice(0, i);
    const value = a.slice(i + 1);
    if (kind === 'region') return !!c.region && c.region === value;
    if (kind === 'line') return (c.lineIds || []).indexOf(value) >= 0;
    if (kind === 'station') return (c.stationIds || []).indexOf(value) >= 0;
    return false;
  }

  function isExpired(b, now) {
    return b.expiresAtEpochSeconds != null && now >= b.expiresAtEpochSeconds;
  }

  function valid(b) {
    return !!b && typeof b.id === 'string' && b.id && Number.isFinite(b.sentAtEpochSeconds);
  }

  function newestFirst(a, b) {
    return (b.sentAtEpochSeconds - a.sentAtEpochSeconds) || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0);
  }

  // Merge a fresh page into what we already hold: dedupe by id (the incoming
  // copy wins, it is newer), drop expired, newest first.
  function merge(existing, incoming, now) {
    const byId = new Map();
    for (const b of (existing || [])) if (valid(b)) byId.set(b.id, b);
    for (const b of (incoming || [])) if (valid(b)) byId.set(b.id, b);
    return Array.from(byId.values()).filter((b) => !isExpired(b, now)).sort(newestFirst);
  }

  // Broadcasts the banner should show right now.
  function visible(broadcasts, ctx, now, dismissedIds) {
    const dismissed = new Set(dismissedIds || []);
    return merge([], broadcasts, now)
      .filter((b) => !dismissed.has(b.id) && audienceMatches(b.audience, ctx));
  }

  // The `since` for the next poll: the newest sentAt we hold, else the fallback.
  function nextSince(broadcasts, fallback) {
    let best = fallback == null ? 0 : fallback;
    for (const b of (broadcasts || [])) if (valid(b) && b.sentAtEpochSeconds > best) best = b.sentAtEpochSeconds;
    return best;
  }

  function decodeIds(raw) {
    try {
      const v = JSON.parse(raw || '[]');
      return Array.isArray(v) ? v.filter((x) => typeof x === 'string') : [];
    } catch (_) {
      return [];
    }
  }

  // Append an id, keeping only the most recent DISMISSED_CAP so the blob stays small.
  function encodeIds(ids, add) {
    const list = (ids || []).filter((x) => x !== add);
    if (add) list.push(add);
    return JSON.stringify(list.slice(-DISMISSED_CAP));
  }

  function decodeCache(raw) {
    try {
      const v = JSON.parse(raw || 'null');
      return v && Array.isArray(v.broadcasts) ? v.broadcasts.filter(valid) : [];
    } catch (_) {
      return [];
    }
  }

  function encodeCache(list) {
    return JSON.stringify({ schemaVersion: 1, broadcasts: list || [] });
  }

  // "syrmos://station/M2_SYN" -> "M2_SYN"; anything else -> null.
  function stationIdFromDeepLink(link) {
    const m = /^syrmos:\/\/station\/([A-Za-z0-9_]+)$/.exec(String(link || '').trim());
    return m ? m[1] : null;
  }

  return {
    DISMISSED_KEY, CACHE_KEY, DISMISSED_CAP,
    audienceMatches, isExpired, merge, visible, nextSince,
    decodeIds, encodeIds, decodeCache, encodeCache, stationIdFromDeepLink,
  };
});
