'use strict';
// Syrmos 3.0 Phase R disruption exclusions (web reference implementation).
//
// Mirrors Kotlin com.syrmos.core.domain.journey.DisruptionExclusion exactly.
// A CLOSURE-severity service notice suspends its affected line ids; the planner
// must route AROUND suspended track, and when the only path rides suspended track
// it must name the affected line and its notice (S10 "Suspended segment") instead
// of handing the rider a plan they cannot travel. Never fabricate a replacement.
//
// Covered case-for-case by fixtures/journeys/disruption.json (the Kotlin test
// asserts the SAME cases, so web and Kotlin agree by construction).
//
// UMD: `require('./web-disruption.js')` in node, `window.SyrmosDisruption` in
// the browser.
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.SyrmosDisruption = factory();
})(typeof self !== 'undefined' ? self : this, function () {

  // trim + lowercase, strip the "line"/"metro" wording. Notice affectedLineIds
  // and line ids are already canonical tokens like "M3"/"T6"; this only absorbs
  // casing and the odd "Line 3" phrasing. Mirror of Kotlin normalizeLine.
  function normalizeLine(id) {
    return String(id == null ? '' : id)
      .trim().toLowerCase()
      .split('line').join('')
      .split('metro').join('')
      .split(' ').join('')
      .trim();
  }

  // Mirror Kotlin AdvisorySeverity.fromRaw: both "closure" and "closed" are a
  // closure. Comparing the raw string against only "closure" would let a
  // "closed" feed value slip through and route a rider through a shut line.
  function isClosure(notice) {
    const s = String(notice && notice.severity || '').trim().toLowerCase();
    return s === 'closure' || s === 'closed';
  }

  function unique(list) {
    const seen = new Set();
    const out = [];
    for (const v of list) { if (!seen.has(v)) { seen.add(v); out.push(v); } }
    return out;
  }

  // Set<String> of suspended line ids, from CLOSURE notices only.
  function suspendedLineIds(notices) {
    const out = [];
    for (const n of (notices || [])) {
      if (!isClosure(n)) continue;
      for (const l of (n.affectedLineIds || [])) {
        const norm = normalizeLine(l);
        if (norm) out.push(norm);
      }
    }
    return unique(out);
  }

  // The suspended lines an option's ride legs actually travel on.
  function optionUsesSuspended(option, suspended) {
    const susp = new Set(suspended || []);
    if (susp.size === 0) return [];
    const out = [];
    for (const leg of ((option && option.legs) || [])) {
      if (leg.kind !== 'ride') continue;
      if (leg.lineId == null) continue;
      const norm = normalizeLine(leg.lineId);
      if (susp.has(norm)) out.push(norm);
    }
    return unique(out);
  }

  // Classify a disruption-aware plan into routed | suspended | noRoute.
  function classify(avoidingOptions, naiveOptions, notices) {
    const suspended = suspendedLineIds(notices);
    if ((avoidingOptions || []).length > 0) {
      // Only disclose lines the naive (unrestricted) plan actually rode, so an
      // unrelated closure does not show a false "routing around" chip.
      const naive0 = (naiveOptions || [])[0];
      const excluded = (suspended.length === 0 || !naive0) ? [] : optionUsesSuspended(naive0, suspended);
      return { kind: 'routed', excludedLineIds: excluded };
    }
    const naive = (naiveOptions || [])[0];
    if (naive) {
      const hit = optionUsesSuspended(naive, suspended);
      if (hit.length > 0) {
        const hitSet = new Set(hit);
        const relevant = (notices || []).filter(n =>
          isClosure(n) && (n.affectedLineIds || []).some(l => hitSet.has(normalizeLine(l))));
        return {
          kind: 'suspended',
          affectedLineIds: hit,
          noticeIds: relevant.map(n => n.id),
          notices: relevant,
        };
      }
    }
    return { kind: 'noRoute' };
  }

  return { normalizeLine, suspendedLineIds, optionUsesSuspended, classify };
});
