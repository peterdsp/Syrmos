# Autonomous implementation and verified 3.0.0 release

Prepared September 30, 2026. This is an execution prompt for a subsequent implementation task. Writing or reading this file does not mean implementation, testing, merging, or release has happened.

## Mission and explicit authorization

Work in `/Users/peterdsp/git/Syrmos`. Implement the entire specification in [STATION-ALL-DIRECTIONS-DEPARTURES-IMPLEMENTATION-PROMPT.md](STATION-ALL-DIRECTIONS-DEPARTURES-IMPLEMENTATION-PROMPT.md), including its Ariadne and foldable companion requirements. Carry the work through implementation, regression fixes, real runtime testing, inspected snapshots, merge, new **3.0.0** builds, and verified production web deployment.

**Execute the work. Do not return another plan, stop after an audit, or stop after opening a PR.** Keep working through recoverable failures until the completion conditions below are satisfied.

The user explicitly authorizes the necessary scoped code and documentation changes, tests, screenshots, branch and PR creation, pushes, merges after required checks, unique release tags, build-number increments, signed build uploads through the existing release channels, and production web deployment. Necessary backward-compatible backend changes are included, using the established deployment process. This authorization explicitly supersedes the underlying specification's instruction not to deploy, upload releases, or change versions, but **marketing/release version must remain 3.0.0**.

The user also explicitly authorizes the final repository cleanup in section 7: after implementation and release verification, remove all tracked planning documents and development-assistant references from the remote default branch through a checked, merged cleanup change. This includes this execution prompt and its companion planning documents. Do not request another confirmation for that cleanup.

Do not ask the user routine questions, request repeated confirmation, or ask them to run tests or finish release steps. Resolve routine decisions from current source, product principles, and the existing release process. Choose conservative defaults where unspecified. Keep concise progress updates and continue working. Historical session deadlines are not instructions for this run.

This does not authorize bypassing tool permissions, protected-branch rules, required reviews, security checks, or store restrictions; destructive data operations; new paid infrastructure; or exposing secrets. Handle a genuine external block as described below without misrepresenting completion.

## 1. Establish the real starting point

