# Syrmos Verified Release Truth & Reconciliation Ledger

> **Audit note (2026-10-13):** This ledger's historical tables below predate
> `5063b5ea` / `v3.0.0-beta.13`. Do not use their older HEAD, tag, or store
> claims as current beta.13 evidence until the beta.13 workflow, artifact,
> processing, installation, and runtime checks are recorded separately.

> Repository: `Syrmos` (`/Users/peterdsp/git/Syrmos`)  
> Generated: October 2026 (Audit against branch `master` at commit `42689f42`)  
> Audited across: Git tags (52 tags), GitHub Releases API (`gh release list`), `CHANGELOG.md`, `project.pbxproj`, `build.gradle.kts`, `RELEASE-EVIDENCE-3.0.0.md`, and CI workflows.

---

## 1. Executive Summary & Core Discrepancies

1. **GitHub Releases vs Git Tags Divergence:**
   - There are **52 git tags** in the repository (from `v1.2.1` up to `v3.0.0-beta.12`).
   - However, **only 6 GitHub Releases** exist on GitHub:
     - `v1.2.1` (Syrmos 1.2.1)
     - `v1.4.0` (Syrmos 1.4.0 — **erroneously marked as "Latest" release on GitHub**)
     - `v2.0.0-beta.5` (Syrmos 2.0.0 Beta 5, Pre-release)
     - `v2.0.0-beta.6` (Syrmos 2.0.0 Beta 6, Pre-release)
     - `v2.0.0-beta.7` (Syrmos 2.0.0 beta 7, Pre-release)
     - `v2.0.0-beta.8` (Syrmos 2.0.0 beta 8, Pre-release)
   - **Critical Mismatch:** The production release `v2.0.0` (tagged at commit `d4f92f37` on 2026-09-02) was **never created on GitHub Releases**. As a result, GitHub still displays `v1.4.0` (from August 2026) as the "Latest" release to all public visitors!
   - **3.0.0 Beta Train Absence:** None of the 12 tags on the 3.0.0 train (`v3.0.0-beta.1` through `v3.0.0-beta.12`) have corresponding GitHub releases.

2. **Current Production State (Verified):**
   - **iOS Production:** Version `2.0.0`, build `138` (published to App Store).
   - **Android Production:** Version `2.0.0`, versionCode `223` (published to Google Play).
   - **Web Production:** Continuous rolling deploy from `master` to GitHub Pages at `https://syrmos.peterdsp.dev`.

3. **Current Active Development State (3.0.0 Train):**
   - **HEAD Commit:** `42689f42` ("Add suburban ticket sheet to station route rows").
   - **Latest Tag:** `v3.0.0-beta.12` at commit `1772cf1a` (2026-10-11).
   - **iOS Marketing Version:** `3.0.0` (in `project.pbxproj` and `project.yml`). Build numbers are auto-stamped using Unix epoch timestamps during CI builds to avoid collision.
   - **Android Version:** `versionName = "3.0.0"`, `versionCode = 233` (in `androidApp/build.gradle.kts`).
   - **Distribution Channel:** `v3.0.0-beta.8` (iOS epoch build `1790754983`, Android versionCode `231`) successfully uploaded and verified in **TestFlight** and **Google Play Internal Testing**.

---

## 2. Release Status Taxonomy & State Definitions

To prevent ambiguity between local git state and actual store availability, every record uses these explicit status definitions:

- **implemented:** Code written and present in local working tree.
- **committed:** Captured in a Git commit on a local branch.
- **merged:** Merged into the default branch (`master`).
- **built:** Successfully compiled into a binary package (`.ipa`, `.aab`, `.apk`, Wasm bundle).
- **uploaded:** Uploaded to a distribution backend (App Store Connect / Play Console API).
- **available in TestFlight:** Processed and approved by Apple for internal/external TestFlight testers.
- **available in Google Play:** Processed and available on the Google Play internal test track.
- **store-reviewed:** Passed App Store Review or Google Play Store Review.
- **published:** Publicly live and downloadable on the Apple App Store or Google Play Store.
- **installed:** Installed on test hardware / simulator.
- **runtime-verified:** Tested interactively at runtime on hardware/simulator/browser.

---

