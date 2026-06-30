import Foundation
import Combine

struct AriadneMessage: Identifiable {
    let id = UUID()
    let fromUser: Bool
    let text: String
    var departures: [Departure] = []
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
    private var loc: LocalizationManager { LocalizationManager.shared }

    init() {
        messages = [greeting()]
    }

    func ask(_ input: String) {
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        messages.append(AriadneMessage(fromUser: true, text: text))
        thinking = true
        Task {
            let reply = await resolve(parser.parse(text))
            messages.append(reply)
            thinking = false
        }
    }

    // MARK: - Dispatch

    private func resolve(_ intent: AssistantIntent) async -> AriadneMessage {
        switch intent {
        case let .showDepartures(stationId, lineId, day):
            return resolveDepartures(stationId: stationId, lineId: lineId, day: day)
        case let .lastTrain(stationId, lineId):
            return resolveLastTrain(stationId: stationId, lineId: lineId)
        case let .planTrip(from, to, lowExposure):
            return resolvePlanTrip(from: from, to: to, lowExposure: lowExposure)
        case let .findStation(query):
            return resolveFindStation(query)
        case let .explainLine(lineId):
            return resolveExplainLine(lineId)
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
        }
    }

    private func resolveDepartures(stationId: String?, lineId: String?, day: DayContext) -> AriadneMessage {
        guard let station = resolveStation(stationId: stationId, lineId: lineId) else {
            return bot(clarify(.station))
        }
        let lineIds = lineId.map { [$0] } ?? station.lineIds
        let deps = ScheduleProjector.nextDepartures(for: station.id, lineIds: lineIds, limit: 4)
        if deps.isEmpty {
            return bot(t("No more trains from \(name(station)) right now.",
                "Δεν υπάρχουν άλλα δρομολόγια από \(name(station)) τώρα.",
                "Nuk ka më trena nga \(name(station)) tani."))
        }
        let header = t("Next from \(name(station)):", "Επόμενα από \(name(station)):", "Të ardhshmet nga \(name(station)):")
        let note = day == .today ? "" : "\n" + t("Showing today. Open the line for the full timetable.",
            "Εμφανίζεται σήμερα. Άνοιξε τη γραμμή για όλο το πρόγραμμα.",
            "Po shfaqet sot. Hap linjën për orarin e plotë.")
        return AriadneMessage(fromUser: false, text: header + note, departures: Array(deps.prefix(4)))
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
                "Shërbimi për sonte ka mbaruar te \(name(station))."))
        }
        return bot(t("Last \(displayLine(last.lineId)) from \(name(station)) leaves at \(last.time). Leave by then.",
            "Ο τελευταίος \(displayLine(last.lineId)) από \(name(station)) φεύγει \(last.time). Φύγε ως τότε.",
            "Treni i fundit \(displayLine(last.lineId)) nga \(name(station)) niset \(last.time). Nisu deri atëherë."))
    }

    /// Compact 0-or-1-transfer planner over the bundled network. The Athens
    /// rail map is small enough that direct or single-interchange routes cover
    /// almost every pair; deeper routing is the KMP Dijkstra planner's job.
    private func resolvePlanTrip(from: String?, to: String?, lowExposure: Bool) -> AriadneMessage {
        guard let fromId = from else { return bot(clarify(.originStation)) }
        guard let toId = to else { return bot(clarify(.destinationStation)) }
        guard let a = station(fromId), let b = station(toId) else { return bot(outOfScopeText()) }
        let exposure = lowExposure ? "\n" + t("I can't check live weather offline, but this is the simplest route.",
            "Δεν μπορώ να δω τον καιρό εκτός σύνδεσης, αλλά αυτή είναι η απλούστερη διαδρομή.",
            "Nuk e kontrolloj dot motin pa internet, por kjo është rruga më e thjeshtë.") : ""

        // Direct: a shared line.
        if let shared = Set(a.lineIds).intersection(b.lineIds).first {
            return bot(t("Take \(displayLine(shared)) directly from \(name(a)) to \(name(b)). No change.",
                "Πάρε \(displayLine(shared)) απευθείας από \(name(a)) στο \(name(b)). Χωρίς αλλαγή.",
                "Merr \(displayLine(shared)) drejtpërdrejt nga \(name(a)) te \(name(b)). Pa ndërrim.") + exposure)
        }
        // One transfer at a shared interchange.
        for la in a.lineIds {
            for lb in b.lineIds {
                if let interchange = interchangeBetween(la, lb) {
                    return bot(t("Take \(displayLine(la)) to \(name(interchange)), change to \(displayLine(lb)) to \(name(b)). 1 change.",
                        "Πάρε \(displayLine(la)) ως \(name(interchange)), άλλαξε σε \(displayLine(lb)) για \(name(b)). 1 αλλαγή.",
                        "Merr \(displayLine(la)) te \(name(interchange)), ndërro në \(displayLine(lb)) për \(name(b)). 1 ndërrim.") + exposure)
                }
            }
        }
        return bot(t("That trip needs more than one change. Open the Map to plan it visually.",
            "Αυτή η διαδρομή χρειάζεται πάνω από μία αλλαγή. Άνοιξε τον Χάρτη.",
            "Ai udhëtim kërkon më shumë se një ndërrim. Hap Hartën."))
    }

    private func resolveFindStation(_ query: String) -> AriadneMessage {
        let folded = AthensTransitParser.fold(query)
        let matches = allStations().filter {
            AthensTransitParser.fold($0.name).contains(folded) || AthensTransitParser.fold($0.nameEl).contains(folded)
        }
        guard let top = matches.first else {
            return bot(t("I couldn't find a station matching that.",
                "Δεν βρήκα σταθμό που να ταιριάζει.", "Nuk gjeta një stacion që përputhet."))
        }
        let names = matches.prefix(3).map { name($0) }.joined(separator: ", ")
        _ = top
        return bot(t("Found: \(names).", "Βρέθηκαν: \(names).", "U gjet: \(names)."))
    }

    private func resolveExplainLine(_ lineId: String) -> AriadneMessage {
        guard let line = SyrmosData.line(for: normalizeLine(lineId)) else { return bot(outOfScopeText()) }
        return bot(t("\(line.name): \(line.terminalA) to \(line.terminalB), \(line.stationCount) stations.",
            "\(line.name): \(line.terminalA) ως \(line.terminalB), \(line.stationCount) σταθμοί.",
            "\(line.name): \(line.terminalA) deri \(line.terminalB), \(line.stationCount) stacione."))
    }

    private func resolveAlerts(lineId: String?) async -> AriadneMessage {
        await alertsService.fetchAnnouncements()
        let alerts = alertsService.announcements.filter { $0.category == .serviceAlert }
        if let first = alerts.first {
            let titles = alerts.prefix(2).map { $0.displayTitle(language: loc.language) }.joined(separator: "; ")
            _ = first
            return bot(t("Active alerts: \(titles)", "Ενεργές ειδοποιήσεις: \(titles)", "Njoftime aktive: \(titles)"))
        }
        if let status = alertsService.serviceStatus, status.status == "alert" {
            return bot(status.displayMessage(language: loc.language))
        }
        return bot(t("No active service alerts right now.",
            "Δεν υπάρχουν ενεργές ειδοποιήσεις τώρα.", "Nuk ka njoftime aktive tani."))
    }

    private func resolveOpenMap(_ stationId: String?) -> AriadneMessage {
        if let id = stationId, let st = station(id) {
            return bot(t("\(name(st)) is on the Map tab, with live train positions.",
                "Ο \(name(st)) είναι στον Χάρτη, με ζωντανές θέσεις συρμών.",
                "\(name(st)) është te Harta, me pozicione të drejtpërdrejta."))
        }
        return bot(t("Open the Map tab to see live train positions.",
            "Άνοιξε τον Χάρτη για ζωντανές θέσεις συρμών.", "Hap Hartën për pozicionet e trenave."))
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

    /// A station served by both lines (a usable interchange), if any.
    private func interchangeBetween(_ lineA: String, _ lineB: String) -> TransitStation? {
        let onA = SyrmosData.stations(for: normalizeLine(lineA))
        let onBIds = Set(SyrmosData.stations(for: normalizeLine(lineB)).map { $0.id })
        return onA.first { onBIds.contains($0.id) }
    }

    private func name(_ st: TransitStation) -> String {
        loc.language == .greek && !st.nameEl.isEmpty ? st.nameEl : st.name
    }

    private func displayLine(_ lineId: String) -> String { normalizeLine(lineId) }
    private func normalizeLine(_ lineId: String) -> String { lineId.hasPrefix("M3") ? "M3" : lineId }

    private func greeting() -> AriadneMessage {
        bot(t("Hi, I'm Ariadne. Ask me about Athens trains, last departures, or how to get somewhere.",
            "Γεια, είμαι η Αριάδνη. Ρώτησέ με για τα τρένα της Αθήνας, τελευταία δρομολόγια ή πώς να πας κάπου.",
            "Përshëndetje, jam Ariadne. Më pyet për trenat e Athinës, nisjet e fundit ose si të shkosh diku."))
    }

    private func helpText() -> String {
        t("I can show next departures, the last train home, plan a trip between two stations, explain a line, and show alerts. I only cover Syrmos and Athens public transport, fully offline.",
            "Μπορώ να δείξω επόμενες αναχωρήσεις, το τελευταίο τρένο, διαδρομή, να εξηγήσω μια γραμμή και ειδοποιήσεις. Καλύπτω μόνο το Syrmos και τις συγκοινωνίες της Αθήνας, εκτός σύνδεσης.",
            "Mund të tregoj nisjet, trenin e fundit, një udhëtim, të shpjegoj një linjë dhe njoftimet. Mbuloj vetëm Syrmos dhe transportin e Athinës, pa internet.")
    }

    private func outOfScopeText() -> String {
        t("I can only help with Syrmos and Athens public transport.",
            "Μπορώ να βοηθήσω μόνο με το Syrmos και τις συγκοινωνίες της Αθήνας.",
            "Mund të ndihmoj vetëm me Syrmos dhe transportin publik të Athinës.")
    }

    private func clarify(_ missing: MissingSlot) -> String {
        switch missing {
        case .originStation: return t("From which station?", "Από ποιον σταθμό;", "Nga cili stacion?")
        case .destinationStation: return t("To which station?", "Προς ποιον σταθμό;", "Te cili stacion?")
        case .station: return t("Which station?", "Ποιος σταθμός;", "Cili stacion?")
        }
    }

    private func bot(_ text: String) -> AriadneMessage { AriadneMessage(fromUser: false, text: text) }

    private func t(_ en: String, _ el: String, _ sq: String) -> String {
        switch loc.language {
        case .greek: return el
        case .albanian: return sq
        default: return en
        }
    }
}
