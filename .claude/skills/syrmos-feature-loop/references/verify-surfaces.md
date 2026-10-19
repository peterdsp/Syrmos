# Runtime-verify each surface

Building green is CI-verified, not runtime-verified. For each affected surface, reach the
highest tier you can and state it plainly in your report. "I saw it behave" beats "it
compiles" every time, and the user has repeatedly insisted on the difference.

## iOS (simulator)

1. Boot exactly one device: `xcrun simctl shutdown all` then boot the target, and
   `open -a Simulator` so the window shows the live device (stale black windows are
   powered-off devices).
2. Build + install to it (see `build-and-test.md`), launch, and drive it.
3. Driving options, in order of preference:
   - The iOS Simulator MCP (`control attach`) when its grant registers.
   - Fallback when the grant will not register: `computer-use` on the Simulator window
     (`request_access ["Simulator"]`, `app_screenshot com.apple.iphonesimulator` exposes
     the UI as AX elements, `app_click element_index:N`, `app_drag` for drags). Coordinates
     are window-local from the last screenshot.
4. Capture a screenshot of the actual behavior.

Gotchas: station-marker hit targets are tiny; pinch-zoom in hard before tapping or you hit
a moving-train marker (vehicle sheet) instead of the station. SwiftUI `.draggable`/
`.dropDestination` is NOT drivable by synthesized input; `List` + `.onMove` is (prefer it
for reorderable lists). To seed UserDefaults, edit the container plist directly via
`simctl get_app_container <udid> <bundleid> data` + `plistlib`, then
`simctl spawn <udid> killall -9 cfprefsd` before relaunch (a plain `defaults write` targets
the wrong domain and the app will not see it).

## Android (emulator)

With the env set (`build-and-test.md`), install the debug APK to a booted emulator and
drive it. KMP/feature logic is also runnable as local unit tests, which is often the
faster honest check for pure-transform work. Instrumented CI needs KVM + a clean emulator
boot (see `android-tests.yml`).

## Web (local serve + browser)

- Fast logic check: call the engines directly. Serve
  `composeApp/src/wasmJsMain/resources/` with `python3 -m http.server`, open the in-app
  browser, and call `window.SyrmosDepartures.groupDepartures(...)`,
  `window.SyrmosAriadne.parse('...')`, etc. in the console.
- Caveat: a static serve loads the map and all `web-*.js`, but the Plan panel's From/To
  station dropdowns come back EMPTY (station data needs the full build/backend). So
  end-to-end "pick stations -> Find routes" is not verifiable from a static serve; verify
  web engines by calling `window.Syrmos*` directly instead.
- Full app serve with live `/api`: `stageWebRelease` +
  `scripts/prepare-pages-web-release.sh`, then serve `composeApp/build/github-pages`.
- Known limits on this machine: no Chromium/Chrome/Edge is installed (only Safari), so the
  Playwright MCP's `chrome` channel errors. The in-app browser CANNOT install Service
  Workers. Verify SW logic in a node harness with a mocked Cache API (see
  `web-tests/service-worker-behavior.test.js`); leave the final in-browser SW confirmation
  to the deployed Pages site. `index.html` has a mobile-UA redirect to a download page, so
  the SW and full map only run on DESKTOP web.

## Live API (curl)

This sandbox can reach `api-syrmos.peterdsp.dev` directly (HTTP 200), so verify live
endpoints by curling them and watching the real values, for example:

```bash
curl https://api-syrmos.peterdsp.dev/api/oasa-airport-buses
```

Ariadne chat is `POST /api/ariadne/chat` and wants a `messages` array (chat format), not
`{message}`. Only `overpass-api.de` is blocked, so real OSM route geometry cannot be
snapshotted here; that work needs a host with outbound internet (or run it on the Pi).
