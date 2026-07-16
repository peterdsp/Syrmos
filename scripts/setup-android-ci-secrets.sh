#!/usr/bin/env bash
# Push the Android release secrets to GitHub Actions.
#
# Four of the five secrets already exist on your machine:
#   - androidApp/syrmos-release.keystore  (the signing key)
#   - local.properties                    (store password, key alias, key password)
# This script reads them locally and sets them as repo secrets via `gh`.
# Nothing is printed; values go straight from your disk to GitHub.
#
# The fifth (PLAY_SERVICE_ACCOUNT_JSON) can't be derived from anything local —
# pass the downloaded Google service-account JSON as the first argument:
#
#   ./scripts/setup-android-ci-secrets.sh ~/Downloads/syrmos-play-ci-xxxx.json
#
# Omit the argument to set only the four keystore secrets.
#
# See docs/ops/RELEASE.md.

set -euo pipefail

cd "$(dirname "$0")/.."

KEYSTORE="androidApp/syrmos-release.keystore"
LOCAL_PROPS="local.properties"
PLAY_JSON="${1:-}"

fail() { echo "ERROR: $*" >&2; exit 1; }

command -v gh >/dev/null || fail "gh CLI not found. brew install gh"
gh auth status >/dev/null 2>&1 || fail "gh not authenticated. Run: gh auth login"

[ -f "$KEYSTORE" ] || fail "keystore not found at $KEYSTORE"
[ -f "$LOCAL_PROPS" ] || fail "$LOCAL_PROPS not found"

prop() {
  grep -E "^$1[[:space:]]*=" "$LOCAL_PROPS" | head -1 | cut -d'=' -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//'
}

STORE_PASSWORD="$(prop RELEASE_STORE_PASSWORD)"
KEY_ALIAS="$(prop RELEASE_KEY_ALIAS)"
KEY_PASSWORD="$(prop RELEASE_KEY_PASSWORD)"

[ -n "$STORE_PASSWORD" ] || fail "RELEASE_STORE_PASSWORD missing from $LOCAL_PROPS"
[ -n "$KEY_ALIAS" ]      || fail "RELEASE_KEY_ALIAS missing from $LOCAL_PROPS"
[ -n "$KEY_PASSWORD" ]   || fail "RELEASE_KEY_PASSWORD missing from $LOCAL_PROPS"

# Verify the keystore actually opens with the stored password before uploading
# anything, so we never push a secret set that can't sign.
if command -v keytool >/dev/null; then
  keytool -list -keystore "$KEYSTORE" -storepass "$STORE_PASSWORD" -alias "$KEY_ALIAS" >/dev/null 2>&1 \
    || fail "keystore did not open with the password/alias in $LOCAL_PROPS"
  echo "verified: keystore opens with alias '$KEY_ALIAS'"
fi

echo "setting ANDROID_KEYSTORE_BASE64 ..."
base64 -i "$KEYSTORE" | gh secret set ANDROID_KEYSTORE_BASE64

echo "setting RELEASE_STORE_PASSWORD ..."
printf '%s' "$STORE_PASSWORD" | gh secret set RELEASE_STORE_PASSWORD

echo "setting RELEASE_KEY_ALIAS ..."
printf '%s' "$KEY_ALIAS" | gh secret set RELEASE_KEY_ALIAS

echo "setting RELEASE_KEY_PASSWORD ..."
printf '%s' "$KEY_PASSWORD" | gh secret set RELEASE_KEY_PASSWORD

if [ -n "$PLAY_JSON" ]; then
  [ -f "$PLAY_JSON" ] || fail "Play service-account JSON not found at $PLAY_JSON"
  python3 -c "import json,sys; json.load(open(sys.argv[1]))" "$PLAY_JSON" \
    || fail "$PLAY_JSON is not valid JSON"
  echo "setting PLAY_SERVICE_ACCOUNT_JSON ..."
  gh secret set PLAY_SERVICE_ACCOUNT_JSON < "$PLAY_JSON"
else
  echo
  echo "NOTE: PLAY_SERVICE_ACCOUNT_JSON not set (no JSON argument given)."
  echo "  Create it: Google Cloud Console > IAM & Admin > Service Accounts >"
  echo "  Create service account > Keys > Add key > JSON. Then grant it access in"
  echo "  Play Console > Users and permissions (app com.syrmos.android,"
  echo "  Releases > manage testing-track releases). Then re-run this script with"
  echo "  the JSON path as the first argument."
fi

echo
echo "current Android-related repo secrets:"
gh secret list | grep -iE "ANDROID|RELEASE|PLAY" || true

echo
echo "Next: re-run the Android release for the tag:"
echo "  gh workflow run release-android.yml --ref v1.2.2"
