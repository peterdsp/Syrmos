"""Admin pages for closures, broadcasts and push (Access session + CSRF)."""
import json
import time
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo

import pytest

from syrmos_admin import app as appmod
from syrmos_admin import db as dbmod
from syrmos_admin import dispatch
from syrmos_admin import push


@pytest.fixture
def fake_translate(monkeypatch):
    calls = []

    def translate(text, target):
        calls.append((text, target))
        return f"[{target}] {text}"

    monkeypatch.setattr(appmod.translation_mod, "translate_from_greek", translate)
    return calls


def _post(admin_client, path, data):
    client, headers, csrf = admin_client
    return client.post(path, headers=headers, data={"csrf_token": csrf, **data}, follow_redirects=False)


def _closure_form(**over):
    data = {
        "station_ids": ["M2_SYN", "M3_SYN"],
        "mode": "pass_through",
        "starts": "2026-10-28T08:00",
        "until_further_notice": "1",
        "reason_el": "Με εντολή της Αστυνομίας",
        "reason_en": "By police order",
        "status": "scheduled",
        "action": "save",
    }
    data.update(over)
    return data


def test_create_closure_converts_athens_time(admin_client, access):
    resp = _post(admin_client, "/closures/save", _closure_form(id="syn-parade-2026"))
    assert resp.status_code == 303, resp.text[:400]
    row = dbmod.connect().execute("SELECT * FROM station_closures").fetchone()
    assert row["id"] == "syn-parade-2026"
    assert row["starts_at"] == 1793167200  # 08:00 Athens (UTC+2 after 25 Oct) = 06:00Z
    assert row["ends_at"] is None
    assert json.loads(row["station_ids"]) == ["M2_SYN", "M3_SYN"]
    assert row["source"] == "admin"
    assert row["created_by"] == access.email
    assert row["status"] in ("scheduled", "active")
    public = admin_client[0].get("/api/closures").json()["closures"]
    assert [c["id"] for c in public] == ["syn-parade-2026"]


# `public_closures` only returns closures whose end is inside a 24 h grace, so a
# hard-coded end date silently turns this test into a time bomb: it passed until
# the date went 24 h stale, then failed with an IndexError on an empty list and
# blocked a release. Derive the window from now instead.
_ATHENS = ZoneInfo("Europe/Athens")


def _future_athens_hour(days: int = 2) -> tuple[str, str]:
    """(form value in Athens local time, the UTC instant it must convert to)."""
    local = (datetime.now(_ATHENS) + timedelta(days=days)).replace(
        minute=0, second=0, microsecond=0
    )
    return local.strftime("%Y-%m-%dT%H:00"), local.astimezone(timezone.utc).strftime(
        "%Y-%m-%dT%H:%M:%SZ"
    )


def test_create_with_end_and_bus(admin_client):
    ends_local, ends_utc = _future_athens_hour()
    resp = _post(admin_client, "/closures/save", _closure_form(
        until_further_notice="", ends=ends_local, mode="closed",
        bus_label_el="Έκτακτο λεωφορείο", bus_label_en="Replacement bus",
        bus_from_el="Σύνταγμα (Αμαλίας)", bus_to_el="Ευαγγελισμός", bus_headway="10",
        bus_stops="Σύνταγμα | 37.9755 | 23.7356",
    ))
    assert resp.status_code == 303
    closures = admin_client[0].get("/api/closures").json()["closures"]
    assert closures, "a closure ending in the future must be served publicly"
    item = closures[0]
    assert item["endsAt"] == ends_utc
    assert item["replacementBus"] == {
        "label": {"el": "Έκτακτο λεωφορείο", "en": "Replacement bus"},
        "fromStop": {"el": "Σύνταγμα (Αμαλίας)"},
        "toStop": {"el": "Ευαγγελισμός"},
        "headwayMinutes": 10,
        "stops": [{"name": {"el": "Σύνταγμα"}, "lat": 37.9755, "lon": 23.7356}],
    }


def test_draft_is_not_public(admin_client):
    _post(admin_client, "/closures/save", _closure_form(status="draft"))
    assert admin_client[0].get("/api/closures").json()["closures"] == []


