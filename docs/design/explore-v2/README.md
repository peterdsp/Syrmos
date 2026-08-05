# Syrmos Explore V2 concept set

This set combines nationwide journey discovery with the existing everyday network browser. Explore has two equal modes:

- Discover: destinations, time-based ideas, live context, collections, events, and Ariadne
- Network: metro, suburban rail, tram, Intercity, scenic rail, replacement bus, stations, and lines

## Screens

1. [Explore Discover](01-explore-discover.svg)
2. [Explore Network](02-explore-network-search.svg)
3. [Explore by time](03-explore-by-time.svg)
4. [Explore Collections](04-explore-collections.svg)
5. [Explore Events](05-explore-events.svg)
6. [Ask Ariadne](06-explore-ariadne.svg)
7. [Explore with RailPulse](07-explore-with-railpulse.svg)
8. [RailPulse quick report](08-railpulse-quick-report.svg)
9. [RailPulse train detail](09-railpulse-train-detail.svg)
10. [RailPulse station detail](10-railpulse-station-detail.svg)
11. [RailPulse contributor profile](11-railpulse-contributor.svg)
12. [RailPulse Live Activity and Apple Watch](12-railpulse-live-surfaces.svg)

## Build handoff

- [Implementation prompt](explore-implementation-prompt.md)
- [Explore and RailPulse implementation plan](explore-railpulse-implementation-plan.md)
- [Image requirements](IMAGE_REQUIREMENTS.md)

## Core design decision

The original transport search is preserved inside Network, one tap from Discover. The same universal search sits above both modes and can find destinations, stations, lines, modes, and Ariadne suggestions. Time is the main comparison unit across Greece: total duration, next departure, changes, last return, fare, and confidence are shown before decorative travel content.

The visual examples use placeholder travel values. Production must render only values returned by verified project data and must label each as Live, Scheduled, Projected, Offline, or Approx. fare.

## RailPulse integration

RailPulse is the live community layer inside Explore, Network, train detail, station detail, tracked journeys, Ariadne, Live Activities, and Apple Watch. It is intentionally not a third root mode or another bottom-navigation tab.

Explore shows only fresh, relevant community intelligence. The full feed stays attached to the affected train, station, line, or journey. Reports are structured, time-limited, independently confirmed, and clearly separated from official operator information.

RailPulse applies privacy constraints inline: no accounts, names, email, aliases, photos, free text, exact location, public profiles, named leaderboards, or stable contributor identifiers. Reports use short-lived unlinkable proofs, raw anonymous events expire after aggregation, and badges remain on-device. If those unlinkability and no-log guarantees cannot be implemented, contribution writes stay disabled and RailPulse remains read-only.
