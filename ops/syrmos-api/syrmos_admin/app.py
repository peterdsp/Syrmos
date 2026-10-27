"""FastAPI admin service for Syrmos schedules.

Auth: Cloudflare Access in front of /admin/, verified here (see security.py):
the Cf-Access-Jwt-Assertion JWT is checked against the team certs, audience
and expiry, and its email must be in ADMIN_EMAILS. Locally the service binds
to 127.0.0.1 only; nginx fronts it at /admin/. HTTP_ADMIN_TOKEN (X-Admin-Token
header) is a fallback only when set. With neither configured, admin is closed.

Run: uvicorn syrmos_admin.app:app --host 127.0.0.1 --port 8091
"""
from __future__ import annotations

import asyncio
import contextvars
import hashlib
import json
import logging
import os
import pathlib
import re
import time
import datetime as _dt
from contextlib import contextmanager
from html import escape as _html_escape
from pathlib import Path
from typing import Any, Iterator

from fastapi import BackgroundTasks, Depends, FastAPI, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse, Response

from . import ariadne as ariadne_mod
from . import community as community_mod
from . import db as dbmod
from . import generator
from . import projector as projector_mod
from . import security

logger = logging.getLogger(__name__)
AIRPORT_BUS_SNAPSHOT = Path(os.environ.get(
    "AIRPORT_BUS_SNAPSHOT",
    str(Path(__file__).resolve().parent.parent / "out" / "airport-buses.json"),
))


# Identity of the admin making the current request. Set by auth() (async, so
# it runs in the request task and the value is visible to the endpoint) and
# read by page() to sign the CSRF token embedded in every POST form.
_ADMIN_IDENTITY: contextvars.ContextVar[str | None] = contextvars.ContextVar(
    "syrmos_admin_identity", default=None
)
_UNSAFE_METHODS = {"POST", "PUT", "PATCH", "DELETE"}


def _audit_connect():
    conn = dbmod.connect()
    dbmod.migrate(conn)
    return conn


async def auth(request: Request) -> str:
    """Return the admin identity or raise 401/403.

    Mutating requests from a browser session must carry a valid CSRF token,
    and every accepted mutating request writes one admin_audit row.
    """
    try:
        identity, needs_csrf = security.identify(request.headers)
    except security.AuthError as exc:
        logger.info("admin auth rejected for %s: %s", request.url.path, exc)
        raise HTTPException(status_code=401, detail="Unauthorized") from None
    _ADMIN_IDENTITY.set(identity)
    if request.method not in _UNSAFE_METHODS:
        return identity
    items: list[tuple[str, Any]] = []
    if "form" in (request.headers.get("content-type") or ""):
        form = await request.form()
        items = list(form.multi_items())
    fields = dict(items)
    if needs_csrf:
        fetch_site = request.headers.get("sec-fetch-site")
        if fetch_site and fetch_site not in ("same-origin", "none"):
            raise HTTPException(status_code=403, detail="Cross-site admin request refused")
        token = fields.get(security.CSRF_FIELD) or request.headers.get("x-csrf-token")
        if not security.check_csrf_token(identity, token if isinstance(token, str) else None):
            raise HTTPException(status_code=403, detail="CSRF token missing or expired, reload the page")
    action = fields.get("action")
    security.write_audit(
        _audit_connect,
        actor=identity,
        method=request.method,
        path=request.url.path,
        action=action if isinstance(action, str) else None,
        digest=security.payload_hash(items),
    )
    return identity


@contextmanager
def get_db() -> Iterator[Any]:
    conn = dbmod.connect()
    try:
        dbmod.migrate(conn)
        yield conn
    finally:
        conn.close()


# docs_url is None because we serve our own /docs page (the editable-surfaces
# + endpoint catalogue with a Publish button). FastAPI's auto Swagger lives
# at /admin/swagger instead so it's still reachable but doesn't shadow the
# human-readable docs page.
app = FastAPI(
    title="Syrmos Admin",
    docs_url="/swagger",
    redoc_url="/redoc",
    openapi_url="/openapi.json",
)


# Admin URL prefix. The service is mounted under /admin/ by nginx, so every
# link, form action, and redirect needs that prefix. Earlier versions used
# bare absolute paths (href="/lines") which the browser resolved against
# the public host, sending the user to api-syrmos.peterdsp.dev/lines instead
# of /admin/lines. The base tag below + the P() helper resolve relative
# paths against /admin/ consistently across every page.
ADMIN_PREFIX = "/admin"


def P(path: str) -> str:
    """Prefix a relative admin path with /admin/. Pass '' for the index."""
    if not path:
        return f"{ADMIN_PREFIX}/"
    if path.startswith("/"):
        path = path[1:]
    return f"{ADMIN_PREFIX}/{path}"


BASE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>{title} - Syrmos Admin</title>
<style>
:root {{
  --bg: #f7f8fa;
  --panel: #ffffff;
  --panel-2: #f3f4f6;
  --border: #e5e7eb;
  --text: #111827;
  --text-muted: #6b7280;
  --accent: #0072ce;
  --accent-soft: rgba(0, 114, 206, 0.12);
  --danger: #dc2626;
  --success: #16a34a;
  --warning: #d97706;
  --radius: 10px;
  --radius-sm: 6px;
  --shadow: 0 1px 2px rgba(0,0,0,0.04), 0 4px 12px rgba(0,0,0,0.04);
}}
@media (prefers-color-scheme: dark) {{
  :root {{
    --bg: #0b0d10;
    --panel: #15181d;
    --panel-2: #1d2127;
    --border: #2a2f37;
    --text: #f3f4f6;
    --text-muted: #9aa0a6;
    --accent: #4ea4ff;
    --accent-soft: rgba(78, 164, 255, 0.18);
    --shadow: 0 1px 2px rgba(0,0,0,0.4), 0 6px 16px rgba(0,0,0,0.35);
  }}
}}
* {{ box-sizing: border-box; }}
html, body {{ background: var(--bg); color: var(--text); margin: 0; }}
body {{
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  font-size: 14px;
  line-height: 1.45;
  min-height: 100vh;
}}
.shell {{ max-width: 1180px; margin: 0 auto; padding: 0 16px 64px; }}

/* Top bar */
.topbar {{
  position: sticky; top: 0; z-index: 10;
  background: var(--panel);
  border-bottom: 1px solid var(--border);
  padding: 14px 0;
  margin-bottom: 24px;
}}
.topbar-inner {{
  max-width: 1180px;
  margin: 0 auto;
  padding: 0 16px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}}
.brand {{
  display: flex; align-items: center; gap: 10px;
  font-weight: 700; font-size: 16px;
  color: var(--text);
  text-decoration: none;
}}
.brand-dot {{
  width: 10px; height: 10px; border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 4px var(--accent-soft);
}}
nav.tabs {{
  display: flex; gap: 4px; flex-wrap: wrap;
  margin-left: auto;
}}
nav.tabs a {{
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  text-decoration: none;
  font-weight: 500;
  transition: background .12s, color .12s;
}}
nav.tabs a:hover {{ background: var(--panel-2); color: var(--text); }}
nav.tabs a.is-active {{
  background: var(--accent-soft);
  color: var(--accent);
}}

/* Cards + content */
h1, h2, h3 {{ color: var(--text); margin: 0 0 12px; }}
h2 {{ font-size: 20px; font-weight: 700; }}
h3 {{ font-size: 16px; font-weight: 600; margin-top: 20px; }}
.card {{
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 18px;
  margin-bottom: 18px;
  box-shadow: var(--shadow);
}}

/* Tables */
table {{
  border-collapse: separate;
  border-spacing: 0;
  width: 100%;
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
  margin: 0 0 18px;
}}
th, td {{
  padding: 10px 12px;
  text-align: left;
  vertical-align: middle;
  font-size: 13.5px;
  border-bottom: 1px solid var(--border);
}}
tr:last-child td {{ border-bottom: 0; }}
th {{
  background: var(--panel-2);
  color: var(--text-muted);
  font-weight: 600;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  position: sticky; top: 0;
}}
tr:hover td {{ background: var(--panel-2); }}

/* Forms */
input, select, textarea {{
  background: var(--panel);
  color: var(--text);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 7px 10px;
  font: inherit;
  outline: none;
  transition: border-color .12s, box-shadow .12s;
}}
input:focus, select:focus, textarea:focus {{
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
}}
button, input[type=submit] {{
  background: var(--accent);
  color: #fff;
  border: 0;
  border-radius: var(--radius-sm);
  padding: 8px 14px;
  font-weight: 600;
  cursor: pointer;
  transition: filter .12s, transform .04s;
}}
button:hover {{ filter: brightness(1.06); }}
button:active {{ transform: translateY(1px); }}
button.ghost {{
  background: transparent;
  color: var(--text);
  border: 1px solid var(--border);
}}
button.danger {{ background: var(--danger); }}
form.inline {{ display: inline; }}
a.action {{
  display: inline-block; margin: 2px 4px 2px 0; padding: 6px 12px;
  border: 1px solid var(--border); border-radius: 8px;
  color: var(--text); text-decoration: none; font-size: 13px;
}}
a.action:hover {{ background: var(--panel-2); }}

/* Small badges + meta */
.small {{ font-size: 12px; color: var(--text-muted); }}
code.small {{
  background: var(--panel-2);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}}
.color-swatch {{
  display: inline-block; width: 14px; height: 14px;
  border-radius: 3px; vertical-align: middle; margin-right: 6px;
  border: 1px solid rgba(0,0,0,0.1);
}}
img.icon-preview {{
  height: 32px; width: auto;
  background: var(--panel-2);
  border-radius: 4px;
  padding: 2px;
}}
.filter-bar {{
  background: var(--panel-2);
  padding: 10px 14px;
  border-radius: var(--radius-sm);
  margin-bottom: 12px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}}
.pill {{
  display: inline-flex; align-items: center; gap: 6px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}}
.pill-success {{ background: rgba(22, 163, 74, 0.14); color: var(--success); }}
.pill-danger {{ background: rgba(220, 38, 38, 0.14); color: var(--danger); }}
.pill-muted {{ background: var(--panel-2); color: var(--text-muted); }}

/* Stat grid for index */
.stat-grid {{
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}}
.stat {{
  background: var(--panel);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 14px 16px;
}}
.stat-label {{ font-size: 11px; text-transform: uppercase; color: var(--text-muted); letter-spacing: .06em; }}
.stat-value {{ font-size: 22px; font-weight: 700; margin-top: 4px; }}
</style>
</head>
<body>
<header class="topbar">
  <div class="topbar-inner">
    <a class="brand" href="{prefix}/"><span class="brand-dot"></span>Syrmos Admin</a>
    <nav class="tabs">
      <a href="{prefix}/lines" class="{c_lines}">Lines</a>
      <a href="{prefix}/frequency-bands" class="{c_bands}">Frequency bands</a>
      <a href="{prefix}/holidays" class="{c_holidays}">Holidays</a>
      <a href="{prefix}/overrides" class="{c_overrides}">Overrides</a>
      <a href="{prefix}/icons" class="{c_icons}">Icons</a>
      <a href="{prefix}/line-display" class="{c_line_display}">Line drawing</a>
      <a href="{prefix}/stations" class="{c_stations}">Stations</a>
      <a href="{prefix}/operators" class="{c_operators}">Operators</a>
      <a href="{prefix}/closures" class="{c_closures}">Closures</a>
      <a href="{prefix}/broadcasts" class="{c_broadcasts}">Broadcasts</a>
      <a href="{prefix}/push" class="{c_push}">Push</a>
      <a href="{prefix}/sync" class="{c_sync}">Sync</a>
      <a href="{prefix}/docs" class="{c_docs}">Docs</a>
    </nav>
  </div>
</header>
<main class="shell">
{body}
</main>
</body>
</html>"""


_TAB_KEYS = {
    "lines": "c_lines",
    "frequency-bands": "c_bands",
    "holidays": "c_holidays",
    "overrides": "c_overrides",
    "icons": "c_icons",
    "line-display": "c_line_display",
    "stations": "c_stations",
    "operators": "c_operators",
    "closures": "c_closures",
    "broadcasts": "c_broadcasts",
    "push": "c_push",
    "sync": "c_sync",
    "docs": "c_docs",
}


_POST_FORM_RE = re.compile(r"(<form\b[^>]*\bmethod=[\"']?post\b[\"']?[^>]*>)", re.IGNORECASE)


def with_csrf(body: str, identity: str | None) -> str:
    """Append the signed CSRF field to every POST form in body."""
    if not identity:
        return body
    field = (
        f"<input type=hidden name={security.CSRF_FIELD} "
        f"value='{security.make_csrf_token(identity)}'>"
    )
    return _POST_FORM_RE.sub(lambda m: m.group(1) + field, body)


def page(title: str, body: str, active: str = "") -> HTMLResponse:
    fields = {k: "" for k in _TAB_KEYS.values()}
    if active in _TAB_KEYS:
        fields[_TAB_KEYS[active]] = "is-active"
    return HTMLResponse(BASE.format(
        title=title,
        body=with_csrf(body, _ADMIN_IDENTITY.get()),
        prefix=ADMIN_PREFIX,
        **fields,
    ))


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
        f"<td><a href='{ADMIN_PREFIX}/lines/{r['id']}/edit'>edit</a></td></tr>"
        for r in rows
    )
    return page(
        "Lines",
        f"<h2>Lines</h2><table><tr><th>ID</th><th>Mode</th><th>Name EN</th><th>Name EL</th><th>Color</th><th>Terminals</th><th></th></tr>{tr}</table>",
        active="lines",
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
<form method=post action={ADMIN_PREFIX}/lines/{r['id']}/save>
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
    return RedirectResponse(f"{ADMIN_PREFIX}/lines", status_code=303)


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
        f"<td><form class=inline method=post action={ADMIN_PREFIX}/frequency-bands/delete>"
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
<form method=post action={ADMIN_PREFIX}/frequency-bands/add>
 <input type=hidden name=line value={line}>
 <input type=hidden name=day_type value={day_type}>
 Start <input name=time_start placeholder=07:00 size=6>
 End <input name=time_end placeholder=10:00 size=6>
 Headway <input name=headway_minutes placeholder=4.0 size=6>
 Label <input name=label placeholder=morning_peak size=18>
 <button>Add</button>
</form>"""
    return page("Frequency bands", body, active="frequency-bands")


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
    return RedirectResponse(f"{ADMIN_PREFIX}/frequency-bands?line={line}&day_type={day_type}", status_code=303)


