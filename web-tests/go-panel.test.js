'use strict';
// Unit-tests the pure text logic of the web GO panel (describe()); DOM rendering
// is verified separately in a browser.
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const Panel = require(path.join(
  __dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources', 'web-go-panel.js'
));

test('describe() renders each guidance kind (en)', () => {
  assert.match(Panel.describe({ kind: 'board', lineId: 'M2', towards: 'Omonia', stopsRemaining: 2, nextStation: 'Panepistimio' }, 'en').headline, /Board M2/);
  assert.match(Panel.describe({ kind: 'ride', lineId: 'M2', towards: 'Omonia', stopsRemaining: 1, nextStation: 'Omonia' }, 'en').headline, /Stay on M2/);
  const off = Panel.describe({ kind: 'getOffNext', nextStation: 'Syntagma', isDestination: false, transferTo: 'M3' }, 'en');
  assert.match(off.headline, /Get off next/);
  assert.match(off.detail, /change to M3/);
  assert.match(Panel.describe({ kind: 'transfer', atStation: 'Syntagma', toLineId: 'M3', towards: 'Airport' }, 'en').headline, /Change here/);
  assert.match(Panel.describe({ kind: 'arrived', station: 'Airport' }, 'en').detail, /Airport/);
});

test('describe() names the alight point, not the next stop, in the ride/board sub (finding 3)', () => {
  // The sub-line counts stops to THIS leg's alight and names it, so it can never be
  // read as the S05 "intermediate stops" number. Plural, singular, and fallback.
  const board = Panel.describe({ kind: 'board', lineId: 'M2', towards: 'Elliniko', stopsRemaining: 4, nextStation: 'Panepistimio' }, 'en', 'Syntagma');
  assert.equal(board.sub, '4 stops to Syntagma');
  const ride = Panel.describe({ kind: 'ride', lineId: 'M2', towards: 'Elliniko', stopsRemaining: 1, nextStation: 'Syntagma' }, 'en', 'Syntagma');
  assert.equal(ride.sub, '1 stop to Syntagma');
  // No alight supplied -> falls back to the next station rather than showing undefined.
  const fb = Panel.describe({ kind: 'ride', lineId: 'M2', towards: 'Elliniko', stopsRemaining: 3, nextStation: 'Omonia' }, 'en');
  assert.equal(fb.sub, '3 stops to Omonia');
  // Localized.
  assert.equal(Panel.describe({ kind: 'ride', lineId: 'M2', towards: 'X', stopsRemaining: 2, nextStation: 'Y' }, 'el', 'Σύνταγμα').sub, '2 στάσεις μέχρι Σύνταγμα');
  assert.equal(Panel.describe({ kind: 'ride', lineId: 'M2', towards: 'X', stopsRemaining: 2, nextStation: 'Y' }, 'sq', 'Syntagma').sub, '2 ndalesa deri te Syntagma');
  assert.equal(Panel.describe({ kind: 'ride', lineId: 'M2', towards: 'X', stopsRemaining: 2, nextStation: 'Y' }, 'it', 'Syntagma').sub, '2 fermate fino a Syntagma');
});

test('describe() localizes (el/sq/it)', () => {
  assert.match(Panel.describe({ kind: 'getOffNext', nextStation: 'X', isDestination: true }, 'el').headline, /Αποβίβαση/);
  assert.match(Panel.describe({ kind: 'arrived', station: 'X' }, 'sq').headline, /Mbërritët/);
  assert.match(Panel.describe({ kind: 'transfer', atStation: 'X', toLineId: 'M3', towards: 'Y' }, 'it').headline, /Cambia qui/);
});
