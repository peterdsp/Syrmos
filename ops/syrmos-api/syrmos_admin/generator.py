"""Generate static JSON files from the SQLite DB.

These files are what nginx serves as /api/lines, /api/schedules,
/api/schedules/manifest, /api/schedules/{lineId}, /api/holidays,
/api/overrides. Same atomic-write-and-rename pattern as scrape_stasy.py.
"""
from __future__ import annotations

import hashlib
import json
import os
import sqlite3
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from . import db as dbmod

DEFAULT_OUT = Path(os.environ.get(
    "SYRMOS_API_OUT_DIR",
    str(Path(__file__).resolve().parent.parent / "out"),
))


def _atomic_write_json(path: Path, payload: Any) -> str:
    """Write JSON atomically. Returns sha256 of the canonical bytes."""
    path.parent.mkdir(parents=True, exist_ok=True)
    body = json.dumps(payload, ensure_ascii=False, separators=(",", ":"), sort_keys=False)
    encoded = body.encode("utf-8")
    digest = hashlib.sha256(encoded).hexdigest()
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_bytes(encoded)
    os.replace(tmp, path)
    return digest


def _now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def _build_lines(conn: sqlite3.Connection) -> dict:
    lines: list[dict] = []
    line_rows = conn.execute(
        "SELECT id, mode, name_en, name_el, color, terminal_a, terminal_b, sort_order"
        " FROM lines WHERE id NOT LIKE '%\\_AIR' ESCAPE '\\' ORDER BY sort_order"
    ).fetchall()
    station_rows_by_line: dict[str, list[sqlite3.Row]] = defaultdict(list)
    for r in conn.execute(
        "SELECT ls.line_id, s.id, s.name_en, s.name_el, s.lat, s.lng, ls.seq"
        " FROM line_stations ls JOIN stations s ON s.id = ls.station_id"
        " ORDER BY ls.line_id, ls.seq"
    ):
        station_rows_by_line[r["line_id"]].append(r)

    for ln in line_rows:
        stops = station_rows_by_line.get(ln["id"], [])
        lines.append({
            "id": ln["id"],
            "name": ln["name_en"],
            "nameEl": ln["name_el"],
            "type": ln["mode"],
            "color": ln["color"],
            "terminalA": ln["terminal_a"],
            "terminalB": ln["terminal_b"],
            "stationCount": len(stops),
            "stations": [
                {
                    "id": s["id"],
                    "name": s["name_en"],
                    "nameEl": s["name_el"],
                    "lat": s["lat"],
                    "lng": s["lng"],
                }
                for s in stops
            ],
        })
    return {
        "version": int(conn.execute("SELECT value FROM meta WHERE key='version'").fetchone()["value"]),
        "updatedAt": conn.execute("SELECT value FROM meta WHERE key='updated_at'").fetchone()["value"],
        "lines": lines,
    }


def _build_holidays(conn: sqlite3.Connection) -> dict:
    rules = conn.execute(
        "SELECT name, date_pattern, day_type, notes FROM holiday_rules ORDER BY id"
    ).fetchall()
    return {
        "updatedAt": _now_iso(),
        "rules": [
            {
                "name": r["name"],
                "datePattern": r["date_pattern"],
                "dayType": r["day_type"],
                "notes": r["notes"] or "",
            }
            for r in rules
        ],
    }


def _build_overrides(conn: sqlite3.Connection) -> dict:
    rows = conn.execute(
        "SELECT override_date, line_id, source, payload_json, fetched_at"
        " FROM date_overrides ORDER BY override_date, line_id"
    ).fetchall()
    return {
        "updatedAt": _now_iso(),
        "overrides": [
            {
                "date": r["override_date"],
                "lineId": r["line_id"],
                "source": r["source"],
                "payload": json.loads(r["payload_json"]),
                "fetchedAt": r["fetched_at"],
            }
            for r in rows
        ],
    }