@app.post("/frequency-bands/delete")
def delete_band(rowid: int = Form(...), line: str = Form(...), day_type: str = Form(...),
                _: str = Depends(auth)) -> RedirectResponse:
    with get_db() as conn:
        conn.execute("DELETE FROM frequency_bands WHERE rowid=?", (rowid,))
    generator.generate()
    return RedirectResponse(f"{ADMIN_PREFIX}/frequency-bands?line={line}&day_type={day_type}", status_code=303)


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
        active="holidays",
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
        f"<td><form class=inline method=post action={ADMIN_PREFIX}/overrides/delete>"
        f"<input type=hidden name=id value={r['id']}><button>delete</button></form></td></tr>"
        for r in rows
    )
    return page(
        "Overrides",
        f"<h2>Date overrides</h2>"
        f"<form method=post action={ADMIN_PREFIX}/scrape/run><button>Scrape 24mmm now</button></form>"
        f"<table><tr><th>Date</th><th>Line</th><th>Source</th><th>Fetched</th><th></th></tr>{tr}</table>",
        active="overrides",
    )


@app.post("/overrides/delete")
def delete_override(id: int = Form(...), _: str = Depends(auth)) -> RedirectResponse:
    with get_db() as conn:
        conn.execute("DELETE FROM date_overrides WHERE id=?", (id,))
    generator.generate()
    return RedirectResponse(f"{ADMIN_PREFIX}/overrides", status_code=303)


# Icons: table of every station icon with preview + override field

@app.get("/icons", response_class=HTMLResponse)
def icons_page(scope: str = "station", _: str = Depends(auth)) -> HTMLResponse:
    with get_db() as conn:
        rows = conn.execute(
            "SELECT id, scope, station_id, line_id, direction, default_url, override_url, description"
            " FROM icons WHERE scope=? ORDER BY station_id, line_id, direction",
            (scope,),
        ).fetchall()
    tabs = "".join(
        f"<a href='/icons?scope={s}' style='margin-right:14px;font-weight:{600 if s == scope else 400}'>{s.title()}</a>"
        for s in ("station", "interchange", "vehicle")
    )
    tr = "".join(
        f"<tr><td><img class=icon-preview src='{r['override_url'] or r['default_url']}' loading=lazy></td>"
        f"<td><code>{r['station_id'] or ''}</code></td>"
        f"<td>{r['line_id'] or ''}</td><td>{r['direction'] or ''}</td>"
        f"<td class=small>{r['description'] or ''}</td>"
        f"<td><form method=post action={ADMIN_PREFIX}/icons/save>"
        f"<input type=hidden name=id value={r['id']}>"
        f"<input name=override_url value='{r['override_url'] or ''}' placeholder='paste alt URL' size=44>"
        f"<input type=hidden name=scope value={scope}>"
        f"<button>save</button></form></td></tr>"
        for r in rows
    )
    body = f"""
<h2>Icons</h2>
<p class=small>Each row is an SVG served from the Pi. Paste a URL into "override" to replace it everywhere without an app release. Cleared overrides fall back to the package default.</p>
<div class=filter-bar>Scope: {tabs}</div>
<table>
 <tr><th>Preview</th><th>Station</th><th>Line</th><th>Direction</th><th>Description</th><th>Override URL</th></tr>
 {tr}
</table>"""
    return page("Icons", body, active="icons")


@app.post("/icons/save")
def icons_save(id: int = Form(...), override_url: str = Form(""), scope: str = Form("station"),
               _: str = Depends(auth)) -> RedirectResponse:
    value = override_url.strip() or None
    with get_db() as conn:
        conn.execute(
            "UPDATE icons SET override_url=?, updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now') WHERE id=?",
            (value, id),
        )
    generator.generate()
    return RedirectResponse(f"{ADMIN_PREFIX}/icons?scope={scope}", status_code=303)


# Line drawing: editable polyline parameters

@app.get("/line-display", response_class=HTMLResponse)
def line_display_page(_: str = Depends(auth)) -> HTMLResponse:
    with get_db() as conn:
        rows = conn.execute(
            "SELECT line_id, stroke_color, stroke_weight, stroke_dash, label_color, glow, notes"
            " FROM line_display ORDER BY line_id"
        ).fetchall()
    tr = "".join(
        f"<tr><form method=post action={ADMIN_PREFIX}/line-display/save>"
        f"<td><code>{r['line_id']}</code></td>"
        f"<td><span class=color-swatch style='background:{r['stroke_color']}'></span>"
        f"<input type=hidden name=line_id value={r['line_id']}>"
        f"<input name=stroke_color value='{r['stroke_color']}' size=8></td>"
        f"<td><input name=stroke_weight type=number min=1 max=12 value={r['stroke_weight']} style=width:4em></td>"
        f"<td><input name=stroke_dash value='{r['stroke_dash'] or ''}' size=8 placeholder='6 4'></td>"
        f"<td><input name=label_color value='{r['label_color'] or ''}' size=8></td>"
        f"<td><input name=glow type=checkbox {'checked' if r['glow'] else ''}></td>"
        f"<td class=small>{r['notes'] or ''}</td>"
        f"<td><button>save</button></td></form></tr>"
        for r in rows
    )
    return page(
        "Line drawing",
        f"<h2>Line drawing</h2>"
        f"<p class=small>Polyline rendering. Apps fetch <code>/api/line-display</code> on cold start and apply these to the map line strokes.</p>"
        f"<table><tr><th>Line</th><th>Stroke</th><th>Weight</th><th>Dash</th><th>Label</th><th>Glow</th><th>Notes</th><th></th></tr>{tr}</table>",
        active="line-display",
    )


@app.post("/line-display/save")
def line_display_save(
    line_id: str = Form(...), stroke_color: str = Form(...), stroke_weight: int = Form(...),
    stroke_dash: str = Form(""), label_color: str = Form(""), glow: str = Form(""),
    _: str = Depends(auth)
) -> RedirectResponse:
    with get_db() as conn:
        conn.execute(
            "UPDATE line_display SET stroke_color=?, stroke_weight=?, stroke_dash=?,"
            " label_color=?, glow=?, updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now')"
            " WHERE line_id=?",
            (
                stroke_color, stroke_weight,
                stroke_dash.strip() or None,
                label_color.strip() or None,
                1 if glow else 0,
                line_id,
            ),
        )
    generator.generate()
    return RedirectResponse(f"{ADMIN_PREFIX}/line-display", status_code=303)


# Stations: bulk edit names, coords and accessibility

@app.get("/stations", response_class=HTMLResponse)
def stations_page(line: str | None = None, q: str | None = None,
                  _: str = Depends(auth)) -> HTMLResponse:
    with get_db() as conn:
        line_ids = [r["id"] for r in conn.execute("SELECT id FROM lines ORDER BY sort_order")]
        where = []
        params: list = []
        if line:
            where.append("EXISTS(SELECT 1 FROM line_stations ls WHERE ls.station_id=s.id AND ls.line_id=?)")
            params.append(line)
        if q:
            where.append("(s.name_en LIKE ? OR s.name_el LIKE ? OR s.id LIKE ?)")
            like = f"%{q}%"
            params.extend([like, like, like])
        wsql = (" WHERE " + " AND ".join(where)) if where else ""
        rows = conn.execute(
            "SELECT s.id, s.name_en, s.name_el, s.lat, s.lng,"
            " (SELECT GROUP_CONCAT(ls.line_id) FROM line_stations ls WHERE ls.station_id=s.id) AS lines"
            f" FROM stations s{wsql} ORDER BY s.id LIMIT 500",
            params,
        ).fetchall()
    line_opts = (
        "<option value=''>All lines</option>"
        + "".join(
            f"<option value='{lid}' {'selected' if lid == line else ''}>{lid}</option>"
            for lid in line_ids
        )
    )
    tr = "".join(
        f"<tr><form method=post action={ADMIN_PREFIX}/stations/save>"
        f"<td><code>{r['id']}</code><input type=hidden name=id value='{r['id']}'></td>"
        f"<td><input name=name_en value='{(r['name_en'] or '').replace(chr(39), '&#39;')}' size=20></td>"
        f"<td><input name=name_el value='{(r['name_el'] or '').replace(chr(39), '&#39;')}' size=20></td>"
        f"<td><input name=lat type=number step=0.0000001 value='{r['lat']}' style=width:10em></td>"
        f"<td><input name=lng type=number step=0.0000001 value='{r['lng']}' style=width:10em></td>"
        f"<td class=small>{r['lines'] or ''}</td>"
        f"<td><button>save</button></form></tr>"
        for r in rows
    )
    body = f"""
<h2>Stations</h2>
<p class=small>Edit station names (EN/EL) and coordinates. The /api/stations endpoint re-publishes on every save.</p>
<form method=get class=filter-bar>
 Line <select name=line>{line_opts}</select>
 Search <input name=q value='{q or ""}' placeholder='Syntagma, Σύνταγμα, M3_NIK'>
 <button>Filter</button>
</form>
<table><tr><th>ID</th><th>Name (EN)</th><th>Name (EL)</th><th>Lat</th><th>Lng</th><th>Lines</th><th></th></tr>{tr}</table>"""
    return page("Stations", body, active="stations")


@app.post("/stations/save")
def stations_save(
    id: str = Form(...),
    name_en: str = Form(...),
    name_el: str = Form(...),
    lat: float = Form(...),
    lng: float = Form(...),
    _: str = Depends(auth),
) -> RedirectResponse:
    with get_db() as conn:
        conn.execute(
            "UPDATE stations SET name_en=?, name_el=?, lat=?, lng=? WHERE id=?",
            (name_en.strip(), name_el.strip(), lat, lng, id),
        )
    generator.generate()
    return RedirectResponse(f"{ADMIN_PREFIX}/stations", status_code=303)


# Operator partners: placeholder for live-feed credentials

@app.get("/operators", response_class=HTMLResponse)
def operators_page(_: str = Depends(auth)) -> HTMLResponse:
    with get_db() as conn:
        rows = conn.execute(
            "SELECT id, operator_id, operator_name, contact_email, contact_url,"
            " feed_kind, feed_url, auth_method, refresh_seconds, status, notes,"
            " last_seen_at, enabled_at"
            " FROM operator_partners ORDER BY status DESC, operator_id"
        ).fetchall()
    status_opts = ("awaiting_partnership", "in_discussion", "enabled", "disabled", "broken")
    auth_opts = ("none", "bearer", "basic", "header_key")
    feed_opts = ("live_arrivals", "live_positions", "service_alerts", "gtfs_realtime")
    # Build the editable rows for each operator partner. We collapsed the
    # original messy single-f-string approach (it called r[""] which throws
    # KeyError -> 500) into a per-row builder that's easier to read and
    # actually works.
    parts = []
    for r in rows:
        feed_sel = "".join(f'<option value="{k}" {"selected" if k == r["feed_kind"] else ""}>{k}</option>' for k in feed_opts)
        auth_sel = "".join(f'<option value="{a}" {"selected" if a == r["auth_method"] else ""}>{a}</option>' for a in auth_opts)
        status_sel = "".join(f'<option value="{s}" {"selected" if s == r["status"] else ""}>{s}</option>' for s in status_opts)
        parts.append(
            f"<tr><form method=post action={ADMIN_PREFIX}/operators/save>"
            f"<input type=hidden name=id value={r['id']}>"
            f"<td><strong>{r['operator_name']}</strong><br><span class=small><code>{r['operator_id']}</code></span></td>"
            f"<td><select name=feed_kind>{feed_sel}</select></td>"
            f"<td><input name=feed_url value='{r['feed_url'] or ''}' placeholder='https://...' size=32></td>"
            f"<td><select name=auth_method>{auth_sel}</select></td>"
            f"<td><input name=auth_credential type=password placeholder='set to update' size=14></td>"
            f"<td><input name=refresh_seconds type=number value={r['refresh_seconds']} min=5 max=3600 style=width:5em></td>"
            f"<td><select name=status>{status_sel}</select></td>"
            f"<td><button>save</button></form></tr>"
            f"<tr><td colspan=8 class=small style='border:none;padding-bottom:14px'>{r['notes'] or ''}</td></tr>"
        )
    tr = "".join(parts)
    body = f"""
<h2>Operator partners</h2>
<p class=small>This page is the seam where future STASY / OASA / Hellenic Train real-time feeds plug in. Set <em>status=enabled</em> only after you've validated the feed shape; the <code>LiveArrivalsProvider</code> implementations in <code>core/domain</code> read these rows.</p>
<p class=small><strong>Privacy/security note.</strong> <code>auth_credential</code> is stored as-is in SQLite today. For production credentials, switch the column to encrypted-at-rest or move secrets to a dedicated vault (1Password Connect, Doppler, etc.).</p>
<table>
 <tr><th>Operator</th><th>Feed kind</th><th>URL</th><th>Auth</th><th>Credential</th><th>Refresh (s)</th><th>Status</th><th></th></tr>
 {tr}
</table>"""
    return page("Operators", body, active="operators")


@app.post("/operators/save")
def operators_save(
    id: int = Form(...), feed_kind: str = Form(...), feed_url: str = Form(""),
    auth_method: str = Form("none"), auth_credential: str = Form(""),
    refresh_seconds: int = Form(30), status: str = Form("awaiting_partnership"),
    _: str = Depends(auth),
) -> RedirectResponse:
    with get_db() as conn:
        if auth_credential.strip():
            conn.execute(
                "UPDATE operator_partners SET feed_kind=?, feed_url=?, auth_method=?,"
                " auth_credential=?, refresh_seconds=?, status=?,"
                " updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),"
                " enabled_at=CASE WHEN ?='enabled' AND enabled_at IS NULL"
                "   THEN strftime('%Y-%m-%dT%H:%M:%SZ','now') ELSE enabled_at END"
                " WHERE id=?",
                (feed_kind, feed_url.strip() or None, auth_method, auth_credential.strip(),
                 refresh_seconds, status, status, id),
            )
        else:
            conn.execute(
                "UPDATE operator_partners SET feed_kind=?, feed_url=?, auth_method=?,"
                " refresh_seconds=?, status=?,"
                " updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),"
                " enabled_at=CASE WHEN ?='enabled' AND enabled_at IS NULL"
                "   THEN strftime('%Y-%m-%dT%H:%M:%SZ','now') ELSE enabled_at END"
                " WHERE id=?",
                (feed_kind, feed_url.strip() or None, auth_method, refresh_seconds,
                 status, status, id),
            )
    generator.generate()
    return RedirectResponse(f"{ADMIN_PREFIX}/operators", status_code=303)


