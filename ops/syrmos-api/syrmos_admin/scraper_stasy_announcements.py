"""Hourly scraper for stasy.gr/ανακοινώσεις/.

Pulls the announcements index page, follows each detail page, classifies
the item (info | warning | closure), extracts which lines it affects and
any valid-from / valid-until dates, and stores both:

- one row in `announcements` (so the apps' STASY feed picks it up)
- one row per closed (line_id, date) in `date_overrides` with a
  ``{"closed": true, "reason": ...}`` payload, so the projector and
  /api/live-positions can return zero departures / zero trains for the
  closed slice.

The classification uses keyword matching on the Greek title + detail
copy. We deliberately keep the keyword set narrow — false negatives
(item missed) are fine because the title still shows up in-app, but
false positives that mark a normal-service day as closed would be a
real problem.

Run: python3 -m syrmos_admin.scraper_stasy_announcements
Cron: hourly via systemd timer (see systemd/syrmos-scraper-stasy.timer)
"""
from __future__ import annotations

import json
import re
import sqlite3
from dataclasses import dataclass, field
from datetime import date, datetime
from html.parser import HTMLParser
from urllib.error import URLError
from urllib.parse import quote, urlparse, urlunparse
from urllib.request import Request, urlopen

from . import db as dbmod

USER_AGENT = "syrmos-stasy-scraper/1.0 (+https://syrmos.peterdsp.dev)"
# The Greek index (/ανακοινώσεις/) is rendered client-side via JS and
# returns no detail links to a non-browser fetcher. The English index
# is plain HTML and lists every post — and the Greek detail page is
# just the English URL with /en/ stripped, so we get both languages.
INDEX_URL = "https://www.stasy.gr/en/announcements/"
GREEK_HOMEPAGE_URL = "https://www.stasy.gr/"
TIMEOUT_SECONDS = 25

# Slugs at the root of stasy.gr that are section / nav roots or legal /
# infra pages, not announcement permalinks.
_GREEK_HOMEPAGE_NAV_SLUGS = {
    "ανακοινώσεις", "δελτία-τύπου", "δρομολόγια", "εισιτήρια-κάρτες",
    "εκδηλώσεις", "εμπορική-εκμετάλλευση", "εξυπηρέτηση-επιβατών",
    "εταιρεία", "διαφήμιση", "διαγωνισμοί", "επικοινωνία", "σταθμοί",
    "χάρτης", "en", "category", "tag", "author",
    # Legal / infrastructure pages — never service alerts:
    "πολιτική-απορρήτου-και-προστασίας-δεδομένων", "πολιτική-cookies",
    "όροι-χρήσης", "λειτουργικότητα-ανελκυστήρων", "νέα-ανακοινώσεις",
    "προσβασιμότητα", "δελτία-τύπου-2",
}

# Match canonical homepage links to article-style permalinks (percent-encoded
# Greek slugs surfaced on the homepage as "Έκτακτες Ανακοινώσεις"). Excludes
# the language switch and resource sections.
_GREEK_ARTICLE_RE = re.compile(
    r'href=["\'](https://www\.stasy\.gr/[^/\"\']+/)["\']',
    re.IGNORECASE,
)

# Bilingual line-detection patterns. Matches are case-insensitive
# substring tests. The longer patterns are listed first so e.g.
# "γραμμή 11" never collapses into "γραμμή 1".
LINE_KEYWORDS: list[tuple[str, str]] = [
    # Greek
    ("γραμμή 1", "M1"),
    ("γραμμή ένα", "M1"),
    ("γραμμή 2", "M2"),
    ("γραμμή δύο", "M2"),
    ("γραμμή 3", "M3"),
    ("γραμμή τρία", "M3"),
    ("γραμμή τρια", "M3"),
    ("αεροδρόμιο", "M3_AIR"),
    ("αεροδρομιο", "M3_AIR"),
    ("γραμμή 6", "T6"),
    ("γραμμή έξι", "T6"),
    ("γραμμή 7", "T7"),
    ("γραμμή επτά", "T7"),
    ("τραμ", "T6"),  # generic Greek "tram" → seeds T6, T7 added below
    # English (STASY mirrors most announcements at /en/<slug>/)
    ("line 1", "M1"),
    ("line one", "M1"),
    ("metro line 1", "M1"),
    ("line 2", "M2"),
    ("line two", "M2"),
    ("metro line 2", "M2"),
    ("line 3", "M3"),
    ("line three", "M3"),
    ("metro line 3", "M3"),
    ("airport", "M3_AIR"),
    ("line 6", "T6"),
    ("line 7", "T7"),
    ("tram line", "T6"),
    ("tram", "T6"),
]

