'use strict';
// Web point-to-point planner: Dijkstra over the bundled Athens rail graph,
// producing a GuidanceJourney (legs of ordered {id, name} stops) that the GO
// engine (web-go.js) can guide the rider through. It is the web peer of iOS
// `JourneyPlanner.planDetailed`, so GO works on the web the same way it does on
// iOS: plan A -> B, then GO.
//
// Interchange stations carry a different id per line (M2_SYN vs M3_SYN at
// Syntagma), so the graph adds transfer edges between co-located stations (same
// accent-folded name), letting Dijkstra change lines at a physical interchange.
//
// Pure: no DOM, no network. Callers pass the bundled `stations` (registry, for
// names + interchange grouping) and `lines` (each with an ordered `stations`
// array and a `type`).
//
// UMD: `require('./web-planner.js')` in node, `window.SyrmosPlanner` in browser.
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.SyrmosPlanner = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  const TRANSFER_MINUTES = 3;

  function travelTime(type) {
    switch (type) {
      case 'metro': return 2;
      case 'tram': return 3;
      case 'suburban': return 4;
      case 'bus': return 4;
      default: return 5;
    }
  }

  // Accent-folded, alphanumerics-only key for grouping co-located interchange
  // stations by name (mirrors iOS AthensTransitParser.fold + filter).
  function nameKey(s) {
    return String(s || '')
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]/g, '');
  }

  function displayName(st) {
    if (!st) return '';
    return st.name || st.name_el || st.nameEl || st.id;
  }

  // Build { id -> station } from the registry, falling back to line.stations.
  function indexStations(stations, lines) {
    const byId = new Map();
    for (const s of stations || []) byId.set(s.id, s);
    for (const l of lines || []) {
      for (const s of l.stations || []) if (!byId.has(s.id)) byId.set(s.id, s);
    }
    return byId;
  }

  function buildGraph(lines, byId) {
    const graph = new Map();
    const add = (a, b, lineId, weight) => {
      if (!graph.has(a)) graph.set(a, []);
      graph.get(a).push({ to: b, lineId, weight });
    };

    const toRad = (d) => (d * Math.PI) / 180;
    const latOf = (s) => (s && (s.lat != null ? s.lat : s.latitude));
    const lngOf = (s) => (s && (s.lng != null ? s.lng : (s.lon != null ? s.lon : s.longitude)));
    const distMeters = (a, b) => {
      const la1 = latOf(a), lo1 = lngOf(a), la2 = latOf(b), lo2 = lngOf(b);
      if (la1 == null || lo1 == null || la2 == null || lo2 == null) return Infinity;
      const dLat = toRad(la2 - la1), dLng = toRad(lo2 - lo1);
      const h = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(la1)) * Math.cos(toRad(la2)) * Math.sin(dLng / 2) ** 2;
      return 6371000 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    };
    // Ride time for one hop. In-city lines (metro/tram/local suburban) keep the
    // flat per-stop value (close, evenly-spaced stops; preserves route choices).
    // National-region lines are intercity rail / long-distance coaches whose
    // stops are tens of km apart, so those are costed by real distance / speed.
    // A flat per-stop value made the ~500 km Athens-Thessaloniki intercity cost
    // like ten metro hops (~40 min) instead of its real ~5 hours.
    const segMinutes = (line, a, b) => {
      const national = String(line.region || '').toLowerCase() === 'national';
      if (!national) return travelTime(line.type);
      const km = distMeters(a, b) / 1000;
      if (!isFinite(km)) return travelTime(line.type);
      const speed = line.type === 'bus' ? 70 : 95;
      return Math.max(1, Math.round((km / speed) * 60));
    };

    // Same-line edges between consecutive stops, weighted by real ride time.
    for (const line of lines || []) {
      const ordered = line.stations || [];
      for (let i = 0; i < ordered.length - 1; i++) {
        const sa = ordered[i], sb = ordered[i + 1];
        const w = segMinutes(line, sa, sb);
        add(sa.id, sb.id, line.id, w);
        add(sb.id, sa.id, line.id, w);
      }
    }

    // Transfer edges between co-located stations: same folded name AND
    // physically close. A shared name alone is wrong: "Panepistimio",
    // "Syntagma" etc. exist in both Athens and Thessaloniki, so grouping by name
    // alone lets Dijkstra "change lines" from an Athens metro to a Thessaloniki
    // one at a station 500 km away instead of routing via the intercity train.
    const MAX_TRANSFER_METERS = 500;
    const groups = new Map();
    for (const id of byId.keys()) {
      const key = nameKey(displayName(byId.get(id)));
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key).push(id);
    }
    for (const ids of groups.values()) {
      if (ids.length < 2) continue;
      for (let i = 0; i < ids.length; i++) {
        for (let j = i + 1; j < ids.length; j++) {
          if (distMeters(byId.get(ids[i]), byId.get(ids[j])) > MAX_TRANSFER_METERS) continue;
          add(ids[i], ids[j], null, TRANSFER_MINUTES);
          add(ids[j], ids[i], null, TRANSFER_MINUTES);
        }
      }
    }
    return graph;
  }

  // Dijkstra returning the ordered [{stationId, edge}] path and total weight.
  function shortestPath(graph, fromId, toId) {
    const dist = new Map([[fromId, 0]]);
    const prev = new Map();
    const visited = new Set();
    const frontier = [[fromId, 0]];
    while (frontier.length) {
      frontier.sort((a, b) => a[1] - b[1]);
      const [cur, d] = frontier.shift();
      if (visited.has(cur)) continue;
      visited.add(cur);
      if (cur === toId) break;
      for (const e of graph.get(cur) || []) {
        const nd = d + e.weight;
        if (nd < (dist.has(e.to) ? dist.get(e.to) : Infinity)) {
          dist.set(e.to, nd);
          prev.set(e.to, [cur, e]);
          frontier.push([e.to, nd]);
        }
      }
    }
    if (!prev.has(toId)) return null;
    const path = [];
    let node = toId;
    while (node !== fromId) {
      const [p, e] = prev.get(node);
      path.unshift({ stationId: node, edge: e });
      node = p;
    }
    return { path, total: dist.get(toId) };
  }

  // Plan the fastest route as a GuidanceJourney { legs: [ { lineId, towards,
  // stops: [ {id, name} ] } ] }, or null when there is no route.
  function planDetailed(stations, lines, fromId, toId, language) {
    if (!fromId || !toId || fromId === toId) return null;
    const byId = indexStations(stations, lines);
    if (!byId.has(fromId) || !byId.has(toId)) return null;
    const graph = buildGraph(lines, byId);
    const result = shortestPath(graph, fromId, toId);
    if (!result) return null;

    const name = (id) => {
      const st = byId.get(id);
      if (!st) return id;
      if (language === 'el' && (st.name_el || st.nameEl)) return st.name_el || st.nameEl;
      return displayName(st);
    };

    const legs = [];
    let curLine = null;
    let curStops = [fromId];
    const flush = () => {
      if (curLine && curStops.length >= 2) {
        legs.push({
          lineId: curLine,
          towards: name(curStops[curStops.length - 1]),
          stops: curStops.map((id) => ({ id, name: name(id) })),
        });
      }
    };
    for (const { stationId, edge } of result.path) {
      if (edge.lineId === null) {
        flush();
        curLine = null;
        curStops = [stationId];
      } else if (edge.lineId !== curLine) {
        if (curLine !== null) {
          flush();
          curStops = [curStops[curStops.length - 1]];
        }
        curLine = edge.lineId;
        curStops.push(stationId);
      } else {
        curStops.push(stationId);
      }
    }
    flush();

    if (!legs.length) return null;
    // `total` is the Dijkstra cost in minutes (distance-based per-hop ride time
    // plus transfer penalties), so it captures the real intercity duration that a
    // flat per-stop estimate would get badly wrong.
    return { legs, totalMinutes: result.total };
  }

  // The first leg's line and the terminal it heads TOWARD, so a caller can pick
  // the correctly-directed next departure at the origin instead of the soonest
  // train in any direction (which may run the opposite way). Derived from the
  // planned route's first two stops against the line's ordered stations. Returns
  // null when there is no route. `terminalName` matches a board group's
  // `destination`, so departures can be filtered by line + direction.
  function firstLegHeading(stations, lines, fromId, toId, language) {
    const route = planDetailed(stations, lines, fromId, toId, language);
    if (!route || !route.legs || !route.legs.length) return null;
    const leg = route.legs[0];
    const stops = leg.stops || [];
    const out = {
      lineId: leg.lineId,
      towards: leg.towards || (stops.length ? stops[stops.length - 1].name : null),
      terminalId: null,
      terminalName: null,
      legCount: route.legs.length,
      totalMinutes: route.totalMinutes != null ? route.totalMinutes : null,
    };
    const line = (lines || []).find((l) => l.id === leg.lineId);
    const ordered = (line && line.stations) || [];
    if (ordered.length && stops.length >= 2) {
      const ord = ordered.map((s) => s.id);
      const i0 = ord.indexOf(stops[0].id);
      const i1 = ord.indexOf(stops[1].id);
      if (i0 >= 0 && i1 >= 0 && i0 !== i1) {
        const term = i1 > i0 ? ordered[ordered.length - 1] : ordered[0];
        out.terminalId = term ? term.id : null;
        out.terminalName = term ? (term.name || term.name_el || term.nameEl || term.id) : null;
      }
    }
    return out;
  }

  return { planDetailed, firstLegHeading, _nameKey: nameKey, _travelTime: travelTime };
});
