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

  // plan(stations, lines, request) -> { requestId, options: [JourneyOption] }
  // request: { fromStationId, toStationId, ranking = 'fastest', language }
  function plan(stations, lines, request) {
    const d = deps();
    if (!d.Planner || !d.Feasibility || !d.Ranker) return { requestId: null, options: [] };
    const fromId = request && request.fromStationId;
    const toId = request && request.toStationId;
    const ranking = (request && request.ranking) || 'fastest';
    const detailed = d.Planner.planDetailed(stations, lines, fromId, toId, request && request.language);
    if (!detailed || !detailed.legs || !detailed.legs.length) return { requestId: null, options: [] };

    const typeById = typeIndex(lines);
    let option = toOption(detailed, fromId, toId, typeById, d.Planner._travelTime);
    // If a real timetable is supplied, upgrade the estimated option to scheduled
    // instants so feasibility is real (comfortable/tight) rather than estimated.
    // Without one, the estimated option stands (honest: feasibility unknown/direct).
    if (request && request.timetable && d.SchedulePlan) {
      option = d.SchedulePlan.assignSchedule(
        option,
        { requestedInstant: request.requestedInstant, defaultTransferSeconds: request.defaultTransferSeconds },
        request.timetable,
      );
    }
    option.feasibility = d.Feasibility.forOption(option);
    const options = d.Ranker.rank([option], ranking);
    return { requestId: option.requestId, options };
  }

  return { plan, _toOption: toOption };
});
