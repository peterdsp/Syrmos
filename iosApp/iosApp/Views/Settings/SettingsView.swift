import SwiftUI

struct SyrmosSettingsView: View {
    @ObservedObject private var loc = LocalizationManager.shared
    @ObservedObject private var schedules = SyrmosSchedulesStore.shared
    @ObservedObject private var themeManager = ThemeManager.shared
    @State private var safariURL: URL?
    @State private var refreshAlert: RefreshAlert?

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
                    Toggle(offlineOnlyLabel, isOn: Binding(
                        get: { schedules.offlineOnly },
                        set: { schedules.offlineOnly = $0 }
                    ))
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
                    .disabled(schedules.isRefreshing || schedules.offlineOnly)
                }

                Section {
                    Button {
                        safariURL = URL(string: "https://www.oasa.gr/en/tickets/prices-of-products/")
                    } label: {
                        Label(
                            loc.language == .greek ? "Τιμοκατάλογος εισιτηρίων (OASA)" : "Ticket prices (OASA)",
                            systemImage: "eurosign.circle"
                        )
                    }
                    Label {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(loc.language == .greek ? "Ανέπαφη πληρωμή" : "Contactless payment")
                                .font(.body)
                            Text(loc.language == .greek
                                 ? "Πληρώστε στις πύλες μετρό/τραμ ή μέσα σε τραμ και τρένα με Apple Pay, Google Wallet ή ανέπαφη κάρτα."
                                 : "Tap to pay at metro/tram gates and onboard trams and trains with Apple Pay, Google Wallet, or any contactless card.")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "wave.3.right.circle")
                    }
                } header: {
                    Text(loc.language == .greek ? "Εισιτήρια" : "Tickets")
                } footer: {
                    Text(loc.language == .greek
                         ? "Οι τιμές και η διαθεσιμότητα διαχειρίζονται από τον ΟΑΣΑ. Το Syrmos δεν αποθηκεύει τιμές — απλώς ανοίγει την επίσημη σελίδα."
                         : "Prices and availability are managed by OASA. Syrmos does not store prices — it just opens the official page.")
                        .font(.caption2)
                }

                Section(loc[.about]) {
                    Text(loc[.aboutText])
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                Section {
                    NavigationLink {
                        DiagnosticsView()
                    } label: {
                        Label(
                            loc.language == .greek ? "Διαγνωστικά" : "Diagnostics",
                            systemImage: "stethoscope"
                        )
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(Color.syrmosBackground)
            .navigationTitle(loc[.settings])
            .inAppSafari(url: $safariURL)
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
        await schedules.refresh()
        let after = schedules.lastSyncAt
        let isGreek = loc.language == .greek

        if after != nil, after != before {
            refreshAlert = RefreshAlert(
                title: isGreek ? "Ενημερώθηκε" : "Up to date",
                message: isGreek
                    ? "Τα δρομολόγια συγχρονίστηκαν με την τελευταία έκδοση."
                    : "Schedules synced with the latest version.",
                isSuccess: true
            )
        } else if schedules.offlineOnly {
            refreshAlert = RefreshAlert(
                title: isGreek ? "Λειτουργία εκτός σύνδεσης" : "Offline-only mode",
                message: isGreek
                    ? "Απενεργοποιήστε την για να συγχρονίσετε με τον διακομιστή."
                    : "Turn it off to sync with the server.",
                isSuccess: false
            )
        } else {
            refreshAlert = RefreshAlert(
                title: isGreek ? "Δεν ήταν δυνατή η ενημέρωση" : "Update failed",
                message: isGreek
                    ? "Δεν φτάσαμε στον διακομιστή. Δοκιμάστε ξανά με σύνδεση στο διαδίκτυο."
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
            return loc.language == .greek ? "Ποτέ" : "Never"
        }
        let f = DateFormatter()
        f.dateStyle = .short
        f.timeStyle = .short
        return f.string(from: date)
    }

    private var lastUpdatedLabel: String {
        loc.language == .greek ? "Τελευταία ενημέρωση" : "Last updated"
    }

    private var offlineOnlyLabel: String {
        loc.language == .greek ? "Μόνο εκτός σύνδεσης" : "Offline-only mode"
    }

    private var checkNowLabel: String {
        loc.language == .greek ? "Έλεγχος τώρα" : "Check now"
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
                        loc.language == .greek ? "Εξαγωγή διαγνωστικών" : "Export diagnostics",
                        systemImage: "square.and.arrow.up"
                    )
                }
            } footer: {
                Text(loc.language == .greek
                     ? "Δημιουργεί ένα αρχείο JSON με τα τελευταία συμβάντα της εφαρμογής. Μπορείτε να το στείλετε στον προγραμματιστή για διάγνωση παγωμάτων."
                     : "Creates a JSON file with the app's recent events. You can send it to the developer to diagnose freezes.")
            }

            if !center.hangs.isEmpty {
                Section(loc.language == .greek ? "Παγώματα" : "Hangs") {
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

            Section(loc.language == .greek ? "Πρόσφατα συμβάντα" : "Recent events") {
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
        .navigationTitle(loc.language == .greek ? "Διαγνωστικά" : "Diagnostics")
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