# Severity keywords (Greek + English).
CLOSURE_KEYWORDS = (
    "δεν λειτουργ", "δεν θα λειτουργ", "ακινητοποι",
    "διακοπή κυκλοφορ", "κλειστ", "αναστολή λειτουργ",
    "will not operate", "out of operation", "no service",
    "suspension of service", "lines closed", "stations closed",
)
WARNING_KEYWORDS = (
    "παράταση ωραρ", "αλλαγή", "τροποποίηση", "αλλαγές δρομολογ",
    "καθυστερ", "απεργ", "στάση εργασ",
    "κυκλοφοριακές ρυθμ", "ρυθμίσεις",
    "εργασ", "αντικατάστασ",                 # railworks, maintenance
    "extension of", "extended", "schedule change", "schedule changes",
    "traffic arrangement", "strike", "modification", "delay",
    "maintenance", "rail replacement",
)


@dataclass
class AnnouncementItem:
    slug: str
    title: str
    summary: str
    url: str
    title_en: str = ""
    summary_en: str = ""
    publish_date: str = ""
    affected_lines: list[str] = field(default_factory=list)
    severity: str = "info"
    valid_from: str | None = None
    valid_until: str | None = None
    closure_dates: list[str] = field(default_factory=list)


def _ascii_url(url: str) -> str:
    """STASY publishes Greek-character slugs (e.g. /ανακοινώσεις/). urllib's
    http client rejects non-ASCII characters in the request line, so we
    percent-encode the path / query before opening the connection."""
    parsed = urlparse(url)
    return urlunparse((
        parsed.scheme,
        parsed.netloc.encode("idna").decode("ascii"),
        quote(parsed.path, safe="/%"),
        parsed.params,
        quote(parsed.query, safe="=&%"),
        parsed.fragment,
    ))


def fetch_text(url: str) -> str:
    req = Request(_ascii_url(url), headers={"User-Agent": USER_AGENT, "Accept-Language": "el,en;q=0.5"})
    with urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
        return resp.read().decode("utf-8", errors="replace")


class _StripTagsParser(HTMLParser):
    """Collects visible text and remembers <a href> targets so we can
    distinguish announcement links from navigation chrome."""

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.parts: list[str] = []
        self.in_script = 0

    def handle_starttag(self, tag, attrs):
        if tag in ("script", "style"):
            self.in_script += 1

    def handle_endtag(self, tag):
        if tag in ("script", "style") and self.in_script > 0:
            self.in_script -= 1
        if tag in ("p", "br", "li", "div", "h1", "h2", "h3", "h4"):
            self.parts.append("\n")

    def handle_data(self, data):
        if self.in_script:
            return
        self.parts.append(data)

    def text(self) -> str:
        joined = "".join(self.parts)
        return re.sub(r"[ \t]+", " ", joined).strip()


def strip_html(html: str) -> str:
    p = _StripTagsParser()
    p.feed(html)
    return p.text()


# Each announcement card is a heading whose anchor links to the detail
# page. STASY renders this pattern via the WordPress blog template.
_HEADING_LINK_RE = re.compile(
    r'<h[1-4][^>]*>\s*<a[^>]+href=["\']'
    r'(https://www\.stasy\.gr/en/[^"\']+/)["\'][^>]*>([^<]+)</a>',
    re.IGNORECASE,
)


