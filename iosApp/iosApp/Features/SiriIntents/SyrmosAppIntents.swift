import AppIntents
import Foundation

// MARK: - Entities

struct SiriStationEntity: AppEntity {
    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "Station")
    static let defaultQuery = SiriStationQuery()

    var id: String
    var name: String

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(title: "\(name)")
    }
}

struct SiriStationQuery: EntityQuery {
    func entities(for identifiers: [String]) async -> [SiriStationEntity] {
        await MainActor.run {
            identifiers.compactMap { id in
                let all = allStations()
                return all.first { $0.id == id }
            }
        }
    }

    func suggestedEntities() async -> [SiriStationEntity] {
        await MainActor.run { allStations() }
    }

    @MainActor
    private func allStations() -> [SiriStationEntity] {
        var seen = Set<String>()
        var out: [SiriStationEntity] = []
        for line in SyrmosData.lines {
            for st in SyrmosData.stations(for: line.id) where !seen.contains(st.id) {
                seen.insert(st.id)
                out.append(SiriStationEntity(id: st.id, name: st.name))
            }
        }
        return out
    }
}

// MARK: - Next Departure Intent

struct NextDepartureIntent: AppIntent {
    static let title: LocalizedStringResource = "Next Departure"
    static let description: IntentDescription = "Get the next train departure from a station."
    static let openAppWhenRun = false

    @Parameter(title: "Station")
    var station: SiriStationEntity

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let st = SyrmosData.bundleStations.first { $0.id == station.id }
        guard let st else {
            return .result(dialog: "I couldn't find that station.")
        }
        let deps = ScheduleProjector.nextDepartures(for: st.id, lineIds: st.lineIds, limit: 3)
        if deps.isEmpty {
            return .result(dialog: "No upcoming departures from \(st.name) right now.")
        }
        let lines = deps.prefix(3).map { "\($0.lineId) at \($0.time) toward \($0.direction)" }
        let text = "Next from \(st.name): " + lines.joined(separator: ", ")
        return .result(dialog: IntentDialog(stringLiteral: text))
    }
}

// MARK: - Service Alerts Intent

struct ServiceAlertsIntent: AppIntent {
    static let title: LocalizedStringResource = "Service Alerts"
    static let description: IntentDescription = "Check for active transit service alerts."
    static let openAppWhenRun = false

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let service = STASYService()
        await service.fetchAnnouncements()
        let alerts = service.announcements.filter { $0.category == .serviceAlert }
        if alerts.isEmpty {
            return .result(dialog: "No active service alerts right now. All lines are running normally.")
        }
        let titles = alerts.prefix(3).map { $0.titleEn.isEmpty ? $0.title : $0.titleEn }
        let text = "Active alerts: " + titles.joined(separator: ". ")
        return .result(dialog: IntentDialog(stringLiteral: text))
    }
}

// MARK: - Ticket Price Intent

struct TicketPriceIntent: AppIntent {
    static let title: LocalizedStringResource = "Ticket Prices"
    static let description: IntentDescription = "Get current Athens transit ticket prices."
    static let openAppWhenRun = false

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let store = SyrmosFaresStore.shared
        let singles = store.products(in: "single")
        if singles.isEmpty {
            return .result(dialog: "A 90-minute ticket costs 1.20 EUR. Airport single is 9 EUR.")
        }
        let items = singles.prefix(4).compactMap { p -> String? in
            guard let price = p.fullPriceEur else { return nil }
            return "\(p.titleEn): \(String(format: "%.2f", price)) EUR"
        }
        let text = items.joined(separator: ". ")
        return .result(dialog: IntentDialog(stringLiteral: text))
    }
}

// MARK: - Ask Ariadne Intent

struct AskAriadneIntent: AppIntent {
    static let title: LocalizedStringResource = "Ask Syrmos"
    static let description: IntentDescription = "Ask Ariadne, the Syrmos transit assistant, any question about Athens public transport."
    static let openAppWhenRun = false

    @Parameter(title: "Question")
    var question: String

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let msg = AriadneChatMessage(role: "user", text: question)
        guard let reply = await AriadneAPIService.shared.chat(messages: [msg]) else {
            return .result(dialog: "I couldn't reach the Ariadne assistant right now. Try again or open the app.")
        }
        return .result(dialog: IntentDialog(stringLiteral: reply))
    }
}

// MARK: - Shortcuts Provider

struct SyrmosShortcutsProvider: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: NextDepartureIntent(),
            phrases: [
                "Next train from \(\.$station) in \(.applicationName)",
                "When is the next train from \(\.$station) in \(.applicationName)",
                "Departures from \(\.$station) in \(.applicationName)",
                "\(.applicationName) next departure from \(\.$station)",
            ],
            shortTitle: "Next Departure",
            systemImageName: "tram.fill"
        )
        AppShortcut(
            intent: ServiceAlertsIntent(),
            phrases: [
                "Transit alerts in \(.applicationName)",
                "Any metro disruptions in \(.applicationName)",
                "Service alerts in \(.applicationName)",
                "\(.applicationName) alerts",
            ],
            shortTitle: "Service Alerts",
            systemImageName: "exclamationmark.triangle.fill"
        )
        AppShortcut(
            intent: TicketPriceIntent(),
            phrases: [
                "Ticket prices in \(.applicationName)",
                "How much is a metro ticket in \(.applicationName)",
                "Athens transit fares in \(.applicationName)",
            ],
            shortTitle: "Ticket Prices",
            systemImageName: "ticket.fill"
        )
    }
}
