# Phase Z visual baselines — 3.0.0

Captures for the S01-S10 matrix defined in
[`docs/ops/RELEASE-3.0.0-GATE.md`](../../ops/RELEASE-3.0.0-GATE.md).

## What is here

| | |
|---|---|
| Platform | iOS only so far |
| Device | iPhone 17 simulator, iOS 27.0 |
| Content rect | **402x874 pt @3x** (the C390 compact bucket; the narrowest stock iPhone 17 is 402pt, so C390 is read as "compact", not as an exact width) |
| Locale | `en` |
| Themes | light + dark |
| Font | default (Dynamic Type not yet swept) |
| Cells | 20 |

`manifest.tsv` carries the per-capture metadata the gate requires
(platform, OS, viewport, scale, locale, theme, font, fixture revision, source
commit, screen, label, file).

## Honest limitations of this batch

These are **reference captures, not an approved raster baseline**, for three
reasons that must be fixed before the raster gate can run:

1. **The clock is live, not frozen.** Captured at ~02:00 Europe/Athens, so every
   schedule-bearing screen shows overnight state (the first plan departs 04:00,
   `0 live` vehicles, no active disruptions). A raster comparison against these
   would fail at any other hour. The gate needs the injectable Athens test clock
   pinned and the planner fed from `fixtures/journeys/`, not the live seed.
2. **Content comes from the live API and leftover local state.** The saved-journey
   list is whatever a previous session left on the simulator, and the
   announcement feed is live upstream data.
3. **Location permission is not granted**, which is itself why the Map cells are
   off-network (see finding 5 in `FINDINGS.md`).

## Not yet captured

- iOS: S07, the window expansions (C360 / M768 / E1024 / W1360 / Short), the
  el / sq / it locales, and the accessibility sweep (Dynamic Type XXXL).
- Android and web: nothing yet.

## Size note

20 cells at 3x is 16 MB. The full matrix across three platforms at this
resolution is on the order of 150 MB of binaries in git, so the raster job should
either downscale for storage or move baselines to LFS before the sweep is
completed.
