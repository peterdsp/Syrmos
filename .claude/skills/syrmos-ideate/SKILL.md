---
name: syrmos-ideate
description: >-
  Generate and pressure-test new feature ideas for the Syrmos transit app before any code
  is written. Use this skill whenever the user is thinking about what to build rather than
  how: "what should I build next", "any ideas for", "what's missing", "how could we improve
  X", "is this feature worth doing", "brainstorm", "what would make this better", or when a
  rough idea needs shaping into something concrete enough to plan. It produces short,
  honest idea briefs grounded in what Syrmos already ships, what its data sources can
  actually support, and what the four-surface cost really is, so ideas do not die at the
  feasibility wall later. Hand off to syrmos-feature-loop once an idea is chosen.
---

# Syrmos ideation

The job here is to turn a vague wish into a concrete, buildable idea brief, or to propose
good candidates, without pretending the hard constraints do not exist. Syrmos is a real
shipping app with real data limits; an idea that ignores them wastes a planning cycle.

## What Syrmos is (the north star)

A Greek public-transport companion (Athens + Thessaloniki + regional rail) whose identity is:
- **Honesty over optimism.** Live data is labeled by freshness and confidence; it never
  invents a train or hides that a position is stale. A new idea must preserve this.
- **Comfortable-first.** The default journey ranking is RECOMMENDED (comfortable), not
  fastest. Features should respect that the user may not want the frantic option.
- **Four languages** (en/el/sq/it) and **accessibility** are first-class, not afterthoughts.
- **Desktop web, native mobile.** Phones/tablets on web are redirected to the app; the web
  app is a desktop experience. An idea that only makes sense on a phone belongs on mobile.

## Where good ideas come from

- **Gaps in shipped features.** Do not re-propose what already exists. The user's memory
  index (`memory/MEMORY.md`) lists shipped areas: departures grouping, GO journey glances,
  leave-by reminders, airport hubs, live vehicle freshness, interchange geometry, foldables/
  Duo, Ariadne assistant, i18n. Skim it first and look for the adjacent missing piece.
- **Established transit-app practice** (Transit, Citymapper, Google Maps transit, Moovit):
  what do mature apps do that Syrmos does not yet? Grounding an idea in known practice is a
  strength, cite it. Several shipped features (departures grouping, confidence language)
  came this way.
- **Real user pain** in the local context: OASA reliability, strikes, construction
  (suspended lines), seasonal railways, airport transfers, tourist vs commuter needs.

## Reality-check every idea against these

An idea is only worth planning if it survives these questions. Put the answers in the brief.

1. **Data: can we actually get it?** Sources are the bundled seed (schedules/lines), OASA
   telematics (live buses/trains, via the Pi API), and OSM geometry. Hard limit: Overpass
   is blocked in the dev sandbox, so new route geometry needs the Pi or a host with
   internet. If the idea needs data no source provides, say so plainly.
2. **Surfaces: what is the real cost?** A user-visible behavior usually means iOS + Android
   + web, sometimes + server. A "small" idea that touches all four is not small. State which
   surfaces it hits.
3. **Server or client-only?** Client-only (pure transform over existing data) is cheap.
   Anything needing new scraping, a new endpoint, or Ariadne changes pulls in the Pi and a
   deploy.
4. **Does it fit the identity?** Honest-data, comfortable-first, a11y, 4-lang. An idea that
   would require faking certainty or dropping a language is a red flag, not a feature.
5. **Is it a pure transform?** If the logic is pure (ranking, grouping, projection), it is a
   clean candidate: one shared rule mirrored per client, easy to test. Favor these.

## Output: the idea brief

For each idea, produce this compact brief. Keep it honest and short; this is a decision
aid, not a spec.

```
### <Idea name>
- Problem: who hurts today and how
- Idea: one or two sentences of what it does
- Prior art: the transit app(s) that do something like this, if any
- Data needed: which source, and whether it exists / is reachable
- Surfaces: iOS / Android / web / server (and why)
- Shape: pure transform? new UI? new endpoint?
- Rough effort: S / M / L, with the main cost driver
- Risks / open questions: the thing most likely to kill it
```

When proposing several, rank them by value-to-effort and say which one you would pick and
why. When the user settles on one, hand off to **syrmos-feature-loop** to plan and build it.
