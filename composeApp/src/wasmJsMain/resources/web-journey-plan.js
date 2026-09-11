'use strict';
// Syrmos 3.0 web journey plan (Phase P): composes the existing topology planner
// into the shared 3.0 contract. Takes a request, produces ranked JourneyOptions
// with honest, estimated timing and computed feasibility.
//
// Truthfulness: the topology planner has no schedule, so this attaches NO
// absolute clock times. Every ride leg is timingKind 'estimated', instants are
// null (unknown, not zero), and durationSeconds is a disclosed estimate derived
// from per-mode hop times. Feasibility therefore reads 'unknown' for any transfer
// (we cannot prove a connection without real times) and 'direct' for a single
// ride. This is deliberate: it never fabricates a 08:42 arrival from a graph weight.
//
// Ranking, dedup and cap come from the shared SyrmosJourneyRanker; feasibility
// from the shared SyrmosFeasibility, so the rules match Kotlin exactly.
//
// UMD: `require('./web-journey-plan.js')` / `window.SyrmosJourneyPlan`.
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.SyrmosJourneyPlan = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  // Disclosed estimate for an interchange when there is no scheduled minimum.
  const TRANSFER_ESTIMATE_MINUTES = 3;

  function deps() {
    const g = (typeof window !== 'undefined') ? window : {};
    const req = (typeof require === 'function') ? require : null;
    return {
      Planner: g.SyrmosPlanner || (req && req('./web-planner.js')),
      Feasibility: g.SyrmosFeasibility || (req && req('./web-feasibility.js')),
      Ranker: g.SyrmosJourneyRanker || (req && req('./web-journey-ranker.js')),
      SchedulePlan: g.SyrmosSchedulePlan || (req && req('./web-schedule-plan.js')),
    };
  }

  function typeIndex(lines) {
    const m = new Map();
    for (const l of lines || []) m.set(l.id, l.type);
    return m;
  }

  // Convert the topology planner's { legs:[{lineId, towards, stops}] } into a
  // JourneyOption with explicit transfer legs between rides.
  function toOption(detailed, fromId, toId, typeById, travelTime) {
    const legs = [];
    detailed.legs.forEach((pl, i) => {
      const stops = pl.stops.map((s) => s.id);
      if (i > 0) {
        const prevAlight = legs[legs.length - 1].toId;
        legs.push({
          id: 'transfer-' + i, kind: 'transfer',
          fromId: prevAlight, toId: stops[0],
          orderedStopIds: [prevAlight, stops[0]],
          departureInstant: null, arrivalInstant: null,
          timingKind: 'estimated', transferMinimumSeconds: null, uncertaintySeconds: null,
          accessibility: 'unknown',
        });
      }
      legs.push({
        id: 'ride-' + i, kind: 'ride', lineId: pl.lineId,
        fromId: stops[0], toId: stops[stops.length - 1],
        orderedStopIds: stops,
        departureInstant: null, arrivalInstant: null,
        timingKind: 'estimated', uncertaintySeconds: null, accessibility: 'unknown',
      });
    });

    let minutes = 0;
    for (const pl of detailed.legs) {
      minutes += (pl.stops.length - 1) * travelTime(typeById.get(pl.lineId));
    }
    minutes += (detailed.legs.length - 1) * TRANSFER_ESTIMATE_MINUTES;
    const transferCount = Math.max(0, detailed.legs.length - 1);

    return {
      id: 'plan-' + fromId + '-' + toId,
      requestId: 'req-' + fromId + '-' + toId,
      legs,
      departureInstant: null,
      arrivalInstant: null,
      durationSeconds: minutes * 60,
      transferCount,
      walkingSeconds: null, // no walking data on web -> unknown, never invented
      timingKind: 'estimated',
      rankingBadge: null,
    };
  }

  // Stable per-route id from the leg chain, so distinct candidates sort/dedup
  // deterministically (the ranker dedups by leg signature; this feeds tiebreaks).
  function optionId(detailed) {
    return 'opt-' + detailed.legs.map((l) => l.lineId + ':' + l.stops[0].id + '>' + l.stops[l.stops.length - 1].id).join('_');
  }

  // Alternatives via line-banning: re-plan with each line used by the base route
  // removed, yielding a materially different route where one exists. A light,
  // deterministic stand-in for k-shortest that reuses the topology planner as-is.
  function candidateRoutes(base, stations, lines, fromId, toId, language, Planner) {
    const routes = [base];
    const used = [];
    for (const l of base.legs) if (used.indexOf(l.lineId) < 0) used.push(l.lineId);
    for (const banned of used) {
      const filtered = lines.filter((l) => l.id !== banned);
      const alt = Planner.planDetailed(stations, filtered, fromId, toId, language);
      if (alt && alt.legs && alt.legs.length) routes.push(alt);
    }
    return routes;
  }

  // Apply the requested time mode (forward / arrive-by / last-connection) to one
  // option with its own timetable. Returns the option unchanged when no timetable.
  function applySchedule(option, request, timetable, SchedulePlan) {
    if (!timetable || !SchedulePlan) return option;
    const dt = request.defaultTransferSeconds;
    if (request.timeMode === 'arriveBy' && request.arriveByInstant) {
      return SchedulePlan.assignScheduleArriveBy(option, { arriveByInstant: request.arriveByInstant, defaultTransferSeconds: dt }, timetable);
    }
    if (request.timeMode === 'lastConnection') {
      return SchedulePlan.lastConnection(option, { defaultTransferSeconds: dt }, timetable);
    }
    return SchedulePlan.assignSchedule(option, { requestedInstant: request.requestedInstant, defaultTransferSeconds: dt }, timetable);
  }

  // plan(stations, lines, request) -> { requestId, options: [JourneyOption] (<=3) }
  // request: { fromStationId, toStationId, ranking, language, timeMode,
  //   requestedInstant?, arriveByInstant?, defaultTransferSeconds?,
  //   timetable? (single) OR buildTimetable?(detailed)->timetable (per candidate) }
  function plan(stations, lines, request) {
    const d = deps();
    if (!d.Planner || !d.Feasibility || !d.Ranker) return { requestId: null, options: [] };
    const fromId = request && request.fromStationId;
    const toId = request && request.toStationId;
    const ranking = (request && request.ranking) || 'fastest';
    const base = d.Planner.planDetailed(stations, lines, fromId, toId, request && request.language);
    if (!base || !base.legs || !base.legs.length) return { requestId: null, options: [] };

    const typeById = typeIndex(lines);
    const routes = candidateRoutes(base, stations, lines, fromId, toId, request && request.language, d.Planner);
    const options = routes.map((detailed) => {
      const bId = detailed.legs[0].stops[0].id;
      const aId = detailed.legs[detailed.legs.length - 1].stops.slice(-1)[0].id;
      let o = toOption(detailed, bId, aId, typeById, d.Planner._travelTime);
      o.id = optionId(detailed);
      // Each candidate needs its OWN timetable (different lines/boards); the caller
      // supplies buildTimetable(detailed) for that, or a single shared timetable.
      const tt = (typeof request.buildTimetable === 'function') ? request.buildTimetable(detailed) : request.timetable;
      o = applySchedule(o, request, tt, d.SchedulePlan);
      o.feasibility = d.Feasibility.forOption(o);
      return o;
    });
    // Ranker dedups identical leg sequences, orders by objective, caps at 3.
    const ranked = d.Ranker.rank(options, ranking);
    return { requestId: (options[0] && options[0].requestId) || null, options: ranked };
  }

  return { plan, _toOption: toOption, _candidateRoutes: candidateRoutes };
});
