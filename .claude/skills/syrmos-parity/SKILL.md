---
name: syrmos-parity
description: >-
  Enforce 1:1 feature and UI parity between the iOS client and the Android/KMP client.
  Use this skill whenever a Syrmos change touches a user-facing surface on either
  platform: "add this feature", "fix this screen", "port this to Android", "does iOS
  have this", "why is Android behind", "check parity", "match iOS", or before tagging
  any release. Every user-visible change must land on BOTH clients in the same release
  or be explicitly recorded as a deliberate, justified divergence. This skill covers
  the capability-inventory method (never match by filename), the per-surface mapping
  between the two codebases, the divergences that are intentional, and the gate that
  must pass before a version bump.
---

# Syrmos iOS / Android parity

Syrmos ships three independent clients (see `syrmos-platform-architecture`). iOS is
Swift/SwiftUI under `iosApp/`. Android is Kotlin Multiplatform Compose under
`composeApp/` + `feature/*` + `core/*`. They share seed data and server contracts,
**not** UI code. Nothing keeps them in step automatically, so Android drifts behind
unless parity is checked deliberately.

## Hard rule

A user-facing change is not done when it works on one client. It is done when it
works on both, or when the gap is written into `docs/PARITY.md` with a reason.

---

## Method: compare capabilities, NOT filenames

Filename matching produces false positives in both directions. This is the single
most important rule in this skill.

A real audit of this repo on 2026-10-29 flagged `TravelNotificationSettings` as
"missing on Android". It was not missing. iOS keeps those toggles in their own file;
Android has the same nine toggles inline in `SettingsScreen.kt` as `NotifToggleRow`
entries. Matching by name would have produced a pointless port and a duplicated
settings screen.

The same audit flagged `RailPulse` as present on both because the string appeared in
four Kotlin files. Presence of a string is not presence of a feature.

It also recorded "reorder / hide Home sections" as a confirmed Android gap, because
`HomeSectionStore.swift` and `HomeCustomizeSheet.swift` exist on iOS. They compile and
they are dead: nothing presents the sheet, `HomeView` never reads the store, and the
store's section names no longer match what `HomeView` renders. Neither client ships
the capability. That is three filename false positives in one audit, and the third
one nearly bought a multi-day Android port of a feature that does not exist.

**So: before recording any iOS capability, grep for a reference to it from outside
its own file.** A type with no caller is not a feature.

```bash
# Does anything actually USE this iOS type?
grep -rn "HomeSectionStore" iosApp/ --include="*.swift" | grep -v "HomeSectionStore.swift:"
```

The reverse also matters: a capability can be on Android and deliberately *absent*
from iOS. Android showed a "Smarter Ariadne, Download (~1.1 GB)" card above the
conversation; iOS had deleted exactly that banner with its reasoning written into
`AriadneView.swift`. The parity fix was to remove it from Android, not to add it to
iOS. Read the comments at the iOS site before assuming iOS is behind.

So the method is:

1. **Enumerate what the user can DO on iOS**, surface by surface. Not files, not
   types. Verbs: "reorder the Home sections", "pinch-zoom an offline network map",
   "toggle nearby-train alerts".
2. **For each capability, find the Android code that delivers it**, under any name.
   Search for the behaviour (persisted keys, state fields, UI affordances), not the
   iOS type name.
3. **Only after step 2 fails** do you record a gap.
4. **Confirm the gap by running the app**, not by reading. See the verification tiers
   in `syrmos-test`.

### Searches that find behaviour rather than names

```bash
# Capability: can the rider reorder/hide Home sections?
grep -rniE "customi[sz]e|section.?order|reorder|hiddenSection|visibleSection" \
  --include="*.kt" feature/home composeApp core/common | grep -v "/build/"

# Capability: is there an OFFLINE bundled asset, or only an external link?
grep -rn "openUri\|UriHandler" feature/settings --include="*.kt" | grep -v "/build/"
```

An external link is **not** parity with a bundled offline asset. Syrmos is used
underground, where a `https://` link is a dead end.

---

## Surface map

