import datetime as dt
import sqlite3
import unittest

from syrmos_admin.community import delete_report, summary, upsert_report


def make_conn() -> sqlite3.Connection:
    conn = sqlite3.connect(":memory:")
    conn.row_factory = sqlite3.Row
    conn.executescript(
        """
        CREATE TABLE community_reports (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            report_id TEXT NOT NULL UNIQUE,
            scope_id TEXT NOT NULL,
            scope_label TEXT NOT NULL,
            signal TEXT NOT NULL,
            detail TEXT,
            platform TEXT NOT NULL,
            locale TEXT,
            created_at TEXT NOT NULL,
            expires_at TEXT NOT NULL
        );
        """
    )
    return conn


class CommunityReportingTest(unittest.TestCase):
    def setUp(self):
        self.conn = make_conn()
        self.now = dt.datetime(2026, 8, 6, 9, 0, tzinfo=dt.timezone.utc)

    def payload(self, signal: str = "normal") -> dict[str, str]:
        return {
            "reportId": "report_1234567890",
            "scopeId": "M1_KAL",
            "scopeLabel": "Kallithea",
            "signal": signal,
            "detail": "",
            "platform": "ios",
            "locale": "en",
        }

    def test_normal_mode_uses_labeled_estimate_when_no_issue_exists(self):
        result = summary(self.conn, scope_id="M1_KAL", now=self.now)
        self.assertEqual(result["displayMode"], "normal")
        self.assertGreater(result["estimatedJourneysToday"], 0)
        self.assertEqual(result["issues"], [])

    def test_one_issue_hides_the_normal_estimate(self):
        upsert_report(self.conn, self.payload("delayed"), now=self.now)
        result = summary(self.conn, scope_id="M1_KAL", now=self.now)
        self.assertEqual(result["displayMode"], "issues")
        self.assertIsNone(result["estimatedJourneysToday"])
        self.assertEqual(result["issues"][0]["signal"], "delayed")

    def test_same_report_id_refines_instead_of_duplicating(self):
        upsert_report(self.conn, self.payload("crowded"), now=self.now)
        upsert_report(self.conn, self.payload("stopped"), now=self.now)
        result = summary(self.conn, scope_id="M1_KAL", now=self.now)
        self.assertEqual(result["activeIssueCount"], 1)
        self.assertEqual(result["issues"][0]["signal"], "stopped")

    def test_undo_removes_the_report(self):
        upsert_report(self.conn, self.payload("safety"), now=self.now)
        self.assertTrue(delete_report(self.conn, "report_1234567890"))
        self.assertEqual(summary(self.conn, scope_id="M1_KAL", now=self.now)["displayMode"], "normal")

    def test_expired_issue_does_not_hide_normal_state(self):
        old = self.now - dt.timedelta(hours=3)
        upsert_report(self.conn, self.payload("facilities"), now=old)
        self.assertEqual(summary(self.conn, scope_id="M1_KAL", now=self.now)["displayMode"], "normal")


if __name__ == "__main__":
    unittest.main()
