# Web living-map overhaul — MapLibre GL + Motion One

Status: superseded (2026-07-19) by section 17 of
`2026-07-08-hellenic-rail-atlas-2.0.0-design.md`, which folds in this doc's
living-map and motion ideas. Kept for history. Owner: web.

## Goal

Make syrmos.peterdsp.dev feel premium and alive so web visitors want the app.
Two north stars, phased:

1. **Living map** (flagship): trains glide along the lines in real time, stations
   breathe, lines glow. This is the unique, hard-to-fake wow.
2. **Premium UI polish**: spring sheet transitions, staggered reveals, micro-interactions.
3. (Later) **Download-conversion moment**: a hero + store CTAs, reusing the
   what's-new card shipped in 1.1.1.

## Stack decision (from brainstorm: "go all-in")

- **Map**: MapLibre GL JS (WebGL, ~200kb gz, BSD). Replaces Leaflet. Lines,
  stations and trains become GPU layers, which is what unlocks 60fps motion,
  inertial zoom, pitch/tilt, and glow.
- **Motion**: Motion One (~5kb, Web Animations API) for UI choreography.
  Chosen over GSAP for size/licence; revisit only if timeline-heavy sequences appear.
- **Tiles**: recommend **self-hosted Protomaps `.pmtiles`** on the existing Pi
  (cheap, in our control, and it keeps the "runs offline / from our own infra"
  story). MapTiler hosted is the fast alternative if Pi bandwidth is a concern.
  This is the one external decision to lock before implementation.

## Architecture

Today `web-map.js` (~2.4k lines) is organised around Leaflet markers/popups. The
**data layer stays**: SSE live positions, the schedule projector, the station
sheet, and the JS Ariadne (`web-ariadne.js`) are all map-agnostic. Only the
**render layer** changes.

Split `web-map.js` into:
- `map/core.js` — MapLibre init, style, camera.
- `map/lines.js` — line geometry as `line` layers with a glow (blur) layer beneath.
- `map/stations.js` — station `circle`/`symbol` layers + click → existing station sheet.
- `map/trains.js` — live train source; per-frame interpolation (see below).
- `map/motion.js` — Motion One helpers for the panels/sheets.
- Keep `web-ariadne.js` and the station-sheet DOM as-is.

## Living-train motion (the core)

Live positions arrive over SSE every few seconds. Instead of snapping markers:
- Keep each train's last two known positions + timestamps.
- On a `requestAnimationFrame` loop, interpolate along the **line geometry**
  (not straight lines) between the two, easing, so trains glide.
- Feed interpolated positions into a GeoJSON source updated with
  `source.setData()` each frame (MapLibre handles GPU redraw).
- Fall back to schedule-projected "ghost" trains (dimmed) when SSE is stale, so
  the map is never empty — reuses the projector already in the codebase.

## UI polish (Motion One)

- Station sheet / Ariadne panel: spring in/out (`animate(el, {transform}, {type:'spring'})`).
- Live-trains list: staggered reveal.
- "Next train" countdown: number transitions.
- Respect `prefers-reduced-motion`: disable non-essential motion.

## Offline / performance

- The web already brands "runs offline / predicted from schedule". MapLibre
  needs a vector style; Protomaps `.pmtiles` can be range-requested and cached,
  keeping most of that promise. Document the exact offline degradation.
- Budget: cap the rAF train loop; use one GeoJSON source for all trains; avoid
  per-train DOM. Test on a mid-tier Android browser, not just desktop.

## Rollout

- Build on a branch (`feature/maplibre-web`), NOT master, because master
  auto-deploys to the live site via `pages.yml`. Requires **visual QA** before merge.
- Ship P1 (living map) first behind the same URL; P2 and P3 follow.
- Unify the parser here: MapLibre rewrite is the clean moment to have the web
  call the KMP `AthensTransitParser` (already compiles to wasmJs) via `@JsExport`
  instead of maintaining `web-ariadne.js` as a third port.

## Risks

- Full render-layer rewrite of a 2.4k-line file — highest-effort item; do it
  incrementally (base map → lines → stations → trains → motion), each visually QA'd.
- Tile hosting cost/bandwidth on the Pi — validate before committing.
- Bundle size + first-paint on mobile web — measure, lazy-load MapLibre if needed.
