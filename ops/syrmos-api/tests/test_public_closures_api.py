"""Public 3.0.2 endpoints: /api/closures, /api/broadcasts, /api/push/*."""
import hashlib
import json
import time

import pytest
from fastapi.testclient import TestClient

from syrmos_admin import app as appmod
from syrmos_admin import db as dbmod

IOS_TOKEN = "a1" * 32
FCM_TOKEN = "dGVzdC10b2tlbg:APA91bHun4MxP5egoKMwt2KZFBaFUH-1RYqx"


@pytest.fixture
def client(temp_db):
    appmod._push_hits.clear()
    yield TestClient(appmod.app)
    appmod._push_hits.clear()


def _closure(conn, cid, **kw):
    conn.execute(
        "INSERT INTO station_closures(id, station_ids, line_ids, mode, starts_at, ends_at, reason_el, status)"
        " VALUES(?,?,?,?,?,?,?,?)",
        (cid, json.dumps(kw.get("stations", ["M2_SYN", "M3_SYN"])), "[]", kw.get("mode", "closed"),
         kw.get("starts", int(time.time()) + 3600), kw.get("ends"), kw.get("reason", "Κλειστός"),
         kw.get("status", "scheduled")),
    )


def _broadcast(conn, bid, *, sent_at, expires_at=None, status="sent", audience="all"):
    conn.execute(
        "INSERT INTO broadcasts(id, title_el, title_en, body_el, audience, send_at, sent_at, expires_at,"
        " deep_link, status) VALUES(?,?,?,?,?,?,?,?,?,?)",
        (bid, "Τίτλος", "Title", "Κείμενο", audience, sent_at, sent_at, expires_at,
         "syrmos://station/M2_SYN", status),
    )


# --- /api/closures -------------------------------------------------------------

def test_closures_empty(client):
    resp = client.get("/api/closures")
    assert resp.status_code == 200
    body = resp.json()
    assert body["schemaVersion"] == 1
    assert body["closures"] == []
    assert body["generatedAt"].endswith("Z")
    assert resp.headers["cache-control"] == "public, max-age=60"
    assert resp.headers["etag"]


def test_closures_hides_drafts_and_revalidates(client):
    conn = dbmod.connect()
    _closure(conn, "live")
    _closure(conn, "draft-one", status="draft")
    resp = client.get("/api/closures")
    assert [c["id"] for c in resp.json()["closures"]] == ["live"]
    etag = resp.headers["etag"]
    again = client.get("/api/closures", headers={"If-None-Match": etag})
    assert again.status_code == 304
    assert again.headers["etag"] == etag
    assert again.content == b""


def test_closure_contract_keys(client):
    conn = dbmod.connect()
    _closure(conn, "c1", ends=int(time.time()) + 7200)
    item = client.get("/api/closures").json()["closures"][0]
    assert set(item) == {
        "id", "stationIds", "lineIds", "mode", "startsAt", "startsAtEpochSeconds", "endsAt",
        "endsAtEpochSeconds", "status", "reason", "replacementBus", "source",
    }
    assert item["reason"] == {"el": "Κλειστός"}
    assert item["replacementBus"] is None


# --- /api/broadcasts --------------------------------------------------------------

def test_broadcasts_since_expiry_and_order(client):
    now = int(time.time())
    conn = dbmod.connect()
    _broadcast(conn, "b_old", sent_at=now - 7200)
    _broadcast(conn, "b_new", sent_at=now - 60)
    _broadcast(conn, "b_expired", sent_at=now - 30, expires_at=now - 1)
    _broadcast(conn, "b_draft", sent_at=None, status="draft")
    _broadcast(conn, "b_sched", sent_at=None, status="scheduled")
    body = client.get("/api/broadcasts").json()
    assert body["schemaVersion"] == 1
    assert [b["id"] for b in body["broadcasts"]] == ["b_new", "b_old"]
    since = client.get(f"/api/broadcasts?since={now - 600}").json()
    assert [b["id"] for b in since["broadcasts"]] == ["b_new"]
    item = since["broadcasts"][0]
    assert set(item) == {
        "id", "title", "body", "audience", "sentAt", "sentAtEpochSeconds", "expiresAt",
        "expiresAtEpochSeconds", "deepLink", "closureId",
    }
    assert item["title"] == {"en": "Title", "el": "Τίτλος"}
    assert item["sentAtEpochSeconds"] == now - 60
    assert item["expiresAt"] is None and item["closureId"] is None


