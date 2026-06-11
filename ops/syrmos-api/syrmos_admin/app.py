"""FastAPI admin service for Syrmos schedules.

Auth: relies on Cloudflare Access (Cf-Access-Authenticated-User-Email header).
Locally the service binds to 127.0.0.1:8091 only; nginx fronts it at /admin/
and Cloudflare Access enforces the login. If CF Access is disabled, the
HTTP_ADMIN_TOKEN env var is the fallback (X-Admin-Token header).

Run: uvicorn syrmos_admin.app:app --host 127.0.0.1 --port 8091
"""
from __future__ import annotations

import os
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterator

from fastapi import Depends, FastAPI, Form, HTTPException, Request
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse

from . import db as dbmod
from . import generator

ADMIN_TOKEN = os.environ.get("SYRMOS_ADMIN_TOKEN")  # fallback when CF Access is off
CF_USER_HEADER = "cf-access-authenticated-user-email"


def auth(request: Request) -> str:
    """Return the admin identity or raise 401."""
    user = request.headers.get(CF_USER_HEADER)
    if user:
        return user
    if ADMIN_TOKEN and request.headers.get("x-admin-token") == ADMIN_TOKEN:
        return "token-admin"
    raise HTTPException(status_code=401, detail="Unauthorized")


@contextmanager
def get_db() -> Iterator[Any]:
    conn = dbmod.connect()
    try:
        dbmod.migrate(conn)
        yield conn
    finally:
        conn.close()


app = FastAPI(title="Syrmos Admin", docs_url="/docs", openapi_url="/openapi.json")


# Tiny HTML chrome — no SPA, server-rendered HTMX-friendly pages.

BASE = """<!doctype html><html><head><meta charset="utf-8"><title>{title}</title>
<style>
 body{{font-family:-apple-system,Segoe UI,sans-serif;max-width:980px;margin:24px auto;padding:0 16px;color:#111}}
 nav a{{margin-right:14px;text-decoration:none;color:#0066c8}}
 table{{border-collapse:collapse;width:100%;margin:8px 0 24px}}
 th,td{{border-bottom:1px solid #ddd;padding:6px 8px;text-align:left;font-size:14px}}
 input,select{{padding:4px 6px;font-size:14px}}
 button{{padding:6px 12px;background:#0066c8;color:#fff;border:0;border-radius:4px;cursor:pointer}}
 .small{{font-size:12px;color:#666}}
 form.inline{{display:inline}}
</style></head><body>
<nav><strong>Syrmos Admin</strong> &middot;
 <a href="/lines">Lines</a>
 <a href="/frequency-bands">Frequency bands</a>
 <a href="/holidays">Holidays</a>
 <a href="/overrides">Overrides</a>
 <a href="/sync">Sync</a>
</nav><hr>{body}</body></html>"""


def page(title: str, body: str) -> HTMLResponse:
    return HTMLResponse(BASE.format(title=title, body=body))


# Root / status

@app.get("/", response_class=HTMLResponse)
def index(_: str = Depends(auth)) -> HTMLResponse:
    with get_db() as conn:
        meta = {r["key"]: r["value"] for r in conn.execute("SELECT key,value FROM meta")}
        n_lines = conn.execute("SELECT COUNT(*) c FROM lines").fetchone()["c"]
        n_stops = conn.execute("SELECT COUNT(*) c FROM line_stations").fetchone()["c"]
        n_bands = conn.execute("SELECT COUNT(*) c FROM frequency_bands").fetchone()["c"]
        n_over = conn.execute("SELECT COUNT(*) c FROM date_overrides").fetchone()["c"]
        last_scrape = conn.execute(
            "SELECT run_at, source, ok, rows_written, error FROM scrape_log ORDER BY id DESC LIMIT 1"
        ).fetchone()
    body = f"""
<h2>Status</h2>
<table>
 <tr><th>DB version</th><td>{meta.get('version', '?')}</td></tr>
 <tr><th>Last updated</th><td>{meta.get('updated_at', '?')}</td></tr>
 <tr><th>Manifest ETag</th><td><code class=small>{meta.get('etag', '')[:24]}…</code></td></tr>
 <tr><th>Client min version</th><td>{meta.get('client_min_version', '0')}</td></tr>
 <tr><th>Lines</th><td>{n_lines}</td></tr>
 <tr><th>Line-stations</th><td>{n_stops}</td></tr>
 <tr><th>Frequency bands</th><td>{n_bands}</td></tr>
 <tr><th>Date overrides</th><td>{n_over}</td></tr>
</table>
<h3>Last scrape</h3>
"""
    if last_scrape:
        ok = "ok" if last_scrape["ok"] else "FAILED"
        body += f"<p>{last_scrape['run_at']} &middot; {last_scrape['source']} &middot; {ok} &middot; {last_scrape['rows_written']} rows</p>"
        if last_scrape["error"]:
            body += f"<pre class=small>{last_scrape['error']}</pre>"
    else:
        body += "<p class=small>No scrape runs yet.</p>"
    return page("Syrmos Admin", body)


