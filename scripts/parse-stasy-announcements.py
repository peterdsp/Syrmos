"""Scrape STASY's homepage status badge + news/announcements page.

Two distinct signals we surface to the apps:

1. Homepage service-status badge (e.g. "Κανονική Λειτουργία" / "Trains until
   21:40 due to ..."). Lives on https://www.stasy.gr/ and changes only on
   disruption. We scrape it because there's no public STASY API for service
   status.

2. The announcements list at /νέα-ανακοινώσεις/ — operational notices,
   tenders, hires, etc. We surface them in the iOS Home tab as a small
   carousel with read-more links.

Output: assets/stasy-announcements/parsed/announcements.jsonl
"""
from __future__ import annotations

import json
import re
import sys
import unicodedata
import urllib.parse
import urllib.request
from datetime import datetime
from pathlib import Path

from bs4 import BeautifulSoup


def _fold(s: str) -> str:
    """Lowercase + strip accents so keyword matching ignores tonos/dialytika.
    Python's str.lower() already maps uppercase Σ to word-final ς correctly."""
    nkfd = unicodedata.normalize("NFD", s).lower()
    return "".join(c for c in nkfd if not unicodedata.combining(c))

ROOT = Path(__file__).resolve().parent.parent
CACHE_DIR = ROOT / "assets" / "stasy-announcements"
OUT_DIR = CACHE_DIR / "parsed"
OUT_DIR.mkdir(parents=True, exist_ok=True)

HOMEPAGE_URL = "https://www.stasy.gr/"
NEWS_URL = "https://www.stasy.gr/νέα-ανακοινώσεις/"
USER_AGENT = "syrmos-stasy-announcements/1.0 (+https://syrmos.peterdsp.dev)"


def fetch(url: str, cache_name: str) -> str:
    cached = CACHE_DIR / cache_name
    parsed = urllib.parse.urlsplit(url)
    safe_path = urllib.parse.quote(parsed.path, safe="/%")
    encoded = urllib.parse.urlunsplit(
        (parsed.scheme, parsed.netloc, safe_path, parsed.query, parsed.fragment)
    )
    req = urllib.request.Request(encoded, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=30) as r:
        body = r.read().decode("utf-8", errors="ignore")
    cached.parent.mkdir(parents=True, exist_ok=True)
    cached.write_text(body)
    return body


# The homepage shows a small status pill near the top of every page. When
# service is normal it reads "Κανονική Λειτουργία" / "Normal Operation".
# When a disruption is active STASY swaps in a free-form message, often
# something like "Δρομολόγια έως 21:40" ("Trains until 21:40 ...").
def extract_status(html: str) -> dict:
    soup = BeautifulSoup(html, "html.parser")
    for tag in soup(["script", "style", "noscript"]):
        tag.decompose()
    text = soup.get_text(" ", strip=True)
    # Prefer an explicit "Δρομολόγια έως HH:MM" / "service until HH:MM" hit.
    m = re.search(r"(?:Δρομολόγια|Λειτουργία)[^.]{0,40}(?:έως|μέχρι|until)\s*(\d{1,2}[:.]\d{2})", text, flags=re.I)
    if m:
        return {
            "status": "alert",
            "raw_message": m.group(0).strip()[:300],
            "service_until": _normalize_hhmm(m.group(1)),
        }
    if "Κανονική Λειτουργία" in text or "Normal Operation" in text:
        return {
            "status": "normal",
            "raw_message": "Κανονική Λειτουργία",
            "service_until": None,
        }
    # Couldn't detect anything; surface that explicitly so the apps don't
    # show a stale status pill.
    return {"status": "unknown", "raw_message": "", "service_until": None}


def _normalize_hhmm(s: str) -> str:
    s = s.replace(".", ":")
    h, m = s.split(":")
    return f"{int(h):02d}:{int(m):02d}"


# STASY's news page mixes operational notices (the only ones our users care
# about) with cultural events, chamber-of-commerce announcements, tenders,
# hires, etc. Drop everything that doesn't mention the network. `serviceAlert`
# items always pass — those are the Έκτακτες Ανακοινώσεις banner posts.
# Operational keywords. All compared in accent-folded lowercase form.
TRANSIT_KEYWORDS = (
    # Greek
    "μετρο", "τραμ", "προαστιακ",
    "γραμμη 1", "γραμμη 2", "γραμμη 3",
    "γραμμες", "γραμμων",
    "σταθμ", "δρομολογ",
    "καθυστερ", "αναστολ", "διακοπ",
    "κυκλοφορ", "συρμ", "αμαξοστοιχ",
    "εισιτηρ", "ωραρι",
    "δικτυο", "συγκοινων",
    "οασα", "στασυ", "στα.συ",
    # English / transliterated
    "metro", "tram", "suburban",
    "station", "line 1", "line 2", "line 3",
    "schedule", "timetable", "delay", "service",
    "fare", "ticket", "network",
)

