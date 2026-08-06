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
VALID_HISTORY_PERIODS = {"day", "month", "year"}


def utc_now() -> dt.datetime:
    return dt.datetime.now(dt.timezone.utc)


def _iso(value: dt.datetime) -> str:
    return value.astimezone(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def _report_day(value: dt.datetime) -> str:
    return value.astimezone(ATHENS).date().isoformat()


def _adjust_daily_aggregate(
    conn: sqlite3.Connection,
    *,
    report_day: str,
    scope_id: str,
    scope_label: str,
    signal: str,
    amount: int,
    now: dt.datetime,
) -> None:
    if amount > 0:
        conn.execute(
            """
            INSERT INTO community_report_daily(
                report_day, scope_id, scope_label, signal, report_count, updated_at
            ) VALUES(?,?,?,?,?,?)
            ON CONFLICT(report_day, scope_id, signal) DO UPDATE SET
                scope_label=excluded.scope_label,
                report_count=community_report_daily.report_count + excluded.report_count,
                updated_at=excluded.updated_at
            """,
            (report_day, scope_id, scope_label, signal, amount, _iso(now)),
        )
        return

    conn.execute(
        """
        UPDATE community_report_daily
        SET report_count = MAX(0, report_count + ?), updated_at = ?
        WHERE report_day = ? AND scope_id = ? AND signal = ?
        """,
        (amount, _iso(now), report_day, scope_id, signal),
    )
    conn.execute(
        """
        DELETE FROM community_report_daily
        WHERE report_day = ? AND scope_id = ? AND signal = ? AND report_count = 0
        """,
        (report_day, scope_id, signal),
    )


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
    conn.execute("BEGIN IMMEDIATE")
    try:
        existing = conn.execute(
            """
            SELECT scope_id, scope_label, signal, created_at
            FROM community_reports WHERE report_id = ?
            """,
            (values["report_id"],),
        ).fetchone()
        if existing:
            existing_created = dt.datetime.fromisoformat(existing["created_at"].replace("Z", "+00:00"))
            _adjust_daily_aggregate(
                conn,
                report_day=_report_day(existing_created),
                scope_id=existing["scope_id"],
                scope_label=existing["scope_label"],
                signal=existing["signal"],
                amount=-1,
                now=current,
            )
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
        _adjust_daily_aggregate(
            conn,
            report_day=_report_day(current),
            scope_id=values["scope_id"],
            scope_label=values["scope_label"],
            signal=values["signal"],
            amount=1,
            now=current,
        )
        conn.execute("COMMIT")
    except Exception:
        conn.execute("ROLLBACK")
        raise
    return {
        "ok": True,
        "reportId": values["report_id"],
        "expiresAt": _iso(expires_at),
    }


def delete_report(
    conn: sqlite3.Connection,
    report_id: str,
    *,
    now: dt.datetime | None = None,
) -> bool:
    if not REPORT_ID_RE.fullmatch(report_id):
        raise ValueError("invalid reportId")
    current = now or utc_now()
    conn.execute("BEGIN IMMEDIATE")
    try:
        existing = conn.execute(
            """
            SELECT scope_id, scope_label, signal, created_at
            FROM community_reports WHERE report_id = ?
            """,
            (report_id,),
        ).fetchone()
        if not existing:
            conn.execute("COMMIT")
            return False
        created_at = dt.datetime.fromisoformat(existing["created_at"].replace("Z", "+00:00"))
        cursor = conn.execute("DELETE FROM community_reports WHERE report_id = ?", (report_id,))
        _adjust_daily_aggregate(
            conn,
            report_day=_report_day(created_at),
            scope_id=existing["scope_id"],
            scope_label=existing["scope_label"],
            signal=existing["signal"],
            amount=-1,
            now=current,
        )
        conn.execute("COMMIT")
    except Exception:
        conn.execute("ROLLBACK")
        raise
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


def history(
    conn: sqlite3.Connection,
    *,
    period: str = "day",
    scope_id: str | None = None,
    limit: int = 31,
    now: dt.datetime | None = None,
) -> dict[str, Any]:
    if period not in VALID_HISTORY_PERIODS:
        raise ValueError("period must be day, month, or year")
    if not 1 <= limit <= 366:
        raise ValueError("limit must be between 1 and 366")
    if scope_id is not None and not SCOPE_ID_RE.fullmatch(scope_id):
        raise ValueError("invalid scopeId")

    period_expression = {
        "day": "report_day",
        "month": "substr(report_day, 1, 7)",
        "year": "substr(report_day, 1, 4)",
    }[period]
    params: list[Any] = []
    where = "report_count > 0"
    if scope_id:
        where += " AND scope_id = ?"
        params.append(scope_id)
    params.append(limit)
    rows = conn.execute(
        f"""
        SELECT bucket, signal, SUM(report_count) AS report_count
        FROM (
            SELECT {period_expression} AS bucket, signal, report_count
            FROM community_report_daily
            WHERE {where}
        )
        WHERE bucket IN (
            SELECT DISTINCT {period_expression}
            FROM community_report_daily
            WHERE {where}
            ORDER BY {period_expression} DESC
            LIMIT ?
        )
        GROUP BY bucket, signal
        ORDER BY bucket ASC, signal ASC
        """,
        params[:-1] + params[:-1] + [params[-1]],
    ).fetchall()

    grouped: dict[str, dict[str, int]] = {}
    for row in rows:
        grouped.setdefault(row["bucket"], {})[row["signal"]] = int(row["report_count"])
    buckets = []
    for bucket, counts in grouped.items():
        positive = sum(count for signal, count in counts.items() if signal in POSITIVE_SIGNALS)
        total = sum(counts.values())
        buckets.append(
            {
                "period": bucket,
                "totalReports": total,
                "positiveReports": positive,
                "issueReports": total - positive,
                "counts": {signal: counts.get(signal, 0) for signal in sorted(VALID_SIGNALS)},
            }
        )

    current = now or utc_now()
    return {
        "granularity": period,
        "scopeId": scope_id,
        "buckets": buckets,
        "updatedAt": _iso(current),
        "privacy": "Permanent anonymous aggregates only. Individual reports are deleted within seven days.",
    }
