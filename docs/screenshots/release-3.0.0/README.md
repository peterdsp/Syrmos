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

Most cells here were captured **before the harness existed** and are reference
captures, not approved baselines: the clock was live (~02:00 Europe/Athens, so
schedule-bearing screens show overnight state), content came from the live API,
and location was not granted. They are kept as evidence for `FINDINGS.md`.

`S01-home` has been recaptured through the harness and is deterministic. The
remaining screens need the same treatment before they can be diffed.

## Not yet captured

- iOS: S07, the window expansions (C360 / M768 / E1024 / W1360 / Short), the
  el / sq / it locales, and the accessibility sweep (Dynamic Type XXXL).
- Android and web: nothing yet.

## Size note

20 cells at 3x is 16 MB. The full matrix across three platforms at this
resolution is on the order of 150 MB of binaries in git, so the raster job should
either downscale for storage or move baselines to LFS before the sweep is
completed.
