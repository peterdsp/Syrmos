"""Read, write, and validate the editable pack (the harness2 "tree" + _accept_tree).

A candidate pack is accepted only if it edits nothing off the surface and leaves
the hard schema enums untouched. New intents MAY be added (that is the point of
the loop), but an existing intent, the allowed line ids, and the day-context
values may never be removed or renamed. A rejected candidate is reported with a
single actionable message, fed back to the proposer verbatim, exactly as in
harness2.
"""

from __future__ import annotations

import json
from pathlib import Path

from .config import SURFACE_FILES

SCHEMA_FILE = "ariadne_intent_schema.json"
FROZEN_SCHEMA_KEYS = ("allowedLineIds", "dayContextValues")


def read_pack(src_dir: Path) -> dict[str, str]:
    """Load the surface files from a directory into {relpath: text}."""
    out: dict[str, str] = {}
    for rel in SURFACE_FILES:
        p = Path(src_dir) / rel
        if p.exists():
            out[rel] = p.read_text(encoding="utf-8")
    return out


def write_pack(pack: dict[str, str], dst_dir: Path) -> Path:
    dst = Path(dst_dir)
    dst.mkdir(parents=True, exist_ok=True)
    for rel, text in pack.items():
        (dst / rel).write_text(text, encoding="utf-8")
    return dst


def _intent_consts(schema_text: str) -> set[str]:
    schema = json.loads(schema_text)
    consts: set[str] = set()
    for branch in schema.get("oneOf", []):
        const = branch.get("properties", {}).get("intent", {}).get("const")
        if const:
            consts.add(const)
    return consts


def validate(base: dict[str, str], candidate: dict[str, str]) -> list[str]:
    """Return a list of problems; empty means accept. Never raises on policy."""
    errors: list[str] = []

    off_surface = sorted(set(candidate) - set(SURFACE_FILES))
    if off_surface:
        errors.append(
            "these files are off the editable surface and must not be edited: "
            + ", ".join(off_surface)
            + f". Edit only {', '.join(SURFACE_FILES)}."
        )

    if not candidate:
        errors.append("the pack came back empty; edit the files, do not delete them")

    base_schema = base.get(SCHEMA_FILE)
    cand_schema = candidate.get(SCHEMA_FILE)
    if cand_schema is not None:
        try:
            cand = json.loads(cand_schema)
        except json.JSONDecodeError as exc:
            errors.append(f"{SCHEMA_FILE} is not valid JSON: {exc}")
            return errors
        if base_schema is not None:
            base_json = json.loads(base_schema)
            for key in FROZEN_SCHEMA_KEYS:
                if cand.get(key) != base_json.get(key):
                    errors.append(
                        f"{SCHEMA_FILE}: {key} is frozen and was changed; restore it exactly. "
                        f"You may add intents, not touch {key}."
                    )
            removed = _intent_consts(base_schema) - _intent_consts(cand_schema)
            if removed:
                errors.append(
                    f"{SCHEMA_FILE}: existing intents were removed or renamed: "
                    f"{sorted(removed)}. Add intents; never drop one the dispatch serves."
                )
    return errors
