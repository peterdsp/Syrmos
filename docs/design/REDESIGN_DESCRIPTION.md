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

The proposed product navigation is Home, Network, Map, Departures, and More.
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
- **Network** replaces the flat Lines catalog and organizes all lines and
  stations by region, mode, status, favorites, and search.
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

The full line catalog and static network totals move to Network, where they are
easier to browse.

### Network and entity redesign

I designed a region-first Network for Athens, Thessaloniki, Patras, and
National services. It includes search, Nearby, Regions, Saved, mode filters,
service filters, and complete support for metro, tram, rail, and bus.

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
The same selector is used by Network, Departures, Track, and Ariadne.

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

The design contract requires the same Home priority, Network filters,
departure answers, station and line state, Ariadne answer cards, confidence,
freshness, and current logo on iOS, Android, and web.

### Implementation and validation guidance

I documented a phased migration:

1. Token and typography foundation
2. Answer and tracking surfaces
3. Network, station, departures, fares, settings, and search
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
