"""STASY scraper: station detection, station closures and draft closures.

Regression texts are the live announcements of 27 Oct 2026 (Line 3 rail works
banner and the 28 October Syntagma police closure).
"""
import json
from datetime import date
from pathlib import Path

import pytest

from syrmos_admin import closures as closures_mod
from syrmos_admin import db as dbmod
from syrmos_admin import scraper_stasy_announcements as scraper

FIXTURES = Path(__file__).resolve().parent / "fixtures"
TODAY = date(2026, 10, 27)

LINE3_BANNER = (
    "Κυκλοφοριακές ρυθμίσεις στη Γραμμή 3 Μετρό από την Κυριακή 25 έως και την Πέμπτη 29 "
    "Οκτωβρίου, λόγω εργασιών αντικατάστασης σιδηροτροχιών: οι σταθμοί «Εθνική Άμυνα», "
    "«Χολαργός», «Νομισματοκοπείο», «Αγία Παρασκευή» και «Χαλάνδρι» θα κλείνουν στις 21:40 "
    "το βραδύ - Για την εξυπηρέτηση του επιβατικού κοινού δρομολογείται η προσωρινή "
    "λεωφορειακή Γραμμή"
)
LINE3_BANNER_EN = (
    "Traffic adjustments on Metro Line 3 from Sunday, October 25 to Thursday, October 29, due to "
    "rail replacement works: the stations Ethniki Amyna, Cholargos, Nomismatokopeio, Agia Paraskevi "
    "and Chalandri will close at 21:40 in the evening - For the service of the passengers, a "
    "temporary bus line is deployed"
)
SYNTAGMA_URL = (
    "https://www.stasy.gr/%ce%bb%ce%b5%ce%b9%cf%84%ce%bf%cf%85%cf%81%ce%b3%ce%af%ce%b1-"
    "%ce%bc%ce%b5%cf%84%cf%81%cf%8c-%cf%84%cf%81%ce%b1%ce%bc-%cf%84%ce%b7%ce%bd-"
    "%cf%84%ce%b5%cf%84%ce%ac%cf%81%cf%84%ce%b7-28-%ce%bf%ce%ba/"
)
SYNTAGMA_HTML = (FIXTURES / "stasy-2026-10-28-syntagma.html").read_text(encoding="utf-8")
LINE3_STATIONS = ["M3_AG3", "M3_CHA", "M3_CHO", "M3_ETH", "M3_NOM"]


@pytest.fixture(autouse=True)
def _no_translation(monkeypatch):
    # The scraper translates Greek text; never reach a provider from a test.
    monkeypatch.setattr(scraper, "translate_from_greek", lambda text, target: "")


# --- detection ---------------------------------------------------------------------

def test_line3_banner_names_all_five_stations():
    assert scraper.detect_affected_stations(LINE3_BANNER) == LINE3_STATIONS
    assert scraper.detect_affected_stations(LINE3_BANNER_EN) == LINE3_STATIONS


def test_station_detection_ignores_accents_case_and_genitive():
    assert scraper.detect_affected_stations("ΣΤΑΘΜΟΣ ΧΑΛΑΝΔΡΙ") == ["M3_CHA"]
    assert scraper.detect_affected_stations("του σταθμού Χαλανδρίου") == ["M3_CHA"]
    assert scraper.detect_affected_stations("της Αγίας Παρασκευής") == ["M3_AG3"]
    assert scraper.detect_affected_stations("στην Εθνικής Άμυνας") == ["M3_ETH"]
    assert scraper.detect_affected_stations("Συντάγματος") == ["M2_SYN", "M3_SYN"]


def test_syntagma_maps_to_both_lines():
    assert scraper.detect_affected_stations("Ο σταθμός του Μετρό «Σύνταγμα»") == ["M2_SYN", "M3_SYN"]


def test_date_range_without_month_on_first_day():
    vf, vu, dates = scraper.detect_dates(LINE3_BANNER.lower(), today=TODAY)
    assert (vf, vu) == ("2026-10-25", "2026-10-29")


def test_line3_banner_is_a_nightly_closure_with_bus():
    info = scraper.detect_station_closure(LINE3_BANNER)
    assert info["mode"] == "closed"
    assert info["time"] == "21:40"
    assert info["nightly"] is True
    assert info["replacementBus"] is True
    assert info["openEnded"] is False
    assert "21:40" in info["reason"]