@pytest.mark.parametrize("over,message", [
    ({"station_ids": []}, "pick at least one station"),
    ({"station_ids": ["M9_NOPE"]}, "unknown stations"),
    ({"starts": ""}, "start date/time is required"),
    ({"until_further_notice": "", "ends": ""}, "until further notice"),
    ({"until_further_notice": "", "ends": "2026-10-28T07:00"}, "after the start"),
    ({"reason_el": "", "reason_en": ""}, "reason"),
    ({"id": "Bad Id!"}, "id:"),
    ({"bus_headway": "ten"}, "headway"),
])
def test_validation_errors_rerender_form(admin_client, over, message):
    resp = _post(admin_client, "/closures/save", _closure_form(**over))
    assert resp.status_code == 200
    assert message in resp.text
    assert dbmod.connect().execute("SELECT COUNT(*) FROM station_closures").fetchone()[0] == 0


def test_closing_a_whole_line_is_refused(admin_client):
    resp = _post(admin_client, "/closures/save", _closure_form(station_ids=["T6_SYN", "T6_FIX"]))
    assert resp.status_code == 200
    assert "every station of T6" in resp.text


def test_translate_fills_but_does_not_save(admin_client, fake_translate):
    resp = _post(admin_client, "/closures/save", _closure_form(action="translate", reason_en=""))
    assert resp.status_code == 200
    assert "[en] Με εντολή της Αστυνομίας" in resp.text
    assert "[sq] Με εντολή της Αστυνομίας" in resp.text
    assert "[it] Με εντολή της Αστυνομίας" in resp.text
    assert {t for _, t in fake_translate} == {"en", "sq", "it"}
    assert dbmod.connect().execute("SELECT COUNT(*) FROM station_closures").fetchone()[0] == 0


def test_translate_failure_keeps_form(admin_client, monkeypatch):
    monkeypatch.setattr(appmod.translation_mod, "translate_from_greek", lambda t, l: "")
    resp = _post(admin_client, "/closures/save", _closure_form(action="translate"))
    assert "Translation unavailable" in resp.text
    assert "By police order" in resp.text


def test_edit_keeps_id_and_updates(admin_client):
    _post(admin_client, "/closures/save", _closure_form(id="syn-x"))
    client, headers, _ = admin_client
    assert "syn-x" in client.get("/closures/syn-x/edit", headers=headers).text
    page_html = client.get("/closures/syn-x/edit", headers=headers).text
    assert "name=edit value=1" in page_html
    _post(admin_client, "/closures/save", _closure_form(id="syn-x", mode="closed", edit="1"))
    rows = dbmod.connect().execute("SELECT id, mode FROM station_closures").fetchall()
    assert [(r["id"], r["mode"]) for r in rows] == [("syn-x", "closed")]


def test_new_form_with_existing_id_is_a_duplicate_not_an_update(admin_client):
    _post(admin_client, "/closures/save", _closure_form(id="syn-dup", mode="pass_through"))
    resp = _post(admin_client, "/closures/save", _closure_form(id="syn-dup", mode="closed"))
    assert resp.status_code == 200
    assert "a closure with id syn-dup already exists" in resp.text
    row = dbmod.connect().execute("SELECT mode FROM station_closures WHERE id='syn-dup'").fetchone()
    assert row["mode"] == "pass_through"


def test_edit_of_unknown_id_is_404(admin_client):
    assert _post(admin_client, "/closures/save", _closure_form(id="ghost", edit="1")).status_code == 404


@pytest.mark.parametrize("kept", ["cancelled", "ended"])
def test_editing_cancelled_or_ended_closure_keeps_status(admin_client, kept):
    _post(admin_client, "/closures/save", _closure_form(id="syn-old"))
    dbmod.connect().execute("UPDATE station_closures SET status=? WHERE id='syn-old'", (kept,))
    client, headers, _ = admin_client
    form = client.get("/closures/syn-old/edit", headers=headers).text
    assert f"value='{kept}' selected" in form
    # The owner fixes a typo and saves without touching status: it stays put.
    assert _post(admin_client, "/closures/save", _closure_form(
        id="syn-old", edit="1", status=kept, reason_en="By police order (fixed)",
    )).status_code == 303
    row = dbmod.connect().execute("SELECT status, reason_en FROM station_closures").fetchone()
    assert (row["status"], row["reason_en"]) == (kept, "By police order (fixed)")
    assert client.get("/api/closures").json()["closures"] == [] or kept == "ended"
    # Choosing scheduled explicitly is what brings it back.
    _post(admin_client, "/closures/save", _closure_form(id="syn-old", edit="1", status="scheduled"))
    assert dbmod.connect().execute("SELECT status FROM station_closures").fetchone()[0] in ("scheduled", "active")


