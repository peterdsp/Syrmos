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
private let kWhatsNewCurrentVersion = "2.0.0"

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
          "Τι νεο υπαρχει στο Syrmos",
          "Çfare ka te re ne Syrmos",
          "Novità in Syrmos")
    }

    private var subtitle: String {
        t("Hellenic Rail Atlas: a whole new look.",
          "Hellenic Rail Atlas: ολοκαινουριος σχεδιασμος.",
          "Hellenic Rail Atlas: dizajn krejtesisht i ri.",
          "Hellenic Rail Atlas: un look completamente nuovo.")
    }

    private var items: [String] {
        [
            t("A fresh light-first design built around one-glance answers",
              "Νεος σχεδιασμος με απαντησεις στη μια ματια",
              "Dizajn i ri me pergjigje ne nje shikim",
              "Un nuovo design chiaro, pensato per risposte a colpo d'occhio"),
            t("Ariadne now links to stations and lines: tap any answer to jump straight there",
              "Η Αριαδνη τωρα συνδεεται με σταθμους και γραμμες: πατα μια απαντηση και πηγαινε κατευθειαν",
              "Ariadne tani lidhet me stacione dhe linja: prek nje pergjigje dhe shko direkt",
              "Ariadne ora collega stazioni e linee: tocca una risposta per aprirle subito"),
            t("Browse All Stations with interactive maps, line pills and interchange badges",
              "Περιηγηση σε ολους τους σταθμους με χαρτη, ετικετες γραμμων και κομβους ανταποκρισης",
              "Shfleto te gjitha stacionet me harta, etiketa linjash dhe nyje nderkembimi",
              "Esplora tutte le stazioni con mappe interattive, linee e interscambi"),
            t("Redesigned Explore tab with actionable destination cards and recent stations",
              "Ανανεωμενη καρτελα Εξερευνηση με καρτες προορισμων και προσφατους σταθμους",
              "Kartela Eksploro e ridizajnuar me karta destinacionesh dhe stacione te fundit",
              "Scheda Esplora rinnovata con destinazioni utili e stazioni recenti"),
            t("Operators directory and map preferences in the new More tab",
              "Καταλογος φορεων και ρυθμισεις χαρτη στη νεα καρτελα Περισσοτερα",
              "Drejtori operatoresh dhe preferenca harte ne kartelen e re Me shume",
              "Elenco operatori e preferenze della mappa nella nuova scheda Altro"),
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
