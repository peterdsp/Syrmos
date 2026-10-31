# Brag Plan: Syrmos

## What is this app?
Syrmos is an offline-first rail companion for Greece (Athens, Thessaloniki, Patras, and the InterCity corridor) that gives you a live countdown to your next train, a living map of every vehicle, and a transit assistant that answers in plain language, all of it still working when you are underground with no signal.

## The angle
Every transit app dies exactly where you need it: on the platform, 40 metres down, one bar of nothing. Syrmos does not. The whole film is built on that one honest, verifiable claim, told the way the product tells it: calm, precise, Greek, not touristy. No startup swagger, no "streamline your commute". We show the offline chip, the countdown that keeps ticking, 71 trains moving on a real map, and an assistant that answers a fare question from a bundled snapshot. The brag is restraint: this thing is quietly very finished.

## Hook (first 2-3 seconds)
Airplane-mode glyph and `NO SIGNAL` in the status strip. The screen is a warm station-white card. And the countdown is still running: a huge `4'` with `Line 2 · Elliniko` above it and `then 9 min, 14 min` below. The joke is that there is no joke, it just works. One word of text, one enormous number, no explanation.

## Key moments (the middle)
- The wordmark landing on the product's own line: **Your next connection. Beautifully clear.**
- The Athens network drawing itself in on the map, line by line in operator colours (red M2, blue M3, green M1, orange tram, purple suburban), then live vehicle pills popping on along the drawn track while a counter climbs to **71 trains active**.
- Ariadne, verbatim from the shipping app: the user asks *How much from Syntagma to Airport?*, and the answer arrives with the **Offline snapshot** chip attached. Same beat as the hook, but now the app is answering a question, not just showing a number.
- The coverage row as a quiet stat line: **32 lines · 4 regions · 4 languages · 0 trackers**.

## Outro / punchline
Everything clears to the warm surface. The logo settles, then one line: **Free on iPhone, Android and web.** The punchline is the last stat before it, `0 trackers`, left hanging just long enough to register that it was a real claim and not a slogan.

## User flow worth showing
Three beats of the real app, in order:
1. **Entry:** you open Syrmos on the platform with no connection; the Now card already shows Line 2 → Elliniko in 4 minutes with an offline chip.
2. **Key action:** you open the living map and watch where every train actually is, 71 of them, live feed where one exists and labelled Estimated where it does not.
3. **Result:** you ask Ariadne a plain-language question and get a real answer, sourced from the bundled snapshot, still with no signal.

All UI copy in the film is lifted from the shipping app and the product page (`docs/screenshots/product-2026-09/`, `composeApp/src/wasmJsMain/resources/product/index.html`). No invented data, no real user data, nothing personal on screen.

## Tone
- Preset: `app-store`
- Creative direction: a calm Apple-style product film for the transit app that still knows the time underground.
- Interpretation: clean feature-card reveals, generous holds, one idea per scene, motion that glides rather than snaps. Confidence comes from how little the film tries. No zooms, no flashes, no stacked copy.

## Format: landscape — 1920x1080
## Duration: 20.5 seconds

