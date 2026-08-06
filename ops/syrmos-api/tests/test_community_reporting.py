import datetime as dt
import sqlite3
import unittest

from syrmos_admin.community import delete_report, history, summary, upsert_report


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
        CREATE TABLE community_report_daily (
            report_day TEXT NOT NULL,
            scope_id TEXT NOT NULL,
            scope_label TEXT NOT NULL,
            signal TEXT NOT NULL,
            report_count INTEGER NOT NULL DEFAULT 0 CHECK (report_count >= 0),
            updated_at TEXT NOT NULL,
            PRIMARY KEY (report_day, scope_id, signal)
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
        bucket = history(self.conn, period="day", now=self.now)["buckets"][0]
        self.assertEqual(bucket["totalReports"], 1)
        self.assertEqual(bucket["counts"]["crowded"], 0)
        self.assertEqual(bucket["counts"]["stopped"], 1)

    def test_undo_removes_the_report(self):
        upsert_report(self.conn, self.payload("safety"), now=self.now)
        self.assertTrue(delete_report(self.conn, "report_1234567890", now=self.now))
        self.assertEqual(summary(self.conn, scope_id="M1_KAL", now=self.now)["displayMode"], "normal")
        self.assertEqual(history(self.conn, period="day", now=self.now)["buckets"], [])

    def test_expired_issue_does_not_hide_normal_state(self):
        old = self.now - dt.timedelta(hours=3)
        upsert_report(self.conn, self.payload("facilities"), now=old)
        self.assertEqual(summary(self.conn, scope_id="M1_KAL", now=self.now)["displayMode"], "normal")

    def test_history_keeps_daily_counts_after_raw_report_cleanup(self):
        old = self.now - dt.timedelta(days=8)
        upsert_report(self.conn, self.payload("normal"), now=old)
        summary(self.conn, now=self.now)
        self.assertEqual(self.conn.execute("SELECT COUNT(*) FROM community_reports").fetchone()[0], 0)
        buckets = history(self.conn, period="day", now=self.now)["buckets"]
        self.assertEqual(len(buckets), 1)
        self.assertEqual(buckets[0]["positiveReports"], 1)
        self.assertEqual(buckets[0]["issueReports"], 0)

    def test_history_groups_days_into_months_and_years(self):
        upsert_report(self.conn, self.payload("normal"), now=self.now)
        second = self.payload("delayed")
        second["reportId"] = "report_abcdefghijk1"
        upsert_report(self.conn, second, now=self.now + dt.timedelta(days=1))
        month = history(self.conn, period="month", now=self.now)["buckets"][0]
        year = history(self.conn, period="year", now=self.now)["buckets"][0]
        self.assertEqual(month["totalReports"], 2)
        self.assertEqual(month["positiveReports"], 1)
        self.assertEqual(month["issueReports"], 1)
        self.assertEqual(year["counts"]["normal"], 1)
        self.assertEqual(year["counts"]["delayed"], 1)

    def test_history_can_filter_by_scope(self):
        upsert_report(self.conn, self.payload("normal"), now=self.now)
        other = self.payload("crowded")
        other["reportId"] = "report_abcdefghijk2"
        other["scopeId"] = "M1_MON"
        other["scopeLabel"] = "Monastiraki"
        upsert_report(self.conn, other, now=self.now)
        buckets = history(self.conn, period="day", scope_id="M1_KAL", now=self.now)["buckets"]
        self.assertEqual(buckets[0]["totalReports"], 1)
        self.assertEqual(buckets[0]["counts"]["normal"], 1)

    def test_history_rejects_invalid_granularity_and_limit(self):
        with self.assertRaises(ValueError):
            history(self.conn, period="week")
        with self.assertRaises(ValueError):
            history(self.conn, limit=0)


if __name__ == "__main__":
    unittest.main()
