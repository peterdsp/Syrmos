# Syrmos 2.0.0 store privacy declarations (Apple App Privacy + Google Data Safety)

Factual mapping derived from the same code audit as the privacy policy
(`/privacy`). These are the values to enter in **App Store Connect** (App
Privacy) and the **Play Console** (Data Safety). Those console fields are
account-gated, so this file prepares them; it does not submit them. The three
must agree: this file, the privacy policy, and the console entries.

## Baseline facts (from the audit)

- **No tracking** (no IDFA / ATT / ad SDKs / cross-app tracking). Apple "Used to
  Track You" = none. Play "shared for advertising/tracking" = none.
- **No accounts**, no stable user/device identifier collected or sent.
- **No analytics, no third-party crash SDK, no ads.**
- Everything below is over **HTTPS (encrypted in transit)**.
- Nothing here is **Required to use the app**; the core timetable works offline.
- There is **no server-side user profile**, so account-level deletion is N/A;
  deletion = remove the app / clear site data. Feedback deletion by email.

## Apple App Privacy (App Store Connect)

"Used to Track You": **nothing.** Everything below is **App Functionality** and
is **Not Linked to your identity**, except an optional email you type into
feedback (which is Linked, because you supplied it as contact info).

| Data type | Collected (leaves device)? | Linked to you? | Purpose |
|---|---|---|---|
| Precise/Coarse Location | Only for weather, and only on Android (iOS uses a city/station anchor, not device GPS) | No | App Functionality |
| Contact Info - Email Address | Only if you type one into the feedback form | Yes (you provided it) | Customer Support |
| User Content - other (assistant question text; feedback message; optional feedback photo/video) | Yes, when you send it | No | App Functionality / Customer Support |
| Identifiers | None | - | - |
| Usage Data / Diagnostics | Not collected off-device (iOS diagnostics stay on device unless you tap Share) | - | - |

Notes for the reviewer / consistency: location is used **on-device** for
nearest-station on all platforms; the only off-device location is the Android
weather request. The assistant question text is relayed by the Syrmos API to AI
providers to answer; it is not linked to an identity.

## Google Play Data Safety (Play Console)

"Data shared with third parties": the assistant question text is processed by
third-party AI providers via the Syrmos API (declare **Shared** for that item);
weather coordinates go to a weather provider. Nothing is shared for advertising
or tracking. "Encrypted in transit": **Yes** for everything. "You can request
data deletion": there is no account; feedback deletion is by email (state this
in the listing).

| Data type | Collected? | Shared? | Processed ephemerally? | Optional | Purpose | Encrypted in transit |
|---|---|---|---|---|---|---|
| Location - approximate | Yes (Android weather only) | To the weather provider | Not stored server-side by Syrmos | Optional (needs permission) | App functionality | Yes |
| Personal info - Email address | Only if entered in feedback | No | No (kept to reply) | Optional | Customer support | Yes |
| Messages / other user content (feedback text, optional media, assistant question) | Yes, when you send it | Assistant text: to AI providers via the Syrmos API | Assistant text not persisted by the client | Optional | App functionality / support | Yes |
| App activity / analytics | No | No | - | - | - | - |
| Device or other IDs | No | No | - | - | - | - |

Community status reports (Ichnos) send an anonymous one-off token + a stop/line +
a signal (e.g. crowded); no personal data and no stable identifier. Declare as
app-functionality user content if the console requires it; it carries nothing
identifiable.

## Consistency check (must all agree)

| Claim | Privacy policy | Apple | Play |
|---|---|---|---|
| No tracking / no ads / no IDFA | stated | Used to Track: none | Advertising: none |
| No accounts / no stable ID | stated | Identifiers: none | Device IDs: none |
| Location off-device only for Android weather | stated | Location, not linked | Location approximate, weather |
| Assistant text -> AI providers | stated | User content, not linked | Shared with AI providers |
| Feedback email optional -> support | stated | Contact info, linked | Personal info, support |
| Encrypted in transit | HTTPS stated | - | Yes |

If any console entry cannot match a row above, fix the entry, not the audit.