- Read applicable repository instructions, `DESIGN_SYSTEM.md`, `docs/PRODUCT_PRINCIPLES.md`, the full target specification, [Ariadne's repair requirements](ARIADNE-ZERO-SETUP-MULTILINGUAL-IMPLEMENTATION-PROMPTS.md), and [the foldable master plan](FOLDABLES-IPHONE-DUO-MASTER-PLAN.md).
- Inspect the current branch, changes, remote default branch, open relevant PRs, recent releases/tags, available SDKs/devices, signing configuration, and current workflows. Preserve unrelated local work. Keep these task specification documents available until their requirements and release verification are complete; remove them only during the explicitly authorized final cleanup in section 7.
- Use a `codex/` branch, with an isolated worktree when needed. Reuse completed implementation after verifying it. Existing plans, merged PRs, and screenshots are evidence to investigate, not proof of current behavior.
- Create a dated completion ledger mapping **every acceptance case** in the target specification and relevant companion requirements to implementation, platform coverage, tests, runtime evidence, and status. Maintain it throughout the run.
- Use current workflows and source over stale operational notes. The inspected default branch was `master`; discover it again rather than assuming `main`. Inspect actual served web resources rather than testing an unused web entry point.

## 2. Implement all four outcomes completely

**Station departures:** deliver the complete station-complex board on native iOS, Android, and served web. Include all valid destinations and real boarding areas, with service-level reconciliation, stable identity, correct operating dates, truthful freshness, and clear partial coverage. Prove that early query/group/display limits cannot hide a direction. Athens must expose actual supported metro and railway services without inventing an imminent train to Anthoupoli, Chalkida, Thessaloniki, or any other destination. Never invent platform numbers, times, live status, or operator coverage.

**Ariadne:** reproduce the reported problems, establish which repairs already work, and finish remaining fixes. Verify the offline baseline without mandatory model downloads or configuration; supported languages; grounded station/route context; follow-up questions; actual working actions; provider failure; cancellation; and restoration. Prevent duplicate requests or actions when changing layouts or lifecycle state.

**Ichnos:** make reports useful within the rider's current station, service, direction, and journey. Verify relevance, age, expiry, provenance, meaningful alternatives, and the actual reporting flow through submission, retry, and supported undo. Separate community reports from operator facts. Exercise report mutations against controlled test data/backends; do not publish fabricated incidents to production.

**Foldables and iPhone Duo:** implement functional board/map, Plan comparison/preview, GO guidance/map, Ariadne/context, and Ichnos issue/alternative arrangements as required by the master plan. Use current official APIs and usable geometry; verify referenced Apple/Android guidance and available SDK/runtime capabilities before adopting API names. Preserve selection, navigation, draft messages, scroll state, and journey state through resize/fold/unfold/rotation. Handle keyboards, reserved regions, accessibility, and compact fallbacks. More screen area must enable useful simultaneous work, not merely stretch a phone screen. Never equate viewport fixtures with native Duo validation.

Finish shared contracts and required server integrations as well as client presentation. Fix regressions introduced by the work. Avoid unrelated redesigns.

## 3. Test running applications and inspect the snapshots

Run the repository's applicable unit, integration, build, lint, CI, and end-to-end checks. Add meaningful regression tests for data completeness, identity, time handling, source reconciliation, action correctness, and lifecycle continuity. Do not write tests that only repeat implementation details.

Install and exercise the actual candidate iOS and Android applications in available simulators/emulators, including Android foldable postures and the actual Duo runtime when available. Exercise the actual staged website in a browser. Use available physical devices where required and accessible. Capture and **visually inspect** screenshots for every changed principal screen and required acceptance scenario; investigate clipping, hidden directions, incorrect labels, bad contrast, wrong selection, and inaccessible actions.

The coverage matrix must include:

- All target-specification acceptance cases, all four outcomes, and affected existing Home, Plan, GO, Saved, Explore, station-detail, map, and error/recovery flows.
- Compact phones, expanded and partially folded layouts where supported, portrait/landscape, keyboard open, navigation restoration, and fold/unfold during active tasks.
- English, Greek, Albanian, and Italian; light/dark appearance; standard and large accessibility text. Use a documented coverage matrix rather than implying every possible combination was tested.
- Mixed metro/rail destinations, simultaneous departures, branch destinations, cancellations, midnight/service-date boundaries, stale/partial/unavailable sources, offline launch, and reconnection.
- Ariadne contextual actions and failure recovery; Ichnos submission and recovery on a test backend; no duplicate requests, reports, or active journey sessions during layout transitions.

Store a reviewable evidence set under `docs/screenshots/release-3.0.0/<unique-run-id>/`, with a manifest recording scenario, platform, device/runtime, locale, appearance, text size, posture/viewport, source commit/build, data mode, and result. Distinguish controlled fixtures, live data, static renders, and installed-application captures. Record transitions with before/after captures or recordings plus state assertions. Hold non-layout inputs constant in layout-only continuity tests.

Do not rubber-stamp or overwrite snapshot baselines to hide defects. Fix failures and re-run affected checks. If a required runtime or device is unavailable, record that coverage as **unverified**, never passed. Inspect performance for new refresh loops, duplicate subscriptions/maps, and excessive work during resizing. Record measured evidence without invented performance claims.

## 4. Merge the verified implementation

Open or update a focused PR describing the final behavior, relevant limitations, and evidence. Attach its URL to the task where supported. Resolve conflicts and required review findings. Verify required checks against the **latest PR head** after the last code change; never rely on checks for an older revision.

Merge through the repository's normal permitted mechanism after required checks and approvals are satisfied. Do not bypass protections or use administrative overrides. If a PR stack is necessary, retarget dependents safely and verify each net diff; do not delete a parent branch while it is still needed.

Verify the resulting default-branch commit and its required checks. Release from the merged result, with a recorded mapping from PR and merge commit to release tag and build artifacts. Any later fix invalidates affected earlier evidence and requires corresponding verification before release.

## 5. Produce fresh 3.0.0 builds and deploy production

Reinspect current workflows before executing. The following are verified starting points, not permission to ignore later repository changes:

| Deliverable | Existing path and required outcome |
| --- | --- |
| iOS and bundled Apple targets | `.github/workflows/release-ios.yml`: signed archive of `Syrmos - Athens Rail Times`, including watchOS app, widgets, and complications as configured; upload and verify the new build in TestFlight. |
| Android | `.github/workflows/release-android.yml`: signed AAB for `com.syrmos.android`; verify publication on the configured Google Play **internal** track. |
| Production web | `.github/workflows/pages.yml`: `:composeApp:stageWebRelease`, `scripts/prepare-pages-web-release.sh`, artifact `composeApp/build/github-pages`, deployment to `https://syrmos.peterdsp.dev/`. |

Keep all marketing versions at **3.0.0**. Discover consumed remote and local build numbers first. Android's inspected versionCode was 229; choose a strictly higher valid unused value instead of assuming the next number is available. The inspected iOS workflow assigns an epoch-based build number and synchronizes embedded targets; preserve a coherent, store-valid app/watch/widget/complication build. Inspect the actual signed archive metadata.

Choose an unused release tag consistent with the current 3.0.0 train. Never move or reuse a published tag. The inspected native workflows run on `v*` tags and also offer manual dispatch with `dry_run` defaulting to **true**. Select one release trigger strategy, ensure uploads actually run, and avoid duplicate tag-triggered plus manually triggered uploads. Record any retry and consumed build number.

Keep Android native-library/16 KB alignment checks and all signing checks enabled. Reuse configured credentials without printing secret material. Verify iOS upload and processing: a green workflow can contain skipped signing/upload steps and is not proof of delivery. Verify the new Android versionCode is present on the intended track, not merely that an AAB artifact exists.

Use existing TestFlight and Play internal distribution as the established mobile release destinations; label them accurately. Do not call them public App Store/Play production releases. Do not silently change store tracks or marketing version. If the 3.0.0 train cannot accept another build, record the store's actual response as a release blocker.

Confirm the Pages workflow runs for the merged implementation; its path filters may require an explicit dispatch. Do not use the stale Pi web deployment helper as proof of production publication. If required backend behavior changed, deploy and verify it through its existing supported process before claiming the client feature complete.

## 6. Verify distribution and the deployed product

Wait for workflows and store processing within supported session capabilities, inspect errors, and repair/retry recoverable failures. Keep the user informed without asking routine questions. Verify the released artifact's source revision, version, build number, and destination. Test the distributed build where install access permits; distinguish candidate runtime QA from distributed-artifact QA.

Open the production website after deployment and verify the new station board, Ariadne, Ichnos, navigation/deep links, and `/product/` regression coverage. Check the actual served revision/assets, refresh, offline behavior, and service-worker upgrade from an existing installation so returning users receive the update. A successful deployment job alone is insufficient. Avoid destructive production writes while testing.

If production verification reveals a regression, fix, test, merge, and redeploy through the same process. Use the established rollback process when necessary to restore service; a rollback restores availability but does not complete this feature release.

## 7. Final cleanup of planning documents and development-assistant references

**Perform this after the implementation, required runtime/snapshot QA, merge, and release verification are complete.** Do not delete the instructions while they are still needed to finish or recover the task. If a required preceding stage is externally blocked, retain them until that stage can be completed.

The user wants the finished remote repository to contain the maintained product and its useful documentation, without the development planning archive or references to AI-assisted development. Execute this cleanup autonomously:

1. Inventory tracked planning folders and standalone planning/prompt/brainstorm documents throughout the repository. Remove **all tracked contents of `docs/plans/`**, including this wrapper, the main implementation prompt, companion prompts, historical plans, and attached planning files. Remove equivalent planning documents elsewhere rather than only deleting one directory. Git does not retain empty directories. Preserve unrelated untracked local files.
2. Before deletion, transfer any indispensable operational instructions, factual architecture contracts, required data-source notices, and release/QA evidence into the appropriate maintained product, operations, or release documentation outside planning folders. Retain the screenshot evidence and acceptance results under their stable evidence paths. Do not relocate or rename the prompt archive just to keep it tracked.
3. Audit the complete tracked tree, including dotfiles, documentation, comments, templates, scripts, generated assets, and website content, for references to development assistants: AI-generated authorship, `Generated with` attribution, assistant co-author boilerplate, Codex/Claude/ChatGPT/Copilot workflow notes, tool-session transcripts, assistant-specific instruction/configuration files, and machine-specific agent paths. Inspect each match in context. Known places to inspect include `docs/tooling/AI_TOOLBOX_AUDIT.md`, `docs/tooling/SYRMOS_TOOLBOX.md`, `docs/ASSISTANT_BRAINSTORM.md`, `docs/ops/NEXT_RELEASE_SESSION_STATE.md`, and `.github/PULL_REQUEST_TEMPLATE.md`. Remove assistant-only material; rewrite useful mixed-purpose material as factual product/engineering documentation without assistant attribution. Do not remove essential build, release, or QA automation merely because an assistant originally created it.
4. Interpret "AI presence" as development-assistant attribution and tooling residue. Preserve Ariadne's actual functionality, necessary model/provider integration, accurate product/privacy disclosures, required third-party license notices, and genuine human contributor attribution. Do not replace assistant attribution with a false human-authorship claim. Do not run a blind replacement of `AI`, which can corrupt unrelated identifiers and words. Record legitimate retained matches with their functional or legal reason in the final task report.
5. Fix every live reference to deleted files: Markdown links, README indexes, documentation navigation, workflow inputs, scripts, tests, generated manifests, packaging rules, and website assets. Ensure builds and CI no longer require deleted planning files. Remove the published copies from the website/generated artifacts and verify normal cache/service-worker updates remove any formerly served planning or assistant-only pages.
6. Review the deletion diff, run applicable documentation/link checks, builds and required CI, and merge the cleanup through the normal PR process. Verify the resulting **remote default-branch tree**, not only the local working directory: no tracked planning documents remain, no broken references remain, and contextual searches find no unexplained development-assistant residue. Delete this task's merged temporary remote branches once they have no dependents, using the normal branch cleanup process. Do not delete unrelated active branches.
7. Deploy the cleaned website from the cleanup commit and repeat affected production checks. If cleanup changes packaged app content or behavior, produce and verify replacement 3.0.0 builds with fresh build numbers. If it is strictly repository documentation cleanup, retain the verified mobile builds and record their source commits separately from the cleanup/deployed-web commit; do not imply they were built from a later revision.

This is a normal, reviewable deletion from the current remote repository tree. Do not rewrite Git history, force-push published branches, move release tags, delete old releases, or falsify historical commits/PR records to conceal past tooling. Historical revisions can still contain deleted files. Do not touch global assistant configuration or files outside this repository. State the scope of the completed cleanup accurately.

Before removing this prompt, preserve the remaining execution checklist and final evidence references in the task context or a temporary file outside the tracked repository so cleanup can finish and be verified. Do not create a replacement assistant transcript or planning document inside the repository.

## 8. Completion, persistence, and truthful handoff

Do not stop while the next necessary action is authorized and feasible. Do not hand back routine debugging, snapshot review, CI retries, merge operations, or release verification to the user. Never finish with “ready to merge,” “ready to release,” or “shall I continue?” when those actions can be completed.

A missing credential, mandatory external reviewer, unavailable required SDK/device, rejected tool authorization, store restriction, or external service outage is not solved by repeated blind retries or invented evidence. Diagnose the exact cause, try supported non-destructive recovery, complete all independent work, and preserve a precise resumption record. Do not bypass the boundary. Report the exact blocked operation and observed reason without asking generic confirmation or calling the whole task complete. Use **verified complete**, **failed**, **externally blocked**, or **unverified** per deliverable.

Maintain a durable release report with:

1. Status of all four outcomes and every acceptance case, with real runtime and snapshot evidence links.
2. PR URLs, merge commit, release tag, and successful required-check runs.
3. iOS/watchOS/widget version/build and actual TestFlight processing/availability status.
4. Android versionName/versionCode and verified Play internal-track status.
5. Web deployment run, production URL, deployed revision, and post-deployment smoke results; backend deployment evidence if changed.
6. Any missing device coverage, distribution checks, or external blockers, stated explicitly.
7. Cleanup PR and merged remote commit, removed planning locations, link/search validation results, any legitimate retained functional/legal references, and the cleaned production web revision. Keep the maintained report factual and free of development-assistant attribution.

**Success means the requested behavior is implemented, verified with inspected runtime evidence, merged, distributed as fresh 3.0.0 builds through the existing mobile channels, and running on the production website, with the final planning-document and development-assistant cleanup merged and verified on the remote default branch. A prompt, a green build, an uploaded artifact, an attractive screenshot, or a local-only deletion alone does not satisfy this mission.**
