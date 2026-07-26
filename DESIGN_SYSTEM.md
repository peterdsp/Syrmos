# Syrmos Design System

Status: v1.2.10 redesign contract  
Design language: Calm Signal  
Platforms: iOS, Android, Web  
Canonical implementation: `core/designsystem`

## Purpose

Syrmos has grown beyond an Athens timetable. Version 1.2.10 includes nationwide
rail, replacement buses, a living map, source confidence, departure tracking,
weather and emergency context, fares, Ariadne, widgets, Live Activities, and
watch surfaces.

The product needs one visual language that can hold all of those capabilities
without turning Home into a collection of unrelated cards.

Calm Signal is that language:

> Show the next useful action, state how trustworthy it is, and keep the rest
> quiet until it is needed.

This document is the shared contract. It does not require the three platforms
to look pixel-identical. It requires them to express the same hierarchy,
semantics, component anatomy, content order, and state model through native
platform behavior.

## Design artifacts

| Artifact | Role |
| --- | --- |
| `DESIGN_SYSTEM.md` | Human-readable system contract |
| `core/designsystem/src/commonMain/.../theme/tokens/*.kt` | Canonical token source |
| `core/designsystem/tokens.generated.json` | Machine-readable token export |
| `composeApp/src/wasmJsMain/resources/design-tokens.css` | Generated web CSS tokens |
| `iosApp/iosApp/DesignSystem/SyrmosColors.swift` | Generated Swift token mirror |
| `docs/design/syrmos-brand-board.svg` | Visual brand and component board |
| `docs/design/REDESIGN_DESCRIPTION.md` | Copy-ready description of the completed work |
| `ops/designsystem/generate_tokens.py` | Kotlin to JSON, CSS, and Swift export |

## 1. Product idea

Syrmos is a calm mobility companion for Greece.

It is not a schedule painted with better colors. It interprets schedules,
location, service status, weather, and live data into one answer the user can
act on.

The core promise has three parts:

1. **Next move.** What should I do now?
2. **Confidence.** Is this live, scheduled, estimated, or available offline?
3. **Continuity.** If the situation changes, what is my next safe option?

Every screen should make those three answers easier to find.

## 2. Identity: Calm Signal

The earlier "Hellenic Rail Atlas" direction remains useful for maps, national
coverage, and the light-first palette. Calm Signal expands it into a product
language that also fits buses, tracking, alerts, weather, fares, and Ariadne.

### Personality

- Calm, never passive
- Precise, never clinical
- Greek, never tourist-themed
- Modern, never ornamental
- Reassuring, never falsely certain
- Native, never generic

### Visual signature

The system combines four recognizable cues:

1. Warm station-white surfaces
2. A single Aegean blue brand signal
3. Transit line color used only as service data
4. A continuous route motif with clear station nodes

The current logo owns the linked-route motif. That motif may also inform loading
motion, onboarding illustrations, route progress, and empty states. It should not
become decorative wallpaper behind operational content.

### Brand mark

The current production logo remains the Syrmos logo. Calm Signal redesigns the
interface around it. It does not redraw, flatten, simplify, or replace it.

Use the checked-in production assets directly:

- Default: `docs/assets/icon-default-1024.png`
- Dark: `docs/assets/icon-dark-1024.png`
- Tinted: `docs/assets/icon-tinted-1024.png`
- iOS source: `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset`

The default documentation asset and the current iOS default icon have identical
checksums. Do not recreate the logo with substitute typography or approximate
route geometry.

## 3. Cross-platform parity

### What must stay the same

- Information hierarchy
- Component names and anatomy
- State names and priority rules
- Semantic token names
- Source-confidence language
- Line and service colors
- Icon meaning
- Content order
- Empty, loading, offline, and error behavior
- Localization coverage in English, Greek, and Albanian

### What should adapt

| Concern | iOS | Android | Web |
| --- | --- | --- | --- |
| Type family | San Francisco | Roboto or system sans | System UI stack |
| Navigation | Native tab and sheet behavior | Material navigation and sheets | Responsive rail and bottom sheet |
| Icons | SF Symbols where semantics match | Material or shared vector icons | Shared inline SVG sprite |
| Feedback | Haptics and native transitions | Haptics and Compose motion | Hover, focus, keyboard, reduced motion |
| Surface material | Native material when useful | Tonal surface and restrained blur | Opaque or translucent CSS surface |
| Pointer state | Not applicable | Optional on large screens | Hover, active, focus-visible |

iOS remains the behavioral reference when platform implementations disagree.
The design system, not an iOS screenshot, is the visual reference.

## 4. Hierarchy

Every product surface follows the same five levels:

1. **Answer.** One clear action or decision.
2. **Trust.** Confidence, freshness, disruption, or offline state.
3. **Action.** One primary action and at most two secondary actions.
4. **Context.** Nearby stops, route progress, weather, map, or alternatives.
5. **Detail.** Full schedules, long lists, diagnostics, or source links.

