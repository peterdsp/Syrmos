# Model command wrappers

`ppc run` reaches its proposer/composer/probe model through one external command,
`ARIADNE_PPC_MODEL_CMD`, following a tiny contract: the prompt arrives on stdin,
the completion is written to stdout. Nothing runs or costs anything unless you
set this and call `ppc run` yourself.

Two wrappers ship here, both free:

- `github_models_cmd.py` - calls an OpenAI-compatible endpoint, defaulting to
  **GitHub Models**, which is free from inside GitHub Actions via the built-in
  `GITHUB_TOKEN` (`permissions: models: read`). No account, no card, no secret,
  nothing on your machine. Stdlib only. This is what the CI workflow uses.
- `mock_cmd.py` - deterministic, offline, credential-free. For smoke-testing the
  pipeline; it does not do real refinement.

## Running in GitHub Actions (free)

The workflow `.github/workflows/ariadne-ppc.yml` runs the loop on GitHub's
runners using GitHub Models. Trigger it from the Actions tab (workflow_dispatch).
It uploads the candidate pack and `summary.json` as build artifacts and never
writes to `docs/ariadne` or commits.

## Running the wrapper yourself (also free, if you want)

```bash
cd scripts/ariadne-ppc
export ARIADNE_PPC_MODEL_CMD='python3 model_cmd/github_models_cmd.py'
export ARIADNE_PPC_OPENAI_TOKEN=<a GitHub token with models:read>   # or GITHUB_TOKEN
python3 -m ppc.run run --out runs/ppc-1 --limit 12
```

`--limit N` uses only the first N tasks, which keeps the number of model calls
within free-tier rate limits (each task is probed under base + 2 candidates +
the composition, so calls are roughly `4*N + 3`).

Env overrides: `ARIADNE_PPC_OPENAI_BASE_URL` (default the GitHub Models endpoint),
`ARIADNE_PPC_OPENAI_MODEL` (default `openai/gpt-4o-mini`). Point the base URL at a
local Ollama server (`http://localhost:11434/v1`) to run a free local model
instead; the same wrapper works unchanged.

## Offline, no model at all

The scoring, gate, validation, golden suite, and unit tests need no model:

```bash
python3 tests/test_ppc.py
python3 -m ppc.run score --pred PREDS.jsonl
python3 -m ppc.run gate --base B.jsonl --cand C.jsonl
```
