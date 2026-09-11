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

  return { assignSchedule, nextDeparture, DEFAULT_TRANSFER_SECONDS };
});
