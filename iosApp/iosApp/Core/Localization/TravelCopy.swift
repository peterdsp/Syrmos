import Foundation

// Syrmos 3.0.2 user-facing copy: closures, Syrmos announcements, the nearby
// next-train coach and calendar leave-by reminders, in English, Greek, Albanian
// and Italian.
//
// The app switches language in-app (LocalizationManager), so copy is picked per
// AppLanguage here, the same way the rest of the app does it, rather than through
// the system bundle. Tone (owner): friendly, short, a bit playful, never "run".

@inline(__always)
func travelText(_ lang: AppLanguage, _ en: String, _ el: String, _ sq: String, _ it: String) -> String {
    switch lang {
    case .english: return en
    case .greek: return el
    case .albanian: return sq
    case .italian: return it
    }
}

private func fill(_ template: String, _ values: [String: String]) -> String {
    var out = template
    for (key, value) in values { out = out.replacingOccurrences(of: "{\(key)}", with: value) }
    return out
}

private let athensZone = TimeZone(identifier: "Europe/Athens")!

/// "08:00" in Athens time for an epoch second.
func athensClock(_ epochSeconds: Int64) -> String {
    let f = DateFormatter()
    f.locale = Locale(identifier: "en_GB")
    f.timeZone = athensZone
    f.dateFormat = "HH:mm"
    return f.string(from: Date(timeIntervalSince1970: TimeInterval(epochSeconds)))
}

/// "28/10 08:00" when the instant is not on the same Athens day as `now`, else "08:00".
func athensClockWithDay(_ epochSeconds: Int64, now: Int64) -> String {
    var cal = Calendar(identifier: .gregorian)
    cal.timeZone = athensZone
    let a = Date(timeIntervalSince1970: TimeInterval(epochSeconds))
    let b = Date(timeIntervalSince1970: TimeInterval(now))
    if cal.isDate(a, inSameDayAs: b) { return athensClock(epochSeconds) }
    let f = DateFormatter()
    f.locale = Locale(identifier: "en_GB")
    f.timeZone = athensZone
    f.dateFormat = "dd/MM HH:mm"
    return f.string(from: a)
}

/// Athens calendar date "yyyy-MM-dd" (the coach copy-variant key).
func athensDateKey(_ date: Date) -> String {
    let f = DateFormatter()
    f.locale = Locale(identifier: "en_US_POSIX")
    f.timeZone = athensZone
    f.dateFormat = "yyyy-MM-dd"
    return f.string(from: date)
}

func athensHour(_ date: Date) -> Int {
    var cal = Calendar(identifier: .gregorian)
    cal.timeZone = athensZone
    return cal.component(.hour, from: date)
}

// MARK: - Closures

enum ClosureCopy {
    /// The status chip word. Rendered as one non-wrapping chip.
    static func chip(mode: String?, _ lang: AppLanguage) -> String {
        switch mode {
        case "exit_only": return travelText(lang, "Exit only", "Μόνο έξοδος", "Vetëm dalje", "Solo uscita")
        case "entry_only": return travelText(lang, "Entry only", "Μόνο είσοδος", "Vetëm hyrje", "Solo ingresso")
        default: return travelText(lang, "Closed", "Κλειστός", "I mbyllur", "Chiusa")
        }
    }

