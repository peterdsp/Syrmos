import Foundation
import Combine

enum AriadneAction {
    case openStation(String)
    case openLine(String)
}

struct AriadneMessage: Identifiable {
    let id = UUID()
    let fromUser: Bool
    let text: String
    var departures: [Departure] = []
    var sourceConfidence: SourceConfidence = .unknown
    var action: AriadneAction?
    var actionLabel: String?
}

/// Ariadne's iOS resolver. Parses offline with `AthensTransitParser`, then
/// dispatches the intent to the deterministic projector and bundled data, the
/// same contract as the KMP `AssistantViewModel`. No model, no network for
/// normal answers (alerts do an optional refresh).
@MainActor
final class AriadneModel: ObservableObject {
    @Published private(set) var messages: [AriadneMessage] = []
    @Published private(set) var thinking = false

    private let parser = AthensTransitParser(vocabulary: .fromSyrmosData())
    private let alertsService = STASYService()
    // Own location source for travel-time ("how long to X") answers. Started
    // early so a fix is usually ready by the time the user asks; when it isn't
    // (or permission is off) the ETA resolver asks for an origin instead.
    private let location = LocationService()
    private let weather = WeatherStore.shared
    private var loc: LocalizationManager { LocalizationManager.shared }

    // Conversation state: after NeedsClarification we remember what we
    // asked for so the user's next bare answer completes it instead of
    // starting a fresh unrelated intent.
    private var pendingIntent: AssistantIntent?
    private var pendingMissing: MissingSlot?

    // Durable co-pilot memory: the current station ("I'm at Syntagma"), the last
    // destination/route, and the last intent, so follow-ups like "go airport
    // faster" don't re-ask what the user already told us.
    private var session = AssistantSessionContext.empty

    init() {
        messages = [greeting()]
        location.requestIfNeeded()
        Task { await loadAlertNote() }
    }

    private func loadAlertNote() async {
        await alertsService.fetchAnnouncements()
        let alerts = alertsService.announcements.filter { $0.category == .serviceAlert }
        guard !alerts.isEmpty else { return }
        let titles = alerts.prefix(3).map { $0.displayTitle(language: loc.language) }.joined(separator: ". ")
        messages.append(bot(t(
            "Heads up: \(titles)",
            "Προσοχή: \(titles)",
            "Kujdes: \(titles)",
            "Attenzione: \(titles)")))
    }

    func ask(_ input: String) {
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        messages.append(AriadneMessage(fromUser: true, text: text))
        thinking = true
        Task {
            // Online mode: try cloud Ariadne first. The Pi backend has live
            // transit data (lines, fares, alerts, frequencies) and chains
            // three LLMs, so it gives richer answers than local parsing.
            if let cloudReply = await askLLM(text) {
                messages.append(cloudReply)
                thinking = false
                return
            }
            // Offline fallback: local Ariadne (rule-based parser + resolver).
            var raw: AssistantIntent
            if let guided = await AriadneGuided.classify(text, vocabulary: parser.vocabulary) {
                raw = guided
            } else {
                let cleaned = await AriadneBrain.normalize(text) ?? text
                raw = parser.parse(cleaned)
            }
            raw = applyDayFollowUp(text, raw)
            let intent = fillFromContext(mergePendingIfApplicable(raw))
            if case let .needsClarification(base, missing) = intent {
                pendingIntent = base
                pendingMissing = missing
            } else {
                pendingIntent = nil
                pendingMissing = nil
            }
            updateSession(intent)
            let reply = await resolve(intent)
            messages.append(reply)
            thinking = false
        }
    }

    /// Bare day-change follow-up: "what about tomorrow?", "and the weekend?".
    /// The parser can't classify these alone (no station), so they land as
    /// outOfScope; if the last answered turn was a departures query for a known
    /// station, re-issue it for the new day instead of declining. Mirrors KMP.
    private func applyDayFollowUp(_ text: String, _ raw: AssistantIntent) -> AssistantIntent {
        guard case .outOfScope = raw else { return raw }
        let day = parser.dayOf(text)
        guard day != .today else { return raw }
        if case let .showDepartures(stationId, lineId, _) = session.lastIntent,
           stationId != nil || lineId != nil {
            return .showDepartures(stationId: stationId, lineId: lineId, day: day)
        }
        return raw
    }

    /// Fill the pending clarification slot with a station id the user just
    /// named. Bare "Syntagma" turns into a Departures intent from the
    /// parser; we grab its stationId and merge it into whatever the
    /// pending PlanTrip / LastTrain / etc was waiting for.
    private func mergePendingIfApplicable(_ raw: AssistantIntent) -> AssistantIntent {
        guard let pending = pendingIntent, let missing = pendingMissing else { return raw }
        let stationId: String? = {
            switch raw {
            case let .showDepartures(id, _, _): return id
            case let .lastTrain(id, _): return id
            case let .firstTrain(id, _): return id
            case let .needsClarification(base, _):
                if case let .showDepartures(id, _, _) = base { return id }
                return nil
            default: return nil
            }
        }()
        guard let sid = stationId else { return raw }

        switch pending {
        case let .planTrip(from, to, lowExposure, preference):
            var patchedFrom = from, patchedTo = to
            switch missing {
            case .originStation: patchedFrom = sid
            case .destinationStation: patchedTo = sid
            default: return raw
            }
            if let _ = patchedFrom, let _ = patchedTo {
                return .planTrip(fromStationId: patchedFrom, toStationId: patchedTo, lowExposure: lowExposure, preference: preference)
            }
            let stillMissing: MissingSlot = patchedFrom == nil ? .originStation : .destinationStation
            let base: AssistantIntent = .planTrip(fromStationId: patchedFrom, toStationId: patchedTo, lowExposure: lowExposure, preference: preference)
            return .needsClarification(base: base, missing: stillMissing)
        case let .lastTrain(_, lineId):
            return .lastTrain(stationId: sid, lineId: lineId)
        case let .firstTrain(_, lineId):
            return .firstTrain(stationId: sid, lineId: lineId)
        case let .showDepartures(_, lineId, day):
            return .showDepartures(stationId: sid, lineId: lineId, day: day)
        case .stationAccessibility:
            return .stationAccessibility(stationId: sid)
        case .whichLines:
            return .whichLines(stationId: sid)
        case let .stopsBetween(from, to):
            var pf = from, pt = to
            switch missing {
            case .originStation: pf = sid
            case .destinationStation: pt = sid
            default: return raw
            }
            if pf != nil, pt != nil { return .stopsBetween(fromStationId: pf, toStationId: pt) }
            return .needsClarification(base: .stopsBetween(fromStationId: pf, toStationId: pt),
                                       missing: pf == nil ? .originStation : .destinationStation)
        case let .toggleFavorite(_):
            return .toggleFavorite(stationId: sid)
        case let .travelTime(to, from):
            if to == nil { return .travelTime(toStationId: sid, fromStationId: from) }
            return raw
        default:
            return raw
        }
    }

    // MARK: - Dispatch

    private func resolve(_ intent: AssistantIntent) async -> AriadneMessage {
        switch intent {
        case let .showDepartures(stationId, lineId, day):
            return resolveDepartures(stationId: stationId, lineId: lineId, day: day)
        case let .lastTrain(stationId, lineId):
            return resolveLastTrain(stationId: stationId, lineId: lineId)
        case let .firstTrain(stationId, lineId):
            return resolveFirstTrain(stationId: stationId, lineId: lineId)
        case let .stationAccessibility(stationId):
            return resolveAccessibility(stationId: stationId)
        case .reverseTrip:
            return resolveReverseTrip()
        case let .whichLines(stationId):
            return resolveWhichLines(stationId: stationId)
        case let .stopsBetween(from, to):
            return resolveStopsBetween(from: from, to: to)
        case let .planTrip(from, to, lowExposure, preference):
            return resolvePlanTrip(from: from, to: to, lowExposure: lowExposure, preference: preference)
        case let .travelTime(to, from):
            return resolveTravelTime(to: to, from: from)
        case let .findStation(query):
            return resolveFindStation(query)
        case let .explainLine(lineId):
            return resolveExplainLine(lineId)
        case let .explainFare(airport, from, to):
            return resolveFare(airport: airport, from: from, to: to)
        case let .toggleFavorite(stationId):
            return resolveFavorite(stationId: stationId)
        case let .showAlerts(lineId):
            return await resolveAlerts(lineId: lineId)
        case let .openMap(stationId):
            return resolveOpenMap(stationId)
        case .help:
            return bot(helpText())
        case let .needsClarification(_, missing):
            return bot(clarify(missing))
        case .outOfScope:
            return bot(outOfScopeText())
        case .easterEggLiepur:
            return bot(catJoke())
        case let .weatherAt(stationId):
            return await resolveWeather(stationId: stationId)
        case let .planTripByArrival(from, to, absMin, relMin):
            return resolvePlanByArrival(from: from, to: to, absMin: absMin, relMin: relMin)
        case let .stationStatus(stationId):
            return await resolveStationStatus(stationId: stationId)
        case let .setCurrentLocation(stationId):
            return resolveSetLocation(stationId: stationId)
        case let .wrongTrain(stationId, lineId):
            return resolveWrongTrain(stationId: stationId, lineId: lineId)
        case let .missedStop(stationId, targetStationId):
            return resolveMissedStop(stationId: stationId, targetStationId: targetStationId)
        case let .canIStillMakeIt(toStationId, fromStationId):
            return resolveCanIStillMakeIt(to: toStationId, from: fromStationId)
        }
    }

    /// Fills a missing trip origin from the remembered current station, so a
    /// follow-up like "go airport faster" after "I'm at Syntagma" resolves
    /// without re-asking. Only touches the origin slot; everything else is left
    /// to the normal clarification flow.
    private func fillFromContext(_ intent: AssistantIntent) -> AssistantIntent {
        guard let current = session.currentStation else { return intent }
        guard case let .needsClarification(base, missing) = intent, missing == .originStation else { return intent }
        switch base {
        case let .planTrip(_, to, lowExposure, preference):
            return .planTrip(fromStationId: current, toStationId: to, lowExposure: lowExposure, preference: preference)
        case let .travelTime(to, _):
            return .travelTime(toStationId: to, fromStationId: current)
        case let .planTripByArrival(_, to, absMin, relMin):
            return .planTripByArrival(fromStationId: current, toStationId: to, arriveByAthensMinutes: absMin, inMinutesFromNow: relMin)
        default:
            return intent
        }
    }

