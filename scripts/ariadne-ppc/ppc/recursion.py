"""Propose-Probe-Compose, parallel k=1 (one INV candidate, one DOM candidate,
one compose), the cheapest useful step. Faithful to harness2: propose edits the
pack, probe runs it on the shared base task set, compose merges from the
behavioral contrast, and an invalid proposal gets one retry then falls back to
the current pack.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field

from . import harness
from .adapter import Prediction, Report, Task, score
from .model import ModelError, _first_json_object, complete, probe

STAGE_ATTEMPTS = 2

_DIM_BRIEF = {
    "INV": (
        "INVARIANT edit: general, task-agnostic improvements that transfer to any "
        "query (clearer tool-selection and clarification rules, tighter output format, "
        "stronger 'call a tool before stating a fact' guardrail)."
    ),
    "DOM": (
        "DOMAIN edit: Athens-rail-specific improvements (station-name folding across "
        "English, Greek, Greeklish, Albanian and Italian; airport branch M3 vs M3_AIR; "
        "T7 loop and A4 curve; fare airport=true; and ADD any intent the app already "
        "serves but the schema omits, e.g. firstTrain, stationStatus, setCurrentLocation, "
        "reverseTrip, stationAccessibility)."
    ),
}


@dataclass
class Stage:
    label: str
    dim: str
    pack: dict
    notes: str = ""
    error: str = ""
    kept_base: bool = False
    fell_back: bool = False


@dataclass
class StepResult:
    base_pack: dict
    base_preds: dict
    base_report: Report
    stages: list = field(default_factory=list)
    composed: Stage | None = None
    composed_preds: dict = field(default_factory=dict)
    composed_report: Report | None = None


def _evidence(report: Report) -> str:
    lines = [f"Base routing accuracy: {report.accuracy:.3f} ({report.passed}/{report.total})."]
    if report.failures:
        lines.append("Failing cases (task_id: reason):")
        lines += [f"  {tid}: {reason}" for tid, reason in report.failures]
    return "\n".join(lines)


def _harvest(reply: str, base_pack: dict) -> dict:
    """A reply is one JSON object mapping changed filename -> full new content."""
    changed = _first_json_object(reply)
    if not isinstance(changed, dict) or not all(isinstance(v, str) for v in changed.values()):
        raise ModelError("expected a JSON object of {filename: full_file_contents}")
    return {**base_pack, **changed}


def _propose(label: str, dim: str, base_pack: dict, evidence: str, cmd: str) -> Stage:
    prompt_head = (
        f"You are refining Ariadne's LLM contract. {_DIM_BRIEF[dim]}\n\n"
        "You may edit ONLY these files: " + ", ".join(harness.SURFACE_FILES) + ".\n"
        "Do not change allowedLineIds or dayContextValues, and never remove an existing "
        "intent. Return a single JSON object mapping each file you changed to its FULL new "
        "contents, then a <notes>...</notes> line explaining the edit.\n\n"
        f"# Evidence from the base run\n{evidence}\n\n"
        "# Current pack files\n"
    )
    body = "\n".join(
        f"=== {rel} ===\n{base_pack[rel]}" for rel in harness.SURFACE_FILES if rel in base_pack
    )
    err = ""
    for _ in range(STAGE_ATTEMPTS):
        reply = complete(
            prompt_head + body + (f"\n\n# YOUR PREVIOUS ATTEMPT WAS REJECTED\n{err}\nFix it." if err else ""),
            cmd,
        )
        try:
            merged = _harvest(reply, base_pack)
        except ModelError as exc:
            err = str(exc)
            continue
        errors = harness.validate(base_pack, merged)
        if errors:
            err = " ".join(errors)
            continue
        notes = reply.split("<notes>", 1)[-1].split("</notes>", 1)[0].strip() if "<notes>" in reply else ""
        kept = merged == base_pack
        return Stage(label, dim, merged, notes=notes, kept_base=kept)
    return Stage(label, dim, dict(base_pack), error=err[:400], fell_back=True)


def _compose(base_pack: dict, stages: list, reports: dict, cmd: str) -> Stage:
    contrast = "\n".join(
        f"Candidate {s.label} ({s.dim}): accuracy {reports[s.label].accuracy:.3f}; "
        f"notes: {s.notes or s.error or 'none'}"
        for s in stages
    )
    prompt = (
        "Compose ONE refined Ariadne pack by merging the edits from the candidates below "
        "that improved routing, keeping the deterministic safety rules intact. Edit only "
        + ", ".join(harness.SURFACE_FILES)
        + "; keep allowedLineIds and dayContextValues unchanged; never drop an intent. "
        "Return a single JSON object {filename: full new contents} then <notes>...</notes>.\n\n"
        f"# Candidate contrast\n{contrast}\n\n"
        "# Base pack\n"
        + "\n".join(f"=== {rel} ===\n{base_pack[rel]}" for rel in base_pack)
        + "\n\n# Candidate packs (JSON of changed files)\n"
        + "\n".join(f"--- {s.label} ---\n{json.dumps(s.pack, ensure_ascii=False)}" for s in stages)
    )
    err = ""
    for _ in range(STAGE_ATTEMPTS):
        reply = complete(prompt + (f"\n\n# REJECTED\n{err}\nFix it." if err else ""), cmd)
        try:
            merged = _harvest(reply, base_pack)
        except ModelError as exc:
            err = str(exc)
            continue
        errors = harness.validate(base_pack, merged)
        if errors:
            err = " ".join(errors)
            continue
        notes = reply.split("<notes>", 1)[-1].split("</notes>", 1)[0].strip() if "<notes>" in reply else ""
        return Stage("compose", "COMPOSE", merged, notes=notes, kept_base=(merged == base_pack))
    return Stage("compose", "COMPOSE", dict(base_pack), error=err[:400], fell_back=True)


def run_step(base_pack: dict, tasks: list[Task], cmd: str, vocab_hint: str = "") -> StepResult:
    base_preds = probe(base_pack, tasks, cmd, vocab_hint)
    base_report = score(tasks, base_preds)
    evidence = _evidence(base_report)

    stages = [
        _propose("inv1", "INV", base_pack, evidence, cmd),
        _propose("dom1", "DOM", base_pack, evidence, cmd),
    ]
    reports = {s.label: score(tasks, probe(s.pack, tasks, cmd, vocab_hint)) for s in stages}

    composed = _compose(base_pack, stages, reports, cmd)
    composed_preds = probe(composed.pack, tasks, cmd, vocab_hint)
    composed_report = score(tasks, composed_preds)

    return StepResult(
        base_pack=base_pack,
        base_preds=base_preds,
        base_report=base_report,
        stages=stages,
        composed=composed,
        composed_preds=composed_preds,
        composed_report=composed_report,
    )