    static func modeDescription(_ mode: String?, _ lang: AppLanguage) -> String {
        switch mode {
        case "pass_through":
            return travelText(lang, "Trains pass without stopping.",
                              "Οι συρμοί διέρχονται χωρίς στάση.",
                              "Trenat kalojnë pa ndalur.",
                              "I treni transitano senza fermarsi.")
        case "exit_only":
            return travelText(lang, "You can leave the station, but not board here.",
                              "Μπορείς να βγεις από τον σταθμό, αλλά όχι να επιβιβαστείς εδώ.",
                              "Mund të dalësh nga stacioni, por jo të hipësh këtu.",
                              "Puoi uscire dalla stazione, ma non salire qui.")
        case "entry_only":
            return travelText(lang, "You can board here, but trains do not let passengers off.",
                              "Μπορείς να επιβιβαστείς εδώ, αλλά δεν γίνεται αποβίβαση.",
                              "Mund të hipësh këtu, por nuk zbret njeri.",
                              "Puoi salire qui, ma non si scende.")
        default:
            return travelText(lang, "The station is closed. Trains do not stop here.",
                              "Ο σταθμός είναι κλειστός. Οι συρμοί δεν σταματούν εδώ.",
                              "Stacioni është i mbyllur. Trenat nuk ndalojnë këtu.",
                              "La stazione è chiusa. I treni non fermano qui.")
        }
    }

    /// "Closed 08:00 to 16:00" / "Closed until further notice" / "Closed until 16:00".
    static func window(startsAt: Int64, endsAt: Int64?, now: Int64, _ lang: AppLanguage) -> String {
        let from = athensClockWithDay(startsAt, now: now)
        guard let endsAt else {
            if startsAt > now {
                return travelText(lang, "From \(from) until further notice", "Από \(from) έως νεωτέρας",
                                  "Nga \(from) deri në njoftim të ri", "Dalle \(from) fino a nuovo avviso")
            }
            return travelText(lang, "Until further notice", "Έως νεωτέρας", "Deri në njoftim të ri", "Fino a nuovo avviso")
        }
        let to = athensClockWithDay(endsAt, now: now)
        return travelText(lang, "\(from) to \(to)", "\(from) έως \(to)", "\(from) deri \(to)", "\(from) alle \(to)")
    }

    static func replacementBus(_ bus: ReplacementBus, _ lang: AppLanguage) -> String {
        let label = bus.label?.pick(lang) ?? travelText(lang, "Replacement bus", "Έκτακτο λεωφορείο", "Autobus zëvendësues", "Bus sostitutivo")
        var text = label
        if let h = bus.headwayMinutes, h > 0 {
            text += travelText(lang, " every \(h)'", " κάθε \(h)'", " çdo \(h)'", " ogni \(h)'")
        }
        let from = bus.fromStop?.pick(lang)
        let to = bus.toStop?.pick(lang)
        switch (from, to) {
        case let (f?, t?):
            text += travelText(lang, ", from \(f) to \(t)", ", από \(f) προς \(t)", ", nga \(f) drejt \(t)", ", da \(f) a \(t)")
        case let (f?, nil):
            text += travelText(lang, ", from \(f)", ", από \(f)", ", nga \(f)", ", da \(f)")
        case let (nil, t?):
            text += travelText(lang, ", to \(t)", ", προς \(t)", ", drejt \(t)", ", a \(t)")
        default: break
        }
        return text
    }

    static func upcoming(startsAt: Int64, now: Int64, mode: String?, _ lang: AppLanguage) -> String {
        let at = athensClockWithDay(startsAt, now: now)
        if mode == "pass_through" {
            return travelText(lang, "From \(at) trains will pass without stopping here.",
                              "Από τις \(at) οι συρμοί θα διέρχονται χωρίς στάση.",
                              "Nga ora \(at) trenat do të kalojnë pa ndalur këtu.",
                              "Dalle \(at) i treni transiteranno senza fermarsi qui.")
        }
        return travelText(lang, "This station closes at \(at).",
                          "Ο σταθμός κλείνει στις \(at).",
                          "Ky stacion mbyllet në \(at).",
                          "Questa stazione chiude alle \(at).")
    }

    static func goWarning(stationName: String, _ lang: AppLanguage) -> String {
        travelText(lang, "\(stationName) is closed on your route. Check before you go.",
                   "Ο σταθμός \(stationName) είναι κλειστός στη διαδρομή σου. Έλεγξε πριν ξεκινήσεις.",
                   "\(stationName) është i mbyllur në rrugën tënde. Kontrollo para se të nisesh.",
                   "\(stationName) è chiusa sul tuo percorso. Controlla prima di partire.")
    }

