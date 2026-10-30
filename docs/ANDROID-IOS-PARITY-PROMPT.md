# Android ↔ iOS full parity prompt

Paste this to run a complete parity pass. It is written to be used verbatim.

---

## Prompt

> Bring the Syrmos Android/KMP client to 1:1 parity with the iOS client, in UI and in
> function, end to end.
>
> **Method, in this order. Do not skip step 0.**
>
> **0. Load the `syrmos-parity` skill first.** It holds the surface map, the
> capability-inventory method, and the list of divergences that are deliberate. Do not
> re-litigate those.
>
> **1. Build the capability inventory, not a file list.**
> Walk the iOS app surface by surface and write down what the rider can *do*, as verbs.
> "Reorder the Home sections." "Pinch-zoom an offline network map." "Toggle nearby-train
> alerts." Never compare filenames: iOS keeps notification toggles in their own file
> while Android has the same nine toggles inline in `SettingsScreen.kt`, so a name diff
> reports a gap that does not exist and would produce a duplicate settings screen.
>
> **2. For every capability, hunt the Android implementation under any name.**
> Search for behaviour: persisted keys, state fields, UI affordances. A string appearing
> in a Kotlin file is not proof the feature exists. Only record a gap after this search
> genuinely fails.
>
> **3. Classify every gap before writing code:**
> - **Port**: Android should have it, build it.
> - **Deliberate divergence**: record it in `docs/PARITY.md` with the reason.
> - **Android-ahead**: iOS is the one missing it; say so explicitly.
>
> **4. Rank what you port by rider impact, not by effort.** A rider underground with no
> signal is the default case. An external `https://` link is NOT parity with a bundled
> offline asset.
>
> **5. Implement, with tests for every change, and run them.** KMP commonTest runs via
> `./gradlew :core:domain:testDebugUnitTest`. Confirm tests actually executed by reading
> the result XML; a bad `--tests` filter prints `BUILD SUCCESSFUL` having run nothing.
>
> **6. Verify on the real surface and state your tier honestly.** "It compiles" is not
> verification. On the physical MIUI device, `adb shell input` is refused, so say plainly
> which screens you confirmed by screenshot, which by test, and which you could not reach.
>
> **7. Report as a table**: capability | iOS | Android before | Android after | verified how.
>
> **Hard rules.** No em-dash in any prose, code, comment, commit or PR. No AI references
> anywhere in the repo or in git history. Minimal diffs, follow the neighbouring code's
> conventions. Never bump a `versionCode` that has been used.

---

## Why this prompt is shaped this way

Each instruction exists because the obvious approach failed in a real audit on
2026-10-29.

**Why "capabilities, not filenames".** A filename diff of the two codebases reported
`TravelNotificationSettings` as missing from Android. Android has those toggles, inline
in `SettingsScreen.kt`. Acting on that diff would have produced a second settings screen.
The same diff reported `RailPulse` as present on Android because the string occurs in
four Kotlin files, which says nothing about whether the feature works.

**Why "hunt under any name".** The two clients were written independently. Equivalent
behaviour is routinely organised differently: iOS favours one file per sheet, the KMP
side favours composables inside a parent screen.

**Why "an external link is not parity".** Android's Settings opens `stasy.gr` in a
browser. iOS bundles `AthensRailMap.jpg` and renders it in a pinch-zoom view that works
with no signal. For a transit app used in tunnels these are not the same feature, though
a naive audit scores both as "has a network map".

**Why "state your verification tier".** The MIUI test device blocks input injection and
instrumentation installs, so most screens cannot be driven from the host. Claiming
end-to-end verification that did not happen is worse than reporting a partial pass.

**Why "rank by rider impact".** Android drifts behind in ways that are invisible in a
diff and obvious to a rider standing on a platform.

---

## Confirmed gaps as of 2026-10-29

Evidence-backed, after applying the method above.

| Capability | iOS | Android | Verdict |
|---|---|---|---|
| Reorder / hide Home sections | `HomeSectionStore.swift` + `HomeCustomizeSheet.swift` (6 sections, persisted, 4 languages) | nothing | **PORT** |
| Offline Athens rail map | `StasyMapView.swift` + bundled `AthensRailMap.jpg`, pinch-zoom, works offline | external link to `stasy.gr` only | **PORT** |
| Travel notification toggles | `TravelNotificationSettings.swift` | inline `NotifToggleRow` in `SettingsScreen.kt` | not a gap, verify toggle-for-toggle |
| Interchange per-line timetable switch | station detail view | map bottom sheet, passive label | deliberate divergence |
| Bus route geometry | straight-through-stops | reverted, needs seed ordering fix | deliberate, tracked |

Anything below this line has not been audited yet and must not be reported as parity.
