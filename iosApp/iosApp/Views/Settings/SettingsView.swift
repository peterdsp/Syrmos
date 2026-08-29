import SwiftUI
import UIKit

struct SyrmosSettingsView: View {
    @Environment(\.openURL) private var openURL
    @ObservedObject private var loc = LocalizationManager.shared
    @ObservedObject private var schedules = SyrmosSchedulesStore.shared
    @ObservedObject private var themeManager = ThemeManager.shared
    @State private var refreshAlert: RefreshAlert?
    @State private var showContactSheet = false
    @State private var showAriadne = false
    @AppStorage("syrmos.dev.forceEmergencyPreview") private var forceEmergencyPreview: Bool = false
    @AppStorage("syrmos.map.showLiveVehicles") private var showLiveVehicles: Bool = true
    @AppStorage("syrmos.map.defaultRegion") private var defaultRegionRaw: String = "athens"

    private struct RefreshAlert: Identifiable {
        let id = UUID()
        let title: String
        let message: String
        let isSuccess: Bool
    }

    var body: some View {
        NavigationStack {
            List {
                ariadneSection
                preferencesSection
                mapPreferencesSection
                operatorsSection
                savedStationsSection
                notificationsSection
                dataSection
                ticketsSection
                aboutSection

                if BuildEnv.isInternalBuild {
                    developerSection
                }

                contactSection

                #if DEBUG
                Section {
                    NavigationLink {
                        DiagnosticsView()
                    } label: {
                        Label(
                            loc.language == .greek ? "Διαγνωστικα" : loc.language == .albanian ? "Diagnostika" : loc.language == .italian ? "Diagnostica" : "Diagnostics",
                            systemImage: "stethoscope"
                        )
                    }
                }
                #endif
            }
            .scrollContentBackground(.hidden)
            .background(Color.syrmosBackground)
            .safeAreaInset(edge: .top, spacing: 8) {
                CompactTabHeader(loc[.moreTab])
            }
            .toolbar(.hidden, for: .navigationBar)
            .sheet(isPresented: $showAriadne) {
                AriadneView()
            }
            .sheet(isPresented: $showContactSheet) {
                NavigationStack {
                    ContactDeveloperView()
                        .toolbar {
                            ToolbarItem(placement: .topBarTrailing) {
                                Button(loc.language == .greek ? "Κλεισιμο" : loc.language == .albanian ? "Mbylle" : loc.language == .italian ? "Chiudi" : "Close") {
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

    // MARK: - Ariadne

    private var ariadneSection: some View {
        Section {
            Button {
                showAriadne = true
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: "sparkles")
                        .font(.title3)
                        .foregroundStyle(.purple)
                        .frame(width: 32, height: 32)
                        .background(.purple.opacity(0.12), in: RoundedRectangle(cornerRadius: 8, style: .continuous))

                    VStack(alignment: .leading, spacing: 2) {
                        Text("Ariadne")
                            .font(.headline)
                            .foregroundStyle(.primary)
                        Text(loc.language == .greek ? "Ο βοηθος σου στα τρενα" :
                             loc.language == .albanian ? "Asistenti yt i trenave" :
                             loc.language == .italian ? "Il tuo assistente ferroviario" :
                             "Your rail assistant")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Spacer()

                    Image(systemName: "chevron.right")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.tertiary)
                }
            }
            .buttonStyle(.plain)
        } header: {
            Text(loc.language == .greek ? "Βοηθός" : loc.language == .albanian ? "Asistent" : loc.language == .italian ? "Assistente" : "Assistant")
        }
    }

    // MARK: - Preferences

    private var preferencesSection: some View {
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
    }

    // MARK: - Map Preferences

    private var mapPreferencesSection: some View {
        Section {
            Toggle(isOn: $showLiveVehicles) {
                Label(
                    loc.language == .greek ? "Ζωντανα οχηματα" :
                    loc.language == .albanian ? "Mjetet e gjalla" :
                    loc.language == .italian ? "Veicoli in tempo reale" :
                    "Live vehicles",
                    systemImage: "train.side.front.car"
                )
            }

            Picker(
                loc.language == .greek ? "Προεπιλεγμενη περιοχη" :
                loc.language == .albanian ? "Rajoni i parazgjedhur" :
                loc.language == .italian ? "Regione predefinita" :
                "Default region",
                selection: $defaultRegionRaw
            ) {
                Text(loc.language == .greek ? "Αθηνα" : loc.language == .albanian ? "Athine" : loc.language == .italian ? "Atene" : "Athens").tag("athens")
                Text(loc.language == .greek ? "Θεσσαλονικη" : loc.language == .albanian ? "Selanik" : loc.language == .italian ? "Salonicco" : "Thessaloniki").tag("thessaloniki")
                Text(loc.language == .greek ? "Πατρα" : loc.language == .albanian ? "Patra" : loc.language == .italian ? "Patrasso" : "Patras").tag("patras")
                Text(loc.language == .greek ? "Ολη η Ελλαδα" : loc.language == .albanian ? "E gjithe Greqia" : loc.language == .italian ? "Tutta la Grecia" : "All Greece").tag("national")
            }
        } header: {
            Text(loc.language == .greek ? "Χαρτης" : loc.language == .albanian ? "Harta" : loc.language == .italian ? "Preferenze mappa" : "Map preferences")
        } footer: {
            Text(loc.language == .greek ? "Τα ζωντανα οχηματα εμφανιζονται σαν κινουμενα τριγωνα στον χαρτη." :
                 loc.language == .albanian ? "Mjetet e gjalla shfaqen si trekendsha levizes ne harte." :
                 loc.language == .italian ? "I veicoli in tempo reale appaiono come triangoli in movimento sulla mappa." :
                 "Live vehicles appear as moving triangles on the map.")
                .font(.caption2)
        }
    }

    // MARK: - Operators

    private var operatorsSection: some View {
        Section {
            OperatorRow(
                name: "STASY",
                detail: loc.language == .greek ? "Μετρο & Τραμ Αθηνας" :
                        loc.language == .albanian ? "Metro & Tramvaj Athine" :
                        loc.language == .italian ? "Metro e Tram di Atene" :
                        "Athens Metro & Tram",
                icon: "tram.tunnel.fill",
                tint: .metroBlue,
                url: "https://www.stasy.gr"
            )
            OperatorRow(
                name: "OASA",
                detail: loc.language == .greek ? "Αστικες συγκοινωνιες Αθηνας" :
                        loc.language == .albanian ? "Transporti publik Athine" :
                        loc.language == .italian ? "Trasporto pubblico di Atene" :
                        "Athens public transport",
                icon: "bus.fill",
                tint: .orange,
                url: "https://www.oasa.gr"
            )
            OperatorRow(
                name: "Hellenic Train",
                detail: loc.language == .greek ? "Προαστιακος & Υπεραστικα" :
                        loc.language == .albanian ? "Periferike & Nderqytetese" :
                        loc.language == .italian ? "Suburbano e Intercity" :
                        "Suburban & Intercity",
                icon: "train.side.front.car",
                tint: .suburbanPurple,
                url: "https://www.hellenictrain.gr"
            )
            OperatorRow(
                name: "OSETH",
                detail: loc.language == .greek ? "Μετρο Θεσσαλονικης" :
                        loc.language == .albanian ? "Metro Selanik" :
                        loc.language == .italian ? "Metro di Salonicco" :
                        "Thessaloniki Metro",
                icon: "tram.fill",
                tint: .red,
                url: "https://www.oseth.gr"
            )
        } header: {
            Text(loc.language == .greek ? "Διαχειριστες" : loc.language == .albanian ? "Operatoret" : loc.language == .italian ? "Operatori" : "Operators")
        } footer: {
            Text(loc.language == .greek ? "Οι τιμες και τα δρομολογια διαχειριζονται απο τους αντιστοιχους φορεις." :
                 loc.language == .albanian ? "Cmimet dhe oraret menaxhohen nga operatoret perkates." :
                 loc.language == .italian ? "Tariffe e orari sono gestiti dai rispettivi operatori." :
                 "Fares and schedules are managed by their respective operators.")
                .font(.caption2)
        }
    }

    // MARK: - Saved Stations

    private var savedStationsSection: some View {
        let recents = RecentStationStore.load()
        return Group {
            if !recents.isEmpty {
                Section {
                    ForEach(recents.prefix(5)) { recent in
                        if let line = SyrmosData.line(for: recent.lineId) {
                            let station = SyrmosData.stations(for: recent.lineId).first { $0.id == recent.stationId }
                            NavigationLink {
                                DestinationDetailView(
                                    stationId: recent.stationId,
                                    lineId: recent.lineId,
                                    onStationViewed: { _, _ in }
                                )
                            } label: {
                                HStack(spacing: 10) {
                                    Circle()
                                        .fill(line.color)
                                        .frame(width: 10, height: 10)
                                    Text(loc.language == .greek ? (station?.nameEl ?? recent.stationId) : (station?.name ?? recent.stationId))
                                        .font(.subheadline)
                                    Spacer()
                                    Text(line.localizedName(loc.language))
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                } header: {
                    Text(loc.language == .greek ? "Προσφατοι σταθμοι" : loc.language == .albanian ? "Stacionet e fundit" : loc.language == .italian ? "Stazioni recenti" : "Recent stations")
                }
            }
        }
    }

    // MARK: - Notifications

    private var notificationsSection: some View {
        Section(loc.language == .greek ? "Ειδοποιησεις" : loc.language == .albanian ? "Njoftimet" : loc.language == .italian ? "Notifiche" : "Notifications") {
            Toggle(isOn: Binding(
                get: { NotificationPreferences.serviceAlertsEnabled },
                set: { NotificationPreferences.serviceAlertsEnabled = $0 }
            )) {
                Label(
                    loc.language == .greek ? "Ειδοποιησεις υπηρεσιας" : loc.language == .albanian ? "Njoftimet e sherbimit" : loc.language == .italian ? "Avvisi di servizio" : "Service alerts",
                    systemImage: "exclamationmark.triangle"
                )
            }
            Toggle(isOn: Binding(
                get: { NotificationPreferences.weatherAlertsEnabled },
                set: { NotificationPreferences.weatherAlertsEnabled = $0 }
            )) {
                Label(
                    loc.language == .greek ? "Καιρικες ειδοποιησεις" : loc.language == .albanian ? "Njoftimet e motit" : loc.language == .italian ? "Avvisi meteo" : "Weather alerts",
                    systemImage: "cloud.bolt.rain"
                )
            }
            Toggle(isOn: Binding(
                get: { NotificationPreferences.nearbyAlertsEnabled },
                set: { NotificationPreferences.nearbyAlertsEnabled = $0 }
            )) {
                Label(
                    loc.language == .greek ? "Ειδοποιησεις κοντινου σταθμου" : loc.language == .albanian ? "Njoftimet e stacionit te afert" : loc.language == .italian ? "Avvisi stazione vicina" : "Nearby station alerts",
                    systemImage: "location.circle"
                )
            }
            Toggle(isOn: Binding(
                get: { NotificationPreferences.morningDigestEnabled },
                set: {
                    NotificationPreferences.morningDigestEnabled = $0
                    NotificationService.shared.scheduleMorningDigest()
                }
            )) {
                Label(
                    loc.language == .greek ? "Πρωινη ενημερωση (07:00)" : loc.language == .albanian ? "Perditesimi i mengjesit (07:00)" : loc.language == .italian ? "Riepilogo mattutino (07:00)" : "Morning digest (07:00)",
                    systemImage: "sunrise"
                )
            }
        }
    }

    // MARK: - Data

    private var dataSection: some View {
        Section(loc[.data]) {
            LabeledContent(loc[.stations], value: "380+")
            LabeledContent(loc[.lines], value: "31")
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
            .disabled(schedules.isRefreshing)
        }
    }

    // MARK: - Tickets

    private var ticketsSection: some View {
        Section {
            NavigationLink {
                FaresView()
            } label: {
                Label(
                    loc.language == .greek ? "Τιμοκαταλογος εισιτηριων" : loc.language == .albanian ? "Cmimet e biletave" : loc.language == .italian ? "Prezzi biglietti" : "Ticket prices",
                    systemImage: "eurosign.circle"
                )
            }
            Label {
                VStack(alignment: .leading, spacing: 2) {
                    Text(loc.language == .greek ? "Ανεπαφη πληρωμη" : loc.language == .albanian ? "Pagesa pa kontakt" : loc.language == .italian ? "Pagamento contactless" : "Contactless payment")
                        .font(.body)
                    Text(loc.language == .greek
                         ? "Πληρωστε στις πυλες μετρο/τραμ η μεσα σε τραμ και τρενα με Apple Pay, Google Wallet η ανεπαφη καρτα."
                         : loc.language == .albanian
                         ? "Paguaj ne portat e metros/tramvajit ose brenda tramvajeve dhe trenave me Apple Pay, Google Wallet ose cdo karte pa kontakt."
                         : loc.language == .italian
                         ? "Paga ai tornelli metro/tram e a bordo di tram e treni con Apple Pay, Google Wallet o qualsiasi carta contactless."
                         : "Tap to pay at metro/tram gates and onboard trams and trains with Apple Pay, Google Wallet, or any contactless card.")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            } icon: {
                Image(systemName: "wave.3.right.circle")
            }
        } header: {
            Text(loc.language == .greek ? "Εισιτηρια" : loc.language == .albanian ? "Bileta" : loc.language == .italian ? "Biglietti" : "Tickets")
        }
    }

    // MARK: - About

    private var aboutSection: some View {
        Section(loc[.about]) {
            Text(loc[.aboutText])
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }

    // MARK: - Developer

    private var developerSection: some View {
        Section(loc.language == .greek ? "Αναπτυξη" : loc.language == .albanian ? "Zhvillim" : loc.language == .italian ? "Sviluppo" : "Developer") {
            Toggle(isOn: $forceEmergencyPreview) {
                Label(
                    loc.language == .greek ? "Προεπισκοπηση κακοκαιριας"
                        : loc.language == .albanian ? "Paraafisho paralajmerim moti"
                        : loc.language == .italian ? "Anteprima allerta maltempo"
                        : "Preview severe-weather card",
                    systemImage: "cloud.bolt.rain.fill"
                )
            }
            Text(
                loc.language == .greek ? "Δειχνει την κοκκινη καρτα στην Αρχικη χωρις να χρειαζεται πραγματικη καταιγιδα."
                    : loc.language == .albanian ? "Shfaq karten e paralajmerimit ne Home pa nevoje per stuhi te vertete."
                    : loc.language == .italian ? "Mostra la scheda di allerta nella Home senza bisogno di una vera tempesta."
                    : "Forces the amber warning card on Home so you can smoke-test it without waiting for a storm."
            )
            .font(.caption)
            .foregroundStyle(.secondary)
        }
    }

    // MARK: - Contact

    private var contactSection: some View {
        Section(loc.language == .greek ? "Επικοινωνια" : loc.language == .albanian ? "Kontakt" : loc.language == .italian ? "Contatti" : "Contact") {
            Button {
                showContactSheet = true
            } label: {
                HStack {
                    Label(
                        loc.language == .greek ? "Επικοινωνια με τον μηχανικο" : loc.language == .albanian ? "Kontakto zhvilluesin" : loc.language == .italian ? "Contatta lo sviluppatore" : "Contact engineer",
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
    }

    // MARK: - Helpers

    @MainActor
    private func runRefresh() async {
        let before = schedules.lastSyncAt
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
                title: lang == .greek ? "Ενημερωθηκε" : lang == .albanian ? "I perditesuar" : lang == .italian ? "Aggiornato" : "Up to date",
                message: lang == .greek
                    ? "Τα δρομολογια συγχρονιστηκαν με την τελευταια εκδοση."
                    : lang == .albanian
                    ? "Oraret u sinkronizuan me versionin me te fundit."
                    : lang == .italian
                    ? "Gli orari sono stati sincronizzati con l'ultima versione."
                    : "Schedules synced with the latest version.",
                isSuccess: true
            )
        } else {
            refreshAlert = RefreshAlert(
                title: lang == .greek ? "Δεν ηταν δυνατη η ενημερωση" : lang == .albanian ? "Perditesimi deshtoi" : lang == .italian ? "Aggiornamento fallito" : "Update failed",
                message: lang == .greek
                    ? "Δεν φτασαμε στον διακομιστη. Δοκιμαστε ξανα με συνδεση στο διαδικτυο."
                    : lang == .albanian
                    ? "Nuk arritem te serveri. Provo perseri me nje lidhje te qendrueshme."
                    : lang == .italian
                    ? "Impossibile raggiungere il server. Riprova con una connessione stabile."
                    : "Could not reach the server. Try again on a stable connection.",
                isSuccess: false
            )
        }
    }

    private var lastSyncLabel: String {
        guard let date = schedules.lastSyncAt else {
            return loc.language == .greek ? "Ποτε" : loc.language == .albanian ? "Asnjehere" : loc.language == .italian ? "Mai" : "Never"
        }
        let f = DateFormatter()
        f.dateStyle = .short
        f.timeStyle = .short
        return f.string(from: date)
    }

    private var lastUpdatedLabel: String {
        loc.language == .greek ? "Τελευταια ενημερωση" : loc.language == .albanian ? "Perditesimi i fundit" : loc.language == .italian ? "Ultimo aggiornamento" : "Last updated"
    }

    private var checkNowLabel: String {
        loc.language == .greek ? "Ελεγχος τωρα" : loc.language == .albanian ? "Kontrollo tani" : loc.language == .italian ? "Controlla ora" : "Check now"
    }
}

// MARK: - Operator Row

private struct OperatorRow: View {
    let name: String
    let detail: String
    let icon: String
    let tint: Color
    let url: String
    @Environment(\.openURL) private var openURL

    var body: some View {
        Button {
            if let u = URL(string: url) { openURL(u) }
        } label: {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.subheadline)
                    .foregroundStyle(tint)
                    .frame(width: 28, height: 28)
                    .background(tint.opacity(0.12), in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                VStack(alignment: .leading, spacing: 1) {
                    Text(name)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.primary)
                    Text(detail)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Image(systemName: "arrow.up.right")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Diagnostics

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
                        loc.language == .greek ? "Εξαγωγη διαγνωστικων" : loc.language == .albanian ? "Eksporto diagnostiken" : loc.language == .italian ? "Esporta diagnostica" : "Export diagnostics",
                        systemImage: "square.and.arrow.up"
                    )
                }
            } footer: {
                Text(loc.language == .greek
                     ? "Δημιουργει ενα αρχειο JSON με τα τελευταια συμβαντα της εφαρμογης."
                     : loc.language == .albanian
                     ? "Krijon nje skedar JSON me ngjarjet e fundit te aplikacionit."
                     : loc.language == .italian
                     ? "Crea un file JSON con gli eventi recenti dell'app."
                     : "Creates a JSON file with the app's recent events.")
            }

            if !center.hangs.isEmpty {
                Section(loc.language == .greek ? "Παγωματα" : loc.language == .albanian ? "Ngrirje" : loc.language == .italian ? "Blocchi" : "Hangs") {
                    ForEach(center.hangs.reversed()) { hang in
                        VStack(alignment: .leading, spacing: 4) {
                            Text("\(hang.durationMs) ms")
                                .font(.headline)
                                .foregroundStyle(SyrmosTokens.warning)
                            Text(hang.timestamp, style: .relative)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }

            Section(loc.language == .greek ? "Προσφατα συμβαντα" : loc.language == .albanian ? "Ngjarjet e fundit" : loc.language == .italian ? "Eventi recenti" : "Recent events") {
                ForEach(center.breadcrumbs.suffix(40).reversed()) { crumb in
                    VStack(alignment: .leading, spacing: 2) {
                        HStack {
                            Text(crumb.category)
                                .font(.caption2)
                                .fontWeight(.semibold)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.syrmosOnSurfaceMuted.opacity(0.15))
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
        .navigationTitle(loc.language == .greek ? "Διαγνωστικα" : loc.language == .albanian ? "Diagnostika" : loc.language == .italian ? "Diagnostica" : "Diagnostics")
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

enum BuildEnv {
    static var isInternalBuild: Bool {
        #if DEBUG
        return true
        #else
        return Bundle.main.appStoreReceiptURL?.lastPathComponent == "sandboxReceipt"
        #endif
    }
}
