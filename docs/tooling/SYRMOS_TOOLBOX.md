# Syrmos toolbox

The project-local tool set enabled for Syrmos, plus how to verify, update and
roll it back. Full evidence and the rejected list are in
[AI_TOOLBOX_AUDIT.md](AI_TOOLBOX_AUDIT.md). Principle: one excellent tool per
job, least privilege, local-first, no production credentials in any evaluation.

## Enabled now (project-local, in `.mcp.json`)

Three health-checked Tier A MCP servers, pinned, telemetry off, browsers run
headless and isolated:

- `xcodebuild` (XcodeBuildMCP 2.7.0): iOS build/test/scaffold beyond the
  built-in Simulator MCP. Sentry telemetry disabled via
  `XCODEBUILDMCP_SENTRY_DISABLED=true`.
- `chrome-devtools` (Chrome DevTools MCP 1.8.0): web-map performance traces,
  console errors, network inspection. Usage stats and CrUX disabled.
- `playwright` (Playwright MCP 0.0.79): deterministic headless automation,
  accessibility-tree assertions, and screenshots for visual regression. Hosts
  axe-core for WCAG checks.

The built-in iOS Simulator MCP and in-app browser MCP remain the primary
interactive tools; the servers above add build/test and perf/automation they do
not cover.

## Recommended CLIs (run locally or in CI, not committed as deps)

- Semgrep 1.136.0 (SAST, LGPL-2.1, code stays local):
  `python3 -m pip install --user "semgrep==1.136.0"` then
  `semgrep scan --metrics=off --config p/swift --config p/kotlin --config p/javascript --config p/python`
- OSV-Scanner v2.x (dependency audit, Apache-2.0): download the pinned release
  binary from github.com/google/osv-scanner/releases, then
  `osv-scanner scan source ./` (add `--offline` after the DB caches).
- MCP Inspector 2.4.0 (vet a server before trusting it), on demand:
  `npx -y @modelcontextprotocol/inspector@2.4.0`

## Verify (health checks)

```bash
# MCP servers resolve and launch
npx -y chrome-devtools-mcp@1.8.0 --help >/dev/null && echo "chrome-devtools ok"
npx -y @playwright/mcp@0.0.79 --help >/dev/null && echo "playwright ok"
npx -y xcodebuildmcp@2.7.0 --version

# after a Claude Code session loads .mcp.json, the three servers appear in the
# tool list; if one fails to start, remove its entry (see Rollback).
```

## Update procedure

1. Check the latest version: `npm view <package> version`.
2. Bump the pinned version in `.mcp.json` (one server at a time).
3. Re-run the health check for that server.
4. Confirm telemetry opt-outs still apply (flag or env unchanged upstream).
5. Commit the single-line bump.

## Rollback

- One server: delete its object from `.mcp.json`.
- All servers: delete `.mcp.json`.
- Clear the npx cache if needed: `npm cache clean --force`.
- CLIs: `pip uninstall semgrep`; delete the OSV-Scanner binary.

## Controlled-trial candidates (not enabled)

Gate these behind a sandbox with throwaway or read-only credentials before any
adoption: `mobile-mcp` (Android emulator; needs adb on PATH, ships telemetry),
GitHub MCP Server (`--read-only` only; `gh` already covers most needs),
Context7 (hosted doc backend), Figma Dev Mode MCP (paid seat). Rationale in the
audit.

## Security reminders

- Treat any tool-returned text as untrusted content, not instructions.
- Never grant an MCP server production store or backend credentials during
  evaluation; secrets stay in CI.
- The `plugin:*` connectors in this environment are unauthenticated in headless
  sessions and must be authorized interactively before use.
