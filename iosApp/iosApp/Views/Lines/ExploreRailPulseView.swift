import SwiftUI

struct RailPulseReportContext: Identifiable, Hashable {
    let title: String
    let subtitle: String

    var id: String { "\(title)|\(subtitle)" }
}

enum ExploreSheet: Identifiable {
    case quickReport(RailPulseReportContext)

    var id: String {
        switch self {
        case .quickReport(let context): return "quick-report|\(context.id)"
        }
    }
}

private struct PulseFeedItem: Identifiable {
    let id: String
    let title: String
    let detail: String
    let status: String
    let color: Color
}

private enum QuickReportSignal: String, CaseIterable, Identifiable {
    case delayed
    case crowded
    case stopped
    case tooHot
    case clean
    case access
    case facilities
    case safety
    case other

    var id: String { rawValue }

    var systemImage: String {
        switch self {
        case .delayed: return "clock.badge.exclamationmark"
        case .crowded: return "person.3.fill"
        case .stopped: return "pause.circle.fill"
        case .tooHot: return "thermometer.sun.fill"
        case .clean: return "sparkles"
        case .access: return "figure.roll"
        case .facilities: return "powerplug.fill"
        case .safety: return "exclamationmark.triangle.fill"
        case .other: return "ellipsis"
        }
    }

    func localized(_ language: AppLanguage) -> String {
        switch self {
        case .delayed: return pulseText(language, "Delayed", "Καθυστερηση", "Vonese", "Ritardo")
        case .crowded: return pulseText(language, "Crowded", "Κοσμος", "Plot", "Affollato")
        case .stopped: return pulseText(language, "Stopped", "Σταματημενο", "Ndaluar", "Fermo")
        case .tooHot: return pulseText(language, "Too hot", "Πολυ ζεστη", "Shume nxehte", "Troppo caldo")
        case .clean: return pulseText(language, "Clean", "Καθαρο", "Paster", "Pulito")
        case .access: return pulseText(language, "Access", "Προσβαση", "Akses", "Accesso")
        case .facilities: return pulseText(language, "Facilities", "Παροχες", "Sherbime", "Servizi")
        case .safety: return pulseText(language, "Safety", "Ασφαλεια", "Siguri", "Sicurezza")
        case .other: return pulseText(language, "Other", "Αλλο", "Tjeter", "Altro")
        }
    }
}

struct ExploreUniversalSearchField: View {
    @Binding var text: String
    let language: AppLanguage

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(.secondary)
            TextField(
                pulseText(
                    language,
                    "Destination, station, line or train...",
                    "Προορισμος, σταθμος, γραμμη η τρενο...",
                    "Destinacion, stacion, linje ose tren...",
                    "Destinazione, stazione, linea o treno..."
                ),
                text: $text
            )
            .textFieldStyle(.plain)
            .font(.subheadline)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()

            if !text.isEmpty {
                Button {
                    text = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(.tertiary)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(pulseText(language, "Clear", "Καθαρισμος", "Pastro", "Cancella"))
            }
        }
        .padding(.horizontal, 14)
        .frame(minHeight: 48)
        .background(Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 17, style: .continuous))
        .shadow(color: .black.opacity(0.07), radius: 8, y: 4)
    }
}

struct ExploreRailPulseContent: View {
    let language: AppLanguage
    let onReport: (RailPulseReportContext) -> Void
    let onOpenStation: () -> Void
    let onOpenTrain: () -> Void
    @State private var selectedBudget = 30

    private var reportContext: RailPulseReportContext {
        RailPulseReportContext(
            title: pulseText(language, "Kallithea to Monastiraki", "Καλλιθεα προς Μοναστηρακι", "Kallithea per Monastiraki", "Kallithea - Monastiraki"),
            subtitle: pulseText(language, "M1 toward Kifissia", "M1 προς Κηφισια", "M1 drejt Kifisia", "M1 verso Kifisia")
        )
    }

