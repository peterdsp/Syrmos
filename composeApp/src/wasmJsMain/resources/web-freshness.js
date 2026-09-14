'use strict';
// Syrmos 3.0 Phase N J07 unified freshness/offline presentation (web reference).
//
// Mirrors Kotlin com.syrmos.core.common.FreshnessPresentation exactly. One rule
// derives the banner state from connectivity + liveness so no surface hand-rolls a
// connectivity-only check: "online but the API is unreachable/stale" is a real
// predicted state a bare navigator.onLine gate would miss. Offline always wins.
//
// Covered case-for-case by fixtures/freshness/presentation.json.
//
// UMD: `require('./web-freshness.js')` in node, `window.SyrmosFreshness` in browser.
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.SyrmosFreshness = factory();
})(typeof self !== 'undefined' ? self : this, function () {

  function evaluate(isNetworkAvailable, isLive) {
    if (!isNetworkAvailable) return 'offline';
    return isLive ? 'live' : 'predicted';
  }

  function showsBanner(state) { return state !== 'live'; }

  return { evaluate, showsBanner };
});
