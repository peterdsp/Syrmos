# Syrmoula: an offline, on-device Athens transit helper

A codex-brainstorm pass on the "chat button" idea: a help assistant inside Syrmos that a user can ask, in their own words, to do things in the app and answer questions, while staying fully offline and strictly limited to Syrmos and Athens public transport.

Structure: name, repo findings, competing positions, three debate rounds, equilibrium recommendation, rejected ideas, and a phased Android/iOS delivery plan.

Final recommendation up front: build a **constrained offline intent router**, not a free-form chatbot. The helper turns natural language into a fixed set of approved actions over the deterministic tools the app already has, refuses anything outside Athens transit, and treats an on-device language model as an optional natural-language front-end on capable devices, never as the source of truth.

## 0. The name

Recommended: **Syrmoula** (Συρμούλα). It is the affectionate Greek diminutive of *syrmos* (συρμός, a train set), literally "little Syrmos." It ties straight to the app name, it reads as a pet name rather than a corporate "Assistant," and it is easy to say in all three supported languages. The pitch line writes itself: "Ask Syrmoula." A chat bubble with a tiny train face is the mascot.

Two alternatives if a different tone is wanted, each a one-word swap throughout this doc:

- **Trenaki** (Τρενάκι, "little train"). Maximum cute and universally legible to tourists. Most viral, least tied to the Syrmos brand.
- **Ariadne** (Αριάδνη). The thread that guides you out of the labyrinth. Elegant and unmistakably Athenian, leans premium-guide rather than cute-mascot.

The rest of this document uses **Syrmoula**. Everything below holds regardless of which name is chosen; the name does not change the architecture.

---

## 1. Repo findings

The premise "we would have to build the transit brain from scratch" is wrong. Most of the deterministic engine an assistant needs is already in the codebase. The assistant is a thin natural-language layer over tools that already exist and are already offline.

Tools that already exist (the action surface):

- Next departures, offline, station-aware: [ComputeDeparturesFromBandsUseCase.kt](core/domain/src/commonMain/kotlin/com/syrmos/core/domain/usecase/ComputeDeparturesFromBandsUseCase.kt) and [GetNextDeparturesUseCase.kt](core/domain/src/commonMain/kotlin/com/syrmos/core/domain/usecase/GetNextDeparturesUseCase.kt), plus the iOS mirror [ScheduleProjector.swift](iosApp/iosApp/Core/Schedule/ScheduleProjector.swift).
- Last train tonight, just shipped: [GetLastTrainUseCase.kt](core/domain/src/commonMain/kotlin/com/syrmos/core/domain/usecase/GetLastTrainUseCase.kt) and `ScheduleProjector.lastTrainTonight`.
- Station search: [SearchStationsUseCase.kt](core/domain/src/commonMain/kotlin/com/syrmos/core/domain/usecase/SearchStationsUseCase.kt).
- Nearest station from GPS: [FindNearestStationUseCase.kt](core/domain/src/commonMain/kotlin/com/syrmos/core/domain/usecase/FindNearestStationUseCase.kt).
- **Routing already exists**: [PlanJourneyUseCase.kt](core/domain/src/commonMain/kotlin/com/syrmos/core/domain/usecase/PlanJourneyUseCase.kt) runs Dijkstra over a station graph with transfer edges at interchanges, returning [JourneyResult](core/model/src/commonMain/kotlin/com/syrmos/core/model/planner/JourneyResult.kt) / [JourneySegment](core/model/src/commonMain/kotlin/com/syrmos/core/model/planner/JourneySegment.kt). Earlier framing assumed "no routing, single line only." Not true. A `PlanTrip` intent maps straight onto this.
- Lines and line detail: [GetLinesUseCase.kt](core/domain/src/commonMain/kotlin/com/syrmos/core/domain/usecase/GetLinesUseCase.kt), [GetLineDetailUseCase.kt](core/domain/src/commonMain/kotlin/com/syrmos/core/domain/usecase/GetLineDetailUseCase.kt).
- Station detail: [GetStationDetailUseCase.kt](core/domain/src/commonMain/kotlin/com/syrmos/core/domain/usecase/GetStationDetailUseCase.kt).
- Service alerts and status, with a bundled last-known snapshot for cold offline launch: [AnnouncementsRepository.kt](core/data/src/commonMain/kotlin/com/syrmos/core/data/sync/AnnouncementsRepository.kt).
- **Favorites already scaffolded**: a `favorite_entity` table with `getAllFavorites` / `isFavorite` / `insertFavorite` in [SyrmosDatabase.sq](core/database/src/commonMain/sqldelight/com/syrmos/core/database/SyrmosDatabase.sq), and `favoriteStationIds` / `favoriteLineIds` in [AppSettings.kt](core/model/src/commonMain/kotlin/com/syrmos/core/model/settings/AppSettings.kt). "Favorite this station" is wireable now, not a future dependency.
- Map navigation exists via the map feature and `MapViewModel`.

