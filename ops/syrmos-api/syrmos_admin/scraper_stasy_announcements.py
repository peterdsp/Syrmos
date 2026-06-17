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

# Anti-relevance keywords. If an article's title or body matches any of these
# AND has no transit-relevant signal of its own, it's almost certainly
# corporate / HR / procurement / culture noise that a transit rider never
# wants to see ("we hired a new CFO", "tender for office cleaning",
# "competition for student photography"). Bilingual.
ANTI_RELEVANCE_KEYWORDS = (
    # Greek
    "διαγωνισμ", "πρόσκληση εκδήλωσης", "προμήθεια",
    "θέσεις εργασίας", "θέση εργασίας", "προκήρυξη θέσ",
    "ισολογισμός", "οικονομικά στοιχεία", "δελτίο τύπου",
    "ετήσια έκθεση", "γενική συνέλευση", "διοικητικό συμβούλιο",
    "εκπαιδευτικ", "πολιτιστικ", "καλλιτεχνικ", "διαγωνισμός φωτογραφ",
    "πολιτική απορρήτου", "πολιτική cookies", "όροι χρήσης",
    "εορτασμός", "επέτειος",
    # English
    "tender", "procurement", "job position", "vacancy", "recruitment",
    "annual report", "balance sheet", "press release",
    "general assembly", "board of directors", "anniversary",
    "photo competition", "art competition", "student competition",
    "privacy policy", "cookies policy", "terms of use",
)

# Transit-relevance keywords. If a title / body matches at least one of these,
# the article is about something a rider could be affected by. Used both to
# accept Greek-homepage candidates and to override the anti-relevance check
# when the body genuinely is about service.
TRANSIT_RELEVANCE_KEYWORDS = (
    # Greek
    "γραμμή 1", "γραμμή 2", "γραμμή 3", "γραμμή 6", "γραμμή 7",
    "μετρό", "τραμ", "προαστιακ", "δρομολόγ", "σταθμός", "σταθμοί",
    "συρμό", "συρμοί", "κλείν", "κλειστ", "δεν λειτουργ",
    "καθυστερ", "διακοπ", "αλλαγή ωραρ", "παράταση ωραρ",
    "κυκλοφοριακές ρυθμ", "απεργ", "στάση εργασ",
    "εργασίες", "αντικατάσταση", "λεωφορει",
    # English
    "line 1", "line 2", "line 3", "line 6", "line 7",
    "metro", "tram", "suburban", "station", "stations",
    "schedule", "schedules", "departure", "operating hours",
    "closure", "closed", "out of service",
    "delay", "strike", "service alert", "rail replacement",
    "traffic arrangement", "maintenance",
)


_GREEK_LETTER_RE = re.compile(r"[Ͱ-Ͽἀ-῿]")


def _has_greek(text: str) -> bool:
    return bool(_GREEK_LETTER_RE.search(text))


def _translate_gr_en(text: str) -> str:
    """Translate Greek text to English via deep-translator (Google).
    Returns the original text on any failure so the scraper never errors
    out a whole run because translation hiccups. Safe to call even when
    deep-translator is missing — the import is wrapped."""
    text = text.strip()
    if not text or not _has_greek(text):
        return text
    try:
        from deep_translator import GoogleTranslator
        translated = GoogleTranslator(source="el", target="en").translate(text)
        translated = (translated or "").strip() or text
        return _canonicalise_station_names(translated)
    except Exception:
        return _canonicalise_station_names(text)