    /// Threads the turn's outcome into the session so the next turn has context.
    private func updateSession(_ intent: AssistantIntent) {
        switch intent {
        case let .setCurrentLocation(stationId):
            session = session.withCurrentStation(stationId ?? session.currentStation)
            session.lastIntent = intent
        case let .planTrip(from, to, _, preference):
            session.currentStation = from ?? session.currentStation
            session.lastDestination = to ?? session.lastDestination
            if let from, let to {
                session.lastRoute = RouteMemory(fromStationId: from, toStationId: to, preference: preference)
            }
            session.lastIntent = intent
        case let .travelTime(to, _):
            session.lastDestination = to ?? session.lastDestination
            session.lastIntent = intent
        case let .showDepartures(stationId, _, _):
            session.currentStation = stationId ?? session.currentStation
            session.lastIntent = intent
        case let .firstTrain(stationId, _):
            session.currentStation = stationId ?? session.currentStation
            session.lastIntent = intent
        case let .whichLines(stationId):
            session.currentStation = stationId ?? session.currentStation
            session.lastIntent = intent
        case let .stopsBetween(from, to):
            session.currentStation = from ?? session.currentStation
            session.lastDestination = to ?? session.lastDestination
            if let from, let to {
                session.lastRoute = RouteMemory(fromStationId: from, toStationId: to, preference: .balanced)
            }
            session.lastIntent = intent
        // "and back" flips the remembered route so a second "and back" flips it
        // right back, and the return origin becomes the new current station.
        case .reverseTrip:
            if let r = session.lastRoute {
                session.lastRoute = RouteMemory(fromStationId: r.toStationId, toStationId: r.fromStationId, preference: r.preference)
                session.currentStation = r.toStationId
                session.lastDestination = r.fromStationId
            }
            session.lastIntent = intent
        case let .wrongTrain(stationId, lineId):
            session.currentStation = stationId ?? session.currentStation
            session.currentLine = lineId ?? session.currentLine
            session.lastIntent = intent
        case let .missedStop(stationId, _):
            session.currentStation = stationId ?? session.currentStation
            session.lastIntent = intent
        case let .canIStillMakeIt(toStationId, fromStationId):
            session.currentStation = fromStationId ?? session.currentStation
            session.lastDestination = toStationId ?? session.lastDestination
            session.lastIntent = intent
        case .needsClarification:
            break
        default:
            session.lastIntent = intent
        }
    }

    // MARK: - Context set + station status

    private func resolveSetLocation(stationId: String?) -> AriadneMessage {
        guard let stationId else {
            return bot(t("Okay, you're at the station. Where do you want to go next?",
                "Οκ, είσαι στον σταθμό. Πού θέλεις να πας μετά;",
                "Në rregull, je te stacioni. Ku do të shkosh më pas?",
                "Ok, sei alla stazione. Dove vuoi andare adesso?"))
        }
        let stationName = station(stationId).map { name($0) } ?? stationId
        return bot(t("Got it. I'll use \(stationName) as your starting station.",
            "Οκ. Θα χρησιμοποιώ τον \(stationName) ως σταθμό εκκίνησης.",
            "Në rregull. Do ta përdor \(stationName) si stacionin tënd të nisjes.",
            "Capito. User\u{00F2} \(stationName) come stazione di partenza."))
    }

    // MARK: - Recovery intents

    /// Recovery: wrong train. Find the next interchange on the current line so
    /// the user can transfer to the right one. Calming, trilingual.
    private func resolveWrongTrain(stationId: String?, lineId: String?) -> AriadneMessage {
        let currentLineId = lineId ?? session.currentLine
        guard let currentLineId else {
            return bot(t(
                "Stay calm. Get off at the next station and look for a platform going the other direction. Which line are you on?",
                "Ηρέμησε. Κατέβα στον επόμενο σταθμό και βρες την αποβάθρα για την αντίθετη κατεύθυνση. Σε ποια γραμμή είσαι;",
                "Qetësohu. Zbrit te stacioni i ardhshëm dhe gjej platformën për drejtimin tjetër. Në cilën linjë je?",
                "Stai calmo. Scendi alla prossima stazione e cerca la banchina nella direzione opposta. Su quale linea sei?"), confidence: .scheduled)
        }
        let lineStations = SyrmosData.stations(for: normalizeLine(currentLineId))
        // Find an interchange station on this line where the user can switch.
        let interchange = lineStations.first(where: { $0.isInterchange })
        if let interchange {
            let stName = name(interchange)
            let otherLines = interchange.lineIds.filter { $0 != normalizeLine(currentLineId) }.map { displayLine($0) }.joined(separator: ", ")
            return bot(t(
                "Stay calm. Get off at \(stName), the next interchange. You can transfer to \(otherLines) there.",
                "Ηρέμησε. Κατέβα στον \(stName), τον επόμενο κόμβο. Εκεί μπορείς να αλλάξεις σε \(otherLines).",
                "Qetësohu. Zbrit te \(stName), ndërrimi i ardhshëm. Aty mund të kalosh te \(otherLines).",
                "Stai calmo. Scendi a \(stName), il prossimo interscambio. L\u{00EC} puoi cambiare su \(otherLines)."), confidence: .scheduled)
        }
        return bot(t(
            "Stay calm. Get off at the next station and take the opposite direction back.",
            "Ηρέμησε. Κατέβα στον επόμενο σταθμό και πάρε την αντίθετη κατεύθυνση.",
            "Qetësohu. Zbrit te stacioni i ardhshëm dhe merr drejtimin e kundërt.",
            "Stai calmo. Scendi alla prossima stazione e prendi la direzione opposta."), confidence: .scheduled)
    }

    /// Recovery: missed stop. Tell the user to stay on, get off at the next
    /// station, and take the opposite direction back. Calm, trilingual.
    private func resolveMissedStop(stationId: String?, targetStationId: String?) -> AriadneMessage {
        if let targetId = targetStationId, let target = station(targetId) {
            let targetName = name(target)
            return bot(t(
                "No worries. Get off at the next station, cross to the opposite platform, and ride back to \(targetName).",
                "Μην ανησυχείς. Κατέβα στον επόμενο σταθμό, πέρασε στην απέναντι αποβάθρα και γύρνα πίσω στον \(targetName).",
                "Mos u shqetëso. Zbrit te stacioni i ardhshëm, kalo te platforma e kundërt dhe kthehu te \(targetName).",
                "Nessun problema. Scendi alla prossima stazione, vai alla banchina opposta e torna a \(targetName)."), confidence: .scheduled)
        }
        return bot(t(
            "No worries. Get off at the next station and take the train in the opposite direction back.",
            "Μην ανησυχείς. Κατέβα στον επόμενο σταθμό και πάρε το τρένο στην αντίθετη κατεύθυνση.",
            "Mos u shqetëso. Zbrit te stacioni i ardhshëm dhe merr trenin në drejtimin e kundërt.",
            "Nessun problema. Scendi alla prossima stazione e prendi il treno nella direzione opposta."), confidence: .scheduled)
    }

    /// Recovery: can I still make it? Check if there are departures that get the
    /// user to their destination in time.
    private func resolveCanIStillMakeIt(to: String?, from: String?) -> AriadneMessage {
        let toId = to ?? session.lastDestination
        guard let toId else {
            return bot(t(
                "Make it to where? Tell me the station you're trying to reach.",
                "Να προλάβεις πού; Πες μου ποιον σταθμό θέλεις να φτάσεις.",
                "Të arrish ku? Më thuaj cilin stacion po përpiqesh të arrish.",
                "Arrivare dove? Dimmi la stazione che stai cercando di raggiungere."))
        }
        let fromId = from ?? session.currentStation
        guard let fromId else {
            return bot(t(
                "Where are you now? Tell me your current station.",
                "Πού είσαι τώρα; Πες μου τον σταθμό σου.",
                "Ku je tani? Më thuaj stacionin tënd aktual.",
                "Dove sei adesso? Dimmi la tua stazione attuale."))
        }
        guard let plan = JourneyPlanner.plan(from: fromId, to: toId, language: loc.language) else {
            return bot(t(
                "I couldn't find a rail route between those stations.",
                "Δεν βρήκα σιδηροδρομική διαδρομή μεταξύ αυτών των σταθμών.",
                "Nuk gjeta rrugë hekurudhore mes këtyre stacioneve.",
                "Non ho trovato un percorso ferroviario tra queste stazioni."), confidence: .scheduled)
        }
        let toName = stationName(toId)
        let deps = ScheduleProjector.nextDepartures(for: fromId, lineIds: plan.legs.map { $0.lineId }, limit: 1)
        if deps.isEmpty {
            return bot(t(
                "Service has ended for tonight. No more trains to \(toName) right now.",
                "Τα δρομολόγια τελείωσαν για απόψε. Δεν υπάρχουν άλλα τρένα προς \(toName) τώρα.",
                "Shërbimi ka mbaruar për sonte. Nuk ka më trena për te \(toName) tani.",
                "Il servizio \u{00E8} terminato per stasera. Nessun altro treno per \(toName) adesso."), confidence: .scheduled)
        }
        let fromName = stationName(fromId)
        return bot(t(
            "Yes, you can still make it. The trip from \(fromName) to \(toName) takes about \(plan.totalMinutes) min. There are still trains running.",
            "Ναι, μπορείς ακόμα να προλάβεις. Η διαδρομή από \(fromName) προς \(toName) κάνει περίπου \(plan.totalMinutes) λεπτά. Υπάρχουν ακόμα δρομολόγια.",
            "Po, mund ta arrish akoma. Udhëtimi nga \(fromName) te \(toName) zgjat rreth \(plan.totalMinutes) min. Ka akoma trena.",
            "S\u{00EC}, puoi ancora farcela. Il viaggio da \(fromName) a \(toName) dura circa \(plan.totalMinutes) min. Ci sono ancora treni in servizio."), confidence: .scheduled)
    }

    /// Honest station status: lead with any live advisory, else the timetable.
    private func resolveStationStatus(stationId: String?) async -> AriadneMessage {
        guard let id = stationId, let st = station(id) else { return bot(clarify(.station)) }
        await alertsService.fetchAnnouncements()
        let advisory = ServiceAdvisoryMatcher.forStation(
            stationNames: stationSearchNames(st),
            stationLineIds: st.lineIds,
            notices: currentNotices(),
            severeWeather: WeatherStore.shared.snapshot?.current.condition.isSevere == true
        )
        return bot(stationStatusText(name: name(st), advisory: advisory), confidence: .live)
    }

