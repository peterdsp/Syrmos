# Syrmos iOS showcase - guided tour (build 129)

Real Syrmos app target (`Syrmos - Athens Rail Times`), v2.0.0 build 129, commit
72f458ea, on the iPhone 17 Simulator (iOS 26.5). The running app is mirrored in
the live Simulator panel; these PNGs are the reviewable record. Live OASA data
where reachable; the app labels any demo fixture in-app.

## Terminal-condition checklist

- [x] Real app builds (BUILD_OK, watch/widget targets stripped for the sim build).
- [x] Tests pass (`iosAppTests/AirportHubTests`: TEST SUCCEEDED).
- [x] App launches without a crash and is navigable across all five tabs.
- [x] Mirrored Simulator open for the user (Simulator panel).
- [x] Visible screens inspected before capture.
- [x] Known chip/status clipping absent (M3, A1, X95, 24/7 atomic; "Schedules"
      readable) - the #21 fix holds on every screen and both airport cities.
- [x] Screenshots + HTML contact sheet exist (this directory).
- [ ] Accessibility evidence: partial. The hero uses a combined accessibility
      element and nav items carry labels; iOS CI runs `agent-device snapshot`
      for the a11y tree. A manual VoiceOver + Increased-Contrast +
      Differentiate-Without-Color + Reduced-Motion pass is the documented
      expansion (see Matrix below), not yet captured here.
- [ ] Performance/memory: no launch/scroll latency or leak observed during the
      tour; no formal Instruments/ETTrace/Memgraph run captured yet.

## Tour index

1. 01-home - launch and first useful answer; "Your Ichnos status" with a Live
   provenance chip and a Scheduled chip, next train and countdown.
2. 02-explore - Discover/Network, community rail status, and the honest
   "Estimated N rail journeys ... This is an estimate, not a report count."
3. 03-map - the live Athens network: colored lines, station dots, user location,
   live-vehicle and report controls.
4. 04-airport-athens - Eleftherios Venizelos (ATH): direct rail (M3, A1) and
   express buses (X95, X93, X96, X97), route overview, calendar hub.
5. 05-more - language (EN/EL/SQ/IT), theme (System/Light/Dark), live-vehicle
   toggle, default region, operator links (STASY, OASA, Hellenic Train).
6. 06-airport-thessaloniki - Makedonia (SKG): honest "the metro does not reach
   the terminal yet", multimodal L2+X3 and L1+2X options.
7. en/dark - Home and Airport in dark mode; fully theme-aware.
8. el/light - Home and Airport in Greek; complete UI translation.

## Runtime logs

No unresolved critical error observed during the tour. The only console noise
reproduced was in the offline local simulation (missing `/files/seed/*` returning
the recovery page, parsed as non-JSON); on-device with reachable data this does
not occur.

## Known minor items (logged, not blocking)

- Greek "No saved airport trip" label in the airport calendar card truncates
  (`Δεν υπαρχει αποθηκευμε...`); a localization-length item for a follow-up
  (allow a second line or scale the label). Graceful ellipsis, not a hard clip.
- Some Greek labels render without tonos accents; to be reviewed against the
  translation source.

## Matrix (captured vs expansion set)

Captured here: iPhone 17, EN + EL, Light + Dark, six core screens.

Documented expansion set (evidence to add): small iPhone, Pro Max, iPad
portrait/landscape; SQ + IT locales; large Dynamic Type; Increased Contrast;
Differentiate Without Color; Reduced Motion; VoiceOver semantics. The
interactive showcase uses one representative device; the PNG matrix is expanded
in follow-up capture runs.
