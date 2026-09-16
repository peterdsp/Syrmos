# Syrmos product showcase — implementation prompt

Build a complete, polished product showcase for **Syrmos** at **https://syrmos.peterdsp.dev/product**. Make it memorable through strong art direction, authentic product demonstrations, and beautifully choreographed animation. The page should help someone understand Syrmos within five seconds and immediately open the web app or download the iOS or Android app.

Act as a senior product designer, motion designer, copywriter, and frontend engineer. Implement the finished page, inspect it in the browser, and refine it. Deliver working code and a local preview. Publication requires a separate instruction.

## 1. Inspect the actual product first

Work in `/Users/peterdsp/git/Syrmos`. Read applicable repository instructions and inspect the working tree. Preserve existing unrelated work, including native iOS changes.

The inspection when this prompt was prepared found that `/product` renders the transit app. Recheck that behavior. The new product page must be a dedicated page while `/` and existing application routes continue serving the working app.

The shipped web UI is static HTML, CSS, and JavaScript under `/Users/peterdsp/git/Syrmos/composeApp/src/wasmJsMain/resources`. Read the actual release pipeline instead of assuming this is a React or Compose-rendered website. Inspect:

- `/Users/peterdsp/git/Syrmos/composeApp/src/wasmJsMain/resources/index.html`
- `/Users/peterdsp/git/Syrmos/composeApp/src/wasmJsMain/resources/design-tokens.css`
- `/Users/peterdsp/git/Syrmos/composeApp/src/wasmJsMain/resources/sw.js`
- `/Users/peterdsp/git/Syrmos/scripts/prepare-pages-web-release.sh`
- `/Users/peterdsp/git/Syrmos/docs/adr/0001-web-url-model.md`
- `/Users/peterdsp/git/Syrmos/docs/press.html`
- `/Users/peterdsp/git/Syrmos/CHANGELOG.md`

Verify feature claims against current releases, source, and visible behavior. Repository plans and unfinished changes are not proof of released functionality. Identify which features are available on each platform before writing copy or choosing screenshots.

## 2. Use the real identity and destinations

Use these exact destinations, checking them before delivery:

- **Open web app:** https://syrmos.peterdsp.dev/
- **Download for iOS:** https://apps.apple.com/app/id6777650671
- **Get it on Android:** https://play.google.com/store/apps/details?id=com.syrmos.android

Use the existing production Syrmos logo and app icon. Preserve their artwork, proportions, and colors. Source assets from `/Users/peterdsp/git/Syrmos/docs/assets`, the web resources directory, and the current native asset catalogs. Keep the existing Apple and Google store badges intact and legible.

Real iOS captures exist at `/Users/peterdsp/git/Syrmos/docs/screenshots/ios_home.png`, `/Users/peterdsp/git/Syrmos/docs/screenshots/ios_map.png`, `/Users/peterdsp/git/Syrmos/docs/screenshots/ios_lines.png`, and `/Users/peterdsp/git/Syrmos/docs/screenshots/ios_settings.png`. Inspect them for age and accuracy. Capture current product screens where necessary and feasible. Capture Android and web independently; an iPhone screenshot inside an Android frame is not an Android demonstration.

Optimize faithful copies for the page. Preserve originals. Product screenshots must remain authentic: do not generate replacement app interfaces, alter displayed functionality, or invent screens. Simplified explanatory diagrams are welcome when visibly presented as illustrations. Preserve attribution for any map imagery.

## 3. Creative direction: a journey through Syrmos

Use a rail line as the visual thread through the page. It begins in the hero, connects the product-story chapters, and arrives at the final download section. Station nodes mark meaningful sections. Its movement should explain progress and connection, making the page unmistakably about transit.

Create a cinematic midnight-blue hero and alternate spacious warm-white product sections with one darker immersive section. Draw colors from the existing brand and transit tokens: Syrmos blue, metro green/red/blue, tram orange, and suburban purple. Preserve each line's meaning. Use restrained glass surfaces for floating controls, fine borders, subtle depth, and excellent typography.

Favor a few large, carefully composed demonstrations over a wall of interchangeable cards. Avoid stock commuters, invented awards, generic gradient blobs, endless marquees, floating emoji, and unrelated visual effects.

Use approximately 1280px maximum content width, responsive side gutters of 20/32/48px, and section spacing around 64–112px. Use fluid headline sizing around 44–96px and comfortable 16–20px body text. Layout must expand for translations and zoom. These are design starting points: refine them against screenshots and actual content.

## 4. Build the page in this order

### A. Compact navigation

