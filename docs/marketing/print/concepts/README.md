# QR concepts: the code as part of the artwork

**Adopted:**

- The **ticket** is the airport counter card (C1).
- The **departure board** is the A3 poster (P1, with a 12 cm code).

Both are built by `../kit/build_kit.py`, which calls the drawing code here. Destination and You are here were not adopted.

Four concepts from the design brief, each as an 8 cm die-cut sticker, an A6 counter card and an A3 poster, in English, Greek, Albanian and Italian. They are generated, not drawn by hand, so every size and quiet zone is exact and checked.

```sh
python3 docs/marketing/print/concepts/build_concepts.py          # 96 PDFs, previews, one board per concept
python3 -m unittest docs/marketing/print/concepts/test_concepts.py
```

## What you get (`out/`, gitignored)

- `out/<concept>/<concept>-<format>-<lang>.pdf`: the deliverable. The code is a **marked placeholder** at its true size: hatched module area, dashed edge, "QR PLACEHOLDER", its `?src=` code, level H and size. The corner squares are drawn in the concept's style, because they are part of the design.
- `out/proofs/...-proof.pdf`: the same art with a real level-H code, styled the same way. Use these for the three-phone test. The placeholders are what goes to the designer and printer once the real codes are dropped in.
- `out/board-<concept>.png`: the three sizes in English plus the sticker in Greek, Albanian and Italian. `out/previews/` has every placeholder file as a PNG.
- `out/manifest.csv`: per file, the size, module count and size, quiet zone, finder style and colour, ground colour and logo coverage.

**Every PDF:**

- Trim plus 3 mm bleed, with TrimBox and BleedBox set.
- Layered, as PDF optional content groups: `Background`, `Artwork`, `Text`, `QR`, and on stickers `CutContour`. They open as layers in Illustrator and Acrobat.
- The die-cut path is a 0.25 pt overprinting stroke in the `CutContour` spot colour, on its own layer.
- Fonts are embedded (Inter); the icon is the production PNG.
- Text is live, not outlined. Ask the printer to outline it, or outline it in Illustrator.

**Codes** follow the brief: `?src=st-<lang>`, `c1-<lang>`, `p1-<lang>`, error correction H. Every brief URL comes out as a 41 x 41 code, so the module size is:

| Format | Module area | Module size |
|---|---|---|
| Sticker | 22 mm | 0.54 mm |
| A6 card | 40 mm | 0.98 mm |
| A3 poster | 100 mm | 2.4 mm |

## The concepts

| Concept | Idea | Why it gets scanned more than a plain QR |
|---|---|---|
| **1. Destination** (`c1-destination`) | M1, M2, M3 and the tram run in from both sides, each with two stations, and end in an interchange ring (Brand strong) around the quiet zone. The corners are rounded "node" squares in `#0F4E8C`. Headline "All lines lead here." | Every line visibly ends at the code, so the eye goes there and the code reads as the destination, not as an ad. |
| **2. Departure board** (`c2-departure-board`) | A dark board: "Next departure", M3 Airport, the code on a white tile next to a flip-board "3 / min". The poster adds M1, M2 and T6 rows. Headline "Your train in 3 min." | The code sits where the next train's time would be, so scanning feels like checking the board. |
| **3. Ticket** (`c3-ticket`) | A ticket with notches and a perforation. The blue body carries M3, From Syntagma To Airport, and "Valid for every calm ride."; the white stub carries the code. | A stub is the part you naturally reach for, and "scan for your next train ↓" points straight at it. |
| **4. You are here** (`c4-you-are-here`) | A ring line around the code, one side per line colour, stations at the three finder corners. A "you are here" dot sits at the fourth corner, the one with no finder. The corners are roundel-style squares, with the app icon in the centre. Headline "You are here." | The code becomes a little map with you on it, and the icon in the middle says whose map it is before anyone scans. |

## Hard rules, enforced by the tests

