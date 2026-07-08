import SwiftUI
import UIKit

struct SyrmosSettingsView: View {
    @Environment(\.openURL) private var openURL
    @ObservedObject private var loc = LocalizationManager.shared
    @ObservedObject private var schedules = SyrmosSchedulesStore.shared
    @ObservedObject private var themeManager = ThemeManager.shared
    @State private var refreshAlert: RefreshAlert?
    @State private var showContactSheet = false
    @State private var showSystemMap = false
    /// Developer toggle: forces the severe-weather card to render on Home
    /// regardless of the actual weather condition, so we can smoke-test the
    /// card without waiting for a storm to roll in. Persists across launches
    /// (helpful when handing the phone to a designer to review the layout).
    @AppStorage("syrmos.dev.forceEmergencyPreview") private var forceEmergencyPreview: Bool = false

    private struct RefreshAlert: Identifiable {
        let id = UUID()
        let title: String
        let message: String
        let isSuccess: Bool
    }

    var body: some View {
        NavigationStack {
            List {
                    Section(loc[.preferences]) {
                    Picker(loc[.language], selection: $loc.language) {
                        ForEach(AppLanguage.allCases, id: \.self) { lang in
                            Text(lang.displayName).tag(lang)
                        }
                    }
                    Picker(loc[.theme], selection: $themeManager.theme) {
                        ForEach(AppTheme.allCases) { theme in
                            Text(theme.localizedName(loc.language)).tag(theme)
                        }
                    }
                }

                Section(loc[.data]) {
                    LabeledContent(loc[.stations], value: "90+")
                    LabeledContent(loc[.lines], value: "9")
                    LabeledContent(lastUpdatedLabel, value: lastSyncLabel)
                    Button {
                        Task { await runRefresh() }
                    } label: {
                        HStack {
                            Label(checkNowLabel, systemImage: "arrow.clockwise")
                            if schedules.isRefreshing {
                                Spacer()
                                ProgressView()
                            }
                        }
                    }
                    // Always tappable. The previous gate disabled the
                    // button whenever the offline-only toggle was on,
                    // but that misread the product intent: Syrmos is an
                    // offline app by default, and Check now is the only
                    // way the user gets fresh server data. Forcing the
                    // user to toggle "offline mode" off first to then
                    // tap Check now was friction with no purpose.
                    .disabled(schedules.isRefreshing)
                }

                Section {
                    NavigationLink {
                        FaresView()
                    } label: {
                        Label(
                            loc.language == .greek ? "Τιμοκατάλογος εισιτηρίων (OASA)" : loc.language == .albanian ? "Çmimet e biletave (OASA)" : "Ticket prices (OASA)",
                            systemImage: "eurosign.circle"
                        )
                    }
                    Label {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(loc.language == .greek ? "Ανέπαφη πληρωμή" : loc.language == .albanian ? "Pagesa pa kontakt" : "Contactless payment")
                                .font(.body)
                            Text(loc.language == .greek
                                 ? "Πληρώστε στις πύλες μετρό/τραμ ή μέσα σε τραμ και τρένα με Apple Pay, Google Wallet ή ανέπαφη κάρτα."
                                 : loc.language == .albanian
                                 ? "Paguaj në portat e metros/tramvajit ose brenda tramvajeve dhe trenave me Apple Pay, Google Wallet ose çdo kartë pa kontakt."
                                 : "Tap to pay at metro/tram gates and onboard trams and trains with Apple Pay, Google Wallet, or any contactless card.")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "wave.3.right.circle")
                    }
                } header: {
                    Text(loc.language == .greek ? "Εισιτήρια" : loc.language == .albanian ? "Bileta" : "Tickets")
                } footer: {
                    Text(loc.language == .greek
                         ? "Οι τιμές και η διαθεσιμότητα διαχειρίζονται από τον ΟΑΣΑ. Το Syrmos δεν αποθηκεύει τιμές — απλώς ανοίγει την επίσημη σελίδα."
                         : loc.language == .albanian
                         ? "Çmimet dhe disponueshmëria menaxhohen nga OASA. Syrmos nuk ruan çmime, thjesht hap faqen zyrtare."
                         : "Prices and availability are managed by OASA. Syrmos does not store prices — it just opens the official page.")
                        .font(.caption2)
                }

                Section(loc[.about]) {
                    Text(loc[.aboutText])
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                Section {
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Label(ariadneEngineTitle, systemImage: ariadneEngineIcon)
                            Spacer()
                            Text(ariadneEngineValue)
                                .font(.subheadline)
                                .foregroundStyle(AriadneBrain.availability.isClever ? Color.accentColor : .secondary)
                        }
                        Text(ariadneEngineDetail)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        if ariadneCanOpenSettings {
                            Button(ariadneOpenSettingsLabel) {
                                if let url = URL(string: UIApplication.openSettingsURLString) {
                                    openURL(url)
                                }
                            }
                            .font(.caption)
                        }
                    }
                } header: {
                    Text(ariadneEngineTitle)
                }

                Section(loc.language == .greek ? "Ανάπτυξη" : loc.language == .albanian ? "Zhvillim" : "Developer") {
                    Toggle(isOn: $forceEmergencyPreview) {
                        Label(
                            loc.language == .greek ? "Προεπισκόπηση κακοκαιρίας"
                                : loc.language == .albanian ? "Paraafisho paralajmërim moti"
                                : "Preview severe-weather card",
                            systemImage: "cloud.bolt.rain.fill"
                        )
                    }
                    Text(
                        loc.language == .greek ? "Δείχνει την κόκκινη κάρτα στην Αρχική χωρίς να χρειάζεται πραγματική καταιγίδα."
                            : loc.language == .albanian ? "Shfaq kartën e paralajmërimit në Home pa nevojën për stuhi të vërtetë."
                            : "Forces the amber warning card on Home so you can smoke-test it without waiting for a storm."
                    )
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }

                Section(loc.language == .greek ? "Χάρτης" : loc.language == .albanian ? "Harta" : "Map") {
                    Button {
                        showSystemMap = true
                    } label: {
                        HStack {
                            Label(
                                loc.language == .greek ? "Σιδηροδρομικό δίκτυο Αθήνας" : loc.language == .albanian ? "Hekurudhat e zonës metropolitane të Athinës" : "Athens metropolitan area railways",
                                systemImage: "map"
                            )
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.footnote)
                                .foregroundStyle(.tertiary)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }

                Section(loc.language == .greek ? "Επικοινωνία" : loc.language == .albanian ? "Kontakt" : "Contact") {
                    Button {
                        showContactSheet = true
                    } label: {
                        HStack {
                            Label(
                                loc.language == .greek ? "Επικοινωνία με τον μηχανικό" : loc.language == .albanian ? "Kontakto zhvilluesin" : "Contact engineer",
                                systemImage: "envelope"
                            )
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.footnote)
                                .foregroundStyle(.tertiary)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }

                #if DEBUG
                Section {
                    NavigationLink {
                        DiagnosticsView()
                    } label: {
                        Label(
                            loc.language == .greek ? "Διαγνωστικά" : loc.language == .albanian ? "Diagnostika" : "Diagnostics",
                            systemImage: "stethoscope"
                        )
                    }
                }
                #endif
            }
            .scrollContentBackground(.hidden)
            .background(Color.syrmosBackground)
            .safeAreaInset(edge: .top, spacing: 8) {
                CompactTabHeader(loc[.settings])
            }
            .toolbar(.hidden, for: .navigationBar)
            .sheet(isPresented: $showSystemMap) {
                StasyMapView()
            }
            .sheet(isPresented: $showContactSheet) {
                NavigationStack {
                    ContactDeveloperView()
                        .toolbar {
                            ToolbarItem(placement: .topBarTrailing) {
                                Button(loc.language == .greek ? "Κλείσιμο" : loc.language == .albanian ? "Mbylle" : "Close") {
                                    showContactSheet = false
                                }
                            }
                        }
                }
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
            }
            .alert(item: $refreshAlert) { alert in
                Alert(
                    title: Text(alert.title),
                    message: Text(alert.message),
                    dismissButton: .default(Text("OK"))
                )
            }
        }
    }

