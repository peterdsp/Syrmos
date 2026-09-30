# Syrmos — complete station departures, useful Ariadne and Ichnos, and functional foldables

Prepared September 30, 2026 against checkout `a005fa59`. This is an implementation prompt; creating this document does not implement the feature.

Expanded September 30 at the user's request: retain the complete station board and also verify/fix Ariadne, improve Ichnos's practical usefulness, and establish whether the iPhone Duo experience is functionally ready. Keep this filename as the single entry point for the combined task.

## 1. User request and intended result

Replace the incomplete Home/Now departure card with a clear **station-wide departures board on iOS, Android, and the served web app**. The user must immediately see the next departures in every supported direction from the selected station complex, including metro, suburban, regional, and intercity services when they actually depart there.

The reference image shows `ATHENS / Line 2 / Elliniko / Now / then 10 min, 20 min`. That does not answer whether another train leaves toward Anthoupoli in one minute or toward Chalkida or Thessaloniki shortly afterwards. Each destination needs its own identifiable next departure. Times for different destinations must never appear as an unlabeled “then” sequence beneath one destination.

Act as a senior transit-data engineer and native/web product engineer. Implement the complete behavior, inspect each running client, and deliver tested changes. Work in `/Users/peterdsp/git/Syrmos`. Read applicable repository instructions, `DESIGN_SYSTEM.md`, and `docs/PRODUCT_PRINCIPLES.md`; preserve unrelated changes. The user's explicit requirement for all directions on this card takes precedence over interpreting “answer first” as permitting only one direction. Keep the result concise and calm with one row per destination, rather than a large full-day timetable.

Scope comprises four connected outcomes: the Home/Now all-directions board; a working Ariadne using the same grounded data; useful Ichnos information and reporting within the rider's task; and verified, functional iPhone Duo/Android foldable layouts for those flows and their Plan/GO integrations. This is not a change to the `/product` marketing page. Do not deploy, upload releases, change versions, or redesign unrelated features.

Use [the Ariadne repair prompt pack](ARIADNE-ZERO-SETUP-MULTILINGUAL-IMPLEMENTATION-PROMPTS.md) and [the foldable master plan](FOLDABLES-IPHONE-DUO-MASTER-PLAN.md) as detailed companion requirements. Their historical findings are reproduction targets, not proof of today's defects or fixes. The foldable master plan supersedes conflicting older Duo prescriptions. Build on completed work rather than restarting the implementation.

Begin with a dated status table for each outcome: **verified working**, **partially working**, **reproduced failure**, or **not yet verified**. Include the current commit, platform, reproduction steps, and evidence. Then implement the remaining work and update the table. A merged PR, an existing plan, or green unrelated CI does not answer whether Ariadne is fixed or Duo is useful. Do not stop at an audit when a reproduced gap can be fixed within this scope.

## 2. Verified starting point: fix the whole path

Recheck this baseline before editing; do not assume all clients are missing the same feature.