def parse_greek_homepage(html: str) -> list[str]:
    """Return article URLs surfaced on STASY's Greek homepage. STASY
    features fresh "Έκτακτες Ανακοινώσεις" as direct permalinks to
    Greek-only articles (e.g. the rolling 'rail replacement works'
    notice). Those don't appear on /en/announcements/, so this is the
    only path that catches them."""
    from urllib.parse import unquote
    seen: set[str] = set()
    out: list[str] = []
    for match in _GREEK_ARTICLE_RE.findall(html):
        url = match.rstrip("/") + "/"
        # Take the slug between stasy.gr/ and the trailing slash
        try:
            slug = url.split("stasy.gr/", 1)[1].rstrip("/")
        except IndexError:
            continue
        slug_decoded = unquote(slug).lower()
        if slug_decoded in _GREEK_HOMEPAGE_NAV_SLUGS:
            continue
        if slug_decoded.startswith(("διαγωνισμ", "wp-", "feed")):
            continue
        if url in seen:
            continue
        seen.add(url)
        out.append(url)
    return out


def parse_index(html: str) -> list[tuple[str, str]]:
    """Return [(english_detail_url, english_title)] for each announcement
    card on the English index page."""
    nav_blacklist = (
        "/en/announcements/",
        "/en/news-announcements/",
        "/en/urgent-announcements/",
        "/en/category/",
    )
    seen: set[str] = set()
    out: list[tuple[str, str]] = []
    for url, title in _HEADING_LINK_RE.findall(html):
        if url in seen:
            continue
        if any(url.endswith(seg) for seg in nav_blacklist):
            continue
        seen.add(url)
        out.append((url, strip_html(title).strip()))
    return out


_OG_TITLE_RE = re.compile(
    r'<meta[^>]+property=["\']og:title["\'][^>]+content=["\']([^"\']+)["\']',
    re.IGNORECASE,
)
_OG_DESCRIPTION_RE = re.compile(
    r'<meta[^>]+property=["\']og:description["\'][^>]+content=["\']([^"\']+)["\']',
    re.IGNORECASE,
)


def _meta(pattern: re.Pattern[str], html: str) -> str:
    m = pattern.search(html)
    if not m:
        return ""
    text = m.group(1).replace("&#038;", "&").replace("&amp;", "&").replace("&nbsp;", " ")
    # Trim the trailing " | ΣΤΑΣΥ" tail STASY appends to every og:title.
    return re.sub(r"\s*\|\s*ΣΤΑΣΥ\s*$", "", text).strip()


def slug_from_url(url: str) -> str:
    # Use the last path segment; stable as long as STASY doesn't rename.
    return url.rstrip("/").rsplit("/", 1)[-1]


def classify(text: str) -> str:
    lowered = text.lower()
    if any(k in lowered for k in CLOSURE_KEYWORDS):
        return "closure"
    if any(k in lowered for k in WARNING_KEYWORDS):
        return "warning"
    return "info"


def detect_affected_lines(text: str) -> list[str]:
    lowered = text.lower()
    found: list[str] = []
    seen: set[str] = set()
    # Sort longest first so "γραμμή 11" never matches the "γραμμή 1" pattern.
    for pat, lid in sorted(LINE_KEYWORDS, key=lambda kv: -len(kv[0])):
        if pat in lowered and lid not in seen:
            seen.add(lid)
            found.append(lid)
    # Generic "tram" / "τραμ" already adds T6; if "γραμμή 7" / "line 7"
    # wasn't mentioned but tram was, also add T7 so the warning covers
    # both tram lines.
    tram_word = "τραμ" in lowered or "tram" in lowered
    if "T6" in seen and tram_word and "T7" not in seen:
        seen.add("T7")
        found.append("T7")
    return found


_DMY_GREEK_RE = re.compile(
    r"(\d{1,2})\s*(?:η|ης|ού|ου|ή)?\s*"
    r"(ιαν|φεβ|μαρ|απρ|μαΐ|μαϊ|μαι|ιουν|ιουλ|αυγ|σεπ|οκτ|νοε|δεκ)[α-ωίάέήόύώϊϋΐΰ]*",
    re.IGNORECASE,
)
_DMY_NUMERIC_RE = re.compile(
    r"(?<!\d)(\d{1,2})[/\-.](\d{1,2})(?:[/\-.](\d{2,4}))?(?!\d)"
)
_TIME_OF_DAY_RE = re.compile(r"\b(\d{1,2}):(\d{2})\b")
_GREEK_MONTHS = {
    "ιαν": 1, "φεβ": 2, "μαρ": 3, "απρ": 4, "μαΐ": 5, "μαϊ": 5, "μαι": 5,
    "ιουν": 6, "ιουλ": 7, "αυγ": 8, "σεπ": 9, "οκτ": 10, "νοε": 11, "δεκ": 12,
}