Show the authentic logo and wordmark, anchors for Features, How it works, and Download, plus a clear Open web app action. A Get the app action can scroll to the three platform options. Make the sticky header subtly more opaque after scrolling, with an opaque fallback. On mobile, preserve a visible primary action and provide an accessible compact menu. The header must never obscure anchored headings.

### B. An exceptional hero

Start with this copy and refine only where evidence or layout warrants it:

**Your next connection. Beautifully clear.**

**Explore routes, check departures, and keep essential travel information close—with Syrmos for iOS, Android, and web.**

Place all three platform links directly beneath the copy. Present Open web app as an immediately usable action and the two official store badges alongside it. All three remain available on every device; avoid forced redirects or mobile download gates.

Create a large product composition with a real phone screen, a real desktop web preview, and a thin animated rail diagram that visually connects them. Use believable device proportions and restrained perspective. Keep at least one screen front-facing and readable. Mobile gets a carefully recomposed single-screen lead visual with the web preview lower down.

Animate the rail path drawing, then reveal the product screens and one useful detail such as a departure panel. Keep copy and links available immediately. The hero must look complete before animation runs and with JavaScript disabled.

### C. Show the everyday value

Build three substantial chapters with real product imagery and concise benefit-led copy:

1. **See what’s next.** Demonstrate station selection, direction, and departure information. Preserve distinctions between live, scheduled, estimated, and previously received information.
2. **See the bigger picture.** Demonstrate the network map, stations, and supported connections. Use real supported routes and labels. An illustrated map must not imply geographic precision or universal live tracking.
3. **Keep the essentials with you.** Demonstrate access to stored schedules and station information when connectivity drops. Explain that live updates need connectivity and offline availability depends on stored data and platform behavior.

Use a sticky product frame beside three scrolling story steps on wide screens. As a chapter becomes active, crossfade the appropriate real capture and highlight its matching station node. Keep natural document scrolling. On mobile, use normal stacked chapters with their own images and short transitions.

### D. An interactive product explorer

Add a clearly labeled feature explorer for Departures, Map, and any additional feature verified in the current release, such as fares or Ariadne. Selecting a tab updates the image, caption, and platform availability together. Support keyboard controls and touch. Do not auto-rotate tabs.

If Ariadne is featured, use the current `ariadne-mark.png` and `ariadne-lockup.png` artwork from `/Users/peterdsp/git/Syrmos/composeApp/src/wasmJsMain/resources` and a verified example question and response. Present prerecorded or scripted content as an example. Keep model downloads, permission requests, and production assistant initialization out of the marketing page. Describe the supported transit assistance without promising perfect answers or unrestricted general AI.

A separate platform selector may show iOS, Android, and Web captures if all three are authentic and available. Never imply identical capabilities from a shared visual. Include watchOS, widgets, journey guidance, or advanced planning only when the displayed feature is verified as released on the named platform.

### E. Coverage and practical details

Show the supported regions—Athens, Thessaloniki, Patras, and national rail connections—after verifying current coverage. Use a compact diagram or region selector with factual examples. Avoid hardcoded station counts, operating claims about unopened or suspended lines, and blanket claims of complete Greek transport coverage.

Briefly explain supported languages, fare information, and privacy where verified. Ticket purchase links open the relevant operator; do not imply that Syrmos sells tickets. Keep qualifications next to the relevant feature, in readable language.

### F. Final download destination

End the visual rail line at a generous section headed **Your journey starts here.** Present three polished options: iOS, Android, and Web, each with the verified destination and one accurate sentence about using it. Repeat the authentic store badges. Keep the web choice equally easy to find on mobile.

Follow with a small FAQ covering offline use, supported regions, live versus scheduled information, and ticket purchases. Use native disclosure behavior. Finish with verified Privacy, developer, and press links plus an accurate operator non-affiliation statement.

## 5. Make the motion specific and purposeful

Implement these sequences, then tune them in the browser:

- Hero rail-path reveal: approximately 1200–1800ms, once, with a smooth deceleration. Screen entrance: 650–900ms, 16–28px travel, restrained stagger.
- Section reveals: 400–650ms, 12–20px travel, once as content enters view. Content is visible by default if scripting fails.
- Desktop story transitions: approximately 300ms crossfades with at most 12px translation; no enormous pinning distances or scroll hijacking.
- Explorer transitions: 200–300ms, retaining frame dimensions to prevent layout shifts.
- Buttons: 150–200ms color/shadow changes; at most 2px hover lift and a subtle pressed state. Match hover information with focus and touch behavior.
- Optional fine-pointer depth: maximum 3 degrees of hero tilt, disabled for touch and reduced motion. Never move controls away from the pointer.
- Ambient train movement, if used, is clearly decorative or labeled as a demonstration. Any continuing animation has an accessible Pause animations control and stops offscreen or when the tab is hidden.

