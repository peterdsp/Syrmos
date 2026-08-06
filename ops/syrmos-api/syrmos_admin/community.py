"""Anonymous Ichnos community reporting and privacy-safe summaries."""
from __future__ import annotations

import datetime as dt
import hashlib
import re
import sqlite3
from typing import Any
from zoneinfo import ZoneInfo

ATHENS = ZoneInfo("Europe/Athens")
REPORT_ID_RE = re.compile(r"^[A-Za-z0-9_-]{12,96}$")
SCOPE_ID_RE = re.compile(r"^[A-Za-z0-9_.:-]{1,96}$")
VALID_PLATFORMS = {"ios", "android", "web", "other"}
VALID_SIGNALS = {
    "normal",
    "delayed",
    "crowded",
    "stopped",
    "too_hot",
    "clean",
    "access",
    "facilities",
    "safety",
    "other",
}
POSITIVE_SIGNALS = {"normal", "clean"}
REPORT_TTL_MINUTES = 120


def utc_now() -> dt.datetime:
    return dt.datetime.now(dt.timezone.utc)


def _iso(value: dt.datetime) -> str:
    return value.astimezone(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def validate_report(payload: dict[str, Any]) -> dict[str, str]:
    report_id = str(payload.get("reportId", "")).strip()
    scope_id = str(payload.get("scopeId", "")).strip()
    scope_label = str(payload.get("scopeLabel", "")).strip()
    signal = str(payload.get("signal", "")).strip().lower()
    detail = str(payload.get("detail", "")).strip()
    platform = str(payload.get("platform", "other")).strip().lower()
    locale = str(payload.get("locale", "")).strip().lower()

    if not REPORT_ID_RE.fullmatch(report_id):
        raise ValueError("invalid reportId")
    if not SCOPE_ID_RE.fullmatch(scope_id):
        raise ValueError("invalid scopeId")
    if not scope_label or len(scope_label) > 160:
        raise ValueError("scopeLabel required")
    if signal not in VALID_SIGNALS:
        raise ValueError("invalid signal")
    if len(detail) > 80:
        raise ValueError("detail too long")
    if platform not in VALID_PLATFORMS:
        raise ValueError("invalid platform")
    if len(locale) > 12:
        raise ValueError("invalid locale")

    return {
        "report_id": report_id,
        "scope_id": scope_id,
        "scope_label": scope_label,
        "signal": signal,
        "detail": detail,
        "platform": platform,
        "locale": locale,
    }


def upsert_report(
    conn: sqlite3.Connection,
    payload: dict[str, Any],
    *,
    now: dt.datetime | None = None,
) -> dict[str, Any]:
    values = validate_report(payload)
    current = now or utc_now()
    expires_at = current + dt.timedelta(minutes=REPORT_TTL_MINUTES)
    conn.execute(
        """
        INSERT INTO community_reports(
            report_id, scope_id, scope_label, signal, detail, platform,
            locale, created_at, expires_at
        ) VALUES(?,?,?,?,?,?,?,?,?)
        ON CONFLICT(report_id) DO UPDATE SET
            scope_id=excluded.scope_id,
            scope_label=excluded.scope_label,
            signal=excluded.signal,
            detail=excluded.detail,
            platform=excluded.platform,
            locale=excluded.locale,
            created_at=excluded.created_at,
            expires_at=excluded.expires_at
        """,
        (
            values["report_id"],
            values["scope_id"],
            values["scope_label"],
            values["signal"],
            values["detail"] or None,
            values["platform"],
            values["locale"] or None,
            _iso(current),
            _iso(expires_at),
        ),
    )
    return {
        "ok": True,
        "reportId": values["report_id"],
        "expiresAt": _iso(expires_at),
    }


def delete_report(conn: sqlite3.Connection, report_id: str) -> bool:
    if not REPORT_ID_RE.fullmatch(report_id):
        raise ValueError("invalid reportId")
    cursor = conn.execute("DELETE FROM community_reports WHERE report_id = ?", (report_id,))
    return cursor.rowcount > 0


def cleanup_expired(conn: sqlite3.Connection, *, now: dt.datetime | None = None) -> None:
    current = now or utc_now()
    conn.execute(
        "DELETE FROM community_reports WHERE created_at <= ?",
        (_iso(current - dt.timedelta(days=7)),),
    )


def _estimated_journeys(scope_id: str | None, now: dt.datetime) -> tuple[int, int]:
    local = now.astimezone(ATHENS)
    key = f"{local.date().isoformat()}:{scope_id or 'network'}".encode()
    seed = int.from_bytes(hashlib.sha256(key).digest()[:4], "big")
    if scope_id:
        daily_total = 54 + seed % 91
    else:
        daily_total = 1100 + seed % 901
    elapsed = local.hour * 60 + local.minute
    count = max(1, round(daily_total * min(max(elapsed, 45), 1440) / 1440))
    return count, daily_total


def summary(
    conn: sqlite3.Connection,
    *,
    scope_id: str | None = None,
    now: dt.datetime | None = None,
) -> dict[str, Any]:
    current = now or utc_now()
    cleanup_expired(conn, now=current)
    params: list[Any] = [_iso(current)]
    where = "expires_at > ?"
    if scope_id:
        where += " AND scope_id = ?"
        params.append(scope_id)

    rows = conn.execute(
        f"""
        SELECT scope_id, scope_label, signal, detail, COUNT(*) AS report_count,
               MAX(created_at) AS latest_at
        FROM community_reports
        WHERE {where}
        GROUP BY scope_id, scope_label, signal, detail
        ORDER BY latest_at DESC
        """,
        params,
    ).fetchall()
    weekly_params: list[Any] = [_iso(current - dt.timedelta(days=7))]
    weekly_where = "created_at > ?"
    if scope_id:
        weekly_where += " AND scope_id = ?"
        weekly_params.append(scope_id)
    weekly_count = int(
        conn.execute(
            f"SELECT COUNT(*) AS report_count FROM community_reports WHERE {weekly_where}",
            weekly_params,
        ).fetchone()["report_count"]
    )

    issues = [row for row in rows if row["signal"] not in POSITIVE_SIGNALS]
    normal_count = sum(int(row["report_count"]) for row in rows if row["signal"] in POSITIVE_SIGNALS)
    estimated_so_far, estimated_daily = _estimated_journeys(scope_id, current)

    if issues:
        issue_items = [
            {
                "scopeId": row["scope_id"],
                "scopeLabel": row["scope_label"],
                "signal": row["signal"],
                "detail": row["detail"] or "",
                "count": int(row["report_count"]),
                "latestAt": row["latest_at"],
            }
            for row in issues[:20]
        ]
        return {
            "displayMode": "issues",
            "scopeId": scope_id,
            "activeIssueCount": sum(item["count"] for item in issue_items),
            "normalReportCount": normal_count,
            "totalReportsThisWeek": weekly_count,
            "estimatedJourneysToday": None,
            "estimatedDailyJourneys": None,
            "issues": issue_items,
            "updatedAt": _iso(current),
        }

    return {
        "displayMode": "normal",
        "scopeId": scope_id,
        "activeIssueCount": 0,
        "normalReportCount": normal_count,
        "totalReportsThisWeek": weekly_count,
        "estimatedJourneysToday": estimated_so_far,
        "estimatedDailyJourneys": estimated_daily,
        "issues": [],
        "updatedAt": _iso(current),
    }
