# Ariadne PPC golden suite

Dev-time Propose-Probe-Compose optimizer for Ariadne's **LLM contract** (the
model-facing understanding layer), adapted from the Harness2 method
(google-research/harness2). This folder holds the golden task suite and the
scoring contract. The loop runner is added separately once this contract is
agreed.

## What PPC may edit (the harness surface)

Only the model-facing data files, never app code or bundled facts:

- `docs/ariadne/ariadne_prompt_pack.md` (system prompt, rules, few-shot routing)
- `docs/ariadne/ariadne_intent_schema.json` (per-intent descriptions, few-shot;
  NOT the `allowedLineIds` / `dayContextValues` / intent `const` enums)
- `docs/ariadne/ariadne_tool_contracts.json` (the `description` fields)
- `docs/ariadne/ariadne_rag_chunks.jsonl` (chunk selection and tagging)

## What stays frozen (the safety invariant, equivalent to "fixed weights")

- `core/domain/assistant/AthensTransitParser.kt` (deterministic fallback)
- `core/domain/assistant/IntentGrounder.kt` (quotes -> canonical ids)
- deterministic dispatch and the bundled snapshot (manifest 179)
- the hard enums listed above

A proposal that touches anything frozen is rejected and retried once, then falls
back to the current pack. This mirrors Harness2 `_accept_tree` plus its
single-retry rule.

## Task record schema (`tasks.jsonl`, one JSON object per line)

```json
{
  "id": "en-departures-syntagma",      // stable, kebab-case
  "lang": "en",                         // en | el | sq | it
  "utterance": "next trains from Syntagma",
  "expect": {
    "intent": "showDepartures",         // an intent the dispatch can serve
    "slots": {                          // only keys that must match; omit the rest
      "stationId": "M2_SYN",
      "dayContext": "today"
    }
  },
  "schema_gap": false,                  // true = dispatch serves it but the LLM
                                        //   contract does not yet; PPC must close it
  "source": "AriadneParserTests.swift:25",
  "notes": ""
}
```

Slot keys use the intent-schema field names: `stationId`, `lineId`,
`fromStationId`, `toStationId`, `airport`, `lowExposure`, `dayContext`,
`query`, and for `needsClarification`: `missing` + `baseIntent`.

Station / line ids are canonical bundle ids (e.g. `M2_SYN` Syntagma,
`M1_PIR` Piraeus, `M3_AER` Airport, `M1_MON` Monastiraki). Never invent an id.

## Scoring contract (the adapter's `score`)

Per task:

- **intent_match**: predicted intent equals `expect.intent`.
- **slot_match**: every key in `expect.slots` resolves (through the real
  `IntentGrounder`) to the given value. Keys not listed are ignored.
- **no_fabrication** (phase 2, once full answers are wired): the composed answer
  contains no transit fact absent from a tool result.

**Primary metric**: routing accuracy = fraction with `intent_match` AND
`slot_match`, reported split by `lang` and by `schema_gap`.

## The CI gate

A refined pack is accepted only if, against this suite, it:

1. does not regress any `schema_gap: false` case, and
2. improves overall routing accuracy (chiefly by converting `schema_gap: true`
   cases), and
3. still passes `AriadneParserTests.swift` / `AriadneContextParserTest.kt`
   (grounding untouched).

On acceptance: overwrite the edited pack files, refresh
`docs/ariadne/checksums.sha256`, bump the manifest, rebundle. Lessons that won
are appended to the improvement harness (Kujto: `memory/domains/ios/`).

## Running it

Offline (no model, no credentials):

```bash
cd scripts/ariadne-ppc
python3 tests/test_ppc.py                       # pure-core tests
python3 -m ppc.run score --pred PREDS.jsonl     # score a predictions file
python3 -m ppc.run gate --base B.jsonl --cand C.jsonl
```

Full Propose-Probe-Compose (needs a model). Pick a wrapper from `model_cmd/`
(see `model_cmd/README.md`): `anthropic_cmd.py`, `vertex_cmd.py`, or the
credential-free `mock_cmd.py` for a pipeline smoke test.

```bash
export ARIADNE_PPC_MODEL_CMD='python3 model_cmd/anthropic_cmd.py'
python3 -m ppc.run run --out runs/ppc-1
```

`run` writes the candidate pack, base/candidate predictions, and `summary.json`
to the out dir, prints the gate verdict, and exits non-zero when the gate
rejects. It never overwrites `docs/ariadne`.

## First cut

Parallel, k=1 (one INV candidate, one DOM candidate, one compose), this suite as
the task set.
