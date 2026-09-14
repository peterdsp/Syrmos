'use strict';
// Source/status chips ("Scheduled", "Live", "Estimated", "Offline snapshot")
// must be exactly as wide as their label. On departure rows the chip sits in the
// row's text column, which stretches its children, so an auto-width chip was
// drawn as a long full-width bar. Static guardrail over the served stylesheet
// and the departure-row markup, in the style of the other web-tests.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const RES = path.join(__dirname, '..', 'composeApp', 'src', 'wasmJsMain', 'resources');
const css = fs.readFileSync(path.join(RES, 'web-map.css'), 'utf8');
const js = fs.readFileSync(path.join(RES, 'web-map.js'), 'utf8');

const block = (selector) => {
  const m = css.match(new RegExp(`(?:^|\\n)${selector.replace(/[.]/g, '\\.')}\\s*\\{([^}]*)\\}`));
  assert.ok(m, `${selector} rule not found`);
  return m[1];
};

test('source chips size to their label instead of stretching', () => {
  const chip = block('.src-chip');
  assert.match(chip, /width:\s*fit-content/, '.src-chip needs a content width so a stretching column cannot widen it');
  assert.match(chip, /max-width:\s*100%/, 'a long label must never overflow its container');
  // The atomic-label rule still holds (never wrap or compress the word).
  assert.match(chip, /white-space:\s*nowrap/);
  assert.match(chip, /flex-shrink:\s*0/);
});

test('the departure row still renders the chip inside its text column', () => {
  // This is the stretching context the width rule protects against.
  assert.match(js, /<div class="departure-card__times">\$\{timesHtml\}\$\{moreHtml\}<\/div>\s*\n\s*\$\{sourceChip\}/,
    'departure rows render the source chip directly in the text column');
});