Prefer CSS, SVG, IntersectionObserver, and the Web Animations API. Justify any additional motion dependency and isolate it to this page. Avoid loading a new application framework for a marketing page. Animate transforms and opacity where possible; avoid expensive continuous blur and layout work.

Honor `prefers-reduced-motion`: remove parallax, tilt, path travel, continuous motion, and sticky choreography; show the complete story in a stable reading order. Information and controls must remain equally usable.

## 6. Integrate the route safely

Create a standalone product entry document and isolated assets, naturally under `/Users/peterdsp/git/Syrmos/composeApp/src/wasmJsMain/resources/product/`. Adjust the actual staging pipeline so the built release contains `product/index.html`. Do not add `product` to the loop that copies the app shell into workspace routes.

Both `/product` and `/product/` must reach the product page; a normal redirect to the canonical trailing-slash path is acceptable. Direct navigation and refresh must end in HTTP 200, not a branded 404. Use root-absolute asset URLs and avoid a global `<base>` element.

Inspect service-worker navigation handling carefully. At prompt preparation, `sw.js` cached every successful HTML navigation under `/index.html`. A standalone page could therefore replace the cached transit-app shell. Make the smallest necessary route-aware change so application documents and standalone product/privacy documents use appropriate separate caching and offline fallbacks. Handle an existing worker controlling the first product visit after an update, as well as fresh installations. Do not clear unrelated user data or disable offline support to solve this.

Scope product CSS and scripts. Loading the product page must not boot the transit app, fetch its full schedule dataset, start tracking, request location, download assistant models, or load large mapping dependencies. Add a discreet Product/About link in the app's appropriate existing menu so users can discover the page.

## 7. Finish accessibility, performance, and discoverability

Use semantic landmarks, one clear H1, descriptive links, visible keyboard focus, appropriate image alternatives, and at least 44px touch targets. Ensure readable contrast, 200% zoom, narrow layouts, keyboard navigation, and functional no-JavaScript content. Decorative rail lines stay outside the accessibility tree.

If a language selector is included, translate every visible label, FAQ answer, accessible name, and page description for each exposed language; persist the choice and update document language. Avoid a partially translated experience. Reuse verified current language conventions without changing the app's behavior unexpectedly.

Serve responsive optimized images with explicit dimensions. Prioritize the hero image and lazy-load lower imagery. Self-host any added fonts, check Greek/Albanian character coverage, and retain strong system fallbacks. Target initial compressed transfer below approximately 1MB and product-specific JavaScript below 80KB compressed. Avoid autoplay video and heavyweight 3D rendering. Document any justified exceptions.

Add a product-specific title, description, canonical URL, social metadata, and a locally produced sharing image using authentic branding and screenshots. Add the canonical product URL to the sitemap. Structured data must contain only supported claims. Do not add fabricated ratings, review counts, customers, testimonials, analytics, or trackers.

## 8. Verify and deliver

Run the relevant existing web tests from `/Users/peterdsp/git/Syrmos/web-tests` with `npm test`. Add targeted regression checks for route output and service-worker document isolation where behavior changes; do not write tests that merely repeat CSS values.

Inspect the staged site in a real browser at widths around 390, 768, 1024, and 1440px, plus a 320px overflow check. Check Safari/WebKit and Chromium where available. Verify every action, anchor, platform destination, FAQ, menu, keyboard flow, and reduced-motion presentation. Confirm no unexpected overflow, broken assets, console errors, or layout shifts.

Explicitly verify this sequence: load the app online, visit the product page, go offline, reopen the app, and confirm the app remains the app. Repeat for fresh caches and an existing installed worker, including the privacy page. Verify application deep links, reload, Back/Forward, and mobile web access remain functional.

Measure mobile performance and accessibility, aiming for Lighthouse scores of 90+ where the environment supports reliable measurement. Treat these as targets, not claims without results. State any checks unavailable in the environment.

Review full-page desktop and mobile screenshots and refine visual hierarchy, spacing, crop quality, contrast, and motion. Deliver the implemented files, a local preview, representative screenshots, verified feature/asset sources, and a concise validation report. Clearly distinguish local completion from public deployment.

The finished page should feel carefully designed for Syrmos: striking at first glance, useful while scrolling, honest about the product, and effortless to act on.
