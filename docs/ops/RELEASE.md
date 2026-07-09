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

`release-ios.yml` uses least-privilege manual signing (klipa's cert-import
pattern). The App Store Connect API key stays **App Manager** and is used only
for the upload; signing uses an imported Apple Distribution certificate and
explicit App Store profiles, so no Admin/cloud-signing permission is needed.

Signing:
- `IOS_DIST_CERT_P12_BASE64` — base64 of your Apple Distribution certificate
  exported from Keychain Access as a `.p12` (includes the private key).
- `IOS_DIST_CERT_P12_PASSWORD` — the password you set on that `.p12` export.
- `CI_KEYCHAIN_PASSWORD` — any string; password for the throwaway CI keychain.
- Four App Store provisioning profiles (base64 of each `.mobileprovision`),
  created on the developer portal and named to match `scripts/export-options.plist`:
  - `IOS_PROFILE_APP_BASE64` — "Syrmos AppStore" (`com.syrmosApp.ios`)
  - `IOS_PROFILE_WATCH_BASE64` — "Syrmos Watch AppStore" (`...watchkitapp`)
  - `IOS_PROFILE_COMPLICATION_BASE64` — "Syrmos Complication AppStore" (`...watchkitapp.complications`)
  - `IOS_PROFILE_WIDGET_BASE64` — "Syrmos Widget AppStore" (`...SyrmosWidget`)

Upload (App Manager key is enough):
- `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_API_KEY_P8_BASE64`.

Team id `YTS4KJBX3P` is in `scripts/export-options.plist`. The App Store app
record and all four bundle ids must exist in App Store Connect / the developer
portal. If the distribution cert secret is absent the job skips cleanly, so a
tag still ships web + Android.

## Verifying a release

- Web: the `Pages` run goes green and `https://syrmos.peterdsp.dev` serves the new build.
- Android: the build appears on the Play Console internal track.
- iOS: the build appears in App Store Connect / TestFlight.
