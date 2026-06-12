# Athens Fixed-Rail Station Coordinate Analysis

Generated: 2026-06-11

Scope: Athens Metro Lines 1, 2 and 3, Athens Tram Lines T6 and T7, and Athens Suburban Railway service groups A1, A2, A3 and A4.

Coordinate convention: WGS84 decimal degrees. Columns are latitude then longitude, with longitude shown as the second coordinate column in every table. The OSM stop node is used where available because it is the ordered public transport stop position in the route relation. For shared stations, separate rows can appear when OSM has distinct mode-specific stop positions or platforms.

## Sources

- OASA/National Access Point GTFS dataset page for metro, ISAP and tram fixed-line public transport: https://data.nap.gov.gr/dataset/dromologia-statherwn-metro-hsap-tram
- OASA network page: https://www.oasa.gr/en/oasa-network/
- OpenStreetMap Athens Metro route relations: https://wiki.openstreetmap.org/wiki/Athens/Metro
- OpenStreetMap Athens Tram route relations: https://wiki.openstreetmap.org/wiki/Athens/Tram
- OpenStreetMap Hellenic Train and Athens Suburban Railway route relations: https://wiki.openstreetmap.org/wiki/Greece/Hellenic_Train
- Hellenic Train Athens Suburban and Regional Railway service overview: https://www.hellenictrain.gr/en/athens-suburban-and-regional-railway
- Coordinate extraction: OpenStreetMap API relation/full endpoint for the route relation IDs listed in each table.
- Operational schedule reference: `/Users/p.dhespollari/Downloads/Official Athens Transit Master Schedule (Lines 1, 2, 3 & Tram).md`
- Icon asset source: `/Users/p.dhespollari/Desktop/athens_transit_t7_station_icons_updated`

## Operating Schedule Reference

This section copies the full operating reference from the official master schedule document so the station coordinate index and the service timetable guidance stay in one place.

### Weekly Operating Hours and Exact Last Trains

| Transport line and terminals | Mon-Thu hours | Friday hours | Saturday hours | Sunday hours | Precise last departure times |
|---|---|---|---|---|---|
| Line 1 (Green) - Kifisia to Piraeus | 05:00 - 00:30 | 05:00 - 00:30 | 05:00 - 00:30 | 05:00 - 00:30 | 00:15 from both terminal stations. 00:30 from Omonia to Kifisia and Piraeus. |
| Line 2 (Red) - Anthoupoli to Elliniko | 05:30 - 00:30 | 05:30 - 02:00 | 24 Hours / 24/7 | 05:30 - 00:30 | 00:03 (Sun-Thu) from Elliniko. 00:06 (Sun-Thu) from Anthoupoli. 01:40 (Fri night) from Elliniko. 01:43 (Fri night) from Anthoupoli. |
| Line 3 (Blue) City - Dimotiko Theatro to Doukissis Plakentias | 05:30 - 00:30 | 05:30 - 02:00 | 24 Hours / 24/7 | 05:30 - 00:30 | 00:03 (Sun-Thu) from Piraeus and Doukissis Plakentias. 01:40 (Fri night) from Piraeus and Doukissis Plakentias. |
| Line 3 (Blue) Airport - Doukissis Plakentias to Airport | 05:30 - 23:00 | 05:30 - 23:00 | 05:30 - 23:00 | 05:30 - 23:00 | 23:00 daily from both Airport and Dimotiko Theatro. Excluded from 24/7 and late extensions. |
| Tram (T6 and T7) - Syntagma, Voula, Piraeus | 05:30 - 01:00 | 05:00 - 01:30 | 24 Hours / 24/7 | 05:30 - 01:00 | Last arrivals at terminal stations end exactly at the noted closing times. |

### Minute-by-Minute Frequencies

#### Weekdays (Monday to Friday)

- Morning peak, 07:00 - 10:00
- Lines 2 and 3, city route: every 4 minutes
- Line 3, airport route: every 36 minutes, fixed interval
- Line 1: every 6 minutes
- Tram: every 10 minutes
- Midday off-peak, 10:00 - 17:00
- Lines 2 and 3, city route: every 5 to 7 minutes
- Line 3, airport route: every 36 minutes, fixed interval
- Line 1: every 8 minutes
- Tram: every 12 minutes
- Evening peak, 17:00 - 20:00
- Lines 2 and 3, city route: every 4.5 minutes
- Line 3, airport route: every 36 minutes, fixed interval
- Line 1: every 6 minutes
- Tram: every 10 minutes
- Night off-peak, 20:00 - 22:30
- Lines 1, 2 and 3, city route: every 10 minutes
- Line 3, airport route: every 36 minutes, until 23:00
- Tram: every 15 minutes
- Late night wind-down, 22:30 - 00:30 / 02:00
- Lines 1, 2 and 3, city route: every 15 minutes
- Friday late extensions, 00:30 - 02:00: city lines fixed at exactly every 15 minutes, airport line closed

#### Weekends (Saturday and Sunday)

- Saturday daytime: city Lines 1, 2 and 3 arrive every 7 to 10.5 minutes. Line 3 airport service remains every 36 minutes.
- Saturday overnight 24/7 service: Lines 2, 3 city route only, and the tram arrive every 15 minutes from 00:30 through 05:30 Sunday morning.
- Sunday: holiday-style schedule across all tracks with arrivals every 10.5 to 15 minutes. Line 3 airport service remains every 36 minutes.

### Line 3 Split: Doukissis Plakentias Versus Airport

| Service feature | Core city route, Dimotiko Theatro to Doukissis Plakentias | Airport extension route, Doukissis Plakentias to Athens Airport |
|---|---|---|
| Track type | Completely underground rapid transit subway. | Surface-level tracks shared with the Suburban Railway. |
| Intermediate stations | Serves all 23 urban transit stations. | Stops only at 4 regional hubs: Pallini, Paiania-Kantza, Koropi, Airport. |
| Weekday peak frequencies | Every 4 to 4.5 minutes. | Every 36 minutes, fixed interval. |
| Weekday off-peak frequencies | Every 5 to 9 minutes. | Every 36 minutes, fixed interval. |
| Weekend daytime frequencies | Every 7 to 10.5 minutes. | Every 36 minutes, fixed interval. |
| Precise daily service hours | 05:30 - 00:30, Sun-Thu. | 05:30 - 23:00 daily, strict cutoff. |
| Friday late-night, 00:30 - 02:00 | Open, trains every 15 minutes. | Closed, no trains run past 23:00. |
| Saturday night 24/7 service | Open, continuous city service. | Closed, excluded from overnight service. |

### Public, Bank and National Holiday Rules

- Official public holidays: Dec 25 Christmas Day, Dec 26 Boxing Day, Jan 1 New Year, Clean Monday, Good Friday, Easter Monday, May 1 Labor Day, Oct 28 Ohi Day.
- Default rule: use the standard Sunday frequency, 10 to 15 minutes.
- If a holiday falls on a Friday or Saturday, all late-night extensions and 24/7 service windows are canceled.
- August 15, Assumption of Mary: uses a distinct mid-August holiday shift. Weekday rush peaks are removed. All 3 metro lines maintain a flat 12-minute interval all day long. Airport service remains every 36 minutes.
- Bank holidays and school breaks: Jan 2, Jan 6 Epiphany, Easter weekdays, Nov 17 School Holiday.
- Rule for bank holidays and school breaks: the system follows a Saturday frequency map, 7 to 10-minute intervals. Weekend late-night shifts are preserved if the holiday touches a weekend.
- Mandatory 23:00 shutdown rule: on Dec 24 and Dec 31, final trains leave terminals between 22:00 and 22:20 so that the network is fully shut down by 23:00. The final departure from the Airport is pulled forward to 22:00.

## Icon Asset Rules

