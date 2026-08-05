# Syrmos Explore V2: implementation prompt

## Mission

Implement a production-ready Explore tab for Syrmos that combines two jobs without sacrificing either one:

1. Discover Greece through realistic public transport journeys, organized by destination, travel time, live conditions, collections, events, and Ariadne suggestions.
2. Preserve the existing network utility for searching and browsing metro, suburban rail, tram, Intercity, scenic rail, replacement bus services, stations, and lines.

The result must feel like a rail product first and a travel product second. Inspiration starts the journey, but every recommendation must immediately answer how long it takes, when the next departure is, how many changes are required, what the fare may be, and how trustworthy each value is.

Use the six SVGs in this folder as product direction, not as pixel-perfect constraints.

## Existing code seams

Start from the current common Compose implementation:

- `composeApp/src/commonMain/kotlin/com/syrmos/app/tab/ExploreTab.kt`
- `feature/lines/src/commonMain/kotlin/com/syrmos/feature/lines/LinesScreen.kt`
- `feature/lines/src/commonMain/kotlin/com/syrmos/feature/lines/LinesViewModel.kt`
- `core/domain/src/commonMain/kotlin/com/syrmos/core/domain/usecase/PlanJourneyUseCase.kt`
- existing schedule, fare, weather, announcement, location, and Ariadne repositories

Do not remove the current line filters, line grouping, station navigation, line navigation, disruption indicators, localization support, or offline behavior. Avoid a big-bang module rename. The existing `LinesScreen` and `LinesViewModel` may remain as compatibility entry points while Explore-specific state and components are extracted incrementally.

## Product structure

### Root layout

The Explore root has one sticky header and one universal search field. Under it, use a two-option segmented control:

- Discover: the default experience
- Network: the existing transport catalogue and search experience

Remember the selected segment and search state during the app session. Switching segments must not discard the query.

### Universal search

Search one local index containing:

- destinations and curated places
- stations and stops
- line IDs and localized line names
- transport modes
- events
- Ariadne intent suggestions

Group results by type. A query such as `M3` should show the line, its stations, matching destinations such as Airport, and an `Ask Ariadne` action. Search must work offline from the bundled database. Remote enrichment is optional and must never block local results.

### Discover sections

Build Discover as a lazy, independently loadable feed:

1. Destination of the day
2. Live now
3. Go by time
4. Popular today
5. Near you
6. Collections
7. Events and service alerts
8. Continue exploring
9. Ask Ariadne

Show no more than three dense sections above the fold. Destination imagery may be prominent, but journey facts and the primary action must remain readable without opening the card.

### Network section

Keep the current network browser intact and improve its hierarchy:

- mode chips: All, Metro, Suburban, Tram, Intercity, Scenic, Bus
- optional region chips after mode selection
- Nearby station card when location is available
- results grouped into Lines and Stations
- route colors, direction, next departure, disruption state, and data confidence

Network is not a secondary settings page. It is an equal mode inside Explore and remains reachable with one tap.

### Go by time

Provide time budgets of 15, 30, 60, 90, and 120+ minutes. The user can change:

- origin station or current location
- leave now or a chosen date and time
- maximum travel time
- direct-only or number of changes
- transport modes
- maximum approximate fare

Rank reachable places by total journey usefulness, not raw route duration alone. Consider wait time, walking, changes, service reliability, last return option, and user constraints.

### Collections

Initial collections:

- Beaches by rail
- Mountain escapes
- Islands with an explicit ferry connection
- Day trips
- History and museums
- Stadiums
- Universities
- Airports
- Back before sunset
- No changes
- Under a selected fare

Never imply a direct rail connection where a bus, ferry, or walking transfer is required. Show connector mode and transfer time clearly.

### Events

An event card is useful only when it includes a journey decision. Show:

- venue and event time
- nearest station
- recommended departure time
- total travel time
- last realistic return service
- disruption warning
- source and freshness

If event data is not available from a verified source, omit the section or ship editorial fixtures marked as editorial. Do not fabricate live events.

### Ariadne