    private func stationStatusText(name: String, advisory: ServiceAdvisory) -> String {
        if let top = advisory.top {
            let lead: String
            switch top.severity {
            case .closure:
                lead = t("Heads up, there's an active closure affecting \(name).",
                    "Προσοχή, υπάρχει ενεργό κλείσιμο που αφορά τον \(name).",
                    "Kujdes, ka një mbyllje aktive që prek \(name).",
                    "Attenzione, c'\u{00E8} una chiusura attiva che riguarda \(name).")
            case .warning:
                lead = t("There's an active advisory affecting \(name).",
                    "Υπάρχει ενεργή ειδοποίηση που αφορά τον \(name).",
                    "Ka një njoftim aktiv që prek \(name).",
                    "C'\u{00E8} un avviso attivo che riguarda \(name).")
            case .info:
                lead = t("There's a notice affecting \(name).",
                    "Υπάρχει ανακοίνωση που αφορά τον \(name).",
                    "Ka një njoftim që prek \(name).",
                    "C'\u{00E8} un avviso che riguarda \(name).")
            }
            let tail = t("Check official STASY alerts for details.",
                "Δες τις επίσημες ανακοινώσεις της ΣΤΑΣΥ για λεπτομέρειες.",
                "Kontrollo njoftimet zyrtare të STASY për detaje.",
                "Controlla gli avvisi ufficiali di STASY per i dettagli.")
            return "\(lead) \(top.text) \(tail)"
        }
        let base = t(
            "I don't have a live closure alert for \(name). Based on the normal timetable, the station should be operating. Check official STASY alerts if this is urgent.",
            "Δεν έχω ζωντανή ειδοποίηση κλεισίματος για τον \(name). Με βάση το κανονικό πρόγραμμα, ο σταθμός πρέπει να λειτουργεί. Αν είναι επείγον, δες τις ανακοινώσεις της ΣΤΑΣΥ.",
            "Nuk kam njoftim live për mbyllje të \(name). Sipas orarit normal, stacioni duhet të jetë në punë. Nëse është urgjente, kontrollo njoftimet e STASY.",
            "Non ho un avviso di chiusura in tempo reale per \(name). In base all'orario normale, la stazione dovrebbe essere operativa. Controlla gli avvisi ufficiali di STASY se \u{00E8} urgente.")
        if advisory.severeWeather {
            return base + " " + t("Severe weather is in effect, so allow extra time.",
                "Επικρατεί κακοκαιρία, οπότε άφησε περιθώριο.",
                "Ka mot të keq, ndaj lër kohë shtesë.",
                "Il maltempo \u{00E8} in corso, quindi prevedi tempo extra.")
        }
        return base
    }

    /// Station names in every language the matcher should look for in notices.
    private func stationSearchNames(_ st: TransitStation) -> [String] {
        Array(Set([st.name, st.nameEl].filter { !$0.isEmpty }))
    }

    /// Projects the iOS STASY announcement feed into `ServiceNotice`s. `text` is
    /// the localized title for read-back; `searchText` concatenates every
    /// language so station-name matching works regardless of the notice language.
    /// `affectedLineIds` and `severity` now come straight from the feed, so iOS
    /// matches the KMP path: a line-wide advisory reaches any station on that
    /// line, and a real closure reads as a closure.
    private func currentNotices() -> [ServiceNotice] {
        alertsService.announcements
            .filter { $0.category == .serviceAlert || AdvisorySeverity.fromRaw($0.severity) != .info }
            .map { a in
                ServiceNotice(
                    id: a.id,
                    text: a.displayTitle(language: loc.language),
                    affectedLineIds: a.affectedLines,
                    severity: AdvisorySeverity.fromRaw(a.severity),
                    validFrom: a.validFrom,
                    validUntil: a.validUntil,
                    searchText: [a.title, a.titleEn, a.titleSq, a.summary, a.summaryEn, a.summarySq]
                        .filter { !$0.isEmpty }.joined(separator: " ")
                )
            }
    }

    // MARK: - Time-anchored planning

    private func resolvePlanByArrival(from: String?, to: String?, absMin: Int?, relMin: Int?) -> AriadneMessage {
        guard let fromId = from else { return bot(clarify(.originStation)) }
        guard let toId = to else { return bot(clarify(.destinationStation)) }

        guard let plan = JourneyPlanner.plan(from: fromId, to: toId, language: loc.language) else {
            return bot(noRouteText(from: name(fromId), to: name(toId)))
        }
        let duration = plan.totalMinutes

        let cal = Calendar(identifier: .gregorian)
        let tz = TimeZone(identifier: "Europe/Athens") ?? .current
        var comps = cal.dateComponents(in: tz, from: Date())
        let nowMin = (comps.hour ?? 0) * 60 + (comps.minute ?? 0)

        let targetMin: Int
        if let absMin { targetMin = absMin }
        else if let relMin { targetMin = nowMin + relMin }
        else { return bot(clarify(.destinationStation)) }

        let effective = (targetMin < nowMin && absMin != nil) ? targetMin + 24 * 60 : targetMin
        let leaveByMin = effective - duration
        let slack = leaveByMin - nowMin

        let fromName = name(fromId)
        let toName = name(toId)
        let leaveLabel = formatClock(leaveByMin % (24 * 60))
        let arriveLabel = formatClock(effective % (24 * 60))

        if slack < 0 {
            return bot(arrivalMissed(to: toName, arrive: arriveLabel, minutesOver: -slack), confidence: .scheduled)
        }
        if slack < 5 {
            return bot(arrivalTight(from: fromName, leaveBy: leaveLabel, to: toName, arrive: arriveLabel, slack: slack), confidence: .scheduled)
        }
        if slack > duration + 45 {
            return bot(arrivalEarly(from: fromName, leaveBy: leaveLabel, to: toName, arrive: arriveLabel, slack: slack), confidence: .scheduled)
        }
        return bot(arrivalOk(from: fromName, leaveBy: leaveLabel, to: toName, arrive: arriveLabel, slack: slack), confidence: .scheduled)
    }

    private func name(_ id: String) -> String {
        for line in SyrmosData.lines {
            for s in SyrmosData.stations(for: line.id) where s.id == id {
                return loc.language == .greek ? s.nameEl : s.name
            }
        }
        return id
    }

    private func formatClock(_ minutes: Int) -> String {
        let h = (minutes / 60) % 24
        let m = minutes % 60
        return String(format: "%02d:%02d", h, m)
    }

    private func noRouteText(from: String, to: String) -> String {
        switch loc.language {
        case .greek: return "Δεν βρήκα διαδρομή από \(from) προς \(to)."
        case .albanian: return "S'gjeta rrugë nga \(from) për te \(to)."
        case .italian: return "Non ho trovato un percorso da \(from) a \(to)."
        case .english: return "I couldn't find a route from \(from) to \(to)."
        }
    }
    private func arrivalOk(from: String, leaveBy: String, to: String, arrive: String, slack: Int) -> String {
        switch loc.language {
        case .greek: return "Ξεκίνα από \(from) έως \(leaveBy) και θα είσαι στο \(to) στις \(arrive). \(slack) λεπτά περιθώριο."
        case .albanian: return "Nis nga \(from) deri në \(leaveBy) dhe do të jesh në \(to) në \(arrive). \(slack) minuta hapësirë."
        case .italian: return "Parti da \(from) entro le \(leaveBy) e arriverai a \(to) entro le \(arrive). Hai \(slack) min di margine."
        case .english: return "Leave \(from) by \(leaveBy) and you'll be at \(to) by \(arrive). \(slack) min to spare."
        }
    }
    private func arrivalTight(from: String, leaveBy: String, to: String, arrive: String, slack: Int) -> String {
        switch loc.language {
        case .greek: return "Στριμωγμένα. Πρέπει να είσαι εκτός από \(from) μέσα στα επόμενα \(slack) λεπτά για να προλάβεις στο \(to) στις \(arrive)."
        case .albanian: return "Ngushtë. Duhet të nisesh nga \(from) brenda \(slack) minutash për të arritur në \(to) në \(arrive)."
        case .italian: return "Tempi stretti. Devi partire da \(from) entro \(slack) min per arrivare a \(to) entro le \(arrive)."
        case .english: return "Tight. You need to leave \(from) within the next \(slack) min to make \(to) by \(arrive)."
        }
    }
    private func arrivalMissed(to: String, arrive: String, minutesOver: Int) -> String {
        switch loc.language {
        case .greek: return "Δύσκολο. Για να είσαι στο \(to) στις \(arrive) θα έπρεπε να έχεις ξεκινήσει πριν \(minutesOver) λεπτά."
        case .albanian: return "E vështirë. Për të qenë në \(to) në \(arrive) duhej të kishe nisur \(minutesOver) minuta më parë."
        case .italian: return "Sei al limite. Per arrivare a \(to) entro le \(arrive) avresti dovuto partire \(minutesOver) min fa."
        case .english: return "Cutting it close. To make \(to) by \(arrive) you'd have needed to leave \(minutesOver) min ago."
        }
    }
    private func arrivalEarly(from: String, leaveBy: String, to: String, arrive: String, slack: Int) -> String {
        switch loc.language {
        case .greek: return "Έχεις άπλα. Ξεκίνα από \(from) όποτε θες μέσα στα επόμενα \(slack) λεπτά και θα φτάσεις στο \(to) στις \(arrive)."
        case .albanian: return "Ke kohë. Nisu nga \(from) kur të duash brenda \(slack) minutash dhe do të jesh në \(to) në \(arrive)."
        case .italian: return "Hai tempo. Parti da \(from) in qualsiasi momento nei prossimi \(slack) min e arriverai a \(to) entro le \(arrive)."
        case .english: return "You have time. Leave \(from) anytime in the next \(slack) min and you'll reach \(to) by \(arrive)."
        }
    }

    // MARK: - Weather

    private func resolveWeather(stationId: String?) async -> AriadneMessage {
        let anchor = weatherAnchor(stationId: stationId)
        guard let anchor else {
            if let snap = weather.snapshot {
                return bot(formatWeather(snap: snap, placeName: snap.placeName), confidence: .live)
            }
            return bot(weatherUnavailable(), confidence: .estimated)
        }
        // Trigger a fresh fetch; iOS WeatherStore refreshes centrally, so
        // we just ask for a refresh at that coord and read snapshot back.
        await weather.refresh(latitude: anchor.lat, longitude: anchor.lng, placeName: anchor.name)
        if let snap = weather.snapshot {
            return bot(formatWeather(snap: snap, placeName: anchor.name), confidence: .live)
        }
        return bot(weatherUnavailable(), confidence: .estimated)
    }

    private struct WeatherAnchor { let lat: Double; let lng: Double; let name: String }

    private func weatherAnchor(stationId: String?) -> WeatherAnchor? {
        if let stationId {
            for line in SyrmosData.lines {
                for s in SyrmosData.stations(for: line.id) where s.id == stationId {
                    let name = loc.language == .greek ? s.nameEl : s.name
                    return WeatherAnchor(lat: s.coordinate.latitude, lng: s.coordinate.longitude, name: name)
                }
            }
        }
        // No explicit station: use the nearest station LocationService
        // already computes, so weather reflects where the user actually is.
        if let nearest = location.nearbyStations.first {
            let node = nearest.station
            let name = loc.language == .greek ? node.nameEl : node.displayName
            return WeatherAnchor(lat: node.coordinate.latitude, lng: node.coordinate.longitude, name: name)
        }
        return nil
    }

