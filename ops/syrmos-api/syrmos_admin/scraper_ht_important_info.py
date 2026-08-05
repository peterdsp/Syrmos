"""Daily scraper for hellenictrain.gr/important-information (service alerts).

Fetches the Important Information page, extracts service disruptions
grouped by line category and issue type, translates from Greek to
English and Albanian, and stores them in the `announcements` table
for the Service Alerts carousel.

Run: python3 -m syrmos_admin.scraper_ht_important_info
Cron: daily via systemd timer (see systemd/syrmos-scraper-ht-important-info.timer)
"""
from __future__ import annotations

import hashlib
import html as html_mod
import json
import re
import sqlite3
from dataclasses import dataclass, field
from datetime import date
from urllib.error import URLError
from urllib.request import Request, urlopen

from . import db as dbmod

USER_AGENT = "syrmos-ht-scraper/1.0 (+https://syrmos.peterdsp.dev)"
INDEX_URL = "https://www.hellenictrain.gr/important-information"
TIMEOUT_SECONDS = 30
MAX_ENTRIES = 50

_GREEK_LETTER_RE = re.compile(r"[Ͱ-Ͽἀ-῿]")
_HTML_TAG_RE = re.compile(r"<[^>]+>")
_DATE_RE = re.compile(r"(\d{1,2})/(\d{1,2})/(\d{4})")

_EXPIRATION_DATE_RE = re.compile(
    r'<div\s+class="expiration-date">(.*?)</div>',
    re.DOTALL,
)
_ITINERARY_TITLE_RE = re.compile(
    r'<div\s+class="itinerary-title">(.*?)</div>',
    re.DOTALL,
)
_ISSUE_TYPE_TITLE_RE = re.compile(
    r'<div\s+class="issue-type-title">(.*?)</div>',
    re.DOTALL,
)
_RESULT_ITEM_RE = re.compile(
    r'<li\s+class="result-item">\s*'
    r'<div\s+class="created-time">(.*?)</div>\s*'
    r'<div\s+class="result-content">(.*?)</div>',
    re.DOTALL,
)


def _has_greek(text: str) -> bool:
    return bool(_GREEK_LETTER_RE.search(text))


def _translate(text: str, target: str) -> str:
    text = text.strip()
    if not text or (target != "it" and not _has_greek(text)):
        return text
    try:
        from deep_translator import GoogleTranslator
        source = "auto" if target == "it" else "el"
        result = GoogleTranslator(source=source, target=target).translate(text)
        return (result or "").strip() or text
    except Exception:
        return text


def _translate_en(text: str) -> str:
    return _translate(text, "en")


def _translate_sq(text: str) -> str:
    return _translate(text, "sq")


def _translate_it(text: str) -> str:
    return _translate(text, "it")


def _strip_html(text: str) -> str:
    return html_mod.unescape(_HTML_TAG_RE.sub("", text)).strip()


@dataclass
class AlertItem:
    entry_id: str
    title: str
    title_en: str
    title_sq: str
    title_it: str
    summary: str
    summary_en: str
    summary_sq: str
    summary_it: str
    url: str
    published_at: str
    line_category: str
    affected_lines: list[str] = field(default_factory=list)


def _parse_date(text: str) -> str:
    m = _DATE_RE.search(text)
    if not m:
        return date.today().isoformat()
    try:
        day, month, year = int(m.group(1)), int(m.group(2)), int(m.group(3))
        return date(year, month, day).isoformat()
    except (ValueError, OverflowError):
        return date.today().isoformat()


def _make_id(line_cat: str, issue_type: str, content: str) -> str:
    raw = f"{line_cat}|{issue_type}|{content[:100]}"
    digest = hashlib.md5(raw.encode("utf-8")).hexdigest()[:12]
    return f"ht-alert-{digest}"


_LINE_SLUG_MAP = {
    "proastiakos": ["A1", "A2", "A3", "A4"],
    "suburban": ["A1", "A2", "A3", "A4"],
    "intercity": [],
    "international": [],
}


def _affected_lines_from_slug(slug: str) -> list[str]:
    slug_lower = slug.lower().strip()
    return _LINE_SLUG_MAP.get(slug_lower, [])


def fetch_page(url: str = INDEX_URL) -> str:
    req = Request(url, headers={
        "User-Agent": USER_AGENT,
        "Accept-Language": "el,en;q=0.5",
    })
    with urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
        return resp.read().decode("utf-8", errors="replace")


