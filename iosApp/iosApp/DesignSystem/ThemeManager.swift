import SwiftUI

/// User-controlled appearance. Default is System (follow the OS), but the
/// Settings screen lets the user lock the app to Light or Dark independently.
/// Persisted in UserDefaults so the choice survives cold launches.
enum AppTheme: String, CaseIterable, Identifiable {
    case system
    case light
    case dark

    var id: String { rawValue }

    func localizedName(_ lang: AppLanguage) -> String {
        switch (self, lang) {
        case (.system, .english): return "System"
        case (.system, .greek):   return "Σύστημα"
        case (.light, .english):  return "Light"
        case (.light, .greek):    return "Φωτεινό"
        case (.dark, .english):   return "Dark"
        case (.dark, .greek):     return "Σκοτεινό"
        }
    }

    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light:  return .light
        case .dark:   return .dark
        }
    }
}

@MainActor
final class ThemeManager: ObservableObject {
    static let shared = ThemeManager()

    private let key = "syrmos.appTheme"

    @Published var theme: AppTheme {
        didSet {
            UserDefaults.standard.set(theme.rawValue, forKey: key)
        }
    }

    private init() {
        let saved = UserDefaults.standard.string(forKey: "syrmos.appTheme") ?? ""
        self.theme = AppTheme(rawValue: saved) ?? .system
    }
}
