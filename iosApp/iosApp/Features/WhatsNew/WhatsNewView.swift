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
private let kWhatsNewCurrentVersion = "1.3.2"

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
        t(“What's new in Syrmos 1.3”,
          “Τι νέο υπάρχει στο Syrmos 1.3”,
          “Çfarë ka të re në Syrmos 1.3”)
    }

    private var subtitle: String {
        t(“Nationwide coverage and new Ariadne look.”,
          “Πανελλαδική κάλυψη και νέα εμφάνιση Αριάδνης.”,
          “Mbulim kombëtar dhe pamje e re e Ariadnes.”)
    }

    private var items: [String] {
        var list: [String] = []
        list.append(t(
            “Departures for Thessaloniki, Patras and national rail stations via live API”,
            “Δρομολόγια για Θεσσαλονίκη, Πάτρα και εθνικό δίκτυο μέσω live API”,
            “Nisje për Selanik, Patra dhe rrjetin kombëtar nëpërmjet API live”
        ))
        list.append(t(
            “Ticket prices for all networks: OASA, STASY, OSETH, Hellenic Train”,
            “Τιμές εισιτηρίων για όλα τα δίκτυα: OASA, STASY, OSETH, Hellenic Train”,
            “Çmimet e biletave për të gjitha rrjetet: OASA, STASY, OSETH, Hellenic Train”
        ))
        list.append(t(
            “Fresh Ariadne button with the owl mark, matching the web style”,
            “Νέο κουμπί Αριάδνης με το σήμα κουκουβάγιας, όπως στο web”,
            “Butoni i ri i Ariadnes me shenjën e bufit, si në web”
        ))
        list.append(t(
            “Journey fare planner: pick two stations and see the price instantly”,
            “Υπολογιστής κομίστρου: επίλεξε δύο σταθμούς και δες την τιμή αμέσως”,
            “Planifikuesi i tarifave: zgjidh dy stacione dhe shiko çmimin menjëherë”
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
