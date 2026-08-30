import sqlite3
import sys
import types
import unittest
from unittest.mock import patch

from syrmos_admin.generator import _build_announcements, _build_news
from syrmos_admin.scraper_hellenic_train import _translate_it as translate_news_it
from syrmos_admin.scraper_ht_important_info import (
    AlertItem,
    _translate_alert_title_it,
    _translate_it as translate_ht_alert_it,
    upsert as upsert_ht_alerts,
)
from syrmos_admin.scraper_oseth import _translate_it as translate_oseth_it
from syrmos_admin.scraper_stasy_announcements import _translate_it as translate_alert_it
from syrmos_admin.scraper_thessmetro import _translate_it as translate_thessmetro_it


def make_conn() -> sqlite3.Connection:
    conn = sqlite3.connect(":memory:")
    conn.row_factory = sqlite3.Row
    conn.executescript(
        """
        CREATE TABLE stasy_status (
            id INTEGER PRIMARY KEY,
            status TEXT NOT NULL,
            raw_message TEXT NOT NULL,
            raw_message_en TEXT NOT NULL,
            raw_message_sq TEXT NOT NULL,
            raw_message_it TEXT NOT NULL,
            service_until TEXT,
            scraped_at TEXT NOT NULL
        );
        CREATE TABLE announcements (
            id TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            title_en TEXT NOT NULL,
            title_sq TEXT NOT NULL,
            title_it TEXT NOT NULL,
            summary TEXT NOT NULL,
            summary_en TEXT NOT NULL,
            summary_sq TEXT NOT NULL,
            summary_it TEXT NOT NULL,
            url TEXT NOT NULL,
            date TEXT NOT NULL,
            category TEXT NOT NULL,
            sort_order INTEGER NOT NULL,
            affected_lines TEXT NOT NULL,
            severity TEXT NOT NULL,
            valid_from TEXT,
            valid_until TEXT,
            affected_station_ids TEXT,
            service_until_time TEXT
        );
        CREATE TABLE rail_news (
            id TEXT PRIMARY KEY,
            title TEXT NOT NULL,
            title_en TEXT NOT NULL,
            title_sq TEXT NOT NULL,
            title_it TEXT NOT NULL,
            summary TEXT NOT NULL,
            summary_en TEXT NOT NULL,
            summary_sq TEXT NOT NULL,
            summary_it TEXT NOT NULL,
            url TEXT NOT NULL,
            published_at TEXT NOT NULL,
            thumbnail_url TEXT NOT NULL,
            categories TEXT NOT NULL
        );
        """
    )
    return conn


