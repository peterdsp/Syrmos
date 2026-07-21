# Changelog

User-facing and architectural changes to Syrmos. Keep this file up to date with every release. Format loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

Current shipping: **iOS 1.2.9** (TestFlight), **Android 1.2.9** (Play internal, versionCode 113), **Web** (rolling). 1.2.7 cut 2026-07-21 by the `v1.2.7` tag; 1.2.8 cut the same day for iOS; 1.2.9 (map cross-platform parity) cut the same day for all three via the `v1.2.9` tag. Burned Android version codes never reusable: 105, 106, 109, 110, 111, 112, 113. Next Android release must use 114+.

How Android 1.2.2 actually shipped, because the version history is not linear: the `v1.2.2` tag's Android job failed on missing Play/signing secrets, so no bundle was uploaded and Android sat on **1.1.1** while iOS was on 1.2.2. 1.2.1 never reached Play at all. On 2026-07-16 the long-pending 1.2.0 (versionCode 102, approved but unpublished since 2026-07-04) was published, then 1.2.2 (versionCode 106) was uploaded by hand after the native-lib fixes below. Burned version codes that can never be reused: **105** (rejected, 4 KB-aligned libs) and **106** (released). The next release must use **107+**.

The release secrets are now set, so a `v*` tag ships Android automatically alongside iOS and web. See [docs/ops/RELEASE.md](docs/ops/RELEASE.md). Do not re-run the `v1.2.2` tag: it points at the pre-fix commit (versionCode 105, 4 KB libs, broken JNI shim).