    static func ariadneAnswer(stationName: String, mode: String?, window: String, reason: String?, bus: String?, _ lang: AppLanguage) -> String {
        var parts: [String] = [
            travelText(lang, "\(stationName): \(chip(mode: mode, lang).lowercased()) (\(window)).",
                       "\(stationName): \(chip(mode: mode, lang).lowercased()) (\(window)).",
                       "\(stationName): \(chip(mode: mode, lang).lowercased()) (\(window)).",
                       "\(stationName): \(chip(mode: mode, lang).lowercased()) (\(window))."),
            modeDescription(mode, lang),
        ]
        if let reason, !reason.isEmpty { parts.append(reason) }
        if let bus { parts.append(bus) }
        return parts.joined(separator: " ")
    }
}

// MARK: - Syrmos announcements (broadcasts)

enum BroadcastCopy {
    static func settingsToggle(_ lang: AppLanguage) -> String {
        travelText(lang, "Announcements from Syrmos", "Ανακοινώσεις από το Syrmos", "Njoftime nga Syrmos", "Annunci da Syrmos")
    }
    static func settingsFooter(_ lang: AppLanguage) -> String {
        travelText(lang, "Station closures and important changes, sent by Syrmos. No account, no location: only an anonymous device token.",
                   "Κλεισίματα σταθμών και σημαντικές αλλαγές, από το Syrmos. Χωρίς λογαριασμό και χωρίς τοποθεσία: μόνο ένα ανώνυμο αναγνωριστικό συσκευής.",
                   "Mbyllje stacionesh dhe ndryshime të rëndësishme, nga Syrmos. Pa llogari, pa vendndodhje: vetëm një token anonim i pajisjes.",
                   "Chiusure di stazioni e cambiamenti importanti, inviati da Syrmos. Nessun account, nessuna posizione: solo un token anonimo del dispositivo.")
    }
    static func inboxTitle(_ lang: AppLanguage) -> String {
        travelText(lang, "From Syrmos", "Από το Syrmos", "Nga Syrmos", "Da Syrmos")
    }
}

// MARK: - Nearby next-train coach

enum NearbyCopy {
    struct Params {
        var next: Int?
        var following: Int?
        var margin: Int?
        var wait: Int?
        var line: String
        var destination: String
    }

    private typealias Variants = (en: [String], el: [String], sq: [String], it: [String])

