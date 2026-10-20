"""Paths and the model seam. No vendor is hard-coded.

The proposer/composer/probe model is reached through one external command, set in
the environment, so any provider (Anthropic, Vertex, a local server) can be wired
without changing this package:

    ARIADNE_PPC_MODEL_CMD   a shell command; the prompt arrives on stdin, the
                            completion is read from stdout. Required for `run`.
                            Scoring and the gate never need it.
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

# scripts/ariadne-ppc/ppc/config.py -> repo root is three parents up.
REPO_ROOT = Path(__file__).resolve().parents[3]
PACK_DIR = REPO_ROOT / "docs" / "ariadne"

# The editable surface: exactly these files, relative to PACK_DIR. Everything
# else in the repo is frozen for the purposes of this loop.
SURFACE_FILES = (
    "ariadne_prompt_pack.md",
    "ariadne_intent_schema.json",
    "ariadne_tool_contracts.json",
    "ariadne_rag_chunks.jsonl",
)

MODEL_CMD_ENV = "ARIADNE_PPC_MODEL_CMD"


@dataclass(frozen=True)
class Settings:
    pack_dir: Path = PACK_DIR
    tasks_path: Path = Path(__file__).resolve().parents[1] / "tasks.jsonl"
    model_cmd: str = ""

    @classmethod
    def from_env(cls) -> "Settings":
        return cls(model_cmd=os.environ.get(MODEL_CMD_ENV, ""))
