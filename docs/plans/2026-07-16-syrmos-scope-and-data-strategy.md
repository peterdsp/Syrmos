# Syrmos scope and data strategy: the rail app for Greece

Date: 2026-07-16
Author: Petros Dhespollari
Status: agreed direction. Supersedes nothing; sharpens
[PRODUCT_PRINCIPLES.md](../PRODUCT_PRINCIPLES.md) into a scope boundary and a
data plan.

## What Syrmos is

**Syrmos is rail.** Metro, tram and suburban today; every rail line in Greece
over time. Athens and Thessaloniki first, then the rest of the Hellenic Train
network, then other cities as they get rail.

The goal is to be the go-to rail app for Greece. That goal is reachable
precisely because Greek rail is a **finite, knowable list**. It is a set of lines
we can enumerate and finish, not an open-ended race.

## What Syrmos is not

Syrmos is not a navigation app. It does not do door-to-door routing across
walking, cycling, scooters and cars, and it does not compete with Google Maps.

This is a decision, not a deferral. It was taken against real user feedback
(2026-07-16) asking for bike+rail routing, combined car+metro, and live traffic.
That feedback was thoughtful and its diagnosis was right, but it describes a
different product. Recording the reasoning so it does not get relitigated:

- **Live traffic is unobtainable on our terms.** It is licensed (Google, TomTom,
  HERE) and it is precisely Google's moat. The same user correctly observed that
  car+metro is "pretty much impossible without a live traffic dataset like Google
  has". With cars out of scope, live traffic becomes moot: we never need it.
- **Head-on with Google Maps we lose.** They have traffic, POIs, global bike and
  road graphs, and car routing.
- **The winnable ground is where Google is weak**: offline (Google dies in the
  tunnel, which is where the metro is), companion framing (they hand you a table,
  we hand you an answer), and Greek rail depth nobody else will invest in.

The useful half of that feedback was the diagnosis, not the prescription: Google
already shows Athens metro times, so "we show a timetable" is not a reason to
exist. Offline, companion, and depth are.

What we kept from it: the suburban marker bug, live-marker map density, and live
Hellenic Train data. All rail. Out: bikes, scooters, cars, navigation, traffic,
and buses (OASA).

## The region model

`region` groups a line and its stations into a network. Three values:

- `athens` — M1, M2, M3, M3_AIR, T6, T7, A1-A4
- `thessaloniki` — TM1, TM2, TP1, TP2, TP3
- `national` — the intercity / long-distance network

`national` exists because an Athens-Thessaloniki train belongs to neither city.
It is the same problem as the Thessaloniki-Larisa corridor, one size larger: a
line legitimately spans regions, which is why the field is `region` and not
`city`.

National lines still behave correctly, because geometry does the work. A user in
Athens has Larissa Station as their nearest stop and sees the Thessaloniki train
without any special case. The map camera stays on the user's city and a national
line simply runs off-screen, pannable. The track-picker groups national lines as
Intercity. Hellenic Train announcements scope to `national`.

Region drives exactly five things: the default map camera, the no-GPS home hero,
track-picker grouping, announcement scoping, and weather coordinates.
Nearest-station stays global on purpose: if you are physically in Thessaloniki
the nearest station already is a Thessaloniki one, so a region filter would only
add a way to be wrong.

`region` shipped in migration `0018_region_and_status.sql` and is the seam every
future city hangs off. Adding a city is then a **data exercise, not an
architecture one**.

## Offline-first, opportunistically online

Two tiers. The boundary between them is the product.

**Tier 0 — bundled. Always works, radio off.**
Stations, lines, schedules (frequency bands or scheduled trips), station offsets,
fares, route geometry. Syrmos is fully functional on Tier 0 alone. This is the
moat: the Athens metro is underground, and underground is where Google Maps
stops being useful.

**Tier 0 must never be blocked by Tier 1.** No screen waits on the network. No
spinner stands between the user and an answer.

**Tier 1 — opportunistic, automatic, silent.**
Announcements, live positions, weather, timetable freshness. Fetched when the
device happens to have connectivity. Never announced, never blocking.

**The rule:** Tier 1 may only ever *improve* an answer Tier 0 already gave. A
feature that cannot degrade gracefully to Tier 0 does not ship. That rule is what
keeps "companion" true when the signal dies mid-tunnel.

**Automatic means no button.** Today there is a manual refresh. That is a
decision we failed to make for the user (litmus test 4) and it should go: sync on
launch, on foreground, and opportunistically in the background, silently.
Freshness is already surfaced honestly by the offline-alive indicator
(`FreshnessEvaluator`), which is the correct pattern: state what you know, do not
ask the user to fetch it.

Explicit non-goal: Tier 1 must not become a login, an account, or a sync
service. It is a cache warm-up.

## What every line needs (the data contract)

Adding any rail line anywhere in Greece needs exactly this. It is the checklist
for city 3 and beyond.

| Data | Source | Notes |
|---|---|---|
| Line: id, mode, names EN/EL, colour, terminals, region, status, sort order | operator + OSM | colour from the OSM route relation is authoritative |
| Stations: id, names EN/EL, lat/lng, region | **OSM route relation** | order comes from the relation, not from prose lists |
| Station order along the line | **OSM route relation** | see the Sintrivani lesson below |
| Route geometry | OSM relation id -> `snapshot-osm-shapes.py` | avoids the station-spline zigzag |
| Timetable | operator | frequency bands (metro/tram) or scheduled trips (suburban/intercity) |
| Station offsets | operator timetable, else distance-derived | flag estimated ones honestly |
| Fares | operator | region-scoped |

**Source the geometry and station order from OSM, not from marketing copy.** On
2026-07-16 the Thessaloniki design had Panepistimio before Sintrivani because it
was copied from a prose station list. The OSM relation order and the coordinates
(a monotonic run southeast) both disagreed, as did the official line map. Wrong
station order would have shipped into every departure board.

**Do not resolve OSM ids by web search.** Searching returns confident wrong
answers: a "Metro 1" relation hit is Brussels. Public Overpass endpoints also
rate-limit unreliably. Use a Geofabrik Greece extract with `osmium` locally; it
is fast (a Thessaloniki bbox cut takes ~2s) and it is the same data.

## Roadmap under this scope

1. **Thessaloniki** (in flight): migration landed, OSM data real and verified,
   `seed_thessaloniki.py` pending. TM2 opens end of July 2026 and is a `status`
   flip.
2. **Rail polish from real user feedback**: suburban markers render smaller and
   off the line (likely station-interpolation against OSM-drawn track, the same
   class of bug as the old T7 zigzag); live markers crowd the map.
3. **National / intercity network** (`region = national`): the Athens-Thessaloniki
   main line and the rest of Hellenic Train.
4. **Automatic sync**: delete the manual refresh.
5. **Live Hellenic Train data** if a feed exists. The Thessaloniki design already
   reserves the seam: TP lines stay out of the live-poll sets until there is
   something to poll.

## Open questions

- Does Hellenic Train expose any real-time feed, or is `LiveArrivalsProvider`
  still a no-op seam waiting on an operator?
- Which national lines actually run, at what frequency, and is there a per-station
  timetable or only endpoints (as with Larisa/Florina)?
- Patras and other cities: rail exists, but is there a published timetable to
  model?
