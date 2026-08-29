# ADR 0001: Web URL model on GitHub Pages

Status: Accepted (2026-08-29)
Scope: web app (`composeApp/src/wasmJsMain/resources`), GitHub Pages deploy.

## Context

The web redesign introduces five workspace roots (Now, Plan, Explore,
Departures, Tickets) plus line and station views. These must be deep-linkable:
a shared URL, a reload, and Back/Forward all have to land on the right
workspace and selection.

Hard constraints discovered:

- The site is hosted on **GitHub Pages** with a custom domain
  (`syrmos.peterdsp.dev`). Verified `GET /plan` returns **HTTP 404**
  (`Page not found - GitHub Pages`): Pages has **no SPA fallback**, so an
  unknown path is a real 404, not the app.
- The map page is a **pure static site** (Leaflet + hand-written JS/CSS); there
  is no Wasm bundle in the served page. So there is no framework router and no
  Wasm loader path behaviour to work around.
- Icons are inline SVG referenced with `<use href="#ic-...">` fragments. A
  `<base href="/">` (the usual way to make relative assets resolve from any
  path depth) **breaks these fragment refs** in Chrome and Safari, so it is not
  an option.
- Runtime data (`/files/seed/*.json`, `/icons/*/manifest.json`, `/shapes.json`)
  and images were fetched with relative URLs, which only work at the site root.

## Decision

Serve each route as a **static entry-point directory** and carry every dynamic
identifier as a **query parameter**:

- Workspaces: `/now/`, `/plan/`, `/explore/`, `/departures/`, `/tickets/`
- Views: `/line/`, `/station/`
- Dynamic ids and selections in the query string:
  `/line/?id=M3&direction=airport`, `/station/?id=SYNTAGMA`,
  `/plan/?from=SYNTAGMA&to=AIRPORT`, `/departures/?station=PIRAEUS`
- The Now workspace is the site root `/` (its document is the canonical entry).

Implementation:

1. `scripts/prepare-pages-web-release.sh` copies the finished, asset-versioned
   `index.html` into each route directory and into `404.html`.
2. All asset references in `index.html` and all runtime fetches/images in
   `web-map.js` are **root-absolute** (`/web-map.js`, `/files/seed/...`), so
   every route copy loads the exact same content-hashed assets from the root.
   SVG `<use href="#ic-...">` fragments are left untouched.
3. `web-router.js` builds and parses these URLs (workspace directory + query),
   and `web-nav.js` restores the workspace on load and on Back/Forward.
4. `404.html` is **recovery only**: it lets a stale or mistyped deep path still
   boot and route client-side, but because the canonical entry points return
   200, a custom 404 (which stays an HTTP 404 on Pages) is never the primary
   routing mechanism. This keeps indexing, link previews and uptime checks
   honest.

## Alternatives considered

- `<base href="/">` + single index: rejected, breaks the inline SVG icons.
- Custom `404.html` as the sole SPA fallback: rejected as the primary
  mechanism, it returns HTTP 404 and weakens indexing, previews and monitoring.
- Hash routing (`/#/plan`): kept as a **documented fallback** only. If static
  route generation is proven incompatible after three materially different
  attempts, switch to hash routing so development is not blocked, record the
  limitation here, and plan a migration back.

## Consequences

- Deep links, reloads and Back/Forward work with no SPA-fallback dependency.
- `/<workspace>` without a trailing slash gets a single 301 to `/<workspace>/`
  from Pages, then 200. Canonical links use the trailing slash.
- New workspaces require a one-line addition to the route list in the deploy
  script.
- Asset paths are absolute, so the app must remain served from the domain root
  (true for the custom domain).

## Verification

- `node web-router.js` and `node web-nav.js` self-tests pass (parse/build
  round-trips for every route shape; nav click/restore/utility behaviour).
- Local simulation of the Pages bundle (the site is pure static, so no JDK is
  needed): every entry point returns 200, `/plan` returns 301 to `/plan/`, and
  loading `/plan/` and `/tickets/` boots the full app, loads all assets from the
  root, restores the workspace and its nav highlight, pushes the path on click,
  and restores state on Back.
- Post-deploy: direct-load and reload each workspace URL on the live custom
  domain and confirm 200 + asset loading. (Run after this change deploys.)
