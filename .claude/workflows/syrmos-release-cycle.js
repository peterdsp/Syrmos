export const meta = {
  name: 'syrmos-release-cycle',
  description: 'One autonomous Syrmos cycle: brainstorm, pick, build+test, verify, prepare a PR + beta plan',
  whenToUse: 'Run the Syrmos release squad for one improvement cycle; loop it for an hour/day.',
  phases: [
    { title: 'Brainstorm', detail: 'parallel idea agents across product angles' },
    { title: 'Select', detail: 'rank the pool, pick one buildable-now item, write backlog' },
    { title: 'Build', detail: 'implement + test across clients in an isolated worktree, open a PR' },
    { title: 'Verify', detail: 'adversarially confirm green + hygiene + cross-client' },
    { title: 'Release', detail: 'prepare the beta (version bumps + tag command); never merge or tag' },
  ],
}

// Knob (optional): bias brainstorming toward an area.
const focus = (args && args.focus) ? String(args.focus) : 'any area'
const SKILLS = '/Users/peterdsp/git/Syrmos/.claude/skills'

const IDEA_POOL = {
  type: 'object',
  properties: {
    ideas: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          name: { type: 'string' },
          problem: { type: 'string' },
          idea: { type: 'string' },
          dataSource: { type: 'string' },
          dataReachable: { type: 'boolean' },
          surfaces: { type: 'array', items: { type: 'string' } },
          shape: { type: 'string' },
          effort: { type: 'string' },
          risk: { type: 'string' },
          score: { type: 'number' },
        },
        required: ['name', 'idea', 'dataReachable', 'surfaces', 'effort', 'score'],
      },
    },
  },
  required: ['ideas'],
}

const SELECTION = {
  type: 'object',
  properties: {
    chosen: { type: 'object' },
    rationale: { type: 'string' },
    backlogWritten: { type: 'boolean' },
  },
  required: ['chosen', 'rationale', 'backlogWritten'],
}

const BUILD = {
  type: 'object',
  properties: {
    branch: { type: 'string' },
    filesTouched: { type: 'array', items: { type: 'string' } },
    surfaces: { type: 'array', items: { type: 'string' } },
    testCommands: { type: 'array', items: { type: 'string' } },
    green: { type: 'boolean' },
    tier: { type: 'string' },
    prOpened: { type: 'boolean' },
    prUrl: { type: 'string' },
    notes: { type: 'string' },
  },
  required: ['branch', 'surfaces', 'green', 'tier', 'notes'],
}

const VERIFY = {
  type: 'object',
  properties: {
    confirmed: { type: 'boolean' },
    green: { type: 'boolean' },
    hygieneOK: { type: 'boolean' },
    crossClientOK: { type: 'boolean' },
    issues: { type: 'array', items: { type: 'string' } },
  },
  required: ['confirmed', 'green', 'hygieneOK', 'crossClientOK', 'issues'],
}

const RELEASE = {
  type: 'object',
  properties: {
    actionTaken: { type: 'string' },
    versionBumpPlan: { type: 'string' },
    tagCommand: { type: 'string' },
    runlogWritten: { type: 'boolean' },
    summary: { type: 'string' },
  },
  required: ['actionTaken', 'runlogWritten', 'summary'],
}

const ANGLES = [
  { key: 'airport', hint: 'the airport hub experience (arrivals, flight-aware plans, live buses)' },
  { key: 'map-live', hint: 'the live map and vehicle freshness/telematics' },
  { key: 'journey-go', hint: 'journey planning, ranking, GO guidance and leave-by reminders' },
  { key: 'a11y-i18n-perf', hint: 'accessibility, localization parity, or performance' },
]

// 1. Brainstorm: a barrier is correct, the selector needs the whole pool at once.
phase('Brainstorm')
const pools = await parallel(ANGLES.map(a => () =>
  agent(
    `Read and follow the skill at ${SKILLS}/syrmos-ideate/SKILL.md. Brainstorm concrete, ` +
    `buildable-now Syrmos feature ideas focused on ${a.hint}. Bias toward ${focus}. ` +
    `Ground each in the real repo + memory, reality-check data/surfaces/identity, and score ` +
    `each 0-100 by value-to-effort. Do NOT re-propose already-shipped features. Read-only; do not edit.`,
    { label: `ideate:${a.key}`, phase: 'Brainstorm', schema: IDEA_POOL }
  )
))
const allIdeas = pools.filter(Boolean).flatMap(p => p.ideas)
log(`Brainstormed ${allIdeas.length} ideas across ${ANGLES.length} angles`)
if (!allIdeas.length) return { error: 'no ideas produced' }