# Sync / scrape

@app.get("/sync", response_class=HTMLResponse)
def sync_page(_: str = Depends(auth)) -> HTMLResponse:
    body = f"""
<h2>Sync</h2>
<form method=post action={ADMIN_PREFIX}/sync/regen><button>Regenerate JSON snapshots</button></form>
<p class=small>Reads the DB and writes lines.json, schedules.json, manifest, holidays, overrides.</p>
<form method=post action={ADMIN_PREFIX}/scrape/run><button>Run OASA 24mmm scraper now</button></form>
<p class=small>Pulls next 30 days for M2, M3, T6, T7. Writes only diffs vs the rule output.</p>
"""
    return page("Sync", body, active="sync")


@app.post("/sync/regen")
def sync_regen(_: str = Depends(auth)) -> RedirectResponse:
    generator.generate()
    return RedirectResponse(f"{ADMIN_PREFIX}/", status_code=303)


@app.post("/scrape/run")
def scrape_run(_: str = Depends(auth)) -> RedirectResponse:
    from .scraper_24mmm import run_once
    run_once()
    generator.generate()
    return RedirectResponse(f"{ADMIN_PREFIX}/overrides", status_code=303)


# Docs + Publish

@app.get("/docs", response_class=HTMLResponse)
def docs_page(_: str = Depends(auth)) -> HTMLResponse:
    """One-page reference for every editable surface and every public API.

    Top action is the Publish button: it runs the JSON generator and the
    rebuilt snapshots are immediately served by nginx. Apps see the new
    data on their next /api/schedules/manifest poll without an app
    release or a git push."""
    with get_db() as conn:
        meta = {r["key"]: r["value"] for r in conn.execute("SELECT key,value FROM meta")}
        counts = {
            "lines":        conn.execute("SELECT COUNT(*) c FROM lines").fetchone()["c"],
            "stations":     conn.execute("SELECT COUNT(*) c FROM stations").fetchone()["c"],
            "rules":        conn.execute("SELECT COUNT(*) c FROM schedule_rules").fetchone()["c"],
            "bands":        conn.execute("SELECT COUNT(*) c FROM frequency_bands").fetchone()["c"],
            "holidays":     conn.execute("SELECT COUNT(*) c FROM holiday_rules").fetchone()["c"],
            "overrides":    conn.execute("SELECT COUNT(*) c FROM date_overrides").fetchone()["c"],
        }
        # train_timestamps may not exist on older DBs; guard.
        try:
            counts["timestamps"] = conn.execute(
                "SELECT COUNT(*) c FROM train_timestamps"
            ).fetchone()["c"]
        except Exception:
            counts["timestamps"] = 0

    endpoints = [
        ("/api/schedules/manifest",
         "Manifest of every line, with per-line ETag. Apps poll this on cold start and use If-None-Match for 304.",
         "Sync (Publish), Lines, Frequency bands, Holidays, Overrides"),
        ("/api/schedules",
         "Full per-line schedule bundles in one payload. Used by the web client for offline cold-start hydration.",
         "Sync (Publish), Lines, Frequency bands"),
        ("/api/schedules/{lineId}",
         "One line's schedule bundle: rules (open / close) + frequency bands by day_type. lineId is M1, M2, M3, M3_AIR, T6, T7, A1, A2, A3, A4.",
         "Lines, Frequency bands, Holidays"),
        ("/api/lines",
         "Public line catalogue: id, mode, color, terminals, English and Greek names.",
         "Lines"),
        ("/api/stations",
         "Every station the apps render, with WGS84 lat/lng and bilingual names.",
         "Stations"),
        ("/api/holidays",
         "Public holiday calendar with date patterns and the day_type each maps to.",
         "Holidays"),
        ("/api/overrides",
         "Per-date schedule overrides emitted by the 24mmm scraper.",
         "Overrides"),
        ("/api/fares",
         "Contactless payment info + a link to the OASA prices page. Syrmos deliberately stores no prices itself.",
         "Operators (Fares panel coming)"),
        ("/api/icons",
         "Per-station and per-vehicle SVG URLs, with override_url winning over default_url.",
         "Icons"),
        ("/api/line-display",
         "Line stroke color, weight, dash pattern and glow used when the apps render polylines on the map.",
         "Line drawing"),
        ("/api/operators",
         "Operator partner registry: STASY, Hellenic Train, OASA. Includes contact, feed registration status, but never auth credentials.",
         "Operators"),
        ("/api/train-timestamps",
         "Per-train per-station HH:MM timestamps parsed from Hellenic Train PDFs. Apps prefer these over band projection where they exist.",
         "(Re-ingested by scripts/ingest-train-timestamps.py)"),
    ]
    rows = "".join(
        f"<tr><td><a href='{e[0]}' target='_blank'><code class=small>{e[0]}</code></a></td>"
        f"<td>{e[1]}</td><td class=small>{e[2]}</td></tr>"
        for e in endpoints
    )

    body = f"""
<div class="card" style="background: linear-gradient(135deg, var(--accent-soft), transparent); border: 1px solid var(--accent);">
  <h2 style="margin-top:0;">Publish</h2>
  <p class="small" style="margin: 4px 0 16px;">
    Regenerates every /api/*.json snapshot from the current DB state and writes
    them atomically to <code class=small>~/syrmos-api/out</code>. Apps see the
    change on their next manifest poll, no app release needed.
  </p>
  <form method="post" action="{ADMIN_PREFIX}/publish">
    <button type="submit">Publish changes now</button>
    <span class="small" style="margin-left: 12px;">
      Current manifest version: <strong>v{meta.get('version', '?')}</strong>
      &middot; ETag <code class=small>{meta.get('etag', '')[:12]}…</code>
      &middot; updated {meta.get('updated_at', '?')}
    </span>
  </form>
</div>

<div class="card">
  <h3 style="margin-top:0;">What you can edit, where</h3>
  <table>
    <tr><th>Surface</th><th>Edit in admin</th><th>Lives at</th><th>Rows</th></tr>
    <tr><td>Lines + terminals + colors</td><td><a href="{ADMIN_PREFIX}/lines">Lines</a></td><td><code class=small>lines</code></td><td>{counts['lines']}</td></tr>
    <tr><td>Stations + coordinates</td><td><a href="{ADMIN_PREFIX}/stations">Stations</a></td><td><code class=small>stations</code></td><td>{counts['stations']}</td></tr>
    <tr><td>Operating hours per day type</td><td><a href="{ADMIN_PREFIX}/frequency-bands">Frequency bands</a></td><td><code class=small>schedule_rules</code></td><td>{counts['rules']}</td></tr>
    <tr><td>Frequency bands by daypart</td><td><a href="{ADMIN_PREFIX}/frequency-bands">Frequency bands</a></td><td><code class=small>frequency_bands</code></td><td>{counts['bands']}</td></tr>
    <tr><td>Public holiday rules</td><td><a href="{ADMIN_PREFIX}/holidays">Holidays</a></td><td><code class=small>holiday_rules</code></td><td>{counts['holidays']}</td></tr>
    <tr><td>Per-date overrides</td><td><a href="{ADMIN_PREFIX}/overrides">Overrides</a></td><td><code class=small>date_overrides</code></td><td>{counts['overrides']}</td></tr>
    <tr><td>Station + vehicle icons</td><td><a href="{ADMIN_PREFIX}/icons">Icons</a></td><td><code class=small>icons</code></td><td>(file-served)</td></tr>
    <tr><td>Polyline stroke for the map</td><td><a href="{ADMIN_PREFIX}/line-display">Line drawing</a></td><td><code class=small>line_display</code></td><td>(per line)</td></tr>
    <tr><td>Operator partner registry</td><td><a href="{ADMIN_PREFIX}/operators">Operators</a></td><td><code class=small>operator_partners</code></td><td>(per feed)</td></tr>
    <tr><td>Per-train PDF timestamps</td><td>scripts/ingest-train-timestamps.py</td><td><code class=small>train_timestamps</code></td><td>{counts['timestamps']}</td></tr>
  </table>
  <p class="small">Editing any of the above and pressing <strong>Publish changes now</strong> at the top of this page is the whole workflow. The phone apps never see uncommitted database state.</p>
</div>

<div class="card">
  <h3 style="margin-top:0;">Public API endpoints</h3>
  <table>
    <tr><th>Endpoint</th><th>What it returns</th><th>Edit surface</th></tr>
    {rows}
  </table>
</div>

<div class="card">
  <h3 style="margin-top:0;">Workflow</h3>
  <ol>
    <li>Open the surface you want to edit from the nav above (Lines, Frequency bands, Stations, etc.).</li>
    <li>Save your changes inline. Each save writes to the SQLite DB on the Pi.</li>
    <li>Come back here and press <strong>Publish changes now</strong>. The generator rewrites every <code class=small>~/syrmos-api/out/*.json</code> snapshot atomically and bumps the manifest version + ETag.</li>
    <li>Apps poll <code class=small>/api/schedules/manifest</code> on cold start, see the bumped version, and pull only the per-line bundles whose hash changed.</li>
    <li>Iconography, line strokes, fares, operator info propagate the same way.</li>
  </ol>
  <p class="small">For PDF-driven suburban schedules, re-run <code class=small>scripts/parse-hellenic-train-pdfs.py</code> and <code class=small>scripts/ingest-train-timestamps.py</code> locally, then come back here and Publish.</p>
</div>
"""
    return page("Docs", body, active="docs")


@app.post("/publish")
def publish(_: str = Depends(auth)) -> RedirectResponse:
    """Trigger a full snapshot regeneration. Same as Sync > Regenerate JSON,
    but invoked from the Docs page so a non-engineer admin has one obvious
    button to push after any edit. Also pings the GitHub seed-refresh
    workflow so the bundled seeds in the repo update within minutes
    instead of waiting for the nightly cron."""
    generator.generate()
    _trigger_seed_refresh()
    return RedirectResponse(f"{ADMIN_PREFIX}/docs", status_code=303)


def _trigger_seed_refresh() -> None:
    """Best-effort dispatch of the seed-refresh GitHub Action. Requires
    SYRMOS_GITHUB_TOKEN in the admin env (a fine-grained PAT with
    contents:write on peterdsp/Syrmos). Silent no-op if absent."""
    token = os.environ.get("SYRMOS_GITHUB_TOKEN")
    if not token:
        return
    import urllib.error
    import urllib.request
    url = "https://api.github.com/repos/peterdsp/Syrmos/actions/workflows/seed-refresh.yml/dispatches"
    body = b'{"ref":"main"}'
    req = urllib.request.Request(
        url,
        data=body,
        method="POST",
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "Content-Type": "application/json",
            "User-Agent": "syrmos-admin/1.0",
        },
    )
    try:
        urllib.request.urlopen(req, timeout=10).close()
    except (urllib.error.URLError, TimeoutError, OSError):
        # Non-blocking; the nightly cron is the safety net.
        pass


# Ariadne chat (unauthenticated). Admission control below caps total input
# size and bounds concurrent in-flight chats so the public endpoint cannot
# exhaust the Pi, the httpx pool, or upstream provider quotas. Per-IP rate
# limiting belongs in nginx (follow-up).
_ARIADNE_MAX_INPUT_CHARS = int(os.environ.get("ARIADNE_MAX_INPUT_CHARS", "6000"))
_ARIADNE_MAX_CONCURRENCY = int(os.environ.get("ARIADNE_MAX_CONCURRENCY", "6"))
_ARIADNE_RATE_PER_MIN = int(os.environ.get("ARIADNE_RATE_PER_MIN", "20"))
_ARIADNE_GLOBAL_PER_MIN = int(os.environ.get("ARIADNE_GLOBAL_PER_MIN", "120"))
_ARIADNE_RATE_MAX_KEYS = int(os.environ.get("ARIADNE_RATE_MAX_KEYS", "8192"))
_ariadne_sem = asyncio.Semaphore(_ARIADNE_MAX_CONCURRENCY)
_ariadne_hits: dict[str, list[float]] = {}
_ariadne_global: list[float] = []
_ariadne_rate_lock = asyncio.Lock()


def _ariadne_client_key(request: Request) -> str:
    """Ephemeral, hashed client identity for rate limiting. Prefers the true
    client IP behind Cloudflare; the raw IP is never stored or logged."""
    ip = (
        request.headers.get("cf-connecting-ip")
        or request.headers.get("x-real-ip")
        or (request.client.host if request.client else "unknown")
    )
    return hashlib.sha256(ip.encode("utf-8", "ignore")).hexdigest()[:16]


async def _ariadne_rate_ok(key: str) -> bool:
    """Admission for one request: a global 60s ceiling across all clients plus
    a per-client 60s window, with the per-client map hard-capped so a flood of
    distinct keys cannot grow memory without bound. In-memory, ephemeral,
    single worker; a stronger edge limit still belongs at Cloudflare."""
    now = time.monotonic()
    async with _ariadne_rate_lock:
        _ariadne_global[:] = [t for t in _ariadne_global if now - t < 60.0]
        if len(_ariadne_global) >= _ARIADNE_GLOBAL_PER_MIN:
            return False
        window = [t for t in _ariadne_hits.get(key, ()) if now - t < 60.0]
        if len(window) >= _ARIADNE_RATE_PER_MIN:
            _ariadne_hits[key] = window
            return False
        window.append(now)
        _ariadne_hits[key] = window
        _ariadne_global.append(now)
        if len(_ariadne_hits) > _ARIADNE_RATE_MAX_KEYS:
            for stale in [k for k, v in _ariadne_hits.items() if not v or now - max(v) >= 60.0]:
                _ariadne_hits.pop(stale, None)
            if len(_ariadne_hits) > _ARIADNE_RATE_MAX_KEYS:  # hard cap: evict oldest-active
                overflow = len(_ariadne_hits) - _ARIADNE_RATE_MAX_KEYS
                for k, _v in sorted(_ariadne_hits.items(), key=lambda kv: max(kv[1]))[:overflow]:
                    _ariadne_hits.pop(k, None)
        return True