| Current source | Finding and implication |
| --- | --- |
| `composeApp/src/wasmJsMain/resources/web-map.js`, `setupHero()` | Chooses `data.deps[0]` for the visible line/destination and uses `data.deps.slice(1, 3)` for unlabeled later times. Both its legacy card and `answerHero` receive this single-result representation. |
| Same file, `buildStationDepartures()` | Returns immediately if `realTimetableDepartures()` has results; that can prevent other sources contributing services. Its final `.slice(0, 10)` also caps raw departures before destination coverage is established. Audit and replace station-wide source exclusivity with service-level reconciliation. |
| Same file, `realTimetableDepartures()` | Matches stop names and returns at most ten results. Name-only matching, service-date handling, and arrival-versus-departure semantics need explicit station/trip identity. |
| Same file, projected fallback | Can fill a missing direction by alternating terminal labels by result index. Do not treat that as evidence of a train's direction. Trace available route/stop/pattern metadata instead. |
| `iosApp/iosApp/Views/Home/HomeView.swift` | Already renders a direction board. `nearestUpcoming()` requests only three departures per line; selection can therefore omit a less frequent destination before grouping. |
| `iosApp/iosApp/Core/Schedule/DepartureGrouping.swift` | `directionBoard()` defaults to four rows. Groups use generated UUIDs and Home renders by offset; this is unsuitable for stable identity when rows reorder. Preserve useful existing grouping, but resolve identity and completeness. |
| `feature/home/src/commonMain/kotlin/com/syrmos/feature/home/HomeViewModel.kt` | Already requests two departures per direction per stop/line and builds a direction board. Verify branch destinations and real boarding-stop mapping, not just inbound/outbound. |
| `core/domain/src/commonMain/kotlin/com/syrmos/core/domain/usecase/HomeDirectionBoard.kt` | Defaults to four groups keyed by `(lineId, Direction)`; lacks full destination/boarding-stop identity and individual departure source metadata. |
| `core/domain/src/commonMain/kotlin/com/syrmos/core/domain/usecase/GetStationDeparturesUseCase.kt` | Ends with `.take(8)`. Reusing it unchanged can silently hide services; do not merely move the truncation to another layer. |
| `core/domain/src/commonMain/kotlin/com/syrmos/core/domain/usecase/NearestStationCluster.kt` | Groups candidates by differences in their distance from the rider. Similar distances from a person do not prove two stops belong to one interchange. Audit this before using its output as the board's station identity. |

Explain the final root cause with traced inputs and outputs: station resolution → member stops → service/destination enumeration → source reconciliation → grouping → visible rows. A visual list alone is insufficient if upstream results have already lost destinations.

## 3. Resolve Athens as a station complex, retaining real stops

The checked seed data contains these candidate members/services. This table is repository evidence to reconcile, **not a claim that each service is currently running or departing soon**:

| Boarding stop ID | Service membership found in `schedules-v2/lines.json` |
| --- | --- |
| `M2_STA` | M2, with Anthoupoli/Elliniko terminal metadata |
| `A1_ATH` | A1, Piraeus/Airport |
| `A3_ATH` | A3, Athens/Chalcis |
| `A4_ATH` | A4, Piraeus/Kiato |
| `GR_ATH` | IC1, Athens/Thessaloniki; RG1, Athens/Leianokladi |

There is also a naming mismatch: the older station seed calls `M2_STA` Athens, while the line data uses Σταθμός Λαρίσης. Some seed `line_ids` include interchange connections, not solely services boarding at that exact member stop. Resolve those differences explicitly.

Required identity contract:

- One stable station-complex ID owns a verified collection of member boarding-stop IDs, their serving routes/patterns, names, and available transfer information.
- Every departure retains its actual boarding stop and actual service. Do not send `M2_STA` to a railway schedule just because its presentation group includes railway lines.
- Prefer authoritative parent-station/transfer/route membership. Use reviewed aliases when needed; do not merge by city name, display text, rider-distance similarity, or proximity alone. Preserve distinct nearby stations.
- Keep Metro and Railway boarding areas identifiable within the Athens card. Show walking/transfer information only when available from verified data; do not invent a platform number or walking time.
- Reconcile all relevant services even when the map's selected region is Athens and a rail service is tagged national. A display-region filter must not silently erase services of the selected complex.
- Generalize this identity contract to other interchanges. Do not hardcode a special UI list of destinations only for Athens.

Inspect the existing Kotlin/Swift `StationGrouping` implementations, `NearestStationCluster`, web `buildStationNodes()`/`stationIdByLineId`, and `web-station-grouping.js` before introducing another resolver. Reuse one consistent identity rule while retaining platform-specific adapters.

User-selected or pinned station scope must remain stable. Location can establish an initial nearby default, but it must not replace an explicit selection during refresh. If location is unavailable, use the existing fallback with a clear label and station picker; never imply that a guessed station is the user's current location.

## 4. Aggregate every valid destination before limiting presentation

Enumerate the complex's actual boarding services, both valid directions, branches, short turns, and trip-specific destinations. Query the correct member stop for each. A two-value direction enum alone cannot represent all destinations of a branching route.

