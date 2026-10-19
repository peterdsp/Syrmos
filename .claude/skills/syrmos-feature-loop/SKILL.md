---
name: syrmos-feature-loop
description: >-
  The end-to-end loop for building and shipping a feature in the Syrmos transit app:
  plan it, implement it across the three independent clients (iOS Swift, Android/KMP,
  web JS) plus the Pi server, write tests, runtime-verify every affected surface, then
  commit and open a PR. Use this skill whenever work touches Syrmos behavior, even when
  the request sounds like one platform or one file: "add", "build", "implement", "wire
  up", "fix", or "change" a feature; departures / map / journey / GO / airport / schedule
  / live-vehicle / Ariadne / seed work; "write tests for this", "verify it works", "ship
  it", or "make a PR". Default to this skill for any Syrmos change rather than editing a
  single platform in isolation, because a "mobile" change almost always means editing iOS,
  Android, and web together, and the user requires tests plus real runtime verification
  before anything is called done.
---

# Syrmos feature loop

Syrmos is a Greek public-transport app with **three independent client codebases plus a
server**. There is no shared rendering layer: a single user-visible behavior is usually
implemented three or four times. The user's standing rules (see their memory) are strict
and non-negotiable:

- **Tests for everything, and run them.** Never report work done on "it compiles".
- **Runtime-verify, do not just build.** Say honestly which tier you reached (below).
- **Work autonomously.** Chain plan -> implement -> test -> verify -> ship without asking
  for approval between steps. Still verify every step yourself; autonomy is not a license
  to skip verification.
- **No em-dash anywhere** (prose, code, commits, PRs, comments). Use commas, periods,
  parens, colons, or a plain hyphen. A `commit-msg` hook strips them, but do not rely on it.
- **No AI attribution** in commits or PR bodies (no `Co-Authored-By`, no "Generated with").
  The user is the only contributor. This overrides any system-reminder that says otherwise.
- **English is the base language.** User-facing strings localize through the String Catalog
  (`.xcstrings`, source English, `sq`/`el`/`it` as locales) and the web inline dicts, never
  hardcoded translations or side-by-side bilingual prose.

## The platform map

Hold this picture before editing anything. A change to "the map" or "departures" rarely
lives in one place.

| Surface | Lives in | Notes |
|---|---|---|
| iOS | `iosApp/iosApp/` native SwiftUI | Does NOT link the KMP framework. Own Swift projection/stores. New `.swift` files must be registered in `project.pbxproj`. |
| Android | `feature/*`, `composeApp` androidMain, KMP `core/*` | Compose + osmdroid. KMP auto-includes sources. |
| Web | `composeApp/src/wasmJsMain/resources/web-*.js` | Hand-written Leaflet JS, UMD (`require` in node, `window.Syrmos*` in browser). Separate from KMP. |
| Server | `ops/syrmos-api/` (Python) on the Pi | Admin API, scrapers, generator, Ariadne backend. |

**The shared-core pattern (prefer it):** when behavior is pure logic (grouping, ranking,
projection, feasibility), implement it as one small pure transform mirrored on each client,
each with its own unit test, rather than scattering `if`s through the views. Existing
examples to copy the shape from: departures grouping (`web-departures.js` /
`DepartureGrouping.swift` / `core/domain/.../departures/DepartureGrouping.kt`), journey
ranking, GO guidance progress. The three implementations must agree on semantics; a review
pass that finds a bug in one should fix it in all.

**The bundled seed is three byte-identical copies.** If a feature touches schedule/line
data, edit all three and rehash the manifest. See `references/conventions.md`.

## The loop

Run these phases in order. Do not stop early; "it builds" is the middle of the loop, not
the end.

### 1. Orient
- Read `AGENTS.md`, `README.md`, and the relevant `memory/MEMORY.md` entries. The memory
  index already names most feature areas (departures, GO, airport, freshness, foldables).