@app.post("/api/ariadne/chat")
async def api_ariadne_chat(request: Request) -> JSONResponse:
    """Ariadne LLM chat endpoint. Accepts JSON with a `messages` array
    of {role: "user"|"assistant", text: "..."} objects. Returns
    {reply: "...", ok: true}."""
    try:
        body = await request.json()
    except Exception:
        raise HTTPException(status_code=400, detail="invalid JSON")
    messages = body.get("messages", [])
    if not messages or not isinstance(messages, list):
        raise HTTPException(status_code=400, detail="messages array required")
    if len(messages) > 20:
        messages = messages[-20:]
    total_chars = sum(len(str(m.get("text", ""))) for m in messages if isinstance(m, dict))
    if total_chars > _ARIADNE_MAX_INPUT_CHARS:
        raise HTTPException(status_code=413, detail="input too large")
    if not await _ariadne_rate_ok(_ariadne_client_key(request)):
        raise HTTPException(status_code=429, detail="rate limit, slow down")
    try:
        await asyncio.wait_for(_ariadne_sem.acquire(), timeout=1.0)
    except asyncio.TimeoutError:
        raise HTTPException(status_code=503, detail="assistant busy, retry shortly") from None
    try:
        result = await ariadne_mod.chat_async(messages)
    finally:
        _ariadne_sem.release()
    return JSONResponse({
        "reply": result["reply"],
        "ok": True,
        "provider": result["provider"],
        "model": result.get("model"),
    })


@app.on_event("shutdown")
async def _ariadne_shutdown() -> None:
    """Close the shared Ariadne httpx client on shutdown."""
    await ariadne_mod.providers.aclose_client()


# Health (unauthenticated)

@app.get("/healthz")
def healthz() -> JSONResponse:
    return JSONResponse({"ok": True})


# Public anonymous Ichnos reporting. These endpoints intentionally store no
# account, device identifier, precise location, or source IP.

@app.post("/api/community/reports")
async def api_community_report_submit(request: Request) -> JSONResponse:
    try:
        payload = await request.json()
        if not isinstance(payload, dict):
            raise ValueError("JSON object required")
        with get_db() as conn:
            result = community_mod.upsert_report(conn, payload)
        return JSONResponse(result)
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from None


@app.delete("/api/community/reports/{report_id}")
def api_community_report_delete(report_id: str) -> JSONResponse:
    try:
        with get_db() as conn:
            deleted = community_mod.delete_report(conn, report_id)
        return JSONResponse({"ok": True, "deleted": deleted})
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from None


@app.get("/api/community/summary")
def api_community_summary(scopeId: str | None = None) -> JSONResponse:
    if scopeId is not None and not community_mod.SCOPE_ID_RE.fullmatch(scopeId):
        raise HTTPException(status_code=400, detail="invalid scopeId")
    with get_db() as conn:
        return JSONResponse(community_mod.summary(conn, scope_id=scopeId))


@app.get("/api/community/history")
def api_community_history(
    period: str = "day",
    scopeId: str | None = None,
    limit: int = 31,
) -> JSONResponse:
    try:
        with get_db() as conn:
            return JSONResponse(
                community_mod.history(conn, period=period, scope_id=scopeId, limit=limit)
            )
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from None


# Public projector endpoint. Single source of truth for what the apps render
# in the Next Departures lists. It bypasses admin auth deliberately because it
# is read only.

_TZ_OFFSET_RE = re.compile(r"([+-]\d{2})(\d{2})$")


def _parse_projector_now(value: str | None) -> _dt.datetime | None:
    if not value:
        return None
    normalised = value.replace("Z", "+00:00")
    normalised = _TZ_OFFSET_RE.sub(r"\1:\2", normalised)
    try:
        return _dt.datetime.fromisoformat(normalised)
    except ValueError:
        raise HTTPException(status_code=400, detail="now must be ISO-8601") from None


# --- contact-developer feature ---
# Apps post a free-text message (optional image / video / file attachment)
# from the Settings screen. The admin reads them in the /admin/contact
# panel below and optionally gets an email nudge per arrival.
CONTACT_UPLOAD_DIR = pathlib.Path(
    os.environ.get("SYRMOS_CONTACT_UPLOAD_DIR", "/home/peterdsp/syrmos-api/uploads/contact")
)
MAX_CONTACT_ATTACHMENT_BYTES = 15 * 1024 * 1024  # 15 MB
MAX_CONTACT_MESSAGE_CHARS = 8000


def _send_contact_email_nudge(message_id: int, platform: str, subject: str, body_preview: str) -> None:
    """Best-effort email notification to the admin.

    Configured via env vars; silently a no-op if SMTP credentials aren't
    set on the Pi. Never raises — the contact submission itself must not
    fail just because the mail relay is down.
    """
    smtp_host = os.environ.get("SYRMOS_SMTP_HOST")
    smtp_user = os.environ.get("SYRMOS_SMTP_USER")
    smtp_pass = os.environ.get("SYRMOS_SMTP_PASS")
    admin_email = os.environ.get("SYRMOS_CONTACT_NOTIFY_EMAIL", "info@peterdsp.dev")
    if not (smtp_host and smtp_user and smtp_pass and admin_email):
        return
    try:
        import smtplib
        from email.message import EmailMessage
        msg = EmailMessage()
        msg["From"] = smtp_user
        msg["To"] = admin_email
        msg["Subject"] = f"[Syrmos #{message_id}] {platform}: {subject or 'New message'}"
        msg.set_content(
            f"New message from a Syrmos {platform} user.\n\n"
            f"{body_preview[:1500]}\n\n"
            f"Open the admin panel: https://api-syrmos.peterdsp.dev/admin/contact/{message_id}"
        )
        port = int(os.environ.get("SYRMOS_SMTP_PORT", "587"))
        with smtplib.SMTP(smtp_host, port, timeout=8) as s:
            s.starttls()
            s.login(smtp_user, smtp_pass)
            s.send_message(msg)
    except Exception:
        pass


@app.post("/api/contact")
async def api_contact_submit(
    platform: str = Form(...),
    message: str = Form(...),
    app_version: str | None = Form(None),
    locale: str | None = Form(None),
    user_agent: str | None = Form(None),
    category: str | None = Form("other"),
    subject: str | None = Form(None),
    contact_email: str | None = Form(None),
    attachment: UploadFile | None = File(None),
) -> JSONResponse:
    """User-submitted feedback / bug report / feature request.

    Accepts multipart/form-data so the apps can attach a screenshot or
    short video. Returns the assigned message id so the client can show
    "Sent (#42)" without needing to re-fetch.
    """
    platform = (platform or "").strip().lower()
    if platform not in {"ios", "android", "web", "other"}:
        raise HTTPException(status_code=400, detail="platform must be ios|android|web|other")
    message = (message or "").strip()
    if not message:
        raise HTTPException(status_code=400, detail="message required")
    if len(message) > MAX_CONTACT_MESSAGE_CHARS:
        raise HTTPException(status_code=400, detail="message too long")
    category = (category or "other").strip().lower()
    if category not in {"bug", "feature", "question", "other"}:
        category = "other"

    attachment_path_rel: str | None = None
    attachment_mime: str | None = None
    attachment_size: int | None = None
    if attachment is not None and attachment.filename:
        contents = await attachment.read()
        if len(contents) > MAX_CONTACT_ATTACHMENT_BYTES:
            raise HTTPException(status_code=413, detail="attachment too large")
        attachment_size = len(contents)
        attachment_mime = attachment.content_type or "application/octet-stream"
        suffix = pathlib.Path(attachment.filename).suffix[:8]
        CONTACT_UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
        import secrets
        fname = f"{_dt.datetime.now(_dt.UTC).strftime('%Y%m%d-%H%M%S')}-{secrets.token_hex(6)}{suffix}"
        (CONTACT_UPLOAD_DIR / fname).write_bytes(contents)
        attachment_path_rel = fname

    with dbmod.connect() as conn:
        dbmod.migrate(conn)
        cur = conn.cursor()
        cur.execute(
            "INSERT INTO contact_messages"
            "(platform, app_version, locale, user_agent, category, subject, message,"
            " contact_email, attachment_path, attachment_mime, attachment_size)"
            " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
            (
                platform, app_version, locale, user_agent, category, subject, message,
                contact_email, attachment_path_rel, attachment_mime, attachment_size,
            ),
        )
        message_id = cur.lastrowid

    _send_contact_email_nudge(
        message_id=int(message_id),
        platform=platform,
        subject=subject or category,
        body_preview=message,
    )
    return JSONResponse({"id": message_id, "ok": True})


def _contact_status_badge(status: str) -> str:
    color = {
        "new": "#0072CE",
        "read": "#7a8593",
        "replied": "#22a35a",
        "spam": "#b32a2a",
    }.get(status, "#7a8593")
    return (
        f"<span style='display:inline-block;padding:2px 8px;border-radius:8px;"
        f"background:{color};color:#fff;font-size:11px;'>{_html_escape(status)}</span>"
    )


@app.get("/contact", response_class=HTMLResponse)
def admin_contact_list(_: str = Depends(auth)) -> HTMLResponse:
    with get_db() as conn:
        rows = conn.execute(
            "SELECT id, created_at, platform, app_version, category, subject,"
            " substr(message, 1, 200) AS preview, contact_email, attachment_path,"
            " status FROM contact_messages ORDER BY created_at DESC LIMIT 500"
        ).fetchall()
    new_count = sum(1 for r in rows if r["status"] == "new")
    body_parts = [
        f"<h2 style='margin-top:0;'>Inbox <small>({new_count} new)</small></h2>",
        "<table class='data-table'><thead><tr>"
        "<th>#</th><th>When</th><th>Platform</th><th>Category</th>"
        "<th>Subject / preview</th><th>Attachment</th><th>Status</th>"
        "</tr></thead><tbody>",
    ]
    for r in rows:
        att = "📎" if r["attachment_path"] else ""
        subj = r["subject"] or ""
        preview = r["preview"] or ""
        body_parts.append(
            f"<tr><td><a href='{ADMIN_PREFIX}/contact/{r['id']}'>{r['id']}</a></td>"
            f"<td>{_html_escape(r['created_at'])}</td>"
            f"<td>{_html_escape(r['platform'])} {_html_escape(r['app_version'] or '')}</td>"
            f"<td>{_html_escape(r['category'] or '')}</td>"
            f"<td><strong>{_html_escape(subj)}</strong><br>"
            f"<span style='color:#7a8593;'>{_html_escape(preview)}</span></td>"
            f"<td>{att}</td>"
            f"<td>{_contact_status_badge(r['status'])}</td></tr>"
        )
    body_parts.append("</tbody></table>")
    if not rows:
        body_parts.append("<p>No messages yet.</p>")
    return page(title=f"Inbox ({new_count})", body="".join(body_parts))


@app.get("/contact/{message_id}", response_class=HTMLResponse)
def admin_contact_detail(message_id: int, _: str = Depends(auth)) -> HTMLResponse:
    with get_db() as conn:
        row = conn.execute(
            "SELECT * FROM contact_messages WHERE id=?", (message_id,),
        ).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="not found")
        if row["status"] == "new":
            conn.execute(
                "UPDATE contact_messages SET status='read' WHERE id=?", (message_id,),
            )
    att_html = ""
    if row["attachment_path"]:
        mime = row["attachment_mime"] or ""
        href = f"{ADMIN_PREFIX}/contact/{message_id}/attachment"
        if mime.startswith("image/"):
            att_html = f"<img src='{href}' style='max-width:520px;border-radius:12px;'>"
        elif mime.startswith("video/"):
            att_html = f"<video src='{href}' style='max-width:520px;border-radius:12px;' controls></video>"
        else:
            att_html = f"<a href='{href}'>Download attachment ({mime}, {row['attachment_size']} bytes)</a>"
    reply_btn = ""
    if row["contact_email"]:
        reply_btn = (
            f"<a class='button' href='mailto:{_html_escape(row['contact_email'])}"
            f"?subject=Re%3A%20Syrmos%20%23{message_id}'>Reply via email</a>"
        )
    status_buttons = "".join(
        f"<form method='post' action='{ADMIN_PREFIX}/contact/{message_id}/status' style='display:inline'>"
        f"<input type='hidden' name='status' value='{s}'>"
        f"<button class='button' type='submit'>{s.title()}</button></form>"
        for s in ("read", "replied", "spam", "new")
    )
    body = f"""
<h2 style='margin-top:0;'>Message #{message_id} {_contact_status_badge(row['status'])}</h2>
<p><a href='{ADMIN_PREFIX}/contact'>← Back to inbox</a></p>
<table class='data-table'><tbody>
<tr><th>When</th><td>{_html_escape(row['created_at'])}</td></tr>
<tr><th>Platform</th><td>{_html_escape(row['platform'])} {_html_escape(row['app_version'] or '')}</td></tr>
<tr><th>Locale</th><td>{_html_escape(row['locale'] or '')}</td></tr>
<tr><th>User-Agent</th><td>{_html_escape(row['user_agent'] or '')}</td></tr>
<tr><th>Category</th><td>{_html_escape(row['category'] or '')}</td></tr>
<tr><th>Subject</th><td>{_html_escape(row['subject'] or '')}</td></tr>
<tr><th>Reply-to</th><td>{_html_escape(row['contact_email'] or '')}</td></tr>
</tbody></table>
<h3>Message</h3>
<pre style='white-space:pre-wrap;background:#f7fafc;padding:14px;border-radius:12px;'>{_html_escape(row['message'])}</pre>
{att_html}
<div style='margin-top:18px;display:flex;gap:8px;flex-wrap:wrap;'>{reply_btn}{status_buttons}</div>
"""
    return page(title=f"Message #{message_id}", body=body)


@app.get("/contact/{message_id}/attachment")
def admin_contact_attachment(message_id: int, _: str = Depends(auth)):
    with get_db() as conn:
        row = conn.execute(
            "SELECT attachment_path, attachment_mime FROM contact_messages WHERE id=?",
            (message_id,),
        ).fetchone()
        if not row or not row["attachment_path"]:
            raise HTTPException(status_code=404, detail="no attachment")
    path = CONTACT_UPLOAD_DIR / row["attachment_path"]
    if not path.exists():
        raise HTTPException(status_code=404, detail="file missing")
    from fastapi.responses import FileResponse
    return FileResponse(str(path), media_type=row["attachment_mime"] or "application/octet-stream")


@app.post("/contact/{message_id}/status")
def admin_contact_set_status(
    message_id: int,
    status: str = Form(...),
    _: str = Depends(auth),
):
    if status not in {"new", "read", "replied", "spam"}:
        raise HTTPException(status_code=400, detail="bad status")
    with get_db() as conn:
        conn.execute(
            "UPDATE contact_messages SET status=?, replied_at=CASE WHEN ?='replied'"
            " THEN strftime('%Y-%m-%dT%H:%M:%SZ','now') ELSE replied_at END"
            " WHERE id=?",
            (status, status, message_id),
        )
    return RedirectResponse(url=f"{ADMIN_PREFIX}/contact/{message_id}", status_code=303)


