#!/usr/bin/env bash
# Guard the vendored Ariadne native libs against the two failure modes that have
# each already shipped once, silently:
#
#   1. 4 KB page alignment  -> Google Play rejects the bundle ("does not support
#      16 KB memory page sizes"), and on a 16 KB-page device the libs fail to
#      load at runtime.
#   2. libc++_shared.so dependency -> dlopen fails ("library libc++_shared.so not
#      found") because we do not package the shared C++ runtime. LlamaBridge
#      catches it, available=false, and Ariadne drops to the rule parser with no
#      crash and no visible error. This is how the on-device LLM shipped dead.
#
# Both are invisible at build time and invisible in the UI, so they must be
# asserted mechanically. See composeApp/src/androidMain/jniLibs/README.md.
#
# Usage: ./scripts/check-native-libs.sh [jniLibs-dir]

set -euo pipefail

cd "$(dirname "$0")/.."

LIB_DIR="${1:-composeApp/src/androidMain/jniLibs}"
FAIL=0

# llvm-readelf (NDK or PATH) preferred; GNU readelf is fine too and is present on
# stock CI runners. Both print the LOAD align as the last field of -l output and
# list NEEDED entries in -d output.
find_readelf() {
  if command -v llvm-readelf >/dev/null; then command -v llvm-readelf; return; fi
  local ndk c
  for ndk in "${ANDROID_NDK_HOME:-}" "${ANDROID_NDK_ROOT:-}" "$HOME"/Library/Android/sdk/ndk/* "${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"/ndk/*; do
    [ -n "$ndk" ] || continue
    for c in "$ndk"/toolchains/llvm/prebuilt/*/bin/llvm-readelf; do
      [ -x "$c" ] && { echo "$c"; return; }
    done
  done
  command -v readelf 2>/dev/null || true
}

READELF="$(find_readelf || true)"
if [ -z "$READELF" ]; then
  echo "ERROR: no readelf found. Install binutils, or the Android NDK, or put llvm-readelf on PATH." >&2
  exit 2
fi

# 64-bit ABIs only: the 16 KB requirement does not apply to 32-bit ABIs.
SIXTEEN_K_ABIS="arm64-v8a x86_64"

shopt -s nullglob
CHECKED=0
for abi_dir in "$LIB_DIR"/*/; do
  abi="$(basename "$abi_dir")"
  for so in "$abi_dir"*.so; do
    name="$(basename "$so")"
    CHECKED=$((CHECKED + 1))

    # --- check 1: 16 KB alignment (64-bit ABIs only) ---
    case " $SIXTEEN_K_ABIS " in
      *" $abi "*)
        align="$("$READELF" -l "$so" 2>/dev/null | awk '/LOAD/{print $NF}' | sort -u | tail -1)"
        if [ "$align" != "0x4000" ]; then
          echo "FAIL  $abi/$name: LOAD align $align, want 0x4000 (rebuild with -Wl,-z,max-page-size=16384)"
          FAIL=1
        fi
        ;;
    esac

    # --- check 2: no libc++_shared.so dependency ---
    if "$READELF" -d "$so" 2>/dev/null | grep -q "libc++_shared"; then
      echo "FAIL  $abi/$name: needs libc++_shared.so, which we do not package (rebuild with -static-libstdc++)"
      FAIL=1
    fi
  done
done
shopt -u nullglob

if [ "$CHECKED" = "0" ]; then
  echo "ERROR: no .so found under $LIB_DIR" >&2
  exit 2
fi

if [ "$FAIL" = "0" ]; then
  echo "OK: $CHECKED native libs are 16 KB-aligned and free of libc++_shared.so"
else
  echo
  echo "See composeApp/src/androidMain/jniLibs/README.md for the build recipe."
  exit 1
fi
