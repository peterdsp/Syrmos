# Phase Z visual-baseline findings — iOS, first pass

Defects found while capturing the first 20 cells (iPhone 17 / iOS 27.0, 402x874pt,
`en`, light + dark). Each one was reproduced on the running simulator and then
traced to the code that causes it, so none of these is a guess from a screenshot.

Ordered by severity.

---

## 1. Announcements are content-free in every non-Greek locale (S01)

**Evidence:** `S01-home__C402__light__en__default.png`, `S01-home__C402__dark__en__default.png`
— "What matters now" shows two cards that read only "Service alert", one with a
bare date, one with nothing at all. They look like duplicates of each other.

**Cause:** `STASYService.isUsableTranslation` ([STASYService.swift:89](../../../iosApp/iosApp/Core/Networking/STASYService.swift:89))
rejects any string containing Greek scalars, and `safeTitle`
([STASYService.swift:96](../../../iosApp/iosApp/Core/Networking/STASYService.swift:96))
therefore returns the literal category word when the translated title is missing.
The live feed currently supplies `titleEn`/`titleSq`/`titleIt` as `""` for **all
14** announcements:

```
curl -s https://api-syrmos.peterdsp.dev/api/announcements
# every item: title = Greek text, titleEn = "", titleSq = "", titleIt = ""
```

So English, Albanian and Italian users get a placeholder instead of any
information, on the primary screen, for every announcement. Three of the four
shipped locales are affected. The Greek locale is fine.

The refusal to show untranslated Greek is a deliberate choice, so the fix is a
product decision, not a one-line patch: either populate the translations upstream,
or show the Greek source behind an explicit "Greek only" disclosure. A
content-free card is the one option that helps nobody.

**Blocks the locale axis of the gate:** S01/S09 baselines in el/sq/it cannot be
approved while the card content depends on live upstream translation coverage.

---

## 2. The station selector lists indistinguishable duplicates (S03)

**Evidence:** `S03-station-selector-keyboard__C402__light__en__default.png` — the
query `Kifis` returns `Kifissia`, `Kifisias`, `Kifisias`: two rows with identical
text and no way to tell them apart.

**Cause:** `PlanFlow.matches` ([PlanFlow.swift:695](../../../iosApp/iosApp/Features/Assistant/PlanFlow.swift:695))
filters the raw station list with no grouping. Per-line station ids that share a
display name each get their own row.

This violates the hard de-duplication invariant in the roadmap (section 2.1: no
list on any surface may ever show the same thing twice) on a surface introduced
by 3.0. The fix is the same shape as the departures grouping already in place:
group by display name and disambiguate by line, or collapse co-located ids.

---

## 3. The same leg reports two different stop counts on adjacent screens (S05 vs S06)

**Evidence:** `S05-journey-detail__C402__light__en__default.png` says
**"11 stops"**; `S06-go-active__C402__light__en__default.png`, for the same leg of
the same journey, says **"12 stops · next Lefka"**.

**Cause:** two different quantities share one word.
- S05: `JourneyDetail.timeline` uses `orderedStopIds.count - 2`
  ([JourneyDetail.swift:61](../../../iosApp/iosApp/Core/Journey/JourneyDetail.swift:61))
  — stations *between* board and alight.
- S06: `GoJourneyView.subdetail`
  ([GoJourneyView.swift:408](../../../iosApp/iosApp/Features/Go/GoJourneyView.swift:408))
  renders `remaining` from the shared engine, which is `lastStop - stopIndex`
  ([GoGuidance.kt:63](../../../core/domain/src/commonMain/kotlin/com/syrmos/core/domain/go/GoGuidance.kt:63))
  — inter-stop hops to the alight point.

Both numbers are correct for what they measure; the label is what is wrong. A
user tapping Start journey watches the count change with no explanation. Parity
discipline says one rule, one transform, one name.

---

## 4. Floating overlays cover content and actions (S09)

Two instances, both of the same class, both flagged by the gate's "no overlap, no
hidden action" rule:

- `S09-explore__C402__light__en__default.png` — the "Plan a journey" pill sits on
  top of the "Explore by time" chip row and covers the `90 m` chip and everything
  after it. A control is unreachable, not merely ugly.
- `S09-airport-hub__C402__light__en__default.png` — the Ariadne owl button covers
  the tail of the route-overview stop labels.

---

## 5. Map has no route back to the network without location permission (S09)

**Evidence:** `S09-map-no-location__C402__light__en__default.png` and
`S09-map-trains-on__C402__light__en__default.png`.

With location denied, the Map tab opens on the device's own coordinates
(Cupertino on a stock simulator) showing empty streets and no transit content.
Toggling the live-train layer turns the control on but does not move the camera.
The `Default region = Athens` preference visible in More is not used as the camera
fallback, so there is no way to reach the Athens network from this screen.

This also blocks the Map cells of the matrix: they cannot be captured meaningfully
until either permission is granted in the capture harness or the region fallback
is fixed.

---

## 6. List rows pass under the translucent title pill and are left half-legible (S09)

**Evidence:** `S09-more-settings-scrolled__C402__light__en__default.png` and the
dark counterpart — "Leave-by reminders" and "Saved departures" are sliced by the
floating "More" capsule and cannot be read.

Reproduced in both themes, so it is a layout/inset problem rather than a
material-contrast one: the scroll view's top inset does not account for the
floating title.

---

## Checked and NOT a defect

- **Plans departing at 04:00.** Captured at 02:03 Athens, when the metro is not
  running, so the first feasible departure really is 04:00. The `~150 min` and
  `~212 min` alternatives are honest overnight waits, not a ranking bug.
- **Marketing artwork behind the settings list.** Visible only in the first frame
  after launch; it is the launch screen during the transition and does not persist.
- **`Last updated: Never`.** Correct for a freshly installed build that has not
  run a seed refresh.
