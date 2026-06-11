# Syrmos — A Case Study in Building a Civic Transit App from a Childhood Obsession

**Version 1.0 · Last updated June 10, 2026**

> A living document. Every shipped feature, every store milestone, every meaningful user-growth datapoint earns a new entry in the Revision Log at the bottom. The body of the case is rewritten when the situation analysis materially changes.

---

## Table of Contents

1. [Time Context](#1-time-context)
2. [Point of View](#2-point-of-view)
3. [Central Problem](#3-central-problem)
4. [Objectives](#4-objectives)
5. [Situation Analysis](#5-situation-analysis)
6. [Alternatives Considered](#6-alternatives-considered)
7. [Evaluation Criteria](#7-evaluation-criteria)
8. [Recommendation](#8-recommendation)
9. [Implementation Plan](#9-implementation-plan)
10. [Marketing and Go-to-Market](#10-marketing-and-go-to-market)
11. [User Growth Projection](#11-user-growth-projection)
12. [Risks and Mitigation](#12-risks-and-mitigation)
13. [Appendix A — Architecture Decisions](#appendix-a--architecture-decisions)
14. [Appendix B — Product Roadmap](#appendix-b--product-roadmap)
15. [Revision Log](#revision-log)

---

## 1. Time Context

The case takes place in **June 2026**, the month Syrmos goes from a personal project into a published consumer transit app. The first iOS build was submitted to App Store Review on June 7, 2026 and rejected for incomplete review notes. By June 10 a sixth build (1.0.6, soon to be superseded by 1.0.8) has been uploaded with the rejection issues addressed, the Android Internal Testing track is live with build 5, the Closed Testing - Alpha track has been sent to Google for review, and the wasm web client is live at **syrmos.peterdsp.dev**.

Three forces converge to make this the right moment.

First, **Greek public transit data has finally become reliably accessible**. The Hellenic Train Train Tracker (`railway.gov.gr`) publishes a public SSE feed of live suburban train positions. STASY (Athens Urban Rail Transport S.A.) publishes its full schedule and service announcements on `stasy.gr`. Until 2024 this data either didn't exist in any structured form or was locked behind agency intranets.

Second, **Athens public transit ridership has bounced back** to pre-pandemic levels. STASY reports ~1.5M daily metro trips in the Attica region across the three metro lines, with strong year-over-year growth on the Tram T7 extension that opened to Piraeus in 2022.

Third, the founder's personal situation aligns. After several years as an iOS engineer in fintech (currently at Plum), the technical depth needed to build Syrmos end-to-end without a team or seed funding has accumulated. The marginal cost of shipping is now a weekend.

The decision the founder must make this month is not whether to build Syrmos. Syrmos is built. The decision is **what to do next, and how fast**.

## 2. Point of View

This analysis is written from the point of view of **Petros Dhespollari, the founder, sole engineer, and product manager of Syrmos**. He is responsible for:

- Every line of code on iOS (native Swift), Android (Compose Multiplatform), and Web (Kotlin/Wasm + vanilla JS)
- The Pi-hosted API at `api-syrmos.peterdsp.dev`
- The product roadmap and feature prioritization
- The marketing strategy, the App Store and Play Console submissions, and direct correspondence with Apple and Google review teams
- The relationship with the data sources (STASY, Hellenic Train, OpenStreetMap)
- Customer support, eventually

He has no co-founder, no investors, and no external deadlines other than the App Store review timeline he chose to enter. He has a day job. He has finite weekends.

His tradeoffs are not the ones a venture-backed startup faces. He optimizes for **shipping quality without burning out**, not for growth at all costs.

## 3. Central Problem

The central problem is this:

> **How does a one-person, no-budget, civic transit app go from a personal side project to a daily-use utility for a meaningful share of Athens commuters, without a single advertising spend, without compromising the offline-first promise that makes it different, and without burning the founder out?**

Three sub-problems sit under that umbrella:

1. **Distribution.** App Stores reward apps that already have users. Greek commuters don't search "Athens metro times" in English. They use Google Maps for routes and squint at faded paper timetables on the platform. How does a new app cut through that?

2. **Trust.** STASY and Hellenic Train are state operators. Their data is public, but their official apps don't exist or are unmaintained. Will users trust a third-party app over a hand-typed paper schedule? What signals legitimacy?

3. **Sustainability.** A solo project that depends on one developer's free weekends has a half-life. The current architecture (Raspberry Pi at home, GitHub Pages for web, BSD-licensed source on GitHub) was chosen to minimize ongoing cost. But there is no monetization plan, no donations link, and no realistic path to hiring help. If Syrmos succeeds, it has to keep succeeding without growing the team.

## 4. Objectives

The June 2026 decision needs to produce a plan that:

1. **Gets the iOS app and Android app approved and published** by the end of Q3 2026 (September 30, 2026)
2. **Reaches 10,000 monthly active users (MAU) within 6 months of public launch** on either store. This is roughly 0.7% of daily Athens metro riders, an achievable beachhead
3. **Achieves a 30-day retention rate of at least 25%**, which would put Syrmos in the top quartile of utility apps in its category
4. **Keeps maintenance under 4 hours per week** of founder time in steady state, freeing weekends for feature development not firefighting
5. **Preserves the offline-first contract**: the app must always work without network, even if every API on the Pi goes dark for a week
6. **Stays free, ad-free, and tracking-free** for as long as the maintenance budget allows. If donations or a one-time tip jar become necessary, they appear inside the Settings tab, never in the main UI flow

## 5. Situation Analysis

### 5.1 Origin and history — a personal note woven into the strategy

Syrmos did not start as a product. It started as a **boyhood obsession with the Athens Metro**.

In the early 2000s, when Athens Line 2 (red) and Line 3 (blue) had just opened in time for the 2004 Olympics, an eight-year-old version of the founder rode the metro repeatedly for the sake of riding it. He carried a small notebook. He wrote down the times trains arrived. He compared them to the printed timetables at Syntagma station and tried to find the pattern. He noticed that **the timetables were rounded**, that real arrivals had variance, and that the variance itself was a pattern: rush hour was 3 to 5 minutes between trains, midday was 7 to 10, and very late evening drifted toward 12.

The fascination outlasted childhood. As a teenager he sketched what he called "Live Athens times" on graph paper, with no understanding that he was inventing a UI mockup. The page had a station name at the top, a list of upcoming departures with minute countdowns, and arrow icons pointing toward the terminal stations. The notebook still exists in a drawer in the family home in Greece.

In 2018, as an undergraduate computer science student, the founder built a first attempt: a Python script that scraped `stasy.gr` once an hour and emailed him a digest. It worked for two weeks before STASY changed their site layout and silently broke it. The script was abandoned.

In 2022, the **opening of the Piraeus extension of the Tram T7** brought the obsession back. The founder rode the new line on its first weekend, noticed that no app showed the new stops, and started a second attempt. This one was a SwiftUI prototype with hardcoded schedule data for Line 3 only. He used it himself on commutes for six months. The codebase was thrown away after he realized that he had built it for the iPhone only and his Android-using friends had no way to test it.

In late 2025, with two years of Kotlin Multiplatform experience accumulated at his day job, the founder restarted the project for the third and final time. This iteration is what shipped as Syrmos 1.0 in June 2026. The name "Syrmos" (συρμός) is the Greek word for the connected carriages that form a metro train, and it carries the connotation of "the next train is coming." It was chosen over alternatives like "Tachys" (fast) and "Athina Metro" because it is short, evokes movement, and is not already taken on any app store.

This origin story matters strategically for two reasons:

- **The product was used personally by the founder for a year before it had any users**, which is a much better dogfooding regime than most startups can claim. Every flow that survives in the production app is one a real commuter (the founder) found indispensable.
- **The founder's personal connection to Athens transit is marketable**. The "made by a kid who grew up writing down train times" narrative is genuine, distinctive, and translatable into press and TikTok content. It is the opposite of the generic "we built a transit app" pitch.

### 5.2 Current state of the product (June 2026)

Syrmos ships across three platforms from a single source repository:

| Platform | Technology | Status |
|---|---|---|
| iOS | Native Swift, SwiftUI, MapKit | Build 1.0 (8) in App Store Review, June 10, 2026 |
| Android | Kotlin Multiplatform, Compose Multiplatform | Build 5 live on Play Console Internal Testing, build 5 in Closed Testing - Alpha review |
| Web | Kotlin/Wasm + vanilla JS overlay, Leaflet maps | Live at syrmos.peterdsp.dev, auto-deployed from main branch via GitHub Pages |

The shared business logic lives in `core/domain`, `core/data`, and `core/network` Kotlin modules. The iOS app is native to give Apple Maps and SwiftUI a clean home, with a thin bridge to the shared models. The Pi (`192.168.10.10`, exposed via Cloudflare Tunnel at `api-syrmos.peterdsp.dev`) hosts three live endpoints:

- `/api/announcements` — STASY scrape, refreshed every 5 minutes via cron
- `/api/trains` — Hellenic Train SSE relay, serving cached JSON every 10 seconds
- `/api/lines` — canonical line and station data so future fixes don't require app releases

Feature coverage today:

- 71 metro stations across 3 lines
- 56 tram stations across 2 lines (T6, T7)
- 68 suburban railway stations across 4 lines (A1, A2, A3, A4)
- Live train positions for suburban trains (A1-A4 only at this writing)
- Clock-aligned departure countdowns that tick down in real time
- Offline-first contract: all schedule data is embedded; live trains and announcements degrade gracefully
- English and Greek bilingual interface with auto-detect
- Light and dark themes, with dark mode using OLED pure black on web
- GPS "Near me" section that surfaces the five closest stations
- Live map view with simulated train animations and real suburban positions

### 5.3 Market analysis

**Total addressable market.** Athens metropolitan area has ~3.8M residents. STASY reports ~1.5M daily metro and tram trips. Hellenic Train reports ~50K daily suburban rail trips on the Athens corridors. The realistic target is anyone who uses the metro, tram, or suburban at least once a week.

**Competition.** Three categories:

1. **Google Maps**. The default. Strong at routing, weak at real-time timetables, weak at offline behavior, weak at "what does the next departure look like at this station." Maps users will not abandon Google Maps for routing. Syrmos has to win the "I'm already at the station and I want to know when the next train is" moment, which Google Maps does not own.

2. **STASY official channels**. STASY does not have a live timetable app. Their website has a static PDF schedule, and a marquee announcing service alerts. There is no real-time tracking. This is a gift to Syrmos.

3. **Hellenic Train Train Tracker**. The state operator's live web tracker is excellent for desktops but is not designed for phones. It requires login as of June 2026. The publicly accessible SSE feed (used by Syrmos behind the scenes) is undocumented and could change.

**Differentiation.** Syrmos wins on:

- **Speed**. The app responds instantly because the data lives on the device.
- **Offline reliability**. Subway platforms have no signal. Google Maps does not work underground. Syrmos does.
- **Civic respect**. Free, no ads, no tracking, BSD-licensed source. This positioning is rare and resonates with Greek users who are skeptical of foreign tech.
- **Made for Athens**. The visual identity uses the official line colors (metro green, red, blue; tram orange; suburban purple). The fonts and tone are Greek-first.

### 5.4 Constraints

- **One developer**. Hard ceiling on output. Every feature competes with every other feature for weekend hours.
- **No budget**. App Store fees and a $10 domain are the entire annual external spend. Hosting is on a Raspberry Pi at home behind a residential ISP.
- **No legal entity**. The app is published under the founder's personal Apple ID and Google Play developer account. This limits liability protection and complicates any future monetization.
- **Greek language burden**. Every UI string and announcement parse must work in both English and Greek. This doubles the QA matrix.
- **Apple App Review and Google's 14-day closed testing rule**. Both are gating factors out of the founder's control.

## 6. Alternatives Considered

Three realistic paths emerged in the strategy review at the end of Q2 2026.

### Alternative 1: Ship narrow, expand carefully

Launch with the current scope (Athens metro, tram, and suburban A1–A4 only). Spend the first 90 days post-launch on **polish, marketing, and user feedback loops**. Do not expand to InterCity national rail, Thessaloniki suburban, or accessibility features until 10,000 MAU is reached.

- **Pros**: low risk of overpromising, focused QA matrix, marketing message stays simple ("Athens metro times, instantly"), founder energy preserved for maintenance.
- **Cons**: the larger Greek diaspora (Patras, Thessaloniki, Crete) doesn't see itself in the product, slower TAM growth, harder to argue for press coverage as "just an Athens app."

### Alternative 2: Ship wide, take on national rail immediately

Launch with InterCity and Thessaloniki suburban in scope from day one, even though the data quality is lower. Position Syrmos as "the Greek rail app" rather than "the Athens metro app."

- **Pros**: 3x larger TAM (Greek rail riders nationally), better PR angle ("first nationwide Greek transit app"), defensible against future Athens-only entrants.
- **Cons**: triples the data integration burden, introduces operational risks (THESLAR corridor schedule changes, Hellenic Train data quality), much larger surface area for bugs at launch, founder burnout risk significantly higher.

### Alternative 3: Open-source the entire stack and recruit contributors

Stop trying to ship a product and instead **publish Syrmos as a community-maintained open source civic-tech project**, modeled on Citymapper's data tooling or San Francisco's Transbase. Invite the Athens dev community to contribute station data, language strings, and bug fixes.

- **Pros**: scales beyond one developer, builds local technical community, creates a long-term institution rather than a one-person side project, aligns with the existing BSD license.
- **Cons**: open source projects without a maintainer paid for the role have a high mortality rate, governance overhead (PR review, issue triage) competes with the same weekend hours as feature work, the founder's day-job employer might have IP concerns about a contributor-style project that overlaps with mobile engineering.

## 7. Evaluation Criteria

The three alternatives are evaluated on:

| Criterion | Weight | Why it matters |
|---|---|---|
| Strategic fit with founder's stated objectives | 30% | Section 4 lists six objectives. Whichever option meets the most of them wins. |
| Financial feasibility | 10% | Low weight because the budget is effectively zero. The relevant cost is founder time, captured separately. |
| Implementation difficulty | 20% | A solo developer can't carry compound complexity. The simpler option wins ties. |
| Stakeholder impact | 15% | Users, STASY/Hellenic Train as data sources, future contributors, the founder's personal life. |
| Long-term risk | 25% | Burnout, project death, data-source revocation. Heavily weighted because they are existential. |

## 8. Recommendation

**Adopt Alternative 1 (Ship narrow, expand carefully).** This is the option that:

- Scores highest on strategic fit, particularly on the "keeps maintenance under 4 hours per week" objective
- Has the lowest implementation difficulty, since the entire current product was scoped for Athens-only and shipping it as-is is essentially free
- Lowers long-term risk by reserving founder energy for maintenance and selective expansion, not for a chronic scope-creep crisis

Alternative 2 (Ship wide) is rejected because it triples the data-integration surface area at the worst possible moment (immediately post-launch, when bug discovery is highest and the founder has the least slack). It is **deferred, not killed**. National rail and Thessaloniki suburban remain on the roadmap as v1.2 and v1.3 features, contingent on Athens-only adoption metrics hitting their targets first.

Alternative 3 (Open-source first) is rejected as the primary strategy, but **partially adopted**: the source code remains public on GitHub under BSD 3-Clause, contributions are accepted, and the canonical schedule data on the Pi is structured so external contributors can submit PRs against the JSON file rather than the app binary. This captures the upside of community participation without depending on it.

## 9. Implementation Plan

The plan is structured in three phases. Phase 0 is **already done by the time this case is written**.

### Phase 0: Build and submit (October 2025 — June 10, 2026) — COMPLETE

- Kotlin Multiplatform skeleton, Compose Multiplatform Android, native Swift iOS
- Embedded schedule data for 195 stations across 9 lines
- Pi-hosted API for live data and announcements
- Web build deployed via GitHub Actions to syrmos.peterdsp.dev
- iOS build 1.0 (8) submitted to App Store Review
- Android build 5 published to Internal Testing, build 5 in Closed Testing review

### Phase 1: Public launch (June 11 — September 30, 2026)

- Pass App Store Review on iOS (target: build 8 approved by June 17, 2026)
- Complete Google's mandatory 14-day Closed Testing with 12 testers (target: Closed Testing complete by July 5, 2026)
- Launch on Google Play production track on July 8, 2026
- Launch on Apple App Store production immediately after Apple approval
- Issue a v1.0 announcement on syrmos.peterdsp.dev, the founder's personal social channels, and one Greek tech outlet
- Monitor MetricKit and the in-app Diagnostics export for the first cluster of user-reported bugs and ship a v1.0.1 hotfix within 14 days of launch
- Set up the GitHub Issues template so user feedback flows back to a single inbox

### Phase 2: First 6 months post-launch (October 2026 — March 2027)

- Reach 10,000 MAU on at least one platform
- Reach 25% 30-day retention
- Ship v1.1 with widget support (iOS Home Screen widget showing next departures at a saved station)
- Ship v1.2 with accessibility audit fixes (full VoiceOver and TalkBack coverage)
- Add Greek translation polish based on user feedback
- Consider a one-time donation tip jar in Settings if hosting costs cross 50 EUR per month

### Phase 3: Expansion (April 2027 onward)

- Trip planner ("how do I get from X to Y by metro and tram") if v1.0 retention is healthy
- National InterCity rail and Thessaloniki suburban rail, contingent on Hellenic Train cooperation
- Optional AI chat helper for natural-language queries ("when is the next airport train after 10pm?")
- Apple Watch and Wear OS companion apps if iOS engagement justifies the engineering cost

## 10. Marketing and Go-to-Market

The marketing strategy assumes a zero-budget reality. Every channel listed must produce installs without paid acquisition.

### 10.1 Channels in priority order

| Channel | Why it works for Syrmos | Effort cost | Expected installs in first 90 days |
|---|---|---|---|
| **Apple App Store and Google Play organic search** | Greek "metro" and "τραίνο" queries have low competition. ASO with the right keywords ranks fast. | 4 hours of metadata writing | 1,500 - 3,000 |
| **Word of mouth on r/athens, r/greece, r/Athens, r/HellenicTrain** | Subreddits skew tech-literate and respond well to civic projects from Greek developers | 1 hour per post, 1 post per subreddit at launch | 500 - 1,500 |
| **TikTok and Instagram Reels — origin-story content** | The "kid who wrote down metro times" backstory is genuinely interesting. Short-form video can hit Greek transit influencers organically. | 4 hours per video, 4 videos in launch month | 2,000 - 5,000 potential reach, 200 - 500 installs |
| **Greek tech press (Tovima.gr, In.gr Tech, Kathimerini)** | A solo-developer civic app with a backstory is a publishable feature. Cold-pitch with a clean press kit. | 6 hours to write the kit, 4 hours to email 10 journalists | 1 article = 500 - 2,000 installs |
| **University partnerships (NTUA, AUEB, University of Piraeus)** | Computer science and architecture students ride the metro daily. Posters in CS department lounges. Talk at a student association event. | 8 hours for a 30-minute talk, free | 200 - 500 installs per event |
| **Posters at metro stations** | High-traffic, low-cost, requires permission from STASY (which may or may not grant it) | 4 hours of design, ~30 EUR printing, 4 hours hanging | Hard to attribute, but a strong signal of legitimacy |
| **GitHub stars and the open source community** | A polished BSD-licensed Kotlin Multiplatform civic project is a portfolio piece. Hacker News submission is plausible. | 1 hour to write the HN post | 1,000 - 5,000 if HN front page, 100 - 300 otherwise |
| **Direct outreach to STASY and Hellenic Train communications** | Long-shot partnership. Free press if either agency officially recommends the app on their channels. | 8 hours of email diplomacy | Unknown, potentially game-changing |

Paid channels (Apple Search Ads, Google App Campaigns, Meta Ads) are **explicitly excluded from the v1.0 plan**. They become available only if the donation tip jar generates a meaningful budget.

### 10.2 ASO keyword strategy

Primary keywords (target rank: top 5 in Greek App Store):

- "μετρό Αθήνα" (Athens metro)
- "τραίνο Αθήνα" (Athens train)
- "δρομολόγια μετρό" (metro schedule)
- "πότε έρχεται το τραίνο" (when is the train coming)
- "ΣΤΑΣΥ" (the operator name, high-intent searches)

Secondary keywords (target rank: top 20):

- "Αθήνα συγκοινωνίες" (Athens transit)
- "προαστιακός" (suburban)
- "τραμ Αθήνα" (Athens tram)

Title format used in the stores: **"Syrmos — Athens Rail Times"**. The dash and English subtitle help with both Greek-language and English-language search.

### 10.3 Press kit contents

A `press/` directory in the repository will contain:

- 1 paragraph elevator pitch in English and Greek
- High-resolution app icon and product screenshots
- The founder's biography
- The "kid who wrote down train times" origin photo (the actual childhood notebook page, scanned)
- A one-pager PDF with the same content for journalists who prefer email attachments

## 11. User Growth Projection

These are projections, not guarantees. They assume Alternative 1 is executed cleanly and at least 4 of the 8 marketing channels listed in section 10.1 produce installs.

| Milestone | Date | iOS MAU | Android MAU | Web MAU | Total |
|---|---|---|---|---|---|
| Public launch | July 2026 | 0 | 0 | 100 | 100 |
| 30 days post-launch | August 2026 | 800 | 600 | 400 | 1,800 |
| 90 days post-launch | October 2026 | 3,000 | 2,500 | 1,500 | 7,000 |
| 6 months post-launch | January 2027 | 5,500 | 4,500 | 2,000 | **12,000** |
| 12 months post-launch | July 2027 | 12,000 | 10,000 | 3,000 | 25,000 |
| End of Phase 3 | December 2027 | 25,000 | 22,000 | 5,000 | 52,000 |

The 12,000 MAU at 6 months exceeds the 10,000 objective in section 4. The 25,000 MAU at 12 months represents roughly 1.6% of daily Athens metro riders, a defensible market share.

Retention projection (30-day cohort retention):

- Month 1: 35% (high curiosity, launch press)
- Month 3: 28% (settles to utility usage pattern)
- Month 6: 26% (steady state)
- Month 12: 24% (mild long-tail decay)

The 25% objective is hit in months 3 through 12.

## 12. Risks and Mitigation

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Apple App Store rejects build 8 again | Medium | High | The build 8 review notes were rewritten based on Apple's specific Guideline 2.1 feedback. A pre-recorded screen recording is attached. If rejected, the founder has 7 days to respond before the submission ages out. |
| Google's 14-day closed testing rule blocks production launch | High | Medium | Already accepted. 12 testers will be recruited from the Athens iOS Developers Slack and CS friends. Worst case, launch slips to August. |
| Hellenic Train changes their SSE feed or revokes access | Medium | Medium | The Pi daemon is decoupled from the app. If the feed breaks, live trains degrade gracefully to "no live data" and the app stays useful. A short notice on Settings can explain the situation. |
| STASY changes their website layout, breaking the announcement scrape | High | Low | The scrape failure is invisible to users (cached announcements continue showing). The Pi log will alert the founder, who can fix the parser without an app release. |
| Founder burnout | Medium | Critical | Hard 4-hour weekly maintenance ceiling. Anything above the ceiling for 3 weeks in a row triggers a hiatus. The BSD license means the project can be picked up by a community contributor in the founder's absence. |
| A larger competitor enters the market | Low | High | Google Maps has chosen not to do this for 15 years. STASY has not done it. A bigger competitor entering would validate the market but force Syrmos to differentiate harder on civic-respect positioning. |
| Raspberry Pi failure or residential ISP outage | Medium | Low | The Pi hosts only live data. The app keeps working with embedded data. Acceptable downtime: 48 hours. Mitigation beyond that: move the API to a $5/month VPS, which the donation tip jar must fund. |
| Personal data privacy complaint from a Greek user | Low | High | The app collects nothing. The privacy policy at `docs/PRIVACY_POLICY.md` is short and accurate. The Diagnostics export is opt-in and user-controlled. |
| The founder takes a different job, moves, or otherwise loses time | Medium | Critical | Documented architecture, comprehensive test coverage as it ships, clear ROADMAP and CASE_STUDY files. Anyone reading the repo should be able to take over within a week. |

## Appendix A — Architecture Decisions

This appendix records the key technical choices and their justifications. They are intentionally summarized; the full reasoning lives in the commit history and the README.

| Decision | Choice | Why |
|---|---|---|
| Native iOS vs cross-platform iOS | **Native Swift** | SwiftUI + MapKit produce a noticeably better feel for navigation, sheets, and live map than Compose Multiplatform's iOS target as of June 2026. The shared business logic is small enough to bridge cleanly. |
| Android stack | **Kotlin Multiplatform + Compose Multiplatform** | Free reuse of the Android Compose code on Web. The single source of truth for use cases lives in `core/domain`. |
| Web stack | **Compose Multiplatform Wasm + vanilla JS overlay** | Compose handles the app shell; a vanilla JS popup handles the Leaflet map view because Compose's web map integration is immature. |
| Live data layer | **Polling-based JSON, not SSE** | An earlier SSE implementation locked up the iOS main thread when the upstream feed sent large payloads. Polling a small cached JSON file every 10 seconds is dramatically lighter. |
| Schedule data location | **Operator rules in SQLite on the Pi → static JSON snapshots → bundled into each app release as the offline floor, refreshed live on cold start** | Pre-computed minute-by-minute timestamps go stale at midnight and silently misrepresent service hours (the original seed showed M3 Airport trains at 01:12 even though the airport branch closes at 23:00 daily). Storing operator *rules* — operating window per day type, frequency bands by daypart, holiday patterns — lets a domain projector compute correct departures for any wall-clock time without a release. The build-time snapshot keeps the offline-first guarantee; the live API overlay lets us fix a wrong frequency band server-side and have every installed app pick it up on next launch. |
| Admin write path | **FastAPI behind nginx, gated by Cloudflare Access** | Cloudflare Access provides email-based SSO with no third-party SDK. Free tier covers 50 users; we use 1. The admin UI binds locally on 127.0.0.1, nginx fronts it, and CF Access enforces the email allowlist before any request reaches uvicorn. |
| Upstream source watcher | **Daily systemd timer hashes the source PDFs and pages** | OASA, STASY, and Hellenic Train change their published timetables a few times a year with no notice. A daily SHA-256 over each source URL means a maintainer is alerted within 24h of any change, and can re-verify the affected bands before the next snapshot ships. |
| Hosting | **Raspberry Pi at the founder's home + Cloudflare Tunnel** | Zero monthly cost. Acceptable for a free civic app at this scale. |
| License | **BSD 3-Clause** | Permissive, allows derivative works (e.g. a Thessaloniki fork), keeps the option to relicense open. |
| Crash and hang reporting | **Apple MetricKit + custom watchdog** | No third-party SDK, no user data leaves the device unless the user explicitly exports the diagnostics bundle. |

## Appendix B — The Schedule Correctness Refactor (June 2026)

A user reported that the Nikaia station screen showed a Line 3 train arriving "1 min" at 01:10 on a Friday morning, plus an airport-bound train at 01:12 — the same hour at which the airport branch is mandatorily closed by STASY. The bug exposed a structural mistake that had been accreted across two years of feature work.

### Root cause

The bundled seed contained pre-computed minute-by-minute departure timestamps generated by an early script that didn't model:

1. **The M3 split**. Line 3 city service runs to Doukissis Plakentias on a 4–9 min headway. The airport extension runs on a fixed 36-min interval and **closes at 23:00 every day**, no exceptions for Friday late nights or Saturday's 24/7 service. The seed treated M3 as a single line and emitted airport departures whenever city service was scheduled.
2. **Day-type rollover**. At 01:10 Friday morning we are in the *Friday late extension* (00:30 → 02:00, every 15 min on M2/M3). The seed was queried with `day_type='friday'` even at 01:10 — meaning the early Friday morning bands ran instead of Thursday-night's late_night tail. Departures shown at 01:10 had no relationship to actual service.
3. **The Saturday 24/7 rule**. The package master schedule documents continuous service from 22:00 Saturday through 05:30 Sunday. The seed reset Saturday to Sunday at midnight, dropping every overnight departure.

### The fix

Three layers, in order:

| Layer | Change |
|---|---|
| Data | The Athens transit reference package was imported into a SQLite DB on the Pi as **operator rules**: per-day-type operating windows, per-line frequency bands, holiday calendar with fixed-date and Easter-relative patterns, M3 city/airport modeled as separate scheduling lines (`M3` and `M3_AIR`) sharing one station list. Suburban A1–A4 added from the Hellenic Train PDFs (effective 2025-11-22). |
| API | Five new endpoints on `api-syrmos.peterdsp.dev`: `/api/schedules`, `/api/schedules/manifest` (small ETag-driven manifest with per-line content hashes), `/api/schedules/{lineId}`, `/api/holidays`, `/api/overrides`. A FastAPI admin UI behind Cloudflare Access lets the maintainer edit any frequency band; on save the static JSON snapshots regenerate atomically and the manifest version bumps so clients invalidate. |
| Client | A new `ComputeDeparturesFromBandsUseCase` in `core/domain` projects departures from frequency bands at the current Athens wall clock — when the current time is before 04:00 it *also* walks yesterday's bands so the late-night tail is found at 01:10. The iOS `ScheduleProjector` mirrors this in Swift. Both fall back to the seed when bundles aren't loaded yet. |

Every release ships a build-time snapshot of the live API as `seed/schedules-v2/`, hydrated at cold start before any network call. The app launches with correct data even on airplane mode, then catches up silently if a connection appears.

### Why a daily upstream watcher

OASA, STASY, and Hellenic Train change their published timetables a few times a year with no notice. A new `syrmos-watcher.timer` on the Pi hashes the 9 source URLs every morning at 04:30 Athens time and logs any diff to the scrape_log table. The admin UI surfaces a "Hellenic Train updated their A1-A2 PDF on 2026-06-19, please re-verify" without burning a single push notification or analytics event. The maintainer reviews the diff, edits the affected bands in the admin, and the next cold start of every installed app picks up the correction.

### What this enables

A wrong frequency band can be fixed server-side in 30 seconds and reach every installed app on its next cold start. The store-release path is no longer the bottleneck for schedule accuracy.

### What it does not enable

Real-time arrival predictions still require operator-published live data, which Athens does not currently publish for metro and tram. The projector is rule-based; it cannot model a stalled train or a strike. Per-date overrides (table `date_overrides`) exist for the admin to record those manually when known in advance.

## Appendix C — External Services Integration

The suburban station detail screens include a **Buy ticket on Hellenic Train** link button. This link is the only outbound integration with a commercial service in the entire app.

The design rule is strict and is enforced in the privacy policy: the button is a `Link` to `newtickets.hellenictrain.gr`. Tapping it leaves the Syrmos app and the entire purchase flow happens on Hellenic Train's site under Hellenic Train's terms. Syrmos does not pass any URL parameter that identifies the user, does not embed Hellenic Train's site in a webview, does not receive a redirect after purchase, and does not have any business or technical relationship with Hellenic Train. This is documented explicitly in both English and Greek privacy policies so users understand that any ticketing issue is between them and Hellenic Train.

The same rule will apply to every future external integration we ship — the OASA telematics page, the STASY service alerts page, intercity tickets, etc. Syrmos is a discovery app; ticketing is intentionally not our product.

## Appendix D — Live Arrivals Infrastructure (placeholder)

As of June 2026, Athens's public-transport operators do **not** publish a machine-readable real-time arrivals feed:

- **STASY** (metro, tram): a public site with announcements only, no JSON API for next-train predictions
- **OASA telematics**: an HTML page showing bus arrivals; no documented JSON; metro/tram coverage unclear
- **Hellenic Train**: publishes live train *positions* via an SSE feed at `railway.gov.gr` (Syrmos already consumes this), but no per-stop ETA stream

To avoid a multi-week refactor on the day any of those operators opens a feed, Syrmos ships a `LiveArrivalsProvider` interface in `core/domain` today with three no-op implementations (`StasyLiveArrivalsProvider`, `OasaLiveArrivalsProvider`, `HellenicTrainLiveArrivalsProvider`), routed by a `LiveArrivalsRouter` based on each provider's declared `lineIds()`. The use case prefers live data when a provider returns a non-null list and falls through to the rule-based projector when not.

Every provider currently returns `null`, so the rule-based projector remains the source of truth. When STASY (or anyone) publishes:

1. Fill in the body of the relevant provider — fetch, parse, return a list of `LiveArrival`
2. Wire any new HTTP client into the Koin module
3. Done. No other code change. The use case, the UI, the offline-first guarantee, the countdown timer, the bilingual strings — all untouched

The rule is enforced in the docstring: live data must never silently degrade offline-first. Network failures inside a provider must return `null`, not throw, so the projector covers gracefully.

### Why ship the infrastructure now if the feed doesn't exist

Two reasons:

1. **Forced-decoupling**. Whoever fills in the STASY provider in 2027 will not have access to my head. The interface — input/output, failure semantics, line-id routing — has to be unambiguous from the day it's written, not retro-fitted around a half-built implementation.
2. **Signalling**. A `LiveArrivalsProvider` in the public source code, with no implementation body, is the clearest possible message to maintainers and any STASY engineer looking at the repo: "here is the seam, please fill it in." It also reassures users that the architecture is ready when the operators are.

## Appendix E — Zoom-Aware Map Markers

Early reviewer feedback flagged the country-zoom map view as "rice grains" — the per-station smart-code SVGs (designed at 28×28 pt for street-level zoom) were rendered uniformly at every zoom level. At z<12, all 200 markers became indistinguishable white shapes.

The fix is the same pattern across all three platforms: pick the marker design from the current zoom level, not from a fixed image. Four buckets:

| Bucket | Web (Leaflet) zoom | iOS (region span Δ°) | Android (osmdroid zoom) | Marker |
|---|---|---|---|---|
| 0 — country | < 10 | > 0.6 | < 10 | Tiny line-colored dot, white outline. No glyph (illegible at that scale). |
| 1 — city | 10–11 | 0.18–0.6 | 10–11 | Colored teardrop pin with mode glyph (🚇 / 🚊 / 🚆 emoji on web, SF Symbol on iOS, painted bitmap on Android). |
| 2 — district | 12–13 | 0.05–0.18 | 12–13 | Same teardrop with a white inner cap. |
| 3 — street | ≥ 14 | < 0.05 | ≥ 14 | The original `station_smart_code` SVG (the design's intended use). |

Interchanges show 2–3 line-color ring badges in the corner of the pin. The same threshold table lives in three places (`web-map.js`, `MapView.swift`, `PlatformMapView.android.kt`) deliberately — KMP would have meant lifting the platform-specific map code into shared code, and the map abstraction in Compose Multiplatform isn't ready to absorb that yet. The thresholds are documented inline in each file so a future refactor can collapse them.

## Appendix F — Fares Endpoint and the No-Prices Rule

The app shows a Settings entry "Ticket prices (OASA)" that opens `oasa.gr/en/tickets/prices-of-products/` and a paragraph explaining contactless tap-and-go: Apple Pay, Google Wallet, or any contactless card works at metro and tram gates, plus on the validators inside trams and trains.

The corresponding endpoint, `/api/fares`, intentionally **does not store prices**. It stores:
- The canonical URL of OASA's fare page (in both EN and EL)
- The list of accepted contactless methods
- The list of locations where tap-and-go works
- Free-text operator notes in EN and EL

Prices change without notice. Mirroring them in the app would inevitably show stale prices to a user who's about to tap their card at a gate — exactly the moment when "wrong price" is most embarrassing. Linking out to the operator's page is the only safe behavior, and the privacy/responsibility framing (Appendix C) carries over: Syrmos doesn't store the answer, it routes the user to the authority that does.

The endpoint exists at all so the contactless rules can be edited without an app release. If OASA enables tap-and-go on a new vehicle type, the admin updates the `contactless_locations` JSON array and every app sees it on next cold start.

## Appendix G — Web Discoverability

A reviewer searched "syrmos" on Google and got a baby clothing shop instead of the website. Root cause: the page had a single `<title>` and `<meta description>`, no structured data, no PWA manifest, no JSON-LD, no canonical URL. Google had no signals beyond keyword density on a brand-new domain.

The fix is in the `<head>` of `index.html` shipped on 2026-06-12. Three layers:

1. **Discovery basics** — proper title with "Syrmos — Athens Metro, Tram & Suburban Departures", a 320-char description, canonical URL, `robots.txt`, `sitemap.xml`
2. **Social preview** — Open Graph + Twitter Card tags so a paste of the URL into Slack/LinkedIn/Discord renders a card with the right title, description, and favicon
3. **Knowledge graph** — A single JSON-LD `@graph` with five typed entities: a `WebApplication` for the website, two `MobileApplication` entries (one per store), a `Person` for the developer, and a `SoftwareSourceCode` pointing at the GitHub repo. The `MobileApplication` entries include `downloadUrl` pointing at App Store id `6753050019` and Play Store id `com.syrmos.android`. Google's Knowledge Graph builder uses exactly this shape to construct the right-hand panel that shows the app + store buttons + developer + repo

Plus: Apple's `apple-itunes-app` and Chrome's `google-play-app` smart banners; a `manifest.webmanifest` with `related_applications` (which Chromium-based browsers use to prompt "install the native app instead"); and two store badges (the official SVGs) in the header brand block linking to both stores.

Indexing depends on Search Console submission, sitemap submission, and a few authoritative backlinks — actions that only the developer can take. They're documented in the README. Realistic timeline: 2–6 weeks until "syrmos" returns the website + apps + repo in the first results.

## Appendix H — Product Roadmap

| Version | Features | Target |
|---|---|---|
| **1.0** | Current state. Metro, tram, Athens suburban. Live trains. STASY announcements. GPS Near me. Bilingual. | June - July 2026 |
| **1.0.x** | Bug-fix hotfixes responding to App Review and early user feedback. | July - August 2026 |
| **1.1** | iOS Home Screen widget. Wear OS / WatchOS companion. Improved accessibility. | Q4 2026 |
| **1.2** | Trip planner (point-to-point routing using the embedded line topology). | Q1 2027 |
| **1.3** | National InterCity rail (E85 corridor). Greek diaspora unlock. | Q2 2027 |
| **1.4** | Thessaloniki suburban (THESLAR corridor). | Q3 2027 |
| **1.5** | AI chat helper for natural-language schedule queries. | Q4 2027 |
| **2.0** | TBD. Either a redesign or a regional expansion (Patras, Heraklion). | 2028+ |

## Revision Log

| Date | Author | Change |
|---|---|---|
| 2026-06-10 | Petros Dhespollari | Initial version. Written following the Yale SOM / University of Potsdam / IIM Bangalore frameworks. Covers state of the project as of iOS build 1.0 (8) and Android build 5. To be updated whenever a material change occurs in scope, store status, user numbers, or strategic direction. |
| 2026-06-12 | Petros Dhespollari | Schedule correctness refactor (Appendix B). Added Pi-hosted API at `api-syrmos.peterdsp.dev` for live schedule updates, FastAPI admin behind Cloudflare Access, daily OASA 24mmm scraper, daily upstream-source watcher across STASY/OASA/Hellenic Train PDFs, build-time API snapshot bundled into every release, frequency-band projector in `core/domain` and Swift `ScheduleProjector`, suburban A1–A4 schedules from Hellenic Train PDFs effective 2025-11-22, Buy Ticket link to Hellenic Train (Appendix C) with explicit privacy disclosure. iOS bumped to 1.0.1 build 9. |
| 2026-06-12 (later) | Petros Dhespollari | Zoom-aware map markers across web/iOS/Android (Appendix E), `LiveArrivalsProvider` infrastructure with no-op STASY/OASA/Hellenic Train providers ready for operator feeds (Appendix D), `/api/fares` endpoint with OASA price-page link + contactless tap-and-go metadata (Appendix F), and `index.html` SEO overhaul with JSON-LD, OG, Twitter Card, PWA manifest, and store badges for the "Syrmos" Google search problem (Appendix G). |

---

*This case study is part of the public Syrmos repository at https://github.com/peterdsp/Syrmos. It is licensed under the same BSD 3-Clause terms as the source code, so other Greek civic-tech projects can adapt the analysis structure to their own contexts.*