Only one element may dominate a viewport.

- A tracked departure replaces the normal answer hero.
- A critical safety warning may temporarily outrank the hero.
- A normal weather card never outranks the next departure.
- Operator news never outranks an actionable service disruption.
- Ariadne supports the answer and recovery path. It does not become a second
  competing home screen.

## 5. Color

### Core palette

| Role | Light | Dark | Use |
| --- | --- | --- | --- |
| Brand | `#1466B8` | `#8ECAFF` | Primary actions, focus, selected navigation |
| Brand strong | `#0F4E8C` | `#8ECAFF` | High-emphasis text and pressed state |
| Canvas | `#F7F5F1` | `#0F1216` | App background |
| Subtle surface | `#EFEBE4` | `#171B21` | Grouped background and quiet controls |
| Raised surface | `#FFFFFF` | `#1B2028` | Cards, sheets, panels |
| Primary text | `#14181F` | `#E6ECF5` | Main content |
| Secondary text | `#5B636E` | `#9AA3AF` | Supporting content |
| Border | `#E0DACF` | `#2A2F37` | Dividers and card outlines |

### Transit colors

Transit colors carry data, not decoration.

| Token | Value | Meaning |
| --- | --- | --- |
| `metroGreen` | `#00843D` | Athens M1 |
| `metroRed` | `#DA291C` | Athens M2 |
| `metroBlue` | `#0072CE` | Athens M3 |
| `tram` | `#F39800` | Tram |
| `suburban` | `#6F2DA8` | Suburban rail |
| `national` | `#2A5C8A` | Regional and national rail fallback |
| `scenic` | `#B8860B` | Scenic services |
| `bus` | `#B45309` | Replacement and connecting buses |

Individual lines still use the exact color from line data. Mode colors are
fallbacks and legends.

### Trust and operational state

| State | Token | User meaning |
| --- | --- | --- |
| Live | `live` | Updated from a live source |
| Scheduled | `scheduled` | Exact schedule, not live |
| Offline | `offline` | Available from the local snapshot |
| Estimated | `estimated` | Derived or projected |
| Warning | `warning` | Attention needed |
| Disruption | `disruption` | Service or safety impact |

Never communicate a state by color alone. Pair it with text and, when space
allows, an icon.

### Bounded feature palettes

Weather and Ariadne may have their own illustrative palette, but it stays inside
their feature surfaces.

- Ariadne artwork may use its navy owl, teal maze, and red thread.
- Ariadne controls still use the shared brand, surface, text, and focus tokens.
- Weather gradients may describe conditions inside the weather card.
- Weather colors must not leak into navigation, buttons, or generic cards.
- Emergency weather uses the shared warning or disruption hierarchy.

## 6. Typography

Use the platform system family. Shared language comes from roles, scale,
weight, and rhythm, not from forcing one font file onto every platform.

| Role | Size / line | Weight | Use |
| --- | --- | --- | --- |
| `displayNow` | 44 / 48 | Bold | Primary countdown only |
| `headline` | 22 / 28 | Semibold | Screen or hero heading |
| `title` | 17 / 22 | Semibold | Card and section title |
| `body` | 15 / 20 | Regular | Main reading text |
| `label` | 13 / 16 | Medium | Controls, chips, metadata |
| `caption` | 11 / 14 | Medium | Supporting state |
| `clock` | 15 / 18 | Medium | Scheduled times |

Rules:

- Countdown and clock roles use tabular figures.
- The large countdown appears once per surface.
- Do not shrink operational content to fit one line. Reflow it.
- Greek and Albanian text must be tested at the same width as English.
- iOS uses Dynamic Type styles or scaled metrics.
- Android respects system font scale.
- Web zoom at 200 percent must preserve reading order and actions.

## 7. Space, shape, stroke, and elevation

### Space

Use the shared 8 point rhythm:

`0, 2, 4, 8, 12, 16, 24, 32, 48, 64`

The minimum interactive target is 44 by 44 points or CSS pixels.

### Shape

| Token | Radius | Use |
| --- | --- | --- |
| `sm` | 8 | Small badges and compact controls |
| `md` | 12 | Rows, fields, small cards |
| `lg` | 16 | Standard cards |
| `xl` | 24 | Hero cards, panels, major sheets |
| `pill` | 999 | Chips and segmented controls |

Avoid feature-specific radii such as 6, 10, 14, 18, or 20 unless the geometry
has a real functional reason.

### Borders and elevation

- Prefer a 1 point outline over a heavy shadow.
- Use shadow only to separate an overlay from moving map content.
- A card inside a card should usually be a grouped row, not another raised card.
- Glass is reserved for navigation, compact overlays, widgets, and live
  activity surfaces. It is not the default card treatment.

## 8. Motion

The signature curve is the train glide:

`cubic-bezier(0.16, 1, 0.30, 1)`

| Duration | Value | Use |
| --- | --- | --- |
| Fast | 150 ms | Press, hover, chip state |
| Medium | 300 ms | Card update, list insertion, panel change |
| Slow | 450 ms | Route draw, sheet detent, major transition |

