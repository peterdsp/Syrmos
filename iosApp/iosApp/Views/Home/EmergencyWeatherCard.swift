import SwiftUI

/// Severe-weather warning card shown on Home when the current condition is
/// a thunderstorm, showers, or snow. Amber palette + animated raindrops
/// out of the cloud so the eye reads "rain" without the card needing a
/// full particle system. Emergency numbers below the copy so the user
/// can reach help without leaving Syrmos. Mirrors the Compose
/// EmergencyWeatherCard so the two platforms match.
struct EmergencyWeatherCard: View {
    let condition: WeatherCondition
    let language: AppLanguage

    @State private var dropOffset: CGFloat = 0
    @State private var dropOpacity: Double = 1

    private let amber = Color(red: 0.90, green: 0.32, blue: 0.00)
    private let bg = Color(red: 1.00, green: 0.95, blue: 0.88)

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center, spacing: 10) {
                ZStack(alignment: .top) {
                    Text("☁️").font(.title2)
                    RoundedRectangle(cornerRadius: 2, style: .continuous)
                        .fill(amber.opacity(dropOpacity))
                        .frame(width: 4, height: 10)
                        .offset(y: 22 + dropOffset)
                }
                .frame(width: 44, height: 48)
                .onAppear {
                    withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: false)) {
                        dropOffset = 16
                        dropOpacity = 0
                    }
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.headline).fontWeight(.bold)
                        .foregroundStyle(amber)
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(.primary)
                }
            }

            Text(body_)
                .font(.subheadline)
                .foregroundStyle(.primary)

            VStack(alignment: .leading, spacing: 6) {
                Text(numbersHeader)
                    .font(.caption2).fontWeight(.semibold)
                    .foregroundStyle(.secondary)
                emergencyRow("112", label112)
                emergencyRow("199", labelFire)
                emergencyRow("11185", labelOASA)
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(bg)
        .overlay(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(amber.opacity(0.35), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private func emergencyRow(_ number: String, _ label: String) -> some View {
        HStack(spacing: 10) {
            Text(number)
                .font(.caption).fontWeight(.bold)
                .foregroundStyle(.white)
                .padding(.horizontal, 8).padding(.vertical, 3)
                .background(amber)
                .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
            Text(label)
                .font(.subheadline)
                .foregroundStyle(.primary)
        }
    }

    private var title: String {
        let storm = condition == .thunderstorm
        switch language {
        case .greek: return storm ? "Καταιγίδα σε εξέλιξη" : "Έντονη κακοκαιρία"
        case .albanian: return storm ? "Stuhi në zhvillim" : "Mot i keq"
        case .english: return storm ? "Storm in progress" : "Severe weather"
        }
    }
    private var subtitle: String {
        switch language {
        case .greek: return "Πρόσεχε στη μετακίνηση."
        case .albanian: return "Ki kujdes gjatë udhëtimit."
        case .english: return "Take care on your journey."
        }
    }
    private var body_: String {
        switch language {
        case .greek: return "Οι υπόγειες γραμμές μετρό είναι η πιο ασφαλής επιλογή. Το τραμ και ο προαστιακός μπορεί να έχουν καθυστερήσεις. Αν χρειαστείς άμεση βοήθεια, κάλεσε:"
        case .albanian: return "Metroja nëntokësore është zgjidhja më e sigurt. Tramvaji dhe treni periferik mund të kenë vonesa. Nëse ke nevojë për ndihmë të menjëhershme, telefono:"
        case .english: return "Underground metro lines are the safest option. Tram and Suburban services may run late. If you need immediate help, call:"
        }
    }
    private var numbersHeader: String {
        switch language {
        case .greek: return "ΤΗΛΕΦΩΝΑ ΕΚΤΑΚΤΗΣ ΑΝΑΓΚΗΣ"
        case .albanian: return "NUMRAT E EMERGJENCËS"
        case .english: return "EMERGENCY NUMBERS"
        }
    }
    private var label112: String {
        switch language {
        case .greek: return "Ευρωπαϊκή γραμμή έκτακτης ανάγκης"
        case .albanian: return "Numri europian i emergjencës"
        case .english: return "European emergency line"
        }
    }
    private var labelFire: String {
        switch language {
        case .greek: return "Πυροσβεστική"
        case .albanian: return "Zjarrfikësit"
        case .english: return "Fire service"
        }
    }
    private var labelOASA: String {
        switch language {
        case .greek: return "Πληροφορίες OASA"
        case .albanian: return "Informacione OASA"
        case .english: return "OASA transit info"
        }
    }
}
