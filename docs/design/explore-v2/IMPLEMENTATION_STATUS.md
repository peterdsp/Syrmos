# Explore V2 and RailPulse implementation status

Updated: 2026-08-05

## Implemented in this slice

- Explore Discover shell on Android and iOS with universal search, Discover or Network switching, route pulse, Greece-wide feed, time budgets, and Ariadne summary.
- RailPulse station detail on Android and iOS with next departure, community confidence, expiring conditions, confirmations, and the official-source disclaimer.
- RailPulse train detail on Android and iOS with occupancy, delay, temperature, cleanliness, service state, recency, and confirmations.
- One-tap Quick Report on Android and iOS with structured signals, crowd refinement, immediate local confirmation, and a 10-second undo window.
- Local contribution profile on Android and iOS with counters, levels, badges, and weekly progress stored on the device.
- iOS Live Activity community summary and inline confirmation App Intent.
- Apple Watch RailPulse summary and quick actions, with opportunistic WatchConnectivity delivery to the phone.
- Android tracked-departure notification community summary and local confirmation action.
- Albanian, English, Greek, and Italian interface copy for the new mobile surfaces.

## Current data boundary

The UI uses fixture community conditions so the product flow can be built and tested without weakening the current privacy posture. Contributions currently update local counters only. No name, profile, report history, location, photo, free text, advertising identifier, or stable contributor identifier is transmitted.

The interface does not claim that the anonymous proof protocol is live. Network submission remains disabled until an audited unlinkable one-time proof design and no-log aggregate service exist.

## Backend work still required

- Pulse aggregate read endpoint with timestamps, independent confirmation counts, confidence, expiry, disputes, and resolution.
- Audited unlinkable one-time proof mint and redemption flow.
- Anonymous aggregate write endpoint with replay protection and no stable contributor identity.
- Moderation rules for safety signals, contradictions, expiry, and malicious activity.
- Feature flags for pulse read, pulse write, Watch, and system surfaces.
- Tests proving that official alerts always outrank community conditions and that cached community data becomes stale or expires correctly.

## Verification completed

- Android debug APK assembled successfully.
- Android emulator visually verified Explore, local profile, station detail, Quick Report, crowd refinement, sent state, and undo.
- iOS app built and launched successfully on iPhone 17 Simulator.
- iOS Simulator visually verified Explore, local profile, station detail, train detail, Quick Report, crowd refinement, sent state, and undo.
- The main iOS scheme compiled the app, widget, Live Activity, App Intent, and embedded Watch sources.

## Verification not completed

- Live Activity was not started and exercised from an actual tracked journey.
- Watch quick actions were not exercised on a paired Watch simulator or physical Watch.
- No anonymous backend submission exists to integration test.
