# Syrmos squad backlog

Every brainstormed idea, ranked by value-to-effort and feasibility, appended each cycle.
Ideas whose data is not reachable (or that need a blocked/paid source) are kept for the
record but marked REJECTED and never selected.

## Cycle 2026-10-20 17:06:32 EEST

Selector: ranked by value-to-effort and feasibility; rejected anything whose data is not
reachable; preferred small, high-confidence pure-transform wins over large speculative builds.

**Chosen this cycle: "Wire real service alerts into the airport hub" (score 88).**

| Rank | Score | Idea | Effort | Surfaces | Shape | Status |
|------|-------|------|--------|----------|-------|--------|
| 1 | 88 | Wire real service alerts into the airport hub | S | iOS, Android | Pure transform (intersect alert lines/stations with hub set) feeding existing card | CHOSEN |
| 2 | 86 | Delay-encoded live vehicle markers | S-M | iOS, Android, web | Pure transform (delayMinutes -> tier) per-client marker styling | ranked |
| 3 | 84 | Greek all-caps tonos correctness on web | S-M | web | Port existing core util + thin render wiring | ranked |
| 4 | 83 | Withheld-vehicles honesty chip | S | iOS, Android, web | Read two existing fields -> one localized chip | ranked |
| 5 | 82 | Next-three airport bus ETAs with real headway | S | iOS, Android | Pure transform over data already in payload | ranked |
| 6 | 80 | Arm leave-by from a planned trip | S | iOS, Android, web | Small adapter + button wiring two shipped engines | ranked |
| 7 | 78 | Community crowding overlay on the live map | M | iOS, Android, web | New client overlay over existing endpoint | ranked |
| 7 | 78 | Live connection re-check in GO | M | iOS, Android, web | Margin recompute (reuses ConnectionRisk) + live poll/banner | ranked |
| 9 | 76 | iOS localization parity guardrail test | S | iOS | Test only (optional enum refactor) | ranked |
| 10 | 75 | Last-service-of-night honesty at the airport | M | iOS, Android | Pure transform combining projector + live bus feed | ranked |
| 11 | 74 | Defer web script loading for first paint | S | web | Attribute change + bootstrap-timing verification | ranked |
| 12 | 73 | Live network pulse banner | S-M | iOS, Android, web | Pure transform (fleet delays -> status enum) | ranked |
| 13 | 72 | Flight check-in buffer with one-tap leave-by | M | iOS, Android | Pure transform (buffer + duration -> leave-by) + control | ranked |
| 13 | 72 | Route-matched disruption alerts | M | iOS, Android, web | Pure transform (notice <-> lineId match) mirrored | ranked |
| 15 | 70 | Per-journey fare summary | M | iOS, Android, web | Pure transform (legs -> fare) + small fare row | ranked |
| 16 | 69 | Last-service safety badge | M | iOS, Android, web | Pure transform + badge via chip-atomicity pattern | ranked |
| 17 | 67 | Walk-time leave-by lead | S | iOS, Android, web | Pure transform (distance -> leadSeconds) | ranked |
| 18 | 66 | Just-landed arrivals mode | M | iOS, Android | New UI entry over existing data + ranking | ranked |
| 18 | 66 | Live-countdown screen-reader announcements | M | web, iOS, Android | Shared announce rule + per-client live-region wiring | ranked |
| 20 | 64 | Follow-this-vehicle tracking mode | M | iOS, Android, web | Client UI state + camera follow over per-id registry | ranked |
| 21 | 62 | Fare per airport option | M | iOS, Android | Pure mapping option -> seeded fare product | ranked |
| 22 | 61 | Vehicle breadcrumb trail | M | iOS, Android, web | Client-only ring buffer -> fading polyline | ranked |
| 23 | 60 | GO missed-connection recovery | L | iOS, Android, web | Pure transform + stateful GO re-anchor | ranked |
| 24 | 59 | Per-vehicle train freshness (end batch-aging) | M-L | server, iOS, Android, web | Server field + swap client classifier | ranked |
| 25 | 56 | Live to-airport express bus ETAs | L | iOS, Android, server | New server watcher + feed change + client render | ranked |
| 26 | 55 | Estimated time-to-next-station on live vehicles | M | iOS, Android, web | Pure transform (distance/speed); heavy honesty hedging | ranked |
| 27 | 54 | iOS Dynamic Type audit for fixed-size fonts | M | iOS | Audit + per-view layout change | ranked |
| 28 | 52 | Robust line-name localization on iOS | S | iOS | Small transform + test | ranked |
| - | 38 | Live flight status in the plan | L | iOS, Android, server | New external integration + new Pi endpoint | REJECTED: dataReachable=false; no in-repo flight source, needs a paid/rate-limited external API and a key |

### Why the chosen idea won

"Wire real service alerts into the airport hub" is the best value-to-effort and the highest
confidence in the pool:

- **Pure transform, S effort, no server work.** It intersects the live `/api/announcements`
  feed (affectedLines / affectedStationIds) with each hub's airport line/station set and
  renders into the existing `AirportServiceAlertCard`. No new endpoint, no feed shape change.
- **Data is live right now.** `/api/announcements` already carries an active M3 rail-works
  alert (station closures 21:40, X20 replacement bus) with severity, valid-until window, and
  Ariadne-translated 4-language title/summary. Fully reachable from this sandbox.
- **Fixes a shipped dead feature that breaches the honesty identity rule.** The card currently
  ships passive placeholder copy ("Syrmos does not infer a clear status when no alert feed is
  available"). That is exactly the no-passive-copy / honesty rule the app exists to uphold.
- **Only two surfaces (iOS + Android), fixture-testable.** Smaller and lower-risk than the
  3-client marker/chip ideas above it, so it is the highest value-per-unit-effort in the pool.

Known risks to handle in the build: match `affectedLines` coding to the hub line IDs
(M3_AIR vs M3, X-bus codes X93/X95/X96/X97 / X3/2X/THS_AIR), provide an honest clear state
when no airport-scoped alert is active (never the old "no feed available" copy), and confirm
no existing global alert banner already covers the same notice to avoid duplication.