    private func formatWeather(snap: WeatherSnapshot, placeName: String) -> String {
        let tempC = Int(snap.current.temperatureC.rounded())
        let feels = Int(snap.current.apparentC.rounded())
        let cond = conditionLabel(snap.current.condition)
        let ageMin = max(0, Int(Date().timeIntervalSince(snap.fetchedAt)) / 60)
        let ageSuffix: String = ageMin >= 5 ? {
            switch loc.language {
            case .greek: return " (πριν \(ageMin) λεπτά)"
            case .albanian: return " (\(ageMin) min më parë)"
            case .italian: return " (\(ageMin) min fa)"
            case .english: return " (\(ageMin) min ago)"
            }
        }() : ""
        switch loc.language {
        case .greek:  return "\(placeName) τώρα: \(tempC)°C, \(cond). Αίσθηση \(feels)°C.\(ageSuffix)"
        case .albanian: return "\(placeName) tani: \(tempC)°C, \(cond). Ndihet si \(feels)°C.\(ageSuffix)"
        case .italian: return "\(placeName) adesso: \(tempC)°C, \(cond). Percepiti \(feels)°C.\(ageSuffix)"
        case .english: return "\(placeName) right now: \(tempC)°C, \(cond). Feels like \(feels)°C.\(ageSuffix)"
        }
    }

    private func conditionLabel(_ c: WeatherCondition) -> String {
        switch loc.language {
        case .greek:
            switch c {
            case .clear: return "καθαρός"
            case .partlyCloudy: return "μερική συννεφιά"
            case .cloudy: return "συννεφιασμένος"
            case .fog: return "ομίχλη"
            case .drizzle: return "ψιχάλα"
            case .rain: return "βροχή"
            case .snow: return "χιόνι"
            case .showers: return "μπόρες"
            case .thunderstorm: return "καταιγίδα"
            case .unknown: return "άγνωστη"
            }
        case .albanian:
            switch c {
            case .clear: return "kthjellët"
            case .partlyCloudy: return "pjesërisht i vranët"
            case .cloudy: return "i vranët"
            case .fog: return "mjegull"
            case .drizzle: return "shi i lehtë"
            case .rain: return "shi"
            case .snow: return "borë"
            case .showers: return "reshje"
            case .thunderstorm: return "stuhi"
            case .unknown: return "e panjohur"
            }
        case .italian:
            switch c {
            case .clear: return "sereno"
            case .partlyCloudy: return "parzialmente nuvoloso"
            case .cloudy: return "nuvoloso"
            case .fog: return "nebbia"
            case .drizzle: return "pioviggine"
            case .rain: return "pioggia"
            case .snow: return "neve"
            case .showers: return "rovesci"
            case .thunderstorm: return "temporale"
            case .unknown: return "sconosciuto"
            }
        case .english:
            switch c {
            case .clear: return "clear"
            case .partlyCloudy: return "partly cloudy"
            case .cloudy: return "cloudy"
            case .fog: return "foggy"
            case .drizzle: return "drizzling"
            case .rain: return "raining"
            case .snow: return "snowing"
            case .showers: return "showery"
            case .thunderstorm: return "thunderstorm"
            case .unknown: return "unknown"
            }
        }
    }

    /// No live reading: fall back to the honest Athens seasonal norm ("usually
    /// hot and dry this time of year") rather than a dead end, phrased as
    /// "usually", never "now". Matches the KMP `weatherUnavailableText`.
    private func weatherUnavailable() -> String {
        let ctx = weatherContext()
        if ctx.source == .seasonalFallback {
            let typical = seasonalClause(ctx)
            return t("I don't have live weather right now, but Athens this time of year is usually \(typical).",
                "Δεν έχω ζωντανό καιρό τώρα, αλλά η Αθήνα αυτή την εποχή είναι συνήθως \(typical).",
                "Nuk kam mot live tani, por Athina në këtë periudhë zakonisht është \(typical).",
                "Non ho il meteo in tempo reale adesso, ma Atene in questo periodo \u{00E8} di solito \(typical).")
        }
        return t("I don't have weather data yet. Try again when you're online.",
            "Δεν έχω ακόμα δεδομένα καιρού. Δοκίμασε ξανά όταν είσαι online.",
            "Ende s'kam të dhëna moti. Provo përsëri kur je online.",
            "Non ho ancora dati meteo. Riprova quando sei online.")
    }

    private func catJoke() -> String {
        let jokes: [String]
        switch loc.language {
        case .greek:
            jokes = [
                "Γιατί οι γάτες δεν παίζουν πόκερ στη ζούγκλα; Έχει πολλά τσιτάχ.",
                "Πώς λέγεται μια στοίβα γατάκια; Μιαοβούνο.",
                "Τι κάνει ένας γάτος στον υπολογιστή; Προσέχει το ποντίκι.",
                "Γιατί ο γάτος πήγε στο νοσοκομείο; Είχε πυρετό αγέλας.",
                "Πώς τελειώνει η μάχη δύο γάτων; Με ένα σφύριγμα και ένα μιάου.",
            ]
        case .albanian:
            jokes = [
                "Pse macet nuk luajnë poker në xhungël? Sepse ka shumë çita.",
                "Si e quajnë një grumbull macesh të vogla? Një mjaumal.",
                "Pse ishte macja ulur mbi kompjuter? Për të vëzhguar miun.",
                "Cila është ëmbëlsira e preferuar e maces? Muslet me çokollatë.",
                "Si e mbyllin macet një grindje? Me një fshirje dhe një mjau.",
            ]
        case .italian:
            jokes = [
                "Perché i gatti non giocano a poker nella giungla? Ci sono troppi ghepardi.",
                "Come si chiama un mucchio di gattini? Una montagna di miao.",
                "Perché il gatto era seduto sul computer? Per tenere d'occhio il mouse.",
                "Qual è il dolce preferito del gatto? La mousse al cioccolato.",
                "Come concludono una lite due gatti? Soffiano e fanno pace.",
            ]
        default:
            jokes = [
                "Why don't cats play poker in the jungle? Too many cheetahs.",
                "What do you call a pile of kittens? A meowntain.",
                "Why was the cat sitting on the computer? To keep an eye on the mouse.",
                "What's a cat's favourite dessert? Chocolate mousse.",
                "How do two cats end a fight? They hiss and make up.",
            ]
        }
        return jokes.randomElement() ?? jokes[0]
    }

    private func resolveDepartures(stationId: String?, lineId: String?, day: DayContext) -> AriadneMessage {
        guard let station = resolveStation(stationId: stationId, lineId: lineId) else {
            return bot(clarify(.station))
        }
        let lineIds = lineId.map { [$0] } ?? station.lineIds

        if day != .today {
            return resolveDeparturesForDay(day: day, station: station, lineIds: lineIds)
        }

        let deps = ScheduleProjector.nextDepartures(for: station.id, lineIds: lineIds, limit: 4)
        if deps.isEmpty {
            return bot(t("No more trains from \(name(station)) right now.",
                "Δεν υπάρχουν άλλα δρομολόγια από \(name(station)) τώρα.",
                "Nuk ka më trena nga \(name(station)) tani.",
                "Nessun altro treno da \(name(station)) adesso."))
        }
        let header = t("Next from \(name(station)):", "Επομενα απο \(name(station)):", "Te ardhshmet nga \(name(station)):", "Prossimi da \(name(station)):")
        let label = t("Open \(name(station))", "Ανοιγμα \(name(station))", "Hap \(name(station))", "Apri \(name(station))")
        return AriadneMessage(fromUser: false, text: header, departures: Array(deps.prefix(4)), sourceConfidence: .scheduled, action: .openStation(station.id), actionLabel: label)
    }

    /// "this weekend / tomorrow / Saturday": project that whole service day from
    /// 00:00 via the projector's dayOffset path. Times are shown as text (a
    /// countdown from midnight is meaningless for a future day).
    private func resolveDeparturesForDay(day: DayContext, station: TransitStation, lineIds: [String]) -> AriadneMessage {
        let offset = Self.dayOffset(for: day)
        let deps = ScheduleProjector.nextDepartures(for: station.id, lineIds: lineIds, limit: 4, dayOffset: offset)
        let label = dayLabel(day)
        if deps.isEmpty {
            return bot(t("I don't have \(label)'s schedule for \(name(station)) offline.",
                "Δεν έχω το πρόγραμμα \(label) για \(name(station)) εκτός σύνδεσης.",
                "Nuk e kam orarin \(label) për \(name(station)) pa internet.",
                "Non ho l'orario di \(label) per \(name(station)) offline."), confidence: .offline)
        }
        let times = deps.prefix(4).map { "\($0.lineId) \($0.time)" }.joined(separator: ", ")
        return bot(t("First trains \(label) from \(name(station)): \(times).",
            "Πρώτα δρομολόγια \(label) από \(name(station)): \(times).",
            "Trenat e parë \(label) nga \(name(station)): \(times).",
            "Primi treni \(label) da \(name(station)): \(times)."), confidence: .scheduled)
    }

    /// Days from today to the requested DayContext (Athens calendar). Mirrors
    /// the KMP DayContextResolver.
    private static func dayOffset(for day: DayContext) -> Int {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Europe/Athens") ?? .current
        // Calendar weekday: Sunday = 1 ... Saturday = 7. Convert to ISO Mon=1..Sun=7.
        let weekday = cal.component(.weekday, from: Date())
        let iso = weekday == 1 ? 7 : weekday - 1
        switch day {
        case .today: return 0
        case .tomorrow: return 1
        case .saturday: return (6 - iso + 7) % 7
        case .sunday: return (7 - iso + 7) % 7
        case .weekend: return iso >= 6 ? 0 : 6 - iso
        }
    }

    private func dayLabel(_ day: DayContext) -> String {
        switch day {
        case .tomorrow: return t("tomorrow", "αύριο", "nesër", "domani")
        case .weekend: return t("this weekend", "το Σαββατοκύριακο", "këtë fundjavë", "questo fine settimana")
        case .saturday: return t("Saturday", "το Σάββατο", "të shtunën", "sabato")
        case .sunday: return t("Sunday", "την Κυριακή", "të dielën", "domenica")
        case .today: return t("today", "σήμερα", "sot", "oggi")
        }
    }