| Capability | iOS | Android / KMP |
|---|---|---|
| Home board | `Views/Home/HomeView.swift` | `feature/home/.../HomeScreen.kt` |
| Home sections store | `Views/Home/HomeSectionStore.swift`, orphaned and unreachable | not a gap, see `docs/PARITY.md` |
| Map | `Views/Map/MapView.swift` | `feature/map/.../PlatformMapView.android.kt` |
| Station detail | `Views/StationDetailView.swift` | `feature/stations/.../StationDetailScreen.kt` |
| Lines / line detail | `Views/LinesView.swift`, `LineDetailView.swift` | `feature/lines/.../LinesScreen.kt`, `LineDetailScreen.kt` |
| Settings | `Views/Settings/SettingsView.swift` | `feature/settings/.../SettingsScreen.kt` |
| Notification toggles | `Views/Settings/TravelNotificationSettings.swift` | inline `NotifToggleRow` in `SettingsScreen.kt` |
| Offline rail map | `Views/Settings/StasyMapView.swift` + `Assets.xcassets/AthensRailMap` | `screen/AthensRailMapScreen.kt` (closed in 273) |
| Ariadne model opt-in | none offered anywhere; banner deliberately removed | Settings -> Ariadne engine, `AriadneModelOptIn` in `SettingsScreen.kt` |
| Alerts | `Views/AlertDetailSheet.swift` | `.../AlertDetailScreen.kt` |
| Saved departures | `Views/SavedDeparturesBoardView.swift` | `.../SavedDeparturesBoardScreen.kt` |
| Assistant (Ariadne) | iOS assistant views | `.../AssistantScreen.kt` |

Android-only surfaces (iOS reaches these differently, not gaps): `AirportHubScreen`,
`BrowseAllStationsScreen`, `FaresScreen`, `OnboardingScreen`, `ScheduleScreen`.

Keep this table current. A new screen on either side means a new row.

---

## Known deliberate divergences

Do NOT "fix" these. They are decisions, recorded so each audit stops re-finding them.

- **Interchange UI.** iOS switches the per-line timetable from the station detail
  view; Android resolves interchange in the map bottom sheet and shows a passive
  label. A true Android per-line switch is a new screen, not a port. See
  `syrmos-interchange-architecture`.
- **Android bus geometry.** The iOS straight-through-stops change was reverted on
  Android because most buses are absent from `routes.json` there; it needs a seed
  ordering fix first. Same memory.
- **Web is desktop only.** Not an iOS/Android concern, but do not drag web into a
  mobile parity fix. See `syrmos-web-desktop-only`.

---

## Device reality check (MIUI)

The physical Mi 10T refuses `adb shell input` and blocks instrumentation APKs. Before
claiming a parity gap is "verified on device", read `android-device-mi10t-testing`. A
screenshot proves rendering; it does not prove behaviour you could not tap through.
State which tier you reached, honestly.

Compose UI tests click through the semantics tree and so sidestep the input block,
but the test APK still has to install.

As of 2026-10-30 the device refuses installs too:

```
adb install ...     -> INSTALL_FAILED_USER_RESTRICTED: Install canceled by user
connectedDebugAndroidTest -> same, on the androidTest APK
```

Everything, including the Compose UI test route, is gated behind Developer options ->
**"USB debugging (Security settings)"**. Ask for it; do not work around it.

**Never install or boot an Android emulator to get around this.** The owner has asked
that no AVD, emulator package or system image exist on the machine. Android runtime
verification happens on the real phone or it is reported as not reached. There is no
third option.

### What drives the phone without input injection

Once the app is installed, `MainActivity` reads intent extras, so `am start` reaches
real screens with no tap at all:

```bash
adb -s <serial> shell am start -n com.syrmos.android/.MainActivity --es question "airport by 21:30"
adb -s <serial> shell am start -n com.syrmos.android/.MainActivity --es station "Syntagma"
adb -s <serial> shell am start -n com.syrmos.android/.MainActivity --es line "3"
adb -s <serial> shell am start -n com.syrmos.android/.MainActivity --es notif_deep_link "syrmos://station/M2_SYN"
```

The manifest declares no URL scheme, so those extras are the whole remote surface.
`uiautomator dump` plus `exec-out screencap` then read the result.

---

## The gate before any version bump

Run this before bumping `versionCode` in `androidApp/build.gradle.kts`:

1. List every user-facing change since the last release tag, both platforms:
   ```bash
   git log --oneline <last-tag>..HEAD -- iosApp/ | cat
   git log --oneline <last-tag>..HEAD -- composeApp/ feature/ core/ androidApp/ | cat
   ```
2. For each iOS-only change, decide: port it, or record it in `docs/PARITY.md`.
3. For each Android-only change, same in reverse.
4. Run the shared tests. KMP commonTest runs through the Android unit test task:
   ```bash
   export JAVA_HOME="$HOME/Library/Android/jdk/jdk-17.0.20.1+1/Contents/Home"
   ./gradlew :core:domain:testDebugUnitTest
   ```
   Confirm the result XML shows tests actually ran, since a bad `--tests` filter
   reports `BUILD SUCCESSFUL` on zero tests:
   ```bash
   grep -h "<testsuite " core/domain/build/test-results/testDebugUnitTest/*.xml
   ```
5. Only then bump the version. A consumed `versionCode` can never be reused.

If step 2 or 3 produces an unported user-facing change and you bump anyway, you have
shipped the drift this skill exists to prevent.
