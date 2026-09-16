# Phase Z visual baselines — 3.0.0

Captures for the S01-S10 matrix defined in
[`docs/ops/RELEASE-3.0.0-GATE.md`](../../ops/RELEASE-3.0.0-GATE.md).

## What is here

| | |
|---|---|
| Platform | iOS only so far |
| Device | iPhone 17 simulator, iOS 27.0 |
| Content rect | **402x874 pt @3x** (the C390 compact bucket; the narrowest stock iPhone 17 is 402pt, so C390 is read as "compact", not as an exact width) |
| Clock | pinned to **2026-09-16T08:42:00+03:00**, a weekday mid-morning inside Athens service hours |
| Location | Syntagma, pre-granted |
| Data | offline: the bundled seed, no live API |
| Locales | `en` (light + dark), `el` (light) |
| Font | default |
| Cells | **54** (18 screens x 2 themes in `en`, plus 18 in `el`) |

Screens covered: S01 · S02 (draft, filled) · S03 (list, keyboard open) · S04 ·
S05 · S06 (active, get-off-next) · S07 (risk, alternatives) · S08 · S09
(Explore, Map, Airport, More, Ariadne) · S10 (offline).

Every cell was captured through `scripts/capture-baselines.sh`, so all 54 are
reproducible. The harness sets the app's own `app_language` from the same
variable that names the file, so a cell cannot be labelled `__el__` while the app
is still running in English. `manifest.tsv` carries the per-capture metadata the gate requires,
including the pinned clock and the offline flag as the fixture revision.

## The harness

`scripts/capture-baselines.sh` is what makes a capture reproducible. It pins
everything that otherwise moves under the app between two runs:

| pinned | how |
|---|---|
| clock | `SYRMOS_CAPTURE_NOW` read once by `SyrmosClock`, and **verified**: the app writes a receipt into its container and the harness refuses to continue without it, so a launch where the environment never arrived fails loudly instead of quietly producing wall-clock pixels |
| data | `SYRMOS_CAPTURE_OFFLINE=1` installs a `URLProtocol` that fails every request, so screens render from the bundled seed rather than whatever the API is serving this minute |
| animation | looping pulses are held still; a countdown that breathes guarantees two captures of the same state differ |
| state | the app is uninstalled and reinstalled, and onboarding plus the What's New sheet are seeded as already seen |
| permissions | location pre-granted and set to a fixed point; stale notification prompts reset (the app skips the request under a pinned clock) |
| chrome | status bar frozen at 09:41, full bars, charged |
| timing | a settle period before the first shot, because data load and entrance transitions finish over the first few seconds |

```bash
scripts/capture-baselines.sh setup --app path/to/Syrmos.app
scripts/capture-baselines.sh shot S01-home "Home / Now"
scripts/capture-baselines.sh theme dark
scripts/capture-baselines.sh teardown
```

**Verified, not assumed:** two full uninstall → install → launch → settle → capture
cycles produce byte-for-byte identical PNGs (same sha256). That is the property
the raster gate needs.

What this deliberately does not cover: the **online** variants of every screen.
Offline is a state we control; the live API is not. Baselining the online
variants needs recorded response fixtures, which do not exist yet.

## Honest limitations of this batch

These are reproducible, which is what the raster gate needs, but they are not yet
**approved** baselines: nobody has inspected all 36 and signed them off. That is
the next step, and `FINDINGS.md` is what came out of inspecting them so far.

The captured state is deliberately the **offline** one. It is a state we control;
the live API is not. The online variant of each screen needs recorded response
fixtures before it can be diffed.

## Not yet captured

- The window expansions: C360, M768, E1024, W1360 and Short.
- The `sq` and `it` locales, and `el` in dark.
- The accessibility sweep: Dynamic Type XXXL, and web zoom / Android font scale
  on those platforms.
- Android and web: nothing yet.

## Size note

54 cells at 3x is 34 MB. The full matrix across three platforms at this
resolution is on the order of 150 MB of binaries in git, so the raster job should
either downscale for storage or move baselines to LFS before the sweep is
completed.