Use an explicit board result carrying:

- Complex identity, selected scope, generation time, and requested time window.
- Groups keyed by stable boarding area/stop, operator or network namespace, service/route, destination identity, and material branch/pattern differences.
- Individual departure identity, trip/train reference where available, actual destination/headsign, boarding stop, scheduled timestamp, predicted timestamp when supported, cancellation/status, confidence, source timestamp, and freshness.
- Coverage per service/destination: loaded, loading, unavailable, no departure in the window, or not operating with supporting evidence. Partial success remains visibly partial.

These are semantic fields; adapt existing types instead of creating duplicate session or schedule authorities. Do not expose implementation IDs in the interface.

Reconcile live observations, published schedules, valid bundled schedules, and estimates **per matching service/trip**, not by selecting a single source for the whole station. One suburban timetable response must not suppress both metro directions; one live train must not replace the rest of the station board. A vehicle position is not automatically a station departure prediction.

Deduplicate only records known to represent the same departure. Prefer a provider-qualified trip ID plus service date and boarding stop. If IDs are absent, use a documented conservative composite; never deduplicate by rounded minute or destination alone. Two real trains leaving in the same minute remain distinct. Match live updates onto scheduled trips without hiding a different service.

Default coverage should include the next 12 hours, aligned with existing station-detail behavior. Enumerate groups before limiting the number of times shown within each group. If a known service has no result in that window, show a truthful status or its next verified departure beyond the window, with a date label. Use bounded lookahead supported by the data; do not fabricate a next train to fill a row.

Determine operating days, exceptions, service dates, station offsets, pickup permissions, and trip destinations from real inputs. Exclude terminal arrivals with no onward boarding, pass-through-only stops, and nonoperating services from actionable departures. Cancellations remain understandable status information and never become the recommended next departure.

Use absolute timestamps and the Europe/Athens service timezone. Handle after-midnight service times and daylight-saving transitions without adding 24 hours to every past departure. “Now” is a narrowly defined imminence state derived from valid data, not a clamp for stale or expired records. Frequency-derived results retain Estimated labeling and must not gain fake second-level precision.

## 5. The card should answer “what leaves from here?”

Use the same content hierarchy on iOS, Android, and web:

1. Station-complex name, explicit **All directions** scope, and freshness/partial-coverage state.
2. A vertically readable list with one row per supported departure group. Show service badge and mode, actual destination, next time, source/status, and up to two later times belonging to that exact same group.
3. Emphasize the soonest valid departure gently. Avoid repeating it in an oversized hero that pushes every other direction below the fold.
4. A clear station-wide “All departures” action opens the full chronological list for the same complex. Selecting a group opens its actual destination-specific departures.

**Illustrative test fixture only — these are not current timetables:**

| Service / boarding area | Destination | Next | Later in the same group |
| --- | --- | --- | --- |
| Metro · Line 2 | Elliniko | Now · Estimated | 10 min, 20 min |
| Metro · Line 2 | Anthoupoli | 1 min · Estimated | 11 min, 21 min |
| Suburban railway | Chalkida | 8 min · Scheduled | A verified later time, if present |
| Intercity railway | Thessaloniki | 25 min · Scheduled | A verified later time, if present |

Production rows come exclusively from the validated service data. Include other supported destinations too; do not ship this four-row example as a fixed list or silently omit a fifth direction.

Default to all directions without requiring the user to pick a line or reveal a carousel. Remove the arbitrary four-group limit. Let the card/page grow and scroll naturally; long lists can use a lazy renderer while retaining all group rows. A compact teaser on a space-constrained secondary surface must explicitly say it is a preview and link to the full board. It must not claim to show all directions while hiding rows.

Sort active groups by their next eligible effective departure timestamp, with stable tie breakers. Do not let frequent metro departures crowd less frequent railway destinations out of the board. Keep unavailable/no-service groups in a clearly differentiated state after timed results. Refresh countdown text without rebuilding every row or reordering on each rounded-minute tick.

