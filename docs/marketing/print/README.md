# Syrmos 3.0.2 print kit

This brief is for a designer or printer. The research, the legal notes and the reasoning are in `../3.0.2-GROWTH-RESEARCH.md`.

**Legal note for everyone handling these:**

- These items go only where an owner has said yes (cafés, kiosks, hostels, campus boards, coworking), or to people who stick them on their own things.
- Never put them in metro, tram or bus stations or vehicles, or on public property. That is prohibited and fined (research doc, section 1).

## Global spec

- **QR target:** `https://syrmos.peterdsp.dev/get-app/?src=<code>`. The domain is confirmed from `composeApp/src/wasmJsMain/resources/get-app/index.html`. Each batch gets its own code, as listed below.
- **QR rendering:**
  - Static code, error correction M (use Q if a logo is placed in the middle).
  - Dark modules `#14181F` on `#FFFFFF`.
  - Quiet zone of at least 4 modules on every side.
  - Never inverted, never coloured, and never touching the cut line.
- **Minimum QR size:**
  - Stickers: 2.0 cm (2.5 cm preferred).
  - A6 / A5 cards: 4 cm.
  - A4 poster: 7 cm.
  - A3 poster: 12 cm.
- **Under every QR:** the text `syrmos.peterdsp.dev/get-app`.
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

## Assets

Indicative prices are from Copy Corner and Yellowbug as of 27 October 2026 (see the research doc, section 5). They exclude VAT unless marked. Always get a fresh quote.

| ID | Item | Size / shape | Material | Qty (run 1) | Copy (EL / EN) | QR src code(s) | Indicative price |
|---|---|---|---|---|---|---|---|
| S1 | Countdown die-cut | 9 x 4.5 cm pill (or a 10.5 x 5 cm stock shape) | Matte laminated vinyl | 200 | "Ο συρμός σου σε 3'. Κατέβασε το Syrmos." / "Your train in 3 min. Get Syrmos." | `s1-give` (100), `s1-cafe` (60), `s1-uni` (40) | about 28 € for 200 at 5x5 equivalent (Copy Corner); about 105 € incl. VAT for 200 custom die-cut (Yellowbug) |
| S2 | Line roundels, set of 3 (M1, M2, M3) | Ø8 cm circle | Matte laminated vinyl | 200 (about 67 of each line) | "Μην τρέχεις. Το επόμενο έρχεται." / "Don't run. The next one's coming." | `s2-give` (100), `s2-esn` (70), `s2-meet` (30) | 48 € for 200 Ø8 (Copy Corner; ask whether 3 designs can share one run) |
| S3 | Airport tag | 9 x 5 cm tag shape with a printed hole | Matte laminated vinyl | 100 | "Πτήση στις 10; Φύγε στις 7:12. Θα σου το θυμίσουμε." / "Flight at 10? Leave at 7:12. We'll remind you." Print 50 with Greek first and 50 with English first | `s3-hst` (70), `s3-cowk` (30) | 29 € for 100 at 9x6 (Copy Corner) |
| S4 | Tunnel / offline | 8 x 8 cm, rounded corners | Matte laminated vinyl, dark ground | Run 2 only | "Δουλεύει και στο τούνελ, χωρίς internet." / "Works in the tunnel. No signal needed." | `s4-uni`, `s4-cowk`, `s4-give` | 30 € per 100 |
| S6 | Night owl | Ø8 cm | Matte laminated vinyl | Run 2 only | "Πρόλαβες το τελευταίο. Πάλι." / "Caught the last train. Again." | `s6-cafe`, `s6-give` | 30 € per 100 |
| S8 | Thessaloniki | 8 x 8 cm | Matte laminated vinyl | Run 2 (W5) | "Η Θεσσαλονίκη απέκτησε μετρό. Τώρα και Syrmos." / "Thessaloniki got a metro. Now it gets Syrmos." | `s8-auth`, `s8-uom`, `s8-esn` | 30 € per 100 |
| S10 | Made in Athens | 6 x 6 cm | Matte laminated vinyl | Run 2 only | "Φτιαγμένο στην Αθήνα. Ανοιχτός κώδικας. Χωρίς διαφημίσεις." / "Made in Athens. Open source. No ads." | `s10-meet`, `s10-cowk` | about 20 € per 100 |
| C3 | Airport front-desk card | A6, 10.5 x 14.8 cm, double-sided (EN front, EL back) | 350 g card, matte laminated | 30 (part of the C-card run) | Same copy as S3, plus "Works offline · English, Ελληνικά, Shqip, Italiano" | `c3-hst` (20), `c3-info` (10) | Quote (about 1 € each **[unverified]**) |
| C5 | Closures counter card | A5, single-sided | 300 g card, matte laminated, with an easel or acrylic holder | 40 | "Κλειστός σταθμός; Σου δείχνουμε το λεωφορείο." / "Station closed? We'll show you the bus." | `c5-kio` (20), `c5-cafe` (20) | Part of the 100 A5 run: 28 € for 100 A5 at 150 g double-sided (Copy Corner; 300 g card on quote) |
| C9 | Welcome tourist card | A6, double-sided | 350 g card | 30 | "Athens by metro, in your language." / "Καλώς ήρθες. Το μετρό, στη γλώσσα σου." | `c9-hst` (20), `c9-info` (10) | Part of the C-card run |
| C8 | Thessaloniki counter card | A5 | 300 g card | Run 2 (W5) | Same copy as S8 | `c8-cafe` | Quote |
| P5 | Closures poster | A3 portrait | 170 g silk paper, indoor | 5 | Same copy as C5 | `p5-uni` | About 1 to 3 € each, local copy shop **[unverified]** |
| P7 | Student commute poster | A4 or A3 portrait, one version per campus | 170 g silk paper | 5 run 1, more in run 2 | "Μάθημα σε 12'. Προλαβαίνεις." / "Lecture in 12 min. You'll make it." | `p7-ekpa`, `p7-ntua`, `p7-aueb`, `p7-unwa`, `p7-pant`, `p7-auth`, `p7-uom` | About 1 to 3 € each **[unverified]** |