    private static let table: [CoachCase: Variants] = [
        .relaxNextOneIsClose: (
            en: ["The next one to {destination} is in {next}', but the one after is in {following}'. No stress.",
                 "{destination}: you'll miss the {next}', but another comes in {following}'. Take it easy.",
                 "No need to hurry for {destination}. After the {next}' there's one in {following}'.",
                 "The {next}' to {destination} is a bit soon, the {following}' is yours. Relax."],
            el: ["Μπορεί το επόμενο για {destination} να έρχεται σε {next}', αλλά το μεθεπόμενο έρχεται σε {following}'. Μην αγχώνεσαι.",
                 "Για {destination}: το {next}' δεν το πιάνεις, αλλά έρχεται άλλο σε {following}'. Χαλαρά.",
                 "Δεν χρειάζεται βιασύνη για {destination}. Μετά το {next}' έρχεται ένα σε {following}'.",
                 "Το {next}' για {destination} είναι λίγο νωρίς, το {following}' είναι δικό σου. Ήρεμα."],
            sq: ["I radhës për {destination} vjen pas {next}', por tjetri vjen pas {following}'. Pa stres.",
                 "Për {destination}: atë të {next}' e humb, por një tjetër vjen pas {following}'. Qetë.",
                 "S'ka nevojë të nxitosh për {destination}. Pas atij të {next}' vjen një pas {following}'.",
                 "Ai i {next}' për {destination} është pak herët, ai i {following}' është yti. Relaks."],
            it: ["Il prossimo per {destination} passa tra {next}', ma quello dopo arriva tra {following}'. Niente stress.",
                 "Per {destination}: quello tra {next}' lo perdi, ma un altro arriva tra {following}'. Con calma.",
                 "Nessuna fretta per {destination}. Dopo quello tra {next}' ce n'è uno tra {following}'.",
                 "Quello tra {next}' per {destination} è un po' presto, quello tra {following}' è il tuo. Rilassati."]
        ),
        .longGapTakeItEasy: (
            en: ["You'll miss this one to {destination}; the next is in {following}'. Coffee time.",
                 "The {next}' to {destination} is out of reach; the next one is in {following}'. Grab a snack.",
                 "{destination}: the next one you can catch is in {following}'. Plenty of time, take it slow."],
            el: ["Το τωρινό για {destination} δεν το προλαβαίνεις, το επόμενο σε {following}'. Ώρα για ένα freddo.",
                 "Το {next}' για {destination} δεν το φτάνεις. Το επόμενο σε {following}', πάρε κάτι να τσιμπήσεις.",
                 "Για {destination}, το επόμενο που προλαβαίνεις είναι σε {following}'. Έχεις χρόνο, με την ησυχία σου."],
            sq: ["Këtë për {destination} nuk e kap; tjetri pas {following}'. Kohë për një kafe.",
                 "Ai i {next}' për {destination} nuk arrihet; tjetri pas {following}'. Merr diçka për të ngrënë.",
                 "Për {destination}, i pari që kap vjen pas {following}'. Ke kohë, me qetësi."],
            it: ["Questo per {destination} non lo prendi; il prossimo tra {following}'. Tempo per un caffè.",
                 "Quello tra {next}' per {destination} è fuori portata; il prossimo tra {following}'. Prenditi uno spuntino.",
                 "Per {destination} il primo che prendi è tra {following}'. Hai tempo, con calma."]
        ),
        .hurry: (
            en: ["{destination} in {next}'. Walk briskly and you'll make it.",
                 "{destination} in {next}'. Keep a good pace and you're on it.",
                 "The {next}' to {destination} is tight, but a quick walk gets you there."],
            el: ["Για {destination} σε {next}'. Με γρήγορο βήμα το προλαβαίνεις.",
                 "Για {destination} σε {next}'. Με καλό ρυθμό είσαι μέσα.",
                 "Το {next}' για {destination} είναι στο τσακ, αλλά με γρήγορο περπάτημα το πιάνεις."],
            sq: ["{destination} pas {next}'. Me hap të shpejtë e arrin.",
                 "{destination} pas {next}'. Me ritëm të mirë je brenda.",
                 "Ai i {next}' për {destination} është ngushtë, por me ecje të shpejtë e kap."],
            it: ["{destination} tra {next}'. A passo svelto ce la fai.",
                 "{destination} tra {next}'. Con un buon passo sei a bordo.",
                 "Quello tra {next}' per {destination} è stretto, ma camminando svelto lo prendi."]
        ),
        .comfortable: (
            en: ["{destination} in {next}'. You'll get there with {margin}' to spare.",
                 "{destination} in {next}'. Easy walk, {margin}' of margin.",
                 "Plenty of time for the {next}' to {destination}: {margin}' to spare."],
            el: ["Για {destination} σε {next}'. Φτάνεις άνετα, με {margin}' περιθώριο.",
                 "Για {destination} σε {next}'. Χαλαρό περπάτημα, {margin}' περιθώριο.",
                 "Άνετα για το {next}' προς {destination}: σου περισσεύουν {margin}'."],
            sq: ["{destination} pas {next}'. Arrin rehat, me {margin}' rezervë.",
                 "{destination} pas {next}'. Ecje e qetë, {margin}' rezervë.",
                 "Ke kohë për atë të {next}' drejt {destination}: të tepron {margin}'."],
            it: ["{destination} tra {next}'. Arrivi comodo, con {margin}' di margine.",
                 "{destination} tra {next}'. Passeggiata tranquilla, {margin}' di margine.",
                 "Tutto il tempo per quello tra {next}' per {destination}: {margin}' di margine."]
        ),
        .lastTrain: (
            en: ["Heads up: the {next}' to {destination} is the last one today.",
                 "The last one of the day to {destination} leaves in {next}'. Don't miss it.",
                 "The {next}' is tonight's last to {destination}."],
            el: ["Προσοχή: το {next}' για {destination} είναι το τελευταίο για σήμερα.",
                 "Το τελευταίο της ημέρας για {destination} φεύγει σε {next}'. Μην το χάσεις.",
                 "Το {next}' είναι το τελευταίο της βραδιάς για {destination}."],
            sq: ["Kujdes: ai i {next}' për {destination} është i fundit për sot.",
                 "I fundit i ditës për {destination} niset pas {next}'. Mos e humb.",
                 "Ai i {next}' është i fundit i mbrëmjes për {destination}."],
            it: ["Attenzione: quello tra {next}' per {destination} è l'ultimo di oggi.",
                 "L'ultimo della giornata per {destination} parte tra {next}'. Non perderlo.",
                 "Quello tra {next}' è l'ultimo di stasera per {destination}."]
        ),
        .missedLast: (
            en: ["The last one to {destination} has gone. Check alternatives in Syrmos.",
                 "No more trains to {destination} today. Syrmos can show you other ways.",
                 "Service to {destination} is over for today. Open Syrmos for alternatives."],
            el: ["Το τελευταίο για {destination} έφυγε. Δες εναλλακτικές στο Syrmos.",
                 "Δεν έχει άλλο για {destination} σήμερα. Το Syrmos σου δείχνει άλλους τρόπους.",
                 "Η σημερινή υπηρεσία για {destination} τελείωσε. Άνοιξε το Syrmos για εναλλακτικές."],
            sq: ["I fundit për {destination} u nis. Shiko alternativat në Syrmos.",
                 "Nuk ka më për {destination} sot. Syrmos të tregon rrugë të tjera.",
                 "Shërbimi për {destination} mbaroi për sot. Hap Syrmos për alternativa."],
            it: ["L'ultimo per {destination} è partito. Guarda le alternative su Syrmos.",
                 "Niente più treni per {destination} oggi. Syrmos ti mostra altre strade.",
                 "Il servizio per {destination} è finito per oggi. Apri Syrmos per le alternative."]
        ),
        .stationClosed: (
            en: ["The station is closed right now. See the replacement route.",
                 "This station is closed at the moment. Syrmos has the replacement details.",
                 "Closed for now. Check the replacement service in Syrmos."],
            el: ["Ο σταθμός είναι κλειστός τώρα. Δες τη διαδρομή αντικατάστασης.",
                 "Ο σταθμός είναι κλειστός αυτή τη στιγμή. Οι λεπτομέρειες αντικατάστασης είναι στο Syrmos.",
                 "Κλειστός προς το παρόν. Δες την εναλλακτική εξυπηρέτηση στο Syrmos."],
            sq: ["Stacioni është i mbyllur tani. Shiko rrugën zëvendësuese.",
                 "Ky stacion është i mbyllur për momentin. Detajet e zëvendësimit janë në Syrmos.",
                 "I mbyllur për tani. Shiko shërbimin zëvendësues në Syrmos."],
            it: ["La stazione è chiusa in questo momento. Guarda il percorso sostitutivo.",
                 "Questa stazione è chiusa al momento. Su Syrmos trovi i dettagli del servizio sostitutivo.",
                 "Chiusa per ora. Controlla il servizio sostitutivo su Syrmos."]
        ),
        .noService: (
            en: ["No service to {destination} right now.",
                 "Nothing to {destination} at this hour.",
                 "No trains to {destination} at the moment."],
            el: ["Δεν υπάρχει δρομολόγιο για {destination} αυτή την ώρα.",
                 "Τίποτα για {destination} αυτή την ώρα.",
                 "Κανένα δρομολόγιο για {destination} αυτή τη στιγμή."],
            sq: ["Nuk ka shërbim për {destination} tani.",
                 "Asgjë për {destination} në këtë orë.",
                 "Asnjë tren për {destination} për momentin."],
            it: ["Nessun servizio per {destination} in questo momento.",
                 "Niente per {destination} a quest'ora.",
                 "Nessun treno per {destination} al momento."]
        ),
    ]