def test_broadcasts_caps_at_50(client):
    now = int(time.time())
    conn = dbmod.connect()
    for i in range(60):
        _broadcast(conn, f"b_{i:03d}", sent_at=now - i)
    assert len(client.get("/api/broadcasts").json()["broadcasts"]) == 50


def test_broadcasts_bad_since(client):
    assert client.get("/api/broadcasts?since=yesterday").status_code == 400
    assert client.get("/api/broadcasts?since=-5").status_code == 400


def test_broadcasts_etag(client):
    resp = client.get("/api/broadcasts?since=0")
    assert client.get("/api/broadcasts?since=0", headers={"If-None-Match": resp.headers["etag"]}).status_code == 304


# --- /api/push -----------------------------------------------------------------

def _reg(**over):
    body = {"token": IOS_TOKEN, "platform": "ios", "environment": "production", "lang": "el",
            "topics": ["all", "region.athens", "line.M3", "station.M3_NIK"], "appVersion": "3.0.2"}
    body.update(over)
    return body


def test_register_upserts_by_token_hash(client):
    assert client.post("/api/push/register", json=_reg()).status_code == 204
    assert client.post("/api/push/register", json=_reg(lang="en", topics=["all"])).status_code == 204
    conn = dbmod.connect()
    rows = conn.execute("SELECT * FROM push_devices").fetchall()
    assert len(rows) == 1
    row = rows[0]
    assert row["token_hash"] == hashlib.sha256(IOS_TOKEN.encode()).hexdigest()
    assert row["lang"] == "en"
    assert json.loads(row["topics"]) == ["all"]
    assert row["environment"] == "production"


def test_register_android_forces_production_and_normalises_lang(client):
    body = _reg(token=FCM_TOKEN, platform="android", environment="sandbox", lang="sq-AL")
    assert client.post("/api/push/register", json=body).status_code == 204
    row = dbmod.connect().execute("SELECT environment, lang FROM push_devices").fetchone()
    assert (row["environment"], row["lang"]) == ("production", "sq")


def test_register_uppercase_ios_token_is_one_device(client):
    client.post("/api/push/register", json=_reg(token=IOS_TOKEN.upper()))
    client.post("/api/push/register", json=_reg(token=IOS_TOKEN))
    assert dbmod.connect().execute("SELECT COUNT(*) FROM push_devices").fetchone()[0] == 1


@pytest.mark.parametrize("body", [
    [],
    {"platform": "ios"},
    _reg(platform="windows"),
    _reg(token="not hex!"),
    _reg(token=123),
    _reg(environment="staging"),
    _reg(topics="all"),
    _reg(topics=["all", "line M3"]),
    _reg(topics=["region.patras"]),
    _reg(lang=""),
    _reg(appVersion="x" * 100),
])
def test_register_rejects_bad_shape(client, body):
    assert client.post("/api/push/register", json=body).status_code == 400


def test_register_rejects_non_json(client):
    resp = client.post("/api/push/register", content=b"nope", headers={"content-type": "application/json"})
    assert resp.status_code == 400


def test_unregister(client):
    client.post("/api/push/register", json=_reg())
    assert client.post("/api/push/unregister", json={"token": IOS_TOKEN.upper()}).status_code == 204
    assert dbmod.connect().execute("SELECT COUNT(*) FROM push_devices").fetchone()[0] == 0
    assert client.post("/api/push/unregister", json={"token": IOS_TOKEN}).status_code == 204
    assert client.post("/api/push/unregister", json={}).status_code == 400


def test_rate_limit_30_per_hour_per_ip(client):
    for _ in range(30):
        assert client.post("/api/push/register", json=_reg(), headers={"cf-connecting-ip": "1.2.3.4"}).status_code == 204
    assert client.post("/api/push/register", json=_reg(), headers={"cf-connecting-ip": "1.2.3.4"}).status_code == 429
    assert client.post("/api/push/unregister", json={"token": IOS_TOKEN}, headers={"cf-connecting-ip": "1.2.3.4"}).status_code == 429
    assert client.post("/api/push/register", json=_reg(), headers={"cf-connecting-ip": "5.6.7.8"}).status_code == 204


def test_push_endpoints_need_no_admin_auth(client):
    # Public by design: Access headers are irrelevant here.
    assert client.post("/api/push/register", json=_reg()).status_code == 204
