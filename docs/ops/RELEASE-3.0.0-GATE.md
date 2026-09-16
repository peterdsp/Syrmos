# Syrmos 3.0.0 "Journeys" release gate

Phase Z (release candidate) readiness for the 3.0.0 "Journeys" line. This is the
authoritative record of what 3.0 ships, the verification evidence gathered, the
visual-baseline capture matrix, and the remaining release actions. It mirrors the
structure proven in `RELEASE-2.0.0-GATE.md`.

> Status: **RC in preparation.** Web is deployed to production; iOS is on
> TestFlight. The full visual-baseline raster matrix and public store submission
> remain (see Open items). An environment blocker (Xcode licence) is recorded
> under Environment.

## Version state

- `versionName` = **3.0.0** (`androidApp/build.gradle.kts`); Android `versionCode` = **226**.
- iOS `CFBundleVersion` is **epoch-stamped per upload** (build numbers are Unix epochs;
  the static `CURRENT_PROJECT_VERSION = 138` in the pbxproj is legacy and unused for
  TestFlight delivery). Latest TestFlight upload this cycle: build ≈ `1789426xxx`,
  Delivery UUID `299fe5d5-aca7-4f69-95a1-494f185c6cc3`, `UPLOAD SUCCEEDED` /
  `Verified delivery to App Store Connect / TestFlight`.
- Web production: GitHub Pages built `master` HEAD `a39e8ebe` successfully and is
  live at `https://syrmos.peterdsp.dev` (content-hashed assets confirmed serving
  the 3.0 CSS/JS).

## Scope shipped in 3.0.0 (by phase)

3.0 is the "Journeys" line: plan a trip, get live guidance, recover from
disruption, and glance at it from native surfaces. Each slice shipped on all
three clients against shared language-neutral fixtures.

- **Phase F — Foundation.** Web app access for every browser; shared visual
  fixture (`fixtures/journeys/ui-reference.json`); adaptive shell (readable
  column / content breakpoints); versioned session/state models + injectable
  Athens test clock. (Later reversed for web: web is now desktop-only, phones
  redirect to `/get-app/` — see `syrmos-web-desktop-only`.)
- **Phase P — Planning (J01, J02, J05).** Time-aware ranked itineraries
  (depart-at / arrive-by backward search, up to 3 distinct options); endpoint
  picker (S03), plan draft (S02), route results (S04), selected detail (S05);
  timing margin + feasibility (comfortable/tight/missed/unknown), last connection
  home; saved journeys + recents + resume (S08).
- **Phase G — Guidance (J03).** Selected itinerary → persistent GO session (S06),
  manual progression, resume/end, evidence-based auto-advance; the demo entry
  points removed from release navigation.
- **Phase R — Recovery (J04).** Disruption exclusions (never route through a
  suspended line), connection risk + confirmed replacement / alternatives (S07),
  failure & capability states (S10: no-route, no-coverage, stale-data,
  location-denied, offline), accessibility-unknown disclosure. Shared engines
  `DisruptionExclusion`, `AccessibilityDisclosure`, `ConnectionRisk`, mirrored on
  all three clients.
