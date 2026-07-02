import SwiftUI

// One-time highlights shown after an install or update, gated by the stored
// last-seen version. Informational for existing users (no store link).
//
// XCODE: add this file to the iosApp target.

private let kWhatsNewVersionKey = "syrmos.whatsnew.version"
private let kWhatsNewCurrentVersion = "1.1.1"

struct WhatsNewView: View {
    let onDismiss: () -> Void
    @ObservedObject private var loc = LocalizationManager.shared

    private func t(_ en: String, _ el: String, _ sq: String) -> String {
        switch loc.language {
        case .greek: return el
        case .albanian: return sq
        case .english: return en
        }
    }

    private var items: [String] {
        [
            t("Ask Ariadne — the offline assistant for departures, trips and last trains",
              "Ρώτα την Αριάδνη — τον offline βοηθό για αναχωρήσεις, διαδρομές και τελευταία τρένα",
              "Pyet Ariadnen — asistenti offline për nisjet, udhëtimet dhe trenat e fundit"),
            t("Smarter search that understands typos (nikea → Nikaia)",
              "Πιο έξυπνη αναζήτηση που καταλαβαίνει τα λάθη (nikea → Νίκαια)",
              "Kërkim më i zgjuar që kupton gabimet (nikea → Nikaia)"),
            t("\"How long to…\" travel-time answers from your location",
              "Απαντήσεις χρόνου \"Πόση ώρα για…\" από την τοποθεσία σου",
              "Përgjigje kohe \"Sa gjatë te…\" nga vendndodhja jote"),
            t("Track any train, plus a Home Screen widget for next departures",
              "Παρακολούθησε κάθε τρένο, με widget στην Αρχική οθόνη για επόμενες αναχωρήσεις",
              "Ndiq çdo tren, plus një widget në Ekranin Kryesor për nisjet"),
        ]
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("🦉").font(.largeTitle)
            Text(t("What's new in Syrmos", "Τι νέο υπάρχει στο Syrmos", "Çfarë ka të re në Syrmos"))
                .font(.title2).fontWeight(.bold)
            VStack(alignment: .leading, spacing: 12) {
                ForEach(items, id: \.self) { line in
                    HStack(alignment: .top, spacing: 10) {
                        Image(systemName: "checkmark.circle.fill").foregroundStyle(.blue)
                        Text(line).font(.subheadline)
                        Spacer(minLength: 0)
                    }
                }
            }
            Spacer()
            Button(action: onDismiss) {
                Text(t("Got it", "Εντάξει", "Në rregull"))
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Color.blue, in: RoundedRectangle(cornerRadius: 14))
                    .foregroundStyle(.white)
            }
        }
        .padding(24)
        .presentationDetents([.medium, .large])
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
