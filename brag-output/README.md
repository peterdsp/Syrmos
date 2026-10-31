# Syrmos launch films

Six videos built with HyperFrames (HTML rendered to video, deterministic frame-by-frame).
All of them are in `~/Downloads` too.

| File | Format | Length | What it is |
| --- | --- | --- | --- |
| `brag.mp4` | 1920x1080 | 23.6s | The main launch film: offline countdown, the name, the living map, Ariadne, the coverage claim, the end card. |
| `brag-vertical-stories.mp4` | 1080x1920 | 18.1s | The same story recomposed for Instagram Stories / Reels, faster, one idea per screen. |
| `brag-vertical-neon.mp4` | 1080x1920 | 11.5s | Dark-mode neon network: a handset over a glowing Athens/Thessaloniki map, light running the tracks, two trains passing. |
| `brag-vertical-tunnel.mp4` | 1080x1920 | 11.5s | Underground platform: macro push-in on the offline countdown ticking 3' to 2', concrete pillars, passing headlights. |
| `brag-vertical-devices.mp4` | 1080x1920 | 11.5s | Studio desk, camera boom up: laptop, upright handset and a folding handset in tent position, all three screens synced. |
| `brag-landscape-devices.mp4` | 1920x1080 | 13.2s | Dark bench, slow slider pan: laptop centre, handset and an unfolded folding handset to its right. |

Each `.mp4` has a `.jpg` poster beside it, and that poster is baked as frame 0 so every
player and platform picks it as the idle thumbnail.

Every application interface in all six films is a real capture of the shipping app.
`SCREENSHOTS.md` lists which capture is in which video and where each one came from.
`MOCKUPS.md` covers the device hardware: which mockup, measured against which Apple
specification, and how each capture was fitted without stretching or cropping.

## Source projects

`composition/` (landscape), `vertical-stories/`, `vertical-neon/`, `vertical-tunnel/`,
`device-vertical/`, `device-landscape/`. Each is a standalone HyperFrames project:

```bash
cd <project> && npx hyperframes preview     # edit in the browser
cd <project> && npx hyperframes check       # lint + runtime + layout + contrast
cd <project> && npx hyperframes render --output ../<name>.mp4
```

The four dark films declare `color-ground`, `color-ink`, `color-accent` and `font-display`
as composition variables, so the palette can be changed from the Variables panel.

## Grounding

Every factual claim and every string of UI copy on screen is traceable to this repo:
the shipping screen captures themselves (see `SCREENSHOTS.md`),
`README.md` (coverage, privacy, the offline line), the product page
(`composeApp/src/wasmJsMain/resources/product/index.html`), the shipping screenshots in
`docs/screenshots/product-2026-09/`, and the generated tokens in
`composeApp/src/wasmJsMain/resources/design-tokens.css`. No invented metrics, no personal data.

## Devices

Supplied photoreal mockups: iPhone 17, MacBook Pro 16-inch (identified from its own panel
ratio to 0.02%) and iPhone Duo. Real Syrmos captures are composited into their screens with
zero crop. Measurements, deviations and method are in `MOCKUPS.md`.

## Audio

One bed (`happy-beats-business-moves-vol-12` by ende.app, 110 BPM) and a small set of CC0
Kenney SFX, every cue matched to something moving on screen. All six files are mastered to
roughly -15 LUFS integrated with true peak at or under -1.1 dBTP. Measured with ffmpeg, not heard.

## Fonts

Inter stands in for SF Pro: only HyperFrames' bundled families resolve by name in the renderer.
