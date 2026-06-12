# Claude Handoff: Athens Icon Coverage

Goal: keep Athens station and vehicle icon coverage complete across API, iOS, Android, and web.

## Current State

- Live station target: 211 station ids from `https://api-syrmos.peterdsp.dev/api/lines`.
- Local source package now covers 211 of 211 stations.
- Source manifest now has 206 station icon entries. The count is lower than 211 because some station ids share names or use interchange overrides.
- Bundled seed `icons.json` files now contain 211 station mappings, 7 interchange mappings, and 26 vehicle mappings.

## Source Paths

- Repo: `/Users/p.dhespollari/git/personal/Syrmos`
- Primary icon package source: `/Users/p.dhespollari/git/personal/Syrmos/assets/athens-transit-package/icons`
- Desktop copy: `/Users/p.dhespollari/Desktop/athens_transit_icons_and_rules_package/athens_transit_t7_station_icons_updated`
- Package manifest: `/Users/p.dhespollari/git/personal/Syrmos/assets/athens-transit-package/icons/station_smart_codes/athens_station_smart_code_icons/manifest.json`
- Web generated SVGs: `/Users/p.dhespollari/git/personal/Syrmos/composeApp/src/wasmJsMain/resources/icons`
- iOS generated PNG assets: `/Users/p.dhespollari/git/personal/Syrmos/iosApp/iosApp/Assets.xcassets/stations`
- Bundled seed icons:
  - `/Users/p.dhespollari/git/personal/Syrmos/core/data/src/commonMain/composeResources/files/seed/schedules-v2/icons.json`
  - `/Users/p.dhespollari/git/personal/Syrmos/androidApp/src/androidMain/assets/files/seed/schedules-v2/icons.json`
  - `/Users/p.dhespollari/git/personal/Syrmos/iosApp/iosApp/Resources/seed-schedules-v2/icons.json`

## New Source Assets

- A2 station SVGs: `assets/athens-transit-package/icons/station_smart_codes/athens_station_smart_code_icons/stations_smart_codes/train/A2/`
- T6 added station SVGs:
  - `tram_t6_14_alexander_the_great_al.svg`
  - `tram_t6_15_aghia_paraskevi_ap.svg`
  - `tram_t6_16_medeas_mykalis_mm.svg`
  - `tram_t6_17_evangeliki_scholi_es.svg`
  - `tram_t6_18_achilleos_ac.svg`
  - `tram_t6_19_amfitheas_am.svg`
  - `tram_t6_20_panaghitsa_pa.svg`
  - `tram_t6_21_mousson_mo.svg`

## Code Changes To Know

- `ops/syrmos-api/scripts/import_icons.py` now supports optional `station_id` entries in the package manifest. This is needed because A2 has two `Acharnai Railway Center` station ids.
- `ops/syrmos-api/scripts/import_icons.py` still falls back to normalized station-name matching for existing package entries.
- `ops/syrmos-api/deploy.sh` now syncs the top-level icon package to the Pi at `~/syrmos-api/assets/icons`.
- `ops/syrmos-api/deploy.sh` runs `python -m scripts.import_icons --apply` before regenerating API JSON.
- `scripts/rebuild-icons-from-package.sh` accepts `PKG=/path/to/icons` so it can rebuild directly from the repo package.

## Rebuild Commands

From `/Users/p.dhespollari/git/personal/Syrmos`:

```bash
PKG=/Users/p.dhespollari/git/personal/Syrmos/assets/athens-transit-package/icons ./scripts/rebuild-icons-from-package.sh
python3 -m py_compile ops/syrmos-api/scripts/import_icons.py
bash -n ops/syrmos-api/deploy.sh
bash -n scripts/rebuild-icons-from-package.sh
```

To deploy the API after review:

```bash
./ops/syrmos-api/deploy.sh
```

After deploy, regenerate bundled seeds from the live API:

```bash
python3 scripts/snapshot-api-to-seed.py
```

## Validation Contract

Run a coverage check against live `/api/lines` and the local package. Expected result after this change:

```text
M1 24/24
M2 20/20
M3 27/27
T6 19/19
T7 43/43
A1 19/19
A2 13/13
A3 23/23
A4 23/23
TOTAL_MISSING 0
```

Do not reintroduce sequence-number icon matching. Sequence matching caused station labels to shift to neighboring stops on T6 and T7.
