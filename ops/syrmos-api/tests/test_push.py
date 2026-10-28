"""Push senders: APNs and FCM with mocked transports (no network)."""
import json

import httpx
import jwt
import pytest
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec, rsa

from syrmos_admin import db as dbmod
from syrmos_admin import push

IOS_TOKEN = "ab" * 32


def _ec_pem():
    key = ec.generate_private_key(ec.SECP256R1())
    pem = key.private_bytes(
        serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()
    ).decode()
    return key, pem


def _rsa_pem():
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    pem = key.private_bytes(
        serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()
    ).decode()
    return key, pem


class Clock:
    def __init__(self, t=1_800_000_000.0):
        self.t = t

    def __call__(self):
        return self.t


def _row(**over):
    row = {f"{p}_{l}": "" for p in ("title", "body") for l in ("en", "el", "sq", "it")}
    row.update({"id": "b_1", "audience": "all", "deep_link": "syrmos://station/M2_SYN", "expires_at": None})
    row.update(over)
    return row


# --- audience + text -------------------------------------------------------------

def test_audience_topic_mapping():
    assert push.audience_topic("all") == "all"
    assert push.audience_topic("line:M3") == "line.M3"
    assert push.audience_topic("station:M3_NIK") == "station.M3_NIK"
    assert push.audience_topic("region:athens") == "region.athens"
    for bad in ("", "line:", "region:patras", "everyone", "line:M3;drop"):
        with pytest.raises(ValueError):
            push.audience_topic(bad)


def test_device_matches():
    topics = ["all", "region.athens", "line.M3", "station.M3_NIK"]
    assert push.device_matches(topics, "all")
    assert push.device_matches([], "all")  # 'all' reaches every registered device
    assert push.device_matches(topics, "line:M3")
    assert not push.device_matches(topics, "line:M1")
    assert push.device_matches(topics, "station:M3_NIK")
    assert push.device_matches(topics, "region:athens")
    assert not push.device_matches(topics, "region:thessaloniki")


def test_pick_text_language_fallback():
    row = _row(title_el="Τίτλος", body_el="Σώμα", title_en="Title", body_en="Body", title_sq="Titulli")
    assert push.pick_text(row, "sq") == ("Titulli", "Body")  # per field: sq title, en body
    assert push.pick_text(row, "it") == ("Title", "Body")
    assert push.pick_text(row, "el") == ("Τίτλος", "Σώμα")
    assert push.pick_text(_row(title_el="Τίτλος", body_el="Σώμα"), "it") == ("Τίτλος", "Σώμα")
    assert push.pick_text(row, None) == ("Title", "Body")
    assert push.pick_text(row, "el-GR") == ("Τίτλος", "Σώμα")


# --- APNs ------------------------------------------------------------------------

def _apns(handler, clock=None):
    key, pem = _ec_pem()
    client = httpx.Client(transport=httpx.MockTransport(handler))
    sender = push.ApnsSender(key_pem=pem, key_id="KEY123", team_id="TEAM456",
                             topic="com.syrmosApp.ios", client=client, clock=clock or Clock())
    return sender, key


def test_apns_request_shape():
    seen = []

    def handler(request):
        seen.append(request)
        return httpx.Response(200)

    sender, key = _apns(handler)
    payload = sender.payload(title="T", body="B", broadcast_id="b_1", deep_link="syrmos://x")
    result = sender.send(IOS_TOKEN, "sandbox", payload, expires_at=1_800_003_600)
    assert result.ok
    req = seen[0]
    assert str(req.url) == f"https://api.sandbox.push.apple.com/3/device/{IOS_TOKEN}"
    assert req.headers["apns-topic"] == "com.syrmosApp.ios"
    assert req.headers["apns-push-type"] == "alert"
    assert req.headers["apns-expiration"] == "1800003600"
    token = req.headers["authorization"].removeprefix("bearer ")
    assert jwt.get_unverified_header(token) == {"alg": "ES256", "kid": "KEY123", "typ": "JWT"}
    claims = jwt.decode(token, key.public_key(), algorithms=["ES256"], options={"verify_iat": False})
    assert claims == {"iss": "TEAM456", "iat": 1_800_000_000}
    assert json.loads(req.content) == {
        "aps": {"alert": {"title": "T", "body": "B"}, "sound": "default", "thread-id": "syrmos.broadcast"},
        "broadcastId": "b_1",
        "deepLink": "syrmos://x",
    }