- **Phase N — Native surfaces (J07, J08, J09).**
  - J07: unified freshness/offline presentation (one shared `FreshnessPresentation`
    rule, banner reactivity on all clients).
  - J08: native journey glances — iOS Live Activity (#158), Android ongoing
    notification (#159), web GO panel; GO progress unified into one shared
    fixture-tested `GoGuidance.progress` (#160).
  - J09: user-enabled leave-by reminders + saved-departure board — shared engine
    (#165), shared store + byte-parity persistence (#166), Android scheduling +
    board (#167), iOS scheduling + board (#168), web foreground runtime + board
    (#170); local opt-in, dedup, updated/cancelled reminders.
- **Product website + web fares polish (#169).** Product-site Fares & Ariadne
  previews rebuilt as on-brand CSS mockups; web app fares reworked from a long
  list into a two-column tile grid.

Out of 3.0 scope (deliberate): **J10** (remote/web push, automatic trip
detection) — separate data/permission/battery design.

## Verification evidence (by surface, this cycle)

Tiers: **RT** = runtime-verified on the real surface; **UT** = unit/fixture
tests; **CI** = green CI checks. See `verify-empirically-before-claiming`.

- **Shared core (KMP).** UT: `:core:common`, `:core:domain`, `:core:model`
  test suites green, including the J09 engine (8 state + 7 reconcile fixture
  cases), the store byte-parity contract, and `GoGuidance.progress` (cross-client
  fixture). CI: green on every 3.0 PR.
- **Web.** RT: browser-verified end to end — fares two-column grid, leave-by
  board (add → opt-in flips on + timer armed → "Leave in N min" → delete cancels),
  product-site Fares & Ariadne previews, freshness banner. UT: `node --test`
  suite 239/239 (incl. `reminders`, `reminders-runtime`, `source-chip`,
  `go-guidance`). Deployed + live-asset-verified in production.
- **iOS.** RT (earlier this session, before the Xcode-licence block): app builds
  0 warnings; `LeaveByReminderTests`, `SavedDepartureStoreTests`,
  `JourneyGuidanceTests` pass on the simulator; the saved-departure board renders
  the seeded reminder with correct engine-derived state; the Live Activity was
  verified on the iPhone 17 simulator. CI: iOS build + tests green on every PR.
  TestFlight upload succeeded this cycle.
- **Android.** RT: on the emulator, seeding the board schedules an exact wakeup
  alarm keyed by the reminder id and the fired `LeaveByReminderReceiver` posts
  "Time to leave for M1 / Victoria to Kifisia" on the `leave_by_reminders`
  channel (confirmed via `dumpsys`). UT: KMP suites green. CI: Android build +
  lint green on every PR.

## Visual-baseline capture matrix (Phase Z — tracked)

The screen inventory is S01–S10 (prompt §8). The acceptance target is a raster
gate over approved per-platform baselines; this matrix is the checklist to run in
a frozen rendering environment / CI visual-regression job. `[ ]` = pending
capture, `[x]` = captured and inspected this cycle.

### Screens
- S01 Home / Now · S02 Journey / Plan draft · S03 Station selector ·
  S04 Route results · S05 Selected journey detail · S06 GO active journey ·
  S07 Connection risk & alternatives · S08 Saved journeys & last trip home ·
  S09 Explore / Departures / Map / Airport / Ariadne · S10 Failure & capability states.

### Base matrix (per platform: iOS, Android, web)
- [x] **S01–S10 at C390, light and dark — iOS complete (2026-09-16).** 36 cells
      (18 screens x 2 themes) on iPhone 17 / iOS 27.0 at 402x874pt, every one
      captured through `scripts/capture-baselines.sh` and therefore
      reproducible: clock pinned to 2026-09-16T08:42+03:00 and verified by a
      receipt, offline so screens render from the bundled seed, animations held,
      app state and first-run gates reset, location pre-granted at Syntagma,
      status bar frozen. Covers S01, S02 (draft + filled), S03 (list + keyboard),
      S04, S05, S06 (active + get-off-next), S07 (risk + alternatives), S08, S09
      (Explore, Map, Airport, More, Ariadne) and S10 (offline). See
      `docs/screenshots/release-3.0.0/` with `manifest.tsv`.
      **Reproducible, not yet approved:** nobody has inspected all 36 and signed
      them off, and only the offline variant of each screen is covered. Android
      and web not started.
- [ ] S02 / S05 / S06 additionally at C360, M768, E1024, W1360 and Short.
- [~] S02 / S04 / S06 in all four locales (en / el / sq / it) with **real**
      localized strings (long Greek/Albanian/Italian must not clip).
      **`el` done on iOS (2026-09-16):** all 18 screens, not just S02/S04/S06.
      Nothing clips at C402 and the layouts hold under longer Greek. The pass
      found three localization defects instead: 31% of Greek display strings
      carry no accent at all (254 of 802), Greek loses its accents when
      uppercased because the call sites use the locale-unaware `uppercased()`,
      and some screens show Latin place names inside otherwise Greek cards. See
      `docs/screenshots/release-3.0.0/FINDINGS.md` findings 8-10. `sq` and `it`
      not started; `sq` looks likely to share finding 8.
- [ ] Accessibility: iOS Dynamic Type XXXL, Android font scale 2.0, web zoom 200%,
      and keyboard-open layouts.

### Per-capture metadata (required for each cell)
platform · OS/browser · viewport/content rect · scale · locale · theme ·
font setting · fixture revision · source commit.

### Acceptance gates (prompt §8)
- Geometry: ≤ 1 logical unit for component frames/gaps at default scale; no
  clipped text, overlap, hidden action, or horizontal page scroll.
- Raster: ≤ 0.5% changed unmasked pixels at channel tolerance 12/255, then
  human/agent inspection of every diff. Mask only provider tiles, the system
  status clock, and documented nondeterminism — never our buttons, trust labels
  or text. Compare each platform to its own approved baseline (native map
  providers/fonts differ). A source-string test alone is not a visual acceptance
  test.

### Evidence captured this cycle (representative, not the full gate)
- [x] Web (desktop, light): app shell with the 3.0 leave-by-reminders card + toggle.
- [x] Web (in-session, earlier): fares two-column grid; leave-by board with two
      reminders showing "Leave in 20 min" / "Leave in 7 min"; product-site Fares
      and Ariadne CSS previews; all browser-verified.
- [x] Android (emulator): leave-by alarm scheduled + "Time to leave" notification
      fired (dumpsys), plus J07 predicted-schedule banner and S07 risk card.
- [x] iOS (simulator, pre-licence-block): saved-departure board, settings opt-in,
      Live Activity on the Dynamic Island.

The full C390 light/dark S01–S10 grid, the window/locale/a11y expansions, and the
raster gate over approved baselines remain to be run in a device lab / CI
visual-regression job. The iOS reference pass above is the first real progress
against it; what it proved is that the blocker is no longer the environment (the
simulator works again) but **capture determinism**: until the Athens test clock is
injected and the planner reads `fixtures/journeys/` instead of the live seed,
nothing captured can serve as a diffable baseline. Three findings also block axes
of the matrix outright — announcements are content-free outside Greek (locale
axis), and Map cannot reach the network without location permission (Map cells).

## Environment

- **Xcode licence not accepted (blocker).** The host updated to Darwin 27 between
  sessions; `/usr/bin/git`, `xcodebuild`, `simctl`, and `/usr/bin/python3` all
  error with "You have not agreed to the Xcode license agreements." Until an
  operator runs `sudo xcodebuild -license accept` (interactive, needs the account
  password), this session cannot commit/push, build or run the iOS simulator, or
  capture fresh iOS/visual baselines. Web (node) and file editing are unaffected.
  **Action:** operator accepts the licence, then commit these docs and run the
  capture matrix.

## Open items before public release

- [x] Accept the Xcode licence; commit + push the Phase Z docs. (Done; the
      simulator, `git` and `xcodebuild` all work on this host again.)
- [x] Make the capture harness deterministic. `scripts/capture-baselines.sh`
      pins the clock (verified by a receipt the app writes, so a failed pin
      cannot pass as a baseline), cuts the network so screens render from the
      bundled seed, holds looping animations still, resets app state and the
      first-run gates, pre-grants location, freezes the status bar and settles
      before the first shot. Proven by two full reinstall cycles producing
      byte-identical PNGs. Remaining: recorded API fixtures, without which only
      the offline variant of each screen is diffable.
- [ ] Triage the six iOS findings in `docs/screenshots/release-3.0.0/FINDINGS.md`.
- [ ] Run the full visual-baseline capture matrix + raster gate (device lab / CI).
- [ ] Device-gated behaviours to confirm on hardware: Android 12+ exact-alarm
      permission UX; iOS notification-authorization banner delivery.
- [ ] Store metadata: confirm `STORE-PRIVACY-DECLARATIONS-3.0.0.md` against App
      Store Connect / Play Data Safety (no new data collection; local
      notifications + `SCHEDULE_EXACT_ALARM` rationale).
- [ ] Public submission (account-owner / MFA / legal): App Store submit + release,
      Play production promotion. Not runnable from CI (store credentials are
      write-only secrets). Tag flow ships beta/internal only.

## Honest final state

3.0 is feature-complete across iOS, Android and web, verified at the strongest
tier each surface allowed this cycle, deployed to production web and TestFlight.
Phase Z documentation is in place; the visual-baseline raster gate and the public
store submission are the remaining, operator-gated steps, plus the local Xcode
licence acceptance needed to resume git/iOS work on this host.

## 3.0.0 correction pass (2026-09-16)

The seven remaining visual/journey findings (8, 10, 2, 3, 4, 6, 7) were worked in a
focused correction pass. Status per finding is in
`docs/screenshots/release-3.0.0/FINDINGS.md` (correction-pass table). Product and
review records: `docs/qa/3.0.0-ranking-decision.md` (owner chose comfortable-first,
option 2) and `docs/qa/3.0.0-greek-copy-review.md` (Greek pack; bulk awaiting native
review). Verification this pass: web suite green (`node --test web-tests/*.test.js`),
Kotlin `JourneyRankerTest` green, iOS app+tests build clean and the new unit classes
(`JourneyRankingOrderTests`, `StationGroupingTests`, `PlaceNameLocalizationTests`,
`GreekCopyRegressionTests`) green on the simulator. Runtime baseline recapture
(S01/S03/S04/S05/S06/S09 + More scroll) and native Greek review remain open; no
version bump, upload or deploy was done.
