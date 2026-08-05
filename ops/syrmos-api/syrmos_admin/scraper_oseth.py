"""Bi-hourly scraper for oseth.com.gr/el/nea-anakoinoseis (OSETH announcements).

Fetches the HTML announcements page, extracts article links, titles and
dates, translates from Greek to English and Albanian, and stores them in
the `announcements` table as service alerts for the Thessaloniki feed.

Run: python3 -m syrmos_admin.scraper_oseth
Cron: every 2 hours via systemd timer (see systemd/syrmos-scraper-oseth.timer)
"""
from __future__ import annotations

import html as html_mod
import json
import re
import sqlite3
from dataclasses import dataclass, field
from datetime import date, datetime
from urllib.error import URLError
from urllib.request import Request, urlopen

from . import db as dbmod

USER_AGENT = "syrmos-oseth-scraper/1.0 (+https://syrmos.peterdsp.dev)"
INDEX_URL = "https://oseth.com.gr/el/nea-anakoinoseis"
TIMEOUT_SECONDS = 30
MAX_ENTRIES = 50

_GREEK_LETTER_RE = re.compile(r"[Ͱ-Ͽἀ-῿]")
_HTML_TAG_RE = re.compile(r"<[^>]+>")

# Match article links: <a href="/el/article/SLUG">
_ARTICLE_LINK_RE = re.compile(
    r'<a[^>]+href=["\'](/el/article/[^"\']+)["\'][^>]*>(.*?)</a>',
    re.IGNORECASE | re.DOTALL,
)

# Greek month names to month numbers
_GREEK_MONTHS = {
    "ιανουαρίου": 1, "ιανουάριος": 1, "ιανουαριου": 1,
    "φεβρουαρίου": 2, "φεβρουάριος": 2, "φεβρουαριου": 2,
    "μαρτίου": 3, "μάρτιος": 3, "μαρτιου": 3,
    "απριλίου": 4, "απρίλιος": 4, "απριλιου": 4,
    "μαΐου": 5, "μάιος": 5, "μαιου": 5, "μαίου": 5,
    "ιουνίου": 6, "ιούνιος": 6, "ιουνιου": 6,
    "ιουλίου": 7, "ιούλιος": 7, "ιουλιου": 7,
    "αυγούστου": 8, "αύγουστος": 8, "αυγουστου": 8,
    "σεπτεμβρίου": 9, "σεπτέμβριος": 9, "σεπτεμβριου": 9,
    "οκτωβρίου": 10, "οκτώβριος": 10, "οκτωβριου": 10,
    "νοεμβρίου": 11, "νοέμβριος": 11, "νοεμβριου": 11,
    "δεκεμβρίου": 12, "δεκέμβριος": 12, "δεκεμβριου": 12,
}

# Date pattern: "DD MonthName YYYY"
_GREEK_DATE_RE = re.compile(
    r"(\d{1,2})\s+("
    + "|".join(re.escape(m) for m in _GREEK_MONTHS)
    + r")\s+(\d{4})",
    re.IGNORECASE,
)


def _has_greek(text: str) -> bool:
    return bool(_GREEK_LETTER_RE.search(text))


def _is_valid_italian_translation(text: str) -> bool:
    lowered = text.lower()
    return bool(text) and not _has_greek(text) and not any(
        marker in lowered
        for marker in ("error 500", "that's an error", "there was an error", "server error")
    )


def _translate(text: str, target: str) -> str:
    text = text.strip()
    if not text or (target != "it" and not _has_greek(text)):
        return text
    if target == "it":
        try:
            from deep_translator import GoogleTranslator
            translated = (GoogleTranslator(source="el", target="it").translate(text) or "").strip()
            if _is_valid_italian_translation(translated):
                return translated
        except Exception:
            pass
        try:
            from deep_translator import MyMemoryTranslator
            translated = (
                MyMemoryTranslator(source="greek", target="italian").translate(text) or ""
            ).strip()
            return translated if _is_valid_italian_translation(translated) else ""
        except Exception:
            return ""
    try:
        from deep_translator import GoogleTranslator
        result = GoogleTranslator(source="el", target=target).translate(text)
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


def _parse_greek_date(text: str) -> str:
    """Parse 'DD MonthName YYYY' from text and return ISO date string.
    Falls back to empty string if no date found."""
    m = _GREEK_DATE_RE.search(text.lower())
    if not m:
        return ""
    try:
        day = int(m.group(1))
        month = _GREEK_MONTHS.get(m.group(2).lower(), 0)
        year = int(m.group(3))
        if not month:
            return ""
        return date(year, month, day).isoformat()
    except (ValueError, OverflowError):
        return ""


@dataclass
class AnnouncementItem:
    slug: str
    title: str
    title_en: str
    title_sq: str
    title_it: str
    summary: str
    summary_en: str
    summary_sq: str
    summary_it: str
    url: str
    publish_date: str
    valid_from: str


def fetch_page(url: str = INDEX_URL) -> str:
    req = Request(url, headers={
        "User-Agent": USER_AGENT,
        "Accept-Language": "el,en;q=0.5",
    })
    with urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
        return resp.read().decode("utf-8", errors="replace")