## 3. Verified Repository Release Ledger

The following ledger cross-references every version marker, git tag, release artifact, and external distribution status.

| Version | Build / Code | Commit SHA | Git Tag | Platform | Channel | Status | Date | Evidence Source | Mismatch / Blocker |
|---|---|---|---|---|---|---|---|---|---|
| **1.2.1** | iOS 103 / And 103 | `ef19feb7` | `v1.2.1` | iOS, Android, Web | GitHub, TestFlight, Play | **published** | 2026-07-08 | Git tag, GitHub Release, CHANGELOG | None. GitHub release exists. |
| **1.2.2** | iOS 104 / And 104 | `ec274642` | `v1.2.2` | iOS, Android | Internal | **merged** | 2026-07-10 | Git tag, CHANGELOG | Missing GitHub Release. |
| **1.2.3** | iOS 105 / And 107 | `d0291339` | `v1.2.3` | iOS, Android | Internal | **merged** | 2026-07-19 | Git tag | Missing GitHub Release. And 105 burned/rejected. |
| **1.2.4** | iOS 106 / And 108 | `8a32c226` | `v1.2.4` | iOS, Android | Internal | **merged** | 2026-07-20 | Git tag | Missing GitHub Release. |
| **1.2.5** | iOS 107 / And 110 | `3c46ffd6` | `v1.2.5` | iOS, Android | Internal | **merged** | 2026-07-21 | Git tag, CHANGELOG | Missing GitHub Release. |
| **1.2.6** | iOS 108 / And 111 | `c32a11e8` | `v1.2.6` | iOS, Android | Internal | **merged** | 2026-07-21 | Git tag, CHANGELOG | Missing GitHub Release. |
| **1.2.7** | iOS 109 / And 112 | `99ca5f6e` | `v1.2.7` | iOS, Android | Internal | **merged** | 2026-07-21 | Git tag, CHANGELOG | Missing GitHub Release. |
| **1.2.9** | iOS 110 / And 113 | `8cbd5c36` | `v1.2.9` | iOS, Android, Web | Internal | **merged** | 2026-07-21 | Git tag | Missing GitHub Release. |
| **1.2.10** | iOS 111 / And 114 | `b23a4bb0` | `v1.2.10` | iOS, Android | Internal | **merged** | 2026-07-26 | Git tag, CHANGELOG | Missing GitHub Release. |
| **1.2.11** | iOS 112 / And 115 | `045b9443` | `v1.2.11` | iOS, Android | Internal | **merged** | 2026-07-26 | Git tag, CHANGELOG | Missing GitHub Release. |
| **1.2.12** | iOS 113 / And 116 | `84608df2` | `v1.2.12` | iOS, Android | Internal | **merged** | 2026-07-27 | Git tag | Missing GitHub Release. |
| **1.2.13** | iOS 114 / And 117 | `e3014843` | `v1.2.13` | iOS, Android | Internal | **merged** | 2026-07-27 | Git tag | Missing GitHub Release. |
| **1.2.14** | iOS 115 / And 118 | `61041d9e` | `v1.2.14` | iOS, Android, Web | Internal | **merged** | 2026-07-27 | Git tag, CHANGELOG | Missing GitHub Release. |
| **1.3.0** | iOS 116 / And 119 | `c4caeefd` | `v1.3.0` | iOS, Android | Internal | **merged** | 2026-07-27 | Git tag, CHANGELOG | Missing GitHub Release. |
| **1.4.0** | iOS 116 / And 120 | `62cf568f` | `v1.4.0` | iOS, Android, Web | GitHub Public | **published** | 2026-08-03 | Git tag, GitHub Release, CHANGELOG | **Mismatch:** Marked "Latest" on GitHub but superseded by 2.0.0. |
| **2.0.0-beta.1** | iOS 116 / And 201 | `49d3f4ca` | `v2.0.0-beta.1` | Cross-platform | Internal | **merged** | 2026-08-04 | Git tag | Missing GitHub Release. |
| **2.0.0-beta.2** | iOS 116 / And 201 | `fbcb6703` | `v2.0.0-beta.2` | Cross-platform | Internal | **merged** | 2026-08-05 | Git tag | Missing GitHub Release. |
| **2.0.0-beta.3** | iOS 117 / And 202 | `3bfa4f0e` | `v2.0.0-beta.3` | Cross-platform | Internal | **merged** | 2026-08-05 | Git tag | Missing GitHub Release. |
| **2.0.0-beta.4** | iOS 118 / And 203 | `3f4c75e5` | `v2.0.0-beta.4` | Cross-platform | Internal | **merged** | 2026-08-05 | Git tag | Missing GitHub Release. |
| **2.0.0-beta.5** | iOS 119 / And 204 | `b0bfa20d` | `v2.0.0-beta.5` | iOS, Android | Pre-release | **available in TestFlight / Play** | 2026-08-05 | Git tag, GitHub Release | Release created as Pre-release. |
| **2.0.0-beta.6** | iOS 120 / And 205 | `9aa844a7` | `v2.0.0-beta.6` | iOS, Android | Pre-release | **available in TestFlight / Play** | 2026-08-06 | Git tag, GitHub Release | Release created as Pre-release. |
| **2.0.0-beta.7** | iOS 121 / And 206 | `9cc1510c` | `v2.0.0-beta.7` | iOS, Android | Pre-release | **available in TestFlight / Play** | 2026-08-06 | Git tag, GitHub Release | Release created as Pre-release. |
| **2.0.0-beta.8** | iOS 122 / And 207 | `67c8cedb` | `v2.0.0-beta.8` | iOS, Android | Pre-release | **available in TestFlight / Play** | 2026-08-06 | Git tag, GitHub Release | Release created as Pre-release. |
| **2.0.0-beta.9** | iOS 123 / And 208 | `19268821` | `v2.0.0-beta.9` | iOS, Android | Internal | **merged** | 2026-08-06 | Git tag | Missing GitHub Release. |
| **2.0.0-beta.10**| iOS 124 / And 209 | `170118d0` | `v2.0.0-beta.10`| Web, Android, iOS | Internal | **merged** | 2026-08-28 | Git tag | Missing GitHub Release. |
| **2.0.0-beta.11**| iOS 125 / And 210 | `059da941` | `v2.0.0-beta.11`| iOS, Android | Internal | **merged** | 2026-08-29 | Git tag | Missing GitHub Release. |
| **2.0.0-beta.12**| iOS 126 / And 211 | `350b784b` | `v2.0.0-beta.12`| iOS, Android | Internal | **merged** | 2026-08-29 | Git tag | Missing GitHub Release. |
| **2.0.0-beta.13**| iOS 127 / And 212 | `5cfaa65a` | `v2.0.0-beta.13`| iOS, Android | Internal | **merged** | 2026-08-29 | Git tag | Missing GitHub Release. |
| **2.0.0-beta.14**| iOS 128 / And 213 | `192fa6eb` | `v2.0.0-beta.14`| iOS, Android | Internal | **merged** | 2026-08-29 | Git tag | Missing GitHub Release. |
| **2.0.0-beta.15**| iOS 129 / And 214 | `e0b220a7` | `v2.0.0-beta.15`| iOS, Android | Internal | **merged** | 2026-08-29 | Git tag | Missing GitHub Release. |
| **2.0.0-beta.16**| iOS 130 / And 215 | `6cc04f53` | `v2.0.0-beta.16`| iOS, Android | Internal | **merged** | 2026-08-30 | Git tag | Missing GitHub Release. |
| **2.0.0-beta.17**| iOS 131 / And 216 | `0da03a94` | `v2.0.0-beta.17`| iOS, Android | Internal | **merged** | 2026-08-30 | Git tag | Missing GitHub Release. |
| **2.0.0-beta.18**| iOS 132 / And 217 | `9bb6560c` | `v2.0.0-beta.18`| iOS, Android | Internal | **merged** | 2026-08-30 | Git tag | Missing GitHub Release. |
| **2.0.0-beta.19**| iOS 133 / And 218 | `1d66bfe6` | `v2.0.0-beta.19`| iOS, Android | Internal | **merged** | 2026-08-30 | Git tag | Missing GitHub Release. |
| **2.0.0-beta.20**| iOS 134 / And 219 | `aeec707b` | `v2.0.0-beta.20`| iOS, Android | Internal | **merged** | 2026-08-31 | Git tag | Missing GitHub Release. |
| **2.0.0-beta.21**| iOS 135 / And 220 | `e30852c3` | `v2.0.0-beta.21`| iOS, Android | Internal | **merged** | 2026-08-31 | Git tag | Missing GitHub Release. |
| **2.0.0-beta.22**| iOS 136 / And 221 | `08fb7249` | `v2.0.0-beta.22`| iOS, Android | Internal | **merged** | 2026-08-31 | Git tag | Missing GitHub Release. |
| **2.0.0-beta.23**| iOS 137 / And 222 | `cba806f8` | `v2.0.0-beta.23`| iOS, Android | Internal | **merged** | 2026-08-31 | Git tag | Missing GitHub Release. |
| **2.0.0** | iOS 138 / And 223 | `d4f92f37` | `v2.0.0` | iOS, Android, Web, Pi | Production | **published** | 2026-09-02 | Git tag, CHANGELOG, App Store, Google Play | GitHub Release creation pending authenticated `gh` credentials. |
| **3.0.0-beta.1** | iOS epoch / And 224| `16d20458` | `v3.0.0-beta.1` | iOS, Android, Web | Internal | **available in Play internal** | 2026-09-03 | Git tag, PR #113 | Missing GitHub Release. |
| **3.0.0-beta.2** | iOS epoch / And 225| `4e782597` | `v3.0.0-beta.2` | iOS, Android, Web | Internal | **available in Play internal** | 2026-09-03 | Git tag, PR #118 | Missing GitHub Release. |
| **3.0.0-beta.3** | iOS epoch / And 226| `7c1052cd` | `v3.0.0-beta.3` | iOS, Android, Web | Internal | **available in Play internal** | 2026-09-26 | Git tag, PR #198 | Missing GitHub Release. |
| **3.0.0-beta.4** | iOS epoch / And 227| `207a0aca` | `v3.0.0-beta.4` | iOS, Android, Web | Internal | **available in Play internal** | 2026-09-26 | Git tag, PR #207 | Missing GitHub Release. |
| **3.0.0-beta.5** | iOS epoch / And 228| `264577c8` | `v3.0.0-beta.5` | iOS, Android, Web | Internal | **available in Play internal** | 2026-09-26 | Git tag, PR #214 | Missing GitHub Release. |
| **3.0.0-beta.6** | iOS epoch / And 229| `95af441a` | `v3.0.0-beta.6` | iOS, Android, Web | Internal | **available in Play internal** | 2026-09-27 | Git tag, PR #228 | Missing GitHub Release. |
| **3.0.0-beta.7** | iOS epoch / And 230| `0e7e9ac2` | `v3.0.0-beta.7` | iOS, Android, Web | Internal | **available in Play internal** | 2026-09-30 | Git tag, CHANGELOG | iOS upload was failed due to transient 500 in altool guard. |
| **3.0.0-beta.8** | iOS 1790754983 / And 231 | `16612dbe` (merge `2f1ebb89`) | `v3.0.0-beta.8` | iOS, Android, Web | Internal TestFlight & Play | **available in TestFlight & Play** | 2026-09-30 | `RELEASE-EVIDENCE-3.0.0.md`, PR #241 | Delivery UUID `a3088aac-2be4-4381-ae1a-44fc704b6790`. GitHub Release creation pending authenticated `gh` credentials. |
| **3.0.0-beta.9** | iOS epoch / And 232| `04cd2a96` | `v3.0.0-beta.9` | iOS, Android | Internal | **merged** | 2026-10-10 | Git tag | Missing GitHub Release. |
| **3.0.0-beta.10**| iOS epoch / And 232| `d8cf5e04` | `v3.0.0-beta.10`| iOS, Android | Internal | **merged** | 2026-10-11 | Git tag | Missing GitHub Release. |
| **3.0.0-beta.11**| iOS epoch / And 232| `fd01fcf7` | `v3.0.0-beta.11`| iOS, Android | Internal | **merged** | 2026-10-11 | Git tag | Missing GitHub Release. |
| **3.0.0-beta.12**| iOS 138 / And 233 | `75bba986` (commit `1772cf1a`) | `v3.0.0-beta.12`| iOS, Android | Internal | **merged & built** | 2026-10-11 | Git tag, CHANGELOG | GitHub Release creation pending authenticated `gh` credentials. |
| **HEAD (master)**| iOS 138 / And 233 | `42689f42` | None (Unreleased) | iOS, Android, Web | Master branch | **committed & merged** | 2026-10-11 | Local git HEAD | Contains Suburban ticket sheet and Home train detail navigation. |

