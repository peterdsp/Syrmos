# Explore V2 and RailPulse implementation plan

## Outcome

Build one production Explore tab with two equal modes:

- Discover: Greece-wide destinations, journeys by time, collections, events, Ariadne, and relevant RailPulse summaries
- Network: the current metro, suburban, tram, Intercity, scenic, replacement-bus, station, and line browser

RailPulse is a community confidence layer inside Explore, Network, train detail, station detail, tracked journeys, Live Activities, and Apple Watch. It is not another root mode or tab.

## Fixed constraints

1. Preserve offline schedules, station direction, line navigation, localization, source-confidence labels, and the existing Network search.
2. Never label projected, scheduled, offline, or community information as operator-live data.
3. Keep RailPulse accountless and without personal profiles.
4. Do not transmit names, email, aliases, free text, photos, exact GPS, location history, advertising identifiers, or stable device identifiers.
5. Keep contribution history, levels, badges, and quality feedback on-device.
6. Publish only anonymous aggregates that reach a minimum independent-proof threshold.
7. Use an audited unlinkable one-time proof design. Do not create custom cryptography.
8. If unlinkability or no-log operation cannot be demonstrated, keep RailPulse writes disabled while the read-only and local-preview experience ships.
9. No analytics or behavioral tracking.

## Target architecture

### Feature ownership

Create `feature/explore` as the owner of the root Explore experience. Keep `feature/lines` focused on the Network browser and reusable line UI.

```text
composeApp/ExploreTab
        |
        v
feature/explore/ExploreScreen + ExploreViewModel
        |
        +-- Discover feed
        +-- Universal search
        +-- Go by time
        +-- Collections and events
        +-- RailPulse summaries
        |
        +-- feature/lines/NetworkBrowser
```

Avoid a big-bang rename. `LinesScreen` can first expose a reusable `NetworkBrowser` composable while its existing entry point remains compatible.

### Shared layers

```text
core/model
  ExplorePlace, JourneySnapshot, PulseContext, PulseSignal, PulseAggregate

core/domain
  SearchExploreUseCase, BuildDiscoverFeedUseCase, GetReachablePlacesUseCase
  GetPulseSnapshotUseCase, SubmitAnonymousPulseUseCase

core/data
  ExploreRepository, PulseRepository, local caches and offline queue

core/network
  Explore content client, Pulse read client, anonymous proof and report client

core/database
  cached Explore content, pulse aggregates, pending anonymous writes

ops/syrmos-api
  Explore content endpoints, pulse aggregation, expiry and moderation
```

## Milestone 0: freeze contracts and feature flags

### Work

- Add capability flags for `explore_v2`, `pulse_read`, `pulse_write`, `pulse_watch`, and `pulse_system_surfaces`.
- Define the source-confidence vocabulary once: Live, Scheduled, Estimated, Offline, Operator, Community.
- Define the Pulse confidence vocabulary: Awaiting confirmation, Community supported, High community confidence, Disputed, Resolved, Expired.
- Decide minimum aggregation thresholds by signal sensitivity.
- Create fixture JSON for every Explore and RailPulse UI state.

### File targets

- `core/model/src/commonMain/kotlin/com/syrmos/core/model/explore/`
- `core/model/src/commonMain/kotlin/com/syrmos/core/model/pulse/`
- `core/common/src/commonMain/kotlin/com/syrmos/core/common/`
- `docs/design/explore-v2/`

### Exit gate

- Models serialize consistently on Kotlin, iOS, Android, and web.
- No model contains a user name, stable contributor ID, coordinate, photo, or free-text report.

## Milestone 1: extract and preserve Network

### Work

- Split the current `LinesScreen` into a root compatibility wrapper and reusable `NetworkBrowser`.
- Preserve query, region filter, type filter, line grouping, disruptions, station navigation, and line navigation.
- Extend modes to cover Metro, Tram, Suburban, Bus, Scenic, and the existing Intercity representation.
- Add station results beside line results.
- Preserve search and scroll state when switching between Discover and Network.

### File targets