Keep Ariadne grounded in deterministic transit tools and existing local data. Natural-language input may rank or filter results, but the model must not invent timetables, fares, disruptions, station IDs, or routes.

Every Ariadne journey result must be backed by the same domain objects used by regular Explore cards. Suggested prompts include:

- Somewhere quiet under two hours
- Back before midnight
- A beach with no changes
- Last train to Kifissia
- Athens to Kalambaka tomorrow

## RailPulse: community rail intelligence

RailPulse is a cross-cutting community confidence layer, not a third root segment and not a generic social feed.

It appears in these places:

- Discover: a small, personalized `RailPulse across Greece` section containing only relevant, fresh, high-confidence conditions
- Network: a compact pulse state on line and station results
- station detail: accessibility, crowding, facilities, and recent confirmations
- train and vehicle detail: occupancy, delay, temperature, cleanliness, and onboard facilities
- tracked journey: important community changes in the Live Activity and notification surfaces
- Apple Watch: four one-tap report actions based on the active journey
- Ariadne: a structured summary built only from current RailPulse aggregates

Do not add RailPulse to the bottom navigation. `Explore`, `Network`, station detail, train detail, and the tracked journey are already the correct entry points.

### Quick reporting

The default report flow must take about two seconds:

1. Infer the active train, station, line, and direction from the tracked journey or current screen.
2. Show large structured choices.
3. Submit on the first unambiguous tap.
4. Ask a second question only when a category needs a level, such as occupancy.

Initial structured signals:

- movement: normal, delayed, stopped unexpectedly, slow, waiting
- occupancy: empty, seats available, half full, standing, packed
- temperature: comfortable, too hot, too cold, AC not working, heating not working
- cleanliness: clean, acceptable, dirty
- facilities: toilets unavailable, Wi-Fi unavailable, outlets working, outlets broken
- accessibility: lift unavailable, escalator unavailable, platform inaccessible, ramp unavailable
- station: ticket office closed, crowded, parking nearly full
- resolution: working again, cleared, normal again

Avoid free-text reports in the MVP. `Other` opens another structured category list, not a paragraph field.

### Context and presence

Every report must reference a canonical context:

- `TRAIN_RUN`: operator train number plus service date
- `VEHICLE`: live vehicle ID when trustworthy
- `STATION`: canonical station ID
- `LINE_SEGMENT`: line ID plus two bounding stations
- `FACILITY`: station ID plus lift, escalator, platform, ticket office, or parking identifier

Do not submit exact GPS coordinates. Presence verification should happen on device where possible and send only a coarse proof such as `near station`, `on tracked journey`, or `recently passed station`. A user may still report without location, but the report begins with lower trust.

### Community confidence

Raw report counts are not confidence. Calculate an aggregate from:

- number of independent contributors
- recency
- anonymous proof quality and prior aggregate agreement
- presence proof strength
- agreement versus contradiction
- expected category lifetime
- whether the report matches an official alert or live vehicle behavior

Use these user-facing states:

- Awaiting confirmation
- Community supported
- High community confidence
- Disputed
- Resolved
- Expired

Never call a community report `Official` or `Operator verified`. Official alerts remain visually and semantically separate.

One anonymous proof cannot repeatedly confirm the same aggregate. Replayed, duplicated, or suspicious proofs must not multiply confidence.

### Expiry and resolution

Use category-specific expiry, extended by fresh independent confirmation:

| Signal | Initial lifetime |
| --- | ---: |
| Occupancy | 20 minutes or until the train terminates |
| Temperature and cleanliness | 60 minutes or until the train terminates |
| Delay, slow movement, unexpected stop | 30 minutes, unless live movement supports it |
| Onboard facility | Until the train terminates |
| Station crowding | 30 minutes |
| Lift or escalator unavailable | 6 hours |
| Ticket office closed | Until the expected opening period changes |
| Parking full | 60 minutes |
| Safety event | Minutes, with immediate moderation and no automatic public amplification |

Allow explicit opposite reports such as `working again` or `normal again`. A resolved item remains in recent history briefly, then disappears from the active summary.

### Contributor trust

