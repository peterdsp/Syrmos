<p align="center">
<img width="400" height="534" alt="image" src="https://github.com/user-attachments/assets/8839e263-4a6c-49c6-8a31-0554eec4e97b" />
</p>

<h1 align="center">Syrmos</h1>

<p align="center">
  <strong>Your next Greek train, instantly.</strong><br/>
  Metro &bull; Tram &bull; Suburban &bull; InterCity Railway
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Compose_Multiplatform-1.8-4285F4?logo=jetpackcompose&logoColor=white" alt="Compose" />
  <img src="https://img.shields.io/badge/SwiftUI-5-FA7343?logo=swift&logoColor=white" alt="SwiftUI" />
  <img src="https://img.shields.io/badge/Platform-iOS_|_Android_|_Web-333?logo=apple&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/License-BSD_3--Clause-green" alt="License" />
</p>

<p align="center">
  <a href="https://apps.apple.com/app/syrmos"><img src="docs/badges/app-store.svg" height="44" alt="Download on the App Store" /></a>
  &nbsp;
  <a href="https://play.google.com/store/apps/details?id=com.syrmos.android"><img src="docs/badges/google-play.svg" height="44" alt="Get it on Google Play" /></a>
  &nbsp;
  <a href="https://syrmos.peterdsp.dev"><img src="docs/badges/web-app.svg" height="44" alt="Open Web App" /></a>
</p>

---

A rail companion for Greece. Athens (metro, tram, suburban), Thessaloniki (metro + suburban), the InterCity corridor between them, and Patras suburban. Pick a station or let GPS find the nearest one and get a live countdown to your next departure. Works offline, underground, with no signal.

> *Syrmos (συρμός)* is the Greek word for the carriages that form a metro train.

<table align="center">
  <tr>
    <td align="center">
      <img width="370" height="800" alt="image" src="https://github.com/user-attachments/assets/a83aa1d4-9f15-4836-8e68-0ef32b69c9ea" />
    </td>
    <td align="center">
     <img width="370" height="800" alt="image" src="https://github.com/user-attachments/assets/c98d681f-22bf-4ad5-a072-941c174b03b1" />
    </td>
  </tr>
  <tr>
    <td align="center"><sub>Home</sub></td>
    <td align="center"><sub>Live Map</sub></td>
  </tr>
</table>

## Highlights

- **Offline-first.** Every release bundles a full snapshot of the API; the app launches with correct data on airplane mode and syncs silently when online.
- **Live train map** with simulated metro/tram positions and real-time suburban tracking from the Hellenic Train SSE feed.
- **Frequency-band projector** computes next departures from operator rules, so schedules stay correct without a client release.
- **Real OSM track geometry** for every line: T7 Piraeus loop, M3 airport branch, A4 Megara curve render along actual rail.
- **GPS nearest station**, trilingual EN/EL/SQ (Albanian first-class), light/dark theme, full timetables, station details, line browser.
- **Ariadne**, an offline transit assistant: ask in plain English, Greek or Albanian and she answers departures, last trains, routes and fares from the bundled data. A tool-only intent router with an optional on-device LLM &mdash; she never invents a fact.
- **Hot-patchable schedules** via `api-syrmos.peterdsp.dev`: fix a band on the Pi, every installed app sees it on next cold start.

## Coverage

Greek passenger rail, city by city. 26 lines, four regions.

| Region | Lines | Operator |
|--------|-------|----------|
| **Athens** | Metro 1/2/3, Tram T6/T7, Suburban A1-A4 | STASY, Hellenic Train |
| **Thessaloniki** | Metro TM1 (live), TM2 (Kalamaria, opening) &bull; Suburban Larisa, Florina, Sindos, Serres-Drama | Thessaloniki Metro, Hellenic Train |
| **National** | InterCity Athens&ndash;Thessaloniki, Athens&ndash;Leianokladi, Alexandroupoli&ndash;Orestiada&ndash;Ormenio (Evros) &bull; Rail-replacement buses: Paleofarsalos&ndash;Kalambaka, Volos&ndash;Larisa, Drama&ndash;Xanthi&ndash;Alexandroupoli, Kiato&ndash;Patra | Hellenic Train |
| **Patras** | Suburban Kaminia & Rio branches, Kato Achaia connecting bus | Hellenic Train |

Every timetable is transcribed from the official Hellenic Train / railway.gov.gr sources and stored as exact per-station times &mdash; never estimated. A line that is built but not yet open (Thessaloniki Line 2) renders greyed and carries no departures until it opens.

## Architecture