- `feature/lines/src/commonMain/kotlin/com/syrmos/feature/lines/LinesScreen.kt`
- `feature/lines/src/commonMain/kotlin/com/syrmos/feature/lines/LinesViewModel.kt`
- `composeApp/src/commonMain/kotlin/com/syrmos/app/tab/ExploreTab.kt`

### Tests

- Existing line filtering remains unchanged.
- Search matches English, Greek, Albanian, Italian, IDs, terminals, and aliases.
- Returning from station or line detail restores state.
- Airplane mode still shows all bundled network data and known direction.

## Milestone 2: build the Explore shell

### Work

- Create `ExploreScreen`, `ExploreViewModel`, and immutable `ExploreUiState`.
- Add the sticky universal search and Discover or Network segmented control.
- Create design-system components for destination cards, metric rows, confidence chips, section headers, route threads, and compact pulse summaries.
- Implement independent section loading and failure states.

### File targets

- new `feature/explore/` Gradle module
- `core/designsystem/src/commonMain/kotlin/com/syrmos/core/designsystem/component/`
- `composeApp/src/commonMain/kotlin/com/syrmos/app/di/AppModule.kt`
- `composeApp/src/commonMain/kotlin/com/syrmos/app/tab/ExploreTab.kt`

### Exit gate

- Discover and Network switch in one tap.
- A section error does not blank the screen.
- Back navigation restores segment, query, filters, and scroll position.
- Dark mode, large text, VoiceOver, and TalkBack layouts are usable.

## Milestone 3: universal search

### Work

- Build one local search index for destinations, stations, lines, modes, events, and Ariadne suggestions.
- Normalize diacritics, Greeklish, Albanian variants, common station aliases, and line IDs.
- Group results by destination, station, line, event, and Ask Ariadne.
- Return exact local matches immediately and debounce only fuzzy ranking.

### File targets

- `core/domain/src/commonMain/kotlin/com/syrmos/core/domain/explore/SearchExploreUseCase.kt`
- `core/data/src/commonMain/kotlin/com/syrmos/core/data/repository/ExploreSearchRepository.kt`
- `feature/explore/src/commonMain/kotlin/com/syrmos/feature/explore/search/`

### Tests

- `M3`, `Αεροδρόμιο`, `Aeroporti`, `Airport`, and common misspellings resolve appropriately.
- Search functions with no network.
- Stale remote enrichment never replaces a valid local result.

## Milestone 4: Discover core feed

### Work

- Add Destination of the day, Live now, Go by time, Popular today, Near you, Collections, Events, Continue exploring, and Ask Ariadne.
- Source destination metadata from bundled editorial content first.
- Use repository image assets with stable keys and fallbacks.
- Show duration, departure, changes, fare, direction, last return, and source confidence before tourism copy.
- Keep popularity local or editorial until a privacy-safe aggregate exists. Never invent live popularity.

### File targets

- `feature/explore/src/commonMain/kotlin/com/syrmos/feature/explore/discover/`
- `core/data/src/commonMain/resources/explore/`
- `assets/explore/`

### Exit gate

- The feed remains useful in airplane mode.
- Missing fare shows `Fare unavailable`.
- A connector bus, ferry, replacement bus, or walk is never presented as direct rail.

## Milestone 5: JourneySnapshot and Go by time

### Work

- Reuse `PlanJourneyUseCase`, schedule repositories, fare data, service announcements, station offsets, and location origin.
- Add `JourneySnapshot` composition without duplicating existing line or station models.
- Compute reachable places for 15, 30, 60, 90, and 120+ minute budgets.
- Rank by total time, waiting, changes, reliability, walking, fare, and last return.
- Cancel stale calculations when filters change.

### File targets

- `core/domain/src/commonMain/kotlin/com/syrmos/core/domain/explore/GetReachablePlacesUseCase.kt`
- `core/domain/src/commonMain/kotlin/com/syrmos/core/domain/usecase/PlanJourneyUseCase.kt`
- `feature/explore/src/commonMain/kotlin/com/syrmos/feature/explore/time/`

### Tests

