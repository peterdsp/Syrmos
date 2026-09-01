# Syrmos 2.0.0 release gate

Deadline: 2026-09-02 10:00 Europe/Athens. This is the release-readiness record
for the 2.0.0 general release. It is a point-in-time snapshot; the machine block
at the end is the canonical status.

## Version state (finalized on master)

| Platform | Marketing | Build | Source |
|---|---|---|---|
| iOS (app + watch + complication + widget) | 2.0.0 | 138 | `iosApp/Syrmos.xcodeproj/project.pbxproj` (`CURRENT_PROJECT_VERSION`) |
| Android | 2.0.0 | versionCode 223 | `androidApp/build.gradle.kts` |
| Web | rolling (no version field by design) | n/a | GitHub Pages |

The beta line ended at 2.0.0-beta.23 (iOS 137 / versionCode 222); the GA
candidate is iOS 138 / versionCode 223. No stable `v2.0.0` tag exists yet.

## What was validated this pass

- **Web production LIVE** at https://syrmos.peterdsp.dev. Smoke test: loads, 0
  console errors, station list, "Now" departures, 45 live trains, map render +
  tap-through, honest "Estimated" data-state labeling, live Pi feed reachable.
- **iOS Release archive** for device (`generic/platform=iOS`, `Release`)
  succeeded and stamped CFBundleVersion 138 / 2.0.0. Full `iosAppTests` suite:
  132 tests, 0 failures. Privacy usage strings, entitlements, export-options
  (Team YTS4KJBX3P, app-store-connect, manual signing), and
  `ITSAppUsesNonExemptEncryption=false` all valid.
- **Android** release config is sound (minify + shrink + release signing +
  proguard + the 16 KB native-lib alignment gate). KMP/Android unit tests green
  in CI.
- **Release-blocking fix**: `release-ios.yml` was reading the build number with
  PlistBuddy on the source Info.plist, whose `CFBundleVersion` is the literal
  `$(CURRENT_PROJECT_VERSION)` placeholder; App Store Connect would have rejected
  the upload. It now reads the resolved build setting via
  `xcodebuild -showBuildSettings`.

## Cross-platform parity + QA hardening merged for 2.0.0

Shipped this pass (all merged to master, CI green): live-positions/offsets
decode resilience (Android), accent/case-insensitive station search (Android),
Athens time via the IANA zone (Android), offline rail-news cache (Android),
date-scoped trip `validDates` end to end (Android), fast per-call live/departures
timeouts (Android), interactive Airport tab (iOS + Android), circular bus route
loop closure (iOS), and two iOS decode-resilience fixes (GPS-less train,
malformed announcement).

## Blockers

No engineering P0/P1 blockers. The remaining steps are external / human-gated:

1. **Publish trigger (human):** pushing the `v2.0.0` tag starts the signed
   TestFlight and Google Play uploads. It was intentionally not pushed
   autonomously because it initiates outward-facing store uploads. Command:
   `git tag -a v2.0.0 -m "Syrmos 2.0.0" && git push origin v2.0.0`.
2. **iOS signing (external):** Apple Distribution `.p12` + 4 provisioning
   profiles + App Store Connect API key. Stored as the 10 `IOS_*` / `ASC_*`
   GitHub secrets (per docs/ops/RELEASE.md, verified 2026-07-09); the workflow
   skips signing cleanly if absent.
3. **Android signing (external):** upload keystore + Play service-account JSON,
   stored as the 5 `ANDROID_*` / `RELEASE_*` / `PLAY_*` GitHub secrets. First
   upload of the package must be done manually in Play Console (Google rule).

## Known non-blocking follow-ups (post-release)

- **CI iOS tests are non-gating.** `.github/workflows/ci.yml` runs
  `xcodebuild test` suffixed with `|| echo "::warning::..."`, so a test failure
  cannot fail CI; and the `SIM_DEST` line uses `sed E` (missing dash), so it
  always falls back to the generic "Any iOS Simulator Device" destination.
  Effectively iOS is build-gated in CI. The full suite was run locally instead
  (132/0). Fixing the gating safely (parse `-showdestinations` into a real
  `platform=...,id=...` string, drop the `|| echo`) is a deliberate follow-up,
  held back from the release window to avoid destabilizing the shared gating CI.
- **`libllama-sim.a` is arm64-only** (no x86_64 simulator slice), so a `Release`
  build targeting the x86_64 simulator slice fails to link. Irrelevant to the
  device release (arm64) and to Apple-Silicon simulators (arm64); a dev-env note
  only.
