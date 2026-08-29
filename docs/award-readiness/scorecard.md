# Syrmos award-readiness scorecard

Scores the **real, shipped product**, not aspirations. Every score needs
evidence (a test, benchmark, screenshot, audit, before/after, or repo
reference). A capability existing is not evidence of quality. Dimensions with no
verified evidence yet are left **Pending** on purpose rather than guessed.

Scale: 0 absent - 1 poor - 2 partial - 3 solid - 4 strong - 5 exemplary.

Last updated: 2026-08-29.

| # | Dimension | Score | Evidence | Gap to raise it |
|---|-----------|-------|----------|-----------------|
| 1 | Three-second comprehension | Pending | Not yet measured with a task/timing protocol. | Run a first-glance comprehension test on Now/Airport; record timings. |
| 2 | Journey-task completion | Pending | No end-to-end task-success measurement yet. | Define core tasks (last train, A-to-B, airport buffer) and measure completion. |
| 3 | Transit-data honesty | 3 | Typed provenance/freshness contract merged (PR #18: `core/model/.../status/DataStatus.kt`, 17 tests). Airport OASA feed correctly framed as vehicles approaching the airport, never fake city departures ([[chip-status-atomicity]] context, [[web-map-athens-time]]). | Surface the DataStatus contract in every web/iOS/Android arrival row with a visible live/scheduled/cached/offline chip + age. |
| 4 | Offline usefulness | Pending | Seed bundles + cached schedules exist; not yet audited as an offline flow. | Audit airplane-mode behaviour on all three platforms; capture screenshots. |
| 5 | Accessibility | 2 | Web nav has `aria-current`/`aria-label` (PR #22); no full audit, contrast/VoiceOver/TalkBack unverified. | Run axe/Lighthouse a11y + VoiceOver + TalkBack passes; log issues. |
| 6 | Localization | 3 | EN/EL/SQ/IT strings across web + native (Ariadne i18n in web-map.js; `airportText` on native). | Audit long-locale layouts (EL/SQ) for truncation; add pseudo-loc screenshots. |
| 7 | Platform-native quality | 3 | Native SwiftUI (iOS) + Compose (Android) + hand-written web; chips now atomic across all three (PR #21). | Adaptive/large-screen (tablet) audit on Android; Dynamic Type sweep on iOS. |
| 8 | Visual coherence | 3 | "Calm Signal" design system (DESIGN_SYSTEM.md) + `--sy-*` tokens; systemic chip/status fix (PR #21). | Token audit for stragglers; unify the three FlowLayout copies on iOS. |
| 9 | Motion and interaction | Pending | Some transitions exist; not evaluated. | Inventory motion; check reduced-motion support. |
| 10 | Performance | Pending | No traces captured. | Lighthouse web trace; cold-start + scroll traces on native. |
| 11 | Privacy | 3 | Web geolocation is opt-in; no third-party analytics observed in web resources. | Confirm no PII in URLs (new query-param routing uses ids, not PII) and document. |
| 12 | Social impact | 3 | Free, honest public-transport info for Athens/Thessaloniki incl. airport access for 4 languages. | Quantify reach/benefit for a UITP-style measurable-outcome claim. |
| 13 | Sustainability | Pending | Modal shift toward transit is inherently positive; not quantified. | Frame a modal-shift/behaviour-change hypothesis with evidence. |
| 14 | Distinctiveness | 3 | Ariadne assistant + calm dense map + airport command surfaces are recognisable. | Sharpen a single signature interaction that is unmistakably Syrmos. |
| 15 | Measurable outcomes | Pending | No analytics/outcome instrumentation reviewed. | Decide privacy-safe outcome metrics (task success, not tracking). |
| 16 | Shareability / deep links | 3 | Deep-linkable workspace URLs shipping (PR #23, [docs/adr/0001-web-url-model.md](../adr/0001-web-url-model.md)): `/plan/`, `/line/?id=M3`, etc. return 200 and restore state. | Add per-route share affordance + verify link-preview og:image per route. |
| 17 | Screenshot quality | 2 | Have before/after regression PNGs (chips) and deep-route render shots; no polished store-grade set. | Produce a device-frame screenshot matrix for web/iOS/Android key states. |
| 18 | Store-listing quality | Pending | Not reviewed this cycle. | Audit App Store + Play listing copy/screenshots against Best-of criteria. |

## How this file is used

- Update a row only when new **evidence** exists; cite the PR/file/screenshot.
- Never raise a score because a feature was added; raise it when the feature is
  measured or audited.
- Feeds the decision framework: dimensions with the highest weight
  (transit truth, immediate usefulness, accessibility) and the lowest current
  score are the priority backlog.

## Current priority backlog (low score x high weight)

1. Accessibility audit (weight 14, score 2) across all three platforms.
2. Three-second comprehension + journey-task completion measurement (the two
   highest-usefulness dimensions, currently unmeasured).
3. Surface the DataStatus provenance/freshness chip in every arrival row so
   transit-data honesty (weight 20) is visible, not just modelled.
