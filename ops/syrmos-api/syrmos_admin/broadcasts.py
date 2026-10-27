"""Owner broadcasts: storage helpers and the GET /api/broadcasts contract."""
from __future__ import annotations

import secrets
import sqlite3
import time

from .closures import etag_for, iso_utc, localized

SCHEMA_VERSION = 1
MAX_PUBLIC = 50
# Feed rows: a broadcast is public once the dispatcher has claimed it.
PUBLIC_STATUSES = ("sending", "sent")


def new_id(prefix: str = "b") -> str:
    """Sortable id: prefix, base36 epoch seconds, 6 random hex chars."""
    n = int(time.time())
    digits = "0123456789abcdefghijklmnopqrstuvwxyz"
    stamp = ""
    while n:
        n, r = divmod(n, 36)
        stamp = digits[r] + stamp
    return f"{prefix}_{stamp}{secrets.token_hex(3)}"


def serialize(row: sqlite3.Row) -> dict:
    sent = row["sent_at"]
    expires = row["expires_at"]
    return {
        "id": row["id"],
        "title": localized(row, "title"),
        "body": localized(row, "body"),
        "audience": row["audience"],
        "sentAt": iso_utc(sent),
        "sentAtEpochSeconds": int(sent) if sent is not None else None,
        "expiresAt": iso_utc(expires),
        "expiresAtEpochSeconds": int(expires) if expires is not None else None,
        "deepLink": row["deep_link"] or None,
        "closureId": row["closure_id"] or None,
    }


def public_broadcasts(conn: sqlite3.Connection, since: int, now: int | None = None) -> list[dict]:
    """Sent broadcasts with sentAt >= since that have not expired, newest first."""
    if now is None:
        now = int(time.time())
    try:
        rows = conn.execute(
            "SELECT * FROM broadcasts"
            " WHERE status IN ('sending', 'sent') AND sent_at IS NOT NULL AND sent_at >= ?"
            " AND (expires_at IS NULL OR expires_at > ?)"
            " ORDER BY sent_at DESC, id DESC LIMIT ?",
            (int(since), now, MAX_PUBLIC),
        ).fetchall()
    except sqlite3.OperationalError:
        return []
    return [serialize(r) for r in rows]


def public_payload(conn: sqlite3.Connection, since: int, now: int | None = None) -> tuple[dict, str]:
    items = public_broadcasts(conn, since, now)
    return {"schemaVersion": SCHEMA_VERSION, "broadcasts": items}, etag_for(items)
