'use strict';
// Syrmos 3.0 selected-journey detail timeline (web reference, prompt S05).
//
// Pure transform: a JourneyOption's legs -> an ordered list of timeline rows the
// S05 detail view renders. Mirrors Kotlin JourneyDetail.timeline and iOS
// JourneyDetail.timeline exactly, against the shared golden fixture
// fixtures/journeys/detail.json. Language-neutral: rows carry ids/instants/counts,
// never localized strings (the view localizes). Honest timing: a null clock stays
// null (unknown), never a fabricated 0.
//
// Row kinds:
//   board    { legId, stationId, lineId, towardsId, clock, timingKind, node }
//   stops    { legId, lineId, count }                 (intermediate stops, hidden by default)
//   alight   { legId, stationId, clock, timingKind, node }
//   transfer { legId, fromId, toId, seconds, node }   (dashed segment between rides)
//   walk     { legId, fromId, toId, seconds, node }
// node role: 'origin' (first board), 'destination' (last alight), 'interchange'
// (a board/alight next to a transfer/walk).
//
// UMD: `require('./web-journey-detail.js')` / `window.SyrmosJourneyDetail`.
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.SyrmosJourneyDetail = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  function isRide(leg) { return leg && leg.kind === 'ride'; }

  // Seconds between two ISO instants, or null if either is missing/invalid.
  function gapSeconds(fromIso, toIso) {
    if (!fromIso || !toIso) return null;
    const a = Date.parse(fromIso), b = Date.parse(toIso);
    if (!Number.isFinite(a) || !Number.isFinite(b)) return null;
    return Math.round((b - a) / 1000);
  }

  /**
   * @param {{legs:Array}} option
   * @returns {Array} ordered timeline rows
   */
  function timeline(option) {
    const legs = (option && Array.isArray(option.legs)) ? option.legs : [];
    const rows = [];
    const lastIndex = legs.length - 1;

    legs.forEach((leg, i) => {
      if (isRide(leg)) {
        const stops = Array.isArray(leg.orderedStopIds) ? leg.orderedStopIds : [];
        const towardsId = stops.length ? stops[stops.length - 1] : leg.toId;
        rows.push({
          kind: 'board', legId: leg.id, stationId: leg.fromId, lineId: leg.lineId || null,
          towardsId: towardsId || null,
          clock: leg.departureInstant || null, timingKind: leg.timingKind || 'unknown',
          node: i === 0 ? 'origin' : 'interchange',
        });
        const count = Math.max(0, stops.length - 2);
        if (count > 0) rows.push({ kind: 'stops', legId: leg.id, lineId: leg.lineId || null, count });
        rows.push({
          kind: 'alight', legId: leg.id, stationId: leg.toId,
          clock: leg.arrivalInstant || null, timingKind: leg.timingKind || 'unknown',
          node: i === lastIndex ? 'destination' : 'interchange',
        });
      } else {
        // transfer / walk between rides: dashed segment with an explicit duration.
        const prev = legs[i - 1];
        const next = legs[i + 1];
        let seconds = (leg.transferMinimumSeconds != null) ? leg.transferMinimumSeconds : null;
        if (seconds == null && prev && next) {
          seconds = gapSeconds(prev.arrivalInstant, next.departureInstant);
        }
        rows.push({
          kind: leg.kind === 'walk' ? 'walk' : 'transfer', legId: leg.id,
          fromId: leg.fromId, toId: leg.toId, seconds, node: 'interchange',
        });
      }
    });
    return rows;
  }

  return { timeline };
});