# Google Translate either translates Greek station names literally
# ("Εθνική Άμυνα" → "National Defense") or hallucinates transliterations
# ("Νομισματοκοπείο" → "Nimismatokopio"). The app uses canonical English
# spellings (the ones in lines.json), so any banner/alert that names a
# station should use those exact strings — otherwise users see two
# different spellings for the same station depending on the surface.
# Keep the list narrow: only names where Google's output diverges from
# the canonical. Plain transliterations (Piraeus, Syntagma, Monastiraki,
# Egaleo, Kerameikos, Eleonas, Korydallos, Panormou, Katechaki, Cholargos,
# Chalandri, Ambelokipi, Megaro Mousikis, Evangelismos, Maniatika, Nikaia,
# Agia Varvara, Agia Marina, Peania-Kantza, Pallini, Koropi, Airport,
# Doukissis Plakentias, Dimotiko Theatro) already round-trip correctly.
_STATION_NAME_CANONICAL: list[tuple[str, str]] = [
    # M3 stations Google gets wrong
    ("National Defense", "Ethniki Amyna"),  # Εθνική Άμυνα
    ("National defense", "Ethniki Amyna"),
    ("Nimismatokopio", "Nomismatokopio"),   # Νομισματοκοπείο (transliteration miss)
    ("Mint", "Nomismatokopio"),              # alternative literal translation
    # M2 stations Google translates literally
    ("Acropolis", "Akropoli"),               # Ακρόπολη
    ("Daphne", "Dafni"),                     # Δάφνη
    ("Saint Demetrios", "Agios Dimitrios"),
    ("Saint Dimitrios", "Agios Dimitrios"),
    ("Saint Antonios", "Agios Antonios"),
    ("Holy Antonios", "Agios Antonios"),
    # M1 stations Google often softens
    ("Saint Nicholas", "Agios Nikolaos"),
    ("Saint Nikolaos", "Agios Nikolaos"),
    ("Holy Nicholas", "Agios Nikolaos"),
]


def _canonicalise_station_names(text: str) -> str:
    """Replace Google-Translate-isms with the canonical English station
    names used everywhere else in the app. Order-sensitive: longer
    phrases first so "Holy Antonios" isn't half-replaced before the
    longer rule fires."""
    out = text
    for bad, good in _STATION_NAME_CANONICAL:
        out = out.replace(bad, good)
    return out


def is_transit_relevant(text: str) -> bool:
    """Soft filter for whether an article is about something a rider would
    want to hear about. Match logic: at least one transit-relevance hit
    and either zero anti-relevance hits or more transit hits than
    anti-relevance hits."""
    lowered = text.lower()
    transit_hits = sum(1 for k in TRANSIT_RELEVANCE_KEYWORDS if k in lowered)
    anti_hits = sum(1 for k in ANTI_RELEVANCE_KEYWORDS if k in lowered)
    if transit_hits == 0:
        return False
    return transit_hits >= anti_hits


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


# --- Homepage status badge + inline emergency banner -----------------------
#
# STASY's Greek homepage exposes two things the apps' Home pill cares about
# that DON'T appear in /en/announcements/:
#
#  1. A "Κανονική Λειτουργία" / alert badge near the header.
#  2. An inline emergency banner (e.g. the rolling Line 3 21:40 closure)
#     that is plain text in the page body, NOT a separate permalink.
#
# Without this parser the stasy_status row never gets written, so
# /api/announcements.status stays "unknown" and the in-app pill silently
# falls back to "Trains until 00:50" derived from the bundled schedule
# rules — which masks the real per-line restriction.

_NORMAL_BADGE_RE = re.compile(r"Κανονικ[ηή]\s+Λειτουργ[ιί]α", re.IGNORECASE)
# Match HH:MM after any of the closure keywords STASY uses: "έως 21:40",
# "μέχρι τις 21:40", "θα κλείνουν στις 21:40", "αναστέλλονται στις 23:00".
# The keyword acts as a guard so we don't pick up unrelated times that
# happen to appear earlier in the page (e.g. operating-hour ranges).
_SERVICE_UNTIL_RE = re.compile(
    r"(?:έως|μ[εέ]χρι|στις|κλείνουν|αναστ[εέ]λλονται|διακ[οό]πτονται)"
    r"\s+(?:τις\s+)?(\d{1,2}[:.]\d{2})"
)
# Strong-signal alert openers used by STASY on the homepage banner.
_ALERT_OPENER_RE = re.compile(
    r"(Κυκλοφοριακ[έε]ς\s+ρυθμ[ίι]σεις|"
    r"[ΈΕ]κτακτη\s+[ΑA]νακο[ίι]νωση|"
    r"Διακοπ[ήη]\s+δρομολογ[ίι]ων|"
    r"Αναστολ[ήη]\s+δρομολογ[ίι]ων)",
    re.IGNORECASE,
)