def test_apns_production_host():
    hosts = []
    sender, _ = _apns(lambda r: hosts.append(r.url.host) or httpx.Response(200))
    sender.send(IOS_TOKEN, "production", {})
    assert hosts == ["api.push.apple.com"]


def test_apns_provider_token_refreshes_after_50_minutes():
    clock = Clock()
    sender, _ = _apns(lambda r: httpx.Response(200), clock)
    first = sender.provider_token()
    clock.t += 49 * 60
    assert sender.provider_token() == first
    clock.t += 60
    assert sender.provider_token() != first


@pytest.mark.parametrize("status,reason,unregister", [
    (410, "Unregistered", True),
    (400, "BadDeviceToken", True),
    (400, "PayloadEmpty", False),
    (429, "TooManyRequests", False),
])
def test_apns_errors(status, reason, unregister):
    sender, _ = _apns(lambda r: httpx.Response(status, json={"reason": reason}))
    result = sender.send(IOS_TOKEN, "production", {})
    assert not result.ok
    assert result.unregister is unregister
    assert result.reason == reason


def test_apns_expired_provider_token_retries_once():
    calls = []

    def handler(request):
        calls.append(request.headers["authorization"])
        if len(calls) == 1:
            return httpx.Response(403, json={"reason": "ExpiredProviderToken"})
        return httpx.Response(200)

    sender, _ = _apns(handler)
    assert sender.send(IOS_TOKEN, "production", {}).ok
    assert len(calls) == 2


def test_apns_from_env(tmp_path, monkeypatch):
    _, pem = _ec_pem()
    key_file = tmp_path / "AuthKey.p8"
    key_file.write_text(pem)
    assert push.ApnsSender.from_env() is None
    monkeypatch.setenv("APNS_KEY_PATH", str(key_file))
    monkeypatch.setenv("APNS_KEY_ID", "K")
    monkeypatch.setenv("APNS_TEAM_ID", "T")
    monkeypatch.setenv("APNS_TOPIC", "com.syrmosApp.ios")
    sender = push.ApnsSender.from_env()
    assert sender is not None and sender.topic == "com.syrmosApp.ios"


# --- FCM -------------------------------------------------------------------------

def _fcm(handler, clock=None):
    key, pem = _rsa_pem()
    account = {
        "client_email": "push@syrmos.iam.gserviceaccount.com",
        "private_key": pem,
        "private_key_id": "pk1",
        "token_uri": "https://oauth2.googleapis.com/token",
        "project_id": "syrmos-test",
    }
    client = httpx.Client(transport=httpx.MockTransport(handler))
    return push.FcmSender(service_account=account, project_id="syrmos-test", client=client,
                          clock=clock or Clock()), key


def _fcm_handler(log, send_responses):
    def handler(request):
        log.append(request)
        if request.url.host == "oauth2.googleapis.com":
            return httpx.Response(200, json={"access_token": f"at-{len(log)}", "expires_in": 3600})
        return send_responses.pop(0)
    return handler


def test_fcm_oauth_and_message_shape():
    log = []
    sender, key = _fcm(_fcm_handler(log, [httpx.Response(200, json={"name": "m/1"})]))
    msg = sender.message("fcm-token", title="T", body="B", broadcast_id="b_1",
                         deep_link="syrmos://x", expires_at=1_800_003_600)
    assert sender.send(msg).ok
    oauth, send = log
    form = dict(x.split("=", 1) for x in oauth.content.decode().split("&"))
    assert form["grant_type"] == "urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer"
    assertion = jwt.decode(form["assertion"], key.public_key(), algorithms=["RS256"],
                           audience="https://oauth2.googleapis.com/token",
                           options={"verify_iat": False, "verify_exp": False})
    assert assertion["scope"] == "https://www.googleapis.com/auth/firebase.messaging"
    assert assertion["iss"] == "push@syrmos.iam.gserviceaccount.com"
    assert str(send.url) == "https://fcm.googleapis.com/v1/projects/syrmos-test/messages:send"
    assert send.headers["authorization"] == "Bearer at-1"
    assert json.loads(send.content) == {"message": {
        "token": "fcm-token",
        "notification": {"title": "T", "body": "B"},
        "data": {"broadcastId": "b_1", "deepLink": "syrmos://x"},
        "android": {"notification": {"channel_id": "syrmos_broadcasts"}, "ttl": "3600s"},
    }}