def test_extract_post_body_reads_the_whole_notice():
    body = scraper.extract_post_body(SYNTAGMA_HTML)
    assert "διέρχονται από αυτόν χωρίς να πραγματοποιούν στάση" in body
    assert "«ΣΕΦ»" in body
    assert "ΚΟΙΝΟΠΟΙΗΣΗ" not in body
    assert "Γραμμή 1" not in body  # nav and footer are outside the post body
    assert scraper.extract_post_body("<html>no widget</html>") == ""


def test_syntagma_notice_is_pass_through_from_8_until_further_notice():
    info = scraper.detect_station_closure(scraper.extract_post_body(SYNTAGMA_HTML))
    assert info["mode"] == "pass_through"
    assert info["time"] == "08:00"
    assert info["openEnded"] is True
    assert info["nightly"] is False
    assert info["reason"].startswith("Ο σταθμός του Μετρό «Σύνταγμα» θα κλείσει στις 8 το πρωί")


def test_no_station_closure_in_plain_notice():
    assert scraper.detect_station_closure("Παράταση ωραρίου λειτουργίας της Γραμμής 1") is None


# --- build_announcement on the live 28 October page ---------------------------------

def test_syntagma_article_becomes_service_alert(monkeypatch):
    monkeypatch.setattr(scraper, "fetch_text", lambda url: SYNTAGMA_HTML)
    item = scraper.build_announcement(SYNTAGMA_URL, "", today=TODAY)
    assert item is not None
    assert item.severity == "warning"  # -> category serviceAlert in upsert()
    assert item.affected_station_ids == ["M2_SYN", "M3_SYN"]
    assert set(item.affected_lines) >= {"T6", "T7", "M2", "M3"}
    assert item.station_closure["mode"] == "pass_through"
    # A station closure is not a full-day line closure: no date_overrides.
    assert item.closure_dates == []


# --- drafts ------------------------------------------------------------------------

def _item(**over):
    base = dict(slug="syn", title="Ο σταθμός «Σύνταγμα» θα κλείσει", summary="", url="u",
                affected_station_ids=["M2_SYN", "M3_SYN"], valid_from="2026-10-28",
                valid_until="2026-10-28")
    base.update(over)
    return scraper.AnnouncementItem(**base)


def test_draft_for_syntagma_pass_through():
    info = scraper.detect_station_closure(scraper.extract_post_body(SYNTAGMA_HTML))
    draft = scraper.draft_closure_for_item(_item(station_closure=info), TODAY)
    assert draft["mode"] == "pass_through"
    assert draft["station_ids"] == ["M2_SYN", "M3_SYN"]
    assert draft["line_ids"] == []
    assert draft["starts_at"] == 1793167200  # 28 Oct 08:00 Athens
    assert draft["ends_at"] is None  # "αναμένεται μετά την ολοκλήρωση" = until further notice
    assert draft["replacement_bus"] is None
    assert draft["reason_el"].startswith("Ο σταθμός του Μετρό «Σύνταγμα»")


def test_draft_for_line3_banner_is_tonight_with_bus():
    info = scraper.detect_station_closure(LINE3_BANNER)
    item = _item(slug="stasy-homepage-alert", title=LINE3_BANNER, title_en=LINE3_BANNER_EN,
                 affected_station_ids=LINE3_STATIONS, valid_from="2026-10-25",
                 valid_until="2026-10-29", station_closure=info)
    draft = scraper.draft_closure_for_item(item, TODAY)
    assert draft["mode"] == "closed"
    assert draft["starts_at"] == scraper._athens_epoch(TODAY, "21:40")
    assert draft["ends_at"] == scraper._athens_epoch(date(2026, 10, 28), "05:00")
    assert draft["replacement_bus"]["label"] == {"el": "Προσωρινή λεωφορειακή γραμμή", "en": "Temporary bus line"}
    assert draft["replacement_bus"]["stops"] == []
    assert draft["reason_en"] == LINE3_BANNER_EN
    assert "@2026-10-27" in draft["source_ref"]
    # After the range has passed there is nothing to draft.
    assert scraper.draft_closure_for_item(item, date(2026, 10, 30)) is None