- Use the icon pack at `/Users/p.dhespollari/Desktop/athens_transit_t7_station_icons_updated` for all station and vehicle icon rendering across metro, tram and suburban railway.
- Use station icons from `/Users/p.dhespollari/Desktop/athens_transit_t7_station_icons_updated/station_smart_codes/athens_station_smart_code_icons/`.
- Use directional vehicle icons from `/Users/p.dhespollari/Desktop/athens_transit_t7_station_icons_updated/directional_vehicle_icons/`.
- For shared stations with multiple lines or modes, use the icon of the larger, more important line rather than stacking multiple badges. In practice, use metro over tram or suburban at major interchanges such as Syntagma, Monastiraki, Piraeus, Airport and Doukissis Plakentias.
- For suburban train services where the exact destination or direction is unknown, use `/Users/p.dhespollari/Desktop/athens_transit_t7_station_icons_updated/directional_vehicle_icons/generic_vehicle/vehicle_train.svg`.
- Generic fallback vehicles are also available in `/Users/p.dhespollari/Desktop/athens_transit_t7_station_icons_updated/directional_vehicle_icons/generic_vehicle/` for metro and tram if a directional icon cannot be resolved.

## Network Analysis

- Metro is the high-capacity urban spine. M1 is the older surface and elevated north-south line from Piraeus to Kifissia. M2 is the red underground north-west to south-east axis from Anthoupoli to Elliniko. M3 is the blue cross-city and airport axis from Dimotiko Theatro through central Athens to Doukissis Plakentias and Athens Airport.
- Tram is the coastal and central-southern distributor. T6 links Syntagma with Pikrodafni. T7 links the Piraeus coastal terminus at Akti Poseidonos with Asklipiio Voulas. Pikrodafni is the main operational branch junction between the central and coastal tram sections.
- Suburban railway connects the port, central railway station, northern rail corridor, airport corridor, western Attica, Corinthia and Chalcis. A1 and A2 are airport-facing commuter services. A3 is the Athens to Chalcis regional commuter corridor. A4 is the Piraeus to Kiato regional corridor.
- Principal rail interchanges include Piraeus, Syntagma, Monastiraki, Omonia, Attiki, Larissa Station/Athens, Doukissis Plakentias, Neratziotissa, Airport and Pikrodafni. Exact walking interchange distances should be checked separately because this file records stop positions, not every entrance.
- For map or UI rendering, stations served by multiple fixed-rail lines should use the icon of the larger, more important line rather than stacking every mode badge. In practice, use the primary metro icon over tram or suburban at interchanges such as Syntagma, Monastiraki, Piraeus, Airport and Doukissis Plakentias.

## Line Summary

| Mode | Line | Direction used for station order | OSM route relation | Stops extracted |
|---|---:|---|---:|---:|
| Metro | M1 | Piraeus to Kifissia | 445858 | 24 |
| Metro | M2 | Anthoupoli to Elliniko | 7963539 | 20 |
| Metro | M3 | Dimotiko Theatro to Airport | 445945 | 27 |
| Tram | T6 | Syntagma to Pikrodafni | 3648688 | 19 |
| Tram | T7 | Piraeus loop to Asklipiio Voulas | 6792078 | 43 |
| Suburban railway | A1 | Piraeus to Airport | 8467445 | 19 |
| Suburban railway | A2 | Ano Liosia to Airport | 8467443 | 12 |
| Suburban railway | A3 | Athens to Chalcis | 8467442 | 17 |
| Suburban railway | A4 | Piraeus to Kiato | 8467515 | 20 |

## Metro: M1 (Piraeus to Kifissia)

OSM route relation: https://www.openstreetmap.org/relation/445858

| # | Station | Greek name | Latitude | Longitude | OSM object | Role |
|---:|---|---|---:|---:|---|---|
| 1 | Piraeus | Πειραιάς | 37.9480230 | 23.6432467 | node/3962223864 | stop_entry_only |
| 2 | Faliro | Φάληρο | 37.9450255 | 23.6652902 | node/2253053668 | stop |
| 3 | Moschato | Μοσχάτο | 37.9552687 | 23.6804874 | node/2264918304 | stop |
| 4 | Kallithea | Καλλιθέα | 37.9605389 | 23.6974102 | node/2264918788 | stop |
| 5 | Tavros-Eleftherios Venizelos | Ταύρος-Ελευθέριος Βενιζέλος | 37.9625102 | 23.7037122 | node/1024902125 | stop |
| 6 | Petralona | Πετράλωνα | 37.9689559 | 23.7094090 | node/2278850037 | stop |
| 7 | Thiseio | Θησείο | 37.9766852 | 23.7205356 | node/1005304990 | stop |
| 8 | Monastiraki | Μοναστηράκι | 37.9763360 | 23.7258259 | node/9666216520 | stop |
| 9 | Omonia | Ομόνοια | 37.9844999 | 23.7281773 | node/5396738090 | stop |
| 10 | Victoria | Βικτώρια | 37.9934877 | 23.7303950 | node/5396738082 | stop |
| 11 | Attiki | Αττική | 37.9995276 | 23.7228526 | node/2973313516 | stop |
| 12 | Agios Nikolaos | Άγιος Νικόλαος | 38.0069859 | 23.7277292 | node/960915823 | stop |
| 13 | Kato Patisia | Κάτω Πατήσια | 38.0119208 | 23.7286630 | node/9178426348 | stop |
| 14 | Agios Eleftherios | Άγιος Ελευθέριος | 38.0203392 | 23.7319353 | node/9178426345 | stop |
| 15 | Ano Patisia | Άνω Πατήσια | 38.0241359 | 23.7365181 | node/9178426343 | stop |
| 16 | Perissos | Περισσός | 38.0332002 | 23.7450680 | node/9178426341 | stop |
| 17 | Pefkakia | Πευκάκια | 38.0374108 | 23.7505057 | node/9178426339 | stop |
| 18 | Nea Ionia | Νέα Ιωνία | 38.0416198 | 23.7554095 | node/9178426338 | stop |
| 19 | Irakleio | Ηράκλειο | 38.0460142 | 23.7665601 | node/3951242237 | stop |
| 20 | Eirini | Ειρήνη | 38.0429902 | 23.7837888 | node/4689709385 | stop |
| 21 | Νεραντζιώτισσα |  | 38.0455338 | 23.7934250 | node/939521334 | stop |
| 22 | Marousi | Μαρούσι | 38.0565273 | 23.8055114 | node/1815728753 | stop |
| 23 | KAT | ΚΑΤ | 38.0663647 | 23.8042356 | node/2541933609 | stop |
| 24 | Kifissia | Κηφισιά | 38.0733951 | 23.8082198 | node/9178426337 | stop |

## Metro: M2 (Anthoupoli to Elliniko)

OSM route relation: https://www.openstreetmap.org/relation/7963539

