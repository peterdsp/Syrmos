# Syrmos 3.0.0 "Journeys" — what we did

A cross-phase résumé of the 3.0 line and its release state. Companion to
`RELEASE-3.0.0-GATE.md` (the formal readiness gate).

## The phase map

| Phase | Theme | Status |
|---|---|---|
| F | Foundation (web access, shared fixture, adaptive shell, session/state models) | ✅ shipped |
| P | Planning — J01/J02/J05 (ranked itineraries, endpoint picker, results, detail, feasibility, saved journeys) | ✅ shipped |
| G | Guidance — J03 (persistent GO session, progression, resume/end) | ✅ shipped |
| R | Recovery — J04 (disruption exclusions, connection risk + alternatives, failure/capability states, accessibility disclosure) | ✅ shipped |
| N | Native surfaces — J07/J08/J09 (unified freshness; iOS Live Activity / Android notification / web GO panel; leave-by reminders + saved-departure board) | ✅ shipped |
| Z | Release candidate (visual baselines, matrix, device evidence, release docs) | ⏳ docs done; visual raster gate + public submission remain |

There are **no other feature phases** in 3.0 after Z. The only deferred work is
**J10** (remote/web push, automatic trip detection), which is intentionally a
separate future line (its own data/permission/battery design), not part of 3.0.

## What this session shipped (all merged to master, green CI)

Phase N completion + polish:

- **#158** iOS journey Live Activity (J08)
- **#159** Android active-journey ongoing notification (J08)
- **#160** unified GO progress into one shared `GoGuidance.progress` (J08 parity)
- **#165** J09 leave-by reminder engine (timing + reconcile) — shared, fixture-tested
- **#166** J09 saved-departure store + byte-parity persistence contract
- **#167** J09 Android — AlarmManager scheduling + board + opt-in (notification fire proven via dumpsys)
- **#168** J09 iOS — UNUserNotificationCenter scheduling + board + opt-in (board sim-verified)
- **#169** product-site Fares & Ariadne previews rebuilt as CSS mockups; web app fares → two-column grid
- **#170** J09 web — foreground runtime + board + opt-in (node-tested + browser-verified)

J09 is complete on all three clients on the shared engine (#165) + store (#166).

## Deploy state

- **Web** — live in production at `https://syrmos.peterdsp.dev` (GitHub Pages built
  master HEAD `a39e8ebe`; hashed assets confirmed serving the 3.0 CSS/JS).
- **iOS** — uploaded to TestFlight (epoch build ≈ `1789426xxx`, delivery UUID
  `299fe5d5-…`, verified). Not yet promoted to public App Store.
- **Android** — `versionName 3.0.0`, `versionCode 226`; no store push this session.
- **Server (Pi)** — no changes this release; nothing to deploy.

## Phase Z produced

- `docs/ops/RELEASE-3.0.0-GATE.md` — readiness gate: scope by phase, per-surface
  verification evidence, the S01–S10 × light/dark × window × locale × a11y capture
  matrix as a tracked checklist with geometry/raster acceptance gates, environment
  blocker, open items.
- `docs/ops/STORE-PRIVACY-DECLARATIONS-3.0.0.md` — 3.0 delta: leave-by reminders +
  saved journeys are on-device only (no new data collected/shared); new Android
  `SCHEDULE_EXACT_ALARM` rationale.
- This summary.

## What remains before public GA

1. Run the full visual-baseline raster matrix (S01–S10 × the expansions) against
   approved per-platform baselines — a device lab / CI visual-regression job.
2. Confirm two device-gated behaviours on real hardware: Android 12+ exact-alarm
   permission UX; iOS notification-authorization banner delivery.
3. Public store submission (App Store submit + release; Play production promotion) —
   account-owner / MFA / legal, not runnable from CI.

## Host blocker (must clear to resume git/iOS work here)

After the host updated to Darwin 27, the **Xcode licence is unaccepted**, so
`git`, `xcodebuild`, `simctl` and `python3` all error. These Phase Z docs are
written to disk but **not committed** because git is gated. An operator must run:

```
sudo xcodebuild -license accept
```

then commit the docs and (optionally) run the iOS build / visual-capture steps.
Web tooling (node) and file editing were unaffected.
