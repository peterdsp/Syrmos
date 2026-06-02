# Syrmos

**Live Athens rail times, all in one place.**

Syrmos is a Kotlin Multiplatform app for Athens metro, tram and suburban railway. It provides offline timetables, next departure calculations and route planning across the entire Athens rail network.

Built with Compose Multiplatform, targeting Android, iOS and Web from a single codebase.

> *Syrmos (συρμός)* is the Greek word for a train consist, the linked carriages that form a metro train.

## Features

- Browse all metro lines (1, 2, 3), tram lines (T6, T7) and suburban railway routes
- View station details with connecting lines and interchange info
- Check next departures based on current time and day
- Full timetable viewer with weekday, Saturday and Sunday schedules
- Schematic transit map
- Offline-first: all schedule data is embedded, no internet required
- Bilingual: English and Greek

## Architecture

The project follows a modular MVI architecture with clear separation of concerns.

```
androidApp / iosApp
    |
composeApp (shared UI entry point)
    |
feature modules (home, lines, stations, schedule, map, settings)
    |
core modules (domain -> data -> database, designsystem, navigation, model)
```

Each feature module depends on `core:domain` for business logic and `core:designsystem` for shared UI components. Feature modules never depend on each other.

### Tech Stack

| Technology | Purpose |
|------------|---------|
| Kotlin 2.1 | Language |
| Compose Multiplatform 1.8 | Shared UI |
| SQLDelight | Local database |
| Koin | Dependency injection |
| Ktor | HTTP client (future API) |
| Voyager | Multiplatform navigation |
| kotlinx-datetime | Time calculations |
| kotlinx-serialization | JSON parsing |

## Module Structure

| Module | Description |
|--------|-------------|
| `:core:model` | Domain data classes (Line, Station, Schedule, etc.) |
| `:core:common` | Shared utilities, Result type, datetime extensions |
| `:core:database` | SQLDelight schema, DAOs and drivers |
| `:core:data` | Repository implementations, JSON seed data, DataSeeder |
| `:core:domain` | Use cases (GetNextDepartures, PlanJourney, etc.) |
| `:core:network` | Ktor client scaffold for future API integration |
| `:core:designsystem` | Theme, colors, typography, shared components |
| `:core:navigation` | Screen routes and navigation graph |
| `:feature:home` | Favorites, nearby stations, next departures |
| `:feature:lines` | Line browser and line detail |
| `:feature:stations` | Station detail with departures |
| `:feature:schedule` | Full timetable viewer |
| `:feature:map` | Schematic transit map |
| `:feature:settings` | Language, theme, about |

## Transit Coverage

| Mode | Lines | Stations |
|------|-------|----------|
| Metro | Line 1 (Green), Line 2 (Red), Line 3 (Blue) | 65+ |
| Tram | T6, T7 | 50+ |
| Suburban Railway | Airport-Piraeus, Piraeus-Kiato, Kiato-Aigio | 30+ |

Schedule data sourced from official [STASY](https://www.stasy.gr) and [Hellenic Train](https://www.hellenictrain.gr) timetables.

## Getting Started

### Prerequisites

- JDK 17+
- Android Studio Ladybug or later (for Android)
- Xcode 15+ (for iOS, macOS only)

### Build and Run

```bash
# Clone
git clone https://github.com/peterdsp/Syrmos.git
cd Syrmos

# Android
./gradlew :androidApp:installDebug

# iOS
open iosApp/iosApp.xcodeproj

# Web
./gradlew :composeApp:wasmJsBrowserRun
```

### Run Tests

```bash
./gradlew allTests
```

## Roadmap

- [ ] Core domain models and data layer
- [ ] Embedded schedule data for all lines
- [ ] Home screen with next departures
- [ ] Line and station detail screens
- [ ] Schematic transit map
- [ ] Route planner with transfers
- [ ] Live data integration (when OASA API becomes available)
- [ ] Push notifications for service disruptions
- [ ] Widget support (Android, iOS)

## Data Sources

All schedule data is extracted from official PDF timetables published by:

- **STASY** (stasy.gr): Metro Lines 1, 2, 3 and Tram
- **Hellenic Train** (hellenictrain.gr): Suburban Railway (Proastiakos)

This app is not affiliated with STASY, Hellenic Train or OASA.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
