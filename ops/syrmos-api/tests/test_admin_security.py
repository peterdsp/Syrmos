"""Admin auth: Cloudflare Access JWT verification, token fallback, CSRF, audit."""
import pytest
from fastapi.testclient import TestClient

from syrmos_admin import app as appmod
from syrmos_admin import db as dbmod
from syrmos_admin import security


# --- verify_cf_access_jwt ----------------------------------------------------

def test_valid_token_returns_lowercased_email(access):
    assert security.verify_cf_access_jwt(access.token(email="Owner@Example.com")) == "owner@example.com"


def test_certs_are_cached(access):
    security.verify_cf_access_jwt(access.token())
    security.verify_cf_access_jwt(access.token())
    assert access.fetches == 1


def test_certs_refresh_after_an_hour(access):
    security.verify_cf_access_jwt(access.token())
    import time

    security.verify_cf_access_jwt(access.token(), now=time.time() + security.CERTS_TTL_SECONDS + 5)
    assert access.fetches == 2


def test_unknown_kid_triggers_one_refetch_then_rejects(access):
    import time

    security.verify_cf_access_jwt(access.token())
    later = time.time() + security.CERTS_MIN_REFETCH_SECONDS + 1
    with pytest.raises(security.AuthError):
        security.verify_cf_access_jwt(access.token(kid="rotated"), now=later)
    assert access.fetches == 2
    # A burst of junk kids inside the refetch window does not hammer the certs URL.
    with pytest.raises(security.AuthError):
        security.verify_cf_access_jwt(access.token(kid="junk"), now=later + 1)
    assert access.fetches == 2


@pytest.mark.parametrize("overrides", [
    {"aud": "someone-else"},
    {"iss": "https://evil.cloudflareaccess.com"},
    {"exp_in": -120},
    {"email": "intruder@example.com"},
])
def test_bad_claims_rejected(access, overrides):
    with pytest.raises(security.AuthError):
        security.verify_cf_access_jwt(access.token(**overrides))


def test_wrong_signing_key_rejected(access):
    from cryptography.hazmat.primitives.asymmetric import rsa

    other = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    with pytest.raises(security.AuthError):
        security.verify_cf_access_jwt(access.token(key=other))


def test_hs256_token_rejected(access):
    import jwt

    forged = jwt.encode({"email": access.email}, "secret", algorithm="HS256", headers={"kid": access.kid})
    with pytest.raises(security.AuthError):
        security.verify_cf_access_jwt(forged)


def test_not_configured_rejects(monkeypatch, access):
    monkeypatch.delenv("ADMIN_EMAILS")
    with pytest.raises(security.AuthError):
        security.verify_cf_access_jwt(access.token())


def test_team_domain_forms(monkeypatch):
    for raw in ("myteam", "myteam.cloudflareaccess.com", "https://myteam.cloudflareaccess.com/"):
        monkeypatch.setenv("CF_ACCESS_TEAM", raw)
        assert security.team_domain() == "myteam.cloudflareaccess.com"


# --- identify (header token fallback) ----------------------------------------

def test_token_fallback_only_when_set(monkeypatch):
    with pytest.raises(security.AuthError):
        security.identify({"x-admin-token": ""})
    monkeypatch.setenv("HTTP_ADMIN_TOKEN", "s3cret-token")
    assert security.identify({"x-admin-token": "s3cret-token"}) == ("token-admin", False)
    with pytest.raises(security.AuthError):
        security.identify({"x-admin-token": "wrong"})


def test_legacy_token_name_still_works(monkeypatch):
    monkeypatch.setenv("SYRMOS_ADMIN_TOKEN", "legacy")
    assert security.identify({"x-admin-token": "legacy"})[0] == "token-admin"


def test_email_header_alone_is_not_trusted(access):
    with pytest.raises(security.AuthError):
        security.identify({"cf-access-authenticated-user-email": access.email})


# --- CSRF tokens ---------------------------------------------------------------

def test_csrf_round_trip_and_binding():
    tok = security.make_csrf_token("a@example.com", now=1000)
    assert security.check_csrf_token("a@example.com", tok, now=1001)
    assert not security.check_csrf_token("b@example.com", tok, now=1001)
    assert not security.check_csrf_token("a@example.com", tok, now=1000 + security.CSRF_TTL_SECONDS + 1)
    assert not security.check_csrf_token("a@example.com", tok[:-1] + "0", now=1001)
    assert not security.check_csrf_token("a@example.com", None)
    assert not security.check_csrf_token("a@example.com", "garbage")


