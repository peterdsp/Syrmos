"""Daily scraper for sidirodromikanea.blogspot.com (Greek rail news blog).

Fetches the Atom feed, filters entries by rail-relevant keywords
(Hellenic Train, Proastiakos, delays, disruptions, closures, fires),
translates titles and summaries from Greek to English and Albanian,
and stores them in the `rail_news` table for the Home news carousel.

Run: python3 -m syrmos_admin.scraper_rail_news
Cron: daily via systemd timer (see systemd/syrmos-scraper-rail-news.timer)
"""
from __future__ import annotations

import json
import re
import sqlite3
from dataclasses import dataclass, field
from datetime import date, datetime, timezone
from urllib.error import URLError
from urllib.request import Request, urlopen
from xml.etree import ElementTree as ET

from . import db as dbmod

USER_AGENT = "syrmos-rail-news-scraper/1.0 (+https://syrmos.peterdsp.dev)"
FEED_URL = "https://sidirodromikanea.blogspot.com/feeds/posts/default"
TIMEOUT_SECONDS = 30
MAX_ENTRIES = 50

ATOM_NS = "http://www.w3.org/2005/Atom"

RAIL_KEYWORDS_GR = (
    "hellenic train", "ελληνικα σιδηροδρομικα", "τρενα", "τραινοσε",
    "trainose", "προαστιακ", "proastiakos",
    "μετρο", "τραμ", "ησαπ", "στασυ", "stasy", "oasa",
    "σιδηροδρομ", "σιδηρόδρομ",
    "καθυστερ", "καθυστέρ",
    "ακυρωσ", "ακύρωσ", "ματαιωσ", "ματαίωσ",
    "διακοπ", "διακοπή",
    "κλειστ", "κλειστό",
    "πυρκαγ", "φωτια", "φωτιά",
    "απεργ", "απεργία",
    "κυκλοφοριακ",
    "δρομολογ", "δρομολόγ",
    "σταθμ",
    "επιβατ",
    "αμαξοστοιχ",
    "λεωφορει", "λεωφορεί",
    "intercity",
    "delay", "disruption", "cancellation",
    "closure", "fire", "strike",
    "railway", "railroad", "rail",
    "passengers",
)

ANTI_KEYWORDS = (
    "tender", "procurement", "job position", "vacancy",
    "annual report", "balance sheet",
    "photo competition", "art competition",
    "privacy policy", "cookies",
    "διαγωνισμ", "προμηθει", "θεση εργασιας",
    "ισολογισμ", "πολιτικη απορρητου",
)

_GREEK_LETTER_RE = re.compile(r"[Ͱ-Ͽἀ-῿]")


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


def is_rail_relevant(text: str) -> bool:
    lowered = text.lower()
    rail_hits = sum(1 for k in RAIL_KEYWORDS_GR if k in lowered)
    anti_hits = sum(1 for k in ANTI_KEYWORDS if k in lowered)
    if rail_hits == 0:
        return False
    return rail_hits > anti_hits


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


def _text(elem: ET.Element | None) -> str:
    if elem is None:
        return ""
    return (elem.text or "").strip()


def _parse_feed(xml_bytes: bytes) -> list[NewsItem]:
    root = ET.fromstring(xml_bytes)
    items: list[NewsItem] = []

    for entry in root.findall(f"{{{ATOM_NS}}}entry")[:MAX_ENTRIES]:
        entry_id = _text(entry.find(f"{{{ATOM_NS}}}id"))
        title = _text(entry.find(f"{{{ATOM_NS}}}title"))

        summary_elem = entry.find(f"{{{ATOM_NS}}}summary")
        if summary_elem is None:
            summary_elem = entry.find(f"{{{ATOM_NS}}}content")
        raw_summary = _text(summary_elem)
        summary = _strip_html(raw_summary)[:500]

        url = ""
        for link in entry.findall(f"{{{ATOM_NS}}}link"):
            if link.get("rel") == "alternate":
                url = link.get("href", "")
                break

        published = _text(entry.find(f"{{{ATOM_NS}}}published"))
        updated = _text(entry.find(f"{{{ATOM_NS}}}updated"))
        pub_date = published or updated

        thumbnail = ""
        media_ns = "http://search.yahoo.com/mrss/"
        thumb_elem = entry.find(f"{{{media_ns}}}thumbnail")
        if thumb_elem is not None:
            thumbnail = thumb_elem.get("url", "")

        categories = []
        for cat in entry.findall(f"{{{ATOM_NS}}}category"):
            term = cat.get("term", "")
            if term:
                categories.append(term)

        classifier_text = f"{title} {summary}".lower()
        if not is_rail_relevant(classifier_text):
            continue

        title_en = _translate_en(title)
        title_sq = _translate_sq(title)
        summary_en = _translate_en(summary) if summary else ""
        summary_sq = _translate_sq(summary) if summary else ""

        items.append(NewsItem(
            entry_id=entry_id or url,
            title=title,
            title_en=title_en,
            title_sq=title_sq,
            summary=summary,
            summary_en=summary_en,
            summary_sq=summary_sq,
            url=url,
            published_at=pub_date,
            thumbnail_url=thumbnail,
            categories=categories,
        ))

    return items


_HTML_TAG_RE = re.compile(r"<[^>]+>")


def _strip_html(text: str) -> str:
    return _HTML_TAG_RE.sub("", text).strip()


def fetch_feed(url: str = FEED_URL) -> bytes:
    req = Request(url, headers={"User-Agent": USER_AGENT})
    with urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
        return resp.read()


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
        xml_bytes = fetch_feed()
    except (URLError, TimeoutError) as e:
        with dbmod.connect() as conn:
            dbmod.migrate(conn)
            try:
                conn.execute(
                    "INSERT INTO scrape_log(source, ok, rows_written, error)"
                    " VALUES('rail_news', 0, 0, ?)",
                    (f"fetch feed failed: {e}",),
                )
            except sqlite3.OperationalError:
                pass
        return 0

    items = _parse_feed(xml_bytes)

    with dbmod.connect() as conn:
        dbmod.migrate(conn)
        count = upsert(conn, items)
        try:
            conn.execute(
                "INSERT INTO scrape_log(source, ok, rows_written)"
                " VALUES('rail_news', 1, ?)",
                (count,),
            )
        except sqlite3.OperationalError:
            pass
    return count


if __name__ == "__main__":
    written = run_once()
    print(f"rail_news: wrote {written} item(s)")
