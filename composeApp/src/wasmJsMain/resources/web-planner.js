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

    // Same-line edges between consecutive stops.
    for (const line of lines || []) {
      const w = travelTime(line.type);
      const ordered = line.stations || [];
      for (let i = 0; i < ordered.length - 1; i++) {
        const a = ordered[i].id, b = ordered[i + 1].id;
        add(a, b, line.id, w);
        add(b, a, line.id, w);
      }
    }

    // Transfer edges between co-located stations (same folded name).
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
    return { legs };
  }

  return { planDetailed, _nameKey: nameKey, _travelTime: travelTime };
});
