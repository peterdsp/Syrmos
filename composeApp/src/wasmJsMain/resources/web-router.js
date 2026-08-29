/*
 * Syrmos web URL <-> workspace-state router (Command Panel redesign, Phase 0).
 *
 * The redesign replaces the single endless left panel with five deep-linkable
 * task workspaces around the permanent map canvas (Now, Plan, Explore,
 * Departures, Tickets). This module is the foundation: it maps the browser URL
 * to a workspace-state object and back, and drives Back/reload. It is additive
 * and deliberately touches no DOM yet - Phase 1 (nav + panel state) subscribes
 * to `window.syrmosRouter` and renders the workspaces. See DESIGN_SYSTEM.md
 * §19.3 "Web amendment (v1.2.11)" and §19.4.
 *
 * URL model (dossier §03):
 *   /now?station=ATH
 *   /plan?from=SYNTAGMA&to=AIRPORT            /plan/journey/<id>
 *   /explore/discover?region=athens
 *   /explore/network?region=athens&mode=metro
 *   /line/M3?direction=airport&stop=SYN
 *   /station/PIRAEUS
 *   /departures?station=PIRAEUS
 *   /tickets?from=SYNTAGMA&to=AIRPORT&rider=adult
 *
 * Pure `parseRoute` / `buildRoute` round-trip so state survives Back and reload.
 * No personal data or precise location is ever placed in the URL.
 *
 * Browser: exposes `window.SyrmosRouter` (the module) and a live singleton
 * `window.syrmosRouter`. Node: `module.exports` + a self-test (run
 * `node web-router.js`), which the browser never executes.
 */