def test_fcm_access_token_is_cached():
    log = []
    sender, _ = _fcm(_fcm_handler(log, [httpx.Response(200), httpx.Response(200)]))
    sender.send(sender.message("t", title="a", body="b", broadcast_id="b", deep_link=None))
    sender.send(sender.message("t", title="a", body="b", broadcast_id="b", deep_link=None))
    assert sum(1 for r in log if r.url.host == "oauth2.googleapis.com") == 1


@pytest.mark.parametrize("response,unregister", [
    (httpx.Response(404, json={"error": {"status": "NOT_FOUND", "details": [
        {"@type": "type.googleapis.com/google.firebase.fcm.v1.FcmError", "errorCode": "UNREGISTERED"}]}}), True),
    (httpx.Response(400, json={"error": {"status": "INVALID_ARGUMENT", "details": [
        {"errorCode": "UNREGISTERED"}]}}), True),
    (httpx.Response(400, json={"error": {"status": "INVALID_ARGUMENT", "details": [
        {"errorCode": "INVALID_ARGUMENT"}]}}), False),
    (httpx.Response(503, json={"error": {"status": "UNAVAILABLE"}}), False),
])
def test_fcm_errors(response, unregister):
    sender, _ = _fcm(_fcm_handler([], [response]))
    result = sender.send(sender.message("t", title="a", body="b", broadcast_id="b", deep_link=None))
    assert not result.ok
    assert result.unregister is unregister


def test_fcm_401_refreshes_token_once():
    log = []
    sender, _ = _fcm(_fcm_handler(log, [httpx.Response(401), httpx.Response(200)]))
    assert sender.send(sender.message("t", title="a", body="b", broadcast_id="b", deep_link=None)).ok
    assert sum(1 for r in log if r.url.host == "oauth2.googleapis.com") == 2


# --- deliver ---------------------------------------------------------------------

class FakeSender:
    def __init__(self, results):
        self.results = results
        self.sent = []

    def payload(self, **kw):
        return kw

    def message(self, token, **kw):
        return {"token": token, **kw}

    def send(self, *args, **kw):
        self.sent.append(args)
        token = args[0] if isinstance(args[0], str) else args[0]["token"]
        return self.results.get(token, push.SendResult(ok=True, status=200))


def _device(conn, token, platform, lang, topics):
    import hashlib

    conn.execute(
        "INSERT INTO push_devices(token_hash, token, platform, lang, topics) VALUES(?,?,?,?,?)",
        (hashlib.sha256(token.encode()).hexdigest(), token, platform, lang, json.dumps(topics)),
    )


def test_deliver_targets_language_and_cleanup(temp_db):
    conn = dbmod.connect()
    _device(conn, "ios-el", "ios", "el", ["all", "line.M3"])
    _device(conn, "ios-gone", "ios", "en", ["line.M3"])
    _device(conn, "and-sq", "android", "sq", ["line.M3"])
    _device(conn, "and-gone", "android", "it", ["line.M3"])
    _device(conn, "and-m1", "android", "en", ["line.M1"])
    apns = FakeSender({"ios-gone": push.SendResult(ok=False, status=410, unregister=True)})
    fcm = FakeSender({"and-gone": push.SendResult(ok=False, status=404, unregister=True)})
    row = _row(audience="line:M3", title_el="Τίτλος", body_el="Σώμα", title_en="Title", body_en="Body")
    report = push.deliver(conn, row, push.Senders(apns=apns, fcm=fcm))
    assert (report.targeted, report.sent, report.failed, report.removed) == (4, 2, 2, 2)
    left = {r[0] for r in conn.execute("SELECT token FROM push_devices")}
    assert left == {"ios-el", "and-sq", "and-m1"}
    titles = {args[0]: args[2]["title"] for args in apns.sent}
    assert titles["ios-el"] == "Τίτλος"
    assert {m[0]["token"]: m[0]["title"] for m in fcm.sent}["and-sq"] == "Title"


def test_deliver_without_senders_skips(temp_db):
    conn = dbmod.connect()
    _device(conn, "ios-1", "ios", "el", ["all"])
    report = push.deliver(conn, _row(title_el="x", body_el="y"), push.Senders())
    assert (report.targeted, report.sent, report.skipped) == (1, 0, 1)


def test_count_targets(temp_db):
    conn = dbmod.connect()
    _device(conn, "a", "ios", "el", ["region.athens"])
    _device(conn, "b", "android", "en", ["region.thessaloniki"])
    assert push.count_targets(conn, "region:athens") == {"total": 1, "platform:ios": 1, "lang:el": 1}
    assert push.count_targets(conn, "all")["total"] == 2
