'use strict';
// Cross-client parity guard for the station-complex board.
//
// The board ships three times: `web-station-board.js`, the Swift
// `StationComplexBoard` and the Kotlin `StationComplexBoard`. The web and Swift
// suites read the golden fixture directly. Kotlin's commonTest cannot read
// repository files, so its copy of the cases is inlined — which is exactly the
// kind of copy that rots.
//
// This test fails when a fixture case has no counterpart in the Kotlin or Swift
// suite, so adding a case on one platform forces the others to answer for it.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..');
const fixture = JSON.parse(
  fs.readFileSync(path.join(ROOT, 'fixtures/station-board/athens-all-directions.json'), 'utf8'),
);
const kotlin = fs.readFileSync(
  path.join(ROOT, 'core/domain/src/commonTest/kotlin/com/syrmos/core/domain/station/StationComplexBoardTest.kt'),
  'utf8',
);
const swift = fs.readFileSync(
  path.join(ROOT, 'iosApp/iosAppTests/StationComplexBoardTests.swift'),
  'utf8',
);

test('every fixture case is claimed by the Kotlin suite', () => {
  for (const c of fixture.cases) {
    assert.ok(
      kotlin.includes(`// fixture case: ${c.id}`),
      `StationComplexBoardTest.kt has no test marked "// fixture case: ${c.id}"`,
    );
  }
});

test('the Kotlin suite claims no case the fixture does not define', () => {
  const claimed = [...kotlin.matchAll(/\/\/ fixture case: (\S+)/g)].map((m) => m[1]);
  const known = new Set(fixture.cases.map((c) => c.id));
  for (const id of claimed) {
    assert.ok(known.has(id), `StationComplexBoardTest.kt claims unknown fixture case "${id}"`);
  }
  assert.equal(claimed.length, fixture.cases.length);
});

test('the Swift suite drives the fixture file itself', () => {
  // Swift can read the repository, so it must not inline a copy: it loads the
  // same JSON and iterates every case.
  assert.match(swift, /fixtures\/station-board\/athens-all-directions\.json/);
  assert.match(swift, /for testCase in fixture\.cases/);
});

test('the fixture still covers the acceptance cases it was written for', () => {
  // A case silently deleted is the same failure as a case never written.
  const required = [
    'every_direction_is_represented',
    'frequent_line_cannot_crowd_out_a_rare_one',
    'one_source_cannot_suppress_another_mode',
    'branch_destinations_stay_distinct',
    'duplicates_collapse_same_minute_trains_survive',
    'arrivals_and_non_pickup_stops_are_not_departures',
    'a_cancellation_is_status_never_the_recommendation',
    'partial_coverage_cannot_look_complete',
    'no_departure_in_window_shows_the_next_verified_one',
    'a_live_update_moves_its_own_trip_only',
  ];
  const ids = new Set(fixture.cases.map((c) => c.id));
  for (const id of required) assert.ok(ids.has(id), `fixture lost the case "${id}"`);
});
