'use strict';
// Syrmos 3.0 schedule-aware planning pass (web reference implementation).
//
// Turns a topology option (legs with no clock) into a time-aware one by
// assigning REAL scheduled departure/arrival instants from a timetable, so the
// shared feasibility engine can compute honest margins (comfortable/tight) from
// actual service headways instead of leaving everything "estimated". A direct
// trip gets a real departure/arrival clock; a transfer gets a real margin.
//
// Rules (mirror Kotlin com.syrmos.core.domain.journey.SchedulePlanner):
//   - First ride: earliest departure at/after the requested instant.
//   - Next ride: earliest departure at/after (previous arrival + transferMinimum),
//     so the assigned plan is always physically catchable; the feasibility margin
//     then measures the spare time beyond that minimum.
//   - A leg with no available departure (or unknown travel time) stays unscheduled
//     (instants null, timingKind 'unknown'); later legs cannot be timed either.
//   - All arithmetic is on absolute epoch seconds, so Europe/Athens DST and
//     past-midnight service are handled by the offsets in the instants themselves.
//     Emitted instants are canonical UTC ISO.
//
// UMD: `require('./web-schedule-plan.js')` / `window.SyrmosSchedulePlan`.
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.SyrmosSchedulePlan = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  const DEFAULT_TRANSFER_SECONDS = 120;

  function epoch(iso) {
    if (iso == null) return null;
    const ms = Date.parse(iso);
    return Number.isFinite(ms) ? Math.floor(ms / 1000) : null;
  }
  function iso(epochSeconds) {
    return new Date(epochSeconds * 1000).toISOString();
  }
  function key2(a, b) { return a + '|' + b; }
  function key3(a, b, c) { return a + '|' + b + '|' + c; }

  // Earliest departure epoch at/after `readyEpoch`, or null if none.
  function nextDeparture(timetable, lineId, stationId, readyEpoch) {
    const list = (timetable.departures || {})[key2(lineId, stationId)] || [];
    let best = null;
    for (const d of list) {
      const e = epoch(d);
      if (e == null || e < readyEpoch) continue;
      if (best == null || e < best) best = e;
    }
    return best;
  }

  // assignSchedule(option, opts, timetable) -> new option with instants filled.
  // opts: { requestedInstant (ISO, required), defaultTransferSeconds }
  function assignSchedule(option, opts, timetable) {
    const o = opts || {};
    const tt = timetable || {};
    const defTransfer = Number.isFinite(o.defaultTransferSeconds) ? o.defaultTransferSeconds : DEFAULT_TRANSFER_SECONDS;
    const requested = epoch(o.requestedInstant);
    const legs = (option && Array.isArray(option.legs)) ? option.legs.map((l) => Object.assign({}, l)) : [];

    let readyEpoch = requested;
    let pendingTransferMin = 0; // transfer minimum that gates the NEXT ride
    let firstDep = null, lastArr = null, timedAll = true;

    for (let i = 0; i < legs.length; i++) {
      const leg = legs[i];
      if (leg.kind === 'transfer' || leg.kind === 'walk') {
        pendingTransferMin = (leg.transferMinimumSeconds == null) ? defTransfer : leg.transferMinimumSeconds;
        continue;
      }
      if (leg.kind !== 'ride') continue;

      if (readyEpoch == null || !timedAll) { markUnknown(leg); timedAll = false; continue; }

      const boardReady = readyEpoch + (firstDep == null ? 0 : pendingTransferMin);
      const depEpoch = nextDeparture(tt, leg.lineId, leg.fromId, boardReady);
      const travel = ((tt.legSeconds || {})[key3(leg.lineId, leg.fromId, leg.toId)]);
      if (depEpoch == null || !Number.isFinite(travel)) { markUnknown(leg); timedAll = false; continue; }

      const arrEpoch = depEpoch + travel;
      leg.departureInstant = iso(depEpoch);
      leg.arrivalInstant = iso(arrEpoch);
      leg.timingKind = 'scheduled';
      if (firstDep == null) firstDep = depEpoch;
      lastArr = arrEpoch;
      readyEpoch = arrEpoch;
      pendingTransferMin = 0;
    }

    const out = Object.assign({}, option, { legs });
    out.departureInstant = firstDep == null ? null : iso(firstDep);
    out.arrivalInstant = (timedAll && lastArr != null) ? iso(lastArr) : null;
    out.durationSeconds = (timedAll && firstDep != null && lastArr != null) ? (lastArr - firstDep) : null;
    out.timingKind = timedAll ? 'scheduled' : 'unknown';
    return out;
  }

  function markUnknown(leg) {
    leg.departureInstant = null;
    leg.arrivalInstant = null;
    leg.timingKind = 'unknown';
  }

  // Latest departure epoch with (dep + travel) <= deadline, or null if none.
  function latestDeparture(timetable, lineId, stationId, travel, deadline) {
    const list = (timetable.departures || {})[key2(lineId, stationId)] || [];
    let best = null;
    for (const d of list) {
      const e = epoch(d);
      if (e == null) continue;
      if (e + travel <= deadline && (best == null || e > best)) best = e;
    }
    return best;
  }

  // Backward (arrive-by) pass: assign the LATEST departures such that the rider
  // still arrives by `arriveByInstant`. This is a real backward search, not a
  // static-duration subtraction (prompt 7.2). When `arriveByInstant` is null the
  // last leg takes the latest available departure -> "last connection home".
  // The first ride's departure is the "leave by" answer.
  function assignScheduleArriveBy(option, opts, timetable) {
    const o = opts || {};
    const tt = timetable || {};
    const defTransfer = Number.isFinite(o.defaultTransferSeconds) ? o.defaultTransferSeconds : DEFAULT_TRANSFER_SECONDS;
    const legs = (option && Array.isArray(option.legs)) ? option.legs.map((l) => Object.assign({}, l)) : [];
    const rideIdx = [];
    legs.forEach((l, i) => { if (l.kind === 'ride') rideIdx.push(i); });

    // Transfer minimum gating the connection INTO ride at ride-array position `pos`.
    function transferMinBefore(pos) {
      if (pos <= 0) return 0;
      for (let j = rideIdx[pos] - 1; j > rideIdx[pos - 1]; j--) {
        const k = legs[j] && legs[j].kind;
        if (k === 'transfer' || k === 'walk') {
          return (legs[j].transferMinimumSeconds == null) ? defTransfer : legs[j].transferMinimumSeconds;
        }
      }
      return defTransfer;
    }

    let deadline = (o.arriveByInstant != null) ? epoch(o.arriveByInstant) : Infinity;
    let timedAll = true;
    for (let pos = rideIdx.length - 1; pos >= 0; pos--) {
      const leg = legs[rideIdx[pos]];
      const travel = (tt.legSeconds || {})[key3(leg.lineId, leg.fromId, leg.toId)];
      if (deadline == null || !Number.isFinite(travel)) { markUnknown(leg); timedAll = false; continue; }
      const dep = latestDeparture(tt, leg.lineId, leg.fromId, travel, deadline);
      if (dep == null) { markUnknown(leg); timedAll = false; continue; }
      leg.departureInstant = iso(dep);
      leg.arrivalInstant = iso(dep + travel);
      leg.timingKind = 'scheduled';
      deadline = dep - transferMinBefore(pos); // previous ride must arrive by here
    }

    const out = Object.assign({}, option, { legs });
    const rides = rideIdx.map((i) => legs[i]);
    const firstDep = rides.length && rides[0].departureInstant ? epoch(rides[0].departureInstant) : null;
    const lastArr = (timedAll && rides.length && rides[rides.length - 1].arrivalInstant)
      ? epoch(rides[rides.length - 1].arrivalInstant) : null;
    out.departureInstant = firstDep == null ? null : iso(firstDep);
    out.arrivalInstant = lastArr == null ? null : iso(lastArr);
    out.durationSeconds = (timedAll && firstDep != null && lastArr != null) ? (lastArr - firstDep) : null;
    out.timingKind = timedAll ? 'scheduled' : 'unknown';
    return out;
  }

  // Last connection home: the latest feasible journey to the destination (no
  // arrive-by target; the final leg takes the latest available departure).
  function lastConnection(option, opts, timetable) {
    return assignScheduleArriveBy(option, Object.assign({}, opts || {}, { arriveByInstant: null }), timetable);
  }

  return { assignSchedule, assignScheduleArriveBy, lastConnection, nextDeparture, DEFAULT_TRANSFER_SECONDS };
});