    static func variantCount(_ c: CoachCase) -> Int { table[c]?.en.count ?? 1 }

    static func coachLine(_ c: CoachCase, variant: Int, language lang: AppLanguage, params p: Params) -> String {
        guard let v = table[c] else { return "" }
        let list: [String]
        switch lang {
        case .english: list = v.en
        case .greek: list = v.el
        case .albanian: list = v.sq
        case .italian: list = v.it
        }
        let template = list[((variant % list.count) + list.count) % list.count]
        return fill(template, [
            "next": p.next.map(String.init) ?? "",
            "following": p.following.map(String.init) ?? "",
            "margin": p.margin.map(String.init) ?? "",
            "wait": p.wait.map(String.init) ?? "",
            "line": p.line,
            "destination": p.destination,
        ])
    }

    static func scheduledLabel(_ lang: AppLanguage) -> String {
        travelText(lang, "scheduled", "κατά πρόγραμμα", "sipas orarit", "da orario")
    }

    /// "Nikaia · 6' walk"
    static func title(stationName: String, walkMinutes: Int, _ lang: AppLanguage) -> String {
        travelText(lang, "\(stationName) · \(walkMinutes)' walk", "\(stationName) · \(walkMinutes)' με τα πόδια",
                   "\(stationName) · \(walkMinutes)' në këmbë", "\(stationName) · \(walkMinutes)' a piedi")
    }

