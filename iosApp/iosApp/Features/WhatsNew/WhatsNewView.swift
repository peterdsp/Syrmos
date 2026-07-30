import SwiftUI

// One-time highlights shown after an install or update, gated by the
// last-seen version in UserDefaults. Bullet list is device-aware: on
// Apple Intelligence devices (Foundation Models available), we lead
// with "clever Ariadne"; on older devices we skip that bullet and keep
// the rest so we don't promise capabilities the device can't deliver.
//
// The version tag below intentionally includes "-r2" so the mid-1.1.1
// refresh (weather answering, time-anchored planning, emergency
// warnings) surfaces its highlights to users who already dismissed
// the earlier 1.1.1 sheet.

private let kWhatsNewVersionKey = "syrmos.whatsnew.version"
private let kWhatsNewCurrentVersion = "1.4.0"

struct WhatsNewView: View {
    let onDismiss: () -> Void
    @ObservedObject private var loc = LocalizationManager.shared
    @Environment(\.colorScheme) private var colorScheme

    private var isClever: Bool { AriadneBrain.isAvailable }

    private func t(_ en: String, _ el: String, _ sq: String) -> String {
        switch loc.language {
        case .greek: return el
        case .albanian: return sq
        case .english: return en
        }
    }

    private var title: String {
        t("What's new in Syrmos",
          "Τι νεο υπαρχει στο Syrmos",
          "Çfare ka te re ne Syrmos")
    }

    private var subtitle: String {
        t("Live cameras, train telemetry and a customizable Home.",
          "Ζωντανες καμερες, τηλεμετρια τρενων και προσαρμοσιμη Αρχικη.",
          "Kamera live, telemetri trenash dhe Balline e personalizueshme.")
    }

    private var items: [String] {
        var list: [String] = []
        list.append(t(
            "Watch Live: stream onboard cameras from suburban trains in real time",
            "Watch Live: δες ζωντανη εικονα απο καμερες προαστιακων τρενων σε πραγματικο χρονο",
            "Watch Live: shiko kamerat ne bord te trenave periferike ne kohe reale"
        ))
        list.append(t(
            "Train telemetry: speed, heading, altitude, signal status and distance to next station",
            "Τηλεμετρια τρενων: ταχυτητα, πορεια, υψομετρο, κατασταση σηματος και αποσταση εως τον επομενο σταθμο",
            "Telemetria e trenit: shpejtesia, drejtimi, lartesia, sinjali dhe distanca deri ne stacionin e ardhshem"
        ))
        list.append(t(
            "Customize Home: drag to reorder sections and hide what you don't need",
            "Προσαρμογη Αρχικης: σειρα μετακινησε τις ενοτητες και κρυψε οσες δε χρειαζεσαι",
            "Personalizo Ballinen: terheq per te ndryshuar rradhen dhe fshih ato qe nuk te duhen"
        ))
        list.append(t(
            "Live trains in Explore: see active trains on any line, or projected departures when offline",
            "Ζωντανα τρενα στο Explore: δες τα ενεργα τρενα σε καθε γραμμη, η προβολη δρομολογιων οταν εισαι εκτος συνδεσης",
            "Trenat live ne Eksploro: shiko trenat aktive ne cdo linje, ose nisjet e parashikuara kur je offline"
        ))
        list.append(t(
            "Tappable live trains on Home: tap any train to see full details, telemetry and Watch Live",
            "Ζωντανα τρενα στην Αρχικη: πατησε οποιοδηποτε τρενο για πληρεις λεπτομερειες και ζωντανη μεταδοση",
            "Trenat live ne Balline: shtyp cdo tren per detaje te plota, telemetri dhe Watch Live"
        ))
        list.append(t(
            "Web livestream: Watch Live now works on syrmos.peterdsp.dev too",
            "Ζωντανη μεταδοση στο web: το Watch Live λειτουργει πλεον και στο syrmos.peterdsp.dev",
            "Transmetim live ne web: Watch Live tani funksionon edhe ne syrmos.peterdsp.dev"
        ))
        return list
    }

    private var backdrop: some View {
        LinearGradient(
            colors: colorScheme == .dark
                ? [Color(red: 0.05, green: 0.07, blue: 0.12), Color(red: 0.02, green: 0.03, blue: 0.06)]
                : [Color(red: 0.94, green: 0.97, blue: 1.0), Color(red: 0.99, green: 0.98, blue: 0.94)],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 12) {
                Text("🦉")
                    .font(.system(size: 44))
                    .frame(width: 76, height: 76)
                    .background(Circle().fill(Color.syrmosPrimary.opacity(0.14)))
                Text(title)
                    .font(.title2).fontWeight(.bold)
                    .multilineTextAlignment(.center)
                Text(subtitle)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, 24)
            .padding(.top, 32)
            .padding(.bottom, 8)

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    ForEach(items, id: \.self) { line in
                        HStack(alignment: .top, spacing: 12) {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(Color.syrmosPrimary)
                                .imageScale(.medium)
                            Text(line)
                                .font(.subheadline)
                                .foregroundStyle(.primary)
                            Spacer(minLength: 0)
                        }
                    }
                }
                .padding(.horizontal, 24)
                .padding(.vertical, 20)
            }

            Button(action: onDismiss) {
                Text(t("Got it", "Εντάξει", "Në rregull"))
                    .font(.headline)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Color.syrmosPrimary, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            .padding(.horizontal, 24)
            .padding(.top, 12)
            .padding(.bottom, 24)
        }
        .task { await NotificationService.shared.requestAuthorization() }
        .background(backdrop.ignoresSafeArea())
        .presentationDetents([.large])
        .presentationCornerRadius(28)
        .presentationDragIndicator(.visible)
    }
}

/// Presents WhatsNewView once per release over whatever it modifies.
struct WhatsNewPresenter: ViewModifier {
    @State private var show = UserDefaults.standard.string(forKey: kWhatsNewVersionKey) != kWhatsNewCurrentVersion

    func body(content: Content) -> some View {
        content.sheet(isPresented: $show) {
            WhatsNewView {
                UserDefaults.standard.set(kWhatsNewCurrentVersion, forKey: kWhatsNewVersionKey)
                show = false
            }
        }
    }
}