def test_no_draft_without_closure_or_real_stations():
    assert scraper.draft_closure_for_item(_item(station_closure=None), TODAY) is None
    info = scraper.detect_station_closure("θα κλείσει")
    assert scraper.draft_closure_for_item(_item(affected_station_ids=["AIRPORT"], station_closure=info), TODAY) is None


def test_upsert_drafts_is_idempotent_and_never_public(temp_db):
    conn = dbmod.connect()
    info = scraper.detect_station_closure(scraper.extract_post_body(SYNTAGMA_HTML))
    items = [_item(station_closure=info)]
    assert scraper.upsert_draft_closures(conn, items, TODAY) == 1
    assert scraper.upsert_draft_closures(conn, items, TODAY) == 0
    rows = conn.execute("SELECT * FROM station_closures").fetchall()
    assert len(rows) == 1
    assert (rows[0]["status"], rows[0]["source"]) == ("draft", "stasy")
    assert closures_mod.public_closures(conn, now=1793167200 + 60) == []
    assert closures_mod.load_closures(conn) == []  # projector ignores drafts


def test_upsert_drafts_keeps_owner_edits(temp_db):
    conn = dbmod.connect()
    info = scraper.detect_station_closure("θα κλείσει στις 8 το πρωί")
    items = [_item(station_closure=info)]
    scraper.upsert_draft_closures(conn, items, TODAY)
    conn.execute("UPDATE station_closures SET status='scheduled', reason_en='edited'")
    scraper.upsert_draft_closures(conn, items, TODAY)
    row = conn.execute("SELECT status, reason_en FROM station_closures").fetchone()
    assert (row["status"], row["reason_en"]) == ("scheduled", "edited")


def test_upsert_drafts_filters_unknown_stations(seeded_db):
    conn = dbmod.connect()
    info = scraper.detect_station_closure("θα κλείσει")
    items = [_item(slug="a", affected_station_ids=["M2_SYN", "M9_NOPE"], station_closure=info),
             _item(slug="b", affected_station_ids=["M9_NOPE"], station_closure=info)]
    assert scraper.upsert_draft_closures(conn, items, TODAY) == 1
    assert json.loads(conn.execute("SELECT station_ids FROM station_closures").fetchone()[0]) == ["M2_SYN"]


def test_upsert_drafts_refuses_to_empty_a_line(seeded_db):
    conn = dbmod.connect()
    info = scraper.detect_station_closure("θα κλείσει")
    items = [_item(slug="tram", affected_station_ids=["T6_SYN", "T6_FIX"], station_closure=info)]
    assert scraper.upsert_draft_closures(conn, items, TODAY) == 0


def test_upsert_drafts_without_table_is_a_no_op():
    import sqlite3

    conn = sqlite3.connect(":memory:")
    info = scraper.detect_station_closure("θα κλείσει")
    assert scraper.upsert_draft_closures(conn, [_item(station_closure=info)], TODAY) == 0


# --- run_once end to end -----------------------------------------------------------

def test_run_once_drafts_both_live_cases(temp_db, monkeypatch):
    homepage = (
        "<html><body><div>Κανονική Λειτουργία</div>"
        f"<p>{LINE3_BANNER}.</p>"
        f"<a href='{SYNTAGMA_URL}'>Λειτουργία Μετρό & Τραμ</a></body></html>"
    )
    pages = {scraper.INDEX_URL: "<html></html>", scraper.GREEK_HOMEPAGE_URL: homepage, SYNTAGMA_URL: SYNTAGMA_HTML}
    monkeypatch.setattr(scraper, "fetch_text", lambda url: pages.get(url, ""))
    scraper.run_once(now=TODAY)
    scraper.run_once(now=TODAY)  # idempotent
    conn = dbmod.connect()
    ann = {r["id"]: r for r in conn.execute("SELECT * FROM announcements")}
    banner = ann["stasy-homepage-alert"]
    assert json.loads(banner["affected_station_ids"]) == LINE3_STATIONS
    syn = ann[scraper.slug_from_url(SYNTAGMA_URL)]
    assert syn["category"] == "serviceAlert"
    assert json.loads(syn["affected_station_ids"]) == ["M2_SYN", "M3_SYN"]
    drafts = conn.execute("SELECT * FROM station_closures ORDER BY mode").fetchall()
    assert [(d["mode"], d["status"], d["source"]) for d in drafts] == [
        ("closed", "draft", "stasy"), ("pass_through", "draft", "stasy"),
    ]
    assert json.loads(drafts[0]["station_ids"]) == LINE3_STATIONS
    assert json.loads(drafts[0]["replacement_bus"])["label"]["en"] == "Temporary bus line"
    assert drafts[1]["ends_at"] is None
    # Nothing turned into a whole-line closure.
    assert conn.execute("SELECT COUNT(*) FROM date_overrides").fetchone()[0] == 0


