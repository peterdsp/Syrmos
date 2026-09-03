"""Validates the server GO engine against the cross-client golden contract in
fixtures/go-guidance/cases.json (the same fixtures the web/iOS/Android engines
use), plus structural properties any correct implementation must hold."""
import json
import os

import pytest

from syrmos_admin import go_guidance as go

_FIXTURE = os.path.join(
    os.path.dirname(__file__), "..", "..", "..", "fixtures", "go-guidance", "cases.json"
)
with open(_FIXTURE, encoding="utf-8") as fh:
    FIX = json.load(fh)


@pytest.mark.parametrize("case", FIX["cases"], ids=[c["name"] for c in FIX["cases"]])
def test_guidance_matches_golden_fixture(case):
    journey = FIX["journeys"][case["journey"]]
    g = go.guidance(journey, case["position"])
    # Exact-equality against the full expected object (not a subset), so every
    # rider-facing field is part of the cross-client contract.
    assert g == case["expect"], f"guidance: got {g!r}, want {case['expect']!r}"
    assert go.should_alert_get_off(journey, case["position"]) is case["alert"]


@pytest.mark.parametrize("name", list(FIX["journeys"].keys()))
def test_advance_walks_to_arrived_alerting_once_per_leg(name):
    journey = FIX["journeys"][name]
    pos = {"legIndex": 0, "stopIndex": 0}
    alerts_per_leg = {}
    total_stops = sum(len(l["stops"]) for l in journey["legs"])
    steps = 0
    while not go.is_arrived(journey, pos):
        if go.should_alert_get_off(journey, pos):
            alerts_per_leg[pos["legIndex"]] = alerts_per_leg.get(pos["legIndex"], 0) + 1
        pos = go.advance(journey, pos)
        steps += 1
        assert steps <= total_stops + 5, f"{name}: advance did not converge"
    for i in range(len(journey["legs"])):
        assert alerts_per_leg.get(i, 0) == 1, f"{name}: leg {i} should alert exactly once"
    last_leg = journey["legs"][-1]
    assert go.guidance(journey, pos) == {
        "kind": go.ARRIVED,
        "station": last_leg["stops"][-1]["name"],
    }


def test_guidance_rejects_out_of_range():
    j = FIX["journeys"]["m2_direct_3"]
    with pytest.raises(IndexError):
        go.guidance(j, {"legIndex": 9, "stopIndex": 0})
    with pytest.raises(IndexError):
        go.guidance(j, {"legIndex": 0, "stopIndex": 9})