    static func settingsToggle(_ lang: AppLanguage) -> String {
        travelText(lang, "Next trains when I'm near a station", "Επόμενοι συρμοί όταν είμαι κοντά σε σταθμό",
                   "Trenat e radhës kur jam pranë një stacioni", "Prossimi treni quando sono vicino a una stazione")
    }

    static func settingsFooter(_ lang: AppLanguage) -> String {
        travelText(lang, "When you come within 1 km of a station, Syrmos shows the next trains in each direction and how long the walk takes. Needs location access set to Always. Your location never leaves your phone. At most 4 a day, never at night or while you are moving fast.",
                   "Όταν πλησιάζεις σε απόσταση 1 χλμ. από σταθμό, το Syrmos δείχνει τους επόμενους συρμούς ανά κατεύθυνση και πόσο θα περπατήσεις. Χρειάζεται πρόσβαση στην τοποθεσία «Πάντα». Η τοποθεσία σου δεν φεύγει ποτέ από το κινητό σου. Έως 4 την ημέρα, ποτέ τη νύχτα ή όταν κινείσαι γρήγορα.",
                   "Kur afrohesh brenda 1 km nga një stacion, Syrmos tregon trenat e radhës në çdo drejtim dhe sa zgjat ecja. Duhet qasja në vendndodhje «Gjithmonë». Vendndodhja jote nuk largohet kurrë nga telefoni. Deri në 4 në ditë, kurrë natën ose kur lëviz shpejt.",
                   "Quando arrivi entro 1 km da una stazione, Syrmos mostra i prossimi treni in ogni direzione e quanto dura la camminata. Serve l'accesso alla posizione «Sempre». La tua posizione non lascia mai il telefono. Al massimo 4 al giorno, mai di notte o quando ti muovi velocemente.")
    }

