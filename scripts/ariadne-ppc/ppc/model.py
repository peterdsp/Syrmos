"""The one model call, behind a provider-agnostic command seam.

Nothing here knows about any vendor. The command named in ARIADNE_PPC_MODEL_CMD
receives the prompt on stdin and returns the completion on stdout. Swap in an
Anthropic, Vertex, or local-server wrapper without touching this package.

`probe` is the only Ariadne-specific helper: it asks the model, constrained by a
candidate pack, to classify each utterance into one intent-schema object carrying
canonical ids. Its output feeds adapter.score directly.
"""

from __future__ import annotations

import json
import re
import subprocess
from pathlib import Path

from .adapter import Prediction, Task


class ModelError(RuntimeError):
    pass


def complete(prompt: str, cmd: str, *, timeout: int = 300) -> str:
    if not cmd:
        raise ModelError(
            "no model command set; export ARIADNE_PPC_MODEL_CMD to a program that "
            "reads a prompt on stdin and writes the completion on stdout"
        )
    try:
        proc = subprocess.run(
            cmd,
            shell=True,
            input=prompt.encode("utf-8"),
            capture_output=True,
            timeout=timeout,
        )
    except subprocess.TimeoutExpired as exc:
        raise ModelError(f"model command timed out after {timeout}s") from exc
    if proc.returncode != 0:
        raise ModelError(
            f"model command exited {proc.returncode}: {proc.stderr.decode('utf-8', 'replace')[:400]}"
        )
    return proc.stdout.decode("utf-8", "replace")


def _first_json_object(text: str) -> dict:
    """Pull the first balanced {...} out of a completion."""
    start = text.find("{")
    while start != -1:
        depth = 0
        for i in range(start, len(text)):
            if text[i] == "{":
                depth += 1
            elif text[i] == "}":
                depth -= 1
                if depth == 0:
                    try:
                        return json.loads(text[start : i + 1])
                    except json.JSONDecodeError:
                        break
        start = text.find("{", start + 1)
    raise ModelError(f"no JSON object in completion: {text[:200]!r}")


def _classify_prompt(pack: dict[str, str], utterance: str, vocab_hint: str) -> str:
    system = pack.get("ariadne_prompt_pack.md", "")
    schema = pack.get("ariadne_intent_schema.json", "")
    return (
        f"{system}\n\n"
        f"# Intent schema (emit ONE object that matches it, canonical ids only)\n"
        f"{schema}\n\n"
        f"# Station and line vocabulary (id: aliases)\n{vocab_hint}\n\n"
        f"# User message\n{utterance}\n\n"
        f"Reply with ONLY the JSON object, no prose."
    )


def probe(
    pack: dict[str, str],
    tasks: list[Task],
    cmd: str,
    vocab_hint: str = "",
) -> dict[str, Prediction]:
    """Run every task utterance through the model under this pack."""
    preds: dict[str, Prediction] = {}
    for t in tasks:
        raw = complete(_classify_prompt(pack, t.utterance, vocab_hint), cmd)
        obj = _first_json_object(raw)
        intent = obj.pop("intent", "")
        preds[t.id] = Prediction(intent=intent, slots=obj)
    return preds


def save_predictions(preds: dict[str, Prediction], path: Path) -> None:
    lines = [
        json.dumps({"id": tid, "intent": p.intent, "slots": p.slots}, ensure_ascii=False)
        for tid, p in preds.items()
    ]
    Path(path).write_text("\n".join(lines) + "\n", encoding="utf-8")


def strip_fences(text: str) -> str:
    """Drop ```lang fences a model may wrap a file edit in."""
    return re.sub(r"^```[a-zA-Z0-9]*\n|\n```$", "", text.strip())
