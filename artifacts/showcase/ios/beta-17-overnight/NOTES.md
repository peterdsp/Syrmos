# iOS overnight evidence (Saturday 24h fix)

Device: iPhone 17 simulator. Build: local Debug of scheme "Syrmos - Athens Rail
Times" with the offline-projection + honest-status changes. Captured ~04:25
Sunday Athens time (Saturday 24h overnight window), against the corrected Pi.

- `01-map-overnight-trains.png` — map shows live metro/tram vehicles at 04:2x
  (M1/M2/M3/T6/T7), i.e. Saturday's 24h service continuing past midnight.
- `02-panepistimio-overnight-departures.png` — Panepistimio station sheet shows
  Line 2 (M2) departures at 04:32 / 04:39 / 04:47 ("Πρόγραμμα" = scheduled),
  the overnight service that returned zero before the fix.

Full iOS build (`xcodebuild build`, watchOS runtime installed) BUILD SUCCEEDED
with the changes; the same build feeds CI on the PR.