    @MainActor
    private func runRefresh() async {
        let before = schedules.lastSyncAt
        // Refresh EVERY store we ship, not just the schedule rules.
        // Previously Check now only hit /api/schedules/manifest; the
        // fares, train timestamps, station offsets, visual overrides
        // and live train feeds were on independent auto-poll loops
        // that have now been disabled in favour of this single
        // explicit pull. Sequential rather than parallel so the user
        // sees one progress indicator and we don't flood the Pi with
        // 6 simultaneous requests on a flaky mobile link.
        await schedules.refresh()
        let lines = SyrmosLinesService()
        await lines.refresh()
        await SyrmosStationOffsetsStore.shared.refresh()
        await SyrmosVisualOverridesStore.shared.refresh()
        await SyrmosTrainTimestampsStore.shared.refresh()
        await SyrmosFaresStore.shared.refresh()
        await LivePositionsService.shared.refresh()
        await LiveTrainService.shared.refresh()
        let after = schedules.lastSyncAt
        let lang = loc.language

        if after != nil, after != before {
            refreshAlert = RefreshAlert(
                title: lang == .greek ? "Ενημερώθηκε" : lang == .albanian ? "I përditësuar" : "Up to date",
                message: lang == .greek
                    ? "Τα δρομολόγια συγχρονίστηκαν με την τελευταία έκδοση."
                    : lang == .albanian
                    ? "Oraret u sinkronizuan me versionin më të fundit."
                    : "Schedules synced with the latest version.",
                isSuccess: true
            )
        } else {
            refreshAlert = RefreshAlert(
                title: lang == .greek ? "Δεν ήταν δυνατή η ενημέρωση" : lang == .albanian ? "Përditësimi dështoi" : "Update failed",
                message: lang == .greek
                    ? "Δεν φτάσαμε στον διακομιστή. Δοκιμάστε ξανά με σύνδεση στο διαδίκτυο."
                    : lang == .albanian
                    ? "Nuk arritëm te serveri. Provo përsëri me një lidhje të qëndrueshme."
                    : "Could not reach the server. Try again on a stable connection.",
                isSuccess: false
            )
        }
    }

