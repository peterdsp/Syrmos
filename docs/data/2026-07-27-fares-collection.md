# Syrmos fares data collection — 2026-07-27

Grounded fare tables for the v2.0.0 fares menu + journey fare planner (from→to→price)
and Ariadne fare answers. **Every price here is transcribed from an official operator
source, never invented.** Where an operator uses dynamic pricing (intercity), that is
stated and the planner must defer to the official booking channel instead of guessing.

Existing schema to extend: `ops/syrmos-api/scripts/seed_fare_products.py` →
`fare_products(section, sort_order, title_en, title_el, full_price_eur,
discounted_price_eur, validity, notes, tags, source_url)`, served at `/api/fares`.
Today it only carries OASA (Athens). This document is the source to seed the rest.

---

## 1. Athens — OASA / STASY (metro M1-M3, tram T6/T7, bus, suburban within the urban zone)
Source: https://www.oasa.gr/en/tickets/prices-of-products/ — fetched 2026-07-27. Full / reduced.

| Product | Full | Reduced |
|---|---|---|
| 90-minute single (integrated) | €1.20 | €0.50 |
| 24-hour ticket | €4.10 | – |
| 5-day ticket | €8.20 | – |
| 3-day tourist ticket | €20.00 | – |
| 2-single pack | €2.30 | €1.00 |
| 5-single pack | €5.70 | €2.50 |
| 10+1 single pack | €12.00 | €5.00 |
| Airport express bus (1-way) | €5.50 | €2.70 |
| Airport Metro (M3, 1-way) | €9.00 | €4.50 |
| Airport Metro (2-way, 30 days) | €16.00 | – |
| Airport Metro from Pallini/Kantza/Koropi | €5.50 | €2.70 |
| 30-day pass (urban) | €27.00 | €13.50 |
| 90-day pass | €78.00 | €39.00 |
| 180-day pass | €155.00 | €77.50 |
| 365-day pass | €300.00 | €150.00 |
| 30-day pass + airport | €45.00 | €22.50 |
| 90-day + airport | €129.00 | €64.50 |
| 180-day + airport | €228.00 | €114.00 |
| 365-day + airport | €446.00 | €223.00 |

## 2. Thessaloniki — OSETH (metro TM1/TM2, city bus, suburban/peri-urban)
Source: https://oseth.com.gr/en/frequently-asked-questions — fetched 2026-07-27. Full / reduced (50%).
Note: metro and bus tickets are **not yet interoperable** — separate products for each mode.

| Product | Full | Reduced |
|---|---|---|
| Urban single | €0.60 | €0.30 |
| Suburban (peri-urban) single | €0.80 | €0.40 |
| Special bus (X1/X2 Airport, Cultural Route 50) | €2.00 | €1.00 |
| 10+1 pack (urban) | €5.80 | €2.90 |
| 10+1 pack (suburban) | €7.80 | €3.90 |
| 24-hour unlimited | €2.50 | – |
| Monthly card | €16.00 | €8.00 |
| 3-month card | €45.00 | €22.50 |
| 6-month card | €85.00 | €42.50 |
| Inter-prefectural (Lagadas, paper only) | €0.80 | – |

## 3. Patras suburban — Hellenic Train zone grid
Source: HT Patras Suburban map PDF (user-provided, 2025-07):
https://www.hellenictrain.gr/sites/default/files/2025-07/HT_Proastiakos_Patra_Map_130-22_12072025_0.pdf
Zones: **A1** Ag.Vassilios–Ag.Andreas · **A** Ag.Vassilios–Antheias · **B** Agyia–Kaminia · **C** Vrachneika–Kato Achaia.
Stations N→S: Ag.Vassilios, Aktaion, Rio, Kastelokampos, Bozaitika, Agyia, Panachaiki, Patra, Ag.Andreas, Anthias, Ities, Paralia Patron, Tsaousi Mintilogli, Vrachneika, Tsoukaleika, Kaminia, Alissos, Kato Achaia.

