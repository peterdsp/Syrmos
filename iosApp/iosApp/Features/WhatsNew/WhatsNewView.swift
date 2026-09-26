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
private let kWhatsNewCurrentVersion = "3.0.0"

struct WhatsNewView: View {
    let onDismiss: () -> Void
    @ObservedObject private var loc = LocalizationManager.shared
    @Environment(\.colorScheme) private var colorScheme

    private var isClever: Bool { AriadneBrain.isAvailable }

    private func t(_ en: String, _ el: String, _ sq: String, _ it: String) -> String {
        switch loc.language {
        case .greek: return el
        case .albanian: return sq
        case .italian: return it
        case .english: return en
        }
    }

    private var title: String {
        t("What's new in Syrmos",
          "Τι νέο υπάρχει στο Syrmos",
          "Çfare ka te re ne Syrmos",
          "Novità in Syrmos")
    }

    private var subtitle: String {
        t("3.0 Journeys: plan it, ride it, arrive.",
          "3.0 Διαδρομές: σχεδίασε, ταξίδεψε, φτάσε.",
          "3.0 Udhëtime: planifiko, udhëto, mbërri.",
          "3.0 Viaggi: pianifica, viaggia, arriva.")
    }

    private var items: [String] {
        [
            t("Plan any journey A to B: ranked routes, arrive-by, and the last train home",
              "Σχεδίασε κάθε διαδρομή από Α σε Β: ταξινομημένες επιλογές, άφιξη έως, και το τελευταίο τρένο για το σπίτι",
              "Planifiko çdo udhëtim nga A në B: rrugë të renditura, mbërritje deri, dhe treni i fundit për në shtëpi",
              "Pianifica qualsiasi viaggio da A a B: percorsi ordinati, arrivo entro, e l'ultimo treno per casa"),
            t("GO guides you stop by stop, with a get-off alert, a live route map and a Lock Screen Live Activity",
              "Το GO σε καθοδηγεί στάση-στάση, με ειδοποίηση αποβίβασης, ζωντανό χάρτη διαδρομής και Live Activity στην οθόνη κλειδώματος",
              "GO të udhëzon ndalesë pas ndalese, me njoftim zbritjeje, hartë rruge live dhe Live Activity në ekranin e kyçjes",
              "GO ti guida fermata per fermata, con avviso di discesa, mappa del percorso dal vivo e Live Activity sulla schermata di blocco"),
            t("Home shows the next train in every direction at your nearest station",
              "Η Αρχική δείχνει τον επόμενο συρμό προς κάθε κατεύθυνση στον κοντινότερο σταθμό σου",
              "Kreu tregon trenin tjetër për çdo drejtim në stacionin tënd më të afërt",
              "La Home mostra il prossimo treno in ogni direzione dalla tua stazione più vicina"),
            t("Leave-by reminders for your saved departures, and connection-risk warnings that offer alternatives",
              "Υπενθυμίσεις αναχώρησης για τις αποθηκευμένες σου αναχωρήσεις, και προειδοποιήσεις κινδύνου ανταπόκρισης με εναλλακτικές",
              "Kujtesa për nisjen për nisjet e ruajtura, dhe paralajmërime rreziku lidhjeje me alternativa",
              "Promemoria di partenza per le partenze salvate, e avvisi di coincidenza a rischio con alternative"),
            t("Made for iPad and iPhone Duo: planning and guidance side by side on the open display",
              "Φτιαγμένο για iPad και iPhone Duo: σχεδιασμός και καθοδήγηση δίπλα-δίπλα στην ανοιχτή οθόνη",
              "Bërë për iPad dhe iPhone Duo: planifikim dhe udhëzim krah për krah në ekranin e hapur",
              "Pensato per iPad e iPhone Duo: pianificazione e guida fianco a fianco sullo schermo aperto"),
        ]
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
                Text(t("Got it", "Εντάξει", "Në rregull", "Ho capito"))
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
