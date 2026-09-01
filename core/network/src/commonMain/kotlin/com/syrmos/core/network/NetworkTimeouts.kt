package com.syrmos.core.network

/**
 * Per-request timeout for the LIVE and DEPARTURES calls (live-positions,
 * station-offsets, projected departures, railway.gov live trains).
 *
 * The shared HttpClient carries a 30s default so background sync (manifest,
 * per-line bundles, fares) can ride out a slow link. But the live/departures
 * calls feed an interactive, self-refreshing UI: when the Pi is unreachable
 * (down, LAN-only, captive portal) a user should drop to the bundled seed /
 * simulated layer in a few seconds, not stare at a spinner for 30. iOS already
 * uses per-call 6-10s timeouts here; this brings Android to parity.
 *
 * Kept well below the 30s client default on purpose: raising it back toward the
 * default would defeat the fast fallback, so [NetworkTimeoutsTest] pins it to
 * the fast range.
 */
internal const val LIVE_REQUEST_TIMEOUT_MS = 8_000L