### Run 1 order summary (W2)

| Item | Qty | Est. cost excluding VAT |
|---|---|---|
| S2 roundels Ø8 | 200 | 48 € |
| S1 countdown (5x5 or stock pill) | 200 | 28 € |
| S3 airport tag 9x6 | 100 | 29 € |
| A5 / A6 cards (C3, C5, C9) | 100 | 28 € plus card-weight upgrade |
| A3 / A4 posters (P5, P7) | 10 | about 15 € |
| **Total** | | **about 150 € excluding VAT, about 185 € including 24% VAT** |

Lean option (about 100 € incl. VAT): S2 plus S3 plus 100 A5 cards only.

## Per-item layout notes

- **S1:** a big "3'" on the left; a Brand-blue route line under it ending in a station node; three tiny M1 / M2 / M3 dots as a legend; the QR on the right inside a white rounded square.
- **S2:** a thick outer ring in the line colour, with the line code as a small label on the ring; Canvas inner field; the headline over two lines; a small logo; the QR in the lower third.
- **S3:** a Brand-blue header band with a plane glyph and "ATH"; "10:00" and "7:12" in large tabular figures joined by a thin M3-blue line ending at an airport node; the QR on the tag body. Before printing, confirm that the 7:12 example matches the app's output for a central station with the 2 h buffer.
- **S4:** a dark ground with a thin tunnel arch; a Dark-brand route line; a glowing "2'"; an "Offline" chip with an icon; the QR on white.
- **S6:** dark ground, moon-shaped node and a "Last / Τελευταίο" chip. Do not print a specific time unless it has been checked against the schedule.
- **S8:** a Brand-blue field with a white route line along the waterfront. No TM1 colour until it has been confirmed.
- **S10:** the production logo with the route line forming a curly brace; mono type for the repo name; the QR goes to the app.
- **C5 / P5:** a metro line broken at one node; a dashed bus-colour arc bridging the gap with a bus glyph; the node labelled "Closed / Κλειστός" (text, not colour alone).
- **C9:** postcard with a stylised three-line diagram; four language chips; a mini airport tag in the corner.
- **P7:** notebook-grid ground; a route line drawn as a pen stroke to a campus pictogram; a big "12'". Show the campus's real lines.

## Placement log (fill in when placing)

| src | Venue name | Address / nearest station | Contact person | Date placed | Pieces | Permission (yes, who) | Collected / refreshed |
|---|---|---|---|---|---|---|---|