Data and platform facts that constrain the design:

- Offline-first is already the contract (see [docs/PRODUCT_PRINCIPLES.md](docs/PRODUCT_PRINCIPLES.md) and the offline-alive work). Bundled JSON drives stations, lines, schedules, fares, and offsets. Only announcements, live positions, and manual refresh touch the network. An assistant that answers from these tools is offline by construction.
- **Three languages, not one**: English, Greek, Albanian ([Localization.kt](core/common/src/commonMain/kotlin/com/syrmos/core/common/Localization.kt)). The assistant must parse Greek and Albanian input, not just English. This is the single hardest constraint for any small on-device model, and it pushes hard toward a deterministic parser as the floor.
- The team's existing bias is deterministic pipelines over device ML: STASY announcements are translated server-side at ingest ("no on-device translation", per the CASE_STUDY revision log). That precedent matters for the "how smart is the brain" debate.
- **Station metadata is thin**: [Station.kt](core/model/src/commonMain/kotlin/com/syrmos/core/model/transit/Station.kt) has only `accessibility: Boolean` and `zone: Int`. The rich "comfort" fields a weather-aware assistant would want (underground vs exposed, covered entrance, outdoor walk length, transfer exposure) do not exist yet. Those are net-new bundled data, not something a model can infer.
- **This is already on the roadmap**: CASE_STUDY Appendix K lists an "Optional AI chat helper for natural-language queries" and the revision table targets it at **1.5 (Q4 2027)**. So this brainstorm is sequencing a planned feature, not inventing scope.
- Architecture: shared KMP `core` (the tools), Compose for Android and Web, native SwiftUI for iOS. A shared intent layer fits in KMP; the natural-language engine and the chat surface are per-platform.

Net: the deterministic brain is ~80 percent built. The open question is only the natural-language front-end and the scope fence.

---

## 2. Competing positions

Four coherent philosophies, stated at their strongest.

### Position A: No chat box. Make the assistant invisible.
The product north star is "companion, not schedule," and rule 4 is explicitly low-decision: "a mode the user has to choose is a decision we failed to make for them." A blank chat box is the most high-decision surface there is. So do not ship one. Take every query an assistant would answer and push it proactively instead: answer-first home, last-train teaser, widgets, Live Activities. The "assistant" is the app already having done the thinking.

### Position B: Constrained intent router (tool-only).
Ship a chat/voice entry, but the brain only classifies intent and extracts slots. It maps text to a fixed `AssistantIntent` enum and calls the existing use cases. The model, if any, never generates a transit fact. It picks an approved command and fills its parameters; the deterministic tool produces the answer. Offline, trilingual via a rule parser, refuses out-of-scope by design.

### Position C: On-device generative assistant.
Use a real on-device LLM (Apple Foundation Models on Apple Intelligence devices, Gemini Nano via AICore / ML Kit GenAI on supported Android) to converse, summarize alerts in plain language, and explain routes, grounded by tool outputs. More magical, handles long-tail phrasing, can chain steps. Costs: hallucination risk even when grounded, uneven device coverage, weak Albanian, app size and RAM and battery.

