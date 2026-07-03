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
private let kWhatsNewCurrentVersion = "1.1.1-r2"

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
        isClever
            ? t("What's new — clever mode",
                "Τι νέο υπάρχει — έξυπνη λειτουργία",
                "Çfarë ka të re — modaliteti i zgjuar")
            : t("What's new in Syrmos",
                "Τι νέο υπάρχει στο Syrmos",
                "Çfarë ka të re në Syrmos")
    }

    private var subtitle: String {
        isClever
            ? t("Your device supports Ariadne's on-device AI.",
                "Η συσκευή σου υποστηρίζει το on-device AI της Ariadne.",
                "Pajisja jote mbështet AI-në e Ariadnes në pajisje.")
            : t("A quick tour of the new features.",
                "Επισκόπηση των νέων δυνατοτήτων.",
                "Përmbledhje e veçorive të reja.")
    }

    private var items: [String] {
        var list: [String] = []
        if isClever {
            list.append(t(
                "Cleverer Ariadne — on-device AI reads your typos before the parser",
                "Πιο έξυπνη Αριάδνη — το on-device AI διαβάζει τα λάθη σου πριν τον αναλυτή",
                "Ariadne më e zgjuar — AI në pajisje lexon gabimet e tua përpara analizuesit"
            ))
        }
        list.append(t(
            "Ask about weather — “weather at Piraeus”, offline-safe from the last snapshot",
            "Ρώτα για τον καιρό — “καιρός στον Πειραιά”, offline-safe από το τελευταίο snapshot",
            "Pyet për motin — “moti në Piraeus”, offline i sigurt nga snapshot-i i fundit"
        ))
        list.append(t(
            "Time-anchored planning — “airport by 21:30” answers when to leave",
            "Σχεδιασμός με στόχο χρόνου — “αεροδρόμιο στις 21:30” σου λέει πότε να ξεκινήσεις",
            "Planifikim me kohë objektiv — “aeroporti deri në 21:30” të thotë kur të nisesh"
        ))
        list.append(t(
            "Severe-weather warnings on Home with emergency numbers (112, 199, 11185)",
            "Προειδοποιήσεις κακοκαιρίας στην Αρχική με τηλέφωνα έκτακτης ανάγκης (112, 199, 11185)",
            "Paralajmërime moti të keq në Home me numra emergjence (112, 199, 11185)"
        ))
        list.append(t(
            "Redesigned tracking card with an animated station strip and a Live Activity",
            "Ανανεωμένη κάρτα παρακολούθησης με στριπ σταθμών και Live Activity",
            "Karta e ndjekjes e ridizajnuar me strip stacionesh dhe një Live Activity"
        ))
        list.append(t(
            "Track any train — pick line, direction, station, departure",
            "Παρακολούθηση οποιουδήποτε τρένου — γραμμή, κατεύθυνση, σταθμός, δρομολόγιο",
            "Ndiq çdo tren — linjë, drejtim, stacion, nisje"
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
