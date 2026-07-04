import Foundation
import WidgetKit

/// Writes the app-only data the widgets can't compute themselves (live weather
/// and service alerts) into the shared App Group, then nudges WidgetKit to
/// reload. The Weather + Alerts widget reads these keys; when nothing has been
/// written yet it degrades to its offline placeholder. Requires the
/// `group.com.syrmosApp.ios` App Group on both the app and widget targets.
enum WidgetBridge {
    static let suite = "group.com.syrmosApp.ios"
    private static var defaults: UserDefaults? { UserDefaults(suiteName: suite) }

    /// Publish the latest weather so the widget shows real conditions.
    static func publishWeather(temperatureC: Double, condition: WeatherCondition, highC: Double?, lowC: Double?) {
        guard let d = defaults else { return }
        d.set(Int(temperatureC.rounded()), forKey: "weather.temp")
        d.set(symbol(for: condition), forKey: "weather.symbol")
        d.set(Int((highC ?? temperatureC).rounded()), forKey: "weather.high")
        d.set(Int((lowC ?? temperatureC).rounded()), forKey: "weather.low")
        WidgetCenter.shared.reloadAllTimelines()
    }

    /// Publish up to four service-alert titles for the widget's alerts column.
    static func publishAlerts(_ titles: [String]) {
        guard let d = defaults else { return }
        d.set(Array(titles.prefix(4)), forKey: "alerts")
        WidgetCenter.shared.reloadAllTimelines()
    }

    /// Mirror the app's selected language into the App Group so the widgets
    /// render in EN / EL / SQ to match the app (they run in a separate process
    /// and can't read the app's standard UserDefaults).
    static func publishLanguage(_ code: String) {
        guard let d = defaults else { return }
        d.set(code, forKey: "app_language")
        WidgetCenter.shared.reloadAllTimelines()
    }

    private static func symbol(for c: WeatherCondition) -> String {
        switch c {
        case .clear: return "sun.max.fill"
        case .partlyCloudy: return "cloud.sun.fill"
        case .cloudy: return "cloud.fill"
        case .fog: return "cloud.fog.fill"
        case .drizzle: return "cloud.drizzle.fill"
        case .rain: return "cloud.rain.fill"
        case .snow: return "snowflake"
        case .showers: return "cloud.heavyrain.fill"
        case .thunderstorm: return "cloud.bolt.rain.fill"
        case .unknown: return "cloud.fill"
        }
    }
}
