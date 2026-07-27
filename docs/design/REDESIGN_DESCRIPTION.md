# What I Created for the Syrmos v1.2.10 Redesign

## Short description

I created a complete cross-platform design system and product redesign for
Syrmos v1.2.10. The work keeps the current Syrmos app logo unchanged and
introduces one shared visual and interaction language for iOS, Android, and
web.

The new system is called **Calm Signal**. It is designed around one principle:
show the traveler the next useful answer first, explain how trustworthy it is,
and reveal the complete data only when it is needed.

I analyzed the current Home, Lines, line details, station details, airport
timetables, map, fares, settings, tracking, alerts, weather, widgets, watch
experience, and Ariadne assistant. I then redesigned how those areas should
work now that Syrmos contains nationwide data, 31 lines, 389 unique stations,
four regions, four transport modes, live and scheduled states, and three
languages.

The proposed product navigation is Home, Explore, Map, Departures, and More.
The old Airport tab becomes a useful shortcut inside a universal Departures
area. Ariadne remains available everywhere as a contextual assistant instead
of becoming an isolated tab.

## Complete description

### One shared design language

I defined the Calm Signal product idea, brand hierarchy, color roles,
typography, spacing, shapes, elevation, motion, icons, content rules,
accessibility requirements, responsive behavior, and cross-platform parity
rules.

The system preserves native iOS, Android, and web interaction patterns while
requiring the same information priority, component anatomy, state names, and
trust signals on every platform.

### The existing logo was preserved

I reused the exact current production Syrmos logo. I did not redraw, simplify,
or replace it. The SVG brand board embeds the current app logo and shows how it
belongs beside the Syrmos wordmark, colors, typography, trust states, answer
hero, and tracking card.

### Machine-readable design tokens

I connected the existing Kotlin design token source to generated artifacts for
all three platforms:

- Machine-readable JSON tokens
- Web CSS custom properties
- Swift color values for iOS
- Compose token wrappers for Android and shared UI

I also added semantic aliases such as accent, canvas, raised surface, primary
text, subtle border, positive, caution, warning, and critical. The web dark
theme now follows the app's explicit theme state.

### A nationwide information architecture

I redesigned the app architecture around the current data volume:

- **Home** gives one immediate travel answer, current tracking, relevant saved
  journeys, and only the service information that matters now.
- **Explore** replaces the flat Lines catalog with a destination-first,
  engagement-driven surface: curated destination cards + personal gravity
  (recent trips, saved stations, smart suggestions).
- **Map** keeps the live geographic network as the main canvas with one
  selected context panel or sheet.
- **Departures** replaces the airport-only timetable and supports any station,
  line, direction, and date.
- **More** groups personalization, offline data, Ariadne, map preferences,
  tickets, operators, support, and product information.

Airport information remains easy to reach as a saved shortcut and search
result, but it no longer limits the timetable architecture.

### Home redesign

I reorganized Home so it no longer behaves like a collection of unrelated
cards. A critical disruption may lead when necessary. Otherwise, the active
tracking card replaces the normal departure hero. Search, Plan, and Track form
one compact action row. Saved or nearby journeys, relevant service status, and
travel-impacting weather follow below.

The full line catalog and static network totals move to Explore, where they are
organized by destination and personal relevance rather than flat infrastructure
lists.

### Explore and entity redesign

I designed a destination-first Explore tab replacing the old Lines catalog. It has
two segments: Destinations (curated place cards with emotional hooks, like "Athens
Airport: your fastest route to the terminal") and Your Network (recent trips, saved
stations, smart suggestions based on time/location/usage). A "Browse all 389
stations" row at the bottom opens the full region-grouped station list. The tab
covers Athens, Thessaloniki, Patras, and National services with search, Nearby,
Regions, Saved, mode filters, and complete support for metro, tram, rail, and bus.

I defined a shared line detail with a service hero, source freshness, primary
actions, direction switch, next departure, journey strip, live vehicles,
service calendar, alerts, and official ticket or operator links.

I also unified station detail. The compact map sheet and full station page now
share the same state and progressively reveal departures, lines,
interchanges, accessibility, zone, alerts, first and last service, directions,
and applicable ticket information.

### Universal departures

I replaced the airport-only timetable concept with a universal departure
board. A traveler can start from a station, line, saved journey, recent search,
or nearby context.

The screen shows the next three departures first, then direction, day, service
exceptions, the next hour, later services, and an optional full-day inventory.
Exact time remains visible beside countdowns, and every result includes source
and freshness information.

### Map redesign

