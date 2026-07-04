# Ariadne Ultra Clever Data Inventory

Generated: 2026-07-04T15:27:19.166184+00:00  
Repository: `/Users/p.dhespollari/git/personal/Syrmos`  
Git branch: `master`  
Git HEAD: `2d53e7a656ae746e9019f56ff7b02f91549869ff`  
Bundled manifest version: `179`  
Bundled manifest updatedAt: `2026-07-03T08:07:14Z`  
Bundled ETag: `ca3ecc96cbb3559057c3467f29a7703973fb22c8c1cb0791c690ab9d41b791c2`

## Executive Summary

This checkout already contains enough data and deterministic logic to make Ariadne much smarter on selected devices without making the language model the source of truth. The right package is a grounded assistant stack:

1. Guided generation or structured intent classification.
2. Tool calls into Syrmos deterministic functions for every operational fact.
3. Small RAG chunks for explanations, station descriptions, fare rules, app capabilities, and platform guidance.
4. Full raw seed data available as an archive, not as a runtime prompt.

The export in this folder gives all four layers. `ariadne_llm_context_pack.json` is the complete archive. `ariadne_rag_chunks.jsonl`, `ariadne_tool_contracts.json`, and `ariadne_intent_schema.json` are the practical runtime inputs.

## Data Inventory

| Area | Current evidence | LLM treatment |
|---|---:|---|
| Public lines | 9 lines across metro, tram, suburban | RAG for description, tools for operational answers |
| Mode counts | {'metro': 3, 'tram': 2, 'suburban': 4} | RAG summary |
| Station records | 201 seed station records | RAG lookup plus deterministic station search |
| Approx physical stations | 178 by name and rounded coordinate | RAG summary, not a unique-id source |
| Interchange records | 30 | Tool route graph source |
| Routes | 9 route order records | Tool route graph source |
| Transfers | 72 transfer records | Tool route graph source |
| Schedule bundles | 10 bundles including virtual `M3_AIR` | Tool source only |
| Station offset direction entries | 20 | Tool source only |
| Station offset stops | 448 | Tool source only |
| Fare operator records | 2 | RAG plus fare tool |
| Fare products | 14 | Tool source for prices |
| Fare info links | 3 | RAG and fare tool |
| Service alerts in bundle | 1 | Tool source, refresh online when available |
| Route shapes | 9 geometry records | Map rendering source, not LLM prompt |
| Icon override sets | updatedAt, stations, interchanges, vehicles | UI rendering source |

## Public Lines

| Line | Mode | Terminal A | Terminal B | Stations | Bands |
|---|---|---|---|---:|---:|
| M1 | metro | Piraeus | Kifissia | 24 | 24 |
| M2 | metro | Anthoupoli | Elliniko | 20 | 45 |
| M3 | metro | Dimotiko Theatro | Doukissis Plakentias | 27 | 45 |
| T6 | tram | Syntagma | Pikrodafni | 19 | 16 |
| T7 | tram | Piraeus loop | Asklipiio Voulas | 43 | 16 |
| A1 | suburban | Piraeus | Airport | 19 | 4 |
| A2 | suburban | Ano Liosia | Airport | 12 | 4 |
| A3 | suburban | Athens | Chalcis | 17 | 4 |
| A4 | suburban | Piraeus | Kiato | 20 | 4 |

## Schedule Bundle Summary

| Schedule id | Rules | Bands | Trips | Min headway | Max headway | Day types |
|---|---:|---:|---:|---:|---:|---|
| M1 | 4 | 24 | 0 | 6.0 | 15.0 | fri, mon_thu, sat, sun |
| M2 | 4 | 45 | 0 | 4.5 | 15.0 | fri, mon_thu, sat, sun |
| M3 | 4 | 45 | 0 | 4.0 | 15.0 | fri, mon_thu, sat, sun |
| M3_AIR | 4 | 8 | 0 | 36.0 | 36.0 | fri, mon_thu, sat, sun |
| T6 | 4 | 16 | 0 | 9.0 | 25.0 | fri, mon_thu, sat, sun |
| T7 | 4 | 16 | 0 | 12.0 | 25.0 | fri, mon_thu, sat, sun |
| A1 | 4 | 4 | 0 | 60.0 | 90.0 | fri, mon_thu, sat, sun |
| A2 | 4 | 4 | 0 | 60.0 | 90.0 | fri, mon_thu, sat, sun |
| A3 | 4 | 4 | 0 | 90.0 | 120.0 | fri, mon_thu, sat, sun |
| A4 | 4 | 4 | 0 | 60.0 | 90.0 | fri, mon_thu, sat, sun |