“Then” applies only within a row. If later times have different confidence or cancellation states, label them individually or avoid a misleading group-wide badge. Cached live observations must retain their age and cannot remain Live indefinitely.

Keep the existing brand, line colors, and platform controls. Use warm readable surfaces and clear destination typography. Permit destination wrapping and stacked metadata at large text sizes. On unfolded devices/tablets, place this board beside its map; on phones, keep a readable vertical list. Folding, resizing, refresh, and localization must preserve the selected complex, row identity, scroll anchor, and any open departure detail.

## 6. Give every action an unambiguous target

- **Station:** opens the selected complex and all its actual member services, preserving current filters only when visibly indicated.
- **Departure row:** opens the matching group/trip detail, not whatever happens to be the new first result when the tap is handled.
- **Track:** targets the explicitly selected real departure. If no departure is selected, present a clear picker or label the button with its featured service/destination. Preserve each platform's supported tracking behavior; do not imply live tracking where only schedule/map information exists.
- **Map:** focuses the selected boarding stop/service while preserving manual camera intent unless the user requests recentering.

Audit the web card's existing Track and Station handlers: both currently call `selectStation(...)`. Correct misleading labels or route them to meaningful distinct behavior within current capabilities. Merely showing a map does not prove that a specific train has begun tracking. Never start GO or replace an active tracking session because a row moved or became the soonest result.

## 7. Platform implementation map

| Platform | Existing files to inspect and extend |
| --- | --- |
| Web | `composeApp/src/wasmJsMain/resources/web-map.js`, `web-departures.js`, `web-station-grouping.js`, `index.html`, `web-map.css` |
| iOS | `iosApp/iosApp/Views/Home/HomeView.swift`, `Core/Schedule/DepartureGrouping.swift`, `Core/Schedule/ScheduleProjector.swift`, `Core/Schedule/TransitData.swift`, `Features/Assistant/StationGrouping.swift`, `Views/Stations/StationDetailView.swift` beneath `iosApp/iosApp/` |
| Android/Kotlin | `feature/home/.../HomeViewModel.kt`, `HomeScreen.kt`; `core/domain/.../usecase/HomeDirectionBoard.kt`, `NearestStationCluster.kt`, `GetNextDeparturesUseCase.kt`, `GetStationDeparturesUseCase.kt`; station grouping and departure models |
| Data | Station seeds, `schedules-v2/lines.json`, transfers, route patterns, calendars, station offsets, live/published schedule adapters |

On web, extract pure aggregation/selection logic into the existing departures module or a focused new module that Node tests can exercise. Update both Home card representations and the compact peek summary from the same board state; do not create competing polling loops. Preserve service-worker and standalone product-page behavior.

On iOS, reuse the existing board and replace unstable group/view identities and premature per-line truncation. On Android, retain actual destination and boarding-stop identity beyond `Direction`, and preserve per-departure confidence through board conversion. Extend query contracts or coverage metadata if existing server limits cannot supply one result per group; requesting an arbitrarily large global count is not proof of completeness.

Use shared deterministic fixture data and equivalent semantic assertions across Kotlin, Swift, and JavaScript. Share behavior, not a forced UI framework. Keep state ownership above replaceable layout branches and reuse existing repositories and lifecycle-aware subscriptions.

For existing watch/widget surfaces that consume the same provider, preserve correct selected-trip identity and source labels. Their fixed-size summaries may show fewer rows with an honest deep link. This task requires the full board on the three main app clients; it does not require cramming every direction into a small widget.

## 8. Verify that Ariadne is fixed, and finish any remaining repair

Answer explicitly for iOS, Android, and web: **Is Ariadne usable now, which earlier issues are fixed, and what still fails?** Reproduce the repair pack's scenarios on the current version. Preserve fixes already present. The earlier hidden-panel accessibility fix and launcher-spacing fix are not evidence that conversational understanding, data grounding, or actions work.

### Required useful behavior