Motion rules:

- Movement explains location, progress, or state.
- Train markers move along route geometry, not decorative loops.
- Countdown changes do not animate position.
- Tracking progress moves forward only.
- Under Reduce Motion, route and sheet motion becomes a short cross-fade.
- Severe-weather animation stops under Reduce Motion.

## 9. Icons and illustration

- Use one line-icon grammar at 1.75 point stroke for custom icons.
- Default rendered icon size is 20 inside a 44 minimum target.
- Use filled icons only for selected, live, or critical states.
- Vehicle markers use one directional triangle grammar across the three maps.
- Station markers use the shared dot, ring, interchange, and selected-halo
  contract.
- Do not use emoji as operational icons.
- Illustrations may be expressive. Operational controls remain simple.

## 10. Core component contracts

| Component | Required anatomy | Priority rule |
| --- | --- | --- |
| `AnswerHero` | Context label, line badge, destination, countdown, clock, confidence, then-times, action | One per surface |
| `TrackingCard` | Live state, destination, countdown, progress, route context, stop action | Replaces `AnswerHero` |
| `DepartureRow` | Line, destination, clock, relative time, confidence | Reusable in station, map, search, widget |
| `SourceConfidenceChip` | State icon or dot, localized state label | Never color-only |
| `OfflinePill` | Snapshot state, age, reassurance | App-level state, not an error |
| `LineBadge` | Exact line color, short line ID, accessible line name | Color is service data |
| `StationRow` | Name, served lines, optional distance, next useful departure | Name remains primary |
| `ServiceAlert` | Severity, affected service, concise action, source link | Critical can outrank hero |
| `WeatherCard` | Condition, now, near-term change, travel relevance | Context below transit answer |
| `EmergencyWeatherCard` | Severity, action, emergency contacts | May outrank hero while active |
| `UnifiedSearch` | Query, station and line results, Ariadne handoff | One entry point |
| `AriadneMessage` | Speaker, answer, confidence, grounded action | Same trust language as departures |
| `MapMarker` | Service color, selection, interchange, direction | Same geometry on all maps |
| `EmptyState` | What happened, useful next action, offline-safe fallback | No decorative dead end |

### AnswerHero behavior

The hero answers one question:

`Next [line] to [destination] in [time]`

Then it adds only the information needed to trust or recover:

- Scheduled clock time
- Source-confidence chip
- Next two alternatives
- One primary action

The hero must not contain network totals, general news, weather forecasts,
promotional text, or more than one primary action.

### Source-confidence labels

Use these concepts consistently in all three languages:

- Live
- Scheduled
- Estimated
- Offline snapshot
- Operator check required
- Unknown

The data model decides the state. The view never guesses from network reachability.

## 11. Home composition

The v1.2.10 Home surface has more capabilities than one viewport can present as
peers. Use this order:

1. Critical disruption or emergency, only when active
2. Tracking card, otherwise answer hero
3. Compact action row: track, search, map
4. Nearby or saved station context
5. Service status and concise weather context
6. Relevant live vehicles
7. Browse network entry point
8. General announcements and secondary information

Network totals and the full line catalog are browse content. They do not belong
above the next useful departure.

## 12. Map composition

The map is a living network, not a second visual brand.

- Base map remains quiet and label-light.
- Route color and geometry carry the scene.
- Stops resolve by zoom tier.
- Moving vehicles hide when the network is too dense to read.
- Selection changes the marker state and opens one shared detail surface.
- Search, map tap, and Ariadne all drive the same station detail contract.
- Line width, dash, station dot, halo, and vehicle triangle must share values
  through `MapDesignTokens`.

Map tokens should be moved into the same export pipeline after the foundation
adoption is complete. Until then, the Kotlin source and platform mirrors must be
verified together.

## 13. Ariadne

Ariadne is the recovery and explanation layer.

She shares Syrmos layout, typography, surfaces, action styles, and confidence
components. Her owl, maze, and thread form a bounded sub-brand.

Rules:

- Do not make the chat panel visually unrelated to the app.
- Do not use a second navigation system inside chat.
- Every transit answer exposes source confidence.
- Suggested prompts look like secondary actions, not ads.
- A recovery answer includes one concrete next step.
- On-device model download is a settings or capability state, not a premium
  promotion.

## 14. Widgets, Live Activities, and watch

These surfaces are the strictest expression of Calm Signal.

- One headline number
- One line
- One destination
- One confidence or live state when space allows
- No scroll
- No duplicated label
- Line tint is informational
- Deep-link to the exact station or tracked departure

Small surfaces may remove context, but they must not change meaning.

## 15. Content and localization

- English, Greek, and Albanian ship together.
- Use sentence case.
- Prefer verbs that describe the action: Track, Open station, Check operator.
- Avoid technical backend language.
- Keep the primary answer short enough to scan while walking.
- Preserve station and line names as product data, not UI string approximations.
- Announce confidence after the departure in screen-reader order.