def parse_homepage_status(html: str) -> dict:
    """Distill the STASY Greek homepage down to one of:

      {"status": "alert",  "raw_message": "...", "raw_message_en": "...",
       "service_until": "21:40"}
      {"status": "normal", "raw_message": "Κανονική Λειτουργία",
       "raw_message_en": "Normal operation", "service_until": None}
      {"status": "unknown", "raw_message": "", "raw_message_en": "",
       "service_until": None}

    Alert wins over normal when both are present (an active banner means a
    restriction exists even if the badge still says "Κανονική Λειτουργία").
    service_until is parsed from the banner only — never from the badge.
    """
    text = strip_html(html)
    alert_text = _extract_alert_banner(text)
    if alert_text:
        m = _SERVICE_UNTIL_RE.search(alert_text)
        service_until = m.group(1).replace(".", ":") if m else None
        return {
            "status": "alert",
            "raw_message": alert_text,
            "raw_message_en": _translate_gr_en(alert_text),
            "service_until": service_until,
        }
    if _NORMAL_BADGE_RE.search(text):
        return {
            "status": "normal",
            "raw_message": "Κανονική Λειτουργία",
            "raw_message_en": "Normal operation",
            "service_until": None,
        }
    return {"status": "unknown", "raw_message": "", "raw_message_en": "", "service_until": None}


def _extract_alert_banner(text: str) -> str:
    """Pull the first sentence-ish chunk of the homepage starting at an
    alert opener like 'Κυκλοφοριακές ρυθμίσεις…'. STASY phrases its
    inline banners as one long sentence ending at a period, so we cut at
    the next period or 350 chars, whichever comes first, to keep the pill
    short. Returns empty string when no opener is found."""
    m = _ALERT_OPENER_RE.search(text)
    if not m:
        return ""
    tail = text[m.start():]
    # End at the first period (Greek text uses .) or after a hard cap.
    end = tail.find(".")
    if end == -1 or end > 350:
        end = min(len(tail), 350)
    else:
        end += 1  # include the period
    result = " ".join(tail[:end].split()).strip()
    return _strip_nav_tail(result)


# Language-switcher and footer-nav tokens that bleed into the extracted
# banner because the homepage has no period between the alert sentence
# and the nav block. We cut the banner at the FIRST occurrence of any
# of these — they're distinctive enough that they should never appear
# inside a real service alert sentence.
_NAV_CUT_TOKENS = (
    "Ελληνικά", "ΕΛΛΗΝΙΚΑ", "English", "ENGLISH",
    "Δείτε περισσότερα", "Read more",
)


def _strip_nav_tail(text: str) -> str:
    """Drop everything from the first language-switcher / footer nav
    token onwards. Returns the input unchanged if no token is found."""
    cut = len(text)
    for token in _NAV_CUT_TOKENS:
        idx = text.find(token)
        if 0 < idx < cut:
            cut = idx
    return text[:cut].rstrip(" ,;:·-")