def _nearest_year(today: date, month: int, day: int) -> date | None:
    """Pick the year that puts this (month, day) closest to today. Used
    when the article cites a date with no year (e.g. "Δευτέρα 15/06"):
    the article was almost certainly written for the upcoming or
    just-past occurrence, not one years away."""
    for y in (today.year - 1, today.year, today.year + 1):
        try:
            candidate = date(y, month, day)
        except ValueError:
            continue
        if abs((candidate - today).days) <= 200:
            return candidate
    try:
        return date(today.year, month, day)
    except ValueError:
        return None


def detect_dates(text: str, today: date | None = None) -> tuple[str | None, str | None, list[str]]:
    """Returns (valid_from, valid_until, closure_dates) parsed from the
    article body. Handles both Greek month-name phrases ("1η Μαΐου") and
    DD/MM[/YYYY] numeric dates ("Δευτέρα 15/06")."""
    if today is None:
        today = date.today()
    iso_dates: set[str] = set()

    for d_str, m_str in _DMY_GREEK_RE.findall(text.lower()):
        try:
            day = int(d_str)
            month = _GREEK_MONTHS.get(m_str.strip()[:3].lower())
        except ValueError:
            continue
        if not month or not 1 <= day <= 31:
            continue
        candidate = _nearest_year(today, month, day)
        if candidate is not None:
            iso_dates.add(candidate.isoformat())

    for d_str, m_str, y_str in _DMY_NUMERIC_RE.findall(text):
        try:
            day = int(d_str)
            month = int(m_str)
        except ValueError:
            continue
        if not (1 <= day <= 31 and 1 <= month <= 12):
            continue
        if y_str:
            year = int(y_str)
            if year < 100:
                year += 2000
            try:
                iso_dates.add(date(year, month, day).isoformat())
            except ValueError:
                pass
        else:
            candidate = _nearest_year(today, month, day)
            if candidate is not None:
                iso_dates.add(candidate.isoformat())

    ordered = sorted(iso_dates)
    if not ordered:
        return None, None, []
    return ordered[0], ordered[-1], ordered


def has_time_of_day(text: str) -> bool:
    """True if the body cites a specific HH:MM. Used to downgrade
    severity from 'closure' to 'warning' — STASY's full-day closures
    don't mention a time, only the date; partial closures like
    "θα κλείνουν στις 21:40" do, and shouldn't write date_overrides."""
    for h, m in _TIME_OF_DAY_RE.findall(text):
        try:
            if 0 <= int(h) <= 23 and 0 <= int(m) <= 59:
                return True
        except ValueError:
            continue
    return False


def build_announcement(
    english_url: str, english_title_fallback: str, today: date | None = None
) -> AnnouncementItem | None:
    """Fetch the English detail page and classify it.

    STASY doesn't expose a hreflang link to the Greek version, so we
    store the English title/summary in both language fields and rely on
    the bilingual keyword sets in classify() / detect_affected_lines()
    to extract structure. If/when we get a reliable Greek-slug bridge,
    populate `title` + `summary` from there.
    """
    try:
        html = fetch_text(english_url)
    except (URLError, TimeoutError):
        return None

    title_en = _meta(_OG_TITLE_RE, html) or english_title_fallback
    summary_en = _meta(_OG_DESCRIPTION_RE, html)
    if not summary_en:
        body = strip_html(html)
        if not body:
            return None
        summary_en = _summary(body)
    classifier_text = " ".join((title_en, summary_en)).lower()
    severity = classify(classifier_text)
    affected = detect_affected_lines(classifier_text)
    if not affected and severity in ("warning", "closure"):
        affected = ["M1", "M2", "M3", "M3_AIR", "T6", "T7"]
    valid_from, valid_until, closure_dates = detect_dates(classifier_text, today=today)

    # Partial-time closures ("stations will close at 21:40", "after 23:00")
    # mention a specific HH:MM and are not full-day shutdowns. Drop them
    # back to a warning so we never write a phantom date_overrides row.
    # A full-day closure requires the wording AND a date AND no time.
    if severity == "closure" and (has_time_of_day(classifier_text) or not closure_dates):
        severity = "warning"

    item = AnnouncementItem(
        slug=slug_from_url(english_url),
        title=title_en.strip(),
        summary=summary_en.strip(),
        url=english_url,
        title_en=title_en.strip(),
        summary_en=summary_en.strip(),
        affected_lines=affected,
        severity=severity,
        valid_from=valid_from,
        valid_until=valid_until,
    )
    if severity == "closure":
        item.closure_dates = closure_dates
    return item