Example:

`M3 to Airport, 4 minutes. Scheduled. Then 13 and 23 minutes.`

## 16. Accessibility

The minimum acceptance level is:

- WCAG AA contrast for text and meaningful icons
- 44 point minimum targets
- Dynamic Type and font scaling
- VoiceOver, TalkBack, and keyboard labels
- Visible keyboard focus on web
- Reduced motion support
- No state conveyed by color alone
- Logical reading order after responsive layout changes
- Text alternatives for map and vehicle state
- Emergency actions reachable without animation or precise gestures

## 17. Token architecture and governance

The Kotlin `Raw` blocks are the only editable value source.

```text
Kotlin token source
  -> Compose wrappers
  -> generated JSON
  -> generated CSS custom properties
  -> generated Swift enum
```

### Update workflow

1. Edit the relevant Kotlin token file.
2. Run `python3 ops/designsystem/generate_tokens.py`.
3. Review JSON, CSS, and Swift changes together.
4. Run `git diff --check`.
5. Verify light and dark themes on iOS, Android, and web.
6. Verify one Greek and one Albanian stress case.

Do not hand-edit generated token values.

### Semantic CSS aliases

Use product roles in feature CSS:

- `--sy-accent`
- `--sy-accent-strong`
- `--sy-background-canvas`
- `--sy-background-subtle`
- `--sy-background-raised`
- `--sy-text-primary`
- `--sy-text-secondary`
- `--sy-border-subtle`
- `--sy-status-positive`
- `--sy-status-info`
- `--sy-status-neutral`
- `--sy-status-caution`
- `--sy-status-warning`
- `--sy-status-critical`

Primitive tokens remain available for line data and low-level components.

The web dark tokens follow `body.dark-mode`, which is controlled by the app
theme toggle. They must not switch independently through
`prefers-color-scheme`.

## 18. v1.2.10 audit

The foundation exists, but token adoption is incomplete.

Snapshot from 2026-07-26:

- 81 hard-coded Compose color constructions were found across shared UI.
- 141 Swift color construction or named-color sites were found in the iOS app.
- 140 raw hex occurrences remain in `web-map.css`.
- Home and assistant surfaces use many radii outside the shared 8, 12, 16, 24,
  and pill scale.
- Compose uses the canonical color scheme, but the shared typography roles are
  not yet installed into `MaterialTheme`.
- Legacy `Color.kt` and iOS aliases still contain a tram-orange value that
  differs from the canonical token.
- The Compose Track action still uses an emoji while the web already uses a
  coherent line-icon system.
- Map tokens are shared by contract but are still mirrored separately.

These search counts are migration signals, not automatic bug counts. Some raw
colors are legitimate line data or feature illustration.

## 19. Product redesign for nationwide data

### 19.1 The new data reality

The original navigation and screen density were suitable for a smaller,
Athens-first product. The v1.2.10 seed now contains:

| Dimension | Current volume |
|---|---:|
| Lines | 31 |
| Unique stations | 389 |
| Line to station references | 429 |
| Regions | 4 |
| Metro lines | 5 |
| Tram lines | 2 |
| Suburban and rail lines | 16 |
| Bus and replacement services | 8 |
| Operational services | 29 |
| Under construction services | 2 |
| Product languages | 3 |

This volume changes the design problem. A flat list, a fixed airport tab, and a
four-step tracking picker no longer scale. The redesign must reveal the right
slice of the network without hiding the full dataset.

Use three levels of information:

1. **Answer:** what the traveler needs now.
2. **Context:** why the answer is trustworthy and what can change it.
3. **Inventory:** the complete timetable, line, station, or service data.

Never solve density by deleting useful data. Put inventory one deliberate
interaction below the answer.

### 19.2 Current product audit

| Surface | Current strength | Current pressure | Redesign direction |
|---|---|---|---|
| Home | Strong next-departure answer and tracking state | Weather, alerts, statistics, nearby places, live vehicles, and the full line catalog compete vertically | Personalized travel dashboard with one answer, one action row, and relevant modules |
| Lines | Familiar mode grouping | A 31-line flat catalog hides region and omits bus in the current Compose ordering | Region-first Network with search, filters, saved lines, and status |
| Line detail | Clear identity and station order | iOS and Compose expose different depth and long lines become large scrolls | Shared service hero, direction switch, journey strip, live state, alerts, and schedule |
| Timetables | Useful airport direction and date selection | Airport-only structure does not represent nationwide departures | Universal Departures destination with airport as a shortcut |
| Station detail | Line badges, departures, directions, and map context | Different page and sheet versions expose different facts | One station state model rendered as compact sheet or full page |
| Map | Real geometry, stations, vehicle direction, and quiet base map | Separate controls and panels can fragment context on larger screens | One map canvas and one adaptive context surface |
| Fares | Clear OASA product cards and official links | Athens-only products look global beside nationwide data | Tickets and fares hub scoped by region, operator, and journey |
| Settings | Language, theme, freshness, map, Ariadne, and diagnostics exist | Long technical list mixes traveler choices with developer controls | Personalization, data, Ariadne, map, tickets, and support groups |
| Ariadne | Grounded departure, route, weather, alert, fare, and recovery capabilities | A chat shell can hide the quality of structured answers and differs by platform | Contextual assistant with typed answer cards, explicit evidence, and shared state |
| Web | Powerful map-first exploration | "Athens rail map" language and dual panels understate nationwide scope | Same information architecture in a desktop rail and context panel |