- Time budgets include waiting and transfer time.
- Offline direction and terminal remain visible.
- Scheduled and estimated values retain different labels.
- A missed connection changes the result ranking.

## Milestone 6: RailPulse read path

### Work

- Add `PulseSnapshot` and `PulseAggregate` readers.
- Cache only active anonymous aggregates with their timestamp and expiry.
- Add compact pulse state to Discover, Network rows, station detail, train detail, and Ariadne context.
- Keep official alerts visually above community reports.
- Render cached community data as stale or expired when appropriate, never as current.

### Backend file targets

- `ops/syrmos-api/migrations/0024_pulse_aggregates.sql`
- new `ops/syrmos-api/syrmos_admin/pulse.py`
- `ops/syrmos-api/syrmos_admin/app.py`
- `ops/syrmos-api/tests/test_pulse.py`

### Client file targets

- `core/network/src/commonMain/kotlin/com/syrmos/core/network/PulseService.kt`
- `core/data/src/commonMain/kotlin/com/syrmos/core/data/repository/PulseRepository.kt`
- `core/database/src/commonMain/sqldelight/com/syrmos/core/database/`
- station, map, schedule, and Explore feature surfaces

### Exit gate

- `pulse_write=false` still allows the complete read experience.
- Expired aggregates disappear without a client release.
- Community summaries never inherit the `SourceConfidence.LIVE` label.

## Milestone 7: anonymous RailPulse write path

### Protocol work

- Select an audited implementation of unlinkable one-time tokens or blind-signed proofs.
- Obtain small batches of proofs without storing an account or stable client identity.
- Scope each proof to a short epoch and action class while keeping mint and redemption unlinkable.
- Spend one proof per report, confirmation, contradiction, or resolution.
- Store only a spent-proof hash until its replay window expires.
- Keep the origin server free of persistent IP, user-agent, and request-header logs.
- Use transient edge controls only for immediate abuse pressure.

### Report payload

```json
{
  "context": {"type": "STATION", "id": "M1_MON"},
  "signal": "PLATFORM_CROWDING",
  "value": "STANDING",
  "observedAtBucket": "2026-08-05T19:10+03:00",
  "proof": "single-use-unlinkable-proof"
}
```

No coordinates, account, alias, device ID, free text, media, or stable public key are permitted.

### Aggregation

- Require independent valid proofs.
- Weight recency, contradiction, presence class, and official correlation.
- Apply per-category expiry.
- Delete raw reports after aggregation or expiry.
- Retain only coarse historical buckets needed for `Typical crowding`.
- Apply minimum-group suppression before returning aggregates.

### File targets

- `ops/syrmos-api/migrations/0025_pulse_reports.sql`
- `ops/syrmos-api/migrations/0026_pulse_spent_proofs.sql`
- `ops/syrmos-api/syrmos_admin/pulse.py`
- `ops/syrmos-api/syrmos_admin/pulse_aggregation.py`
- `core/network/src/commonMain/kotlin/com/syrmos/core/network/PulseService.kt`
- `core/data/src/commonMain/kotlin/com/syrmos/core/data/repository/PulseRepository.kt`
- `feature/explore/src/commonMain/kotlin/com/syrmos/feature/explore/pulse/`

### Security and privacy gate

- A database dump cannot link two reports to one device.
- A proof-mint log cannot be joined to a redeemed proof.
- A repeated proof is rejected.
- Small groups are not exposed.
- Raw request logging is disabled and verified.
- Write capability remains off until these tests pass.

## Milestone 8: quick report and moderation

### Work

- Infer current context from tracked journey, train detail, station detail, or line segment.
- Submit an unambiguous signal on one tap.
- Ask a second tap only for a required level, such as crowd occupancy.
- Offer undo for ten seconds.
- Queue offline reports only while their context remains valid.
- Add a moderator view for high-risk signals and aggregate disputes.
- Route immediate danger to emergency guidance before accepting the signal.

### Safety exclusions

- no free-text accusations
- no staff or passenger identities
- no precise police or emergency-responder positions
- no public unmoderated track-obstruction alert
- no photo upload

### Tests

