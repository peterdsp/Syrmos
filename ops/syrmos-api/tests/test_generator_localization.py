import sqlite3
import unittest
from datetime import date

from syrmos_admin.generator import _build_news


class GeneratorLocalizationContractTest(unittest.TestCase):
    def test_missing_news_translations_are_not_serialized_as_greek(self):
        conn = sqlite3.connect(":memory:")
        conn.row_factory = sqlite3.Row
        conn.execute(
            """
            CREATE TABLE rail_news (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                title_en TEXT,
                title_sq TEXT,
                title_it TEXT,
                summary TEXT,
                summary_en TEXT,
                summary_sq TEXT,
                summary_it TEXT,
                url TEXT,
                published_at TEXT,
                thumbnail_url TEXT,
                categories TEXT
            )
            """
        )
        conn.execute(
            """
            INSERT INTO rail_news VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                "story-1",
                "Νέα δρομολόγια",
                "",
                "",
                "Nuovi orari",
                "Επίσημη ανακοίνωση",
                "",
                "",
                "Avviso ufficiale",
                "https://example.com/story-1",
                date.today().isoformat(),
                "",
                "[]",
            ),
        )
        conn.commit()

        item = _build_news(conn)["news"][0]

        self.assertEqual(item["titleEn"], "")
        self.assertEqual(item["summaryEn"], "")
        self.assertEqual(item["titleIt"], "Nuovi orari")
        self.assertEqual(item["summaryIt"], "Avviso ufficiale")


if __name__ == "__main__":
    unittest.main()