Keep contribution identity local to the device. Do not create a server-side user, account, public contributor ID, alias, or persistent cross-report identifier.

The device may maintain private local progress based on accepted reports, later consensus, contradiction rate, and report diversity. This local score is never uploaded and never affects server-side community confidence.

Badges, levels, contribution history, and quality feedback remain on-device. Replace public named leaderboards with anonymous community goals, such as `1,284 useful confirmations this week`. Cap accepted reports per context and time window so spam never becomes the optimal strategy.

For duplicate prevention without a persistent identity, use short-lived, single-use, unlinkable report proofs. A proof may attest only that an eligible app instance has not already voted on a specific context bucket. It must not reveal a stable device identifier or allow reports across different contexts to be linked.

### AI summary

Build the first version with deterministic templates over structured aggregates:

`Running with a minor delay. Standing room only. Air conditioning confirmed working. Updated 53 seconds ago.`

A language model may improve phrasing only after the facts are fixed. It must not add causes, severity, estimated delay, or advice absent from source data. Every summary retains the aggregate timestamp and community confidence.

### Safety and emergency reports

RailPulse is not an emergency service. For medical emergencies, people on tracks, fire, violence, or an immediate obstruction:

- show emergency guidance before submission
- provide the relevant emergency and operator contact action
- submit the structured signal into a high-risk moderation queue
- do not broadcast precise location, contributor identity, police presence, or unreviewed allegations publicly
- do not send broad push notifications until the signal has sufficient independent support or official confirmation

Avoid public reports that could enable harm, harassment, evasion, or operational interference.

### Photos

Photos are outside the privacy-preserving RailPulse scope. Do not upload or accept community photos. Reports remain structured signals with no free text, media, account, alias, exact coordinates, advertising identifier, or stable device identifier.

Apply privacy constraints inline to every RailPulse operation:

- send only the canonical train, station, facility, or line context already visible in the app
- never send exact GPS coordinates or a location history
- use single-use unlinkable proofs instead of contributor identities
- keep badges, levels, history, and quality feedback on-device
- configure the origin service not to persist IP addresses, user agents, request headers, or raw request logs
- store raw anonymous signals only until aggregation or expiry
- publish only minimum-threshold aggregates
- suppress unique or rare combinations that could identify a passenger
- provide RailPulse reading without any contribution state
- keep photos, aliases, public profiles, named leaderboards, and cross-device reputation out of scope

These constraints preserve the product's no-account and no-personal-profile position. They are not permission to describe identifiable or linkable telemetry as anonymous. If implementation cannot meet the unlinkability and logging requirements, contribution writes remain disabled and RailPulse operates as a read-only official-data and local-preview layer.

### RailPulse domain model

Suggested shape, reusing current station, line, vehicle, and source-confidence types:

```kotlin
enum class PulseContextType { TRAIN_RUN, VEHICLE, STATION, LINE_SEGMENT, FACILITY }

enum class PulseConfidence {
    AWAITING_CONFIRMATION,
    COMMUNITY_SUPPORTED,
    HIGH_CONFIDENCE,
    DISPUTED,
    RESOLVED,
    EXPIRED,
}

data class PulseReport(
    val id: String,
    val context: PulseContext,
    val signal: PulseSignal,
    val value: PulseValue,
    val createdAt: Instant,
    val expiresAt: Instant,
    val presenceProof: PresenceProof,
)

data class PulseAggregate(
    val context: PulseContext,
    val signal: PulseSignal,
    val value: PulseValue,
    val confidence: PulseConfidence,
    val independentContributors: Int,
    val lastConfirmedAt: Instant,
    val expiresAt: Instant,
    val officialCorrelation: OfficialCorrelation?,
)
```

The public client never receives proof-minting secrets, raw abuse signals, IP addresses, moderation notes, or exact presence evidence.

### RailPulse API and storage

Add a versioned API surface to the existing FastAPI service:

```text
GET  /api/v1/pulse/feed
GET  /api/v1/pulse/snapshot
POST /api/v1/pulse/proofs
POST /api/v1/pulse/reports
POST /api/v1/pulse/reports/{id}/confirm
POST /api/v1/pulse/reports/{id}/contradict
POST /api/v1/pulse/reports/{id}/resolve
```