# HR postings, tenders, and cultural events that happen to mention "σταθμό",
# "ΣΤΑ.ΣΥ", or "μετρό" but aren't operational. All accent-folded.
EXCLUDE_TERMS = (
    # Hiring / personnel competitions
    "πληρωσης θεσεων", "αγγελια πληρωσης",
    "θεσεων προσωπικου",
    "πινακες καταταξης", "πινακων προκηρυξης",
    "οριστικων πινακων", "προσωρινων πινακων",
    "δημοσιευσης", "προκηρυξης", "προκηρυξη",
    "οδηγων συρμων", "εκδοτων εισιτηριων",
    "σταθμαρχων", "ελεγκτων κομιστρου",
    "ορθη επαναληψη", "επανακοινοποιησης",
    # Procurement / venue rental tenders
    "προσκληση εκδηλωσης ενδιαφεροντος",
    "μισθωση χωρων", "διαθεση χωρων",
    "χωρου πολλαπλων χρησεων", "πολλαπλων χρησεων",
    "αντικειμενο την", "επαναφορτισης ηλεκτρονικων",
    # Cultural events held at metro stations
    "πανηγυρι", "γευσεις απο",
    "χριστουγεννιατικη εκθεση",
    "ιδρυμα βιβλιου", "ελιβιπ",
    "καρδια της ελλαδας",
)


def _is_transit_related(item: dict) -> bool:
    if item.get("category") == "serviceAlert":
        return True
    blob = _fold(" ".join((item.get("title", ""), item.get("summary", ""), item.get("url", ""))))
    return any(k in blob for k in TRANSIT_KEYWORDS)


def _is_excluded(item: dict) -> bool:
    blob = _fold(" ".join((item.get("title", ""), item.get("summary", ""))))
    return any(k in blob for k in EXCLUDE_TERMS)


def extract_news(html: str, limit: int = 30) -> list[dict]:
    """The STASY news page is built with Elementor, which renders each post
    title in an `entry-title` (or `elementor-post__title`) wrapper and the
    canonical link a few ancestors up. We pair them by walking up the DOM
    from each unique title, dedupe by URL, filter to transit-relevant
    content, and emit at most `limit` items."""
    soup = BeautifulSoup(html, "html.parser")
    for tag in soup(["script", "style", "noscript"]):
        tag.decompose()
    raw: list[dict] = []
    seen_urls: set[str] = set()
    title_nodes = soup.find_all(class_=re.compile(r"entry-title|elementor-post__title"))
    for t in title_nodes:
        title = t.get_text(" ", strip=True)
        if not title or len(title) < 5:
            continue
        # Walk up to find an ancestor with a stasy.gr link.
        cur = t
        href = ""
        for _ in range(8):
            cur = cur.parent
            if cur is None:
                break
            link = cur.find("a", href=True)
            if link and link["href"].startswith("http"):
                href = link["href"]
                break
        if not href or href in seen_urls:
            continue
        seen_urls.add(href)
        # Try to find a sibling paragraph/summary near the title for context.
        summary_node = None
        if cur is not None:
            summary_node = cur.find("p")
        summary = (summary_node.get_text(" ", strip=True) if summary_node else "")[:400]
        # Date hint: look for a date-y class within the wrapper.
        date_str = ""
        if cur is not None:
            for sel in (cur.find("time"),
                        cur.find(class_=re.compile(r"date|entry-date|published"))):
                if sel:
                    date_str = sel.get_text(" ", strip=True)
                    if date_str:
                        break
        cat = "general"
        if cur is not None:
            cur_text = cur.get_text(" ", strip=True)[:300]
            if "Έκτακτες" in cur_text:
                cat = "serviceAlert"
        raw.append({
            "id": _slug(href),
            "title": title[:200],
            "summary": summary,
            "url": href,
            "date": date_str,
            "category": cat,
        })
    filtered = [
        it for it in raw
        if it.get("category") == "serviceAlert"
        or (_is_transit_related(it) and not _is_excluded(it))
    ]
    return filtered[:limit]


def _slug(url: str) -> str:
    last = url.rstrip("/").rsplit("/", 1)[-1]
    return urllib.parse.unquote(last)[:80] or url[-80:]


def main() -> None:
    home_html = fetch(HOMEPAGE_URL, "homepage.html")
    status = extract_status(home_html)
    print(f"status: {status['status']} ({status['raw_message']!r})")

    news_html = fetch(NEWS_URL, "news.html")
    items = extract_news(news_html)
    print(f"announcements: {len(items)}")

    payload = {
        "scrapedAt": datetime.utcnow().isoformat(timespec="seconds") + "Z",
        "status": status,
        "announcements": items,
    }
    out = OUT_DIR / "announcements.jsonl"
    with out.open("w") as f:
        f.write(json.dumps(payload, ensure_ascii=False) + "\n")
    print(f"wrote {out.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