(function (global) {
  'use strict';

  var WORKSPACES = ['now', 'plan', 'explore', 'departures', 'tickets'];

  // Which query keys are meaningful per entry point, in canonical order. Every
  // dynamic identifier or selection travels as a query parameter so each route
  // is a single static entry point on GitHub Pages (/plan/, /line/, ...), never
  // an unbounded path like /line/M3 that would need a directory per line.
  var QUERY_KEYS = {
    now: ['station'],
    plan: ['from', 'to', 'when'],
    explore: ['view', 'region', 'mode'],
    departures: ['station'],
    tickets: ['from', 'to', 'rider'],
    line: ['id', 'direction', 'stop'],
    station: ['id'],
  };

  function trimSlashes(p) {
    return String(p || '').replace(/\/+$/, '').replace(/^\/+/, '');
  }

  function readQuery(search) {
    var out = {};
    var qs = new URLSearchParams(search || '');
    qs.forEach(function (v, k) { if (v !== '') out[k] = v; });
    return out;
  }

  function put(state, key, val) {
    if (val !== undefined && val !== null && val !== '') state[key] = val;
  }

  /** Pure: parse a location (pathname + search) into a workspace-state object. */
  function parseRoute(pathname, search) {
    var seg = trimSlashes(pathname).split('/').filter(Boolean);
    var q = readQuery(search);
    var head = (seg[0] || 'now').toLowerCase();

    if (head === 'line') {
      var state = { workspace: 'explore', line: q.id || null };
      put(state, 'direction', q.direction);
      put(state, 'stop', q.stop);
      return state;
    }
    if (head === 'station') {
      return { workspace: 'now', view: 'station', station: q.id || null };
    }
    if (head === 'explore') {
      var view = (q.view || 'discover').toLowerCase();
      if (view !== 'discover' && view !== 'network') view = 'discover';
      var es = { workspace: 'explore', mode: view };
      put(es, 'region', q.region);
      if (view === 'network') put(es, 'netMode', q.mode);
      return es;
    }
    if (head === 'plan') {
      if (q.journey) return { workspace: 'plan', journey: q.journey };
      var ps = { workspace: 'plan' };
      put(ps, 'from', q.from); put(ps, 'to', q.to); put(ps, 'when', q.when);
      return ps;
    }
    if (head === 'departures') {
      var ds = { workspace: 'departures' };
      put(ds, 'station', q.station);
      return ds;
    }
    if (head === 'tickets') {
      var ts = { workspace: 'tickets' };
      put(ts, 'from', q.from); put(ts, 'to', q.to); put(ts, 'rider', q.rider);
      return ts;
    }
    // Default / unknown -> Now.
    var ns = { workspace: 'now' };
    put(ns, 'station', q.station);
    return ns;
  }

  /** Pure: build a canonical URL string ("/path?query") from a state object. */
  function buildRoute(state) {
    state = state || {};
    var ws = WORKSPACES.indexOf(state.workspace) >= 0 ? state.workspace : 'now';
    var path;
    var keys;

    if (ws === 'explore' && state.line) {
      path = '/line/';
      keys = QUERY_KEYS.line;
    } else if (ws === 'now' && state.view === 'station' && state.station) {
      path = '/station/';
      keys = QUERY_KEYS.station;
    } else if (ws === 'plan' && state.journey) {
      path = '/plan/';
      keys = ['journey'];
    } else if (ws === 'now') {
      path = '/'; // the root document IS the Now workspace
      keys = QUERY_KEYS.now;
    } else {
      path = '/' + ws + '/';
      keys = QUERY_KEYS[ws] || [];
    }

    var qs = new URLSearchParams();
    keys.forEach(function (k) {
      var v;
      if (path === '/line/' && k === 'id') v = state.line;
      else if (path === '/station/' && k === 'id') v = state.station;
      else if (ws === 'explore' && k === 'view') v = (state.mode === 'network') ? 'network' : '';
      else if (ws === 'explore' && k === 'mode') v = state.netMode;
      else v = state[k];
      if (v !== undefined && v !== null && v !== '') qs.set(k, String(v));
    });
    var query = qs.toString();
    return query ? path + '?' + query : path;
  }

  /** Browser wiring: reflect state to the URL and notify subscribers on Back. */
  function createRouter(win) {
    win = win || global;
    var listeners = [];
    function current() { return parseRoute(win.location.pathname, win.location.search); }
    function emit(st) {
      for (var i = 0; i < listeners.length; i++) {
        try { listeners[i](st); } catch (e) { /* a subscriber must not break routing */ }
      }
    }
    function navigate(state, opts) {
      var url = buildRoute(state);
      var replace = !!(opts && opts.replace);
      if (win.history && win.history.pushState) {
        win.history[replace ? 'replaceState' : 'pushState'](state, '', url);
      }
      emit(current());
    }
    if (win.addEventListener) {
      win.addEventListener('popstate', function () { emit(current()); });
    }
    return {
      current: current,
      navigate: navigate,
      subscribe: function (fn) {
        listeners.push(fn);
        return function () { var i = listeners.indexOf(fn); if (i >= 0) listeners.splice(i, 1); };
      },
    };
  }

  var api = { WORKSPACES: WORKSPACES, parseRoute: parseRoute, buildRoute: buildRoute, create: createRouter };
  global.SyrmosRouter = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  // Live singleton in the browser (tracks URL state; no DOM changes yet).
  if (global.document && !global.syrmosRouter) {
    try { global.syrmosRouter = createRouter(global); } catch (e) { /* non-fatal */ }
  }
})(typeof window !== 'undefined' ? window : globalThis);

/* ------------------------------- self-test -------------------------------- *
 * Runs only under `node web-router.js`; the browser never enters this block. */