| Zone(s) | Ticket full | Ticket reduced | Monthly full | Monthly reduced |
|---|---|---|---|---|
| A1 | €1.40 | €1.00 | €25.00 | €15.00 |
| A | €1.40 | €1.00 | €30.00 | €20.00 |
| B | €1.40 | €1.00 | €30.00 | €20.00 |
| C | €1.40 | €1.00 | €30.00 | €20.00 |
| A+B | €2.00 | €1.40 | €45.00 | €30.00 |
| B+C | €2.00 | €1.00 | €45.00 | €30.00 |
| A+B+C | €3.00 | €1.40 | €65.00 | €45.00 |

## 4. Athens suburban (Proastiakos) beyond the OASA urban zone — Hellenic Train
Sources: https://www.hellenictrain.gr/en/athens-suburban-and-regional-railway ; OASA (airport);
myartemida 2025 guide. Verify exact station-pair prices on hellenictrain.gr before shipping.
- Within the OASA urban zone (roughly Magoula–Piraeus–Koropi): covered by the OASA €1.20 integrated ticket.
- To/from the Airport: **€9.00** one-way / **€16.00** round-trip (OASA airport product; same as §1).
- Athens ↔ Kiato: **≈€6.00** one-way (≈€12 round-trip) — confirm on HT.
- Athens ↔ Chalkida: **€6.00** one-way — confirm on HT.
- Ano Liosia (A2) — within/around the urban zone; confirm.

## 5. Intercity (IC) + regional — Hellenic Train  ⚠ DYNAMIC PRICING
Source: https://www.hellenictrain.gr/en/tickets-ticket-discounts-offers — fetched 2026-07-27.
**No fixed fare table.** Price depends on route, class, date, and availability, sold via the
booking system (newtickets.hellenictrain.gr). The planner must NOT fabricate an IC price:
show the discount structure below and defer to the official booking link for the exact fare.

Discount categories (percent off the base fare):
- Early booking: up to **15%** (prior to departure).
- Return ticket: **20%** (also 65+ / military).
- Students (university / public IEK): **25%**; home↔campus **50%**.
- Youth under 24: **25%**.
- Children 4–12: **50%**; under 4: free without a separate seat, 50% with a seat.
- Persons with reduced mobility: **50%**.
- Large families (4+ children): **50%**.
- Multi-journey cards: up to **50%**.
- **Cap:** total reduction cannot exceed **40%** of the base fare.

## 6. Rail-replacement buses (TL1, KB1, VL1, DX1, KP1, and any others)
These coaches substitute suspended rail segments and are covered by the **same Hellenic Train
ticket** for that segment — i.e. the same intercity/suburban (often dynamic) fare as the rail
they replace, not a separate bus tariff. Treat their price as the underlying rail segment's
fare; for IC-priced segments this is dynamic (defer to booking), for suburban-zone segments use
the relevant zone fare.

---

## Data model implication for the planner
- **Zone/fixed networks** (Athens OASA, Thessaloniki OSETH, Patras suburban): the planner can
  return an **exact grounded price** for any from→to (resolve the zone(s) crossed → look up the
  fare above). These get seeded into `fare_products` (extend the existing OASA-only seed) plus a
  small **zone-resolution table** per network (station → zone).
- **Dynamic networks** (intercity IC/regional, and the rail-replacement buses on IC segments):
  the planner returns the **discount structure + official booking link**, explicitly labelled
  "price set at booking", never a fabricated number. Ariadne answers the same way.

## Still to verify before build (grounded gaps)
- Exact Athens Proastiakos station-pair fares to Kiato / Chalkida / Corinth (HT site).
- Whether OASA metro/bus and the Athens suburban share the same tap within the urban zone edges.
- Confirm each replacement-bus corridor's underlying segment fare basis (IC vs suburban zone).
- OSETH metro↔bus interoperability once/if the integrated single ticket goes live.