`M3_AIR` is a virtual schedule-only line for airport branch frequency. It should be exposed to Ariadne only through tools, usually as part of M3 airport questions.

## What The Model May Read

The model may read small chunks about:

- What Syrmos is and what Ariadne can do.
- Line names, modes, terminals, and station lists.
- Station names, language aliases, coordinates for explanation only.
- Fare explanations and official links.
- Current bundled service alert summaries, with refresh through tools when online.
- Platform guidance for Apple, Android, and Samsung-class devices.

The model should not read or memorize the whole schedule in a prompt. Runtime should retrieve only relevant chunks, then call tools for numbers.

## What Must Stay Deterministic

These facts must come from code or structured data, never from free-form model text:

- Next departures and countdowns.
- Last-train answers.
- Any HH:mm service time.
- Route feasibility and transfer count.
- Fare prices.
- Active service alerts.
- Station coordinates used for distance or map actions.
- Weather and live train positions.
- Favorites, map navigation, and other app actions.

## Current Ariadne Implementation

Current iOS behavior:

- `AriadneBrain` optionally uses Apple Foundation Models to normalize fuzzy text.
- `AthensTransitParser` still classifies the normalized text into fixed intents.
- `AriadneModel` dispatches to `ScheduleProjector`, `JourneyPlanner`, fare store, alerts service, weather store, location, and local favorites.

Current Kotlin behavior:

- `AssistantIntent`, `AthensTransitParser`, and `AssistantVocabulary` define the shared contract.
- `ComputeDeparturesFromBandsUseCase` projects bands with Athens day-type rules, station offsets, late-night rollover, holidays, and M3 airport lookahead.
- `PlanJourneyUseCase` builds a station graph and uses deterministic shortest-path logic.
- `LiveArrivalsProvider` is already the live-data interface, currently returning null where operators do not publish stop-level predictions.

## Recommended Ultra Clever Architecture

1. Keep the existing rule parser as the universal fallback.
2. Add a typed intent generator using `ariadne_intent_schema.json`.
3. Register the tools in `ariadne_tool_contracts.json` on iOS and Android.
4. Retrieve top chunks from `ariadne_rag_chunks.jsonl` only for explanations and context.
5. Require tool calls for operational facts.
6. Log every model-selected intent and every rejected slot so the parser can be improved without weakening safety.

## Gaps And Caveats

- `ops/syrmos-api/README.md` and the admin UI text still say fares avoid storing prices, but the current bundled `schedules-v2/fares.json` contains 14 fare products with prices and source URLs. The export follows the current JSON, not the stale prose.
- Current checked-in schedule bundles have `trips: []` for A1 to A4. The API code supports scheduled trips and `/api/train-timestamps`, but the checked-in `ops/syrmos-api/out/train-timestamps.json` has zero trains in this checkout.
- The full JSON pack is an archive and analysis input. Do not pass it wholesale into Apple or Gemini Nano at runtime.

## Files In This Pack

- `ariadne_llm_context_pack.json`: complete raw seed archive plus derived inventory, tools, intent schema, and platform notes.
- `ariadne_rag_chunks.jsonl`: compact retrieval chunks for runtime RAG.
- `ariadne_tool_contracts.json`: portable tool definitions and safety policy.
- `ariadne_intent_schema.json`: structured intent schema for guided generation.
- `ariadne_prompt_pack.md`: prompt templates and runtime orchestration policy.
- `ariadne_platform_notes.md`: Apple, Android, and Samsung-class platform guidance with source links.
- `checksums.sha256`: checksum evidence for generated files.