def _summary(text: str) -> str:
    lines = [ln.strip() for ln in text.split("\n") if ln.strip()]
    return " ".join(lines[:4])[:500]


def upsert(conn: sqlite3.Connection, items: list[AnnouncementItem]) -> int:
    cur = conn.cursor()
    cur.execute("BEGIN")
    try:
        for idx, item in enumerate(items):
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
                    item.slug, item.title, item.title_en, item.summary, item.summary_en,
                    item.url, item.publish_date,
                    "serviceAlert" if item.severity in ("warning", "closure") else "general",
                    idx,
                    json.dumps(item.affected_lines, ensure_ascii=False),
                    item.severity,
                    item.valid_from, item.valid_until,
                ),
            )
            # Closure overrides: one (line, date) row per affected line per date.
            for d in item.closure_dates:
                for line_id in item.affected_lines:
                    payload = json.dumps({
                        "closed": True,
                        "reason": item.title,
                        "source": item.url,
                    }, ensure_ascii=False)
                    cur.execute(
                        "INSERT INTO date_overrides"
                        "(override_date, line_id, source, payload_json)"
                        " VALUES(?, ?, 'stasy_announcement', ?)"
                        " ON CONFLICT(override_date, line_id) DO UPDATE SET"
                        " source=excluded.source, payload_json=excluded.payload_json,"
                        " fetched_at=strftime('%Y-%m-%dT%H:%M:%SZ', 'now')",
                        (d, line_id, payload),
                    )
        cur.execute("COMMIT")
        return len(items)
    except Exception:
        cur.execute("ROLLBACK")
        raise


def run_once(now: date | None = None) -> int:
    """Scrape once and persist. Returns the number of announcement rows written."""
    if now is None:
        now = date.today()
    try:
        index_html = fetch_text(INDEX_URL)
    except (URLError, TimeoutError) as e:
        with dbmod.connect() as conn:
            conn.execute(
                "INSERT INTO scrape_log(source, ok, rows_written, error)"
                " VALUES('stasy_announcements', 0, 0, ?)",
                (f"fetch index failed: {e}",),
            )
        return 0
    links = parse_index(index_html)
    # Also pull Greek-only urgent announcements from the homepage. STASY
    # features service alerts (e.g. the rolling Line 3 rail-replacement
    # closure that doesn't appear on the English page at all) directly on
    # https://www.stasy.gr/ as permalinks. Without this path, fresh active
    # alerts go silently missing from the app.
    greek_links: list[str] = []
    try:
        greek_homepage_html = fetch_text(GREEK_HOMEPAGE_URL)
        greek_links = parse_greek_homepage(greek_homepage_html)
    except (URLError, TimeoutError):
        greek_links = []
    items: list[AnnouncementItem] = []
    seen_slugs: set[str] = set()
    for url, title in links[:30]:  # cap so a runaway page doesn't tarpit the cron
        item = build_announcement(url, title, today=now)
        if item is not None:
            items.append(item)
            seen_slugs.add(item.slug)
    for url in greek_links[:30]:
        slug = slug_from_url(url)
        if slug in seen_slugs:
            continue
        item = build_announcement(url, "", today=now)
        if item is not None:
            items.append(item)
            seen_slugs.add(item.slug)
    with dbmod.connect() as conn:
        count = upsert(conn, items)
        conn.execute(
            "INSERT INTO scrape_log(source, ok, rows_written)"
            " VALUES('stasy_announcements', 1, ?)",
            (count,),
        )
    return len(items)


if __name__ == "__main__":
    written = run_once()
    print(f"stasy_announcements: wrote {written} item(s)")