def test_payload_hash_ignores_csrf_and_order():
    a = security.payload_hash([("x", "1"), ("y", "2"), ("csrf_token", "t1")])
    b = security.payload_hash([("y", "2"), ("x", "1"), ("csrf_token", "t2")])
    assert a == b
    assert a != security.payload_hash([("x", "1"), ("y", "3")])


# --- end to end through the app -------------------------------------------------

ADMIN_PAGES = [
    "/", "/lines", "/frequency-bands", "/holidays", "/overrides", "/icons",
    "/line-display", "/stations", "/operators", "/sync", "/docs", "/contact",
    "/closures", "/closures/new", "/closures/new?template=police_pass_through",
    "/broadcasts", "/push",
]


def test_admin_closed_without_configuration(seeded_db):
    client = TestClient(appmod.app)
    assert client.get("/closures").status_code == 401
    assert client.get("/closures", headers={"Cf-Access-Authenticated-User-Email": "x@y.z"}).status_code == 401


@pytest.mark.parametrize("path", ADMIN_PAGES)
def test_existing_and_new_admin_pages_render(admin_client, path):
    client, headers, _ = admin_client
    resp = client.get(path, headers=headers)
    assert resp.status_code == 200, resp.text[:500]
    assert "Syrmos Admin" in resp.text


def test_rendered_post_forms_carry_csrf(admin_client):
    client, headers, _ = admin_client
    html = client.get("/sync", headers=headers).text
    assert html.count("name=csrf_token") == html.lower().count("method=post")
    assert html.count("name=csrf_token") >= 2


def test_post_without_csrf_is_refused_and_not_audited(admin_client):
    client, headers, _ = admin_client
    resp = client.post("/closures/x/cancel", headers=headers, data={}, follow_redirects=False)
    assert resp.status_code == 403
    conn = dbmod.connect()
    assert conn.execute("SELECT COUNT(*) FROM admin_audit").fetchone()[0] == 0


def test_cross_site_post_refused_even_with_token(admin_client):
    client, headers, csrf = admin_client
    resp = client.post(
        "/closures/x/cancel", headers={**headers, "Sec-Fetch-Site": "cross-site"},
        data={"csrf_token": csrf}, follow_redirects=False,
    )
    assert resp.status_code == 403


def test_post_with_csrf_is_audited(admin_client, access):
    client, headers, csrf = admin_client
    resp = client.post(
        "/closures/x/cancel", headers={**headers, "Sec-Fetch-Site": "same-origin"},
        data={"csrf_token": csrf, "action": "cancel"}, follow_redirects=False,
    )
    assert resp.status_code == 303
    conn = dbmod.connect()
    row = conn.execute("SELECT actor, method, path, action, payload_sha256 FROM admin_audit").fetchone()
    assert row["actor"] == access.email
    assert row["method"] == "POST"
    assert row["path"] == "/closures/x/cancel"
    assert row["action"] == "cancel"
    assert len(row["payload_sha256"]) == 64


def test_header_token_post_needs_no_csrf_but_is_audited(seeded_db, monkeypatch):
    monkeypatch.setenv("HTTP_ADMIN_TOKEN", "tok")
    client = TestClient(appmod.app)
    resp = client.post("/closures/x/cancel", headers={"X-Admin-Token": "tok"}, follow_redirects=False)
    assert resp.status_code == 303
    conn = dbmod.connect()
    assert conn.execute("SELECT actor FROM admin_audit").fetchone()["actor"] == "token-admin"


def test_existing_form_post_still_works_with_csrf(admin_client, monkeypatch):
    client, headers, csrf = admin_client
    regenerated = []
    monkeypatch.setattr(appmod.generator, "generate", lambda *a, **k: regenerated.append(1))
    resp = client.post(
        "/stations/save", headers=headers, follow_redirects=False,
        data={"csrf_token": csrf, "id": "M3_EVA", "name_en": "Evangelismos", "name_el": "Ευαγγελισμός",
              "lat": "37.976", "lng": "23.747"},
    )
    assert resp.status_code == 303
    assert regenerated == [1]
    conn = dbmod.connect()
    assert conn.execute("SELECT name_en FROM stations WHERE id='M3_EVA'").fetchone()["name_en"] == "Evangelismos"
