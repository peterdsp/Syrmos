"""Minute dispatcher: due broadcasts go out exactly once; closures flip by time."""
import hashlib
import json

from syrmos_admin import db as dbmod
from syrmos_admin import dispatch
from syrmos_admin import push

NOW = 1_793_167_200


class CountingSender:
    def __init__(self):
        self.sent = []

    def payload(self, **kw):
        return kw

    def message(self, token, **kw):
        return {"token": token, **kw}

    def send(self, *args, **kw):
        self.sent.append(args)
        return push.SendResult(ok=True, status=200)


def _device(conn, token, platform="ios", topics=("all",)):
    conn.execute(
        "INSERT INTO push_devices(token_hash, token, platform, lang, topics) VALUES(?,?,?,?,?)",
        (hashlib.sha256(token.encode()).hexdigest(), token, platform, "el", json.dumps(list(topics))),
    )


def _broadcast(conn, bid, *, send_at, status="scheduled", expires_at=None, audience="all"):
    conn.execute(
        "INSERT INTO broadcasts(id, title_el, body_el, audience, send_at, expires_at, status)"
        " VALUES(?,?,?,?,?,?,?)",
        (bid, "Τίτλος", "Σώμα", audience, send_at, expires_at, status),
    )


def _senders():
    return push.Senders(apns=CountingSender(), fcm=CountingSender())


def test_sends_due_broadcast_once(temp_db):
    conn = dbmod.connect()
    _device(conn, "ios-1")
    _device(conn, "and-1", platform="android")
    _broadcast(conn, "b_due", send_at=NOW - 30)
    _broadcast(conn, "b_later", send_at=NOW + 600)
    senders = _senders()
    summary = dispatch.run_once(conn, senders, now=NOW)
    assert summary["broadcasts"]["b_due"]["sent"] == 2
    assert "b_later" not in summary["broadcasts"]
    row = conn.execute("SELECT status, sent_at, sent_count FROM broadcasts WHERE id='b_due'").fetchone()
    assert (row["status"], row["sent_at"], row["sent_count"]) == ("sent", NOW, 2)
    # A second run (or an overlapping one) sends nothing more.
    again = dispatch.run_once(conn, senders, now=NOW + 60)
    assert again["broadcasts"] == {}
    assert len(senders.apns.sent) == 1 and len(senders.fcm.sent) == 1


def test_claim_is_atomic(temp_db):
    conn = dbmod.connect()
    _broadcast(conn, "b1", send_at=NOW)
    assert dispatch.claim(conn, "b1", NOW) is True
    assert dispatch.claim(conn, "b1", NOW) is False
    other = dbmod.connect()
    assert dispatch.send_one(other, "b1", _senders(), NOW) is None


def test_expired_unsent_broadcast_is_never_sent(temp_db):
    conn = dbmod.connect()
    _device(conn, "ios-1")
    _broadcast(conn, "b_stale", send_at=NOW - 7200, expires_at=NOW - 60)
    senders = _senders()
    dispatch.run_once(conn, senders, now=NOW)
    assert conn.execute("SELECT status FROM broadcasts WHERE id='b_stale'").fetchone()[0] == "expired"
    assert senders.apns.sent == []


def test_drafts_and_cancelled_are_ignored(temp_db):
    conn = dbmod.connect()
    _device(conn, "ios-1")
    _broadcast(conn, "b_draft", send_at=NOW - 60, status="draft")
    _broadcast(conn, "b_cancel", send_at=NOW - 60, status="cancelled")
    senders = _senders()
    assert dispatch.run_once(conn, senders, now=NOW)["broadcasts"] == {}
    assert senders.apns.sent == []


def test_audience_filter_applies(temp_db):
    conn = dbmod.connect()
    _device(conn, "m3", topics=["line.M3"])
    _device(conn, "m1", topics=["line.M1"])
    _broadcast(conn, "b_m3", send_at=NOW, audience="line:M3")
    senders = _senders()
    dispatch.run_once(conn, senders, now=NOW)
    assert [a[0] for a in senders.apns.sent] == ["m3"]


def test_flips_closures(temp_db):
    conn = dbmod.connect()
    conn.execute(
        "INSERT INTO station_closures(id, station_ids, mode, starts_at, ends_at, status)"
        " VALUES('c1', '[\"M2_SYN\"]', 'closed', ?, ?, 'scheduled')",
        (NOW, NOW + 3600),
    )
    assert dispatch.run_once(conn, _senders(), now=NOW)["closures"] == {"activated": 1, "ended": 0}
    assert dispatch.run_once(conn, _senders(), now=NOW + 3600)["closures"] == {"activated": 0, "ended": 1}


def test_main_runs_against_a_db(temp_db, capsys):
    conn = dbmod.connect()
    _broadcast(conn, "b1", send_at=1)
    conn.close()
    assert dispatch.main() == 0
    # No push credentials configured: delivered to nobody, still marked sent once.
    conn = dbmod.connect()
    assert conn.execute("SELECT status FROM broadcasts WHERE id='b1'").fetchone()[0] == "sent"
