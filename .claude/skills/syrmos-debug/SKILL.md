---
name: syrmos-debug
description: >-
  Diagnose and fix bugs in the Syrmos transit app with a method that respects its
  three-codebase architecture, plus the catalogue of bug classes that have already bitten
  this app. Use this skill whenever something is wrong rather than missing: "this is
  broken", "X is in the wrong place", "the times are off", "it crashes", "trains in the
  sea", "the countdown is wrong", "the chip is clipped", "it leaks / keeps polling", "works
  on one platform but not another", or any symptom report. It teaches reproduce-isolate-fix
  across iOS/Android/web/server, carries the known root causes and their fast-diagnosis
  playbooks, and insists on a regression test plus real verification so the bug cannot
  silently return. Pair with syrmos-test to lock in the fix.
---

# Syrmos debugging

A Syrmos bug lives in one (or more) of four independent codebases, so the first question
is always *which surface*, and whether the three clients have drifted. Trust the user's
signal about where the bug is NOT: when they said "it is NOT GPS, fix the rendering", they
were right, and chasing the data wasted time.

## Method

1. **Reproduce and pin the surface.** Which client shows it: iOS, Android, web, or all
   three? A bug on one surface and not another usually means the shared transform diverged
   (see syrmos-implement's shared-transform pattern), or it is platform-specific layout/
   lifecycle. A bug on all surfaces points at the data/seed or a shared assumption.
2. **Confirm the data is innocent before blaming the data.** For "wrong position / wrong
   value" bugs, verify the feed/seed first (intercept the fetch, check the raw numbers). If
   the data is clean, the bug is in projection, time math, or rendering, not the source.
3. **Isolate to the layer.** Data -> transform/projection -> time/timezone -> rendering/
   layout -> lifecycle. The known-bug catalogue below maps common symptoms to the layer.
4. **Fix at the root, in every affected codebase.** If a shared transform is wrong, fix all
   three clients, not just the one you reproduced on.
5. **Add a regression guard and verify.** Encode the fix so it cannot come back (a
   static-source guard or a behavioral test), then runtime-verify the real surface. Hand to
   **syrmos-test** for the exact commands and the verification tiers.

## Known bug classes (symptom -> root cause -> fix)

### "Live trains render in the sea / march south of their real spot" (web)
Not GPS, not the feed: a CSS bug. A custom Leaflet marker class set `position: relative`,
overriding the `position: absolute` Leaflet puts on `.leaflet-marker-icon`; relative icons
fall into normal flow and get pushed progressively down the DOM. **Rule:** any custom
Leaflet marker/divIcon class MUST keep `position: absolute`; style inner wrappers, never the
icon root's position/flow. Fast playbook: (1) wrap `window.fetch` to confirm the data is
clean; (2) instrument `L.LayerGroup.prototype.addLayer` to confirm Leaflet gets the right
`_latlng`; (3) compare each marker's inline `transform` translateY vs its
`getBoundingClientRect().top` -> a constant delta is correct, a spread means the icons are
not absolutely positioned; then check `getComputedStyle(marker).position`.

### "Departures / countdown are wrong by hours" (web)
Schedule math read the viewer's device timezone instead of Europe/Athens. Any time-of-day
or date math in `web-map.js` MUST go through the existing helpers `athensNow()`,
`nowMinutes()`, or `currentAthensParts()`, never `new Date().getHours()/getDay()/
toISOString()`. Mobile is already correct (KMP uses `TimeZone.of("Europe/Athens")`). Also:
suburban `realTimetableDepartures` must emit `time` (not `timeLabel`), and near-midnight
countdowns need a `+24*60` wrap or use the already-correct `.minutesAway`.

### "Chips clipped / wrapped mid-token (M3 -> M/3) or stretched to a full-width bar"
Short codes (M3, X95, 24/7) and status words (Scheduled, Live) must render as one atomic,
non-shrinking line. Root cause is always a chip in a compressing row without a single-line +
no-shrink constraint, and it is three separate fixes because each client lays out chips
independently: iOS `.lineLimit(1).fixedSize()` inside the file-local `FlowLayout`; Android
`maxLines = 1, softWrap = false` inside `FlowRow`; web CSS `white-space: nowrap;
flex-shrink: 0;` with the row `flex-wrap: wrap`. The opposite (a status chip drawn as a
full-width pale bar) is a column flexbox stretching it: `width: fit-content; max-width:
100%`. Never shrink text to illegibility or clip to hide overflow. Reproduce at narrow width.

### "Unbounded polling / loops / growing memory" (Android/KMP only)
KMP feature ViewModels create a custom `CoroutineScope` and launch loops in `init`, but the
scope was never cancelled. Voyager `TabNavigator` disposes the non-current tab, and a Koin
`factory` built a fresh VM on return while the old one's loops ran forever. **Rule:** any
KMP feature ViewModel that launches a loop/collector in a custom scope must be a Koin
`single`, OR expose `dispose(){ scope.cancel() }` wired from its screen via
`DisposableEffect(Unit){ onDispose { vm.dispose() } }`. Any VM injected at the shell level
with `koinInject` must be a `single`, or its state dies on every fold/unfold resize. Web
and iOS do not have this leak.

### "Live Activity / widget crashes after the target time" (iOS)
Never build `Date()...Date(timeIntervalSince1970: epoch)` from a stored `isDue` flag: the
flag is not refreshed while backgrounded, so once the target passes Swift's `...` traps
(lower > upper). Guard with a live `Date().timeIntervalSince1970 < epoch` check.

### "Albanian ë/ç or Greek tonos stripped"
A diacritics regression. Restore the accented source; but certain parser files must stay
accentless on purpose (guardrail tests enforce both sides). Do not blindly re-accent a
parser.

## After the fix

Add the regression test, run it, and runtime-verify the surface (see **syrmos-test**). If
the root cause was a shared transform, confirm the fix landed on all three clients. Ship via
**syrmos-deploy**. These bug classes recur, so if you fix a new one worth remembering,
suggest a memory entry.
