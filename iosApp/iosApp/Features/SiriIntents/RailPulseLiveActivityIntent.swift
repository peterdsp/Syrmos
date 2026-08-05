import AppIntents
import Foundation

@available(iOS 17.0, *)
struct ConfirmRailPulseIntent: AppIntent {
    static let title: LocalizedStringResource = "Confirm RailPulse condition"
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
        let defaults = UserDefaults(suiteName: "group.com.syrmosApp.ios") ?? .standard
        let confirmed = defaults.object(forKey: "railpulse_confirmed") as? Int ?? 347
        let thisWeek = defaults.object(forKey: "railpulse_week") as? Int ?? 28
        defaults.set(confirmed + 1, forKey: "railpulse_confirmed")
        defaults.set(thisWeek + 1, forKey: "railpulse_week")
        defaults.set(signal, forKey: "railpulse_last_signal")
        defaults.set(Date().timeIntervalSince1970, forKey: "railpulse_last_signal_epoch")
        return .result()
    }
}
