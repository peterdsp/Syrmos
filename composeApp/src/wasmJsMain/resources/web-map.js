(async function () {
    const ATHENS_CENTER = [37.98, 23.73];
    const INITIAL_ZOOM = 12;
    const DIRECTION_OUTBOUND = "outbound";
    const DIRECTION_INBOUND = "inbound";

    // Shared map design tokens. Verbatim mirror of
    // core/common/src/commonMain/kotlin/com/syrmos/core/common/map/MapDesignTokens.kt
    // (and iosApp/.../DesignSystem/MapDesignTokens.swift). Change the Kotlin
    // source of truth, then update these so the three maps never drift.
    const MAP_TOKENS = {
        dotCountry: 10,
        dotCity: 13,
        dotSelected: 18,
        glyphMinZoom: 14,
        minorStopMinZoom: 10,
        majorHubMinZoom: 8,
        linesOnlyMaxZoom: 7,
        greyedColor: "#94a3b8",
        busDash: "2 7",
        greyedDash: "6 8",
    };

    // --- i18n -----------------------------------------------------------
    // Three-language UI for the web build. Mirrors the iOS / KMP enum so
    // a Greek or Albanian user sees the same vocabulary across every
    // platform. Strings are organised by key; t(key) looks up the active
    // language with English as the fallback.
    const I18N = {
        en: {
            brand_subtitle: "Athens rail map",
            search_placeholder: "Search station, Syntagma, Piraeus, Airport",
            search_aria: "Search station",
            locate_me: "Locate me",
            location_unavailable: "Location unavailable",
            show_vehicles: "Show vehicles",
            hide_vehicles: "Hide vehicles",
            live_trains: "Live trains",
            trains_active: "{n} trains active",
            nearby_stations: "Nearby stations",
            popular_stations: "Popular stations",
            oasa_tickets: "OASA tickets",
            view_on_oasa: "View on OASA ↗",
            useful_information: "Useful information",
            tickets_unavailable: "Tickets unavailable",
            no_fare_data: "No fare data",
            select_a_station: "Select a station",
            done: "Done",
            lines_at_this_station: "Lines at this station",
            next_departures: "Next departures",
            no_departures: "No departures available for this station right now.",
            get_directions: "Get directions",
            now: "Now",
            hero_next: "Next departure",
            then: "then",
            scheduled: "Scheduled",
            interchange: "Interchange",
            accessible: "Accessible",
            airport: "Airport",
            train: "Train",
            live: "Live",
            estimated: "Estimated",
            offline_snapshot: "Offline snapshot",
            unknown: "unknown",
            next: "next",
            reduced: "Reduced",
            verify_on: "Verify on {op} ↗",
            ask_ariadne: "Ask Ariadne",
            ariadne_title: "Ariadne",
            ariadne_placeholder: "Ask Ariadne...",
            send: "Send",
            ariadne_greeting: "Hi, I'm Ariadne. Ask me about Athens transit — next departures, last train tonight, a trip A to B, service alerts, or ticket prices.",
            ariadne_looking_up: "Looking up {station}...",
            ariadne_next_from: "Next from {station}:",
            ariadne_none_now: "No more trains from {station} right now.",
            ariadne_no_station: "I couldn't match that to an Athens station. Try Syntagma, Piraeus, Airport.",
            ariadne_did_you_mean: "I didn't quite catch that — did you mean {station}? Try \"next trains from {station}\".",
            ariadne_try_asking: "I didn't catch that. Ask me about departures, a route between two stations, or the last train home.",
            search_ask_ariadne: "Ask Ariadne",
            ariadne_open_map: "Opening {station} on the map.",
            ariadne_open_alerts: "Showing service alerts.",
            ariadne_open_route: "Opening directions from {from} to {to}.",
            ariadne_eta_locating: "Getting your location to estimate the trip to {station}…",
            ariadne_eta_ask_origin: "I couldn't get your location. Which station are you starting from?",
            ariadne_line: "Line {id} runs between {a} and {b}. Tap the line on the map to see all stations.",
            ariadne_fare: "Standard OASA single is €0.90 (Metro/Tram). Airport metro single is €9. See OASA tickets in the side panel for the full list.",
            whatsnew_title: "What's new in Syrmos",
            whatsnew_i1: "Ask Ariadne — the offline assistant for departures, trips and last trains",
            whatsnew_i2: "Smarter search that understands typos (nikea → Nikaia)",
            whatsnew_i3: "\"How long to…\" travel-time answers from your location",
            whatsnew_i4: "Track any train, and get live times on your Home Screen",
            whatsnew_get_app: "Get the app",
            whatsnew_stay: "Continue on web",
        },
        el: {
            brand_subtitle: "Χάρτης σιδηροδρόμων Αθήνας",
            search_placeholder: "Αναζήτηση σταθμού (Σύνταγμα, Πειραιάς, Αεροδρόμιο)",
            search_aria: "Αναζήτηση σταθμού",
            locate_me: "Η τοποθεσία μου",
            location_unavailable: "Η τοποθεσία δεν είναι διαθέσιμη",
            show_vehicles: "Εμφάνιση οχημάτων",
            hide_vehicles: "Απόκρυψη οχημάτων",
            live_trains: "Ζωντανά τρένα",
            trains_active: "{n} ενεργά τρένα",
            nearby_stations: "Κοντινοί σταθμοί",
            popular_stations: "Δημοφιλείς σταθμοί",
            oasa_tickets: "Εισιτήρια OASA",
            view_on_oasa: "Άνοιγμα στην OASA ↗",
            useful_information: "Χρήσιμες πληροφορίες",
            tickets_unavailable: "Τα εισιτήρια δεν είναι διαθέσιμα",
            no_fare_data: "Δεν υπάρχουν τιμές",
            select_a_station: "Επιλέξτε σταθμό",
            done: "Τέλος",
            lines_at_this_station: "Γραμμές αυτού του σταθμού",
            next_departures: "Επόμενα δρομολόγια",
            no_departures: "Δεν υπάρχουν διαθέσιμα δρομολόγια για αυτόν τον σταθμό αυτή τη στιγμή.",
            get_directions: "Οδηγίες",
            now: "Τώρα",
            hero_next: "Επόμενη αναχώρηση",
            then: "μετά",
            scheduled: "Προγραμματισμένο",
            interchange: "Ανταπόκριση",
            accessible: "Προσβάσιμος",
            airport: "Αεροδρόμιο",
            train: "Συρμός",
            live: "Ζωντανά",
            estimated: "Εκτίμηση",
            offline_snapshot: "Εκτός σύνδεσης",
            unknown: "άγνωστο",
            next: "επόμενος",
            reduced: "Μειωμένο",
            verify_on: "Επιβεβαίωση στο {op} ↗",
            ask_ariadne: "Ρώτα την Αριάδνη",
            ariadne_title: "Αριάδνη",
            ariadne_placeholder: "Ρώτα την Αριάδνη...",
            send: "Αποστολή",
            ariadne_greeting: "Γεια, είμαι η Αριάδνη. Ρώτα με για αναχωρήσεις, καιρό σε σταθμό ή διαδρομές όπως «αεροδρόμιο στις 21:30».",
            ariadne_looking_up: "Αναζήτηση για {station}...",
            ariadne_next_from: "Επόμενα από {station}:",
            ariadne_none_now: "Δεν υπάρχουν άλλα δρομολόγια από {station} τώρα.",
            ariadne_no_station: "Δεν αναγνώρισα σταθμό. Δοκίμασε Σύνταγμα, Πειραιά ή Αεροδρόμιο.",
            ariadne_did_you_mean: "Δεν το κατάλαβα ακριβώς — μήπως εννοείς {station}; Δοκίμασε «επόμενα τρένα από {station}».",
            ariadne_try_asking: "Δεν το κατάλαβα. Ρώτησέ με για αναχωρήσεις, διαδρομή μεταξύ δύο σταθμών ή το τελευταίο τρένο.",
            search_ask_ariadne: "Ρώτησε την Αριάδνη",
            ariadne_open_map: "Άνοιγμα του {station} στον χάρτη.",
            ariadne_open_alerts: "Εμφάνιση ειδοποιήσεων.",
            ariadne_open_route: "Άνοιγμα διαδρομής από {from} προς {to}.",
            ariadne_eta_locating: "Εντοπίζω την τοποθεσία σου για τον χρόνο προς {station}…",
            ariadne_eta_ask_origin: "Δεν βρήκα την τοποθεσία σου. Από ποιον σταθμό ξεκινάς;",
            ariadne_line: "Η Γραμμή {id} συνδέει {a} και {b}. Πάτα τη γραμμή στον χάρτη για όλους τους σταθμούς.",
            ariadne_fare: "Το βασικό εισιτήριο OASA είναι €0,90 (Μετρό/Τραμ). Το εισιτήριο μετρό για το αεροδρόμιο είναι €9. Δες τα εισιτήρια OASA στο πλαϊνό πάνελ.",
            whatsnew_title: "Τι νέο υπάρχει στο Syrmos",
            whatsnew_i1: "Ρώτα την Αριάδνη — τον offline βοηθό για αναχωρήσεις, διαδρομές και τελευταία τρένα",
            whatsnew_i2: "Πιο έξυπνη αναζήτηση που καταλαβαίνει τα λάθη (nikea → Νίκαια)",
            whatsnew_i3: "Απαντήσεις χρόνου \"Πόση ώρα για…\" από την τοποθεσία σου",
            whatsnew_i4: "Παρακολούθησε κάθε τρένο και δες ζωντανούς χρόνους στην Αρχική οθόνη",
            whatsnew_get_app: "Κατέβασε την εφαρμογή",
            whatsnew_stay: "Συνέχεια στο web",
        },
        sq: {
            brand_subtitle: "Harta e hekurudhave të Athinës",
            search_placeholder: "Kërko stacion (Syntagma, Piraeus, Aeroporti)",
            search_aria: "Kërko stacion",
            locate_me: "Vendndodhja ime",
            location_unavailable: "Vendndodhja s'është e disponueshme",
            show_vehicles: "Shfaq mjetet",
            hide_vehicles: "Fshih mjetet",
            live_trains: "Trenat aktiv",
            trains_active: "{n} trena aktiv",
            nearby_stations: "Stacionet pranë",
            popular_stations: "Stacionet kryesore",
            oasa_tickets: "Biletat OASA",
            view_on_oasa: "Hap në OASA ↗",
            useful_information: "Informacione të dobishme",
            tickets_unavailable: "Biletat s'janë të disponueshme",
            no_fare_data: "Pa të dhëna çmimesh",
            select_a_station: "Zgjidh një stacion",
            done: "U krye",
            lines_at_this_station: "Linjat në këtë stacion",
            next_departures: "Nisjet e ardhshme",
            no_departures: "Nuk ka nisje në dispozicion për këtë stacion në këtë moment.",
            get_directions: "Udhëzime",
            now: "Tani",
            hero_next: "Nisja e radhës",
            then: "pastaj",
            scheduled: "Sipas orarit",
            interchange: "Korrespondencë",
            accessible: "I aksesueshëm",
            airport: "Aeroporti",
            train: "Tren",
            live: "Drejtpërdrejt",
            estimated: "Vlerësim",
            offline_snapshot: "Pa internet",
            unknown: "i panjohur",
            next: "tjetër",
            reduced: "Me zbritje",
            verify_on: "Verifiko në {op} ↗",
            ask_ariadne: "Pyet Ariadnen",
            ariadne_title: "Ariadne",
            ariadne_placeholder: "Pyet Ariadnen...",
            send: "Dërgo",
            ariadne_greeting: "Përshëndetje, jam Ariadne. Më pyet për nisje, motin te një stacion ose udhëtime si «aeroporti në 21:30».",
            ariadne_looking_up: "Po kërkoj për {station}...",
            ariadne_next_from: "Të ardhshmet nga {station}:",
            ariadne_none_now: "Nuk ka më trena nga {station} tani.",
            ariadne_no_station: "S'e njoha stacionin. Provo Syntagma, Piraeus ose Aeroporti.",
            ariadne_did_you_mean: "Nuk e kuptova mirë — mos ke parasysh {station}? Provo «trenat e ardhshëm nga {station}».",
            ariadne_try_asking: "Nuk e kuptova. Më pyet për nisje, një udhëtim mes dy stacioneve ose trenin e fundit.",
            search_ask_ariadne: "Pyet Ariadnen",
            ariadne_open_map: "Po hap {station} në hartë.",
            ariadne_open_alerts: "Po tregoj njoftimet.",
            ariadne_open_route: "Po hap udhëzimet nga {from} te {to}.",
            ariadne_eta_locating: "Po marr vendndodhjen tënde për kohën te {station}…",
            ariadne_eta_ask_origin: "S'e mora dot vendndodhjen. Nga cili stacion po nisesh?",
            ariadne_line: "Linja {id} lidh {a} me {b}. Prek linjën në hartë për të gjitha stacionet.",
            ariadne_fare: "Bileta standarde OASA është €0,90 (Metro/Tram). Bileta metro për aeroport është €9. Shiko biletat OASA në panelin anësor.",
            whatsnew_title: "Çfarë ka të re në Syrmos",
            whatsnew_i1: "Pyet Ariadnen — asistenti offline për nisjet, udhëtimet dhe trenat e fundit",
            whatsnew_i2: "Kërkim më i zgjuar që kupton gabimet (nikea → Nikaia)",
            whatsnew_i3: "Përgjigje kohe \"Sa gjatë te…\" nga vendndodhja jote",
            whatsnew_i4: "Ndiq çdo tren dhe merr kohët live në Ekranin Kryesor",
            whatsnew_get_app: "Merr aplikacionin",
            whatsnew_stay: "Vazhdo në web",
        },
    };

    const LANG_STORAGE_KEY = "syrmos_lang";

    function detectInitialLanguage() {
        const saved = (() => {
            try { return localStorage.getItem(LANG_STORAGE_KEY); } catch (_) { return null; }
        })();
        if (saved && I18N[saved]) return saved;
        // Use only the browser's PRIMARY language. The previous `.some(...)`
        // pass matched if the language appeared anywhere in the navigator
        // list, which on Athens-resident laptops with Shqip added for
        // translation testing landed on Albanian (or vice versa). The
        // product wants device-primary-or-English, full stop. Mirrors the
        // iOS native + Android Kotlin actuals.
        const primary = (
            (navigator.languages && navigator.languages[0]) ||
            navigator.language ||
            "en"
        ).toLowerCase();
        if (primary.startsWith("el")) return "el";
        if (primary.startsWith("sq")) return "sq";
        return "en";
    }

    let currentLang = detectInitialLanguage();

    function t(key, vars) {
        const table = I18N[currentLang] || I18N.en;
        let s = table[key] != null ? table[key] : (I18N.en[key] != null ? I18N.en[key] : key);
        if (vars) {
            for (const k of Object.keys(vars)) {
                s = s.split("{" + k + "}").join(String(vars[k]));
            }
        }
        return s;
    }

    /// Re-applies translations to every [data-i18n*] element in the DOM.
    /// Called once on init and again on every language change.
    function applyTranslationsToDom() {
        document.querySelectorAll("[data-i18n]").forEach((el) => {
            el.textContent = t(el.getAttribute("data-i18n"));
        });
        document.querySelectorAll("[data-i18n-placeholder]").forEach((el) => {
            el.placeholder = t(el.getAttribute("data-i18n-placeholder"));
        });
        document.querySelectorAll("[data-i18n-aria]").forEach((el) => {
            el.setAttribute("aria-label", t(el.getAttribute("data-i18n-aria")));
        });
        document.querySelectorAll("[data-i18n-title]").forEach((el) => {
            el.title = t(el.getAttribute("data-i18n-title"));
        });
        document.documentElement.lang = currentLang;
        const picker = document.getElementById("languagePicker");
        if (picker) {
            picker.querySelectorAll(".language-pill").forEach((btn) => {
                const pressed = btn.getAttribute("data-lang") === currentLang;
                btn.setAttribute("aria-pressed", String(pressed));
            });
        }
    }

    // Listener list for code paths that hand-render dynamic strings
    // (live trains panel, station sheet, departures list). Each listener
    // is called after the language flips so they re-render with the new
    // strings instead of waiting for a natural refresh tick.
    const langChangeListeners = [];
    function onLanguageChange(fn) { langChangeListeners.push(fn); }
    function setLanguage(next) {
        if (!I18N[next] || next === currentLang) return;
        currentLang = next;
        try { localStorage.setItem(LANG_STORAGE_KEY, next); } catch (_) {}
        applyTranslationsToDom();
        langChangeListeners.forEach((fn) => { try { fn(next); } catch (_) {} });
    }

    // Wire the picker as soon as the DOM is ready (this script runs at the
    // end of <body> so the elements already exist).
    (function wireLanguagePicker() {
        const picker = document.getElementById("languagePicker");
        if (!picker) return;
        picker.querySelectorAll(".language-pill").forEach((btn) => {
            btn.addEventListener("click", () => setLanguage(btn.getAttribute("data-lang")));
        });
        applyTranslationsToDom();
    })();

    const stationSheet = document.getElementById("stationSheet");
    const stationName = document.getElementById("stationName");
    const stationNameEl = document.getElementById("stationNameEl");
    const lineBadges = document.getElementById("lineBadges");
    const stationMeta = document.getElementById("stationMeta");
    const stationDepartures = document.getElementById("stationDepartures");
    const directionsLink = document.getElementById("directionsLink");
    const sheetClose = document.getElementById("sheetClose");
    const stationSearch = document.getElementById("stationSearch");
    const searchResults = document.getElementById("searchResults");
    const locateButton = document.getElementById("locateButton");
    const zoomInButton = document.getElementById("zoomInButton");
    const zoomOutButton = document.getElementById("zoomOutButton");

    const [stations, lines, routes, servicePatterns, vehicleManifest] = await Promise.all([
        fetch("files/seed/stations.json").then((r) => r.json()),
        // schedules-v2 is the generator's payload and the single source of truth
        // for lines. The legacy flat seed/lines.json was transcribed from
        // hardcoded Swift by a script broken since June 2026, so it carries
        // neither region nor status. Falls back to it only if the payload cannot
        // be read, so a bad deploy degrades rather than renders an empty map. See
        // docs/plans/2026-07-17-server-as-single-source-for-lines.md.
        fetch("files/seed/schedules-v2/lines.json")
            .then((r) => r.json())
            .then((d) => (Array.isArray(d?.lines) && d.lines.length ? d.lines : Promise.reject()))
            .catch(() => fetch("files/seed/lines.json").then((r) => r.json())),
        fetch("files/seed/routes.json").then((r) => r.json()),
        fetch("files/seed/service_patterns.json").then((r) => r.json()),
        fetch("icons/vehicles/manifest.json").then((r) => r.json()).catch(() => ({ directional_icons: [] })),
    ]);

    const lineMap = new Map(lines.map((line) => [line.id, line]));

    // A line that is built but not open still renders, greyed, because the track
    // is real and hiding it would be its own kind of lie. What must never exist is
    // a train on it, or a departure from it. Default to operational so an older
    // payload without the field behaves exactly as today.
    const isOperational = (line) => (line?.status ?? "operational") !== "under_construction";
    const operationalLines = lines.filter(isOperational);

    const vehicleIconMap = new Map();
    const lineIdToManifestLine = { M1: "M1", M2: "M2", M3: "M3", T6: "T6", T7: "T7", T6T7: "T6T7", A1: "P1", A2: "P1A", A3: "P3", A4: "P2" };
    for (const icon of vehicleManifest.directional_icons) {
        const dir = icon.arrow === "←" ? "inbound" : "outbound";
        // icon.file already starts with "icons/vehicles/..." in the bundled
        // manifest. Prepending another "icons/vehicles/" produced a double
        // prefix path that 404'd, which is why live + simulated trains
        // were falling back to colored Leaflet pins instead of vehicle SVGs.
        vehicleIconMap.set(`${icon.line}_${dir}`, icon.file);
        if (icon.destination === "Airport") {
            vehicleIconMap.set(`${icon.line}_airport`, icon.file);
        }
    }
    const stationMap = new Map(stations.map((station) => [station.id, station]));
    const stationNodes = buildStationNodes(stations);
    const stationNodeMap = new Map(stationNodes.map((station) => [station.id, station]));
    const markers = new Map();
    const liveTrainMarkers = new Map();
    let departureRefreshTimer = null;
    const lineStations = new Map(
        routes.map((route) => [
            route.line_id,
            route.station_ids.map((stationId) => stationMap.get(stationId)).filter(Boolean),
        ])
    );

    // Source of truth: api-syrmos.peterdsp.dev/api/icons. Cached locally; if the
    // network is down at cold start we fall back to the bundled manifest which
    // shipped with the build.
    const stationIconBySid = new Map();
    const cachedIconsKey = "syrmos.icons.v1";
    let apiIcons = null;
    try {
        const cached = localStorage.getItem(cachedIconsKey);
        if (cached) apiIcons = JSON.parse(cached);
    } catch (_) {}
    try {
        const fresh = await fetch("https://api-syrmos.peterdsp.dev/api/icons").then((r) => r.json());
        if (fresh && (fresh.stations || fresh.interchanges)) {
            apiIcons = fresh;
            try { localStorage.setItem(cachedIconsKey, JSON.stringify(fresh)); } catch (_) {}
        }
    } catch (_) {}
    // PDF-grounded per-train timestamps for suburban A1-A4. When this is
    // populated, buildStationDepartures uses it for suburban stations and
    // falls back to band projection only when the operator hasn't published
    // a real timetable. Cached for offline cold start.
    let apiTrainTimestamps = { trains: [] };
    try {
        const cachedTT = localStorage.getItem("syrmos.train-timestamps.v1");
        if (cachedTT) apiTrainTimestamps = JSON.parse(cachedTT);
    } catch (_) {}
    try {
        const freshTT = await fetch("https://api-syrmos.peterdsp.dev/api/train-timestamps")
            .then((r) => (r.ok ? r.json() : null))
            .catch(() => null);
        if (freshTT && Array.isArray(freshTT.trains)) {
            apiTrainTimestamps = freshTT;
            try { localStorage.setItem("syrmos.train-timestamps.v1", JSON.stringify(freshTT)); } catch (_) {}
        }
    } catch (_) {}

    // Source of truth for schedules: /api/schedules/{lineId}. Cached in
    // localStorage so an offline cold start still has correct data.
    const apiSchedules = new Map();
    // Derived, not hardcoded: a new city should be a data change, not an edit to a
    // list of ids. Operational lines only, so track that is built but not open is
    // never fetched or projected. M3_AIR is appended explicitly because it is a
    // synthetic line (the airport branch of M3) that the generator excludes from
    // lines.json but the projector still needs. Today this yields exactly the ten
    // ids it replaced.
    const lineIdsToFetch = [...operationalLines.map((l) => l.id), "M3_AIR"];
    try {
        const cached = localStorage.getItem("syrmos.schedules.v1");
        if (cached) {
            const obj = JSON.parse(cached);
            for (const [lid, bundle] of Object.entries(obj)) apiSchedules.set(lid, bundle);
        }
        const bundles = await Promise.all(
            lineIdsToFetch.map((lid) =>
                fetch(`https://api-syrmos.peterdsp.dev/api/schedules/${lid}`)
                    .then((r) => (r.ok ? r.json() : null))
                    .catch(() => null)
            )
        );
        const persist = {};
        bundles.forEach((b, idx) => {
            if (b && b.bands && b.rules) {
                apiSchedules.set(lineIdsToFetch[idx], b);
                persist[lineIdsToFetch[idx]] = b;
            }
        });
        if (Object.keys(persist).length) {
            try { localStorage.setItem("syrmos.schedules.v1", JSON.stringify(persist)); } catch (_) {}
        }
    } catch (_) {}

    // Hydrate from API first (preferred source of truth), then plug any
    // gaps from the bundled manifest. The API currently returns empty
    // {stations:{},interchanges:{}} maps, so the bundle is what actually
    // populates the per-station SVGs in production.
    if (apiIcons && apiIcons.stations) {
        for (const [sid, url] of Object.entries(apiIcons.stations)) stationIconBySid.set(sid, url);
        for (const [sid, url] of Object.entries(apiIcons.interchanges || {})) stationIconBySid.set(sid, url);
    }
    const stationIconManifest = await fetch("icons/stations/manifest.json").then((r) => r.json()).catch(() => ({}));
    const lineToManifestDir = { M1: "metro/M1", M2: "metro/M2", M3: "metro/M3", T6: "tram/T6", T7: "tram/T7", A1: "train/P1", A2: "train/P1", A3: "train/P3", A4: "train/P2" };
    for (const route of routes) {
        const mDir = lineToManifestDir[route.line_id];
        if (!mDir) continue;
        route.station_ids.forEach((stationId, index) => {
            if (stationIconBySid.has(stationId)) return;
            const key = `${mDir}/${String(index + 1).padStart(2, "0")}`;
            if (stationIconManifest[key]) stationIconBySid.set(stationId, stationIconManifest[key]);
        });
    }
    for (const [key, url] of Object.entries(stationIconManifest)) {
        if (!key.startsWith("interchange/")) continue;
        const sid = key.substring("interchange/".length);
        if (!stationIconBySid.has(sid)) stationIconBySid.set(sid, url);
    }

    const liveTrainList = document.getElementById("liveTrainList");
    const nearbyStationList = document.getElementById("nearbyStationList");
    const popularStationList = document.getElementById("popularStationList");
    const faresList = document.getElementById("faresList");
    const faresLink = document.getElementById("faresLink");
    const infoLinksList = document.getElementById("infoLinksList");

    // Hydrate OASA tickets + useful info cards from /api/fares. Bundled
    // version is not strictly needed here because the JS map is online-only
    // by design (Leaflet tiles), but we fall back to the structured panel
    // empty state if the API is down rather than rendering nothing.
    (async () => {
        if (!faresList && !infoLinksList) return;
        try {
            const r = await fetch("https://api-syrmos.peterdsp.dev/api/fares");
            if (!r.ok) throw new Error("fares fetch failed");
            const payload = await r.json();
            lastFaresPayload = payload;
            renderFaresPanel(payload);
            renderInfoLinksPanel(payload);
        } catch (_) {
            if (faresList) faresList.innerHTML = `<div class="panel-item__meta">${t("tickets_unavailable")}</div>`;
            if (infoLinksList) infoLinksList.innerHTML = "";
        }
    })();

    /// Pick the localised value of a {baseKey}En / {baseKey}El / {baseKey}Sq
    /// triplet. Returns the active-language field when populated, otherwise
    /// falls back to English, then to whichever variant exists. Lets the
    /// data layer ship partial translations without breaking renders.
    function pickLocalised(obj, baseKey) {
        if (!obj) return "";
        const en = obj[baseKey + "En"];
        const el = obj[baseKey + "El"];
        const sq = obj[baseKey + "Sq"];
        if (currentLang === "el" && el) return el;
        if (currentLang === "sq" && sq) return sq;
        return en || el || sq || "";
    }

    function pickLocalisedBullet(b) {
        if (!b) return "";
        if (currentLang === "el" && b.el) return b.el;
        if (currentLang === "sq" && b.sq) return b.sq;
        return b.en || b.el || b.sq || "";
    }

    // Cache the last /api/fares payload so we can re-render fares and
    // info-links when the user flips the language without re-hitting the
    // network. The fetch fires once on load; the cache holds whatever
    // came back.
    let lastFaresPayload = null;

    function renderFaresPanel(payload) {
        if (!faresList) return;
        const products = (payload && payload.products) || [];
        if (!products.length) {
            faresList.innerHTML = `<div class="panel-item__meta">${t("no_fare_data")}</div>`;
            return;
        }
        // Show top 6 in a compact "title — €price" list so the panel doesn't
        // dominate the sidebar. Verify-on-OASA link below carries the user
        // to the full price list.
        const top = products.slice(0, 6);
        faresList.innerHTML = top.map((p) => {
            const eur = p.fullPriceEur != null ? `€${p.fullPriceEur.toFixed(2)}` : "";
            const validityLocalised = pickLocalised(p, "validity") || p.validity || "";
            const sub = p.discountedPriceEur != null
                ? `${t("reduced")} €${p.discountedPriceEur.toFixed(2)}`
                : validityLocalised;
            return `
                <div class="panel-item">
                    <div class="panel-item__title">${escapeHtml(pickLocalised(p, "title"))}</div>
                    <div class="panel-item__meta">${escapeHtml(sub)}</div>
                    <div class="panel-item__count">${eur}</div>
                </div>
            `;
        }).join("");
    }

    function renderInfoLinksPanel(payload) {
        if (!infoLinksList) return;
        const links = (payload && payload.infoLinks) || [];
        if (!links.length) {
            infoLinksList.innerHTML = "";
            return;
        }
        infoLinksList.innerHTML = links.map((link) => {
            const bullets = (link.bullets || []).map((b) =>
                `<li>${escapeHtml(pickLocalisedBullet(b))}</li>`
            ).join("");
            const localisedSummary = pickLocalised(link, "summary");
            const summary = localisedSummary ? `<p class="info-link__summary">${escapeHtml(localisedSummary)}</p>` : "";
            const localisedUrl = pickLocalised(link, "url") || "#";
            const op = (link.operator || link.operatorId || "").toUpperCase();
            return `
                <article class="info-link">
                    <header class="info-link__head">
                        <h4 class="info-link__title">${escapeHtml(pickLocalised(link, "title"))}</h4>
                        <span class="info-link__op">${escapeHtml(op)}</span>
                    </header>
                    ${summary}
                    <ul class="info-link__bullets">${bullets}</ul>
                    <a class="info-link__verify" href="${escapeAttr(localisedUrl)}" target="_blank" rel="noopener">${escapeHtml(t("verify_on", { op }))}</a>
                </article>
            `;
        }).join("");
    }

    // Re-render fares + info links when the language flips, using the
    // cached payload so Greek bullets snap in instantly without a refetch.
    onLanguageChange(() => {
        if (lastFaresPayload) {
            renderFaresPanel(lastFaresPayload);
            renderInfoLinksPanel(lastFaresPayload);
        }
    });

    function escapeHtml(s) {
        return String(s).replace(/[&<>"']/g, (c) => ({"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;","'":"&#39;"}[c]));
    }
    function escapeAttr(s) { return escapeHtml(s); }

    // preferCanvas renders every station/train dot and line stroke onto ONE
    // shared canvas (like the MapLibre GL railway.gov.gr tracker), instead of a
    // separate DOM <div> per marker. The GPU/canvas redraws all dots each frame
    // at their correct scaled positions, so nothing lags behind the tiles during
    // a zoom ("the data moves") and the DOM stays tiny even with the whole
    // network on screen.
    const DEBUG = new URLSearchParams(location.search).has("debug");
    const map = L.map("map", {
        zoomControl: false,
        attributionControl: true,
        preferCanvas: true,
    }).setView(ATHENS_CENTER, INITIAL_ZOOM);

    // --- Minimal, flat base map (theme-aware) ----------------------------
    // Like the railway.gov.gr live tracker, the base is a calm, near-blank
    // canvas: CARTO's label-free Positron (light) / Dark-Matter (dark). No
    // street clutter, no satellite, no place labels - our own coloured line
    // network + station/train dots carry all the structure, so the map reads as
    // one clean transit diagram instead of a busy road atlas. Tracks the day /
    // night toggle rather than the UI language (a label-free base has no labels
    // to localise; station names live on the dots + panel).
    function isDarkTheme() {
        if (document.body.classList.contains("dark-mode")) return true;
        const saved = localStorage.getItem("syrmos-theme");
        if (saved) return saved === "dark";
        return window.matchMedia("(prefers-color-scheme: dark)").matches;
    }
    function tileSourceFor() {
        const dark = isDarkTheme();
        return {
            url: dark
                ? "https://{s}.basemaps.cartocdn.com/dark_nolabels/{z}/{x}/{y}{r}.png"
                : "https://{s}.basemaps.cartocdn.com/light_nolabels/{z}/{x}/{y}{r}.png",
            subdomains: "abcd",
            attribution: "&copy; OpenStreetMap, &copy; CARTO",
            maxZoom: 20,
        };
    }

    let activeTileLayer = null;
    function applyTileLayer() {
        const cfg = tileSourceFor();
        const next = L.tileLayer(cfg.url, {
            maxZoom: cfg.maxZoom,
            attribution: cfg.attribution,
            subdomains: cfg.subdomains,
        });
        next.addTo(map);
        if (activeTileLayer) {
            // Remove the previous layer only after the new one has had a
            // chance to start fetching, so the user doesn't see a brief
            // blank canvas while tiles swap.
            const prev = activeTileLayer;
            setTimeout(() => map.removeLayer(prev), 250);
        }
        activeTileLayer = next;
    }

    applyTileLayer();
    // The base map follows the day / night toggle, not the language.
    window.__syrmosApplyTileLayer = applyTileLayer;

    // Drop Leaflet's default "Leaflet | " prefix once the map is fully
    // wired up. Guarded because a Leaflet version mismatch could expose
    // a different attribution-control shape, and we'd rather lose the
    // prefix tweak than the whole map.
    try {
        if (map.attributionControl && typeof map.attributionControl.setPrefix === "function") {
            map.attributionControl.setPrefix("");
        }
    } catch (_) {}

    // Pull live line-drawing settings (color, weight, dash) from the API so
    // a maintainer can rebrand a line from the admin without an app release.
    let lineDisplayById = new Map();
    try {
        const cached = localStorage.getItem("syrmos.line_display.v1");
        if (cached) for (const ld of JSON.parse(cached).lines || []) lineDisplayById.set(ld.lineId, ld);
        const fresh = await fetch("https://api-syrmos.peterdsp.dev/api/line-display").then((r) => r.json());
        if (fresh && fresh.lines) {
            lineDisplayById = new Map();
            for (const ld of fresh.lines) lineDisplayById.set(ld.lineId, ld);
            try { localStorage.setItem("syrmos.line_display.v1", JSON.stringify(fresh)); } catch (_) {}
        }
    } catch (_) {}

    // Source of truth order for polyline shape:
    //   1. Bundled `shapes.json` (OSM relation geometry stitched at build time,
    //      ODbL). Always available, fastest, accurate enough for every line.
    //   2. `/line-geometry/{id}.geojson` from the API as remote override —
    //      lets the Pi push corrections without an app release.
    //   3. Catmull-Rom spline through station coords as last-resort fallback.
    const geoCache = new Map();
    try {
        const bundled = await fetch("./shapes.json").then((r) => (r.ok ? r.json() : null)).catch(() => null);
        if (bundled && bundled.shapes) {
            for (const [lid, shape] of Object.entries(bundled.shapes)) {
                if (shape && Array.isArray(shape.coordinates) && shape.coordinates.length > 1) {
                    geoCache.set(lid, {
                        geometry: {
                            type: "LineString",
                            // Convert our [lat,lng] tuples to GeoJSON [lng,lat] so the
                            // downstream renderer treats them uniformly with remote
                            // GeoJSON features.
                            coordinates: shape.coordinates.map(([lat, lng]) => [lng, lat]),
                        },
                    });
                }
            }
        }
    } catch (_) {}
    try {
        const cached = localStorage.getItem("syrmos.line_geometry.v1");
        if (cached) for (const [lid, feat] of Object.entries(JSON.parse(cached))) geoCache.set(lid, feat);
    } catch (_) {}
    const geoFetches = await Promise.all(
        lines.map((line) =>
            fetch(`https://api-syrmos.peterdsp.dev/line-geometry/${line.id}.geojson`)
                .then((r) => (r.ok ? r.json() : null))
                .catch(() => null)
                .then((feat) => ({ id: line.id, feat }))
        )
    );
    const persist = {};
    for (const { id, feat } of geoFetches) {
        if (feat && feat.geometry) {
            geoCache.set(id, feat);
            persist[id] = feat;
        }
    }
    if (Object.keys(persist).length) {
        try { localStorage.setItem("syrmos.line_geometry.v1", JSON.stringify(persist)); } catch (_) {}
    }

    for (const line of lines) {
        const ld = lineDisplayById.get(line.id);
        const feat = geoCache.get(line.id);
        // A line that is built but not open still draws, because the track is
        // real, but it reads as inert: muted grey, thinner, dashed and semi
        // transparent, so it can never be mistaken for a line in service. It
        // carries no trains and no departures either (handled above).
        const underConstruction = !isOperational(line);
        // A rail-replacement bus draws dashed in its own colour, so it reads as
        // "a bus stands in here" without ever looking like a rail line.
        const isBus = line.type === "bus";
        const strokeColor = underConstruction ? MAP_TOKENS.greyedColor : (ld?.strokeColor || line.color);
        const strokeWeight = underConstruction ? 3 : (ld?.strokeWeight ?? (line.type === "suburban" || isBus ? 4 : 5));
        const polylineOpts = {
            color: strokeColor,
            weight: strokeWeight,
            opacity: underConstruction ? 0.55 : 0.9,
            lineCap: "round",
            lineJoin: "round",
            dashArray: underConstruction ? MAP_TOKENS.greyedDash : (isBus ? MAP_TOKENS.busDash : (ld?.strokeDash || null)),
        };
        if (feat && feat.geometry) {
            // GeoJSON is [lng, lat] — Leaflet wants [lat, lng].
            const segments = feat.geometry.type === "MultiLineString"
                ? feat.geometry.coordinates
                : [feat.geometry.coordinates];
            for (const seg of segments) {
                const latLngs = seg.map(([lng, lat]) => [lat, lng]);
                if (latLngs.length > 1) L.polyline(latLngs, polylineOpts).addTo(map);
            }
            continue;
        }
        const orderedStations = lineStations.get(line.id) || [];
        const latLngs = orderedStations.map((station) => [station.latitude, station.longitude]);
        if (latLngs.length > 1) {
            const smoothed = catmullRomSpline(latLngs, 5);
            L.polyline(smoothed, polylineOpts).addTo(map);
        }
    }

    function catmullRomSpline(points, numInterpolated) {
        if (points.length < 3) return points;
        const result = [points[0]];
        for (let i = 0; i < points.length - 1; i++) {
            const p0 = points[Math.max(i - 1, 0)];
            const p1 = points[i];
            const p2 = points[i + 1];
            const p3 = points[Math.min(i + 2, points.length - 1)];
            for (let t = 1; t <= numInterpolated; t++) {
                const f = t / (numInterpolated + 1);
                const lat = cr(p0[0], p1[0], p2[0], p3[0], f);
                const lng = cr(p0[1], p1[1], p2[1], p3[1], f);
                result.push([lat, lng]);
            }
            result.push(p2);
        }
        return result;
    }

    function cr(a, b, c, d, t) {
        return 0.5 * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t * t + (-a + 3 * b - 3 * c + d) * t * t * t);
    }

    let selectedStationId = null;
    let userLocation = null;
    // Declared early (not next to setupHero) so it is initialised before both
    // setupPanelBehavior and the hero run — a `let` next to the function would be
    // in its temporal dead zone when setupHero is invoked earlier in init.
    let heroActive = false;

    // Lightweight canvas dots instead of DOM divIcons. Colour = primary line;
    // interchanges get a slightly larger, heavier white ring; the selected stop
    // is larger with a thicker ring. One size across zoom (no per-zoom restyle),
    // which is what keeps the map calm and cheap.
    function stationRadius(station, selected) {
        if (selected) return 8;
        return station.isInterchange ? 5.5 : 4.5;
    }
    function stationStyle(station, selected) {
        const first = station.lineIds.map((id) => lineMap.get(id)).find(Boolean);
        const color = first ? first.color : "#64748b";
        return {
            radius: stationRadius(station, selected),
            color: "#ffffff",
            weight: selected ? 3 : (station.isInterchange ? 2 : 1.5),
            fillColor: color,
            fillOpacity: 1,
            opacity: 1,
        };
    }
    function restyleStation(id, station, selected) {
        const m = markers.get(id);
        if (!m) return;
        m.setStyle(stationStyle(station, selected));
        m.setRadius(stationRadius(station, selected));
        if (selected) m.bringToFront();
    }

    for (const station of stationNodes) {
        const marker = L.circleMarker(
            [station.latitude, station.longitude],
            stationStyle(station, false)
        );
        marker.on("click", () => selectStation(station.id, true));
        markers.set(station.id, marker);
    }

    // Zoom-tiered decluttering. Drawing all ~390 stops at country zoom turns the
    // map into confetti with no legible network, so below MINOR_STOP_MIN_ZOOM
    // only the skeleton shows: coloured line strokes (always on) + interchange
    // hubs + whatever stop is selected. Zoom into a city and every stop resolves.
    // A major hub is a genuine cross-modal transfer: its lines span 2+ distinct
    // types (metro + suburban, metro + tram, bus + suburban). The is_interchange
    // flag is over-applied in the data, so this tighter rule is what a country
    // view shows. Same definition on iOS + Android.
    function isMajorHub(station) {
        const types = new Set(
            station.lineIds.map((lineId) => lineMap.get(lineId)?.type).filter(Boolean)
        );
        return types.size >= 2;
    }

    function stationVisibleAt(station, z) {
        if (station.id === selectedStationId) return true;
        if (z >= MAP_TOKENS.minorStopMinZoom) return true;        // city: every stop
        if (z >= MAP_TOKENS.majorHubMinZoom) return station.isInterchange; // regional: interchanges
        if (z <= MAP_TOKENS.linesOnlyMaxZoom) return false;       // country: lines only, no dots
        return isMajorHub(station);                               // near-country: major hubs
    }

    function applyStationVisibility(z) {
        // Cull to the padded viewport: only stations you can actually see stay
        // live on the map. The network went nationwide (389 stops across Greece),
        // and keeping them all on the map at once made zooming heavy and painted
        // distant coastal lines (Katakolo, Corinth-Patras) over the sea at the
        // edges. The 0.4 pad keeps a ring just outside the frame so a small pan
        // never flashes an empty border. The selected stop is always kept.
        const bounds = map.getBounds().pad(0.4);
        for (const [id, marker] of markers) {
            const station = stationNodeMap.get(id);
            if (!station) continue;
            const inView = id === selectedStationId
                || bounds.contains([station.latitude, station.longitude]);
            const shouldShow = inView && stationVisibleAt(station, z);
            const onMap = map.hasLayer(marker);
            if (shouldShow && !onMap) marker.addTo(map);
            else if (!shouldShow && onMap) marker.remove();
        }
    }

    applyStationVisibility(map.getZoom());

    // Initialise the draggable bottom sheet here, right after the markers (a
    // point the init provably reaches), rather than at the tail of init where a
    // later unhandled rejection could skip it. Wrapped so any failure surfaces
    // instead of silently disabling the sheet.
    try { setupPanelBehavior(); } catch (e) { console.error("setupPanelBehavior failed", e); }

    function modeGlyph(mode) {
        switch (mode) {
            case "metro": return "🚇";
            case "tram": return "🚊";
            case "suburban":
            case "train": return "🚆";
            default: return "•";
        }
    }

    function buildStationIcon(station, selected) {
        const currentZoom = map.getZoom();
        const stationLines = station.lineIds
            .map((lineId) => lineMap.get(lineId))
            .filter(Boolean);
        const primaryLine = stationLines[0];
        const primaryColor = primaryLine ? primaryLine.color : "#64748b";
        const primaryMode = primaryLine ? primaryLine.type : "metro";

        // Per-station smart-code SVG, ONLY for the selected stop (at any zoom).
        // Previously every dot ballooned into a 22px artwork icon at z14, so the
        // whole map visibly resized at once when you crossed that threshold — a big
        // part of the "everything moves" churn. Now the dots keep one size and only
        // the focused station shows its artwork.
        if (selected) {
            const primarySid = station.stationIds[0];
            // A2 stations share most of their physical platforms with A1
            // (Doukissis Plakentias, Pallini, Metamorfosi, etc.). The icon
            // pack doesn't ship A2-prefixed SVGs, so fall back to any
            // sibling line's icon for the same station before giving up.
            const svgUrl = stationIconBySid.get(primarySid)
                || station.stationIds.map((sid) => stationIconBySid.get(sid)).find(Boolean);
            if (svgUrl) {
                const size = 30;
                // The smart-code SVG is layered as a CSS background ON TOP of the
                // normal dot rather than as an <img>. If the SVG 404s (a station
                // with no artwork, a stale manifest index, a deploy gap) the
                // background just renders nothing and the dot shows through -
                // never the browser's broken-image placeholder. Matches the
                // iOS / Android behaviour, which already fall back to the dot.
                return L.divIcon({
                    className: `station-svg-icon${selected ? " station-svg-icon--selected" : ""}`,
                    html: `<span class="station-pin__core" style="--pin:${primaryColor};"></span>`
                        + `<span class="station-svg-img" style="background-image:url('${svgUrl}');"></span>`,
                    iconSize: [size, size],
                    iconAnchor: [size / 2, size / 2],
                });
            }
        }

        // Mid/low zoom: a compact modern dot centred on the stop. Much smaller
        // than the old teardrop pins and centre-anchored (a dot sits ON the
        // point, it doesn't "drop" onto it). The mode glyph only appears when
        // the stop is selected or the user is zoomed in enough to read it, so
        // the default map stays clean and lightweight.
        const glyph = modeGlyph(primaryMode);
        // One dot size across zoom (only the selected stop differs). The old
        // z>=12 country->city size swap made every dot resize mid-zoom.
        const pinSize = selected ? MAP_TOKENS.dotSelected : MAP_TOKENS.dotCity;
        const showGlyph = selected || currentZoom >= MAP_TOKENS.glyphMinZoom;
        const glyphHtml = showGlyph ? `<span class="station-pin__glyph">${glyph}</span>` : "";

        if (station.isInterchange) {
            return L.divIcon({
                className: `station-pin station-pin--interchange${selected ? " station-pin--selected" : ""}`,
                html: `<span class="station-pin__core" style="--pin:${primaryColor};">${glyphHtml}</span>`,
                iconSize: [pinSize, pinSize],
                iconAnchor: [pinSize / 2, pinSize / 2],
            });
        }

        return L.divIcon({
            className: `station-pin${selected ? " station-pin--selected" : ""}`,
            html: `<span class="station-pin__core" style="--pin:${primaryColor};">${glyphHtml}</span>`,
            iconSize: [pinSize, pinSize],
            iconAnchor: [pinSize / 2, pinSize / 2],
        });
    }

    function updateMarkerSelection(nextId) {
        const previousId = selectedStationId;
        if (previousId && markers.has(previousId)) {
            const previous = stationNodeMap.get(previousId);
            restyleStation(previousId, previous, false);
        }

        selectedStationId = nextId;

        if (nextId && markers.has(nextId)) {
            const selected = stationNodeMap.get(nextId);
            const marker = markers.get(nextId);
            restyleStation(nextId, selected, true);
            // A selected stop is always shown, even a minor one at country zoom
            // that the decluttering rule would otherwise hide.
            if (!map.hasLayer(marker)) marker.addTo(map);
        }

        // If the previously selected stop was only visible because it was
        // selected, hide it again now that it isn't.
        const z = map.getZoom();
        if (previousId && previousId !== nextId && markers.has(previousId)) {
            const previous = stationNodeMap.get(previousId);
            if (previous && !stationVisibleAt(previous, z)) markers.get(previousId).remove();
        }
    }

    function lineLabel(station) {
        return station.lineIds
            .map((lineId) => lineMap.get(lineId)?.name || lineId)
            .join(", ");
    }

    function currentAthensParts() {
        const formatter = new Intl.DateTimeFormat("en-GB", {
            timeZone: "Europe/Athens",
            weekday: "short",
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit",
            hour12: false,
        });
        const parts = formatter.formatToParts(new Date());
        const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
        return {
            weekday: values.weekday,
            hour: Number(values.hour),
            minute: Number(values.minute),
            second: Number(values.second || 0),
        };
    }

    function nowMinutes() {
        const now = currentAthensParts();
        return now.hour * 60 + now.minute;
    }

    function formatTimeFromNow(minutesAway) {
        const now = currentAthensParts();
        const total = (now.hour * 60 + now.minute + minutesAway) % (24 * 60);
        const hour = Math.floor(total / 60);
        const minute = total % 60;
        return `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`;
    }
    // Mirrors core/designsystem/component/DepartureCard.kt#formatMinutesAway
    // and iosApp/Departure.minutesAwayDisplay. "Now" once the train is at
    // the platform, hours+min past 59 so late-night views like Nikaia M3
    // at 02:09 show "3h 25min" instead of "205 min" (which in Greek and
    // Albanian locales rendered as "1.315 min" — thousands-separator
    // confusion that started this whole thread).
    function formatMinutesAway(minutesAway) {
        if (minutesAway <= 0) return t("now");
        if (minutesAway === 1) return "1 min";
        if (minutesAway < 60) return `${minutesAway} min`;
        const h = Math.floor(minutesAway / 60);
        const m = minutesAway % 60;
        return m === 0 ? `${h}h` : `${h}h ${m}min`;
    }

    // Band-based projector matching core/domain/ComputeDeparturesFromBandsUseCase
    // and iosApp/ScheduleProjector.swift. Reads /api/schedules/{lineId} once at
    // page load (cached in `apiSchedules`) and respects:
    //   - operating hours (line is closed -> no departures)
    //   - day_type (mon_thu / fri / sat / sun) including holiday remap
    //   - past-midnight tail: at 01:10 Fri morning we also walk Thursday late_night
    //   - M3 split: city stops show M3 + M3_AIR, airport-only stops show M3_AIR
    function resolveHolidayDayType(date) {
        const mmdd = `${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
        switch (mmdd) {
            case "01-01": case "05-01": case "10-28": case "12-25": case "12-26": return "sun";
            case "08-15": return "aug_15";
            case "12-24": case "12-31": return "dec_24_31";
            case "01-02": case "01-06": case "11-17": return "sat";
            default: return null;
        }
    }
    function dayTypeFor(date, holiday) {
        if (holiday) return holiday;
        const dow = date.getDay();  // 0=Sun, 1=Mon, ... 6=Sat
        if (dow === 0) return "sun";
        if (dow >= 1 && dow <= 4) return "mon_thu";
        if (dow === 5) return "fri";
        return "sat";
    }
    function minutesOfDay(hhmm) {
        const m = /^(\d{2}):(\d{2})$/.exec(hhmm);
        if (!m) return null;
        return Number(m[1]) * 60 + Number(m[2]);
    }
    const M3_AIRPORT_ONLY = new Set(["M3_PAL", "M3_PEK", "M3_KRP", "M3_AER"]);
    function expandLineIds(stationId, lineIds) {
        const out = [];
        for (const lid of lineIds) {
            if (lid === "M3" || lid === "M3A") {
                if (M3_AIRPORT_ONLY.has(stationId)) out.push("M3_AIR");
                else { out.push("M3"); out.push("M3_AIR"); }
            } else {
                out.push(lid);
            }
        }
        return out;
    }
    function projectBand(band, shift, nowMinutes, lineId, direction, limit, out) {
        const rawStart = minutesOfDay(band.timeStart);
        const rawEnd = minutesOfDay(band.timeEnd);
        if (rawStart == null || rawEnd == null || !(band.headwayMinutes > 0)) return;
        const start = rawStart + shift;
        // Bands that close past midnight (M2 sat 22:00 → 00:20) ship with
        // rawEnd < rawStart because timeEnd lives on the next calendar
        // day. Wrap forward 24 h so 22:45 still lands inside the window.
        const end = rawEnd + shift + (rawEnd < rawStart ? 24 * 60 : 0);
        if (end < start) return;
        let slot = start;
        if (slot < nowMinutes) {
            const skips = Math.max(0, Math.floor((nowMinutes - slot) / band.headwayMinutes));
            slot = start + skips * band.headwayMinutes;
            while (slot < nowMinutes) slot += band.headwayMinutes;
        }
        let added = 0;
        while (slot <= end && added < limit) {
            const slotMin = Math.round(slot);
            const display = ((slotMin % (24 * 60)) + 24 * 60) % (24 * 60);
            out.push({
                lineId,
                direction,
                timeMinutes: slotMin,
                time: `${String(Math.floor(display / 60)).padStart(2, "0")}:${String(display % 60).padStart(2, "0")}`,
                minutesAway: Math.max(0, slotMin - nowMinutes),
            });
            slot += band.headwayMinutes;
            added++;
        }
    }
    function projectFromBundle(bundle, nowDate, lineIdForLabel, out, limit) {
        if (!bundle || !bundle.bands || !bundle.rules) return;
        const nowMinutes = nowDate.getHours() * 60 + nowDate.getMinutes();
        const holidayToday = resolveHolidayDayType(nowDate);
        const todayDt = dayTypeFor(nowDate, holidayToday);
        const descriptors = [[todayDt, 0]];
        if (nowMinutes < 4 * 60) {
            const yesterday = new Date(nowDate);
            yesterday.setDate(nowDate.getDate() - 1);
            descriptors.push([dayTypeFor(yesterday, null), -24 * 60]);
        }
        for (const [dt, shift] of descriptors) {
            // Verify the line is actually open on this day-type. If schedule_rules
            // says closed (no rule for this dt), we don't project.
            const rule = bundle.rules.find((r) => r.dayType === dt);
            if (!rule) continue;
            const openMin = minutesOfDay(rule.openTime);
            const closeMin = minutesOfDay(rule.closeTime);
            // closeTime can be 00:30, 02:00, etc. — treated as next-day if smaller than open.
            // If we're past closeTime relative to today's window AND we're not in the
            // late-night extension of yesterday, skip.
            // Subtract the shift so today's nowMinutes lands inside the
            // descriptor's own clock domain (shift = -1440 for the yesterday
            // overnight pass). Then only reject bands that are fully in the
            // past — future bands of today's day-type emit future slots
            // naturally, which is what we want at 02:09 Thursday when M3
            // mon_thu's first train is 05:30. 120 min upper slack mirrors
            // the iOS / Pi projector so downstream stations still emit
            // after the terminus's last slot leaves.
            const effectiveNow = nowMinutes - shift;
            if (closeMin != null && openMin != null && !rule.is247) {
                const effectiveClose = closeMin <= openMin ? closeMin + 24 * 60 : closeMin;
                if (effectiveNow > effectiveClose + 120) continue;
            }
            const bands = bundle.bands
                .filter((b) => b.dayType === dt)
                .sort((a, b) => (minutesOfDay(a.timeStart) ?? 0) - (minutesOfDay(b.timeStart) ?? 0));
            for (const band of bands) {
                const direction = lineIdForLabel === "M3_AIR" ? "Airport" : null;
                projectBand(band, shift, nowMinutes, lineIdForLabel, direction, limit - out.length, out);
                if (out.length >= limit) return;
            }
        }
    }
    /// PDF-grounded next-departures path. For any suburban station call
    /// (A1-A4), pull the next few trains that stop here from the per-train
    /// timestamp data set, with the real published HH:MM time.
    function realTimetableDepartures(station) {
        if (!apiTrainTimestamps || !apiTrainTimestamps.trains?.length) return [];
        const wantedNames = new Set([station.name, station.nameEl].filter(Boolean));
        const out = [];
        const now = new Date();
        const nowMinutes = now.getHours() * 60 + now.getMinutes();
        for (const train of apiTrainTimestamps.trains) {
            const stop = train.stops.find((s) => wantedNames.has(s.stationNameEn) || wantedNames.has(s.stationNameEl));
            if (!stop) continue;
            const [h, m] = stop.time.split(":").map((n) => parseInt(n, 10));
            if (Number.isNaN(h) || Number.isNaN(m)) continue;
            let minutesAway = h * 60 + m - nowMinutes;
            if (minutesAway < 0 || minutesAway > 240) continue;  // drop past and >4h ahead
            const last = train.stops[train.stops.length - 1];
            const line = lineMap.get(train.lineId);
            // Track that is built but not open carries no service, so it can have
            // no departures, however the feed or the projector might describe it.
            if (line && !isOperational(line)) continue;
            out.push({
                line: line || { id: train.lineId, name: train.lineId, color: "#7e22ce" },
                direction: last.stationNameEn,
                minutesAway,
                timeMinutes: h * 60 + m,
                timeLabel: stop.time,
                trainNo: train.trainNo,
            });
        }
        return out.sort((a, b) => a.minutesAway - b.minutesAway).slice(0, 10);
    }

    function buildStationDepartures(station) {
        // Prefer PDF-grounded data when we have it for this station.
        const real = realTimetableDepartures(station);
        if (real.length) return real;
        if (!apiSchedules || apiSchedules.size === 0) return [];
        const nowDate = new Date();
        const result = [];
        const expanded = expandLineIds(station.stationIds[0] || station.id, station.lineIds);
        for (const lineId of expanded) {
            const bundle = apiSchedules.get(lineId);
            if (!bundle) continue;
            const before = result.length;
            projectFromBundle(bundle, nowDate, lineId, result, 12);
            // Map line label for display: M3_AIR also shows as "Line 3" with Airport pill
            const displayLineId = lineId === "M3_AIR" ? "M3" : lineId;
            const line = lineMap.get(displayLineId);
            for (let i = before; i < result.length; i++) {
                result[i].line = line || { id: displayLineId, name: displayLineId, color: "#64748b" };
                if (!result[i].direction) {
                    // Both-direction lines: alternate between terminalA / terminalB for the next two
                    const slot = result[i].timeMinutes - (result[before]?.timeMinutes ?? 0);
                    result[i].direction = (i - before) % 2 === 0 ? line?.terminalB || "" : line?.terminalA || "";
                }
            }
        }
        return result
            .sort((a, b) => a.minutesAway - b.minutesAway)
            .slice(0, 10);
    }

    function vehicleIconFor(lineId, direction) {
        // Maps (lineId, destination text) to the operator directional SVG
        // served at /icons/directional_vehicle_icons/. Mirrors the iOS
        // TimetablesIcons helper so all three platforms pick the same artwork.
        const d = (direction || "").toLowerCase();
        const base = "/icons/directional_vehicle_icons/directional";
        switch (lineId) {
            case "M1":
                return d.includes("piraeus")
                    ? `${base}/metro/m1_piraeus_kifissia/metro_m1_left_to_piraeus.svg`
                    : `${base}/metro/m1_piraeus_kifissia/metro_m1_right_to_kifissia.svg`;
            case "M2":
                return d.includes("anthoupoli")
                    ? `${base}/metro/m2_anthoupoli_elliniko/metro_m2_left_to_anthoupoli.svg`
                    : `${base}/metro/m2_anthoupoli_elliniko/metro_m2_right_to_elliniko.svg`;
            case "M3":
                if (d.includes("airport") || d.includes("αεροδρ")) {
                    return `${base}/metro/m3_dimotiko_theatro_doukissis_plakentias_airport/metro_m3_right_to_airport.svg`;
                }
                if (d.includes("dimotiko") || d.includes("dimarheio") || d.includes("piraeus")) {
                    return `${base}/metro/m3_dimotiko_theatro_doukissis_plakentias_airport/metro_m3_left_to_dimotiko_theatro.svg`;
                }
                return `${base}/metro/m3_dimotiko_theatro_doukissis_plakentias_airport/metro_m3_right_to_doukissis_plakentias.svg`;
            case "T6":
                return d.includes("syntagma")
                    ? `${base}/tram/t6t7_syntagma_akti_posidonos_via_pikrodafni/tram_t6t7_left_to_syntagma.svg`
                    : `${base}/tram/t6t7_syntagma_akti_posidonos_via_pikrodafni/tram_t6t7_right_to_akti_posidonos.svg`;
            case "T7":
                return d.includes("akti") || d.includes("posidonos") || d.includes("piraeus")
                    ? `${base}/tram/t6t7_syntagma_akti_posidonos_via_pikrodafni/tram_t6t7_left_to_syntagma.svg`
                    : `${base}/tram/t6t7_syntagma_asklipiio_voulas_via_pikrodafni/tram_t6t7_right_to_asklipiio_voulas.svg`;
            case "A1":
                return d.includes("piraeus")
                    ? `${base}/train/p1_piraeus_airport/train_p1_left_to_piraeus.svg`
                    : `${base}/train/p1_piraeus_airport/train_p1_right_to_airport.svg`;
            case "A2":
                return d.includes("liosia")
                    ? `${base}/train/p1a_ano_liosia_airport/train_p1a_left_to_ano_liosia.svg`
                    : `${base}/train/p1a_ano_liosia_airport/train_p1a_right_to_airport.svg`;
            case "A3":
                return d.includes("athens") || d.includes("αθήνα")
                    ? `${base}/train/p3_athens_chalkida/train_p3_left_to_athens.svg`
                    : `${base}/train/p3_athens_chalkida/train_p3_right_to_chalkida.svg`;
            case "A4":
                return d.includes("piraeus")
                    ? `${base}/train/p2_piraeus_kiato/train_p2_left_to_piraeus.svg`
                    : `${base}/train/p2_piraeus_kiato/train_p2_right_to_kiato.svg`;
            default:
                return null;
        }
    }

    async function fetchApiDepartures(station) {
        // The server-side projector at /api/departures/next is the single
        // source of truth (iOS uses it too). It returns regular city M3
        // service alongside M3_AIR, sorted by minutesAway, so the bottom
        // sheet shows both Doukissis Plakentias and Airport rows.
        try {
            const stationId = station.stationIds?.[0] || station.id;
            const expanded = expandLineIds(stationId, station.lineIds);
            if (!expanded.length) return null;
            const url = `https://api-syrmos.peterdsp.dev/api/departures/next?stationId=${encodeURIComponent(stationId)}&lineIds=${encodeURIComponent(expanded.join(","))}&limit=10`;
            const r = await fetch(url);
            if (!r.ok) return null;
            const data = await r.json();
            const items = (data.departures || []).map((dep) => {
                const lineKey = dep.lineId === "M3_AIR" ? "M3" : dep.lineId;
                const line = lineMap.get(lineKey) || { id: lineKey, name: dep.line || lineKey, color: "#64748b" };
                return {
                    line,
                    direction: dep.direction || "",
                    destination: dep.direction || "",
                    minutesAway: Math.max(0, Math.round(dep.minutesAway)),
                    time: dep.time || "",
                    serviceType: dep.serviceType || "",
                };
            });
            return items;
        } catch (_) {
            return null;
        }
    }

    async function renderDepartures(station) {
        const apiDepartures = await fetchApiDepartures(station);
        const departures = (apiDepartures && apiDepartures.length)
            ? apiDepartures
            : buildStationDepartures(station);
        if (!departures.length) {
            stationDepartures.innerHTML = `<div class="departure-empty">${t("no_departures")}</div>`;
            return;
        }

        // Source-confidence: API departures are the live timetable feed
        // (SCHEDULED); the bundled fallback is the offline snapshot. One source
        // per render because both branches produce a single homogeneous list.
        const fromApi = !!(apiDepartures && apiDepartures.length);
        const sourceMod = fromApi ? "scheduled" : "offline";
        const sourceLabel = t(fromApi ? "scheduled" : "offline_snapshot");
        const sourceChip = `<span class="src-chip src-chip--${sourceMod}"><span class="src-chip__dot"></span>${sourceLabel}</span>`;

        stationDepartures.innerHTML = departures.map((departure) => {
            const minutesLabel = formatMinutesAway(departure.minutesAway);
            const lineId = departure.line?.id || "";
            const destination = departure.destination || departure.direction || "";
            const iconSrc = vehicleIconFor(lineId, destination);
            const iconHtml = iconSrc
                ? `<img class="departure-card__icon" src="https://api-syrmos.peterdsp.dev${iconSrc}" alt="${lineId}" loading="lazy" />`
                : `<span class="line-dot" style="background:${departure.line?.color || 'var(--accent)'};"></span>`;
            // Airport pill: serviceType=="airport" covers both outbound (to
            // Airport) and inbound (from Airport → Dimotiko Theatro). Keeps
            // the terminus text intact so the destination column stays
            // truthful — the pill just signals "this train touches the
            // Airport leg."
            const airportPill = departure.serviceType === "airport"
                ? `<span class="departure-card__pill departure-card__pill--airport">Airport</span>`
                : "";
            return `
                <div class="departure-card">
                    <div class="departure-card__header">
                        ${iconHtml}
                        <div class="departure-card__text">
                            <div class="departure-card__line">
                                <span>${departure.line?.name || lineId}</span>
                                ${airportPill}
                            </div>
                            <div class="departure-card__destination">${destination}</div>
                            ${sourceChip}
                        </div>
                        <div class="departure-card__eta">
                            <div class="departure-card__minutes">${minutesLabel}</div>
                            <div class="departure-card__time">${departure.time || ""}</div>
                        </div>
                    </div>
                </div>
            `;
        }).join("");
    }

    function selectStation(stationId, panToMarker) {
        const station = stationNodeMap.get(stationId);
        if (!station) return;

        updateMarkerSelection(stationId);

        if (panToMarker) {
            map.flyTo([station.latitude, station.longitude], Math.max(map.getZoom(), 14), {
                duration: 0.45,
            });
        }

        stationName.textContent = station.name;
        stationNameEl.textContent = station.nameEl && station.nameEl !== station.name ? station.nameEl : "";

        lineBadges.innerHTML = "";
        for (const lineId of station.lineIds) {
            const line = lineMap.get(lineId);
            if (!line) continue;

            const badge = document.createElement("div");
            badge.className = "line-badge";
            badge.style.background = `${line.color}18`;
            badge.style.color = line.color;
            badge.innerHTML = `<span class="line-dot" style="background:${line.color};"></span><span>${line.name}</span>`;
            lineBadges.appendChild(badge);
        }

        // Compact chip row instead of the old key/value table that showed
        // "Lines: N" (redundant with the badges above) and
        // "Merged: N records" (internal jargon). Only render chips for
        // information that's actually useful at this station.
        const chips = [];
        if (station.isInterchange) {
            chips.push({ icon: "↔", label: t("interchange") });
        }
        if (station.accessibility) {
            chips.push({ icon: "♿", label: t("accessible") });
        }
        if (station.zone > 1) {
            chips.push({ icon: "📍", label: `Zone ${station.zone}` });
        }

        stationMeta.innerHTML = chips
            .map(({ icon, label }) => `
                <span class="meta-chip">
                    <span class="meta-chip-icon">${icon}</span>
                    <span class="meta-chip-label">${label}</span>
                </span>
            `)
            .join("");
        // Hide the whole block when there are no meaningful chips — saves
        // a row of empty space on the common single-line, accessible, Zone 1
        // case (which describes most stations in the network).
        const metaBlock = document.getElementById("stationMetaBlock");
        if (metaBlock) {
            metaBlock.style.display = chips.length === 0 ? "none" : "";
        }

        renderDepartures(station);
        directionsLink.href = `https://www.google.com/maps/dir/?api=1&destination=${station.latitude},${station.longitude}&travelmode=transit`;

        stationSheet.classList.remove("station-sheet--hidden");

        // Live countdown tick: re-render departures every 15 seconds so the
        // minutes-away number actually counts down (5 → 4 → 3 …) instead of
        // freezing at whatever was on screen when the sheet opened.
        if (departureRefreshTimer) {
            clearInterval(departureRefreshTimer);
        }
        departureRefreshTimer = setInterval(() => {
            const current = stationNodeMap.get(stationId);
            if (!current) return;
            renderDepartures(current);
        }, 15_000);
    }

    function clearSelection() {
        updateMarkerSelection(null);
        stationDepartures.innerHTML = "";
        stationSheet.classList.add("station-sheet--hidden");
        if (departureRefreshTimer) {
            clearInterval(departureRefreshTimer);
            departureRefreshTimer = null;
        }
    }

    function renderSearchResults(results, rawQuery) {
        searchResults.innerHTML = "";
        for (const station of results.slice(0, 8)) {
            const row = document.createElement("div");
            row.className = "search-result";
            row.innerHTML = `
                <div class="search-result-name">${station.name}</div>
                <div class="search-result-meta">${lineLabel(station)}</div>
            `;
            row.addEventListener("click", () => {
                stationSearch.value = station.name;
                searchResults.innerHTML = "";
                selectStation(station.id, true);
            });
            searchResults.appendChild(row);
        }
        // Unified entry point (T6): the one search box also asks Ariadne. Any
        // non-empty query gets an "Ask Ariadne" row (built with textContent so a
        // typed query can't inject markup), so a natural-language question -
        // even one that matches no station - routes to the assistant instead of
        // needing a second, separate Ariadne box.
        const q = (rawQuery || "").trim();
        if (q) {
            const ask = document.createElement("div");
            ask.className = "search-result search-result--ariadne";
            const owl = document.createElement("img");
            owl.className = "search-result__owl";
            owl.src = "ariadne-mark.png";
            owl.alt = "";
            owl.setAttribute("aria-hidden", "true");
            const txt = document.createElement("div");
            const nm = document.createElement("div");
            nm.className = "search-result-name";
            nm.textContent = t("search_ask_ariadne");
            const meta = document.createElement("div");
            meta.className = "search-result-meta";
            meta.textContent = `“${q}”`;
            txt.appendChild(nm);
            txt.appendChild(meta);
            ask.appendChild(owl);
            ask.appendChild(txt);
            ask.addEventListener("click", () => {
                searchResults.innerHTML = "";
                stationSearch.value = "";
                if (window.__syrmosAskAriadne) window.__syrmosAskAriadne(q);
            });
            searchResults.appendChild(ask);
        }
    }

    stationSearch.addEventListener("input", (event) => {
        const raw = event.target.value.trim();
        const query = raw.toLowerCase();
        if (!query) {
            searchResults.innerHTML = "";
            return;
        }

        const filtered = stationNodes.filter((station) => {
            return station.name.toLowerCase().includes(query) || station.nameEl.toLowerCase().includes(query);
        });

        renderSearchResults(filtered, raw);
    });

    // Enter with no matching station routes the whole query to Ariadne, so the
    // single box answers questions as well as finding stops.
    stationSearch.addEventListener("keydown", (event) => {
        if (event.key !== "Enter") return;
        const raw = stationSearch.value.trim();
        if (!raw) return;
        const q = raw.toLowerCase();
        const exact = stationNodes.find((s) =>
            s.name.toLowerCase() === q || s.nameEl.toLowerCase() === q);
        if (exact) { searchResults.innerHTML = ""; selectStation(exact.id, true); return; }
        event.preventDefault();
        searchResults.innerHTML = "";
        stationSearch.value = "";
        if (window.__syrmosAskAriadne) window.__syrmosAskAriadne(raw);
    });

    sheetClose.addEventListener("click", () => {
        clearSelection();
    });

    locateButton.addEventListener("click", () => {
        if (!navigator.geolocation) return;

        navigator.geolocation.getCurrentPosition(
            (position) => {
                const lat = position.coords.latitude;
                const lon = position.coords.longitude;
                userLocation = { lat, lon };
                map.flyTo([lat, lon], 14, { duration: 0.5 });
                L.circleMarker([lat, lon], {
                    radius: 9,
                    color: "#0072CE",
                    weight: 3,
                    fillColor: "#73B9FF",
                    fillOpacity: 0.9,
                }).addTo(map);
                updateNearbyPanel();
            },
            () => {
                locateButton.textContent = t("location_unavailable");
                setTimeout(() => {
                    locateButton.textContent = t("locate_me");
                }, 1800);
            },
            { enableHighAccuracy: true, timeout: 10000 },
        );
    });

    zoomInButton.addEventListener("click", () => {
        map.zoomIn();
    });

    zoomOutButton.addEventListener("click", () => {
        map.zoomOut();
    });

    // Vehicles-hidden toggle: removes all live + simulated train markers from
    // the map so the user can read the network (lines + stations) without the
    // moving dots cluttering the view. Toggling back replays whatever the
    // current train state is.
    const vehiclesToggle = document.getElementById("vehiclesToggle");
    let vehiclesHidden = false;
    if (vehiclesToggle) {
        vehiclesToggle.addEventListener("click", () => {
            vehiclesHidden = !vehiclesHidden;
            vehiclesToggle.classList.toggle("control-button--active", vehiclesHidden);
            vehiclesToggle.setAttribute(
                "aria-label", vehiclesHidden ? t("show_vehicles") : t("hide_vehicles")
            );
            vehiclesToggle.title = vehiclesHidden ? t("show_vehicles") : t("hide_vehicles");
            window.__syrmosVehiclesHidden = vehiclesHidden;
            if (vehiclesHidden) {
                liveTrainMarkers.forEach((marker) => marker.remove());
                liveTrainMarkers.clear();
                simulatedTrainMarkers.forEach((marker) => marker.remove());
                simulatedTrainMarkers.clear();
            } else if (lastSimulatedTrains.length) {
                renderSimulatedTrainsOnMap(lastSimulatedTrains);
            }
        });
    }

    map.on("click", (e) => {
        // The canvas station dots are only ~5px, and a bare map click used to just
        // clearSelection() - so taps that landed a hair off a dot (or that a canvas
        // layer didn't claim) closed the sheet instead of opening the stop. Now the
        // map itself selects the nearest VISIBLE station within a forgiving radius,
        // so you don't need pixel-perfect aim; an empty tap still clears.
        const cp = map.latLngToContainerPoint(e.latlng);
        let bestId = null, bestD = Infinity;
        for (const [id, m] of markers) {
            if (!map.hasLayer(m)) continue;
            const p = map.latLngToContainerPoint(m.getLatLng());
            const d = Math.hypot(cp.x - p.x, cp.y - p.y);
            if (d < bestD) { bestD = d; bestId = id; }
        }
        if (bestId && bestD <= 18) selectStation(bestId, true);
        else clearSelection();
    });

    // Canvas dots are one fixed size across zoom, so there is no per-zoom icon
    // rebucket any more (that mass re-render was part of the "everything moves"
    // churn). Just refresh visibility + vehicles for the new zoom.
    map.on("zoomend", () => {
        const z = map.getZoom();
        applyStationVisibility(z);
        renderSimulatedTrainsOnMap(lastSimulatedTrains);
        if (DEBUG) logMarkerAudit(z);
    });

    // Diagnostic: with ?debug in the URL, print how many markers are live and
    // WARN on any whose real coordinate falls outside the Attica box. This is the
    // definitive on-device check for the "dots in the sea" question - it reads
    // each marker's actual getLatLng(), not a fragile pixel guess.
    const AUDIT_ATHENS_LINES = new Set(["M1", "M2", "M3", "M3_AIR", "T6", "T7", "A1", "A2", "A3", "A4"]);
    function logMarkerAudit(z) {
        let onMap = 0, offAttica = 0;
        for (const [id, m] of markers) {
            if (!map.hasLayer(m)) continue;
            onMap++;
            // Only audit ATHENS-network stops. The national lines (IC/RG toward
            // Lamia/Thessaloniki, Patras, etc.) legitimately span all of Greece and
            // can't be boxed into an Attica region, so they're not "in the sea"
            // anomalies. The box covers the full Athens extent: north to the A3
            // Chalkida line (~38.46), west to the A4 Kiato terminus (~22.73).
            const st = stationNodeMap.get(id);
            if (!st || !st.lineIds.some((l) => AUDIT_ATHENS_LINES.has(l))) continue;
            const ll = m.getLatLng();
            const outside = ll.lat < 37.7 || ll.lat > 38.55 || ll.lng < 22.65 || ll.lng > 24.15;
            if (outside) {
                offAttica++;
                console.warn(`[syrmos] station ${id} OUTSIDE Attica: ${ll.lat.toFixed(4)}, ${ll.lng.toFixed(4)}`);
            }
        }
        console.log(`[syrmos] zoom=${z} stationsOnMap=${onMap} trainsOnMap=${simulatedTrainMarkers.size} offAttica=${offAttica}`);
    }

    // Panning (not just zooming) has to re-cull the viewport too, otherwise
    // stations you scroll toward never appear and ones you leave behind linger.
    // moveend fires once at the end of a drag / inertia, so this stays cheap.
    map.on("moveend", () => applyStationVisibility(map.getZoom()));

    // Declared BEFORE fitBounds: the fit synchronously fires a move/zoom handler
    // that reads simulatedTrainMarkers, so if these sat after the fit (as they
    // did) that handler hit a temporal-dead-zone ReferenceError which aborted the
    // entire rest of init (hero, popular, nearby, live trains, simulator).
    const simulatedTrainMarkers = new Map();
    let lastSimulatedTrains = [];

    // Guard the fit anyway, so no future handler throw during it can abort init.
    // Open framed on the ATHENS network, not the whole country. The app went
    // nationwide, but fitting every station Ioannina->Alexandroupoli->Kalamata
    // opened on an all-Greece view where Athens was a 15px corner with no dots
    // and no trains (decluttered away) — it read as "empty". Frame the Athens
    // region so metro + tram + suburban + live trains are all on screen at launch;
    // zoom out for the country. (GPS "locate me" still recenters on the user.)
    try {
        // Frame the Athens METRO + TRAM core, not every athens-region stop. The
        // suburban A-lines run out to Kiato (100km west) and Chalkida (80km north),
        // so fitting them zoomed the launch view out to all of Attica and pushed the
        // metro to the edge behind the panel. The core set opens tight on the network
        // the user actually rides; suburban + the rest resolve as you pan / zoom out.
        const CORE_LINE_IDS = new Set(["M1", "M2", "M3", "M3_AIR", "T6", "T7"]);
        const coreStations = stationNodes.filter((s) =>
            (s.line_ids || s.lineIds || []).some((id) => CORE_LINE_IDS.has(id))
        );
        const focus = coreStations.length ? coreStations : stationNodes;
        const bounds = L.latLngBounds(focus.map((station) => [station.latitude, station.longitude]));
        map.fitBounds(bounds.pad(0.15));
    } catch (e) {
        console.error("fitBounds failed", e);
    }

    // Each guarded so one panel's failure can't cascade and abort the rest of
    // init (this is what previously left the bottom sheet uninitialised on the
    // live build). Failures surface in the console instead of silently breaking.
    const initStep = (name, fn) => { try { fn(); } catch (e) { console.error(`init ${name} failed`, e); (window.__initErrors = window.__initErrors || []).push(`${name}: ${e && e.message || e}`); } };
    initStep("renderPopularPanel", renderPopularPanel);
    initStep("updateNearbyPanel", updateNearbyPanel);
    initStep("connectLiveTrainStream", connectLiveTrainStream);
    initStep("pollLivePositions", pollLivePositions);
    initStep("setupRightRail", setupRightRail);
    initStep("startTrainSimulation", startTrainSimulation);
    initStep("setupHero", setupHero);
    // setupPanelBehavior() runs earlier (right after the markers) so a later
    // init failure can't skip the bottom sheet.

    function updateNearbyPanel() {
        if (userLocation) {
            const nearby = stationNodes
                .map((station) => ({
                    station,
                    distance: distanceMeters(
                        userLocation.lat,
                        userLocation.lon,
                        station.latitude,
                        station.longitude,
                    ),
                }))
                .sort((a, b) => a.distance - b.distance)
                .slice(0, 6);
            renderStationPanel(nearbyStationList, nearby.map((entry) => entry.station), true, nearby);
        } else {
            renderStationPanel(nearbyStationList, stationNodes
                .slice()
                .sort((a, b) => b.lineIds.length - a.lineIds.length)
                .slice(0, 6), false, []);
        }
    }

    function renderPopularPanel() {
        const popular = stationNodes
            .slice()
            .sort((a, b) => {
                const scoreA = (a.isInterchange ? 10 : 0) + a.lineIds.length;
                const scoreB = (b.isInterchange ? 10 : 0) + b.lineIds.length;
                return scoreB - scoreA;
            })
            .slice(0, 6);
        renderStationPanel(popularStationList, popular, false, []);
    }

    function renderStationPanel(container, stationsToRender, showDistance, distanceEntries) {
        container.innerHTML = stationsToRender.map((station, index) => {
            const distanceLabel = showDistance ? `${Math.round(distanceEntries[index].distance)} m away` : `${station.lineIds.length} lines`;
            return `
                <div class="panel-item" data-station-id="${station.id}">
                    <div class="panel-item__title">${station.name}</div>
                    <div class="panel-item__meta">${distanceLabel}</div>
                </div>
            `;
        }).join("");

        container.querySelectorAll("[data-station-id]").forEach((element) => {
            element.addEventListener("click", () => {
                selectStation(element.getAttribute("data-station-id"), true);
            });
        });
    }

    function connectLiveTrainStream() {
        // Poll the Syrmos API cached JSON every 10 seconds. The Pi handles the
        // upstream SSE connection and pre-filters the data so each browser
        // downloads only ~1.5 KB per poll instead of holding an SSE stream
        // that emits 10+ KB of schedule cards per second.
        const TRAINS_URL = "https://api-syrmos.peterdsp.dev/api/trains";
        const POLL_INTERVAL_MS = 10_000;

        async function pollOnce() {
            try {
                const res = await fetch(TRAINS_URL, { cache: "no-store" });
                if (!res.ok) {
                    return;
                }
                const payload = await res.json();
                updateLiveTrains(payload.trains || []);
            } catch (_error) {
                // Keep showing the last successful frame on transient errors.
            }
        }

        pollOnce();
        setInterval(pollOnce, POLL_INTERVAL_MS);
    }

    function updateLiveTrains(trainsFromApi) {
        const trains = trainsFromApi
            .filter((t) => t && t.lat != null && t.lng != null && t.lineId)
            .map((t) => ({
                id: t.id || t.trainNumber,
                lineId: t.lineId,
                trainNumber: t.trainNumber || I18N[currentLang].train,
                origin: t.origin || "",
                destination: t.destination || "",
                nextStation: t.nextStation || "",
                delay: t.delayMinutes || 0,
                speed: null,
                lat: t.lat,
                lng: t.lng,
                timestamp: "",
            }));

        renderLiveTrains(trains);
    }

    function renderLiveTrains(trains) {
        liveTrainMarkers.forEach((marker) => marker.remove());
        liveTrainMarkers.clear();

        if (trains.length) {
            const suburbanHtml = trains.slice(0, 5).map((train) => {
                const line = lineMap.get(train.lineId);
                return `
                    <div class="panel-item" data-live-suburban>
                        <div class="panel-item__title">🚆 ${line ? line.name : train.lineId} ${train.trainNumber}</div>
                        <div class="panel-item__meta">${train.origin || t("live")} ${currentLang === "el" ? "προς" : currentLang === "sq" ? "drejt" : "to"} ${train.destination || t("unknown")}${train.nextStation ? `, ${t("next")} ${train.nextStation}` : ""}</div>
                    </div>
                `;
            }).join("");
            const existing = liveTrainList.innerHTML;
            if (!existing.includes('data-live-suburban')) {
                liveTrainList.innerHTML = existing + suburbanHtml;
            }
        }

        // The Hide vehicles toggle: keep the live-train list panel populated
        // (users still want to know what's running) but skip rendering any
        // marker on the map itself.
        if (window.__syrmosVehiclesHidden) {
            return;
        }

        for (const train of trains) {
            const line = lineMap.get(train.lineId);
            const lineColor = line ? line.color : "#7C4DFF";
            // Custom divIcon so suburban trains are clearly distinguishable
            // from simulated metro/tram dots: pulsing ring + line-id badge.
            const icon = L.divIcon({
                className: "live-train-marker",
                html: `
                    <span class="live-train-marker__pulse" style="border-color:${lineColor}"></span>
                    <span class="live-train-marker__core" style="background:${lineColor}">
                        <span class="live-train-marker__glyph">🚆</span>
                    </span>
                    <span class="live-train-marker__badge" style="background:${lineColor}">${train.lineId}</span>
                `,
                iconSize: [44, 56],
                iconAnchor: [22, 22],
            });
            const marker = L.marker([train.lat, train.lng], {
                icon,
                keyboard: false,
                zIndexOffset: 1000,
            }).addTo(map);
            marker.bindTooltip(
                `${line ? line.name : train.lineId} ${train.trainNumber}<br>${train.origin || "?"} → ${train.destination || "?"}`,
                { direction: "top", offset: [0, -10] }
            );
            liveTrainMarkers.set(train.id, marker);
        }
    }

    function inferLineId(position) {
        const text = `${position.origin || ""} ${position.destination || ""} ${position.nextStation || ""} ${position.corridor || ""}`.toLowerCase();
        if (text.includes("ανω λιοσια") && text.includes("αεροδρομ")) return "A2";
        if (text.includes("αθην") && text.includes("χαλκιδ")) return "A3";
        if (text.includes("πειραι") && text.includes("κιατ")) return "A4";
        const corridor = (position.corridor || "").toLowerCase();
        if (corridor === "pirair" || (text.includes("πειραι") && text.includes("αεροδρομ"))) return "A1";
        return null;
    }

    function distanceMeters(lat1, lon1, lat2, lon2) {
        const r = 6371000;
        const toRad = (value) => (value * Math.PI) / 180;
        const dLat = toRad(lat2 - lat1);
        const dLon = toRad(lon2 - lon1);
        const a = Math.sin(dLat / 2) ** 2 +
            Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    function buildStationNodes(rawStations) {
        const grouped = new Map();
        const sortedStations = rawStations
            .slice()
            .sort((a, b) => a.latitude - b.latitude || a.longitude - b.longitude || String(a.id).localeCompare(String(b.id)));

        for (const station of sortedStations) {
            const key = stationClusterKey(station);
            if (!grouped.has(key)) {
                grouped.set(key, []);
            }
            grouped.get(key).push(station);
        }

        const initial = [...grouped.values()]
            .flatMap((group) => clusterByProximity(group).map((cluster, index) => {
                const primary = cluster[0];
                const lineIds = [...new Set(cluster.flatMap((station) => station.line_ids))];
                const stationIdByLineId = {};
                for (const station of cluster) {
                    for (const lineId of station.line_ids) {
                        if (!stationIdByLineId[lineId]) {
                            stationIdByLineId[lineId] = station.id;
                        }
                    }
                }
                const latitude = cluster.reduce((sum, station) => sum + station.latitude, 0) / cluster.length;
                const longitude = cluster.reduce((sum, station) => sum + station.longitude, 0) / cluster.length;
                return {
                    id: `${stationClusterKey(primary)}_${index}_${roundKey(latitude)}_${roundKey(longitude)}`,
                    stationIds: cluster.map((station) => station.id),
                    stationIdByLineId,
                    name: primary.name,
                    nameEl: primary.name_el,
                    latitude,
                    longitude,
                    lineIds,
                    isInterchange: lineIds.length > 1 || cluster.some((station) => station.is_interchange),
                    accessibility: cluster.some((station) => station.accessibility),
                    zone: Math.min(...cluster.map((station) => station.zone || 1)),
                };
            }));
        return mergeColocatedNodes(initial)
            .sort((a, b) => a.name.localeCompare(b.name));
    }

    // Mirror of the iOS map's second-pass distance merge — folds nodes
    // that sit within 60 m of each other even when their names differ
    // (M3 "Dimotiko Theatro" + T7 "Dimarhio / Dimotiko Theatro" are the
    // canonical example: same Piraeus square, ~32 m apart, but two
    // different mode names so the first-pass name-then-distance
    // clustering never compares them).
    function mergeColocatedNodes(nodes) {
        const radiusMeters = 60;
        const merged = [];
        for (const node of nodes) {
            const idx = merged.findIndex((existing) =>
                distanceMeters(existing.latitude, existing.longitude, node.latitude, node.longitude) <= radiusMeters
            );
            if (idx >= 0) {
                const existing = merged[idx];
                const combinedLineIds = [...new Set([...existing.lineIds, ...node.lineIds])];
                const combinedMap = { ...existing.stationIdByLineId };
                for (const [lineId, stationId] of Object.entries(node.stationIdByLineId)) {
                    if (!combinedMap[lineId]) combinedMap[lineId] = stationId;
                }
                const pickName = node.name.length > existing.name.length ? node : existing;
                merged[idx] = {
                    id: existing.id,
                    stationIds: [...existing.stationIds, ...node.stationIds],
                    stationIdByLineId: combinedMap,
                    name: pickName.name,
                    nameEl: pickName.nameEl,
                    latitude: (existing.latitude + node.latitude) / 2,
                    longitude: (existing.longitude + node.longitude) / 2,
                    lineIds: combinedLineIds,
                    isInterchange: combinedLineIds.length > 1 || existing.isInterchange || node.isInterchange,
                    accessibility: existing.accessibility || node.accessibility,
                    zone: Math.min(existing.zone || 1, node.zone || 1),
                };
            } else {
                merged.push(node);
            }
        }
        return merged;
    }

    function roundKey(value) {
        return Math.round(value * 1000000);
    }

    function stationClusterKey(station) {
        const nameKey = normalizeStationKey(station.name || "");
        const nameElKey = normalizeStationKey(station.name_el || "");
        return [nameKey, nameElKey]
            .filter(Boolean)
            .sort()
            .join("|");
    }

    function clusterByProximity(stations, radiusMeters = 300) {
        const clusters = [];
        for (const station of stations) {
            const match = clusters.find((cluster) =>
                cluster.some((other) =>
                    distanceMeters(other.latitude, other.longitude, station.latitude, station.longitude) <= radiusMeters,
                ),
            );
            if (match) {
                match.push(station);
            } else {
                clusters.push([station]);
            }
        }
        return clusters;
    }

    function normalizeStationKey(value) {
        return String(value)
            .toLowerCase()
            .normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .replace(/[άέήίϊΐόύϋΰώ]/g, (match) => ({
                ά: "α",
                έ: "ε",
                ή: "η",
                ί: "ι",
                ϊ: "ι",
                ΐ: "ι",
                ό: "ο",
                ύ: "υ",
                ϋ: "υ",
                ΰ: "υ",
                ώ: "ω",
            }[match]))
            .replace(/[^a-z0-9\u0370-\u03ff]+/g, "");
    }
    function setupRightRail() {
        const leftWrap = document.querySelector("#insightPanel .panel-cards-wrap");
        const rightWrap = document.getElementById("insightPanelRightWrap");
        if (!leftWrap || !rightWrap) return;
        const mq = window.matchMedia("(min-width: 980px)");
        const cards = Array.from(leftWrap.querySelectorAll(".panel-card--right-on-desktop"));
        function sync() {
            const target = mq.matches ? rightWrap : leftWrap;
            for (const card of cards) {
                if (card.parentNode !== target) target.appendChild(card);
            }
        }
        sync();
        if (mq.addEventListener) mq.addEventListener("change", sync);
        else mq.addListener(sync);
    }

    // The one-glance answer-first hero (design doc section 3 / task T7): the
    // next departure for the nearest (or busiest fallback) station, with a live
    // countdown that ticks every second. Rendered both as a card at the top of
    // the sheet and, compactly, in the always-visible peek bar so the answer is
    // there before the user asks. Departures re-project every 15s; only the
    // countdown recomputes each second (cheap). heroActive is declared earlier
    // (with the other init state) to avoid a temporal-dead-zone ReferenceError,
    // since setupHero is invoked before this point in the init sequence.
    function setupHero() {
        const wrap = document.querySelector("#insightPanel .panel-cards-wrap");
        const peekText = document.getElementById("panelPeekText");
        if (!wrap) return;

        const card = document.createElement("div");
        card.className = "panel-card hero-card";
        card.innerHTML =
            `<div class="hero-card__label"></div>` +
            `<div class="hero-card__station"></div>` +
            `<div class="hero-card__row">` +
            `<span class="hero-card__badge"></span>` +
            `<div class="hero-card__dest"><div class="hero-card__dir"></div><div class="hero-card__then"></div></div>` +
            `<div class="hero-card__count"></div></div>` +
            `<div class="hero-card__chip"></div>`;
        wrap.prepend(card);
        const el = (c) => card.querySelector(c);

        function heroStation() {
            if (userLocation) {
                let best = null, bestD = Infinity;
                for (const s of stationNodes) {
                    const d = distanceMeters(userLocation.lat, userLocation.lon, s.latitude, s.longitude);
                    if (d < bestD) { bestD = d; best = s; }
                }
                if (best) return best;
            }
            return stationNodes.slice().sort((a, b) =>
                ((b.isInterchange ? 10 : 0) + b.lineIds.length) - ((a.isInterchange ? 10 : 0) + a.lineIds.length))[0] || null;
        }

        let data = null; // { station, deps }
        function refreshData() {
            const station = heroStation();
            data = station ? { station, deps: buildStationDepartures(station) } : null;
        }

        card.addEventListener("click", () => { if (data?.station) selectStation(data.station.id, true); });

        function tick() {
            if (!data || !data.deps.length) { card.style.display = "none"; heroActive = false; return; }
            card.style.display = "";
            heroActive = true;
            const next = data.deps[0];
            const color = next.line?.color || "#64748b";
            el(".hero-card__label").textContent = t("hero_next");
            el(".hero-card__station").textContent = data.station.name || data.station.nameEl;
            const badge = el(".hero-card__badge");
            badge.textContent = next.line?.name || next.line?.id || "";
            badge.style.background = color;
            el(".hero-card__dir").textContent = next.direction ? `→ ${next.direction}` : "";
            const then = data.deps.slice(1, 3).map((d) => formatMinutesAway(d.minutesAway)).filter(Boolean).join(", ");
            el(".hero-card__then").textContent = then ? `${t("then")} ${then}` : "";

            // Countdown from the absolute departure minute-of-day.
            const now = new Date();
            const nowSec = now.getHours() * 3600 + now.getMinutes() * 60 + now.getSeconds();
            let secAway = next.timeMinutes * 60 - nowSec;
            if (secAway < -60) secAway = next.minutesAway * 60; // crossed midnight / stale
            const countEl = el(".hero-card__count");
            if (secAway <= 0) { countEl.textContent = t("now"); card.classList.add("hero-card--now"); }
            else if (secAway < 120) { countEl.textContent = `${Math.floor(secAway / 60)}:${String(secAway % 60).padStart(2, "0")}`; card.classList.remove("hero-card--now"); }
            else { countEl.textContent = `${Math.ceil(secAway / 60)}′`; card.classList.remove("hero-card--now"); }
            el(".hero-card__chip").textContent = `● ${t("scheduled")}`;

            // Answer-first peek line.
            if (peekText) peekText.textContent = `${next.line?.name || ""} → ${next.direction || ""} · ${countEl.textContent}`;
        }

        refreshData();
        tick();
        setInterval(refreshData, 15000);
        setInterval(tick, 1000);
    }

    function setupPanelBehavior() {
        const panel = document.getElementById("insightPanel");
        const peek = document.getElementById("panelPeek");
        const peekText = document.getElementById("panelPeekText");
        if (!panel || !peek) return;

        // --- Draggable bottom sheet with three detents (task T6) ---------
        // Mobile only: drag the peek bar to snap between peek / half / full;
        // a plain tap cycles through them. Desktop keeps the fixed left rail.
        const isMobile = () => window.matchMedia("(max-width: 720px)").matches;
        let detents = { peek: 64, half: 320, full: 640 };
        function recomputeDetents() {
            const vh = window.innerHeight;
            detents = { peek: 64, half: Math.round(vh * 0.5), full: Math.round(vh * 0.9) };
        }
        recomputeDetents();

        let currentDetent = "peek";
        function applyDetent(name, animate) {
            currentDetent = name;
            if (!animate) panel.classList.add("is-dragging");
            panel.style.setProperty("--sheet-h", detents[name] + "px");
            panel.classList.toggle("is-open", name !== "peek");
            if (!animate) requestAnimationFrame(() => panel.classList.remove("is-dragging"));
        }
        function nearestDetent(h) {
            return Object.entries(detents)
                .reduce((best, e) => Math.abs(e[1] - h) < Math.abs(detents[best] - h) ? e[0] : best, "peek");
        }

        window.addEventListener("resize", () => {
            recomputeDetents();
            if (currentDetent !== "peek") applyDetent(currentDetent, false);
        });

        let dragging = false, startY = 0, startH = 0, moved = 0;
        peek.addEventListener("pointerdown", (e) => {
            if (!isMobile()) return;
            dragging = true;
            moved = 0;
            startY = e.clientY;
            startH = panel.getBoundingClientRect().height;
            panel.classList.add("is-dragging");
            try { peek.setPointerCapture(e.pointerId); } catch (_) {}
        });
        peek.addEventListener("pointermove", (e) => {
            if (!dragging) return;
            const dy = startY - e.clientY;
            moved = Math.max(moved, Math.abs(dy));
            const h = Math.min(detents.full, Math.max(detents.peek, startH + dy));
            panel.style.setProperty("--sheet-h", h + "px");
            panel.classList.toggle("is-open", h > detents.peek + 20);
        });
        function endDrag() {
            if (!dragging) return;
            dragging = false;
            panel.classList.remove("is-dragging");
            if (moved < 6) {
                const next = currentDetent === "peek" ? "half" : currentDetent === "half" ? "full" : "peek";
                applyDetent(next, true);
            } else {
                applyDetent(nearestDetent(panel.getBoundingClientRect().height), true);
            }
        }
        peek.addEventListener("pointerup", endDrag);
        peek.addEventListener("pointercancel", endDrag);

        map.on("click", () => {
            if (isMobile()) applyDetent("peek", true);
        });

        const topBar = document.querySelector(".top-bar");
        const rightPanel = document.getElementById("insightPanelRight");
        if (topBar && window.matchMedia("(min-width: 721px)").matches) {
            const observer = new ResizeObserver(() => {
                const topPx = (topBar.offsetHeight + topBar.offsetTop + 12) + "px";
                panel.style.top = topPx;
                if (rightPanel) rightPanel.style.top = topPx;
            });
            observer.observe(topBar);
        }

        window._updatePeekText = function (count) {
            if (heroActive) return; // the hero owns the peek line when it has an answer
            if (peekText) {
                peekText.textContent = count > 0 ? t("trains_active", { n: count }) : t("live_trains");
            }
        };
    }

    function startTrainSimulation() {
        let lastPanelUpdate = 0;
        let lastMapUpdate = 0;
        function animateTrains(timestamp) {
            if (timestamp - lastMapUpdate > 250) {
                let trains = [];
                try { trains = simulateAllTrains(); } catch (_) { trains = []; }
                // Add the national + bus vehicles projected from the bundled timetables.
                try { trains = trains.concat(projectScheduledTrains()); } catch (_) {}
                try { renderSimulatedTrainsOnMap(trains); } catch (_) {}
                lastMapUpdate = timestamp;
                if (timestamp - lastPanelUpdate > 2000) {
                    try { renderSimulatedTrainsInPanel(trains); } catch (_) {}
                    lastPanelUpdate = timestamp;
                }
            }
            requestAnimationFrame(animateTrains);
        }
        requestAnimationFrame(animateTrains);
    }

    // Snapshot of `/api/live-positions` + `/api/station-offsets`. The map
    // dot's position for every metro / tram train is derived from these,
    // not from a haversine guess, so the moving icon stays locked to the
    // projector's "X min away" output.
    let livePositionsSnapshot = null;
    let stationOffsetsByLineDirection = null;

    async function loadStationOffsets() {
        try {
            const r = await fetch("https://api-syrmos.peterdsp.dev/api/station-offsets");
            const data = await r.json();
            const map = new Map();
            for (const line of data.lines || []) {
                const stops = (line.stops || []).slice().sort((a, b) => a.stopSequence - b.stopSequence);
                map.set(`${line.lineId}|${line.direction}`, stops);
            }
            stationOffsetsByLineDirection = map;
        } catch (_) {}
    }

    async function pollLivePositions() {
        // Metro + tram + Athens suburban A1-A4. The Pi projects all of these on
        // /api/live-positions (online), and each has bundled station-offsets so the
        // client interpolates them identically when offline. National + bus lines
        // are handled by the schedule projector below (no offsets on the Pi yet).
        const PROJECTED = "M1,M2,M3,M3_AIR,T6,T7,A1,A2,A3,A4";
        async function tick() {
            try {
                const r = await fetch(`https://api-syrmos.peterdsp.dev/api/live-positions?lineIds=${PROJECTED}`);
                const data = await r.json();
                const generatedAtEpoch = Date.parse(data.generatedAt) / 1000;
                livePositionsSnapshot = {
                    trains: data.trains || [],
                    generatedAtEpochSeconds: isNaN(generatedAtEpoch) ? Date.now() / 1000 : generatedAtEpoch,
                };
            } catch (_) {}
        }
        await loadStationOffsets();
        await tick();
        setInterval(tick, 15000);
    }

    // Position a train ALONG the real line polyline instead of on the straight
    // chord between its two stations. The chord cuts across water on curved /
    // coastal segments (the T7 Voula bay) and jumps across the map when the
    // station-offsets order is imperfect; following the polyline keeps every
    // train on the track. Falls back to the chord for a line with no geometry.
    const linePolyCache = new Map();
    const stationArcCache = new Map();
    function linePolyline(lineId) {
        if (linePolyCache.has(lineId)) return linePolyCache.get(lineId);
        const geom = geoCache.get(lineId)?.geometry;
        let poly = null;
        // geoCache holds either a LineString ([[lng,lat],...]) from shapes.json or
        // a MultiLineString ([[[lng,lat],...],...]) from the API override; flatten
        // both to one [lat,lng] polyline (same as the line-drawing code does).
        if (geom && geom.coordinates) {
            const segs = geom.type === "MultiLineString" ? geom.coordinates : [geom.coordinates];
            const coords = [];
            for (const seg of segs) {
                for (const pt of seg) {
                    if (Array.isArray(pt) && pt.length >= 2) coords.push([pt[1], pt[0]]);
                }
            }
            if (coords.length > 1) {
                const cum = [0];
                for (let i = 1; i < coords.length; i++) {
                    cum[i] = cum[i - 1] + distanceMeters(coords[i - 1][0], coords[i - 1][1], coords[i][0], coords[i][1]);
                }
                poly = { coords, cum };
            }
        }
        linePolyCache.set(lineId, poly);
        return poly;
    }
    function stationArc(poly, lineId, station) {
        const key = `${lineId}|${station.id}`;
        if (stationArcCache.has(key)) return stationArcCache.get(key);
        let best = 0, bestD = Infinity;
        for (let i = 0; i < poly.coords.length; i++) {
            const d = distanceMeters(poly.coords[i][0], poly.coords[i][1], station.latitude, station.longitude);
            if (d < bestD) { bestD = d; best = i; }
        }
        const arc = poly.cum[best];
        stationArcCache.set(key, arc);
        return arc;
    }
    function pointAtArc(poly, arc) {
        const { coords, cum } = poly;
        const total = cum[cum.length - 1];
        if (arc <= 0) return coords[0];
        if (arc >= total) return coords[coords.length - 1];
        let i = 1;
        while (i < cum.length && cum[i] < arc) i++;
        const f = (arc - cum[i - 1]) / ((cum[i] - cum[i - 1]) || 1);
        return [
            coords[i - 1][0] + (coords[i][0] - coords[i - 1][0]) * f,
            coords[i - 1][1] + (coords[i][1] - coords[i - 1][1]) * f,
        ];
    }
    function trainPosition(lineId, fromStation, toStation, frac) {
        const chord = [
            fromStation.latitude + (toStation.latitude - fromStation.latitude) * frac,
            fromStation.longitude + (toStation.longitude - fromStation.longitude) * frac,
        ];
        try {
            const poly = linePolyline(lineId);
            if (poly) {
                const arcFrom = stationArc(poly, lineId, fromStation);
                const arcTo = stationArc(poly, lineId, toStation);
                const p = pointAtArc(poly, arcFrom + (arcTo - arcFrom) * frac);
                // Never return a bad point that would throw at L.marker time.
                if (p && isFinite(p[0]) && isFinite(p[1])) {
                    // Guard against a wrong polyline mapping (a station snapping to a
                    // far vertex on a looping / past-the-terminus shape, e.g. the T7
                    // tram track running past Asklipiio Voulas) flinging the dot into
                    // the sea. A point interpolated between two stations should sit
                    // within roughly the segment length of BOTH; if it overshoots that
                    // (plus a small curve margin), the arc mapping is wrong -> fall
                    // back to the honest chord.
                    const segLen = distanceMeters(fromStation.latitude, fromStation.longitude, toStation.latitude, toStation.longitude);
                    const dF = distanceMeters(p[0], p[1], fromStation.latitude, fromStation.longitude);
                    const dT = distanceMeters(p[0], p[1], toStation.latitude, toStation.longitude);
                    if (dF <= segLen + 600 && dT <= segLen + 600) return p;
                }
            }
        } catch (_) { /* fall through to the chord */ }
        return chord;
    }

    function simulateAllTrains() {
        // Bail until both snapshots have landed; the very first paint
        // shows no dots rather than haversine guesses that disagree
        // with the bottom-sheet projector.
        if (!livePositionsSnapshot || !stationOffsetsByLineDirection) return [];

        const nowEpoch = Date.now() / 1000;
        const linesById = new Map(lines.map((l) => [l.id, l]));
        // Resolve offset stops to stations by id OR by name. The /api/station-offsets
        // feed uses the server's station-id scheme (e.g. M1_THI, M1_KIF) which differs
        // from the bundled snapshot's ids (M1_THE, M1_KHE) for the same physical
        // stations, so an id-only lookup silently drops ~1/3 of stops and the trains
        // between them. The station NAME (stationEn) is the one key both sides share.
        const stationById = new Map();
        for (const stns of lineStations.values()) {
            for (const s of stns) stationById.set(s.id, s);
        }
        // Resolve a stop's NAME only among the train's OWN line. A global name map
        // let a stop that shares a name with a station on a DIFFERENT line resolve
        // to the wrong one, pulling the train onto the wrong track. The id map
        // (shared across the network) is tried first; the name fallback is scoped.
        const nameMapByLine = new Map();
        const lineNameMap = (lineId) => {
            let m = nameMapByLine.get(lineId);
            if (!m) {
                m = new Map();
                for (const s of (lineStations.get(lineId) || [])) {
                    if (s.name) m.set(s.name.toLowerCase(), s);
                    if (s.name_el) m.set(s.name_el.toLowerCase(), s);
                    if (s.nameEl) m.set(s.nameEl.toLowerCase(), s);
                }
                nameMapByLine.set(lineId, m);
            }
            return m;
        };
        const resolveStop = (stop, lineId) =>
            stationById.get(stop.stationId) || lineNameMap(lineId).get((stop.stationEn || "").toLowerCase());

        const result = [];
        for (const raw of livePositionsSnapshot.trains) {
            // station_offsets keys M3_AIR rows under "M3" because the
            // airport service rides the same polyline up to Doukissis
            // Plakentias; mirror the lookup.
            const offsetKey = `${raw.lineId === "M3_AIR" ? "M3" : raw.lineId}|${raw.directionKey}`;
            const stops = stationOffsetsByLineDirection.get(offsetKey);
            if (!stops || stops.length < 2) continue;

            const displayLineId = raw.lineId === "M3_AIR" ? "M3" : raw.lineId;
            const line = linesById.get(displayLineId);
            // Suburban A1-A4 are now projected too (they arrive in the poll and have
            // bundled offsets); only skip a line we can't resolve.
            if (!line) continue;

            const originEpoch = livePositionsSnapshot.generatedAtEpochSeconds - raw.elapsedMinutes * 60;
            const elapsed = (nowEpoch - originEpoch) / 60;
            if (elapsed < 0 || elapsed > raw.totalTravelMinutes + 0.5) continue;

            let segIdx = 0;
            for (let i = 0; i < stops.length - 1; i++) {
                if (stops[i].minutesFromOrigin <= elapsed && elapsed < stops[i + 1].minutesFromOrigin) {
                    segIdx = i;
                    break;
                }
                if (i === stops.length - 2) segIdx = i;
            }
            const fromStop = stops[segIdx];
            const toStop = stops[segIdx + 1];
            const fromStation = resolveStop(fromStop, displayLineId);
            const toStation = resolveStop(toStop, displayLineId);
            if (!fromStation || !toStation) continue;
            const segDuration = toStop.minutesFromOrigin - fromStop.minutesFromOrigin;
            const frac = segDuration > 0
                ? Math.min(Math.max((elapsed - fromStop.minutesFromOrigin) / segDuration, 0), 1)
                : 0;

            const [lat, lng] = trainPosition(displayLineId, fromStation, toStation, frac);
            const dest = raw.directionKey === "outbound" ? line.terminalB : line.terminalA;

            result.push({
                id: `${raw.lineId}_${raw.directionKey}_${Math.round(raw.originDepartureMinute)}`,
                line,
                direction: raw.directionKey,
                destination: dest,
                bearing: bearingDeg(fromStation.latitude, fromStation.longitude, toStation.latitude, toStation.longitude),
                fromStation: fromStation.name,
                toStation: toStation.name,
                lat,
                lng,
                isAirport: raw.lineId === "M3_AIR",
                progress: Math.min(elapsed / raw.totalTravelMinutes, 1),
            });
        }
        return result;
    }

    // Metro/tram/A-line vehicles come from /api/live-positions (simulateAllTrains).
    const LIVE_POSITION_LINES = new Set(["M1", "M2", "M3", "M3_AIR", "T6", "T7", "A1", "A2", "A3", "A4"]);

    // National rail + rail-replacement buses (IC/RG/KO/PL/DK/PS/bus corridors) have
    // no live-position feed or station-offsets on the Pi, so project them CLIENT-side
    // from the bundled timetables: for every trip running right now, find the segment
    // the clock lands in and interpolate along the line's track. This is the "else
    // interpolate from the timetable" path; if the Pi ever serves their live
    // positions it flows through connectLiveTrainStream and wins per line.
    function projectScheduledTrains() {
        if (!apiSchedules || apiSchedules.size === 0) return [];
        const out = [];
        const now = new Date();
        const today = dayTypeFor(now, resolveHolidayDayType(now));
        const nowMin = now.getHours() * 60 + now.getMinutes() + now.getSeconds() / 60;
        for (const line of lines) {
            if (LIVE_POSITION_LINES.has(line.id)) continue;          // handled above
            if ((line.status || "operational") === "under_construction") continue;
            const bundle = apiSchedules.get(line.id);
            if (!bundle || !Array.isArray(bundle.trips)) continue;
            for (const trip of bundle.trips) {
                // dayType is authoritative (mon_thu / fri / sat / sun); an empty
                // dayType means the trip runs every day.
                const td = (trip.dayType || "").toLowerCase();
                if (td && td !== today) continue;
                const stops = trip.stops;
                if (!Array.isArray(stops) || stops.length < 2) continue;
                const times = stops.map((s) => minutesOfDay(s.departureTime));
                if (times.some((t) => t == null)) continue;
                // Skip trips that wrap past midnight (non-monotonic) - rare on these lines.
                let monotonic = true;
                for (let i = 1; i < times.length; i++) if (times[i] < times[i - 1]) { monotonic = false; break; }
                if (!monotonic) continue;
                if (nowMin < times[0] || nowMin > times[times.length - 1]) continue;
                let seg = 0;
                for (let i = 0; i < stops.length - 1; i++) {
                    if (times[i] <= nowMin && nowMin < times[i + 1]) { seg = i; break; }
                }
                const from = stationMap.get(stops[seg].stationId);
                const to = stationMap.get(stops[seg + 1].stationId);
                if (!from || !to) continue;
                const dur = times[seg + 1] - times[seg];
                const frac = dur > 0 ? Math.min(Math.max((nowMin - times[seg]) / dur, 0), 1) : 0;
                const [lat, lng] = trainPosition(line.id, from, to, frac);
                const dest = stationMap.get(stops[stops.length - 1].stationId);
                out.push({
                    id: `${line.id}_${trip.trainNo || seg}_${trip.direction || ""}`,
                    line,
                    direction: trip.direction || "",
                    destination: (dest && dest.name) || line.terminalB || "",
                    fromStation: from.name,
                    toStation: to.name,
                    lat,
                    lng,
                    bearing: bearingDeg(from.latitude, from.longitude, to.latitude, to.longitude),
                    scheduled: true,
                });
            }
        }
        return out;
    }


    // Compass bearing from -> to, so a train's triangle points the way it travels.
    function bearingDeg(lat1, lon1, lat2, lon2) {
        const toRad = (d) => (d * Math.PI) / 180;
        const y = Math.sin(toRad(lon2 - lon1)) * Math.cos(toRad(lat2));
        const x = Math.cos(toRad(lat1)) * Math.sin(toRad(lat2)) -
            Math.sin(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.cos(toRad(lon2 - lon1));
        return (Math.atan2(y, x) * 180 / Math.PI + 360) % 360;
    }

    // Trains are DIRECTIONAL TRIANGLES (a small DOM divIcon), not dots, so they're
    // instantly distinct from the round station stops and show which way each one
    // is heading. Only ~50 of them, so the DOM cost is trivial vs the canvas dots.
    function trainMarkerIcon(train) {
        const color = train.line.color || "#0072CE";
        const rot = Math.round(train.bearing || 0);
        return L.divIcon({
            className: "sim-train-tri",
            html: `<svg width="20" height="20" viewBox="0 0 20 20" class="train-tri" style="transform: rotate(${rot}deg);">`
                + `<path d="M10 2 L17 17 L3 17 Z" fill="${color}" stroke="#ffffff" stroke-width="1.6" stroke-linejoin="round"/></svg>`,
            iconSize: [20, 20],
            iconAnchor: [10, 10],
        });
    }

    function renderSimulatedTrainsOnMap(trains) {
        lastSimulatedTrains = trains;
        const activeIds = new Set(trains.map((t) => t.id));

        // Pull every simulated-train marker off the map when EITHER the manual
        // "Hide vehicles" toggle is on OR the map is zoomed out past the regional
        // threshold. At country/regional zoom the entire Athens fleet collapses
        // into a single ~15px pile on the coastline that reads as "trains in the
        // sea"; stations already declutter to lines-only at this zoom, so the
        // vehicles must follow the same rule. Coords keep accumulating in
        // lastSimulatedTrains, so crossing back over the threshold restores every
        // position without missing a beat.
        if (window.__syrmosVehiclesHidden || map.getZoom() < MAP_TOKENS.majorHubMinZoom) {
            simulatedTrainMarkers.forEach((marker) => marker.remove());
            simulatedTrainMarkers.clear();
            return;
        }

        simulatedTrainMarkers.forEach((marker, id) => {
            if (!activeIds.has(id)) {
                marker.remove();
                simulatedTrainMarkers.delete(id);
            }
        });

        for (const train of trains) {
            if (simulatedTrainMarkers.has(train.id)) {
                const m = simulatedTrainMarkers.get(train.id);
                m.setLatLng([train.lat, train.lng]);
                m.setIcon(trainMarkerIcon(train)); // keep the heading current
            } else {
                const marker = L.marker([train.lat, train.lng], {
                    icon: trainMarkerIcon(train),
                    keyboard: false,
                    zIndexOffset: 1000,
                }).addTo(map);

                marker.bindTooltip(
                    `${train.line.name} → ${train.destination}<br>Near ${train.fromStation}`,
                    { direction: "top", offset: [0, -12] }
                );

                simulatedTrainMarkers.set(train.id, marker);
            }
        }
    }

    function renderSimulatedTrainsInPanel(trains) {
        if (window._updatePeekText) window._updatePeekText(trains.length);
        if (!trains.length) return;

        const perLine = new Map();
        for (const train of trains) {
            const key = `${train.line.id}_${train.direction}`;
            if (!perLine.has(key)) perLine.set(key, train);
        }
        const display = [...perLine.values()].slice(0, 10);

        const panelHtml =
            `<div class="panel-item"><div class="panel-item__count">${t("trains_active", { n: trains.length })}</div></div>` +
            display.map((train) => {
                const icon = train.isAirport ? "✈" : train.line.type === "tram" ? "🚊" : "🚇";
                return `
                    <div class="panel-item" data-train-id="${train.id}" data-train-lat="${train.lat}" data-train-lng="${train.lng}">
                        <div class="panel-item__title">${icon} ${train.line.name} → ${train.destination}</div>
                        <div class="panel-item__meta">Near ${train.fromStation} · Next: ${train.toStation}</div>
                    </div>
                `;
            }).join("");

        liveTrainList.innerHTML = panelHtml;

        liveTrainList.querySelectorAll("[data-train-id]").forEach((el) => {
            el.addEventListener("click", () => {
                const lat = parseFloat(el.getAttribute("data-train-lat"));
                const lng = parseFloat(el.getAttribute("data-train-lng"));
                if (!isNaN(lat) && !isNaN(lng)) {
                    map.flyTo([lat, lng], Math.max(map.getZoom(), 15), { duration: 0.45 });
                }
            });
        });
    }

    const panelStyle = document.createElement("style");
    panelStyle.textContent = `
        @media (min-width: 721px) {
            .insight-panel { left: 16px !important; right: auto !important; width: min(280px, calc(100vw - 32px)) !important; }
            .panel-card { padding: 12px !important; border-radius: 16px !important; }
            .panel-list { max-height: 22vh !important; }
            .panel-item { padding: 8px 10px !important; border-radius: 12px !important; }
            .panel-item__title { font-size: 13px !important; }
            .panel-item__meta { font-size: 11px !important; }
        }
    `;
    document.head.appendChild(panelStyle);

    const themeToggle = document.getElementById("themeToggle");
    if (themeToggle) {
        // Swap the line-icon (moon in light, sun in dark) instead of writing a
        // text glyph, which would blow away the inline <svg><use>.
        const setThemeIcon = (isDark) => {
            const use = themeToggle.querySelector("use");
            if (use) use.setAttribute("href", isDark ? "#ic-sun" : "#ic-theme");
        };
        const saved = localStorage.getItem("syrmos-theme");
        const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;
        if (saved === "dark" || (!saved && prefersDark)) {
            document.body.classList.add("dark-mode");
            setThemeIcon(true);
        }
        themeToggle.addEventListener("click", () => {
            const isDark = document.body.classList.toggle("dark-mode");
            setThemeIcon(isDark);
            localStorage.setItem("syrmos-theme", isDark ? "dark" : "light");
            // Swap the base map to the matching light / dark minimal tiles.
            if (window.__syrmosApplyTileLayer) window.__syrmosApplyTileLayer();
        });
    }

    // Ariadne panel wiring. The parser (window.SyrmosAriadne) is offline and
    // deterministic; this block just routes its intents into the same web
    // primitives the map already uses (selectStation, Google Maps directions,
    // OASA fare panel).
    // What's New modal for the web. Shows once per version tag via
    // localStorage. Content mirrors the mobile popup minus the "clever
    // Ariadne" bullet because the web build doesn't have an on-device LLM
    // normalizer.
    (function whatsNewWeb() {
        const key = "syrmos.whatsnew.version";
        const version = "1.1.1-r2";
        try {
            if (localStorage.getItem(key) === version) return;
        } catch (_) { return; }
        const lang = currentLang;
        const title = lang === "el" ? "Τι νέο υπάρχει στο Syrmos"
            : lang === "sq" ? "Çfarë ka të re në Syrmos"
            : "What's new in Syrmos";
        const gotIt = lang === "el" ? "Εντάξει" : lang === "sq" ? "Në rregull" : "Got it";
        const bullets = lang === "el" ? [
            "Ρώτα την Αριάδνη για τον καιρό — «καιρός στον Πειραιά», offline-safe.",
            "Σχεδιασμός με στόχο χρόνου — «αεροδρόμιο στις 21:30» σου λέει πότε να ξεκινήσεις.",
            "Προειδοποίηση κακοκαιρίας με τηλέφωνα έκτακτης ανάγκης (112, 199, 11185).",
            "Ανανεωμένη κάρτα παρακολούθησης με στριπ σταθμών.",
            "Παρακολούθηση οποιουδήποτε τρένου — γραμμή, κατεύθυνση, σταθμός, δρομολόγιο.",
        ] : lang === "sq" ? [
            "Pyet Ariadnen për motin — “moti në Piraeus”, offline i sigurt.",
            "Planifikim me kohë objektiv — “aeroporti deri në 21:30” të thotë kur të nisesh.",
            "Paralajmërim moti me numra emergjence (112, 199, 11185).",
            "Karta e ndjekjes e ridizajnuar me strip stacionesh.",
            "Ndiq çdo tren — linjë, drejtim, stacion, nisje.",
        ] : [
            "Ask Ariadne about weather — “weather at Piraeus”, offline-safe.",
            "Time-anchored planning — “airport by 21:30” answers when to leave.",
            "Severe-weather warning with emergency numbers (112, 199, 11185).",
            "Redesigned tracking card with an animated station strip.",
            "Track any train — pick line, direction, station, departure.",
        ];

        const scrim = document.createElement("div");
        scrim.className = "whatsnew-scrim";
        scrim.innerHTML = `
            <div class="whatsnew-modal">
                <div class="whatsnew-modal__head">
                    <div class="whatsnew-modal__owl">🦉</div>
                    <div class="whatsnew-modal__title">${title}</div>
                </div>
                <ul class="whatsnew-modal__list">
                    ${bullets.map((b) => `<li>${b}</li>`).join("")}
                </ul>
                <button class="whatsnew-modal__button" type="button">${gotIt}</button>
            </div>
        `;
        document.body.appendChild(scrim);

        const dismiss = () => {
            try { localStorage.setItem(key, version); } catch (_) {}
            scrim.remove();
        };
        scrim.querySelector(".whatsnew-modal__button").addEventListener("click", dismiss);
        scrim.addEventListener("click", (e) => {
            if (e.target === scrim) dismiss();
        });

        const s = document.createElement("style");
        s.textContent = `
            .whatsnew-scrim {
                position: fixed; inset: 0; z-index: 1000;
                background: rgba(0,0,0,0.45);
                display: flex; align-items: center; justify-content: center;
                padding: 16px;
            }
            .whatsnew-modal {
                width: min(440px, 100%);
                background: linear-gradient(160deg, #F0F7FF, #FBFAEF);
                color: #111;
                border-radius: 22px;
                padding: 24px;
                box-shadow: 0 24px 60px rgba(0,0,0,0.35);
            }
            body.dark-mode .whatsnew-modal {
                background: linear-gradient(160deg, #0d1420, #050810);
                color: #f4f4f5;
            }
            .whatsnew-modal__head {
                display: flex; align-items: center; gap: 12px; margin-bottom: 12px;
            }
            .whatsnew-modal__owl {
                width: 52px; height: 52px; border-radius: 50%;
                background: rgba(0,114,206,0.14);
                display: flex; align-items: center; justify-content: center;
                font-size: 28px;
            }
            .whatsnew-modal__title { font-weight: 700; font-size: 18px; }
            .whatsnew-modal__list {
                margin: 4px 0 16px; padding-left: 20px;
                display: flex; flex-direction: column; gap: 8px;
                font-size: 14px; line-height: 1.4;
            }
            .whatsnew-modal__button {
                width: 100%; padding: 12px 16px;
                border: none; cursor: pointer;
                border-radius: 14px; font: inherit; font-weight: 600; font-size: 15px;
                background: #0072CE; color: #fff;
            }
        `;
        document.head.appendChild(s);
    })();

    // Severe-weather banner. Checks central Athens on page load; when the
    // WMO weather code is showers, snow, or thunderstorm, injects a small
    // amber warning strip above the map with local emergency numbers.
    // Mirrors the EmergencyWeatherCard on iOS and Compose.
    (async function severeWeatherWarning() {
        try {
            const url = "https://api.open-meteo.com/v1/forecast?latitude=37.9838&longitude=23.7275&current=weather_code&timezone=auto";
            const r = await fetch(url);
            const data = await r.json();
            const code = data.current && data.current.weather_code;
            const severe = (code >= 80 && code <= 86) ||
                (code >= 71 && code <= 77) ||
                code === 95 || code === 96 || code === 99;
            if (!severe) return;

            const banner = document.createElement("div");
            banner.className = "severe-weather-banner";
            const lang = currentLang;
            const title = code === 95 || code === 96 || code === 99
                ? (lang === "el" ? "Καταιγίδα σε εξέλιξη" : lang === "sq" ? "Stuhi në zhvillim" : "Storm in progress")
                : (lang === "el" ? "Έντονη κακοκαιρία" : lang === "sq" ? "Mot i keq" : "Severe weather");
            const body = lang === "el"
                ? "Οι υπόγειες γραμμές μετρό είναι η πιο ασφαλής επιλογή. Πρόσεχε στη μετακίνηση."
                : lang === "sq"
                ? "Metroja nëntokësore është zgjidhja më e sigurt. Ki kujdes gjatë udhëtimit."
                : "Underground metro lines are the safest option. Take care on your journey.";
            const numbersHeader = lang === "el" ? "ΤΗΛΕΦΩΝΑ ΕΚΤΑΚΤΗΣ ΑΝΑΓΚΗΣ"
                : lang === "sq" ? "NUMRAT E EMERGJENCËS"
                : "EMERGENCY NUMBERS";
            const num112 = lang === "el" ? "Ευρωπαϊκή γραμμή έκτακτης ανάγκης"
                : lang === "sq" ? "Numri europian i emergjencës"
                : "European emergency line";
            const numFire = lang === "el" ? "Πυροσβεστική" : lang === "sq" ? "Zjarrfikësit" : "Fire service";
            const numOASA = lang === "el" ? "Πληροφορίες OASA" : lang === "sq" ? "Informacione OASA" : "OASA transit info";
            banner.innerHTML = `
                <div class="severe-weather-banner__head">
                    <span class="severe-weather-banner__cloud">☁️</span>
                    <span class="severe-weather-banner__drop"></span>
                    <div>
                        <div class="severe-weather-banner__title">${title}</div>
                        <div class="severe-weather-banner__sub">${body}</div>
                    </div>
                </div>
                <div class="severe-weather-banner__numbers">
                    <div class="severe-weather-banner__numbers-header">${numbersHeader}</div>
                    <div><a class="severe-weather-banner__badge" href="tel:112">☎ 112</a> ${num112}</div>
                    <div><a class="severe-weather-banner__badge" href="tel:199">☎ 199</a> ${numFire}</div>
                    <div><a class="severe-weather-banner__badge" href="tel:11185">☎ 11185</a> ${numOASA}</div>
                    <div class="severe-weather-banner__hint">${
                        lang === "el" ? "Πατήστε έναν αριθμό για κλήση."
                        : lang === "sq" ? "Prek një numër për të thirrur."
                        : "Tap a number to call."
                    }</div>
                </div>
                <button class="severe-weather-banner__close" aria-label="Dismiss">×</button>
            `;
            document.body.appendChild(banner);
            banner.querySelector(".severe-weather-banner__close").addEventListener("click", () => banner.remove());
        } catch (_) {
            // Silent no-op; the app keeps working without the warning.
        }
    })();

    if (window.SyrmosAriadne) {
        window.SyrmosAriadne.init({ stations: stations, lines: lines });

        // stationMap keys off the raw station ids from stations.json
        // ("M3_NIK", "M2_SYN"), which is what SyrmosAriadne returns
        // in its intents. selectStation() however keys off the composite
        // stationNode ids ("NIKAIA_0_37966_23648") because it clusters
        // nearby platforms into one map marker. Build a reverse map so
        // an Ariadne raw station id resolves to the correct node id.
        const rawIdToNodeId = new Map();
        for (const node of stationNodes) {
            for (const rawId of node.stationIds) {
                rawIdToNodeId.set(rawId, node.id);
            }
        }
        function nodeIdFor(rawId) {
            return rawIdToNodeId.get(rawId) || null;
        }
        function openStation(rawId) {
            const nodeId = nodeIdFor(rawId);
            if (nodeId) selectStation(nodeId, true);
        }

        const severeWeatherStyle = document.createElement("style");
        severeWeatherStyle.textContent = `
            .severe-weather-banner {
                position: fixed; z-index: 960;
                top: 90px; left: 50%; transform: translateX(-50%);
                width: min(520px, calc(100vw - 32px));
                background: #FFF3E0; color: #111;
                border: 1px solid rgba(230, 81, 0, 0.35);
                border-radius: 18px;
                padding: 14px 18px;
                box-shadow: 0 12px 32px rgba(230, 81, 0, 0.18);
                font-size: 13px;
            }
            body.dark-mode .severe-weather-banner { background: #2A1B0A; color: #f4f4f5; }
            .severe-weather-banner__head {
                display: flex; align-items: center; gap: 12px;
                position: relative;
            }
            .severe-weather-banner__cloud { font-size: 22px; }
            .severe-weather-banner__drop {
                position: absolute; left: 18px; top: 22px;
                width: 4px; height: 10px; border-radius: 2px;
                background: #E65100;
                animation: severeWeatherDrop 900ms ease-in-out infinite;
            }
            @keyframes severeWeatherDrop {
                0%   { transform: translateY(0);  opacity: 1; }
                100% { transform: translateY(16px); opacity: 0; }
            }
            .severe-weather-banner__title { font-weight: 700; color: #E65100; font-size: 14px; }
            .severe-weather-banner__sub { color: inherit; opacity: 0.9; }
            .severe-weather-banner__numbers { margin-top: 10px; display: flex; flex-direction: column; gap: 4px; }
            .severe-weather-banner__numbers-header { font-size: 11px; font-weight: 600; opacity: 0.7; letter-spacing: 0.03em; }
            .severe-weather-banner__badge {
                display: inline-block; padding: 3px 10px;
                background: #E65100; color: #fff; font-weight: 700;
                border-radius: 6px; font-size: 12px; margin-right: 8px;
                min-width: 44px; text-align: center;
                text-decoration: none;
                transition: filter 120ms ease;
            }
            .severe-weather-banner__badge:hover { filter: brightness(1.08); }
            .severe-weather-banner__hint {
                margin-top: 6px; font-size: 11px; opacity: 0.7;
            }
            .severe-weather-banner__close {
                position: absolute; top: 8px; right: 10px;
                background: none; border: none; cursor: pointer;
                color: inherit; font-size: 22px; line-height: 1;
                opacity: 0.6;
            }
            .severe-weather-banner__close:hover { opacity: 1; }
        `;
        document.head.appendChild(severeWeatherStyle);

        const ariadneStyle = document.createElement("style");
        ariadneStyle.textContent = `
            .ariadne-launcher {
                position: fixed; z-index: 900;
                right: 16px; bottom: 16px;
                display: inline-flex; align-items: center; justify-content: center;
                padding: 0; border: none; cursor: pointer;
                width: 56px; height: 56px; border-radius: 50%;
                background: #fff;
                box-shadow: 0 6px 20px rgba(0,0,0,0.28),
                            inset 0 0 0 2px color-mix(in srgb, var(--sy-brand, #1466B8) 26%, transparent);
                transition: transform 0.15s ease, box-shadow 0.15s ease;
            }
            .ariadne-launcher:hover { transform: translateY(-1px); box-shadow: 0 10px 26px rgba(0,0,0,0.32), inset 0 0 0 2px color-mix(in srgb, var(--sy-brand, #1466B8) 40%, transparent); }
            /* The owl mark sits centred with breathing room inside the circle
               (not stretched edge-to-edge like the old squished full lockup). A
               permanent white disc keeps the navy owl legible in dark mode. */
            .ariadne-launcher__img { width: 38px; height: 38px; object-fit: contain; display: block; }
            /* Full Ariadne lockup at its true wide aspect - never squished. */
            .ariadne-panel__lockup { height: 24px; width: auto; display: block; }
            .ariadne-panel {
                position: fixed; z-index: 950;
                right: 16px; bottom: 16px;
                width: min(360px, calc(100vw - 32px));
                max-height: min(560px, calc(100vh - 32px));
                display: flex; flex-direction: column;
                background: #fff; color: #111;
                border-radius: 20px;
                box-shadow: 0 12px 40px rgba(0,0,0,0.2);
                overflow: hidden;
                transition: transform 180ms ease, opacity 180ms ease;
            }
            body.dark-mode .ariadne-panel { background: #1a1a1e; color: #f4f4f5; }
            .ariadne-panel--hidden { transform: translateY(12px); opacity: 0; pointer-events: none; }
            .ariadne-panel__head {
                display: flex; align-items: center; justify-content: space-between;
                padding: 12px 16px; border-bottom: 1px solid rgba(0,0,0,0.08);
            }
            body.dark-mode .ariadne-panel__head { border-bottom-color: rgba(255,255,255,0.08); }
            .ariadne-panel__title { display: inline-flex; align-items: center; gap: 8px; font-weight: 600; }
            .ariadne-panel__messages {
                flex: 1 1 auto; overflow-y: auto;
                padding: 12px 16px; display: flex; flex-direction: column; gap: 8px;
            }
            .ariadne-msg {
                max-width: 85%; padding: 8px 12px; border-radius: 14px;
                font-size: 14px; line-height: 1.35;
            }
            .ariadne-msg--assistant { align-self: flex-start; background: rgba(0,114,206,0.10); }
            body.dark-mode .ariadne-msg--assistant { background: rgba(120,170,255,0.15); }
            .ariadne-msg--user { align-self: flex-end; background: #0072CE; color: #fff; }
            .ariadne-panel__composer {
                display: flex; gap: 8px; padding: 12px 16px;
                border-top: 1px solid rgba(0,0,0,0.08);
            }
            body.dark-mode .ariadne-panel__composer { border-top-color: rgba(255,255,255,0.08); }
            .ariadne-panel__input {
                flex: 1 1 auto; padding: 10px 14px;
                border: 1px solid rgba(0,0,0,0.15); border-radius: 999px;
                background: transparent; color: inherit; font: inherit;
                outline: none;
            }
            body.dark-mode .ariadne-panel__input { border-color: rgba(255,255,255,0.15); }
            .ariadne-panel__input:focus { border-color: #0072CE; }
            .ariadne-panel__send { padding: 8px 14px; }
            /* On wide desktop the left and right columns are already
               taken by the Live-trains / Nearby-stations and STASY /
               OASA cards. Float Ariadne over the map, just past the
               left sidebar's right edge (280px sidebar + 16px gutter +
               16px buffer = 312px), and constrain width so the panel
               never runs into the right-hand info column. Mobile keeps
               the default bottom-right anchor. */
            @media (min-width: 721px) {
                .ariadne-launcher {
                    right: auto; left: 312px; bottom: 16px;
                }
                .ariadne-panel {
                    right: auto; left: 312px; bottom: 16px;
                    width: min(360px, calc(100vw - 640px));
                    max-height: min(440px, calc(100vh - 120px));
                }
            }
        `;
        document.head.appendChild(ariadneStyle);

        const launcher = document.getElementById("ariadneLauncher");
        const panel = document.getElementById("ariadnePanel");
        const closeBtn = document.getElementById("ariadneClose");
        const brainBtn = document.getElementById("ariadneBrain");
        const messages = document.getElementById("ariadneMessages");
        const form = document.getElementById("ariadneForm");
        const input = document.getElementById("ariadneInput");

        // On-demand "clever" brain. The ~1.1 GB model is never bundled and never
        // auto-downloaded; the user opts in here. Once downloaded the browser
        // caches it (OPFS), so later visits are instant and offline. Until then,
        // and if the user never opts in, the deterministic rule parser answers.
        if (brainBtn) {
            const llm = window.AriadneLLM;
            if (!llm) {
                brainBtn.style.display = "none";
            } else {
                // A thin download-progress bar under the panel header, shown only
                // while the ~1.1 GB model is downloading.
                const progress = document.createElement("div");
                progress.className = "ariadne-progress";
                progress.innerHTML =
                    '<div class="ariadne-progress__label"></div>' +
                    '<div class="ariadne-progress__track"><div class="ariadne-progress__fill"></div></div>';
                progress.style.display = "none";
                (document.getElementById("ariadnePanel") || document.body).insertBefore(
                    progress, document.getElementById("ariadneMessages"));
                const pFill = progress.querySelector(".ariadne-progress__fill");
                const pLabel = progress.querySelector(".ariadne-progress__label");

                const pbStyle = document.createElement("style");
                pbStyle.textContent = `
                    .ariadne-progress { padding: 8px 14px 4px; }
                    .ariadne-progress__label { font-size: 12px; opacity: 0.75; margin-bottom: 5px; }
                    .ariadne-progress__track { height: 6px; border-radius: 999px; background: rgba(0,0,0,0.12); overflow: hidden; }
                    body.dark-mode .ariadne-progress__track { background: rgba(255,255,255,0.16); }
                    .ariadne-progress__fill { height: 100%; width: 0%; border-radius: 999px; background: #0072CE; transition: width 300ms ease; }
                `;
                document.head.appendChild(pbStyle);

                const brainIcon = '<svg class="ic" aria-hidden="true"><use href="#ic-brain"/></svg>';
                const paint = () => {
                    const s = llm.status();
                    const pct = Math.round((llm.progress ? llm.progress() : 0) * 100);
                    // Keep the line icon; show the download percentage as text only
                    // while loading. A state class tints it (ready/error) without a
                    // second emoji. Never write a bare glyph over the <svg>.
                    brainBtn.classList.remove("control-button--ready", "control-button--error");
                    if (s === "loading") {
                        brainBtn.textContent = pct + "%";
                    } else {
                        brainBtn.innerHTML = brainIcon;
                        if (s === "ready") brainBtn.classList.add("control-button--ready");
                        else if (s === "error") brainBtn.classList.add("control-button--error");
                    }
                    brainBtn.title = s === "ready"
                        ? "Smarter answers are on (on-device brain ready)"
                        : s === "loading"
                        ? ("Downloading Ariadne's brain… " + pct + "% (~1.1 GB, one time)")
                        : s === "error"
                        ? "Download failed. Tap to retry. Rule parser still answers."
                        : "Smarter answers: download Ariadne's on-device brain (~1.1 GB, one time)";
                    if (s === "loading") {
                        progress.style.display = "block";
                        pFill.style.width = pct + "%";
                        pLabel.textContent = "Downloading Ariadne's brain… " + pct + "%";
                    } else if (s === "error") {
                        progress.style.display = "block";
                        pLabel.textContent = "Download failed. Tap 🧠 to retry.";
                        pFill.style.width = "0%";
                    } else {
                        progress.style.display = "none";
                    }
                };
                paint();
                brainBtn.addEventListener("click", () => {
                    const s = llm.status();
                    if (s === "idle" || s === "error") {
                        llm.download();
                        const poll = setInterval(() => {
                            paint();
                            if (llm.status() === "ready" || llm.status() === "error") {
                                if (llm.status() === "ready") setTimeout(() => { progress.style.display = "none"; }, 800);
                                clearInterval(poll);
                            }
                        }, 400);
                    }
                    paint();
                });
            }
        }

        function appendMessage(text, from) {
            const el = document.createElement("div");
            el.className = "ariadne-msg " + (from === "user" ? "ariadne-msg--user" : "ariadne-msg--assistant");
            el.textContent = text;
            messages.appendChild(el);
            messages.scrollTop = messages.scrollHeight;
            return el;
        }

        function openPanel() {
            panel.classList.remove("ariadne-panel--hidden");
            panel.setAttribute("aria-hidden", "false");
            launcher.style.display = "none";
            if (!messages.dataset.greeted) {
                appendMessage(t("ariadne_greeting"), "assistant");
                messages.dataset.greeted = "1";
            }
            setTimeout(() => input.focus(), 100);
        }

        function closePanel() {
            panel.classList.add("ariadne-panel--hidden");
            panel.setAttribute("aria-hidden", "true");
            launcher.style.display = "";
        }

        function stationName(id) {
            const s = stationMap.get(id);
            if (!s) return id;
            return currentLang === "el" && s.nameEl ? s.nameEl : s.name;
        }

        function lineById(id) {
            return lines.find((l) => l.id === id) || null;
        }

        // Builds the one-line "Next from X: Line 3 now, Line 3 in 2 min, ..."
        // reply. Uses the same projector the station sheet uses, so the chat
        // and the panel never disagree. Returns null on no station.
        async function departuresSummary(station) {
            if (!station) return null;
            const apiDepartures = await fetchApiDepartures(station);
            const list = (apiDepartures && apiDepartures.length)
                ? apiDepartures
                : buildStationDepartures(station);
            const name = stationName(station.id);
            if (!list || !list.length) {
                return t("ariadne_none_now", { station: name });
            }
            const top = list.slice(0, 3).map((dep) => {
                const lineLabel = (dep.line && dep.line.name) || (dep.line && dep.line.id) || "";
                const when = formatMinutesAway(dep.minutesAway);
                const clock = dep.time ? ` (${dep.time})` : "";
                return `${lineLabel} ${when}${clock}`;
            });
            return `${t("ariadne_next_from", { station: name })} ${top.join(", ")}.`;
        }

        // WMO weather code -> localized short label. Used by the weather
        // intent handler above.
        function weatherCodeLabel(code, lang) {
            const bucket = code === 0 ? "clear"
                : (code === 1 || code === 2) ? "partly-cloudy"
                : code === 3 ? "cloudy"
                : (code === 45 || code === 48) ? "fog"
                : (code >= 51 && code <= 57) ? "drizzle"
                : (code >= 61 && code <= 67) ? "rain"
                : (code >= 71 && code <= 77) ? "snow"
                : (code >= 80 && code <= 86) ? "showers"
                : (code === 95 || code === 96 || code === 99) ? "thunderstorm"
                : "unknown";
            const table = {
                el: { "clear": "καθαρός", "partly-cloudy": "μερική συννεφιά", "cloudy": "συννεφιασμένος", "fog": "ομίχλη", "drizzle": "ψιχάλα", "rain": "βροχή", "snow": "χιόνι", "showers": "μπόρες", "thunderstorm": "καταιγίδα", "unknown": "άγνωστη" },
                sq: { "clear": "kthjellët", "partly-cloudy": "pjesërisht i vranët", "cloudy": "i vranët", "fog": "mjegull", "drizzle": "shi i lehtë", "rain": "shi", "snow": "borë", "showers": "reshje", "thunderstorm": "stuhi", "unknown": "e panjohur" },
                en: { "clear": "clear", "partly-cloudy": "partly cloudy", "cloudy": "cloudy", "fog": "foggy", "drizzle": "drizzling", "rain": "raining", "snow": "snowing", "showers": "showery", "thunderstorm": "thunderstorm", "unknown": "unknown" },
            };
            return (table[lang] || table.en)[bucket];
        }

        // Easter egg: random cat joke in the current language. Called from
        // respond() when parser returns kind: "easterEggLiepur".
        function catJoke(lang) {
            const jokes = {
                el: [
                    "Γιατί οι γάτες δεν παίζουν πόκερ στη ζούγκλα; Έχει πολλά τσιτάχ.",
                    "Πώς λέγεται μια στοίβα γατάκια; Μιαοβούνο.",
                    "Τι κάνει ένας γάτος στον υπολογιστή; Προσέχει το ποντίκι.",
                    "Γιατί ο γάτος πήγε στο νοσοκομείο; Είχε πυρετό αγέλας.",
                    "Πώς τελειώνει η μάχη δύο γάτων; Με ένα σφύριγμα και ένα μιάου.",
                ],
                sq: [
                    "Pse macet nuk luajnë poker në xhungël? Sepse ka shumë çita.",
                    "Si e quajnë një grumbull macesh të vogla? Një mjaumal.",
                    "Pse ishte macja ulur mbi kompjuter? Për të vëzhguar miun.",
                    "Cila është ëmbëlsira e preferuar e maces? Muslet me çokollatë.",
                    "Si e mbyllin macet një grindje? Me një fshirje dhe një mjau.",
                ],
                en: [
                    "Why don't cats play poker in the jungle? Too many cheetahs.",
                    "What do you call a pile of kittens? A meowntain.",
                    "Why was the cat sitting on the computer? To keep an eye on the mouse.",
                    "What's a cat's favourite dessert? Chocolate mousse.",
                    "How do two cats end a fight? They hiss and make up.",
                ],
            };
            const pool = jokes[lang] || jokes.en;
            return pool[Math.floor(Math.random() * pool.length)];
        }

        // Web "cleverer than dummy" tier: append an explanatory RAG chunk to an
        // answer. The deterministic engine still produces every fact; retrieval
        // only adds context (line overview, fare rules, capabilities). Gated to
        // English because the pack's chunks are English and the EL/SQ answers are
        // already fully localized, so we never mix languages.
        function ragEnrich(baseText, opts) {
            if (currentLang !== "en" || !window.SyrmosAriadneRAG) return baseText;
            try {
                let chunk = null;
                if (opts.id) chunk = window.SyrmosAriadneRAG.byId(opts.id);
                if (!chunk && opts.query) {
                    const hits = window.SyrmosAriadneRAG.retrieve(opts.query, { types: opts.types || null, k: 1 });
                    chunk = hits[0] || null;
                }
                if (chunk && chunk.text) return baseText + "\n\n" + chunk.text;
            } catch (e) { /* retrieval is best-effort; never break an answer */ }
            return baseText;
        }

        function respond(intent) {
            switch (intent.kind) {
                case "help":
                    return { text: ragEnrich(window.SyrmosAriadne.help(currentLang), { id: "capabilities_current" }) };
                case "outOfScope":
                    return { text: window.SyrmosAriadne.outOfScope(currentLang) };
                case "needsClarification":
                    return { text: window.SyrmosAriadne.clarify(intent.missing, currentLang) };
                case "easterEggLiepur":
                    return { text: catJoke(currentLang) };
                case "planByArrival": {
                    // Backward plan grounded in the live schedule: we look
                    // up the departures at the boarding station node and
                    // pick the LATEST one whose HH:MM is at or before the
                    // target-minus-rough-duration boundary. When the
                    // schedule doesn't have anything to offer we fall
                    // back to the previous "subtract duration" estimate
                    // so the answer is still useful offline.
                    const from = intent.from ? stationMap.get(intent.from) : null;
                    const to = intent.to ? stationMap.get(intent.to) : null;
                    if (!from || !to) return { text: window.SyrmosAriadne.outOfScope(currentLang) };
                    const now = new Date();
                    const nowMin = now.getHours() * 60 + now.getMinutes();
                    const roughDuration = 25;
                    const targetMin = intent.arriveByMinutes != null
                        ? intent.arriveByMinutes
                        : (intent.inMinutesFromNow != null ? nowMin + intent.inMinutesFromNow : null);
                    if (targetMin == null) return { text: window.SyrmosAriadne.outOfScope(currentLang) };
                    const effective = (targetMin < nowMin && intent.arriveByMinutes != null)
                        ? targetMin + 24 * 60 : targetMin;
                    const pad = (n) => String(n).padStart(2, "0");
                    const clockOf = (mins) => `${pad(Math.floor((mins / 60) % 24))}:${pad(mins % 60)}`;
                    const fromName = stationName(from.id);
                    const toName = stationName(to.id);
                    const arriveLabel = clockOf(effective % (24 * 60));

                    // Boarding node (raw id -> clustered node).
                    const boardNodeId = rawIdToNodeId.get(from.id) || nodeIdFor(from.id);
                    const boardNode = boardNodeId ? stationNodeMap.get(boardNodeId) : null;

                    let leaveByExact = null;
                    let leaveLegLine = null;
                    if (boardNode && typeof buildStationDepartures === "function") {
                        try {
                            const deps = buildStationDepartures(boardNode) || [];
                            const boardBy = effective - roughDuration;
                            // Pick the latest scheduled departure at the
                            // boarding station whose time is <= boardBy.
                            let best = null;
                            for (const d of deps) {
                                const t = d.time || "";
                                const m = /^(\d{1,2}):(\d{2})$/.exec(t);
                                if (!m) continue;
                                const mins = parseInt(m[1], 10) * 60 + parseInt(m[2], 10);
                                if (mins <= boardBy && (best == null || mins > best.mins)) {
                                    best = { mins: mins, dep: d };
                                }
                            }
                            if (best) {
                                leaveByExact = clockOf(best.mins);
                                leaveLegLine = best.dep.line?.id || (best.dep.line || {}).id || "";
                            }
                        } catch (_) { /* fall through to estimate */ }
                    }

                    const leaveMin = leaveByExact
                        ? parseInt(leaveByExact.slice(0, 2), 10) * 60 + parseInt(leaveByExact.slice(3, 5), 10)
                        : (effective - roughDuration);
                    const leaveLabel = leaveByExact || clockOf(((leaveMin % (24 * 60)) + 24 * 60) % (24 * 60));
                    const slack = leaveMin - nowMin;
                    const linePart = leaveLegLine ? ` ${leaveLegLine}` : "";
                    let msg;
                    if (slack < 0) {
                        msg = currentLang === "el"
                            ? `Δύσκολο. Για να είσαι στο ${toName} στις ${arriveLabel} έπρεπε να έχεις ξεκινήσει πριν ${-slack} λεπτά.`
                            : currentLang === "sq"
                            ? `E vështirë. Për të qenë në ${toName} në ${arriveLabel} duhej të kishe nisur ${-slack} minuta më parë.`
                            : `Cutting it close. To make ${toName} by ${arriveLabel} you'd have needed to leave ${-slack} min ago.`;
                    } else if (slack < 5) {
                        msg = currentLang === "el"
                            ? `Στριμωγμένα. Πάρε το${linePart} στις ${leaveLabel} από ${fromName} για να προλάβεις στο ${toName} στις ${arriveLabel}.`
                            : currentLang === "sq"
                            ? `Ngushtë. Merr${linePart} në ${leaveLabel} nga ${fromName} për të arritur në ${toName} në ${arriveLabel}.`
                            : `Tight. Board the ${leaveLabel}${linePart} at ${fromName} to make ${toName} by ${arriveLabel}.`;
                    } else if (slack > roughDuration + 45) {
                        msg = currentLang === "el"
                            ? `Έχεις άπλα. Το επόμενο${linePart} στις ${leaveLabel} από ${fromName} σε φτάνει στο ${toName} στις ${arriveLabel}.`
                            : currentLang === "sq"
                            ? `Ke kohë. ${linePart ? "Merri" + linePart : "Nis"} në ${leaveLabel} nga ${fromName} dhe do të jesh në ${toName} në ${arriveLabel}.`
                            : `You have time. Board the ${leaveLabel}${linePart} at ${fromName} to reach ${toName} by ${arriveLabel}.`;
                    } else {
                        msg = leaveByExact
                            ? (currentLang === "el"
                                ? `Πάρε το${linePart} στις ${leaveLabel} από ${fromName} και θα είσαι στο ${toName} στις ${arriveLabel}. ${slack} λεπτά περιθώριο.`
                                : currentLang === "sq"
                                ? `Merr${linePart} në ${leaveLabel} nga ${fromName} dhe do të jesh në ${toName} në ${arriveLabel}. ${slack} minuta hapësirë.`
                                : `Board the ${leaveLabel}${linePart} at ${fromName} and you'll be at ${toName} by ${arriveLabel}. ${slack} min to spare.`)
                            : (currentLang === "el"
                                ? `Ξεκίνα από ${fromName} έως ${leaveLabel} και θα είσαι στο ${toName} στις ${arriveLabel}. ${slack} λεπτά περιθώριο.`
                                : currentLang === "sq"
                                ? `Nis nga ${fromName} deri në ${leaveLabel} dhe do të jesh në ${toName} në ${arriveLabel}. ${slack} minuta hapësirë.`
                                : `Leave ${fromName} by ${leaveLabel} and you'll be at ${toName} by ${arriveLabel}. ${slack} min to spare.`);
                    }
                    return { text: msg };
                }
                case "weatherAt": {
                    const anchor = intent.stationId ? stationMap.get(intent.stationId) : null;
                    const lat = anchor ? anchor.latitude : 37.9838;
                    const lng = anchor ? anchor.longitude : 23.7275;
                    const name = anchor
                        ? (currentLang === "el" ? (anchor.name_el || anchor.nameEl || anchor.name) : anchor.name)
                        : (currentLang === "el" ? "Αθήνα" : currentLang === "sq" ? "Athina" : "Athens");
                    return {
                        text: currentLang === "el" ? `Αναζήτηση καιρού για ${name}…`
                            : currentLang === "sq" ? `Po kërkoj motin për ${name}…`
                            : `Fetching weather for ${name}…`,
                        act: async () => {
                            try {
                                const url = `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lng}&current=temperature_2m,apparent_temperature,weather_code&timezone=auto`;
                                const r = await fetch(url);
                                const data = await r.json();
                                const c = data.current || {};
                                const temp = Math.round(c.temperature_2m);
                                const feels = Math.round(c.apparent_temperature);
                                const cond = weatherCodeLabel(c.weather_code, currentLang);
                                const reply = currentLang === "el"
                                    ? `${name} τώρα: ${temp}°C, ${cond}. Αίσθηση ${feels}°C.`
                                    : currentLang === "sq"
                                    ? `${name} tani: ${temp}°C, ${cond}. Ndihet si ${feels}°C.`
                                    : `${name} right now: ${temp}°C, ${cond}. Feels like ${feels}°C.`;
                                appendMessage(reply, "assistant");
                            } catch (_) {
                                appendMessage(
                                    currentLang === "el" ? "Δεν έχω δεδομένα καιρού. Δοκίμασε ξανά."
                                    : currentLang === "sq" ? "S'kam të dhëna moti. Provo përsëri."
                                    : "I don't have weather data. Try again.",
                                    "assistant"
                                );
                            }
                        },
                    };
                }
                case "departures":
                    if (intent.stationId) {
                        return {
                            text: t("ariadne_looking_up", { station: stationName(intent.stationId) }),
                            departuresFor: stationMap.get(intent.stationId) || null,
                            act: () => { openStation(intent.stationId); if (window.innerWidth < 721) closePanel(); },
                        };
                    }
                    return { text: t("ariadne_no_station") };
                case "lastTrain":
                    if (intent.stationId) {
                        return {
                            text: t("ariadne_looking_up", { station: stationName(intent.stationId) }),
                            act: () => { openStation(intent.stationId); if (window.innerWidth < 721) closePanel(); },
                        };
                    }
                    return { text: t("ariadne_no_station") };
                case "firstTrain":
                    if (intent.stationId || intent.lineId) {
                        const fid = intent.stationId || (lineById(intent.lineId) && (lineById(intent.lineId).terminalA || lineById(intent.lineId).terminal_a));
                        const openId = intent.stationId;
                        return {
                            text: t("ariadne_looking_up", { station: intent.stationId ? stationName(intent.stationId) : intent.lineId }),
                            act: () => { if (openId) { openStation(openId); if (window.innerWidth < 721) closePanel(); } },
                        };
                    }
                    return { text: t("ariadne_no_station") };
                case "stationAccessibility": {
                    const st = intent.stationId ? stationMap.get(intent.stationId) : null;
                    if (!st) return { text: t("ariadne_no_station") };
                    const nm = stationName(st.id);
                    const acc = st.accessibility !== false;   // bundle default is accessible
                    const text = acc
                        ? (currentLang === "el" ? `Ο ${nm} είναι προσβάσιμος για ΑμεΑ (ασανσέρ / ισόπεδη πρόσβαση).`
                            : currentLang === "sq" ? `${nm} është i aksesueshëm pa shkallë (ashensor / qasje e sheshtë).`
                            : `${nm} is step-free accessible (lift / level access).`)
                        : (currentLang === "el" ? `Ο ${nm} δεν είναι σημειωμένος ως προσβάσιμος ΑμεΑ. Ίσως έχει μόνο σκάλες.`
                            : currentLang === "sq" ? `${nm} nuk shënohet si i aksesueshëm pa shkallë. Mund të ketë vetëm shkallë.`
                            : `${nm} is not marked step-free. Check for stairs-only access before you go.`);
                    return { text: text };
                }
                case "reverseTrip": {
                    const r = aSession.lastRoute;
                    if (!r) {
                        return {
                            text: currentLang === "el" ? "Πες μου πρώτα μια διαδρομή, μετά τη γυρίζω για την επιστροφή."
                                : currentLang === "sq" ? "Më trego fillimisht një udhëtim, pastaj e kthej për rrugën e kthimit."
                                : "Tell me a trip first, then I can flip it for the way back.",
                        };
                    }
                    return respond({ kind: "plan", from: r.to, to: r.from, lowExposure: false });
                }
                case "whichLines": {
                    const st = intent.stationId ? stationMap.get(intent.stationId) : null;
                    if (!st) return { text: t("ariadne_no_station") };
                    const ids = [...new Set((st.line_ids || st.lineIds || []).map((id) => String(id).split("_")[0]))];
                    const nm = stationName(st.id);
                    if (!ids.length) {
                        return { text: currentLang === "el" ? `Δεν έχω γραμμές για ${nm}.`
                            : currentLang === "sq" ? `Nuk kam linja për ${nm}.` : `I don't have any lines listed for ${nm}.` };
                    }
                    const list = ids.join(", ");
                    return { text: currentLang === "el" ? `Ο ${nm} εξυπηρετείται από: ${list}.`
                        : currentLang === "sq" ? `${nm} shërbehet nga: ${list}.` : `${nm} is served by: ${list}.` };
                }
                case "stopsBetween": {
                    // The web has no JS stop-counting planner, so delegate to the
                    // route directions like a trip. Native computes the exact count.
                    if (intent.from && intent.to) return respond({ kind: "plan", from: intent.from, to: intent.to, lowExposure: false });
                    return { text: t("ariadne_no_station") };
                }
                case "openMap":
                    if (intent.stationId) {
                        return {
                            text: t("ariadne_open_map", { station: stationName(intent.stationId) }),
                            act: () => { openStation(intent.stationId); if (window.innerWidth < 721) closePanel(); },
                        };
                    }
                    return { text: t("ariadne_no_station") };
                case "alerts": {
                    return {
                        text: t("ariadne_open_alerts"),
                        act: () => window.open("https://www.stasy.gr/en/news/", "_blank", "noopener"),
                    };
                }
                case "plan": {
                    const from = stationMap.get(intent.from);
                    const to = stationMap.get(intent.to);
                    if (!from || !to) return { text: t("ariadne_no_station") };
                    const url = `https://www.google.com/maps/dir/?api=1&origin=${from.latitude},${from.longitude}&destination=${to.latitude},${to.longitude}&travelmode=transit`;
                    return {
                        text: t("ariadne_open_route", {
                            from: stationName(from.id),
                            to: stationName(to.id),
                        }),
                        act: () => window.open(url, "_blank", "noopener"),
                    };
                }
                case "travelTime": {
                    const to = intent.to ? stationMap.get(intent.to) : null;
                    if (!to) return { text: t("ariadne_no_station") };
                    const from = intent.from ? stationMap.get(intent.from) : null;
                    // Explicit origin: open transit directions station → station.
                    if (from) {
                        const url = `https://www.google.com/maps/dir/?api=1&origin=${from.latitude},${from.longitude}&destination=${to.latitude},${to.longitude}&travelmode=transit`;
                        return {
                            text: t("ariadne_open_route", { from: stationName(from.id), to: stationName(to.id) }),
                            act: () => window.open(url, "_blank", "noopener"),
                        };
                    }
                    // No named origin: use the browser location, else ask.
                    return {
                        text: t("ariadne_eta_locating", { station: stationName(to.id) }),
                        act: () => {
                            if (!navigator.geolocation) {
                                appendMessage(t("ariadne_eta_ask_origin"), "assistant");
                                return;
                            }
                            navigator.geolocation.getCurrentPosition(
                                (pos) => {
                                    const url = `https://www.google.com/maps/dir/?api=1&origin=${pos.coords.latitude},${pos.coords.longitude}&destination=${to.latitude},${to.longitude}&travelmode=transit`;
                                    window.open(url, "_blank", "noopener");
                                },
                                () => appendMessage(t("ariadne_eta_ask_origin"), "assistant"),
                            );
                        },
                    };
                }
                case "explainLine": {
                    const line = lineById(intent.lineId);
                    if (!line) return { text: t("ariadne_no_station") };
                    const base = t("ariadne_line", {
                        id: line.id,
                        a: line.terminalA || line.terminal_a || "",
                        b: line.terminalB || line.terminal_b || "",
                    });
                    return { text: ragEnrich(base, { id: "line_" + String(intent.lineId).split("_")[0], query: intent.lineId + " line overview", types: ["line"] }) };
                }
                case "explainFare":
                    return { text: ragEnrich(t("ariadne_fare"), { query: "ticket validation fare price airport points of supply", types: ["fare_info", "fare"] }) };
                case "find":
                    return { text: t("ariadne_no_station") };
                default:
                    return { text: window.SyrmosAriadne.outOfScope(currentLang) };
            }
        }

        // Conversation state: when Ariadne returns NeedsClarification we
        // stash the pending intent so the next user turn can fill the
        // missing slot (e.g. "How do I go to Nikaia" -> "From which
        // station?" -> "Syntagma" now resolves as the trip's origin
        // rather than as a fresh Syntagma-departures query). The state
        // clears once the user asks anything unrelated.
        let pendingIntent = null;
        let pendingMissing = null;

        // Durable conversation memory (parity with the KMP / iOS session):
        // remembers the current station and the last full route so follow-ups
        // like "and back?" and "what about tomorrow?" work without repeating.
        const aSession = { currentStation: null, lastRoute: null, lastIntent: null };

        function updateSession(intent) {
            if (!intent) return;
            switch (intent.kind) {
                case "plan":
                    if (intent.from) aSession.currentStation = intent.from;
                    if (intent.from && intent.to) aSession.lastRoute = { from: intent.from, to: intent.to };
                    break;
                case "departures":
                case "firstTrain":
                case "whichLines":
                    if (intent.stationId) aSession.currentStation = intent.stationId;
                    break;
                case "stopsBetween":
                    if (intent.from) aSession.currentStation = intent.from;
                    if (intent.from && intent.to) aSession.lastRoute = { from: intent.from, to: intent.to };
                    break;
                case "reverseTrip":
                    if (aSession.lastRoute) {
                        const r = aSession.lastRoute;
                        aSession.lastRoute = { from: r.to, to: r.from };
                        aSession.currentStation = r.to;
                    }
                    break;
                case "needsClarification":
                    return;
            }
            aSession.lastIntent = intent;
        }

        // Bare day-change follow-up: "what about tomorrow?", "and the weekend?".
        // The parser can't classify these alone; if the last answered turn was a
        // departures query for a known station, re-issue it for the new day.
        function applyDayFollowUp(rawInput, intent) {
            if (!intent || intent.kind !== "outOfScope") return intent;
            const day = window.SyrmosAriadne.dayOf ? window.SyrmosAriadne.dayOf(rawInput) : "TODAY";
            if (day === "TODAY") return intent;
            const last = aSession.lastIntent;
            if (last && last.kind === "departures" && (last.stationId || last.lineId)) {
                return Object.assign({}, last, { day: day });
            }
            return intent;
        }

        function mergePending(rawInput) {
            // Try to fill the missing slot with a station matched from
            // the bare input. Requires SyrmosAriadne.parse to expose
            // parseStation, which we approximate here by delegating a
            // whole parse and picking a station if one shows up.
            const asStationOnly = window.SyrmosAriadne.parse(rawInput);
            let stationId = null;
            // A bare station name usually resolves to a Departures intent
            // with a stationId slot. Pull that out.
            if (asStationOnly && asStationOnly.kind === "departures" && asStationOnly.stationId) {
                stationId = asStationOnly.stationId;
            } else if (asStationOnly && asStationOnly.kind === "needsClarification" &&
                asStationOnly.base && asStationOnly.base.stationId) {
                stationId = asStationOnly.base.stationId;
            }
            if (!stationId) return null;

            if (pendingIntent.kind === "plan") {
                const patched = Object.assign({}, pendingIntent);
                if (pendingMissing === "ORIGIN_STATION") patched.from = stationId;
                else if (pendingMissing === "DESTINATION_STATION") patched.to = stationId;
                else return null;
                if (!patched.from || !patched.to) {
                    return {
                        kind: "needsClarification",
                        base: patched,
                        missing: !patched.from ? "ORIGIN_STATION" : "DESTINATION_STATION",
                    };
                }
                return patched;
            }
            if (pendingIntent.kind === "lastTrain" || pendingIntent.kind === "departures" ||
                pendingIntent.kind === "firstTrain" || pendingIntent.kind === "stationAccessibility" ||
                pendingIntent.kind === "whichLines") {
                const patched = Object.assign({}, pendingIntent, { stationId: stationId });
                return patched;
            }
            if (pendingIntent.kind === "stopsBetween") {
                const patched = Object.assign({}, pendingIntent);
                if (pendingMissing === "ORIGIN_STATION") patched.from = stationId;
                else if (pendingMissing === "DESTINATION_STATION") patched.to = stationId;
                else return null;
                if (!patched.from || !patched.to) {
                    return { kind: "needsClarification", base: patched, missing: !patched.from ? "ORIGIN_STATION" : "DESTINATION_STATION" };
                }
                return patched;
            }
            if (pendingIntent.kind === "toggleFavorite") {
                return Object.assign({}, pendingIntent, { stationId: stationId });
            }
            if (pendingIntent.kind === "travelTime") {
                // The only travelTime clarification is a missing destination;
                // the origin defaults to the user's location.
                return Object.assign({}, pendingIntent, { to: stationId });
            }
            return null;
        }

        launcher.addEventListener("click", openPanel);
        closeBtn.addEventListener("click", closePanel);

        // T6 bridge: let the unified search box hand a query straight to Ariadne.
        // Open the panel, drop the text into the composer and submit it through
        // the exact same path a typed question takes - one entry point, no
        // second box.
        window.__syrmosAskAriadne = function (query) {
            const q = (query || "").trim();
            if (!q) return;
            openPanel();
            input.value = q;
            if (typeof form.requestSubmit === "function") {
                form.requestSubmit();
            } else {
                form.dispatchEvent(new Event("submit", { cancelable: true }));
            }
        };

        form.addEventListener("submit", (ev) => {
            ev.preventDefault();
            const value = (input.value || "").trim();
            if (!value) return;
            appendMessage(value, "user");
            input.value = "";

            let intent;
            if (pendingIntent && pendingMissing) {
                const merged = mergePending(value);
                if (merged && merged.kind !== "needsClarification") {
                    intent = merged;
                    pendingIntent = null;
                    pendingMissing = null;
                } else if (merged && merged.kind === "needsClarification") {
                    intent = merged;
                    pendingIntent = merged.base;
                    pendingMissing = merged.missing;
                } else {
                    intent = window.SyrmosAriadne.parse(value);
                    pendingIntent = null;
                    pendingMissing = null;
                }
            } else {
                intent = window.SyrmosAriadne.parse(value);
            }

            // Bare "what about tomorrow?" re-runs the last departures query.
            intent = applyDayFollowUp(value, intent);

            if (intent.kind === "needsClarification") {
                pendingIntent = intent.base;
                pendingMissing = intent.missing;
            } else {
                pendingIntent = null;
                pendingMissing = null;
            }

            const deliver = (finalIntent) => {
                updateSession(finalIntent);
                // Recovery: a genuine dead-end suggests the closest station or a
                // warm capability nudge instead of flatly declining. Mirrors KMP/iOS.
                if (finalIntent.kind === "outOfScope") {
                    const sid = window.SyrmosAriadne.suggestStationId(value);
                    const sname = sid ? stationName(sid) : null;
                    const msg = sname
                        ? t("ariadne_did_you_mean").replace(/\{station\}/g, sname)
                        : t("ariadne_try_asking");
                    appendMessage(msg, "assistant");
                    return;
                }
                const reply = respond(finalIntent);
                appendMessage(reply.text, "assistant");
                // For a departures answer, follow the "Looking up X..." line with
                // the actual next trains in a second bubble once the projector
                // resolves, so the chat itself carries the times.
                if (reply.departuresFor) {
                    departuresSummary(reply.departuresFor).then((summary) => {
                        if (summary) appendMessage(summary, "assistant");
                    });
                }
                if (reply.act) setTimeout(reply.act, 400);
            };

            // Clever tier: when the deterministic parser can't place the message
            // and the on-device model is downloaded + ready, let the LLM re-read
            // it, then ground its guess back through the SAME parser (it only
            // picks an intent + quotes a station; parse() does the grounding).
            const llm = window.AriadneLLM;
            const A = window.SyrmosAriadne;
            if (intent.kind === "outOfScope" && llm && llm.status && llm.status() === "ready" && A.buildClassificationPrompt) {
                const thinking = appendMessage("…", "assistant");
                llm.classify(A.buildClassificationPrompt(value)).then((json) => {
                    if (thinking) thinking.remove();
                    const q = A.cleverQueryFromJson(json);
                    const clever = q ? A.parse(q) : null;
                    if (clever && clever.kind !== "outOfScope") {
                        if (clever.kind === "needsClarification") {
                            pendingIntent = clever.base;
                            pendingMissing = clever.missing;
                        }
                        deliver(clever);
                    } else {
                        deliver(intent);
                    }
                }).catch(() => { if (thinking) thinking.remove(); deliver(intent); });
            } else {
                deliver(intent);
            }
        });
    }

    // First-visit "what's new" card. Shows once per release (keyed in
    // localStorage), highlights the new features, and nudges the app install
    // with store links. Never blocks the map if anything is unavailable.
    function maybeShowWhatsNew() {
        const VERSION = "1.1.1";
        const KEY = "syrmos.whatsnew.seen";
        try {
            if (localStorage.getItem(KEY) === VERSION) return;
        } catch (_) { /* private mode: still show, just don't persist */ }

        const overlay = document.createElement("div");
        overlay.className = "whatsnew-overlay";
        const items = [t("whatsnew_i1"), t("whatsnew_i2"), t("whatsnew_i3"), t("whatsnew_i4")]
            .map((it) => `<li>${it}</li>`).join("");
        overlay.innerHTML = `
            <div class="whatsnew-card" role="dialog" aria-modal="true" aria-label="${t("whatsnew_title")}">
                <div class="whatsnew-owl">🦉</div>
                <h2 class="whatsnew-title">${t("whatsnew_title")}</h2>
                <ul class="whatsnew-list">${items}</ul>
                <div class="whatsnew-actions">
                    <a class="whatsnew-cta" href="https://apps.apple.com/app/id6777650671" target="_blank" rel="noopener">${t("whatsnew_get_app")}</a>
                    <button type="button" class="whatsnew-dismiss">${t("whatsnew_stay")}</button>
                </div>
                <div class="whatsnew-stores">
                    <a href="https://apps.apple.com/app/id6777650671" target="_blank" rel="noopener">App Store</a>
                    <span aria-hidden="true">·</span>
                    <a href="https://play.google.com/store/apps/details?id=com.syrmos.android" target="_blank" rel="noopener">Google Play</a>
                </div>
            </div>`;
        const close = () => {
            overlay.remove();
            try { localStorage.setItem(KEY, VERSION); } catch (_) {}
        };
        overlay.addEventListener("click", (e) => { if (e.target === overlay) close(); });
        overlay.querySelector(".whatsnew-dismiss").addEventListener("click", close);
        overlay.querySelector(".whatsnew-cta").addEventListener("click", () => {
            try { localStorage.setItem(KEY, VERSION); } catch (_) {}
        });
        document.body.appendChild(overlay);
    }

    // NOTE: the standalone whatsNewWeb() modal above is the single "what's new"
    // card. The older maybeShowWhatsNew() install-promo is intentionally not
    // invoked (it duplicated the card, and the header already has store links).
    void maybeShowWhatsNew;
})();
