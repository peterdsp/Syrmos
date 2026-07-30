"""Bi-hourly scraper for thessmetro.gr announcements page.

Fetches the HTML announcements page (Greek-character URL, requires
percent-encoding and a realistic browser User-Agent to avoid 403),
extracts dates and titles, translates from Greek to English and Albanian,
and stores them in the `announcements` table as service alerts for the
Thessaloniki Metro feed.

Run: python3 -m syrmos_admin.scraper_thessmetro
Cron: every 2 hours via systemd timer (see systemd/syrmos-scraper-thessmetro.timer)
"""
from __future__ import annotations

import html as html_mod
import json
import re
import sqlite3
from dataclasses import dataclass, field
from datetime import date, datetime
from urllib.error import URLError
from urllib.parse import quote, urlparse, urlunparse
from urllib.request import Request, urlopen

from . import db as dbmod

# The site blocks non-browser User-Agents with a 403
USER_AGENT = (
    "Mozilla/5.0 (X11; Linux aarch64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)
INDEX_URL = "https://www.thessmetro.gr/νέα-ανακοινώσεις/"
TIMEOUT_SECONDS = 30
MAX_ENTRIES = 50

_GREEK_LETTER_RE = re.compile(r"[Ͱ-Ͽἀ-῿]")
_HTML_TAG_RE = re.compile(r"<[^>]+>")

# Date pattern: DD.MM.YYYY
_DOT_DATE_RE = re.compile(r"(\d{1,2})\.(\d{1,2})\.(\d{4})")

# Match article headings/links. ThessMetro renders announcements as
# titled cards with dates and text content.
_HEADING_RE = re.compile(
    r'<(?:h[1-4]|a)[^>]*>(.*?)</(?:h[1-4]|a)>',
    re.IGNORECASE | re.DOTALL,
)
_LINK_RE = re.compile(
    r'<a[^>]+href=["\'](https?://[^"\']+)["\'][^>]*>(.*?)</a>',
    re.IGNORECASE | re.DOTALL,
)


def _ascii_url(url: str) -> str:
    """Percent-encode Greek characters in the URL path so urllib accepts it."""
    parsed = urlparse(url)
    return urlunparse((
        parsed.scheme,
        parsed.netloc.encode("idna").decode("ascii"),
        quote(parsed.path, safe="/%"),
        parsed.params,
        quote(parsed.query, safe="=&%"),
        parsed.fragment,
    ))


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
    return html_mod.unescape(_HTML_TAG_RE.sub("", text)).strip()


def _parse_dot_date(text: str) -> str:
    """Parse DD.MM.YYYY from text and return ISO date string.
    Falls back to empty string if no date found."""
    m = _DOT_DATE_RE.search(text)
    if not m:
        return ""
    try:
        day = int(m.group(1))
        month = int(m.group(2))
        year = int(m.group(3))
        return date(year, month, day).isoformat()
    except (ValueError, OverflowError):
        return ""


def _make_slug(title: str, idx: int) -> str:
    """Create a URL-safe slug from the title text."""
    # Transliterate to ASCII-safe characters
    slug = re.sub(r"[^\w\s-]", "", title.lower())
    slug = re.sub(r"[\s_]+", "-", slug).strip("-")
    if not slug:
        slug = f"item-{idx}"
    return slug[:80]


@dataclass
class AnnouncementItem:
    slug: str
    title: str
    title_en: str
    title_sq: str
    summary: str
    summary_en: str
    summary_sq: str
    url: str
    publish_date: str
    valid_from: str


def fetch_page(url: str = INDEX_URL) -> str:
    encoded_url = _ascii_url(url)
    req = Request(encoded_url, headers={
        "User-Agent": USER_AGENT,
        "Accept-Language": "el,en;q=0.5",
    })
    with urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
        return resp.read().decode("utf-8", errors="replace")


def parse_announcements(html: str) -> list[AnnouncementItem]:
    """Parse the announcements page and return items.

    ThessMetro renders announcements as blocks with a DD.MM.YYYY date
    and a title. We split the page by date occurrences and extract the
    title text that follows each date."""
    items: list[AnnouncementItem] = []
    seen: set[str] = set()

    # Strategy: find all DD.MM.YYYY dates in the page and extract the
    # surrounding text as title/summary.
    for match in _DOT_DATE_RE.finditer(html):
        try:
            day = int(match.group(1))
            month = int(match.group(2))
            year = int(match.group(3))
            publish_date = date(year, month, day).isoformat()
        except (ValueError, OverflowError):
            continue

        # Extract context around the date to find the title
        context_start = max(0, match.start() - 500)
        context_end = min(len(html), match.end() + 1000)
        after_text = _strip_html(html[match.end():context_end]).strip()
        before_text = _strip_html(html[context_start:match.start()]).strip()

        # The title is typically the text following the date
        lines = [ln.strip() for ln in after_text.split("\n") if ln.strip()]
        title = lines[0] if lines else ""

        # If the title looks like just another date or is too short,
        # try the text before the date
        if not title or len(title) < 5:
            before_lines = [ln.strip() for ln in before_text.split("\n") if ln.strip()]
            title = before_lines[-1] if before_lines else ""

        if not title or len(title) < 3:
            continue

        slug = _make_slug(title, len(items))
        item_id = f"thessmetro-{slug}"

        if item_id in seen:
            continue
        seen.add(item_id)

        # Look for a link near this date
        nearby_html = html[context_start:context_end]
        link_match = _LINK_RE.search(nearby_html)
        url = link_match.group(1) if link_match else _ascii_url(INDEX_URL)

        summary = " ".join(lines[:3])[:500] if len(lines) > 1 else ""
        valid_from = publish_date

        title_en = _translate_en(title)
        title_sq = _translate_sq(title)
        summary_en = _translate_en(summary) if summary else ""
        summary_sq = _translate_sq(summary) if summary else ""

        items.append(AnnouncementItem(
            slug=item_id,
            title=title,
            title_en=title_en,
            title_sq=title_sq,
            summary=summary,
            summary_en=summary_en,
            summary_sq=summary_sq,
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
            sort_order = 200 + idx
            # Try the schema with title_sq + summary_sq first; fall back
            # to the pre-Sq column set so an older Pi behind on migrations
            # still ingests rows.
            try:
                cur.execute(
                    "INSERT INTO announcements"
                    "(id, title, title_en, title_sq, summary, summary_en, summary_sq,"
                    " url, date, category, sort_order,"
                    " affected_lines, severity, valid_from, valid_until)"
                    " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
                    " ON CONFLICT(id) DO UPDATE SET"
                    " title=excluded.title, title_en=excluded.title_en, title_sq=excluded.title_sq,"
                    " summary=excluded.summary, summary_en=excluded.summary_en, summary_sq=excluded.summary_sq,"
                    " url=excluded.url, category=excluded.category, sort_order=excluded.sort_order,"
                    " affected_lines=excluded.affected_lines, severity=excluded.severity,"
                    " valid_from=excluded.valid_from, valid_until=excluded.valid_until",
                    (
                        item.slug, item.title, item.title_en, item.title_sq,
                        item.summary, item.summary_en, item.summary_sq,
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
                    " VALUES('thessmetro', 0, 0, ?)",
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
                " VALUES('thessmetro', 1, ?)",
                (count,),
            )
        except sqlite3.OperationalError:
            pass
    return count


if __name__ == "__main__":
    written = run_once()
    print(f"thessmetro: wrote {written} item(s)")
