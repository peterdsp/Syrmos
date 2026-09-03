'use strict';
// Syrmos GO — the live trip-guidance engine (web reference implementation).
//
// GO is the 3.0 "Journeys" spine: once a rider is on a planned journey, tell them
// the one thing that matters right now — board, ride, get off next, change here,
// arrived — so they never have to watch for their stop. This module is the pure,
// deterministic core: no DOM, no network, no clock. It works fully offline from a
// journey's static stop sequence (live positions only advance `position` faster).
//
// It is the reference for the cross-client contract in fixtures/go-guidance/. The
// iOS (Swift), Android/KMP (Kotlin) and web implementations must all satisfy the
// same fixtures so GO guidance cannot drift between platforms.
//
// A journey is { legs: [ { lineId, towards, stops: [ {id, name}, ... ] } ] } where
// each leg's `stops` are ordered from its board stop to its alight stop inclusive,
// and `towards` is the direction/terminal shown to the rider. A position is
// { legIndex, stopIndex }: the rider is currently AT legs[legIndex].stops[stopIndex].
//
// UMD: usable as `require('./web-go.js')` in node and as `window.SyrmosGO` in the
// browser.
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.SyrmosGO = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  // Guidance step kinds.
  const BOARD = 'board';       // at a leg's first stop: get on this line
  const RIDE = 'ride';         // mid-leg: stay on, N stops to go
  const GET_OFF_NEXT = 'getOffNext'; // one stop before this leg's alight point
  const TRANSFER = 'transfer'; // at a leg's alight stop with another leg to come
  const ARRIVED = 'arrived';   // at the destination

  function leg(journey, i) {
    return journey && journey.legs ? journey.legs[i] : undefined;
  }

  // The rider-facing instruction for the current position. Pure and total: an
  // out-of-range position throws, since that is a caller bug, not a rider state.
  function guidance(journey, position) {
    const l = leg(journey, position.legIndex);
    if (!l) throw new RangeError('legIndex out of range');
    const stops = l.stops;
    if (position.stopIndex < 0 || position.stopIndex >= stops.length) {
      throw new RangeError('stopIndex out of range');
    }
    const lastLeg = position.legIndex === journey.legs.length - 1;
    const lastStop = stops.length - 1;
    const remaining = lastStop - position.stopIndex; // stops left on this leg
    const here = stops[position.stopIndex];

    // Destination reached.
    if (lastLeg && remaining === 0) return { kind: ARRIVED, station: here.name };

    // Alight point of a non-final leg: change to the next line.
    if (remaining === 0) {
      const next = journey.legs[position.legIndex + 1];
      return { kind: TRANSFER, atStation: here.name, toLineId: next.lineId, towards: next.towards };
    }

    // First stop of a leg: board this line. (For a 2-stop leg this coincides with
    // being one stop from the alight; the display says "board", and
    // shouldAlertGetOff still returns true so the get-off alert is not missed.)
    if (position.stopIndex === 0) {
      return {
        kind: BOARD, lineId: l.lineId, towards: l.towards,
        stopsRemaining: remaining, nextStation: stops[1].name,
      };
    }

    // One stop before the alight point: tell the rider to get off next.
    if (remaining === 1) {
      const next = lastLeg ? null : journey.legs[position.legIndex + 1];
      return {
        kind: GET_OFF_NEXT,
        nextStation: stops[lastStop].name,
        isDestination: lastLeg,
        transferTo: next ? next.lineId : null,
      };
    }

    // Mid-leg: stay on.
    return {
      kind: RIDE, lineId: l.lineId, towards: l.towards,
      stopsRemaining: remaining, nextStation: stops[position.stopIndex + 1].name,
    };
  }

  // Whether a get-off notification should fire now: the rider is exactly one stop
  // from a leg's alight point (works even on a 2-stop leg, where it coincides with
  // boarding). This is the single most-valued transit alert; keep it independent
  // of the display `kind` so a UI can drive the notification off one predicate.
  //
  // Consumer note: on a 2-stop leg the guidance kind stays `board` while this is
  // already true (board + alert), because after boarding the very next stop is the
  // alight. A consumer that dedupes get-off alerts must key the dedupe on the leg's
  // alight stop, not on the guidance `kind` becoming `getOffNext` (which never
  // happens on a 2-stop leg) — otherwise it would drop the rider's only get-off cue.
  function shouldAlertGetOff(journey, position) {
    const l = leg(journey, position.legIndex);
    if (!l) return false;
    const remaining = l.stops.length - 1 - position.stopIndex;
    return remaining === 1;
  }

  // Advance the position by one stop, rolling from a leg's alight stop onto the
  // next leg's board stop. Returns a new position; returns the same position when
  // already at the destination.
  function advance(journey, position) {
    const l = leg(journey, position.legIndex);
    if (!l) throw new RangeError('legIndex out of range');
    const lastLeg = position.legIndex === journey.legs.length - 1;
    const atLegEnd = position.stopIndex >= l.stops.length - 1;
    if (atLegEnd) {
      if (lastLeg) return { legIndex: position.legIndex, stopIndex: position.stopIndex };
      return { legIndex: position.legIndex + 1, stopIndex: 0 };
    }
    return { legIndex: position.legIndex, stopIndex: position.stopIndex + 1 };
  }

  function isArrived(journey, position) {
    const lastLeg = position.legIndex === journey.legs.length - 1;
    const l = leg(journey, position.legIndex);
    return Boolean(lastLeg && l && position.stopIndex === l.stops.length - 1);
  }

  // Live GO: given the rider's GPS fix and the journey's stop coordinates
  // ({ [stopId]: {lat, lon} }), return the forward-most position they've reached,
  // so the guidance advances on its own. Forward-only (jitter never rewinds),
  // threshold-gated (holds between stops so guidance doesn't flicker). Mirrors iOS
  // GoLocationAdvancer.
  function advancedPosition(journey, current, coords, lat, lon, thresholdMeters) {
    const threshold = thresholdMeters == null ? 350 : thresholdMeters;
    let best = current;
    let bestDist = Infinity;
    let cursor = current;
    while (cursor) {
      const st = stopAt(journey, cursor);
      if (st && coords[st.id]) {
        const d = haversine(coords[st.id].lat, coords[st.id].lon, lat, lon);
        if (d <= threshold && d < bestDist) { bestDist = d; best = cursor; }
      }
      cursor = nextPos(journey, cursor);
    }
    return best;
  }

  function stopAt(journey, p) {
    const l = leg(journey, p.legIndex);
    if (!l || p.stopIndex < 0 || p.stopIndex >= l.stops.length) return null;
    return l.stops[p.stopIndex];
  }

  function nextPos(journey, p) {
    const l = leg(journey, p.legIndex);
    if (!l) return null;
    if (p.stopIndex < l.stops.length - 1) return { legIndex: p.legIndex, stopIndex: p.stopIndex + 1 };
    if (p.legIndex < journey.legs.length - 1) return { legIndex: p.legIndex + 1, stopIndex: 0 };
    return null;
  }

  function haversine(lat1, lon1, lat2, lon2) {
    const R = 6371000;
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLon = (lon2 - lon1) * Math.PI / 180;
    const a = Math.sin(dLat / 2) ** 2
      + Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLon / 2) ** 2;
    return 2 * R * Math.asin(Math.min(1, Math.sqrt(a)));
  }

  return {
    guidance, shouldAlertGetOff, advance, isArrived, advancedPosition, haversine,
    KIND: { BOARD, RIDE, GET_OFF_NEXT, TRANSFER, ARRIVED },
  };
});
