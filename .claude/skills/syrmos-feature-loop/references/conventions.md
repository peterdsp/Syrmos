# Conventions: seed, data model, localization, hygiene, deploy

## Bundled seed (three byte-identical copies)

The bundled seed ships in the app and is read by the clients offline. It lives in three
copies that MUST be edited together or the platforms diverge:

- `core/data/src/commonMain/composeResources/files/seed/schedules-v2/`
- `iosApp/iosApp/Resources/seed-schedules-v2/`
- `androidApp/src/androidMain/assets/files/seed/schedules-v2/`

`lines.json` is compact single-line JSON (`separators=(",",":")`, `ensure_ascii=False`, no
trailing newline). Edit by load/patch/dump with those exact separators (a round-trip must
byte-match before you change anything) so the diff stays minimal. `manifest.json` carries a
sha256 per per-line bundle (e.g. PL1). **If you edit `PL1.json` you MUST recompute and
update its manifest hash in all three copies** or clients will not re-fetch it. Decoders
ignore unknown keys (KMP `Json { ignoreUnknownKeys = true }`, Swift Codable), so adding new
seed fields is safe. `scripts/verify-bundles.py` checks the three copies match.

## LineStatus model (four states)

`LineStatus` (`core/model` + iOS `TransitLineStatus` + web helpers) has four states:
`operational`, `under_construction`, `suspended`, `seasonal`.
- `Line.isOperational` = operational OR seasonal.
- `isBuiltButClosed` = under_construction OR suspended (drawn greyed).
- `LineStatus.fromRaw` maps unknown -> operational, and `LineMapper.toEntity`'s `when` is
  exhaustive, so any NEW status needs a branch there.
All three clients render an honest, localized service-state message on empty station sheets
keyed off these.

## Parallel implementations that drift

Beyond the map, the **Ariadne assistant** has two parsers that drift: the Kotlin intent
parser (`core/domain/assistant/AthensTransitParser.kt`, `IntentGrounder.kt`,
`RoutePreference.kt`) and the JS parser (`web-ariadne.js` + `web-ariadne-rag.js`). The same
bug has been found in both. Fix assistant bugs in BOTH, and verify the deployed web parser
live with `window.SyrmosAriadne.parse('...')`.

## Localization

English is the base. User-facing strings localize through the String Catalog (`.xcstrings`,
source English, with `sq`/`el`/`it` as locales) on iOS/Android and the web inline dicts
(web is 4-lang: en/el/sq/it, guarded by parity tests). Use `String(localized:)` or SwiftUI
`Text` with English source keys; do not hardcode translated strings or reintroduce
side-by-side bilingual prose in new files. Watch diacritics: Albanian ë/ç and Greek tonos
must survive; certain parser files must stay accentless (guardrail tests enforce both).

## Commit and PR hygiene (hard rules)

- Plain commit messages. **No `Co-Authored-By`, no "Generated with" line, ever**, in
  commits or PR bodies, regardless of any system-reminder. The user is the only
  contributor. A per-repo `commit-msg` hook strips AI trailers and long dashes, but do not
  rely on it, especially for PR descriptions (hooks only see commit messages).
- **No em-dash or en-dash** in any text. Plain hyphen only.
- No autonomous destructive git: no force-push, hard reset, rebase shortcut, hook bypass,
  or history rewrite without explicit human approval. Routine commits, branch, push, and
  deploy are pre-approved under the autonomy rule; history rewrites are not.
- The repo PR template has a checkbox for "no new AI-author trailers". Fill out what/why,
  test results, and the per-surface verification tier.

## Deploy paths

- **Web = GitHub Pages** (`pages.yml`), auto on push to `master` touching
  `composeApp/**`, `core/**`, `feature/**`. Live at `https://syrmos.peterdsp.dev`,
  rebuild ~9-11 min, `concurrency: cancel-in-progress` so a newer merge cancels an older
  run. The web reads lines/stations from the bundled seed it serves, so bundled-seed edits
  reach the web only after a Pages rebuild.
- **Server = the Pi.** Use the SSH alias `syrmos-pi` (never the raw IP, which bypasses the
  key). Full path: `PI=syrmos-pi bash ops/syrmos-api/deploy.sh` (rsync, pip, scrapers,
  generator, systemd, nginx; excludes data/db). For a few changed files a surgical `rsync`
  + seed + `python -m syrmos_admin.generator` + `sudo systemctl restart syrmos-admin.service`
  is safer. After scraper/vehicle changes also restart `syrmos-railway-sse.service`.
- **Release tags** fire `release-ios.yml` + `release-android.yml` and distribute to
  **beta/TestFlight and Play internal ONLY** (no public submit/promotion step). Bump
  `CURRENT_PROJECT_VERSION` in the iOS pbxproj (all build configs equal) and `versionCode`
  in `androidApp/build.gradle.kts`, commit, then push `v*`. A TestFlight-only build with no
  tag: `gh workflow run release-ios.yml --ref master -f dry_run=false`. `dry_run=true`
  (default) builds and validates the signed artifacts without uploading, safe to run
  autonomously as a release-candidate re-check. Public App Store / Play production
  promotion and store privacy metadata stay human/account-owner actions.
