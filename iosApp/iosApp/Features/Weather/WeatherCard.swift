import SwiftUI

/// Weather travel-context card, in the vibrant gradient style of the shared
/// weather-app design. Mirrors the Compose `WeatherCard`.
struct WeatherCard: View {
    let snapshot: WeatherSnapshot
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        let w = snapshot.current
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(snapshot.placeName)
                        .font(.subheadline).fontWeight(.semibold)
                        .foregroundStyle(.white.opacity(0.95))
                    Text(WeatherStyle.label(w.condition, loc.language))
                        .font(.body)
                        .foregroundStyle(.white.opacity(0.85))
                }
                Spacer()
                Text(WeatherStyle.glyph(w.condition, w.isDay)).font(.system(size: 40))
            }
            HStack(alignment: .bottom) {
                Text("\(Int(w.temperatureC.rounded()))°")
                    .font(.system(size: 56, weight: .bold))
                    .foregroundStyle(.white)
                Spacer()
                if let high = snapshot.highC, let low = snapshot.lowC {
                    Text("H:\(Int(high.rounded()))°  L:\(Int(low.rounded()))°")
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.9))
                        .padding(.bottom, 8)
                }
            }

            if !snapshot.hourly.isEmpty {
                HStack {
                    ForEach(snapshot.hourly.prefix(6)) { h in
                        VStack(spacing: 3) {
                            Text(h.hourLabel).font(.caption2).foregroundStyle(.white.opacity(0.85))
                            Text(WeatherStyle.glyph(h.condition, w.isDay)).font(.system(size: 18))
                            Text("\(Int(h.temperatureC.rounded()))°").font(.caption).fontWeight(.semibold).foregroundStyle(.white)
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
                .padding(.vertical, 10)
                .background(.white.opacity(0.15))
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            }

            HStack(spacing: 16) {
                stat(feelsLike, "\(Int(w.apparentC.rounded()))°")
                stat(humidity, "\(w.humidity)%")
                stat(wind, "\(Int(w.windKph.rounded())) km/h")
            }
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            LinearGradient(colors: WeatherStyle.gradient(w.condition, w.isDay),
                           startPoint: .top, endPoint: .bottom)
        )
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private func stat(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(label).font(.caption2).foregroundStyle(.white.opacity(0.8))
            Text(value).font(.body).fontWeight(.semibold).foregroundStyle(.white)
        }
    }

    private var feelsLike: String { tri("Feels like", "Αίσθηση", "Ndihet si", "Percepita") }
    private var humidity: String { tri("Humidity", "Υγρασία", "Lagështia", "Umidità") }
    private var wind: String { tri("Wind", "Άνεμος", "Era", "Vento") }

    private func tri(_ en: String, _ el: String, _ sq: String, _ it: String? = nil) -> String {
        switch loc.language { case .greek: return el; case .albanian: return sq; case .italian: return it ?? en; default: return en }
    }
}

/// Shared weather visuals so the card and any future weather surface agree.
enum WeatherStyle {
    static func glyph(_ c: WeatherCondition, _ isDay: Bool) -> String {
        switch c {
        case .clear: return isDay ? "☀️" : "🌙"
        case .partlyCloudy: return isDay ? "⛅" : "☁️"
        case .cloudy: return "☁️"
        case .fog: return "🌫️"
        case .drizzle, .showers: return "🌦️"
        case .rain: return "🌧️"
        case .snow: return "❄️"
        case .thunderstorm: return "⛈️"
        case .unknown: return "🌡️"
        }
    }

    static func gradient(_ c: WeatherCondition, _ isDay: Bool) -> [Color] {
        if !isDay { return [Color(red: 0.10, green: 0.14, blue: 0.49), Color(red: 0.19, green: 0.11, blue: 0.57)] }
        switch c {
        case .clear: return [Color(red: 0.13, green: 0.59, blue: 0.95), Color(red: 0.31, green: 0.76, blue: 0.97)]
        case .partlyCloudy: return [Color(red: 0.26, green: 0.65, blue: 0.96), Color(red: 0.56, green: 0.79, blue: 0.98)]
        case .cloudy, .fog: return [Color(red: 0.38, green: 0.49, blue: 0.55), Color(red: 0.56, green: 0.64, blue: 0.68)]
        case .drizzle, .rain, .showers: return [Color(red: 0.27, green: 0.35, blue: 0.39), Color(red: 0.33, green: 0.43, blue: 0.48)]
        case .thunderstorm: return [Color(red: 0.22, green: 0.28, blue: 0.31), Color(red: 0.27, green: 0.35, blue: 0.39)]
        case .snow: return [Color(red: 0.47, green: 0.56, blue: 0.61), Color(red: 0.69, green: 0.75, blue: 0.77)]
        case .unknown: return [Color(red: 0.26, green: 0.65, blue: 0.96), Color(red: 0.56, green: 0.79, blue: 0.98)]
        }
    }

    static func label(_ c: WeatherCondition, _ lang: AppLanguage) -> String {
        func quad(_ en: String, _ el: String, _ sq: String, _ it: String) -> String {
            switch lang { case .greek: return el; case .albanian: return sq; case .italian: return it; default: return en }
        }
        switch c {
        case .clear: return quad("Clear", "Αίθριος", "Kthjellët", "Sereno")
        case .partlyCloudy: return quad("Partly cloudy", "Λίγα σύννεφα", "Pjesërisht me re", "Parzialmente nuvoloso")
        case .cloudy: return quad("Cloudy", "Συννεφιά", "Me re", "Nuvoloso")
        case .fog: return quad("Fog", "Ομίχλη", "Mjegull", "Nebbia")
        case .drizzle: return quad("Drizzle", "Ψιχάλα", "Shi i imët", "Pioggerella")
        case .rain: return quad("Rain", "Βροχή", "Shi", "Pioggia")
        case .showers: return quad("Showers", "Μπόρες", "Rrebeshe", "Rovesci")
        case .snow: return quad("Snow", "Χιόνι", "Borë", "Neve")
        case .thunderstorm: return quad("Thunderstorm", "Καταιγίδα", "Stuhi", "Temporale")
        case .unknown: return quad("Weather", "Καιρός", "Moti", "Meteo")
        }
    }
}
