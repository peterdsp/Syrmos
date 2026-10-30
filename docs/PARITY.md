# iOS / Android parity ledger

Every user-facing capability that exists on one client and not the other. A gap is
either scheduled to be ported or justified as a deliberate divergence. Nothing sits
here unexplained.

Method and the pre-release gate live in the `syrmos-parity` skill. Audit capabilities,
never filenames.

Last full audit: **2026-10-30**, against iOS `iosApp/` and Android `composeApp/` +
`feature/*` at versionCode 273.

---

## Closed in 273

| Capability | Was | Now |
|---|---|---|
| Offline Athens network map | iOS bundled `AthensRailMap.jpg` in a pinch-zoom view; Android only linked out to `stasy.gr`, useless underground | Android has Settings → "Network map (offline)", same bundled asset, pinch-zoom and pan. Verified on device by screenshot. |
| Greek station names on the Home board | Board rendered the seed's Latin `name`, so a Greek rider saw "Nikaia" | Shared `Station.localizedName(lang)`; board resolves through `destinationId`. 6 unit tests. |
| Notification permission | Only requested lazily, so FCM push was silently dead on Android 13+ | Requested at startup, guarded to API 33+. |

## Closed in the 2026-10-30 pass

| Capability | Was | Now |
|---|---|---|
| Ariadne's model download offer | Android put a "Smarter Ariadne, Download (~1.1 GB)" card at the top of the conversation. iOS offers that download NOWHERE: `AriadneModelStore.download()` has no caller on iOS at all | **Removed entirely on Android**, not relocated. `provideModelDownloader()` returns the no-op, so the shared UI renders no control anywhere. Ariadne is complete at install on every client: bundled data, the deterministic parser, and the cloud assistant. Riders who already downloaded the GGUF keep using it (the engine readout still reports `LOCAL_MODEL`), they are just never asked again. 3 tests pin UNSUPPORTED. |
| Engine readout after a download | Android derived the readout only from AICore / Gemini Nano, so a rider who had just downloaded the 1.1 GB GGUF was told the device cannot run an on-device model | New `AriadneEngineStatus.LOCAL_MODEL`, checked before AICore and counted as Clever mode. The readout re-reads when the download finishes. 6 unit tests. |
| Download failure modes | Android had no storage preflight, no HTTP status check and no verifying state, so a full disk cost 1.1 GB of transfer and the bar then sat at 100% through the hash | Preflight, response-code check, and `VERIFYING` / `INSUFFICIENT_STORAGE` states, matching the iOS store. |
| Voice "Hey Google ... on Syrmos" | All three App Actions in `actions.xml` targeted `com.syrmos.app`; the app ships as `com.syrmos.android`, so every capability resolved to nothing | Targets corrected, pinned by `AppActionsTargetTest` (3 tests). |
| "1 services" | All THREE clients hard-coded the plural noun on the board scope line | Shared `BoardScopeCopy.services(count, lang)` for the mobile clients, mirrored in `HomeView.boardScopeLabel` and in the web i18n tables (`board_service_one` in all four dictionaries). 5 Kotlin + 5 web tests. |
| Failed departures outside Athens | iOS distinguishes a failed network call from an empty board and offers the operator's own timetable. Android showed "No departures right now. The line is closed or today's service has ended." for a failed call, which is false: a Thessaloniki rider on one bar was told their line had shut down. A thrown call was worse, leaving "Loading departures..." on screen forever | Shared `StationDepartureFallback` (region resolution, operator name and URL, copy in 4 languages). The Android VM tracks `departuresFailed` and `region`; the screen shows the honest message plus an OASTH / Hellenic Train link. 9 unit tests. |
| Hellenic Train ticket sheet on Android | Three strings, including the payment liability notice, were Greek-or-English only, so Albanian and Italian riders got English on a payment disclosure | All four languages, matching the iOS wording. |
| No way back from a permanent permission denial on Android | iOS offers "Open Settings" in five places (`UIApplication.openSettingsURLString`: location service, map, GO, calendar, notification settings). Android had none, anywhere. After "Don't ask again" (or MIUI's Deny) `requestPermissions` is a silent no-op forever, so nearby stations, locate-me, station geofences and the widget snapshot all go quiet with nothing on screen explaining why | New `canOpenAppSettings()` / `openAppSettings()` platform pair (Android opens `ACTION_APPLICATION_DETAILS_SETTINGS`; iOS and Web report false so the control hides). Settings gains a Permissions section with the row and an explanation, in 4 languages. |
| Map stations too small to tap on Android | `buildZoomPin` drew the shared `MapDesignTokens` point sizes as RAW PIXELS, while `buildCapsuleTrainBitmap` directly below it multiplied by display density. So the vehicles were the intended size and the station dots were not: a 22-point dot came out 22px, roughly 8dp on a 2.75x phone. osmdroid hit-tests a marker against its icon bounds, so that was also the whole tap target, and riders could not open the station sheet | Pins scale by density, and the bitmap is padded transparently to the shared `MapDesignTokens.pinBitmapSize`, never below a 48-point tap target. The dot keeps its design size. 5 unit tests. |
| Trains kept running on a struck line | A strike greys the stations, but the map's vehicle layer projects from the bundled timetable and knew nothing about closures, so the network greyed out with trains still moving along it | `StationClosures.suspendedLineIds` (closure names the lines) and `linesFullyClosed` (every stop of a line is closed, which is how a strike is actually entered); the map filters both simulated and projected vehicles by the union. 9 unit tests. |
| Airport bus rows with no live vehicle | Android put "24/7" in the time slot styled exactly like a departure, tagged the row `OPERATOR_LINK` but rendered no chip and offered no link. iOS says in its bus sheet that this is operator information, not a guaranteed departure, and links to OASA | The chip now renders for `OPERATOR_LINK`, and the row opens OASA's telematics page, the same destination iOS uses. |

## Open gaps

| Capability | iOS | Android | Plan |
|---|---|---|---|
| (none recorded) | | | |

## Deliberate divergences

Do not "fix" these. Each is a decision.

| Capability | Divergence | Reason |
|---|---|---|
| Interchange per-line timetable | iOS switches the timetable from station detail; Android shows a passive label in the map bottom sheet | A real Android equivalent is a new screen, not a port. See `syrmos-interchange-architecture`. |
| Bus route geometry | iOS draws straight-through-stops; Android reverted it | Most buses are absent from Android's `routes.json`, so it needs a seed ordering fix first. Same memory. |
| Notification settings layout | iOS has a dedicated `TravelNotificationSettings` view; Android has the toggles inline in `SettingsScreen` | Same capability, different organisation. Not a gap. Toggle-for-toggle comparison done on 2026-10-30: service alerts, weather alerts, nearby station alerts, leave-by reminders, broadcasts, nearby trains, calendar reminders and the three calendar kinds all match. |
| Morning digest (07:00) toggle | iOS ships it; Android hides it | Already a deliberate decision, recorded in a comment at `SettingsScreen.kt`: nothing on Android schedules a 07:00 worker reading `notif_morning_digest`, so the toggle persisted a dead flag. Hiding a control that does nothing beats shipping it. Restore it with a `MorningDigestWorker`, not before. |
| Onboarding page 4 | iOS offers a favourites picker; Android explains map tools | Both are six-page intros; favourites are reachable on Android outside onboarding, so no capability is missing. Which one belongs in the slot is an owner call, not a port. |
| Closure line-scoping: Kotlin vs web | Kotlin `StationClosures.lineIdOfStation` reads the line off the station id prefix unconditionally; the web mirror `lineOfStationId(stationId, knownLineIds)` only accepts a prefix that is a real line id, else null | A genuine disagreement in the "one pure rule" contract, found while posting the 30/10 strike. It only bites a LINE-scoped closure on the national network, where ids are geographic (`GR_LAR` -> `GR`): Kotlin narrows the closure to a line the station never resolves to and the station silently stays open, while web closes it. Both agree on a station-scoped closure, which is how the strike is entered. Pinned by `ClosureLineScopeTrapTest` and left alone deliberately: changing shared closure semantics while a closure is live serving users is the wrong moment. Converge on the web behaviour (pass the known line ids in) as its own change. |
| Map vehicle toggles | iOS has one combined vehicles toggle; Android has separate train and airport-bus toggles | Both can hide vehicles. Android is finer-grained, not ahead. |
| Reorder / hide Home sections | `HomeSectionStore.swift` and `HomeCustomizeSheet.swift` exist and compile, but **nothing presents the sheet and `HomeView` never reads the store**: no reference to either type exists outside their own files, and the store's six section names no longer match what `HomeView` renders. The capability does not ship on iOS | Neither client lets a rider reorder or hide Home sections, so there is nothing to port. The 2026-10-29 pass recorded this as an Android gap by reading the iOS file list instead of the iOS behaviour; it was the third filename false positive in one audit. Either wire the sheet on iOS as new work on both clients, or delete the orphaned files. Do not "port" it. |

## Known defects, recorded not fixed

| Where | Defect |
|---|---|
| Android splash | `start_screen.jpg` has the tagline burned into the image: English only, and punctuated with the em-dash this repo forbids ("moving smarter", then the dash, then "every step of the way"). Burned-in text cannot localize. Fixing it means re-cutting the asset. |

## Not yet audited

Audited on 2026-10-30 at source level, capability by capability, both clients:
the assistant screen, the Home board, onboarding, the what's-new sheet, Explore /
Rail Pulse, station detail, line detail, the saved departures board, the airport
hub and alert detail.

Found at parity, no action: Explore / Rail Pulse (origin picker, quick report with
Undo, the privacy disclosure, universal search, the Ichnos feed), line detail
(alert banner, live trains, upcoming-departures fallback, station list), the saved
departures board (same rows and states; iOS deletes by swipe, Android by an icon
button, which is each platform's idiom for the same capability) and alert detail
(title, lines, summary, no-detail label, source link).

Nothing is now listed as unaudited at source level. What remains is behaviour that
only a running build can settle: anything timing, permission, GPS or push
dependent, and the rendering of everything changed in this pass.

### Why they are still unaudited

The Mi 10T refuses every remote drive path. `adb shell input` fails with
`SecurityException: INJECT_EVENTS`, and as of 2026-10-30 both the instrumentation
APK and the plain app APK fail to install with `INSTALL_FAILED_USER_RESTRICTED`:
Syrmos is not currently installed on the device at all. Developer options ->
"USB debugging (Security settings)" has to be on before any of this works.

Do not substitute an emulator. The owner has asked that no Android emulator exist
on the machine; the audit happens on the real phone or it waits.

What does work without that toggle, once the app is installed: `am start` with
`--es question`, `--es station` or `--es line` (the App Action extras
`MainActivity.handleAssistantIntent` reads) and `--es notif_deep_link
syrmos://station/<id>`, which reach the assistant and station detail without any
tap. There is no URL scheme in the manifest, so there is no deep link beyond those.
