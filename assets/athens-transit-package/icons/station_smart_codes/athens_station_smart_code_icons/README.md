# Athens Transit Station Icons with Smart Codes

This fixes the `AG` problem.

Instead of blindly using the first two letters, the visible station code skips generic prefixes like:
- Aghia / Agia / Aghios / Agios / Ag.
- Nea / Neo / Neos
- Ano / Kato
- Platia / Paleo / Leof.

Examples:
- Aghia Varvara -> `M3 AV`
- Aghia Marina -> `M3 AM`
- Aghia Paraskevi -> `M3 AP`
- Aghios Nikolaos -> `M1 AN`
- Neos Kosmos -> `M2 NK`

Full station names remain in SVG `<title>`, `<desc>`, filenames, and `manifest.json`.


## 2026 T7 additions
Added T7 station smart-code icons for: Gipedo Karaiskaki, Mikras Asias, Grigoriou Lambraki, Evangelistria, Plateia Deligianni, and Dimarhio / Dimotiko Theatro.

## One icon per station
For interchange stations, use `station_connection_icons/` and `station_connection_icons` in `manifest.json`. This keeps stations like Monastiraki, Syntagma, and Dimotiko Theatro as one visual station icon even when they have multiple metro/tram lines.