- **Remaining parity backlog** (P2/P3, none release-blocking): `lastTrains`
  short-turn destination override in the shared band projector (#12), `/api/lines`
  overlay-vs-upsert semantics decision (#16), departure-card accessibility
  semantics merge + headings/live-region (#18), map padding/tween cosmetics
  (#21). Airport express buses X93-X97 still show "check OASA" pending a Pi
  seed regeneration with their timetables.

## Deep QA phase (post-candidate, before the 10:00 deadline)

Real Android runtime QA on a booted emulator (built locally with the Android
SDK's bundled JDK; the "no local JDK" note was stale). All VERIFIED, no crashes:
launch, More/settings, Airport tab + tap-through to Station Detail, Station
Detail + interchange, Home (subtitle/status/CTA/living-map/news/network), Map
(network + capsule vehicles on-track), search (case-insensitive in-memory),
offline (news cache persists, living map projects, no crash), and localization
(full Greek UI incl. localized news). The full KMP/Android unit-test suite
passes locally on the consolidated master.

Five more parity fixes landed and were verified this phase:

- **#8 line-detail upcoming departures** (P1): every metro/tram line detail now
  shows a departures section (was suburban-live-only); verified on the M1 detail.
- **#18 departure-card accessibility**: the row merges into one TalkBack
  announcement + section headings; verified via the a11y node tree.
- **#16 /api/lines overlay-only**: Android no longer overwrites seeded line
  status/region or reorders stations (matches iOS); strictly write-reducing.
- **#6 Home enable-location CTA**: the nearby section shows an actionable CTA
  instead of collapsing; verified on the emulator.
- **CI hardening**: the debug APK is published as an artifact for runtime QA,
  and the iOS test step now genuinely gates (the `sed E` typo + `|| echo`
  swallow are fixed).

Parity ledger now: 18 fixed, 2 partial, 5 backlog, 1 intentional (see
cross-platform-parity.json). Remaining backlog is either a product-policy call
(#9 departures source, #11 confidence), an external/Pi dependency (#10 live
arrivals + airport-bus timetables), a documented deferral (#12 lastTrains -
central departures-path blast radius too high for GA; fast-follow), or #14
settings recents/digest.

## Release gate

```
SYRMOS 2.0.0 RELEASE GATE
Deadline: 2026-09-02 10:00 Europe/Athens

WEB
  Production build:      PASS
  Production deployed:   PASS (https://syrmos.peterdsp.dev)
  Production smoke test: PASS (0 console errors, live data, map, interaction)
  API connectivity:      PASS (live Pi feed, 45 trains)

iOS
  2.0.0 version:         PASS (marketing 2.0.0, build 138)
  Release build:         PASS (device archive, stamped 138/2.0.0)
  Tests:                 PASS (iosAppTests 132/0 local)
  Production candidate:  READY
  Publish:               BLOCKED-EXTERNAL (signing creds + v2.0.0 tag push)

ANDROID
  2.0.0 version:         PASS (versionName 2.0.0, versionCode 223)
  Release config:        PASS (sign + minify + shrink + native-lib gate)
  Tests:                 PASS (KMP/Android unit tests, local + CI)
  Runtime QA:            VERIFIED (emulator: launch, home, map+vehicles, airport
                         +tap-through, station detail+interchange, search,
                         offline+news-cache, localization; no crashes)
  Production candidate:  READY (signed AAB builds in CI on tag)
  Publish:               BLOCKED-EXTERNAL (keystore + Play JSON + v2.0.0 tag push)

CROSS-PLATFORM
  Core feature parity:   PASS (18 fixed / 2 partial / 5 backlog / 1 intentional)
  Localization:          PASS (EN/EL/SQ/IT; Greek UI verified on device)
  Accessibility:         PASS (departure rows merged for TalkBack + headings)
  Offline mode:          VERIFIED (news cache persists, living map projects, no crash)
  API/data correctness:  PASS (shared projector, resilient decode, overlay-only lines)
  Critical regression:   PASS (iOS 132 + KMP suite + web build green; iOS tests now gate)

BLOCKERS
  P0 (engineering): 0
  P1 (engineering): 0
  External:         iOS + Android store publish (signing creds + v2.0.0 tag)

FINAL STATUS:
  Web 2.0.0: LIVE. iOS + Android 2.0.0: PRODUCTION CANDIDATES VALIDATED AND
  READY FOR SUBMISSION. Remaining action is the human-gated v2.0.0 tag push.
```
