# Syrmos release squad

An autonomous multi-agent loop that brainstorms, builds, tests, and prepares beta releases
for Syrmos, using the `syrmos-*` skills. It opens reviewed PRs and prepares the beta; a human
does the final merge and tag. It never rewrites git history, merges to master, pushes a tag,
or does a public store release on its own.

## Pieces

- `.claude/agents/syrmos-release-squad.md` - the operator persona and its safety envelope.
- `.claude/workflows/syrmos-release-cycle.js` - one cycle as a multi-agent fan-out:
  Brainstorm (4 parallel idea agents) -> Select (rank + pick + write backlog) -> Build
  (implement + test in an isolated worktree, open a PR) -> Verify (adversarial) -> Release
  (prepare version bumps + the exact tag command; no merge, no tag).
- `docs/squad/BACKLOG.md` - every brainstormed idea, ranked, appended each cycle.
- `docs/squad/RUNLOG.md` - one dated entry per cycle: brainstormed / built / verified / awaiting review.

## Run one cycle

```
# default (brainstorm across all angles)
Workflow(name: "syrmos-release-cycle")

# bias the brainstorm toward an area
Workflow(name: "syrmos-release-cycle", args: { focus: "airport" })
```

A `<task-notification>` arrives when the cycle finishes; `/workflows` shows live progress.
The result names the PR it opened and the prepared `tagCommand`.

## Run it for an hour or a day

The cycle is one fan-out. To keep improving for a window, repeat it on an interval:

```
# re-run the cycle every 45 minutes, self-paced; stop it with /loop again or Ctrl-C
/loop 45m Workflow(name: "syrmos-release-cycle")

# or schedule it as a recurring cloud routine (see the /schedule skill)
/schedule every 2 hours: run the syrmos-release-cycle workflow
```

Pick an interval longer than a cycle takes (a cycle with real builds can run 15-40 min), so
cycles do not overlap and contend for the one iOS simulator and the gradle/Xcode toolchain.

## What you review after each cycle

1. The PR the squad opened (the real change, its tests, the verifier verdict).
2. `RUNLOG.md` for the honest per-surface verification tier and any blocker.
3. If you are happy: merge the PR, then run the prepared `tagCommand` to cut the beta
   (`v3.0.0-beta.NN`), which ships to TestFlight + Play internal only. Bump the iOS build +
   Android `versionCode` first per the squad's `versionBumpPlan` (never reuse a burned code).

## Safety (built in, do not weaken)

- No history rewrite, force-push, hard reset, rebase shortcut, or hook bypass.
- No merging a PR to master and no pushing a tag, autonomously. The squad prepares; you ship.
- No public App Store / Play production release. Beta/internal only, and only by your hand.
- Green before ship: nothing is marked ship-ready on "it compiles"; tests must run and pass,
  independently confirmed by the verify stage.
- No AI attribution, no em-dash, one feature per cycle, stop on repeated failure.