def _line_schedule(conn: sqlite3.Connection, line_id: str) -> dict:
    rules = conn.execute(
        "SELECT day_type, open_time, close_time, is_24_7, notes"
        " FROM schedule_rules WHERE line_id=? ORDER BY day_type",
        (line_id,),
    ).fetchall()
    bands = conn.execute(
        "SELECT day_type, time_start, time_end, headway_minutes, label"
        " FROM frequency_bands WHERE line_id=? ORDER BY day_type, time_start",
        (line_id,),
    ).fetchall()
    return {
        "lineId": line_id,
        "rules": [
            {
                "dayType": r["day_type"],
                "openTime": r["open_time"],
                "closeTime": r["close_time"],
                "is247": bool(r["is_24_7"]),
                "notes": r["notes"] or "",
            }
            for r in rules
        ],
        "bands": [
            {
                "dayType": b["day_type"],
                "timeStart": b["time_start"],
                "timeEnd": b["time_end"],
                "headwayMinutes": b["headway_minutes"],
                "label": b["label"] or "",
            }
            for b in bands
        ],
    }


def generate(out_dir: Path = DEFAULT_OUT, db_path: str | None = None) -> dict:
    """Write all snapshots; bump meta.version and meta.etag in the DB."""
    out_dir.mkdir(parents=True, exist_ok=True)
    with dbmod.connect(db_path or dbmod.DEFAULT_DB_PATH) as conn:
        # /api/lines (extends today's static file)
        lines_payload = _build_lines(conn)
        lines_hash = _atomic_write_json(out_dir / "lines.json", lines_payload)

        # /api/holidays
        holidays_payload = _build_holidays(conn)
        holidays_hash = _atomic_write_json(out_dir / "holidays.json", holidays_payload)

        # /api/overrides
        overrides_payload = _build_overrides(conn)
        overrides_hash = _atomic_write_json(out_dir / "overrides.json", overrides_payload)

        # /api/schedules/{lineId} per line
        line_ids = [r["id"] for r in conn.execute("SELECT id FROM lines ORDER BY sort_order")]
        per_line_hashes: dict[str, str] = {}
        full_schedules: list[dict] = []
        for lid in line_ids:
            sched = _line_schedule(conn, lid)
            full_schedules.append(sched)
            digest = _atomic_write_json(out_dir / "schedules" / f"{lid}.json", sched)
            per_line_hashes[lid] = digest

        # /api/schedules (full snapshot)
        schedules_full = {
            "updatedAt": _now_iso(),
            "lines": full_schedules,
        }
        full_hash = _atomic_write_json(out_dir / "schedules.json", schedules_full)

        # /api/schedules/manifest
        client_min = conn.execute(
            "SELECT value FROM meta WHERE key='client_min_version'"
        ).fetchone()["value"]
        manifest_payload = {
            "version": _bump_version(conn),
            "updatedAt": _now_iso(),
            "clientMinVersion": int(client_min),
            "etag": full_hash,
            "perLineHashes": per_line_hashes,
            "linesHash": lines_hash,
            "holidaysHash": holidays_hash,
            "overridesHash": overrides_hash,
        }
        manifest_hash = _atomic_write_json(out_dir / "schedules-manifest.json", manifest_payload)

        conn.execute("UPDATE meta SET value=? WHERE key='etag'", (manifest_hash,))
        conn.execute(
            "UPDATE meta SET value=strftime('%Y-%m-%dT%H:%M:%SZ','now') WHERE key='updated_at'"
        )

        return {
            "out_dir": str(out_dir),
            "version": manifest_payload["version"],
            "manifest_etag": manifest_hash,
            "files": [
                "lines.json",
                "schedules.json",
                "schedules-manifest.json",
                "holidays.json",
                "overrides.json",
                *[f"schedules/{lid}.json" for lid in line_ids],
            ],
        }


def _bump_version(conn: sqlite3.Connection) -> int:
    row = conn.execute("SELECT value FROM meta WHERE key='version'").fetchone()
    v = int(row["value"]) + 1
    conn.execute("UPDATE meta SET value=? WHERE key='version'", (str(v),))
    return v


if __name__ == "__main__":
    result = generate()
    print(json.dumps(result, indent=2))