def test_new_closure_cannot_start_cancelled(admin_client):
    resp = _post(admin_client, "/closures/save", _closure_form(status="cancelled"))
    assert "a new closure starts as draft or scheduled" in resp.text


def test_approve_draft_and_cancel(admin_client):
    conn = dbmod.connect()
    conn.execute(
        "INSERT INTO station_closures(id, station_ids, mode, starts_at, status, source, reason_el)"
        " VALUES('stasy-1', '[\"M2_SYN\"]', 'closed', ?, 'draft', 'stasy', 'Κλειστός')",
        (int(time.time()) + 3600,),
    )
    client, headers, _ = admin_client
    page = client.get("/closures", headers=headers).text
    assert "/closures/stasy-1/approve" in page
    assert _post(admin_client, "/closures/stasy-1/approve", {}).status_code == 303
    assert conn.execute("SELECT status FROM station_closures").fetchone()[0] == "scheduled"
    assert [c["id"] for c in client.get("/api/closures").json()["closures"]] == ["stasy-1"]
    assert _post(admin_client, "/closures/stasy-1/cancel", {}).status_code == 303
    assert conn.execute("SELECT status FROM station_closures").fetchone()[0] == "cancelled"
    assert client.get("/api/closures").json()["closures"] == []


def test_approve_refuses_line_emptying_draft(admin_client):
    conn = dbmod.connect()
    conn.execute(
        "INSERT INTO station_closures(id, station_ids, mode, starts_at, status, source)"
        " VALUES('t6', '[\"T6_SYN\",\"T6_FIX\"]', 'closed', 1, 'draft', 'stasy')"
    )
    assert _post(admin_client, "/closures/t6/approve", {}).status_code == 409
    assert conn.execute("SELECT status FROM station_closures").fetchone()[0] == "draft"


def test_police_template_prefills(admin_client):
    client, headers, _ = admin_client
    html = client.get("/closures", headers=headers).text
    assert "Station closed by police order, trains pass without stopping until further notice" in html
    form = client.get("/closures/new?template=police_pass_through", headers=headers).text
    assert "value='pass_through' selected" in form
    assert "name=until_further_notice value=1 checked" in form
    assert "Οι συρμοί διέρχονται χωρίς στάση έως νεωτέρας." in form


# --- broadcasts --------------------------------------------------------------------

def _device(token, platform, lang, topics):
    import hashlib

    dbmod.connect().execute(
        "INSERT INTO push_devices(token_hash, token, platform, lang, topics) VALUES(?,?,?,?,?)",
        (hashlib.sha256(token.encode()).hexdigest(), token, platform, lang, json.dumps(topics)),
    )


def _broadcast_form(**over):
    data = {
        "title_el": "28η Οκτωβρίου", "body_el": "Ο σταθμός Σύνταγμα είναι κλειστός.",
        "title_en": "28 October", "body_en": "Syntagma station is closed.",
        "audience": "all", "send_mode": "draft", "action": "save",
        "deep_link": "syrmos://station/M2_SYN",
    }
    data.update(over)
    return data


def test_dry_run_counts_and_previews(admin_client):
    _device("a" * 64, "ios", "el", ["all", "line.M3"])
    _device("b" * 64, "ios", "en", ["line.M1"])
    _device("c" * 30, "android", "sq", ["line.M3"])
    resp = _post(admin_client, "/broadcasts/save", _broadcast_form(action="preview", audience="line:M3"))
    assert resp.status_code == 200
    assert "Dry run: 2 device(s)" in resp.text
    assert "28η Οκτωβρίου" in resp.text and "28 October" in resp.text
    assert dbmod.connect().execute("SELECT COUNT(*) FROM broadcasts").fetchone()[0] == 0


