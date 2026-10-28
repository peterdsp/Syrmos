'use strict';
// Syrmos 3.0.2 station closures (web reference).
//
// Mirrors the server closure_effect() (scripts/generate-302-fixtures.py), Kotlin
// StationClosures and Swift StationClosures exactly. A closure is active when its
// status is not draft/cancelled and startsAt <= now < endsAt (endsAt null = until
// further notice). Clients evaluate the window themselves, so an 08:00 closure
// flips at 08:00 even offline; the server `status` is informational only.
//
// Pure and offline: the caller passes the epoch-second clock. The window labels
// format in Europe/Athens wall-clock, never the viewer's device zone.
// Covered case-for-case by fixtures/closures/effect.json.
//
// UMD: `require('./web-closures.js')` in node, `window.SyrmosClosures` in browser.
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.SyrmosClosures = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  const MODE_ORDER = ['closed', 'pass_through', 'exit_only', 'entry_only'];
  const UPCOMING_WINDOW_SECONDS = 24 * 3600;

  function modeRank(mode) {
    const i = MODE_ORDER.indexOf(mode);
    return i < 0 ? MODE_ORDER.length : i;
  }

  function isLive(c) {
    return !!c && c.status !== 'draft' && c.status !== 'cancelled';
  }

  function isActive(c, now) {
    if (!isLive(c)) return false;
    if (now < c.startsAtEpochSeconds) return false;
    const ends = c.endsAtEpochSeconds;
    return ends == null || now < ends;
  }

  function applies(c, stationId, lineId) {
    if (!c || !Array.isArray(c.stationIds) || c.stationIds.indexOf(stationId) < 0) return false;
    const lines = c.lineIds || [];
    return !lines.length || lineId == null || lines.indexOf(lineId) >= 0;
  }

  function byIdAsc(a, b) { return a.id < b.id ? -1 : a.id > b.id ? 1 : 0; }

  // The per-(station, line) effect. Field names and values match the fixture.
  function effect(closures, stationId, lineId, now) {
    const list = Array.isArray(closures) ? closures : [];
    const active = list.filter((c) => applies(c, stationId, lineId) && isActive(c, now));
    if (!active.length) {
      const upcoming = list
        .filter((c) => applies(c, stationId, lineId) && isLive(c)
          && now < c.startsAtEpochSeconds && c.startsAtEpochSeconds <= now + UPCOMING_WINDOW_SECONDS)
        .sort((a, b) => (a.startsAtEpochSeconds - b.startsAtEpochSeconds) || byIdAsc(a, b));
      return {
        active: false, mode: null, canBoard: true, canAlight: true,
        closureId: null, endsAtEpochSeconds: null,
        upcomingClosureId: upcoming.length ? upcoming[0].id : null,
        upcomingStartsAtEpochSeconds: upcoming.length ? upcoming[0].startsAtEpochSeconds : null,
      };
    }
    const canBoard = active.every((c) => c.mode === 'entry_only');
    const canAlight = active.every((c) => c.mode === 'exit_only');
    let mode;
    if (!canBoard && !canAlight) {
      const modes = active.map((c) => c.mode);
      mode = MODE_ORDER.find((m) => modes.indexOf(m) >= 0) || 'closed';
      // exit_only + entry_only together leave nothing open.
      if (mode === 'exit_only' || mode === 'entry_only') mode = 'closed';
    } else {
      mode = active[0].mode;
    }
    const primary = active.slice().sort((a, b) => (modeRank(a.mode) - modeRank(b.mode)) || byIdAsc(a, b))[0];
    // Latest end wins; any open-ended closure means "until further notice".
    const ends = active.map((c) => c.endsAtEpochSeconds);
    const endsAt = ends.some((e) => e == null) ? null : Math.max.apply(null, ends);
    return {
      active: true, mode, canBoard, canAlight,
      closureId: primary.id, endsAtEpochSeconds: endsAt,
      upcomingClosureId: null, upcomingStartsAtEpochSeconds: null,
    };
  }

  // Station ids carry their line as the prefix ("M2_SYN" -> M2). Returns that
  // line when it is one of the node's lines, else null (= any line), so a merged
  // map node never applies an M2 closure to the tram stop beside it.
  function lineOfStationId(stationId, knownLineIds) {
    const prefix = String(stationId || '').split('_')[0];
    return (knownLineIds || []).indexOf(prefix) >= 0 ? prefix : null;
  }

  function pairsFor(stationIds, lineIds) {
    return (stationIds || []).map((sid) => ({ stationId: sid, lineId: lineOfStationId(sid, lineIds) }));
  }

  // Combined effect for a merged map node / station sheet. `pairs` is
  // [{stationId, lineId}]. blockedLineIds: lines whose departures the board must
  // hide (canBoard false). primary: the most restrictive active effect, for the
  // closure card. allBlocked: no pair can board. allClosed: no pair can board OR
  // alight (the map greys the whole station). upcoming: the earliest upcoming
  // effect when nothing at the node is active yet.
  function nodeEffect(closures, pairs, now) {
    const blocked = [];
    let primary = null;
    let upcoming = null;
    let boardable = 0;
    let open = 0;
    const list = pairs || [];
    for (const p of list) {
      const e = effect(closures, p.stationId, p.lineId, now);
      if (e.canBoard) boardable++;
      if (e.canBoard || e.canAlight) open++;
      if (e.active) {
        if (!e.canBoard && p.lineId && blocked.indexOf(p.lineId) < 0) blocked.push(p.lineId);
        if (!primary || modeRank(e.mode) < modeRank(primary.mode)
          || (modeRank(e.mode) === modeRank(primary.mode) && e.closureId < primary.closureId)) {
          primary = Object.assign({ stationId: p.stationId, lineId: p.lineId }, e);
        }
      } else if (e.upcomingClosureId && (!upcoming
        || e.upcomingStartsAtEpochSeconds < upcoming.upcomingStartsAtEpochSeconds)) {
        upcoming = Object.assign({ stationId: p.stationId, lineId: p.lineId }, e);
      }
    }
    const any = list.length > 0;
    return {
      primary,
      upcoming: primary ? null : upcoming,
      blockedLineIds: blocked,
      allBlocked: any && boardable === 0,
      allClosed: any && open === 0,
    };
  }

  // Board filter: drops departure groups for lines that cannot be boarded here.
  // M3_AIR rides the M3 track, so it follows the M3 closure.
  function filterDepartures(groups, blockedLineIds) {
    if (!blockedLineIds || !blockedLineIds.length) return groups || [];
    return (groups || []).filter((g) => {
      const raw = String(g.lineId || (g.line && g.line.id) || '');
      const id = raw === 'M3_AIR' ? 'M3' : raw;
      return blockedLineIds.indexOf(id) < 0;
    });
  }

  // Per-departure rule (matches the server projector and iOS
  // removingClosedBoardings): each time is checked at its own departure
  // minute, so at 07:50 a closure starting 08:00 already hides the 08:05 train
  // and a closure ending 10:00 still shows the 10:05 one. A line is boardable
  // at a moment when any of its station ids at this node can be boarded then.
  function boardableAt(closures, pairs, lineId, epoch) {
    const id = lineId === 'M3_AIR' ? 'M3' : lineId;
    const own = (pairs || []).filter((p) => p.lineId === id);
    if (!own.length) return true;
    return own.some((p) => effect(closures, p.stationId, p.lineId, epoch).canBoard);
  }

  function filterDeparturesAt(groups, closures, pairs, now) {
    if (!closures || !closures.length) return groups || [];
    const out = [];
    for (const g of groups || []) {
      const lineId = String(g.lineId || (g.line && g.line.id) || '');
      if (Array.isArray(g.times)) {
        const times = g.times.filter((t) =>
          boardableAt(closures, pairs, lineId, now + Math.max(0, Number(t.minutesAway) || 0) * 60));
        if (times.length) out.push(times.length === g.times.length ? g : Object.assign({}, g, { times }));
        continue;
      }
      const mins = Number.isFinite(g.minutesAway) ? g.minutesAway : 0;
      if (boardableAt(closures, pairs, lineId, now + Math.max(0, mins) * 60)) out.push(g);
    }
    return out;
  }

  // Planner rule: a ride leg may not board where canBoard is false nor alight
  // where canAlight is false. Passing through a closed station is fine
  // (pass_through trains keep running). Returns the offending stop ids.
  function optionViolations(option, closures, now) {
    const bad = [];
    for (const leg of (option && option.legs) || []) {
      if (leg.kind !== 'ride') continue;
      const lineId = leg.lineId == null ? null : (leg.lineId === 'M3_AIR' ? 'M3' : leg.lineId);
      if (!effect(closures, leg.fromId, lineId, now).canBoard && bad.indexOf(leg.fromId) < 0) bad.push(leg.fromId);
      if (!effect(closures, leg.toId, lineId, now).canAlight && bad.indexOf(leg.toId) < 0) bad.push(leg.toId);
    }
    return bad;
  }

  function filterOptions(options, closures, now) {
    const kept = [];
    const blockedStationIds = [];
    for (const o of options || []) {
      const bad = optionViolations(o, closures, now);
      if (!bad.length) kept.push(o);
      for (const id of bad) if (blockedStationIds.indexOf(id) < 0) blockedStationIds.push(id);
    }
    return { options: kept, removed: (options || []).length - kept.length, blockedStationIds };
  }

  // Localised text object {en, el, sq, it}: app language, then en, then el, then
  // any value. Accepts a plain string too.
  function pickText(obj, lang) {
    if (obj == null) return '';
    if (typeof obj === 'string') return obj;
    for (const k of [lang, 'en', 'el']) {
      if (k && typeof obj[k] === 'string' && obj[k].trim()) return obj[k];
    }
    for (const k of Object.keys(obj)) if (typeof obj[k] === 'string' && obj[k].trim()) return obj[k];
    return '';
  }

  const LOCALE = { en: 'en-GB', el: 'el-GR', sq: 'sq-AL', it: 'it-IT' };

  function athensParts(epochSeconds) {
    const parts = new Intl.DateTimeFormat('en-GB', {
      timeZone: 'Europe/Athens', year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', hour12: false,
    }).formatToParts(new Date(epochSeconds * 1000));
    const v = {};
    for (const p of parts) v[p.type] = p.value;
    return { day: v.year + '-' + v.month + '-' + v.day, clock: (v.hour === '24' ? '00' : v.hour) + ':' + v.minute };
  }

  // "08:00" when the instant falls on the same Athens day as `now`, else the
  // short date plus clock ("28 Oct 08:00"), always in Europe/Athens.
  function athensLabel(epochSeconds, now, lang) {
    const at = athensParts(epochSeconds);
    if (at.day === athensParts(now).day) return at.clock;
    let date;
    try {
      date = new Intl.DateTimeFormat(LOCALE[lang] || 'en-GB', {
        timeZone: 'Europe/Athens', day: 'numeric', month: 'short',
      }).format(new Date(epochSeconds * 1000));
    } catch (_) {
      date = at.day;
    }
    return date + ' ' + at.clock;
  }

  // Window copy. `labels` supplies the localised templates:
  //   { range: '{from} to {to}', untilFurther: 'until further notice',
  //     fromUntilFurther: '{from}, until further notice' }
  function windowText(startsAt, endsAt, now, lang, labels) {
    const L = labels || {};
    const from = athensLabel(startsAt, now, lang);
    if (endsAt == null) {
      return startsAt <= now
        ? (L.untilFurther || 'until further notice')
        : (L.fromUntilFurther || '{from}, until further notice').split('{from}').join(from);
    }
    return (L.range || '{from} to {to}').split('{from}').join(from).split('{to}').join(athensLabel(endsAt, now, lang));
  }

  function findClosure(closures, id) {
    return (closures || []).find((c) => c && c.id === id) || null;
  }

  // Normalise an /api/closures (or bundled closures.json) payload into the
  // closures array; anything malformed is dropped rather than trusted.
  function parsePayload(payload) {
    const list = payload && Array.isArray(payload.closures) ? payload.closures : [];
    return list.filter((c) => c && typeof c.id === 'string' && Array.isArray(c.stationIds)
      && Number.isFinite(c.startsAtEpochSeconds)
      && (c.endsAtEpochSeconds == null || Number.isFinite(c.endsAtEpochSeconds))
      && MODE_ORDER.indexOf(c.mode) >= 0);
  }

  return {
    MODE_ORDER, UPCOMING_WINDOW_SECONDS,
    isActive, applies, effect, lineOfStationId, pairsFor, nodeEffect,
    filterDepartures, filterDeparturesAt, boardableAt, optionViolations, filterOptions,
    pickText, athensLabel, windowText, findClosure, parsePayload,
  };
});
