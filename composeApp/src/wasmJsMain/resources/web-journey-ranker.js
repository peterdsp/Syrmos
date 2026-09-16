'use strict';
// Syrmos 3.0 itinerary ranking (web reference implementation).
//
// Mirrors Kotlin com.syrmos.core.domain.journey.JourneyRanker exactly:
//   - sort by the requested objective, then arrival, changes, walking, stable id
//   - dedup identical leg sequences (never show the same trip twice)
//   - keep at most three materially distinct options; never pad to three
//   - badge the top option only when the objective ranking is genuinely true
// Unknown metrics (null) sort last so a metric-less option can't look best.
//
// UMD: `require('./web-journey-ranker.js')` / `window.SyrmosJourneyRanker`.
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.SyrmosJourneyRanker = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  const MAX_OPTIONS = 3;

  function legSignature(option) {
    const legs = (option && Array.isArray(option.legs)) ? option.legs : [];
    return legs.map((l) => l.kind + ':' + (l.lineId || '') + ':' + l.fromId + ':' + l.toId).join('|');
  }

  // Comparator helper: nulls (and non-finite) sort last.
  function numNullsLast(a, b) {
    const x = (a == null || !Number.isFinite(a)) ? Infinity : a;
    const y = (b == null || !Number.isFinite(b)) ? Infinity : b;
    return x < y ? -1 : (x > y ? 1 : 0);
  }
  function arrivalMs(o) {
    const ms = Date.parse(o && o.arrivalInstant);
    return Number.isFinite(ms) ? ms : null;
  }

  // Comfortable-first ordering class for the RECOMMENDED default: comfortable
  // outranks tight outranks unknown, and a missed connection sorts last so it can
  // never lead the list (finding 7, product decision option 2). Mirrors Kotlin
  // JourneyRanker.feasibilityClass.
  function feasibilityClass(opt) {
    const s = opt && opt.feasibility && opt.feasibility.status;
    if (s === 'comfortable') return 0;
    if (s === 'tight') return 1;
    if (s === 'missed') return 3;
    return 2; // unknown / absent
  }

  function primaryCmp(ranking, a, b) {
    if (ranking === 'recommended') {
      return (feasibilityClass(a) - feasibilityClass(b)) || numNullsLast(a.durationSeconds, b.durationSeconds);
    }
    if (ranking === 'fewestChanges') return numNullsLast(a.transferCount, b.transferCount);
    if (ranking === 'leastWalking') return numNullsLast(a.walkingSeconds, b.walkingSeconds);
    return numNullsLast(a.durationSeconds, b.durationSeconds); // fastest
  }

  function comparator(ranking) {
    return function (a, b) {
      return primaryCmp(ranking, a, b)
        || numNullsLast(arrivalMs(a), arrivalMs(b))
        || numNullsLast(a.transferCount, b.transferCount)
        || numNullsLast(a.walkingSeconds, b.walkingSeconds)
        || String(a.id).localeCompare(String(b.id));
    };
  }

  function badgeFor(ranking, opt) {
    if (ranking === 'recommended') {
      const s = opt && opt.feasibility && opt.feasibility.status;
      return (s === 'missed') ? null : 'recommended';
    }
    if (ranking === 'fewestChanges') return 'fewestChanges';
    if (ranking === 'leastWalking') return (opt.walkingSeconds != null) ? 'leastWalking' : null;
    return (opt.durationSeconds != null) ? 'fastest' : null;
  }

  function rank(options, ranking) {
    const list = Array.isArray(options) ? options.slice() : [];
    list.sort(comparator(ranking));
    const seen = new Set();
    const distinct = list.filter((o) => {
      const sig = legSignature(o);
      if (seen.has(sig)) return false;
      seen.add(sig);
      return true;
    });
    return distinct.slice(0, MAX_OPTIONS).map((opt, index) => {
      const badge = index === 0 ? badgeFor(ranking, opt) : null;
      return Object.assign({}, opt, { rankingBadge: badge });
    });
  }

  return { rank, legSignature, MAX_OPTIONS };
});
