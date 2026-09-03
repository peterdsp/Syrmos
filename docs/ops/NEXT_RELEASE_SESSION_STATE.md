# Autonomous next-release session — durable state

This is the resumable state file for the autonomous Syrmos 3.0 session. On resume: read the REAL Athens clock
(`TZ=Europe/Athens date`), read this file, run `git status` / `git log`, check open PRs (`gh pr list`) and CI
(`gh run list`), reconcile with reality (never trust this file over git/CI), then continue from the highest
value unfinished task. Do not stop or produce a FINAL report while Athens time < 20:00.

This file is a convenience index, NOT a source of truth. Git history, PRs, CI runs, and tests are the truth.

---

## Snapshot

- Last updated (Athens): 2026-09-03 03:12 EEST
- Window ends: 20:00 Europe/Athens 2026-09-03
- Branch at session start: `master` @ `5e90d78b`
- Version shipped: 2.0.0 (iOS build 138 / Android versionCode 223), tagged `v2.0.0`
- Next release: **3.0 "Journeys"** — see [docs/plans/3.0-JOURNEYS.md](../plans/3.0-JOURNEYS.md)

## Product thesis (locked, high confidence)

Own the whole journey, especially the passive middle of the ride: plan A→B, say plainly whether you'll make
it, then guide stop-by-stop without opening the app. Pillars: (1) first-class journey planner, (2) journey
confidence + last-train-home, (3) GO live trip guidance (get-off/transfer alerts on glanceable surfaces),
(4) disruption-aware + connection-risk routing, (5) proactive native surfaces. Version = 3.0 (category shift
from departure companion to journey companion). Evidence: 6 competitor studies + platform study all converge
on the proactive live-trip-companion pattern; the project's own roadmap named the trip planner "the one true
unlock" and only shipped fragments.

## Toolchain reality (bounds what is locally verifiable)

- iOS: full local build + XCTest + iPhone 17 sims (Xcode 26.6). STRONGEST local loop.
- Web JS (static web-*.js) + Node v24: locally testable + browser-verifiable.
- Python backend: runnable in scratch venv; 136/139 pass locally (3 failures are Python 3.9 + pydantic
  `str | None` artifacts only — pass on CI/Pi 3.11+). NOT a code bug.
- Android / KMP Kotlin: NO local JDK → CI-only (can write, cannot locally verify).
- `gh` CLI authenticated as peterdsp → PRs + CI drivable.

## Scoring buckets (see 3.0-JOURNEYS.md for detail)

MUST: planner surface, confidence, last-train-home, GO get-off/transfer alerts, disruption-aware routing.
SHOULD: connection-risk + alternatives, iOS Control Center + push-to-start Live Activity, Android
ProgressStyle Live Updates, saved journeys/commute nudges, pinnable red/green departures board.
EXPERIMENT: predict-before-official delay hint, AutoGO auto-detect, crowding hints from frequency.
LATER: web PWA Web Push/badging, Wear tiles, CarPlay tuning, broadcast Live Activity.
REJECT (reasons in thesis): in-app ticketing/refunds, SBB coach/occupancy, always-on GPS default, tourist
mode, iOS→KMP unification this release.

## Recommended implementation spine (from research)

Start with get-off / "your stop is next" alert (LOW effort, highest reassurance, offline from stop sequences
Syrmos already has) + journey state machine + one-tap "missed it/next departure" re-projection. Anchor rail
identity with the live connection-risk check. Flagship differentiator = auto-reroute on disruption.

## Parity strategy

iOS (native Swift) and Android (KMP Kotlin) run duplicate logic; web JS is a third port. Every 3.0 rule ships
with a language-neutral GOLDEN FIXTURE (JSON inputs → expected outputs) that all clients + server test
against, so planner/GO logic cannot drift. No risky client-unification refactor this release.

## Workstreams / status