def test_station_audience_overrides_list(admin_client):
    _device("a" * 64, "ios", "el", ["station.M3_SYN"])
    resp = _post(admin_client, "/broadcasts/save", _broadcast_form(action="preview", audience_station="M3_SYN"))
    assert "Dry run: 1 device(s)" in resp.text


def test_save_draft_and_schedule(admin_client):
    assert _post(admin_client, "/broadcasts/save", _broadcast_form()).status_code == 303
    assert _post(admin_client, "/broadcasts/save", _broadcast_form(
        send_mode="schedule", send_at="2026-10-28T07:30", expires="2026-10-28T22:00",
    )).status_code == 303
    rows = dbmod.connect().execute("SELECT status, send_at, expires_at FROM broadcasts ORDER BY status").fetchall()
    assert rows[0]["status"] == "draft"
    assert rows[1]["status"] == "scheduled"
    assert rows[1]["send_at"] == 1793165400  # 07:30 Athens
    assert rows[1]["expires_at"] == 1793217600


def test_send_now_dispatches_once(admin_client, monkeypatch):
    _device("a" * 64, "ios", "el", ["all"])
    sent = []

    class Sender:
        def payload(self, **kw):
            return kw

        def send(self, token, env, payload, **kw):
            sent.append((token, payload["title"]))
            return push.SendResult(ok=True, status=200)

    monkeypatch.setattr(push.Senders, "from_env", classmethod(lambda cls: push.Senders(apns=Sender())))
    assert _post(admin_client, "/broadcasts/save", _broadcast_form(send_mode="now")).status_code == 303
    assert sent == [("a" * 64, "28η Οκτωβρίου")]
    conn = dbmod.connect()
    row = conn.execute("SELECT status, sent_count FROM broadcasts").fetchone()
    assert (row["status"], row["sent_count"]) == ("sent", 1)
    dispatch.run_once(conn, push.Senders(apns=Sender()))
    assert len(sent) == 1
    feed = admin_client[0].get("/api/broadcasts").json()["broadcasts"]
    assert feed[0]["deepLink"] == "syrmos://station/M2_SYN"


@pytest.mark.parametrize("over,message", [
    ({"title_el": "", "title_en": ""}, "title"),
    ({"body_el": "", "body_en": ""}, "body"),
    ({"audience": "everyone"}, "invalid audience"),
    ({"send_mode": "schedule", "send_at": ""}, "pick the date/time"),
    ({"deep_link": "javascript:alert(1)"}, "deep link"),
    ({"closure_id": "missing"}, "unknown closure"),
    ({"send_mode": "schedule", "send_at": "2026-10-28T10:00", "expires": "2026-10-28T09:00"}, "expiry"),
])
def test_broadcast_validation(admin_client, over, message):
    resp = _post(admin_client, "/broadcasts/save", _broadcast_form(**over))
    assert resp.status_code == 200
    assert message in resp.text


def test_broadcast_translate(admin_client, fake_translate):
    resp = _post(admin_client, "/broadcasts/save", _broadcast_form(action="translate", title_en="", body_en=""))
    assert "[en] 28η Οκτωβρίου" in resp.text
    assert "[it] Ο σταθμός Σύνταγμα είναι κλειστός." in resp.text


def test_broadcast_cancel(admin_client):
    _post(admin_client, "/broadcasts/save", _broadcast_form())
    bid = dbmod.connect().execute("SELECT id FROM broadcasts").fetchone()[0]
    assert bid.startswith("b_")
    assert _post(admin_client, f"/broadcasts/{bid}/cancel", {}).status_code == 303
    assert dbmod.connect().execute("SELECT status FROM broadcasts").fetchone()[0] == "cancelled"


def test_broadcast_prefill_from_closure(admin_client):
    _post(admin_client, "/closures/save", _closure_form(id="syn-y"))
    client, headers, _ = admin_client
    html = client.get("/broadcasts?closure=syn-y", headers=headers).text
    assert "value='syn-y' selected" in html
    assert "Με εντολή της Αστυνομίας" in html


def test_push_page_counts(admin_client):
    _device("a" * 64, "ios", "el", ["all", "line.M3"])
    client, headers, _ = admin_client
    html = client.get("/push", headers=headers).text
    assert "line.M3" in html
    assert "missing" in html  # no APNs/FCM keys configured in tests
