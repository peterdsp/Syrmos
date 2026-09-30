'use strict';
// Syrmos station-complex departure board (web reference implementation).
//
// Answers one question for a whole station complex: "what leaves from here, in
// EVERY supported direction?" It is the pure core shared, semantically, with the
// Swift `StationComplexBoard` and the Kotlin `StationComplexBoard`. No DOM, no
// clock, no network: the caller resolves the complex, collects candidate
// departures per member boarding stop and passes an explicit `now`.
//
// Why this module exists. The previous web path picked `deps[0]` for the hero
// and printed `deps.slice(1, 3)` as an unlabelled "then" tail, so a second
// direction leaving in one minute was invisible, and `buildStationDepartures`
// returned the published suburban timetable EXCLUSIVELY whenever it had rows,
// which suppressed both metro directions of the same complex. Both defects are
// structural: they happen before grouping. This module therefore enumerates
// groups first and limits presentation last.
//
// Contract summary
// ----------------
// A *complex* is a reviewed collection of real boarding stops (`areas[].stopIds`)
// that together form one station a rider walks into. Boarding membership is
// route membership: a line belongs to a stop only when the line's own station
// list contains that stop id. Interchange unions in the station seed are
// transfer information, never boarding membership.
//
// A *group* is one row of the board: one destination reachable from one boarding
// area on one service pattern. Its id is stable across refreshes so a row that
// reorders keeps its identity, its open detail and its screen-reader focus.
//
// UMD: `require('./web-station-board.js')` in node, `window.SyrmosStationBoard`
// in the browser.
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.SyrmosStationBoard = factory();
})(typeof self !== 'undefined' ? self : this, function () {

  // ---------------------------------------------------------------- folding

  // Fold case and accents but keep the letters, so "Elliniko", "elliniko " and
  // "Ελληνικό"/"Ελληνικο" fold consistently while genuinely different
  // destinations never collapse. Mirrors SyrmosStationGrouping.fold.
  function fold(s) {
    return String(s == null ? '' : s).trim().toLowerCase()
      .normalize('NFD').replace(/[̀-ͯ]/g, '')
      .replace(/\s+/g, ' ');
  }

  // --------------------------------------------------------------- coverage

  // Coverage of one (boarding stop, service) pair. `partial` stays visible to
  // the rider: a board that could not load one of its services must never look
  // complete.
  const COVERAGE = {
    LOADED: 'loaded',
    LOADING: 'loading',
    UNAVAILABLE: 'unavailable',
    NO_DEPARTURE: 'no_departure_in_window',
    NOT_OPERATING: 'not_operating',
  };

  // Confidence ranking, highest wins when two records describe one departure.
  const SOURCE_RANK = { live: 4, scheduled: 3, estimated: 2, offline: 1, operator: 1, unknown: 0 };
  function sourceRank(s) {
    const r = SOURCE_RANK[String(s || 'unknown').toLowerCase()];
    return Number.isFinite(r) ? r : 0;
  }

  // ------------------------------------------------------- complex resolving

  // Build the boarding-service list of a complex from authoritative route
  // membership. `lines` is the schedules-v2 `lines[]` array: a line's own
  // `stations[]` is the only evidence that a service actually boards at a stop.
  //
  //   resolveComplexServices(complex, lines) ->
  //     [{ areaId, areaName..., stopId, lineId, line }]
  //
  // Order is area order, then the complex's own stop order, then line id, so the
  // result is deterministic regardless of the input order of `lines`.
  function resolveComplexServices(complex, lines) {
    const byStop = new Map();
    for (const line of (Array.isArray(lines) ? lines : [])) {
      for (const st of (line.stations || [])) {
        if (!st || !st.id) continue;
        if (!byStop.has(st.id)) byStop.set(st.id, []);
        byStop.get(st.id).push(line);
      }
    }
    const out = [];
    for (const area of (complex && complex.areas) || []) {
      for (const stopId of (area.stopIds || [])) {
        const served = (byStop.get(stopId) || []).slice()
          .sort((a, b) => String(a.id).localeCompare(String(b.id)));
        for (const line of served) {
          out.push({
            areaId: area.id,
            areaName: area.name || '',
            areaNameEl: area.nameEl || '',
            areaNameSq: area.nameSq || '',
            areaNameIt: area.nameIt || '',
            stopId,
            lineId: line.id,
            line,
          });
        }
      }
    }
    return out;
  }

  // The complex a stop belongs to, or null. Reviewed membership only: never
  // name similarity, never rider distance, never proximity alone.
  function complexForStop(stopId, registry) {
    const list = (registry && registry.complexes) || [];
    for (const c of list) {
      for (const area of c.areas || []) {
        if ((area.stopIds || []).includes(stopId)) return c;
      }
    }
    return null;
  }

  // Every member boarding stop of a complex, in area order.
  function memberStopIds(complex) {
    const out = [];
    for (const area of (complex && complex.areas) || []) {
      for (const id of area.stopIds || []) if (!out.includes(id)) out.push(id);
    }
    return out;
  }

  // ------------------------------------------------------------- departures

  // Identity of one physical departure, used for deduplication ONLY.
  //
  // A provider-qualified trip id plus service date plus boarding stop is the
  // strong form. With no trip id we fall back to a documented conservative
  // composite that includes the EXACT absolute minute: two real trains leaving
  // in the same minute toward different destinations stay distinct, and two
  // trains to the same destination one minute apart stay distinct. We never
  // deduplicate by a rounded countdown or by destination alone.
  function departureIdentity(d) {
    const op = d.operator || (d.line && d.line.operator) || '';
    if (d.tripId) return 'trip:' + op + ':' + d.tripId + '@' + (d.serviceDate || '') + '#' + (d.stopId || '');
    return 'composite:' + (d.stopId || '') + '|' + (d.lineId || '') + '|' + fold(d.destination) +
      '|' + String(d.absoluteMinutes);
  }

  // Merge two records known to describe one departure. The higher-confidence
  // source wins for the time and label; a cancellation from either record
  // survives; live observation metadata is preserved so the row can age out of
  // its Live label instead of staying Live forever.
  function mergeDeparture(a, b) {
    const primary = sourceRank(b.source) > sourceRank(a.source) ? b : a;
    const other = primary === a ? b : a;
    return Object.assign({}, other, primary, {
      cancelled: !!(a.cancelled || b.cancelled),
      status: primary.status || other.status || null,
      scheduledMinutes: a.scheduledMinutes != null ? a.scheduledMinutes
        : (b.scheduledMinutes != null ? b.scheduledMinutes : null),
      observedAt: primary.observedAt != null ? primary.observedAt : other.observedAt,
      tripId: a.tripId || b.tripId || null,
    });
  }

  // Collapse records that provably describe the same departure. Input order is
  // preserved for the survivors so a caller's deliberate ordering is kept.
  function dedupeDepartures(list) {
    const byId = new Map();
    const order = [];
    for (const d of (Array.isArray(list) ? list : [])) {
      if (!d) continue;
      const id = departureIdentity(d);
      if (!byId.has(id)) { byId.set(id, d); order.push(id); continue; }
      const existing = byId.get(id);
      // Two records that BOTH carry a trip id and disagree are two trains, not
      // one. They only reach here through the composite fallback, so keep both.
      if (existing.tripId && d.tripId && existing.tripId !== d.tripId) {
        const alt = id + '#' + d.tripId;
        if (!byId.has(alt)) { byId.set(alt, d); order.push(alt); }
        continue;
      }
      byId.set(id, mergeDeparture(existing, d));
    }
    return order.map((id) => byId.get(id));
  }

  // --------------------------------------------------- trip interpretation

  // Minutes of day, or null.
  function minutesOfDay(hhmm) {
    const m = /^(\d{1,2}):(\d{2})/.exec(String(hhmm || ''));
    if (!m) return null;
    const h = Number(m[1]);
    const min = Number(m[2]);
    if (!Number.isFinite(h) || !Number.isFinite(min)) return null;
    return h * 60 + min;
  }

  // A trip's stops in TRAVEL order, with minutes unwrapped past midnight.
  //
  // The bundled trip data is not consistent about array direction: the suburban
  // A-lines list an inbound trip's stops in outbound geographic order (so the
  // times descend), while the intercity and regional corridors list them in
  // travel order. Reading the array blindly produced destinations like "a train
  // from Athens to Athens". So the travel order is derived from the times, which
  // are the same fact in both layouts, and the array order is only a tie break.
  //
  // Returns { stops: [{ stationId, minutes }], reversed } or null when the trip
  // carries no usable times.
  function tripTravelOrder(trip) {
    const raw = (trip && trip.stops || [])
      .map((s, i) => ({ stationId: s.stationId || s.station_id || s.id, index: i, minutes: minutesOfDay(s.departureTime || s.time) }))
      .filter((s) => s.stationId && s.minutes != null);
    if (raw.length < 2) return null;

    // Unwrap a candidate ordering: every decrease is a midnight crossing. The
    // real travel order is the one that needs no crossing, or the fewest.
    function unwrap(list) {
      let day = 0;
      let crossings = 0;
      const out = [];
      for (let i = 0; i < list.length; i++) {
        if (i > 0 && list[i].minutes + day < out[i - 1].minutes) { day += 24 * 60; crossings++; }
        out.push({ stationId: list[i].stationId, index: list[i].index, minutes: list[i].minutes + day });
      }
      return { out, crossings, span: out[out.length - 1].minutes - out[0].minutes };
    }

    const forward = unwrap(raw);
    const backward = unwrap(raw.slice().reverse());
    // A real trip spans a few hours. The wrong orientation spans nearly a day
    // because every step has to cross midnight, so the smaller span wins.
    const useBackward = backward.crossings < forward.crossings ||
      (backward.crossings === forward.crossings && backward.span < forward.span);
    const chosen = useBackward ? backward : forward;
    return { stops: chosen.out, reversed: useBackward };
  }

  // What this trip offers a rider standing at `stopId`.
  //
  //   { departureMinutes, destinationStopId, destinationIndex, boardsHere }
  //
  // `boardsHere` is false at the trip's own final stop: a terminal arrival is
  // not a departure, and offering "to this very station" is the defect this
  // check exists to prevent. It is also false when the stop is not served.
  function tripBoarding(trip, stopId) {
    const order = tripTravelOrder(trip);
    if (!order) return null;
    const idx = order.stops.findIndex((s) => s.stationId === stopId);
    if (idx < 0) return null;
    const last = order.stops[order.stops.length - 1];
    return {
      departureMinutes: order.stops[idx].minutes % (24 * 60),
      absoluteMinutesOfTrip: order.stops[idx].minutes,
      destinationStopId: last.stationId,
      destinationIndex: order.stops.length - 1,
      boardsHere: idx < order.stops.length - 1,
    };
  }

  // Which directions a rider can actually leave in from `stopId` on a line whose
  // ordered stop list is `orderedStopIds`. A terminal offers one direction, not
  // two: projecting both is how a board invents a train to nowhere.
  //
  //   -> [{ destination, towardIndex }] using the line's own terminal names.
  function lineDirectionsAt(line, stopId) {
    const ordered = (line && line.stations || []).map((s) => s.id);
    const idx = ordered.indexOf(stopId);
    if (idx < 0) return [];
    const out = [];
    if (idx < ordered.length - 1 && line.terminalB) out.push({ destination: line.terminalB, towardIndex: ordered.length - 1 });
    if (idx > 0 && line.terminalA) out.push({ destination: line.terminalA, towardIndex: 0 });
    return out;
  }

  // ------------------------------------------------------------- the board

  // Stable group id. Built from identity, never from an array offset: rows
  // reorder every time a train leaves.
  function groupIdOf(d) {
    return [
      d.areaId || '',
      d.operator || '',
      d.lineId || '',
      d.patternKey || '',
      fold(d.destination),
    ].join('|');
  }

  // buildBoard(input) -> board
  //
  // input = {
  //   complex:   { id, name, nameEl, nameSq, nameIt, areas: [{ id, name... }] },
  //   departures: [{
  //     stopId, areaId, lineId, line, operator, destination, destinationId,
  //     patternKey, tripId, serviceDate, time, absoluteMinutes, minutesAway,
  //     scheduledMinutes, source, sourceLabel, cancelled, status, observedAt,
  //     boardsHere, serviceType, trainNo
  //   }],
  //   coverage:  [{ areaId, stopId, lineId, destination?, state, reason?, nextBeyondWindow? }],
  //   windowMinutes, maxTimesPerGroup, generatedAt
  // }
  //
  // `absoluteMinutes` is minutes from `now` on a monotonic timeline the CALLER
  // computed from absolute Europe/Athens timestamps. This module never adds 24h
  // to a past departure and never re-derives a service date: doing that here is
  // what produced "every yesterday train is 23h away" bugs.
  function buildBoard(input) {
    const complex = (input && input.complex) || null;
    const windowMinutes = Number.isFinite(input && input.windowMinutes) ? input.windowMinutes : 12 * 60;
    const maxTimes = Number.isFinite(input && input.maxTimesPerGroup) ? input.maxTimesPerGroup : 3;
    const coverage = Array.isArray(input && input.coverage) ? input.coverage : [];

    // 1. Only real boardable departures become actionable rows. A terminal
    //    arrival with no onward boarding is not a departure, a pass-through
    //    with no pickup is not a departure, and a record outside the requested
    //    window belongs to the lookahead, not the board.
    const raw = (Array.isArray(input && input.departures) ? input.departures : [])
      .filter((d) => d && d.boardsHere !== false)
      .filter((d) => Number.isFinite(d.absoluteMinutes));

    // 2. Reconcile per service/trip, NOT per station. One published suburban
    //    response can no longer suppress both metro directions, because nothing
    //    here selects a single winning source for the whole complex.
    const merged = dedupeDepartures(raw);

    // 3. Enumerate groups BEFORE any limit is applied.
    const groups = new Map();
    const order = [];
    for (const d of merged) {
      const id = groupIdOf(d);
      if (!groups.has(id)) {
        groups.set(id, {
          id,
          areaId: d.areaId || '',
          stopId: d.stopId || '',
          operator: d.operator || '',
          lineId: d.lineId || '',
          line: d.line || null,
          serviceType: d.serviceType || '',
          destination: d.destination || '',
          destinationId: d.destinationId || null,
          destinationKey: fold(d.destination),
          patternKey: d.patternKey || '',
          _all: [],
        });
        order.push(id);
      }
      const g = groups.get(id);
      if (!g.serviceType && d.serviceType) g.serviceType = d.serviceType;
      g._all.push(d);
    }

    const inWindow = (d) => d.absoluteMinutes >= 0 && d.absoluteMinutes <= windowMinutes;

    const rows = order.map((id) => {
      const g = groups.get(id);
      const all = g._all.slice().sort((a, b) =>
        (a.absoluteMinutes - b.absoluteMinutes) || String(a.tripId || '').localeCompare(String(b.tripId || '')));
      const within = all.filter(inWindow);
      const beyond = all.filter((d) => d.absoluteMinutes > windowMinutes);
      // A cancellation is status, never the recommended next departure.
      const eligible = within.filter((d) => !d.cancelled);
      const shown = (within.length ? within : beyond.slice(0, 1)).slice(0, maxTimes > 0 ? maxTimes : within.length);
      const next = eligible[0] || null;
      let state = COVERAGE.LOADED;
      if (!within.length && beyond.length) state = COVERAGE.NO_DEPARTURE;
      else if (!within.length) state = COVERAGE.NO_DEPARTURE;
      return {
        id: g.id,
        areaId: g.areaId,
        stopId: g.stopId,
        operator: g.operator,
        lineId: g.lineId,
        line: g.line,
        serviceType: g.serviceType,
        destination: g.destination,
        destinationId: g.destinationId,
        destinationKey: g.destinationKey,
        patternKey: g.patternKey,
        // Each time keeps its OWN source and cancellation state, so a group-wide
        // badge can never advertise a later live ETA over a scheduled lead time
        // and a cancelled later train is not silently painted "Scheduled".
        times: shown.map((d) => ({
          minutesAway: d.minutesAway != null ? d.minutesAway : d.absoluteMinutes,
          absoluteMinutes: d.absoluteMinutes,
          time: d.time || '',
          tripId: d.tripId || null,
          trainNo: d.trainNo || null,
          source: d.source || null,
          sourceLabel: d.sourceLabel || null,
          cancelled: !!d.cancelled,
          status: d.status || null,
          observedAt: d.observedAt != null ? d.observedAt : null,
          beyondWindow: d.absoluteMinutes > windowMinutes,
          scheduledMinutes: d.scheduledMinutes != null ? d.scheduledMinutes : null,
        })),
        next: next ? {
          minutesAway: next.minutesAway != null ? next.minutesAway : next.absoluteMinutes,
          absoluteMinutes: next.absoluteMinutes,
          time: next.time || '',
          tripId: next.tripId || null,
          source: next.source || null,
          sourceLabel: next.sourceLabel || null,
        } : null,
        source: (shown[0] && shown[0].source) || null,
        sourceLabel: (shown[0] && shown[0].sourceLabel) || null,
        moreCount: Math.max(0, within.length - shown.length),
        total: within.length,
        coverage: state,
        sortMinutes: next ? next.absoluteMinutes
          : (within[0] ? within[0].absoluteMinutes : (beyond[0] ? beyond[0].absoluteMinutes : Infinity)),
      };
    });

    // 4. Services that produced no row at all still have to be visible, or a
    //    partly loaded board looks complete. Their coverage entry becomes a
    //    differentiated row placed after the timed results.
    const timedIds = new Set(rows.filter((r) => r.total > 0).map((r) => r.id));
    const statusRows = [];
    for (const c of coverage) {
      if (!c || c.state === COVERAGE.LOADED) continue;
      const id = [c.areaId || '', c.operator || '', c.lineId || '', c.patternKey || '', fold(c.destination)].join('|');
      if (timedIds.has(id)) continue;
      statusRows.push({
        id,
        areaId: c.areaId || '',
        stopId: c.stopId || '',
        operator: c.operator || '',
        lineId: c.lineId || '',
        line: c.line || null,
        serviceType: c.serviceType || '',
        destination: c.destination || '',
        destinationId: c.destinationId || null,
        destinationKey: fold(c.destination),
        patternKey: c.patternKey || '',
        times: [],
        next: null,
        source: null,
        sourceLabel: null,
        moreCount: 0,
        total: 0,
        coverage: c.state,
        coverageReason: c.reason || null,
        nextBeyondWindow: c.nextBeyondWindow || null,
        sortMinutes: Infinity,
      });
    }

    // 5. Sort by the next eligible effective departure, with a stable tie break
    //    on the group id. A frequent metro service therefore cannot crowd a less
    //    frequent railway destination off the board: every group keeps one row.
    const timed = rows.filter((r) => r.total > 0)
      .sort((a, b) => (a.sortMinutes - b.sortMinutes) || a.id.localeCompare(b.id));
    const empty = rows.filter((r) => r.total === 0)
      .concat(statusRows)
      .sort((a, b) => a.id.localeCompare(b.id));

    const states = {};
    for (const c of coverage) {
      const k = c && c.state;
      if (!k) continue;
      states[k] = (states[k] || 0) + 1;
    }
    // Partial means a service could not be READ, not that a service has no
    // train in the window. A quiet night is complete information; a failed
    // fetch is not, and conflating them would cry wolf every night.
    const partial = coverage.some((c) => c &&
      (c.state === COVERAGE.LOADING || c.state === COVERAGE.UNAVAILABLE));

    return {
      complexId: complex ? complex.id : '',
      complex,
      scope: 'all_directions',
      generatedAt: (input && input.generatedAt) || null,
      windowMinutes,
      groups: timed.concat(empty),
      timedGroupCount: timed.length,
      partial,
      coverageStates: states,
    };
  }

  // Localized complex name for a reader's language.
  function complexName(complex, language) {
    if (!complex) return '';
    switch (String(language || 'en')) {
      case 'el': return complex.nameEl || complex.name || '';
      case 'sq': return complex.nameSq || complex.name || '';
      case 'it': return complex.nameIt || complex.name || '';
      default: return complex.name || complex.nameEl || '';
    }
  }

  // Localized boarding-area name.
  function areaName(complex, areaId, language) {
    const area = ((complex && complex.areas) || []).find((a) => a.id === areaId);
    if (!area) return '';
    switch (String(language || 'en')) {
      case 'el': return area.nameEl || area.name || '';
      case 'sq': return area.nameSq || area.name || '';
      case 'it': return area.nameIt || area.name || '';
      default: return area.name || area.nameEl || '';
    }
  }

  return {
    COVERAGE,
    fold,
    sourceRank,
    resolveComplexServices,
    minutesOfDay,
    tripTravelOrder,
    tripBoarding,
    lineDirectionsAt,
    complexForStop,
    memberStopIds,
    departureIdentity,
    dedupeDepartures,
    groupIdOf,
    buildBoard,
    complexName,
    areaName,
  };
});
