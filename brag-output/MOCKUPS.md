# Hardware: mockups, captures and how each dimension was verified

Every device in the six films is a supplied photoreal mockup with a real Syrmos
capture composited into its screen. Nothing is a drawn silhouette any more.

## 1. How the target geometry was established

Two independent sources, cross-checked against each other.

**Apple's simulator device profile on this machine**
`/Library/Developer/CoreSimulator/Profiles/DeviceTypes/iPhone Duo.simdevicetype/Contents/Resources/capabilities.plist`

| iPhone Duo (`iPhone19,4`, product class V68, min iOS 27.1) | |
| --- | --- |
| Cover display | 1398 x 2034 px @3x = **466 x 678 pt**, ratio **0.68732** |
| Cover corner radii | UL 8, UR 59, LL 8, LR 59 pt (sharp on the hinge edge) |
| Inner display | 2007 x 2853 px @3x = **669 x 951 pt**, `nativeOrientation 270` |
| Inner, as held unfolded | **951 x 669 pt**, ratio **1.42152** |
| Inner corner radii | 55 pt on all four |

**Apple's published specification** (body, mm): open 164.6 x 117.8 x 5.2,
closed 84.1 x 117.8 x 11.3, 5.4-inch cover, 7.6-inch inner.

**The hinge is vertical.** Half the open body (164.6 / 2 = 82.3 mm) matches the
closed width (84.1 mm, the difference being the spine), and the inner panel's
native orientation is landscape. The fold therefore runs down the middle of the
951 pt width, giving two leaves of **475.5 x 669 pt, ratio 0.71076**.

That is the defect in the previous cut: the tent pose used the *portrait* inner
capture split into 0.35-ratio slivers. Wrong capture, wrong axis, wrong proportions.

**iPhone 17**: display 1206 x 2622 px @3x = **402 x 874 pt**, ratio **0.45995**;
body 149.6 x 71.5 x 7.95 mm, so about 2.2 mm of bezel on all four sides.

## 2. The supplied mockups, measured

Each mockup's screen area was measured in pixels and compared with the table above.

| Mockup | Measured screen | Ratio | Target | Error | Verdict |
| --- | --- | --- | --- | --- | --- |
| iPhone 17 front (transparent cutout) | 1290 x 2796 | 0.46137 | 0.45995 | **0.31%** | used |
| MacBook front | 1092 x 706 | 1.54674 | 1.54700 (MBP 16) | **0.02%** | used |
| iPhone Duo tent | quad, perspective | - | - | see below | used |
| iPhone Duo flat pair (front-on) | cover 166 x 251 | 0.66135 | 0.68732 | **3.78%** | measured only |
| iPhone Duo flat pair (front-on) | inner 350 x 254 | 1.37795 | 1.42152 | **3.07%** | measured only |
| iPhone Duo unfolded (star white) | quad, perspective | - | - | - | scene could not be keyed |

**The MacBook was identified from its own geometry**, not from its filename: 1.54674
matches the MacBook Pro 16-inch panel (3456 x 2234 = 1.54700) to 0.02%, against
1.5385 / 1.5397 / 1.5451 for the Air 13, Pro 14 and Air 15.

### Known deviation, stated plainly

The supplied iPhone Duo mockups are third-party concept renders. Measured front-on,
their screens assume the unfolded width is exactly twice the cover width
(2 x 466 / 678 = 1.375, against the real 1.42152). That is a **3.0 to 3.8% deviation**
from the shipping hardware, and their fold geometry shows the full inner display on
a single plane, which a real Duo in tent posture cannot do. The captures are mapped
to the mockup's own screen plane with **zero crop and no stretch beyond the
perspective the mockup itself depicts**. If strict physical accuracy matters more
than the photoreal render, a frame built to the measured geometry can replace it.

## 3. Captures composited into those screens