### Position D: Cloud LLM.
A hosted model (any provider) behind the chat button. Best raw language quality, trivial to ship. Eliminated immediately by the hard requirement: offline-only, on device, no server inference, no logs. Listed for completeness so the rejection is on the record.

---

## 3. Debate rounds

### Round 1: Does Syrmos even want a chat box? (A vs B/C)

A presses: the homepage work just made the app answer-first. A chat box reintroduces the exact failure mode the principles doc warns against, a blank surface that hands the user the work of forming a query. Worse, it invites out-of-scope questions the product then has to refuse, which feels like a downgrade from a focused app.

B/C answer: the fixed UI can only express what its screens were designed for. It cannot answer "weekend M3 to the airport after 10pm," "which nearby station is easier in the rain," or "last train from here to Piraeus" in one step, even though the tools to answer all three already exist. The assistant is not a new brain, it is a different door into the brain. And it does not have to be a blank box: open it with suggestion chips, a "from here" default from GPS, and voice. That is low-decision if the entry is designed as answer-first.

Convergence: A's objection reshapes the entry surface rather than killing it. The assistant must never be an empty prompt. It opens with the two or three most likely actions for the user's current context, pre-filled. The chat is the long-tail escape hatch, not the front door. A's "invisible assistant" stays the priority for common cases; the assistant covers the long tail the proactive surfaces cannot.

### Round 2: How smart is the brain? (B vs C)

C presses: a rule parser is brittle. Real users type "trains aprt sat nite" and Greeklish and half-sentences. Only a model degrades gracefully across phrasing, and on-device models exist now for exactly this.

B answers with three hard facts from the repo:
1. **Trilingual.** The app ships English, Greek, and Albanian. Apple Foundation Models and Gemini Nano handle English well, Greek passably, Albanian poorly. A model-only design silently fails one of three supported languages. A rule parser can cover all three because the vocabulary of transit (station names, line ids, "next," "last," "weekend," "from," "to") is small and enumerable.
2. **Determinism.** A tool-only router cannot state a wrong departure time, because it never states times at all, it calls the projector. A generative model can hallucinate a schedule even when grounded, and a single confident wrong "last train 00:40" destroys trust in a safety feature.
3. **Coverage and cost.** On-device LLMs run only on recent hardware and cost RAM, battery, and startup latency. The rule parser runs on every device the app already supports, instantly.

C's legitimate point survives: for messy phrasing on capable devices, a model is genuinely better at the classify-and-extract step.

Convergence: the brain is layered, not chosen. The rule parser is the floor and the fallback, always present, trilingual, deterministic. The on-device model is a progressive enhancement on capable devices that does only one job, turn fuzzy text into a structured `AssistantIntent` plus slots, whose output is then validated against the same schema and rejected if malformed. The model never emits the answer. This is Position B's spine with Position C bolted on as an optional slot-filler. It matches the team's existing "deterministic core, model at the edges" bias.

### Round 3: Scope fence, weather, and closures (all positions)

The user wants two things that look like scope creep but are not: weather should be allowed (bad weather changes how you travel), and the assistant should warn when a station might be hard to reach or closed.

Debate resolves the fence precisely:

- **Weather is a routing constraint, not a topic.** "What is the weather in London" is refused. "It is raining, get me to the airport with less walking" is in scope, because the deliverable is still an Athens transit route. The model never answers weather. Offline, the assistant says plainly "I cannot check live weather offline" and still produces the lower-exposure route. If a fresh cached weather reading exists, it is used with a timestamp.
- **Station approach difficulty is offline-answerable, but only with new data.** Today [Station.kt](core/model/src/commonMain/kotlin/com/syrmos/core/model/transit/Station.kt) has a single `accessibility` boolean. To answer "which nearby station is easier in the rain," the app needs a `StationAccessProfile` layer: underground vs exposed, covered entrance, outdoor walk length, transfer exposure. That is bundled metadata to author, not something to infer. Until it exists, the assistant must not pretend to know.
- **Closures are not weather-derivable.** The assistant must never say "station closed because of rain." Closure comes only from official alerts. Offline, it reports last-known status with a timestamp and honest uncertainty: "Last cached status, normal service as of 09:30. I can route you with less walking."

