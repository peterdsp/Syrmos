# Web UI refresh — design

Date: 2026-07-21
Status: approved (brainstorm), ready to implement

## Problem
The web header uses raw emoji/text glyphs for its controls — `☾` (theme), `🚇`
(vehicles), `🧠` (Ariadne brain), plus `−`/`+` — which render as inconsistent
full-colour OS emoji and read as cheap. The Ariadne logo is a horizontal lockup
(owl + labyrinth + red thread + the word "Ariadne") crammed into 56×56 and 24×24
squares, so it squishes and its light background shows as a box — "not shown
correctly." The art itself is good and should be kept.

## Decisions (from brainstorm)
- Scope: full web visual refresh — icons + logo + header + bottom sheet +
  Ariadne panel.
- Icons: one monochrome inline-SVG line-icon set, `currentColor`, stroke only.
- Ariadne: split the mark (owl + thread) from the wordmark; use each where it
  fits. Keep the existing art (crop, don't redraw).
- Header: one clean single toolbar; zoom floats onto the map.

## 1. Icon system (foundation)
- One inline `<svg style="display:none">` sprite of `<symbol>`s at the top of
  `index.html`: `ic-theme`, `ic-theme-sun`, `ic-vehicles`, `ic-zoom-in`,
  `ic-zoom-out`, `ic-locate`, `ic-brain`, `ic-search`, `ic-close`. Buttons use
  `<svg class="ic"><use href="#ic-…"/></svg>`.
- Style: `stroke: currentColor; stroke-width: 1.75; fill: none;
  stroke-linecap/linejoin: round`; 24×24 viewBox; renders at 20px inside a
  40×40 tappable button; hover/active tint from `--sy-surface-muted`. Inherits
  `currentColor` so it flips light/dark automatically.
- Accessibility unchanged: buttons keep `aria-label` + `data-i18n-title`; the
  SVG is `aria-hidden`.

## 2. Header toolbar
- Rounded top-bar card over the map. Left: `Syrmos` wordmark. Right, grouped:
  `EN | EL | SQ` segmented control (active = `--sy-brand`), hairline divider,
  then theme / vehicles / locate line-icon buttons.
- Zoom `−/+` moves off the bar to Leaflet's native zoom control floated
  bottom-right, restyled to match the icon system.
- Search bar full-width directly below, `ic-search` leading, one radius.

## 3. Ariadne mark
- Asset: crop the existing 256px art to an icon-only mark (owl + red thread, no
  wordmark, transparent) exported at 2×; keep the full lockup for wide contexts.
- Launcher: 56px circular button, light surface, subtle brand ring + soft
  shadow, mark centred with padding. Dark mode keeps a light disc behind the
  mark so the navy owl reads.
- Panel header: full `Ariadne` lockup at natural wide aspect.
- Replies: 24px circular owl avatar beside each Ariadne bubble.

## 4. Bottom sheet + Ariadne panel
- Shared tokens for both: `--sy-radius-xl` cards, `--sy-surface-card`, the
  already-fixed light/dark text.
- Station sheet: line pills in true line colours, departure rows on the type
  scale, close/search as line icons, and the home of the T8 source-confidence
  chip.
- Ariadne panel: slide-in aside, lockup header, line-icon close + brain,
  message bubbles with owl avatars, rounded composer with a primary Send.

## Files
- `composeApp/src/wasmJsMain/resources/index.html` — icon sprite, button
  markup, Ariadne launcher/panel markup.
- `composeApp/src/wasmJsMain/resources/web-map.css` — icon + toolbar + sheet +
  panel styling.
- `composeApp/src/wasmJsMain/resources/web-map.js` — theme toggle swaps
  `ic-theme`↔`ic-theme-sun`; any glyph writes become `<use>` swaps.
- New asset `ariadne-mark.png` (cropped, transparent, 2×).

## Verification
- Deploy to master (Pages), then on the live site (cache-bust): no emoji in the
  header; icons crisp in light + dark; Ariadne launcher shows the mark cleanly
  (no white box, no squish); panel header shows the full lockup; sheet + panel
  read as one system. Screenshot both themes.

## Rollout
Web-only (no version bump; web is rolling). After it ships + is verified,
resume the queued batch: T8 (iOS + web mirrors + remaining Android surfaces),
T9, T7, T6, station-ID reconcile.