- Core transit answers work on a clean install with bundled data and no mandatory model download, account, API key, system AI, or extra setup. The optional intelligence path cannot delay a known local answer or quietly download a model. An offline answer must state the limits of its data.
- Opening Ariadne from Athens carries an explicit station context. Asking “What leaves from here?” returns the same normalized groups and times as the all-directions board, including all supported modes. Ariadne must not implement a second, inconsistent departures calculation.
- Follow-ups such as “And toward Anthoupoli?”, “What about Chalkida?”, “The other direction”, and “After this one?” resolve against conversation and station context. If more than one interpretation remains possible, ask a focused clarification with real selectable candidates.
- Changing station, date, language, or destination deliberately updates context. “From here” must not silently refer to an old station or assume GPS permission. Retain canonical entity IDs behind localized names.
- Replies contain concise useful results, source/freshness, and working actions: open the station, inspect departures, preview a route, or track a specifically selected service when that capability exists. Verify the resulting screen and identifiers after tapping; a decorative button or textual promise is not an action.
- Questions about delays, crowding, or station conditions may include relevant Ichnos evidence, clearly labeled Community. Separate that from operator notices and schedule/live information. Missing reports or a failed request never become “everything is fine.”
- Support the app's current English, Greek, Albanian, and Italian coverage end to end: question understanding, clarification, response, labels, errors, and actions. Use the existing multilingual cases plus ordinary paraphrases, station aliases, and corrections.
- Preserve one conversation, unsent draft, pending clarification, and selected result through sheet/pane changes, keyboard use, background recovery, and folding. Serialize turns, support Stop/Retry where appropriate, reject late canceled results, and prevent duplicate messages or actions.

Test clean offline launch, normal online use, slow/unavailable providers, missing data, rapid submissions, cancellation, and resume. Measure time to a usable answer for core local questions and report the measurement; do not call a spinner or an eventual cloud fallback “ready.” Keep reported accessibility facts separate from current equipment availability.

Inspect the existing implementation at `iosApp/iosApp/Features/Assistant/`, `iosApp/iosApp/Core/Networking/AriadneAPIService.swift`, `feature/home/src/commonMain/kotlin/com/syrmos/feature/home/assistant/`, `core/domain/src/commonMain/kotlin/com/syrmos/core/domain/assistant/`, the served `web-ariadne.js`/`web-map.js` integration, and `ops/syrmos-api/syrmos_admin/ariadne.py`. Extend their existing orchestration and typed domain actions; do not introduce a competing planner or embed operational facts in model instructions.

## 9. Make Ichnos useful for the journey in front of the rider

Ichnos already has community summaries, reporting, history, and contribution-related UI in the inspected source. Much of that code still uses the name RailPulse. Improve the real feature rather than building another dashboard or treating the older implementation plan as evidence that nothing exists.

### Required product improvements

| Rider need | Required behavior |
| --- | --- |
| “Does this affect my departure?” | Show relevant station/service/direction/trip information beside the selected departure or journey. Use the narrowest supported scope and visibly label broader line-wide information. A report for the opposite direction must not appear as a confirmed issue on this train. |
| “What was reported, and how recently?” | Name the issue, applicable boarding area or service, report age, and supported confirmation state. Distinguish loading, unavailable, no recent reports, active, disputed, resolved, and expired where the backend supports those states. |
| “What can I do?” | Offer useful existing actions: open affected service details, check the operator notice, inspect the next departure, or preview planner-backed alternatives. Keep the user's current journey until they deliberately choose a change. |
| “I can contribute something useful.” | Open a short contextual report flow from station, service, or journey detail with the target prefilled and editable. Use supported structured categories, make scope understandable, and require an explicit send action. |
| “Did my report go through?” | Show genuine pending/sent/failed state, retry without duplicates, and working undo where supported. Increment any local contribution count only after confirmed acceptance; reconcile undo and retries. |
| “There is little community activity.” | Show an honest empty or stale state with a useful reporting entry point. Continue to show available official service information. Do not populate the feed with synthetic activity or reassuring invented totals. |

Prioritize relevant issues and practical actions above badges, contributor levels, historical totals, and estimated journey counters. Keep those secondary features only where they help; estimated journeys must never be presented as people, reports, or confirmations. A count of anonymous reports alone does not prove independent contributors or high confidence.

