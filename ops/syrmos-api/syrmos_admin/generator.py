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


def _build_stations(conn: sqlite3.Connection) -> dict:
    """Flat station list with current bilingual name, coords, and accessibility."""
    rows = conn.execute(
        "SELECT s.id, s.name_en, s.name_el, s.lat, s.lng,"
        " (SELECT GROUP_CONCAT(ls.line_id) FROM line_stations ls WHERE ls.station_id = s.id) AS line_ids"
        " FROM stations s ORDER BY s.id"
    ).fetchall()
    return {
        "updatedAt": _now_iso(),
        "stations": [
            {
                "id": r["id"],
                "nameEn": r["name_en"],
                "nameEl": r["name_el"],
                "lat": r["lat"],
                "lng": r["lng"],
                "lineIds": [l for l in (r["line_ids"] or "").split(",") if l],
            }
            for r in rows
        ],
    }


def _build_operators(conn: sqlite3.Connection) -> dict:
    """Public list of operator feed registrations. `auth_credential` is NEVER
    serialized — admins manage that field in the admin UI only."""
    rows = conn.execute(
        "SELECT operator_id, operator_name, contact_email, contact_url,"
        " feed_kind, feed_url, auth_method, refresh_seconds, status,"
        " notes, last_seen_at, enabled_at, updated_at"
        " FROM operator_partners ORDER BY operator_id, feed_kind"
    ).fetchall()
    return {
        "updatedAt": _now_iso(),
        "operators": [
            {
                "operatorId": r["operator_id"],
                "operatorName": r["operator_name"],
                "contactEmail": r["contact_email"] or "",
                "contactUrl": r["contact_url"] or "",
                "feedKind": r["feed_kind"],
                "feedUrl": r["feed_url"] or "",
                "authMethod": r["auth_method"],
                "refreshSeconds": r["refresh_seconds"],
                "status": r["status"],
                "notes": r["notes"] or "",
                "lastSeenAt": r["last_seen_at"] or "",
                "enabledAt": r["enabled_at"] or "",
                "updatedAt": r["updated_at"],
            }
            for r in rows
        ],
    }


def _build_icons(conn: sqlite3.Connection) -> dict:
    """Effective icon manifest: station-id -> SVG url, plus vehicle direction map.
    Override URL wins over default; consumers use this as the source of truth."""
    rows = conn.execute(
        "SELECT scope, station_id, line_id, direction, default_url, override_url"
        " FROM icons ORDER BY scope, station_id, line_id"
    ).fetchall()
    stations: dict[str, str] = {}
    interchanges: dict[str, str] = {}
    vehicles: dict[str, dict] = {}
    for r in rows:
        url = r["override_url"] or r["default_url"]
        if r["scope"] == "station" and r["station_id"]:
            stations[r["station_id"]] = url
        elif r["scope"] == "interchange" and r["station_id"]:
            interchanges[r["station_id"]] = url
        elif r["scope"] == "vehicle":
            key = f"{r['line_id'] or 'generic'}:{r['direction'] or '_'}"
            vehicles[key] = {
                "url": url,
                "lineId": r["line_id"],
                "direction": r["direction"],
            }
    return {
        "updatedAt": _now_iso(),
        "stations": stations,
        "interchanges": interchanges,
        "vehicles": vehicles,
    }


def _build_line_display(conn: sqlite3.Connection) -> dict:
    rows = conn.execute(
        "SELECT line_id, stroke_color, stroke_weight, stroke_dash, label_color, glow, notes, updated_at"
        " FROM line_display ORDER BY line_id"
    ).fetchall()
    return {
        "updatedAt": _now_iso(),
        "lines": [
            {
                "lineId": r["line_id"],
                "strokeColor": r["stroke_color"],
                "strokeWeight": r["stroke_weight"],
                "strokeDash": r["stroke_dash"],
                "labelColor": r["label_color"],
                "glow": bool(r["glow"]),
                "notes": r["notes"] or "",
                "updatedAt": r["updated_at"],
            }
            for r in rows
        ],
    }


def _build_fares(conn: sqlite3.Connection) -> dict:
    rows = conn.execute(
        "SELECT operator_id, region, prices_url, prices_url_el, currency,"
        " contactless_methods, contactless_locations, notes_en, notes_el, updated_at"
        " FROM fares ORDER BY operator_id"
    ).fetchall()
    return {
        "updatedAt": _now_iso(),
        "fares": [
            {
                "operatorId": r["operator_id"],
                "region": r["region"],
                "pricesUrl": r["prices_url"],
                "pricesUrlEl": r["prices_url_el"] or r["prices_url"],
                "currency": r["currency"],
                "contactlessMethods": json.loads(r["contactless_methods"]),
                "contactlessLocations": json.loads(r["contactless_locations"]),
                "notesEn": r["notes_en"] or "",
                "notesEl": r["notes_el"] or "",
                "updatedAt": r["updated_at"],
            }
            for r in rows
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

        # /api/fares
        fares_payload = _build_fares(conn)
        fares_hash = _atomic_write_json(out_dir / "fares.json", fares_payload)

        # /api/icons
        icons_payload = _build_icons(conn)
        icons_hash = _atomic_write_json(out_dir / "icons.json", icons_payload)

        # /api/line-display
        ld_payload = _build_line_display(conn)
        ld_hash = _atomic_write_json(out_dir / "line-display.json", ld_payload)

        # /api/stations
        stations_payload = _build_stations(conn)
        stations_hash = _atomic_write_json(out_dir / "stations.json", stations_payload)

        # /api/operators
        operators_payload = _build_operators(conn)
        operators_hash = _atomic_write_json(out_dir / "operators.json", operators_payload)

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
            "faresHash": fares_hash,
            "iconsHash": icons_hash,
            "lineDisplayHash": ld_hash,
            "stationsHash": stations_hash,
            "operatorsHash": operators_hash,
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
                "fares.json",
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
