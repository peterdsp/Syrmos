(async function () {
    const ATHENS_CENTER = [37.98, 23.73];
    const INITIAL_ZOOM = 12;
    const DIRECTION_OUTBOUND = "outbound";
    const DIRECTION_INBOUND = "inbound";

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
            interchange: "Interchange",
            accessible: "Accessible",
            airport: "Airport",
            train: "Train",
            live: "Live",
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
            ariadne_no_station: "I couldn't match that to an Athens station. Try Syntagma, Piraeus, Airport.",
            ariadne_open_map: "Opening {station} on the map.",
            ariadne_open_alerts: "Showing service alerts.",
            ariadne_open_route: "Opening directions from {from} to {to}.",
            ariadne_line: "Line {id} runs between {a} and {b}. Tap the line on the map to see all stations.",
            ariadne_fare: "Standard OASA single is €0.90 (Metro/Tram). Airport metro single is €9. See OASA tickets in the side panel for the full list.",
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
            interchange: "Ανταπόκριση",
            accessible: "Προσβάσιμος",
            airport: "Αεροδρόμιο",
            train: "Συρμός",
            live: "Ζωντανά",
            unknown: "άγνωστο",
            next: "επόμενος",
            reduced: "Μειωμένο",
            verify_on: "Επιβεβαίωση στο {op} ↗",
            ask_ariadne: "Ρώτα την Αριάδνη",
            ariadne_title: "Αριάδνη",
            ariadne_placeholder: "Ρώτα την Αριάδνη...",
            send: "Αποστολή",
            ariadne_greeting: "Γεια, είμαι η Αριάδνη. Ρώτα με για τις συγκοινωνίες της Αθήνας — επόμενες αναχωρήσεις, τελευταίο τρένο, διαδρομή Α σε Β, ειδοποιήσεις ή τιμές εισιτηρίων.",
            ariadne_looking_up: "Αναζήτηση για {station}...",
            ariadne_no_station: "Δεν αναγνώρισα σταθμό. Δοκίμασε Σύνταγμα, Πειραιά ή Αεροδρόμιο.",
            ariadne_open_map: "Άνοιγμα του {station} στον χάρτη.",
            ariadne_open_alerts: "Εμφάνιση ειδοποιήσεων.",
            ariadne_open_route: "Άνοιγμα διαδρομής από {from} προς {to}.",
            ariadne_line: "Η Γραμμή {id} συνδέει {a} και {b}. Πάτα τη γραμμή στον χάρτη για όλους τους σταθμούς.",
            ariadne_fare: "Το βασικό εισιτήριο OASA είναι €0,90 (Μετρό/Τραμ). Το εισιτήριο μετρό για το αεροδρόμιο είναι €9. Δες τα εισιτήρια OASA στο πλαϊνό πάνελ.",
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
            interchange: "Korrespondencë",
            accessible: "I aksesueshëm",
            airport: "Aeroporti",
            train: "Tren",
            live: "Drejtpërdrejt",
            unknown: "i panjohur",
            next: "tjetër",
            reduced: "Me zbritje",
            verify_on: "Verifiko në {op} ↗",
            ask_ariadne: "Pyet Ariadnen",
            ariadne_title: "Ariadne",
            ariadne_placeholder: "Pyet Ariadnen...",
            send: "Dërgo",
            ariadne_greeting: "Përshëndetje, unë jam Ariadne. Më pyet për transportin publik të Athinës — nisjet e ardhshme, treni i fundit, udhëtim A në B, njoftime ose çmime biletash.",
            ariadne_looking_up: "Po kërkoj për {station}...",
            ariadne_no_station: "S'e njoha stacionin. Provo Syntagma, Piraeus ose Aeroporti.",
            ariadne_open_map: "Po hap {station} në hartë.",
            ariadne_open_alerts: "Po tregoj njoftimet.",
            ariadne_open_route: "Po hap udhëzimet nga {from} te {to}.",
            ariadne_line: "Linja {id} lidh {a} me {b}. Prek linjën në hartë për të gjitha stacionet.",
            ariadne_fare: "Bileta standarde OASA është €0,90 (Metro/Tram). Bileta metro për aeroport është €9. Shiko biletat OASA në panelin anësor.",
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
        fetch("files/seed/lines.json").then((r) => r.json()),
        fetch("files/seed/routes.json").then((r) => r.json()),
        fetch("files/seed/service_patterns.json").then((r) => r.json()),
        fetch("icons/vehicles/manifest.json").then((r) => r.json()).catch(() => ({ directional_icons: [] })),
    ]);

    const lineMap = new Map(lines.map((line) => [line.id, line]));

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
    const lineIdsToFetch = ["M1", "M2", "M3", "M3_AIR", "T6", "T7", "A1", "A2", "A3", "A4"];
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

    const map = L.map("map", {
        zoomControl: false,
        attributionControl: true,
    }).setView(ATHENS_CENTER, INITIAL_ZOOM);

    // --- Localised tile layer --------------------------------------------
    // Tile labels follow OpenStreetMap's "name" / "name:en" / "name:sq" tags;
    // each tile renderer picks a different language to bake into the raster.
    // Greek users want Greek place names (default OSM), English / Albanian
    // users want Latin-script labels (Carto Voyager renders name:en in
    // preference to local name). Albanian-specific rendering isn't a free
    // public service in 2026, so Albanian falls back to the English raster.
    function tileSourceFor(lang) {
        if (lang === "el") {
            return {
                url: "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
                subdomains: "abc",
                attribution: "&copy; OpenStreetMap",
                maxZoom: 19,
            };
        }
        // English + Albanian + any unknown future code
        return {
            url: "https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png",
            subdomains: "abcd",
            attribution: "&copy; OpenStreetMap, &copy; CARTO",
            maxZoom: 19,
        };
    }

    let activeTileLayer = null;
    function applyTileLayer(lang) {
        const cfg = tileSourceFor(lang);
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

    applyTileLayer(currentLang);
    onLanguageChange((next) => applyTileLayer(next));

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
        const strokeColor = ld?.strokeColor || line.color;
        const strokeWeight = ld?.strokeWeight ?? (line.type === "suburban" ? 4 : 5);
        const polylineOpts = {
            color: strokeColor,
            weight: strokeWeight,
            opacity: 0.9,
            lineCap: "round",
            lineJoin: "round",
            dashArray: ld?.strokeDash || null,
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

    for (const station of stationNodes) {
        const marker = L.marker([station.latitude, station.longitude], {
            icon: buildStationIcon(station, false),
            keyboard: false,
        }).addTo(map);

        marker.on("click", () => {
            selectStation(station.id, true);
        });

        markers.set(station.id, marker);
    }

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

        // High zoom: per-station smart-code SVG when readable.
        if (currentZoom >= 14) {
            const primarySid = station.stationIds[0];
            // A2 stations share most of their physical platforms with A1
            // (Doukissis Plakentias, Pallini, Metamorfosi, etc.). The icon
            // pack doesn't ship A2-prefixed SVGs, so fall back to any
            // sibling line's icon for the same station before giving up.
            const svgUrl = stationIconBySid.get(primarySid)
                || station.stationIds.map((sid) => stationIconBySid.get(sid)).find(Boolean);
            if (svgUrl) {
                const size = selected ? 36 : 28;
                return L.icon({
                    iconUrl: svgUrl,
                    iconSize: [size, size],
                    iconAnchor: [size / 2, size / 2],
                    className: `station-svg-icon${selected ? " station-svg-icon--selected" : ""}`,
                });
            }
        }

        // Mid/low zoom: colored pin with mode glyph. Interchange shows a ring of line colors.
        const glyph = modeGlyph(primaryMode);
        const pinSize = selected ? 32 : (currentZoom >= 12 ? 26 : 22);

        if (station.isInterchange) {
            const rings = stationLines.slice(0, 3).map((line, idx) =>
                `<span class="station-pin__ring" style="background:${line.color};" data-i="${idx}"></span>`
            ).join("");
            return L.divIcon({
                className: `station-pin station-pin--interchange${selected ? " station-pin--selected" : ""}`,
                html: `<span class="station-pin__core" style="background:${primaryColor};">${glyph}</span><span class="station-pin__rings">${rings}</span>`,
                iconSize: [pinSize, pinSize],
                iconAnchor: [pinSize / 2, pinSize],
            });
        }

        return L.divIcon({
            className: `station-pin${selected ? " station-pin--selected" : ""}`,
            html: `<span class="station-pin__core" style="background:${primaryColor};">${glyph}</span>`,
            iconSize: [pinSize, pinSize],
            iconAnchor: [pinSize / 2, pinSize],
        });
    }

    function updateMarkerSelection(nextId) {
        if (selectedStationId && markers.has(selectedStationId)) {
            const previous = stationNodeMap.get(selectedStationId);
            markers.get(selectedStationId).setIcon(buildStationIcon(previous, false));
        }

        selectedStationId = nextId;

        if (nextId && markers.has(nextId)) {
            const selected = stationNodeMap.get(nextId);
            markers.get(nextId).setIcon(buildStationIcon(selected, true));
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

    function renderSearchResults(results) {
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
    }

    stationSearch.addEventListener("input", (event) => {
        const query = event.target.value.trim().toLowerCase();
        if (!query) {
            searchResults.innerHTML = "";
            return;
        }

        const filtered = stationNodes.filter((station) => {
            return station.name.toLowerCase().includes(query) || station.nameEl.toLowerCase().includes(query);
        });

        renderSearchResults(filtered);
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

    map.on("click", () => {
        clearSelection();
    });

    let lastZoomBucket = INITIAL_ZOOM >= 14 ? 2 : INITIAL_ZOOM >= 12 ? 1 : 0;
    map.on("zoomend", () => {
        const z = map.getZoom();
        const bucket = z >= 14 ? 2 : z >= 12 ? 1 : 0;
        if (bucket !== lastZoomBucket) {
            lastZoomBucket = bucket;
            for (const [id, marker] of markers) {
                const station = stationNodeMap.get(id);
                if (station) marker.setIcon(buildStationIcon(station, id === selectedStationId));
            }
            for (const [id, marker] of simulatedTrainMarkers) {
                const train = lastSimulatedTrains.find((t) => t.id === id);
                if (train) marker.setIcon(trainMarkerIcon(train));
            }
        }
    });

    const bounds = L.latLngBounds(stationNodes.map((station) => [station.latitude, station.longitude]));
    map.fitBounds(bounds.pad(0.12));

    const simulatedTrainMarkers = new Map();
    let lastSimulatedTrains = [];

    renderPopularPanel();
    updateNearbyPanel();
    connectLiveTrainStream();
    pollLivePositions();
    setupRightRail();
    startTrainSimulation();
    setupPanelBehavior();

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

    function setupPanelBehavior() {
        const panel = document.getElementById("insightPanel");
        const peek = document.getElementById("panelPeek");
        const peekText = document.getElementById("panelPeekText");
        if (!panel || !peek) return;

        peek.addEventListener("click", () => {
            panel.classList.toggle("insight-panel--expanded");
        });

        map.on("click", () => {
            panel.classList.remove("insight-panel--expanded");
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
        const PROJECTED = "M1,M2,M3,M3_AIR,T6,T7";
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

    function simulateAllTrains() {
        // Bail until both snapshots have landed; the very first paint
        // shows no dots rather than haversine guesses that disagree
        // with the bottom-sheet projector.
        if (!livePositionsSnapshot || !stationOffsetsByLineDirection) return [];

        const nowEpoch = Date.now() / 1000;
        const linesById = new Map(lines.map((l) => [l.id, l]));
        const stationById = new Map();
        for (const stns of lineStations.values()) {
            for (const s of stns) stationById.set(s.id, s);
        }

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
            if (!line || line.type === "suburban") continue;

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
            const fromStation = stationById.get(fromStop.stationId);
            const toStation = stationById.get(toStop.stationId);
            if (!fromStation || !toStation) continue;
            const segDuration = toStop.minutesFromOrigin - fromStop.minutesFromOrigin;
            const frac = segDuration > 0
                ? Math.min(Math.max((elapsed - fromStop.minutesFromOrigin) / segDuration, 0), 1)
                : 0;

            const lat = fromStation.latitude + (toStation.latitude - fromStation.latitude) * frac;
            const lng = fromStation.longitude + (toStation.longitude - fromStation.longitude) * frac;
            const dest = raw.directionKey === "outbound" ? line.terminalB : line.terminalA;

            result.push({
                id: `${raw.lineId}_${raw.directionKey}_${Math.round(raw.originDepartureMinute)}`,
                line,
                direction: raw.directionKey,
                destination: dest,
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


    function trainMarkerIcon(train) {
        const zoom = map.getZoom();
        const manifestLine = lineIdToManifestLine[train.line.id] || train.line.id;

        if (zoom < 12) {
            const color = train.line.color || "#0072CE";
            return L.divIcon({
                className: "train-marker",
                html: `<span class="train-marker__ring" style="background:${color}"></span>`,
                iconSize: [14, 14],
                iconAnchor: [7, 7],
            });
        }

        let svgKey;
        if (train.isAirport) {
            svgKey = vehicleIconMap.get(`${manifestLine}_airport`) || vehicleIconMap.get(`${manifestLine}_outbound`);
        } else {
            svgKey = vehicleIconMap.get(`${manifestLine}_${train.direction}`);
        }
        const size = zoom >= 14 ? 38 : 28;
        if (svgKey) {
            return L.icon({
                iconUrl: svgKey,
                iconSize: [size, size],
                iconAnchor: [size / 2, size / 2],
                className: "sim-train-marker",
            });
        }
        const genericType = train.line.type === "tram" ? "tram" : train.line.type === "suburban" ? "train" : "metro";
        return L.icon({
            iconUrl: `icons/vehicles/generic_vehicle/vehicle_${genericType}.svg`,
            iconSize: [size, size],
            iconAnchor: [size / 2, size / 2],
            className: "sim-train-marker",
        });
    }

    function renderSimulatedTrainsOnMap(trains) {
        lastSimulatedTrains = trains;
        const activeIds = new Set(trains.map((t) => t.id));

        // The Hide vehicles toggle: pull every simulated-train marker off the
        // map. Coords keep accumulating in lastSimulatedTrains so toggling back
        // restores positions without missing a beat.
        if (window.__syrmosVehiclesHidden) {
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
                simulatedTrainMarkers.get(train.id).setLatLng([train.lat, train.lng]);
            } else {
                const marker = L.marker([train.lat, train.lng], {
                    icon: trainMarkerIcon(train),
                    keyboard: false,
                    zIndexOffset: 900,
                }).addTo(map);

                marker.bindTooltip(
                    `${train.line.name} → ${train.destination}<br>Near ${train.fromStation}`,
                    { direction: "top", offset: [0, -10] }
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
        const saved = localStorage.getItem("syrmos-theme");
        const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches;
        if (saved === "dark" || (!saved && prefersDark)) {
            document.body.classList.add("dark-mode");
            themeToggle.textContent = "☀";
        }
        themeToggle.addEventListener("click", () => {
            const isDark = document.body.classList.toggle("dark-mode");
            themeToggle.textContent = isDark ? "☀" : "☾";
            localStorage.setItem("syrmos-theme", isDark ? "dark" : "light");
        });
    }

    // Ariadne panel wiring. The parser (window.SyrmosAriadne) is offline and
    // deterministic; this block just routes its intents into the same web
    // primitives the map already uses (selectStation, Google Maps directions,
    // OASA fare panel).
    if (window.SyrmosAriadne) {
        window.SyrmosAriadne.init({ stations: stations, lines: lines });

        const ariadneStyle = document.createElement("style");
        ariadneStyle.textContent = `
            .ariadne-launcher {
                position: fixed; z-index: 900;
                right: 16px; bottom: 16px;
                display: inline-flex; align-items: center; gap: 8px;
                padding: 10px 16px; border: none; cursor: pointer;
                border-radius: 999px; font: inherit; font-weight: 600;
                background: #0072CE; color: #fff;
                box-shadow: 0 6px 20px rgba(0,114,206,0.35);
            }
            .ariadne-launcher:hover { filter: brightness(1.05); }
            .ariadne-launcher__icon { font-size: 18px; line-height: 1; }
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
        const messages = document.getElementById("ariadneMessages");
        const form = document.getElementById("ariadneForm");
        const input = document.getElementById("ariadneInput");

        function appendMessage(text, from) {
            const el = document.createElement("div");
            el.className = "ariadne-msg " + (from === "user" ? "ariadne-msg--user" : "ariadne-msg--assistant");
            el.textContent = text;
            messages.appendChild(el);
            messages.scrollTop = messages.scrollHeight;
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

        function respond(intent) {
            switch (intent.kind) {
                case "help":
                    return { text: window.SyrmosAriadne.help(currentLang) };
                case "outOfScope":
                    return { text: window.SyrmosAriadne.outOfScope(currentLang) };
                case "needsClarification":
                    return { text: window.SyrmosAriadne.clarify(intent.missing, currentLang) };
                case "departures":
                case "lastTrain":
                    if (intent.stationId) {
                        return {
                            text: t("ariadne_looking_up", { station: stationName(intent.stationId) }),
                            act: () => selectStation(intent.stationId, true),
                        };
                    }
                    return { text: t("ariadne_no_station") };
                case "openMap":
                    if (intent.stationId) {
                        return {
                            text: t("ariadne_open_map", { station: stationName(intent.stationId) }),
                            act: () => selectStation(intent.stationId, true),
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
                case "explainLine": {
                    const line = lineById(intent.lineId);
                    if (!line) return { text: t("ariadne_no_station") };
                    return {
                        text: t("ariadne_line", {
                            id: line.id,
                            a: line.terminalA || line.terminal_a || "",
                            b: line.terminalB || line.terminal_b || "",
                        }),
                    };
                }
                case "explainFare":
                    return { text: t("ariadne_fare") };
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
            if (pendingIntent.kind === "lastTrain" || pendingIntent.kind === "departures") {
                const patched = Object.assign({}, pendingIntent, { stationId: stationId });
                return patched;
            }
            if (pendingIntent.kind === "toggleFavorite") {
                return Object.assign({}, pendingIntent, { stationId: stationId });
            }
            return null;
        }

        launcher.addEventListener("click", openPanel);
        closeBtn.addEventListener("click", closePanel);
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

            if (intent.kind === "needsClarification") {
                pendingIntent = intent.base;
                pendingMissing = intent.missing;
            } else {
                pendingIntent = null;
                pendingMissing = null;
            }

            const reply = respond(intent);
            appendMessage(reply.text, "assistant");
            if (reply.act) setTimeout(reply.act, 400);
        });
    }
})();