def parse_important_info(html: str) -> list[AlertItem]:
    items: list[AlertItem] = []
    seen: set[str] = set()

    date_match = _EXPIRATION_DATE_RE.search(html)
    published_at = _parse_date(date_match.group(1) if date_match else "")

    group_starts = list(re.finditer(
        r'<div\s+class="itinerary-group\s+([^"]+)">',
        html,
    ))

    for gi, group_match in enumerate(group_starts):
        group_slug = group_match.group(1).strip()
        start = group_match.end()
        end = group_starts[gi + 1].start() if gi + 1 < len(group_starts) else len(html)
        group_html = html[start:end]

        issue_starts = list(re.finditer(
            r'<div\s+class="issue-type-group">',
            group_html,
        ))

        for ii, issue_match in enumerate(issue_starts):
            istart = issue_match.end()
            iend = issue_starts[ii + 1].start() if ii + 1 < len(issue_starts) else len(group_html)
            issue_html = group_html[istart:iend]

            itinerary_m = _ITINERARY_TITLE_RE.search(issue_html)
            issue_type_m = _ISSUE_TYPE_TITLE_RE.search(issue_html)
            line_category = _strip_html(itinerary_m.group(1)) if itinerary_m else group_slug.replace("_", " ").title()
            issue_type = _strip_html(issue_type_m.group(1)) if issue_type_m else ""

            for result_m in _RESULT_ITEM_RE.finditer(issue_html):
                content = _strip_html(result_m.group(2))[:500]
                if not content:
                    continue

                entry_id = _make_id(line_category, issue_type, content)
                if entry_id in seen:
                    continue
                seen.add(entry_id)

                title = f"{line_category}: {issue_type}" if issue_type else line_category
                summary = content

                title_en = _translate_en(title)
                title_sq = _translate_sq(title)
                title_it = _translate_it(title)
                summary_en = _translate_en(summary) if summary else ""
                summary_sq = _translate_sq(summary) if summary else ""
                summary_it = _translate_it(summary) if summary else ""

                items.append(AlertItem(
                    entry_id=entry_id,
                    title=title,
                    title_en=title_en,
                    title_sq=title_sq,
                    title_it=title_it,
                    summary=summary,
                    summary_en=summary_en,
                    summary_sq=summary_sq,
                    summary_it=summary_it,
                    url=INDEX_URL,
                    published_at=published_at,
                    line_category=line_category,
                    affected_lines=_affected_lines_from_slug(group_slug),
                ))

                if len(items) >= MAX_ENTRIES:
                    return items

    return items


def upsert(conn: sqlite3.Connection, items: list[AlertItem]) -> int:
    cur = conn.cursor()
    cur.execute("BEGIN")
    try:
        for idx, item in enumerate(items):
            affected_json = json.dumps(item.affected_lines, ensure_ascii=False)
            try:
                cur.execute(
                    "INSERT INTO announcements"
                    "(id, title, title_en, title_sq, title_it, summary, summary_en, summary_sq, summary_it,"
                    " url, date, category, sort_order,"
                    " affected_lines, severity, valid_from, valid_until)"
                    " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
                    " ON CONFLICT(id) DO UPDATE SET"
                    " title=excluded.title, title_en=excluded.title_en, title_sq=excluded.title_sq, title_it=excluded.title_it,"
                    " summary=excluded.summary, summary_en=excluded.summary_en, summary_sq=excluded.summary_sq, summary_it=excluded.summary_it,"
                    " url=excluded.url, category=excluded.category, sort_order=excluded.sort_order,"
                    " affected_lines=excluded.affected_lines, severity=excluded.severity,"
                    " valid_from=excluded.valid_from, valid_until=excluded.valid_until",
                    (
                        item.entry_id, item.title, item.title_en, item.title_sq, item.title_it,
                        item.summary, item.summary_en, item.summary_sq, item.summary_it,
                        item.url, item.published_at, "serviceAlert", idx,
                        affected_json, "warning",
                        item.published_at, None,
                    ),
                )
            except sqlite3.OperationalError:
                cur.execute(
                    "INSERT INTO announcements"
                    "(id, title, title_en, summary, summary_en, url, date, category, sort_order,"
                    " affected_lines, severity, valid_from, valid_until)"
                    " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)"
                    " ON CONFLICT(id) DO UPDATE SET"
                    " title=excluded.title, title_en=excluded.title_en,"
                    " summary=excluded.summary, summary_en=excluded.summary_en,"
                    " url=excluded.url, category=excluded.category, sort_order=excluded.sort_order,"
                    " affected_lines=excluded.affected_lines, severity=excluded.severity,"
                    " valid_from=excluded.valid_from, valid_until=excluded.valid_until",
                    (
                        item.entry_id, item.title, item.title_en,
                        item.summary, item.summary_en,
                        item.url, item.published_at, "serviceAlert", idx,
                        affected_json, "warning",
                        item.published_at, None,
                    ),
                )
        cur.execute("COMMIT")
        return len(items)
    except Exception:
        cur.execute("ROLLBACK")
        raise


def run_once() -> int:
    try:
        html = fetch_page()
    except (URLError, TimeoutError) as e:
        with dbmod.connect() as conn:
            dbmod.migrate(conn)
            try:
                conn.execute(
                    "INSERT INTO scrape_log(source, ok, rows_written, error)"
                    " VALUES('ht_important_info', 0, 0, ?)",
                    (f"fetch page failed: {e}",),
                )
            except sqlite3.OperationalError:
                pass
        return 0

    items = parse_important_info(html)

    with dbmod.connect() as conn:
        dbmod.migrate(conn)
        count = upsert(conn, items)
        try:
            conn.execute(
                "INSERT INTO scrape_log(source, ok, rows_written)"
                " VALUES('ht_important_info', 1, ?)",
                (count,),
            )
        except sqlite3.OperationalError:
            pass
    return count


if __name__ == "__main__":
    written = run_once()
    print(f"ht_important_info: wrote {written} alert(s)")