class ItalianContentPayloadTest(unittest.TestCase):
    def test_translation_failure_does_not_mislabel_greek_as_italian(self):
        class FailingTranslator:
            def __init__(self, **_kwargs):
                pass

            def translate(self, _text):
                raise RuntimeError("offline")

        fake_module = types.SimpleNamespace(
            GoogleTranslator=FailingTranslator,
            MyMemoryTranslator=FailingTranslator,
        )
        with patch.dict(sys.modules, {"deep_translator": fake_module}):
            self.assertEqual(translate_news_it("ΑΝΑΚΟΙΝΩΣΗ"), "")
            self.assertEqual(translate_alert_it("Κυκλοφοριακές ρυθμίσεις"), "")
            self.assertEqual(translate_ht_alert_it("Καθυστέρηση"), "")
            self.assertEqual(translate_oseth_it("Καθυστέρηση"), "")
            self.assertEqual(translate_thessmetro_it("Καθυστέρηση"), "")

    def test_google_error_page_uses_italian_fallback(self):
        class ErrorPageTranslator:
            def __init__(self, **_kwargs):
                pass

            def translate(self, _text):
                return "Error 500 (Server Error). That's an error."

        class ItalianFallbackTranslator:
            def __init__(self, **_kwargs):
                pass

            def translate(self, _text):
                return "Comunicazione"

        fake_module = types.SimpleNamespace(
            GoogleTranslator=ErrorPageTranslator,
            MyMemoryTranslator=ItalianFallbackTranslator,
        )
        with patch.dict(sys.modules, {"deep_translator": fake_module}):
            self.assertEqual(translate_news_it("ΑΝΑΚΟΙΝΩΣΗ"), "Comunicazione")
            self.assertEqual(translate_alert_it("ΑΝΑΚΟΙΝΩΣΗ"), "Comunicazione")
            self.assertEqual(translate_ht_alert_it("ΑΝΑΚΟΙΝΩΣΗ"), "Comunicazione")
            self.assertEqual(translate_oseth_it("ΑΝΑΚΟΙΝΩΣΗ"), "Comunicazione")
            self.assertEqual(translate_thessmetro_it("ΑΝΑΚΟΙΝΩΣΗ"), "Comunicazione")

    def test_announcements_emit_italian_content(self):
        conn = make_conn()
        conn.execute(
            "INSERT INTO stasy_status VALUES (1, ?, ?, ?, ?, ?, ?, ?)",
            ("alert", "Ρύθμιση", "Traffic change", "Ndryshim", "Modifica al servizio", "21:40", "2026-08-05T10:00:00Z"),
        )
        conn.execute(
            "INSERT INTO announcements VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (
                "alert-1", "Καθυστέρηση", "Delay", "Vonesë", "Ritardo",
                "Λεπτομέρειες", "Details", "Detaje", "Dettagli",
                "https://example.com", "2099-08-05", "serviceAlert", 0,
                '["M3"]', "warning", "2099-08-05", "2099-08-06",
                '[]', None,
            ),
        )

        payload = _build_announcements(conn)

        self.assertEqual(payload["status"]["rawMessageIt"], "Modifica al servizio")
        self.assertEqual(payload["announcements"][0]["titleIt"], "Ritardo")
        self.assertEqual(payload["announcements"][0]["summaryIt"], "Dettagli")

    def test_news_emit_italian_content(self):
        conn = make_conn()
        conn.execute(
            "INSERT INTO rail_news VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (
                "news-1", "ΑΝΑΚΟΙΝΩΣΗ", "Announcement", "Njoftim", "Annuncio",
                "Περίληψη", "Summary", "Permbledhje", "Riepilogo",
                "https://example.com", "2099-08-05", "", "[]",
            ),
        )

        payload = _build_news(conn)

        self.assertEqual(payload["news"][0]["titleIt"], "Annuncio")
        self.assertEqual(payload["news"][0]["summaryIt"], "Riepilogo")

    def test_common_hellenic_train_alert_title_is_deterministic_italian(self):
        self.assertEqual(
            _translate_alert_title_it(
                "Προαστιακές - Περιφερειακές Γραμμές Αθήνας",
                "Καθυστέρηση Δρομολογίου",
            ),
            "Linee suburbane e regionali di Atene: Ritardo del servizio",
        )

    def test_hellenic_train_alert_refresh_prunes_only_stale_operator_alerts(self):
        conn = make_conn()
        conn.execute(
            "INSERT INTO announcements VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (
                "ht-alert-stale", "Παλιό", "Old", "Vjetër", "Vecchio",
                "", "", "", "", "https://example.com", "2026-08-01",
                "serviceAlert", 0, "[]", "warning", "2026-08-01", None,
                "[]", None,
            ),
        )
        conn.execute(
            "INSERT INTO announcements VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (
                "stasy-current", "Τρέχον", "Current", "Aktual", "Attuale",
                "", "", "", "", "https://example.com", "2026-08-01",
                "serviceAlert", 0, "[]", "warning", "2026-08-01", None,
                "[]", None,
            ),
        )
        conn.commit()
        current = AlertItem(
            entry_id="ht-alert-current",
            title="Καθυστέρηση",
            title_en="Delay",
            title_sq="Vonesë",
            title_it="Ritardo",
            summary="",
            summary_en="",
            summary_sq="",
            summary_it="",
            url="https://example.com",
            published_at="2026-08-05",
            line_category="IC",
        )

        upsert_ht_alerts(conn, [current])

        ids = {row[0] for row in conn.execute("SELECT id FROM announcements")}
        self.assertEqual(ids, {"ht-alert-current", "stasy-current"})


if __name__ == "__main__":
    unittest.main()
