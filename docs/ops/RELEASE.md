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
| iOS (TestFlight) | tag `v*` | `.github/workflows/release-ios.yml` | Pending: needs the Loupe + agent-device invocation, plus the secrets below. |

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

## iOS secrets (once the workflow lands)

- `APP_STORE_CONNECT_API_KEY_ID`, `APP_STORE_CONNECT_ISSUER_ID`,
  `APP_STORE_CONNECT_API_KEY_P8` — App Store Connect API key for TestFlight upload.
- Signing: either an App Store Connect API key with "cloud managed" signing, or a
  distribution certificate + provisioning profile (base64) plus a keychain password.

Open item: the repo standard is to run Apple platform CI through **Loupe +
agent-device**. Those are not yet wired into any workflow, and the exact
invocation (custom action, self-hosted runner, or CLI) needs to be confirmed
before `release-ios.yml` can be written correctly rather than guessed. Once that
is known, the iOS job will: check out, resolve signing, archive the
`Syrmos - Athens Rail Times` scheme, export an App Store `.ipa`, and upload to
TestFlight via the App Store Connect API key.

## Verifying a release

- Web: the `Pages` run goes green and `https://syrmos.peterdsp.dev` serves the new build.
- Android: the build appears on the Play Console internal track.
- iOS: the build appears in App Store Connect / TestFlight.
