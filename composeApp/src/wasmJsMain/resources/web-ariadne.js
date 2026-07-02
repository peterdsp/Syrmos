/*
 * Ariadne on the web — port of core/domain/.../assistant/AthensTransitParser.kt.
 *
 * Same design rules as the native Ariadne:
 *   - Offline, deterministic, tool-only. Never generates a transit fact.
 *   - Trilingual EN / EL / SQ via accent-folded lowercase matching.
 *   - Returns an approved intent + optional NeedsClarification wrapper.
 *
 * Public API on window.SyrmosAriadne:
 *   init({ stations, lines })   — build vocab once
 *   parse(rawInput)             — returns an intent object
 *   help(lang)                  — canned capabilities blurb
 *
 * Intent shape:
 *   { kind: 'departures'|'plan'|'lastTrain'|'alerts'|'openMap'
 *          |'explainLine'|'explainFare'|'find'|'help'
 *          |'needsClarification'|'outOfScope',
 *     stationId?, lineId?, from?, to?, lowExposure?, airport?,
 *     query?, day?, base?, missing? }
 */

(function (global) {
    'use strict';

    // MARK: - Accent-fold

    function foldChar(ch) {
        switch (ch) {
            case 'ά': return 'α';
            case 'έ': return 'ε';
            case 'ή': return 'η';
            case 'ί': case 'ϊ': case 'ΐ': return 'ι';
            case 'ό': return 'ο';
            case 'ύ': case 'ϋ': case 'ΰ': return 'υ';
            case 'ώ': return 'ω';
            case 'ς': return 'σ';
            case 'à': case 'á': case 'â': case 'ä': case 'ã': return 'a';
            case 'è': case 'é': case 'ê': case 'ë': return 'e';
            case 'ì': case 'í': case 'î': case 'ï': return 'i';
            case 'ò': case 'ó': case 'ô': case 'ö': case 'õ': return 'o';
            case 'ù': case 'ú': case 'û': case 'ü': return 'u';
            case 'ç': return 'c';
            default: return ch;
        }
    }

    function fold(input) {
        if (!input) return '';
        const lower = input.toLowerCase();
        let out = '';
        for (const ch of lower) out += foldChar(ch);
        return out;
    }

    // MARK: - Vocabulary

    const TRANSIT_NOUNS = [
        'train', 'trains', 'metro', 'tram', 'station', 'departure', 'departures',
        'τρεν', 'μετρο', 'τραμ', 'σταθμ', 'δρομολογ', 'αναχωρη', 'συρμ', 'προαστιακ',
        'tren', 'stacion', 'nisje',
    ];
    const DEPARTURE_WORDS = [
        'next', 'departure', 'departures', 'when', 'trains', 'leave', 'leaving', 'schedule',
        'επομεν', 'αναχωρη', 'ποτε', 'δρομολογ', 'φευγει', 'τρεν',
        'ardhsh', 'kur', 'nisje', 'tren', 'trena',
    ];
    const LAST_TRAIN_PHRASES = [
        'last train', 'last metro', 'last one', 'leave by',
        'τελευται', 'τελευταιο τρεν', 'τελευταιοσ',
        'treni i fundit', 'fundit', 'i fundit', 'tren i fundit',
    ];
    const PLAN_PHRASES = [
        'how do i get', 'how to get', 'get to', 'get me to', 'route',
        'πωσ πα', 'πωσ πη', 'πωσ φτα', 'διαδρομη', 'για να πα',
        'si shkoj', 'si te shkoj', 'rruga', 'udhetim',
    ];
    const TO_MARKERS = [
        ' to ', ' for ', '->', '→', ' προσ ', ' για ', ' te ', ' per ', ' ne ',
    ];
    const FIND_WORDS = [
        'where is', 'find', 'locate', 'nearest', 'near me', 'closest',
        'που ειναι', 'βρεσ', 'κοντιν', 'κοντα μου', 'πλησιεστερ',
        'ku eshte', 'gjej', 'me afert', 'afer meje',
    ];
    const LINE_WORDS = [
        'line', 'about', 'tell me about',
        'γραμμη', 'σχετικα',
        'linja', 'rreth',
    ];
    const FARE_WORDS = [
        'fare', 'fares', 'ticket', 'tickets', 'how much', 'price', 'cost', 'cheap',
        'εισιτηρι', 'ποσο κανει', 'ποσο κοστιζει', 'τιμη', 'κοστοσ',
        'bilete', 'sa kushton', 'kushton', 'cmim', 'cmimi',
    ];
    const FAVORITE_WORDS = [
        'favorite', 'favourite', 'save this', 'bookmark', 'add to favorites', 'pin',
        'αγαπημεν', 'αποθηκευσ', 'σημειωσε',
        'i preferuar', 'te preferuarat', 'ruaj',
    ];
    const AIRPORT_WORDS = ['airport', 'αεροδρομιο', 'aeroport'];
    const ALERT_WORDS = [
        'alert', 'alerts', 'status', 'disruption', 'delay', 'delays', 'problem', 'closed', 'closure',
        'ειδοποι', 'κατασταση', 'καθυστερη', 'προβλημα', 'κλειστ', 'διακοπη',
        'njoftim', 'vonese', 'problem', 'mbyll',
    ];
    const MAP_WORDS = [
        'map', 'show on map', 'on the map',
        'χαρτη', 'στον χαρτη',
        'harta', 'ne harte',
    ];
    const HELP_PHRASES = [
        'what can you do', 'help', 'how do you work', 'what do you do', 'who are you',
        'τι μπορεισ', 'βοηθεια', 'πωσ δουλευ', 'ποιοσ εισαι',
        'si funksionon', 'ndihme', 'cfare mund', 'kush je',
    ];
    const WEATHER_WORDS = [
        'rain', 'raining', 'rainy', 'weather', 'storm', 'wet',
        'βροχη', 'βρεχει', 'καιρο', 'κακοκαιρ',
        'shi', 'moti', 'stuhi',
    ];
    const TOMORROW_WORDS = ['tomorrow', 'αυριο', 'neser'];
    const WEEKEND_WORDS = ['weekend', 'σαββατοκυριακο', 'fundjave'];
    const SATURDAY_WORDS = ['saturday', 'σαββατο', 'te shtune', 'shtune'];
    const SUNDAY_WORDS = ['sunday', 'κυριακη', 'te diel', 'diel'];

    // MARK: - Vocab builder (mirrors AssistantVocabularyBuilder)

    let stationVocab = [];   // [{ id, names: [folded], lineIds }]
    let lineVocab = [];      // [{ id, aliases: [folded] }]

    function init(opts) {
        const stations = (opts && opts.stations) || [];
        const lines = (opts && opts.lines) || [];

        stationVocab = stations.map(function (st) {
            const raw = [st.name, st.nameEl || st.name_el].filter(Boolean);
            const distinct = Array.from(new Set(raw));
            return {
                id: st.id,
                rawNames: distinct,
                names: distinct.map(fold).filter(function (n) { return n.length >= 3; }),
                lineIds: st.lineIds || st.line_ids || [],
            };
        });

        lineVocab = lines.map(function (line) {
            const aliases = [line.id];
            if (line.name) aliases.push(line.name);
            const nameEl = line.nameEl || line.name_el;
            if (nameEl) aliases.push(nameEl);
            const suffix = (line.id.match(/\d+/) || [''])[0];
            if (suffix) {
                aliases.push('line ' + suffix);
                aliases.push('γραμμη ' + suffix);
                aliases.push('linja ' + suffix);
                if (line.id[0] === 'M') aliases.push('metro ' + suffix);
                if (line.id[0] === 'T') aliases.push('tram ' + suffix);
            }
            const folded = Array.from(new Set(aliases.map(fold).filter(function (a) { return a.length >= 2; })));
            return { id: line.id, aliases: folded };
        });
    }

    // MARK: - Token helpers

    function isLetterOrDigit(ch) {
        if (!ch) return false;
        return /[\p{L}\p{N}]/u.test(ch);
    }

    function containsToken(text, needle) {
        if (!needle) return false;
        if (needle.indexOf(' ') >= 0) return text.indexOf(needle) >= 0;
        let idx = text.indexOf(needle);
        while (idx >= 0) {
            const before = idx === 0 ? ' ' : text.charAt(idx - 1);
            const afterIdx = idx + needle.length;
            const after = afterIdx >= text.length ? ' ' : text.charAt(afterIdx);
            if (!isLetterOrDigit(before) && !isLetterOrDigit(after)) return true;
            idx = text.indexOf(needle, idx + 1);
        }
        return false;
    }

    function containsAny(text, needles) {
        for (let i = 0; i < needles.length; i++) {
            if (containsToken(text, fold(needles[i]))) return true;
        }
        return false;
    }

    // MARK: - Matching

    function matchStations(text) {
        const ordered = [];
        for (const st of stationVocab) {
            for (const folded of st.names) {
                ordered.push({ id: st.id, name: folded, len: folded.length });
            }
        }
        ordered.sort(function (a, b) { return b.len - a.len; });
        const found = [];
        const foundSet = new Set();
        let scratch = text;
        for (const entry of ordered) {
            if (foundSet.has(entry.id)) continue;
            if (scratch.indexOf(entry.name) >= 0) {
                found.push(entry.id);
                foundSet.add(entry.id);
                scratch = scratch.split(entry.name).join(' '.repeat(entry.name.length));
            }
        }
        return found;
    }

    function matchLine(text) {
        const ordered = [];
        for (const line of lineVocab) {
            for (const alias of line.aliases) ordered.push({ id: line.id, alias: alias, len: alias.length });
        }
        ordered.sort(function (a, b) { return b.len - a.len; });
        for (const entry of ordered) {
            if (containsToken(text, entry.alias)) return entry.id;
        }
        return null;
    }

    function matchDay(text) {
        if (containsAny(text, TOMORROW_WORDS)) return 'TOMORROW';
        if (containsAny(text, WEEKEND_WORDS)) return 'WEEKEND';
        if (containsAny(text, SATURDAY_WORDS)) return 'SATURDAY';
        if (containsAny(text, SUNDAY_WORDS)) return 'SUNDAY';
        return 'TODAY';
    }

    function positionOf(text, stationId) {
        const st = stationVocab.find(function (s) { return s.id === stationId; });
        if (!st) return Number.MAX_SAFE_INTEGER;
        let best = Number.MAX_SAFE_INTEGER;
        for (const n of st.names) {
            const idx = text.indexOf(n);
            if (idx >= 0 && idx < best) best = idx;
        }
        return best;
    }

    function resolveTripEndpoints(text, stations) {
        if (stations.length === 0) return { from: null, to: null };
        if (stations.length === 1) {
            const hasToMarker = TO_MARKERS.some(function (m) { return text.indexOf(m) >= 0; });
            return hasToMarker
                ? { from: null, to: stations[0] }
                : { from: stations[0], to: null };
        }
        const sorted = stations.slice().sort(function (a, b) {
            return positionOf(text, a) - positionOf(text, b);
        });
        return { from: sorted[0], to: sorted[1] };
    }

    function isBareLineQuery(text) {
        return text.split(/\s+/).filter(function (t) { return t.length; }).length <= 3;
    }

    // MARK: - Public parse

    function parse(rawInput) {
        const text = fold(rawInput || '');
        if (!text.trim()) return { kind: 'outOfScope' };

        const mentionedStations = matchStations(text);
        const mentionedLine = matchLine(text);
        const day = matchDay(text);

        if (containsAny(text, HELP_PHRASES)) return { kind: 'help' };

        const strongTransit = mentionedStations.length > 0 ||
            mentionedLine !== null ||
            containsAny(text, TRANSIT_NOUNS);
        const intentSignal = containsAny(text, ALERT_WORDS) ||
            containsAny(text, MAP_WORDS) ||
            containsAny(text, LAST_TRAIN_PHRASES) ||
            containsAny(text, PLAN_PHRASES) ||
            containsAny(text, FIND_WORDS) ||
            containsAny(text, FARE_WORDS) ||
            containsAny(text, FAVORITE_WORDS);

        const weather = containsAny(text, WEATHER_WORDS);
        if (weather && !strongTransit) return { kind: 'outOfScope' };
        if (!strongTransit && !intentSignal && !weather) return { kind: 'outOfScope' };

        // Fares first, so "how much to the airport" is a fare, not a trip.
        if (containsAny(text, FARE_WORDS)) {
            const ep = resolveTripEndpoints(text, mentionedStations);
            return {
                kind: 'explainFare',
                airport: containsAny(text, AIRPORT_WORDS),
                from: ep.from,
                to: ep.to,
            };
        }

        // Favorites: needs a station.
        if (containsAny(text, FAVORITE_WORDS)) {
            const st = mentionedStations[0] || null;
            const base = { kind: 'toggleFavorite', stationId: st };
            return st ? base : { kind: 'needsClarification', base: base, missing: 'STATION' };
        }

        // Plan a trip.
        const hasToMarker = TO_MARKERS.some(function (m) { return text.indexOf(m) >= 0; });
        const planning = containsAny(text, PLAN_PHRASES) ||
            weather ||
            (hasToMarker && mentionedStations.length > 0) ||
            mentionedStations.length >= 2;
        if (planning) {
            const ep = resolveTripEndpoints(text, mentionedStations);
            const base = { kind: 'plan', from: ep.from, to: ep.to, lowExposure: !!weather };
            if (!ep.to) return { kind: 'needsClarification', base: base, missing: 'DESTINATION_STATION' };
            if (!ep.from) return { kind: 'needsClarification', base: base, missing: 'ORIGIN_STATION' };
            return base;
        }

        // Last train tonight.
        if (containsAny(text, LAST_TRAIN_PHRASES)) {
            const st = mentionedStations[0] || null;
            const base = { kind: 'lastTrain', stationId: st, lineId: mentionedLine };
            return st ? base : { kind: 'needsClarification', base: base, missing: 'STATION' };
        }

        // Alerts.
        if (containsAny(text, ALERT_WORDS)) {
            return { kind: 'alerts', lineId: mentionedLine };
        }

        // Open on map.
        if (containsAny(text, MAP_WORDS)) {
            return { kind: 'openMap', stationId: mentionedStations[0] || null };
        }

        // Explain a line (line named, no station, no departures cue).
        if (mentionedLine && mentionedStations.length === 0 &&
            !containsAny(text, DEPARTURE_WORDS) &&
            (containsAny(text, LINE_WORDS) || isBareLineQuery(text))
        ) {
            return { kind: 'explainLine', lineId: mentionedLine };
        }

        // Departures — the default.
        if (mentionedStations.length > 0 || containsAny(text, DEPARTURE_WORDS) || mentionedLine) {
            const st = mentionedStations[0] || null;
            const base = { kind: 'departures', stationId: st, lineId: mentionedLine, day: day };
            return (st === null && mentionedLine === null)
                ? { kind: 'needsClarification', base: base, missing: 'STATION' }
                : base;
        }

        // Find a station.
        if (containsAny(text, FIND_WORDS) && mentionedStations.length === 0) {
            return { kind: 'find', query: (rawInput || '').trim() };
        }

        return { kind: 'outOfScope' };
    }

    // MARK: - Canned help + out-of-scope replies

    function help(lang) {
        switch (lang) {
            case 'el':
                return 'Μπορώ να βρω επόμενες αναχωρήσεις, τελευταίο τρένο, να σχεδιάσω διαδρομή, να δείξω ειδοποιήσεις, να ανοίξω σταθμό στον χάρτη ή να πω τιμές εισιτηρίων. Ρώτα με στα Ελληνικά, Αγγλικά ή Αλβανικά.';
            case 'sq':
                return 'Mund të gjej nisjet e ardhshme, trenin e fundit, të planifikoj një udhëtim, të tregoj njoftime, të hap një stacion në hartë ose të tregoj çmimet e biletave. Pyet mua në shqip, anglisht ose greqisht.';
            default:
                return 'I can find next departures, the last train tonight, plan a trip, show service alerts, open a station on the map, or tell you ticket prices. Ask in English, Greek, or Albanian.';
        }
    }

    function outOfScope(lang) {
        switch (lang) {
            case 'el':
                return 'Δεν είμαι σίγουρη ότι είναι σχετικό με τα ΜΜΜ Αθήνας. Δοκίμασε "επόμενα τρένα από Σύνταγμα" ή "πώς πάω από Πειραιά στο Αεροδρόμιο".';
            case 'sq':
                return 'S\'jam e sigurt që kjo lidhet me transportin publik të Athinës. Provo "nisjet e ardhshme nga Syntagma" ose "si shkoj nga Piraeus në aeroport".';
            default:
                return "I'm not sure that's about Athens transit. Try 'next trains from Syntagma' or 'how do I get from Piraeus to the airport'.";
        }
    }

    function clarify(missing, lang) {
        const map = {
            STATION: {
                en: 'Which station?',
                el: 'Ποιος σταθμός;',
                sq: 'Cili stacion?',
            },
            ORIGIN_STATION: {
                en: 'From which station?',
                el: 'Από ποιον σταθμό;',
                sq: 'Nga cili stacion?',
            },
            DESTINATION_STATION: {
                en: 'To which station?',
                el: 'Προς ποιον σταθμό;',
                sq: 'Për te cili stacion?',
            },
        };
        const row = map[missing] || map.STATION;
        return row[lang] || row.en;
    }

    global.SyrmosAriadne = {
        init: init,
        parse: parse,
        help: help,
        outOfScope: outOfScope,
        clarify: clarify,
        fold: fold,
    };
})(window);
