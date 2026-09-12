'use strict';
// Syrmos 3.0 connection feasibility (web reference implementation).
//
// Mirrors Kotlin com.syrmos.core.domain.journey.FeasibilityCalculator exactly.
// Transparent evidence only: a real margin in seconds and one of four honest
// states (comfortable | tight | missed | unknown), never a success percentage.
//
//   margin = nextDeparture - previousArrival - transferMinimum - uncertaintyAllowance   (seconds)
//   margin < 0            -> missed
//   0 .. tightMaxSeconds  -> tight
//   > tightMaxSeconds     -> comfortable
//
// The journey takes the WORST valid transfer state; a required unknown term
// (previous arrival / next departure) makes the whole option unknown; a closure
// overrides everything to missed. A direct trip shows schedule status, not a
// transfer calculation.
//
// UMD: `require('./web-feasibility.js')` in node, `window.SyrmosFeasibility` in
// the browser.
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.SyrmosFeasibility = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  const DEFAULT_POLICY = { tightMaxSeconds: 179, defaultTransferBufferSeconds: 120 };

  function epochSeconds(instant) {
    if (instant == null) return null;
    const ms = Date.parse(instant);
    return Number.isFinite(ms) ? Math.floor(ms / 1000) : null;
  }

  // Margin in seconds, or null when a required term (arrival/departure) is unknown.
  function transferMarginSeconds(previousArrival, nextDeparture, transferMinimumSeconds, uncertaintySeconds, policy) {
    const p = policy || DEFAULT_POLICY;
    const prev = epochSeconds(previousArrival);
    const next = epochSeconds(nextDeparture);
    if (prev == null || next == null) return null;
    const minimum = (transferMinimumSeconds == null) ? p.defaultTransferBufferSeconds : transferMinimumSeconds;
    const uncertainty = (uncertaintySeconds == null) ? 0 : uncertaintySeconds;
    return (next - prev) - minimum - uncertainty;
  }

  function statusFor(margin, policy) {
    if (margin < 0) return 'missed';
    if (margin <= policy.tightMaxSeconds) return 'tight';
    return 'comfortable';
  }

  const SEVERITY = { missed: 3, unknown: 2, tight: 1, comfortable: 0 };

  function connectorBetween(legs, fromIdx, toIdx) {
    for (let j = fromIdx + 1; j < toIdx; j++) {
      const k = legs[j] && legs[j].kind;
      if (k === 'transfer' || k === 'walk') return legs[j];
    }
    return null;
  }

  // option: { legs: [{ id, kind, arrivalInstant, departureInstant, transferMinimumSeconds, uncertaintySeconds }] }
  function forOption(option, policy, closedLegIds) {
    const p = policy || DEFAULT_POLICY;
    const legs = (option && Array.isArray(option.legs)) ? option.legs : [];
    const closedSet = new Set(closedLegIds || []);

    const closed = legs.find((l) => l && closedSet.has(l.id));
    if (closed) return { status: 'missed', minimumMarginSeconds: null, explanationCode: 'segment_closed', limitingLegId: closed.id };

    const rideIdx = [];
    legs.forEach((l, i) => { if (l && l.kind === 'ride') rideIdx.push(i); });
    if (rideIdx.length <= 1) {
      return { status: 'comfortable', minimumMarginSeconds: null, explanationCode: 'direct', limitingLegId: null };
    }

    let worst = 'comfortable';
    let worstMargin = null;
    let worstLegId = null;

    for (let i = 0; i < rideIdx.length - 1; i++) {
      const prev = legs[rideIdx[i]];
      const next = legs[rideIdx[i + 1]];
      const connector = connectorBetween(legs, rideIdx[i], rideIdx[i + 1]);
      const margin = transferMarginSeconds(
        prev.arrivalInstant, next.departureInstant,
        connector ? connector.transferMinimumSeconds : null,
        prev.uncertaintySeconds, p,
      );
      const status = (margin == null) ? 'unknown' : statusFor(margin, p);
      if (SEVERITY[status] >= SEVERITY[worst]) {
        worst = status;
        worstMargin = margin;
        worstLegId = connector ? connector.id : next.id;
      }
    }

    const code = {
      missed: 'transfer_missed', tight: 'transfer_tight',
      unknown: 'transfer_unknown', comfortable: 'transfer_comfortable',
    }[worst];
    return {
      status: worst,
      minimumMarginSeconds: worst === 'unknown' ? null : worstMargin,
      explanationCode: code,
      limitingLegId: worstLegId,
    };
  }

  return { transferMarginSeconds, forOption, DEFAULT_POLICY };
});