## Visual identity (from the project)
Pulled from `composeApp/src/wasmJsMain/resources/design-tokens.css` (generated from `core/designsystem` tokens):
- Background: `#F7F5F1` (warm station white), muted surface `#EFEBE4`, cards `#FFFFFF`
- Accent: `#1466B8` (Aegean blue brand signal), strong `#0F4E8C`
- Text: `#14181F`, muted `#5B636E`, outline `#E0DACF`
- Service colours (data only, never decoration): M1 green `#00843D`, M2 red `#DA291C`, M3 blue `#0072CE`, tram `#F39800`, suburban `#6F2DA8`, national `#2A5C8A`
- State colours: live `#059669`, scheduled `#2563EB`, offline `#6B7280`, estimated `#B45309`
- Display font: SF Pro / `-apple-system` (the iOS surface's real type family)
- Body font: same system UI stack, per the design system's web row
- Radii and motion from the same tokens: 16-24px card radius, pill chips, `cubic-bezier(0.16, 1, 0.30, 1)` glide easing
- Strongest visual element: the linked-route motif, a blue line with open station nodes and one filled node, which is both the logo's idea and the Living map strip on Home

## Share copy (draft)
Built a rail app for Greece that still tells you when your train is coming when you're 40 metres underground with no signal. 32 lines, 4 regions, 0 trackers.

## Audio direction
- Role: warm, restrained bed with sparse motion-matched accents. The audio should feel like a product film, never like an ad.
- Music: `happy-beats-business-moves-vol-12-by-ende-dot-app.mp3` (110 BPM, calmest of the bundled set), from track time 0.0 so the preset cue grid lines up with the storyboard.
- Music treatment: fade in over the first 0.6s under the hook, sit low (roughly -18 to -20 LUFS under the visuals), lift slightly at the map scene, fade out across the last 1.2s so the final line lands in near-silence.
- Music cue guidance: preset read from `assets/music/cues/happy-beats-business-moves-vol-12-...music-cues.md`. Target strong cues at **8.74s** (network bloom / vehicle pills), **13.11s** (Ariadne's answer lands), **17.47s** (last stat in the coverage row). Beat grid is ~0.55s; sequential text reveals snap to **every other beat (~1.1s)**, never every beat.
- Audio-reactive treatment: subtle. Music energy may breathe the map's live-vehicle glow and the Now card's elevation only. No waveform bars, no pulsing type.
- SFX posture: sparse. At most five cues in the whole film: the countdown settling, the vehicle pills arriving, the typed question, the answer bubble, and one soft settle under the logo.
- Audio-coupled moments: the `4'` counting down and settling; vehicle pills arriving one by one along the drawn lines; the Ariadne question typing character by character; the answer bubble appearing; the four stats arriving on alternate beats.
- Restraint rule: no risers, no whooshes on every transition, no impact hit on the logo. If a cue is not matched to something physically moving on screen, it does not exist.

## Storyboard

### Scene 1 — No signal, still counting — 3.0s (0.0-3.0)
Warm `#F7F5F1` field. A single Now card centres on screen. Top row: the context tag `ATHENS`, and on the right an offline chip in `#6B7280` reading `No signal` with an airplane glyph. Inside the card: the M2 pill (`Line 2`, `#DA291C`) next to `Elliniko`, then the countdown in display-now weight: `4'`, with `then 9 min, 14 min` in muted grey beneath. The `4'` arrives from `6'` ticking down and settles on `4'`. Nothing else moves.
Sequential/interaction: yes — the countdown ticks 6' → 5' → 4' and settles, then the offline chip fades in after it.
Audio intent: calm arrival, a product being confident rather than loud.
Audio-coupled idea: one soft UI tick per countdown step, then a quieter settle on `4'`.
Music: low bed fading in from silence.
Transition mood: soft → Scene 2

### Scene 2 — The name — 3.5s (3.0-6.5)
Card clears upward. The Syrmos logo mark settles centre, and the linked-route motif draws under it as a blue `#1466B8` line with five open station nodes and one filled. Wordmark `Syrmos`, then the product's own line beneath: `Your next connection. Beautifully clear.` Full sentence, holds ~1.6s settled.
Sequential/interaction: yes — route line draws left to right, nodes pop in along it, then the line of copy fades up as one piece.
Audio intent: the bed opens up slightly; this is the only "reveal" moment in the film.
Audio-coupled idea: a very soft node tick as each station node lands, under the music, barely there.
Transition mood: clean → Scene 3

### Scene 3 — The living map — 4.5s (6.5-11.0)
A stylised Athens network on the muted surface. Lines draw in their operator colours: M1 green, M2 red, M3 blue, tram orange, suburban purple. Once the lines are drawn, rounded vehicle pills pop onto the tracks in sequence. A counter in the corner climbs from `0` to `71`, labelled `trains active`, with a live dot in `#059669`. Small muted caption: `Live where a feed exists. Estimated where it doesn't.`
Sequential/interaction: yes — lines draw one after another (~0.35s each), then roughly a dozen vehicle pills land along them, with the network bloom hitting the 8.74s strong cue.
Audio intent: the busiest moment in the film, but still restrained; a network coming alive.
Audio-coupled idea: a light, soft interface tick per vehicle pill (quiet, overlapping, never machine-gun), the counter's final number landing on the strong cue.
Transition mood: smooth wipe → Scene 4

### Scene 4 — Ask Ariadne — 4.0s (11.0-15.0)
An Ariadne sheet on white, taken verbatim from the shipping app. The user bubble types out in accent blue: `How much from Syntagma to Airport?` Then the assistant bubble arrives on the left in `#D9E8F7`: `Syntagma → Airport: €9.00 (Reduced €4.50). Airport Metro ticket (Line 3) · OASA.` with the grey `Offline snapshot` chip attached to it. The answer holds ~1.6s settled.
Sequential/interaction: yes — question types character by character (~0.9s), brief pause, then the answer bubble rises and the chip appears last, landing on the 13.11s strong cue.
Audio intent: intimate, close, a conversation rather than a feature.
Audio-coupled idea: subtle key ticks under the typing, a soft message pop on the answer bubble.
Transition mood: soft → Scene 5

### Scene 5 — What it actually covers — 3.0s (15.0-18.0)
Back to the warm field. Four stats arrive on alternate beats (~1.1s apart is too slow for four in 3s, so: first three at ~0.7s apart as short labels, the fourth held): `32 lines`, `4 regions`, `4 languages`, `0 trackers`. Each is a short label, so each needs ~0.8s settled; `0 trackers` holds last and longest in accent blue while the other three sit muted.
Sequential/interaction: yes — four short stat labels arrive in order, all four remain on screen together for the final ~1.0s so the set reads as a whole.
Audio intent: matter-of-fact. These are claims, not boasts.
Audio-coupled idea: one dry, quiet tick per stat; the last one slightly fuller, on the 17.47s strong cue.
Transition mood: clean → Scene 6

### Scene 6 — Free, everywhere — 2.5s (18.0-20.5)
Everything clears. Logo mark and `Syrmos` wordmark settle centre on `#F7F5F1`. One line under it: `Free on iPhone, Android and web.` Then, small and muted at the base: `syrmos.peterdsp.dev`. Holds to black-free end; the frame simply rests.
Sequential/interaction: none — one settle, one line, one hold.
Audio intent: music fades out across the last 1.2s so the final frame is almost silent.
Audio-coupled idea: one soft settle under the logo. No impact hit.
Transition mood: rest / end

**Music mood for this video:** calm upbeat, held low
**Audio summary:** A warm 110 BPM bed fades in under an offline countdown, opens at the name, lifts for the network coming alive, pulls close for the Ariadne exchange, flattens for the stat line, and fades to near-silence under the final frame, with every SFX cue tied to something physically moving on screen.

---

## As shipped (final timings)

The storyboard above is the creative contract. Three things moved during composition,
all to satisfy reading floors and to keep the stage from going empty at a seam:

| Scene | Planned | Shipped |
| --- | --- | --- |
| 1 No signal, still counting | 0.0-3.0 | 0.0-3.0 (countdown starts mid-tick at frame 0 so the thumbnail is never a bare card) |
| 2 The name | 3.0-6.5 | 3.0-6.5 |
| 3 The living map | 6.5-11.0 | 6.5-11.0 |
| 4 Ask Ariadne | 11.0-15.0 | 11.0-15.84 (the fare line needs 2.4s settled) |
| 5 What it actually covers | 15.0-18.0 | 15.84-20.19 (three facts, a full bar of nothing, then the claim) |
| 6 Free, everywhere | 18.0-20.5 | 20.19-23.6 (the closing line needs 2s+) |

**Final duration: 23.6s.**

Beat locks used (3): vehicle pills at **8.74s**, the Ariadne answer at **13.11s**,
`0 trackers` at **18.56s** (moved from the planned 17.47s when scene 5 shifted; both
are strongCues at 0.99). The seam into the end card is a hard cut on the beat at 20.19s.

Audio-reactive: implemented as per-frame music RMS extracted with ffmpeg and inlined
into scenes 1 and 3, driving the Now card's shadow depth and the live-vehicle glow only.
The current Hyperframes skill set has no `hyperframes-creative` extraction helper under
that name, so the extraction was done directly and kept deterministic (no fetch, no clock).

Mastering: the render came out at -25.5 LUFS, so the delivered file is loudness-normalised
to **-14.7 LUFS integrated, -1.1 dBTP**, inside the -16 to -14 social target. Measured, not heard.
