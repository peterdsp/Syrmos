# Syrmos

**Live Athens rail times, all in one place.**

Syrmos is a transit companion for everyone who rides the Athens metro, tram or suburban railway. Pick a station or let GPS find the nearest one, and see exactly when the next train arrives.

Built with Kotlin Multiplatform for Android, iOS and Web.

> *Syrmos (συρμός)* is the Greek word for a train consist, the linked carriages that make up a metro train.

---

## Why this exists

I have been commuting on Athens rail for years. Metro Line 2 to work, Line 3 to the airport, the suburban train to visit family. Every day the same question: how long until the next one?

The official apps cover buses well but rail is an afterthought. Timetables live in PDFs. Suburban railway schedules are buried on a separate website. There is no single place that answers "when does my train come" across all three networks.

I am originally from Tripolis, in the Peloponnese. The train used to reach my city, and I hope one day it will again. This project is partly a small push in that direction: if we build better tools for the rail network we have, maybe it helps people appreciate and expand the one we could have.

Syrmos started as a personal itch. I hope it becomes useful for anyone who rides a train in Athens.

## How it works

**Find your station.** Open the app and either:
- Let GPS detect the nearest station (works above ground and near station entrances)
- Browse by line and pick a station manually
- Search by name

**See the next trains.** The app calculates arrivals from embedded schedule data. No internet needed. It knows the day of the week, the time, and which services are running right now. You get a live countdown: "Line 3 towards Airport in 4 min".

**Everything is offline.** All schedule data ships with the app. Underground with no signal? It still works. The data comes from official STASY and Hellenic Train timetables, parsed and structured into a local database on first launch.

## Features

- Next departure countdown for any station, any line, any direction
- GPS-based nearest station detection
- Full timetable browser with weekday, Friday, Saturday and Sunday schedules
- Line browser with station lists, frequencies and operating hours
- Station detail with all connecting lines and interchange info
- Schematic transit map
- Route planner with transfer suggestions
- Offline-first: no internet required after install
- Bilingual: English and Greek

## Transit coverage

| Mode | Lines | Stations |
|------|-------|----------|
| Metro | Line 1 (Green), Line 2 (Red), Line 3 (Blue) | 65+ |
| Tram | T6, T7 | 50+ |
| Suburban Railway | Airport-Piraeus, Piraeus-Kiato, Kiato-Aigio | 30+ |

Schedule data sourced from official [STASY](https://www.stasy.gr) and [Hellenic Train](https://www.hellenictrain.gr) timetables.

## Architecture

Modular MVI architecture with unidirectional data flow. Each feature is an isolated Gradle module that depends only on shared core modules.

```
androidApp / iosApp
    |
composeApp (shared Compose UI entry point)
    |
feature modules (home, lines, stations, schedule, map, settings)
    |
core modules (domain -> data -> database, designsystem, navigation, model)
```

### Tech stack

| Technology | Purpose |
|------------|---------|
| Kotlin 2.1 | Language |
| Compose Multiplatform 1.8 | Shared UI across platforms |
| SQLDelight | Local offline database |
| Koin | Dependency injection |
| Ktor | HTTP client (for future live data) |
| Voyager | Multiplatform navigation |
| kotlinx-datetime | Schedule and time calculations |

### Modules

| Module | Role |
|--------|------|
| `:core:model` | Domain data classes |
| `:core:common` | Result type, time utilities, dispatcher abstraction |
| `:core:database` | SQLDelight schema, DAOs, platform drivers |
| `:core:data` | Repositories, JSON seed data, database seeder |
| `:core:domain` | Use cases (next departures, route planning, search) |
| `:core:network` | Ktor client scaffold for future API |
| `:core:designsystem` | Theme, colors, typography, shared components |
| `:core:navigation` | Screen routes and tab structure |
| `:feature:home` | Nearest station, favorites, next departures |
| `:feature:lines` | Line browser and line detail |
| `:feature:stations` | Station detail with arrival countdowns |
| `:feature:schedule` | Full timetable viewer |
| `:feature:map` | Schematic transit map |
| `:feature:settings` | Language, theme, about |

## Getting started

### Prerequisites

- JDK 17+
- Android Studio Ladybug or later (for Android)
- Xcode 15+ (for iOS, macOS only)

### Build and run

```bash
git clone https://github.com/peterdsp/Syrmos.git
cd Syrmos

# Android
./gradlew :androidApp:installDebug

# iOS
open iosApp/iosApp.xcodeproj

# Web
./gradlew :composeApp:wasmJsBrowserRun
```

### Tests

```bash
./gradlew allTests
```

## Roadmap

- [x] Project structure and core models
- [ ] Embedded schedule data for all lines and stations
- [ ] Next departure calculation engine
- [ ] GPS nearest station detection
- [ ] Home screen with live countdowns
- [ ] Line and station detail screens
- [ ] Schematic transit map
- [ ] Route planner with transfers
- [ ] Live data integration (when OASA API is available)
- [ ] Service disruption alerts
- [ ] Home screen widgets (Android, iOS)

## Data sources

All schedule data is extracted from official PDF timetables published by:

- **STASY** (stasy.gr): Metro Lines 1, 2, 3 and Tram
- **Hellenic Train** (hellenictrain.gr): Suburban Railway (Proastiakos)

Syrmos is not affiliated with STASY, Hellenic Train or OASA.

## License

MIT. See [LICENSE](LICENSE) for details.
