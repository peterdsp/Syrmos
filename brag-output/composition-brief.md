# Hyperframes Composition Brief: Syrmos

## Objective
Create a short launch-style brag video for Syrmos, the offline-first rail companion for Greece.

## Output
- Composition directory: `brag-output/composition/`
- Rendered video: `brag-output/brag.mp4`
- Format: landscape — 1920x1080
- Duration: 20.5 seconds

## Source Material
- Project root: `/Users/peterdsp/git/Syrmos`
- Primary files read:
  - `README.md` (coverage, highlights, architecture, privacy)
  - `DESIGN_SYSTEM.md` ("Calm Signal" language, identity rules)
  - `composeApp/src/wasmJsMain/resources/design-tokens.css` (canonical generated colour/spacing/motion/type tokens)
  - `composeApp/src/wasmJsMain/resources/product/index.html` (product page copy)
  - `docs/screenshots/product-2026-09/ios_home.png`, `web_desktop_now.png`, `web_ariadne_fare.png` (real shipping UI)
- Product name: Syrmos
- Tagline / strongest claim: "Your next connection. Beautifully clear." plus the offline-first promise: the app launches with correct data on airplane mode.
- Key UI or visual moment to recreate: the Now card with an offline chip and a live countdown; the Athens living map with per-line operator colours and live vehicle pills; the Ariadne fare answer carrying an "Offline snapshot" chip.
- Copy that must appear verbatim (all of it exists in the shipping app or product page):
  - `Line 2` / `Elliniko` / `4'` / `then 9 min, 14 min` (web Now card)
  - `Your next connection. Beautifully clear.` (product page h1)
  - `71 trains active` (web live-trains panel)
  - `How much from Syntagma to Airport?` (Ariadne screenshot)
  - `Syntagma → Airport: €9.00 (Reduced €4.50). Airport Metro ticket (Line 3) · OASA.` (Ariadne screenshot)
  - `Offline snapshot` (Ariadne source chip)
  - `32 lines` / `4 regions` (README coverage: 32 lines, four regions)
  - `4 languages` (product page "Four languages"; EN/EL/SQ/IT language switcher in the web UI)
  - `0 trackers` (README privacy: no data collection, no analytics, no tracking)
  - `Free on iPhone, Android and web.` (product page: "Syrmos is free on every platform", choices named iPhone / Android / Web)
  - `syrmos.peterdsp.dev`

