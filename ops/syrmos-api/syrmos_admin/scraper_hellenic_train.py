"""Daily scraper for hellenictrain.gr/anakoinoseis-ht (announcements page).

Fetches the HTML announcements index, extracts announcement links, titles,
dates and summaries, translates from Greek to English and Albanian, and
stores them in the `rail_news` table for the Home news carousel.

Run: python3 -m syrmos_admin.scraper_hellenic_train
Cron: daily via systemd timer (see systemd/syrmos-scraper-hellenic-train.timer)
"""
from __future__ import annotations

import json
import re
import sqlite3
from dataclasses import dataclass, field
from datetime import date, datetime
from urllib.error import URLError
from urllib.request import Request, urlopen

from . import db as dbmod

USER_AGENT = "syrmos-ht-scraper/1.0 (+https://syrmos.peterdsp.dev)"
INDEX_URL = "https://www.hellenictrain.gr/anakoinoseis-ht"
TIMEOUT_SECONDS = 30
MAX_ENTRIES = 50

_GREEK_LETTER_RE = re.compile(r"[Ͱ-Ͽἀ-῿]")
_HTML_TAG_RE = re.compile(r"<[^>]+>")

# Match announcement links: <a href="/news/anakoinosi-...">TITLE</a>
_ANNOUNCE_LINK_RE = re.compile(
    r'<a[^>]+href=["\'](/news/[^"\']+)["\'][^>]*>(.*?)</a>',
    re.IGNORECASE | re.DOTALL,
)

# Extract date from title text: "ΑΝΑΚΟΙΝΩΣΗ DD/MM/YYYY"
_TITLE_DATE_RE = re.compile(r"(\d{1,2})/(\d{1,2})/(\d{4})")


def _has_greek(text: str) -> bool:
    return bool(_GREEK_LETTER_RE.search(text))


def _translate(text: str, target: str) -> str:
    text = text.strip()
    if not text or not _has_greek(text):
        return text
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


def _strip_html(text: str) -> str:
    return _HTML_TAG_RE.sub("", text).strip()


@dataclass
class NewsItem:
    entry_id: str
    title: str
    title_en: str
    title_sq: str
    summary: str
    summary_en: str
    summary_sq: str
    url: str
    published_at: str
    thumbnail_url: str
    categories: list[str] = field(default_factory=list)


def _slug_from_path(path: str) -> str:
    """Extract a stable slug from the URL path, e.g. /news/anakoinosi-2025-foo
    becomes anakoinosi-2025-foo."""
    return path.rstrip("/").rsplit("/", 1)[-1]


def _parse_date_from_title(title: str) -> str:
    """Extract DD/MM/YYYY from the title and return ISO date string.
    Falls back to empty string if no date found."""
    m = _TITLE_DATE_RE.search(title)
    if not m:
        return ""
    try:
        day, month, year = int(m.group(1)), int(m.group(2)), int(m.group(3))
        return date(year, month, day).isoformat()
    except (ValueError, OverflowError):
        return ""


def fetch_page(url: str = INDEX_URL) -> str:
    req = Request(url, headers={
        "User-Agent": USER_AGENT,
        "Accept-Language": "el,en;q=0.5",
    })
    with urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
        return resp.read().decode("utf-8", errors="replace")


def parse_announcements(html: str) -> list[NewsItem]:
    """Parse the announcements index page and return NewsItem list."""
    items: list[NewsItem] = []
    seen: set[str] = set()

    for match in _ANNOUNCE_LINK_RE.finditer(html):
        path = match.group(1)
        raw_title = _strip_html(match.group(2)).strip()

        if not path.startswith("/news/"):
            continue
        if not raw_title:
            continue

        slug = _slug_from_path(path)
        entry_id = f"ht-{slug}"

        if entry_id in seen:
            continue
        seen.add(entry_id)

        url = f"https://www.hellenictrain.gr{path}"
        published_at = _parse_date_from_title(raw_title)

        # Extract summary: look for text after this link up to the next
        # announcement link or "read more" marker.
        link_end = match.end()
        next_link = _ANNOUNCE_LINK_RE.search(html, link_end)
        chunk_end = next_link.start() if next_link else link_end + 2000
        chunk = html[link_end:chunk_end]
        summary = _strip_html(chunk).strip()
        # Remove "read more" tail
        for marker in ("Διαβάστε περισσότερα", "Read more"):
            idx = summary.find(marker)
            if idx >= 0:
                summary = summary[:idx].strip()
        summary = summary[:500]

        title_en = _translate_en(raw_title)
        title_sq = _translate_sq(raw_title)
        summary_en = _translate_en(summary) if summary else ""
        summary_sq = _translate_sq(summary) if summary else ""

        items.append(NewsItem(
            entry_id=entry_id,
            title=raw_title,
            title_en=title_en,
            title_sq=title_sq,
            summary=summary,
            summary_en=summary_en,
            summary_sq=summary_sq,
            url=url,
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
                "(id, title, title_en, title_sq, summary, summary_en, summary_sq,"
                " url, published_at, thumbnail_url, categories)"
                " VALUES(?,?,?,?,?,?,?,?,?,?,?)"
                " ON CONFLICT(id) DO UPDATE SET"
                " title=excluded.title, title_en=excluded.title_en, title_sq=excluded.title_sq,"
                " summary=excluded.summary, summary_en=excluded.summary_en, summary_sq=excluded.summary_sq,"
                " url=excluded.url, published_at=excluded.published_at,"
                " thumbnail_url=excluded.thumbnail_url, categories=excluded.categories",
                (
                    item.entry_id, item.title, item.title_en, item.title_sq,
                    item.summary, item.summary_en, item.summary_sq,
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
