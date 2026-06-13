# Contribution workflow

Three-line summary so the reflexes are easy:

1. Never push to `main` directly. Open a branch + PR.
2. CI must be green before merge. Re-run, don't override.
3. If something does land broken, the post-merge guard opens a draft
   revert PR — accept it or push a fix-forward, your call.

## Branching

```
main                ← protected, always deployable
  └─ feature/<thing>          (your branch)
       └─ commits
       └─ open PR → review → merge
```

Branch naming is free-form: `fix/`, `feat/`, `chore/`, `docs/` — whatever
helps you. The only requirement is that it isn't `main`.

## CI gates that run on every PR

| Workflow | Triggers | What it checks |
|---|---|---|
| `ci.yml` | push / PR to main | Verifier + KMP/Android/iOS module builds + unit tests |
| `seed-refresh.yml` | nightly cron | Pulls live API into bundled seeds, fails if seeds break the offline-first contract |
| `pages.yml` | push to main, web file changes | Builds + deploys the web app to syrmos.peterdsp.dev |
| `gitleaks.yml` | push / PR | Secret scanning |
| `main-guard.yml` | push to main | If CI fails, opens a draft revert PR + tracking issue |

## Reviewing your own PRs

The repo is single-maintainer right now, so PRs route back to you. The
value of opening the PR anyway:

- CI must pass before merge — caught regressions never land.
- The diff has a clean review surface for self-review.
- Future contributors see the precedent.

## Enabling branch protection (one-time setup)

This isn't done automatically. Run once, from your laptop after `gh auth
login`:

```bash
gh api -X PUT "/repos/peterdsp/Syrmos/branches/main/protection" \
  -H "Accept: application/vnd.github+json" \
  -f required_status_checks.strict=true \
  -F 'required_status_checks.contexts[]=build' \
  -F required_pull_request_reviews.required_approving_review_count=1 \
  -f restrictions=null \
  -f enforce_admins=false
```

What that enables:

- `main` can only be updated through a PR.
- The `build` status check from `ci.yml` must succeed.
- The PR needs 1 approval (you, against your own PR is allowed by
  default; flip `dismiss_stale_reviews=true` if you want approvals to
  re-trigger on push).
- Admins can still force-push in a genuine emergency
  (`enforce_admins=false`).

## When the guard opens a revert PR

You'll get a GitHub notification (email + mobile push if enabled). The
PR title looks like `Auto-revert <sha> (CI failed on main)`.

Pick one:

1. **Accept the revert.** Click merge on the draft PR. Main goes back to
   the last green commit.
2. **Fix-forward.** Push a follow-up commit on `main` (or via another
   PR) that fixes the regression. Close the revert PR. The guard rearms
   on the next push.

The guard doesn't auto-merge anything — it just stages the revert so
you can do it in one click instead of typing `git revert` from your
phone.

## What's deliberately not automated

- Auto-merge of the revert PR. A bad fix-forward attempt could be worse
  than the original break; humans pick.
- Snapshot tests for SwiftUI views. The wiring needs a proper test
  target + `swift-snapshot-testing` SPM dependency + per-device
  baselines. Tracked separately — current guard relies on
  `verify-bundles.py` + KMP unit tests + iOS build success.
- App Store / Play Store upload. Both review stores reject machine
  uploads of binaries that haven't been notarised / signed with a real
  certificate, and the credentials don't belong in CI yet.
