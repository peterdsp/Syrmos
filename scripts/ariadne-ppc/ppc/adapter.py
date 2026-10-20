"""Tasks, predictions, and scoring. Pure: no model, no network, no file writes.

A prediction is the model's structured output for one utterance, already carrying
CANONICAL ids (the on-device grounder's alias resolution is covered by the Kotlin
and Swift tests, not re-implemented here). Scoring compares intent and the slots
the task pins; slots a task does not list are ignored.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path


@dataclass(frozen=True)
class Task:
    id: str
    lang: str
    utterance: str
    intent: str
    slots: dict
    schema_gap: bool
    source: str = ""
    notes: str = ""


@dataclass(frozen=True)
class Prediction:
    intent: str
    slots: dict = field(default_factory=dict)


def load_tasks(path: Path) -> list[Task]:
    tasks: list[Task] = []
    for n, line in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), 1):
        line = line.strip()
        if not line:
            continue
        try:
            row = json.loads(line)
        except json.JSONDecodeError as exc:
            raise ValueError(f"{path}:{n}: invalid JSON: {exc}") from exc
        exp = row.get("expect", {})
        tasks.append(
            Task(
                id=row["id"],
                lang=row["lang"],
                utterance=row["utterance"],
                intent=exp["intent"],
                slots=dict(exp.get("slots", {})),
                schema_gap=bool(row.get("schema_gap", False)),
                source=row.get("source", ""),
                notes=row.get("notes", ""),
            )
        )
    ids = [t.id for t in tasks]
    dupes = sorted({i for i in ids if ids.count(i) > 1})
    if dupes:
        raise ValueError(f"duplicate task ids: {dupes}")
    return tasks


def load_predictions(path: Path) -> dict[str, Prediction]:
    """A JSONL of {"id", "intent", "slots"} keyed by task id."""
    out: dict[str, Prediction] = {}
    for n, line in enumerate(Path(path).read_text(encoding="utf-8").splitlines(), 1):
        line = line.strip()
        if not line:
            continue
        row = json.loads(line)
        out[row["id"]] = Prediction(intent=row["intent"], slots=dict(row.get("slots", {})))
    return out


def intent_match(task: Task, pred: Prediction) -> bool:
    return pred.intent == task.intent


def slot_match(task: Task, pred: Prediction) -> bool:
    # Every slot the task pins must be present and equal. Type-exact, so a string
    # "true" never passes for a boolean true.
    for key, want in task.slots.items():
        if key not in pred.slots or pred.slots[key] != want:
            return False
    return True


def case_pass(task: Task, pred: Prediction | None) -> bool:
    if pred is None:
        return False
    return intent_match(task, pred) and slot_match(task, pred)


@dataclass
class Report:
    total: int
    passed: int
    by_lang: dict
    by_gap: dict
    failures: list  # (task_id, reason)

    @property
    def accuracy(self) -> float:
        return self.passed / self.total if self.total else 0.0

    def to_dict(self) -> dict:
        return {
            "total": self.total,
            "passed": self.passed,
            "accuracy": round(self.accuracy, 4),
            "by_lang": self.by_lang,
            "by_gap": self.by_gap,
            "failures": self.failures,
        }


def score(tasks: list[Task], preds: dict[str, Prediction]) -> Report:
    passed = 0
    by_lang: dict[str, list[int]] = {}
    by_gap: dict[str, list[int]] = {"gap": [0, 0], "core": [0, 0]}
    failures: list = []
    for t in tasks:
        pred = preds.get(t.id)
        ok = case_pass(t, pred)
        passed += int(ok)
        by_lang.setdefault(t.lang, [0, 0])
        by_lang[t.lang][0] += int(ok)
        by_lang[t.lang][1] += 1
        bucket = "gap" if t.schema_gap else "core"
        by_gap[bucket][0] += int(ok)
        by_gap[bucket][1] += 1
        if not ok:
            if pred is None:
                failures.append((t.id, "no prediction"))
            elif not intent_match(t, pred):
                failures.append((t.id, f"intent {pred.intent!r} != {t.intent!r}"))
            else:
                failures.append((t.id, f"slots {pred.slots} miss {t.slots}"))
    fmt = lambda d: {k: {"passed": v[0], "total": v[1]} for k, v in d.items()}
    return Report(len(tasks), passed, fmt(by_lang), fmt(by_gap), failures)