# Lines

@app.get("/lines", response_class=HTMLResponse)
def lines_page(_: str = Depends(auth)) -> HTMLResponse:
    with get_db() as conn:
        rows = conn.execute(
            "SELECT id, mode, name_en, name_el, color, terminal_a, terminal_b, sort_order"
            " FROM lines ORDER BY sort_order"
        ).fetchall()
    tr = "".join(
        f"<tr><td><code>{r['id']}</code></td><td>{r['mode']}</td>"
        f"<td>{r['name_en']}</td><td>{r['name_el']}</td>"
        f"<td><span style='background:{r['color']};padding:2px 10px;color:#fff'>{r['color']}</span></td>"
        f"<td>{r['terminal_a']} → {r['terminal_b']}</td>"
        f"<td><a href='/lines/{r['id']}/edit'>edit</a></td></tr>"
        for r in rows
    )
    return page(
        "Lines",
        f"<h2>Lines</h2><table><tr><th>ID</th><th>Mode</th><th>Name EN</th><th>Name EL</th><th>Color</th><th>Terminals</th><th></th></tr>{tr}</table>",
    )


@app.get("/lines/{line_id}/edit", response_class=HTMLResponse)
def edit_line(line_id: str, _: str = Depends(auth)) -> HTMLResponse:
    with get_db() as conn:
        r = conn.execute(
            "SELECT id, mode, name_en, name_el, color, terminal_a, terminal_b"
            " FROM lines WHERE id=?",
            (line_id,),
        ).fetchone()
    if not r:
        raise HTTPException(404, "Line not found")
    body = f"""
<h2>Edit line {r['id']}</h2>
<form method=post action=/lines/{r['id']}/save>
 <table>
  <tr><th>Name EN</th><td><input name=name_en value="{r['name_en']}" size=40></td></tr>
  <tr><th>Name EL</th><td><input name=name_el value="{r['name_el']}" size=40></td></tr>
  <tr><th>Color (hex)</th><td><input name=color value="{r['color']}" size=10></td></tr>
  <tr><th>Terminal A</th><td><input name=terminal_a value="{r['terminal_a']}" size=30></td></tr>
  <tr><th>Terminal B</th><td><input name=terminal_b value="{r['terminal_b']}" size=30></td></tr>
 </table>
 <button>Save and regenerate</button>
</form>"""
    return page(f"Edit {r['id']}", body)


@app.post("/lines/{line_id}/save")
def save_line(
    line_id: str,
    name_en: str = Form(...),
    name_el: str = Form(...),
    color: str = Form(...),
    terminal_a: str = Form(...),
    terminal_b: str = Form(...),
    _: str = Depends(auth),
) -> RedirectResponse:
    with get_db() as conn:
        conn.execute(
            "UPDATE lines SET name_en=?, name_el=?, color=?, terminal_a=?, terminal_b=?"
            " WHERE id=?",
            (name_en, name_el, color, terminal_a, terminal_b, line_id),
        )
    generator.generate()
    return RedirectResponse("/lines", status_code=303)


# Frequency bands

@app.get("/frequency-bands", response_class=HTMLResponse)
def bands_page(line: str | None = None, day_type: str | None = None,
               _: str = Depends(auth)) -> HTMLResponse:
    with get_db() as conn:
        line_ids = [r["id"] for r in conn.execute("SELECT id FROM lines ORDER BY sort_order")]
        line = line or line_ids[0]
        day_type = day_type or "mon_thu"
        rows = conn.execute(
            "SELECT rowid, time_start, time_end, headway_minutes, label"
            " FROM frequency_bands WHERE line_id=? AND day_type=? ORDER BY time_start",
            (line, day_type),
        ).fetchall()
    line_opts = "".join(
        f"<option value='{lid}' {'selected' if lid == line else ''}>{lid}</option>"
        for lid in line_ids
    )
    day_opts = "".join(
        f"<option value='{d}' {'selected' if d == day_type else ''}>{d}</option>"
        for d in ("mon_thu", "fri", "sat", "sun", "holiday", "bank_holiday", "aug_15", "dec_24_31")
    )
    tr = "".join(
        f"<tr><td>{r['time_start']}</td><td>{r['time_end']}</td>"
        f"<td>{r['headway_minutes']}</td><td>{r['label'] or ''}</td>"
        f"<td><form class=inline method=post action=/frequency-bands/delete>"
        f"<input type=hidden name=rowid value={r['rowid']}>"
        f"<input type=hidden name=line value={line}>"
        f"<input type=hidden name=day_type value={day_type}>"
        f"<button>delete</button></form></td></tr>"
        for r in rows
    )
    body = f"""
<h2>Frequency bands</h2>
<form method=get>
 Line <select name=line>{line_opts}</select>
 Day type <select name=day_type>{day_opts}</select>
 <button>Show</button>
</form>
<table>
 <tr><th>Start</th><th>End</th><th>Headway (min)</th><th>Label</th><th></th></tr>{tr}
</table>
<h3>Add band</h3>
<form method=post action=/frequency-bands/add>
 <input type=hidden name=line value={line}>
 <input type=hidden name=day_type value={day_type}>
 Start <input name=time_start placeholder=07:00 size=6>
 End <input name=time_end placeholder=10:00 size=6>
 Headway <input name=headway_minutes placeholder=4.0 size=6>
 Label <input name=label placeholder=morning_peak size=18>
 <button>Add</button>
</form>"""
    return page("Frequency bands", body)