## Creative Direction
- Tone preset: `app-store`
- Creative direction: a calm Apple-style product film for the transit app that still knows the time underground.
- Interpretation: clean feature-card reveals, generous holds, one idea per scene, motion that glides rather than snaps (the project's own `cubic-bezier(0.16, 1, 0.30, 1)` easing). Confidence through restraint: no zooms, no flashes, no stacked copy, no more than one claim on screen at a time.
- Angle: Every transit app dies exactly where you need it, on the platform, 40 metres down, with one bar of nothing. Syrmos does not. The whole film is built on that one honest, verifiable claim, told the way the product tells it: calm, precise, Greek, not touristy. We show the offline chip, the countdown that keeps ticking, 71 trains moving on a real map, and an assistant that answers a fare question from a bundled snapshot. The brag is restraint: this thing is quietly very finished.
- Hook: an airplane glyph and `No signal`, and the countdown still running on a huge `4'` for Line 2 to Elliniko.
- Outro / punchline: `0 trackers` left hanging, then the logo and `Free on iPhone, Android and web.`
- Avoid:
  - Generic SaaS language
  - Abstract filler visuals
  - Unrelated visual redesign (do not redraw or re-style the Syrmos identity; Calm Signal forbids approximating the logo with substitute typography, so use a simple route-motif mark plus the wordmark rather than inventing a logo)
  - Line colours used decoratively; in this product they are data only

## Visual Identity
From `design-tokens.css` (generated from `core/designsystem` Kotlin token source):
- Background: `#F7F5F1` (warm station white); muted surface `#EFEBE4`; cards `#FFFFFF`
- Text: `#14181F`; muted `#5B636E`; outline `#E0DACF`
- Accent: `#1466B8` (Aegean blue brand signal); strong `#0F4E8C`
- Service colours (data only): M1 `#00843D`, M2 `#DA291C`, M3 `#0072CE`, tram `#F39800`, suburban `#6F2DA8`, national `#2A5C8A`
- State colours: live `#059669`, scheduled `#2563EB`, offline `#6B7280`, estimated `#B45309`
- Display font: SF Pro via `-apple-system` / `system-ui` (the real iOS type family; the design system specifies the system UI stack on web)
- Body font: same system UI stack
- Geometry and motion from the same tokens: radius 12/16/24px, pill chips at 999px, 8pt spacing grid, `--sy-ease-glide: cubic-bezier(0.16, 1.0, 0.30, 1.0)`, medium duration 300ms
- Visual references from the project:
  - The Now card: context tag, line pill, large countdown, "then …" secondary times
  - The Living map strip: a blue line with open station nodes and one filled node (this is also the logo's linked-route motif)
  - The live-trains panel: rounded vehicle pills on coloured track, `71 trains active`, live dot
  - The Ariadne sheet: blue right-aligned user bubble, pale blue left assistant bubble, inline grey source chip

## Storyboard
Use the storyboard in `brag-output/brag-plan.md` as the creative contract.

Scene summary:
1. **No signal, still counting** — 3.0s (0.0-3.0) — Now card: `ATHENS`, offline chip `No signal`, `Line 2` pill, `Elliniko`, countdown ticking 6' → 5' → 4' and settling, `then 9 min, 14 min`.
2. **The name** — 3.5s (3.0-6.5) — route motif draws with station nodes, `Syrmos` wordmark, then `Your next connection. Beautifully clear.` held ~1.6s.
3. **The living map** — 4.5s (6.5-11.0) — Athens network draws line by line in operator colours, then ~12 vehicle pills land in sequence; counter climbs to `71` with label `trains active` and a live dot; caption `Live where a feed exists. Estimated where it doesn't.`
4. **Ask Ariadne** — 4.0s (11.0-15.0) — user bubble types `How much from Syntagma to Airport?`, assistant bubble arrives with the fare answer and the `Offline snapshot` chip last.
5. **What it actually covers** — 3.0s (15.0-18.0) — four short stats arrive in order: `32 lines`, `4 regions`, `4 languages`, `0 trackers`; all four hold together for the final ~1.0s, `0 trackers` in accent blue.
6. **Free, everywhere** — 2.5s (18.0-20.5) — logo mark and wordmark settle, `Free on iPhone, Android and web.`, muted `syrmos.peterdsp.dev` at the base.

## Audio
- Audio role: warm, restrained bed with sparse motion-matched accents. A product film, never an ad.
- Audio arc: fades in under the offline countdown, opens at the name, lifts for the network coming alive, pulls close and quiet for the Ariadne exchange, flattens for the stat line, fades to near-silence under the final frame.
- Music: `happy-beats-business-moves-vol-12-by-ende-dot-app.mp3` (110 BPM, "steady and clean"), played from track time 0.0 so the bundled cue grid aligns with the storyboard.
- Music treatment: fade in over the first ~0.6s; bed volume ~0.30; a small lift at the map scene; fade out across the last ~1.2s so the closing line lands almost dry.
- Music cue guidance: bundled preset at
  `~/.claude/skills/brag/assets/music/cues/happy-beats-business-moves-vol-12-by-ende-dot-app.music-cues.json`
  (markdown summary beside it). Tempo 109.96 BPM, beat grid ~0.55s apart.
  Strong cues to target: **8.74s** (network bloom / vehicle pills begin), **13.11s** (Ariadne's answer bubble lands), **17.47s** (final stat `0 trackers`). Use these three locks and no more.
  Sequential text (the four stats) must snap to **every other beat** or be revealed quickly and held as a set; never one line per beat at this tempo.
- Audio-reactive treatment: subtle. Use RMS/bass to breathe the Now card's elevation and the live-vehicle glow on the map only. No waveform bars, no equalizer, no pulsing type, nothing that touches text legibility.
- Audio-coupled moments:
  - Scene 1 countdown 6' → 5' → 4' — one quiet UI tick per step, softer settle on `4'`
  - Scene 2 station nodes landing along the drawn route — barely-there node ticks under the music
  - Scene 3 vehicle pills arriving in sequence — light overlapping interface ticks; the counter's final `71` on the 8.74s strong cue
  - Scene 4 question typing character by character — randomized `keyboard/keypress-*.wav`; soft message pop on the answer bubble at 13.11s
  - Scene 5 four stats — one dry tick each, the last slightly fuller at 17.47s
  - Scene 6 logo settle — one soft cue only, no impact hit
- SFX selection guidance: `app-store` energy, consistent light layer, volumes 0.55-0.75 (lower than the usual ceiling, this film is calm). Favour `interface/drop_*`, `interface/click_*`, `interface/select_008`, `ui/rollover*`, light `impact/impactGeneric_light_*` or `impact/impactSoft_medium_*`. Keep the whole film to roughly five to eight cues total; if a cue is not matched to something physically moving on screen, drop it.
- SFX analysis guidance: read `~/.claude/skills/brag/assets/sfx/sfx-analysis.md` (and the JSON beside it) before choosing files. Prefer low high-frequency-risk files throughout, since every cue here is repeated or polished.
- Exact SFX choice: Hyperframes chooses filenames, timestamps, density, and volume after the animation exists.
- Audio files: copy the chosen music and any selected SFX into `brag-output/composition/assets/` (music under `assets/music/`, SFX under `assets/sfx/<family>/`). Relative paths only, never absolute.

## Hyperframes Instructions
Load the composition-building Hyperframes domain skills — `hyperframes-core`, `hyperframes-animation`, `hyperframes-creative`, `hyperframes-keyframes`, `hyperframes-cli`. /brag is its own workflow: do not enter the `hyperframes` entry-point intent interview and do not route into its generic promo / launch-video workflow. Prefer native Hyperframes conventions over anything in `/brag`.

Requirements:
- Show at least one real UI, copy, or visual element from Syrmos. (Scenes 1, 3 and 4 are all recreations of shipping UI; scene 4 is verbatim.)
- Keep all text readable: short labels hold ~0.8s settled, full sentences ~0.3s per word with a ~1.2s floor. The hook and the Ariadne answer get the longest holds.
- Keep the video within 15-25 seconds (target 20.5s).
- Include the planned music and SFX layer.
- Treat `/brag` audio notes as guidance, not a fixed cue sheet; choose SFX after the visual animation exists.
- Treat music cue metadata as optional timing hints; ignore any cue that hurts readability or pacing.
- Lock only the three named strong cues (±0.15s) and snap small sequential entrances to nearby beats (±0.10s). Mark them `// beat-locked:` and `// beat-grid:`.
- Honour the music fade-in, mid lift, and final fade-out.
- Wire at least one visual element to extracted per-frame audio data per the `hyperframes-creative` audio-reactive guidance; if extraction is unavailable, document that and continue.
- Use local assets only; no remote runtimes, no absolute paths.
- Run `npx hyperframes check` before render — it is brag's single gate.
- Keep creation and rendering local. No publish, no cloud render.

## Grounding note
Every factual claim on screen is traceable to a file in this repo (see the verbatim copy list above). No invented metrics, no fake testimonials, no personal or user data: the Ariadne exchange is the one from the public product screenshot, and the station names and fares are the operator's published values already shipping in the app.
