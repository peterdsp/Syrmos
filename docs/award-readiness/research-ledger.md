# Syrmos research ledger

Each entry records a question, the source that answered it, the finding, the
product decision it changed, and the evidence after implementation. Findings
must change implementation, not decorate documentation. Entries are only added
once the source has actually been consulted; the backlog lists sources not yet
read (no findings invented for them).

Last updated: 2026-08-29.

## Consulted

### R-001 GitHub Pages routing behaviour (primary, empirical)
- Source: live host responses, `curl https://syrmos.peterdsp.dev/...` (2026-08-29).
- Question: does the host serve an SPA fallback for unknown paths?
- Finding: no. `GET /plan` returns HTTP 404 (`Page not found - GitHub Pages`).
  The served map page carries no `composeApp.js`/`.wasm`, so it is a pure static
  site.
- Decision influenced: adopt static per-workspace entry-point directories plus
  query-param dynamic ids instead of an SPA fallback or `<base href>`
  ([docs/adr/0001-web-url-model.md](../adr/0001-web-url-model.md)).
- Evidence after implementation: local Pages-bundle simulation returns 200 for
  every entry point, 301 for `/plan` to `/plan/`, and `/plan/` + `/tickets/`
  boot the full app and restore state (PR #23).

### R-002 `<base href>` and inline SVG `<use>` (secondary, known web behaviour)
- Source: HTML/SVG resolution behaviour for `<use href="#id">` under a document
  `<base>` (Chromium/WebKit fragment-resolution bug class).
- Question: can a `<base href="/">` make relative assets resolve from any depth?
- Finding: it would, but it breaks inline SVG `<use href="#ic-...">` icon refs,
  which the app relies on for every nav and status icon.
- Decision influenced: use root-absolute asset paths instead of `<base>`; leave
  `<use href="#...">` fragments untouched (ADR-0001).
- Evidence after implementation: absolutization diff kept all 13 `href="#ic-"`
  fragments intact; icons render on deep routes in the local simulation.

## Backlog (not yet consulted, no findings recorded)

Award criteria and finalists to study (verify current deadlines/eligibility
before relying on them):
- Apple Design Awards - https://developer.apple.com/design/awards/
- Google Play Best of - editorial criteria
- Webby Awards - Travel/Transportation + UX/UI judging criteria
- Red Dot - public-transport projects (iBus, wohinduwillst)
- iF Design Award - apps/software, mobility UX
- UX Design Awards - judging criteria
- UITP Awards - measurable public-transport improvement

Peer-reviewed / applied research topics to source:
- Real-time information uncertainty and rider trust
- Transfer anxiety and buffer communication (airport journeys)
- Cognitive load under time pressure in transit UIs
- Accessible maps and low-vision transit interfaces
- Offline-first mobility tools

Rule: do not add a finding here until the source is actually read; do not raise
a scorecard dimension from an unread source.
