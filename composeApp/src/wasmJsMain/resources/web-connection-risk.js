'use strict';
// Syrmos 3.0 connection risk (web reference, prompt S07 / Phase R).
//
// Pure transform: a JourneyOption -> a per-transfer risk list the S07 warning
// renders. For each transfer it exposes the ACTUAL numbers the rider sees:
//   availableSeconds  = real gap (nextDeparture - previousArrival)
//   recommendedSeconds = change time to allow (the transfer leg's minimum, or the
//                        policy default of 120s when it carries none)
//   status = reuses the feasibility policy EXACTLY:
//     margin = available - recommended - uncertainty
//     margin < 0 -> 'missed', 0..179 -> 'tight', > 179 -> 'comfortable',
//     a missing required instant -> 'unknown' (availableSeconds null).
// The whole option's worst upcoming transfer drives the inline GO warning. Mirrors
// Kotlin ConnectionRisk and iOS ConnectionRisk against fixtures/journeys/connection-risk.json.
//
// UMD: `require('./web-connection-risk.js')` / `window.SyrmosConnectionRisk`.
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.SyrmosConnectionRisk = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  const TIGHT_MAX_SECONDS = 179;
  const DEFAULT_RECOMMENDED_SECONDS = 120;

  function ms(iso) {
    if (!iso) return null;
    const t = Date.parse(iso);
    return Number.isFinite(t) ? t : null;
  }

  function statusFor(marginSeconds) {
    if (marginSeconds < 0) return 'missed';
    if (marginSeconds <= TIGHT_MAX_SECONDS) return 'tight';
    return 'comfortable';
  }

  const SEVERITY = { missed: 3, unknown: 2, tight: 1, comfortable: 0 };

  // The transfer/walk leg between two ride legs, if any (for its minimum).
  function connectorBetween(legs, fromIdx, toIdx) {
    for (let j = fromIdx + 1; j < toIdx; j++) {
      if (legs[j].kind === 'transfer' || legs[j].kind === 'walk') return legs[j];
    }
    return null;
  }

  /** Per-transfer risk rows for an option. Empty for a direct (single-ride) trip. */
  function risks(option) {
    const legs = (option && Array.isArray(option.legs)) ? option.legs : [];
    const rideIdx = [];
    legs.forEach((l, i) => { if (l.kind === 'ride') rideIdx.push(i); });
    const out = [];
    for (let k = 0; k < rideIdx.length - 1; k++) {
      const prev = legs[rideIdx[k]];
      const next = legs[rideIdx[k + 1]];
      const connector = connectorBetween(legs, rideIdx[k], rideIdx[k + 1]);
      const recommended = (connector && connector.transferMinimumSeconds != null)
        ? connector.transferMinimumSeconds : DEFAULT_RECOMMENDED_SECONDS;
      const uncertainty = (prev.uncertaintySeconds != null) ? prev.uncertaintySeconds : 0;
      const a = ms(prev.arrivalInstant), d = ms(next.departureInstant);
      let available = null, status = 'unknown';
      if (a != null && d != null) {
        available = Math.round((d - a) / 1000);
        status = statusFor(available - recommended - uncertainty);
      }
      out.push({
        transferIndex: k,
        fromLegId: prev.id, toLegId: next.id,
        atStationId: prev.toId, toLineId: next.lineId || null,
        status, availableSeconds: available, recommendedSeconds: recommended,
      });
    }
    return out;
  }

  /** The worst status across all transfers; 'comfortable' when there are none. */
  function worstStatus(option) {
    const rs = risks(option);
    let worst = 'comfortable';
    for (const r of rs) if (SEVERITY[r.status] >= SEVERITY[worst]) worst = r.status;
    return worst;
  }

  /**
   * The nearest transfer risk at or after ride leg `fromRideIndex` that needs the
   * rider's attention (tight / missed / unknown), for the inline GO warning; null
   * when every upcoming connection is comfortable.
   */
  function nextConcern(option, fromRideIndex) {
    const rs = risks(option);
    const from = Number.isFinite(fromRideIndex) ? fromRideIndex : 0;
    for (const r of rs) {
      if (r.transferIndex >= from && r.status !== 'comfortable') return r;
    }
    return null;
  }

  return { risks, worstStatus, nextConcern, TIGHT_MAX_SECONDS, DEFAULT_RECOMMENDED_SECONDS };
});
