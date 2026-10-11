# Syrmos Repository Audit & Cleanup Plan

> Repository: `Syrmos` (`/Users/peterdsp/git/Syrmos`)  
> Generated: October 2026 (Audit against branch `master` at commit `42689f42`)  
> Policy: Zero destructive deletion without prior manifest review and explicit authorization.

---

## 1. AI Tooling, Residue & Metadata Audit

A systematic scan of the entire codebase was conducted across all files and git history to identify external AI assistant configurations, tool metadata, agent instruction files, and stray attribution.

### 1.1 Findings Summary

| Category | Query / Pattern | Finding Location | Status / Nature |
|---|---|---|---|
| **Local Tooling Directory** | `.claude/` | `/Users/peterdsp/git/Syrmos/.claude/` | **Local Residue:** Untracked directory left by Claude Code. Contains `scheduled_tasks.lock` and empty `worktrees/`. Ignored in `.gitignore`. |
| **Model Context Protocol** | `.mcp.json` | `/Users/peterdsp/git/Syrmos/.mcp.json` | **Development Tooling:** Tracked in git. Configures `xcodebuild`, `chrome-devtools`, and `playwright` MCP servers. |
| **Copilot Metadata** | `copilot*`, `.github/copilot*` | None | **Clean:** 0 files or references found. |
| **AI Assistant Residue in Git** | "claude", "chatgpt", "copilot" | Commit `b877a21a` | **Clean:** Repository author intentionally removed all assistant planning archives (`docs/plans/`, `AI_TOOLBOX_AUDIT.md`, `autonomous-watchdog.sh`) on 2026-09-30. |
| **Binary Symbol Occurrences** | "claude" in binaries | `libllama.so`, `libllama-sim.a`, `libllama-device.a` | **False Positive:** Internal symbols inside embedded upstream `llama.cpp` compiled binaries. Must be preserved. |
| **Product Feature "Ariadne" / "OpenAI"**| `openai`, `Ariadne` | `ops/syrmos-api/syrmos_admin/ariadne.py` | **Product Feature:** Legitimate user-facing feature (Ariadne assistant provider chain interfacing with Groq/Cloudflare LLM APIs). Must NOT be removed. |

---

## 2. Categorized File Classification

### 2.1 Safe to Remove (Disposable Artifacts)

These items are transient local files, OS-generated metadata, editor locks, or build-derived artifacts with 0 build, CI, or runtime dependency:

1. `.claude/` directory and its contents (`.claude/scheduled_tasks.lock`, `.claude/worktrees/`) — Local session lock file from an external agent.
2. `.DS_Store` and `docs/.DS_Store` — macOS Finder folder metadata.
3. `iosApp/Syrmos.xcodeproj/project.xcworkspace/xcuserdata/` and `iosApp/Syrmos.xcodeproj/xcuserdata/` — Local Xcode user window states.
4. `ops/syrmos-api/.pytest_cache/` — Pytest runtime cache.

### 2.2 Requires Review (Owner Confirmation Needed)

These files might appear obsolete or non-standard, but may serve as historical reference, debugging fixtures, or developer tooling:

1. **`.mcp.json`**:
   - *Description:* Model Context Protocol config specifying local MCP tool servers (`xcodebuildmcp`, `chrome-devtools-mcp`, `playwright`).
   - *Impact:* Removing it disables MCP tooling for IDE assistants, but has no impact on Gradle, Xcode, or GitHub Actions builds.
2. **`scripts/generate_report.py`**:
   - *Description:* Python script for generating QA markdown reports.
   - *Impact:* Not called by any CI workflow; manually invoked by developers.
3. **`docs/press.html` & `docs/assets/`**:
   - *Description:* Press kit and promotional zip/png assets.
   - *Impact:* Copied by `scripts/prepare-pages-web-release.sh` into web release output if present. Must be kept if press page is intended to be live.
4. **`web-tests/go-live-harness.html` & `web-tests/go-panel-harness.html`**:
   - *Description:* Standalone HTML harnesses used for manual in-browser testing of the GO guidance panel.
   - *Impact:* Useful developer fixtures; not actively run by `node --test`.

### 2.3 Keep (Strictly Required)

1. **All source code:** `iosApp/`, `androidApp/`, `composeApp/`, `core/`, `feature/`.
2. **Build logic:** `build-logic/`, `settings.gradle.kts`, `build.gradle.kts`, `gradle/`.
3. **Bundled seeds & datasets:** `core/data/.../seed/`, `assets/`, `fixtures/`.
4. **CI/CD workflows:** `.github/workflows/*.yml`.
5. **Xcode configuration:** `iosApp/project.yml`, `iosApp/Syrmos.xcodeproj/project.pbxproj`, `scripts/export-options.plist`.
6. **Documentation & Legal:** `LICENSE`, `README.md`, `CHANGELOG.md`, `DESIGN_SYSTEM.md`, `REUSE.toml`, `docs/PRIVACY_POLICY.md`.
7. **Test suites:** `iosAppTests/`, `web-tests/`, `*Test.kt`, `ops/syrmos-api/tests/`.

---

## 3. Explicit Deletion Manifest

| Absolute Path | Why It Is Unused | Evidence Checked | Recoverable? | Proposed Action |
|---|---|---|---|---|
| `/Users/peterdsp/git/Syrmos/.claude/scheduled_tasks.lock` | Stale process lock file from previous tool run | Ignored in `.gitignore`; no code or build script references | Yes (Regenerated if tool runs) | **Delete on approval** |
| `/Users/peterdsp/git/Syrmos/.claude/worktrees` | Empty temporary worktree directory | Ignored in `.gitignore`; 0 bytes | Yes | **Delete on approval** |
| `/Users/peterdsp/git/Syrmos/.claude` | Local AI agent folder | Ignored in `.gitignore`; contains only above stale lock | Yes | **Delete on approval** |
| `/Users/peterdsp/git/Syrmos/.DS_Store` | macOS filesystem cache | Ignored in `.gitignore` | Yes (OS recreates) | **Delete on approval** |
| `/Users/peterdsp/git/Syrmos/docs/.DS_Store` | macOS filesystem cache | Ignored in `.gitignore` | Yes (OS recreates) | **Delete on approval** |
| `/Users/peterdsp/git/Syrmos/ops/syrmos-api/.pytest_cache` | Local test execution cache | Ignored in `.gitignore`; recreated on `pytest` run | Yes | **Delete on approval** |

*(Note: Per repository audit guidelines, no files from this manifest have been deleted yet. Deletion will only occur upon explicit owner instruction).*
