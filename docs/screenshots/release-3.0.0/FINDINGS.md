# Phase Z visual-baseline findings — iOS, first pass

Defects found while capturing the first 20 cells (iPhone 17 / iOS 27.0, 402x874pt,
`en`, light + dark). Each one was reproduced on the running simulator and then
traced to the code that causes it, so none of these is a guess from a screenshot.

Ordered by severity.

---

## 1. Announcements are content-free in every non-Greek locale (S01) — FIXED

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

**Fixed** in peterdsp/Syrmos#176 by reordering the fallback chain to
reader's language, then English, then the operator's source wording, then the
generic label. The S01 captures in this directory have been retaken against the
fixed build and now show two distinct named alerts; the before-images are the
first revision of these same files in this PR's history.

The fix shows untranslated Greek to a non-Greek reader, which is a deliberate
trade (an unreadable name beats no name). Marking that text as untranslated in
the UI is still open.

**No longer blocks the locale axis**, though el/sq/it baselines still depend on
live upstream translation coverage, so they want the fixture layer under
`README.md` before they are approved.

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

## 5. Map with no location permission — RETRACTED, was a capture artifact

Originally reported as "Map has no route back to the network". With location
pre-granted by the harness, `S09-map__C402__light__en__default.png` shows the
Athens network rendered correctly and centred on Syntagma, offline. The empty
Cupertino viewport was the stock simulator's own location with permission never
granted, not a product defect.

What remains true and is worth a separate look: with permission genuinely
**denied** by a user, the `Default region = Athens` preference is not used as a
camera fallback. That is a different, smaller claim than the one first made here,
and it has not been re-tested, so it is recorded as untested rather than
confirmed.

---

## 6. List rows pass under the translucent title pill and are left half-legible (S09)

**Evidence:** captured pre-harness (the scrolled cells are no longer in the
baseline set; reproduce by scrolling the More tab) — "Leave-by reminders" and "Saved departures" are sliced by the
floating "More" capsule and cannot be read.

Reproduced in both themes, so it is a layout/inset problem rather than a
material-contrast one: the scroll view's top inset does not account for the
floating title.

---

## 7. The top-ranked option can be the one you are least likely to make (S04)

**Evidence:** `S04-route-results__C402__light__en__default.png` — at the pinned
08:42 the list reads:

1. ~48 min · 1 change · M1 → A1 · **Tight** (selected)
2. ~48 min · 0 changes · A1 · Comfortable
3. ~56 min · 1 change · M1 → A2 · Comfortable

Option 2 has the same rounded duration, no change at all, and a comfortable
margin, yet is ranked below a tight one. The shared ranker's tie-break is
duration, then arrival, then change count
([JourneyRanker.kt:47](../../../core/domain/src/commonMain/kotlin/com/syrmos/core/domain/journey/JourneyRanker.kt:47)),
so a minute of arrival time outranks both the change and the feasibility, and
**feasibility is not a ranking input at all**.

That may well be the intended trade, and the rounded "~48 min" label hides the
real difference, so this is raised as a product question rather than asserted as
a bug: should an itinerary the rider is likely to miss be offered first?

Note that iOS plans through its own `JourneyPlanner`, so the exact comparator
above has not been confirmed to be what produced this screen.

## 8. A third of the Greek UI text has no accents at all (el)

**Evidence:** `S01-home__C402__light__el__default.png`,
`S09-explore__C402__light__el__default.png`,
`S09-airport-hub__C402__light__el__default.png`.

Explore reads *"Ελλαδα, ζωντανα και με τη δυναμη της κοινοτητας"* where correct
Greek is *"Ελλάδα, ζωντανά και με τη δύναμη της κοινότητας"*. In monotonic Greek
every word of two or more syllables carries exactly one accent, so unaccented
text does not read as a style choice: it reads as broken Greek, in the app's own
home market.

Counted over the iOS sources, excluding `AriadneParser.swift` whose unaccented
strings are deliberate match keys for user input rather than display text:

| Greek display literals of 8+ Greek letters | 802 |
|---|---|
| **carrying no accent at all** | **254 (31%)** |