| # | Station | Greek name | Latitude | Longitude | OSM object | Role |
|---:|---|---|---:|---:|---|---|
| 1 | Anthoupoli | Ανθούπολη | 38.0171089 | 23.6908755 | node/7480425187 | stop |
| 2 | Peristeri | Περιστέρι | 38.0128856 | 23.6957605 | node/7480417739 | stop |
| 3 | Agios Antonios | Άγιος Αντώνιος | 38.0060932 | 23.6997156 | node/7480425188 | stop |
| 4 | Sepolia | Σεπόλια | 38.0025946 | 23.7140361 | node/7480425105 | stop |
| 5 | Attiki | Αττική | 37.9995336 | 23.7226918 | node/7480425088 | stop |
| 6 | Σταθμός Λαρίσης |  | 37.9922861 | 23.7207006 | node/7480417778 | stop |
| 7 | Metaxourgeio | Μεταξουργείο | 37.9858450 | 23.7213621 | node/7480417768 | stop |
| 8 | Omonia | Ομόνοια | 37.9840538 | 23.7279830 | node/7480417761 | stop |
| 9 | Panepistimio | Πανεπιστήμιο | 37.9803461 | 23.7330038 | node/7480417749 | stop |
| 10 | Syntagma | Σύνταγμα | 37.9755009 | 23.7356474 | node/7480425125 | stop |
| 11 | Akropoli | Ακρόπολη | 37.9688590 | 23.7295551 | node/7480425186 | stop |
| 12 | Syngrou-Fix | Συγγρού-Φίξ | 37.9646366 | 23.7268038 | node/7480425176 | stop |
| 13 | Neos Kosmos | Νέος Κόσμος | 37.9576551 | 23.7283685 | node/7480425154 | stop |
| 14 | Agios Ioannis | Άγιος Ιωάννης | 37.9564161 | 23.7346772 | node/7480425191 | stop |
| 15 | Dafni | Δάφνη | 37.9495529 | 23.7372110 | node/6512724033 | stop |
| 16 | Agios Dimitrios | Άγιος Δημήτριος | 37.9398434 | 23.7407267 | node/2484079186 | stop |
| 17 | Ilioupoli | Ηλιούπολη | 37.9290637 | 23.7447546 | node/7480417719 | stop |
| 18 | Alimos | Άλιμος | 37.9178695 | 23.7440625 | node/2393048312 | stop |
| 19 | Argyroupoli | Αργυρούπολη | 37.9020569 | 23.7456163 | node/2393075569 | stop |
| 20 | Elliniko | Ελληνικό | 37.8925855 | 23.7470953 | node/2393088489 | stop |

## Metro: M3 (Dimotiko Theatro to Airport)

OSM route relation: https://www.openstreetmap.org/relation/445945

| # | Station | Greek name | Latitude | Longitude | OSM object | Role |
|---:|---|---|---:|---:|---|---|
| 1 | Δημοτικό Θέατρο |  | 37.9429173 | 23.6475926 | node/7485440486 | stop |
| 2 | Piraeus | Πειραιάς | 37.9486160 | 23.6423273 | node/7485440505 | stop |
| 3 | Μανιάτικα |  | 37.9600579 | 23.6397874 | node/7485440516 | stop |
| 4 | Νίκαια |  | 37.9660813 | 23.6478426 | node/7485440531 | stop |
| 5 | Korydallos | Κορυδαλλός | 37.9770388 | 23.6504221 | node/7485440548 | stop |
| 6 | Agia Varvara | Αγία Βαρβάρα | 37.9899646 | 23.6593530 | node/7485440560 | stop |
| 7 | Agia Marina | Αγία Μαρίνα | 37.9968128 | 23.6677666 | node/5358007122 | stop |
| 8 | Egaleo | Αιγάλεω | 37.9913832 | 23.6818003 | node/7484233202 | stop |
| 9 | Eleonas | Ελαιώνας | 37.9877488 | 23.6933605 | node/7484233201 | stop |
| 10 | Kerameikos | Κεραμεικός | 37.9785236 | 23.7115309 | node/7484233158 | stop |
| 11 | Monastiraki | Μοναστηράκι | 37.9766579 | 23.7263158 | node/7478372546 | stop |
| 12 | Syntagma | Σύνταγμα | 37.9748526 | 23.7357062 | node/7478372558 | stop |
| 13 | Evangelismos | Ευαγγελισμός | 37.9761107 | 23.7471092 | node/7478372569 | stop |
| 14 | Megaro Mousikis | Μέγαρο Μουσικής | 37.9790211 | 23.7530234 | node/7478372579 | stop |
| 15 | Ambelokipi | Αμπελόκηποι | 37.9871894 | 23.7576995 | node/7478372593 | stop |
| 16 | Panormou | Πανόρμου | 37.9931964 | 23.7635814 | node/7478372610 | stop |
| 17 | Katechaki | Κατεχάκη | 37.9937402 | 23.7767641 | node/7478322247 | stop |
| 18 | Ethniki Amyna | Εθνική Άμυνα | 37.9991725 | 23.7844882 | node/7484233212 | stop |
| 19 | Cholargos | Χολαργός | 38.0048986 | 23.7947223 | node/7484233226 | stop |
| 20 | Nomismatokopio | Νομισματοκοπείο | 38.0090857 | 23.8057861 | node/7484233242 | stop |
| 21 | Agia Paraskevi | Αγία Παρασκευή | 38.0171203 | 23.8125006 | node/7484233251 | stop |
| 22 | Chalandri | Χαλάνδρι | 38.0216963 | 23.8207595 | node/7484233263 | stop |
| 23 | Δουκίσσης Πλακεντίας |  | 38.0239773 | 23.8325569 | node/7484233286 | stop |
| 24 | Παλλήνη |  | 38.0045807 | 23.8699500 | node/10700888533 | stop |
| 25 | Peania-Kantza | Παιανία-Κάντζα | 37.9849428 | 23.8700731 | node/937892866 | stop |
| 26 | Κορωπί |  | 37.9125370 | 23.8960768 | node/10700888565 | stop |
| 27 | Αεροδρόμιο |  | 37.9368813 | 23.9447336 | node/7799596873 | stop_exit_only |

## Tram: T6 (Syntagma to Pikrodafni)

OSM route relation: https://www.openstreetmap.org/relation/3648688

| # | Station | Greek name | Latitude | Longitude | OSM object | Role |
|---:|---|---|---:|---:|---|---|
| 1 | Syntagma | Σύνταγμα | 37.9744347 | 23.7353381 | node/940327783 | stop_entry_only |
| 2 | Zappeion | Ζάππειο | 37.9693069 | 23.7364183 | node/364338802 | stop |
| 3 | Vouliagmenis | Βουλιαγμένης | 37.9667197 | 23.7317325 | node/6021744958 | stop |
| 4 | Fix | Φίξ | 37.9648164 | 23.7276122 | node/6021744964 | stop |
| 5 | Kasomouli | Κασομούλη | 37.9604298 | 23.7234816 | node/6021744996 | stop |
| 6 | Νέος Κόσμος |  | 37.9569955 | 23.7270853 | node/821499092 | stop |
| 7 | Baknana | Μπακνανά | 37.9546714 | 23.7238777 | node/939869073 | stop |
| 8 | Aegeou | Αιγαίου | 37.9501464 | 23.7189049 | node/6020438754 | stop |
| 9 | Αγίας Φωτεινής-Πλατεία |  | 37.9467388 | 23.7150445 | node/939869148 | stop |
| 10 | Alexander the Great | Μεγάλου Αλεξάνδρου | 37.9427014 | 23.7137932 | node/6021744944 | stop |
| 11 | Aghia Paraskevi | Αγία Παρασκευή | 37.9402146 | 23.7130260 | node/9661848150 | stop |
| 12 | Medeas - Mykalis | Μηδείας - Μυκάλης | 37.9371506 | 23.7120552 | node/6021744937 | stop |
| 13 | Evangeliki Scholi | Ευαγγελική Σχολή | 37.9333042 | 23.7108485 | node/6021744935 | stop |
| 14 | Achilleos | Αχιλλέως | 37.9301057 | 23.7098664 | node/925377298 | stop |
| 15 | Amfitheas | Αμφιθέας | 37.9281400 | 23.7050355 | node/9661865581 | stop |
| 16 | Panaghitsa | Παναγίτσα | 37.9252678 | 23.7018664 | node/9661865578 | stop |
| 17 | Mousson | Μουσών | 37.9220022 | 23.6997361 | node/9661865576 | stop |
| 18 | Edem | Έδεμ | 37.9187302 | 23.7006817 | node/7498860474 | stop |
| 19 | Pikrodafni | Πικροδάφνη | 37.9159342 | 23.7054737 | node/7498860473 | stop_exit_only |

