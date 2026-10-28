"""Minute dispatcher: send due broadcasts once and flip closure status by time.

Run by systemd/syrmos-broadcasts.timer every minute:

    python -m syrmos_admin.dispatch

Idempotency: a due broadcast is claimed with a single
UPDATE ... SET status='sending', sent_at=? WHERE id=? AND status='scheduled'
before any push leaves the Pi. Only the run whose UPDATE changed the row
sends it, so overlapping runs (the timer plus an admin "send now") never
double-send, and a crash mid-send leaves the row in 'sending' rather than
retrying it into riders' phones a second time. sent_count / failed_count are
recorded when delivery finishes.
"""
from __future__ import annotations

import json
import logging
import sqlite3
import sys
import time

from . import closures as closures_mod
from . import db as dbmod
from . import push as push_mod

logger = logging.getLogger(__name__)


def claim(conn: sqlite3.Connection, broadcast_id: str, now: int) -> bool:
    cur = conn.execute(
        "UPDATE broadcasts SET status='sending', sent_at=?"
        " WHERE id=? AND status='scheduled'",
        (now, broadcast_id),
    )
    return cur.rowcount == 1


def send_one(
    conn: sqlite3.Connection, broadcast_id: str, senders: push_mod.Senders, now: int | None = None
) -> push_mod.DeliveryReport | None:
    """Claim and deliver one broadcast. Returns None when another run owns it,
    it is not due, or it has expired."""
    if now is None:
        now = int(time.time())
    row = conn.execute("SELECT * FROM broadcasts WHERE id=?", (broadcast_id,)).fetchone()
    if row is None or row["status"] != "scheduled":
        return None
    if row["send_at"] is not None and row["send_at"] > now:
        return None
    if row["expires_at"] is not None and row["expires_at"] <= now:
        conn.execute(
            "UPDATE broadcasts SET status='expired' WHERE id=? AND status='scheduled'",
            (broadcast_id,),
        )
        return None
    if not claim(conn, broadcast_id, now):
        return None
    row = conn.execute("SELECT * FROM broadcasts WHERE id=?", (broadcast_id,)).fetchone()
    report = push_mod.deliver(conn, row, senders)
    conn.execute(
        "UPDATE broadcasts SET status='sent', sent_count=?, failed_count=? WHERE id=?",
        (report.sent, report.failed, broadcast_id),
    )
    for err in report.errors[:20]:
        logger.warning("broadcast %s: %s", broadcast_id, err)
    return report


def run_once(
    conn: sqlite3.Connection, senders: push_mod.Senders | None = None, now: int | None = None
) -> dict:
    if now is None:
        now = int(time.time())
    if senders is None:
        senders = push_mod.Senders.from_env()
    flipped = closures_mod.flip_statuses(conn, now)
    due = [
        r["id"] for r in conn.execute(
            "SELECT id FROM broadcasts WHERE status='scheduled'"
            " AND (send_at IS NULL OR send_at <= ?) ORDER BY send_at, id",
            (now,),
        )
    ]
    sent: dict[str, dict] = {}
    for bid in due:
        report = send_one(conn, bid, senders, now)
        if report is not None:
            sent[bid] = {
                "targeted": report.targeted, "sent": report.sent, "failed": report.failed,
                "removed": report.removed, "skipped": report.skipped,
            }
    return {"closures": flipped, "broadcasts": sent}


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    conn = dbmod.connect()
    try:
        dbmod.migrate(conn)
        summary = run_once(conn)
    finally:
        conn.close()
    if summary["broadcasts"] or any(summary["closures"].values()):
        print(json.dumps(summary, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