Convergence: the fence is "Syrmos and Athens public transport only," and weather and accessibility enter solely as constraints on transit answers, with graceful, explicit degradation when offline data is missing or stale. Honesty about what it does not know is a feature, consistent with the offline-alive philosophy already in the app.

---

## 4. Equilibrium recommendation

Build **Syrmoula** as a constrained offline intent router (Position B as the spine), with an optional on-device language model (Position C) as a natural-language front-end on capable devices, and a trilingual rule parser as the permanent floor and fallback. Keep Position A's discipline: Syrmoula is the long-tail door, the proactive surfaces stay the front door. (`AssistantIntent` and friends below are kept as neutral code identifiers; Syrmoula is the product-facing name.)

Shape:

1. **Shared `AssistantIntent` schema in KMP.** A sealed class enumerating only approved actions:

   ```kotlin
   sealed interface AssistantIntent {
       data class ShowDepartures(val stationId: String?, val lineId: String?, val day: DayContext?) : AssistantIntent
       data class LastTrain(val stationId: String?, val lineId: String?) : AssistantIntent
       data class FindStation(val query: String) : AssistantIntent
       data class PlanTrip(val fromStationId: String?, val toStationId: String?, val constraint: TripConstraint?) : AssistantIntent
       data class ExplainLine(val lineId: String) : AssistantIntent
       data class ExplainFare(val zoneOrRoute: String?) : AssistantIntent
       data class ShowServiceAlerts(val lineId: String?) : AssistantIntent
       data class OpenMap(val stationId: String?) : AssistantIntent
       data class ToggleFavorite(val stationId: String) : AssistantIntent
       data class WeatherAwareTripHelp(val fromStationId: String?, val toStationId: String?) : AssistantIntent
       data class HelpUsingApp(val topic: String?) : AssistantIntent
       data object OutOfScope : AssistantIntent
   }
   ```

2. **Tool registry binding each intent to an existing use case.** `ShowDepartures` to the projector, `LastTrain` to `GetLastTrainUseCase`, `PlanTrip` and `WeatherAwareTripHelp` to `PlanJourneyUseCase`, `FindStation` to `SearchStationsUseCase`, `ShowServiceAlerts` to `AnnouncementsRepository`, `ToggleFavorite` to the favorites table, and so on. The registry is the only thing allowed to produce answers.

3. **Two interchangeable parsers behind the same schema.** A trilingual rule parser (always on), and an on-device model adapter (Apple Foundation Models on iOS, Gemini Nano on Android) used only where available to fill the intent and slots. Every model output is validated against the schema; anything that does not parse cleanly falls back to the rule parser or to a clarifying question.

4. **Clarify, never guess.** Missing a required slot (station, line, or day) triggers one focused question, the same pattern the transcript settled on: "From which station?" Resolution then uses the deterministic tool, not model memory.

5. **Low-decision entry, honoring the principles.** The assistant opens with context chips ("Next train from here," "Last train home," "Plan a trip," "Alerts") and supports voice. Never a blank box.

6. **Hard scope fence with honest offline degradation.** Out-of-scope to a polite refusal. Weather and station-comfort only as transit constraints. Missing or stale data stated plainly with a timestamp.

Why this is the equilibrium: it gives the user the feel of an AI chat button without betting the app on hallucinations, it is fully offline for normal answers, it works on every supported device (model is additive, not required), it respects the trilingual reality, and it reuses the deterministic engine the repo already ships. It satisfies the companion rules: answer-first entry, reassuring honesty about uncertainty, low-decision chips, and it is additive to the proactive surfaces rather than replacing them.

---

## 5. Rejected ideas

