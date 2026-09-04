'use strict';
// Static-source guardrails for the map "vehicles" hide/show toggle. The button,
// its handler, its i18n labels and its ic-vehicles icon all shipped, but nothing
// ever surfaced it: index.html parked it in the hidden "JS backward compat"
// block (style="display:none" aria-hidden), so there was no user-facing way to
// declutter the live vehicles. These tests pin the button as a real map control
// so a regression that re-hides it (or drops the wiring) re-fails CI. iOS already
// shows a visible bus toggle on the Map tab; this keeps web at parity.
//
// Run: `node --test` from web-tests/, or `node --test web-tests/` from the repo root.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const read = (p) => fs.readFileSync(path.join(RES, p), 'utf8');

// Pull the single <button ... id="vehiclesToggle" ...> tag out of the markup.
function vehiclesToggleTag(html) {
  const m = /<button\b[^>]*\bid="vehiclesToggle"[^>]*>/.exec(html);
  assert.notEqual(m, null, 'index.html: no <button id="vehiclesToggle"> found');
  return m[0];
}

test('index.html: vehiclesToggle is a surfaced control, not a hidden compat stub', () => {
  const tag = vehiclesToggleTag(read('index.html'));
  // The dead state was: style="display:none" aria-hidden="true" in the
  // "Hidden controls for JS backward compatibility" block. Guard both.
  assert.doesNotMatch(tag, /display\s*:\s*none/i, 'vehiclesToggle must not be display:none');
  assert.doesNotMatch(tag, /aria-hidden\s*=\s*"true"/i, 'vehiclesToggle must not be aria-hidden');
  // It must render as a real map-chrome control with an icon glyph.
  assert.match(tag, /class="[^"]*\bchrome-button\b[^"]*"/, 'vehiclesToggle must use the chrome-button style');
});

test('index.html: vehiclesToggle lives in the map chrome and carries i18n + icon', () => {
  const html = read('index.html');
  // It sits inside the bottom-right map chrome region (mirrors zoom/locate).
  const brStart = html.indexOf('map-chrome--br');
  assert.notEqual(brStart, -1, 'map-chrome--br region not found');
  const brBlock = html.slice(brStart, html.indexOf('</main>', brStart));
  assert.match(brBlock, /id="vehiclesToggle"/, 'vehiclesToggle must sit in the bottom-right map chrome');
  const tag = vehiclesToggleTag(html);
  // i18n hooks so the accessible label localizes (en/el/sq/it), and the bus icon.
  assert.match(tag, /data-i18n-aria="(hide|show)_vehicles"/, 'vehiclesToggle needs a data-i18n-aria label');
  assert.match(brBlock, /<use href="#ic-vehicles"\/>/, 'vehiclesToggle must render the #ic-vehicles glyph');
  // The referenced icon symbol must actually exist in the sprite sheet.
  assert.match(html, /<symbol id="ic-vehicles"/, '#ic-vehicles symbol missing from index.html sprite');
});

test('index.html: the four vehicles i18n labels exist in web-map.js', () => {
  const js = read('web-map.js');
  // en/el/sq/it each define both keys; two languages worth of asserts is enough
  // to catch a wholesale removal of the label set.
  for (const key of ['show_vehicles', 'hide_vehicles']) {
    const count = [...js.matchAll(new RegExp(`\\b${key}\\s*:`, 'g'))].length;
    assert.ok(count >= 4, `${key} should be defined for all 4 locales, found ${count}`);
  }
});

test('web-map.js: the toggle handler is wired and drives the vehicles-hidden flag', () => {
  const js = read('web-map.js');
  // Grab the handler body so we assert on the wiring, not a stray mention.
  const start = js.indexOf('const vehiclesToggle = document.getElementById("vehiclesToggle")');
  assert.notEqual(start, -1, 'vehiclesToggle handler not found in web-map.js');
  const block = js.slice(start, start + 1600);
  assert.match(block, /addEventListener\("click"/, 'vehiclesToggle must have a click handler');
  assert.match(block, /window\.__syrmosVehiclesHidden\s*=\s*vehiclesHidden/, 'handler must set __syrmosVehiclesHidden');
  // Active-state class must match the CSS rule that tints the button.
  assert.match(block, /classList\.toggle\("chrome-button--active"/, 'handler must toggle chrome-button--active');
  // Hiding clears the live train + airport-bus layers; showing replays them.
  assert.match(block, /liveTrainLayer\.clearLayers\(\)/, 'hiding must clear live trains');
  assert.match(block, /liveBusLayer\.clearLayers\(\)/, 'hiding must clear live buses');
  assert.match(block, /renderAirportBusVehicles\(lastBusVehicles\)/, 'showing must replay airport buses');
});

test('web-map.css: chrome-button--active tints the icon (visible pressed state)', () => {
  const css = read('web-map.css');
  assert.match(css, /\.chrome-button--active\b/, 'no .chrome-button--active rule to show the pressed state');
  const m = /\.chrome-button--active\s*\{[^}]*\}/.exec(css);
  assert.notEqual(m, null, '.chrome-button--active rule body not found');
  assert.match(css, /\.chrome-button--active[^{]*\{[^}]*var\(--sy-accent\)/, 'active state must use the accent color');
});