Use the same station-complex/member-stop and route/destination IDs as the new departure board. Explain when only a station-wide or line-wide summary is available rather than inventing a direction-specific association. Never automatically convert a community delay report into an exact ETA, treat crowding as a cancellation, or convert a static accessibility field into a claim that a lift is working.

Retain source timestamps and explicit expiry. Verify the current backend's active window, retention, undo, and history behavior; make the UI match the actual contract. An expired or failed-to-refresh report cannot remain a current warning without a stale label. Contradictory reports should remain understandable instead of silently canceling each other out into a green status. Preserve the existing privacy and publication rules when adding fields; the usefulness improvements do not require accounts, raw GPS histories, personal profiles, or free-form personal information.

Audit and complete parity on iOS, Android, and served web. If a main client lacks an Ichnos entry point, add a coherent contextual read/report path backed by the actual service contract; do not substitute a screenshot or inert “coming soon” card and call parity complete. Where writes are unavailable or intentionally gated by the existing publication/privacy contract, retain the useful read-only flow and explicitly report that limitation rather than bypassing the gate.

Implementation entry points:

- iOS: `iosApp/iosApp/Views/Lines/ExploreRailPulseView.swift` and `RailPulseDetailViews.swift`; the first currently contains `IchnosCommunityService` and summary/report/history contracts.
- Kotlin/Android: `feature/lines/src/commonMain/kotlin/com/syrmos/feature/lines/ExploreRailPulseContent.kt`, `RailPulseDetailScreens.kt`, `core/network/src/commonMain/kotlin/com/syrmos/core/network/CommunityReportService.kt`, and `core/common/src/commonMain/kotlin/com/syrmos/core/common/RailPulseLocalStore.kt`.
- Backend: `ops/syrmos-api/syrmos_admin/community.py` and its existing reporting tests; inspect actual endpoints before changing clients.
- Web: audit existing served entry points and reuse the API semantics. Add an isolated module if needed instead of growing unrelated map behavior.

Use local/test services for submission, retry, undo, and expiry verification. Do not send fabricated reports into the production community feed. If backend work is necessary, implement and test it locally and describe the deployment dependency separately.

## 10. Prove that iPhone Duo is ready and adds useful functionality

Answer two different questions with evidence: **Does the actual app work correctly on Duo? What useful work becomes easier when it opens?** Both must pass. Additional columns, larger cards, a successful build, and color-block snapshots are insufficient.

Follow the current foldable master plan. It records that Xcode 27.1 and a Duo simulator were available during its September 26 inspection; inventory the tools again instead of repeating older “SDK unavailable” statements. Prove which native SDK branch is compiled and exercised, including `SYRMOS_DUO_SDK` where still used. Never report a fallback layout running on a Duo destination as proof of native arrangement/region integration. Separate unavailable physical-hardware checks from simulator-testable work and finish all available verification.

### Functional use of the displays

| Flow | Closed/compact task | Useful open-display behavior |
| --- | --- | --- |
| All-direction station board | Read every destination in a clear scrolling card | Keep destinations visible beside the map and selected boarding/service detail; selecting either pane updates the same entity. |
| Plan | Enter endpoints, compare options, inspect an itinerary | Keep route choices alongside the selected route map and transfer details; changing the choice updates context without losing the query. |
| Active GO | Current instruction and reachable recovery/end actions | Keep the current instruction and leg progression beside the route/next-transfer context with real leg colors and one active session. |
| Ariadne | Focused conversation with functional result actions | Keep the conversation beside the station board, selected itinerary, or map it refers to. Act on an answer while retaining the question and relevant context. |
| Ichnos | Read a relevant issue or submit a scoped report | Inspect issue context alongside the affected station/service and available alternatives. A report composer can coexist with the task when space permits. |

Keep primary navigation distinct from task panes. Side-by-side content must be related and independently usable, with one selection authority and appropriate scrolling. Do not open Ariadne automatically just to fill empty space. Use a third inspector only when it helps and fits; preserve meaningful content at large text sizes.