Use idempotency keys, single-use unlinkable proofs, transient edge abuse controls, payload size limits, abuse queues, and server-side context validation. The origin must not build a device or network activity history. Platform attestation may help mint a batch of unlinkable proofs, but the attestation result must not be stored beside reports and must never be required for reading.

Store raw anonymous reports, spent-proof hashes, aggregates, and moderation decisions separately. Do not store contributor profiles or cross-report trust histories. Aggregation must be transactional. Index by context type, context ID, signal, active state, and expiry. Delete raw reports after aggregation or category expiry. Retain only coarse aggregate counts required for typical-crowding models.

Start with polling and ETags. Add streaming only after load and battery evidence justify it. Queue offline reports locally with an explicit `Waiting to send` state, and discard them if their short-lived context expires before reconnection.

### RailPulse rollout

Phase 1:

- read and submit structured reports
- train and station snapshots
- confirm, contradict, resolve, and expiry
- unlinkable single-use report proofs
- rate limits and moderation console
- no photos, aliases, named leaderboards, public profiles, or server-side contributor history

Phase 2:

- on-device quality levels and private badges
- anonymous weekly community goals
- Ariadne summaries
- tracked-journey and Live Activity integration
- Apple Watch quick actions

Phase 3:

- learned occupancy patterns, clearly separated from current reports
- operator or municipality collaboration if available

Historical crowd estimates must be labelled `Typical`, never `Live`, unless fresh reports support them.

## Domain model

Introduce shared Explore models in the smallest appropriate common module. Suggested shape:

```kotlin
data class ExplorePlace(
    val id: String,
    val name: LocalizedText,
    val summary: LocalizedText,
    val stationIds: List<String>,
    val categories: Set<ExploreCategory>,
    val heroAssetKey: String?,
    val connectorModes: Set<TransportMode>,
)

data class JourneySnapshot(
    val originId: String,
    val destinationId: String,
    val totalMinutes: Int,
    val departure: Instant?,
    val arrival: Instant?,
    val legs: List<JourneyLeg>,
    val changes: Int,
    val fare: FareEstimate?,
    val confidence: JourneyConfidence,
    val lastUpdated: Instant?,
)

enum class JourneyConfidence {
    LIVE,
    SCHEDULED,
    PROJECTED,
    OFFLINE_CACHED,
}

data class FareEstimate(
    val amount: Money,
    val kind: FareKind,
    val sourceLabel: String,
    val lastVerified: LocalDate?,
)
```

Use current project types where equivalents already exist. Do not duplicate line, station, schedule, fare, alert, or route models.

## Trust and uncertainty rules

These labels are mandatory and semantically distinct:

- Live: supplied by a real-time provider and still fresh
- Scheduled: taken from a published timetable
- Projected: calculated from a frequency rule or inferred connection
- Offline: from bundled or cached data, with no network freshness guarantee
- Approx. fare: a non-booking estimate that may change

Never use `Live` as decorative green text. Never label published exact timetable entries as estimated solely because the device is offline. When offline, preserve line direction, terminal, platform when known, and the original schedule provenance. The lack of internet must not erase the train direction.

Add a compact confidence badge to every journey or departure card. On tap, explain the source and last update in plain language.

## State and data flow

Use one immutable `ExploreUiState` exposed as `StateFlow`. Keep these concerns separate:

- selected root segment
- search query and grouped results
- location and origin
- travel-time filters
- discover section loading states
- journey snapshots keyed by destination
- network filters
- errors and offline status

Load local content first. Merge remote updates into the same domain model. A network failure must leave cached and bundled results usable. Individual section failures must not blank the whole screen.

Avoid one ViewModel coroutine scope that outlives the screen without cancellation. Follow the existing project lifecycle pattern, or add a clear disposal path if the current class is retained.

## Navigation

- Destination card: open a destination detail with itinerary choices
- Journey card primary action: open full itinerary
- Route thread: open Map focused on the route and relevant vehicle
- Line result: use the existing line detail route
- Station result: use the existing station detail route
- Ariadne result: use existing `AriadneNavBus` or a typed replacement
- Collections and Events: push dedicated screens within the Explore navigator

