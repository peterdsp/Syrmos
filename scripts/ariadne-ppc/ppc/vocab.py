"""Build the canonical station/line vocabulary hint for the probe prompt.

Source of truth is docs/ariadne/ariadne_llm_context_pack.json (rawSeedFiles:
stations.json and lines.json), the same bundle the app ships. The hint lets the
dev-time model emit canonical ids; alias, Greeklish, Albanian and Italian folding
remains the on-device grounder's job (covered by the Kotlin/Swift tests), not
re-implemented here.
"""

from __future__ import annotations

import json
from pathlib import Path


def _raw(pack: dict, name: str):
    v = pack["rawSeedFiles"][name]
    return json.loads(v) if isinstance(v, str) else v


def build_hint(pack_dir: Path) -> str:
    pack_path = Path(pack_dir) / "ariadne_llm_context_pack.json"
    if not pack_path.exists():
        return ""
    pack = json.loads(pack_path.read_text(encoding="utf-8"))

    station_lines = []
    for s in _raw(pack, "stations.json"):
        aliases = " | ".join(a for a in (s.get("name"), s.get("name_el")) if a)
        lines = ",".join(s.get("line_ids", []))
        station_lines.append(f"{s['id']}: {aliases} (lines {lines})")

    line_lines = []
    for ln in _raw(pack, "lines.json"):
        aliases = " | ".join(a for a in (ln.get("name"), ln.get("nameEl")) if a)
        line_lines.append(f"{ln['id']}: {aliases}")

    return (
        "Stations (canonical id: English | Greek (lines)):\n"
        + "\n".join(station_lines)
        + "\n\nLines (canonical id: English | Greek):\n"
        + "\n".join(line_lines)
    )