In book posture, pair related views around system-reported regions. In tabletop, keep useful map/overview content above and reachable task controls below where the native arrangement and usable space support that choice. Ordinary lists must retain scroll continuity. Do not make a feature available only in one pose, force controls through an occlusion, or infer geometry from a marketing device name.

Exercise the real app on the outer display, inner portrait and landscape, partial book/tabletop postures, both sides of system multitasking, constrained-height/video arrangements where supported, and with the keyboard open. Verify native bars, overflow, sheets, popovers, safe areas, active reserved regions, readable destinations, and accessible controls. Use the master plan's full applicable acceptance matrix rather than just these summaries.

Preserve the station, selected departure, list anchor, map camera intent, Plan draft, active GO ID, Ariadne conversation/draft/pending work, and Ichnos report draft through transitions. Folding cannot submit a report, resend a question, restart a journey, advance a leg, or reset the map. Test layout-only transitions with the clock and external inputs held constant, then test legitimate live events arriving during transitions separately.

Implement equivalent task benefits on Android foldables using their actual fold/hinge information and native conventions. Keep ordinary iPhone/iPad, Android phone/tablet, and web behavior correct. For web, verify responsive layouts in the supported browser configurations; do not claim access to device-specific native posture APIs without actual support.

### Evidence of usefulness

For each flow in the table, record one complete compact and open-device walkthrough using the same deterministic task. Capture the action sequence, navigation steps, visible decision information, and result. At minimum, the open layout must let the rider inspect related content while retaining the main task, with all controls working and no additional mandatory navigation. Fix panes that show empty maps, unrelated feeds, duplicated headers, or stale selections. Report measured results without invented productivity percentages or speed claims.

Update the current-status portion of `docs/design/FOLDABLE-READINESS.md` with fresh commit/tool/runtime evidence. Preserve historical entries as history and reconcile stale statements with completed scene-restoration work. Report native Duo runtime readiness, Android foldable readiness, ordinary-device regressions, and remaining physical-device checks separately.

## 11. Verification and acceptance

Add meaningful tests alongside `HomeDirectionBoardTest.kt`, `NearestStationClusterTest.kt`, `DepartureGroupingTests.swift`, `StationGroupingTests.swift`, `web-tests/departures-grouping.test.js`, and `web-tests/station-grouping.test.js`. Add a focused web Home-board integration test if none exists. Reuse existing time infrastructure and control live/feed/location inputs.

Required cases:

1. Athens fixture contains both M2 directions plus validated suburban and intercity services, each using its correct boarding stop.
2. One direction leaves Now and the opposite direction in one minute: both are immediately represented with correct labels.
3. More than four groups, and many early departures on one frequent line: no destination disappears before or after grouping.
4. A published rail response and projected metro data coexist; one source cannot suppress the other mode.
5. A branch/short-turn route has more than two destination patterns: real headsigns remain distinct.
6. Duplicate source records for one trip collapse; separate same-minute trains survive.
7. Stop aliases resolve consistently; nearby unrelated/equidistant stops do not enter the complex. Interchange line unions never substitute for actual boarding membership.
8. Terminal arrivals, non-pickup stops, canceled trains, suspended services, date exceptions, midnight, and both DST changes behave correctly.
9. No scheduled departure, unavailable source, and offline cached data render different truthful states. Partial coverage cannot look complete.
10. Live delay updates change the matching departure and ordering without duplicating it or transferring its Live label to other times.
11. No location permission, manual station choice, region change, refresh, background/foreground, and host/activity restoration preserve intended station scope.
12. Track/Station/row actions retain the correct destination and departure even when new data arrives during interaction.
13. iOS, Android, and web produce equivalent normalized groups, order, next times, and status from the same fixture/clock, including Greek, English, Albanian, and Italian labels.
14. Compact, unfolded, large-text, dark/light, VoiceOver/TalkBack/keyboard, and reduced-motion layouts remain readable. Preserve screen-reader focus and do not announce every countdown second.
15. Actual app captures show the complete Athens board on each main platform. Fixture screenshots are clearly marked as fixtures; live screenshots are never treated as a reproducible timetable.