Worst files: `RailPulseDetailViews.swift` (63), `ExploreRailPulseView.swift` (58),
`SettingsView.swift` (42), `LinesView.swift` (34).

It is not uniform, which is what makes it look careless rather than systematic:
the planner, the journey detail and the GO screens are fully and correctly
accented, while Explore, Home, Settings and the rail-pulse views are not.

One case is worse than cosmetic. The Explore search placeholder is
*"Προορισμος, σταθμος, γραμμη **η** τρενο..."*
([ExploreRailPulseView.swift:213](../../../iosApp/iosApp/Views/Lines/ExploreRailPulseView.swift:213)).
Unaccented **η** is the feminine article "the"; the intended word is **ή**, "or".
Dropping the accent there changes what the sentence says.

The Albanian strings sitting beside these look the same (`Vonese` for `Vonesë`,
`per` for `për`), so `sq` probably has the same problem, but that has not been
captured and is not asserted here.

## 9. Greek loses its accents when uppercased (el) — FIXED

**Evidence:** `S01-home__C402__light__el__default.png` shows the status chip as
**ΠΡΩΙΝΉ ΜΕΤΑΚΊΝΗΣΗ**. Correct Greek is **ΠΡΩΙΝΗ ΜΕΤΑΚΙΝΗΣΗ**: monotonic Greek
drops the accent in all-caps. The airport day chips show the same thing (ΠΈΜ,
ΣΆΒ for ΠΕΜ, ΣΑΒ).

**Cause:** `Text(text.uppercased())`
([HomeView.swift:335](../../../iosApp/iosApp/Views/Home/HomeView.swift:335)) and
about a dozen similar call sites use the locale-unaware `uppercased()`, which
keeps the tonos. `uppercased(with: Locale(identifier: "el"))` removes it, which
is exactly the behaviour wanted.

Same class as finding 8 and just as visible to a native reader, but this one is a
single mechanical fix rather than a translation pass.

**Fixed** in peterdsp/Syrmos#183: a `uppercasedForDisplay(_:)` helper uppercases in the
reader's language, applied at the six display call sites. The el cells here are
retaken against the fix and now read ΠΡΩΙΝΗ ΜΕΤΑΚΙΝΗΣΗ and ΠΕΜ / ΣΑΒ.

Deliberately left on the plain `uppercased()`: line ids and anything matched
rather than read, and the two `prefix(1).uppercased()` call sites in `MapView`,
because Greek *keeps* the accent in title case (Άμεσο, not Αμεσο).

## 10. Place names stay in Latin script while everything around them is Greek (el)

**Evidence:** `S01-home__C402__light__el__default.png` reads *"προς
**Anthoupoli**"*, and `S09-airport-hub__C402__light__el__default.png` titles the
airport ***Eleftherios Venizelos*** inside an otherwise Greek card.

The data has the Greek names and other screens use them: the station selector,
the journey detail and GO all read Πειραιάς, Ταύρος, Κηφισίας. So this is
specific screens reading the Latin name field rather than missing data.

## Checked and NOT a defect

- **Plans departing at 04:00.** Captured at 02:03 Athens, when the metro is not
  running, so the first feasible departure really is 04:00. The `~150 min` and
  `~212 min` alternatives are honest overnight waits, not a ranking bug.
- **Marketing artwork behind the settings list.** Visible only in the first frame
  after launch; it is the launch screen during the transition and does not persist.
- **`Last updated: Never`.** Correct for a freshly installed build that has not
  run a seed refresh.
- **The GO screen's colour.** First seen entirely red and flagged as alarming; it
  is the line colour. The same screen is green for M1
  (`S06-go-active__C402__light__en__default.png`) and was red only because the
  leg was on A1.
- **Greek on the planner, detail, GO and selector screens.** Fully accented,
  correctly localized station names, correct transit vocabulary
  (Επιβίβαση / Αποβίβαση / Μετεπιβίβαση), correct plurals (1 αλλαγή vs 0
  αλλαγές), and no clipping anywhere at C402. Greek is longer than English and
  the layouts hold.
- **Announcements reading as "Service alert".** Fixed in peterdsp/Syrmos#176 and
  confirmed on device: the cards now carry their real names, in English, from
  the bundled seed.
