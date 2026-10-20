#!/usr/bin/env python3
"""Deterministic offline ARIADNE_PPC_MODEL_CMD stand-in, no credentials.

Lets the full `ppc run` pipeline execute end to end so the plumbing can be
tested without a real model. It is intentionally simple: a keyword classifier
for the probe step, and a no-op edit for propose/compose (so base and candidate
packs come out identical and the gate correctly reports no improvement). It does
NOT read the pack, so it never demonstrates a real refinement; use a real
wrapper for that.
"""

from __future__ import annotations

import json
import sys

STATIONS = [
    ("M2_SYN", ("syntagma", "sintagma", "sintagna", "σύνταγμα", "συνταγμα")),
    ("M1_PIR", ("piraeus", "pireas", "pireo", "πειραι")),
    ("M3_AER", ("airport", "aeroport", "aeroporto", "aeroporti", "αεροδρ")),
    ("M1_MON", ("monastiraki", "monastraki", "μοναστηρ")),
]


def _station(text: str):
    for sid, aliases in STATIONS:
        if any(a in text for a in aliases):
            return sid
    return None


def _classify(utterance: str) -> dict:
    t = utterance.lower()
    sid = _station(t)
    has = lambda *ws: any(w in t for w in ws)

    if has("first train", "πρώτο", "parë"):
        return {"intent": "firstTrain", "stationId": sid}
    if has("last train", "ultimo", "τελευτα"):
        return {"intent": "lastTrain", "stationId": sid}
    if has("how much", "ticket", "fare", "quanto", "κοστίζει", "biglietto"):
        return {"intent": "explainFare", "airport": _station(t) == "M3_AER"}
    if has("from ", " to ", "si shkoj", "come arrivo", "πάω", "get from"):
        return {"intent": "planTrip", "lowExposure": False}
    if has("open", "aperto", "λειτουργεί", "closed"):
        return {"intent": "stationStatus", "stationId": sid}
    if has("next train", "επόμενα", "prossimi", "trains from", "δρομολόγια"):
        return {"intent": "showDepartures", "stationId": sid, "dayContext": "today"}
    if has("what can you do", "help"):
        return {"intent": "help"}
    if has("alert", "service"):
        return {"intent": "showAlerts"}
    if sid:
        return {"intent": "showDepartures", "stationId": sid, "dayContext": "today"}
    return {"intent": "outOfScope"}


def main() -> int:
    prompt = sys.stdin.read()
    if "# User message" in prompt:
        utterance = prompt.split("# User message", 1)[1].split("\n\n", 1)[0].strip()
        sys.stdout.write(json.dumps(_classify(utterance), ensure_ascii=False))
    else:
        # propose / compose: a valid no-op edit (no files changed)
        sys.stdout.write('{}\n<notes>mock: no-op edit</notes>')
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
