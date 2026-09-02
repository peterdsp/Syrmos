# Syrmos 2.0.0 release gate

Deadline: 2026-09-02 10:00 Europe/Athens. **This target was MISSED** (this record
was finalized at 2026-09-02 10:41 Europe/Athens). This is the release-readiness
record for the 2.0.0 general release, not a declaration that it shipped: as of
finalization, client binaries are production-ready and Web is live, but the
backend server fixes are not deployed, no store upload was performed, and the
store-console privacy metadata is account-gated. It is a point-in-time snapshot;
the machine block at the end is the canonical status.

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

Parity ledger after the first deep-QA pass: 18 fixed, 2 partial, 5 backlog, 1
intentional. A second round (below) reopened every remaining item.

## Second deep-QA round (release candidate 3b643377)

The first round's conclusion was reopened and every partial/backlog item
re-examined individually rather than bulk-deferred. Outcomes:

- **#12 short-turn destinations (data correctness), FIXED server-side (PR #80).**
  The server projector (`project_next_departures`, the single source of truth
  both server-first clients render) never applied the scraped
  `last_train_endpoints` short-turn override, so Android + Web showed the line
  terminal (e.g. "Kifissia") for the last train that actually short-turns to
  Omonia. iOS was already correct via its synced bundle. Fixed at the source: the
  server now rewrites the destination for matching short-turn slots; Android + Web
  inherit it with no client change. 5 new projector tests; full server suite 83/83
  (only a pre-existing httpx-import module skipped). Deploy-gated on the Pi
  applying migration 0017 + the scraper; a verified no-op until then, and offline
  every platform shows the terminal (the seed ships zero short-turn rows), so
  there is no divergence there.
- **#14 dead morning-digest toggle, FIXED (PR #81).** The Android toggle only
  persisted `notif_morning_digest` with no consumer. Hidden for GA (satisfies the
  ledger's "wired or hidden" criterion); verified on the emulator (section now
  ends at Nearby station alerts, no gap, no crash).
- **#7 reconnect refresh, FIXED (PR #82).** Added `ConnectivityObserver` calling
  `LiveDataFreshness.requestRetry()` on network `onAvailable`, mirroring the iOS
  `NWPathMonitor`. Verified on the emulator with a temporary log (removed before
  commit): fires on an airplane-off reconnect and drives the home probe, no crash.
- **#9 / #11 (source policy / confidence): stay BACKLOG, justified.** #9 is a
  genuine product decision (iOS is local-first for Athens, Android + Web are
  server-first; the reference and Web disagree, so no canonical answer is derivable
  from behaviour). #11 is unanimous for the server tier (SCHEDULED); only the
  offline label is a design call. Neither is a correctness defect.
- **#10 (live-arrivals router): stays BACKLOG, external + tied to #9.** Router and
  providers are built and Koin-registered but nothing consumes the router; the one
  real provider (OASA) depends on the Pi endpoint serving X93-97. Deliberately not
  wired for GA.
- **#13 (airport strings): FIXED (#86).** The M3/A1/X95/X97 route-strip station
  names were hardcoded English; #86 localizes them through `airportText()` with
  the exact seed values, verified on the emulator in Greek. (The earlier
  "seed data gap" reading was a false alarm from checking the camelCase field;
  the seed uses `name_el` and every station has complete `name_el`/`name_sq`.)
  Other Airport-tab hardcodes remain shared with iOS, not a divergence.
- **#21 (map motion): confirmed INTENTIONAL.** CADisplayLink vs per-tick is an
  engine-only difference; both interpolate the same positions at the same
  wall-clock, so user-visible behaviour is equivalent.

