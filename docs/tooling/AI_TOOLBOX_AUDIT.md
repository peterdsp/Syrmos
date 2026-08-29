# AI toolbox audit

Evidence-backed audit of MCP servers, skills and developer tools for Syrmos and
future projects. Research-only method: every candidate was checked against
primary sources (official repos, docs, release pages) on 2026-08-30, and every
third-party MCP server is treated as executable software with potential access
to source, files, credentials and external accounts. Tier A additions were
version-resolved and health-checked locally before enabling (see the
verification column and the log at the end).

Environment facts that shaped the recommendations (verified in-repo and on this
host): three codebases (native iOS SwiftUI with a plain `.xcodeproj` and no SPM
or CocoaPods deps, Android/KMP + Compose on Gradle, hand-written Leaflet web on
GitHub Pages); a self-hosted Python + SQLite backend under `ops/syrmos-api/`;
release CI that is already least-privilege (iOS via `altool` + an App Store
Connect API key, Android via `r0adkll/upload-google-play` + a scoped Play
service account, no Fastlane); no crash-reporting SDK anywhere; localization
that is custom in-code, not a TMS; and a host with `node`, `python3`, `git`,
`gh`, `rg`, `jq`, `sqlite3`, `xcrun` present but no Homebrew, no `uv`, no `adb`
on PATH. Because there is no `brew`/`uv`, the reproducible install path is
pinned `npx` for MCP servers and pinned prebuilt binaries or `pip --user` for
CLI scanners. Built-ins already present: the Claude Code iOS Simulator MCP, the
in-app Chrome browser MCP, WebSearch/WebFetch, the `security-review` skill, and
the memory system.

## Tiers

- A: add now. Trusted, maintained, clearly valuable, compatible, least
  privilege, non-redundant, reproducible pinned install.
- B: controlled trial. Valuable but needs a sandbox, broader permissions, or a
  younger ecosystem. Never given production credentials during evaluation.
- C: watch. Promising but immature, redundant, or not yet justified.
- D: reject. Abandoned, opaque, excessive permissions, poor license, security
  concern, needless data transmission, or duplicates a trusted built-in.

## Matrix

