/* =========================================================================
 * web-nav.js  ---  Workspace nav-rail controller (Phase 1)
 *
 * Turns the left nav rail into a URL-addressable workspace switcher on top of
 * the Phase 0 router (window.syrmosRouter, see web-router.js). Clicking a
 * workspace button navigates the router (which updates the URL and history);
 * the router's emitted state drives the active highlight and an optional
 * per-workspace "frame" hook (frame the map, scroll the panel, focus search).
 *
 * The five workspace roots are Now / Plan / Explore / Departures / Tickets
 * (DESIGN_SYSTEM.md web amendment v1.2.11). Buttons whose data-nav is NOT a
 * workspace (e.g. the footer "more") are treated as plain utilities: they run
 * their hook but never change the workspace or the active highlight.
 *
 * This module is deliberately DOM-light and Leaflet-free so it unit-tests
 * under node with tiny stubs (run `node web-nav.js`), the same pattern as
 * web-router.js. web-map.js supplies the real map/panel hooks at runtime.
 * ========================================================================= */
(function (global) {
  'use strict';

  var WORKSPACES =
    (global.SyrmosRouter && global.SyrmosRouter.WORKSPACES) ||
    ['now', 'plan', 'explore', 'departures', 'tickets'];

  function isWorkspace(id) {
    return WORKSPACES.indexOf(id) >= 0;
  }

  /* Pure: which workspace nav item should be active for a router state.
   * Unknown / missing workspaces fall back to Now, matching the router. */
  function activeWorkspace(state) {
    var ws = state && state.workspace;
    return isWorkspace(ws) ? ws : 'now';
  }

  /* Wire a nav-rail element to a router.
   *   navEl  - element containing [data-nav] buttons (workspaces + utilities)
   *   router - { current(), navigate(state), subscribe(fn) } from web-router.js
   *   hooks  - { <workspace|utility>: function(state) } run when that id
   *            becomes active (workspace) or is clicked (utility)
   * Returns an unsubscribe function. Safe to call with a missing nav/router. */
  function wire(navEl, router, hooks) {
    hooks = hooks || {};
    if (!navEl || !router) return function () {};

    var buttons = navEl.querySelectorAll('[data-nav]');

    function setActive(ws) {
      for (var i = 0; i < buttons.length; i++) {
        var btn = buttons[i];
        // Only workspace buttons carry the active state; utilities never do.
        var isWs = isWorkspace(btn.getAttribute('data-nav'));
        var on = isWs && btn.getAttribute('data-nav') === ws;
        if (btn.classList) btn.classList.toggle('nav-item--active', on);
        if (btn.setAttribute) btn.setAttribute('aria-current', on ? 'page' : 'false');
      }
    }

    function apply(state) {
      var ws = activeWorkspace(state);
      setActive(ws);
      var hook = hooks[ws];
      if (typeof hook === 'function') {
        try { hook(state); } catch (e) { /* a hook must not break routing */ }
      }
    }

    for (var i = 0; i < buttons.length; i++) {
      bindClick(buttons[i]);
    }
    function bindClick(btn) {
      btn.addEventListener('click', function () {
        var id = btn.getAttribute('data-nav');
        if (isWorkspace(id)) {
          // Router emits -> apply() sets the highlight and runs the hook, so
          // there is exactly one code path that updates the UI.
          router.navigate({ workspace: id });
        } else if (typeof hooks[id] === 'function') {
          try { hooks[id](); } catch (e) { /* utility hook is best-effort */ }
        }
      });
    }

    var unsubscribe = router.subscribe(apply);
    apply(router.current()); // initialise the rail from the current URL
    return unsubscribe;
  }

  var api = {
    WORKSPACES: WORKSPACES,
    isWorkspace: isWorkspace,
    activeWorkspace: activeWorkspace,
    wire: wire,
  };
  global.SyrmosNav = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(typeof window !== 'undefined' ? window : globalThis);

/* ------------------------------- self-test -------------------------------- *
 * Runs only under `node web-nav.js`; the browser never enters this block.
 * Uses tiny DOM/router stubs so the wiring is verified with no browser. */
if (typeof require !== 'undefined' && typeof module !== 'undefined' && require.main === module) {
  var N = module.exports;
  var assert = require('assert');

  // --- pure activeWorkspace ---
  assert.strictEqual(N.activeWorkspace({ workspace: 'plan' }), 'plan');
  assert.strictEqual(N.activeWorkspace({ workspace: 'tickets' }), 'tickets');
  assert.strictEqual(N.activeWorkspace({ workspace: 'bogus' }), 'now', 'unknown -> now');
  assert.strictEqual(N.activeWorkspace({}), 'now', 'missing -> now');
  assert.strictEqual(N.isWorkspace('more'), false, 'more is a utility, not a workspace');

  // --- DOM + router stubs ---
  function fakeButton(nav) {
    var classes = {};
    var attrs = { 'data-nav': nav };
    var handlers = [];
    return {
      _classes: classes,
      _handlers: handlers,
      getAttribute: function (k) { return attrs[k]; },
      setAttribute: function (k, v) { attrs[k] = v; },
      classList: {
        toggle: function (c, on) { classes[c] = !!on; },
        contains: function (c) { return !!classes[c]; },
      },
      addEventListener: function (_evt, fn) { handlers.push(fn); },
      click: function () { handlers.forEach(function (h) { h(); }); },
    };
  }
  function fakeNav(navIds) {
    var buttons = navIds.map(fakeButton);
    return {
      buttons: buttons,
      querySelectorAll: function () { return buttons; },
    };
  }
  function fakeRouter(initial) {
    var state = initial;
    var subs = [];
    return {
      _state: function () { return state; },
      current: function () { return state; },
      navigate: function (s) { state = s; subs.forEach(function (fn) { fn(state); }); },
      subscribe: function (fn) { subs.push(fn); return function () {}; },
    };
  }
  function activeIds(nav) {
    return nav.buttons
      .filter(function (b) { return b.classList.contains('nav-item--active'); })
      .map(function (b) { return b.getAttribute('data-nav'); });
  }

  var nav = fakeNav(['now', 'plan', 'explore', 'departures', 'tickets', 'more']);
  var router = fakeRouter({ workspace: 'now' });
  var framed = [];
  N.wire(nav, router, {
    now: function () { framed.push('now'); },
    plan: function () { framed.push('plan'); },
    explore: function () { framed.push('explore'); },
    departures: function () { framed.push('departures'); },
    tickets: function () { framed.push('tickets'); },
    more: function () { framed.push('more'); },
  });

  // initialises from the current URL (now)
  assert.deepStrictEqual(activeIds(nav), ['now'], 'initial active = now');
  assert.deepStrictEqual(framed, ['now'], 'now hook ran on init');

  // clicking a workspace navigates the router and moves the highlight
  nav.buttons[1].click(); // plan
  assert.strictEqual(router._state().workspace, 'plan', 'click navigates router to plan');
  assert.deepStrictEqual(activeIds(nav), ['plan'], 'plan is the only active item');
  assert.strictEqual(framed[framed.length - 1], 'plan', 'plan hook ran');

  nav.buttons[4].click(); // tickets
  assert.strictEqual(router._state().workspace, 'tickets');
  assert.deepStrictEqual(activeIds(nav), ['tickets']);

  // clicking the "more" utility runs its hook but changes neither URL nor active
  nav.buttons[5].click(); // more
  assert.strictEqual(router._state().workspace, 'tickets', 'utility does not change workspace');
  assert.deepStrictEqual(activeIds(nav), ['tickets'], 'utility does not steal the highlight');
  assert.strictEqual(framed[framed.length - 1], 'more', 'more utility hook ran');

  // popstate / external router change is reflected in the rail
  router.navigate({ workspace: 'explore' });
  assert.deepStrictEqual(activeIds(nav), ['explore'], 'external nav updates the rail');

  console.log('web-nav self-test: OK (' + N.WORKSPACES.length + ' workspaces)');
}
