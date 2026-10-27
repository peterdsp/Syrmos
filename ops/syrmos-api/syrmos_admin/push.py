"""Push delivery for owner broadcasts: APNs (iOS) and FCM HTTP v1 (Android).

APNs is called directly from the Pi over HTTP/2 with a .p8 token key: the
provider JWT (ES256, kid = APNS_KEY_ID, iss = APNS_TEAM_ID) is cached and
refreshed every 50 minutes, inside Apple's 20 to 60 minute window. FCM uses
the HTTP v1 API with an OAuth access token minted from a service-account key.

Secrets never live in the repository. Configuration comes from admin.env on
the Pi:

    APNS_KEY_PATH, APNS_KEY_ID, APNS_TEAM_ID, APNS_TOPIC
    FCM_SERVICE_ACCOUNT_PATH, FCM_PROJECT_ID

Every network call goes through an injectable httpx.Client and every clock
read through an injectable callable, so the tests never touch the network.

Text is chosen per device from its registered language, falling back to
English and then Greek. Tokens the push services reject as gone (APNs 410 or
BadDeviceToken, FCM UNREGISTERED or NOT_FOUND) are deleted from push_devices.
"""
from __future__ import annotations

import json
import logging
import os
import re
import sqlite3
import time
from dataclasses import dataclass, field
from typing import Any, Callable

import httpx

logger = logging.getLogger(__name__)

APNS_HOSTS = {
    "production": "https://api.push.apple.com",
    "sandbox": "https://api.sandbox.push.apple.com",
}
APNS_JWT_REFRESH_SECONDS = 50 * 60
FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
FCM_SEND_URL = "https://fcm.googleapis.com/v1/projects/{project}/messages:send"
ANDROID_CHANNEL = "syrmos_broadcasts"
APNS_THREAD_ID = "syrmos.broadcast"
TEXT_FALLBACK = ("en", "el")
LANGS = ("en", "el", "sq", "it")

AUDIENCE_RE = re.compile(
    r"^(all|line:[A-Za-z0-9_]{1,32}|station:[A-Za-z0-9_]{1,32}|region:(athens|thessaloniki))$"
)
TOPIC_RE = re.compile(
    r"^(all|line\.[A-Za-z0-9_]{1,32}|station\.[A-Za-z0-9_]{1,32}|region\.(athens|thessaloniki))$"
)


@dataclass
class SendResult:
    ok: bool
    status: int = 0
    reason: str = ""
    unregister: bool = False


@dataclass
class DeliveryReport:
    targeted: int = 0
    sent: int = 0
    failed: int = 0
    removed: int = 0
    skipped: int = 0
    errors: list[str] = field(default_factory=list)


# --- audience + text -------------------------------------------------------

def audience_topic(audience: str) -> str:
    """'line:M3' -> 'line.M3'; 'all' stays 'all'."""
    if not AUDIENCE_RE.match(audience or ""):
        raise ValueError(f"invalid audience: {audience!r}")
    return audience.replace(":", ".", 1)


def device_matches(topics: list[str], audience: str) -> bool:
    """Audience 'all' reaches every registered device; anything else needs the
    device to have subscribed to the matching topic."""
    topic = audience_topic(audience)
    return topic == "all" or topic in topics


def pick_text(row: Any, lang: str | None) -> tuple[str, str]:
    """(title, body) for a broadcast row in the device language, per field
    falling back to en, then el."""
    order = []
    for candidate in ((lang or "").lower()[:2], *TEXT_FALLBACK):
        if candidate in LANGS and candidate not in order:
            order.append(candidate)

    def first(prefix: str) -> str:
        for code in order:
            value = row[f"{prefix}_{code}"]
            if value:
                return value
        return ""

    return first("title"), first("body")


def _topics(raw: str | None) -> list[str]:
    try:
        value = json.loads(raw or "[]")
    except (TypeError, ValueError):
        return []
    return [str(v) for v in value] if isinstance(value, list) else []


def target_devices(conn: sqlite3.Connection, audience: str) -> list[sqlite3.Row]:
    rows = conn.execute(
        "SELECT token_hash, token, platform, environment, lang, topics FROM push_devices"
    ).fetchall()
    return [r for r in rows if device_matches(_topics(r["topics"]), audience)]


def count_targets(conn: sqlite3.Connection, audience: str) -> dict[str, int]:
    """Dry-run counts for the admin form: total plus per platform and language."""
    devices = target_devices(conn, audience)
    out: dict[str, int] = {"total": len(devices)}
    for d in devices:
        out[f"platform:{d['platform']}"] = out.get(f"platform:{d['platform']}", 0) + 1
        out[f"lang:{d['lang']}"] = out.get(f"lang:{d['lang']}", 0) + 1
    return out