@app.get("/api/live-positions")
def api_live_positions(
    lineIds: str,
    now: str | None = None,
) -> JSONResponse:
    """Active trains right now for the requested lines.

    The map renders dots from this list. Each train carries enough info
    (line, direction, when it left origin, total travel time on the leg)
    that the client can interpolate position locally against its bundled
    station_offsets + station coordinates, so the moving icon and the
    bottom-sheet "minutes away" agree by construction — both come out of
    the same projector logic.
    """
    line_id_list = [s for s in (lineIds or "").split(",") if s]
    if not line_id_list:
        raise HTTPException(status_code=400, detail="lineIds required")
    now_dt = _parse_projector_now(now)
    with dbmod.connect() as conn:
        dbmod.migrate(conn)
        trains = projector_mod.active_trains(conn, line_id_list, now=now_dt)
    return JSONResponse({
        "generatedAt": (now_dt or _dt.datetime.now(projector_mod.ATHENS)).isoformat(),
        "lineIds": line_id_list,
        "trains": trains,
    })


@app.get("/api/departures/next")
def api_departures_next(
    stationId: str,
    lineIds: str,
    limit: int = 8,
    now: str | None = None,
    direction: str | None = None,
) -> JSONResponse:
    """Project next departures for (stationId, lineIds) at the requested
    time (defaults to Athens-now). Returns a JSON list of departure dicts
    sorted by minutesAway.

    Query params:
      stationId  e.g. M2_SYN, T7_DIM
      lineIds    comma-separated, e.g. M2,M3
      limit      max entries (default 8, capped at 30 to discourage abuse)
      now        optional ISO-8601 timestamp; defaults to server time
      direction  optional outbound or inbound filter
    """
    line_id_list = [s for s in (lineIds or "").split(",") if s]
    if not stationId or not line_id_list:
        raise HTTPException(status_code=400, detail="stationId and lineIds required")
    limit = max(1, min(int(limit), 30))

    now_dt = _parse_projector_now(now)

    with dbmod.connect() as conn:
        dbmod.migrate(conn)
        departures = projector_mod.project_next_departures(
            conn,
            stationId,
            line_id_list,
            direction=direction,
            now=now_dt,
            limit=limit,
        )
        # Why the board may be empty: an active closed / pass_through closure
        # at this station (shape: fixtures/closures/effect.json expected).
        station_closure = projector_mod.station_closure_effect(conn, stationId, now_dt)
    return JSONResponse({
        "stationId": stationId,
        "lineIds": line_id_list,
        "direction": direction,
        "generatedAt": (now_dt or _dt.datetime.now(projector_mod.ATHENS)).isoformat(),
        "departures": departures,
        "stationClosure": station_closure,
    })


# =============================================================================
# Airport Bus Endpoints
# =============================================================================

from . import airport_bus_providers as abp_mod


def _airport_bus_snapshot() -> dict[str, Any]:
    """Read the atomically-written watcher snapshot; never poll upstream per request."""
    try:
        with AIRPORT_BUS_SNAPSHOT.open(encoding="utf-8") as handle:
            payload = json.load(handle)
    except FileNotFoundError:
        raise HTTPException(status_code=503, detail="Airport bus snapshot is unavailable")
    except (OSError, json.JSONDecodeError) as exc:
        logger.error("Invalid airport bus snapshot: %s", exc)
        raise HTTPException(status_code=503, detail="Airport bus snapshot is invalid")
    if not isinstance(payload, dict) or not isinstance(payload.get("operators"), dict):
        raise HTTPException(status_code=503, detail="Airport bus snapshot has an invalid schema")
    return payload


@app.get("/api/airport-buses")
def api_airport_buses():
    """Get normalized airport bus data for all operators.

    Returns a unified snapshot containing:
    - routes: All airport bus routes from all operators
    - vehicles: Live vehicle positions for all routes
    - arrivals: Live ETAs at airport stops
    - alerts: Service alerts
    - metadata: Source attribution, freshness, timestamps

    This endpoint provides the new normalized contract. The legacy
    /api/oasa-airport-buses endpoint remains for backward compatibility.

    Response format:
    {
        "updated_at": "2026-10-12T12:34:56.789+00:00",
        "operators": {
            "oasa": {routes, vehicles, arrivals, alerts, ...},
            "oseth": {routes, vehicles, arrivals, alerts, ...}
        },
        "sources": [...],
        "generated_at": "2026-10-12T12:34:56.890+00:00",
        "generation_duration": 0.101
    }
    """
    return JSONResponse(_airport_bus_snapshot())


@app.get("/api/airport-buses/{operator}")
def api_airport_buses_operator(operator: str):
    """Get airport bus data for a specific operator.

    Args:
        operator: Operator identifier ("oasa" or "oseth")

    Returns:
        Operator-specific snapshot with routes, vehicles, arrivals, alerts
    """
    operator_lower = operator.lower()
    snapshot = _airport_bus_snapshot()
    operator_data = snapshot.get("operators", {}).get(operator_lower)
    if operator_data is None:
        raise HTTPException(status_code=404, detail=f"Unknown operator: {operator}")
    return JSONResponse({
        **operator_data,
        "updated_at": snapshot.get("updated_at"),
        "generated_at": snapshot.get("generated_at"),
        "generation_duration": snapshot.get("generation_duration"),
    })


@app.get("/api/airport-buses/{operator}/{route_id}")
def api_airport_buses_operator_route(operator: str, route_id: str):
    """Get airport bus data for a specific operator and route.

    Args:
        operator: Operator identifier ("oasa" or "oseth")
        route_id: Route identifier (e.g., "X95", "X3")

    Returns:
        Route-specific data including metadata, vehicles, arrivals
    """
    snapshot = _airport_bus_snapshot()
    operator_data = snapshot.get("operators", {}).get(operator.lower())
    if operator_data is None:
        raise HTTPException(status_code=404, detail=f"Unknown operator: {operator}")
    route = next((r for r in operator_data.get("routes", []) if r.get("route_id") == route_id), None)
    if route is None:
        raise HTTPException(status_code=404, detail=f"Route {route_id} not found")
    return JSONResponse({"operator": operator.lower(), "route_id": route_id,
                         "source": operator_data.get("source"),
                         "attribution": operator_data.get("attribution"),
                         "updated_at": snapshot.get("updated_at"), "route": route,
                         "vehicles": [v for v in operator_data.get("vehicles", []) if v.get("route_id") == route_id],
                         "arrivals": [a for a in operator_data.get("arrivals", []) if a.get("route_id") == route_id or a.get("line_id") == route_id],
                         "alerts": [a for a in operator_data.get("alerts", []) if a.get("route_id") in (None, route_id)]})

@app.get("/api/airport-buses/{operator}/{route_id}/vehicles")
def api_airport_buses_operator_route_vehicles(operator: str, route_id: str):
    """Get live vehicle positions for a specific route.

    Args:
        operator: Operator identifier ("oasa" or "oseth")
        route_id: Route identifier (e.g., "X95", "X3")

    Returns:
        List of vehicle positions with metadata
    """
    snapshot = _airport_bus_snapshot()
    operator_data = snapshot.get("operators", {}).get(operator.lower())
    if operator_data is None:
        raise HTTPException(status_code=404, detail=f"Unknown operator: {operator}")
    return JSONResponse({"operator": operator.lower(), "route_id": route_id,
                         "updated_at": snapshot.get("updated_at"),
                         "vehicles": [v for v in operator_data.get("vehicles", []) if v.get("route_id") == route_id]})

@app.get("/api/airport-buses/{operator}/{route_id}/alerts")
def api_airport_buses_operator_route_alerts(operator: str, route_id: str):
    """Get service alerts for a specific route.

    Args:
        operator: Operator identifier ("oasa" or "oseth")
        route_id: Route identifier (e.g., "X95", "X3")

    Returns:
        List of service alerts
    """
    snapshot = _airport_bus_snapshot()
    operator_data = snapshot.get("operators", {}).get(operator.lower())
    if operator_data is None:
        raise HTTPException(status_code=404, detail=f"Unknown operator: {operator}")
    return JSONResponse({"operator": operator.lower(), "route_id": route_id,
                         "updated_at": snapshot.get("updated_at"),
                         "alerts": [a for a in operator_data.get("alerts", []) if a.get("route_id") in (None, route_id)]})

@app.get("/api/airport-buses/sources")
def api_airport_buses_sources():
    """Get metadata about all airport bus data sources.

    Returns:
        List of source information for each operator including:
        - operator: Identifier
        - name: Human-readable name
        - source_url: API documentation URL
        - attribution: Attribution text
        - is_live: Whether live data is available
        - last_tested: When the source was last validated
        - notes: Additional notes
    """
    snapshot = _airport_bus_snapshot()
    operators = snapshot.get("operators", {})
    oasa_data = operators.get("oasa", {})
    oseth_data = operators.get("oseth", {})

    return JSONResponse({
        "sources": [
            {
                "operator": "oasa",
                "name": "OASA Telematics",
                "source_url": oasa_data.get("source"),
                "attribution": oasa_data.get("attribution"),
                "is_live": bool(oasa_data.get("is_live")),
                "last_tested": snapshot.get("updated_at"),
                "notes": "Snapshot-backed OASA airport-bus data.",
            },
            {
                "operator": "oseth",
                "name": "OSETH/OASTH",
                "source_url": oseth_data.get("source"),
                "attribution": oseth_data.get("attribution"),
                "is_live": bool(oseth_data.get("is_live")),
                "last_tested": snapshot.get("updated_at"),
                "notes": "Snapshot-backed OSETH data; live availability follows the watcher status.",
            },
        ],
        "generated_at": snapshot.get("generated_at"),
    })


@app.get("/api/oasa-airport-buses")
def api_oasa_airport_buses_legacy():
    """Get legacy OASA airport bus data (backward compatible).

    This endpoint provides the same format as the original /api/oasa-airport-buses
    static JSON file, generated by oasa-airport-bus-watcher.py.

    Deprecated: Use /api/airport-buses for the new normalized format.
    This endpoint is maintained for backward compatibility.

    Response format:
    {
        "updatedAt": "2026-10-12T12:34:56.789+00:00",
        "vehicles": [
            {
                "vehicleId": "12345",
                "lat": 37.9364,
                "lng": 23.9045,
                "heading": 125.5,
                "routeCode": 2051,
                "lineId": "X95",
                "timestamp": "2026-10-12 12:34:56"
            },
            ...
        ],
        "airportArrivals": [
            {
                "lineId": "X95",
                "routeCode": 2051,
                "vehicleId": "12345",
                "minutesAway": 5
            },
            ...
        ],
        "routes": {
            "X93": {"toAirport": [5675], "fromAirport": [5676]},
            "X95": {"toAirport": [2051], "fromAirport": [2052]},
            "X96": {"toAirport": [5532, 3196], "fromAirport": [5533, 3008]},
            "X97": {"toAirport": [5373, 5375], "fromAirport": [5374, 5376]}
        }
    }
    """
    snapshot = _airport_bus_snapshot()
    data = snapshot.get("operators", {}).get("oasa", {})
    return JSONResponse({
        "updatedAt": snapshot.get("updated_at"),
        "vehicles": [
            {
                "vehicleId": v.get("vehicle_id", ""),
                "lat": v.get("latitude"),
                "lng": v.get("longitude"),
                "heading": v.get("heading"),
                "routeCode": v.get("route_code", 0),
                "lineId": v.get("route_id", v.get("line_id", "")),
                "timestamp": v.get("observed_at"),
            }
            for v in data.get("vehicles", [])
        ],
        "airportArrivals": [
            {
                "lineId": a.get("route_id", a.get("line_id", "")),
                "routeCode": a.get("route_code", 0),
                "vehicleId": a.get("vehicle_id", ""),
                "minutesAway": a.get("estimated_minutes", 0),
            }
            for a in data.get("arrivals", [])
        ],
        "routes": {
            r.get("route_id", ""): {
                "toAirport": r.get("to_airport_route_codes", []),
                "fromAirport": r.get("from_airport_route_codes", []),
            }
            for r in data.get("routes", [])
        },
    })


# =============================================================================
# 3.0.2: station closures, broadcasts and push registration
# =============================================================================
#
# Public reads (GET /api/closures, GET /api/broadcasts) and the anonymous push
# registration endpoints, followed by the private admin pages that write them.
# Contract: docs/3.0.2-CONTRACTS.md. Window rules: syrmos_admin/closures.py.

import sqlite3

from starlette.concurrency import run_in_threadpool

from . import broadcasts as broadcasts_mod
from . import closures as closures_mod
from . import dispatch as dispatch_mod
from . import push as push_mod
from . import schedule_invariants
from . import translation as translation_mod

ATHENS_TZ = projector_mod.ATHENS
_PUBLIC_MAX_AGE = 60


def _etag_matches(request: Request, etag: str) -> bool:
    inm = request.headers.get("if-none-match")
    if not inm:
        return False
    if inm.strip() == "*":
        return True
    tags = [t.strip().removeprefix("W/") for t in inm.split(",")]
    return etag in tags


def _cached_json(request: Request, payload: dict, etag: str) -> Response:
    headers = {"ETag": etag, "Cache-Control": f"public, max-age={_PUBLIC_MAX_AGE}"}
    if _etag_matches(request, etag):
        return Response(status_code=304, headers=headers)
    return JSONResponse(payload, headers=headers)


@app.get("/api/closures")
def api_closures(request: Request) -> Response:
    """Non-draft, non-cancelled station closures (open-ended or ended < 24 h ago)."""
    with get_db() as conn:
        payload, etag = closures_mod.public_payload(conn)
    return _cached_json(request, payload, etag)


@app.get("/api/broadcasts")
def api_broadcasts(request: Request, since: str | None = None) -> Response:
    """Sent, unexpired broadcasts with sentAt >= since (epoch seconds), newest first."""
    try:
        since_epoch = int(since) if since not in (None, "") else 0
    except ValueError:
        raise HTTPException(status_code=400, detail="since must be epoch seconds") from None
    if since_epoch < 0:
        raise HTTPException(status_code=400, detail="since must be epoch seconds")
    with get_db() as conn:
        payload, etag = broadcasts_mod.public_payload(conn, since_epoch)
    return _cached_json(request, payload, etag)


# --- anonymous push registration ---------------------------------------------