def upsert_homepage_status(conn: sqlite3.Connection, status: dict) -> None:
    """Write the singleton stasy_status row. Idempotent — uses INSERT OR
    REPLACE so a missing migration 0010 (raw_message_en column) still
    works after a fallback to the legacy column set."""
    try:
        conn.execute(
            "INSERT OR REPLACE INTO stasy_status"
            "(id, status, raw_message, raw_message_en, service_until, scraped_at)"
            " VALUES(1, ?, ?, ?, ?, strftime('%Y-%m-%dT%H:%M:%fZ','now'))",
            (status["status"], status["raw_message"],
             status["raw_message_en"], status["service_until"]),
        )
    except sqlite3.OperationalError:
        conn.execute(
            "INSERT OR REPLACE INTO stasy_status"
            "(id, status, raw_message, service_until, scraped_at)"
            " VALUES(1, ?, ?, ?, strftime('%Y-%m-%dT%H:%M:%fZ','now'))",
            (status["status"], status["raw_message"], status["service_until"]),
        )


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
    # Transit-relevance gate. Drop corporate / HR / tender / cultural junk
    # before it ever reaches the DB so the home screen stays signal-only.
    if not is_transit_relevant(classifier_text):
        return None
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

    # When the source is Greek (the homepage path picks up Greek-only
    # urgent permalinks), translate the title + summary to English so EN
    # clients don't render Greek text. Greek stays in the `title` /
    # `summary` fields and the translation goes in `title_en` /
    # `summary_en`. For English sources both ends are already English so
    # the translator no-ops on the relevance gate (no Greek chars).
    raw_title = title_en.strip()
    raw_summary = summary_en.strip()
    if _has_greek(raw_title) or _has_greek(raw_summary):
        title_translated = _translate_gr_en(raw_title)
        summary_translated = _translate_gr_en(raw_summary)
        title_native = raw_title
        summary_native = raw_summary
    else:
        title_translated = raw_title
        summary_translated = raw_summary
        title_native = raw_title
        summary_native = raw_summary

    item = AnnouncementItem(
        slug=slug_from_url(english_url),
        title=title_native,
        summary=summary_native,
        url=english_url,
        title_en=title_translated,
        summary_en=summary_translated,
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
    homepage_status: dict | None = None
    greek_homepage_html = ""
    try:
        greek_homepage_html = fetch_text(GREEK_HOMEPAGE_URL)
        greek_links = parse_greek_homepage(greek_homepage_html)
        homepage_status = parse_homepage_status(greek_homepage_html)
    except (URLError, TimeoutError):
        greek_links = []
    items: list[AnnouncementItem] = []
    seen_slugs: set[str] = set()
    # Surface the inline emergency banner as a synthetic serviceAlert so
    # the home alert card renders it even when STASY doesn't link a
    # separate article. Lines + dates are best-effort from the banner
    # text using the same detectors the normal path uses.
    if homepage_status and homepage_status["status"] == "alert" and homepage_status["raw_message"]:
        banner_gr = homepage_status["raw_message"]
        banner_en = homepage_status["raw_message_en"] or banner_gr
        affected = detect_affected_lines(banner_gr + " " + banner_en)
        vf_detected, vu_detected, _ = detect_dates(banner_gr + " " + banner_en, today=now)
        # The generator's _is_fresh() gate drops anything without a parseable
        # valid_from or valid_until. The inline banner usually has no date,
        # so anchor valid_from to today — the banner only exists while STASY
        # publishes it, so "fresh as of today" is the right semantics.
        synthetic = AnnouncementItem(
            slug="stasy-homepage-alert",
            title=banner_gr,
            summary=banner_gr,
            url=GREEK_HOMEPAGE_URL,
            title_en=banner_en,
            summary_en=banner_en,
            affected_lines=affected,
            severity="warning",
            valid_from=vf_detected or now.isoformat(),
            valid_until=vu_detected,
        )
        if synthetic.slug not in seen_slugs:
            items.append(synthetic)
            seen_slugs.add(synthetic.slug)
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
        if homepage_status is not None:
            upsert_homepage_status(conn, homepage_status)
        conn.execute(
            "INSERT INTO scrape_log(source, ok, rows_written)"
            " VALUES('stasy_announcements', 1, ?)",
            (count,),
        )
    return len(items)


if __name__ == "__main__":
    written = run_once()
    print(f"stasy_announcements: wrote {written} item(s)")
