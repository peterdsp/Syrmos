# Syrmos squad runlog

One dated entry per cycle: what was brainstormed, what was built, the test and verify
result, and what awaits human review. The squad prepares; a human merges and tags.

## Cycle 2026-10-20 17:27:22 EEST

**Feature: Wire real service alerts into the airport hub.** Branch
`syrmos-squad/airport-service-alerts`, PR #243.

### Brainstormed

28 ideas ranked by value-to-effort and feasibility (full table in `BACKLOG.md`, cycle
2026-10-20 17:06:32). Chosen: "Wire real service alerts into the airport hub" (score 88),
the highest-confidence pure transform in the pool: two surfaces (iOS + Android), no server
work, fixture-testable, and it retires a shipped dead card that breached the no-passive-copy
honesty rule. Runners-up kept for later cycles were delay-encoded live markers (86), Greek
all-caps tonos on web (84), and the withheld-vehicles honesty chip (83). One idea (live
flight status, score 38) was rejected as data-unreachable (no in-repo source, needs a paid
external API).

### Built

A shared pure selector `AirportServiceAlerts.matches(...)` that intersects each alert's
`affectedLines` / `affectedStationIds` with the hub's line and station id sets, ordered
highest severity first. Mirrored on both clients: KMP in `core/data`
(`AirportServiceAlerts.kt`) and Swift in `iosApp` (`AirportServiceAlerts.swift`). Each hub
carries explicit `alertLineIds` / `alertStationIds` (Athens: M3, M3_AIR, A1, A2,
X93/X95/X96/X97 plus rail terminals; Thessaloniki: TM1, TM2, X3, 2X, 1X, 1N, 79 plus the
interchange stations). The card now has three honest states: matching alerts, a clear state
shown only when a feed actually loaded, and the genuine no-feed note. +428 / -29 across 8
files, web untouched (no Airport tab, mobile only).

### Tested and verified

- Android: `AirportServiceAlertsTest` (`core:data` commonTest) covers line and station
  matching, non-airport and non-service-alert exclusion, severity ordering, case-insensitive
  line match, and Thessaloniki vs Athens scoping. Green via `:core:data:testDebugUnitTest`.
- iOS: `AirportServiceAlertsTests` plus `AirportHubTests` assert the same contract; full app
  built and the test host ran the suite green.
- Live feed (runtime-verified from this sandbox against the production API): GET
  `/api/announcements` returns 17 items including the active `stasy-homepage-alert` (lines
  M3, severity warning, stations M3_ETH / M3_HOL / M3_NOM). Piped through the selector the
  Athens hub surfaces that alert; Thessaloniki shows the clear state. Premise holds.
- Builder reported green; the adversarial verifier confirmed green, hygiene, and
  cross-client parity. Ship-ready = true.
- Honest note on live CI at prep time: the Android, KMP, web, and the iOS build-app checks on
  PR #243 are green; two iOS UI-inspection runs and the CI iOS build-and-test job were still
  in progress. These are the same suites the verifier already ran green locally; the human
  should confirm the rollup is fully green before merging.

### Awaiting human review

The squad does not merge and does not tag. For a human to ship beta.34 with one review:

1. Merge PR #243 into `master`.
2. Prep commit on `master`: bump `androidApp/build.gradle.kts` versionCode 238 -> 239
   (versionName stays 3.0.0). 237 (beta.32) and 238 (beta.33) are burned and can never be
   reused; 239 is the next free Play code. iOS needs no pbxproj change: CURRENT_PROJECT_VERSION
   stays 138 and MARKETING_VERSION stays 3.0.0, because `release-ios.yml` epoch-stamps
   CFBundleVersion at build time, so the iOS build number is always fresh and never collides.
3. Cut the beta: `git tag v3.0.0-beta.34 && git push origin v3.0.0-beta.34`. The tag fires
   `release-ios.yml` + `release-android.yml` to TestFlight and Play internal only. No App
   Store or Play production promotion.