    // Swift mirror of the KMP ComputeFareUseCase (core/domain) + the web engine.
    // Grounded operator tables; intercity is dynamic (booking-priced), never a
    // fabricated number. See docs/data/2026-07-27-fares-collection.md.
    private struct FareResult {
        let full: Double?; let reduced: Double?; let product: String; let op: String; let dynamic: Bool
    }
    private static let intercityReferenceFares: [String: Double] = [
        routeFareKey("GR_ATH", "GR_THE"): 43.00,
        routeFareKey("GR_ATH", "GR_LAR"): 32.50,
        routeFareKey("GR_ATH", "KB_TRI"): 29.50,
        routeFareKey("GR_ATH", "KB_KAL"): 30.90,
        routeFareKey("GR_THE", "GR_LAR"): 14.00,
        routeFareKey("KB_TRI", "KB_KAL"): 1.80,
    ]
    private static func routeFareKey(_ fromId: String, _ toId: String) -> String {
        [fromId, toId].sorted().joined(separator: "|")
    }
    private func stationRegions(_ st: TransitStation) -> Set<TransitRegion> {
        Set(st.lineIds.compactMap { SyrmosData.line(for: $0)?.region })
    }
    private func isThessSuburbanStation(_ st: TransitStation) -> Bool {
        st.lineIds.contains { id in
            guard let l = SyrmosData.line(for: id) else { return false }
            return l.region == .thessaloniki && l.type == .suburban
        }
    }
    private func computeFare(_ from: TransitStation, _ to: TransitStation) -> FareResult {
        // Charge on the local network the two stations SHARE; else intercity.
        let fr = stationRegions(from), tr = stationRegions(to)
        let local = fr.first { $0 != .national && tr.contains($0) }
        switch local {
        case .athens:
            return (isAirportStation(from.id) || isAirportStation(to.id))
                ? FareResult(full: 9.00, reduced: 4.50, product: "Airport Metro ticket (Line 3)", op: "OASA", dynamic: false)
                : FareResult(full: 1.20, reduced: 0.50, product: "90-minute integrated ticket", op: "OASA", dynamic: false)
        case .thessaloniki:
            return (isThessSuburbanStation(from) || isThessSuburbanStation(to))
                ? FareResult(full: 0.80, reduced: 0.40, product: "Suburban single", op: "OSETH", dynamic: false)
                : FareResult(full: 0.60, reduced: 0.30, product: "Urban single", op: "OSETH", dynamic: false)
        case .patras:
            return FareResult(full: 1.40, reduced: 1.00, product: "Suburban zone ticket", op: "Hellenic Train", dynamic: false)
        default:
            return FareResult(
                full: Self.intercityReferenceFares[Self.routeFareKey(from.id, to.id)],
                reduced: nil,
                product: "Intercity / regional",
                op: "Hellenic Train",
                dynamic: true
            )
        }
    }

    private func resolveFare(airport: Bool, from: String?, to: String?) -> AriadneMessage {
        // Grounded from -> to fare when both endpoints are known (same engine as
        // the web planner + KMP); otherwise the generic product list below.
        if let fid = from, let tid = to, let fs = station(fid), let ts = station(tid) {
            let q = computeFare(fs, ts)
            let a = name(fs); let b = name(ts)
            if q.dynamic {
                if let reference = q.full {
                    return bot(t(
                        "\(a) to \(b): approximately \(money(reference)) for a standard one-way ticket, checked on 5 August 2026. The exact price can change by train, date, class, availability, discount, or replacement-bus connection. Verify it in Hellenic Train booking.",
                        "\(a) προς \(b): περίπου \(money(reference)) για απλό εισιτήριο μίας διαδρομής, με έλεγχο στις 5 Αυγούστου 2026. Η τελική τιμή αλλάζει ανά τρένο, ημερομηνία, θέση, διαθεσιμότητα, έκπτωση ή σύνδεση με λεωφορείο αντικατάστασης. Επιβεβαίωσέ την στην κράτηση της Hellenic Train.",
                        "\(a) në \(b): afërsisht \(money(reference)) për një biletë standarde vetëm vajtje, kontrolluar më 5 gusht 2026. Çmimi i saktë mund të ndryshojë sipas trenit, datës, klasës, disponueshmërisë, zbritjes ose lidhjes me autobus zëvendësues. Verifikoje në rezervimin e Hellenic Train.",
                        "\(a) a \(b): circa \(money(reference)) per un biglietto standard di sola andata, verificato il 5 agosto 2026. Il prezzo esatto può cambiare in base a treno, data, classe, disponibilità, sconto o collegamento con autobus sostitutivo. Verificalo nella prenotazione Hellenic Train."))
                }
                return bot(t(
                    "\(a) → \(b) is an intercity trip — the price is set at booking (route, date, class). Discounts include early-booking up to 15%, return 20% and students up to 50%. Book on hellenictrain.gr for the exact fare.",
                    "\(a) → \(b) είναι υπεραστικό δρομολόγιο — η τιμή ορίζεται στην κράτηση (διαδρομή, ημέρα, θέση). Εκπτώσεις: έγκαιρη κράτηση έως 15%, επιστροφή 20%, φοιτητές έως 50%. Κάνε κράτηση στο hellenictrain.gr.",
                    "\(a) → \(b) është udhëtim ndërqytetës, çmimi caktohet në rezervim (rruga, dita, klasa). Zbritje: rezervim i hershëm deri 15%, kthim 20%, studentë deri 50%. Rezervo në hellenictrain.gr.",
                    "\(a) → \(b) \u{00E8} un viaggio interurbano: il prezzo viene stabilito alla prenotazione (tratta, data, classe). Sconti: prenotazione anticipata fino al 15%, andata e ritorno 20%, studenti fino al 50%. Prenota su hellenictrain.gr per la tariffa esatta."))
            }
            let reduced = q.reduced.map { " (\(t("reduced", "μειωμένο", "e reduktuar", "ridotto")) \(money($0)))" } ?? ""
            return bot("\(a) → \(b): \(money(q.full))\(reduced). \(q.product) · \(q.op)")
        }
        let products = SyrmosFaresStore.shared.products
        if products.isEmpty {
            return bot(t("I don't have fare prices available offline right now.",
                "Δεν έχω διαθέσιμες τιμές εισιτηρίων εκτός σύνδεσης τώρα.",
                "Nuk kam çmime biletash të disponueshme pa internet tani.",
                "Non ho i prezzi dei biglietti disponibili offline adesso."))
        }
        // Journey-derived: airport fare if the word was used OR either endpoint
        // is actually an airport station.
        let isAirport = airport || isAirportStation(from) || isAirportStation(to)
        let picks: [SyrmosFaresStore.Product]
        if isAirport {
            picks = products.filter { $0.tags.contains { $0.contains("airport") } }
                .sorted { ($0.fullPriceEur ?? .greatestFiniteMagnitude) < ($1.fullPriceEur ?? .greatestFiniteMagnitude) }
        } else {
            picks = products.filter { $0.section == "single" }
                .sorted { ($0.fullPriceEur ?? .greatestFiniteMagnitude) < ($1.fullPriceEur ?? .greatestFiniteMagnitude) }
        }
        let chosen = (picks.isEmpty ? Array(products.prefix(2)) : Array(picks.prefix(2)))
        let line = chosen.map { "\($0.localizedTitle(loc.language)) \(money($0.fullPriceEur))" }.joined(separator: " · ")
        return bot(line)
    }

    private func isAirportStation(_ stationId: String?) -> Bool {
        guard let id = stationId, let st = station(id) else { return false }
        let name = (st.name + " " + st.nameEl).lowercased()
        return name.contains("airport") || name.contains("αεροδρ")
    }

    private func resolveFavorite(stationId: String?) -> AriadneMessage {
        guard let id = stationId else { return bot(clarify(.station)) }
        let st = station(id)
        let label = st.map { name($0) } ?? id
        let nowFavorite = toggleFavorite(id)
        return bot(nowFavorite
            ? t("Added \(label) to your favorites.", "Πρόσθεσα τον \(label) στα αγαπημένα σου.", "Shtova \(label) te të preferuarat e tua.", "Aggiunto \(label) ai tuoi preferiti.")
            : t("Removed \(label) from your favorites.", "Αφαίρεσα τον \(label) από τα αγαπημένα σου.", "Hoqa \(label) nga të preferuarat e tua.", "Rimosso \(label) dai tuoi preferiti."))
    }

    /// Favorites persistence on iOS (UserDefaults set of station ids). Returns
    /// the new state. Parallels the KMP FavoritesRepository.
    private func toggleFavorite(_ stationId: String) -> Bool {
        let key = "syrmos.favorite.stations"
        var set = Set(UserDefaults.standard.stringArray(forKey: key) ?? [])
        let nowFavorite: Bool
        if set.contains(stationId) { set.remove(stationId); nowFavorite = false }
        else { set.insert(stationId); nowFavorite = true }
        UserDefaults.standard.set(Array(set), forKey: key)
        return nowFavorite
    }

    private func money(_ amount: Double?) -> String {
        guard let amount else { return "" }
        let cents = Int((amount * 100).rounded())
        return "€\(cents / 100).\(String(format: "%02d", cents % 100))"
    }

    private func resolveLastTrain(stationId: String?, lineId: String?) -> AriadneMessage {
        guard let station = resolveStation(stationId: stationId, lineId: lineId) else {
            return bot(clarify(.station))
        }
        let line = lineId ?? station.lineIds.first
        guard let line else { return bot(clarify(.station)) }
        guard let last = ScheduleProjector.lastTrainTonight(for: station.id, lineIds: [line]) else {
            return bot(t("Service is over for tonight at \(name(station)).",
                "Τα δρομολόγια για απόψε τελείωσαν στον \(name(station)).",
                "Shërbimi për sonte ka mbaruar te \(name(station)).",
                "Il servizio per stasera \u{00E8} terminato a \(name(station))."), confidence: .scheduled)
        }
        let label = t("Open \(name(station))", "Ανοιγμα \(name(station))", "Hap \(name(station))", "Apri \(name(station))")
        return bot(t("Last \(displayLine(last.lineId)) from \(name(station)) leaves at \(last.time). Leave by then.",
            "Ο τελευταίος \(displayLine(last.lineId)) από \(name(station)) φεύγει \(last.time). Φύγε ως τότε.",
            "Treni i fundit \(displayLine(last.lineId)) nga \(name(station)) niset \(last.time). Nisu deri atëherë.",
            "L'ultimo \(displayLine(last.lineId)) da \(name(station)) parte alle \(last.time). Parti entro allora."), confidence: .scheduled, action: .openStation(station.id), actionLabel: label)
    }