# --- APNs ------------------------------------------------------------------

class ApnsSender:
    def __init__(
        self,
        *,
        key_pem: str,
        key_id: str,
        team_id: str,
        topic: str,
        client: httpx.Client | None = None,
        clock: Callable[[], float] = time.time,
    ) -> None:
        self.key_pem = key_pem
        self.key_id = key_id
        self.team_id = team_id
        self.topic = topic
        self.clock = clock
        self._client = client
        self._jwt: str | None = None
        self._jwt_issued = 0.0

    @classmethod
    def from_env(cls, **kwargs: Any) -> "ApnsSender | None":
        path = os.environ.get("APNS_KEY_PATH")
        key_id = os.environ.get("APNS_KEY_ID")
        team_id = os.environ.get("APNS_TEAM_ID")
        topic = os.environ.get("APNS_TOPIC")
        if not (path and key_id and team_id and topic):
            return None
        try:
            with open(path, encoding="utf-8") as handle:
                key_pem = handle.read()
        except OSError as exc:
            logger.error("APNs key unreadable at %s: %r", path, exc)
            return None
        return cls(key_pem=key_pem, key_id=key_id, team_id=team_id, topic=topic, **kwargs)

    @property
    def client(self) -> httpx.Client:
        if self._client is None:
            try:
                self._client = httpx.Client(http2=True, timeout=10.0)
            except ImportError:  # h2 missing; APNs requires HTTP/2
                raise RuntimeError("APNs needs httpx[http2] (the h2 package)") from None
        return self._client

    def provider_token(self) -> str:
        import jwt  # PyJWT[crypto]

        now = self.clock()
        if self._jwt is None or now - self._jwt_issued >= APNS_JWT_REFRESH_SECONDS:
            self._jwt = jwt.encode(
                {"iss": self.team_id, "iat": int(now)},
                self.key_pem,
                algorithm="ES256",
                headers={"kid": self.key_id},
            )
            self._jwt_issued = now
        return self._jwt

    def payload(self, *, title: str, body: str, broadcast_id: str, deep_link: str | None) -> dict:
        out: dict[str, Any] = {
            "aps": {
                "alert": {"title": title, "body": body},
                "sound": "default",
                "thread-id": APNS_THREAD_ID,
            },
            "broadcastId": broadcast_id,
        }
        if deep_link:
            out["deepLink"] = deep_link
        return out

    def send(self, token: str, environment: str, payload: dict, *, expires_at: int | None = None) -> SendResult:
        host = APNS_HOSTS.get(environment, APNS_HOSTS["production"])
        result = SendResult(ok=False)
        for attempt in range(2):
            headers = {
                "authorization": f"bearer {self.provider_token()}",
                "apns-topic": self.topic,
                "apns-push-type": "alert",
                "apns-priority": "10",
            }
            if expires_at:
                headers["apns-expiration"] = str(int(expires_at))
            try:
                resp = self.client.post(f"{host}/3/device/{token}", json=payload, headers=headers)
            except httpx.HTTPError as exc:
                return SendResult(ok=False, reason=f"network: {exc!r}")
            if resp.status_code == 200:
                return SendResult(ok=True, status=200)
            try:
                reason = (resp.json() or {}).get("reason", "")
            except ValueError:
                reason = ""
            result = SendResult(
                ok=False,
                status=resp.status_code,
                reason=reason,
                unregister=resp.status_code == 410 or reason in ("BadDeviceToken", "Unregistered"),
            )
            if resp.status_code == 403 and reason == "ExpiredProviderToken" and attempt == 0:
                self._jwt = None  # mint a fresh provider token and retry once
                continue
            return result
        return result


# --- FCM -------------------------------------------------------------------