// 2. Select: one agent ranks the pool, picks a single buildable-now item, writes the backlog.
phase('Select')
const selection = await agent(
  `You are the Syrmos squad selector. Here is the brainstormed idea pool as JSON:\n` +
  JSON.stringify(allIdeas) +
  `\nRank by value-to-effort and feasibility. Reject anything whose data is not reachable or ` +
  `that needs a blocked source. Pick the SINGLE best item to build THIS cycle (prefer a small, ` +
  `high-confidence pure-transform win over a large speculative build). Append the full ranked ` +
  `pool, dated via a shell \`date\` call, to docs/squad/BACKLOG.md (create it if missing; no ` +
  `em-dash). Return the chosen idea and your rationale.`,
  { phase: 'Select', schema: SELECTION }
)
log(`Selected: ${selection.chosen && selection.chosen.name ? selection.chosen.name : 'an item'}`)

// 3. Build: implement + test across clients in an isolated worktree, commit to a branch, open a PR.
phase('Build')
const build = await agent(
  `You are the Syrmos squad builder in an isolated git worktree. Implement this feature end to ` +
  `end by following ${SKILLS}/syrmos-feature-loop/SKILL.md (use syrmos-implement and syrmos-test ` +
  `as it directs; use syrmos-debug if something breaks):\n` +
  JSON.stringify(selection.chosen) +
  `\nTouch every affected client (iOS + Android + web; server only if required) and keep them in ` +
  `lockstep. Write tests and RUN them; runtime-verify where you can and report the honest tier. ` +
  `Then create a feature branch named syrmos-squad/<short-slug>, commit with a plain message ` +
  `(NO AI trailers, NO em-dash), push the branch, and open a PR with a what/why body (no AI ` +
  `attribution). Do NOT merge anything, do NOT bump versions, do NOT push any tag. Set green=true ` +
  `ONLY if new and existing tests actually passed. If you cannot get it green, open a DRAFT PR ` +
  `describing the blocker and set green=false.`,
  { phase: 'Build', schema: BUILD, isolation: 'worktree' }
)
log(`Build on ${build.branch}: green=${build.green}, surfaces=${(build.surfaces || []).join('+')}`)

// 4. Verify: an independent adversarial pass over what was actually produced.
phase('Verify')
const verify = await agent(
  `You are an adversarial verifier for the Syrmos squad. The builder reported:\n` +
  JSON.stringify(build) +
  `\nInspect the branch ${build.branch} (git log/diff, test logs, PR). Confirm, skeptically: ` +
  `(a) the tests genuinely ran and passed (not just compiled), (b) every client that should ` +
  `change actually changed and they stay in lockstep, (c) hygiene: no em-dash and no AI ` +
  `attribution anywhere in the commits/PR/diff. Default each check to false if you cannot ` +
  `confirm it. List concrete issues. Read-only; do not fix or merge.`,
  { phase: 'Verify', schema: VERIFY }
)
log(`Verify: confirmed=${verify.confirmed} issues=${verify.issues.length}`)

// 5. Release prep only: the squad never merges a PR or pushes a tag. It leaves them ready.
phase('Release')
const shipReady = Boolean(build.green && verify.confirmed && verify.green && verify.hygieneOK && verify.crossClientOK)
const release = await agent(
  `You are the Syrmos squad release manager. Follow ${SKILLS}/syrmos-deploy/SKILL.md. ` +
  `The feature is on branch ${build.branch}; PR ${build.prUrl || '(see branch)'}. ` +
  `Ship-ready = ${shipReady} (builder.green and the verifier confirmed green + hygiene + cross-client).\n` +
  `You do NOT merge the PR and you do NOT push any tag: merging to master and tagging a release ` +
  `are human-reviewed steps, out of scope. Instead PREPARE the beta so a human can ship with one ` +
  `review: as versionBumpPlan state the exact iOS build + Android versionCode bump (never a burned ` +
  `code), and as tagCommand give the exact \`git tag v3.0.0-beta.NN && git push origin v3.0.0-beta.NN\` ` +
  `a human runs AFTER merging the PR. If ship-ready is false, put what blocks it in summary instead. ` +
  `Append a dated entry (shell \`date\`) to docs/squad/RUNLOG.md: what was brainstormed, what was ` +
  `built, the test/verify result, and what awaits review (no em-dash). Return what you prepared.`,
  { phase: 'Release', schema: RELEASE }
)

return {
  focus,
  shipReady,
  chosen: selection.chosen && selection.chosen.name,
  branch: build.branch,
  prUrl: build.prUrl,
  green: build.green,
  verifyConfirmed: verify.confirmed,
  verifyIssues: verify.issues,
  versionBumpPlan: release.versionBumpPlan,
  tagCommand: release.tagCommand,
  summary: release.summary,
}
