# Syrmos 2.0.0 — "Hellenic Rail Atlas" design

Status: draft for review. Owner: peterdsp. Date: 2026-07-08.

> Syrmos 2.0 becomes a light-first Hellenic Rail Atlas: one-glance departures,
> real rail geometry, offline confidence, and Ariadne as a truthful recovery
> layer for Greek rail.

This is the 2.0.0 north-star design. 1.2.1 (the Ariadne co-pilot, Phases 1 to 4)
ships separately and is already in review. 2.0.0 is the redesign, the
cross-platform design system, and the path to Greece-wide rail.

## 1. Vision and soul

Syrmos is the calm, offline-first rail companion for Greece, starting with Athens
and expanding across suburban, regional, and scenic rail. It should feel like the
official-quality app Greece never built.

The product soul is trust, not decoration. It works underground. It works
offline. It knows the line, the route, and the next train. It tells you what it
knows and how sure it is. It does not track you. Every design decision serves that.

North star: one-glance living answer. On every surface the hero is a single
adaptive "your next train, now" moment, expressed through one shared design
language so the app is visibly one product on iPhone, Apple Watch, a widget,
Android, and the web.

The strongest 2.0.0 leap is not visual polish, it is that Syrmos knows what it
knows: it tells you when data is live, scheduled, offline, or uncertain, and it
helps when you are confused. Source-confidence and recovery come before the map.

## 2. Identity: "Hellenic Rail Atlas" (light-first)

Inspiration: Greek station signage, printed timetables, OSM rail geometry, and
Apple-native calm. Not tourist-postcard, not government-PDF, not generic Material.

Light-first is a hard identity decision, because transit is used outdoors, in
sunlight, on platforms, glanced at, on low battery. Dark mode is an excellent
graphite night and underground variant of the same system, never the foundation.

Tokens (defined once, consumed by SwiftUI, Compose, and web):

