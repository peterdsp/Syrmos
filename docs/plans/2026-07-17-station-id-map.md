# Legacy -> server station id map

Both seeds keep one record per (line, station) and preserve the line prefix;
only the suffix convention changed (AER->AIR, KRP->KOR, PEK->PEA). Matched on
same line prefix + nearest coordinate.

**85 confident** · **1 need your call** (of 86)

## Confident (same line, <80m, unambiguous)

| legacy id | name | zone | -> server id | server name | dist |
|---|---|---|---|---|---|
| `A1_AAN` | Ag. Anargyroi | 2 | `A1_AGI` | Άγιοι Ανάργυροι | 0m |
| `A1_AER` | Airport | 4 | `A1_AIR` | Airport | 0m |
| `A1_DPL` | Douk. Plakentias | 2 | `A1_DOY` | Δουκίσσης Πλακεντίας | 0m |
| `A1_IRK` | Irakleio | 2 | `A1_IRA` | Ηράκλειο | 0m |
| `A1_KAC` | Kato Acharnai | 2 | `A1_KAT` | Κάτω Αχαρναί | 0m |
| `A1_KRP` | Koropi | 2 | `A1_KOR` | Koropi | 0m |
| `A1_LEF` | Lefka | 2 | `A1_LEY` | Λεύκα | 0m |
| `A1_PEK` | Peania-Kantza | 2 | `A1_PEA` | Peania-Kantza | 0m |
| `A1_ROU` | Rouf | 2 | `A1_ROY` | Ρουφ | 0m |
| `A1_TAV` | Tavros | 2 | `A1_TAY` | Ταύρος | 0m |
| `A2_AER` | Airport | 4 | `A2_AIR` | Airport | 0m |
| `A2_ANL` | Ano Liosia | 2 | `A2_ANO` | Άνω Λιόσια | 0m |
| `A2_DPL` | Douk. Plakentias | 2 | `A2_DOY` | Δουκίσσης Πλακεντίας | 0m |
| `A2_IRK` | Irakleio | 2 | `A2_IRA` | Ηράκλειο | 0m |
| `A2_KRP` | Koropi | 2 | `A2_KOR` | Koropi | 0m |
| `A2_PEK` | Peania-Kantza | 2 | `A2_PEA` | Peania-Kantza | 0m |
| `A3_AAN` | Ag. Anargyroi | 4 | `A3_AGI` | Άγιοι Ανάργυροι | 0m |
| `A3_AGE` | Ag. Georgios | 4 | `A3_AG4` | Άγιος Γεώργιος | 0m |
| `A3_AST` | Ag. Stefanos | 4 | `A3_AG2` | Άγιος Στέφανος | 0m |
| `A3_ATH2` | Ag. Thomas | 4 | `A3_AG3` | Άγιος Θωμάς | 0m |
| `A3_AUL` | Avlida | 4 | `A3_AY2` | Αυλίδα | 0m |
| `A3_AVL` | Avlonas | 4 | `A3_AYL` | Αυλώνας | 0m |
| `A3_AXN` | Acharnes | 4 | `A3_AC2` | Αχαρνές | 0m |
| `A3_OIN2` | Oinoi | 4 | `A3_OI2` | Οινόη | 0m |
| `A4_AAN` | Ag. Anargyroi | 4 | `A4_AGI` | Άγιοι Ανάργυροι | 0m |
| `A4_ANL` | Ano Liosia | 4 | `A4_ANO` | Άνω Λιόσια | 0m |
| `A4_ATH3` | Ag. Theodoroi | 4 | `A4_AG2` | Άγιοι Θεοδώροι | 0m |
| `A4_KAC` | Kato Acharnai | 4 | `A4_KAT` | Κάτω Αχαρναί | 0m |
| `A4_LEF` | Lefka | 4 | `A4_LEY` | Λεύκα | 0m |
| `A4_NPE` | Nea Peramos | 4 | `A4_NEA` | Νέα Πέραμος | 0m |
| `A4_ROU` | Rouf | 4 | `A4_ROY` | Ρουφ | 0m |
| `A4_TAV` | Tavros | 4 | `A4_TAY` | Ταύρος | 0m |
| `A4_ZEV` | Zevgolatio | 4 | `A4_ZEY` | Ζευγολατιό | 0m |
| `M1_AGE` | Agios Eleftherios | 1 | `M1_AG2` | Agios Eleftherios | 0m |
| `M1_AGN` | Agios Nikolaos | 1 | `M1_AGI` | Agios Nikolaos | 0m |
| `M1_ANP` | Ano Patisia | 1 | `M1_ANO` | Ano Patisia | 0m |
| `M1_IRK` | Irakleio | 1 | `M1_IRA` | Irakleio | 0m |
| `M1_KAM` | KAT | 2 | `M1_KA2` | KAT | 0m |
| `M1_KHE` | Kifissia | 2 | `M1_KIF` | Kifissia | 0m |
| `M1_NIO` | Nea Ionia | 1 | `M1_NEA` | Nea Ionia | 0m |
| `M1_THE` | Thiseio | 1 | `M1_THI` | Thiseio | 0m |
| `M2_ALD` | Agios Dimitrios | 1 | `M2_AG3` | Agios Dimitrios | 0m |
| `M2_ALM` | Alimos | 1 | `M2_ALI` | Alimos | 0m |
| `M2_LAR` | Athens | 1 | `M2_STA` | Σταθμός Λαρίσης | 0m |
| `M2_NEK` | Neos Kosmos | 1 | `M2_NEO` | Neos Kosmos | 0m |
| `M2_PEE` | Peristeri | 2 | `M2_PER` | Peristeri | 0m |
| `M2_SYG` | Syngrou-Fix | 1 | `M2_SY2` | Syngrou-Fix | 0m |
| `M3_ABA` | Agia Varvara | 1 | `M3_AGI` | Agia Varvara | 0m |
| `M3_AMA` | Agia Marina | 1 | `M3_AG2` | Agia Marina | 0m |
| `M3_AMP` | Ambelokipoi | 1 | `M3_AMB` | Ambelokipi | 0m |
| `M3_APR` | Agia Paraskevi | 2 | `M3_AG3` | Agia Paraskevi | 0m |
| `M3_DPL` | Douk. Plakentias | 2 | `M3_DOY` | Δουκίσσης Πλακεντίας | 0m |
| `M3_HAL` | Chalandri | 2 | `M3_CHA` | Chalandri | 0m |
| `M3_HOL` | Cholargos | 2 | `M3_CHO` | Cholargos | 0m |
| `M3_KRP` | Koropi | 3 | `M3_KO2` | Κορωπί | 0m |
| `M3_KTC` | Katechaki | 1 | `M3_KAT` | Katechaki | 0m |
| `M3_PEK` | Peania-Kantza | 3 | `M3_PEA` | Peania-Kantza | 0m |
| `M3_PNR` | Panormou | 1 | `M3_PAN` | Panormou | 0m |
| `T6_AFP` | Ag. Fotinis | 1 | `T6_AGI` | Αγίας Φωτεινής-Πλατεία | 0m |
| `T6_AIG` | Aegeou | 1 | `T6_AEG` | Aegeou | 0m |
| `T6_APK` | Ag. Paraskevi | 1 | `T6_AGH` | Aghia Paraskevi | 0m |
| `T6_EVS` | Evangeliki Scholi | 1 | `T6_EVA` | Evangeliki Scholi | 0m |
| `T6_MAL` | Meg. Alexandrou | 1 | `T6_ALE` | Alexander the Great | 0m |
| `T6_NEK` | Neos Kosmos | 1 | `T6_NEO` | Νέος Κόσμος | 0m |
| `T7_34S` | 34 Synt. Pezikou | 1 | `T7_SYN` | 34 Syntagmatos Pezikou | 0m |
| `T7_AGA` | Ag. Alexandros | 1 | `T7_AG2` | Aghios Alexandros | 0m |
| `T7_AK1` | 1st Ag. Kosma | 1 | `T7_STA` | 1st Aghiou Kosma | 0m |
| `T7_AK2` | 2nd Ag. Kosma | 1 | `T7_NDA` | 2nd Aghiou Kosma | 0m |
| `T7_ATR` | Agia Triada | 1 | `T7_AGI` | Agia Triada | 0m |
| `T7_EOL` | Ellinon Olympionikon | 1 | `T7_EL2` | Ellinon Olymbionikon | 0m |
| `T7_ESP` | Pl. Esperidon | 1 | `T7_PL5` | Platia Esperidon | 0m |
| `T7_GLY` | Paralia Glyfadas | 1 | `T7_PA2` | Paralia Glyfadas | 0m |
| `T7_IPP` | Pl. Ippodameias | 1 | `T7_PL2` | Plateia Ippodameias | 0m |
| `T7_KAM` | Kalamaki | 1 | `T7_KA2` | Kalamaki | 0m |
| `T7_KAT` | Pl. Vaso Katraki | 1 | `T7_PL4` | Platia Vaso Katraki | 0m |
| `T7_KIS` | Kentro Istioploias | 1 | `T7_KEN` | Kentro Istioploias | 0m |
| `T7_MET` | Ag. Metaxa | 1 | `T7_AG3` | Agheiou Metaxa | 0m |
| `T7_NEF` | Neo Faliro | 1 | `T7_NEO` | Neo Faliro | 0m |
| `T7_PDM` | Paleo Demarhio | 1 | `T7_PAL` | Paleo Demarhio | 0m |
| `T7_PFL` | Parko Flisvou | 1 | `T7_PAR` | Parko Flisvou | 0m |
| `T7_SEF` | SEF | 1 | `T7_PEA` | Peace and Friendship Stadium | 0m |
| `T7_SKE` | Ag. Skepi | 1 | `T7_AGH` | Aghia Skepi | 0m |
| `T7_SKY` | Om. Skylitsi | 1 | `T7_OMI` | Omiridou Skylitsi | 0m |
| `T7_VER` | Pl. Vergoti | 1 | `T7_PL3` | Platia Vergoti | 0m |
| `T7_VOL` | Asklipiio Voulas | 1 | `T7_ASK` | Asklipiio Voulas | 0m |

