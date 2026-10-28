"""Station closures: the shared fixture, serialisation, and status flips."""
import hashlib
import json
from pathlib import Path

import pytest

from syrmos_admin import closures
from syrmos_admin import db as dbmod

REPO = Path(__file__).resolve().parents[3]
FIXTURE = REPO / "fixtures" / "closures" / "effect.json"
SEED_DIRS = [
    REPO / "core/data/src/commonMain/composeResources/files/seed/schedules-v2",
    REPO / "androidApp/src/androidMain/assets/files/seed/schedules-v2",
    REPO / "iosApp/iosApp/Resources/seed-schedules-v2",
]

T0 = 1793167200  # 2026-10-28T06:00:00Z, 08:00 in Athens


def _fixture():
    return json.loads(FIXTURE.read_text(encoding="utf-8"))


@pytest.mark.parametrize("case", _fixture()["cases"], ids=lambda c: c["id"])
def test_every_fixture_case(case):
    data = _fixture()
    got = closures.closure_effect(data["closures"], case["stationId"], case["lineId"], case["now"])
    assert got == case["expected"]


def test_fixture_has_cases():
    assert len(_fixture()["cases"]) >= 13


def test_effect_does_not_mutate_input():
    data = _fixture()
    before = json.dumps(data["closures"])
    closures.closure_effect(data["closures"], "M3_NIK", "M3", 1793169000)
    assert json.dumps(data["closures"]) == before


def test_status_for_window():
    c = {"startsAtEpochSeconds": T0, "endsAtEpochSeconds": T0 + 3600, "status": "scheduled"}
    assert closures.status_for_window(c, T0 - 1) == "scheduled"
    assert closures.status_for_window(c, T0) == "active"
    assert closures.status_for_window(c, T0 + 3600) == "ended"
    assert closures.status_for_window({**c, "status": "draft"}, T0) == "draft"
    assert closures.status_for_window({**c, "endsAtEpochSeconds": None}, T0 + 10**7) == "active"


def _insert(conn, cid, *, status="scheduled", starts=T0, ends=None, mode="pass_through",
            stations=("M2_SYN", "M3_SYN"), bus=None, **reason):
    conn.execute(
        "INSERT INTO station_closures(id, station_ids, line_ids, mode, starts_at, ends_at,"
        " reason_en, reason_el, reason_sq, reason_it, replacement_bus, source, status)"
        " VALUES(?,?,?,?,?,?,?,?,?,?,?,'admin',?)",
        (cid, json.dumps(list(stations)), "[]", mode, starts, ends,
         reason.get("en", ""), reason.get("el", ""), reason.get("sq", ""), reason.get("it", ""),
         json.dumps(bus, ensure_ascii=False) if bus else None, status),
    )


def test_serialize_matches_contract(temp_db):
    bus = {
        "label": {"el": "Έκτακτο λεωφορείο", "en": "Replacement bus"},
        "fromStop": {"el": "Σύνταγμα (Αμαλίας)", "en": "Syntagma (Amalias)"},
        "toStop": {"el": "Ευαγγελισμός", "en": "Evangelismos"},
        "headwayMinutes": 10,
        "stops": [{"name": {"el": "Σύνταγμα"}, "lat": 37.9755, "lon": 23.7356}],
    }
    conn = dbmod.connect()
    _insert(conn, "syn-parade-2026", bus=bus, el="Με εντολή της Αστυνομίας", en="By police order")
    payload, etag = closures.public_payload(conn, now=T0 - 3600)
    assert payload["schemaVersion"] == 1
    assert payload["generatedAt"] == "2026-10-28T05:00:00Z"
    assert payload["closures"] == [{
        "id": "syn-parade-2026",
        "stationIds": ["M2_SYN", "M3_SYN"],
        "lineIds": [],
        "mode": "pass_through",
        "startsAt": "2026-10-28T06:00:00Z",
        "startsAtEpochSeconds": 1793167200,
        "endsAt": None,
        "endsAtEpochSeconds": None,
        "status": "scheduled",
        "reason": {"el": "Με εντολή της Αστυνομίας", "en": "By police order"},
        "replacementBus": bus,
        "source": "admin",
    }]
    assert etag.startswith('"') and etag.endswith('"')


def test_public_filter_drafts_cancelled_and_old(temp_db):
    conn = dbmod.connect()
    _insert(conn, "draft", status="draft")
    _insert(conn, "cancelled", status="cancelled")
    _insert(conn, "ended-long-ago", status="ended", starts=T0 - 10 * 86400, ends=T0 - 2 * 86400)
    _insert(conn, "ended-recently", status="ended", starts=T0 - 86400, ends=T0 - 3600)
    _insert(conn, "open", status="active")
    ids = [c["id"] for c in closures.public_closures(conn, now=T0)]
    assert ids == ["ended-recently", "open"]


def test_etag_ignores_generated_at(temp_db):
    conn = dbmod.connect()
    _insert(conn, "a")
    _, e1 = closures.public_payload(conn, now=T0)
    _, e2 = closures.public_payload(conn, now=T0 + 30)
    assert e1 == e2
    _insert(conn, "b")
    _, e3 = closures.public_payload(conn, now=T0 + 30)
    assert e3 != e1


def test_flip_statuses(temp_db):
    conn = dbmod.connect()
    _insert(conn, "future", starts=T0 + 3600)
    _insert(conn, "starting", starts=T0)
    _insert(conn, "over", starts=T0 - 7200, ends=T0 - 60, status="active")
    _insert(conn, "draft", status="draft", starts=T0 - 60)
    moved = closures.flip_statuses(conn, now=T0)
    assert moved == {"activated": 1, "ended": 1}
    status = dict(conn.execute("SELECT id, status FROM station_closures").fetchall())
    assert status == {"future": "scheduled", "starting": "active", "over": "ended", "draft": "draft"}


def test_load_closures_tolerates_unmigrated_db():
    import sqlite3

    conn = sqlite3.connect(":memory:")
    assert closures.load_closures(conn) == []
    assert closures.public_closures(conn) == []


def test_seed_closures_json_is_empty_contract_and_hashed():
    expected = closures.canonical_bytes(closures.seed_payload([]))
    assert expected == b'{"schemaVersion":1,"closures":[]}'
    for d in SEED_DIRS:
        raw = (d / "closures.json").read_bytes()
        assert raw == expected, d
        manifest = json.loads((d / "manifest.json").read_text(encoding="utf-8"))
        assert manifest["closuresHash"] == hashlib.sha256(raw).hexdigest(), d


def test_generator_manifest_carries_closures_hash(temp_db, tmp_path):
    from syrmos_admin import generator

    generator.generate(out_dir=tmp_path, db_path=temp_db)
    manifest = json.loads((tmp_path / "schedules-manifest.json").read_text(encoding="utf-8"))
    empty = closures.canonical_bytes(closures.seed_payload([]))
    assert manifest["closuresHash"] == hashlib.sha256(empty).hexdigest()
