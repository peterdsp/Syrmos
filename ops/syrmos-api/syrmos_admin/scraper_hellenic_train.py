"""Daily scraper for hellenictrain.gr/important-information.

Fetches the Important Information page, extracts service disruptions
grouped by line category and issue type, translates from Greek to
English and Albanian, and stores them in the `rail_news` table for the
Home news carousel.

Run: python3 -m syrmos_admin.scraper_hellenic_train
Cron: daily via systemd timer (see systemd/syrmos-scraper-hellenic-train.timer)
"""
from __future__ import annotations

import hashlib
import json
import re
import sqlite3
from dataclasses import dataclass, field
from datetime import date, datetime
from urllib.error import URLError
from urllib.request import Request, urlopen

from . import db as dbmod

USER_AGENT = "syrmos-ht-scraper/1.0 (+https://syrmos.peterdsp.dev)"
INDEX_URL = "https://www.hellenictrain.gr/important-information"
TIMEOUT_SECONDS = 30
MAX_ENTRIES = 50

_GREEK_LETTER_RE = re.compile(r"[Ͱ-Ͽἀ-῿]")
_HTML_TAG_RE = re.compile(r"<[^>]+>")

# Parse the date header: DD/MM/YYYY
_DATE_RE = re.compile(r"(\d{1,2})/(\d{1,2})/(\d{4})")

# Parse itinerary groups: class="itinerary-group <slug>"
_ITINERARY_GROUP_RE = re.compile(
    r'<div\s+class="itinerary-group\s+([^"]+)">(.*?)</div>\s*(?=<div\s+class="itinerary-group|$)',
    re.DOTALL,
)

# Parse issue-type groups within an itinerary group
_ISSUE_GROUP_RE = re.compile(
    r'<div\s+class="issue-type-group">(.*?)</div>\s*</div>',
    re.DOTALL,
)

# Parse the itinerary title (line category)
_ITINERARY_TITLE_RE = re.compile(
    r'<div\s+class="itinerary-title">(.*?)</div>',
    re.DOTALL,
)

# Parse the issue type title
_ISSUE_TYPE_TITLE_RE = re.compile(
    r'<div\s+class="issue-type-title">(.*?)</div>',
    re.DOTALL,
)

# Parse result items
_RESULT_ITEM_RE = re.compile(
    r'<li\s+class="result-item">\s*'
    r'<div\s+class="created-time">(.*?)</div>\s*'
    r'<div\s+class="result-content">(.*?)</div>',
    re.DOTALL,
)

# Parse expiration-date
_EXPIRATION_DATE_RE = re.compile(
    r'<div\s+class="expiration-date">(.*?)</div>',
    re.DOTALL,
)


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


def _parse_date(text: str) -> str:
    """Extract DD/MM/YYYY and return ISO date string."""
    m = _DATE_RE.search(text)
    if not m:
        return date.today().isoformat()
    try:
        day, month, year = int(m.group(1)), int(m.group(2)), int(m.group(3))
        return date(year, month, day).isoformat()
    except (ValueError, OverflowError):
        return date.today().isoformat()


def _make_id(line_cat: str, issue_type: str, content: str) -> str:
    """Create a stable entry ID from the content."""
    raw = f"{line_cat}|{issue_type}|{content[:100]}"
    digest = hashlib.md5(raw.encode("utf-8")).hexdigest()[:12]
    return f"ht-info-{digest}"


def fetch_page(url: str = INDEX_URL) -> str:
    req = Request(url, headers={
        "User-Agent": USER_AGENT,
        "Accept-Language": "el,en;q=0.5",
    })
    with urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
        return resp.read().decode("utf-8", errors="replace")


def parse_important_info(html: str) -> list[NewsItem]:
    """Parse the important-information page and return NewsItem list."""
    items: list[NewsItem] = []
    seen: set[str] = set()

    # Extract date
    date_match = _EXPIRATION_DATE_RE.search(html)
    published_at = _parse_date(date_match.group(1) if date_match else "")

    # Find all itinerary-group sections by splitting on the class marker
    group_starts = list(re.finditer(
        r'<div\s+class="itinerary-group\s+([^"]+)">',
        html,
    ))

    for gi, group_match in enumerate(group_starts):
        group_slug = group_match.group(1).strip()
        start = group_match.end()
        end = group_starts[gi + 1].start() if gi + 1 < len(group_starts) else len(html)
        group_html = html[start:end]

        # Find all issue-type-group sections within this group
        issue_starts = list(re.finditer(
            r'<div\s+class="issue-type-group">',
            group_html,
        ))

        for ii, issue_match in enumerate(issue_starts):
            istart = issue_match.end()
            iend = issue_starts[ii + 1].start() if ii + 1 < len(issue_starts) else len(group_html)
            issue_html = group_html[istart:iend]

            # Extract line category and issue type
            itinerary_m = _ITINERARY_TITLE_RE.search(issue_html)
            issue_type_m = _ISSUE_TYPE_TITLE_RE.search(issue_html)
            line_category = _strip_html(itinerary_m.group(1)) if itinerary_m else group_slug.replace("_", " ").title()
            issue_type = _strip_html(issue_type_m.group(1)) if issue_type_m else ""

            # Extract individual result items
            for result_m in _RESULT_ITEM_RE.finditer(issue_html):
                time_str = _strip_html(result_m.group(1))
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
                summary_en = _translate_en(summary) if summary else ""
                summary_sq = _translate_sq(summary) if summary else ""

                items.append(NewsItem(
                    entry_id=entry_id,
                    title=title,
                    title_en=title_en,
                    title_sq=title_sq,
                    summary=summary,
                    summary_en=summary_en,
                    summary_sq=summary_sq,
                    url=INDEX_URL,
                    published_at=published_at,
                    thumbnail_url="",
                    categories=["Hellenic Train", line_category],
                ))

                if len(items) >= MAX_ENTRIES:
                    return items

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

    items = parse_important_info(html)

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