    static func needsAlwaysHint(_ lang: AppLanguage) -> String {
        travelText(lang, "Allow location access \"Always\" in Settings for this to work.",
                   "Επίτρεψε την πρόσβαση στην τοποθεσία «Πάντα» στις Ρυθμίσεις για να λειτουργεί.",
                   "Lejo qasjen në vendndodhje «Gjithmonë» te Cilësimet që të funksionojë.",
                   "Consenti l'accesso alla posizione «Sempre» nelle Impostazioni perché funzioni.")
    }
}

// MARK: - Calendar leave-by

enum CalendarCopy {
    static func headsUp(kind: CalendarEventKind, place: String, line: String?, board: String?, _ lang: AppLanguage) -> (title: String, body: String) {
        let title = travelText(lang, "Leave in 15'", "Ξεκίνα σε 15'", "Nisu pas 15'", "Parti tra 15'")
        let via = viaText(line: line, board: board, lang)
        let body: String
        switch kind {
        case .flight:
            body = travelText(lang, "Get ready for the airport.\(via) You'll be there with time to spare for your flight.",
                              "Ετοιμάσου για το αεροδρόμιο.\(via) Θα φτάσεις με άνεση για την πτήση.",
                              "Bëhu gati për aeroportin.\(via) Do të arrish me kohë për fluturimin.",
                              "Preparati per l'aeroporto.\(via) Arriverai con calma per il volo.")
        default:
            body = travelText(lang, "Soon time to head to \(place).\(via)",
                              "Σε λίγο φεύγεις για \(place).\(via)",
                              "Së shpejti nisesh për \(place).\(via)",
                              "Tra poco si parte per \(place).\(via)")
        }
        return (title, body)
    }

    static func leaveNow(kind: CalendarEventKind, place: String, line: String?, nextMinutes: Int?, _ lang: AppLanguage) -> (title: String, body: String) {
        let title = travelText(lang, "Time to leave!", "Ώρα να φύγεις!", "Koha për t'u nisur!", "È ora di andare!")
        let lineText = line ?? travelText(lang, "your train", "ο συρμός σου", "treni yt", "il tuo treno")
        let body: String
        switch kind {
        case .flight:
            if let n = nextMinutes {
                body = travelText(lang, "The flight won't wait, but the \(lineText) comes in \(n)'.",
                                  "Η πτήση δεν περιμένει, το \(lineText) όμως έρχεται σε \(n)'.",
                                  "Fluturimi nuk pret, por \(lineText) vjen pas \(n)'.",
                                  "Il volo non aspetta, ma il \(lineText) arriva tra \(n)'.")
            } else {
                body = travelText(lang, "The flight won't wait. Head to the airport now.",
                                  "Η πτήση δεν περιμένει. Ξεκίνα για το αεροδρόμιο.",
                                  "Fluturimi nuk pret. Nisu për në aeroport.",
                                  "Il volo non aspetta. Parti per l'aeroporto.")
            }
        case .venue:
            body = travelText(lang, "Off to \(place)! Leave now and you'll make it before it starts.",
                              "Πάμε για \(place)! Φύγε τώρα και φτάνεις πριν ξεκινήσει.",
                              "Drejt \(place)! Nisu tani dhe arrin para fillimit.",
                              "Si va a \(place)! Parti ora e arrivi prima dell'inizio.")
        case .appointment:
            body = travelText(lang, "Leave now for \(place) and you'll be there a little early.",
                              "Φύγε τώρα για \(place) και θα είσαι εκεί λίγο νωρίτερα.",
                              "Nisu tani për \(place) dhe do të jesh pak më herët.",
                              "Parti ora per \(place) e sarai lì con un po' di anticipo.")
        default:
            body = travelText(lang, "Leave now for \(place). Take \(lineText).",
                              "Φύγε τώρα για \(place). Πάρε το \(lineText).",
                              "Nisu tani për \(place). Merr \(lineText).",
                              "Parti ora per \(place). Prendi il \(lineText).")
        }
        return (title, body)
    }