    private var scheduleVersionLabel: String {
        if let v = schedules.manifestVersion { return "v\(v)" }
        return "3.0"
    }

    private var lastSyncLabel: String {
        guard let date = schedules.lastSyncAt else {
            return loc.language == .greek ? "Ποτέ" : loc.language == .albanian ? "Asnjëherë" : "Never"
        }
        let f = DateFormatter()
        f.dateStyle = .short
        f.timeStyle = .short
        return f.string(from: date)
    }

    private var lastUpdatedLabel: String {
        loc.language == .greek ? "Τελευταία ενημέρωση" : loc.language == .albanian ? "Përditësimi i fundit" : "Last updated"
    }

    private var checkNowLabel: String {
        loc.language == .greek ? "Έλεγχος τώρα" : loc.language == .albanian ? "Kontrollo tani" : "Check now"
    }

    // MARK: Ariadne engine diagnostic

    private var ariadneEngineTitle: String {
        loc.language == .greek ? "Μηχανή Ariadne" : loc.language == .albanian ? "Motori i Ariadne" : "Ariadne engine"
    }

    private var ariadneEngineIcon: String {
        AriadneBrain.availability.isClever ? "sparkles" : "text.magnifyingglass"
    }

    /// "Clever mode" when Apple Intelligence is live, else "Rule parser".
    private var ariadneEngineValue: String {
        if AriadneBrain.availability.isClever {
            return loc.language == .greek ? "Έξυπνη λειτουργία" : loc.language == .albanian ? "Modaliteti i zgjuar" : "Clever mode"
        }
        return loc.language == .greek ? "Αναλυτής κανόνων" : loc.language == .albanian ? "Analizues rregullash" : "Rule parser"
    }

    /// One-line reason, so the user understands why the smart engine is or isn't on.
    private var ariadneEngineDetail: String {
        let el = loc.language == .greek, sq = loc.language == .albanian
        switch AriadneBrain.availability {
        case .available:
            return el ? "Το Apple Intelligence διορθώνει την ερώτησή σας πριν την ανάλυση, εξ ολοκλήρου στη συσκευή."
                : sq ? "Apple Intelligence rregullon pyetjen tuaj para analizës, plotësisht në pajisje."
                : "Apple Intelligence cleans up your question before parsing, fully on device."
        case .appleIntelligenceNotEnabled:
            return el ? "Το Apple Intelligence δεν είναι ενεργό. Ενεργοποιήστε το στις Ρυθμίσεις για την Έξυπνη λειτουργία."
                : sq ? "Apple Intelligence është i çaktivizuar. Aktivizoje te Cilësimet për Modalitetin e zgjuar."
                : "Apple Intelligence is off. Turn it on in Settings for Clever mode."
        case .modelNotReady:
            return el ? "Το μοντέλο κατεβαίνει ακόμη. Δοκιμάστε ξανά σε λίγο."
                : sq ? "Modeli po shkarkohet ende. Provo përsëri pas pak."
                : "The model is still downloading. Try again shortly."
        case .deviceNotEligible:
            return el ? "Αυτή η συσκευή δεν υποστηρίζει το Apple Intelligence. Το Syrmos χρησιμοποιεί τον αναλυτή κανόνων."
                : sq ? "Kjo pajisje nuk mbështet Apple Intelligence. Syrmos përdor analizuesin e rregullave."
                : "This device can't run Apple Intelligence. Syrmos uses the rule parser."
        case .osTooOld:
            return el ? "Απαιτείται iOS 26 ή νεότερο για την Έξυπνη λειτουργία. Το Syrmos χρησιμοποιεί τον αναλυτή κανόνων."
                : sq ? "Kërkohet iOS 26 ose më i ri për Modalitetin e zgjuar. Syrmos përdor analizuesin e rregullave."
                : "iOS 26 or newer is needed for Clever mode. Syrmos uses the rule parser."
        }
    }

