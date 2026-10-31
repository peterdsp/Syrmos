# Syrmos 3.0.2 print kit

This brief is for a designer or printer. The research, the legal notes and the reasoning are in `../3.0.2-GROWTH-RESEARCH.md`. What real brands do with stickers, cards and posters, and why the kit is shaped this way, is in `STICKER-RESEARCH.md`. Four concepts where the QR is part of the artwork (destination, departure board, ticket, you are here) are in `concepts/`.

**Legal note for everyone handling these:**

- These items go only where an owner has said yes (cafés, kiosks, hostels, campus boards, coworking), or to people who stick them on their own things.
- Never put them in metro, tram or bus stations or vehicles, or on public property. That is prohibited and fined (research doc, section 1).

## Global spec

- **QR target:** `https://syrmos.peterdsp.dev/get-app/?src=<code>`. The domain is confirmed from `composeApp/src/wasmJsMain/resources/get-app/index.html`. Each batch gets its own code, as listed below.
- **Sticker QR = an offline guide.** Each sticker's QR holds plain text, not a bare link: 4 to 6 lines of facts for that line (termini, how often, hours, airport fare, a tip), the date of the timetable, then the tracked link. A phone camera shows the text with no internet (iPhone shows it in a notification with Copy; Android Lens shows it with Copy **[Android behaviour unverified]**); the link opens once online. Trade-off: a text code does not open the browser in one tap, so cards and posters keep a plain URL. Every fact comes from the bundled seed, so reprint when the seed's timetable changes.
- **QR rendering:**
  - Static code. Stickers: error correction M (the guide is long). Card and poster: error correction H, because their codes are styled (`concepts/`).
  - Dark modules `#14181F` on `#FFFFFF`.
  - Quiet zone of at least 4 modules on every side.
  - Never inverted, never coloured, and never touching the cut line.
- **Minimum QR size:**
  - Stickers: 2.5 cm, with modules of at least 0.34 mm (a guide is much denser than a short URL).
  - A6 / A5 cards: 4 cm.
  - A4 poster: 7 cm.
  - A3 poster: 12 cm. A window poster is scanned from 1 to 1.5 m, and the rule is about 10:1 distance to size.
- **Every sticker carries a QR**, on a white panel in its lower half (the panel is the quiet zone).
- **Under every card and poster QR:** the text `syrmos.peterdsp.dev/get-app`. On stickers the link is inside the guide, and printed on the liner.
- **QR call to action:** say what the scan gives, not "get the app": "Scan for your next train", "Σκάναρε για τον επόμενο συρμό", "Skano për trenin e ardhshëm", "Inquadra per il prossimo treno".
- **On every item:** "Free · Open source · No ads" and the disclaimer "Not affiliated with STASY, OASA, OSETH or Hellenic Train" (minimum 5 pt; on small stickers it may go on the backing paper).
- **Logo:** the production files only: `docs/assets/icon-default-1024.png`, plus `icon-dark-1024.png` on dark grounds. Do not redraw it.
- **Colours:**
  - Brand `#1466B8`, Brand strong `#0F4E8C`, Canvas `#F7F5F1`, White `#FFFFFF`, Text `#14181F`, Secondary `#5B636E`, Subtle `#EFEBE4`, Dark canvas `#0F1216`, Dark brand `#8ECAFF`.
  - Lines (from the app's line data): M1 `#00843D`, M2 `#E61E2A`, M3 `#0083C9`, Tram `#F39800`, Bus `#B45309`.
  - Supply CMYK conversions with the proof and check M2 and M3 on a physical proof.
- **Type:** Inter (open licence) or SF Pro / Roboto. Countdown digits in bold with tabular figures.
- **Roundels** must not mimic official STASY signage: no STASY logo, pictograms or sign layout.
- **Files to the printer:** PDF/X-4, 3 mm bleed, cut line on a separate spot-colour layer named "CutContour", text outlined, and QR codes as vectors.
- **Proof check before approval:**
  - Scan every QR with three phones from the intended distance.
  - Confirm that the `src` code in the URL matches this table.
  - Check spelling in all four languages.
  - Read each sticker's guide on an iPhone and an Android phone with mobile data off.

## Generated kit

Print-ready files are generated, not drawn by hand, so every QR and `src` code stays in sync with the table below.

```sh
python3 docs/marketing/print/kit/build_kit.py          # PDFs, sheets, previews, contact sheet, manifest
python3 -m unittest docs/marketing/print/kit/test_kit.py
```

The badges are a collectible set with a few words and no phone (`STICKER-RESEARCH.md`), and each one's QR doubles as a pocket guide to its line. Stickers come in English, Greek and Albanian; the card and poster in those plus Italian.

- **Output:** `kit/out/<ID>/<ID>-<variant>-<src>.pdf` for pieces with a QR and `kit/out/ST/ST-backing-<lang>.pdf` for the liners (32 files), `kit/out/sheets/` (one A4 kiss-cut sheet per sticker language), `kit/out/manifest.csv` and `kit/out/sheets.csv` (each with the exact QR payload), `kit/out/previews/` and `kit/out/contact-sheet.png`. The output is gitignored; rebuild it whenever copy or codes change.
- **Each PDF:** trim size plus 3 mm bleed, TrimBox and BleedBox set, vector QR (4-module quiet zone, `#14181F` on white), fonts embedded (Inter and JetBrains Mono, both OFL, downloaded on first run), the production icon clipped to its rounded square.
- **Die-cut and kiss-cut lines** are a 0.25 pt overprinting stroke in a spot colour named `CutContour`. The sticker art is clipped to its own outline plus bleed, so nothing square is printed around a badge on a sheet.
- **Card and poster designs** come from the adopted QR concepts (`concepts/README.md`):
  - **C1** is the **ticket**: From Syntagma To Airport, the leave-by headline, and the code on the tear-off stub.
  - **P1** is the **departure board**: the code as the next M3 departure next to a flip-board "3 min", with M1 and M2 rows below.
  - Both are layered PDFs (Background, Artwork, Text, QR). Their text is live, so ask the printer to outline it.
- **Airport example:** a 10:00 flight from Syntagma gives **7:00**, using `CalendarLeaveBy.plan`: 120' flight buffer, the 42' Syntagma to Airport M3 ride and an 18' margin (half the 36' airport headway), both read from the bundled seed. The tests fail if the printed time drifts from that rule.
- **Copy:** Albanian and Italian follow the app's wording but have not been read by a native speaker yet. The new sticker phrases and guides (`COPY["set"]`, `GUIDE` in `build_kit.py`) and the contributor lines ("Το έφτιαξα κι εγώ.", "E ndërtova edhe unë.") are drafts too. Do that before printing.
- **Still for the printer:** these are RGB PDFs, not PDF/X-4. Ask for a PDF/X-4 conversion with their CMYK profile, and check the line colours on a physical proof.
- **Tests** decode every QR back from a rendered page and every sticker cropped from its sheet (CoreImage, macOS) and compare it with the exact payload, Greek and Albanian included. They check that every sticker has a QR with modules of at least 0.34 mm, that each guide carries the seed's facts (airport ride and fare, hours, Saturday all-night only on M2 and the trams), and match each `src` to this table. They also check boxes, cut lines, page count, QR minimum sizes, the scan call to action, the disclaimer, tagline and short URL, and that the contributor badge never appears on a public sheet.