### 19.3 Navigation models considered

Three models were tested against discoverability, dataset growth, contextual
assistance, and platform parity.

| Model | Structure | Main failure |
|---|---|---|
| A | Home, Network, Map, Departures, More | Five destinations require disciplined labels and no duplicate browse content |
| B | Home, Network, Map, More, with departures reached contextually | Full timetables become difficult to discover for frequent users |
| C | Home, Network, Map, Departures, Ariadne, with settings elsewhere | Ariadne becomes a separate destination instead of helping in the current context |

The canonical model is **A**.

The primary destinations are:

1. **Home:** current travel answer and relevant personal context.
2. **Network:** all regions, modes, lines, and stations.
3. **Map:** spatial exploration and live movement.
4. **Departures:** station, line, date, and full-day schedules.
5. **More:** preferences, data, tickets, support, and product information.

Ariadne is a global action, not a navigation tab. It is available from the app
header, global search, selected entities, and recovery states. It inherits the
current line, station, date, direction, region, and map context.

The same architecture applies on all platforms:

| Product concept | iOS and Android | Web |
|---|---|---|
| Primary navigation | Native bottom tab bar | Left navigation rail |
| Global search | Search field or search sheet | Persistent header search |
| Ariadne | Adaptive sheet | Right context panel |
| Entity details | Page or bottom sheet | Context rail beside map or content |
| Full inventory | Full-screen list | Split view or data table |

### 19.4 Responsive shell

| Width | Navigation | Content model |
|---|---|---|
| Compact, below 600 px | Bottom tab bar | One page, adaptive sheets, sticky bottom actions |
| Medium, 600 to 839 px | Bottom bar or native rail | List and detail where space permits |
| Expanded, 840 to 1199 px | Navigation rail | 360 to 400 px context rail plus main canvas |
| Wide, 1200 px and above | Navigation rail | 400 px context rail plus a bounded detail or map canvas |

Expanded layouts must not create two competing side panels. Ariadne temporarily
occupies the context rail, or opens beside a bounded content column when the
current context must remain visible.

The default readable content width is 960 px. Timetable inventory may expand to
1200 px. Text paragraphs remain narrower than data surfaces.

### 19.5 Home

Home answers "What matters for my next trip?" It is not another network
catalog.

Canonical vertical order:

1. Critical emergency or severe disruption, only when active.
2. `TrackingCard` when tracking is active, otherwise `AnswerHero`.
3. Compact action row: Search, Plan, Track.
4. Saved, recent, or nearby journey shelf.
5. Service state relevant to the current region and saved lines.
6. Compact weather travel impact when it can affect service.
7. Relevant live vehicles or announcements below the first viewport.
8. Network discovery link, not the complete line catalog.

Remove the full line list and static network totals from the default Home
scroll. They belong in Network. Do not show generic news ahead of a usable
travel answer.

Compact blueprint:

```text
+----------------------------------+
| Syrmos              Search  Ask  |
| Good morning                     |
|                                  |
| +------------------------------+ |
| | Next useful departure        | |
| | M3  Monastiraki -> Airport   | |
| | 8 min       08:42 platform 2 | |
| | Live  Updated 20 sec ago     | |
| +------------------------------+ |
| [ Search ] [ Plan ] [ Track ]    |
|                                  |
| Saved and recent                  |
| [ Home -> Work ] [ Airport ]      |
|                                  |
| Your service                      |
| M3 Normal    A1 Minor delay       |
|                                  |
| Weather may affect coastal lines  |
+----------------------------------+
```

`AnswerHero` and `TrackingCard` are mutually exclusive. If no location or
preference is available, the hero becomes a useful setup state with recent
stations and a direct search action, not an empty illustration.

### 19.6 Network

Rename the current Lines destination to **Network**. The new name represents
stations, buses, rail, metro, tram, regions, and future service types.

Network layout:

1. Search field with station, line, city, and operator matching.
2. Scope control: Nearby, Regions, Saved.
3. Region cards ordered by relevance, not permanently by Athens.
4. Mode filters: All, Metro, Tram, Rail, Bus.
5. Service filter: Running, Changed, Planned.
6. Line rows with line badge, endpoints, region, status, and optional next
   useful departure.
7. A clear link from each region to its stations and map extent.

The initial region set is Athens, Thessaloniki, Patras, and National. Display
names come from localized data, not hard-coded presentation strings.