- A one-tap report enters the correct canonical context.
- An offline report expires instead of publishing late.
- Contradictory reports move an aggregate to Disputed.
- `Working again` resolves a facility report.

## Milestone 9: local contribution progress

### Work

- Store personal contribution history and badge progress only in the local database.
- Receive anonymous acceptance or consensus outcomes without a persistent profile.
- Show local levels, badges, and quality feedback.
- Replace named leaderboards with anonymous national or regional goals.
- Provide one action to clear all local progress.

### File targets

- local SQLDelight tables for contribution receipts and badges
- `feature/explore/src/commonMain/kotlin/com/syrmos/feature/explore/contribution/`
- `core/common` localization keys

### Exit gate

- Reinstalling or clearing local data removes the profile.
- The backend has no profile endpoint.
- No other user name or ranking is displayed.

## Milestone 10: Ariadne summaries

### Work

- Build deterministic summaries from `PulseAggregate` first.
- Add community confidence and freshness to every summary.
- Let an on-device language model improve phrasing only after facts are fixed.
- Keep official, live vehicle, schedule, and community sources distinct.

### Tests

- Ariadne cannot invent a report cause.
- Expired aggregates never appear in a current summary.
- Community crowding does not become an official delay.
- The same facts render correctly in English, Greek, Albanian, and Italian.

## Milestone 11: Live Activity and Apple Watch

### Work

- Extend the tracked-journey snapshot with one compact pulse state.
- Show only relevant, fresh, high-confidence aggregates.
- Add `Confirm crowded`, `Delay`, `Broken AC`, and `Normal again` Watch actions.
- Let the phone redeem anonymous proofs for Watch reports.
- Never wake the system surface for low-confidence or stale reports.

### File targets

- current iOS Live Activity and Widget targets
- `iosApp/SyrmosWatch/WatchModels.swift`
- `iosApp/SyrmosWatch/WatchContentView.swift`
- `iosApp/SyrmosWatch/WatchConnectivityProvider.swift`
- shared journey tracking snapshot models

### Exit gate

- The Watch never stores or displays a public contributor identity.
- Watch actions work without opening the phone app when connectivity permits.
- Safety signals do not become unmoderated Live Activity alerts.

## Milestone 12: release hardening

### Verification matrix

- Android, iOS, web, Apple Watch
- online, airplane mode, weak connection, stale cache
- light, dark, increased contrast, Reduce Motion
- English, Greek, Albanian, Italian
- no location permission, coarse location, tracked journey
- no reports, one report, confirmed, disputed, resolved, expired
- operator alert conflicts with community report
- replayed proof and forged proof
- clock skew and daylight-saving transitions

### Operational work

- Build a pulse health dashboard using aggregate service metrics only, without user analytics.
- Alert on proof-mint failure, write rejection rate, expiry-worker lag, and moderation backlog.
- Add a kill switch for all writes and another for public safety aggregates.
- Load-test SQLite write contention with WAL mode and transactional aggregation.
- Define database backup, restore, and automatic raw-report deletion verification.

## Recommended delivery order

```text
M0 contracts
  -> M1 preserve Network
  -> M2 Explore shell
  -> M3 universal search
  -> M4 Discover feed
  -> M5 Go by time
  -> M6 RailPulse read
  -> M7 anonymous writes
  -> M8 report and moderation
  -> M9 local progress
  -> M10 Ariadne summaries
  -> M11 Live Activity and Watch
  -> M12 hardening
```

M6 may ship before M7 using official conditions, developer fixtures, and local preview data. M7 must not ship before its unlinkability and no-log gate passes.

## Definition of done

- The old Network experience remains one tap away and functionally complete.
- Explore answers where the user can go and how long the full journey takes.
- Every time, fare, direction, alert, and community value states its source confidence.
- RailPulse reports are structured and context-bound.
- The server cannot build a contributor profile or link reports across contexts.
- Personal progress never leaves the device.
- Raw anonymous signals expire and are deleted.
- Community information is never confused with operator information.
- Safety reporting cannot become an unmoderated public emergency channel.
- Every surface works sensibly when the network disappears.
