import SwiftUI

/// Severe-weather warning card shown on Home when the current condition is
/// a thunderstorm, showers, or snow. Amber palette + animated raindrops
/// out of the cloud so the eye reads "rain" without the card needing a
/// full particle system. Emergency numbers below the copy so the user
/// can reach help without leaving Syrmos. Mirrors the Compose
/// EmergencyWeatherCard so the two platforms match.
struct EmergencyWeatherCard: View {
    let condition: WeatherCondition
    /// What the warning leads with. Defaults to rain so existing call sites and
    /// previews keep working.
    var warningKind: WeatherWarningKind = .rain
    let language: AppLanguage

    @Environment(\.colorScheme) private var colorScheme

    @State private var dropOffset: CGFloat = 0
    @State private var dropOpacity: Double = 1

    /// Deep orange used for the phone-number badges and their white glyphs.
    /// Kept constant across schemes because white text needs a dark fill to
    /// stay readable, and this reads fine on both the cream and dark cards.
    private let badgeAmber = SyrmosTokens.warning

    /// Title, raindrop, and border accent. Brighter in dark mode so the
    /// thin strokes and headline don't muddy against the dark card.
    private var amber: Color {
        colorScheme == .dark
            ? SyrmosTokens.warning
            : SyrmosTokens.arrivalModerate
    }

    /// Card fill. In light mode the warm cream reads as "advisory"; in dark
    /// mode a deep amber-brown keeps that warning identity while letting the
    /// semantic `.primary` / `.secondary` text stay legible on top.
    private var bg: Color {
        colorScheme == .dark
            ? SyrmosTokens.Dark.warningContainer
            : SyrmosTokens.warningContainer
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .center, spacing: 10) {
                ZStack(alignment: .top) {
                    Text("☁️").font(.title2).accessibilityHidden(true)
                    RoundedRectangle(cornerRadius: 2, style: .continuous)
                        .fill(amber.opacity(dropOpacity))
                        .frame(width: 4, height: 10)
                        .offset(y: 22 + dropOffset)
                }
                .frame(width: 44, height: 48)
                .onAppear {
                    guard !SyrmosClock.animationsSuppressed else { return }
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

            Text(caution)
                .font(.subheadline).fontWeight(.medium)
                .foregroundStyle(.primary)

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
            Text(tapHint)
                .font(.caption2)
                .foregroundStyle(.secondary)
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

    @ViewBuilder
    private func emergencyRow(_ number: String, _ label: String) -> some View {
        // Tap-to-call: iOS opens the dialer with the pre-filled number so
        // a user in trouble taps once instead of memorising three digits.
        if let url = URL(string: "tel:\(number)") {
            Link(destination: url) {
                emergencyRowContent(number, label)
            }
        } else {
            emergencyRowContent(number, label)
        }
    }

    private func emergencyRowContent(_ number: String, _ label: String) -> some View {
        HStack(spacing: 10) {
            HStack(spacing: 4) {
                Image(systemName: "phone.fill")
                    .font(.caption2)
                    .foregroundStyle(.white)
                Text(number)
                    .font(.caption).fontWeight(.bold)
                    .foregroundStyle(.white)
            }
            .padding(.horizontal, 8).padding(.vertical, 4)
            .background(badgeAmber)
            .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
            Text(label)
                .font(.subheadline)
                .foregroundStyle(.primary)
        }
    }

    private var tapHint: String {
        switch language {
        case .greek: return "Πατήστε έναν αριθμό για κλήση."
        case .albanian: return "Prek një numër për të thirrur."
        case .italian: return "Tocca un numero per chiamare."
        case .english: return "Tap a number to call."
        }
    }

    private var title: String {
        switch language {
        case .greek: return warningKind == .storm ? "Καταιγίδα σε εξέλιξη" : warningKind == .wind ? "Ισχυροί άνεμοι" : "Έντονη κακοκαιρία"
        case .albanian: return warningKind == .storm ? "Stuhi në zhvillim" : warningKind == .wind ? "Erëra të forta" : "Mot i keq"
        case .italian: return warningKind == .storm ? "Temporale in corso" : warningKind == .wind ? "Vento forte" : "Maltempo grave"
        case .english: return warningKind == .storm ? "Storm in progress" : warningKind == .wind ? "Strong winds" : "Severe weather"
        }
    }
    /// Explicit caution the user asked for: isolated showers can build locally
    /// into storms with lightning, and strong gusts are dangerous. Always shown.
    private var caution: String {
        switch language {
        case .greek: return "Μικρής κλίμακας μπόρες μπορεί κατά τόπους να ενταθούν σε καταιγίδες με κεραυνούς και ριπές ανέμου. Μείνε προσεκτικός και απόφυγε ανοιχτούς χώρους όταν αστράφτει."
        case .albanian: return "Reshjet lokale mund të forcohen vende-vende në stuhi me vetëtima dhe erëra të forta. Ki kujdes dhe shmang hapësirat e hapura kur ka vetëtima."
        case .italian: return "Rovesci isolati possono intensificarsi a tratti in temporali con fulmini e forti raffiche. Fai attenzione ed evita spazi aperti durante i lampi."
        case .english: return "Isolated showers can build in places into storms with lightning and strong gusts. Stay careful and avoid open spaces when it is lightning."
        }
    }
    private var subtitle: String {
        switch language {
        case .greek: return "Πρόσεχε στη μετακίνηση."
        case .albanian: return "Ki kujdes gjatë udhëtimit."
        case .italian: return "Fai attenzione durante il viaggio."
        case .english: return "Take care on your journey."
        }
    }
    private var body_: String {
        switch language {
        case .greek: return "Οι υπόγειες γραμμές μετρό είναι η πιο ασφαλής επιλογή. Το τραμ και ο προαστιακός μπορεί να έχουν καθυστερήσεις. Αν χρειαστείς άμεση βοήθεια, κάλεσε:"
        case .albanian: return "Metroja nëntokësore është zgjidhja më e sigurt. Tramvaji dhe treni periferik mund të kenë vonesa. Nëse ke nevojë për ndihmë të menjëhershme, telefono:"
        case .italian: return "Le linee della metro sotterranea sono l'opzione più sicura. Tram e servizio suburbano possono avere ritardi. Se hai bisogno di aiuto immediato, chiama:"
        case .english: return "Underground metro lines are the safest option. Tram and Suburban services may run late. If you need immediate help, call:"
        }
    }
    private var numbersHeader: String {
        switch language {
        case .greek: return "ΤΗΛΕΦΩΝΑ ΕΚΤΑΚΤΗΣ ΑΝΑΓΚΗΣ"
        case .albanian: return "NUMRAT E EMERGJENCËS"
        case .italian: return "NUMERI DI EMERGENZA"
        case .english: return "EMERGENCY NUMBERS"
        }
    }
    private var label112: String {
        switch language {
        case .greek: return "Ευρωπαϊκή γραμμή έκτακτης ανάγκης"
        case .albanian: return "Numri europian i emergjencës"
        case .italian: return "Linea di emergenza europea"
        case .english: return "European emergency line"
        }
    }
    private var labelFire: String {
        switch language {
        case .greek: return "Πυροσβεστική"
        case .albanian: return "Zjarrfikësit"
        case .italian: return "Vigili del fuoco"
        case .english: return "Fire service"
        }
    }
    private var labelOASA: String {
        switch language {
        case .greek: return "Πληροφορίες OASA"
        case .albanian: return "Informacione OASA"
        case .italian: return "Info trasporti OASA"
        case .english: return "OASA transit info"
        }
    }
}