Compact blueprint:

```text
+----------------------------------+
| Network                    Search |
| [Nearby] [Regions] [Saved]        |
| [All] [Metro] [Tram] [Rail] [Bus] |
|                                  |
| Athens                       9     |
| Metro, tram, rail and bus          |
| M1  Piraeus -> Kifissia     Normal |
| M2  Anthoupoli -> Elliniko  Normal |
|                                  |
| Thessaloniki                 6     |
| M1  New Railway -> Nea Elvetia    |
|                                  |
| Patras                       5     |
| Regional and replacement service  |
|                                  |
| National                    11     |
| Intercity and regional service    |
+----------------------------------+
```

Do not use line color as the only mode or status signal. Preserve the official
line color in the badge and route strip, then pair it with text and icon labels.

### 19.7 Line detail

Every line detail uses one shared information model:

1. **Service hero:** line badge, localized name, endpoints, mode, region,
   operator, and current status.
2. **Confidence row:** source, freshness, and offline or live state.
3. **Primary actions:** Track, View departures, View on map, Save.
4. **Direction switch:** both termini as full labels.
5. **Next useful departure:** based on selected station or location.
6. **Journey strip:** virtualized station sequence, selected stop, interchange,
   accessibility, and live vehicle positions.
7. **Service calendar:** today state, first and last service, exceptions.
8. **Active alerts:** only alerts that affect this service.
9. **Tickets and operator:** contextual official links.

For long lines, render the selected station and nearby stops first. "All stops"
expands the virtualized journey strip. Never load dozens of full departure cards
for every station in the first render.

On expanded layouts, the station sequence occupies the context rail and the map
or selected station detail occupies the main canvas.

### 19.8 Station detail

The map sheet and full station page share one state model and component names.

Station content order:

1. Station name, localized secondary name, region, and operator.
2. Save and directions actions.
3. Next departure answer.
4. Direction-grouped departure board.
5. Serving line badges and interchange explanation.
6. Accessibility, zone, facilities, and service facts.
7. Active station alerts.
8. First and last services.
9. Applicable ticket and operator links.

The compact sheet shows the identity, next four departures, facts, and primary
action. Expanding the sheet reveals the full page content without changing the
selected station.

### 19.9 Departures

Replace the Airport tab with **Departures**. Airport remains a saved shortcut
and a high-value query, but not the product architecture.

Departures supports four entry paths:

- Station first
- Line first
- Saved journey
- Recent or nearby context

The same reusable entity selector is used by Departures, Track, Network search,
and Ariadne clarification. It supports text search, recent entities, favorites,
region filters, and mode filters.

Selected timetable layout:

1. Context header with station, line, direction, and date.
2. Next three departures as answer cards.
3. Direction control.
4. Day picker with service exceptions.
5. Time groups: Now, Next hour, Later.
6. "Full day" inventory disclosure.
7. Source confidence, freshness, and operator note.

Compact blueprint:

```text
+----------------------------------+
| Departures                 Search |
| Monastiraki                       |
| M3 to Airport      Today  Change  |
|                                  |
| Next                              |
| 08:42   8 min    platform 2 Live  |
| 08:54  20 min    scheduled        |
| 09:06  32 min    scheduled        |
|                                  |
| [To Airport] [To Dimotiko Theatro]|
| [Sun 26] [Mon 27] [Tue 28]        |
|                                  |
| Next hour                         |
| 09:18  09:30  09:42  09:54        |
|                                  |
| [ View full day ]                 |
| Official feed, updated 20 sec ago |
+----------------------------------+
```

The full-day view may use a compact table on wide screens. Mobile keeps a
single reading column and sticky direction and date controls. Exact clock time
is always present even when a countdown is shown.

### 19.10 Map

The map remains the spatial crown of the product:

- Quiet base map
- Real service geometry
- Official line colors
- Station dots that declutter by zoom
- Directional vehicle markers
- One selected context at a time

Compact map chrome:

1. Search field and current region chip at the top.
2. Locate and Layers controls in one vertical group.
3. Optional live vehicle toggle inside Layers.
4. One adaptive bottom sheet for line, station, vehicle, or alert context.

Expanded map chrome:

1. Navigation rail.
2. One 360 to 400 px context rail for search results or the selected entity.
3. Map fills the remaining canvas.
4. Ariadne replaces or temporarily shares the context rail without covering
   essential map controls.

Mode and region visibility belong in one Layers sheet. Do not accumulate a
separate floating button for every filter.

### 19.11 Tickets and fares

Rename the current fares concept to **Tickets and fares** and scope every
answer.

The entry view asks for one of:

- Current journey
- Region
- Operator
- Known product

Product groups may include OASA Athens, Hellenic Train, airport products,
regional products, and replacement-service rules. Each card states coverage,
validity, price freshness, official source, and whether the purchase happens
outside Syrmos.

Never imply that an Athens pass covers a nationwide journey. When data is not
available, present "Check with operator" with the official link instead of an
estimated price.