## NEEDS YOUR DECISION

| legacy id | name | zone | best guess | server name | dist |
|---|---|---|---|---|---|
| `M2_AGA` | Agios Antonios | 2 | `M2_PER` | Peristeri | 831m |

## The one that cannot be resolved mechanically: `M2_AGI`

`M2_AGI` is a genuine id collision. The same id names two different stations
5.5 km apart:

| side | name | lat |
|---|---|---|
| legacy | Agios Ioannis | 37.9564 |
| server | Agios Antonios | 38.0061 |

An id-based backfill sees `M2_AGI` on both sides, treats it as a match, and
writes Agios Ioannis's fare zone onto Agios Antonios. No error, no mismatch in
the counts, just a wrong zone on a real station. This is why
`scripts/import_legacy_station_attrs.py` refuses on ANY discrepancy rather than
trusting id equality, and why it must not be "fixed" by skipping misses.

Legacy `M2_AGA` "Agios Antonios" is the record that should map to server
`M2_AGI`, so the legacy pair (`M2_AGA`, `M2_AGI`) maps to server (`M2_AGI`, ?).
**Petros must confirm** where legacy `M2_AGI` "Agios Ioannis" belongs on the
server before anything is written.

## How to use this map

The rest is mechanical: both seeds keep one record per (line, station) and
preserve the line prefix, so only the suffix convention changed. Match on line
prefix + coordinate, never on id equality and never on name alone.