def _slug_from_path(path: str) -> str:
    return path.rstrip("/").rsplit("/", 1)[-1]


def parse_announcements(html: str) -> list[AnnouncementItem]:
    """Parse the announcements index page and return items."""
    items: list[AnnouncementItem] = []
    seen: set[str] = set()

    for match in _ARTICLE_LINK_RE.finditer(html):
        path = match.group(1)
        raw_title = _strip_html(match.group(2)).strip()

        if not raw_title:
            continue

        slug = _slug_from_path(path)
        item_id = f"oseth-{slug}"

        if item_id in seen:
            continue
        seen.add(item_id)

        url = f"https://oseth.com.gr{path}"

        # Try to find a date near this link in the surrounding HTML
        link_start = max(0, match.start() - 200)
        link_end = min(len(html), match.end() + 500)
        context = html[link_start:link_end]
        publish_date = _parse_greek_date(context)
        valid_from = publish_date or date.today().isoformat()

        # Extract summary from text following the link
        after = html[match.end():match.end() + 1000]
        summary = _strip_html(after).strip()
        # Truncate at next article boundary or limit
        summary = summary[:500]

        title_en = _translate_en(raw_title)
        title_sq = _translate_sq(raw_title)
        title_it = _translate_it(raw_title)
        summary_en = _translate_en(summary) if summary else ""
        summary_sq = _translate_sq(summary) if summary else ""
        summary_it = _translate_it(summary) if summary else ""

        items.append(AnnouncementItem(
            slug=item_id,
            title=raw_title,
            title_en=title_en,
            title_sq=title_sq,
            title_it=title_it,
            summary=summary,
            summary_en=summary_en,
            summary_sq=summary_sq,
            summary_it=summary_it,
            url=url,
            publish_date=publish_date,
            valid_from=valid_from,
        ))

        if len(items) >= MAX_ENTRIES:
            break

    return items


def upsert(conn: sqlite3.Connection, items: list[AnnouncementItem]) -> int:
    cur = conn.cursor()
    cur.execute("BEGIN")
    try:
        for idx, item in enumerate(items):
            affected_json = json.dumps([], ensure_ascii=False)
            sort_order = 100 + idx
            # Try the schema with title_sq + summary_sq first; fall back
            # to the pre-Sq column set so an older Pi behind on migrations
            # still ingests rows.
            try:
                cur.execute(
                    "INSERT INTO announcements"
                    "(id, title, title_en, title_sq, title_it, summary, summary_en, summary_sq, summary_it,"
                    " url, date, category, sort_order,"
                    " affected_lines, severity, valid_from, valid_until)"
                    " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
                    " ON CONFLICT(id) DO UPDATE SET"
                    " title=excluded.title, title_en=excluded.title_en, title_sq=excluded.title_sq,"
                    " title_it=COALESCE(NULLIF(excluded.title_it, ''), announcements.title_it),"
                    " summary=excluded.summary, summary_en=excluded.summary_en, summary_sq=excluded.summary_sq,"
                    " summary_it=COALESCE(NULLIF(excluded.summary_it, ''), announcements.summary_it),"
                    " url=excluded.url, category=excluded.category, sort_order=excluded.sort_order,"
                    " affected_lines=excluded.affected_lines, severity=excluded.severity,"
                    " valid_from=excluded.valid_from, valid_until=excluded.valid_until",
                    (
                        item.slug, item.title, item.title_en, item.title_sq, item.title_it,
                        item.summary, item.summary_en, item.summary_sq, item.summary_it,
                        item.url, item.publish_date, "serviceAlert", sort_order,
                        affected_json, "info",
                        item.valid_from, None,
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
                        item.slug, item.title, item.title_en,
                        item.summary, item.summary_en,
                        item.url, item.publish_date, "serviceAlert", sort_order,
                        affected_json, "info",
                        item.valid_from, None,
                    ),
                )
        cur.execute("COMMIT")
        return len(items)
    except Exception:
        cur.execute("ROLLBACK")
        raise


def run_once() -> int:
    """Scrape once and persist. Returns the number of announcement rows written."""
    try:
        html = fetch_page()
    except (URLError, TimeoutError) as e:
        with dbmod.connect() as conn:
            dbmod.migrate(conn)
            try:
                conn.execute(
                    "INSERT INTO scrape_log(source, ok, rows_written, error)"
                    " VALUES('oseth', 0, 0, ?)",
                    (f"fetch page failed: {e}",),
                )
            except sqlite3.OperationalError:
                pass
        return 0

    items = parse_announcements(html)

    with dbmod.connect() as conn:
        dbmod.migrate(conn)
        count = upsert(conn, items)
        try:
            conn.execute(
                "INSERT INTO scrape_log(source, ok, rows_written)"
                " VALUES('oseth', 1, ?)",
                (count,),
            )
        except sqlite3.OperationalError:
            pass
    return count


if __name__ == "__main__":
    written = run_once()
    print(f"oseth: wrote {written} item(s)")