    static func flightChecklist(_ lang: AppLanguage, airportFare: String?) -> (title: String, body: String) {
        let title = travelText(lang, "Flight today", "Πτήση σήμερα", "Fluturim sot", "Volo oggi")
        let fare = airportFare.map { " (\($0))" } ?? ""
        let body = travelText(lang, "Passport, boarding pass and an airport ticket\(fare)? Syrmos will tell you when to leave.",
                              "Διαβατήριο, κάρτα επιβίβασης και εισιτήριο αεροδρομίου\(fare); Το Syrmos θα σου πει πότε να φύγεις.",
                              "Pasaporta, karta e imbarkimit dhe bileta e aeroportit\(fare)? Syrmos do të të thotë kur të nisesh.",
                              "Passaporto, carta d'imbarco e biglietto per l'aeroporto\(fare)? Syrmos ti dirà quando partire.")
        return (title, body)
    }

    static func late(place: String, _ lang: AppLanguage) -> String {
        travelText(lang, "Late for \(place)? Open Syrmos for the quickest way.",
                   "Άργησες για \(place); Άνοιξε το Syrmos για τον γρηγορότερο δρόμο.",
                   "Je vonë për \(place)? Hap Syrmos për rrugën më të shpejtë.",
                   "In ritardo per \(place)? Apri Syrmos per la via più rapida.")
    }

    private static func viaText(line: String?, board: String?, _ lang: AppLanguage) -> String {
        guard let line else { return "" }
        if let board {
            return travelText(lang, " \(line) from \(board).", " \(line) από \(board).", " \(line) nga \(board).", " \(line) da \(board).")
        }
        return " \(line)."
    }

    static func settingsToggle(_ lang: AppLanguage) -> String {
        travelText(lang, "Calendar reminders", "Υπενθυμίσεις ημερολογίου", "Kujtesa nga kalendari", "Promemoria dal calendario")
    }
    static func settingsFooter(_ lang: AppLanguage) -> String {
        travelText(lang, "Syrmos reads your next 48 hours of calendar events with a location and tells you when to leave. Everything stays on your phone.",
                   "Το Syrmos διαβάζει τα γεγονότα των επόμενων 48 ωρών που έχουν τοποθεσία και σου λέει πότε να φύγεις. Όλα μένουν στο κινητό σου.",
                   "Syrmos lexon ngjarjet e 48 orëve të ardhshme me vendndodhje dhe të thotë kur të nisesh. Gjithçka mbetet në telefon.",
                   "Syrmos legge gli eventi delle prossime 48 ore con un luogo e ti dice quando partire. Tutto resta sul telefono.")
    }
    static func toggleChecklist(_ lang: AppLanguage) -> String {
        travelText(lang, "Flight checklist (3 h before)", "Λίστα πτήσης (3 ώρες πριν)", "Lista e fluturimit (3 orë më parë)", "Checklist volo (3 ore prima)")
    }
    static func toggleHeadsUp(_ lang: AppLanguage) -> String {
        travelText(lang, "Heads-up 15' before", "Ειδοποίηση 15' πριν", "Njoftim 15' më parë", "Avviso 15' prima")
    }
    static func toggleLeaveNow(_ lang: AppLanguage) -> String {
        travelText(lang, "Time to leave", "Ώρα να φύγεις", "Koha për t'u nisur", "Ora di partire")
    }
    static func flightBuffer(_ lang: AppLanguage) -> String {
        travelText(lang, "Be at the airport before a flight", "Στο αεροδρόμιο πριν από πτήση", "Në aeroport para fluturimit", "In aeroporto prima del volo")
    }
}