One Kotlin Multiplatform codebase. iOS uses SwiftUI + MapKit, Android uses Compose + osmdroid, web uses Compose for Wasm + Leaflet. Shared train simulation, schedule logic, data layer, and icons.

```
iosApp/         SwiftUI shell (Apple Maps)
androidApp/     Compose Activity shell (osmdroid)
composeApp/     KMP composition root, navigator, Koin wiring
feature/        home, lines, stations, schedule, map, settings
core/
  domain/       use cases, projector, LiveArrivalsProvider
  data/         repositories, DataSeeder, ScheduleSync
  database/     SQLDelight + platform drivers
  network/      Ktor services
  designsystem/ theme, shared components
  navigation/   Voyager tabs and routes
  model/        domain data classes
  common/       Result, datetime, geo
build-logic/    convention plugins for the 15+ modules
ops/            Pi-hosted FastAPI service + importers (syrmos-api, syrmos-web)
docs/           contribution workflow, case study, privacy, badges, screenshots
scripts/        importers, deployers, seed snapshotters
assets/         icon packs, line geometry, timetable PDFs
```

Backend lives on a Raspberry Pi behind Cloudflare Tunnel at `api-syrmos.peterdsp.dev`. It serves `/api/lines`, `/api/schedules`, `/api/holidays`, `/api/fares`, `/api/announcements`, `/api/trains` (SSE relay), and `/line-geometry/{id}.geojson`, all ETag-driven. Source of truth is SQLite, edited through a gated `/admin` UI.

**Stack:** Kotlin 2.1, Compose Multiplatform 1.8, SwiftUI, SQLDelight 2.1, Koin 4.1, Ktor 3.1, Voyager 1.1, osmdroid, Leaflet, kotlinx-datetime.

## Build and run

Prereqs: JDK 17+, Android Studio Ladybug+, Xcode 16+ (iOS).

```bash
# Android
./gradlew :androidApp:installDebug

# iOS: auto-detects project, picks latest simulator, builds, installs, streams logs
./scripts/simulator.sh
./scripts/simulator.sh --device "iPhone 16 Pro" --clean
./scripts/simulator.sh --release
./scripts/simulator.sh --list

# Web dev
./gradlew :composeApp:wasmJsBrowserRun

# Web release
./gradlew :composeApp:stageWebRelease
./scripts/deploy-web-to-pi.sh

# Tests
./gradlew allTests
```

## Data sources

Schedule data is encoded as operator rules (operating hours + frequency bands by daypart), not as pre-computed departure lists, so it stays accurate when the user's clock disagrees with the build clock.

- [STASY](https://www.stasy.gr): Metro 1/2/3 and Tram T6/T7
- [Hellenic Train](https://www.hellenictrain.gr): Suburban A1/A2/A3/A4 (PDFs archived in [assets/hellenic-train-timetables/](assets/hellenic-train-timetables/))
- [OASA 24mmm](https://www.oasa.gr/en/24mmm/): Saturday 24-hour grid, scraped daily

A daily Pi timer hashes upstream PDFs; when anything changes, the admin UI surfaces the diff before the next snapshot ships. Reference data (coords, icon rules, holidays, M3 split, T7 loop) lives in [ops/syrmos-api/pkg/](ops/syrmos-api/pkg/).

Syrmos is not affiliated with STASY, Hellenic Train, or OASA. Suburban ticket purchases open Hellenic Train's official site under their own terms.

## Releases and roadmap

Shipping: **iOS 1.0.5**, **Android 1.0.4**, **Web** (rolling). See [CHANGELOG.md](CHANGELOG.md) for what landed in each release and what is planned next. The changelog is the source of truth, kept in sync at every release.

## Contributing, privacy, license

- **Contributing:** PR-first workflow with a CI guard on `master`. See [contribution workflow](docs/CONTRIBUTING_WORKFLOW.md) and [.github/CONTRIBUTING.md](.github/CONTRIBUTING.md).
- **Privacy:** no data collection, no analytics, no tracking; location stays on-device. [Privacy Policy](docs/PRIVACY_POLICY.md).
- **License:** code is **BSD 3-Clause** ([LICENSE](LICENSE)), docs and assets are **CC BY-SA 4.0** ([docs/LICENSE-docs.md](docs/LICENSE-docs.md)) and may also be used under BSD 3-Clause. Security: [.github/SECURITY.md](.github/SECURITY.md). Conduct: [.github/CODE_OF_CONDUCT.md](.github/CODE_OF_CONDUCT.md).

Logo by Petros Dhespollari, inspired by designs of art conservator **Amalia Boura**. Map data © OpenStreetMap contributors (ODbL). Live positions via Hellenic Train at `railway.gov.gr`. Trademarks remain with their owners.