- Color surfaces: warm station-white primary (about #F7F5F1), light stone/marble
  secondary, clean white cards with thin rail-line separators, near-black text
  (about #14181F), station-sign grey for secondary text.
- Brand core: a single Aegean/rail blue (about #1466B8).
- Type: one family with strong Greek and Latin coverage, a tight display scale for
  the "now" numerals, tabular figures for clock times so they never jitter.
- Space and shape: 8pt grid, generous touch targets, consistent corner radius,
  soft shadows.
- Motion: one signature easing, the "train glide" (decelerates like a train
  easing into a platform), reused for the countdown, list inserts, and route
  drawing. Degrades to a cross-fade under Reduce Motion.

Hard rule: line colors are service data, not brand decoration. M1 green, M2 red,
M3 blue, tram orange, suburban purple, regional/national steel-blue, scenic
ochre/gold, warnings amber, disruptions red. A line color activates only when the
user is dealing with a line, route, departure, station interchange, or map
segment. The screen chrome stays neutral station-white with the Aegean-blue
brand core. This is what keeps the app from becoming rainbow UI.

## 3. The one-glance living answer (the hero)

A single component, one spec, rendered natively per platform:

- Line badge + destination + direction, the departure clock time, and a live
  countdown that ticks every second (already proven in 1.2.1 via absolute target
  timestamps: `Text(timerInterval:)` on Apple, `TimelineView(.periodic)` on watch,
  an interval ticker on web).
- Imminent state: a large red "now" hero, with a calm pulse and a haptic on Apple
  devices at the "now" moment.
- Secondary line: "then 3m, 5m" so the glance answers "and if I miss it".
- A source-confidence chip (see section 7) so the answer states its certainty.
- One tap deep-links to the station. No assistant or splash blocks the core.

## 4. Surfaces

- iOS app: answer-first home (nearest station hero + the "then" line), then the
  living map, then Ariadne as an ambient recovery affordance, not a wall.
- Apple Watch + widgets: the 1.2.1 live surfaces, elevated to the new tokens;
  Lock Screen widgets (rectangular/circular/inline), iOS tinted-mode rendering,
  deep-link complications, Smart Stack relevance.
- Android: the same hero and tokens via Compose; Glance widgets and (later) a
  Wear OS surface mirroring the watch.
- Web: a fast PWA home with the same hero and live countdown, installable, works
  offline from the bundled snapshot.

## 5. Information architecture and the region model

Athens is the launch surface, not the final identity. The IA must scale by region
rather than becoming one giant list of lines:

- Home: Nearby, Athens, Saved (and later: Greece Rail).
- Map: Athens urban, then Regional/National, then Scenic.
- Lines grouped by region: Athens (M1/M2/M3, T6/T7, A1-A4), Attica and nearby
  (Airport, Kiato/Corinth, Chalkida), Central (Lamia, Volos, Kalampaka/Meteora),
  Northern (Thessaloniki routes), Peloponnese/scenic (Diakopto-Kalavryta Odontotos).
- Ariadne: ask about any station, route, or train.

The app still opens on "nearest station, next train instantly". Only the data
model and navigation stop assuming Athens forever.

Region must land before full UI expansion. If we paint new screens on an
Athens-shaped domain first, 2.0.0 becomes paint, not a rail atlas. `region` is a
real domain concept, not display grouping (see section 6).

## 6. Data model: region-first and multilingual-first

Two principles, both structural rather than cosmetic:

Multilingual station identity becomes first-class, starting with EN/EL/SQ. Station
names are localized product data, not UI strings. Albanians are a large Athens
community, so SQ is core, not a patch; the same mechanism serves every future
language. Practically that is `nameEn` / `nameEl` / `nameSq` on the model.

Region is a real domain concept. The network stops being Athens-assumptive:

```kotlin
data class RailRegion(
    val id: RegionId,
    val nameEn: String,
    val nameEl: String,
    val nameSq: String?,
    val country: CountryCode,
    val primaryOperators: List<OperatorId>,
    val boundingBox: GeoBoundingBox,
)

data class Station(
    val id: StationId,
    val nameEn: String,
    val nameEl: String,
    val nameSq: String?,
    val regionId: RegionId,
    val operatorIds: List<OperatorId>,
    val sourceConfidence: SourceConfidence,
)
```

Line likewise gains `regionId` and `operatorIds` so IA, colors, and data-source
rules follow geography rather than a hardcoded Athens assumption. National data is
messier than metro, so `operator` and `sourceConfidence` are first-class fields,
not footnotes.

## 7. Source-confidence (a headline 2.0.0 pillar)

Syrmos knows what it knows and says so. This is one of the two or three defining
2.0.0 features, not a detail. Visible, calm states (never alarming):

- Live (for example: "Live suburban position")
- Scheduled (for example: "Scheduled metro departure")
- Offline snapshot (for example: "Offline snapshot active, updated 2h ago")
- Estimated (for example: "Estimated from frequency band")
- Operator link required (for example: "Check operator for live status")
- Unknown disruption (for example: "No live disruption data")

It appears in Ariadne answers, departure cards, station detail, route results, and
widgets / Live Activity where relevant. Example Ariadne answer, trilingual,
building on the 1.2.1 `WeatherSource` honesty pattern: "You can reach Chalkida by
suburban rail. This uses the scheduled timetable, not live disruptions. Check
operator announcements if timing is tight." Reviewers notice this because it shows
maturity.

## 8. Offline confidence

Shown beautifully rather than hidden, and distinct from per-answer source-
confidence: a calm, legible whole-app state, for example "Offline snapshot active,
updated 2h ago, schedules available underground, live resumes when online".

## 9. The living map (crown jewel, the visual proof of the data model)

Real rail geometry as the visual centerpiece: actual tracks (OSM), trains gliding
along the polyline, station-aware animation, branch handling (airport split, T7
loop, A4 Megara curve). Rare among transit apps, so push it visually. Reuses the
existing live-positions + 1s simulator the current map already runs. It lands
after source-confidence so the map becomes the beautiful proof of a trustworthy
data model, not an isolated visual upgrade.

## 10. Ariadne as the recovery layer

Ariadne shines when the user is confused or stressed, which is exactly the
platform moment: "I missed my stop", "I'm on the wrong train", "can I still make
it?", "is this train going to the Airport?", "which station for Meteora?", "metro
or suburban?". Every answer carries its source-confidence. Voiced read-back
(shipped in 1.2.1) extends to hands-free.

## 11. Design system delivery: KMP module as canonical tokens

The canonical design-token source is a KMP module, not JSON. Syrmos is already
KMP-first (schedule logic, models, data layer, projector, map concepts), so tokens
join that ecosystem. Motion, typography roles, platform adaptations, contrast
rules, and line/service semantics are safer to model in Kotlin than in dumb JSON.

```
core/designsystem-tokens/
  commonMain/
    SyrmosColorTokens.kt
    SyrmosTypographyTokens.kt
    SyrmosSpacingTokens.kt
    SyrmosMotionTokens.kt
    SyrmosShapeTokens.kt
```

One semantic source, generated outward: Kotlin tokens to Android Compose, to
shared Compose Web, to a generated Swift file, and to CSS variables for the web
shell. JSON only ever as an export artifact (`tokens.generated.json` /
`.swift` / `.css`), never the thing humans edit first.

A component catalog (line badge, departure row, one-glance hero, offline pill,
source-confidence chip, map station marker) shares names on every platform, so a
change lands everywhere.

## 12. Accessibility (award table-stakes)

Full VoiceOver/TalkBack labels, Dynamic Type / font scaling, Reduce Motion respect
(the "train glide" degrades to a cross-fade), and WCAG-AA contrast on the
light-first palette. Judges check these first.

## 13. Phased 2.0.0 roadmap

- M1 Design system foundation: the KMP token module + component catalog on all
  four platforms; restyle existing Athens screens to the light-first identity.
  Ship-able on its own.
- M2 One-glance hero everywhere: unify the home hero + live countdown across iOS,
  watch, widgets, Android, web.
- M3 Ariadne source-confidence + recovery layer: the trust layer. Source states
  across answers, departure cards, station detail, route results, widgets; recovery
  intents ("wrong train", "missed my stop", "can I still make it").
- M4 Living map polish: geometry-accurate map as the visual proof of the data model.
- M5 Region model + first regional pilot: Athens to Chalkida.

  ```
  M5: First regional pilot: Athens to Chalkida
  Goal: prove Syrmos can model a Greece-wide rail corridor beyond the Athens
  urban network while preserving the same one-glance UX, offline confidence,
  route geometry, multilingual station naming, and source-confidence model.
  Non-goal: full national rail coverage.
  ```

  Chalkida first because it is close enough to feel like a natural extension, has
  commuter/suburban behavior (not only tourist/intercity), tests regional rail
  without exploding scope, has a clear "Athens to another city by rail" mental
  model, and is useful for real users, not just a showcase.

- Candidate M6: Athens to Kiato / Corinth corridor (tests the Peloponnese corridor
  and airport/Kiato-style route complexity).
- Later, as a scenic showcase (not an early expansion): Diakopto to Kalavryta
  (Odontotos). Emotionally strong and award-friendly, but operationally special,
  so it comes after the region model is proven.

Rationale for the order: design system and hero create the surface;
source-confidence and recovery give Syrmos the trust layer that makes 2.0.0 feel
meaningfully smarter; the living map then becomes the beautiful proof of the data
model; region and the national pilot extend the atlas once the domain is stable.

## 14. Success signals

Usability and UI awards (Apple Design Award, Google Play/Material), organic
virality (share-worthy one-glance widgets and map), and acquisition interest
(a serious, offline, geography-scaling rail product, not a metro countdown toy).

## 15. Out of scope for 2.0.0 (YAGNI, explicit)

- No dark-first redesign. Light-first is the foundation.
- No full national rail planner or full national coverage in 2.0.0 (pilot one
  corridor first).
- No adding cities before the region model is stable.
- No fake live status when only scheduled or offline data exists.
- No LLM-generated transit facts (Ariadne stays a tool-only router).
- No mascot-heavy Ariadne redesign.
- No in-app ticketing/payments, no turn-by-turn walking.
- No redesign that slows the "next train instantly" path.

## 16. Open questions

- National rail data source and licensing (Hellenic Train feeds, per-operator).
- Exact codegen path from the KMP token module to the Swift and CSS exports.
- Wear OS scope and timing relative to the watchOS surface.
- Confirm Athens to Chalkida as M5 (Kiato/Corinth as M6).
