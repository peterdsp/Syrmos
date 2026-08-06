import AppIntents
import Foundation

@available(iOS 17.0, *)
struct ConfirmRailPulseIntent: AppIntent {
    static let title: LocalizedStringResource = "Confirm Ichnos condition"
    static let description = IntentDescription("Confirm a visible railway condition without opening Syrmos.")
    static let openAppWhenRun = false

    @Parameter(title: "Condition")
    var signal: String

    init() {
        signal = "crowded"
    }

    init(signal: String) {
        self.signal = signal
    }

    func perform() async throws -> some IntentResult {
        let wireSignal = normalizedSignal(signal)
        let reportId = "report_\(UUID().uuidString.replacingOccurrences(of: "-", with: ""))"
        var request = URLRequest(url: URL(string: "https://api-syrmos.peterdsp.dev/api/community/reports")!)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: [
            "reportId": reportId,
            "scopeId": "network",
            "scopeLabel": "Live Activity",
            "signal": wireSignal,
            "detail": "",
            "platform": "ios",
            "locale": "",
        ])
        let (_, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else { return .result() }
        let defaults = UserDefaults(suiteName: "group.com.syrmosApp.ios") ?? .standard
        let confirmed = defaults.object(forKey: "ichnos_v2_confirmed") as? Int ?? 0
        let thisWeek = defaults.object(forKey: "ichnos_v2_week") as? Int ?? 0
        defaults.set(confirmed + 1, forKey: "ichnos_v2_confirmed")
        defaults.set(thisWeek + 1, forKey: "ichnos_v2_week")
        defaults.set(wireSignal, forKey: "ichnos_v2_last_signal")
        defaults.set(Date().timeIntervalSince1970, forKey: "ichnos_v2_last_signal_epoch")
        return .result()
    }

    private func normalizedSignal(_ value: String) -> String {
        switch value.lowercased() {
        case "normal", "everything ok": return "normal"
        case "delay", "delayed": return "delayed"
        case "crowded": return "crowded"
        case "stopped": return "stopped"
        case "broken ac", "facilities": return "facilities"
        default: return "other"
        }
    }
}