- **Cloud LLM (Position D).** Violates the offline-only, no-server-inference, no-logs requirement. Out.
- **Bundling a multi-GB general LLM on every device for V1.** App size, RAM, battery, and cold-start latency are prohibitive, and it would still be worse at Greek and Albanian than a rule parser. Revisit only if cross-device parity ever becomes a hard product requirement, which it currently is not.
- **Letting the model freely drive the UI or chain arbitrary actions.** An autonomous "do anything in the app" agent has unbounded failure modes. The intent router with a fixed action set is the safe ceiling.
- **Model-generated transit facts.** No departure time, fare, or route ever comes from the model. Only from the deterministic tools. Non-negotiable for a trust-critical app.
- **General weather, chitchat, or world knowledge.** "Capital of France," "write me an email," "weather in Paris" all refuse. Weather is allowed only as a constraint on an Athens transit answer.
- **English-only assistant.** Silently abandons Greek and Albanian users. The floor parser must be trilingual.
- **Voice-only with no visual answer card.** Every answer renders as a card reusing existing screens, so the assistant is glanceable and accessible, not a talking box.
- **Claiming station closures from weather.** Closures come only from official alerts; offline means last-known plus timestamp plus honest uncertainty.

---

## 6. Phased Android/iOS delivery plan

Sequenced so value ships early and the model is always additive. Maps to the roadmap's 1.5 slot, but the constrained core is feasible well before that.

### Phase 0: Shared intent spine (KMP, no UI)
- Define `AssistantIntent` sealed interface and the tool registry binding intents to existing use cases, in `core/domain`.
- Pure and unit-testable: given an intent plus slots, assert the right tool is called and the answer shape is correct. No model, no UI.
- Deliverable: a `ResolveAssistantIntentUseCase` that takes a structured intent and returns a deterministic answer model.

### Phase 1: V1, trilingual rule parser plus chat surface (both platforms)
- Rule-based parser over the enumerable transit vocabulary in English, Greek, Albanian. Handles the common commands: next trains, last train, find station, plan trip, explain line, alerts, fares, open map, favorite.
- Entry surface: a chat/voice sheet that opens with context chips and a "from here" GPS default. iOS as a SwiftUI sheet, Android and Web as a Compose bottom sheet. Answers render as cards reusing existing screens.
- Fully offline. Out-of-scope refuses. Clarifying question when a slot is missing.
- This alone delivers most of the perceived value, on every device.

### Phase 2: V2, on-device model as slot-filler (capable devices only)
- iOS: Apple Foundation Models on Apple Intelligence devices, using guided structured output and tool callbacks to emit an `AssistantIntent`.
- Android: Gemini Nano via AICore or ML Kit GenAI on supported devices.
- The model does only classify-and-extract. Output is validated against the schema; on any mismatch, fall back to the Phase 1 parser. Greek improves, Albanian likely stays rule-parser-served.
- No behavior change on unsupported devices; they keep V1.

### Phase 3: Weather and station-comfort constraints (data layer)
- Author a `StationAccessProfile` bundled metadata layer (underground vs exposed, covered entrance, outdoor walk length, transfer exposure) on top of [Station.kt](core/model/src/commonMain/kotlin/com/syrmos/core/model/transit/Station.kt).
- Add `WeatherAwareTripHelp` resolution: bias `PlanJourneyUseCase` toward lower-exposure routes, use cached weather with a timestamp when present, and degrade honestly when absent.
- Closures continue to come only from alerts, with offline last-known plus timestamp.

### Phase 4: Own bundled model (optional, likely declined)
- Only if identical behavior on every device becomes a hard requirement. Gate strictly on size, RAM, battery, and latency. The recommendation is to not do this unless forced, because V1 plus V2 already covers the device matrix with graceful fallback.

### Cross-platform notes
- Shared in KMP: `AssistantIntent`, the tool registry, the rule parser vocabulary, the resolution use case.
- Per-platform: the on-device model adapter (Foundation Models vs Gemini Nano) and the chat surface (SwiftUI sheet vs Compose sheet).
- Trilingual is a first-class constraint at every phase, not a later toggle. The rule parser and any model prompt must handle Greek and Albanian, and the assistant must never silently drop a supported language.