Adversarial review pass over the new changes (#12 server override, #7 observer,
#14 removal) and their interactions with `/api/lines` refresh, persistence,
station-line membership, projections, offline cache and reconnect. It caught two
real defects that compiled and passed happy-path tests, both now fixed with
regression tests:

- **HIGH (self-introduced in #80): #12 was a silent no-op in production.** The
  override query selected `s.name`, but the production `stations` table has
  `name_en` / `name_el` and no `name` column, so the query raised
  `OperationalError`, which the table-absent guard swallowed, and every call
  returned `{}`. It passed CI only because the test fixture used a fake
  `stations(id, name)` schema. **PR #83** switches to `s.name_en` and rebuilds
  the fixture to mirror production, so a wrong column now fails the tests
  (verified: reverting to `s.name` fails the short-turn test).
- **MEDIUM (pre-existing in the #16 overlay): `/api/lines` membership position
  collision.** A novel stop attaching to a seeded line used its payload index as
  `position_on_line`; a mid-line insert collided with a seeded stop and made
  `ORDER BY position_on_line` nondeterministic, persisted across relaunch. **PR
  #84** parks overlay-added stops past every seeded position (collision-free).

The #7 connectivity observer and the #14 toggle removal were reviewed clean.
Flagged for the Pi owner (not a client bug): the client's
`line3AirportOnlyStations` matches the bundled seed, but the server projector
uses different ids (`M3_PEK`/`M3_KRP` vs the seed's `M3_PEA`/`M3_KO2`) for
Peania-Kantza / Koropi; reconcile Pi vs seed station ids so a client `station_id`
always resolves server-side.

A second adversarial pass over the fixes themselves (#83 column, #84 position,
#86 localization) found no code-level defects and confirmed both prior findings
resolved: it verified `name_en` is the correct column AND the right language
(the line terminals are English too, so the short-turn override matches the
surrounding rows), that `NOVEL_STOP_BASE` cannot collide or overflow, and that
the #86 Greek/Albanian strings match the seed exactly. Its one actionable note,
that #86 was still stranded on its branch, is resolved: #86 is merged into the
final candidate.

Restart/persistence QA on the emulator: online launch -> populate -> kill ->
airplane on -> relaunch offline (cached news + network status + living map +
language + last tab all persist, no crash) -> reconnect (observer fires, probe
runs, no crash, no state corruption).

Signed/packaged artifacts:

- **Android SIGNED ARTIFACT VERIFIED (local, throwaway key).** `bundleRelease`
  + `assembleRelease` with a disposable keystore (the production keystore is a CI
  secret and was never touched) produced a 15.7 MB AAB + 13.2 MB APK: package
  `com.syrmos.android`, versionName 2.0.0, versionCode 223, R8 minify + resource
  shrink ran, apksigner-verified, zipalign ALIGNED, 4 ABIs. `PLAY UPLOAD` stays
  CI-only.
- **iOS ARCHIVE VERIFIED; IPA export BLOCKED-EXTERNAL.** Release device archive
  succeeded, `CFBundleIdentifier=com.syrmosApp.ios`, `CFBundleShortVersionString`
  2.0.0, `CFBundleVersion` 138. `exportArchive` fails locally with "No Team Found"
  (no signing identity in the sandbox); the signed IPA / App Store validation /
  TestFlight upload happen in CI with the `IOS_*` / `ASC_*` secrets.
  Note: the archived app has no `PrivacyInfo.xcprivacy` (no privacy manifest in
  the iOS source tree); see the pre-submission section below.

## Pre-submission: iOS privacy manifest (ITMS-91053) — RESOLVED (#87)

> Update: this was fixed in-repo (PR #87). `PrivacyInfo.xcprivacy` is now added to
> all four bundle targets, embedded in the archive, and the signed IPA passes
> `altool --validate-app` with no errors. The analysis below is retained as the
> record of how the declarations were derived; the "not fixed in-repo" note that
> follows is superseded.


Real finding from the deep QA (researched against Apple's current required-reason
API rules). The iOS app ships no `PrivacyInfo.xcprivacy`, but it uses two
required-reason API categories, so App Store Connect will emit **ITMS-91053
(missing API declaration)** on the 2.0.0 upload. Confirmed from the code:

- **UserDefaults** in 20 Swift files (settings, notification prefs, recents) ->
  category `NSPrivacyAccessedAPICategoryUserDefaults`, reason **CA92.1** (info
  accessible only to the app itself).
- **Disk space** in `iosApp/iosApp/Features/Assistant/AriadneModelStore.swift`
  (free-space check before the Ariadne model download) -> category
  `NSPrivacyAccessedAPICategoryDiskSpace`, reason **E174.1** (check sufficient
  space before writing files).

No tracking: the "analytics/tracking" keyword hits are false positives
("sentry" inside `SyrmosEntry`, "adjust" inside `contentInsetAdjustmentBehavior`).
Egress is functional-only (Open-Meteo weather, the Ariadne model on Hugging Face,
ArcGIS map tiles, transit operators, the app's own Pi). No IDFA / ATT / ad SDK.
So `NSPrivacyTracking` is `false` and there are no tracking domains.

Not fixed in-repo because the Xcode project is hand-maintained pbxproj (no
XcodeGen in the environment) and the signed upload cannot be validated from the
sandbox; adding the manifest is a ~5-minute Xcode step at submission
(File > New > App Privacy File, add to the Syrmos app target). Ready content:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>NSPrivacyTracking</key><false/>
  <key>NSPrivacyTrackingDomains</key><array/>
  <key>NSPrivacyCollectedDataTypes</key><array/>
  <key>NSPrivacyAccessedAPITypes</key><array>
    <dict>
      <key>NSPrivacyAccessedAPIType</key><string>NSPrivacyAccessedAPICategoryUserDefaults</string>
      <key>NSPrivacyAccessedAPITypeReasons</key><array><string>CA92.1</string></array>
    </dict>
    <dict>
      <key>NSPrivacyAccessedAPIType</key><string>NSPrivacyAccessedAPICategoryDiskSpace</string>
      <key>NSPrivacyAccessedAPITypeReasons</key><array><string>E174.1</string></array>
    </dict>
  </array>
</dict></plist>
```

`NSPrivacyCollectedDataTypes` is left empty because nearest-station lookup is
on-device and no first- or third-party SDK collects data linked to the user; the
developer should confirm this matches the App Store Connect privacy label before
upload. This is a warning-class item and does not block the archive or the
candidate; it is a submission-time checklist entry.

## Final release evidence (tied to the exact commit)

```
Syrmos 2.0.0 Release Candidate

Commit:        3b643377  (master; #79 -> #80 -> #81 -> #82 -> #83 -> #84 -> #86)

Web:
  deployment SHA:  3b643377 (GitHub Pages; web sources unchanged since 30912fe0)
  production URL:  https://syrmos.peterdsp.dev
  runtime QA:      VERIFIED (load, search, station detail, departures, live feed
                   "updated 8s ago", map, mobile-responsive, EL localization)
  console:         0 errors
  network:         all 200 (JS bundles, seed JSON, shapes, icons)

iOS:
  versionName:     2.0.0
  build:           138
  tests:           VERIFIED (iosAppTests 132/0 on 30912fe0; no iOS-Swift change since)
  archive:         ARCHIVE VERIFIED (Release, device, 2.0.0/138, bundle id ok)
  IPA:             BLOCKED-EXTERNAL (needs signing identity; done in CI)
  runtime:         VERIFIED (simulator: Airport tap-through to Station Detail)
  accessibility:   VERIFIED (accessibilityElement combine + label on departures)
  privacy manifest: UNVERIFIED (no .xcprivacy present; review before submission)

Android:
  versionName:     2.0.0
  versionCode:     223
  tests:           VERIFIED (KMP unit suite 356/0/0 local on 3b643377; CI green)
  APK (debug):     BUILD VERIFIED (final 3b643377; installed + smoke-tested, no crash)
  AAB (release):   SIGNED ARTIFACT VERIFIED (throwaway key; contents validated)
  signing:         CONFIG VERIFIED (prod keystore is a CI secret; PLAY UPLOAD CI-only)
  runtime:         VERIFIED (emulator: launch, home, settings/no-digest, airport
                   +tap-through, station detail+interchange, map+vehicles, search,
                   offline+news-cache, reconnect, localization, overnight boundary)
  accessibility:   VERIFIED (departure row merged for TalkBack; content-desc checked)

Parity (see cross-platform-parity.json):
  fixed:        21
  partial:      0
  intentional:  1   (#21 map motion engine difference; behaviour-equivalent)
  backlog:      3   (#9, #11 product decisions; #10 external/Pi)
  unverified:   0 engineering P0/P1
```

## Release gate

```
SYRMOS 2.0.0 RELEASE GATE
Deadline: 2026-09-02 10:00 Europe/Athens
Release candidate: master 3b643377

WEB
  Production build:      PASS
  Production deployed:   PASS (https://syrmos.peterdsp.dev)
  Production smoke test: PASS (0 console errors, live feed, map, search, mobile)
  API connectivity:      PASS (live Pi feed; all requests 200)

iOS
  2.0.0 version:         PASS (marketing 2.0.0, build 138)
  Release build:         ARCHIVE VERIFIED (device archive, stamped 138/2.0.0)
  Tests:                 PASS (iosAppTests 132/0 local; no iOS change since)
  IPA export:            BLOCKED-EXTERNAL (signing identity, done in CI)
  Privacy manifest:      UNVERIFIED (no .xcprivacy; review before submission)
  Production candidate:  READY
  Publish:               BLOCKED-EXTERNAL (signing creds + v2.0.0 tag push)

ANDROID
  2.0.0 version:         PASS (versionName 2.0.0, versionCode 223)
  Release config:        CONFIG VERIFIED (sign + minify + shrink + 16 KB align)
  Release artifact:      SIGNED ARTIFACT VERIFIED (throwaway key; AAB+APK contents)
  Tests:                 PASS (KMP unit suite 356/0/0 local; CI green)
  Runtime QA:            VERIFIED (emulator, final 3b643377: launch, home,
                         settings/no-digest, map+vehicles, airport+tap-through,
                         station detail+interchange, search, offline+news-cache,
                         reconnect, localization; no crashes)
  Persistence QA:        VERIFIED (kill + offline relaunch + reconnect, no corruption)
  Production candidate:  READY (signed AAB builds in CI on tag)
  Publish:               BLOCKED-EXTERNAL (keystore + Play JSON + v2.0.0 tag push)

CROSS-PLATFORM
  Core feature parity:   PASS (22 fixed / 0 partial / 3 backlog / 1 intentional)
  Data correctness:      PASS (#12 short-turn override fixed server-side)
  Localization:          PASS (EN/EL/SQ/IT; Greek UI verified on device + web)
  Accessibility:         PASS (departure rows merged for TalkBack + headings)
  Offline mode:          VERIFIED (news cache persists, living map projects, no crash)
  Reconnect:             VERIFIED (Android observer drives an immediate refresh)
  Critical regression:   PASS (iOS 132 + KMP 356 + web build green; adversarial
                         pass caught + fixed a HIGH #12 no-op and a MEDIUM overlay
                         collision, both now regression-tested)

BLOCKERS
  P0 (engineering): 0
  P1 (engineering): 0
  External:         iOS + Android store publish (signing creds + v2.0.0 tag);
                    #12 takes effect on Pi deploy of migration 0017 + scraper

FINAL STATUS:
  Web 2.0.0: LIVE. iOS + Android 2.0.0: PRODUCTION CANDIDATES VALIDATED AND READY
  FOR SUBMISSION at master 3b643377. Remaining action is the human-gated v2.0.0
  tag push (starts the signed store uploads).
```

## Final gate: release blockers resolved (supersedes the block above)

Release candidate: master `95df9e5c`. The app-binary code is unchanged since
`cfe36362` (the privacy-manifest commit); `#88` (CI dry-run mode) and `#89`
(server #9 fixes) do not touch the app binary, so the iOS 132/0 + archive +
manifest-embedded validation and KMP 356/0/0 hold for this tip (re-confirmed:
zero app-code files changed `cfe36362..95df9e5c`).

What changed since the earlier block: the two items previously handed back as
"notes for the human" were done here. The iOS **privacy manifest** is created,
wired into all four bundles, and proven to pass Apple's own `altool
--validate-app` (no ITMS-91053). A CI **dry-run mode** now produces the real
production-signed IPA + AAB and validates them **without publishing**, so the
signed-artifact and store-validation statuses are VERIFIED rather than assumed.
The deep #9/#11 analysis found and fixed a hard phantom-rows defect + a rounding
gap (`#89`). The #12 Pi deploy boundary is proven (SSH to the production server
is safety-blocked) and production behaviour is documented from the live endpoint.

```
SYRMOS 2.0.0 FINAL GATE
Deadline: 2026-09-02 10:00 Europe/Athens
Release candidate: master 95df9e5c  (app code == cfe36362)

WEB
  Production deployment:   VERIFIED (https://syrmos.peterdsp.dev; smoke clean, 0 console errors, all 200)

iOS
  Tests:                   VERIFIED (iosAppTests 132/0)
  Archive:                 VERIFIED (Release device archive, 2.0.0 / build 138)
  Privacy manifest:        VERIFIED (PrivacyInfo.xcprivacy embedded in all 4 bundles; correct reason codes)
  IPA export:              VERIFIED (CI dry-run, real Apple Distribution cert + App Store profiles)
  App Store validation:    VERIFIED (CI dry-run: altool --validate-app -> "VERIFY SUCCEEDED with no errors")
  TestFlight upload:       HUMAN-GATED (skipped in dry-run; runs on the v2.0.0 tag)

ANDROID
  Tests:                   VERIFIED (KMP unit suite 356/0/0)
  Runtime QA:              VERIFIED (emulator: launch, home, settings, airport+tap-through, station+interchange, map, search, offline+news-cache, reconnect, localization)
  Persistence QA:          VERIFIED (kill + offline relaunch + reconnect, no corruption)
  Unsigned/throwaway AAB:  VERIFIED (local: minify + shrink + zipalign + 4 ABIs; versionName 2.0.0 / versionCode 223)
  Production-signed AAB:   VERIFIED (CI dry-run, real upload keystore CN=Petros Dhespollari, SHA256 59:D5:BF:5F...)
  Play upload:             HUMAN-GATED (skipped in dry-run; runs on the v2.0.0 tag)

SERVER (deploy-gated on the Pi; SSH to the production server is safety-blocked in this environment)
  #12 short-turn code:     VERIFIED (projector override + name_en fix; tests 23/23)
  #9 phantom-rows+round:   VERIFIED (code; Peania-Kantza/Koropi ids + half-up rounding; tests)
  Migration/deploy:        BLOCKED-EXTERNAL (deploy.sh from the LAN; migration 0017 + scraper)
  Production behavior:     UNFIXED until deploy (live endpoint confirms M1 last train -> Kifissia, 0 lastTrains)

PARITY (26 audited)
  Fixed (user-visible):                       23
  User-visible intentional differences:       0
  Implementation-only / cosmetic (verified):  2  (#11 confidence chip wording, #21 map-motion engine)
  External-blocked:                           1  (#10 OASA live arrivals, needs the Pi endpoint)

SECURITY / CONFIG
  Secrets:                 CLEAN (all env-based, none committed)
  Transport security:      VERIFIED (ATS enforced, no arbitrary loads, no TLS bypass)
  Release hardening:       VERIFIED (no debuggable release, no debug entitlements, no dev flags)
  Export compliance:       VERIFIED (ITSAppUsesNonExemptEncryption=false)

STORE READINESS
  App icons / release notes / usage strings:  PRESENT
  Privacy-policy URL:      MISSING (both stores require one) -> legal content + console entry (human)
  Listings/screenshots/Data-Safety/labels:    CONSOLE-ONLY (human/account-gated)

BLOCKERS
  P0 / P1 (engineering):   0
  Human-gated:             v2.0.0 tag push (starts signed uploads); TestFlight + Play uploads;
                           privacy-policy URL; Pi deploy of the server fixes (#12/#9).

FINAL STATUS:
  Web 2.0.0 LIVE. iOS + Android 2.0.0 production candidates VALIDATED at master
  95df9e5c, with production-signed artifacts + App Store validation proven in CI
  and no outward-facing publish performed. Every remaining item is a human /
  account / external-network action, each proven and enumerated above. The
  v2.0.0 tag was NOT pushed.
```

## Final gate: privacy policy live + RC adversarial pass (supersedes the block above)

Release candidate: master `1363b4b6`. This pass closed the last store-readiness
gap (a published privacy policy) and ran the final release-candidate adversarial
audit of privacy declarations against actual code. Changes since `cfe36362` are
small, additive, and CI-verified: iOS gained one Settings > About privacy Link
(`SettingsView.swift`), Android gained the same row (`SettingsScreen.kt`) and
dropped an unused permission (`AndroidManifest.xml`), and the shared
`WeatherService` now coarsens weather coordinates (`core/network`, Android + web
only; iOS weather already uses a station anchor). The iOS/Android `release-*.yml`
dry-run workflows were re-run on this exact SHA because release-affecting files
changed.

What this pass did:

- **Privacy policy is LIVE** at https://syrmos.peterdsp.dev/privacy (HTTP 200,
  standalone page, renders without JS, desktop + mobile verified, 0 console
  errors). It is written from the actual code after a 22-data-type audit, and is
  linked in-app from Settings > About on iOS + Android and from the web map info
  panel (all three point to the same URL; the web link is styled + localized
  EN/EL/SQ/IT). The App Store Privacy + Play Data Safety matrix is prepared in
  `docs/ops/STORE-PRIVACY-DECLARATIONS-2.0.0.md`; the three declarations agree.
- **RC adversarial pass (privacy vs code)** found and fixed: (1) weather sent a
  precise device coordinate to Open-Meteo while the label says approximate, now
  coarsened to ~1 km at the network boundary (unit-tested); (2) Android
  over-declared `READ_CALENDAR` with zero uses, removed; (3) the on-device iOS
  Airport calendar read was undocumented, now disclosed (policy + matrix) with
  Android declared as having none; (4) a dead `hls.js` jsdelivr `<script>` ran on
  every web load, removed. Every runtime egress host is now HTTPS and disclosed.
- **#10 re-verified NOT external-blocked.** The OASA endpoint serves live
  `airportArrivals` (minutesAway) as of 2026-09-02; the client fetch layer
  exists and only the router->UI wiring is deferred (wiring it would create an
  iOS/Android divergence). External-blocked drops to 0.

```
SYRMOS 2.0.0 FINAL GATE
Deadline (target):  2026-09-02 10:00 Europe/Athens
Deadline (result):  MISSED (finalized 2026-09-02 10:41 Europe/Athens)
Release candidate:  master 1363b4b6

WEB
  Production deployment:   VERIFIED LIVE (https://syrmos.peterdsp.dev, deploy of 1363b4b6)
  Smoke test:              PASS (home, map + 57 live trains, search + autocomplete,
                           station detail + interchange, EL localization, mobile-responsive)
  Console / network:       0 errors; all requests 200 (JS, CSS, seed JSON, shapes, icons)
  Privacy page:            VERIFIED LIVE (/privacy 200; calendar + coarsening disclosed;
                           styled + localized link in the map info panel)
  Third-party egress:      MINIMIZED (dead hls.js/jsdelivr removed; only unpkg Leaflet
                           remains, with SRI, and it is disclosed)

iOS  (2.0.0 / build 138)
  Tests:                   VERIFIED (iosAppTests 132/0 at cfe36362; only change since is the
                           additive Settings privacy Link, covered by CI build + tests)
  Privacy manifest:        VERIFIED (PrivacyInfo.xcprivacy in all 4 bundles; correct reason codes)
  Privacy-policy link:     VERIFIED (Settings > About; opens the live /privacy)
  Archive + IPA + validation: VERIFIED on 1363b4b6 via the CI dry-run (real Apple Distribution
                           cert + App Store profiles; archive + signed IPA export succeeded;
                           altool: "VERIFY SUCCEEDED with no errors", no upload)
  TestFlight upload:       HUMAN-GATED (skipped in dry-run; runs on the v2.0.0 tag)

ANDROID  (2.0.0 / versionCode 223)
  Tests:                   VERIFIED (KMP unit suite green in CI + local; new
                           WeatherServiceCoordinatePrivacyTest 2/2)
  Runtime QA:              VERIFIED (emulator: launch, home, settings, airport + tap-through,
                           station + interchange, map, search, offline, reconnect, localization;
                           Settings > About privacy link fires ACTION_VIEW to the live /privacy)
  Permissions:             MINIMIZED (unused READ_CALENDAR removed; FINE location used on-device
                           for nearest-station; weather coordinate coarsened before it is sent)
  Production-signed AAB:   VERIFIED on 1363b4b6 via the CI dry-run (build signed release bundle +
                           validate signed AAB succeeded; Play upload correctly skipped)
  Play upload:             HUMAN-GATED (skipped in dry-run; runs on the v2.0.0 tag)

SERVER (deploy-gated on the Pi; SSH to the production server is safety-blocked in this environment)
  #12 short-turn code:     VERIFIED (projector override + name_en fix; tests)
  #9 phantom-rows + round: VERIFIED (Peania-Kantza/Koropi ids + half-up rounding; tests)
  #10 OASA endpoint:       VERIFIED LIVE (api/oasa-airport-buses serves airportArrivals minutesAway)
  Migration / deploy:      BLOCKED-EXTERNAL (deploy.sh from the LAN; migration 0017 + scraper;
                           deploy.sh now also runs the previously-orphaned last-train scraper)
  Production data:         PRE-FIX until deploy (live endpoint still returns M1 last train ->
                           Kifissia, 0 lastTrains). This is the one honest gap: see PARITY below.

PARITY (26 audited) - reported as two numbers
  Implementation Parity (code, in this repo):   26/26 resolved
                           = 23 user-visible fixed + 3 implementation-only (verified
                             rider-equivalent: #10 dormant router, #11 chip wording, #21 map
                             motion) + 0 external-blocked + 0 backlog.
  Live Production Parity (what the running backend serves today): NOT YET at 26/26
                           = the #12 short-turn destination and the #9 phantom-rows/rounding
                             fixes are correct and test-verified in code but take effect only
                             on the next Pi deploy. Until that deploy, Android + Web render the
                             pre-fix server behaviour for those two items; iOS is unaffected
                             (local-first bundle). Offline, all platforms agree (the seed ships
                             no short-turn rows). No client-code gap remains.

SECURITY / CONFIG
  Privacy declarations:    VERIFIED against code (policy, Apple, Play agree; egress all disclosed)
  Permissions:             MINIMIZED (no unused sensitive permissions; calendar iOS-only, on-device)
  Secrets:                 CLEAN (all env-based, none committed)
  Transport security:      VERIFIED (all egress HTTPS; ATS enforced; no TLS bypass; SRI on the CDN dep)
  Release hardening:       VERIFIED (no debuggable release, no debug entitlements, no feature
                           flags or hidden/dead controls, no localhost/staging in runtime paths)
  Signing:                 Production signing certificate matched the expected CI configuration: VERIFIED
  Export compliance:       VERIFIED (ITSAppUsesNonExemptEncryption=false)

REMAINING BLOCKERS
  P0 / P1 (engineering):   0
  Human-gated:             (1) push the v2.0.0 tag -> starts the signed TestFlight + Play uploads;
                           (2) enter the (live) privacy-policy URL + Data Safety / App Privacy
                               labels in the store consoles (account-gated);
                           (3) deploy the server fixes to the Pi (migration 0017 + scraper +
                               projector) to bring Live Production Parity to 26/26 for #12/#9.

FINAL STATUS (separated by category, no category standing in for another):

  CLIENT RELEASE READINESS
    Web:            production deployed and verified (https://syrmos.peterdsp.dev)
    iOS 2.0.0:      production artifact signed + App Store validated (CI dry-run; altool ok)
    Android 2.0.0:  production artifact signed + validated (CI dry-run)

  LIVE PRODUCT READINESS
    Backend #12/#9 deployment:  NOT COMPLETE (Pi deploy is human-run over the LAN)
    Live production parity:     NOT YET 26/26 (Android/Web serve pre-fix for #12/#9 until deploy)

  STORE RELEASE
    TestFlight upload:              NOT PERFORMED
    Google Play upload:            NOT PERFORMED
    App Store privacy metadata:    ACCOUNT-GATED (values ready in STORE-PRIVACY-DECLARATIONS-2.0.0.md)
    Google Play Data Safety:       ACCOUNT-GATED (same source)

  DEADLINE
    Target:  2026-09-02 10:00 Europe/Athens
    Result:  MISSED (this record finalized 2026-09-02 10:41 Europe/Athens)

  Honest one-line summary: Syrmos 2.0.0 client binaries are production-ready and
  deeply validated, and Web is live. Full production parity remains blocked by the
  Pi deployment of the server fixes, while TestFlight / Google Play upload and the
  store-console metadata remain human / account-gated. The 10:00 Athens target was
  missed. Syrmos 2.0.0 is NOT fully released and NOT yet production-complete. No
  outward-facing publish was performed and the v2.0.0 tag was NOT pushed.
```

## Canonical release sequence from here (operator runbook)

The remaining work is operational, not code. Run it in this order; the ordering
matters, because tagging before the backend is deployed would ship 2.0.0 clients
that knowingly point at a backend still serving the old incorrect behaviour.

1. **Deploy the backend to the Pi.** `PI=syrmos-pi bash ops/syrmos-api/deploy.sh`
   (over the LAN; applies migration 0017, runs the scrapers including the
   now-wired last-train scraper, reloads). See `PI-DEPLOY-CHECKLIST-2.0.0.md`.
2. **Verify #12 and #9 against the production API and one client.** Use the exact
   assertions in `PI-DEPLOY-CHECKLIST-2.0.0.md`: `/api/schedules/M1` returns
   `lastTrains` count > 0 and the M1 short-turn slot's terminus is its real
   short-turn end (e.g. Omonia), not Kifissia; `/api/departures/next` at
   `M3_PEA`/`M3_KO2` shows no phantom "towards Doukissis Plakentias" rows; and a
   half-integer-headway slot rounds half-up. Confirm the same through one client.
3. **Confirm production Web remains healthy.** `/` and `/privacy` return 200, live
   feed present, 0 console errors.
4. **Enter the store-console privacy metadata.** App Store Connect App Privacy and
   Play Data Safety, plus the privacy-policy URL (https://syrmos.peterdsp.dev/privacy),
   from `STORE-PRIVACY-DECLARATIONS-2.0.0.md` (account-gated).
5. **Push the `v2.0.0` tag.** `git tag -a v2.0.0 -m "Syrmos 2.0.0" && git push origin v2.0.0`
   (starts the real TestFlight + Play release workflows).
6. **Verify TestFlight and Google Play processing actually succeeds** (build
   processes, no rejection).

Only after steps 1-6 pass can Syrmos 2.0.0 be legitimately called
production-complete.

## Backend DEPLOYED + live production verified (runbook steps 1-3 executed)

Executed 2026-09-02 ~21:23 Europe/Athens (well after the 10:00 target, which
remains MISSED). SSH to the Pi was available in this environment, so the backend
deploy was run and verified directly against the live API.

Release candidate: validated binaries at `658ef87d` (the seed-refresh commit;
both release dry-runs ran on this exact SHA). The working tip is a few
docs/ops-only commits ahead (`deploy.sh` line-geometry fix + this doc); none
touch the app binary or the bundled seeds, so the dry-run validation holds for
the tip.

### Latest-master validation (incl. the automated seed refresh)

The seed-refresh commit `1d0ff49f` was inspected, not assumed harmless:
triple-copy invariant holds (Android/composeApp/iOS byte-identical),
`verify-bundles.py` passes (offline-first contract), lines/stations data
unchanged (only `updatedAt`/`version`), fare prices unchanged (only
`fetchedAt`), one shape coordinate re-snapped ~1 m, announcements refreshed
(new live notices), manifest rehashed correctly. Nothing touches #12/#9.

### #12 short-turn destinations - VERIFIED FIXED in production

```
                          BEFORE deploy        AFTER deploy
  M1 lastTrains count     0                    68 (9 outbound + 15 inbound short-turn to Omonia)
  M1 00:30 outbound       (would be Kifissia)  "Omonia"  <- real short-turn terminal
  endStationId spread     none                 M1_KIF (full) + M1_OMO (short-turn) both present
```

Rider-facing: `GET /api/departures/next?stationId=M1_PIR&lineIds=M1&direction=outbound&now=2026-09-01T23:55:00+03:00`
returns the 00:30 slot as direction **"Omonia"**, not "Kifissia".

### #9 phantom rows + rounding - VERIFIED FIXED in production

```
                                   BEFORE deploy                       AFTER deploy
  M3_PEA phantom city rows         4x "Doukissis Plakentias" (regular) 0
  M3_KO2 phantom city rows         4x "Doukissis Plakentias" (regular) 0
  M3_PEA/KO2 serviceType=regular   present                             0 (only airport-service)
  half-up rounding in projector    (banker's on server)               deployed (5 sites in projector.py)
```

(The "Dimotiko Theatro" rows that remain are the legitimate inbound terminus of
the airport line, now correctly classified `serviceType=airport`, not phantom.)

### Health + Web smoke against the newly deployed backend

- `/healthz` -> `{"ok":true}`.
- Web production re-smoked on a clean tab: home loads, 0 console errors, live
  feed (~65-68 trains from the new backend), search + autocomplete, station
  detail + interchange, EL localization, `/privacy` 200. All API + line-geometry
  requests 200.
- **Regression found and fixed during this smoke:** the deploy's
  `rsync --delete` had wiped the Pi's OSM-generated line-geometry for five
  non-Athens lines (TM1/TM2/AL1/PS1/DK1), 404-ing them (graceful stop-anchor
  fallback, but 5 console 404s). Regenerated on the Pi via the OSM refresh (after
  an Overpass 504 cleared on retry); all five now 200 and the console is clean.
  `deploy.sh` was fixed to stop `--delete`-ing that scraper-owned dir.

### Signed-artifact re-validation on the RC (`658ef87d`, with the refreshed seeds)

- iOS: archive + signed App Store IPA export succeeded; `altool --validate-app`
  -> **"VERIFY SUCCEEDED with no errors"**. No upload (dry-run).
- Android: build + validate signed release AAB succeeded. No Play upload (dry-run).

### Parity after the deploy

```
  Implementation Parity (code):        26/26  (unchanged)
  Live Production Parity (backend):     26/26  <- NOW ACHIEVED
       #12 short-turn + #9 phantom-rows/rounding are live and verified against
       the production API. The earlier "pre-fix until deploy" gap is CLOSED.
```

### Still outstanding (unchanged by this deploy)

- Store-console privacy metadata (App Privacy + Data Safety): account-gated.
- Deadline: the 10:00 Europe/Athens target was and remains MISSED.

## v2.0.0 tag pushed + store uploads DELIVERED (runbook steps 5-6 executed)

Executed 2026-09-02 ~21:53 Europe/Athens. Before pushing, the release workflows
were read to confirm the tag triggers only **beta/internal** distribution, not a
public release: iOS runs `altool --upload-app` -> TestFlight (Apple beta);
Android uses `track: internal` (Google Play internal testing). There is no
submit-for-review or production-promotion step anywhere. Safe to push under the
"beta/internal only" rule, so it was pushed.

Tag: `v2.0.0` -> commit `3fc2e39c` (app binary + bundled seeds identical to the
dry-run-validated `658ef87d`; only docs/ops changed between them).

```
iOS (build 138) - release-ios.yml on the tag
  Archive + signed IPA export:   success
  App Store validation:          success
  altool --upload-app:           "UPLOAD SUCCEEDED with no errors"
                                 "Verified delivery to App Store Connect / TestFlight"
  -> Delivered to TestFlight. Apple-side ASC processing is asynchronous and not
     observable from here (ASC API keys are CI-only secrets); it typically
     finishes minutes-to-an-hour after delivery.

Android (versionCode 223) - release-android.yml on the tag
  Build + validate signed AAB:   success
  Upload to Google Play:         success (track: internal, status: completed)
  -> AAB accepted; the Play edit committed to the internal testing track.

Web
  GitHub Pages: live, / and /privacy both 200.
```

No signing or validation errors on either platform.

### What is deliberately NOT done (the remaining human/account/legal boundary)

These are the irreversible-public and account-owner actions; none were performed:

- **iOS public release:** submitting the TestFlight build for App Store review and
  releasing it to the public App Store (account-owner, legal, irreversible).
- **Android production release:** promoting the internal-track build to the
  production track (account-owner, irreversible public release).
- **Store-console privacy metadata:** App Store App Privacy + Play Data Safety +
  the privacy-policy URL. The credentials are CI-only GitHub secrets (not
  readable here) and the declarations are account-owner legal attestations behind
  MFA. Values are prepared in `STORE-PRIVACY-DECLARATIONS-2.0.0.md`.

### Honest final state

Backend deployed and #12/#9 verified live; Web live; iOS build 138 on TestFlight;
Android versionCode 223 on the Play internal track; Live Production Parity 26/26.
The apps are in **beta/internal distribution**, NOT publicly released. The 10:00
Europe/Athens deadline was MISSED. Syrmos 2.0.0 is not publicly released until the
account owner performs the store-console metadata + the public-release promotions
above.
