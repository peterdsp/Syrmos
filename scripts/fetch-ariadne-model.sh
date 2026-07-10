#!/usr/bin/env bash
#
# Download + verify the Ariadne on-device model (GGUF) for a release build.
#
# The model is ~1.1 GB and is intentionally NOT committed to the repo. CI (and
# any local release build) runs this to fetch it from the pinned source and
# verify its SHA-256 before packaging it per platform. The single source of
# truth for filename / URL / checksum is the Kotlin manifest:
#   core/common/src/commonMain/kotlin/com/syrmos/core/common/AriadneModelManifest.kt
# so this script parses those constants instead of duplicating them.
#
# Usage:
#   scripts/fetch-ariadne-model.sh <dest-dir>
# Writes <dest-dir>/<FILE_NAME>. Idempotent: if a file with the right checksum
# is already there, it skips the download.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT_DIR/core/common/src/commonMain/kotlin/com/syrmos/core/common/AriadneModelManifest.kt"
DEST_DIR="${1:?usage: fetch-ariadne-model.sh <dest-dir>}"

# Pull a `const val NAME: String = "value"` constant out of the Kotlin manifest.
# The value may sit on the same line or the following line, so scan forward from
# the declaration to the first double-quoted string.
kt_const () {
  awk -v key="$1" '
    index($0, "val " key ": String") { armed = 1 }
    armed {
      if (match($0, /"[^"]*"/)) {
        s = substr($0, RSTART + 1, RLENGTH - 2)
        print s
        exit
      }
    }
  ' "$MANIFEST"
}

FILE_NAME="$(kt_const FILE_NAME)"
URL="$(kt_const URL)"
SHA256="$(kt_const SHA256)"

if [ -z "$FILE_NAME" ] || [ -z "$URL" ]; then
  echo "ERROR: could not parse FILE_NAME/URL from $MANIFEST" >&2
  exit 1
fi
if [ -z "$SHA256" ]; then
  echo "ERROR: AriadneModelManifest.SHA256 is empty; refusing to ship an unverified model." >&2
  exit 1
fi

mkdir -p "$DEST_DIR"
OUT="$DEST_DIR/$FILE_NAME"

sha_of () { # portable sha256 -> stdout
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | awk '{print $1}';
  else shasum -a 256 "$1" | awk '{print $1}'; fi
}

if [ -f "$OUT" ] && [ "$(sha_of "$OUT")" = "$SHA256" ]; then
  echo "Model already present and verified: $OUT"
  exit 0
fi

echo "Downloading $FILE_NAME (~1.1 GB) from $URL"
curl -fL --retry 3 --retry-delay 5 -o "$OUT" "$URL"

GOT="$(sha_of "$OUT")"
if [ "$GOT" != "$SHA256" ]; then
  echo "ERROR: checksum mismatch for $FILE_NAME" >&2
  echo "  expected $SHA256" >&2
  echo "  got      $GOT" >&2
  rm -f "$OUT"
  exit 1
fi
echo "Verified $OUT"
