"""CLI. Run from scripts/ariadne-ppc:  python -m ppc.run <command> ...

  score --pred PREDS [--tasks TASKS]
      Score a predictions JSONL against the golden suite. Offline, no model.

  gate --base BASE --cand CAND [--tasks TASKS]
      Compare two predictions files through the CI gate. Offline, no model.

  run [--out DIR] [--tasks TASKS]
      Full Propose-Probe-Compose, k=1. Needs ARIADNE_PPC_MODEL_CMD. Writes the
      candidate pack and predictions under DIR and prints the gate verdict. It
      NEVER overwrites docs/ariadne; a human reviews and copies an accepted pack.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from . import gate as gate_mod
from . import harness
from .adapter import load_predictions, load_tasks, score
from .config import Settings


def _tasks_arg(a) -> list:
    return load_tasks(Path(a.tasks) if a.tasks else Settings().tasks_path)


def cmd_score(a) -> int:
    tasks = _tasks_arg(a)
    report = score(tasks, load_predictions(Path(a.pred)))
    print(json.dumps(report.to_dict(), indent=2, ensure_ascii=False))
    return 0


def cmd_gate(a) -> int:
    tasks = _tasks_arg(a)
    result = gate_mod.evaluate(
        tasks, load_predictions(Path(a.base)), load_predictions(Path(a.cand))
    )
    print(json.dumps(result.to_dict(), indent=2, ensure_ascii=False))
    return 0 if result.accepted else 1


def cmd_run(a) -> int:
    from . import model, recursion, vocab  # imported here so score/gate stay model-free

    settings = Settings.from_env()
    if not settings.model_cmd:
        print("error: set ARIADNE_PPC_MODEL_CMD (see ppc/config.py)", file=sys.stderr)
        return 2
    tasks = _tasks_arg(a)
    if a.limit:
        tasks = tasks[: a.limit]
    base_pack = harness.read_pack(settings.pack_dir)
    if not base_pack:
        print(f"error: no pack files found under {settings.pack_dir}", file=sys.stderr)
        return 2

    vocab_hint = vocab.build_hint(settings.pack_dir)
    step = recursion.run_step(base_pack, tasks, settings.model_cmd, vocab_hint=vocab_hint)
    out = Path(a.out or "runs/ppc-latest")
    out.mkdir(parents=True, exist_ok=True)
    harness.write_pack(step.composed.pack, out / "candidate_pack")
    model.save_predictions(step.base_preds, out / "base_preds.jsonl")
    model.save_predictions(step.composed_preds, out / "candidate_preds.jsonl")

    result = gate_mod.evaluate(tasks, step.base_preds, step.composed_preds)
    summary = {
        "gate": result.to_dict(),
        "stages": [
            {"label": s.label, "dim": s.dim, "fell_back": s.fell_back, "kept_base": s.kept_base,
             "notes": s.notes, "error": s.error}
            for s in step.stages
        ],
        "compose": {"fell_back": step.composed.fell_back, "notes": step.composed.notes},
        "out_dir": str(out),
    }
    (out / "summary.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(summary, indent=2, ensure_ascii=False))
    print(
        f"\ncandidate pack written to {out / 'candidate_pack'} "
        f"({'ACCEPTED' if result.accepted else 'REJECTED'}). "
        "Review before copying into docs/ariadne.",
        file=sys.stderr,
    )
    return 0 if result.accepted else 1


def main(argv=None) -> int:
    p = argparse.ArgumentParser(prog="ppc", description=__doc__)
    sub = p.add_subparsers(dest="cmd", required=True)

    s = sub.add_parser("score")
    s.add_argument("--pred", required=True)
    s.add_argument("--tasks")
    s.set_defaults(fn=cmd_score)

    g = sub.add_parser("gate")
    g.add_argument("--base", required=True)
    g.add_argument("--cand", required=True)
    g.add_argument("--tasks")
    g.set_defaults(fn=cmd_gate)

    r = sub.add_parser("run")
    r.add_argument("--out")
    r.add_argument("--tasks")
    r.add_argument("--limit", type=int, help="use only the first N tasks (keeps CI within free-tier limits)")
    r.set_defaults(fn=cmd_run)

    a = p.parse_args(argv)
    return a.fn(a)


if __name__ == "__main__":
    raise SystemExit(main())
