#!/usr/bin/env python3
"""Promote an Android release from the Play internal track to production.

`release-android.yml` uploads every build to the internal track only, so the
public store listing stays on whatever was last promoted. This takes a release
that is already on internal (by version code, or the newest one), copies it to
production with its release notes, and commits the edit, which sends it to
Google's review. Nothing is rebuilt or re-uploaded.

Usage (CI, see .github/workflows/promote-android.yml):
  PLAY_SERVICE_ACCOUNT_JSON=... python3 scripts/play_promote.py \
      [--version-code 272] [--rollout 100] [--dry-run]

--dry-run validates the edit with Google and then discards it: nothing is
published. --status changes nothing: it prints both tracks and asks Google
whether changes are waiting in review. --rollout below 100 makes a staged rollout to that share of users.
"""

import argparse
import json
import os
import sys

PACKAGE = "com.syrmos.android"
API = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}/edits"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"


def pick_release(internal_track, version_code=None):
    """The internal release to promote: the one holding `version_code`, or the
    one with the highest version code."""
    releases = [r for r in internal_track.get("releases", []) if r.get("versionCodes")]
    if not releases:
        raise SystemExit("The internal track has no release with a version code.")
    if version_code is not None:
        for r in releases:
            if str(version_code) in r["versionCodes"]:
                return r
        found = sorted({int(v) for r in releases for v in r["versionCodes"]})
        raise SystemExit(f"Version code {version_code} is not on the internal track (found {found}).")
    return max(releases, key=lambda r: max(int(v) for v in r["versionCodes"]))


def production_track(release, rollout=100):
    """The production track body for `release`: full rollout, or a staged one."""
    if not 0 < rollout <= 100:
        raise SystemExit("--rollout must be between 1 and 100.")
    body = {"versionCodes": [str(v) for v in release["versionCodes"]]}
    if release.get("name"):
        body["name"] = release["name"]
    if release.get("releaseNotes"):
        body["releaseNotes"] = release["releaseNotes"]
    if rollout == 100:
        body["status"] = "completed"
    else:
        body["status"] = "inProgress"
        body["userFraction"] = round(rollout / 100, 4)
    return {"track": "production", "releases": [body]}


def session(service_account_json):
    from google.auth.transport.requests import AuthorizedSession
    from google.oauth2 import service_account

    creds = service_account.Credentials.from_service_account_info(json.loads(service_account_json),
                                                                  scopes=[SCOPE])
    return AuthorizedSession(creds)


def call(s, method, url, **kw):
    r = s.request(method, url, timeout=60, **kw)
    if r.status_code >= 400:
        raise SystemExit(f"{method} {url} -> {r.status_code}: {r.text}")
    return r.json() if r.content else {}


def status(s):
    """Print both tracks, then commit an empty edit that Google refuses if
    changes are in review. The edit holds no changes, so nothing is sent.
    (This app sends changes for review automatically, so Google rejects
    changesNotSentForReview.)"""
    edit = call(s, "POST", API)["id"]
    for track in ("internal", "production"):
        print(f"{track}: {json.dumps(call(s, 'GET', f'{API}/{edit}/tracks/{track}'), ensure_ascii=False)}")
    r = s.request("POST", f"{API}/{edit}:commit", timeout=60,
                  params={"changesInReviewBehavior": "ERROR_IF_IN_REVIEW"})
    if r.status_code < 400:
        print("Review: nothing in review (Google accepted an empty commit while asked to refuse if in review).")
    else:
        print(f"Review: Google refused ({r.status_code}): {r.text}")
        call(s, "DELETE", f"{API}/{edit}")
    return 0


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--version-code", type=int, default=None)
    ap.add_argument("--rollout", type=float, default=100.0)
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--status", action="store_true")
    args = ap.parse_args(argv)

    key = os.environ.get("PLAY_SERVICE_ACCOUNT_JSON")
    if not key:
        raise SystemExit("PLAY_SERVICE_ACCOUNT_JSON is not set.")
    s = session(key)

    if args.status:
        return status(s)
    edit = call(s, "POST", API)["id"]
    try:
        internal = call(s, "GET", f"{API}/{edit}/tracks/internal")
        current = call(s, "GET", f"{API}/{edit}/tracks/production")
        live = [v for r in current.get("releases", []) for v in r.get("versionCodes", [])]
        release = pick_release(internal, args.version_code)
        body = production_track(release, args.rollout)
        print(f"Production now: version codes {live or 'none'}")
        print(f"Promoting: {json.dumps(body['releases'][0], ensure_ascii=False)}")
        call(s, "PUT", f"{API}/{edit}/tracks/production", json=body)
        if args.dry_run:
            call(s, "POST", f"{API}/{edit}:validate")
            print("Dry run: Google validated the edit. Nothing was published.")
            return 0
        call(s, "POST", f"{API}/{edit}:commit")
        edit = None
        print("Committed: the release is sent to Google's review for production.")
        return 0
    finally:
        if edit:
            call(s, "DELETE", f"{API}/{edit}")


if __name__ == "__main__":
    sys.exit(main())
