#!/usr/bin/env bash
#
# Deploy Syrmos web app to GitHub Pages.
# The site is served from GitHub Pages (via Fastly CDN), not from the Pi.
# The Pi only hosts the live train SSE proxy at api-syrmos.peterdsp.dev.
#
# Usage:
#   ./scripts/deploy-web-to-pi.sh          # build, commit, push (triggers GH Actions deploy)
#   ./scripts/deploy-web-to-pi.sh --skip-build   # just push (if already built)
#

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ "${1:-}" != "--skip-build" ]]; then
    echo "==> Building web distribution..."
    ./gradlew :composeApp:wasmJsBrowserDistribution
fi

echo "==> Pushing to main (triggers GitHub Pages deploy)..."
echo "    Make sure your changes are committed first."
echo ""
echo "GitHub Actions will:"
echo "  1. Build the Compose/Wasm distribution"
echo "  2. Version-stamp CSS/JS with content hashes"
echo "  3. Deploy to GitHub Pages"
echo "  4. Fastly CDN automatically purges on deploy"
echo ""
echo "Live train proxy: https://api-syrmos.peterdsp.dev/api/train-stream (Pi nginx)"