---

## 4. Platform-by-Platform Reality Check

| Surface | Claimed in Docs | Verified Local Artifacts | Remote Service / Store Status | Status Category |
|---|---|---|---|---|
| **iOS App Store** | Version 2.0.0 (build 138) | `project.pbxproj` (3.0.0, 138) | Publicly available on Apple App Store | **published** |
| **iOS TestFlight** | Version 3.0.0 (build 1790754983) | Verified in `RELEASE-EVIDENCE-3.0.0.md` | Upload succeeded (UUID `a3088aac-2be4-4381-ae1a-44fc704b6790`) | **available in TestFlight** |
| **Android Google Play (Production)** | Version 2.0.0 (code 223) | Archived in CHANGELOG | Publicly available on Google Play Store | **published** |
| **Android Google Play (Internal)** | Version 3.0.0 (code 231) | `androidApp/build.gradle.kts` (now 233) | Play Console internal track (edit `01500641537142979236`) | **available in Google Play** |
| **Web Production** | Rolling master | `composeApp/.../resources/` (SW v4) | Served live at `https://syrmos.peterdsp.dev` via GitHub Pages | **runtime-verified & deployed** |
| **Backend API (Pi)** | Schema 26, FastAPI | `ops/syrmos-api/` | Active behind Cloudflare Tunnel at `api-syrmos.peterdsp.dev` | **runtime-verified & deployed** |