| # | Workstream | Status | Verifiable | Notes |
|---|---|---|---|---|
| A | Product thesis + decision doc (3.0-JOURNEYS.md) | DONE (this session) | n/a | committed via PR (docs) |
| B | Backend pytest runnable + CI gate | IN PROGRESS | local pytest | 136/139 local; needs pytest config + ci.yml job (py3.11) |
| C | Web JS test harness (node:test) + CI | PLANNED | node local | web has 0 tests over 6.7k lines |
| D | Journey golden-fixture spec (shared) | PLANNED | node/py/xctest | inputs→expected journeys/confidence |
| E | GO get-off alert + journey state machine (iOS first, verifiable) | PLANNED | XCTest/sim | lowest-effort product spine |
| F | README/roadmap staleness fixes | PLANNED | n/a | README says "iOS 1.0.5" (stale); Appendix K roadmap stale |

## RELEASE-ENGINEERING FIX - beta.2 iOS RE-DELIVERED (05:26 EEST)

beta.2 Android UPLOADED (Play internal, versionCode 225). beta.2 iOS first upload FAILED - build-number
collision: the release read a static CURRENT_PROJECT_VERSION (138), and beta.1 had already uploaded 3.0.0
build 138, so ASC rejected "must be higher than 138". FIXED in #121 (merged): the release now stamps a Unix
epoch (date +%s) as CFBundleVersion - always unique + increasing, no manual bump. Dry-run validated the
epoch build (1788401926) against ASC; then dispatched release-ios dry_run=false on master -> **iOS beta.2
RE-DELIVERED to TestFlight (Archive + upload: success)**. Both platforms now have beta.2. Also merged this
session: #119 transit-data quality tests, #120 GO screen-reader a11y.