| Rule | How it is enforced |
|---|---|
| Corner squares intact, dark on light only | `test_code_colours_are_dark_on_light`: finders `#14181F` or `#0F4E8C`, ground `#FFFFFF` or `#F7F5F1`. Modules are always `#14181F`, never inverted, no gradients. |
| Quiet zone of at least 4 plain modules | `test_quiet_zone_is_plain_light_ground` renders every one of the 96 files and fails if any pixel in the 4-module ring is darker than `#E6E6E6`. Lines, rings, nodes and board edges stop outside it. |
| Minimum sizes 2.2 / 4 / 10 cm | `test_minimum_sizes_and_logo_coverage`. |
| Centre logo under 15% | Concept 4 only, at 5.8% of the code area (the panel is 24% of the side). |
| URL under every code, disclaimer on every card and poster | `test_required_lines`. On stickers the disclaimer goes on the backing, as the brief allows. |
| Real codes scan, placeholders cannot be mistaken for one | `test_proofs_scan_and_placeholders_do_not` decodes all 96 files. |
| Layers, die-cut, bleed | `test_layers_and_cut_contour`, `test_bleed_and_trim`. |

## Scan risks to test on three phones

Stress test, run on the sticker proofs (the smallest code): every language rendered at only 150 dpi (about 3 pixels a module), then rotated 0 to 30°, blurred and given noise. The decoder is CoreImage, which is harsher than a live phone camera that retries many frames a second. Read the numbers as a ranking, not a scan rate; each one moves by about ±3 between runs.

| | Decoded | Main risk |
|---|---|---|
| Plain code, square corners, no art (baseline) | 35 / 40 | |
| 3. Ticket | 32 / 40 | Lowest risk |
| 2. Departure board | 30 / 40 | Dark board near the code |
| 1. Destination | 28 / 40 | Strongly rounded corner squares |
| 4. You are here | 22 / 40 | Roundel corners plus the centre logo |

What could hurt scanning, by concept:

1. **Destination.**
   - The node-style rounded corner squares are the main cost.
   - The interchange ring sits one line width outside the quiet zone.
   - Test on older Android camera apps.
2. **Departure board.**
   - A dark board around a light tile.
   - Widening the light margin beyond the quiet zone from 1 to 1.5 mm on the sticker raised it from 27 to 32 of 40. Do not shrink that margin.
   - Test under glossy lamination and shop lighting, where a dark sticker shows glare.
3. **Ticket.**
   - Lowest risk: square-ish corners on a white stub.
   - The ticket metaphor is a non-scanning risk. Someone could try it at a validator. Every card and poster says "Not a travel ticket" in its own language. Keep that line, and never place this concept near gates or validators.
4. **You are here.** Highest risk, from three things together:
   - The very round roundel corners. The brief allows "rounded squares"; these are at the limit.
   - The centre logo, about 4 scans in 40 even at 5.8%.
   - The coloured frame.

   **Fallback measured:** lighter rounding (the ticket's style) scored 25 / 40 with the logo, and 27 / 40 without the logo. If phones struggle, drop the logo first, then soften the corners.

**All:** the sticker codes are 22 mm with 0.54 mm modules. Scan them from 10 to 25 cm.

## Notes and conflicts with the current print kit

- **Sticker QR content.**
  - The kit's stickers (`../kit`) now carry an offline text guide in their QR, at error correction M, 28 mm.
  - These concepts follow the brief: a short `?src=` URL at level H, 22 mm.
  - The two cannot be combined as they stand. A guide at level H needs 81 to 93 modules (measured on the current guides), which at 22 mm is 0.24 to 0.27 mm a module, far too dense for a sticker.
  - **Decided (2026-10-29):** the concepts are used on the card and poster only. Stickers stay the round guide badges. There is no ticket sticker, because the ticket already carries the airport message on the A6 card, and a link-only sticker would add a design and a code without the offline guide.
- **Languages.** The brief asks for all four languages on every piece, including Italian stickers; the kit's stickers are English, Greek and Albanian. Both are built here; drop `st-it` if that decision stands.
- **Comparing concepts.** If you print more than one concept at once, give each its own code (for example `st1-en` to `st4-en`), so the scans show which treatment works. The brief's shared `st-en` cannot tell them apart.
- **Copy for review.** The concept headlines and labels (`C` in `build_concepts.py`) are drafts in Greek, Albanian and Italian, including "Not a travel ticket". Have native speakers read them.
- **Map style.** No STASY or OASA signage is copied: there are no official logos or pictograms, the "roundels" are QR finder squares, and the lines are generic 45° routes in the app's line colours.