---

## 5. GitHub Releases Reconciliation & Repair Plan

### 5.1 Remote State vs Desired State

| Release Tag | Current Remote GitHub State | Desired Truthful State | Action Required |
|---|---|---|---|
| `v1.4.0` | Flagged as **Latest Release** | Flagged as regular historical release | Unset "Latest" flag once `v2.0.0` is published |
| `v2.0.0` | **Does not exist** on GitHub | Should exist as **Latest Release** (Title: "Syrmos 2.0.0") | Create GitHub Release from tag `v2.0.0` |
| `v3.0.0-beta.8` | **Does not exist** on GitHub | Should exist as **Pre-release** (Title: "Syrmos 3.0.0 Beta 8") | Create GitHub Pre-release documenting TestFlight / Play build |
| `v3.0.0-beta.12`| **Does not exist** on GitHub | Should exist as **Pre-release** (Title: "Syrmos 3.0.0 Beta 12") | Create GitHub Pre-release documenting vehicle icons & Duo snapshots |

### 5.2 Proposed GitHub CLI Commands

If authorized by repository owner:

```bash
# 1. Create the missing production release v2.0.0 and mark as latest
gh release create v2.0.0 \
  --title "Syrmos 2.0.0" \
  --latest \
  --notes "Production release 2.0.0 of Syrmos: Hellenic Rail Atlas, nationwide timetables, proactive Home, real OSM track geometry, live vehicle positions, and trilingual support. Shipped to Apple App Store (build 138) and Google Play (versionCode 223)."

# 2. Create the missing Pre-release for v3.0.0-beta.8
gh release create v3.0.0-beta.8 \
  --title "Syrmos 3.0.0 Beta 8" \
  --prerelease \
  --notes "The station-complex all-directions departures board, local-first Ariadne, and scoped Ichnos reporting. Distributed to TestFlight (iOS build 1790754983) and Google Play Internal Testing (versionCode 231)."

# 3. Create the missing Pre-release for v3.0.0-beta.12
gh release create v3.0.0-beta.12 \
  --title "Syrmos 3.0.0 Beta 12" \
  --prerelease \
  --notes "Stable vehicle-icon rendering across live refreshes and scrolling, native iOS launch storyboard presentation, and refreshed iPhone Duo marketing captures. Android versionCode 233."
```

*(Note: In accordance with repository safety guidelines, these release updates are documented as proposals for owner confirmation).*