Tickets and fares lives in More and appears contextually on route, line,
station, and Ariadne answers.

### 19.12 More and settings

Settings becomes the **More** destination with traveler-focused groups:

1. Personalization: language, appearance, saved region, accessibility.
2. Offline data and storage: snapshot age, refresh, downloaded regions, size.
3. Ariadne: capability, on-device model state, voice, privacy.
4. Map: style, vehicles, preferred region, reduced detail.
5. Tickets and operators.
6. Support and about: feedback, privacy, version, data sources.

Developer diagnostics remain available behind an explicit developer entry.
They do not sit among common traveler settings.

### 19.13 Alerts, weather, and service notices

These are travel modifiers, not independent visual systems.

- Critical emergency can temporarily lead Home and relevant detail pages.
- Line or station disruption attaches to the affected entity.
- Weather appears only when it can affect a trip, region, or service.
- General announcements stay below active travel information.
- All notices include affected scope, start time, expected duration when known,
  source, and a plain-language action.

Feature illustrations and weather colors remain bounded inside the notice.
General navigation and cards continue using the shared token system.

### 19.14 Ariadne as the intelligence layer

Ariadne answers "What should I do?" while Syrmos screens answer "What exists?"
It is not a generic chatbot and not a separate visual brand.

The current Ariadne owl artwork and lockup remain unchanged. The assistant uses
the same Calm Signal tokens, typography, card anatomy, line badges, and trust
states as the rest of Syrmos.

#### Entry points

- Ask action in the global header
- Natural-language result from global search
- "Ask about this" on line, station, map, timetable, fare, and alert surfaces
- Recovery action when a route or departure fails
- Optional voice input

Every invocation passes current context:

```text
region
selected line
selected station
direction
travel date and time
map viewport or selected vehicle
accessibility preference
saved and recent context, when permitted
network freshness
```

The visible context chips let the user remove or change any inherited detail.

#### Ariadne shell

The empty state contains:

1. Ariadne identity and offline or on-device capability state.
2. Current context chips.
3. A plain input field with text and voice actions.
4. Three relevant prompts, not a generic carousel.
5. A short privacy and source statement.

Do not lead with model size or technical architecture. Show the model download
state only when capability is unavailable, downloading, paused, or managed in
More.

Compact blueprint:

```text
+----------------------------------+
| Ariadne                     Close |
| [Monastiraki] [Today] [Step-free] |
|                                  |
| You                              |
| Can I reach the airport by 09:30?|
|                                  |
| Ariadne                          |
| +------------------------------+ |
| | Leave at 08:42               | |
| | M3 direct to Airport         | |
| | Arrive about 09:21           | |
| | 8 min to departure           | |
| | [Track] [Open route]         | |
| +------------------------------+ |
| Live feed, updated 20 sec ago     |
|                                  |
| The 08:54 arrival is too late.    |
|                                  |
| [ Ask a follow-up... ]      Mic   |
+----------------------------------+
```

#### Typed answer cards

Ariadne composes responses from shared structured components:

| Answer type | Required content | Primary actions |
|---|---|---|
| Departure | Station, line, destination, exact time, countdown, platform when known | Track, Open departures |
| Route | Leave time, steps, transfer stations, arrival, duration, walking, accessibility | Start, Open map |
| First or last service | Date context, exact service, exceptions | Save, View full day |
| Alert | Affected scope, severity, timing, practical action | View affected service |
| Fare | Coverage, price, validity, freshness, official source | Open official seller |
| Accessibility | Step-free state, known gaps, accessible alternative | Open route |
| Weather | Travel effect, affected service, timing | View affected lines |
| Recovery | What failed, safest alternative, tradeoff | Use alternative |
| Clarification | One missing decision with two to four concrete choices | Select choice |

The first sentence is the direct answer. Supporting explanation follows the
card. Do not repeat values already visible in the card.

Every factual answer ends with `SourceConfidence`:

- Source name
- Data freshness
- Live, scheduled, cached, or inferred state
- Confidence when supplied by the data model
- Scope limitation when relevant

#### Conversation behavior

- Ask one clarification question at a time.
- Prefer choice chips for known entities and directions.
- Preserve the selected context through follow-up questions.
- Show exact dates when "today" or "tomorrow" could be ambiguous.
- Offer a route or screen action instead of producing a long instruction list.
- If a result is stale, say so before the recommendation.
- If no grounded answer exists, explain the missing source and offer the safest
  next action.
- Never invent a platform, fare, accessibility feature, live position, or
  disruption.

#### Ariadne states

| State | Treatment |
|---|---|
| Ready with live data | Direct answer card and live trust footer |
| Ready with scheduled data | Scheduled label, exact time, no fake live motion |
| Offline with usable snapshot | Offline banner, snapshot age, grounded cached answer |
| Snapshot too old | Warning before answer and refresh action |
| On-device model unavailable | Deterministic supported answers remain; optional download action |
| Model downloading | Compact progress row that does not block departures or map |
| Low confidence | State uncertainty, show alternatives, request one clarification |
| No service | Explain date, direction, or service gap and offer next valid option |
| Route impossible | Explain blocking leg and show safe alternatives |
| Severe disruption | Critical state, affected scope, and recovery route |