## Tram: T7 (Piraeus loop to Asklipiio Voulas)

OSM route relation: https://www.openstreetmap.org/relation/6792078

Note: the six Piraeus loop inbound stops below are included for completeness of the one-way circular section into Akti Poseidonos. They come from OSM tram stop nodes in the Piraeus loop, while the previously extracted sequence covered the outward run from Akti Poseidonos toward Voula.

| # | Station | Greek name | Latitude | Longitude | OSM object | Role |
|---:|---|---|---:|---:|---|---|
| 1 | Dimarhio / Dimotiko Theatro | Δημαρχείο / Δημοτικό Θέατρο | 37.9416350 | 23.6506700 | node/8583339901 | stop |
| 2 | Plateia Deligianni | Πλατεία Δεληγιάννη | 37.9449550 | 23.6535200 | node/9706137963 | stop |
| 3 | Evangelistria | Ευαγγελίστρια | 37.9480500 | 23.6561550 | node/3994676193 | stop |
| 4 | Grigoriou Lambraki | Γρηγορίου Λαμπράκη | 37.9457700 | 23.6601800 | node/8583339869 | stop |
| 5 | Mikras Asias | Μικράς Ασίας | 37.9444100 | 23.6649650 | node/8583339860 | stop |
| 6 | Gipedo Karaiskaki | Γήπεδο Καραϊσκάκη | 37.9445650 | 23.6687750 | node/8582839502 | stop |
| 7 | Akti Poseidonos | Ακτή Ποσειδώνος | 37.9449134 | 23.6430209 | node/6647614914 | stop_entry_only |
| 8 | Agia Triada | Αγία Τριάδα | 37.9449403 | 23.6451956 | node/8583339904 | stop |
| 9 | Plateia Ippodameias | Πλατεία Ιπποδαμείας | 37.9474688 | 23.6475793 | node/3994091467 | stop |
| 10 | 34 Syntagmatos Pezikou | 34ου Συντάγματος Πεζικού | 37.9485363 | 23.6522544 | node/3994091494 | stop |
| 11 | Androutsou | Ανδρούτσου | 37.9478346 | 23.6560463 | node/8583339910 | stop |
| 12 | Omiridou Skylitsi | Ομηρίδου Σκυλίτση | 37.9450359 | 23.6609189 | node/8583339911 | stop |
| 13 | Peace and Friendship Stadium | Στάδιο Ειρήνης και Φιλίας | 37.9438634 | 23.6638070 | node/7498877785 | stop |
| 14 | Neo Faliro | Νέο Φάληρο | 37.9445726 | 23.6685528 | node/9661591698 | stop |
| 15 | Moschato | Μοσχάτο | 37.9442591 | 23.6779634 | node/4000027208 | stop |
| 16 | Kallithea | Καλλιθέα | 37.9427133 | 23.6840167 | node/7498860482 | stop |
| 17 | Tzitzifies | Τζιτζιφιές | 37.9407062 | 23.6880185 | node/7498860481 | stop |
| 18 | Delta Falirou | Δέλτα Φαλήρου | 37.9374095 | 23.6922895 | node/8446553902 | stop |
| 19 | Aghia Skepi | Αγία Σκέπη | 37.9340166 | 23.6939305 | node/8420521608 | stop |
| 20 | Trocadero | Τροκαντερό | 37.9313941 | 23.6872264 | node/7498860478 | stop |
| 21 | Parko Flisvou | Πάρκο Φλοίσβου | 37.9278472 | 23.6884354 | node/7498860477 | stop |
| 22 | Flisvos | Φλοίσβος | 37.9235633 | 23.6927091 | node/7498860476 | stop |
| 23 | Batis | Μπάτης | 37.9215266 | 23.6964201 | node/7498860475 | stop |
| 24 | Edem | Έδεμ | 37.9187302 | 23.7006817 | node/7498860474 | stop |
| 25 | Pikrodafni | Πικροδάφνη | 37.9158920 | 23.7054124 | node/9661591687 | stop |
| 26 | Marina Alimou | Μαρίνα Αλίμου | 37.9129907 | 23.7087188 | node/9664372895 | stop |
| 27 | Kalamaki | Καλαμάκι | 37.9096776 | 23.7128631 | node/9664372893 | stop |
| 28 | Zefyros | Ζέφυρος | 37.9063769 | 23.7170206 | node/7498860470 | stop |
| 29 | Loutra Alimou | Λουτρά Αλίμου | 37.9022284 | 23.7195316 | node/7498860469 | stop |
| 30 | Elliniko | Ελληνικό | 37.8975873 | 23.7201746 | node/9664372890 | stop |
| 31 | 1st Aghiou Kosma | 1η Αγίου Κοσμά | 37.8942956 | 23.7211661 | node/1579839513 | stop |
| 32 | 2nd Aghiou Kosma | 2η Αγίου Κοσμά | 37.8907821 | 23.7231968 | node/9664372889 | stop |
| 33 | Aghios Alexandros | Άγιος Αλέξανδρος | 37.8850490 | 23.7269291 | node/1579908440 | stop |
| 34 | Ellinon Olymbionikon | Ελλήνων Ολυμπιονικών | 37.8811588 | 23.7294629 | node/9664372888 | stop |
| 35 | Kentro Istioploias | Κέντρο Ιστιοπλοΐας | 37.8758816 | 23.7318572 | node/9664372887 | stop |
| 36 | Platia Vergoti | Πλατεία Βεργωτή | 37.8714672 | 23.7351894 | node/9664372886 | stop |
| 37 | Paralia Glyfadas | Παραλία Γλυφάδας | 37.8677342 | 23.7383803 | node/9664372885 | stop |
| 38 | Paleo Demarhio | Παλαιό Δημαρχείο | 37.8646553 | 23.7431856 | node/9664372884 | stop |
| 39 | Platia Vaso Katraki | Πλατεία Βάσω Κατράκη | 37.8634672 | 23.7475090 | node/6023128561 | stop |
| 40 | Agheiou Metaxa | Άγγελου Μεταξά | 37.8627144 | 23.7514044 | node/7498860459 | stop |
| 41 | Platia Esperidon | Πλατεία Εσπερίδων | 37.8601549 | 23.7541971 | node/364342642 | stop |
| 42 | Kolymvitirio | Κολυμβητήριο | 37.8560908 | 23.7542381 | node/7498860458 | stop |
| 43 | Asklipiio Voulas | Ασκληπιείο Βούλας | 37.8496644 | 23.7525900 | node/7292595531 | stop_exit_only |

## Suburban railway: A1 (Piraeus to Airport)

OSM route relation: https://www.openstreetmap.org/relation/8467445

