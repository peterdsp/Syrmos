---
name: syrmos-deploy
description: >-
  Ship Syrmos safely: deploy the web (GitHub Pages) and server (the Pi), update the bundled
  or DB seed, and cut beta/internal release builds. Use this skill whenever the task is to
  release or roll out: "deploy", "ship it", "push to the Pi", "update the seed", "re-seed",
  "cut a beta", "push a release", "TestFlight build", "release-ios/android", or verifying a
  deploy landed. It carries the exact deploy paths, the three-copy bundled-seed + manifest
  rule, the SSH-alias and surgical-deploy recipes, the release-tag behavior, and the safety
  boundaries (no history rewrite without approval, public store release stays human-gated).
  Verify the live result afterward with syrmos-test's runtime check.
---

# Syrmos deploy, seed, and release

Deploying is an outward-facing action. The user works autonomously and treats routine
commits, deploys, and seed edits as pre-approved, but the hard safety limits still hold:

- **No destructive git without explicit human approval**: no force-push, hard reset,
  rebase shortcut, hook bypass, or history rewrite. Rewriting master/tags re-triggers
  release workflows and breaks other clones; it is a separate, confirmed step.
- **No public store release.** Release tags ship to TestFlight/Play internal ONLY. App
  Store submit/review and Play production promotion are account-owner/MFA/legal actions.
- **No AI attribution, no em-dash** in commits or PR bodies (hard user rule).
- Always **verify the live result** after deploying; a push is not proof it works.

## Two kinds of seed

- **Bundled seed** (shipped in the app, read offline by the clients): three byte-identical
  copies plus a sha256 manifest. Editing `PL1.json` without rehashing its manifest entry in
  all three copies means clients never re-fetch. Full format + the LineStatus model are in
  `../syrmos-feature-loop/references/conventions.md`; `scripts/verify-bundles.py` checks the
  three copies match.
- **DB seed** (server-side on the Pi): line status/colour seeded by
  `scripts.seed_greek_corridors` and `scripts.seed_thessaloniki_airport_buses`. The nightly
  `syrmos-seed-daily.service` runs the corridors + Thessaloniki seeds but NOT the airport-bus
  seed, so an airport-bus change must be seeded manually or it never lands. Both are
  idempotent UPSERTs.

## Web deploy = GitHub Pages

`.github/workflows/pages.yml` auto-triggers on push to `master` touching `composeApp/**`,
`core/**`, `feature/**`, etc. Live at `https://syrmos.peterdsp.dev`, rebuild ~9-11 min,
`concurrency: cancel-in-progress` (a newer merge cancels an older run; the newest builds
HEAD so it still ships everything). The web reads lines/stations from the BUNDLED seed it
serves, not from `/api/lines`, so bundled-seed edits reach the web only after a Pages
rebuild. Verify after: load the live site (desktop; mobile UA redirects to the app page).

## Server deploy = the Pi

Use the SSH alias **`syrmos-pi`**, never the raw IP (the IP bypasses the key and fails with
publickey denial). NOPASSWD sudo works via the alias.

- Full tested path (heavy: rsync, pip, scrapers, generator, systemd, nginx; excludes
  data/db): `PI=syrmos-pi bash ops/syrmos-api/deploy.sh`.
- Surgical path for a few changed Python files (safer): `rsync -az` the file(s) to
  `/home/peterdsp/syrmos-api/...`, then on the Pi with `PIPELINE_ROOT` / `SYRMOS_DB_PATH` /
  `SYRMOS_API_OUT_DIR` exported, run the relevant seeds, then
  `.venv/bin/python -m syrmos_admin.generator`, then
  `sudo systemctl restart syrmos-admin.service`. After scraper/vehicle-status changes also
  `sudo systemctl restart syrmos-railway-sse.service`.
- The admin API listens on 127.0.0.1:8092 behind nginx; Ariadne is `POST /api/ariadne/chat`
  and wants a `messages` array, not `{message}`.
- Route geometry: `deploy.sh` must NOT `--delete` `assets/line-geometry/` (that wiped
  OSM-generated geometry for TM1/TM2/AL1/PS1/DK1). Regenerate ON THE PI with
  `.venv/bin/python -m scripts.refresh_osm_shapes` (Overpass is reachable from the Pi, not
  from the dev sandbox).

Verify after: `curl https://api-syrmos.peterdsp.dev/api/...` and watch the real values (the
dev sandbox can reach the live API directly).

## Release builds (beta / internal only)

Bump `CURRENT_PROJECT_VERSION` in the iOS pbxproj (all 8 build configs equal) and
`versionCode` in `androidApp/build.gradle.kts`, commit, then push a `v*` tag to fire
`release-ios.yml` + `release-android.yml`. The tag distributes to TestFlight + Play internal
only (no submit-for-review step). Each beta bumps both by 1.

- **TestFlight-only, no tag:** `gh workflow run release-ios.yml --ref master -f dry_run=false`
  (CI gate, epoch-stamps CFBundleVersion, uploads to TestFlight, ~15 min). A `v*` tag ALSO
  ships Android to Play internal, which needs a fresh `versionCode` first.
- **Dry run (safe, no upload):** `-f dry_run=true` (default) builds and validates the real
  signed artifacts (iOS archive + `altool --validate-app`, Android signed AAB) without
  publishing. Safe to run autonomously to re-confirm a release candidate.
- In release logs, `::error::` matches inside echoed script source are not real errors;
  trust `UPLOAD SUCCEEDED` / `Verified delivery`.

## Sandbox network limit

Overpass (`overpass-api.de`) is unreachable from the dev sandbox, so real route geometry
cannot be snapshotted here; run that on the Pi or a host with outbound internet.
