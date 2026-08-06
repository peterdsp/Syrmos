"""Daily scraper for hellenictrain.gr/anakoinoseis-ht (announcements).

Fetches the announcements page, extracts news items with title and
summary, translates from Greek to English and Albanian, and stores
them in the `rail_news` table for the Home news carousel.

Run: python3 -m syrmos_admin.scraper_hellenic_train
Cron: daily via systemd timer (see systemd/syrmos-scraper-hellenic-train.timer)
"""
from __future__ import annotations

import hashlib
import json
import re
import sqlite3
from dataclasses import dataclass, field
from datetime import date
from urllib.error import URLError
from urllib.request import Request, urlopen

from . import db as dbmod
from .translation import translate_from_greek

USER_AGENT = "syrmos-ht-scraper/1.0 (+https://syrmos.peterdsp.dev)"
INDEX_URL = "https://www.hellenictrain.gr/anakoinoseis-ht"
TIMEOUT_SECONDS = 30
MAX_ENTRIES = 50

_HTML_TAG_RE = re.compile(r"<[^>]+>")
_DATE_RE = re.compile(r"(\d{1,2})/(\d{1,2})/(\d{4})")

_VIEWS_ROW_RE = re.compile(
    r'<div\s+class="views-row">\s*'
    r'<article[^>]*data-history-node-id="(\d+)"[^>]*>(.*?)</article>',
    re.DOTALL,
)

_NEWS_TITLE_RE = re.compile(
    r'<a\s+href="([^"]*)"[^>]*class="news-title"[^>]*>\s*'
    r'<span[^>]*class="[^"]*field--name-title[^"]*"[^>]*>(.*?)</span>',
    re.DOTALL,
)

_NEWS_BODY_RE = re.compile(
    r'<div\s+class="news-body">(.*?)</div>',
    re.DOTALL,
)


def _translate(text: str, target: str) -> str:
    return translate_from_greek(text, target)


def _translate_en(text: str) -> str:
    return _translate(text, "en")


def _translate_sq(text: str) -> str:
    return _translate(text, "sq")


def _translate_it(text: str) -> str:
    return _translate(text, "it")


def _strip_html(text: str) -> str:
    return _HTML_TAG_RE.sub("", text).strip()


@dataclass
class NewsItem:
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
    thumbnail_url: str
    categories: list[str] = field(default_factory=list)


def _parse_date(text: str) -> str:
    m = _DATE_RE.search(text)
    if not m:
        return date.today().isoformat()
    try:
        day, month, year = int(m.group(1)), int(m.group(2)), int(m.group(3))
        return date(year, month, day).isoformat()
    except (ValueError, OverflowError):
        return date.today().isoformat()


def fetch_page(url: str = INDEX_URL) -> str:
    req = Request(url, headers={
        "User-Agent": USER_AGENT,
        "Accept-Language": "el,en;q=0.5",
    })
    with urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
        return resp.read().decode("utf-8", errors="replace")


def parse_announcements(html: str) -> list[NewsItem]:
    """Parse the anakoinoseis-ht page and return NewsItem list."""
    items: list[NewsItem] = []
    seen: set[str] = set()

    for row_match in _VIEWS_ROW_RE.finditer(html):
        node_id = row_match.group(1)
        article_html = row_match.group(2)

        title_m = _NEWS_TITLE_RE.search(article_html)
        if not title_m:
            continue

        href = title_m.group(1).strip()
        title_raw = _strip_html(title_m.group(2))
        if not title_raw:
            continue

        body_m = _NEWS_BODY_RE.search(article_html)
        summary_raw = _strip_html(body_m.group(1))[:500] if body_m else ""

        published_at = _parse_date(title_raw)
        entry_id = f"ht-news-{node_id}"

        if entry_id in seen:
            continue
        seen.add(entry_id)

        full_url = f"https://www.hellenictrain.gr{href}" if href.startswith("/") else href

        title_en = _translate_en(title_raw)
        title_sq = _translate_sq(title_raw)
        title_it = _translate_it(title_raw)
        summary_en = _translate_en(summary_raw) if summary_raw else ""
        summary_sq = _translate_sq(summary_raw) if summary_raw else ""
        summary_it = _translate_it(summary_raw) if summary_raw else ""

        items.append(NewsItem(
            entry_id=entry_id,
            title=title_raw,
            title_en=title_en,
            title_sq=title_sq,
            title_it=title_it,
            summary=summary_raw,
            summary_en=summary_en,
            summary_sq=summary_sq,
            summary_it=summary_it,
            url=full_url,
            published_at=published_at,
            thumbnail_url="",
            categories=["Hellenic Train"],
        ))

        if len(items) >= MAX_ENTRIES:
            break

    return items


def upsert(conn: sqlite3.Connection, items: list[NewsItem]) -> int:
    cur = conn.cursor()
    cur.execute("BEGIN")
    try:
        for item in items:
            cats_json = json.dumps(item.categories, ensure_ascii=False)
            cur.execute(
                "INSERT INTO rail_news"
                "(id, title, title_en, title_sq, title_it, summary, summary_en, summary_sq, summary_it,"
                " url, published_at, thumbnail_url, categories)"
                " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)"
                " ON CONFLICT(id) DO UPDATE SET"
                " title=excluded.title, title_en=excluded.title_en, title_sq=excluded.title_sq,"
                " title_it=COALESCE(NULLIF(excluded.title_it, ''), rail_news.title_it),"
                " summary=excluded.summary, summary_en=excluded.summary_en, summary_sq=excluded.summary_sq,"
                " summary_it=COALESCE(NULLIF(excluded.summary_it, ''), rail_news.summary_it),"
                " url=excluded.url, published_at=excluded.published_at,"
                " thumbnail_url=excluded.thumbnail_url, categories=excluded.categories",
                (
                    item.entry_id, item.title, item.title_en, item.title_sq, item.title_it,
                    item.summary, item.summary_en, item.summary_sq, item.summary_it,
                    item.url, item.published_at,
                    item.thumbnail_url, cats_json,
                ),
            )
        cur.execute("COMMIT")
        return len(items)
    except Exception:
        cur.execute("ROLLBACK")
        raise


def run_once() -> int:
    """Scrape once and persist. Returns the number of news rows written."""
    try:
        html = fetch_page()
    except (URLError, TimeoutError) as e:
        with dbmod.connect() as conn:
            dbmod.migrate(conn)
            try:
                conn.execute(
                    "INSERT INTO scrape_log(source, ok, rows_written, error)"
                    " VALUES('hellenic_train', 0, 0, ?)",
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
                " VALUES('hellenic_train', 1, ?)",
                (count,),
            )
        except sqlite3.OperationalError:
            pass
    return count


if __name__ == "__main__":
    written = run_once()
    print(f"hellenic_train: wrote {written} item(s)")
