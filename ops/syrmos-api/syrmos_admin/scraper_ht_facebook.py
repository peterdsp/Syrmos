"""Daily scraper for Hellenic Train's Facebook page.

Fetches recent posts from the Hellenic Train Facebook page via the
mobile site, extracts post text and dates, translates from Greek to
English and Albanian, and stores them in the `rail_news` table.

Facebook page ID: 100075790340469

Run: python3 -m syrmos_admin.scraper_ht_facebook
Cron: daily via systemd timer (see systemd/syrmos-scraper-ht-facebook.timer)
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

USER_AGENT = (
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
    "AppleWebKit/605.1.15 (KHTML, like Gecko) "
    "Version/17.0 Mobile/15E148 Safari/604.1"
)
PAGE_URL = "https://m.facebook.com/hellenictrain/posts/"
TIMEOUT_SECONDS = 30
MAX_ENTRIES = 20

_HTML_TAG_RE = re.compile(r"<[^>]+>")
_GREEK_LETTER_RE = re.compile(r"[Ͱ-Ͽἀ-῿]")
_WHITESPACE_RE = re.compile(r"\s+")

# Match story links to extract post IDs
_STORY_LINK_RE = re.compile(
    r'href="(/story\.php\?story_fbid=[^"]+|/[^/]+/posts/[^"]+)"',
)

# Match post content blocks on the mobile page
_POST_TEXT_RE = re.compile(
    r'<div[^>]*data-ft[^>]*>(.*?)</div>\s*(?:<div|<footer)',
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
    cleaned = _HTML_TAG_RE.sub(" ", text)
    return _WHITESPACE_RE.sub(" ", cleaned).strip()


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


def _make_id(text: str) -> str:
    digest = hashlib.md5(text[:200].encode("utf-8")).hexdigest()[:12]
    return f"ht-fb-{digest}"


def fetch_page(url: str = PAGE_URL) -> str:
    req = Request(url, headers={
        "User-Agent": USER_AGENT,
        "Accept-Language": "el,en;q=0.5",
        "Accept": "text/html,application/xhtml+xml",
    })
    with urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
        return resp.read().decode("utf-8", errors="replace")


def parse_posts(html: str) -> list[NewsItem]:
    """Parse the Facebook mobile page and extract posts."""
    items: list[NewsItem] = []
    seen: set[str] = set()
    today = date.today().isoformat()

    # Split by article or story boundaries
    # Facebook mobile wraps each post in a <article> or <div role="article">
    article_splits = re.split(r'<(?:article|div\s+role="article")', html)

    for chunk in article_splits[1:]:  # skip the first (before any article)
        # Extract text content from the post
        # Look for the main text div
        text_parts = []
        for text_m in re.finditer(r'<(?:p|span|div)[^>]*>(.*?)</(?:p|span|div)>', chunk, re.DOTALL):
            part = _strip_html(text_m.group(1))
            if len(part) > 20 and _has_greek(part):
                text_parts.append(part)

        if not text_parts:
            # Fallback: just grab all visible text
            full_text = _strip_html(chunk)
            if len(full_text) > 40 and _has_greek(full_text):
                text_parts = [full_text[:500]]

        if not text_parts:
            continue

        content = " ".join(text_parts)[:500]

        # Extract post URL if available
        post_url = "https://www.facebook.com/hellenictrain"
        link_m = _STORY_LINK_RE.search(chunk)
        if link_m:
            path = link_m.group(1)
            if path.startswith("/"):
                post_url = f"https://m.facebook.com{path}"

        entry_id = _make_id(content)
        if entry_id in seen:
            continue
        seen.add(entry_id)

        # Use first 80 chars as title, rest as summary
        title = content[:80].rstrip()
        if len(content) > 80:
            title = title.rsplit(" ", 1)[0] + "..."
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
            url=post_url,
            published_at=today,
            thumbnail_url="",
            categories=["Hellenic Train", "Facebook"],
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
                    " VALUES('ht_facebook', 0, 0, ?)",
                    (f"fetch page failed: {e}",),
                )
            except sqlite3.OperationalError:
                pass
        return 0

    items = parse_posts(html)

    with dbmod.connect() as conn:
        dbmod.migrate(conn)
        count = upsert(conn, items)
        try:
            conn.execute(
                "INSERT INTO scrape_log(source, ok, rows_written)"
                " VALUES('ht_facebook', 1, ?)",
                (count,),
            )
        except sqlite3.OperationalError:
            pass
    return count


if __name__ == "__main__":
    written = run_once()
    print(f"ht_facebook: wrote {written} item(s)")