_PUSH_RATE_PER_HOUR = 30
_PUSH_RATE_MAX_KEYS = 20000
_push_hits: dict[str, list[float]] = {}
_push_rate_lock = asyncio.Lock()
_PUSH_LANGS = {"en", "el", "sq", "it"}
_IOS_TOKEN_RE = re.compile(r"^[0-9a-fA-F]{32,200}$")
_FCM_TOKEN_RE = re.compile(r"^[A-Za-z0-9_:\-.]{20,4096}$")
_APP_VERSION_RE = re.compile(r"^[0-9A-Za-z.\-+ ()]{1,32}$")
_MAX_TOPICS = 100


async def _push_rate_ok(key: str) -> bool:
    """30 requests per rolling hour per client (hashed IP), in memory."""
    now = time.monotonic()
    async with _push_rate_lock:
        window = [t for t in _push_hits.get(key, ()) if now - t < 3600.0]
        if len(window) >= _PUSH_RATE_PER_HOUR:
            _push_hits[key] = window
            return False
        window.append(now)
        _push_hits[key] = window
        if len(_push_hits) > _PUSH_RATE_MAX_KEYS:
            for stale in [k for k, v in _push_hits.items() if not v or now - max(v) >= 3600.0]:
                _push_hits.pop(stale, None)
            if len(_push_hits) > _PUSH_RATE_MAX_KEYS:
                overflow = len(_push_hits) - _PUSH_RATE_MAX_KEYS
                for k, _v in sorted(_push_hits.items(), key=lambda kv: max(kv[1]))[:overflow]:
                    _push_hits.pop(k, None)
        return True