| Capture | Source | Pixels | Ratio | Error vs spec |
| --- | --- | --- | --- | --- |
| `ios-home-dark.png` | `release-3.0.0/ios/S01-home__C402__dark__en__default.png` | 1206 x 2622 | 0.45995 | 0.000% |
| `ios-home-light.png` | `release-3.0.0/ios/S01-home__C402__light__en__default.png` | 1206 x 2622 | 0.45995 | 0.000% |
| `ios-map-light.png` | `release-3.0.0/ios/S09-map__C402__light__en__default.png` | 1206 x 2622 | 0.45995 | 0.000% |
| `ios-map-dark.png` | `release-3.0.0/ios/S09-map__C402__dark__en__default.png` | 1206 x 2622 | 0.45995 | 0.000% |
| `duo-inner-unfolded-dark.png` | `__DuoSnapshots__/map-duo-inner-landscape-dark.png` | 1902 x 1338 | 1.42152 | 0.000% |
| `duo-leaf-left/right-dark.png` | same capture, split at the vertical hinge | 951 x 1338 | 0.71076 | 0.000% |
| `duo-cover-folded.png` | `__DuoSnapshots__/departures-duo-cover.png` | 932 x 1356 | 0.68732 | 0.000% |
| `web-board-mbp16-dark.png` | **new live capture**, see below | 3456 x 2234 | 1.54700 | 0.000% |
| `web-ariadne-fare.png` | `product-2026-09/web_ariadne_fare.png` | 760 x 772 | - | - |

### New captures made for this pass

- **`web-board-mbp16-dark.png`** - the deployed web app at `syrmos.peterdsp.dev`,
  driven over the Chrome DevTools Protocol at a 1728 x 1117 viewport, deviceScaleFactor 2,
  giving **3456 x 2234**: the MacBook Pro 16-inch panel exactly, so it drops into that
  mockup at **0.0% crop**. Dark theme and the What's New sheet were set through the app's
  own `syrmos-theme` / `syrmos.whatsnew.*` keys. Live data (70 trains active).
- **`duo-cover-live-sim.png`** - Syrmos running on a **booted iPhone Duo simulator**
  (`F78A27CA`, iOS 27.1), dark, status bar pinned to 09:41, captured off the cover
  framebuffer with `simctl io ... screenshot --display`. Retained as evidence; the films
  use the clean snapshot render because the location permission dialog cannot be
  dismissed without the Simulator GUI.

## 4. Compositing method

`brag-output/mockups/compose.py`.

- **hole** - the iPhone 17 frame has a transparent screen cutout, so the capture sits
  behind the glass and can still be animated independently. Used in all four
  phone-led films.
- **quad** - a four-point homography maps the whole capture onto the mockup's screen
  plane. The quad already encodes the foreshortening, so the capture is mapped at full
  extent: **zero crop, no squash beyond the depicted perspective**. The mask is built in
  source space as a rounded rectangle at the display's real **55 pt** radius and warped
  by the same homography, so the corners project correctly. The mockup's own specular
  glare is carried back over the new screen as a low-frequency lift (35 px minus 160 px
  blur), which keeps the glass reading as glass without dragging the mockup's artwork through.
- **knockout** - the studio backdrop is flood-filled from the image border, so same-coloured
  pixels inside the device survive. Contact shadows are cleared explicitly; the films cast
  their own.

Unfolded Duo: the left and right halves of the inner capture are mapped to the two
planes of the mockup's crease separately, with only the outer corners rounded, so the
map runs continuously across the fold exactly as it does on the hardware.

## 5. Which device is in which film

| Film | Devices |
| --- | --- |
| `brag.mp4` | iPhone 17 (hook, light Home), then the real web board and the Ariadne sheet as flat captures |
| `brag-vertical-stories.mp4` | iPhone 17 for the countdown and the map |
| `brag-vertical-neon.mp4` | iPhone 17 over the real dark Map tab as the background plate |
| `brag-vertical-tunnel.mp4` | iPhone 17, dark Home, pushed out from the countdown |
| `brag-vertical-devices.mp4` | MacBook Pro 16, iPhone 17, iPhone Duo (tent) |
| `brag-landscape-devices.mp4` | MacBook Pro 16, iPhone 17, iPhone Duo (tent) |

## 6. Limits on this machine

- **The Duo's inner display cannot be captured live.** `CoreSimulator` exposes no posture
  or fold API (a `strings` scan finds only `folderName` substrings), `simctl ui` has no
  such option, and `Simulator.app` is absent from both Xcode installs here - both
  `Contents/Developer/Applications/` folders are empty and `mdfind` finds no copy. The
  inner framebuffer reads pure black (luma 0.0) while folded. Unfolded screens therefore
  come from `DuoSnapshotTests`, which renders real SwiftUI views at exactly
  951 x 669 and 669 x 951 pt.
- **No application code was modified.** The simulator was driven only with `simctl`
  (launch, privacy, location, status_bar, io screenshot) against the already-installed build.
