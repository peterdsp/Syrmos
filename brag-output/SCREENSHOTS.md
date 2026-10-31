# Which real capture is in which video

Every application interface in all six films is a capture of the shipping Syrmos app.
Nothing on a screen is rebuilt, redrawn or invented. The captures come from the
reproducible Phase Z baseline harness (`scripts/capture-baselines.sh`, clock pinned to
2026-09-16T08:42+03:00, offline seed, iPhone 17 simulator at 402x874pt @3x), from the
September 2026 product set, and from the Duo snapshot tests.

Working copies live in `brag-output/shots/` and are mirrored into each project's
`assets/shots/`. Wide captures were downscaled (never upscaled) to about twice their
largest on-screen size; the iOS captures are used at their native 1206 px width.

| Working copy | Repository source | Native |
| --- | --- | --- |
| `ios-home-light.png` | `docs/screenshots/release-3.0.0/ios/S01-home__C402__light__en__default.png` | 1206x2622 |
| `ios-home-dark.png` | `docs/screenshots/release-3.0.0/ios/S01-home__C402__dark__en__default.png` | 1206x2622 |
| `ios-map-light.png` | `docs/screenshots/release-3.0.0/ios/S09-map__C402__light__en__default.png` | 1206x2622 |
| `ios-map-dark.png` | `docs/screenshots/release-3.0.0/ios/S09-map__C402__dark__en__default.png` | 1206x2622 |
| `web-board-light.png` | `docs/screenshots/product-2026-09/web_desktop_now.png` | 2880x1800 |
| `web-board-dark.png` | `docs/screenshots/release-3.0.0/web/home-board-athens__W1440__dark__en.png` | 2880x1800 |
| `web-ariadne-fare.png` | `docs/screenshots/product-2026-09/web_ariadne_fare.png` | 760x772 |
| `duo-inner-portrait-dark.png` | `iosApp/iosAppTests/__DuoSnapshots__/go-duo-inner-portrait-dark.png` | 1338x1902 |
| `duo-map-landscape-dark.png` | `iosApp/iosAppTests/__DuoSnapshots__/map-duo-inner-landscape-dark.png` | 1902x1338 |

## Per video

### `brag.mp4` — 1920x1080, 23.6s
| Scene | Real capture | How it moves |
| --- | --- | --- |
| 1. Offline countdown | `ios-home-light` (S01 Home, light) | Opens tight on `1:00`, pulls back to reveal the real `Running offline - Predicted from schedule` chip. |
| 3. Living map | `web-board-light` (web desktop board) | Slow push toward the live-trains panel. |
| 4. Ariadne | `web-ariadne-fare` (the real fare exchange) | Uncovered from the top so the question lands before the answer and the `Offline snapshot` chip. |

Scenes 2, 5 and 6 carry no application interface (mark, stat line, end card).

### `brag-vertical-stories.mp4` — 1080x1920, 18.0s
| Scene | Real capture | How it moves |
| --- | --- | --- |
| 1. Offline countdown | `ios-home-light` | Tight on `1:00`, pulls back to the Now card. |
| 3. Living map | `ios-map-light` (S09 Map, light) | Push across the real Athens map toward the central interchange. |
| 4. Ariadne | `web-ariadne-fare` | Top-down reveal, question then answer. |

### `brag-vertical-neon.mp4` — 1080x1920, 11.5s
| Element | Real capture |
| --- | --- |
| Background plate | `ios-map-dark` (S09 Map, dark), treated with glow and a slow parallax drift |
| Handset screen | `ios-home-dark` (S01 Home, dark), sharp, pulling back across the screen |

### `brag-vertical-tunnel.mp4` — 1080x1920, 11.5s
| Element | Real capture |
| --- | --- |
| Handset screen | `ios-home-dark`, opening tight on `1:00` and pushing out to the offline chip |

The tunnel, pillars and passing headlights are set dressing, not interface.

### `brag-vertical-devices.mp4` — 1080x1920, 11.5s
| Device | Real capture |
| --- | --- |
| Laptop | `web-board-dark` |
| Handset | `ios-home-dark` |
| Folding handset (tent) | `duo-inner-portrait-dark`, one capture painted across both leaves so the hinge falls where it really does |

### `brag-landscape-devices.mp4` — 1920x1080, 13.2s
| Device | Real capture |
| --- | --- |
| Laptop | `web-board-dark` |
| Handset | `ios-home-dark` |
| Folding handset (opened flat) | `duo-map-landscape-dark` |

## No distortion

Each screen box was resized to its capture's own aspect ratio, so no screen is stretched:
1.600 for the web board, 0.460 for the iOS captures, 0.703 for the folding inner display
(split in half at the hinge for the tent pose) and 1.421 for the opened-flat inner display.
Crops are honest crops; perspective on the tented leaves is a camera angle, not a scale.

## Device frames

The laptop, handset and folding-handset frames around the captures are generic unbranded
silhouettes. They carry no manufacturer's marks and are not renderings of any real hardware.
