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

test('describe() localizes (el/sq/it)', () => {
  assert.match(Panel.describe({ kind: 'getOffNext', nextStation: 'X', isDestination: true }, 'el').headline, /Αποβίβαση/);
  assert.match(Panel.describe({ kind: 'arrived', station: 'X' }, 'sq').headline, /Mbërritët/);
  assert.match(Panel.describe({ kind: 'transfer', atStation: 'X', toLineId: 'M3', towards: 'Y' }, 'it').headline, /Cambia qui/);
});