    /// First / earliest scheduled train of today at a station (mirror of last train).
    private func resolveFirstTrain(stationId: String?, lineId: String?) -> AriadneMessage {
        guard let station = resolveStation(stationId: stationId, lineId: lineId) else {
            return bot(clarify(.station))
        }
        let lineIds = lineId.map { [$0] } ?? station.lineIds
        // Project the whole service day from 00:00 and take the earliest slot.
        let deps = ScheduleProjector.nextDepartures(for: station.id, lineIds: lineIds, limit: 4, dayOffset: 0)
        guard let first = deps.min(by: { $0.minutesAway < $1.minutesAway }) else {
            return bot(t("I don't have today's schedule for \(name(station)) offline.",
                "Δεν έχω το σημερινό πρόγραμμα για \(name(station)) εκτός σύνδεσης.",
                "Nuk e kam orarin e sotëm për \(name(station)) pa internet.",
                "Non ho l'orario di oggi per \(name(station)) offline."), confidence: .offline)
        }
        let label = t("Open \(name(station))", "Ανοιγμα \(name(station))", "Hap \(name(station))", "Apri \(name(station))")
        return bot(t("First \(displayLine(first.lineId)) from \(name(station)) is at \(first.time).",
            "Το πρώτο \(displayLine(first.lineId)) από \(name(station)) είναι στις \(first.time).",
            "Treni i parë \(displayLine(first.lineId)) nga \(name(station)) është në \(first.time).",
            "Il primo \(displayLine(first.lineId)) da \(name(station)) \u{00E8} alle \(first.time)."), confidence: .scheduled, action: .openStation(station.id), actionLabel: label)
    }

    /// Step-free accessibility for one station, from the bundled flag. Never invented.
    private func resolveAccessibility(stationId: String?) -> AriadneMessage {
        guard let id = stationId, let st = station(id) else { return bot(clarify(.station)) }
        let n = name(st)
        if AriadneAccessibility.isAccessible(id) {
            return bot(t("\(n) is step-free accessible (lift / level access).",
                "Ο \(n) είναι προσβάσιμος για ΑμεΑ (ασανσέρ / ισόπεδη πρόσβαση).",
                "\(n) është i aksesueshëm pa shkallë (ashensor / qasje e sheshtë).",
                "\(n) \u{00E8} accessibile senza gradini (ascensore / accesso a livello)."), confidence: .offline)
        }
        return bot(t("\(n) is not marked step-free. Check for stairs-only access before you go.",
            "Ο \(n) δεν είναι σημειωμένος ως προσβάσιμος ΑμεΑ. Ίσως έχει μόνο σκάλες.",
            "\(n) nuk shënohet si i aksesueshëm pa shkallë. Mund të ketë vetëm shkallë.",
            "\(n) non \u{00E8} segnato come accessibile senza gradini. Potrebbe avere solo scale."), confidence: .offline)
    }

    /// "and back?" — reverse the remembered route and re-plan.
    private func resolveReverseTrip() -> AriadneMessage {
        guard let route = session.lastRoute else {
            return bot(t("Tell me a trip first, then I can flip it for the way back.",
                "Πες μου πρώτα μια διαδρομή, μετά τη γυρίζω για την επιστροφή.",
                "Më trego fillimisht një udhëtim, pastaj e kthej për rrugën e kthimit.",
                "Dimmi prima un viaggio, poi lo inverto per il ritorno."))
        }
        return resolvePlanTrip(from: route.toStationId, to: route.fromStationId, lowExposure: false, preference: route.preference)
    }

    /// "Which lines serve X?" — list the lines calling at a station.
    private func resolveWhichLines(stationId: String?) -> AriadneMessage {
        guard let id = stationId, let st = station(id) else { return bot(clarify(.station)) }
        let lineIds = Array(NSOrderedSet(array: st.lineIds.map { displayLine($0) })) as? [String] ?? st.lineIds
        let n = name(st)
        if lineIds.isEmpty {
            return bot(t("I don't have any lines listed for \(n).",
                "Δεν έχω γραμμές καταχωρημένες για \(n).",
                "Nuk kam linja të regjistruara për \(n).",
                "Non ho linee registrate per \(n)."))
        }
        let list = lineIds.joined(separator: ", ")
        let label = t("Open \(n)", "Ανοιγμα \(n)", "Hap \(n)", "Apri \(n)")
        return bot(t("\(n) is served by: \(list).",
            "Ο \(n) εξυπηρετείται από: \(list).",
            "\(n) shërbehet nga: \(list).",
            "\(n) \u{00E8} servita da: \(list)."), confidence: .offline, action: .openStation(id), actionLabel: label)
    }

    /// "How many stops / how far from A to B?" — stop count + rough duration.
    private func resolveStopsBetween(from: String?, to: String?) -> AriadneMessage {
        guard let fromId = from ?? session.currentStation else { return bot(clarify(.originStation)) }
        guard let toId = to else { return bot(clarify(.destinationStation)) }
        guard let plan = JourneyPlanner.plan(from: fromId, to: toId, language: loc.language) else {
            return bot(t("I couldn't find a rail route between those.",
                "Δεν βρήκα σιδηροδρομική διαδρομή ανάμεσά τους.",
                "Nuk gjeta një rrugë hekurudhore mes tyre.",
                "Non ho trovato un percorso ferroviario tra queste."))
        }
        let stops = plan.legs.reduce(0) { $0 + $1.stops }
        let fromN = station(fromId).map { name($0) } ?? fromId
        let toN = station(toId).map { name($0) } ?? toId
        let changePart = plan.transfers == 0
            ? t("direct", "απευθείας", "direkt", "diretto")
            : t("\(plan.transfers) change(s)", "\(plan.transfers) αλλαγή/ές", "\(plan.transfers) ndërrim(e)", "\(plan.transfers) cambio/i")
        return bot(t("\(fromN) to \(toN) is \(stops) stops, about \(plan.totalMinutes) min (\(changePart)).",
            "\(fromN) προς \(toN) είναι \(stops) στάσεις, περίπου \(plan.totalMinutes) λεπτά (\(changePart)).",
            "\(fromN) te \(toN) janë \(stops) stacione, rreth \(plan.totalMinutes) min (\(changePart)).",
            "\(fromN) a \(toN) sono \(stops) fermate, circa \(plan.totalMinutes) min (\(changePart))."), confidence: .scheduled)
    }

    /// Full point-to-point routing via `JourneyPlanner` (Dijkstra), matching
    /// the Android/Web `PlanJourneyUseCase`.
    private func resolvePlanTrip(from: String?, to: String?, lowExposure: Bool, preference: RoutePreference) -> AriadneMessage {
        // Origin falls back to the remembered current station ("I'm at Syntagma").
        guard let fromId = from ?? session.currentStation else { return bot(clarify(.originStation)) }
        guard let toId = to else { return bot(clarify(.destinationStation)) }
        guard let fastest = JourneyPlanner.plan(from: fromId, to: toId, language: loc.language) else {
            return bot(t("I couldn't find a rail route between those.",
                "Δεν βρήκα σιδηροδρομική διαδρομή ανάμεσά τους.",
                "Nuk gjeta një rrugë hekurudhore mes tyre.",
                "Non ho trovato un percorso ferroviario tra queste."))
        }

        // Offer a sheltered all-metro alternative only when the fastest route is
        // exposed (tram / surface) and a distinct metro-only path exists. On a
        // hot or wet day the ranker can then pick the drier option.
        let ctx = weatherContext()
        let fastExposure = routeExposure(fastest)
        var candidates: [RouteCandidate] = [RouteCandidate(result: fastest, exposure: fastExposure)]
        if fastExposure != .sheltered,
           let metro = JourneyPlanner.metroOnly(from: fromId, to: toId, language: loc.language) {
            let distinct = metro.totalMinutes != fastest.totalMinutes || metro.legs.count != fastest.legs.count
            if distinct && routeExposure(metro) == .sheltered {
                candidates.append(RouteCandidate(result: metro, exposure: .sheltered))
            }
        }
        let ranked = RouteRanker.rank(candidates, preference: preference, weather: ctx)
        let best = ranked.first!.candidate.result

        let legs = routeLineText(best)
        let transfers = best.transfers == 0
            ? t("no change", "χωρίς αλλαγή", "pa ndërrim", "nessun cambio")
            : t("\(best.transfers) change(s)", "\(best.transfers) αλλαγή/ές", "\(best.transfers) ndërrim(e)", "\(best.transfers) cambio/i")
        // "This is direct" nod when the user asked for the easiest route.
        let directNote = (preference == .fewestChanges && best.transfers == 0)
            ? " " + t("This is direct, no change needed.", "Είναι απευθείας, χωρίς αλλαγή.", "Është direkt, pa ndërrim.", "\u{00C8} diretto, nessun cambio necessario.")
            : ""
        // When the ranker overrode the fastest route (weather tilt), say why.
        let tradeoff = (best != fastest)
            ? "\n" + t(
                "The faster route (\(routeLineText(fastest)), \(fastest.totalMinutes) min) is more exposed; in this weather I'd take this one.",
                "Η πιο γρήγορη διαδρομή (\(routeLineText(fastest)), \(fastest.totalMinutes) λεπτά) είναι πιο εκτεθειμένη· με αυτόν τον καιρό θα προτιμούσα αυτή.",
                "Rruga më e shpejtë (\(routeLineText(fastest)), \(fastest.totalMinutes) min) është më e ekspozuar; me këtë mot do të zgjidhja këtë.",
                "Il percorso pi\u{00F9} veloce (\(routeLineText(fastest)), \(fastest.totalMinutes) min) \u{00E8} pi\u{00F9} esposto; con questo tempo prenderei questo.")
            : ""
        let exposure = lowExposure ? "\n" + weatherAdvice(best) : ""
        // Surface any STASY advisory that intersects the route. The Swift Plan
        // only carries per-leg lineIds + leg destination names, so route matching
        // runs off the leg line ids and the trip endpoint names.
        let routeStationNames = Array(Set(
            ([name(fromId), name(toId)] + best.legs.map { $0.toName }).filter { !$0.isEmpty }
        ))
        let advisory = ServiceAdvisoryMatcher.forRoute(
            lineIds: best.legs.map { normalizeLine($0.lineId) },
            stationNames: routeStationNames,
            notices: currentNotices(),
            severeWeather: WeatherStore.shared.snapshot?.current.condition.isSevere == true
        )
        let caveat = advisory.top.map { "\n" + t("Heads up: ", "Προσοχή: ", "Kujdes: ", "Attenzione: ") + $0.text } ?? ""
        return bot(t("\(legs). About \(best.totalMinutes) min, \(transfers).",
            "\(legs). Περίπου \(best.totalMinutes) λεπτά, \(transfers).",
            "\(legs). Rreth \(best.totalMinutes) min, \(transfers).",
            "\(legs). Circa \(best.totalMinutes) min, \(transfers).") + directNote + tradeoff + exposure + caveat, confidence: .scheduled)
    }