# --- full-day line closures stay title + summary only --------------------------

SINGLE_STATION_CLOSURE_HTML = """<html><head>
<meta property="og:title" content="Δεν θα λειτουργεί ο σταθμός Σύνταγμα την Τετάρτη 28 Οκτωβρίου | ΣΤΑΣΥ" />
<meta property="og:description" content="Ο σταθμός Σύνταγμα δεν θα λειτουργεί την Τετάρτη 28 Οκτωβρίου." />
</head><body>
<div class="elementor-widget elementor-widget-theme-post-content"><div class="elementor-widget-container">
<div dir="auto">Ο σταθμός Σύνταγμα δεν θα λειτουργεί την Τετάρτη 28 Οκτωβρίου.</div>
<div dir="auto">Οι επιβάτες μπορούν να χρησιμοποιούν τη Γραμμή 1 ή τη Γραμμή 7 του Τραμ.</div>
</div></div><section></section></body></html>"""


def test_body_lines_never_become_full_day_line_closures(temp_db, monkeypatch):
    url = "https://www.stasy.gr/syntagma-closed/"
    monkeypatch.setattr(scraper, "fetch_text", lambda u: SINGLE_STATION_CLOSURE_HTML)
    item = scraper.build_announcement(url, "", today=TODAY)
    assert item.severity == "closure"
    assert item.closure_dates == ["2026-10-28"]
    # The body's "use Line 1 or tram Line 7" shows on the alert ...
    assert {"M1", "T7"} <= set(item.affected_lines)
    # ... but full-day closure lines come from title + summary only. They name
    # a station and no line, so this is a station closure: no whole line is
    # closed for the day, and a draft station closure is suggested instead.
    assert item.closure_line_ids == []
    assert item.station_closure["mode"] == "closed"
    conn = dbmod.connect()
    conn.execute("PRAGMA foreign_keys = OFF")
    scraper.upsert(conn, [item])
    assert conn.execute("SELECT COUNT(*) FROM date_overrides").fetchone()[0] == 0
    assert scraper.upsert_draft_closures(conn, [item], TODAY) == 1


def test_network_wide_closure_keeps_all_lines_fallback(temp_db, monkeypatch):
    html = """<html><head>
<meta property="og:title" content="Δεν θα λειτουργήσουν τα δρομολόγια την Τετάρτη 28 Οκτωβρίου | ΣΤΑΣΥ" />
<meta property="og:description" content="Λόγω απεργίας δεν θα λειτουργήσουν τα δρομολόγια την 28 Οκτωβρίου." />
</head><body></body></html>"""
    monkeypatch.setattr(scraper, "fetch_text", lambda u: html)
    item = scraper.build_announcement("https://www.stasy.gr/strike/", "", today=TODAY)
    assert item.severity == "closure"
    assert item.closure_line_ids == ["M1", "M2", "M3", "M3_AIR", "T6", "T7"]


def test_title_named_line_limits_the_full_day_closure(temp_db, monkeypatch):
    html = SINGLE_STATION_CLOSURE_HTML.replace(
        "Δεν θα λειτουργεί ο σταθμός Σύνταγμα", "Δεν θα λειτουργεί η Γραμμή 2 στο Σύνταγμα", 1
    )
    monkeypatch.setattr(scraper, "fetch_text", lambda u: html)
    item = scraper.build_announcement("https://www.stasy.gr/m2-closed/", "", today=TODAY)
    assert item.closure_line_ids == ["M2"]
    conn = dbmod.connect()
    conn.execute("PRAGMA foreign_keys = OFF")
    scraper.upsert(conn, [item])
    lines = {r[0] for r in conn.execute("SELECT line_id FROM date_overrides")}
    assert lines == {"M2"}  # never M1 / T7 from the body, never M3 from the station