NOTE: the iOS beta.2 TestFlight build was delivered via a workflow_dispatch on master (build = epoch), so it
includes the a11y (#120) + transit-quality (#119) + build-number fix (#121) merges on top of the tagged
beta.2 content. The v3.0.0-beta.2 git tag still points at the version-bump commit; the actual TestFlight build
is newer master - honest to call it beta.2.

LESSON: release-ios.yml build number was NOT auto-stamped (RELEASE.md was aspirational); each iOS release
must have a strictly-higher build than any prior in the marketing-version train. Epoch stamp fixes it durably.

## MILESTONE: v3.0.0-beta.2 tagged - LIVE GO (04:51 EEST)

beta.2 (`v3.0.0-beta.2`) tagged + uploading to TestFlight + Play internal (Android versionCode 225).
Content: **live GO** - iOS GPS auto-advance + get-off notification + haptic (#117, gated internal), web live
GO over browser geolocation (#116, browser-verified end to end). Cross-client advancer parity (iOS
GoLocationAdvancer == web-go.js advancedPosition, tests mirror exactly). 17 PRs merged (#103-#118), 2 betas
shipped.

The 3.0 "Journeys" thesis is realized on iOS + web: plan A->B, be guided board/ride/get-off/change/arrived,
and be told hands-free when to get off. GO engine on all 4 targets + server against one Codex-reviewed
golden contract.

### Environment constraints learned (bound what's verifiable here)
- No JDK -> cannot build/run the Compose/Wasm web app or Android APK locally (CI + deploy only).
- iOS simulator device-access gating -> can build+XCTest+launch-smoke, but cannot navigate/screenshot the
  live UI or inject location into a navigated screen.
- So: iOS live GPS path + web Plan-workspace wiring are verified by internal-beta testers / on the Pages
  deploy, not pre-merge here. Web GO panel itself IS fully browser-verified via a served harness.

### beta.3 backlog (verifiable-vs-constrained)
- Web GO Plan-workspace wiring (guarded, verify on live Pages deploy). VISIBLE web GO.
- Android GO Compose screen over GoGuidance (CI-build + commonTest verify; no local run).
- Journey confidence / last-train-home (pillar 2; entangled with the schedule projector).
- Transit-data quality tests (FULLY verifiable via the node harness): duplicate trains, impossible
  departures, wrong terminals, direction/overnight boundary checks -> regression tests.
- iOS live GO robustness: coarse-GPS skip edge (noted; 50m filter mitigates).

## MILESTONE: v3.0.0-beta.1 DELIVERED (04:22 EEST)

The first 3.0 beta is TAGGED (`v3.0.0-beta.1`, master @ f8742244) and **UPLOADED**: Release iOS ->
`Archive + upload to TestFlight: success`; Release Android -> `Signed AAB to Play (internal track): success`.
Both gated by full CI (dry-runs validated the signed IPA/AAB against ASC/Play first). TestFlight then
processes the build asynchronously before testers see it (not monitorable from here; the dry-run validated
the IPA so processing should pass).

### beta.2 in progress (open PRs)
- #114 iOS GPS-proximity advancer (pure core of live GO) — 6/6 local, CI running.
- #115 web GO — JS planner + panel, node 20/20 + BROWSER-VERIFIED (harness screenshot: board -> get-off card).
  Not yet wired into the app's Plan workspace (can't build Compose/Wasm locally, no JDK; wiring verifies on
  Pages deploy).
Contents: GO engine on all 4 targets + server, iOS visible GO journey screen (internal-build-gated),
planner->GO connection, backend+web test gates, T7 reconcile, CI flake fix. 11 PRs merged (#103-#113).
Web auto-deploys via Pages from the master push.

### beta.2 backlog (next 3.0 increments, pick by value/verifiability)
- **Live GO (iOS)** — GPS proximity auto-advance + a get-off local notification (+haptic), so GO stops being
  a manual preview and delivers the real reassurance. Ungate from internal once live. macos-verifiable
  (simulate location). The research's #1 signature feature.
- **Android/web GO UI parity** — web needs a JS planner port (planDetailed) since it plans only via Ariadne
  text today; then a GO panel using web-go.js (browser-verifiable). Android: a Compose GO screen over
  GoGuidance (CI-verify).
- **Journey confidence (pillar 2)** — "you'll make it / tight transfer / take the next one"; can enhance the
  existing Ariadne route answers (not purely dormant).

## Progress log (03:35 EEST)

Merged (8): #103 thesis, #104 backend pytest gate, #105 web harness, #107 KMP GO, #106 web+server GO +
contract, #108 T7 reconcile, #109 iOS GO engine, #110 iOS-CI watchOS-flake fix.
Open: #111 iOS planner->GO connection (CI running).
Branch ready (stacked on #111): `feat/go-ios-screen` = the first VISIBLE GO feature (GoJourneyView +
GoJourneyViewModel + GoDemoEntryView + a "Journey guide (GO)" Settings entry). Locally: view-model tests
pass, whole app compiles, launches clean on iPhone 17 sim. Visual nav screenshot blocked by simulator
device-access gating (honest boundary). TODO before PR: gate the Settings entry behind
`BuildEnv.isInternalBuild` (like developerSection) so GO shows in TestFlight/internal betas but not public GA
until live GO ships.

Next: land #111 -> rebase+PR #112 (GO screen) -> consider tagging v3.0.0-beta.1 (iOS TestFlight/Play internal
= authorized beta channel; needs version bumps, next Android versionCode >= 224). Web GO wiring deferred (web
plans only via Ariadne text, no structured legs; needs a web planner port first).

## GO engine status (the 3.0 spine)

The GO live trip-guidance engine is implemented and tested in FOUR languages against ONE cross-client golden
contract (`fixtures/go-guidance/cases.json`, exact-equality): web `web-go.js`, server `go_guidance.py`, iOS
`JourneyGuidance.swift`, Android/KMP `core/domain/go/GoGuidance.kt`. Codex adversarially reviewed it
(session 01a06488): no divergence across 1,290 states, getOffNext never pairs with alert=false; two MEDIUM
findings addressed (exact-equality contract + 2-stop consumer caveat). NOT yet wired into any client UI
(dormant). Kotlin verified via CI (kmp-tests); Swift verified locally (xcodebuild 3/3); JS+Python in CI.

## PRs

- **Merged:** #103 thesis, #104 backend pytest gate, #105 web JS harness, #107 KMP GO engine.
- **Open (green except long iOS job):** #106 web+server GO engine + contract (Codex-reviewed);
  #108 T7 seed-coord reconciliation (see below).
- **Branch ready, not PR'd:** `feat/go-guidance-ios` (iOS Swift GO port, local xcodebuild 3/3) — PR after
  #106 merges (rebase to keep diff iOS-only).

## Findings this session

- **T7 tram coords** disagreed between seed/lines.json and stations.json for 6 stops (up to ~490m).
  RESOLVED in #108: reconciled the *unused* denormalized lines.json copy to the canonical registry
  (independently verified: exactly 6 T7 coord changes to registry values, no displayed-position change),
  topology test tightened to exact, generator invariant test added. **Open external boundary (needs user):**
  the Pi DB + `ops/syrmos-api/pkg/athens_fixed_rail_station_coordinates.md` still hold pre-realignment values
  with OSM node IDs; which coordinate set is *physically* correct needs OSM/field verification (blocked in
  sandbox). Do not run a fresh snapshot-api-to-seed until that is decided or the split reappears in
  schedules-v2/lines.json.

## Next highest-value actions (updated)

1. Merge #106 + #108 when iOS CI green.
2. Rebase `feat/go-guidance-ios` onto master, open iOS GO PR, CI, merge.
3. Wire GO into a VISIBLE client (make it non-dormant): iOS is most verifiable (simulator screenshots).
   Add `JourneyPlanner.planDetailed` (per-leg ordered stop ids; Dijkstra already computes the path) ->
   map to GuidanceJourney -> a GO view driven by it. Entry point: Ariadne route answers (AriadneModel).
4. Consider a 3.0.0-beta.1 tag ONCE GO is visibly wired on a client (authorized internal/TestFlight beta;
   tag triggers release-ios/release-android workflows). Not before a user-visible feature exists.
5. Later: journey confidence engine (pillar 2), disruption-aware routing (drop suspended edges).

## Findings / bugs (audit)

- README.md:140 "Shipping: iOS 1.0.5, Android 1.0.4" — stale vs 2.0.0 GA. (fix in workstream F)
- CASE_STUDY Appendix K roadmap stops at "2.0 = TBD 2028+"; 2.0 already shipped. Stale. (fix F)
- Backend 139 pytest tests exist but are NOT wired into CI and don't run out-of-the-box (no pytest
  config/pythonpath). (fix B)
- Web JS (6.7k lines incl. projection, fares, Ariadne port) has ZERO tests. (fix C)
- Leaflet loaded from unpkg CDN in web index.html — external egress; privacy/offline gap. (candidate finding)
- Duplicate logic iOS Swift vs KMP Kotlin (one repo TODO flags it). Parity risk — mitigate via fixtures (D).

## Blockers / boundaries

- No local JDK → cannot build/test Android/KMP locally (CI only). Boundary, not blocker.
- Overpass API blocked in sandbox (per memory) → OSM shape regen not runnable here.
- Pi deploy needs LAN + creds → no backend prod deploy from here (and none authorized without human).
- No public App Store/Play production release without explicit human authorization.

## Next 5 highest-value actions

1. Land PR B: pytest config (`pyproject.toml`/`pytest.ini` with pythonpath) + backend-tests CI job.
2. Land PR C: web JS `node:test` harness + first tests (athens-time helpers, fares, Ariadne intents) + CI.
3. Land PR D: shared journey golden-fixture format + first fixtures, consumed by a runnable test.
4. Land PR F: fix README + Appendix K roadmap staleness; point roadmap at 3.0-JOURNEYS.md.
5. Begin PR E: iOS journey state machine + get-off alert with XCTest, behind a feature flag.

## Watchdog / persistence

External supervision (user-run, outside Claude): `scripts/autonomous-watchdog.sh` +
`caffeinate -dimsu`. Claude cannot rename its own session or outlive a hard stop; the watchdog owns the
deadline and resumes this session. See that script's header.