def _token_hash(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def _normalise_token(platform: str, token: Any) -> str:
    if not isinstance(token, str):
        raise ValueError("token must be a string")
    token = token.strip()
    if platform == "ios":
        if not _IOS_TOKEN_RE.match(token):
            raise ValueError("ios token must be hex")
        return token.lower()
    if not _FCM_TOKEN_RE.match(token):
        raise ValueError("invalid android token")
    return token


def validate_push_registration(body: Any) -> dict:
    """Validated, normalised registration or ValueError (-> 400)."""
    if not isinstance(body, dict):
        raise ValueError("JSON object required")
    platform = body.get("platform")
    if platform not in ("ios", "android"):
        raise ValueError("platform must be ios or android")
    token = _normalise_token(platform, body.get("token"))
    environment = body.get("environment", "production")
    if environment not in ("sandbox", "production"):
        raise ValueError("environment must be sandbox or production")
    if platform == "android":
        environment = "production"
    lang = body.get("lang", "en")
    if not isinstance(lang, str) or not lang.strip():
        raise ValueError("lang must be a language code")
    lang = lang.strip().lower().replace("_", "-").split("-", 1)[0]
    if lang not in _PUSH_LANGS:
        lang = "en"
    topics = body.get("topics", ["all"])
    if not isinstance(topics, list) or len(topics) > _MAX_TOPICS:
        raise ValueError("topics must be a list")
    clean_topics: list[str] = []
    for t in topics:
        if not isinstance(t, str) or not push_mod.TOPIC_RE.match(t):
            raise ValueError(f"invalid topic: {t!r}")
        if t not in clean_topics:
            clean_topics.append(t)
    app_version = body.get("appVersion")
    if app_version is not None and (not isinstance(app_version, str) or not _APP_VERSION_RE.match(app_version)):
        raise ValueError("invalid appVersion")
    return {
        "token": token,
        "platform": platform,
        "environment": environment,
        "lang": lang,
        "topics": clean_topics,
        "appVersion": app_version,
    }


async def _push_body(request: Request) -> Any:
    if not await _push_rate_ok(_ariadne_client_key(request)):
        raise HTTPException(status_code=429, detail="rate limit, try again later")
    try:
        return await request.json()
    except Exception:
        raise HTTPException(status_code=400, detail="invalid JSON") from None


@app.post("/api/push/register")
async def api_push_register(request: Request) -> Response:
    body = await _push_body(request)
    try:
        reg = validate_push_registration(body)
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from None
    with get_db() as conn:
        conn.execute(
            "INSERT INTO push_devices(token_hash, token, platform, environment, lang, topics, app_version)"
            " VALUES(?,?,?,?,?,?,?)"
            " ON CONFLICT(token_hash) DO UPDATE SET"
            " token=excluded.token, platform=excluded.platform, environment=excluded.environment,"
            " lang=excluded.lang, topics=excluded.topics, app_version=excluded.app_version,"
            " last_seen=strftime('%Y-%m-%dT%H:%M:%SZ','now')",
            (
                _token_hash(reg["token"]), reg["token"], reg["platform"], reg["environment"],
                reg["lang"], json.dumps(reg["topics"]), reg["appVersion"],
            ),
        )
    return Response(status_code=204)


@app.post("/api/push/unregister")
async def api_push_unregister(request: Request) -> Response:
    body = await _push_body(request)
    token = body.get("token") if isinstance(body, dict) else None
    if not isinstance(token, str) or not token.strip() or len(token) > 4096:
        raise HTTPException(status_code=400, detail="token required")
    token = token.strip()
    with get_db() as conn:
        conn.execute(
            "DELETE FROM push_devices WHERE token_hash IN (?, ?)",
            (_token_hash(token), _token_hash(token.lower())),
        )
    return Response(status_code=204)


# --- admin helpers -----------------------------------------------------------

def _h(value: Any) -> str:
    return _html_escape("" if value is None else str(value), quote=True)


def _athens_input(epoch: int | None) -> str:
    if epoch is None:
        return ""
    return _dt.datetime.fromtimestamp(int(epoch), ATHENS_TZ).strftime("%Y-%m-%dT%H:%M")


def _athens_label(epoch: int | None) -> str:
    if epoch is None:
        return "until further notice"
    return _dt.datetime.fromtimestamp(int(epoch), ATHENS_TZ).strftime("%a %d %b %Y %H:%M")


def parse_athens_datetime(value: str | None) -> int | None:
    """An Athens-local datetime-local input ("2026-10-28T08:00") as UTC epoch."""
    value = (value or "").strip()
    if not value:
        return None
    try:
        local = _dt.datetime.fromisoformat(value)
    except ValueError:
        raise ValueError(f"invalid date/time: {value!r}") from None
    if local.tzinfo is None:
        local = local.replace(tzinfo=ATHENS_TZ)
    return int(local.timestamp())


def _translate_fields(source: str, targets: list[str]) -> dict[str, str]:
    """Greek source -> {lang: text} via the shared pipeline; failures omitted."""
    out: dict[str, str] = {}
    if not source.strip():
        return out
    for lang in targets:
        text = translation_mod.translate_from_greek(source, lang)
        if text and text != source:
            out[lang] = text
    return out


def _station_choices(conn) -> list[Any]:
    return conn.execute(
        "SELECT s.id, s.name_en, s.name_el,"
        " (SELECT GROUP_CONCAT(DISTINCT ls.line_id) FROM line_stations ls WHERE ls.station_id=s.id) AS lines"
        " FROM stations s ORDER BY s.id"
    ).fetchall()


def _line_choices(conn) -> list[str]:
    return [r["id"] for r in conn.execute("SELECT id FROM lines ORDER BY sort_order")]


_FILTER_JS = """<script>
function syrmosFilter(input, selectId) {
  var q = input.value.toLowerCase();
  var opts = document.getElementById(selectId).options;
  for (var i = 0; i < opts.length; i++) {
    opts[i].hidden = q && opts[i].text.toLowerCase().indexOf(q) === -1 && !opts[i].selected;
  }
}
</script>"""


# --- admin: closures ---------------------------------------------------------

CLOSURE_ID_RE = re.compile(r"^[a-z0-9][a-z0-9_-]{2,63}$")
# Statuses the form can submit. cancelled / ended are only offered when editing
# a closure that already has them, so an edit never resurrects it by accident.
CLOSURE_FORM_STATUSES = ("draft", "scheduled", "cancelled", "ended")

CLOSURE_TEMPLATES: dict[str, dict[str, Any]] = {
    "police_pass_through": {
        "label": "Station closed by police order, trains pass without stopping until further notice",
        "mode": "pass_through",
        "until_further_notice": True,
        "reason_el": "Με εντολή της Αστυνομίας ο σταθμός είναι κλειστός. "
                     "Οι συρμοί διέρχονται χωρίς στάση έως νεωτέρας.",
        "reason_en": "Station closed by police order. Trains pass without stopping until further notice.",
        "reason_sq": "Stacioni është mbyllur me urdhër të policisë. "
                     "Trenat kalojnë pa ndalur deri në njoftim të mëtejshëm.",
        "reason_it": "Stazione chiusa per ordine della polizia. "
                     "I treni transitano senza fermarsi fino a nuovo avviso.",
    },
}


def _empty_closure_form() -> dict[str, Any]:
    return {
        "id": "", "station_ids": [], "line_ids": [], "mode": "closed",
        "starts": "", "ends": "", "until_further_notice": False,
        "reason_el": "", "reason_en": "", "reason_sq": "", "reason_it": "",
        "bus_label_el": "", "bus_label_en": "", "bus_from_el": "", "bus_from_en": "",
        "bus_to_el": "", "bus_to_en": "", "bus_headway": "", "bus_stops": "",
        "status": "draft",
    }


def _closure_form_from_row(row: Any) -> dict[str, Any]:
    f = _empty_closure_form()
    c = closures_mod.row_to_closure(row)
    f.update({
        "id": row["id"], "station_ids": c["stationIds"], "line_ids": c["lineIds"],
        "mode": row["mode"], "starts": _athens_input(row["starts_at"]),
        "ends": _athens_input(row["ends_at"]), "until_further_notice": row["ends_at"] is None,
        "reason_el": row["reason_el"], "reason_en": row["reason_en"],
        "reason_sq": row["reason_sq"], "reason_it": row["reason_it"],
        # cancelled / ended are kept on edit unless the owner picks another
        # status; scheduled and active both show as scheduled (the window
        # decides which one is stored).
        "status": row["status"] if row["status"] in ("draft", "cancelled", "ended") else "scheduled",
    })
    bus = closures_mod._replacement_bus(row["replacement_bus"])
    if bus:
        f.update({
            "bus_label_el": bus["label"].get("el", ""), "bus_label_en": bus["label"].get("en", ""),
            "bus_from_el": bus["fromStop"].get("el", ""), "bus_from_en": bus["fromStop"].get("en", ""),
            "bus_to_el": bus["toStop"].get("el", ""), "bus_to_en": bus["toStop"].get("en", ""),
            "bus_headway": "" if bus["headwayMinutes"] is None else str(bus["headwayMinutes"]),
            "bus_stops": "\n".join(
                f"{(s.get('name') or {}).get('el', '')} | {s.get('lat', '')} | {s.get('lon', '')}"
                for s in bus["stops"]
            ),
        })
    return f


def _closure_form_from_post(form: Any) -> dict[str, Any]:
    f = _empty_closure_form()
    for key in f:
        if key in ("station_ids", "line_ids"):
            f[key] = [v for v in form.getlist(key) if isinstance(v, str) and v]
        elif key == "until_further_notice":
            f[key] = form.get(key) in ("1", "on", "true")
        else:
            value = form.get(key)
            f[key] = value.strip() if isinstance(value, str) else ""
    return f


def _replacement_bus_from_form(f: dict[str, Any]) -> dict | None:
    def loc(el: str, en: str) -> dict:
        return {k: v for k, v in (("el", el), ("en", en)) if v}

    label = loc(f["bus_label_el"], f["bus_label_en"])
    from_stop = loc(f["bus_from_el"], f["bus_from_en"])
    to_stop = loc(f["bus_to_el"], f["bus_to_en"])
    stops = []
    for line in (f["bus_stops"] or "").splitlines():
        parts = [p.strip() for p in line.split("|")]
        if not parts or not parts[0]:
            continue
        stop: dict[str, Any] = {"name": {"el": parts[0]}}
        if len(parts) >= 3:
            try:
                stop["lat"] = float(parts[1])
                stop["lon"] = float(parts[2])
            except ValueError:
                raise ValueError(f"bad stop coordinates: {line!r}") from None
        stops.append(stop)
    headway = None
    if f["bus_headway"]:
        try:
            headway = int(f["bus_headway"])
        except ValueError:
            raise ValueError("headway must be whole minutes") from None
        if not 1 <= headway <= 240:
            raise ValueError("headway must be 1 to 240 minutes")
    if not (label or from_stop or to_stop or stops or headway):
        return None
    return {"label": label, "fromStop": from_stop, "toStop": to_stop,
            "headwayMinutes": headway, "stops": stops}


def _validate_closure_form(conn, f: dict[str, Any]) -> dict[str, Any]:
    """Storage values for a closure form, or ValueError with a message for the owner."""
    if f["id"] and not CLOSURE_ID_RE.match(f["id"]):
        raise ValueError("id: lowercase letters, digits, - and _, 3 to 64 characters")
    if not f["station_ids"]:
        raise ValueError("pick at least one station")
    known_stations = {r["id"] for r in conn.execute("SELECT id FROM stations")}
    unknown = [s for s in f["station_ids"] if s not in known_stations]
    if unknown:
        raise ValueError(f"unknown stations: {', '.join(unknown)}")
    known_lines = set(_line_choices(conn))
    unknown = [l for l in f["line_ids"] if l not in known_lines]
    if unknown:
        raise ValueError(f"unknown lines: {', '.join(unknown)}")
    if f["mode"] not in closures_mod.MODES:
        raise ValueError("invalid mode")
    starts = parse_athens_datetime(f["starts"])
    if starts is None:
        raise ValueError("start date/time is required")
    ends = None
    if not f["until_further_notice"]:
        ends = parse_athens_datetime(f["ends"])
        if ends is None:
            raise ValueError("set an end date/time or tick 'until further notice'")
        if ends <= starts:
            raise ValueError("the end must be after the start")
    if not (f["reason_el"] or f["reason_en"]):
        raise ValueError("a reason in Greek (or English) is required")
    if f["status"] not in CLOSURE_FORM_STATUSES:
        raise ValueError("status must be draft, scheduled, cancelled or ended")
    return {
        "station_ids": f["station_ids"], "line_ids": f["line_ids"], "mode": f["mode"],
        "starts_at": starts, "ends_at": ends,
        "replacement_bus": _replacement_bus_from_form(f),
    }


def _check_line_invariant(conn, closure: dict) -> None:
    others = closures_mod.load_closures(conn)
    emptied = schedule_invariants.closure_emptied_lines(conn, closure, others)
    if emptied:
        raise ValueError(
            "this would close every station of " + ", ".join(emptied)
            + "; suspend the line instead of closing all its stations"
        )


def _closure_form_html(conn, f: dict[str, Any], *, error: str = "", notice: str = "", editing: bool = False) -> str:
    stations = _station_choices(conn)
    lines = _line_choices(conn)
    selected_s = set(f["station_ids"])
    selected_l = set(f["line_ids"])
    st_opts = "".join(
        f"<option value='{_h(r['id'])}' {'selected' if r['id'] in selected_s else ''}>"
        f"{_h(r['id'])} &middot; {_h(r['name_el'])} / {_h(r['name_en'])} ({_h(r['lines'] or '')})</option>"
        for r in stations
    )
    ln_opts = "".join(
        f"<option value='{_h(l)}' {'selected' if l in selected_l else ''}>{_h(l)}</option>" for l in lines
    )
    mode_labels = {
        "closed": "closed (no boarding, no alighting)",
        "pass_through": "pass through (trains run without stopping)",
        "exit_only": "exit only (alighting allowed)",
        "entry_only": "entry only (boarding allowed)",
    }
    mode_opts = "".join(
        f"<option value='{m}' {'selected' if f['mode'] == m else ''}>{_h(mode_labels[m])}</option>"
        for m in closures_mod.MODES
    )
    offered = ["draft", "scheduled"]
    if f["status"] in ("cancelled", "ended"):
        offered.append(f["status"])
    status_opts = "".join(
        f"<option value='{s}' {'selected' if f['status'] == s else ''}>{s}</option>"
        for s in offered
    )
    id_field = (
        f"<code>{_h(f['id'])}</code><input type=hidden name=id value='{_h(f['id'])}'>"
        f"<input type=hidden name=edit value=1>"
        if editing else
        f"<input name=id value='{_h(f['id'])}' placeholder='optional, e.g. syn-parade-2026' size=30>"
    )
    msg = ""
    if error:
        msg += f"<p class='pill pill-danger'>{_h(error)}</p>"
    if notice:
        msg += f"<p class='pill pill-success'>{_h(notice)}</p>"
    return f"""{_FILTER_JS}
{msg}
<form method=post action={ADMIN_PREFIX}/closures/save class=card>
 <table>
  <tr><th>Id</th><td>{id_field}</td></tr>
  <tr><th>Stations</th><td>
    <input placeholder='filter: Σύνταγμα, Syntagma, M3_' oninput="syrmosFilter(this, 'closure-stations')" size=40><br>
    <select id=closure-stations name=station_ids multiple size=12 style='min-width:32em'>{st_opts}</select>
    <div class=small>Cmd/Ctrl-click for several. Interchanges are one id per line (M2_SYN and M3_SYN).</div></td></tr>
  <tr><th>Lines</th><td><select name=line_ids multiple size=6>{ln_opts}</select>
    <div class=small>None selected = every line at those stations.</div></td></tr>
  <tr><th>Mode</th><td><select name=mode>{mode_opts}</select></td></tr>
  <tr><th>From (Athens time)</th><td><input type=datetime-local name=starts value='{_h(f['starts'])}'></td></tr>
  <tr><th>Until (Athens time)</th><td><input type=datetime-local name=ends value='{_h(f['ends'])}'>
    <label><input type=checkbox name=until_further_notice value=1 {'checked' if f['until_further_notice'] else ''}> until further notice</label></td></tr>
  <tr><th>Reason (Greek)</th><td><textarea name=reason_el rows=3 cols=70>{_h(f['reason_el'])}</textarea><br>
    <button name=action value=translate class=ghost>Translate to en / sq / it</button></td></tr>
  <tr><th>Reason (English)</th><td><textarea name=reason_en rows=2 cols=70>{_h(f['reason_en'])}</textarea></td></tr>
  <tr><th>Reason (Albanian)</th><td><textarea name=reason_sq rows=2 cols=70>{_h(f['reason_sq'])}</textarea></td></tr>
  <tr><th>Reason (Italian)</th><td><textarea name=reason_it rows=2 cols=70>{_h(f['reason_it'])}</textarea></td></tr>
  <tr><th>Replacement bus</th><td>
    Label <input name=bus_label_el value='{_h(f['bus_label_el'])}' placeholder='Έκτακτο λεωφορείο'>
    <input name=bus_label_en value='{_h(f['bus_label_en'])}' placeholder='Replacement bus'><br>
    From <input name=bus_from_el value='{_h(f['bus_from_el'])}' placeholder='Σύνταγμα (Αμαλίας)'>
    <input name=bus_from_en value='{_h(f['bus_from_en'])}' placeholder='Syntagma (Amalias)'><br>
    To <input name=bus_to_el value='{_h(f['bus_to_el'])}' placeholder='Ευαγγελισμός'>
    <input name=bus_to_en value='{_h(f['bus_to_en'])}' placeholder='Evangelismos'><br>
    Every <input name=bus_headway value='{_h(f['bus_headway'])}' size=4> minutes<br>
    Stops, one per line as <code class=small>name | lat | lon</code><br>
    <textarea name=bus_stops rows=3 cols=70>{_h(f['bus_stops'])}</textarea>
    <div class=small>Leave every bus field empty when there is no replacement bus.</div></td></tr>
  <tr><th>Status</th><td><select name=status>{status_opts}</select>
    <div class=small>draft is never public. scheduled goes public now and turns active at the start time.</div></td></tr>
 </table>
 <button name=action value=save>Save</button>
</form>"""


@app.get("/closures", response_class=HTMLResponse)
def closures_page(_: str = Depends(auth)) -> HTMLResponse:
    with get_db() as conn:
        rows = conn.execute(
            "SELECT * FROM station_closures ORDER BY"
            " CASE status WHEN 'draft' THEN 0 WHEN 'active' THEN 1 WHEN 'scheduled' THEN 2 ELSE 3 END,"
            " starts_at DESC LIMIT 200"
        ).fetchall()
    tr = []
    for r in rows:
        c = closures_mod.row_to_closure(r)
        actions = (
            f"<a class=action href='{ADMIN_PREFIX}/closures/{_h(r['id'])}/edit'>edit</a> "
            f"<a class=action href='{ADMIN_PREFIX}/broadcasts?closure={_h(r['id'])}'>broadcast</a> "
        )
        if r["status"] == "draft":
            actions += (
                f"<form class=inline method=post action={ADMIN_PREFIX}/closures/{_h(r['id'])}/approve>"
                f"<button>approve</button></form> "
            )
        if r["status"] in ("draft", "scheduled", "active"):
            actions += (
                f"<form class=inline method=post action={ADMIN_PREFIX}/closures/{_h(r['id'])}/cancel>"
                f"<button class=danger>cancel</button></form>"
            )
        tr.append(
            f"<tr><td><code>{_h(r['id'])}</code><div class=small>{_h(r['source'])}</div></td>"
            f"<td>{_h(', '.join(c['stationIds']))}</td><td>{_h(', '.join(c['lineIds']) or 'all')}</td>"
            f"<td>{_h(r['mode'])}</td>"
            f"<td>{_h(_athens_label(r['starts_at']))}<br>&rarr; {_h(_athens_label(r['ends_at']))}</td>"
            f"<td>{_h(r['status'])}</td><td class=small>{_h((r['reason_el'] or r['reason_en'])[:120])}</td>"
            f"<td>{actions}</td></tr>"
        )
    templates = "".join(
        f"<a href='{ADMIN_PREFIX}/closures/new?template={k}'><button class=ghost type=button>{_h(t['label'])}</button></a> "
        for k, t in CLOSURE_TEMPLATES.items()
    )
    body = f"""
<h2>Station closures</h2>
<p class=small>Served at <code class=small>/api/closures</code>. Clients evaluate the window themselves, so a
closure scheduled for 08:00 flips at 08:00 even offline. Drafts (including STASY suggestions) are never public.</p>
<p><a href='{ADMIN_PREFIX}/closures/new'><button type=button>New closure</button></a> {templates}</p>
<table><tr><th>Id</th><th>Stations</th><th>Lines</th><th>Mode</th><th>Window (Athens)</th><th>Status</th><th>Reason</th><th></th></tr>
{''.join(tr) or "<tr><td colspan=8 class=small>No closures yet.</td></tr>"}</table>"""
    return page("Closures", body, active="closures")


@app.get("/closures/new", response_class=HTMLResponse)
def closures_new(template: str | None = None, _: str = Depends(auth)) -> HTMLResponse:
    f = _empty_closure_form()
    tpl = CLOSURE_TEMPLATES.get(template or "")
    if tpl:
        f.update({k: v for k, v in tpl.items() if k in f})
    with get_db() as conn:
        form_html = _closure_form_html(conn, f)
    return page("New closure", f"<h2>New closure</h2>{form_html}", active="closures")


@app.get("/closures/{closure_id}/edit", response_class=HTMLResponse)
def closures_edit(closure_id: str, _: str = Depends(auth)) -> HTMLResponse:
    with get_db() as conn:
        row = conn.execute("SELECT * FROM station_closures WHERE id=?", (closure_id,)).fetchone()
        if not row:
            raise HTTPException(404, "Closure not found")
        form_html = _closure_form_html(conn, _closure_form_from_row(row), editing=True)
    note = f"<p class=small>Status now: <strong>{_h(row['status'])}</strong>, source {_h(row['source'])}.</p>"
    return page(f"Edit {closure_id}", f"<h2>Edit closure</h2>{note}{form_html}", active="closures")


@app.post("/closures/save", response_class=HTMLResponse)
async def closures_save(request: Request, identity: str = Depends(auth)) -> Response:
    form = await request.form()
    f = _closure_form_from_post(form)
    action = form.get("action") or "save"
    # Edit vs create is explicit: only the edit form posts edit=1. A new form
    # that reuses an existing id gets the duplicate error, never an update.
    editing = form.get("edit") == "1" and bool(f["id"])
    with get_db() as conn:
        exists = bool(f["id"]) and conn.execute(
            "SELECT 1 FROM station_closures WHERE id=?", (f["id"],)
        ).fetchone() is not None
    if editing and not exists:
        raise HTTPException(404, "Closure not found")
    if action == "translate":
        filled = await run_in_threadpool(_translate_fields, f["reason_el"], ["en", "sq", "it"])
        for lang, text in filled.items():
            f[f"reason_{lang}"] = text
        if f["bus_label_el"] and not f["bus_label_en"]:
            f["bus_label_en"] = (await run_in_threadpool(_translate_fields, f["bus_label_el"], ["en"])).get("en", "")
        notice = "Translated, review before saving." if filled else "Translation unavailable right now, fill in by hand."
        with get_db() as conn:
            html = _closure_form_html(conn, f, notice=notice, editing=editing)
        return page("Closure", f"<h2>{'Edit' if editing else 'New'} closure</h2>{html}", active="closures")
    with get_db() as conn:
        try:
            if not editing and f["status"] in ("cancelled", "ended"):
                raise ValueError("a new closure starts as draft or scheduled")
            if not editing and exists:
                raise ValueError(f"a closure with id {f['id']} already exists, pick another id")
            values = _validate_closure_form(conn, f)
            closure_id = f["id"] or broadcasts_mod.new_id("c")
            probe = {
                "id": closure_id, "stationIds": values["station_ids"], "lineIds": values["line_ids"],
                "mode": values["mode"], "startsAtEpochSeconds": values["starts_at"],
                "endsAtEpochSeconds": values["ends_at"], "status": f["status"],
            }
            if f["status"] not in ("cancelled", "ended"):
                _check_line_invariant(conn, probe)
        except ValueError as error:
            html = _closure_form_html(conn, f, error=str(error), editing=editing)
            return page("Closure", f"<h2>{'Edit' if editing else 'New'} closure</h2>{html}", active="closures")
        if f["status"] in ("draft", "cancelled", "ended"):
            status = f["status"]
        else:
            status = closures_mod.status_for_window({**probe, "status": "scheduled"}, int(time.time()))
        bus = json.dumps(values["replacement_bus"], ensure_ascii=False) if values["replacement_bus"] else None
        params = (
            json.dumps(values["station_ids"]), json.dumps(values["line_ids"]), values["mode"],
            values["starts_at"], values["ends_at"], f["reason_en"], f["reason_el"], f["reason_sq"],
            f["reason_it"], bus, status,
        )
        if editing:
            conn.execute(
                "UPDATE station_closures SET station_ids=?, line_ids=?, mode=?, starts_at=?, ends_at=?,"
                " reason_en=?, reason_el=?, reason_sq=?, reason_it=?, replacement_bus=?, status=?,"
                " updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now') WHERE id=?",
                (*params, closure_id),
            )
        else:
            try:
                conn.execute(
                    "INSERT INTO station_closures(station_ids, line_ids, mode, starts_at, ends_at,"
                    " reason_en, reason_el, reason_sq, reason_it, replacement_bus, status, id, source, created_by)"
                    " VALUES(?,?,?,?,?,?,?,?,?,?,?,?, 'admin', ?)",
                    (*params, closure_id, identity),
                )
            except sqlite3.IntegrityError as error:  # duplicate id
                html = _closure_form_html(conn, f, error=f"cannot save: {error}")
                return page("Closure", f"<h2>New closure</h2>{html}", active="closures")
    return RedirectResponse(f"{ADMIN_PREFIX}/closures", status_code=303)


@app.post("/closures/{closure_id}/approve")
def closures_approve(closure_id: str, _: str = Depends(auth)) -> Response:
    """One-click approval of a draft (typically a STASY suggestion)."""
    with get_db() as conn:
        row = conn.execute("SELECT * FROM station_closures WHERE id=?", (closure_id,)).fetchone()
        if not row:
            raise HTTPException(404, "Closure not found")
        if row["status"] != "draft":
            return RedirectResponse(f"{ADMIN_PREFIX}/closures", status_code=303)
        closure = {**closures_mod.row_to_closure(row), "status": "scheduled"}
        try:
            _check_line_invariant(conn, closure)
        except ValueError as error:
            raise HTTPException(status_code=409, detail=str(error)) from None
        conn.execute(
            "UPDATE station_closures SET status=?, updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now')"
            " WHERE id=? AND status='draft'",
            (closures_mod.status_for_window(closure, int(time.time())), closure_id),
        )
    return RedirectResponse(f"{ADMIN_PREFIX}/closures", status_code=303)


@app.post("/closures/{closure_id}/cancel")
def closures_cancel(closure_id: str, _: str = Depends(auth)) -> Response:
    with get_db() as conn:
        conn.execute(
            "UPDATE station_closures SET status='cancelled',"
            " updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now') WHERE id=?",
            (closure_id,),
        )
    return RedirectResponse(f"{ADMIN_PREFIX}/closures", status_code=303)


# --- admin: broadcasts -------------------------------------------------------

_BROADCAST_FIELDS = (
    "title_el", "title_en", "title_sq", "title_it",
    "body_el", "body_en", "body_sq", "body_it",
    "audience", "audience_station", "send_mode", "send_at", "expires",
    "deep_link", "closure_id",
)
_DEEP_LINK_RE = re.compile(r"^(syrmos://|https://)[^\s<>\"']{1,500}$")
DEFAULT_BROADCAST_TTL = 24 * 3600


def _empty_broadcast_form() -> dict[str, str]:
    f = {k: "" for k in _BROADCAST_FIELDS}
    f.update({"audience": "all", "send_mode": "now"})
    return f


def _broadcast_audience(f: dict[str, str]) -> str:
    return f"station:{f['audience_station']}" if f["audience_station"] else f["audience"]


def _validate_broadcast_form(conn, f: dict[str, str], now: int) -> dict[str, Any]:
    if not (f["title_el"] or f["title_en"]):
        raise ValueError("a title in Greek (or English) is required")
    if not (f["body_el"] or f["body_en"]):
        raise ValueError("a body in Greek (or English) is required")
    audience = _broadcast_audience(f)
    try:
        push_mod.audience_topic(audience)
    except ValueError:
        raise ValueError(f"invalid audience: {audience}") from None
    if f["send_mode"] not in ("now", "schedule", "draft"):
        raise ValueError("invalid send mode")
    send_at = now if f["send_mode"] == "now" else parse_athens_datetime(f["send_at"])
    if f["send_mode"] == "schedule" and send_at is None:
        raise ValueError("pick the date/time to send")
    base = send_at or now
    expires = parse_athens_datetime(f["expires"]) or base + DEFAULT_BROADCAST_TTL
    if expires <= base:
        raise ValueError("expiry must be after the send time")
    if f["deep_link"] and not _DEEP_LINK_RE.match(f["deep_link"]):
        raise ValueError("deep link must start with syrmos:// or https://")
    if f["closure_id"] and not conn.execute(
        "SELECT 1 FROM station_closures WHERE id=?", (f["closure_id"],)
    ).fetchone():
        raise ValueError("unknown closure")
    return {"audience": audience, "send_at": send_at, "expires_at": expires}


def _broadcast_preview(f: dict[str, str]) -> str:
    cards = []
    for lang in ("el", "en", "sq", "it"):
        title, body = push_mod.pick_text(f, lang)
        cards.append(
            f"<div class=stat><div class=stat-label>{lang}</div>"
            f"<strong>{_h(title)}</strong><div class=small>{_h(body)}</div></div>"
        )
    return "<div class=stat-grid>" + "".join(cards) + "</div>"


def _broadcast_form_html(conn, f: dict[str, str], *, error: str = "", notice: str = "", extra: str = "") -> str:
    lines = _line_choices(conn)
    audiences = ["all", "region:athens", "region:thessaloniki"] + [f"line:{l}" for l in lines]
    aud_opts = "".join(
        f"<option value='{_h(a)}' {'selected' if f['audience'] == a else ''}>{_h(a)}</option>" for a in audiences
    )
    st_opts = "<option value=''>(none)</option>" + "".join(
        f"<option value='{_h(r['id'])}' {'selected' if f['audience_station'] == r['id'] else ''}>"
        f"{_h(r['id'])} &middot; {_h(r['name_el'])}</option>"
        for r in _station_choices(conn)
    )
    closures = conn.execute(
        "SELECT id, mode, status FROM station_closures WHERE status NOT IN ('cancelled', 'ended')"
        " ORDER BY starts_at DESC LIMIT 50"
    ).fetchall()
    cl_opts = "<option value=''>(none)</option>" + "".join(
        f"<option value='{_h(r['id'])}' {'selected' if f['closure_id'] == r['id'] else ''}>"
        f"{_h(r['id'])} ({_h(r['mode'])}, {_h(r['status'])})</option>"
        for r in closures
    )
    modes = {"now": "send now", "schedule": "schedule", "draft": "save as draft"}
    mode_radios = " ".join(
        f"<label><input type=radio name=send_mode value={m} {'checked' if f['send_mode'] == m else ''}> {label}</label>"
        for m, label in modes.items()
    )
    msg = ""
    if error:
        msg += f"<p class='pill pill-danger'>{_h(error)}</p>"
    if notice:
        msg += f"<p class='pill pill-success'>{_h(notice)}</p>"

    def row(label: str, name: str, rows: int = 1) -> str:
        if rows > 1:
            return f"<tr><th>{label}</th><td><textarea name={name} rows={rows} cols=70>{_h(f[name])}</textarea></td></tr>"
        return f"<tr><th>{label}</th><td><input name={name} value='{_h(f[name])}' size=70></td></tr>"

    return f"""{msg}{extra}
<form method=post action={ADMIN_PREFIX}/broadcasts/save class=card>
 <table>
  {row('Title (Greek)', 'title_el')}
  {row('Body (Greek)', 'body_el', 4)}
  <tr><th></th><td><button name=action value=translate class=ghost>Translate to en / sq / it</button></td></tr>
  {row('Title (English)', 'title_en')}
  {row('Body (English)', 'body_en', 3)}
  {row('Title (Albanian)', 'title_sq')}
  {row('Body (Albanian)', 'body_sq', 3)}
  {row('Title (Italian)', 'title_it')}
  {row('Body (Italian)', 'body_it', 3)}
  <tr><th>Audience</th><td><select name=audience>{aud_opts}</select>
    or station <select name=audience_station>{st_opts}</select>
    <div class=small>A station overrides the audience list.</div></td></tr>
  <tr><th>When</th><td>{mode_radios}
    <input type=datetime-local name=send_at value='{_h(f['send_at'])}'> <span class=small>Athens time, for schedule</span></td></tr>
  <tr><th>Expires (Athens time)</th><td><input type=datetime-local name=expires value='{_h(f['expires'])}'>
    <span class=small>default: 24 h after sending</span></td></tr>
  <tr><th>Deep link</th><td><input name=deep_link value='{_h(f['deep_link'])}' size=50 placeholder='syrmos://station/M2_SYN'></td></tr>
  <tr><th>Closure</th><td><select name=closure_id>{cl_opts}</select></td></tr>
 </table>
 <button name=action value=preview class=ghost>Dry run and preview</button>
 <button name=action value=save>Save</button>
</form>"""


@app.get("/broadcasts", response_class=HTMLResponse)
def broadcasts_page(closure: str | None = None, _: str = Depends(auth)) -> HTMLResponse:
    f = _empty_broadcast_form()
    with get_db() as conn:
        if closure:
            row = conn.execute("SELECT * FROM station_closures WHERE id=?", (closure,)).fetchone()
            if row:
                f["closure_id"] = row["id"]
                f["body_el"] = row["reason_el"]
                f["body_en"] = row["reason_en"]
                f["body_sq"] = row["reason_sq"]
                f["body_it"] = row["reason_it"]
        rows = conn.execute("SELECT * FROM broadcasts ORDER BY created_at DESC, id DESC LIMIT 100").fetchall()
        form_html = _broadcast_form_html(conn, f)
    tr = []
    for r in rows:
        actions = ""
        if r["status"] in ("draft", "scheduled"):
            actions = (
                f"<form class=inline method=post action={ADMIN_PREFIX}/broadcasts/{_h(r['id'])}/cancel>"
                f"<button class=danger>cancel</button></form>"
            )
        tr.append(
            f"<tr><td><code>{_h(r['id'])}</code></td><td>{_h(r['title_el'] or r['title_en'])}</td>"
            f"<td>{_h(r['audience'])}</td><td>{_h(r['status'])}</td>"
            f"<td>{_h(_athens_label(r['sent_at'] or r['send_at']) if (r['sent_at'] or r['send_at']) else '')}</td>"
            f"<td>{r['sent_count']} sent / {r['failed_count']} failed</td><td>{actions}</td></tr>"
        )
    body = f"""
<h2>Broadcasts</h2>
<p class=small>Pushed to registered devices (APNs / FCM) and served at <code class=small>/api/broadcasts</code>
for polling. The dispatcher timer sends scheduled broadcasts within a minute.</p>
{form_html}
<h3>Recent</h3>
<table><tr><th>Id</th><th>Title</th><th>Audience</th><th>Status</th><th>When (Athens)</th><th>Delivery</th><th></th></tr>
{''.join(tr) or "<tr><td colspan=7 class=small>No broadcasts yet.</td></tr>"}</table>"""
    return page("Broadcasts", body, active="broadcasts")


def _dispatch_in_background(broadcast_id: str) -> None:
    conn = dbmod.connect()
    try:
        dbmod.migrate(conn)
        dispatch_mod.send_one(conn, broadcast_id, push_mod.Senders.from_env())
    except Exception as exc:  # noqa: BLE001 - the timer retries nothing, so just log
        logger.error("broadcast %s dispatch failed: %r", broadcast_id, exc)
    finally:
        conn.close()


@app.post("/broadcasts/save", response_class=HTMLResponse)
async def broadcasts_save(
    request: Request, background: BackgroundTasks, identity: str = Depends(auth)
) -> Response:
    form = await request.form()
    f = _empty_broadcast_form()
    for key in _BROADCAST_FIELDS:
        value = form.get(key)
        if isinstance(value, str):
            f[key] = value.strip()
    action = form.get("action") or "save"
    now = int(time.time())
    if action == "translate":
        notice_parts = []
        for prefix in ("title", "body"):
            filled = await run_in_threadpool(_translate_fields, f[f"{prefix}_el"], ["en", "sq", "it"])
            for lang, text in filled.items():
                f[f"{prefix}_{lang}"] = text
            notice_parts.append(len(filled))
        notice = "Translated, review before saving." if any(notice_parts) else "Translation unavailable right now, fill in by hand."
        with get_db() as conn:
            html = _broadcast_form_html(conn, f, notice=notice)
        return page("Broadcasts", f"<h2>Broadcast</h2>{html}", active="broadcasts")
    with get_db() as conn:
        try:
            values = _validate_broadcast_form(conn, f, now)
        except ValueError as error:
            html = _broadcast_form_html(conn, f, error=str(error))
            return page("Broadcasts", f"<h2>Broadcast</h2>{html}", active="broadcasts")
        if action == "preview":
            counts = push_mod.count_targets(conn, values["audience"])
            breakdown = ", ".join(f"{k} {v}" for k, v in sorted(counts.items()) if k != "total")
            notice = f"Dry run: {counts['total']} device(s) would receive this" + (f" ({breakdown})" if breakdown else "") + "."
            html = _broadcast_form_html(conn, f, notice=notice, extra=_broadcast_preview(f))
            return page("Broadcasts", f"<h2>Broadcast</h2>{html}", active="broadcasts")
        broadcast_id = broadcasts_mod.new_id("b")
        status = "draft" if f["send_mode"] == "draft" else "scheduled"
        conn.execute(
            "INSERT INTO broadcasts(id, title_en, title_el, title_sq, title_it, body_en, body_el, body_sq, body_it,"
            " audience, send_at, expires_at, deep_link, closure_id, status, created_by)"
            " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            (
                broadcast_id, f["title_en"], f["title_el"], f["title_sq"], f["title_it"],
                f["body_en"], f["body_el"], f["body_sq"], f["body_it"],
                values["audience"], values["send_at"], values["expires_at"],
                f["deep_link"] or None, f["closure_id"] or None, status, identity,
            ),
        )
    if f["send_mode"] == "now":
        background.add_task(_dispatch_in_background, broadcast_id)
    return RedirectResponse(f"{ADMIN_PREFIX}/broadcasts", status_code=303)


