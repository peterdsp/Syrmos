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
