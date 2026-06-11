import SwiftUI

struct SyrmosSettingsView: View {
    @ObservedObject private var loc = LocalizationManager.shared
    @ObservedObject private var schedules = SyrmosSchedulesStore.shared

    var body: some View {
        NavigationStack {
            List {
                Section(loc[.preferences]) {
                    Picker(loc[.language], selection: $loc.language) {
                        ForEach(AppLanguage.allCases, id: \.self) { lang in
                            Text(lang.displayName).tag(lang)
                        }
                    }
                    LabeledContent(loc[.theme], value: loc[.systemDefault])
                }

                Section(loc[.data]) {
                    LabeledContent(loc[.scheduleVersion], value: scheduleVersionLabel)
                    LabeledContent(loc[.stations], value: "90+")
                    LabeledContent(loc[.lines], value: "9")
                    LabeledContent(lastUpdatedLabel, value: lastSyncLabel)
                    Toggle(offlineOnlyLabel, isOn: Binding(
                        get: { schedules.offlineOnly },
                        set: { schedules.offlineOnly = $0 }
                    ))
                    Button {
                        Task { await schedules.refresh() }
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