@app.post("/broadcasts/{broadcast_id}/cancel")
def broadcasts_cancel(broadcast_id: str, _: str = Depends(auth)) -> Response:
    with get_db() as conn:
        conn.execute(
            "UPDATE broadcasts SET status='cancelled' WHERE id=? AND status IN ('draft', 'scheduled')",
            (broadcast_id,),
        )
    return RedirectResponse(f"{ADMIN_PREFIX}/broadcasts", status_code=303)


# --- admin: push devices -----------------------------------------------------

@app.get("/push", response_class=HTMLResponse)
def push_page(_: str = Depends(auth)) -> HTMLResponse:
    with get_db() as conn:
        by = conn.execute(
            "SELECT platform, environment, lang, COUNT(*) n FROM push_devices"
            " GROUP BY platform, environment, lang ORDER BY n DESC"
        ).fetchall()
        topic_counts: dict[str, int] = {}
        for r in conn.execute("SELECT topics FROM push_devices"):
            for t in push_mod._topics(r["topics"]):
                topic_counts[t] = topic_counts.get(t, 0) + 1
        total = sum(r["n"] for r in by)
    apns_set = bool(os.environ.get("APNS_KEY_PATH") and os.environ.get("APNS_KEY_ID"))
    fcm_set = bool(os.environ.get("FCM_SERVICE_ACCOUNT_PATH"))
    rows = "".join(
        f"<tr><td>{_h(r['platform'])}</td><td>{_h(r['environment'])}</td><td>{_h(r['lang'])}</td><td>{r['n']}</td></tr>"
        for r in by
    )
    topics = "".join(
        f"<tr><td><code>{_h(t)}</code></td><td>{n}</td></tr>"
        for t, n in sorted(topic_counts.items(), key=lambda kv: -kv[1])[:40]
    )
    body = f"""
<h2>Push devices</h2>
<div class=stat-grid>
 <div class=stat><div class=stat-label>Registered</div><div class=stat-value>{total}</div></div>
 <div class=stat><div class=stat-label>APNs key</div><div class=stat-value>{'set' if apns_set else 'missing'}</div></div>
 <div class=stat><div class=stat-label>FCM account</div><div class=stat-value>{'set' if fcm_set else 'missing'}</div></div>
</div>
<table><tr><th>Platform</th><th>Environment</th><th>Language</th><th>Devices</th></tr>{rows}</table>
<h3>Topics</h3>
<table><tr><th>Topic</th><th>Devices</th></tr>{topics}</table>"""
    return page("Push", body, active="push")
