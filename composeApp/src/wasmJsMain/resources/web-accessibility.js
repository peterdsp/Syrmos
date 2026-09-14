'use strict';
// Syrmos 3.0 Phase R accessibility-unknown disclosure (web reference impl).
//
// Mirrors Kotlin com.syrmos.core.domain.journey.AccessibilityDisclosure exactly.
// Per-leg accessibility is verified | unavailable | unknown; absence of data is
// unknown, never verified. When the rider asks for step-free travel the option's
// disclosure takes the WORST leg state: any unavailable leg makes the route not
// step-free, otherwise any unknown leg means step-free is not confirmed,
// otherwise verified. When step-free was not requested the disclosure is inert.
//
// Covered case-for-case by fixtures/journeys/accessibility.json.
//
// UMD: `require('./web-accessibility.js')` in node, `window.SyrmosAccessibility`
// in the browser.
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.SyrmosAccessibility = factory();
})(typeof self !== 'undefined' ? self : this, function () {

  function forOption(option, preference) {
    if (preference !== 'stepFree') {
      return {
        confidence: 'verified',
        explanationCode: 'not_requested',
        unknownLegIds: [],
        unavailableLegIds: [],
      };
    }
    const legs = (option && option.legs) || [];
    const unknownLegIds = legs.filter(l => l.accessibility === 'unknown').map(l => l.id);
    const unavailableLegIds = legs.filter(l => l.accessibility === 'unavailable').map(l => l.id);
    let confidence;
    if (unavailableLegIds.length > 0) confidence = 'unavailable';
    else if (unknownLegIds.length > 0) confidence = 'unknown';
    else confidence = 'verified';
    const explanationCode = {
      unavailable: 'step_free_unavailable',
      unknown: 'step_free_unknown',
      verified: 'step_free_verified',
    }[confidence];
    return { confidence, explanationCode, unknownLegIds, unavailableLegIds };
  }

  return { forOption };
});
