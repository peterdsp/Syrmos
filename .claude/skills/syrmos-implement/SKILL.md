---
name: syrmos-implement
description: >-
  Write the code for a Syrmos feature or fix across its separate client codebases. Use this
  skill whenever the task is to actually implement, code, wire up, port, or change behavior
  in Syrmos: "implement this", "write the code for", "add X to iOS/Android/web", "wire it
  into the map/departures/journey view", "port this to the other platforms", or "make the
  change". It holds the cross-platform implementation discipline that keeps the three
  clients from drifting: the shared pure-transform pattern, the per-platform idioms and
  traps (iOS pbxproj registration, Android/KMP, web UMD JS), the parallel implementations
  that must be edited together, and localization. Pair with syrmos-test to prove it works;
  for the whole plan-to-ship arc use syrmos-feature-loop.
---

# Syrmos implementation

Syrmos renders the same behavior in **three independent codebases plus a server**. The
cardinal sin is implementing on one and calling it done, or letting the three diverge. This
skill is about writing code that stays parallel and matches local idiom.

Before editing: read `AGENTS.md`, the relevant `memory/MEMORY.md` entries, and the
neighboring files on each client you are about to touch. Minimal diff, follow existing
patterns, no unsolicited refactors.

## The shared pure-transform pattern (use it whenever the logic is pure)

When behavior is pure logic (grouping, ranking, projection, feasibility, formatting), do
NOT scatter `if`s through three sets of views. Implement one small pure transform, mirrored
on each client, each with its own unit test and identical semantics. This is how the app
already works and how it stays consistent:

- Web: `composeApp/src/wasmJsMain/resources/web-departures.js` (`SyrmosDepartures.groupDepartures`)
- iOS: `iosApp/iosApp/Core/Schedule/DepartureGrouping.swift` (`DepartureGrouping.group`)
- Android/KMP: `core/domain/.../departures/DepartureGrouping.kt` (`groupDepartures`)

Design the contract once (input type, output shape, edge cases, sort order), then write it
three times with parallel naming. If you find a bug in one implementation, it almost
certainly exists in the other two: fix all three.

## Per-platform idioms and traps

### iOS (`iosApp/`, native SwiftUI, no KMP framework)
- New `.swift` files are NOT compiled until registered in
  `iosApp/Syrmos.xcodeproj/project.pbxproj` (4 insertions each: PBXBuildFile,
  PBXFileReference, group children, Sources phase). `xcodegen` is not installed; the
  pbxproj is the source of truth. Two safe options:
  1. Put new code in an already-referenced file (no pbxproj surgery).
  2. Use an idempotent helper script like `scripts/add-departure-grouping-files.py`, with
     the `FE0000000000000000000Cxx` UUID convention for hand-added files.
- A `GroupedDeparture.id = UUID()` computed per call means a `.last?.id` divider check never
  matches unless you compute the groups ONCE in a local `let`. Watch per-call identity.
- For reorderable lists prefer `List` + `.onMove` (drivable, idiomatic) over `.draggable`.

### Android (`feature/*`, `composeApp` androidMain, KMP `core/*`, Compose + osmdroid)
- KMP auto-includes sources (no pbxproj equivalent).
- Pure logic goes in `core/domain` (or the right `core` module), with `commonTest` tests.
- Feature VMs: a Koin factory that owns an uncancelled scope leaks loops on Voyager tab
  switches. Use a single, or wire dispose. See the viewmodel-lifecycle memory.

### Web (`composeApp/src/wasmJsMain/resources/web-*.js`, hand-written Leaflet JS)
- UMD modules: `require()` in node tests, `window.Syrmos*` in the browser. Load order
  matters (e.g. `web-departures.js` before `web-map.js`).
- Use `athensNow()` / `nowMinutes()` for any schedule/countdown time, never a bare
  `new Date().getHours()` (device-local time bug). A guardrail test enforces this.
- Live-train markers must be `position: absolute` on the icon, or they march into the sea.

### Server (`ops/syrmos-api/`, Python on the Pi)
- Only when the feature needs new data, an endpoint, scraping, or Ariadne changes.
- Ariadne has TWO parsers that drift: Kotlin (`core/domain/assistant/*`) and JS
  (`web-ariadne.js` + `web-ariadne-rag.js`). Fix assistant logic in BOTH.

## Things that must be edited in lockstep

- **The map/vehicle rendering** lives in three places (iOS `MapView`, Android osmdroid, web
  Leaflet). A map change usually edits all three (e.g. tile overlays, capsule bearing).
- **The bundled seed** ships in three byte-identical copies plus a sha256 manifest. If you
  edit `PL1.json`, rehash its manifest entry in all three copies or clients will not
  re-fetch. See `../syrmos-feature-loop/references/conventions.md`.
- **Chip/status lines** (transport codes + status words) must render as one atomic,
  non-wrapping line, with the per-platform fix pattern (iOS FlowLayout / Android FlowRow /
  web nowrap + flex-wrap).

## Localization (always, for user-facing strings)

English is the base. iOS/Android strings go through the String Catalog (`.xcstrings`, source
English, `sq`/`el`/`it` locales) via `String(localized:)` or SwiftUI `Text` with English
keys. Web uses the inline 4-language dicts guarded by parity tests. Never hardcode a
translation or add side-by-side bilingual prose. Preserve Albanian ë/ç and Greek tonos
(certain parser files must stay accentless; guardrail tests enforce both).

## After implementing

Compile commands per platform are in `../syrmos-feature-loop/references/build-and-test.md`.
Do not stop at compiling: hand to **syrmos-test** to add and run tests and runtime-verify
the real surface. Then ship via **syrmos-feature-loop** / **syrmos-deploy**.