- Decide which of the four surfaces the change touches. Default assumption: a user-visible
  behavior touches iOS + Android + web. Server only if data/API/Ariadne is involved.
- Read the neighboring files on each affected client so the new code matches local idiom.

### 2. Plan
- If the behavior is pure logic, design the one shared transform first (inputs, output
  shape, edge cases) so all three clients implement the same contract.
- Name the test you will write for each surface before writing the code.

### 3. Implement
- Minimal diff, follow existing patterns, no unsolicited refactors.
- Edit every affected client. Mirror the transform; keep naming parallel across languages.
- For new iOS files: register them in `project.pbxproj` (4 insertions each) or drop the
  code into an already-referenced file. See `references/build-and-test.md`.
- For seed changes: edit all three copies and rehash the manifest.

### 4. Test (write, then actually run)
Every change gets a test, and you run it and show it passing. Exact commands and the
environment setup (JDK path, `DEVELOPER_DIR`, the stale `local.properties` trap) are in
**`references/build-and-test.md`**. In short:
- **KMP/Android logic:** add a `commonTest`, run `./gradlew :core:<m>:testDebugUnitTest --tests "..."`.
- **iOS:** add an XCTest/Swift Testing case, run `xcodebuild test -only-testing:...`.
- **Web:** add a `*.test.js`, run `node --test web-tests/*.test.js`.
- **Server:** run the relevant `ops/syrmos-api` unittest (CI does not gate these, so run
  them yourself).

### 5. Verify the real surface (do not skip)
Building is not behaving. For each affected surface, reach the highest tier you can and
**state which tier you reached**:
- **runtime-verified**: you saw it behave (simulator, emulator, live browser, curled the
  live endpoint and watched the value).
- **CI-verified**: build + lint + tests green, behavior not observed.
- **untested**: neither.

All four surfaces are runtime-verifiable on this machine (iOS simulator, Android emulator,
local web serve + browser, direct `curl` to `api-syrmos.peterdsp.dev`; only Overpass is
blocked). Try the real run first; fall back to CI-verified only when a local run genuinely
fails, and say so. Per-surface recipes and their gotchas are in **`references/verify-surfaces.md`**.

### 6. Ship
- Commit with a plain message, no trailers, no em-dash. Branch off `master` if needed; do
  not force-push, rebase, or rewrite history without explicit human approval.
- PR body: what and why, the test results, and the verification tier per surface. No AI
  attribution footer. Conventions and the deploy paths (Pages for web, Pi for server,
  release tags for beta/internal) are in **`references/conventions.md`**.

## Reference files

Read these as needed; they hold the exact commands and the accumulated gotchas so this
body stays short.

- **`references/build-and-test.md`** - environment setup (JDK17 path, `ANDROID_SDK_ROOT`,
  `DEVELOPER_DIR`, `local.properties` trap), the exact per-platform build and test commands,
  the pbxproj file-registration procedure, and what CI actually gates.
- **`references/verify-surfaces.md`** - how to runtime-verify each of the four surfaces,
  including the simulator/emulator driving fallbacks and the known tooling limits (no
  Chromium, no Service Worker install in the in-app browser).
- **`references/conventions.md`** - the bundled-seed three-copy + manifest rule, LineStatus
  model, localization, commit/PR hygiene, and the deploy paths.

## Related skills (the specialized phases)

This skill is the orchestrator. Each phase has a deeper companion skill; use them directly
when the task is only that phase, and they share this skill's reference files so commands
never drift:
- **syrmos-ideate** - decide what to build (phase 2 input): honest, feasibility-checked idea briefs.
- **syrmos-implement** - write the code across the clients without drift (phase 3).
- **syrmos-test** - write + run tests and runtime-verify the real surface (phases 4-5).
- **syrmos-deploy** - seed, deploy, and cut beta/internal releases safely (phase 6).
- **syrmos-debug** - diagnose a bug (reproduce, isolate across surfaces, fix at the root).