Back navigation must restore scroll position, query, filters, and selected segment.

## Visual system

- warm off-white app background
- white surface cards with restrained elevation
- 14 to 24 dp corner radii based on card hierarchy
- transport colors only for operational meaning
- purple reserved for Explore and Ariadne actions
- 44 dp minimum touch targets
- Dynamic Type and screen-reader descriptions
- Reduce Motion support
- dark mode parity
- no glass effect behind dense transit text
- no duplicate chevrons or overlapping bottom navigation

Use real icon assets from the repository where available. Emoji in the SVGs are placeholders only.

## Localization

All user-facing copy must be localization keys for English, Greek, Albanian, and Italian. Do not concatenate translated fragments. Format duration, time, date, currency, and distance with locale-aware utilities.

Test long Greek and Albanian labels at accessibility text sizes. Cards may grow vertically and must not truncate operational information such as direction, last train, or confidence.

## Privacy and performance

- keep precise location on device
- do not add analytics or tracking
- use coarse location for destination ranking when exact position is unnecessary
- cache and resize images off the main thread
- use lazy lists and stable item keys
- avoid recomputing all routes on every keystroke
- debounce search input without delaying local exact matches
- cancel stale journey computations when filters change

## Delivery sequence

1. Extract a reusable universal search index and grouped result UI.
2. Preserve and restyle the existing Network segment.
3. Add the Discover shell with local curated destinations.
4. Add `JourneySnapshot` backed by current schedule, route, fare, alert, and location data.
5. Add Go by time.
6. Add collections and destination detail.
7. Add event-aware journeys only after a verified event source exists.
8. Reuse the journey cards in Ariadne responses.
9. Build RailPulse Phase 1 behind a remote capability flag and updated privacy disclosure.
10. Add all localizations, accessibility, dark mode, tests, and screenshots.

## Acceptance criteria

- A user can reach the old metro, suburban, tram, station, and line search in one tap.
- Search works with English, Greek, Albanian, line IDs, station names, and common aliases.
- Discover renders useful bundled content in airplane mode.
- Going offline never removes a known direction or terminal from a departure.
- Every time and fare value has a truthful confidence or provenance label.
- A recommendation never hides a required bus, ferry, walking, or rail replacement leg.
- Every destination card exposes total time, next departure, changes, and fare when known.
- A missing fare shows `Fare unavailable`, never zero.
- Live failure falls back to scheduled or cached data without changing the destination identity.
- Returning from details restores the Explore state and scroll position.
- VoiceOver or TalkBack can read the route, duration, changes, status, and action in a useful order.
- UI tests cover segment switching, search, offline mode, empty results, service disruption, and large text.
- Unit tests cover confidence mapping, time-budget ranking, connector disclosure, and localization-safe formatting.
- RailPulse is readable without an account and usable without sharing exact location.
- One unambiguous report can be submitted in one tap from an inferred journey context.
- A replayed or duplicate proof does not increase confidence.
- Active reports expire by category and may be resolved by credible opposite reports.
- Community confidence is never presented as official operator confirmation.
- Safety reports cannot trigger unmoderated broad alerts.
- Offline queued reports cannot publish after their context has expired.
- The contributor can inspect and delete all local contribution history and progress.
- The API cannot link two reports to the same person or device after a proof is spent.
- Raw anonymous report rows are deleted after aggregation or expiry.

## Do not ship

- invented event, weather, popularity, delay, or fare data
- generic tourism cards with no journey information
- remote-only search
- a second separate line browser that duplicates Network
- hardcoded English UI strings
- decorative `Live` badges
- fare purchase claims unless the flow uses an official operator destination
- tracking, behavioral profiling, or server-side storage of precise location
- public contributor profiles, aliases, or named leaderboards
- report-volume rewards
- community photo upload
- free-text allegations about staff or passengers
- exact public positions for police, emergency responders, or vulnerable people
- stable device identifiers or linkable anonymous profiles disguised as privacy preservation