| Tool | Category | Syrmos use case | Source | Version (verified 2026-08-30) | License | Permissions / data exposure | Existing overlap | Tier |
|------|----------|-----------------|--------|-------------------------------|---------|-----------------------------|------------------|------|
| XcodeBuildMCP | iOS build/test | Build and test `Syrmos.xcodeproj`, run on a sim, capture logs from the agent loop | github.com/getsentry/XcodeBuildMCP | 2.7.0 | MIT | Local shell + Xcode toolchain; Sentry telemetry on by default (opt out with `XCODEBUILDMCP_SENTRY_DISABLED=true`) | Built-in iOS Simulator MCP covers sim control only, not build/test | A |
| Chrome DevTools MCP | Browser perf/console | Trace the web map load, read console errors, network waterfall (the "trains in the sea" and Athens-time classes of bug) | github.com/ChromeDevTools/chrome-devtools-mcp | 1.8.0 | Apache-2.0 | Drives a Chrome instance; usage stats + CrUX on by default (`--no-usage-statistics --no-performance-crux`) | Built-in in-app browser is interactive, no perf tracing | A |
| Playwright MCP | Browser automation, a11y, screenshots | Deterministic headless web-map automation, accessibility-tree assertions, screenshots for visual regression | github.com/microsoft/playwright-mcp | 0.0.79 | Apache-2.0 | Launches a browser; reachable origins per allow/block list; run `--isolated --headless` | Complements the interactive built-in browser | A |
| Semgrep CLI | Security SAST | One SAST pass over Swift + Kotlin + JS + Python; there is no static analysis in CI today | github.com/semgrep/semgrep | 1.136.0 | LGPL-2.1 (OSS engine) | Reads source locally, code is not uploaded; registry rule fetch is network unless local | Built-in `security-review` skill is LLM, not rule-based | A (CLI) |
| OSV-Scanner | Dependency audit | Audit the Gradle/KMP catalog (Ktor, Koin, osmdroid, SQLDelight, ML Kit) against OSV.dev | github.com/google/osv-scanner | v2.x release binary | Apache-2.0 | Sends package coordinates (not source) to OSV.dev; offline mode after DB cache | GitHub Dependabot | A (CLI) |
| MCP Inspector | MCP debugging | Vet any new MCP server before trusting it | github.com/modelcontextprotocol/inspector | 2.4.0 | MIT | Spawns local processes on loopback, token-auth; do not expose to network | None | A (on-demand) |
| axe-core (+ @axe-core/playwright) | Accessibility | Automated WCAG checks on the web map, driven through Playwright | github.com/dequelabs/axe-core | current | MPL-2.0 | Runs in-browser, fully local | Playwright MCP hosts it; no native-mobile a11y | A (via Playwright) |
| mobile-mcp | Android emulator | Drive an emulator via adb + accessibility tree (fills the missing Android panel) | github.com/mobile-next/mobile-mcp | rolling | Apache-2.0 | adb + device control; PostHog telemetry (`MOBILEMCP_DISABLE_TELEMETRY=1`); needs adb on PATH (absent) | Personal `android.sh`; built-in iOS Sim MCP | B |
| GitHub MCP Server | GitHub/CI review | Structured, read-only PR/CI/Actions review from the model | github.com/github/github-mcp-server | current | MIT | OAuth/PAT scopes; remote transmits to GitHub; use `--read-only` | `gh` CLI is present and authenticated | B |
| Context7 | Docs retrieval | Version-correct Ktor/Compose/Leaflet docs into context | github.com/upstash/context7 | current | MIT (client) | Library/topic queries go to Upstash's hosted backend (not your source) | Built-in WebFetch/WebSearch | B |
| Figma Dev Mode MCP | Design | Pull design tokens/components into `DESIGN_SYSTEM.md` | help.figma.com | beta | proprietary | Needs a paid Dev/Full seat; sends design data to Figma | Built-in `figma:*` skills | B |
| Sentry MCP | Crash/observability | Triage issues/traces once a crash SDK exists | github.com/getsentry/sentry-mcp | current | proprietary/OSS mix | Token scopes; `event:write` mutates | No crash SDK in Syrmos today | C |
| arxiv-mcp-server | Research papers | Transit-routing/GTFS literature for the reusable toolbox | github.com/blazickjp/arxiv-mcp-server | 0.7.2 | Apache-2.0 | Query terms leave the machine; single-author project | Built-in WebFetch on arxiv.org | C |
| Fastlane | Releases/assets | Framed store screenshots (`frameit`/`snapshot`) | github.com/fastlane/fastlane | current | MIT | ASC/Play creds, local | `altool` + `upload-google-play` already ship releases | C |
| SQLite / Filesystem / Git / Fetch reference MCPs | General | Files, git, http, db | modelcontextprotocol reference servers | mixed (SQLite archived) | MIT/Apache-2.0 | Redundant attack surface | Built-in Read/Edit/Grep/Bash/`gh`/WebFetch/`sqlite3` | D |
| Third-party App Store Connect / Play MCPs | Releases | Store automation via MCP | community | unverified | varies | Requires production store credentials inside third-party code | CI already uploads least-privilege | D |
| Localization TMS MCPs (Lokalise/Crowdin) | Localization | Sync strings | vendor | vendor | proprietary | Account + strings off-machine | Localization is custom in-code | D |

## Rollback (any addition)

- MCP servers: delete the entry from `.mcp.json` (or delete the file). No
  residual state; `npx` caches under `~/.npm/_npx` can be cleared with
  `npm cache clean --force` if desired.
- Semgrep: `python3 -m pip uninstall semgrep`.
- OSV-Scanner: delete the pinned binary.
- MCP Inspector: nothing persisted (on-demand `npx`).

## Local verification log (2026-08-30)

- Versions resolved from the registries: `xcodebuildmcp` 2.7.0,
  `chrome-devtools-mcp` 1.8.0, `@playwright/mcp` 0.0.79,
  `@modelcontextprotocol/inspector` 2.4.0, `semgrep` 1.136.0.
- Health checks passed (launch + help/version, exit 0):
  `npx -y chrome-devtools-mcp@1.8.0 --help`,
  `npx -y @playwright/mcp@0.0.79 --help`,
  `npx -y xcodebuildmcp@2.7.0 --version`.
- Telemetry opt-out confirmed from the shipped package: XcodeBuildMCP honours
  `XCODEBUILDMCP_SENTRY_DISABLED`; Chrome DevTools MCP honours
  `--no-usage-statistics` / `--no-performance-crux` (and the `CI` env).
- The three Tier A MCP servers are enabled project-locally in `.mcp.json` with
  telemetry disabled and browsers run `--headless --isolated`.

Note: the `plugin:*` connectors listed in this environment (GitHub, Figma,
Linear, Sentry, etc.) are unauthenticated in a headless session and must be
authorized interactively (claude.ai connector settings or `claude mcp` / `/mcp`)
before their tools work. Do not place production credentials into any MCP
server during evaluation; keep secrets in CI.