I kept the map visually quiet so official line geometry, stations, and
directional vehicles remain dominant. Search, region, location, layers, and
live vehicle settings are consolidated instead of becoming many floating
controls.

Mobile uses one adaptive bottom sheet for station, line, vehicle, or alert
context. Desktop uses one context rail beside the map. The layout avoids two
competing side panels.

### Tickets and fares

I expanded the Athens-focused fare view into Tickets and fares. The new concept
scopes information by journey, region, operator, and product. Every fare states
its coverage, validity, freshness, official source, and whether purchase
happens outside Syrmos.

This prevents an Athens pass from appearing to cover a nationwide trip and
provides a safe official operator action when Syrmos does not have a grounded
price.

### Journey fare planner (from -> to -> price)  [added 2026-07-27]

A dedicated fares menu lives in the right rail (desktop) and as a Tickets tab
entry (mobile). It carries the complete, grounded fare tables for every network:

- Athens metro / tram / suburban (OASA/STASY zones + airport products).
- Thessaloniki metro + suburban.
- All Greece suburban (Athens proastiakos, Patras suburban zone grid A1/A/B/C,
  Thessaloniki suburban), each with full price + discounted price + monthly card.
- All intercity (IC / regional) fares.
- All rail-replacement bus fares (the corridors served by coach: TL1, KB1, VL1,
  DX1, KP1, and any others).

On top of the tables sits a journey fare planner: the traveler picks a start
station and a destination station, and Syrmos returns the exact price (full and
discounted), the operator, the ticket product, and the official-source link.
The picker is station-to-station across the whole nationwide network, not just
Athens, and it resolves the correct zone/operator automatically.

Ariadne must carry and provide the same capability conversationally: "how much
from X to Y" returns a grounded fare card (price, discounted price, product,
operator, source, freshness), and "cheapest way from X to Y" reasons over the
zone/product options. Fares are grounded data only - when Syrmos lacks a real
price for a pair, Ariadne says so and offers the official operator link rather
than estimating. Source of truth for the fare data: the operators' own tables
(hellenictrain.gr, oasa.gr, oasth.gr), transcribed like the timetables, never
invented.

### Ariadne redesign

I redesigned Ariadne as the intelligence layer across the whole product, not
as a generic chatbot. Ariadne can inherit the current station, line, direction,
date, region, map selection, accessibility preference, and data freshness.
Visible context chips allow the traveler to change or remove that context.

Ariadne answers with structured cards for:

- Departures
- Routes and transfers
- First and last services
- Alerts
- Fares
- Accessibility
- Weather effects
- Recovery alternatives
- Clarification choices

Each answer leads with the direct result, provides useful actions such as
Track, Open route, Open map, or View full day, and ends with source,
freshness, live or scheduled state, and confidence.

I defined Ariadne behavior for live data, scheduled data, offline snapshots,
stale data, unavailable on-device models, model downloads, low confidence, no
service, impossible routes, and severe disruption. The exact current Ariadne
owl artwork remains part of the subbrand, while the interface uses the shared
Syrmos design system.

### Search, tracking, and large data

I designed one reusable search and entity selector for stations, lines,
regions, operators, saved journeys, actions, and natural-language questions.
The same selector is used by Explore, Departures, Track, and Ariadne.

Tracking no longer forces a fixed four-step flow when the app already knows the
line or station. Context pre-fills known values, reducing unnecessary steps.

Large station lists, line stops, search results, and full-day timetables use
progressive disclosure and virtualization. The app keeps all available data
without putting all of it in the first viewport.

### Responsive and cross-platform behavior

I specified compact, medium, expanded, and wide layouts. Phones use native
bottom navigation and adaptive sheets. Tablets can use list and detail
layouts. Desktop web uses a navigation rail, one context rail, and a bounded
main canvas.

The design contract requires the same Home priority, Explore filters,
departure answers, station and line state, Ariadne answer cards, confidence,
freshness, and current logo on iOS, Android, and web.

### Implementation and validation guidance

I documented a phased migration:

1. Token and typography foundation
2. Answer and tracking surfaces
3. Explore, station, departures, fares, settings, and search
4. Ariadne, alerts, weather, and recovery states
5. Map, widgets, Live Activities, watch, and store visuals

I also added a pull request checklist covering answer hierarchy, semantic
tokens, line colors, trust state, platform parity, localization, accessibility,
light and dark themes, and generated artifact consistency.

## Result

The result is not only a new visual style. It is a complete product structure
for a much larger Syrmos: one brand, one data hierarchy, one interaction
language, and one trust model across iOS, Android, web, widgets, watch, and
Ariadne.
