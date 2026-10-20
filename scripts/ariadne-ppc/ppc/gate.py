"""The CI gate. A refined pack ships only if it helps and regresses nothing core."""

from __future__ import annotations

from dataclasses import dataclass

from .adapter import Prediction, Report, Task, case_pass, score


@dataclass
class GateResult:
    accepted: bool
    reasons: list[str]
    base: Report
    candidate: Report
    core_regressions: list[str]

    def to_dict(self) -> dict:
        return {
            "accepted": self.accepted,
            "reasons": self.reasons,
            "base_accuracy": round(self.base.accuracy, 4),
            "candidate_accuracy": round(self.candidate.accuracy, 4),
            "core_regressions": self.core_regressions,
        }


def evaluate(
    tasks: list[Task],
    base_preds: dict[str, Prediction],
    cand_preds: dict[str, Prediction],
) -> GateResult:
    base = score(tasks, base_preds)
    cand = score(tasks, cand_preds)

    # A core (non-gap) case that the base passed and the candidate fails.
    core_regressions = [
        t.id
        for t in tasks
        if not t.schema_gap
        and case_pass(t, base_preds.get(t.id))
        and not case_pass(t, cand_preds.get(t.id))
    ]

    reasons: list[str] = []
    if core_regressions:
        reasons.append(f"regressed {len(core_regressions)} core case(s): {core_regressions}")
    if cand.accuracy <= base.accuracy:
        reasons.append(
            f"no improvement: candidate {cand.accuracy:.3f} <= base {base.accuracy:.3f}"
        )

    accepted = not core_regressions and cand.accuracy > base.accuracy
    if accepted:
        reasons.append(
            f"accept: {base.accuracy:.3f} -> {cand.accuracy:.3f}, no core regression"
        )
    return GateResult(accepted, reasons, base, cand, core_regressions)