    /// A route's shelter, from its legs' line types. Mirror of KMP `routeExposure`.
    private func routeExposure(_ plan: JourneyPlanner.Plan) -> Exposure {
        let types = plan.legs.compactMap { SyrmosData.line(for: normalizeLine($0.lineId))?.type }
        return Exposure.forRoute(types)
    }

    /// "M3 Syntagma → M2 Monastiraki" leg text. Mirror of KMP `routeLineText`.
    private func routeLineText(_ plan: JourneyPlanner.Plan) -> String {
        plan.legs.map { "\(displayLine($0.lineId)) \($0.toName)" }.joined(separator: " → ")
    }

    /// "How long to X". Origin is the user's nearest station from GPS, or an
    /// explicitly named origin. With no explicit origin and no location fix (or
    /// permission off), it asks for the origin instead of guessing.
    private func resolveTravelTime(to: String?, from: String?) -> AriadneMessage {
        guard let toId = to else { return bot(clarify(.destinationStation)) }

        let originId: String?
        if let from {
            originId = from
        } else if location.hasPermission, let nearest = location.nearbyStations.first {
            originId = nearest.id
        } else {
            originId = nil
        }
        guard let fromId = originId else {
            // No explicit origin and no usable location: ask which station.
            return bot(clarify(.originStation))
        }
        if fromId == toId {
            return bot(t("You're already at \(stationName(toId)).",
                "Είσαι ήδη στον \(stationName(toId)).",
                "Je tashmë te \(stationName(toId)).",
                "Sei già a \(stationName(toId))."))
        }
        guard let plan = JourneyPlanner.plan(from: fromId, to: toId, language: loc.language) else {
            return bot(t("I couldn't find a rail route between those.",
                "Δεν βρήκα σιδηροδρομική διαδρομή ανάμεσά τους.",
                "Nuk gjeta një rrugë hekurudhore mes tyre.",
                "Non ho trovato un percorso ferroviario tra queste stazioni."))
        }
        let transfers = plan.transfers == 0
            ? t("no change", "χωρίς αλλαγή", "pa ndërrim", "nessun cambio")
            : t("\(plan.transfers) change(s)", "\(plan.transfers) αλλαγή/ές", "\(plan.transfers) ndërrim(e)", "\(plan.transfers) cambio/i")
        return bot(t("About \(plan.totalMinutes) min from \(stationName(fromId)) to \(stationName(toId)), \(transfers).",
            "Περίπου \(plan.totalMinutes) λεπτά από \(stationName(fromId)) προς \(stationName(toId)), \(transfers).",
            "Rreth \(plan.totalMinutes) min nga \(stationName(fromId)) te \(stationName(toId)), \(transfers).",
            "Circa \(plan.totalMinutes) min da \(stationName(fromId)) a \(stationName(toId)), \(transfers)."), confidence: .scheduled)
    }

    private func stationName(_ id: String) -> String {
        guard let s = allStations().first(where: { $0.id == id }) else { return id }
        return name(s)
    }

    /// Real weather-aware advice for a rainy-day route: reads the cached weather
    /// snapshot and the route's exposure (metro is underground/sheltered, tram
    /// is open-air). Degrades honestly when there's no cached weather.
    private func weatherAdvice(_ plan: JourneyPlanner.Plan) -> String {
        let types = plan.legs.compactMap { SyrmosData.line(for: $0.lineId)?.type }
        let exposure = Exposure.forRoute(types)
        return weatherAdviceText(weatherContext(), exposure)
    }

    /// Live snapshot wins; else the Athens seasonal profile for this month.
    private func weatherContext() -> WeatherContext {
        WeatherContextBuilder.resolve(snapshot: WeatherStore.shared.snapshot, month: currentAthensMonth())
    }