Additional integrated cases:

16. On a clean install in each supported language, Ariadne answers a supported station/departure question without model setup; offline answers retain honest provenance.
17. “What leaves from here?” followed by a destination correction yields the same board results and correct working action on each platform. Unknown/ambiguous places produce useful clarification.
18. Optional provider timeout, cancellation, rapid turns, keyboard use, and scene/pane replacement do not lose drafts, duplicate turns/actions, or accept a stale result.
19. Ichnos reports for one station/direction stay attached to that scope. Opposite-direction, line-wide, stale, contradictory, empty, and unavailable fixtures render distinctly.
20. Report submission, failure/retry, undo, and expiry work against a test backend; neither repeated taps nor folding produce duplicate accepted reports or contribution counts.
21. Ariadne describes community evidence as community evidence; operator alerts retain their source, and neither rewrites a departure time without an applicable authoritative prediction.
22. From a station row, inspect relevant Ichnos context, ask Ariadne, preview a verified alternative, and return to the same selected station/departure. Do not lose or silently replace an active journey.
23. Each Duo workflow in section 10 works end to end on the actual installed app, including native arrangements/bars and both panes' actions. Provide runtime captures, not only isolated fixture views.
24. Repeated fold/unfold, rotation, system multitasking, keyboard, and host/activity restoration retain every state listed in section 10 and keep side-effect counts correct.
25. Compact and open-layout walkthroughs demonstrate the promised functional benefit for departures, Plan, GO, Ariadne, and Ichnos on Duo and applicable Android foldable configurations.
26. Supported-language, accessibility, dark/light, offline, and unavailable-service checks cover the new Ichnos/Ariadne content and its cross-pane focus order, not just the departure rows.

Run the relevant existing tests/builds and inspect each running client. Verify the complete path from feed/seed through board to action, not only helper output. Record unavailable platform checks honestly; a common JVM test or static screenshot is not a substitute for native behavior.

## 12. Execution, completion, and handoff

Execute in reviewable slices: establish current status; fix station identity/aggregation; complete board rendering/actions; verify and repair Ariadne against the same domain contract; make Ichnos contextual and actionable; finish functional foldable composition; then run the integrated scenarios and reconcile readiness evidence. Reuse completed slices and rerun affected checks after changes. Do not postpone every runtime check until the end.

Deliver the implementation, a short explanation of source reconciliation and station membership, the changed tests/results, and compact/expanded screenshots for iOS, Android, and web. Include Ariadne actions, useful Ichnos context/reporting, and recordings of real Duo and Android foldable transitions. List services without sufficient verified data and show their explicit coverage states. Keep local completion, backend deployment needs, public deployment, and store release as separate facts.

End the implementation report with explicit evidence-backed answers:

| Question | Required answer |
| --- | --- |
| Are all station directions represented? | State platform coverage, validated station membership, omitted/unavailable services, and board tests. |
| Is Ariadne fixed? | List reproduced earlier issues, existing fixes confirmed, new fixes, remaining failures, and clean-install/multilingual/action evidence. |
| How is Ichnos more useful? | Demonstrate relevant information, working next actions, correct reporting scope, freshness, and tested submission/retry/undo behavior. |
| Is iPhone Duo ready? | State verified native runtime behavior, completed functional workflows, regression results, and precise pending criteria. |
| What does opening the device improve? | Show the paired tasks and real action sequences that preserve context, with captures and observations. |

Use **Verified**, **Partial**, or **Blocked by a named dependency** for each outcome and platform, with evidence. Do not call the whole task complete while a required, executable scenario fails or remains untested. Finish independent work when an external dependency blocks one part, and record the remaining action precisely.

Complete when the rider can see **every supported upcoming destination**, trust and act on Ariadne's answers, use Ichnos to understand relevant conditions or contribute a scoped report, and gain practical context and control by opening a foldable—while the same task remains intact when the device closes.
