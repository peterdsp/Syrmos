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
<title>{title} — Syrmos Admin</title>
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
      <a href="{prefix}/sync" class="{c_sync}">Sync</a>
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
    "sync": "c_sync",
}


def page(title: str, body: str, active: str = "") -> HTMLResponse:
    fields = {k: "" for k in _TAB_KEYS.values()}
    if active in _TAB_KEYS:
        fields[_TAB_KEYS[active]] = "is-active"
    return HTMLResponse(BASE.format(
        title=title,
        body=body,
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
        f"<td><a href='/lines/{r['id']}/edit'>edit</a></td></tr>"
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


# Icons — table of every station icon with preview + override field

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


# Line drawing — editable polyline parameters

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


# Stations — bulk edit names + coords + accessibility

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


# Operator partners — placeholder for live-feed credentials

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
    tr = "".join(
        f"<tr><form method=post action={ADMIN_PREFIX}/operators/save>"
        f"<input type=hidden name=id value={r['id']}>"
        f"<td><strong>{r['operator_name']}</strong><br><span class=small><code>{r['operator_id']}</code></span></td>"
        f"<td><select name=feed_kind>{''.join(f'<option {chr(34) + chr(34) if k != r[chr(34)+chr(34) + chr(34) + chr(34) + chr(34)+chr(34)] else chr(34) + chr(34)} value={chr(34)}{k}{chr(34)} {chr(34) + chr(34) if k != r[chr(34)+chr(34) + chr(34) + chr(34) + chr(34)+chr(34)] else chr(34) + chr(34)}>{k}</option>' for k in feed_opts)}</select></td>"
        f"<td><input name=feed_url value='{r['feed_url'] or ''}' placeholder='https://...' size=36></td>"
        f"<td><select name=auth_method>{''.join(f'<option value={chr(34)}{a}{chr(34)} {chr(34)+chr(34) if a == r[chr(34)+chr(34)+chr(34)+chr(34)+chr(34)+chr(34)] else chr(34) + chr(34)}>{a}</option>' for a in auth_opts)}</select></td>"
        f"<td><input name=auth_credential type=password placeholder='set to update' size=18></td>"
        f"<td><input name=refresh_seconds type=number value={r['refresh_seconds']} min=5 max=3600 style=width:5em></td>"
        f"<td><select name=status>{''.join(f'<option value={chr(34)}{s}{chr(34)} {chr(34) if s == r[chr(34)+chr(34)+chr(34)+chr(34)+chr(34)+chr(34)] else chr(34) + chr(34)}>{s}</option>' for s in status_opts)}</select></td>"
        f"<td><button>save</button></form></tr>"
        for r in rows
    )
    # The above f-string is ugly; rebuild simply.
    parts = []
    for r in rows:
        feed_sel = "".join(f'<option value="{k}" {"selected" if k == r["feed_kind"] else ""}>{k}</option>' for k in feed_opts)
        auth_sel = "".join(f'<option value="{a}" {"selected" if a == r["auth_method"] else ""}>{a}</option>' for a in auth_opts)
        status_sel = "".join(f'<option value="{s}" {"selected" if s == r["status"] else ""}>{s}</option>' for s in status_opts)
        parts.append(
            "<tr><form method=post action={ADMIN_PREFIX}/operators/save>"
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
    body = """
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


# Health (unauthenticated)

@app.get("/healthz")
def healthz() -> JSONResponse:
    return JSONResponse({"ok": True})