    /// The current calendar month (1..12) in Athens local time. Reuses the same
    /// Europe/Athens gregorian calendar the arrival planner and day-offset use.
    private func currentAthensMonth() -> Int {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "Europe/Athens") ?? .current
        return cal.component(.month, from: Date())
    }

    private func shelterClause(_ exposure: Exposure) -> String {
        switch exposure {
        case .sheltered:
            return t("mostly underground and sheltered", "κυρίως υπόγεια και υπό στέγη",
                "kryesisht nëntokë dhe e mbrojtur", "perlopiù sotterraneo e riparato")
        case .mixed:
            return t("partly at surface level", "εν μέρει σε επιφάνεια", "pjesërisht në sipërfaqe",
                "in parte in superficie")
        case .exposed:
            return t("open-air (tram/surface stops)", "σε ανοιχτό χώρο (τραμ/επιφάνεια)",
                "në ajër të hapur (tram/sipërfaqe)", "all'aperto (tram/fermate in superficie)")
        }
    }

    /// Composes honest weather advice: live ("It's hot in Athens right now"),
    /// seasonal ("Athens this time of year is usually hot and dry"), or unknown,
    /// plus the route's shelter and an optional nudge when exposure matters.
    private func weatherAdviceText(_ ctx: WeatherContext, _ exposure: Exposure) -> String {
        let shelter = shelterClause(exposure)
        let nudge = weatherNudge(ctx.state, exposure)
        let lead: String
        switch ctx.source {
        case .live, .forecast:
            let place = ctx.placeName ?? t("Athens", "Αθήνα", "Athinë", "Atene")
            let now = liveStateClause(ctx.state)
            lead = t("\(now) in \(place) right now, and this route is \(shelter).",
                "\(now) στην \(place) τώρα, και η διαδρομή είναι \(shelter).",
                "\(now) në \(place) tani, dhe kjo rrugë është \(shelter).",
                "\(now) a \(place) in questo momento e questo percorso è \(shelter).")
        case .seasonalFallback:
            let typical = seasonalClause(ctx)
            lead = t("I don't have live weather right now, but Athens this time of year is usually \(typical). This route is \(shelter).",
                "Δεν έχω ζωντανό καιρό τώρα, αλλά η Αθήνα αυτή την εποχή είναι συνήθως \(typical). Η διαδρομή είναι \(shelter).",
                "Nuk kam mot live tani, por Athina në këtë periudhë zakonisht është \(typical). Kjo rrugë është \(shelter).",
                "Non ho il meteo in tempo reale, ma Atene in questo periodo è di solito \(typical). Questo percorso è \(shelter).")
        case .unknown:
            lead = t("I can't check the weather offline, but this route is \(shelter).",
                "Δεν μπορώ να δω τον καιρό εκτός σύνδεσης, αλλά η διαδρομή είναι \(shelter).",
                "Nuk e kontrolloj dot motin pa internet, por kjo rrugë është \(shelter).",
                "Non posso controllare il meteo offline, ma questo percorso è \(shelter).")
        }
        return nudge.isEmpty ? lead : "\(lead) \(nudge)"
    }

    private func liveStateClause(_ state: WeatherState) -> String {
        switch state {
        case .rainy: return t("It's wet", "Έχει βροχή", "Ka shi", "Piove")
        case .hot: return t("It's hot", "Έχει ζέστη", "Bën vapë", "Fa caldo")
        case .windy: return t("It's windy", "Έχει αέρα", "Ka erë", "C'è vento")
        case .normal: return t("It's calm", "Ο καιρός είναι ήπιος", "Moti është i qetë", "Il tempo è mite")
        }
    }

    private func seasonalClause(_ ctx: WeatherContext) -> String {
        let month = ctx.month ?? 0
        if ctx.state == .hot {
            return t("hot and dry", "ζεστά και ξηρά", "e nxehtë dhe e thatë", "caldo e secco")
        }
        if [11, 12, 1, 2].contains(month) {
            return t("cooler, with rain possible", "πιο δροσερά, με πιθανή βροχή", "më e freskët, me mundësi shiu",
                "più fresco, con possibile pioggia")
        }
        return t("mild", "ήπια", "e butë", "mite")
    }

    /// Optional nudge toward shelter when the weather makes exposure matter.
    private func weatherNudge(_ state: WeatherState, _ exposure: Exposure) -> String {
        if exposure == .sheltered { return "" }
        switch state {
        case .rainy:
            return t("A more underground option would keep you drier.",
                "Μια πιο υπόγεια επιλογή θα σε κρατούσε πιο στεγνό.",
                "Një opsion më nëntokësor do të të mbante më të thatë.",
                "Un'opzione più sotterranea ti terrebbe più al riparo dalla pioggia.")
        case .hot:
            return t("Prefer an underground route to avoid long sun-exposed waits.",
                "Προτίμησε υπόγεια διαδρομή για να αποφύγεις αναμονές στον ήλιο.",
                "Zgjidh një rrugë nëntokësore për të shmangur pritjet në diell.",
                "Preferisci un percorso sotterraneo per evitare lunghe attese al sole.")
        case .windy:
            return t("Exposed tram/surface stretches can be gusty; metro is steadier.",
                "Τα ανοιχτά τμήματα τραμ/επιφάνειας έχουν ριπές· το μετρό είναι πιο σταθερό.",
                "Pjesët e hapura tram/sipërfaqe mund të kenë erë; metroja është më e qëndrueshme.",
                "I tratti esposti in tram o in superficie possono essere ventosi; la metro è più riparata.")
        case .normal:
            return ""
        }
    }

    private func resolveFindStation(_ query: String) -> AriadneMessage {
        let folded = AthensTransitParser.fold(query)
        let matches = allStations().filter {
            AthensTransitParser.fold($0.name).contains(folded) || AthensTransitParser.fold($0.nameEl).contains(folded)
        }
        guard let top = matches.first else {
            return bot(t("I couldn't find a station matching that.",
                "Δεν βρήκα σταθμό που να ταιριάζει.", "Nuk gjeta një stacion që përputhet.",
                "Non ho trovato una stazione corrispondente."))
        }
        let names = matches.prefix(3).map { name($0) }.joined(separator: ", ")
        let label = t("Open \(name(top))", "Ανοιγμα \(name(top))", "Hap \(name(top))", "Apri \(name(top))")
        return bot(t("Found: \(names).", "Βρέθηκαν: \(names).", "U gjet: \(names).", "Risultati: \(names)."),
            action: .openStation(top.id), actionLabel: label)
    }

    private func resolveExplainLine(_ lineId: String) -> AriadneMessage {
        let normalized = normalizeLine(lineId)
        guard let line = SyrmosData.line(for: normalized) else { return bot(outOfScopeText()) }
        let label = t("Open \(line.name)", "Ανοιγμα \(line.name)", "Hap \(line.name)", "Apri \(line.name)")
        return bot(t("\(line.name): \(line.terminalA) to \(line.terminalB), \(line.stationCount) stations.",
            "\(line.name): \(line.terminalA) ως \(line.terminalB), \(line.stationCount) σταθμοί.",
            "\(line.name): \(line.terminalA) deri \(line.terminalB), \(line.stationCount) stacione.",
            "\(line.name): da \(line.terminalA) a \(line.terminalB), \(line.stationCount) stazioni."),
            confidence: .offline, action: .openLine(normalized), actionLabel: label)
    }

    private func resolveAlerts(lineId: String?) async -> AriadneMessage {
        await alertsService.fetchAnnouncements()
        let alerts = alertsService.announcements.filter { $0.category == .serviceAlert }
        if let first = alerts.first {
            let titles = alerts.prefix(2).map { $0.displayTitle(language: loc.language) }.joined(separator: "; ")
            _ = first
            return bot(t("Active alerts: \(titles)", "Ενεργές ειδοποιήσεις: \(titles)", "Njoftime aktive: \(titles)",
                "Avvisi attivi: \(titles)"), confidence: .live)
        }
        if let status = alertsService.serviceStatus, status.status == "alert" {
            return bot(status.displayMessage(language: loc.language), confidence: .live)
        }
        return bot(t("No active service alerts right now.",
            "Δεν υπάρχουν ενεργές ειδοποιήσεις τώρα.", "Nuk ka njoftime aktive tani.",
            "Non ci sono avvisi di servizio attivi al momento."), confidence: .live)
    }

    private func resolveOpenMap(_ stationId: String?) -> AriadneMessage {
        if let id = stationId, let st = station(id) {
            return bot(t("\(name(st)) is on the Map tab, with live train positions.",
                "Ο \(name(st)) είναι στον Χάρτη, με ζωντανές θέσεις συρμών.",
                "\(name(st)) është te Harta, me pozicione të drejtpërdrejta.",
                "\(name(st)) è nella scheda Mappa, con le posizioni dei treni in tempo reale."))
        }
        return bot(t("Open the Map tab to see live train positions.",
            "Άνοιξε τον Χάρτη για ζωντανές θέσεις συρμών.", "Hap Hartën për pozicionet e trenave.",
            "Apri la scheda Mappa per vedere le posizioni dei treni in tempo reale."))
    }

    // MARK: - LLM fallback

    private func askLLM(_ text: String) async -> AriadneMessage? {
        var chatHistory: [AriadneChatMessage] = []
        for msg in messages.suffix(10) {
            chatHistory.append(AriadneChatMessage(
                role: msg.fromUser ? "user" : "assistant",
                text: msg.text
            ))
        }
        chatHistory.append(AriadneChatMessage(role: "user", text: text))
        guard let reply = await AriadneAPIService.shared.chat(messages: chatHistory) else {
            return nil
        }
        guard Self.isUsableReply(reply) else { return nil }
        return bot(reply)
    }

    private static let leakedReasoningPatterns: [String] = [
        "(greek) or english",
        "(english) or greek",
        "user wrote in",
        "user is asking",
        "let me ",
        "i need to ",
        "i should ",
        "step 1:",
        "chain of thought",
        "reasoning:",
        "thinking:",
        "internal note",
    ]

    private static func isUsableReply(_ text: String) -> Bool {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.count < 8 { return false }
        let lower = trimmed.lowercased()
        for pattern in leakedReasoningPatterns {
            if lower.contains(pattern) { return false }
        }
        if trimmed.hasSuffix(" is") || trimmed.hasSuffix(" the") ||
           trimmed.hasSuffix(" a") || trimmed.hasSuffix(" and") ||
           trimmed.hasSuffix(" or") || trimmed.hasSuffix(" to") ||
           trimmed.hasSuffix(" for") || trimmed.hasSuffix(" with") ||
           trimmed.hasSuffix(" in") || trimmed.hasSuffix(" of") ||
           trimmed.hasSuffix(" that") || trimmed.hasSuffix(" from") {
            return false
        }
        return true
    }

    // MARK: - Helpers

    private func resolveStation(stationId: String?, lineId: String?) -> TransitStation? {
        if let id = stationId { return station(id) }
        if let line = lineId { return SyrmosData.stations(for: normalizeLine(line)).first }
        return nil
    }

    private func allStations() -> [TransitStation] {
        var seen = Set<String>()
        var out: [TransitStation] = []
        for line in SyrmosData.lines {
            for st in SyrmosData.stations(for: line.id) where !seen.contains(st.id) {
                seen.insert(st.id); out.append(st)
            }
        }
        return out
    }

    private func station(_ id: String) -> TransitStation? { allStations().first { $0.id == id } }

    private func name(_ st: TransitStation) -> String {
        loc.language == .greek && !st.nameEl.isEmpty ? st.nameEl : st.name
    }

    private func displayLine(_ lineId: String) -> String { normalizeLine(lineId) }
    private func normalizeLine(_ lineId: String) -> String { lineId.hasPrefix("M3") ? "M3" : lineId }

    private func greeting() -> AriadneMessage {
        bot(t("Hi, I'm Ariadne. Ask about departures, weather, or trips like \"airport by 21:30\".",
            "Γεια, είμαι η Αριάδνη. Ρώτησέ με για αναχωρήσεις, καιρό ή διαδρομές όπως «αεροδρόμιο στις 21:30».",
            "Përshëndetje, jam Ariadne. Më pyet për nisje, motin ose udhëtime si «aeroporti në 21:30».",
            "Ciao, sono Ariadne. Chiedimi di partenze, meteo o viaggi come \"aeroporto entro le 21:30\"."))
    }

    private func helpText() -> String {
        t("I handle departures, last train home, trip planning (including \"be there by X:XX\"), weather at a station, service alerts, ticket prices, and Athens rail info. Offline-safe.",
            "Χειρίζομαι αναχωρήσεις, τελευταίο τρένο, σχεδιασμό διαδρομής (και «να είσαι εκεί στις X:XX»), καιρό σταθμού, ειδοποιήσεις, τιμές εισιτηρίων και πληροφορίες των συγκοινωνιών Αθήνας. Λειτουργώ offline.",
            "Trajtoj nisjet, trenin e fundit, planifikim udhëtimi (edhe «të jesh atje deri në X:XX»), motin te një stacion, njoftime, çmime biletash dhe informacione për transportin e Athinës. Punoj pa internet.",
            "Gestisco partenze, ultimo treno, pianificazione dei viaggi (anche \"arrivare entro le X:XX\"), meteo in stazione, avvisi di servizio, prezzi dei biglietti e informazioni sulla rete ferroviaria di Atene. Funziono offline.")
    }

    private func outOfScopeText() -> String {
        t("I can only help with Syrmos and Athens public transport.",
            "Μπορώ να βοηθήσω μόνο με το Syrmos και τις συγκοινωνίες της Αθήνας.",
            "Mund të ndihmoj vetëm me Syrmos dhe transportin publik të Athinës.",
            "Posso aiutarti solo con Syrmos e il trasporto pubblico di Atene.")
    }

    /// Graceful recovery for a dead-ended turn: suggest the closest station if
    /// the text almost named one, else a warm nudge. Never a flat decline.
    private func recover(_ text: String) -> AriadneMessage {
        if let name = parser.suggestStation(text) {
            return bot(didYouMeanStation(name))
        }
        return bot(recoveryHelp())
    }

    private func didYouMeanStation(_ name: String) -> String {
        t("I didn't quite catch that. Did you mean \(name)? Try \"next trains from \(name)\".",
            "Δεν το κατάλαβα ακριβώς. Μήπως εννοείς \(name); Δοκίμασε «επόμενα τρένα από \(name)».",
            "Nuk e kuptova mirë. Mos ke parasysh \(name)? Provo «trenat e ardhshëm nga \(name)».",
            "Non ho capito bene. Intendevi \(name)? Prova \"prossimi treni da \(name)\".")
    }

    private func recoveryHelp() -> String {
        t("I didn't catch that. Ask me about departures, a route between two stations, or the last train home.",
            "Δεν το κατάλαβα. Ρώτησέ με για αναχωρήσεις, διαδρομή μεταξύ δύο σταθμών ή το τελευταίο τρένο.",
            "Nuk e kuptova. Më pyet për nisje, një udhëtim mes dy stacioneve ose trenin e fundit.",
            "Non ho capito. Chiedimi delle partenze, di un percorso tra due stazioni o dell'ultimo treno.")
    }

    private func clarify(_ missing: MissingSlot) -> String {
        switch missing {
        case .originStation: return t("From which station?", "Από ποιον σταθμό;", "Nga cili stacion?", "Da quale stazione?")
        case .destinationStation: return t("To which station?", "Προς ποιον σταθμό;", "Te cili stacion?", "Verso quale stazione?")
        case .station: return t("Which station?", "Ποιος σταθμός;", "Cili stacion?", "Quale stazione?")
        }
    }

    private func bot(_ text: String, confidence: SourceConfidence = .unknown) -> AriadneMessage {
        AriadneMessage(fromUser: false, text: text, sourceConfidence: confidence)
    }

    private func bot(_ text: String, confidence: SourceConfidence = .unknown, action: AriadneAction, actionLabel: String) -> AriadneMessage {
        AriadneMessage(fromUser: false, text: text, sourceConfidence: confidence, action: action, actionLabel: actionLabel)
    }

    private func t(_ en: String, _ el: String, _ sq: String, _ it: String? = nil) -> String {
        switch loc.language {
        case .greek: return el
        case .albanian: return sq
        case .italian: return it ?? en
        default: return en
        }
    }
}

/// Per-station step-free accessibility, decoded once from the embedded stations
/// in the bundled seed payload (TransitStation itself doesn't carry the flag).
/// Ariadne answers accessibility questions from this, never invented. Unknown
/// ids default to accessible, matching the KMP `Station.accessibility` default.
enum AriadneAccessibility {
    private struct Payload: Decodable {
        struct Line: Decodable {
            struct Stn: Decodable { let id: String; let accessibility: Bool? }
            let stations: [Stn]?
        }
        let lines: [Line]
    }

    static func isAccessible(_ stationId: String) -> Bool {
        map[stationId] ?? true
    }

    private static let map: [String: Bool] = {
        let url = Bundle.main.url(forResource: "lines", withExtension: "json", subdirectory: "seed-schedules-v2")
            ?? Bundle.main.url(forResource: "lines", withExtension: "json")
        guard let url = url,
              let data = try? Data(contentsOf: url),
              let payload = try? JSONDecoder().decode(Payload.self, from: data) else { return [:] }
        var result: [String: Bool] = [:]
        for line in payload.lines {
            for stn in line.stations ?? [] where result[stn.id] == nil {
                result[stn.id] = stn.accessibility ?? true
            }
        }
        return result
    }()
}
