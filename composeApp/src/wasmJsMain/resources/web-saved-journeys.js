'use strict';
// Syrmos 3.0 saved journeys (web reference implementation, prompt S08 / J05).
//
// A locally-owned list of regular journeys. No account, no server: the whole list
// is one versioned root persisted in localStorage under a single key. Mirrors the
// Kotlin com.syrmos.core.model.journey.SavedJourney contract and the shared golden
// fixture fixtures/journeys/saved.json exactly:
//   - root shape: { schemaVersion, savedJourneys: [SavedJourney...] }
//   - display order = array order; a fresh save is prepended (newest first)
//   - HARD de-dup invariant: at most one saved journey per (fromId,toId); saving an
//     existing pair updates it in place (keeping its id), refreshes label/createdAt,
//     and moves it to the top, never adding a duplicate row
//   - decode is atomic + versioned: a newer schema returns 'unsupported' and a
//     corrupt blob returns 'corrupt', so a valid store is never silently lost
//
// The pure ops (save/rename/remove/reorder/decode/encode) take and return plain
// arrays so they are trivially testable and byte-parity with the other clients.
// The store wrapper binds them to localStorage.
//
// UMD: `require('./web-saved-journeys.js')` / `window.SyrmosSavedJourneys`.
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.SyrmosSavedJourneys = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  const SCHEMA_VERSION = 1;
  const STORAGE_KEY = 'syrmos.saved-journeys.v1';

  function defaultPreferences() {
    return { ranking: 'fastest', accessibilityPreference: 'none' };
  }

  // Normalise a raw object into a well-formed SavedJourney, filling contract
  // defaults. Returns null when the required fields are missing (never invents a
  // journey with no endpoints).
  function normalize(item) {
    if (!item || typeof item !== 'object') return null;
    if (!item.fromId || !item.toId || !item.id) return null;
    const prefs = (item.preferences && typeof item.preferences === 'object') ? item.preferences : {};
    return {
      id: String(item.id),
      fromId: String(item.fromId),
      toId: String(item.toId),
      createdAt: item.createdAt || null,
      label: (typeof item.label === 'string' && item.label.trim()) ? item.label : null,
      preferences: {
        ranking: prefs.ranking || 'fastest',
        accessibilityPreference: prefs.accessibilityPreference || 'none',
      },
      schemaVersion: SCHEMA_VERSION,
    };
  }

  // Prepend a new save, de-duplicating by (fromId,toId). An existing pair is
  // updated in place: its id is preserved, its label/createdAt/preferences are
  // refreshed from the new entry, and it moves to the top.
  function save(list, entry) {
    const next = normalize(entry);
    if (!next) return Array.isArray(list) ? list.slice() : [];
    const rest = (Array.isArray(list) ? list : []).map(normalize).filter(Boolean);
    const existing = rest.find((s) => s.fromId === next.fromId && s.toId === next.toId);
    const merged = existing
      ? Object.assign({}, existing, {
          createdAt: next.createdAt,
          label: next.label,
          preferences: next.preferences,
        })
      : next;
    const without = rest.filter((s) => !(s.fromId === next.fromId && s.toId === next.toId));
    return [merged].concat(without);
  }

  // Set (or clear) the label on one saved journey. A blank/whitespace label
  // clears back to null; no name is invented.
  function rename(list, id, label) {
    const clean = (typeof label === 'string' && label.trim()) ? label.trim() : null;
    return (Array.isArray(list) ? list : []).map(normalize).filter(Boolean)
      .map((s) => (s.id === id ? Object.assign({}, s, { label: clean }) : s));
  }

  function remove(list, id) {
    return (Array.isArray(list) ? list : []).map(normalize).filter(Boolean)
      .filter((s) => s.id !== id);
  }

  // Reorder to an explicit id sequence. Ids not present in `order` are dropped and
  // unknown ids ignored, so the caller controls the exact display order.
  function reorder(list, order) {
    const items = (Array.isArray(list) ? list : []).map(normalize).filter(Boolean);
    const byId = new Map(items.map((s) => [s.id, s]));
    const seq = Array.isArray(order) ? order : [];
    const out = [];
    for (const id of seq) {
      if (byId.has(id)) { out.push(byId.get(id)); byId.delete(id); }
    }
    return out;
  }

  function encode(list) {
    const items = (Array.isArray(list) ? list : []).map(normalize).filter(Boolean);
    return JSON.stringify({ schemaVersion: SCHEMA_VERSION, savedJourneys: items });
  }

  // Atomic, versioned decode. Returns one of:
  //   { ok: true, value: [SavedJourney...] }
  //   { unsupported: true, foundVersion }   (newer schema than this build)
  //   { corrupt: true, reason }             (unparseable / invalid root)
  function decode(raw) {
    if (raw == null || raw === '') return { ok: true, value: [] };
    let parsed;
    try { parsed = JSON.parse(raw); } catch (e) { return { corrupt: true, reason: String(e && e.message || e) }; }
    if (!parsed || typeof parsed !== 'object') return { corrupt: true, reason: 'root is not an object' };
    const version = Number(parsed.schemaVersion);
    if (Number.isFinite(version) && version > SCHEMA_VERSION) return { unsupported: true, foundVersion: version };
    const arr = Array.isArray(parsed.savedJourneys) ? parsed.savedJourneys : [];
    return { ok: true, value: arr.map(normalize).filter(Boolean) };
  }

  // localStorage-backed store. Every mutation persists the whole root atomically.
  // Decode failures never overwrite the blob: an unsupported/corrupt store is left
  // untouched and reported so the UI can offer recovery instead of losing trips.
  function createStore(storage) {
    const store = storage || (typeof localStorage !== 'undefined' ? localStorage : null);
    function readRaw() { try { return store ? store.getItem(STORAGE_KEY) : null; } catch (_) { return null; } }
    function writeList(list) {
      try { if (store) store.setItem(STORAGE_KEY, encode(list)); } catch (_) { /* quota / private mode */ }
      return list;
    }
    function load() {
      const res = decode(readRaw());
      return res.ok ? res.value : [];
    }
    return {
      STORAGE_KEY,
      status() { return decode(readRaw()); },
      list() { return load(); },
      save(entry) { return writeList(save(load(), entry)); },
      rename(id, label) { return writeList(rename(load(), id, label)); },
      remove(id) { return writeList(remove(load(), id)); },
      reorder(order) { return writeList(reorder(load(), order)); },
      replace(list) { return writeList((Array.isArray(list) ? list : []).map(normalize).filter(Boolean)); },
    };
  }

  // A stable-ish id when the caller has no better one. Time plus a short random
  // suffix; the de-dup invariant is by (fromId,toId), never by this id.
  function newId() {
    return 'sj-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 7);
  }

  return {
    SCHEMA_VERSION, STORAGE_KEY,
    normalize, save, rename, remove, reorder, encode, decode,
    createStore, newId, defaultPreferences,
  };
});
