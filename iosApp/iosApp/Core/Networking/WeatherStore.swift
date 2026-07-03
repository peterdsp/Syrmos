import Foundation
import Combine
import CoreLocation

/// Current-conditions model + store on iOS, mirroring the KMP weather layer.
/// Weather is the one genuinely-online piece; the snapshot carries its fetch
/// time so the card can say "as of HH:MM" and degrade honestly offline.

enum WeatherCondition {
    case clear, partlyCloudy, cloudy, fog, drizzle, rain, snow, showers, thunderstorm, unknown

    /// Outside is unpleasant, so routing should reduce exposure.
    var isWet: Bool {
        switch self {
        case .drizzle, .rain, .snow, .showers, .thunderstorm: return true
        default: return false
        }
    }

    /// Severe enough to justify an on-Home warning card with emergency
    /// contact numbers. Mirrors WeatherCondition.isSevere in KMP.
    var isSevere: Bool {
        switch self {
        case .showers, .thunderstorm, .snow: return true
        default: return false
        }
    }

    static func fromCode(_ code: Int) -> WeatherCondition {
        switch code {
        case 0: return .clear
        case 1, 2: return .partlyCloudy
        case 3: return .cloudy
        case 45, 48: return .fog
        case 51, 53, 55, 56, 57: return .drizzle
        case 61, 63, 65, 66, 67: return .rain
        case 71, 73, 75, 77: return .snow
        case 80, 81, 82, 85, 86: return .showers
        case 95, 96, 99: return .thunderstorm
        default: return .unknown
        }
    }
}

struct CurrentWeather {
    let temperatureC: Double
    let apparentC: Double
    let weatherCode: Int
    let isDay: Bool
    let windKph: Double
    let humidity: Int
    let precipitationMm: Double
    var condition: WeatherCondition { .fromCode(weatherCode) }
}

struct HourlyForecast: Identifiable {
    let id = UUID()
    let hourLabel: String
    let temperatureC: Double
    let weatherCode: Int
    var condition: WeatherCondition { .fromCode(weatherCode) }
}

struct WeatherSnapshot {
    let current: CurrentWeather
    let placeName: String
    let fetchedAt: Date
    var highC: Double?
    var lowC: Double?
    var hourly: [HourlyForecast] = []
}

@MainActor
final class WeatherStore: ObservableObject {
    static let shared = WeatherStore()

    @Published private(set) var snapshot: WeatherSnapshot?

    private static let athens = CLLocationCoordinate2D(latitude: 37.9838, longitude: 23.7275)
    private var lastFetch: Date?

    private init() {}

    /// Refreshes for the given coordinate (defaults to central Athens so a
    /// no-location launch still shows something). Throttled to once a minute.
    func refresh(latitude: Double = athens.latitude,
                 longitude: Double = athens.longitude,
                 placeName: String = "Athens") async {
        if let last = lastFetch, Date().timeIntervalSince(last) < 60 { return }
        guard var comps = URLComponents(string: "https://api.open-meteo.com/v1/forecast") else { return }
        comps.queryItems = [
            .init(name: "latitude", value: String(latitude)),
            .init(name: "longitude", value: String(longitude)),
            .init(name: "current", value: "time,temperature_2m,apparent_temperature,is_day,precipitation,relative_humidity_2m,weather_code,wind_speed_10m"),
            .init(name: "hourly", value: "temperature_2m,weather_code"),
            .init(name: "daily", value: "temperature_2m_max,temperature_2m_min"),
            .init(name: "forecast_days", value: "1"),
            .init(name: "timezone", value: "auto"),
        ]
        guard let url = comps.url else { return }
        do {
            var req = URLRequest(url: url)
            req.timeoutInterval = 8
            let (data, _) = try await URLSession.shared.data(for: req)
            let decoded = try JSONDecoder().decode(OpenMeteoResponse.self, from: data)
            snapshot = WeatherSnapshot(
                current: decoded.current.toDomain(),
                placeName: placeName,
                fetchedAt: Date(),
                highC: decoded.daily?.temperature_2m_max.first,
                lowC: decoded.daily?.temperature_2m_min.first,
                hourly: decoded.nextHours(6)
            )
            lastFetch = Date()
        } catch {
            // Keep the last snapshot; the card and routing degrade honestly.
        }
    }

    private struct OpenMeteoResponse: Decodable {
        let current: Current
        let hourly: Hourly?
        let daily: Daily?

        /// The next [count] hourly points at or after the current hour.
        func nextHours(_ count: Int) -> [HourlyForecast] {
            guard let h = hourly, !h.time.isEmpty else { return [] }
            let start = h.time.firstIndex(where: { $0 >= current.time }) ?? 0
            let end = min(start + count, h.time.count)
            guard start < end else { return [] }
            return (start..<end).compactMap { i in
                guard i < h.temperature_2m.count else { return nil }
                let code = i < h.weather_code.count ? h.weather_code[i] : 0
                let label = String(h.time[i].split(separator: "T").last?.prefix(5) ?? "")
                return HourlyForecast(hourLabel: label, temperatureC: h.temperature_2m[i], weatherCode: code)
            }
        }

        struct Current: Decodable {
            let time: String
            let temperature_2m: Double
            let apparent_temperature: Double
            let is_day: Int
            let precipitation: Double
            let relative_humidity_2m: Int
            let weather_code: Int
            let wind_speed_10m: Double
            func toDomain() -> CurrentWeather {
                CurrentWeather(
                    temperatureC: temperature_2m,
                    apparentC: apparent_temperature,
                    weatherCode: weather_code,
                    isDay: is_day == 1,
                    windKph: wind_speed_10m,
                    humidity: relative_humidity_2m,
                    precipitationMm: precipitation
                )
            }
        }
        struct Hourly: Decodable {
            let time: [String]
            let temperature_2m: [Double]
            let weather_code: [Int]
        }
        struct Daily: Decodable {
            let temperature_2m_max: [Double]
            let temperature_2m_min: [Double]
        }
    }
}
