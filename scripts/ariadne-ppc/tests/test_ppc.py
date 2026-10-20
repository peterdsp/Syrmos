"""Offline checks for the pure core: task loading, scoring, gate, pack validation.
No model, no network. Run from scripts/ariadne-ppc:  python3 tests/test_ppc.py
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

from ppc import harness  # noqa: E402
from ppc.adapter import Prediction, Task, load_tasks, score  # noqa: E402
from ppc.gate import evaluate  # noqa: E402

TASKS = HERE.parent / "tasks.jsonl"


def _t(id, intent, slots, gap=False, lang="en"):
    return Task(id=id, lang=lang, utterance="x", intent=intent, slots=slots, schema_gap=gap)


def test_golden_suite_loads():
    tasks = load_tasks(TASKS)
    assert len(tasks) >= 30, len(tasks)
    assert {t.lang for t in tasks} == {"en", "el", "sq", "it"}
    assert any(t.schema_gap for t in tasks) and any(not t.schema_gap for t in tasks)


def test_scoring_intent_and_slots():
    tasks = [
        _t("a", "showDepartures", {"stationId": "M2_SYN", "dayContext": "today"}),
        _t("b", "lastTrain", {"stationId": "M1_PIR"}),
        _t("c", "outOfScope", {}),
    ]
    preds = {
        "a": Prediction("showDepartures", {"stationId": "M2_SYN", "dayContext": "today", "lineId": None}),
        "b": Prediction("lastTrain", {"stationId": "WRONG"}),  # slot miss
        # c absent -> no prediction
    }
    r = score(tasks, preds)
    assert r.passed == 1 and r.total == 3, r.to_dict()
    assert r.by_gap["core"]["total"] == 3
    reasons = dict(r.failures)
    assert "slots" in reasons["b"] and reasons["c"] == "no prediction"


def test_slot_type_exactness():
    tasks = [_t("f", "explainFare", {"airport": True})]
    # string "true" must not satisfy boolean True
    assert score(tasks, {"f": Prediction("explainFare", {"airport": "true"})}).passed == 0
    assert score(tasks, {"f": Prediction("explainFare", {"airport": True})}).passed == 1


def test_gate_rejects_core_regression():
    tasks = [_t("core", "lastTrain", {"stationId": "M1_PIR"}), _t("gap", "firstTrain", {}, gap=True)]
    base = {"core": Prediction("lastTrain", {"stationId": "M1_PIR"}), "gap": Prediction("other", {})}
    # candidate fixes the gap case but breaks the core one
    cand = {"core": Prediction("other", {}), "gap": Prediction("firstTrain", {})}
    res = evaluate(tasks, base, cand)
    assert not res.accepted and res.core_regressions == ["core"]


def test_gate_accepts_gap_closure():
    tasks = [_t("core", "lastTrain", {"stationId": "M1_PIR"}), _t("gap", "firstTrain", {}, gap=True)]
    base = {"core": Prediction("lastTrain", {"stationId": "M1_PIR"}), "gap": Prediction("other", {})}
    cand = {"core": Prediction("lastTrain", {"stationId": "M1_PIR"}), "gap": Prediction("firstTrain", {})}
    res = evaluate(tasks, base, cand)
    assert res.accepted, res.to_dict()


def test_validate_rejects_off_surface_and_frozen_enum_changes():
    base_schema = json.dumps(
        {"oneOf": [{"properties": {"intent": {"const": "lastTrain"}}}],
         "allowedLineIds": ["M1", "M2"], "dayContextValues": ["today"]}
    )
    base = {"ariadne_intent_schema.json": base_schema}

    # off-surface file
    assert harness.validate(base, {**base, "AthensTransitParser.kt": "x"})

    # changed frozen enum
    bad = json.dumps(
        {"oneOf": [{"properties": {"intent": {"const": "lastTrain"}}}],
         "allowedLineIds": ["M1"], "dayContextValues": ["today"]}
    )
    assert harness.validate(base, {"ariadne_intent_schema.json": bad})

    # removed an existing intent
    removed = json.dumps(
        {"oneOf": [{"properties": {"intent": {"const": "somethingElse"}}}],
         "allowedLineIds": ["M1", "M2"], "dayContextValues": ["today"]}
    )
    assert harness.validate(base, {"ariadne_intent_schema.json": removed})


def test_validate_allows_added_intent():
    base_schema = json.dumps(
        {"oneOf": [{"properties": {"intent": {"const": "lastTrain"}}}],
         "allowedLineIds": ["M1"], "dayContextValues": ["today"]}
    )
    with_added = json.dumps(
        {"oneOf": [
            {"properties": {"intent": {"const": "lastTrain"}}},
            {"properties": {"intent": {"const": "firstTrain"}}},
        ], "allowedLineIds": ["M1"], "dayContextValues": ["today"]}
    )
    base = {"ariadne_intent_schema.json": base_schema}
    assert harness.validate(base, {"ariadne_intent_schema.json": with_added}) == []


def main() -> int:
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_") and callable(v)]
    failed = 0
    for fn in tests:
        try:
            fn()
            print(f"ok   {fn.__name__}")
        except AssertionError as exc:
            failed += 1
            print(f"FAIL {fn.__name__}: {exc}")
        except Exception as exc:  # noqa: BLE001
            failed += 1
            print(f"ERR  {fn.__name__}: {type(exc).__name__}: {exc}")
    print(f"\n{len(tests) - failed}/{len(tests)} passed")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
