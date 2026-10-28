# Privacy Policy

**Last updated: October 27, 2026**

Syrmos is developed by Petros Dhespollari. This privacy policy explains how the app handles your data.

## Data Collection

Syrmos does **not** collect, store, or transmit any personal data.

### Location Data

Syrmos may request access to your device location to find the nearest transit station. Location data is processed entirely on your device and is never sent to any server, stored, or shared with third parties. Location access is optional and the app works fully without it.

### Background Location (Next Train Near a Station)

If you turn on "Next train near a station" in Settings (off by default), Syrmos asks for location access "all the time" so it can notice when you linger near a station and tell you when the next trains leave. The device monitors a short list of nearby stations itself (Android geofencing, iOS region monitoring). Your location is used only on your device to pick those stations and to estimate walking time. It is never sent to any server, stored outside your device, or shared. Turning the setting off stops the monitoring.

### Calendar

If you turn on "Calendar reminders" (off by default), Syrmos reads the events in your calendar for the next 48 hours, on your device only, to work out when you should leave to arrive on time (for example two hours before a flight). Event titles, locations and times never leave your device. To match an event location to a place, the app may use the operating system's built-in geocoder, which is provided by Apple or Google under their own privacy terms; only the location text is involved, never your identity. The Airport tab uses the same on-device calendar access to list upcoming flights.

### Push Notifications (Announcements from Syrmos)

Syrmos can show announcements such as station closures. To deliver them by push, the app registers an anonymous push token with api-syrmos.peterdsp.dev, together with the app language, the app version and a list of topics (for example "all", your selected region, and the lines and stations you marked as favourites). No account, name, email, location or device identifier is sent. On Android the token comes from Firebase Cloud Messaging (Google); on iOS from Apple Push Notification service. You can turn "Announcements from Syrmos" off in Settings at any time, which asks the server to delete the token. When push is off or unavailable, the app checks for new announcements itself and shows them as local notifications.

### Transit Schedule Data

All transit schedule data (station names, timetables, frequencies) is bundled within the app. No internet connection is required to view departure times.

### Live Train Data

Syrmos connects to the Hellenic Train public API (railway.gov.gr) to display real-time suburban railway positions. This connection transmits no personal information. Only train position data is received.

### Network Requests

The app makes network requests only to:
- **api-syrmos.peterdsp.dev** for the latest line, station, and schedule snapshot (offline-first; the bundled snapshot is used if this request fails)
- **railway.gov.gr** for live suburban train positions
- **stasy.gr** for service announcements (iOS only)
- **OpenStreetMap tile servers** for map tiles (Android, web)
- **api-syrmos.peterdsp.dev/api/closures** and **/api/broadcasts** for station closures and announcements (no personal data sent)
- **api-syrmos.peterdsp.dev/api/push/register** to register the anonymous push token described above, only while "Announcements from Syrmos" is on
- **Firebase Cloud Messaging** (Android) or **Apple Push Notification service** (iOS) to receive push notifications

No analytics, tracking, advertising, or telemetry services are used.

### External Ticket Purchase Link (Suburban Railway)

For suburban railway stations (A1, A2, A3, A4), Syrmos shows a button that opens **Hellenic Train's official ticket website** (`newtickets.hellenictrain.gr`) in your device's browser.

This is **only a link**. Tapping the button leaves the Syrmos app and continues in Hellenic Train's web flow. To be explicit:

- Syrmos **does not** see, intercept, store, transmit, or process any ticket purchase data — including names, payment cards, email addresses, travel dates, ticket numbers, or any other detail you enter on Hellenic Train's site.
- Syrmos **does not** have any business, contractual, or technical relationship with Hellenic Train. We are not a reseller, agent, or affiliate.
- Any ticket purchase, refund, cancellation, support issue, or dispute is **100% the responsibility of Hellenic Train** under their own terms of service and privacy policy.
- Once you leave the app, Hellenic Train's own privacy practices apply. We recommend reading them at hellenictrain.gr.

The button exists purely for user convenience — to save you from manually opening a browser and searching for the ticket page.

## Data Storage

All app data is stored locally on your device using a local database. No data is synced to cloud services.

## Third-Party Services

Syrmos does not integrate any third-party analytics, advertising, or tracking SDKs. On Android, Firebase Cloud Messaging is used only to deliver push announcements; Syrmos does not use Firebase Analytics or any other Firebase product.

## Children's Privacy

Syrmos does not knowingly collect information from children under 13.

## Changes

This policy may be updated occasionally. Changes will be noted with a revised date at the top of this page.

## Contact

For questions about this privacy policy, contact: info@peterdsp.dev