@app.post("/frequency-bands/add")
def add_band(line: str = Form(...), day_type: str = Form(...),
             time_start: str = Form(...), time_end: str = Form(...),
             headway_minutes: float = Form(...), label: str = Form(""),
             _: str = Depends(auth)) -> RedirectResponse:
    with get_db() as conn:
        conn.execute(
            "INSERT OR REPLACE INTO frequency_bands(line_id, day_type, time_start, time_end, headway_minutes, label)"
            " VALUES(?,?,?,?,?,?)",
            (line, day_type, time_start, time_end, headway_minutes, label or None),
        )
    generator.generate()
    return RedirectResponse(f"/frequency-bands?line={line}&day_type={day_type}", status_code=303)


@app.post("/frequency-bands/delete")
def delete_band(rowid: int = Form(...), line: str = Form(...), day_type: str = Form(...),
                _: str = Depends(auth)) -> RedirectResponse:
    with get_db() as conn:
        conn.execute("DELETE FROM frequency_bands WHERE rowid=?", (rowid,))
    generator.generate()
    return RedirectResponse(f"/frequency-bands?line={line}&day_type={day_type}", status_code=303)


# Holidays

@app.get("/holidays", response_class=HTMLResponse)
def holidays_page(_: str = Depends(auth)) -> HTMLResponse:
    with get_db() as conn:
        rows = conn.execute(
            "SELECT id, name, date_pattern, day_type, notes FROM holiday_rules ORDER BY id"
        ).fetchall()
    tr = "".join(
        f"<tr><td>{r['name']}</td><td><code>{r['date_pattern']}</code></td>"
        f"<td>{r['day_type']}</td><td>{r['notes'] or ''}</td></tr>"
        for r in rows
    )
    return page(
        "Holidays",
        f"<h2>Holiday rules</h2><p class=small>Static for now. Edit via DB or future UI.</p>"
        f"<table><tr><th>Name</th><th>Pattern</th><th>Day type</th><th>Notes</th></tr>{tr}</table>",
    )


# Overrides

@app.get("/overrides", response_class=HTMLResponse)
def overrides_page(_: str = Depends(auth)) -> HTMLResponse:
    with get_db() as conn:
        rows = conn.execute(
            "SELECT id, override_date, line_id, source, fetched_at"
            " FROM date_overrides ORDER BY override_date DESC, line_id LIMIT 200"
        ).fetchall()
    tr = "".join(
        f"<tr><td>{r['override_date']}</td><td>{r['line_id']}</td>"
        f"<td>{r['source']}</td><td class=small>{r['fetched_at']}</td>"
        f"<td><form class=inline method=post action=/overrides/delete>"
        f"<input type=hidden name=id value={r['id']}><button>delete</button></form></td></tr>"
        for r in rows
    )
    return page(
        "Overrides",
        f"<h2>Date overrides</h2>"
        f"<form method=post action=/scrape/run><button>Scrape 24mmm now</button></form>"
        f"<table><tr><th>Date</th><th>Line</th><th>Source</th><th>Fetched</th><th></th></tr>{tr}</table>",
    )


@app.post("/overrides/delete")
def delete_override(id: int = Form(...), _: str = Depends(auth)) -> RedirectResponse:
    with get_db() as conn:
        conn.execute("DELETE FROM date_overrides WHERE id=?", (id,))
    generator.generate()
    return RedirectResponse("/overrides", status_code=303)


# Sync / scrape

@app.get("/sync", response_class=HTMLResponse)
def sync_page(_: str = Depends(auth)) -> HTMLResponse:
    body = """
<h2>Sync</h2>
<form method=post action=/sync/regen><button>Regenerate JSON snapshots</button></form>
<p class=small>Reads the DB and writes lines.json, schedules.json, manifest, holidays, overrides.</p>
<form method=post action=/scrape/run><button>Run OASA 24mmm scraper now</button></form>
<p class=small>Pulls next 30 days for M2, M3, T6, T7. Writes only diffs vs the rule output.</p>
"""
    return page("Sync", body)


@app.post("/sync/regen")
def sync_regen(_: str = Depends(auth)) -> RedirectResponse:
    generator.generate()
    return RedirectResponse("/", status_code=303)


@app.post("/scrape/run")
def scrape_run(_: str = Depends(auth)) -> RedirectResponse:
    from .scraper_24mmm import run_once
    run_once()
    generator.generate()
    return RedirectResponse("/overrides", status_code=303)


# Health (unauthenticated)

@app.get("/healthz")
def healthz() -> JSONResponse:
    return JSONResponse({"ok": True})