    /// Only show the Settings shortcut when the user can actually flip
    /// something: Apple Intelligence is supported here but switched off. For
    /// `.modelNotReady` the model is mid-download (nothing to toggle), and for
    /// `.deviceNotEligible` / `.osTooOld` there's no setting that would help,
    /// so the explanatory detail line stands on its own.
    private var ariadneCanOpenSettings: Bool {
        if case .appleIntelligenceNotEnabled = AriadneBrain.availability { return true }
        return false
    }

    /// "Open Settings" — deep-links to the system Settings app so the user can
    /// enable Apple Intelligence, matching the "Turn it on in Settings" copy.
    private var ariadneOpenSettingsLabel: String {
        loc.language == .greek ? "Άνοιγμα Ρυθμίσεων"
            : loc.language == .albanian ? "Hap Cilësimet"
            : "Open Settings"
    }
}

/// User-facing diagnostics screen. Shows recent breadcrumbs, detected
/// main-thread hangs, and a Share button that exports a JSON bundle the
/// user can send to support if they hit a freeze.
struct DiagnosticsView: View {
    @ObservedObject private var center = DiagnosticsCenter.shared
    @ObservedObject private var loc = LocalizationManager.shared
    @State private var shareURL: URL?

    var body: some View {
        List {
            Section {
                Button {
                    if let url = center.shareableBundleURL() {
                        shareURL = url
                    }
                } label: {
                    Label(
                        loc.language == .greek ? "Εξαγωγή διαγνωστικών" : loc.language == .albanian ? "Eksporto diagnostikën" : "Export diagnostics",
                        systemImage: "square.and.arrow.up"
                    )
                }
            } footer: {
                Text(loc.language == .greek
                     ? "Δημιουργεί ένα αρχείο JSON με τα τελευταία συμβάντα της εφαρμογής. Μπορείτε να το στείλετε στον προγραμματιστή για διάγνωση παγωμάτων."
                     : loc.language == .albanian
                     ? "Krijon një skedar JSON me ngjarjet e fundit të aplikacionit. Mund t'ia dërgosh zhvilluesit për të diagnostikuar ngrirjet."
                     : "Creates a JSON file with the app's recent events. You can send it to the developer to diagnose freezes.")
            }

            if !center.hangs.isEmpty {
                Section(loc.language == .greek ? "Παγώματα" : loc.language == .albanian ? "Ngrirje" : "Hangs") {
                    ForEach(center.hangs.reversed()) { hang in
                        VStack(alignment: .leading, spacing: 4) {
                            Text("\(hang.durationMs) ms")
                                .font(.headline)
                                .foregroundStyle(.orange)
                            Text(hang.timestamp, style: .relative)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }

            Section(loc.language == .greek ? "Πρόσφατα συμβάντα" : loc.language == .albanian ? "Ngjarjet e fundit" : "Recent events") {
                ForEach(center.breadcrumbs.suffix(40).reversed()) { crumb in
                    VStack(alignment: .leading, spacing: 2) {
                        HStack {
                            Text(crumb.category)
                                .font(.caption2)
                                .fontWeight(.semibold)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.secondary.opacity(0.15))
                                .clipShape(Capsule())
                            Spacer()
                            Text(crumb.timestamp, style: .time)
                                .font(.caption2)
                                .foregroundStyle(.tertiary)
                                .monospacedDigit()
                        }
                        Text(crumb.message)
                            .font(.footnote)
                    }
                    .padding(.vertical, 2)
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.syrmosBackground)
        .navigationTitle(loc.language == .greek ? "Διαγνωστικά" : loc.language == .albanian ? "Diagnostika" : "Diagnostics")
        .sheet(item: Binding(
            get: { shareURL.map { IdentifiableURL(url: $0) } },
            set: { shareURL = $0?.url }
        )) { wrapped in
            ShareSheet(items: [wrapped.url])
        }
    }
}

private struct IdentifiableURL: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}

private struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
