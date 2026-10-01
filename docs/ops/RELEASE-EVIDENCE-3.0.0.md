# Syrmos 3.0.0 release evidence

The maintained record of what was built, verified, and distributed for the 3.0.0
train. Per-case acceptance results live in
[docs/qa/RELEASE-3.0.0-ACCEPTANCE-LEDGER.md](../qa/RELEASE-3.0.0-ACCEPTANCE-LEDGER.md);
screenshots live under [docs/screenshots/release-3.0.0/](../screenshots/release-3.0.0/)
with a `manifest.tsv` recording scenario, device, locale, appearance, clock and
data mode for each capture.

## 3.0.0-beta.8

| Item | Value |
| --- | --- |
| Source commit | `2f1ebb89` (merge of PR #241 into `master`) |
| Release tag | `v3.0.0-beta.8` |
| Marketing version | 3.0.0 |
| iOS build number | 1790754983 (epoch-stamped by the release workflow) |
| iOS delivery | TestFlight, `UPLOAD SUCCEEDED`, Delivery UUID `a3088aac-2be4-4381-ae1a-44fc704b6790` |
| Android | versionName 3.0.0, versionCode 231, Google Play **internal** track, edit `01500641537142979236` |
| Web | GitHub Pages, deployed from the same merge commit, https://syrmos.peterdsp.dev/ |

TestFlight and Play internal are **internal distribution channels**, not public
App Store or Play production releases.

### Burned Android version codes

105, 106, 109-138, 200-231. Never reusable. The next release must use 232 or
higher.

### Production web checks performed

- Every workspace deep link returns 200: `/`, `/now/`, `/plan/`, `/explore/`,
  `/departures/`, `/station/`, `/line/`, `/product/`, `/privacy/`, `/get-app/`.
- The station-complex registry and the board module are served.
- The removed on-device model runtime returns 404.
- The service worker is at `v4`, so an already installed worker replaces the
  cached shell rather than serving the previous single-direction card.
- With the network cut and the page reloaded, the service worker serves the app
  and the Athens board still renders every supported direction from the bundled
  seed.

## Release engineering notes

- The iOS build number is stamped from the Unix epoch at build time, so it is
  unique and increasing without a manual bump. Reading a static
  `CURRENT_PROJECT_VERSION` once caused a rejected upload.
- The Android `versionCode` is static in `androidApp/build.gradle.kts` and MUST
  be raised past the highest value Play has accepted, not merely past the last
  value in the file.
- The TestFlight step decides delivery from the altool log: it fails on a
  definitive failure marker AND requires a positive success marker. A bare
  `ERROR:` match is deliberately not a failure condition, because altool's
  content delivery prints transient service errors (for example
  `received status code 500`) and then succeeds; that false negative failed
  `v3.0.0-beta.7` immediately above `VERIFY SUCCEEDED with no errors`.
- Both release workflows run on a `v*` tag and also offer a manual dispatch whose
  `dry_run` defaults to true. Use ONE trigger: a tag push performs both uploads,
  so a manual dispatch afterwards would duplicate them.