On iOS and Android, Ariadne uses an adaptive sheet that can expand to full
screen for route comparison. On web, it uses the context panel and keeps the
selected map or timetable visible. The state model and answer components are
identical.

### 19.15 Search and entity selection

One search model serves every product surface. It classifies:

- Station
- Line
- Region
- Operator
- Saved journey
- Action
- Natural-language question

Search results order:

1. Exact and recent entities.
2. Nearby relevant entities.
3. Matching network inventory.
4. A direct answer when one can be grounded.
5. "Ask Ariadne" for a full question.

Entity rows always identify type and region. A station and line with similar
names must never be visually indistinguishable.

The reusable selector can enter a focused mode for Line, Station, Direction, or
Date. Tracking no longer starts from a fixed four-step wizard when context is
already known. A station page pre-fills the station; a line page pre-fills the
line; Departures pre-fills both when selected.

### 19.16 Data density and disclosure rules

- The first compact viewport contains at most one hero, one primary action row,
  and one supporting module.
- A card has one dominant value.
- Show the next three useful departures before the complete schedule.
- Use progressive disclosure for all stops, full day, diagnostics, and raw
  source information.
- Sticky controls are limited to the active task, such as date and direction.
- Use virtualization for station lists, search results, and full-day
  timetables.
- A loading skeleton matches the final layout and does not imply unavailable
  values.
- Empty states always offer a next action.

### 19.17 Cross-platform acceptance matrix

Parity means shared meaning, priority, and state. It does not require identical
native chrome.

| Contract | iOS | Android | Web |
|---|---|---|---|
| Five primary destinations | Native tab bar | Material navigation bar | Navigation rail |
| Home module order | Same priority | Same priority | Same priority in wider composition |
| Network region and mode filters | Native controls | Material controls | Chips and split view |
| Departures answer and full-day inventory | List and disclosure | List and disclosure | List or table |
| Station compact and full state | Sheet and page | Sheet and page | Context rail and page |
| Ariadne typed answer cards | Adaptive sheet | Adaptive sheet | Context panel |
| Confidence and freshness | Shared model | Shared model | Shared model |
| Current app logo | Exact production asset | Exact production asset | Exact production asset |

### 19.18 Redesign completion criteria

The redesign is ready for implementation when:

- Airport is represented as a shortcut inside universal Departures.
- All 31 lines and all four regions are discoverable from Network.
- Bus services are present on iOS, Android, and web.
- Home no longer duplicates the complete network catalog.
- Line and station detail use shared data and component contracts.
- Ariadne answers use typed cards and always expose source state.
- Search and entity selection are reused across Departures, Track, Network, and
  Ariadne.
- The map has one selected context surface at every width.
- Tickets and fares state their region, operator, coverage, and freshness.
- English, Greek, and Albanian layouts pass stress tests.
- The current production logo remains unchanged.

## 20. Migration sequence

### Phase 1: foundation

- Adopt semantic aliases in new work.
- Keep generator output deterministic.
- Wire shared typography into Compose.
- Add Swift type and shape helpers that consume generated values.
- Remove legacy token duplicates after call sites migrate.

### Phase 2: answer surfaces

- Refactor `AnswerHero`, `TrackingCard`, `DepartureRow`, confidence, and offline
  state first.
- Make Home priority identical on iOS and Android.
- Align web hero anatomy with native.
- Remove duplicate countdowns and competing primary actions.

### Phase 3: browse surfaces

- Lines, stations, schedules, fares, settings, and search.
- Replace raw radii, padding, and control colors with semantic roles.
- Keep dense tables one level below answer-first summaries.

### Phase 4: contextual features

- Bring Ariadne, weather, emergency state, alerts, and model-download state into
  the shared hierarchy.
- Define bounded feature palettes without changing general chrome.

### Phase 5: map and small surfaces

- Move map values into the export pipeline.
- Align widgets, Live Activities, watch, and store visuals.
- Preserve the production icon family across every redesigned surface.

## 21. Pull request checklist

- [ ] The first viewport has one dominant answer.
- [ ] Tracking replaces, rather than duplicates, the normal hero.
- [ ] Every state uses a semantic token.
- [ ] Line color represents actual service data.
- [ ] Source confidence is visible and comes from the model.
- [ ] No new raw color, spacing, radius, or duration was added without a token
      decision.
- [ ] iOS, Android, and web use the same component name and state model.
- [ ] Native platform navigation and input behavior remain native.
- [ ] English, Greek, and Albanian were checked.
- [ ] Dynamic text, screen reader, reduced motion, and web keyboard states were
      checked.
- [ ] Light and dark themes were checked.
- [ ] Generated artifacts match the Kotlin source.
