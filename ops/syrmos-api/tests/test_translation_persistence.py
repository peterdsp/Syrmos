"""A failed translation must never blank a translation we already have.

The live feed shipped every announcement with empty `titleEn`/`titleSq`/`titleIt`
because the free providers rate limit, `translate_from_greek` returns "" when
that happens, and the announcement upsert wrote that "" straight over whatever
was stored. Italian was already protected; English and Albanian were not.
"""

import sqlite3
import unittest

from syrmos_admin.scraper_ht_important_info import AlertItem, upsert as upsert_alerts


def make_conn() -> sqlite3.Connection:
    conn = sqlite3.connect(":memory:")
    conn.row_factory = sqlite3.Row
    conn.executescript(
        """
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
        """
    )
    return conn


def alert(**overrides) -> AlertItem:
    fields = dict(
        entry_id="ht-alert",
        title="Καθυστέρηση",
        title_en="Delay",
        title_sq="Vonesë",
        title_it="Ritardo",
        summary="Επίσημη ανακοίνωση",
        summary_en="Official notice",
        summary_sq="Njoftim zyrtar",
        summary_it="Avviso ufficiale",
        url="https://example.com",
        published_at="2026-08-05",
        line_category="IC",
    )
    fields.update(overrides)
    return AlertItem(**fields)


class TranslationPersistenceTest(unittest.TestCase):
    def test_a_rate_limited_rerun_keeps_the_translations_it_already_had(self):
        conn = make_conn()
        upsert_alerts(conn, [alert()])

        # The provider is rate limiting, so every translation comes back "".
        upsert_alerts(conn, [alert(title_en="", title_sq="", title_it="",
                                   summary_en="", summary_sq="", summary_it="")])

        row = conn.execute("SELECT * FROM announcements WHERE id LIKE '%ht-alert%'").fetchone()
        self.assertIsNotNone(row, "the announcement is still there")
        self.assertEqual(row["title_en"], "Delay")
        self.assertEqual(row["title_sq"], "Vonesë")
        self.assertEqual(row["title_it"], "Ritardo")
        self.assertEqual(row["summary_en"], "Official notice")
        self.assertEqual(row["summary_sq"], "Njoftim zyrtar")

    def test_a_better_translation_still_replaces_the_old_one(self):
        conn = make_conn()
        upsert_alerts(conn, [alert(title_en="Delay", title_sq="")])
        upsert_alerts(conn, [alert(title_en="Service delay", title_sq="Vonesë shërbimi")])

        row = conn.execute("SELECT * FROM announcements WHERE id LIKE '%ht-alert%'").fetchone()
        self.assertEqual(row["title_en"], "Service delay")
        self.assertEqual(row["title_sq"], "Vonesë shërbimi", "a first translation still lands")

    def test_the_greek_source_is_always_refreshed(self):
        conn = make_conn()
        upsert_alerts(conn, [alert()])
        upsert_alerts(conn, [alert(title="Ακύρωση", title_en="")])

        row = conn.execute("SELECT * FROM announcements WHERE id LIKE '%ht-alert%'").fetchone()
        self.assertEqual(row["title"], "Ακύρωση", "the source wording tracks the operator")
        self.assertEqual(row["title_en"], "Delay", "but a blank translation does not erase the old one")


if __name__ == "__main__":
    unittest.main()
