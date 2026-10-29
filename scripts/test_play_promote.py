"""Tests for play_promote: `python3 -m unittest scripts/test_play_promote.py`.

They cover the part that decides what reaches production, without network.
"""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import play_promote as P  # noqa: E402

NOTES = [{"language": "en-US", "text": "Calendar leave-by reminders."},
         {"language": "el-GR", "text": "Υπενθυμίσεις αναχώρησης."}]
INTERNAL = {"track": "internal", "releases": [
    {"name": "3.0.1 (270)", "versionCodes": ["270"], "status": "completed"},
    {"name": "3.0.2 (272)", "versionCodes": ["272"], "status": "completed", "releaseNotes": NOTES},
    {"name": "3.0.2 (271)", "versionCodes": ["271"], "status": "completed"},
]}


class PlayPromoteTest(unittest.TestCase):
    def test_picks_the_newest_internal_release_by_default(self):
        self.assertEqual(P.pick_release(INTERNAL)["versionCodes"], ["272"])

    def test_picks_a_requested_version_code(self):
        self.assertEqual(P.pick_release(INTERNAL, 270)["name"], "3.0.1 (270)")

    def test_a_missing_version_code_stops_with_what_exists(self):
        with self.assertRaises(SystemExit) as e:
            P.pick_release(INTERNAL, 999)
        self.assertIn("[270, 271, 272]", str(e.exception))

    def test_an_empty_internal_track_stops(self):
        with self.assertRaises(SystemExit):
            P.pick_release({"track": "internal", "releases": []})

    def test_full_rollout_keeps_name_codes_and_release_notes(self):
        body = P.production_track(P.pick_release(INTERNAL), 100)
        self.assertEqual(body["track"], "production")
        rel = body["releases"][0]
        self.assertEqual(rel, {"versionCodes": ["272"], "name": "3.0.2 (272)", "releaseNotes": NOTES,
                               "status": "completed"})

    def test_staged_rollout(self):
        rel = P.production_track(P.pick_release(INTERNAL), 20)["releases"][0]
        self.assertEqual(rel["status"], "inProgress")
        self.assertEqual(rel["userFraction"], 0.2)

    def test_rollout_out_of_range_stops(self):
        for bad in (0, -5, 101):
            with self.assertRaises(SystemExit):
                P.production_track(P.pick_release(INTERNAL), bad)

    def test_missing_key_stops_before_any_network_call(self):
        import os
        old = os.environ.pop("PLAY_SERVICE_ACCOUNT_JSON", None)
        try:
            with self.assertRaises(SystemExit) as e:
                P.main(["--dry-run"])
            self.assertIn("PLAY_SERVICE_ACCOUNT_JSON", str(e.exception))
        finally:
            if old is not None:
                os.environ["PLAY_SERVICE_ACCOUNT_JSON"] = old


class FakeResponse:
    def __init__(self, body, status=200):
        import json
        self.status_code, self._body = status, body
        self.content = json.dumps(body).encode() if body is not None else b""
        self.text = self.content.decode()

    def json(self):
        return self._body


class FakePlay:
    """Records the calls main() makes; answers like the Play edits API."""

    def __init__(self, fail_put=False, in_review=False):
        self.calls, self.fail_put, self.in_review = [], fail_put, in_review

    def request(self, method, url, timeout=None, json=None, params=None):
        path = url.replace(P.API, "")
        self.calls.append((method, path, json or params))
        if path.endswith(":commit") and params and self.in_review:
            return FakeResponse({"error": {"message": "Changes are in review"}}, 400)
        if method == "POST" and path == "":
            return FakeResponse({"id": "E1"})
        if method == "GET" and path.endswith("/tracks/internal"):
            return FakeResponse(INTERNAL)
        if method == "GET" and path.endswith("/tracks/production"):
            return FakeResponse({"track": "production", "releases": [{"versionCodes": ["140"]}]})
        if method == "PUT" and self.fail_put:
            return FakeResponse({"error": "forbidden"}, 403)
        return FakeResponse(None)


class MainFlowTest(unittest.TestCase):
    def run_main(self, play, *args):
        import os
        from unittest import mock
        with mock.patch.dict(os.environ, {"PLAY_SERVICE_ACCOUNT_JSON": "{}"}), \
                mock.patch.object(P, "session", return_value=play):
            return P.main(list(args))

    def test_dry_run_validates_and_discards(self):
        play = FakePlay()
        self.assertEqual(self.run_main(play, "--dry-run"), 0)
        ops = [(m, p) for m, p, _ in play.calls]
        self.assertIn(("POST", "/E1:validate"), ops)
        self.assertNotIn(("POST", "/E1:commit"), ops)
        self.assertEqual(ops[-1], ("DELETE", "/E1"))

    def test_real_run_commits_272_to_production(self):
        play = FakePlay()
        self.assertEqual(self.run_main(play, "--version-code", "272"), 0)
        put = [b for m, p, b in play.calls if m == "PUT"][0]
        self.assertEqual(put["releases"][0]["versionCodes"], ["272"])
        ops = [(m, p) for m, p, _ in play.calls]
        self.assertEqual(ops[-1], ("POST", "/E1:commit"))
        self.assertNotIn(("DELETE", "/E1"), ops)

    def test_a_failure_deletes_the_open_edit(self):
        play = FakePlay(fail_put=True)
        with self.assertRaises(SystemExit):
            self.run_main(play)
        self.assertEqual([(m, p) for m, p, _ in play.calls][-1], ("DELETE", "/E1"))

    def test_status_changes_nothing_and_reports_review(self):
        for in_review in (False, True):
            play = FakePlay(in_review=in_review)
            self.assertEqual(self.run_main(play, "--status"), 0)
            commits = [b for m, p, b in play.calls if p.endswith(":commit")]
            # The only commit is the empty probe: never sent for review, and
            # refused by Google if anything is already in review.
            self.assertEqual(commits, [{"changesNotSentForReview": "true",
                                        "changesInReviewBehavior": "ERROR_IF_IN_REVIEW"}])
            self.assertFalse([1 for m, p, b in play.calls if m == "PUT"])


if __name__ == "__main__":
    unittest.main()