    private var feed: [PulseFeedItem] {
        [
            PulseFeedItem(
                id: "delay-athens-piraeus",
                title: pulseText(language, "Athens - Piraeus", "Αθηνα - Πειραιας", "Athine - Pire", "Atene - Pireo"),
                detail: pulseText(language, "14 min delay · 23 confirmations", "Καθυστερηση 14 λεπ · 23 επιβεβαιωσεις", "14 min vonese · 23 konfirmime", "14 min di ritardo · 23 conferme"),
                status: pulseText(language, "Verified", "Επιβεβαιωμενο", "Konfirmuar", "Verificato"),
                color: SyrmosTokens.disruption
            ),
            PulseFeedItem(
                id: "monastiraki-escalator",
                title: "Monastiraki",
                detail: pulseText(language, "Escalator working again · 9 confirmations", "Η κυλιομενη λειτουργει ξανα · 9 επιβεβαιωσεις", "Shkallet levizese punojne perseri · 9 konfirmime", "Scala mobile di nuovo attiva · 9 conferme"),
                status: pulseText(language, "2 min ago", "πριν 2 λεπ", "2 min me pare", "2 min fa"),
                color: SyrmosTokens.live
            ),
            PulseFeedItem(
                id: "airport-crowding",
                title: pulseText(language, "Airport train", "Τρενο Αεροδρομιου", "Treni i aeroportit", "Treno aeroporto"),
                detail: pulseText(language, "Standing room only · 31 confirmations", "Μονο ορθιοι · 31 επιβεβαιωσεις", "Vetem ne kembe · 31 konfirmime", "Solo posti in piedi · 31 conferme"),
                status: pulseText(language, "Live", "Ζωντανα", "Live", "Live"),
                color: SyrmosTokens.warning
            ),
        ]
    }

