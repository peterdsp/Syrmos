'use strict';
// Syrmos 3.0 live GO session (web reference implementation, prompt S06).
//
// The single, locally-owned live journey. No account, no server: one versioned
// ActiveJourney is persisted in localStorage under a single key so a trip in
// progress survives a reload / navigation and can be resumed where it left off.
// Mirrors the Kotlin com.syrmos.core.model.journey.ActiveJourney contract and the
// shared, tested com.syrmos.core.domain.journey.ActiveJourneyStore lifecycle, so
// the on-disk shape and every transition are byte- and behaviour-parity across
// web, Android and iOS.
//
// The session is anchored by ids (legId + confirmedStopId), never indices, so a
// resumed trip lands on the exact same stop even after names were re-resolved in
// another language. The itinerary snapshot is FROZEN for the life of the session.
//
// The pure lifecycle (start/advance/back/end/positionOf/phaseFor) is testable in
// node; the store wrapper binds it to localStorage. Needs the GO engine (web-go.js).
//
// UMD: `require('./web-active-journey.js')` / `window.SyrmosActiveJourney`.
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory(root);
  else root.SyrmosActiveJourney = factory(root);
})(typeof self !== 'undefined' ? self : this, function (root) {
  const GO = (typeof require === 'function') ? require('./web-go.js') : root.SyrmosGO;
  const SCHEMA_VERSION = 1;
  const STORAGE_KEY = 'syrmos.active-journey.v1';

  const PHASE = {
    readyToBoard: 'readyToBoard', riding: 'riding', alightSoon: 'alightSoon',
    transfer: 'transfer', waitingForNextLeg: 'waitingForNextLeg',
    locationUncertain: 'locationUncertain', arrived: 'arrived', ended: 'ended',
  };

  function rideLegs(option) {
    return ((option && option.legs) || []).filter((l) => l.kind === 'ride');
  }
  function stopIdAt(journey, pos) {
    const leg = journey.legs[pos.legIndex];
    const stop = leg && leg.stops[pos.stopIndex];
    return stop ? stop.id : null;
  }

  // Map the engine instruction at `pos` to the persisted JourneyPhase.
  function phaseFor(journey, pos) {
    switch (GO.guidance(journey, pos).kind) {
      case 'board': return PHASE.readyToBoard;
      case 'ride': return PHASE.riding;
      case 'getOffNext': return PHASE.alightSoon;
      case 'transfer': return PHASE.transfer;
      case 'arrived': return PHASE.arrived;
      default: return PHASE.riding;
    }
  }

  // Begin a session at the origin (leg 0, board stop).
  function start(id, option, journey, startedAt) {
    const pos = { legIndex: 0, stopIndex: 0 };
    const rides = rideLegs(option);
    const legId = (rides[0] && rides[0].id)
      || (option.legs && option.legs[0] && option.legs[0].id)
      || id;
    return {
      id,
      revision: 0,
      itinerarySnapshot: option,
      phase: phaseFor(journey, pos),
      legId,
      startedAt,
      updatedAt: startedAt,
      confirmedStopId: stopIdAt(journey, pos),
      progressSource: 'manual',
      progressObservedAt: startedAt,
      alertedEventIds: [],
      alertPreferences: { leaveByReminder: false, getOffAlert: false, disruptionAlert: false },
      schemaVersion: SCHEMA_VERSION,
    };
  }

  // The engine position for a persisted session, mapping ids back to indices. An
  // unknown legId/confirmedStopId resolves to the origin of its leg, never throws.
  function positionOf(active, journey) {
    if (!journey || !journey.legs || !journey.legs.length) return { legIndex: 0, stopIndex: 0 };
    const rides = rideLegs(active.itinerarySnapshot);
    let legIndex = rides.findIndex((l) => l.id === active.legId);
    if (legIndex < 0 || legIndex >= journey.legs.length) legIndex = 0;
    const stops = journey.legs[legIndex].stops || [];
    let stopIndex = active.confirmedStopId != null
      ? stops.findIndex((s) => s.id === active.confirmedStopId) : 0;
    if (stopIndex < 0) stopIndex = 0;
    if (stopIndex > stops.length - 1) stopIndex = Math.max(0, stops.length - 1);
    return { legIndex, stopIndex };
  }

  // Rewrite the session onto `pos`, refreshing legId/confirmedStopId/phase/clock.
  function withPosition(active, journey, pos, now, source) {
    const rides = rideLegs(active.itinerarySnapshot);
    const legId = (rides[pos.legIndex] && rides[pos.legIndex].id) || active.legId;
    return Object.assign({}, active, {
      phase: phaseFor(journey, pos),
      legId,
      confirmedStopId: stopIdAt(journey, pos),
      progressSource: source || 'manual',
      progressObservedAt: now,
      updatedAt: now,
    });
  }

  function advance(active, journey, now, source) {
    return withPosition(active, journey, GO.advance(journey, positionOf(active, journey)), now, source);
  }

  function back(active, journey, now) {
    const cur = positionOf(active, journey);
    let prev = cur;
    if (cur.stopIndex > 0) prev = { legIndex: cur.legIndex, stopIndex: cur.stopIndex - 1 };
    else if (cur.legIndex > 0) {
      const p = cur.legIndex - 1;
      prev = { legIndex: p, stopIndex: Math.max(0, journey.legs[p].stops.length - 1) };
    }
    return withPosition(active, journey, prev, now, 'manual');
  }

  function end(active, now) {
    return Object.assign({}, active, { phase: PHASE.ended, updatedAt: now });
  }
  function isEnded(active) { return !!active && active.phase === PHASE.ended; }
  function isResumable(active) { return !!active && active.phase !== PHASE.ended; }

  // A session is well-formed enough to resume when it has a frozen snapshot with at
  // least one ride leg. Missing that, it is treated as no session (never invented).
  function normalize(active) {
    if (!active || typeof active !== 'object') return null;
    if (!active.id || !active.itinerarySnapshot) return null;
    if (!rideLegs(active.itinerarySnapshot).length) return null;
    return active;
  }

  function encode(active) {
    return JSON.stringify(Object.assign({ schemaVersion: SCHEMA_VERSION }, active));
  }

  // Atomic, versioned decode. Returns one of:
  //   { ok: true, value: ActiveJourney|null }
  //   { unsupported: true, foundVersion }   (newer schema than this build)
  //   { corrupt: true, reason }
  function decode(raw) {
    if (raw == null || raw === '') return { ok: true, value: null };
    let parsed;
    try { parsed = JSON.parse(raw); } catch (e) { return { corrupt: true, reason: String((e && e.message) || e) }; }
    if (!parsed || typeof parsed !== 'object') return { corrupt: true, reason: 'root is not an object' };
    const version = Number(parsed.schemaVersion);
    if (Number.isFinite(version) && version > SCHEMA_VERSION) return { unsupported: true, foundVersion: version };
    return { ok: true, value: normalize(parsed) };
  }

  // localStorage-backed store for the single live session. A decode failure or an
  // ENDED session reads back as no live session; the raw blob is never overwritten
  // on a failed decode, so a future-schema store is preserved.
  function createStore(storage) {
    const store = storage || (typeof localStorage !== 'undefined' ? localStorage : null);
    function readRaw() { try { return store ? store.getItem(STORAGE_KEY) : null; } catch (_) { return null; } }
    function write(active) {
      try { if (store) store.setItem(STORAGE_KEY, active ? encode(active) : ''); } catch (_) { /* quota / private mode */ }
      return active;
    }
    return {
      STORAGE_KEY,
      status() { return decode(readRaw()); },
      // The live, resumable session or null (an ENDED / absent session is null).
      get() {
        const res = decode(readRaw());
        const v = res.ok ? res.value : null;
        return v && isResumable(v) ? v : null;
      },
      set(active) { return write(active); },
      clear() { return write(null); },
    };
  }

  function newId() {
    return 'go-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 7);
  }

  return {
    SCHEMA_VERSION, STORAGE_KEY, PHASE,
    start, positionOf, withPosition, advance, back, end,
    isEnded, isResumable, phaseFor, normalize, encode, decode,
    createStore, newId,
  };
});