| # | Station | Greek name | Latitude | Longitude | OSM object | Role |
|---:|---|---|---:|---:|---|---|
| 1 | Piraeus | Πειραιάς | 37.9490666 | 23.6434156 | node/2137213028 | stop_entry_only |
| 2 | Λεύκα |  | 37.9555849 | 23.6541235 | node/9654883729 | stop |
| 3 | Ρέντης |  | 37.9622619 | 23.6683076 | node/2148259068 | stop |
| 4 | Ταύρος |  | 37.9689397 | 23.6942248 | node/2148380495 | stop |
| 5 | Ρουφ |  | 37.9736019 | 23.7040087 | node/2148529035 | stop |
| 6 | Αθήνα |  | 37.9931275 | 23.7202839 | node/2872124552 | stop |
| 7 | Άγιοι Ανάργυροι |  | 38.0224583 | 23.7186452 | node/2354629543 | stop |
| 8 | Πύργος Βασιλίσσης |  | 38.0400365 | 23.7276759 | node/2343647510 | stop |
| 9 | Κάτω Αχαρναί |  | 38.0547251 | 23.7328066 | node/2343670500 | stop |
| 10 | Μεταμόρφωση |  | 38.0600674 | 23.7562064 | node/360017697 | stop |
| 11 | Ηράκλειο |  | 38.0567845 | 23.7720928 | node/936804085 | stop |
| 12 | Νερατζιώτισσα |  | 38.0447682 | 23.7941064 | node/936804443 | stop |
| 13 | Κηφισίας |  | 38.0419210 | 23.8040729 | node/936804010 | stop |
| 14 | Πεντέλης |  | 38.0328796 | 23.8225838 | node/3781391905 | stop |
| 15 | Δουκίσσης Πλακεντίας |  | 38.0247026 | 23.8338693 | node/2142437054 | stop |
| 16 | Παλλήνη |  | 38.0054925 | 23.8696405 | node/937378118 | stop |
| 17 | Peania-Kantza | Παιανία-Κάντζα | 37.9839819 | 23.8698255 | node/937892783 | stop |
| 18 | Koropi | Κορωπί | 37.9133050 | 23.8955043 | node/10700888563 | stop |
| 19 | Airport | Αεροδρόμιο | 37.9368156 | 23.9448470 | node/5777457454 | stop_exit_only |

## Suburban railway: A2 (Ano Liosia to Airport)

OSM route relation: https://www.openstreetmap.org/relation/8467443

| # | Station | Greek name | Latitude | Longitude | OSM object | Role |
|---:|---|---|---:|---:|---|---|
| 1 | Άνω Λιόσια |  | 38.0707953 | 23.7100051 | node/5777457451 | stop_entry_only |
| 2 | Acharnai Railway Center | Σιδηροδρομικό Κέντρο Αχαρνών | 38.0656438 | 23.7376508 | node/2354656162 | stop |
| 3 | Μεταμόρφωση |  | 38.0600674 | 23.7562064 | node/360017697 | stop |
| 4 | Ηράκλειο |  | 38.0567845 | 23.7720928 | node/936804085 | stop |
| 5 | Νερατζιώτισσα |  | 38.0447682 | 23.7941064 | node/936804443 | stop |
| 6 | Κηφισίας |  | 38.0419210 | 23.8040729 | node/936804010 | stop |
| 7 | Πεντέλης |  | 38.0328796 | 23.8225838 | node/3781391905 | stop |
| 8 | Δουκίσσης Πλακεντίας |  | 38.0247026 | 23.8338693 | node/2142437054 | stop |
| 9 | Παλλήνη |  | 38.0054925 | 23.8696405 | node/937378118 | stop |
| 10 | Peania-Kantza | Παιανία-Κάντζα | 37.9839819 | 23.8698255 | node/937892783 | stop |
| 11 | Koropi | Κορωπί | 37.9133050 | 23.8955043 | node/10700888563 | stop |
| 12 | Airport | Αεροδρόμιο | 37.9368156 | 23.9448470 | node/5777457454 | stop_exit_only |

## Suburban railway: A3 (Athens to Chalcis)

OSM route relation: https://www.openstreetmap.org/relation/8467442

| # | Station | Greek name | Latitude | Longitude | OSM object | Role |
|---:|---|---|---:|---:|---|---|
| 1 | Αθήνα |  | 37.9931275 | 23.7202839 | node/2872124552 | stop_entry_only |
| 2 | Άγιοι Ανάργυροι |  | 38.0224583 | 23.7186452 | node/2354629543 | stop |
| 3 | Acharnai Railway Center | Σιδηροδρομικό Κέντρο Αχαρνών | 38.0686445 | 23.7378210 | node/2354641739 | stop |
| 4 | Αχαρνές |  | 38.0802534 | 23.7440766 | node/2798912795 | stop |
| 5 | Δεκέλεια |  | 38.0997540 | 23.7801125 | node/966702938 | stop |
| 6 | Άγιος Στέφανος |  | 38.1403696 | 23.8591873 | node/364329621 | stop |
| 7 | Αφίδνες |  | 38.1883264 | 23.8444658 | node/4683332020 | stop |
| 8 | Σφενδάλη |  | 38.2354158 | 23.7844141 | node/155891790 | stop |
| 9 | Αυλώνας |  | 38.2504724 | 23.6955986 | node/368164861 | stop |
| 10 | Άγιος Θωμάς |  | 38.2816770 | 23.6672270 | node/2291068164 | stop |
| 11 | Οινόφυτα |  | 38.3069654 | 23.6338955 | node/155927732 | stop |
| 12 | Οινόη |  | 38.3230172 | 23.6090770 | node/155927792 | stop |
| 13 | Δήλεσι |  | 38.3376364 | 23.6094499 | node/2291065001 | stop |
| 14 | Άγιος Γεώργιος |  | 38.3548928 | 23.6074074 | node/2291066231 | stop |
| 15 | Καλοχώρι-Παντείχι |  | 38.3893073 | 23.5931559 | node/155706004 | stop |
| 16 | Αυλίδα |  | 38.4044464 | 23.6033835 | node/155708452 | stop |
| 17 | Chalkida | Χαλκίδα | 38.4625271 | 23.5861659 | node/5777457447 | stop_exit_only |

## Suburban railway: A4 (Piraeus to Kiato)

OSM route relation: https://www.openstreetmap.org/relation/8467515

| # | Station | Greek name | Latitude | Longitude | OSM object | Role |
|---:|---|---|---:|---:|---|---|
| 1 | Piraeus | Πειραιάς | 37.9490666 | 23.6434156 | node/2137213028 | stop_entry_only |
| 2 | Λεύκα |  | 37.9555849 | 23.6541235 | node/9654883729 | stop |
| 3 | Ρέντης |  | 37.9622619 | 23.6683076 | node/2148259068 | stop |
| 4 | Ταύρος |  | 37.9689397 | 23.6942248 | node/2148380495 | stop |
| 5 | Ρουφ |  | 37.9736019 | 23.7040087 | node/2148529035 | stop |
| 6 | Αθήνα |  | 37.9931275 | 23.7202839 | node/2872124552 | stop |
| 7 | Άγιοι Ανάργυροι |  | 38.0224583 | 23.7186452 | node/2354629543 | stop |
| 8 | Πύργος Βασιλίσσης |  | 38.0400365 | 23.7276759 | node/2343647510 | stop |
| 9 | Κάτω Αχαρναί |  | 38.0547251 | 23.7328066 | node/2343670500 | stop |
| 10 | Ζεφύρι |  | 38.0699579 | 23.7163427 | node/3979025748 | stop |
| 11 | Άνω Λιόσια |  | 38.0707953 | 23.7100051 | node/5777457451 | stop |
| 12 | Ασπρόπυργος |  | 38.0810388 | 23.6042595 | node/5777574610 | stop |
| 13 | Μαγούλα |  | 38.0730827 | 23.5291665 | node/832802993 | stop |
| 14 | Νέα Πέραμος |  | 38.0127986 | 23.4132616 | node/4919885663 | stop |
| 15 | Μέγαρα |  | 37.9910006 | 23.3610190 | node/836837794 | stop |
| 16 | Κινέτα |  | 37.9654426 | 23.2010371 | node/5777574608 | stop |
| 17 | Άγιοι Θεοδώροι |  | 37.9332405 | 23.1369832 | node/835280795 | stop |
| 18 | Corinth | Κόρινθος | 37.9209680 | 22.9323960 | node/835489926 | stop |
| 19 | Ζευγολατιό |  | 37.9263503 | 22.8046326 | node/3155138146 | stop |
| 20 | Κιάτο |  | 38.0139838 | 22.7348102 | node/309957342 | stop_exit_only |

## Combined Station Index

This index keeps distinct stop positions when coordinates differ materially, even if the passenger-facing station name is shared. For icon selection in downstream map assets, use the highest-priority line icon for shared stations rather than showing every line icon, for example metro over tram at Syntagma and metro over suburban where both modes share the same station complex.

