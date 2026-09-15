# Syrmos 3.0.0 store privacy declarations (Apple App Privacy + Google Data Safety)

Carries forward the audited 2.0.0 declarations. The 3.0 "Journeys" work added
planning, guidance, recovery and native glance surfaces (Live Activity, ongoing
notification, leave-by reminders) — **none of which collect or transmit any new
data.** The values to enter in App Store Connect (App Privacy) and the Play
Console (Data Safety) are therefore **unchanged from 2.0.0**; see
`STORE-PRIVACY-DECLARATIONS-2.0.0.md` for the full audited tables, which remain
authoritative. This file records only the 3.0 delta.

## 3.0 feature delta — privacy impact

| 3.0 feature | Data behaviour | New data collected/shared? |
|---|---|---|
| Journey planning / guidance / recovery (S02–S07, S10) | Computed on-device from the bundled seed + the existing timetable API over HTTPS; no new personal data. Saved journeys and the active GO session persist **locally** (localStorage / NSUserDefaults / SharedPreferences). | No |
| Saved journeys / recents / resume (S08) | Stored locally on the device only. | No |
| Native journey glances — iOS Live Activity, Android ongoing notification (J08) | Render the on-device active-journey state; nothing leaves the device. | No |
| Leave-by reminders + saved-departure board (J09) | Fully on-device: the board persists locally; reminders are scheduled with the local OS scheduler (iOS `UNUserNotificationCenter`, Android `AlarmManager`, web foreground `setTimeout`). No server, no push, no data transmitted. | No |

No new data **types**, no new off-device flows, no tracking, no accounts, no
analytics. The only off-device flows remain the ones audited for 2.0.0 (Android
coarse-location weather request coarsened to ~1 km; assistant question text to AI
providers via the Syrmos API; optional feedback email/media). Everything over
HTTPS.

## New permissions (rationale for reviewers)

- **Android `SCHEDULE_EXACT_ALARM`** (new in 3.0): schedules the user-requested
  "leave now" reminder at the correct minute. It is used only for reminders the
  user explicitly sets, degrades to an inexact alarm when not granted, and drives
  no data collection. (`POST_NOTIFICATIONS` was already declared in 2.0.0.)
- **iOS** uses the existing `UNUserNotificationCenter` authorization (already
  covered); leave-by reminders are local notifications, no push entitlement.

## Deletion / retention

Unchanged from 2.0.0: no server-side user profile, so account deletion is N/A;
deletion = remove the app / clear site data; saved journeys and reminders are
local and removed with the app. Feedback deletion by email.

## Consistency check

The 2.0.0 consistency table (`STORE-PRIVACY-DECLARATIONS-2.0.0.md`) still holds
row-for-row; add one line: **Leave-by reminders / saved journeys are on-device
only, never collected** — Apple: not collected; Play: not collected. The privacy
policy (`/privacy`) should state that saved journeys and reminders stay on the
device.
