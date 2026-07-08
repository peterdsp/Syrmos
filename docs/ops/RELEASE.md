# Release pipeline

Goal: cutting a release should mean "shipped to users", not "merged to GitHub".
A release is driven by a version tag (`vMAJOR.MINOR.PATCH`, for example `v1.2.1`).

## The flow

1. Bump `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION` (iOS) and
   `versionName` / `versionCode` (Android) on the release branch, land the PR.
2. Tag the merge commit and push the tag:
   ```bash
   git tag -a v1.2.1 -m "Syrmos 1.2.1"
   git push origin v1.2.1
   ```
3. Pushing the tag triggers the release workflows below.

## Per-platform status

| Platform | Trigger | Workflow | State |
|---|---|---|---|
| Web (GitHub Pages / Fastly) | push to `master` touching app code | `.github/workflows/pages.yml` | Automated and live. Nothing to add. |
| Android (Play internal track) | tag `v*` | `.github/workflows/release-android.yml` | Ready; needs the secrets below. |
| iOS (TestFlight) | tag `v*` | `.github/workflows/release-ios.yml` | Ready; needs the three ASC-key secrets below. |

Note: the Pi (`api-syrmos.peterdsp.dev`) only hosts the live-train SSE proxy. The
web app itself is served from GitHub Pages, so there is no separate web deploy
step to run by hand.

## Android secrets (GitHub repo settings, Actions secrets)

The `release` signing config in `androidApp/build.gradle.kts` reads from
`local.properties`; the workflow writes those from secrets at build time.

- `ANDROID_KEYSTORE_BASE64` — `base64 -i syrmos-release.keystore` output.
- `RELEASE_STORE_PASSWORD` — keystore store password.
- `RELEASE_KEY_ALIAS` — signing key alias (build default: `syrmos`).
- `RELEASE_KEY_PASSWORD` — signing key password.
- `PLAY_SERVICE_ACCOUNT_JSON` — Play Console service-account JSON with the
  "release to testing/production" permission for `com.syrmos.android`.

First upload of a brand-new package to Play must be done manually in the console
(Google requires the first bundle by hand); the workflow handles every release
after that. Default track is `internal`; promote in the console or change `track`.

## iOS secrets (GitHub repo settings, Actions secrets)

`release-ios.yml` mirrors the klipa App Store Connect job: one App Store Connect
API key drives both automatic signing (`xcodebuild -allowProvisioningUpdates`)
and the TestFlight upload (`xcrun altool --upload-app`). Same secret names as
klipa, so the same key is reused:

- `ASC_KEY_ID` — App Store Connect API key id (Users and Access, Keys).
- `ASC_ISSUER_ID` — the issuer id shown above the keys list.
- `ASC_API_KEY_P8_BASE64` — base64 of the `AuthKey_<id>.p8`.

Team id `YTS4KJBX3P` and `app-store-connect` export live in
`scripts/export-options.plist`, so no separate cert/profile secrets are needed.
The key must have the App Manager role so automatic signing can create/fetch the
distribution profiles for the app, watch app, watch complication, and widget
extension. The app record must already exist in App Store Connect (bundle ids
under the Syrmos app). If the ASC key is absent the job skips cleanly, so a tag
still ships web + Android.

## Verifying a release

- Web: the `Pages` run goes green and `https://syrmos.peterdsp.dev` serves the new build.
- Android: the build appears on the Play Console internal track.
- iOS: the build appears in App Store Connect / TestFlight.