if (typeof require !== 'undefined' && typeof module !== 'undefined' && require.main === module) {
  var R = module.exports;
  var assert = require('assert');
  var eq = function (a, b, m) { assert.deepStrictEqual(a, b, m); };

  // parse: workspaces are directory entry points, dynamic ids are query params
  eq(R.parseRoute('/', ''), { workspace: 'now' });
  eq(R.parseRoute('/now/', '?station=ATH'), { workspace: 'now', station: 'ATH' });
  eq(R.parseRoute('/', '?station=ATH'), { workspace: 'now', station: 'ATH' });
  eq(R.parseRoute('/plan/', '?from=SYNTAGMA&to=AIRPORT'), { workspace: 'plan', from: 'SYNTAGMA', to: 'AIRPORT' });
  eq(R.parseRoute('/plan/', '?journey=abc'), { workspace: 'plan', journey: 'abc' });
  eq(R.parseRoute('/explore/', '?view=network&region=athens&mode=metro'), { workspace: 'explore', mode: 'network', region: 'athens', netMode: 'metro' });
  eq(R.parseRoute('/explore/', ''), { workspace: 'explore', mode: 'discover' });
  eq(R.parseRoute('/line/', '?id=M3&direction=airport&stop=SYN'), { workspace: 'explore', line: 'M3', direction: 'airport', stop: 'SYN' });
  eq(R.parseRoute('/station/', '?id=PIRAEUS'), { workspace: 'now', view: 'station', station: 'PIRAEUS' });
  eq(R.parseRoute('/departures/', '?station=PIRAEUS'), { workspace: 'departures', station: 'PIRAEUS' });
  eq(R.parseRoute('/tickets/', '?from=SYNTAGMA&to=AIRPORT&rider=adult'), { workspace: 'tickets', from: 'SYNTAGMA', to: 'AIRPORT', rider: 'adult' });
  // unknown head falls back to Now
  eq(R.parseRoute('/bogus', ''), { workspace: 'now' });

  // build (canonical URLs)
  eq(R.buildRoute({ workspace: 'now' }), '/');
  eq(R.buildRoute({ workspace: 'now', station: 'ATH' }), '/?station=ATH');
  eq(R.buildRoute({ workspace: 'plan', from: 'SYNTAGMA', to: 'AIRPORT' }), '/plan/?from=SYNTAGMA&to=AIRPORT');
  eq(R.buildRoute({ workspace: 'plan', journey: 'abc' }), '/plan/?journey=abc');
  eq(R.buildRoute({ workspace: 'explore', mode: 'network', region: 'athens', netMode: 'metro' }), '/explore/?view=network&region=athens&mode=metro');
  eq(R.buildRoute({ workspace: 'explore', line: 'M3', direction: 'airport', stop: 'SYN' }), '/line/?id=M3&direction=airport&stop=SYN');
  eq(R.buildRoute({ workspace: 'now', view: 'station', station: 'PIRAEUS' }), '/station/?id=PIRAEUS');
  eq(R.buildRoute({ workspace: 'departures', station: 'PIRAEUS' }), '/departures/?station=PIRAEUS');
  eq(R.buildRoute({ workspace: 'tickets', from: 'SYNTAGMA', to: 'AIRPORT', rider: 'adult' }), '/tickets/?from=SYNTAGMA&to=AIRPORT&rider=adult');
  // unknown workspace falls back to Now (root)
  eq(R.buildRoute({ workspace: 'zzz' }), '/');

  // round-trip: parse(build(x)) === x for representative states
  [
    { workspace: 'now' },
    { workspace: 'now', station: 'ATH' },
    { workspace: 'plan', from: 'SYNTAGMA', to: 'AIRPORT' },
    { workspace: 'plan', journey: 'abc' },
    { workspace: 'explore', mode: 'network', region: 'athens', netMode: 'metro' },
    { workspace: 'explore', mode: 'discover', region: 'athens' },
    { workspace: 'explore', line: 'M3', direction: 'airport', stop: 'SYN' },
    { workspace: 'now', view: 'station', station: 'PIRAEUS' },
    { workspace: 'departures', station: 'PIRAEUS' },
    { workspace: 'tickets', from: 'SYNTAGMA', to: 'AIRPORT', rider: 'adult' },
  ].forEach(function (s) {
    var url = R.buildRoute(s);
    var i = url.indexOf('?');
    var path = i < 0 ? url : url.slice(0, i);
    var search = i < 0 ? '' : url.slice(i);
    eq(R.parseRoute(path, search), s, 'round-trip: ' + url);
  });

  console.log('web-router self-test: OK (' + R.WORKSPACES.length + ' workspaces)');
}
