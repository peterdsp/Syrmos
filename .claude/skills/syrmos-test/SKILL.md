---
name: syrmos-test
description: >-
  Write tests for a Syrmos change and actually run them, then runtime-verify the real
  surface, before anything is called done. Use this skill whenever the task involves
  proving Syrmos works: "write tests for this", "add coverage", "test this change", "run
  the tests", "verify it works", "does it actually behave", "check it on the simulator /
  emulator / live", or right after any Syrmos implementation. The user's hard rule is tests
  for everything, run them, and never claim "done" on the strength of "it compiles". This
  skill covers what to test, where tests live per client, the exact run commands, and how
  to reach and honestly report a real runtime-verification tier across all four surfaces.
---

# Syrmos testing and verification

Two non-negotiable user rules drive this skill:

1. **Tests for everything, and run them.** Every change gets a test, and you execute it and
   show it passing. Work is not done without that.
2. **Runtime-verify, do not stop at compile.** "It builds" and "CI is green" are NOT
   "it works". Observe the actual behavior where you can, and say honestly which tier you
   reached.

## What to test (match the test to the change)

- **Pure logic** (grouping, ranking, projection, feasibility, formatting, parsing): unit
  test the transform directly, including the edge cases (empty input, boundary values, the
  soonest-vs-later sort, case/whitespace folding). This is the highest-value, cheapest test
  and the shared transforms are built to be tested this way.
- **Data invariants** (seed, line status): assert the contract (coordinates inside Greece,
  unique ids, known enum values). See `web-tests/seed-contract.test.js` for the shape.
- **Regression guards** for bug classes that have shipped: encode the fix as a static-source
  or behavioral guard so it cannot come back (see `web-tests/guardrails.test.js`: the
  Athens-time rule, the live-train marker position:absolute rule).
- **UI behavior**: cannot be fully unit-tested; this is what runtime verification covers.

Reuse the fixtures in `fixtures/` (freshness, go-guidance, journeys, reminders,
station-board) rather than hand-rolling test data; they are shared across clients.

## Where tests live and how to run them

Exact environment setup (JDK17 path, `ANDROID_SDK_ROOT`, `DEVELOPER_DIR`, the stale
`local.properties` trap) and the full command list are in
`../syrmos-feature-loop/references/build-and-test.md`. The essentials:

- **KMP / Android logic** -> `commonTest`, run with:
  `./gradlew :core:<module>:testDebugUnitTest --tests "fully.qualified.TestName"`
  (core modules have NO jvm target; `jvmTest` / `test --tests` fail; only
  `testDebugUnitTest` takes `--tests`). Results XML under `<module>/build/test-results/`.
- **iOS** -> XCTest/Swift Testing in `iosAppTests/`, run with:
  `xcodebuild test -scheme "Syrmos - Athens Rail Times" -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:iosAppTests/SomeTests`
  (the test-host link is slow, minutes; the log looks idle until the end). New test files
  need pbxproj registration unless added to an existing test file.
- **Web** -> `web-tests/*.test.js` with the built-in node runner:
  `node --test web-tests/*.test.js` (use the GLOB, not the directory). Tests extract the
  real functions from the UMD files and harness them.
- **Server** -> the `ops/syrmos-api` unittests. CI does NOT gate these (they run in
  `seed-refresh.yml`), so a server test failure will not block a PR: run them yourself.

Run the tests and paste the real pass/fail result into your report. Do not paraphrase a
run you did not do.

## Runtime verification (the part people skip)

After the tests pass, reach the highest tier you can for each affected surface and STATE
which tier you reached:

- **runtime-verified**: you saw it behave (simulator, emulator, live browser, curled the
  live endpoint and watched the value change).
- **CI-verified**: build + lint + tests green, behavior not observed.
- **untested**.

All four surfaces are runtime-verifiable on this machine (iOS simulator, Android emulator,
local web serve + browser, direct `curl` to `api-syrmos.peterdsp.dev`; only Overpass is
blocked). Try the real run first; fall back to CI-verified only when a local run genuinely
fails, and say so. The per-surface recipes and the known tooling limits (no Chromium, no
Service Worker install in the in-app browser, the empty web station dropdowns on static
serve) are in `../syrmos-feature-loop/references/verify-surfaces.md`.

## Reporting

Be precise and honest. For each surface: the test command you ran and its result, and the
verification tier with one line of evidence ("saw Line 3 at Piraeus show +136 on the sim",
"curled /api/oasa-airport-buses, got live ETAs"). If tooling was flaky and you could not
observe it, say that rather than implying you saw it. Autonomy does not license skipping
verification; it means not waiting for a human to greenlight each step.
