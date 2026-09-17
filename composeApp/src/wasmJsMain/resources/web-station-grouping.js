'use strict';
// Syrmos 3.0 station grouping (web reference, finding 2). Mirrors the iOS
// `StationGrouping` and Kotlin `com.syrmos.core.domain.station.StationGrouping`:
// collapse co-located stops that share a station identity (same folded name AND
// the same location) into one presentation group carrying every member stop id
// and the union of serving lines. So the plan From/To <select> never lists the
// same physical station twice (the tram `A1_KIF`/`A2_KIF`, both "Kifisias" at one
// point), while genuinely distinct stations stay separate ("Kifissia" vs
// "Kifisias"). Identity is name AND place, never one alone.
//
// UMD: `require('./web-station-grouping.js')` / `window.SyrmosStationGrouping`.
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.SyrmosStationGrouping = factory();
})(typeof self !== 'undefined' ? self : this, function () {

  // Fold case and accents but keep the letters, so accented and unaccented
  // spellings match while genuinely different spellings never collapse together.
  // NFD + combining-mark strip handles Greek tonos (ά -> α) and Latin diacritics.
  function fold(s) {
    return String(s == null ? '' : s).trim().toLowerCase()
      .normalize('NFD').replace(/[̀-ͯ]/g, '');
  }

  function lat(s) { return Number(s.latitude != null ? s.latitude : s.lat); }
  function lon(s) { return Number(s.longitude != null ? s.longitude : s.lng); }
  function nameOf(s) { return s.name || s.name_el || s.nameEl || s.id || ''; }
  function nameElOf(s) { return s.name_el || s.nameEl || ''; }
  function nameSqOf(s) { return s.name_sq || s.nameSq || ''; }
  function linesOf(s) { return s.line_ids || s.lineIds || []; }

  // ~11m coordinate bucket. Only near-identical points merge, so co-location is
  // required in addition to a name match: proximity is evidence, not identity.
  function bucket(a, b) {
    const r = (x) => (Number.isFinite(x) ? Math.round(x * 10000) / 10000 : 'x');
    return r(a) + ',' + r(b);
  }

  // groups(stations) -> [{ id, name, nameEl, nameSq, memberIds, lineIds }]
  // id/representativeId is the smallest member id, stable across input order.
  function groups(stations) {
    const buckets = new Map();
    const order = [];
    for (const s of (Array.isArray(stations) ? stations : [])) {
      const key = fold(nameOf(s)) + '|' + bucket(lat(s), lon(s));
      if (!buckets.has(key)) { buckets.set(key, []); order.push(key); }
      buckets.get(key).push(s);
    }
    return order.map((key) => {
      const members = buckets.get(key).slice().sort((a, b) => String(a.id).localeCompare(String(b.id)));
      const rep = members[0];
      const lineIds = [];
      for (const m of members) for (const l of linesOf(m)) if (!lineIds.includes(l)) lineIds.push(l);
      return {
        id: rep.id,
        representativeId: rep.id,
        name: nameOf(rep),
        nameEl: nameElOf(rep),
        nameSq: nameSqOf(rep),
        memberIds: members.map((m) => m.id),
        lineIds: lineIds,
      };
    });
  }

  // Whether a group matches a folded query on any of its member names.
  function matches(group, foldedQuery) {
    if (!foldedQuery) return true;
    return fold(group.name).includes(foldedQuery) ||
      fold(group.nameEl).includes(foldedQuery) ||
      fold(group.nameSq).includes(foldedQuery);
  }

  return { fold, groups, matches };
});