## Assets

One QR code per format and language, and per sticker design, so scans show which piece and which language work.

| ID | Item | Size / shape | Material | Copy | QR src codes |
|---|---|---|---|---|---|
| ST | "Lines of Greece" collectible set, numbered 1/6 to 6/6, each with a 2.5 cm QR holding that line's offline guide: M1 anchor ("From the port to the pines."), M2 temple ("Next stop: Acropolis."), M3 plane ("On time for your flight."), tram wave ("Sea view included."), Thessaloniki metro White Tower ("Thessaloniki, finally."), Syrmos owl ("Always knows when."). Plus a 7th "contributor" badge (`ST-contrib-<lang>`, braces around a line node, "I helped build this.", QR with a thank-you and the GitHub link), given only to people who file a bug, add a translation or contribute code, never on a public sheet. English, Greek and Albanian only | 8 cm round die-cut badge (Ø72.8 art, 2.6 mm white keyline, cut at Ø78) | Matte laminated vinyl | 2 lines per badge plus the guide, per language | `st1-en` to `st7-en`: `st1-en`, `st2-en`, `st3-en`, `st4-en`, `st5-en`, `st6-en`, `st7-en`, `st1-el`, `st2-el`, `st3-el`, `st4-el`, `st5-el`, `st6-el`, `st7-el`, `st1-sq`, `st2-sq`, `st3-sq`, `st4-sq`, `st5-sq`, `st6-sq`, `st7-sq` |
| C1 | Airport counter card, ticket design | A6, single-sided, one per language, 4 cm QR (level H) | 350 g card, matte laminated | "Flight at 10? Leave at 7:00." on a ticket From Syntagma To Airport, code on the stub, "Not a travel ticket" | `c1-en`, `c1-el`, `c1-sq`, `c1-it` |
| P1 | Poster, departure board design | A3 portrait, one per language, 12 cm QR (level H) | 170 g silk paper, indoor | "Your train in 3 min." over a departure board with the code as the next M3 departure | `p1-en`, `p1-el`, `p1-sq`, `p1-it` |

The badges carry no disclaimer on the face. It is on the sheet margin (`kit/out/sheets/`) and on the liner print `ST-backing-<lang>.pdf` for single die-cut badges, which also explains that the QR works offline. `st7-*` is the contributor badge: its guide gives the GitHub link first, then the tracked app link.

### Order summary

| Item | Where | Qty | Est. cost |
|---|---|---|---|
| ST sheets, A4 kiss-cut, 6 badges each | AliExpress sheet seller (sample 10 sheets first) or a local printer | 25 sheets per language (450 stickers) | about 1 to 4 € per sheet **[unverified]** |
| ST single die-cut | Nexus Print Lab or Copy Corner, liner printed with `ST-backing-<lang>` | 100 per design and language, as needed | about 27 € per 100 at 7x7 cm (Nexus); 8 cm will cost more **[unverified]** |
| ST contributor | Same printer, single die-cut | 25 per language, handed out by the maintainer | quote |
| C1 | Local copy shop | 25 per language | quote |
| P1 | Local copy shop | 3 per language | about 1 to 3 € each **[unverified]** |

## Placement log (fill in when placing)

| src | Venue name | Address / nearest station | Contact person | Date placed | Pieces | Permission (yes, who) | Collected / refreshed |
|---|---|---|---|---|---|---|---|