class FcmSender:
    def __init__(
        self,
        *,
        service_account: dict,
        project_id: str,
        client: httpx.Client | None = None,
        clock: Callable[[], float] = time.time,
    ) -> None:
        self.service_account = service_account
        self.project_id = project_id
        self.clock = clock
        self._client = client
        self._token: str | None = None
        self._token_expires = 0.0

    @classmethod
    def from_env(cls, **kwargs: Any) -> "FcmSender | None":
        path = os.environ.get("FCM_SERVICE_ACCOUNT_PATH")
        if not path:
            return None
        try:
            with open(path, encoding="utf-8") as handle:
                account = json.load(handle)
        except (OSError, ValueError) as exc:
            logger.error("FCM service account unreadable at %s: %r", path, exc)
            return None
        project = os.environ.get("FCM_PROJECT_ID") or account.get("project_id")
        if not project:
            return None
        return cls(service_account=account, project_id=project, **kwargs)

    @property
    def client(self) -> httpx.Client:
        if self._client is None:
            self._client = httpx.Client(timeout=10.0)
        return self._client

    def access_token(self) -> str:
        import jwt  # PyJWT[crypto]

        now = self.clock()
        if self._token and now < self._token_expires - 60:
            return self._token
        token_uri = self.service_account.get("token_uri") or "https://oauth2.googleapis.com/token"
        assertion = jwt.encode(
            {
                "iss": self.service_account["client_email"],
                "scope": FCM_SCOPE,
                "aud": token_uri,
                "iat": int(now),
                "exp": int(now) + 3600,
            },
            self.service_account["private_key"],
            algorithm="RS256",
            headers={"kid": self.service_account.get("private_key_id", "")},
        )
        resp = self.client.post(
            token_uri,
            data={"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer", "assertion": assertion},
        )
        resp.raise_for_status()
        data = resp.json()
        self._token = data["access_token"]
        self._token_expires = now + int(data.get("expires_in", 3600))
        return self._token

    def message(self, token: str, *, title: str, body: str, broadcast_id: str,
                deep_link: str | None, expires_at: int | None = None) -> dict:
        android: dict[str, Any] = {"notification": {"channel_id": ANDROID_CHANNEL}}
        if expires_at:
            ttl = max(0, int(expires_at - self.clock()))
            android["ttl"] = f"{ttl}s"
        return {
            "message": {
                "token": token,
                "notification": {"title": title, "body": body},
                "data": {"broadcastId": broadcast_id, "deepLink": deep_link or ""},
                "android": android,
            }
        }

    def send(self, message: dict) -> SendResult:
        url = FCM_SEND_URL.format(project=self.project_id)
        result = SendResult(ok=False)
        for attempt in range(2):
            try:
                resp = self.client.post(
                    url, json=message, headers={"authorization": f"Bearer {self.access_token()}"}
                )
            except httpx.HTTPError as exc:
                return SendResult(ok=False, reason=f"network: {exc!r}")
            if resp.status_code == 200:
                return SendResult(ok=True, status=200)
            status_text, codes = "", []
            try:
                err = (resp.json() or {}).get("error", {})
                status_text = err.get("status", "")
                codes = [d.get("errorCode", "") for d in err.get("details", []) if isinstance(d, dict)]
            except ValueError:
                pass
            result = SendResult(
                ok=False,
                status=resp.status_code,
                reason=",".join(c for c in [status_text, *codes] if c),
                unregister="UNREGISTERED" in codes or status_text == "NOT_FOUND" or resp.status_code == 404,
            )
            if resp.status_code == 401 and attempt == 0:
                self._token = None  # expired access token: mint a new one, retry once
                continue
            return result
        return result


# --- delivery --------------------------------------------------------------

@dataclass
class Senders:
    apns: ApnsSender | None = None
    fcm: FcmSender | None = None

    @classmethod
    def from_env(cls) -> "Senders":
        return cls(apns=ApnsSender.from_env(), fcm=FcmSender.from_env())


def remove_device(conn: sqlite3.Connection, token_hash: str) -> None:
    conn.execute("DELETE FROM push_devices WHERE token_hash=?", (token_hash,))


def deliver(conn: sqlite3.Connection, broadcast: Any, senders: Senders) -> DeliveryReport:
    """Send one broadcast row to every matching device. Never raises for a
    single device failure; the report carries the counts."""
    report = DeliveryReport()
    devices = target_devices(conn, broadcast["audience"])
    report.targeted = len(devices)
    for device in devices:
        title, body = pick_text(broadcast, device["lang"])
        if not (title or body):
            report.skipped += 1
            continue
        try:
            if device["platform"] == "ios":
                if senders.apns is None:
                    report.skipped += 1
                    continue
                result = senders.apns.send(
                    device["token"],
                    device["environment"],
                    senders.apns.payload(
                        title=title, body=body,
                        broadcast_id=broadcast["id"], deep_link=broadcast["deep_link"],
                    ),
                    expires_at=broadcast["expires_at"],
                )
            else:
                if senders.fcm is None:
                    report.skipped += 1
                    continue
                result = senders.fcm.send(senders.fcm.message(
                    device["token"], title=title, body=body,
                    broadcast_id=broadcast["id"], deep_link=broadcast["deep_link"],
                    expires_at=broadcast["expires_at"],
                ))
        except Exception as exc:  # noqa: BLE001 - one bad device must not stop the run
            report.failed += 1
            report.errors.append(f"{device['platform']}: {exc!r}")
            continue
        if result.ok:
            report.sent += 1
            continue
        report.failed += 1
        if result.unregister:
            remove_device(conn, device["token_hash"])
            report.removed += 1
        elif result.reason:
            report.errors.append(f"{device['platform']} {result.status} {result.reason}")
    return report
