# Greek passenger rail — OSM route relations

Extracted from the Geofabrik Greece PBF (`greece-260715.osm.pbf`) with osmium, not
web search. These are the per-direction service relations that map to the corridors
in `greece_passenger_rail_timetables_2026-07-16.pdf`. Use them for track geometry
and ordered station coordinates.

## Thessaloniki suburban (region = thessaloniki)

| line | corridor | outbound rel | inbound rel |
|---|---|---|---|
| TP1 | Thessaloniki ↔ Larisa (T1) | 11282320 (Θ→Λ) | 11282315 (Λ→Θ) |
| TP2 | Thessaloniki ↔ Florina (T2) | 11279623 (Θ→Φ) | 11279626 (Φ→Θ) |
| TP3 | Thessaloniki ↔ Sindos | 19892688 (Θ→Σ) | 19892712 (Σ→Θ) |
| TP4 | Thessaloniki ↔ Drama (T3) | 14046166 (Θ→Δ) | 14046165 (Δ→Θ) |

## National / intercity (region = national)

| corridor | outbound rel | inbound rel |
|---|---|---|
| IC Athens ↔ Thessaloniki | 7363387 (Α→Θ) | 7363413 (Θ→Α) |
| ICE Athens ↔ Thessaloniki | 14134130 (Α→Θ) | 14134131 (Θ→Α) |
| IC Athens ↔ Kalambaka | 14008390 (Α→Κ) | 14008391 (Κ→Α) |
| IC Thessaloniki ↔ Serres | 14939622 (Θ→Σ) | 14939623 (Σ→Θ) |
| Thessaloniki ↔ Alexandroupoli | 7051601 | — |
| AL1 Alexandroupoli ↔ Ormenio (Evros) | 14122316 (Α→Ο) | 14122315 (Ο→Α) |
| KB1 Paleofarsalos ↔ Kalambaka bus (rail alignment) | 14007294 (Π→Κ) | 14007293 (Κ→Π) |

## Rail-replacement / regional buses in the PDF (mode = bus)

These corridors are bus-served today; the OSM relation is the rail alignment, still
useful for drawing the line.

| corridor | outbound rel | inbound rel |
|---|---|---|
| VL1 Volos ↔ Larisa (ΑΠ) | 14006996 (Β→Λ) | 14006995 (Λ→Β) |
| Kalambaka ↔ Paleofarsalos (ΑΠ) | 14007294 (Κ→Π) | 14007293 (Π→Κ) |
| DX1 Drama ↔ Xanthi ↔ Alexandroupoli | 1185198 (line, sliced Δράμα→Αλεξ) | — |
| KP1 Kiato ↔ Patra | 12423298 (Κιάτο→Αίγιο, sliced) + 1769919 (Κόρινθος→Πάτρα, axis-ordered) | — |
| Leianokladi ↔ Stylida (ΑΠ) | 8279886 (Λ→Σ) | 14005081 (Σ→Λ) |
| Volos – Milies (heritage/Pelion) | 17931339 | — |

Note: 1769919 (Κόρινθος→Πάτρα) has out-of-order way members that member-order and
greedy stitching both mangle (45-50 km phantom jumps). For KP1 the coastal Kiato→Patra
segment was rebuilt by projecting the relation's node set onto the Kiato→Patra axis and
sorting, which is robust because the Gulf of Corinth line is near-monotonic in longitude.

## Line alignments (for reference, not service)

E85 Piraeus-Athens-Thessaloniki main line: 1270270. Korinthos-Patras: 1769919.
Patras-Kalamata: 1642995. Larisa-Volos (E853): 14318377.
Paleofarsalos-Kalambaka (E856): 1270336. Platy-Florina (E857): 7098161.

## How to use

Stitch each relation's way members in order from the PBF (osmium getid -r), the
same way TM1/TM2 were done. Station coordinates come from the relation's node
members. Per-station TIMES come from the PDF, never invented.