| Station | Greek name | Modes | Lines | Latitude | Longitude | OSM objects |
|---|---|---|---|---:|---:|---|
| 1st Aghiou Kosma | 1η Αγίου Κοσμά | Tram | T7 | 37.8942956 | 23.7211661 | node/1579839513 |
| 2nd Aghiou Kosma | 2η Αγίου Κοσμά | Tram | T7 | 37.8907821 | 23.7231968 | node/9664372889 |
| 34 Syntagmatos Pezikou | 34ου Συντάγματος Πεζικού | Tram | T7 | 37.9485363 | 23.6522544 | node/3994091494 |
| Acharnai Railway Center | Σιδηροδρομικό Κέντρο Αχαρνών | Suburban railway | A2 | 38.0656438 | 23.7376508 | node/2354656162 |
| Acharnai Railway Center | Σιδηροδρομικό Κέντρο Αχαρνών | Suburban railway | A3 | 38.0686445 | 23.7378210 | node/2354641739 |
| Achilleos | Αχιλλέως | Tram | T6 | 37.9301057 | 23.7098664 | node/925377298 |
| Aegeou | Αιγαίου | Tram | T6 | 37.9501464 | 23.7189049 | node/6020438754 |
| Agheiou Metaxa | Άγγελου Μεταξά | Tram | T7 | 37.8627144 | 23.7514044 | node/7498860459 |
| Aghia Paraskevi | Αγία Παρασκευή | Tram | T6 | 37.9402146 | 23.7130260 | node/9661848150 |
| Aghia Skepi | Αγία Σκέπη | Tram | T7 | 37.9340166 | 23.6939305 | node/8420521608 |
| Aghios Alexandros | Άγιος Αλέξανδρος | Tram | T7 | 37.8850490 | 23.7269291 | node/1579908440 |
| Agia Marina | Αγία Μαρίνα | Metro | M3 | 37.9968128 | 23.6677666 | node/5358007122 |
| Agia Paraskevi | Αγία Παρασκευή | Metro | M3 | 38.0171203 | 23.8125006 | node/7484233251 |
| Agia Triada | Αγία Τριάδα | Tram | T7 | 37.9449403 | 23.6451956 | node/8583339904 |
| Agia Varvara | Αγία Βαρβάρα | Metro | M3 | 37.9899646 | 23.6593530 | node/7485440560 |
| Agios Antonios | Άγιος Αντώνιος | Metro | M2 | 38.0060932 | 23.6997156 | node/7480425188 |
| Agios Dimitrios | Άγιος Δημήτριος | Metro | M2 | 37.9398434 | 23.7407267 | node/2484079186 |
| Agios Eleftherios | Άγιος Ελευθέριος | Metro | M1 | 38.0203392 | 23.7319353 | node/9178426345 |
| Agios Ioannis | Άγιος Ιωάννης | Metro | M2 | 37.9564161 | 23.7346772 | node/7480425191 |
| Agios Nikolaos | Άγιος Νικόλαος | Metro | M1 | 38.0069859 | 23.7277292 | node/960915823 |
| Airport | Αεροδρόμιο | Suburban railway | A1, A2 | 37.9368156 | 23.9448470 | node/5777457454 |
| Akropoli | Ακρόπολη | Metro | M2 | 37.9688590 | 23.7295551 | node/7480425186 |
| Akti Poseidonos | Ακτή Ποσειδώνος | Tram | T7 | 37.9449134 | 23.6430209 | node/6647614914 |
| Alexander the Great | Μεγάλου Αλεξάνδρου | Tram | T6 | 37.9427014 | 23.7137932 | node/6021744944 |
| Alimos | Άλιμος | Metro | M2 | 37.9178695 | 23.7440625 | node/2393048312 |
| Ambelokipi | Αμπελόκηποι | Metro | M3 | 37.9871894 | 23.7576995 | node/7478372593 |
| Amfitheas | Αμφιθέας | Tram | T6 | 37.9281400 | 23.7050355 | node/9661865581 |
| Androutsou | Ανδρούτσου | Tram | T7 | 37.9478346 | 23.6560463 | node/8583339910 |
| Ano Patisia | Άνω Πατήσια | Metro | M1 | 38.0241359 | 23.7365181 | node/9178426343 |
| Anthoupoli | Ανθούπολη | Metro | M2 | 38.0171089 | 23.6908755 | node/7480425187 |
| Argyroupoli | Αργυρούπολη | Metro | M2 | 37.9020569 | 23.7456163 | node/2393075569 |
| Asklipiio Voulas | Ασκληπιείο Βούλας | Tram | T7 | 37.8496644 | 23.7525900 | node/7292595531 |
| Attiki | Αττική | Metro | M1 | 37.9995276 | 23.7228526 | node/2973313516 |
| Attiki | Αττική | Metro | M2 | 37.9995336 | 23.7226918 | node/7480425088 |
| Baknana | Μπακνανά | Tram | T6 | 37.9546714 | 23.7238777 | node/939869073 |
| Batis | Μπάτης | Tram | T7 | 37.9215266 | 23.6964201 | node/7498860475 |
| Chalandri | Χαλάνδρι | Metro | M3 | 38.0216963 | 23.8207595 | node/7484233263 |
| Chalkida | Χαλκίδα | Suburban railway | A3 | 38.4625271 | 23.5861659 | node/5777457447 |
| Cholargos | Χολαργός | Metro | M3 | 38.0048986 | 23.7947223 | node/7484233226 |
| Corinth | Κόρινθος | Suburban railway | A4 | 37.9209680 | 22.9323960 | node/835489926 |
| Dafni | Δάφνη | Metro | M2 | 37.9495529 | 23.7372110 | node/6512724033 |
| Delta Falirou | Δέλτα Φαλήρου | Tram | T7 | 37.9374095 | 23.6922895 | node/8446553902 |
| Dimarhio / Dimotiko Theatro | Δημαρχείο / Δημοτικό Θέατρο | Tram | T7 | 37.9416350 | 23.6506700 | node/8583339901 |
| Edem | Έδεμ | Tram | T6, T7 | 37.9187302 | 23.7006817 | node/7498860474 |
| Egaleo | Αιγάλεω | Metro | M3 | 37.9913832 | 23.6818003 | node/7484233202 |
| Eirini | Ειρήνη | Metro | M1 | 38.0429902 | 23.7837888 | node/4689709385 |
| Eleonas | Ελαιώνας | Metro | M3 | 37.9877488 | 23.6933605 | node/7484233201 |
| Elliniko | Ελληνικό | Metro | M2 | 37.8925855 | 23.7470953 | node/2393088489 |
| Elliniko | Ελληνικό | Tram | T7 | 37.8975873 | 23.7201746 | node/9664372890 |
| Ellinon Olymbionikon | Ελλήνων Ολυμπιονικών | Tram | T7 | 37.8811588 | 23.7294629 | node/9664372888 |
| Ethniki Amyna | Εθνική Άμυνα | Metro | M3 | 37.9991725 | 23.7844882 | node/7484233212 |
| Evangeliki Scholi | Ευαγγελική Σχολή | Tram | T6 | 37.9333042 | 23.7108485 | node/6021744935 |
| Evangelismos | Ευαγγελισμός | Metro | M3 | 37.9761107 | 23.7471092 | node/7478372569 |
| Evangelistria | Ευαγγελίστρια | Tram | T7 | 37.9480500 | 23.6561550 | node/3994676193 |
| Faliro | Φάληρο | Metro | M1 | 37.9450255 | 23.6652902 | node/2253053668 |
| Fix | Φίξ | Tram | T6 | 37.9648164 | 23.7276122 | node/6021744964 |
| Flisvos | Φλοίσβος | Tram | T7 | 37.9235633 | 23.6927091 | node/7498860476 |
| Gipedo Karaiskaki | Γήπεδο Καραϊσκάκη | Tram | T7 | 37.9445650 | 23.6687750 | node/8582839502 |
| Grigoriou Lambraki | Γρηγορίου Λαμπράκη | Tram | T7 | 37.9457700 | 23.6601800 | node/8583339869 |
| Ilioupoli | Ηλιούπολη | Metro | M2 | 37.9290637 | 23.7447546 | node/7480417719 |
| Irakleio | Ηράκλειο | Metro | M1 | 38.0460142 | 23.7665601 | node/3951242237 |
| Kalamaki | Καλαμάκι | Tram | T7 | 37.9096776 | 23.7128631 | node/9664372893 |
| Kallithea | Καλλιθέα | Tram | T7 | 37.9427133 | 23.6840167 | node/7498860482 |
| Kallithea | Καλλιθέα | Metro | M1 | 37.9605389 | 23.6974102 | node/2264918788 |
| Kasomouli | Κασομούλη | Tram | T6 | 37.9604298 | 23.7234816 | node/6021744996 |
| KAT | ΚΑΤ | Metro | M1 | 38.0663647 | 23.8042356 | node/2541933609 |
| Katechaki | Κατεχάκη | Metro | M3 | 37.9937402 | 23.7767641 | node/7478322247 |
| Kato Patisia | Κάτω Πατήσια | Metro | M1 | 38.0119208 | 23.7286630 | node/9178426348 |
| Kentro Istioploias | Κέντρο Ιστιοπλοΐας | Tram | T7 | 37.8758816 | 23.7318572 | node/9664372887 |
| Kerameikos | Κεραμεικός | Metro | M3 | 37.9785236 | 23.7115309 | node/7484233158 |
| Kifissia | Κηφισιά | Metro | M1 | 38.0733951 | 23.8082198 | node/9178426337 |
| Kolymvitirio | Κολυμβητήριο | Tram | T7 | 37.8560908 | 23.7542381 | node/7498860458 |
| Koropi | Κορωπί | Suburban railway | A1, A2 | 37.9133050 | 23.8955043 | node/10700888563 |
| Korydallos | Κορυδαλλός | Metro | M3 | 37.9770388 | 23.6504221 | node/7485440548 |
| Loutra Alimou | Λουτρά Αλίμου | Tram | T7 | 37.9022284 | 23.7195316 | node/7498860469 |
| Marina Alimou | Μαρίνα Αλίμου | Tram | T7 | 37.9129907 | 23.7087188 | node/9664372895 |
| Marousi | Μαρούσι | Metro | M1 | 38.0565273 | 23.8055114 | node/1815728753 |
| Medeas - Mykalis | Μηδείας - Μυκάλης | Tram | T6 | 37.9371506 | 23.7120552 | node/6021744937 |
| Megaro Mousikis | Μέγαρο Μουσικής | Metro | M3 | 37.9790211 | 23.7530234 | node/7478372579 |
| Metaxourgeio | Μεταξουργείο | Metro | M2 | 37.9858450 | 23.7213621 | node/7480417768 |
| Mikras Asias | Μικράς Ασίας | Tram | T7 | 37.9444100 | 23.6649650 | node/8583339860 |
| Monastiraki | Μοναστηράκι | Metro | M1 | 37.9763360 | 23.7258259 | node/9666216520 |
| Monastiraki | Μοναστηράκι | Metro | M3 | 37.9766579 | 23.7263158 | node/7478372546 |
| Moschato | Μοσχάτο | Tram | T7 | 37.9442591 | 23.6779634 | node/4000027208 |
| Moschato | Μοσχάτο | Metro | M1 | 37.9552687 | 23.6804874 | node/2264918304 |
| Mousson | Μουσών | Tram | T6 | 37.9220022 | 23.6997361 | node/9661865576 |
| Nea Ionia | Νέα Ιωνία | Metro | M1 | 38.0416198 | 23.7554095 | node/9178426338 |
| Neo Faliro | Νέο Φάληρο | Tram | T7 | 37.9445726 | 23.6685528 | node/9661591698 |
| Neos Kosmos | Νέος Κόσμος | Metro | M2 | 37.9576551 | 23.7283685 | node/7480425154 |
| Nomismatokopio | Νομισματοκοπείο | Metro | M3 | 38.0090857 | 23.8057861 | node/7484233242 |
| Omiridou Skylitsi | Ομηρίδου Σκυλίτση | Tram | T7 | 37.9450359 | 23.6609189 | node/8583339911 |
| Omonia | Ομόνοια | Metro | M2 | 37.9840538 | 23.7279830 | node/7480417761 |
| Omonia | Ομόνοια | Metro | M1 | 37.9844999 | 23.7281773 | node/5396738090 |
| Paleo Demarhio | Παλαιό Δημαρχείο | Tram | T7 | 37.8646553 | 23.7431856 | node/9664372884 |
| Panaghitsa | Παναγίτσα | Tram | T6 | 37.9252678 | 23.7018664 | node/9661865578 |
| Panepistimio | Πανεπιστήμιο | Metro | M2 | 37.9803461 | 23.7330038 | node/7480417749 |
| Panormou | Πανόρμου | Metro | M3 | 37.9931964 | 23.7635814 | node/7478372610 |
| Paralia Glyfadas | Παραλία Γλυφάδας | Tram | T7 | 37.8677342 | 23.7383803 | node/9664372885 |
| Parko Flisvou | Πάρκο Φλοίσβου | Tram | T7 | 37.9278472 | 23.6884354 | node/7498860477 |
| Peace and Friendship Stadium | Στάδιο Ειρήνης και Φιλίας | Tram | T7 | 37.9438634 | 23.6638070 | node/7498877785 |
| Peania-Kantza | Παιανία-Κάντζα | Suburban railway | A1, A2 | 37.9839819 | 23.8698255 | node/937892783 |
| Peania-Kantza | Παιανία-Κάντζα | Metro | M3 | 37.9849428 | 23.8700731 | node/937892866 |
| Pefkakia | Πευκάκια | Metro | M1 | 38.0374108 | 23.7505057 | node/9178426339 |
| Perissos | Περισσός | Metro | M1 | 38.0332002 | 23.7450680 | node/9178426341 |
| Peristeri | Περιστέρι | Metro | M2 | 38.0128856 | 23.6957605 | node/7480417739 |
| Petralona | Πετράλωνα | Metro | M1 | 37.9689559 | 23.7094090 | node/2278850037 |
| Pikrodafni | Πικροδάφνη | Tram | T7 | 37.9158920 | 23.7054124 | node/9661591687 |
| Pikrodafni | Πικροδάφνη | Tram | T6 | 37.9159342 | 23.7054737 | node/7498860473 |
| Piraeus | Πειραιάς | Metro | M1 | 37.9480230 | 23.6432467 | node/3962223864 |
| Piraeus | Πειραιάς | Metro | M3 | 37.9486160 | 23.6423273 | node/7485440505 |
| Piraeus | Πειραιάς | Suburban railway | A1, A4 | 37.9490666 | 23.6434156 | node/2137213028 |
| Plateia Deligianni | Πλατεία Δεληγιάννη | Tram | T7 | 37.9449550 | 23.6535200 | node/9706137963 |
| Plateia Ippodameias | Πλατεία Ιπποδαμείας | Tram | T7 | 37.9474688 | 23.6475793 | node/3994091467 |
| Platia Esperidon | Πλατεία Εσπερίδων | Tram | T7 | 37.8601549 | 23.7541971 | node/364342642 |
| Platia Vaso Katraki | Πλατεία Βάσω Κατράκη | Tram | T7 | 37.8634672 | 23.7475090 | node/6023128561 |
| Platia Vergoti | Πλατεία Βεργωτή | Tram | T7 | 37.8714672 | 23.7351894 | node/9664372886 |
| Sepolia | Σεπόλια | Metro | M2 | 38.0025946 | 23.7140361 | node/7480425105 |
| Syngrou-Fix | Συγγρού-Φίξ | Metro | M2 | 37.9646366 | 23.7268038 | node/7480425176 |
| Syntagma | Σύνταγμα | Tram | T6 | 37.9744347 | 23.7353381 | node/940327783 |
| Syntagma | Σύνταγμα | Metro | M3 | 37.9748526 | 23.7357062 | node/7478372558 |
| Syntagma | Σύνταγμα | Metro | M2 | 37.9755009 | 23.7356474 | node/7480425125 |
| Tavros-Eleftherios Venizelos | Ταύρος-Ελευθέριος Βενιζέλος | Metro | M1 | 37.9625102 | 23.7037122 | node/1024902125 |
| Thiseio | Θησείο | Metro | M1 | 37.9766852 | 23.7205356 | node/1005304990 |
| Trocadero | Τροκαντερό | Tram | T7 | 37.9313941 | 23.6872264 | node/7498860478 |
| Tzitzifies | Τζιτζιφιές | Tram | T7 | 37.9407062 | 23.6880185 | node/7498860481 |
| Victoria | Βικτώρια | Metro | M1 | 37.9934877 | 23.7303950 | node/5396738082 |
| Vouliagmenis | Βουλιαγμένης | Tram | T6 | 37.9667197 | 23.7317325 | node/6021744958 |
| Zappeion | Ζάππειο | Tram | T6 | 37.9693069 | 23.7364183 | node/364338802 |
| Zefyros | Ζέφυρος | Tram | T7 | 37.9063769 | 23.7170206 | node/7498860470 |
| Άγιοι Ανάργυροι |  | Suburban railway | A1, A3, A4 | 38.0224583 | 23.7186452 | node/2354629543 |
| Άγιοι Θεοδώροι |  | Suburban railway | A4 | 37.9332405 | 23.1369832 | node/835280795 |
| Άγιος Γεώργιος |  | Suburban railway | A3 | 38.3548928 | 23.6074074 | node/2291066231 |
| Άγιος Θωμάς |  | Suburban railway | A3 | 38.2816770 | 23.6672270 | node/2291068164 |
| Άγιος Στέφανος |  | Suburban railway | A3 | 38.1403696 | 23.8591873 | node/364329621 |
| Άνω Λιόσια |  | Suburban railway | A2, A4 | 38.0707953 | 23.7100051 | node/5777457451 |
| Αγίας Φωτεινής-Πλατεία |  | Tram | T6 | 37.9467388 | 23.7150445 | node/939869148 |
| Αεροδρόμιο |  | Metro | M3 | 37.9368813 | 23.9447336 | node/7799596873 |
| Αθήνα |  | Suburban railway | A1, A3, A4 | 37.9931275 | 23.7202839 | node/2872124552 |
| Ασπρόπυργος |  | Suburban railway | A4 | 38.0810388 | 23.6042595 | node/5777574610 |
| Αυλίδα |  | Suburban railway | A3 | 38.4044464 | 23.6033835 | node/155708452 |
| Αυλώνας |  | Suburban railway | A3 | 38.2504724 | 23.6955986 | node/368164861 |
| Αφίδνες |  | Suburban railway | A3 | 38.1883264 | 23.8444658 | node/4683332020 |
| Αχαρνές |  | Suburban railway | A3 | 38.0802534 | 23.7440766 | node/2798912795 |
| Δήλεσι |  | Suburban railway | A3 | 38.3376364 | 23.6094499 | node/2291065001 |
| Δεκέλεια |  | Suburban railway | A3 | 38.0997540 | 23.7801125 | node/966702938 |
| Δημοτικό Θέατρο |  | Metro | M3 | 37.9429173 | 23.6475926 | node/7485440486 |
| Δουκίσσης Πλακεντίας |  | Metro | M3 | 38.0239773 | 23.8325569 | node/7484233286 |
| Δουκίσσης Πλακεντίας |  | Suburban railway | A1, A2 | 38.0247026 | 23.8338693 | node/2142437054 |
| Ζευγολατιό |  | Suburban railway | A4 | 37.9263503 | 22.8046326 | node/3155138146 |
| Ζεφύρι |  | Suburban railway | A4 | 38.0699579 | 23.7163427 | node/3979025748 |
| Ηράκλειο |  | Suburban railway | A1, A2 | 38.0567845 | 23.7720928 | node/936804085 |
| Κάτω Αχαρναί |  | Suburban railway | A1, A4 | 38.0547251 | 23.7328066 | node/2343670500 |
| Καλοχώρι-Παντείχι |  | Suburban railway | A3 | 38.3893073 | 23.5931559 | node/155706004 |
| Κηφισίας |  | Suburban railway | A1, A2 | 38.0419210 | 23.8040729 | node/936804010 |
| Κιάτο |  | Suburban railway | A4 | 38.0139838 | 22.7348102 | node/309957342 |
| Κινέτα |  | Suburban railway | A4 | 37.9654426 | 23.2010371 | node/5777574608 |
| Κορωπί |  | Metro | M3 | 37.9125370 | 23.8960768 | node/10700888565 |
| Λεύκα |  | Suburban railway | A1, A4 | 37.9555849 | 23.6541235 | node/9654883729 |
| Μέγαρα |  | Suburban railway | A4 | 37.9910006 | 23.3610190 | node/836837794 |
| Μαγούλα |  | Suburban railway | A4 | 38.0730827 | 23.5291665 | node/832802993 |
| Μανιάτικα |  | Metro | M3 | 37.9600579 | 23.6397874 | node/7485440516 |
| Μεταμόρφωση |  | Suburban railway | A1, A2 | 38.0600674 | 23.7562064 | node/360017697 |
| Νέα Πέραμος |  | Suburban railway | A4 | 38.0127986 | 23.4132616 | node/4919885663 |
| Νέος Κόσμος |  | Tram | T6 | 37.9569955 | 23.7270853 | node/821499092 |
| Νίκαια |  | Metro | M3 | 37.9660813 | 23.6478426 | node/7485440531 |
| Νεραντζιώτισσα |  | Metro | M1 | 38.0455338 | 23.7934250 | node/939521334 |
| Νερατζιώτισσα |  | Suburban railway | A1, A2 | 38.0447682 | 23.7941064 | node/936804443 |
| Οινόη |  | Suburban railway | A3 | 38.3230172 | 23.6090770 | node/155927792 |
| Οινόφυτα |  | Suburban railway | A3 | 38.3069654 | 23.6338955 | node/155927732 |
| Παλλήνη |  | Metro | M3 | 38.0045807 | 23.8699500 | node/10700888533 |
| Παλλήνη |  | Suburban railway | A1, A2 | 38.0054925 | 23.8696405 | node/937378118 |
| Πεντέλης |  | Suburban railway | A1, A2 | 38.0328796 | 23.8225838 | node/3781391905 |
| Πύργος Βασιλίσσης |  | Suburban railway | A1, A4 | 38.0400365 | 23.7276759 | node/2343647510 |
| Ρέντης |  | Suburban railway | A1, A4 | 37.9622619 | 23.6683076 | node/2148259068 |
| Ρουφ |  | Suburban railway | A1, A4 | 37.9736019 | 23.7040087 | node/2148529035 |
| Σταθμός Λαρίσης |  | Metro | M2 | 37.9922861 | 23.7207006 | node/7480417778 |
| Σφενδάλη |  | Suburban railway | A3 | 38.2354158 | 23.7844141 | node/155891790 |
| Ταύρος |  | Suburban railway | A1, A4 | 37.9689397 | 23.6942248 | node/2148380495 |