The long-range product roadmap by version (1.1 through 2.0, with quarterly targets) lives in [docs/CASE_STUDY.md, Appendix K](docs/CASE_STUDY.md#appendix-k--product-roadmap). Detailed historical context for each shipped change lives in the same file's Revision Log. This changelog summarises the version-to-feature mapping.

Product direction: Syrmos is a companion, not a schedule. Every feature is measured against the answer-first / proactive / reassuring / low-decision rules in [docs/PRODUCT_PRINCIPLES.md](docs/PRODUCT_PRINCIPLES.md).

## 1.2.9 — 2026-07-21

**The map now looks the same on all three platforms.** Web was the reference (flat base, coloured line network, clean dots, directional-triangle trains); iOS and Android had drifted badly and web itself had two theme bugs. Fixed everywhere:

- **iOS: the coloured line network was invisible.** The flat CARTO base tile layer, which is opaque and replaces the map, was drawn at the top overlay level, so it painted straight over every route line. Only station and train markers showed. The base now sits at the bottom and the lines render on top of it. National, Thessaloniki and Patras corridors also draw now (iOS previously only splined the Athens lines).
- **iOS + Android: markers matched web.** Both platforms drew custom artwork - iOS bundled per-station icons with baked-in labels ("M3 AM"), Android per-station PNGs on selection - instead of web's clean coloured dot with a white ring. Both now draw the same simple dot (interchanges read as a white-cored ring), with the station name/detail living in the sheet, not the pin.
- **Trains are one directional triangle everywhere.** Web draws the whole fleet as line-coloured triangles pointing the way of travel; native gave metro and tram their own sprite badges. Native now uses the same triangle for every vehicle.
- **Line colours are the real per-line colours.** Web reads each line's exact colour from the data; Android was collapsing every line into one of five buckets, so national/Thessaloniki/Patras lines all came out purple. Android now reads the true per-line colour, matching web and iOS.
- **Web dark mode was pure black.** A brightness filter meant to darken the map crushed the already-dark base map's streets to invisibility. Softened so the street grid shows under the coloured lines again.
- **Web light mode sheet text was washed out.** The station title and line names had no explicit colour, so with the OS in dark mode but the app toggled to light they inherited a near-white colour and vanished on the white sheet. Given explicit colours for both themes.

All platforms bumped to 1.2.9 (Android versionCode 113). Line weights, opacity (0.9), the bus dash and dot sizes were also aligned to web's exact values.

## 1.2.8 (iOS) — 2026-07-21

**iOS national + regional + bus moving vehicles.** iOS now shows moving vehicles for national intercity rail (IC/RG/KO/PL/DK/AL), the Thessaloniki and Patras suburban corridors, and the rail-replacement buses (KB/VL/DX/KP/TL/PU/PSB) — the same fleet Android and web already carry from 1.2.7. These lines have no live feed and no station-offsets table on the Pi, so iOS projects them entirely offline from the bundled timetables (`seed-schedules-v2/{lineId}.json` `trips[]`): for every trip whose day-type matches today, the current segment is found by wall clock and the chord between its two stations is interpolated. They render as the same directional triangles as the suburban fleet, and each carries its real line colour instead of a single purple. This closes the iOS-only gap that 1.2.7 deferred (iOS runs a separate Swift simulator from the KMP `MapViewModel`, so the KMP projector only reached Android). No Android or web change — both already ship this — so 1.2.8 is an iOS-only release; Android stays on versionCode 112 / 1.2.7.

## Unreleased (rolling web + data)

**Viewport-culled map markers** (iOS, Android, Web). With the network nationwide (389 stops across Greece), every platform kept all of them live on the map at once, so zooming felt heavy and distant coastal lines (Katakolo, Corinth-Patras) painted over the sea at the edges of the Athens view. Now only stations inside the padded current viewport are drawn, and panning re-culls as you move. Also confirmed, after a deep investigation, that no station is actually mislocated: the dots that looked "in the sea" were real stations elsewhere in Greece (e.g. Alfeios on the Katakolo-Pyrgos-Olympia line) rendered off-screen, not bad coordinates.

**The map opens on Athens again, and zooming is calm** (iOS, Android, Web). Going nationwide made the map try to show the whole country at once: it opened fit to every station (Ioannina to Alexandroupoli to Kalamata), so Athens was a tiny corner that decluttering emptied out, and Android was worst of all (the fit clamped to min-zoom 9 and centred on the national centroid in central Greece, pushing Athens off-screen entirely). Now every platform opens framed on the Athens network with metro, tram, suburban and live trains on screen; zoom out for the country, and GPS "locate me" still recenters on you. Decluttering is also softer: detail resolves nearer the default view (major hubs from z8, all stops from z10), dots keep one size across zoom instead of resizing at z12, and per-station artwork only shows for the stop you select instead of every dot ballooning at z14, so far fewer markers snap in and out as you pinch. Also clamped the train polyline-follow so a shape that loops or runs past its terminus (the T7 tram past Voula) can no longer fling a train into the sea.

**Vehicles declutter with the map** (iOS, Android, Web). At country and regional zoom the whole Athens metro + tram fleet used to render as ~48 individual dots stacked on one ~15px point at the coastline, which read as "trains scattered in the sea." Stations already collapse to lines-only at that zoom; the moving vehicles now follow the same rule and are hidden below the regional band (`MapDesignTokens.majorHubMinZoom`), reappearing on their lines when you zoom into Athens. Web gates the simulated-train markers and re-renders on `zoomend`; iOS hides the train annotations in `regionDidChangeAnimated`; Android keys the simulated + live train overlays on the zoom band. Diagnosed from the live map: 47 of 48 vehicles were correctly on the Athens network and only 1 coastal tram sat marginally offshore, so the "sea" was a decluttering artefact, not wrong coordinates.

**Modern dot markers on the map** (iOS, Android, Web). The big teardrop station pins are replaced by small, centre-anchored dots in the line colour with a crisp white ring — roughly half the size (10-13px vs 22-32px) so the now country-wide map stays clean and lightweight. The mode glyph only appears when a stop is selected or you zoom in; interchanges read as a white-cored "target" ring; suspended lines still grey out. The whole marker + line design (dot sizes, ring/halo widths, glyph rule, greyed colour, bus/greyed dash) now lives in **one shared source of truth** (`MapDesignTokens` in core:common), mirrored to iOS and web so the three maps can't drift.

**Ariadne, cleverer again.** Two more grounded capabilities on top of the earlier batch: **which lines serve a station** ("ποιες γραμμές περνάνε από X" / "cilat linja shërbejnë X") and **how many stops / how far between two stations** (stop count + duration from the deterministic planner). All trilingual, across iOS/Android/Web, wired through the rule parser, dispatch, session memory and the on-device LLM tier.

**Ariadne got cleverer** across iOS, Android and Web (shared KMP brain + Swift and JS mirrors, all trilingual, still tool-only — she never invents a transit fact):

- **Understands more phrasings.** Broadened the EN/EL/SQ cue vocabularies (departures, planning, arrivals, "I want to go", "navigate to", …) so far more natural wording resolves without a dead-end. Albanian station recall widened from ~4 stops to ~20 high-traffic ones, including real Albanian exonyms (Selanik → Thessaloniki, Athina → Athens) — and the web build now applies those aliases too (it previously didn't).
- **New capabilities.** **First train** of the day (mirror of last train), **step-free accessibility** ("is X wheelchair accessible / does it have a lift?", answered from the bundled per-station flag), and **reverse trip** ("and back?" / "return" / "kthimi" flips the last route). Each is deterministic and grounded.
- **Smarter conversations.** Day-change follow-ups ("what about tomorrow?", "the weekend?") re-run the last departures query without repeating the station, and web now has durable session memory (current station + last route) so multi-turn works there like it already did on iOS/Android. The Swift session context was brought back into parity with the KMP one (last intent, current line/direction).
- **Smarter LLM tier.** The on-device model's few-shot classification prompt and GBNF grammar (all three copies — Kotlin, web `.gbnf`, Swift) plus the IntentGrounder now cover the new intents, with EL/SQ worked examples so Greek and Albanian stay first-class.

All-Greece rail coverage, shipped as bundled data + web with no client-code changes (the app is fully data-driven for lines/stations/geometry):

- **Five more corridors from the complete Hellenic Train timetable** (31-corridor authoritative source, 18 Jul 2026), bringing the network to **30 lines**: **TL1 Tithorea - Lianokladi - Lamia - Stylida** replacement bus (15 stations, 28 daily services with the Lianokladi hub and per-run stop patterns), **KO1 Katakolo - Pyrgos - Olympia** tourist rail (Mon-Sat), **PL1 Pelion** tourist rail (Ano Lechonia - Milies, weekends - only the origin departure is officially published so only that is seeded, nothing invented), and **DK1 Diakopto - Kalavryta rack railway** which is suspended since 13 March 2026 and therefore renders greyed with no departures. Exact per-station times transcribed from the official timetable; coordinates are real OSM rail-station nodes.
- **Patras University campus shuttles** (PU1 Kastelokampos–University–Hospital loop; PU2 Kastelokampos–Agios Vasileios), the last two of the 31 corridors, completing the entire timetable file. Real OSM University-of-Patras building coordinates where mapped (Chemical Engineering, Medicine, Pedagogy, Rectory); OAED and Agios Vasileios are unmapped campus/suburb bus stops placed on the known route. Network total: **32 lines** (athens 10, national 11, thessaloniki 6, patras 5).

- **Real OSM track geometry for every national + Patras corridor.** IC1, RG1, TP1-TP4, PS1/PS2/PSB now follow the real curved rail alignment (stitched in OSM relation-member order from the Greece extract) instead of straight station-to-station chords. Injected into all five bundled `shapes.json` copies. Every served station sits on its line (0-76 m); a few multi-km straight segments on IC1/RG1 are genuine gaps in OSM's mapping of the new high-speed alignment, not stitching errors (not hand-drawn, to avoid inventing track).
- **Two more national corridors.** **AL1 Alexandroupoli - Orestiada - Ormenio** (Evros/Thrace, 31 stations, four daily trains 1680/1681/1682/1683 incl. the Orestiada short-turns) as a real suburban train; **KB1 Paleofarsalos - Kalambaka** (Sofades, Karditsa, Trikala) as a rail-replacement **bus** (C881/C1889/C1880/C888), which is how Hellenic Train serves that branch today. Every time transcribed from the official Hellenic Train timetable, stored as exact per-station departures; coordinates and track geometry from the OSM service relations (14122316/14122315 for AL1, 14007294/14007293 for KB1), never invented.
- **Three rail-replacement bus corridors** (mode = bus, region national), completing the country's replacement-bus network alongside KB1: **VL1 Volos - Larisa** (Velestino; 7 buses each way daily), **DX1 Drama - Xanthi - Alexandroupoli** (15 stations, nine services with per-run stop patterns incl. the Drama-Alexandroupoli express C602 and the Xanthi-Drama C679 that skips Toxotes), and **KP1 Kiato - Patra** (via Diakopto on the longer runs; 40+ services incl. Friday and Friday+Sunday-only departures). Exact per-station times from the official Hellenic Train timetable. Geometry from OSM rail alignments; KP1's coastal Kiato-Patra segment was rebuilt by axis-projection ordering because relation 1769919's member order is scrambled (member-order and greedy stitching both produced 45-50 km phantom jumps). Total network is now **26 lines** across four regions (athens 10, national 7, thessaloniki 6, patras 3).
- Fixed the nightly importer that scoped `DELETE` too broadly and wiped every non-Athens line overnight; corrected station-region tagging so Evros/Kalambaka read national and Patras reads patras.

## 1.2.7 (2026-07-21)

**A flat minimal map + moving vehicles for the whole network** (iOS, Android, Web).

- **Flat, label-free base map** like the railway.gov.gr live tracker: CARTO Positron
  (light) / Dark-Matter (dark), theme-aware, replacing the busy street map on all
  three platforms. The coloured line network + station/train markers are the whole
  picture - a clean transit diagram, not a road atlas.
- **Moving vehicles for suburban, national rail, and rail-replacement buses**, not
  just metro/tram. Suburban A1-A4 ride the Pi `/api/live-positions` feed online and
  bundled station-offsets offline; national rail + buses (which have no live feed or
  offsets) are projected client-side from the bundled timetables - every in-progress
  trip is interpolated by the wall clock along its line. On native this is a new KMP
  `projectScheduledTrains` wired into the map view-model; on web it's the JS twin.
- **Directional triangles**: national/bus/suburban vehicles render as a triangle
  pointing the way they travel (metro/tram keep their per-line directional sprites),
  so a moving train is never mistaken for a station dot.
- **Web map fixes** from the canvas rework: stations are tappable again (nearest-stop
  within a forgiving radius), and the simulator's name-resolution is scoped per line
  so a shared station name can't pull a train onto the wrong track.
- Carries the tightened on-device coordinate audit from 1.2.6.

## 1.2.6 (2026-07-21)

Tightens the on-device station-coordinate audit added in 1.2.5. The audit box now
covers the full Athens-line extent (north to the A3 Chalkida line ~38.46, west to
the A4 Kiato terminus ~22.73: box 37.7-38.55 N, 22.65-24.15 E), and is scoped to
Athens lines (M/T/A) on every platform. National lines (IC/RG toward
Lamia/Thessaloniki, Patras) legitimately span all of Greece and can't be boxed
into Attica, so they're no longer audited. Verified: 0 of 201 Athens-line stations
fall outside the box, so the audit stays silent unless a stop is genuinely
misplaced. No user-facing change; debug logging only.

## 1.2.5 (2026-07-21)

Native store build carrying the map overhaul that shipped to web after 1.2.4.

**The map opens on Athens and stays light** (iOS + Android). Every platform used to fit the map to the whole nationwide network at launch, so Athens was a tiny corner (Android was worst: it clamped to min-zoom and centred on the national centroid in the sea, leaving Athens off-screen). Now it opens framed on the Athens network. Decluttering is softer (major hubs from z8, all stops from z10, one dot size, no z14 artwork balloon), moving vehicles hide below the regional zoom so the fleet never piles into a coastal blob, and the map viewport-culls so only stations in view stay drawn. The train polyline-follow is clamped so a looping/past-terminus shape (T7 tram past Voula) can't fling a train into the sea. Adds an on-device station-coordinate audit that logs any Athens stop outside the Attica box (a quiet console confirms the bundled data is clean). Web additionally moved to canvas circle markers (preferCanvas) so dots stop lagging behind the tiles on zoom.

## 1.2.4 (2026-07-19)

Native store build to carry the country-zoom map change that shipped to web after
1.2.3 was cut.

**Lines-only country zoom (iOS + Android).** At the whole-Greece view the map now
draws only the coloured line network, no station dots (below
`MapDesignTokens.LINES_ONLY_MAX_ZOOM`); dots resolve on zoom-in (major cross-modal
hubs, then all interchanges, then every stop). Matches the web behaviour and reads
as a clean rail atlas instead of a scatter of hub rings over the Aegean.

The web-only 2.0 work since 1.2.3 (the app-shell bottom sheet, the answer-first
one-glance hero, the compact mobile header) is not in these native binaries; the
native hero and app-shell remain to be built.

## 1.2.3 (2026-07-19)

The first store binaries to carry the all-Greece network, the cleverer Ariadne, and
the 2.0 design-system foundation. Everything in the rolling section above (modern dot
markers, Ariadne v2, the 32-line all-Greece coverage with real OSM geometry) is now in
the iOS + Android builds, plus:

**Country-wide map legibility (iOS, Android, Web).** A three-tier zoom rule replaces
the old "draw every stop" behaviour that turned the country view into confetti. At
country zoom only the major cross-modal hubs show (a genuine transfer whose lines span
2+ types, ~20 Greece-wide); at regional zoom all interchanges; in a city every stop.
The coloured line strokes carry the network in between. Roughly 389 markers down to ~20
at country zoom. A missing station smart-code icon now falls back to the dot instead of
the browser's broken-image placeholder. The rule lives in the shared `MapDesignTokens`.

**Design system 2.0 foundation (the "Hellenic Rail Atlas" light-first identity).** A
canonical KMP design-token module (`core:designsystem` `theme/tokens/`) is now the single
semantic source for colour, typography, spacing, shape, and the "train glide" motion
easing. A generator emits the Swift and CSS-variable mirrors so the three platforms never
drift. The identity is adopted at the foundation: the Android Compose theme, iOS adaptive
semantic colours (dark mode preserved), and the web chrome (Aegean-blue brand, warm
station-white surfaces, warm near-black text). Two new trust-layer catalog components
landed — the source-confidence chip and the offline pill.

## 1.2.2 (2026-07-10)

Fixes after tagging:

- **Android on-device LLM never actually ran (silent, since the feature shipped).** `libsyrmos_llama.so` was linked against `libc++_shared.so`, which is not packaged in the APK/AAB, so `System.loadLibrary("syrmos_llama")` always threw `dlopen failed: library "libc++_shared.so" not found`. `LlamaBridge` caught it, set `available=false`, and Ariadne fell back to the rule parser — so nothing crashed and nothing looked wrong, which is exactly why it went unnoticed. llama.cpp's own libs static-link libc++ and loaded fine; only the JNI shim asked for the shared runtime. The shim is now linked with `-static-libstdc++` (matching llama.cpp), needs no extra runtime, and all five libs load on device. Verified on an arm64 emulator: libs load, the 1.5B GGUF loads (~1.2 GB RSS) and inference executes multi-threaded. Inference latency on a real device is still unmeasured (the emulator is too slow to time it meaningfully).

- **Android 16 KB page-size support (blocking Play).** The vendored llama.cpp libs (`libllama`, `libggml*`, `libsyrmos_llama`) were linked with the default 4 KB max page size, so Google Play rejected the 1.2.2 bundle ("Your app does not support 16 KB memory page sizes") and on a 16 KB-page device the libs would fail to load, silently dropping Ariadne to the rule parser. llama.cpp's CMake does not set the alignment and the NDK default did not apply, so the rebuild now passes `-Wl,-z,max-page-size=16384` explicitly to both the CMake link and the JNI shim. All five arm64 libs now report LOAD align `0x4000`. The recipe + a verification step are documented in `composeApp/src/androidMain/jniLibs/README.md`.

Headline: Ariadne gets a **real on-device LLM** (one model, every platform) plus a **cleverer rule parser**. The model does the understanding step only (messy message to an approved intent and quoted slots); it never generates a transit fact, and the deterministic rule parser stays the offline floor, so nothing regresses when the model is absent.

- One model everywhere via **llama.cpp** (GGUF), replacing the old per-platform "smart" tiers (Apple Intelligence on iOS, Gemini Nano on Android, nothing on Web) that lit up on almost no devices. Model: **Qwen2.5-1.5B-Instruct (Q4_K_M)**, chosen after a real browser benchmark on messy trilingual queries where 0.5B scored 3/9 (failing every Greek and Albanian case) and 1.5B scored 9/9. Albanian and Greek stay first-class.
- **On-demand, not bundled**: only the small runtime ships with the app; the ~1.1 GB model is downloaded in-app on explicit opt-in and cached, so first install stays lightweight. Single shared manifest (`AriadneModelManifest`, pinned URL + SHA-256); the binary is fetched + verified by CI, never committed.
- **Web (shipped, browser-verified)**: `@wllama/wllama` (llama.cpp to WASM) runs the model in the browser, cached in OPFS, multi-threaded under COOP/COEP with a single-thread fallback. A GBNF grammar locks output to the exact intent JSON, so an invalid intent is impossible. Output is grounded by the existing `IntentGrounder` and dispatched to the same deterministic use cases.
- **Cleverer rule parser**: expanded EN/EL/SQ cue coverage (departures, last train, plan, find, fare, help) so more natural phrasings resolve without the model, and fixed a bug where a weather question about a place we do not serve ("what's the weather in London") answered with Athens weather instead of declining.
- Shared `IntentGrounder.classificationPrompt` rewritten as a few-shot trilingual prompt (also improves the native paths).
- **iOS + Android native runtimes**: shipped and build-verified. Pinned llama.cpp (Swift `LlamaSession` via an xcframework on iOS; JNI `libsyrmos_llama` on Android arm64), the same on-demand model, an in-app "Download Ariadne's brain" control with live progress, and grammar-locked JSON grounded by the shared `IntentGrounder`. The deterministic rule parser is the floor on both until the model is downloaded. On-device inference is verified per device at first run.

## 1.2.1 (2026-07-08, in review)

Headline: the **Ariadne co-pilot** (Phases 1 to 4) plus first-class Albanian coverage. Ariadne now keeps session context ("I'm at Syntagma" then "go airport faster"), answers honest station status backed by live STASY advisories, ranks routes by preference with a weather tilt, reasons about live-vs-seasonal weather, and drives a live Apple Watch app + widgets with voiced (TTS) read-back. Details below.


Companion surfacing, implemented on iOS, Android and Web (UI on top of data the app already has):

- Answer-first home: the lead element is now one actionable line ("Next M2 to Syntagma, 4 min") with the timetable grid demoted below it. Compose (`feature/home`) and SwiftUI (`HomeView`) both compute the soonest departure across the nearest station's lines via the existing projector.
- Offline-alive indicator: a small pill surfaces whether arrivals are live or "Running offline · predicted from schedule". Driven by a shared freshness rule (`core/common` `FreshnessEvaluator`, mirrored in iOS `DataFreshness`); live network success paths mark the data fresh, and it ages back to predicted after the window.
- Single-line "last train home" teaser: inverts tonight's last departure into "Last M2 · leave by 00:14" for a single line, no transfers. New `GetLastTrainUseCase` (Kotlin) and `ScheduleProjector.lastTrainTonight` (iOS), ahead of the multi-leg version in 1.2.

Ariadne, the offline transit assistant (a constrained, tool-only intent router, implemented on iOS, Android and Web; brought forward from the 1.5 roadmap slot in constrained form):

- "Ask Ariadne" from the Home screen opens a chat that parses natural language fully offline and dispatches to the deterministic use cases the app already ships (departures, last train, trip planning, line info, ticket prices, service alerts, find station, favorite a station). It never generates a transit fact; it picks an approved action and the projector/planner answers.
- Day-aware departures: "M3 from Syntagma this weekend / tomorrow / Saturday" projects that whole service day from 00:00 (`ComputeDeparturesFromBandsUseCase.invokeForDay`, the projector dayOffset path on iOS), not just today.
- Fares: surfaces the relevant ticket products with prices (standard single vs the airport ticket). Favorites: a real `FavoritesRepository` (Kotlin, SQLDelight) + UserDefaults store on iOS persist the toggle.
- iOS trip planning now runs a full Dijkstra (`JourneyPlanner.swift`, with transfer edges between co-located interchange ids) so iOS matches the Android/Web `PlanJourneyUseCase` for any number of transfers.
- Trilingual by design (EN/EL/SQ) via a rule parser, so no supported language degrades. Shared brain in `core/domain/assistant` (`AthensTransitParser`, `AssistantIntent`), mirrored in Swift under `iosApp/.../Features/Assistant`. Scope is fenced to Syrmos and Athens public transport; weather is accepted only as a routing constraint, everything else is declined.
- This also wired `PlanJourneyUseCase` (Dijkstra routing that already existed but was never in the DI graph) into the Compose app.
- Co-pilot Phase 1 (iOS + Android + Web): Ariadne now keeps a small session memory (`AssistantSessionContext`), so "I'm at Syntagma" then "go airport faster" resolves without re-asking the origin. New intents `SetCurrentLocation` ("I'm at X / I'm here / jam te X / είμαι στο X") and `StationStatus` ("is X open/closed/working?"), plus a `RoutePreference` (fastest / fewest-changes) parsed from cues like "faster" / "γρήγορα" / "shpejt" / "direct". Station status is honest: it leads with any matching STASY advisory and otherwise falls back to the timetable ("no live closure alert… should be operating… check STASY") instead of asserting a station is open. A new `ServiceAdvisoryMatcher` links the same STASY announcements + severe-weather signal shown on Home to the station, line, or route the user asked about, so a live closure (e.g. the M3 evening station closures) is surfaced in the answer and appended as a "Heads up:" caveat on trip plans. Shared brain in `core/domain/assistant`, mirrored in Swift (`AriadneDomain.swift`, `AriadneParser.swift`, `AriadneModel.swift`) with parity tests both sides. Voice spec captured in `docs/ARIADNE_VOICE.md`. The iOS Swift `STASYAnnouncement` model now decodes `affectedLines` / `severity` / validity window (feed, cache, and bundle paths), so iOS advisory matching reaches whole-line notices and true closure severity exactly like KMP.

Weather and on-device intelligence:

- Weather card on Home (iOS + Android + Web): current conditions from Open-Meteo (free, no key, CORS-friendly), styled as an immersive condition-tinted gradient with the temperature, today's high/low, a next-six-hours forecast strip, and feels-like / humidity / wind. `WeatherService`/`WeatherRepository` in KMP, `WeatherStore` on iOS; central-Athens default so Web and no-location launches still show something. (Visual language inspired by the shared weather-app design; the Figma community file couldn't be extracted over MCP due to the plan's rate limit.)
- Weather-aware routing: Ariadne's rainy-day trip answers now read the cached weather snapshot and the route's exposure (metro underground/sheltered, tram open-air) via `StationComfort`, and give real advice instead of a generic disclaimer. Degrades honestly with no cached weather.
- Weather context, live-vs-seasonal split (Ariadne Phase 2, iOS + Android + Web): a structured `WeatherContext` (source LIVE / SEASONAL_FALLBACK / UNKNOWN, a dominant state of NORMAL/HOT/RAINY/WINDY, and heat/rain/wind risk bands) now drives trip advice. When there's a live reading Ariadne says "It's hot in Athens right now…"; when there isn't, it falls back to the Athens seasonal norm (`AthensClimate` monthly table) phrased honestly as "…this time of year is usually hot and dry", never as "now". Route exposure plus the weather state add a targeted nudge ("prefer an underground route to avoid long sun-exposed waits"). Built in `core/model/weather/WeatherContext.kt` + `core/domain/assistant/WeatherContextBuilder.kt`, mirrored in Swift, tests both sides. Also added an injectable clock seam (`currentAthensTime/Date(clock = Clock.System)`) so time- and season-dependent logic is deterministically testable.
- Route ranking (Ariadne Phase 3, iOS + Android + Web): when a trip has a real choice, Ariadne no longer just reports the fastest route. `RouteRanker` scores candidates by the user's `RoutePreference` (fastest / fewest-changes / balanced) with an adverse-weather tilt, so on a hot or wet day a slightly slower but sheltered all-metro route can win over a faster tram/surface one, and she says why ("The faster route is more exposed; in this weather I'd take this one"). `PlanJourneyUseCase` gained a `metroOnly` alternative for the sheltered option. Athens' network rarely offers genuinely distinct rail routes, so this mostly adds honest framing plus the metro-vs-tram weather tilt where a corridor has both. Built in `core/domain/assistant/RouteRanker.kt`, mirrored in Swift, tests both sides.
- Journey-specific fares: `ExplainFare` carries the trip endpoints, so an airport fare is derived from an actual airport-station endpoint, not just the keyword.
- Ariadne on-device LLM front-end: on Apple Intelligence devices (iOS 26+), `AriadneBrain` uses Apple Foundation Models to rewrite fuzzy input before the deterministic parser classifies it; the model never picks intents or invents facts, and older devices/Simulator fall straight through to the rule parser. KMP has the matching `AssistantQueryNormalizer` seam (no-op default) for a future Android Gemini Nano backer.

Track this departure (Tier 2 primitive, shared `core/common` `TrackedDeparture` / `DepartureTracking`):

- "Track" on the Home hero pins a live countdown card that ticks offline on all three platforms.
- iOS: full Live Activity. ActivityKit integration in `DepartureTracking.swift` (`SyrmosTrackingAttributes` + start/update/end), `NSSupportsLiveActivities` set, and a real `SyrmosWidgetExtension` target (`iosApp/SyrmosWidget/`) that renders the Lock Screen + Dynamic Island. The extension is built and embedded into the app by the normal scheme build.
- Android: the parallel surfacing is an ongoing count-down notification (`DepartureTrackingNotifier`), driven by the same shared `DepartureTracking` primitive, with a live chronometer and a `POST_NOTIFICATIONS` request on Android 13+.
- Web: the in-app pinned countdown card is the surface; there is no OS-level equivalent.
- Tracking card redesign (all three platforms): the old side-by-side "Tracking your train" and "NEXT TRAIN" cards duplicated the same countdown. The tracked card is now a single Uber-style block with a LIVE pulse, an "Arriving <Station>" subtitle, a headline countdown, a progress bar that fills as the target time approaches, line badge and destination, and a bottom "Stop tracking" action. The answer-hero card is hidden while tracking is active so there is exactly one countdown on screen. Line accent color is now derived from the tracked line (M1 green / M2 red / M3 blue / tram orange / suburban purple), not hard-coded to metro blue.
- Track any train (all three platforms): a "Track a train" chip on Home opens a linear four-step picker (Line → Direction → Station → Departure) so users can pin any train, not just the one at the nearest station. Shared Compose `TrackPickerSheet` on Android + Web, mirrored SwiftUI `TrackPickerSheet` on iOS, both hydrating a `TrackedDeparture` and handing off to the shared primitive.
- iOS Live Activity redesigned to mirror the in-app card: LIVE pulse eyebrow, "Arriving <Station>" header, huge line-tinted countdown, progress bar, line badge, and destination trailer. `ContentState` gains an optional `progress` field the app fills on each tick.
- Live Apple Watch app + widgets (iOS, "Phase 4"): the Watch departures screen was redesigned to a station header + "Syrmos Watch", a red "now" hero for the imminent train, line-badge rows with clock + minutes, an "Updated" line, and a "Next trains nearby" screen. It now ticks **live every second on-wrist** via `TimelineView(.periodic)` reading each departure's absolute `targetEpoch` (the phone sends absolute target times + the app language, so the countdown stays live between pushes without battery-heavy polling). Added **voiced Ariadne, read-back only**: a speaker control reads the soonest departure aloud via `AVSpeechSynthesizer`, trilingual (EN/EL/SQ) and offline. iOS home-screen widgets now show an OS-native self-ticking countdown (`Text(timerInterval:)`) that updates every second with no timeline-reload cost and flips to a red "now", the correct way around WidgetKit's reload budget. Scope: these live surfaces are Apple-only (Apple Watch + WidgetKit); Android Glance/Wear and Web PWA live-updating are a later follow-up.

Tooling:

- Fixed `./gradlew check`: `composeApp` configured its `android {}` block directly and never inherited `SyrmosKmpLibraryPlugin`'s lint disables, so Google's `NonNullableMutableLiveDataDetector` crashed lint on `PlatformModule.kt`. Mirrored the lint-disable block into `composeApp`.

Fixes:

- Albanian (SQ) station coverage: the assistant vocabulary now carries Albanian and common Latin/Greeklish station spellings ("Aeroport"/"Aeroporti" for the airport, "Pireas"/"Pireu", "Sintagma") on top of the bundled EN + EL names, so Albanian input resolves as reliably as EN/EL instead of relying on fuzzy typo-matching. Albanians are a large Athens community, so SQ is treated as first-class. Built in `AssistantVocabularyBuilder` (KMP) and mirrored in the iOS `fromSyrmosData()` builder, with tests both sides (the iOS test runs against the real bundled data). Long-term the bundled station data should gain a `nameSq` field.
- Dark-mode contrast on the severe-weather card (iOS + Android + Web): the card fill was a hard-coded cream that never adapted, so the semantic body text washed out to near-invisible light-on-cream in dark mode. The fill now flips to a deep amber-brown in dark mode (keeping the amber warning identity), the title/border accent brightens for legibility, and the phone-number badges keep their deep-orange chip so the white glyphs stay readable. The Home "Service Alerts" card had the same latent cream-in-dark-mode issue on Compose and was brought to parity.
- Ariadne engine settings row (iOS): when Apple Intelligence is supported but switched off, the row now shows an "Open Settings" button that deep-links to the system Settings app (matching the "Turn it on in Settings" copy), instead of a "Learn More" button that opened a generic `support.apple.com/apple-intelligence` marketing page. The stale in-app Safari plumbing that only fed that link was removed.

Quality:

- iOS XCUITest target. Current CI iOS job is build-only; unit tests exist under `iosApp/iosAppTests/` but no UI test target yet.
- Compose snapshot tests for station detail and station list rows.
- Playwright end-to-end smoke against the live web bundle.

Infrastructure:

- TestFlight upload automation. iOS workflow does not exist yet; secrets (`APPLE_ID`, `APP_SPECIFIC_PASSWORD`, `APP_STORE_CONNECT_API_KEY`) must be added before the upload job can run.

## 1.1 (target Q4 2026)

Per [Appendix K](docs/CASE_STUDY.md#appendix-k--product-roadmap):

- iOS Home Screen widget (WidgetKit, next 3 departures at a favorited station)
- Lock Screen Live Activity + Dynamic Island (next train, transfer countdown, destination progress), sharing the widget's departure-timeline pipeline
- Wear OS / watchOS companion (independent app, glanceable next departure + complication)
- Platform-direction slice ("trains to X depart from the left platform")
- Accessibility improvements

## 1.2 (target Q1 2027)

- Trip planner: point-to-point routing using the embedded line topology
- Live journey confidence score ("you'll make it" / "tight transfer" / "take the next one"), the face of the planner
- Multi-leg "last train home": must-leave-by time across transfers

## 1.3 (target Q2 2027)

- National InterCity rail coverage (E85 corridor: Athens-Thessaloniki, Athens-Patras)

## 1.4 (target Q3 2027)

- Thessaloniki suburban (THESLAR corridor)

## 1.5 (target Q4 2027)

- AI chat helper for natural-language schedule queries

## 2.0 (2028+)

- TBD. Either a redesign or a regional expansion (Patras, Heraklion).

---

## In progress: 1.2 "Widgets Everywhere" (branch `feat/1.2-widgets`, iOS build 100 / Android code 100)

Putting the offline-prediction intelligence on every home screen, lock screen, and wrist. Measured against the widget philosophy in [docs/PRODUCT_PRINCIPLES.md](docs/PRODUCT_PRINCIPLES.md) (single-glance, no-scroll, line-tinted, dark-first).

- Shared widget design system (`iosApp/iosApp/DesignSystem/WidgetKit/`): `SyrmosLineTokens` (single source of truth for line colors + labels), `LiquidGlassTile`, `LinePill`, `StationStripCompact` / `StationStripFull`, `DepartureRowCompact` — all usable by both the app and the widget extension.
- iOS widget families (`SyrmosWidgets.swift`): Next Train (small + medium), Live Departures (large), Near Me (large), All Lines Status (extra-large), Weather + Alerts (extra-large), and an iPad medium trio. All share one `WidgetConfigurationIntent` (primary station + primary line, nearest-mode overrides both).
- Live Activity redesign: full-width Lock Screen card, Dynamic Island tri-lane expanded view (next three trains on the tracked line), line-pill leading / minutes trailing / line-dot minimal, `widgetAccentable` for StandBy tinting.
- Android Glance widgets: migration of the RemoteViews departures widget to Compose Glance, plus Next Train / Live Departures / Line Status / Near Me families, fed by a WorkManager snapshot.
- Apple Watch companion: next-three departures view, complications (corner / circular / rectangular), WatchConnectivity handoff from the phone with a bundled-schedule fallback.

## Shipped

### 1.1.1 (iOS build 41 / Android code 50)

Bug-fix build, no visual changes.

- Home Screen widget no longer hangs on the skeleton. The one-shot widget location request is now raced against a hard 2-second timeout (`withTaskGroup`), so a widget process that never gets a GPS callback falls through to the picked station instead of awaiting forever. `resolveStation` precedence is: nearest (time-bounded) then the picked station then a last-ditch nearest.
- Widget schedule hydration is now defensive: the timeline provider touches `SyrmosSchedulesStore.shared` on the main actor before projecting (forcing the bundle hydration), and if the `seed-schedules-v2` folder were ever missing from the built `.appex` it falls back to `SyrmosData.sampleDepartures` so the widget never renders zero rows. `os.Logger` breadcrumbs (`subsystem: com.syrmos.widget`) trace the path taken.
- Ariadne engine diagnostic in Settings, both platforms. iOS maps `SystemLanguageModel.availability` to an explicit `AriadneBrain.Availability` (available / Apple Intelligence off / model downloading / device ineligible / OS too old) and shows "Clever mode" vs "Rule parser" with an actionable Learn More link. Android mirrors it via a shared `AriadneEngineStatus` derived from the ML Kit GenAI (Gemini Nano / AICore) feature status.

### 1.0.5 (iOS)

iOS-only patch bump. Android remained on 1.0.4.

### 1.0.4 (Android, code 33)

- Suburban offline projection: A1 to A4 project offline like metro and tram
- Bottom-bar padding fixes
- iOS test target wired with XCTest unit suites (`NearbyStationDestinationTests`, `ScheduleProjectorTests`, `SuburbanProjectionTests`)
- Admin UI cleanup pass

### 1.0.3

- M3_AIR full route, offline announcements
- Albanian localisation, web i18n
- AGSL liquid glass shader on Android, map FAB fixes, train toggle
- Animated station sheet, real-time refresh, departure source priority fix
- Projector: 120 min slack on the rule-gate, rollover ignores horizon when empty, future bands emitted, hours formatter
- Fully offline by default; "Check now" is the only network path
- Android dark theme matched to iOS Settings; tab bar and header text fixes
- Map train markers fix on Android
- Web Google Play badge links to store listing
- Launch screen artwork matching iOS

### 1.0.1 and earlier

For the foundational work (June 2026): KMP scaffolding, Compose Multiplatform setup, SQLDelight database, Koin DI, Voyager navigation, frequency-band projector, OSM line geometry pipeline, Hellenic Train SSE relay, FastAPI admin on the Pi, Pi-hosted `api-syrmos.peterdsp.dev` with ETag-driven endpoints, daily upstream-source watcher, suburban explicit-trip ingestion (602 trips, 9,832 stop rows), zoom-aware map markers, OASA fares endpoint, STASY announcements pipeline with Greek-to-English translation, dual-license adoption, Athens transit reference package, iOS folder restructure into `App/Core/DesignSystem/Features/Shared/Views/Resources`.

Full per-date detail is in the Revision Log at the bottom of [docs/CASE_STUDY.md](docs/CASE_STUDY.md).

---

## How to update this file

When you cut a release:

1. Move the relevant items out of **Unreleased** into a new `### <version>` entry under **Shipped**, dated with what actually shipped on each platform.
2. Bump the `Current shipping` line at the top.
3. Re-tag the git release so the tag matches reality (`v1.0.3` is currently stale; the real shipping versions are above).
4. Keep the **Unreleased** list pruned: anything still pending stays, anything dropped gets removed.
5. If the long-range roadmap in [Appendix K](docs/CASE_STUDY.md#appendix-k--product-roadmap) changes, edit that table in `CASE_STUDY.md` rather than duplicating it here.

Source of truth for current shipping versions:

- Android: `androidApp/build.gradle.kts` (`versionName`, `versionCode`)
- iOS: `iosApp/iosApp.xcodeproj/project.pbxproj` (`MARKETING_VERSION`, `CURRENT_PROJECT_VERSION`)
- Git tags are often stale; do not trust them.