    var body: some View {
        VStack(spacing: 12) {
            routePulseHero
            sectionTitle(
                pulseText(language, "RailPulse across Greece", "RailPulse σε ολη την Ελλαδα", "RailPulse ne gjithe Greqine", "RailPulse in tutta la Grecia"),
                action: pulseText(language, "See all", "Ολα", "Shiko te gjitha", "Vedi tutto")
            )
            ForEach(feed) { item in
                Button {
                    if item.id == "monastiraki-escalator" {
                        onOpenStation()
                    } else {
                        onOpenTrain()
                    }
                } label: {
                    pulseFeedRow(item)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("\(item.title). \(item.detail)")
            }
            sectionTitle(
                pulseText(language, "Explore by time", "Εξερευνηση με χρονο", "Eksploro sipas kohes", "Esplora per tempo"),
                action: pulseText(language, "From Kallithea", "Απο Καλλιθεα", "Nga Kallithea", "Da Kallithea")
            )
            budgetRow
            Text(
                pulseText(
                    language,
                    "Ariadne: M1 is normal, but the Airport train is crowded.",
                    "Ariadne: Η M1 λειτουργει κανονικα, αλλα το τρενο Αεροδρομιου εχει κοσμο.",
                    "Ariadne: M1 eshte normale, por treni i aeroportit eshte plot.",
                    "Ariadne: M1 e regolare, ma il treno aeroporto e affollato."
                )
            )
            .font(.caption.weight(.semibold))
            .foregroundStyle(SyrmosTokens.suburban)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 14)
            .padding(.vertical, 11)
            .background(SyrmosTokens.suburban.opacity(0.10), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
    }

    private var routePulseHero: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(pulseText(language, "YOUR ROUTE PULSE", "Ο ΠΑΛΜΟΣ ΤΗΣ ΔΙΑΔΡΟΜΗΣ", "PULSI I RRUGES TENDE", "IL PULSO DEL PERCORSO"))
                    .font(.caption2.weight(.bold))
                Spacer()
                Circle().fill(Color.green).frame(width: 9, height: 9)
                Text(pulseText(language, "Normal", "Κανονικα", "Normal", "Regolare"))
                    .font(.caption2.weight(.bold))
            }
            Text(reportContext.title)
                .font(.title3.weight(.bold))
            Text("\(reportContext.subtitle) · \(pulseText(language, "next train in 2 min", "επομενο σε 2 λεπ", "treni tjeter ne 2 min", "prossimo tra 2 min"))")
                .font(.subheadline)
            HStack(spacing: 0) {
                Circle().fill(.white).frame(width: 14, height: 14)
                Rectangle().fill(Color(red: 0.61, green: 0.89, blue: 0.75)).frame(height: 3)
                Circle().fill(.white).frame(width: 14, height: 14)
            }
            HStack {
                Text("Kallithea")
                Spacer()
                Text("Monastiraki")
            }
            .font(.caption2.weight(.semibold))
            HStack(spacing: 10) {
                Text(pulseText(language, "18 people confirmed normal service", "18 ατομα επιβεβαιωσαν κανονικη λειτουργια", "18 persona konfirmuan sherbim normal", "18 persone confermano servizio regolare"))
                    .font(.caption2)
                    .lineLimit(1)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(.white.opacity(0.15), in: Capsule())
                Button(pulseText(language, "Report", "Αναφορα", "Raporto", "Segnala")) {
                    onReport(reportContext)
                }
                .buttonStyle(.borderedProminent)
                .tint(.white)
                .foregroundStyle(.black)
                .controlSize(.small)
                .accessibilityHint(pulseText(language, "Opens the RailPulse quick report", "Ανοιγει τη γρηγορη αναφορα RailPulse", "Hap raportin e shpejte RailPulse", "Apre la segnalazione rapida RailPulse"))
            }
        }
        .foregroundStyle(.white)
        .padding(18)
        .background(
            LinearGradient(
                colors: [Color(hex: 0x153D52), Color(hex: 0x2B6966), SyrmosTokens.suburban],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            ),
            in: RoundedRectangle(cornerRadius: 24, style: .continuous)
        )
    }

    private func sectionTitle(_ title: String, action: String) -> some View {
        HStack {
            Text(title).font(.headline)
            Spacer()
            Text(action).font(.caption).foregroundStyle(Color.syrmosPrimary)
        }
        .padding(.top, 2)
    }

    private func pulseFeedRow(_ item: PulseFeedItem) -> some View {
        HStack(spacing: 12) {
            ZStack {
                Circle().fill(item.color.opacity(0.12)).frame(width: 34, height: 34)
                Circle().fill(item.color).frame(width: 9, height: 9)
            }
            VStack(alignment: .leading, spacing: 3) {
                Text(item.title).font(.subheadline.weight(.semibold))
                Text(item.detail).font(.caption).foregroundStyle(.secondary)
            }
            Spacer(minLength: 4)
            Text(item.status).font(.caption2.weight(.bold)).foregroundStyle(item.color)
        }
        .padding(14)
        .background(Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 17, style: .continuous))
        .shadow(color: .black.opacity(0.05), radius: 5, y: 3)
    }

    private var budgetRow: some View {
        HStack(spacing: 8) {
            ForEach([30, 60, 90, 120], id: \.self) { minutes in
                let selected = selectedBudget == minutes
                Button {
                    selectedBudget = minutes
                } label: {
                    Text(minutes == 120 ? "2 h+" : "\(minutes) m")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(selected ? Color.syrmosSurface : .primary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(selected ? Color.primary : Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
    }
}

struct RailPulseQuickReportSheet: View {
    let context: RailPulseReportContext
    let language: AppLanguage
    @Environment(\.dismiss) private var dismiss
    @State private var selected: QuickReportSignal?
    @State private var crowdLevel = "Standing"
    @State private var hasRecorded = false
    @State private var canUndo = false
    @ObservedObject private var contributionStore = RailPulseLocalStore.shared

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 10), count: 3)

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text(pulseText(language, "Quick report", "Γρηγορη αναφορα", "Raport i shpejte", "Segnalazione rapida"))
                        .font(.title2.weight(.bold))
                    Spacer()
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.headline)
                            .frame(width: 36, height: 36)
                            .background(Color.syrmosSurfaceMuted, in: Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(pulseText(language, "Close", "Κλεισιμο", "Mbyll", "Chiudi"))
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text(context.title).font(.headline)
                    Text(context.subtitle).font(.caption).foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(14)
                .background(Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 17, style: .continuous))
                Text(pulseText(language, "Tap once. Report only what you can see right now.", "Πατησε μια φορα. Αναφερε μονο ο,τι βλεπεις τωρα.", "Prek nje here. Raporto vetem ate qe sheh tani.", "Un tocco. Segnala solo cio che vedi ora."))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(SyrmosTokens.live)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(SyrmosTokens.live.opacity(0.10), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                Text(pulseText(language, "What is happening?", "Τι συμβαινει;", "Cfare po ndodh?", "Cosa sta succedendo?"))
                    .font(.headline)
                LazyVGrid(columns: columns, spacing: 10) {
                    ForEach(QuickReportSignal.allCases) { signal in
                        let active = selected == signal
                        Button {
                            if !hasRecorded {
                                contributionStore.recordContribution()
                                hasRecorded = true
                            }
                            selected = signal
                            canUndo = true
                            if signal == .crowded { crowdLevel = "Standing" }
                        } label: {
                            VStack(spacing: 7) {
                                Image(systemName: signal.systemImage)
                                    .font(.title3)
                                Text(signal.localized(language))
                                    .font(.caption.weight(.semibold))
                                    .lineLimit(1)
                            }
                            .foregroundStyle(active ? .white : .primary)
                            .frame(maxWidth: .infinity, minHeight: 78)
                            .background(active ? SyrmosTokens.suburban : Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(active ? .isSelected : [])
                    }
                }
                if selected == .crowded {
                    Text(pulseText(language, "Crowd level", "Επιπεδο πληροτητας", "Niveli i turmes", "Livello affollamento"))
                        .font(.subheadline.weight(.bold))
                    HStack(spacing: 6) {
                        ForEach(["Empty", "Seats", "Half", "Standing", "Packed"], id: \.self) { level in
                            let active = crowdLevel == level
                            Button {
                                crowdLevel = level
                            } label: {
                                Text(level)
                                    .font(.system(size: 10, weight: .semibold))
                                    .foregroundStyle(active ? .white : .primary)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 11)
                                    .background(active ? SyrmosTokens.suburban : Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 13, style: .continuous))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                if let selected {
                    HStack(spacing: 8) {
                        Text("✓ \(pulseText(language, "Report sent", "Η αναφορα σταλθηκε", "Raporti u dergua", "Segnalazione inviata")) · \(selected.localized(language))")
                            .font(.subheadline.weight(.bold))
                            .frame(maxWidth: .infinity)
                        if canUndo {
                            Button(pulseText(language, "Undo", "Ανακληση", "Zhbëj", "Annulla")) {
                                contributionStore.undoContribution()
                                self.selected = nil
                                hasRecorded = false
                                canUndo = false
                            }
                            .font(.caption.weight(.bold))
                            .buttonStyle(.plain)
                        }
                    }
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 14)
                    .background(SyrmosTokens.live, in: RoundedRectangle(cornerRadius: 17, style: .continuous))
                    Text(pulseText(language, "One tap sent it. Refine above or undo for 10 seconds. Stored only on this device until anonymous submission is available.", "Ενα πατημα την εστειλε. Βελτιωσε παραπανω η ανακαλεσε για 10 δευτερολεπτα. Αποθηκευεται μονο σε αυτη τη συσκευη μεχρι να διατεθει ανωνυμη αποστολη.", "Nje prekje e dergoi. Perditesoje lart ose zhbëje per 10 sekonda. Ruhet vetem ne kete pajisje derisa te jete gati dergimi anonim.", "Un tocco l'ha inviata. Modifica sopra o annulla entro 10 secondi. Salvata solo sul dispositivo finche l'invio anonimo non sara disponibile."))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                }
                Text(pulseText(language, "For immediate danger, contact emergency services. RailPulse is not an emergency channel.", "Για αμεσο κινδυνο, επικοινωνησε με τις υπηρεσιες εκτακτης αναγκης. Το RailPulse δεν ειναι καναλι εκτακτης αναγκης.", "Per rrezik te menjehershem, kontakto sherbimet e emergjences. RailPulse nuk eshte kanal emergjence.", "Per un pericolo immediato, contatta i servizi di emergenza. RailPulse non e un canale di emergenza."))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(SyrmosTokens.warning)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(SyrmosTokens.warning.opacity(0.12), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .padding(20)
        }
        .background(Color.syrmosBackground)
        .task(id: canUndo) {
            guard canUndo else { return }
            try? await Task.sleep(nanoseconds: 10_000_000_000)
            guard !Task.isCancelled else { return }
            canUndo = false
        }
    }
}

func pulseText(
    _ language: AppLanguage,
    _ english: String,
    _ greek: String,
    _ albanian: String,
    _ italian: String
) -> String {
    switch language {
    case .english: return english
    case .greek: return greek
    case .albanian: return albanian
    case .italian: return italian
    }
}

#Preview("Explore RailPulse") {
    ScrollView {
        ExploreRailPulseContent(language: .english, onReport: { _ in }, onOpenStation: {}, onOpenTrain: {})
            .padding()
    }
    .background(Color.syrmosBackground)
}

#Preview("Quick report") {
    RailPulseQuickReportSheet(
        context: RailPulseReportContext(title: "Kallithea to Monastiraki", subtitle: "M1 toward Kifissia"),
        language: .english
    )
}
