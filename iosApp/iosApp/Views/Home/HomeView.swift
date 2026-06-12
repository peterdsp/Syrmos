import SwiftUI
import WebKit

struct HomeView: View {
    @StateObject private var stasyService = STASYService()
    // Shared instance so HomeView and MapView don't each poll the live trains
    // endpoint in parallel.
    @ObservedObject private var liveTrainService = LiveTrainService.shared
    @StateObject private var locationService = LocationService()
    @ObservedObject private var loc = LocalizationManager.shared
    @State private var webViewURL: URL?
    @State private var isNearMeExpanded = true
    @State private var showLocationDeniedAlert = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    welcomeSection
                    networkOverview
                    nearMeSection
                    liveTrainsSection
                    alertsSection
                    linesSection
                }
                .padding()
            }
            .background(Color.syrmosBackground)
            .navigationTitle("Syrmos")
            .refreshable {
                await stasyService.fetchAnnouncements()
            }
            .task {
                locationService.requestIfNeeded()
                await stasyService.fetchAnnouncements()
            }
            .sheet(item: $webViewURL) { url in
                InAppWebView(url: url)
                    .presentationDetents([.large, .medium])
                    .presentationDragIndicator(.visible)
            }
            .alert(
                loc.language == .greek ? "Η τοποθεσία είναι απενεργοποιημένη" : "Location is disabled",
                isPresented: $showLocationDeniedAlert
            ) {
                Button(loc.language == .greek ? "Άνοιγμα Ρυθμίσεων" : "Open Settings") {
                    locationService.openSystemSettings()
                }
                Button(loc.language == .greek ? "Άκυρο" : "Cancel", role: .cancel) {}
            } message: {
                Text(loc.language == .greek
                    ? "Δεν έχετε δώσει άδεια τοποθεσίας στο Syrmos. Θέλετε να ανοίξετε τις Ρυθμίσεις για να την ενεργοποιήσετε;"
                    : "You haven't granted Syrmos location access. Would you like to open Settings to enable it?")
            }
        }
    }

    private var welcomeSection: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(loc[.appSubtitle])
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var networkOverview: some View {
        HStack(spacing: 12) {
            StatCard(value: "3", label: loc[.metro], color: .metroBlue)
            StatCard(value: "2", label: loc[.tram], color: .tramOrange)
            StatCard(value: "4", label: loc[.suburban], color: .suburbanPurple)
        }
    }

    @ViewBuilder
    private var nearMeSection: some View {
        if !locationService.hasPermission {
            Button {
                if locationService.isDenied {
                    showLocationDeniedAlert = true
                } else {
                    locationService.requestIfNeeded()
                }
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: "location.circle.fill")
                        .font(.title2)
                        .foregroundStyle(.blue)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(loc.language == .greek ? "Κοντά μου" : "Near me")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .foregroundStyle(.primary)
                        Text(loc.language == .greek ? "Ενεργοποιήστε την τοποθεσία για να δείτε κοντινούς σταθμούς" : "Enable location to see nearby stations")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(Color.syrmosSurface)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
        } else if locationService.hasPermission && !locationService.nearbyStations.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Button {
                    withAnimation(.easeInOut(duration: 0.25)) {
                        isNearMeExpanded.toggle()
                    }
                } label: {
                    HStack {
                        Image(systemName: "location.fill")
                            .foregroundStyle(.blue)
                        Text(loc.language == .greek ? "Κοντά μου" : "Near me")
                            .font(.title3)
                            .fontWeight(.semibold)
                            .foregroundStyle(.primary)
                        Spacer()
                        Image(systemName: isNearMeExpanded ? "chevron.up" : "chevron.down")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .buttonStyle(.plain)

                if isNearMeExpanded {
                ForEach(locationService.nearbyStations) { nearby in
                    NavigationLink {
                        // ALWAYS return a non-empty destination view.
                        // Previously this fell through to an empty View for
                        // interchange stations like Piraeus (4 lines, stationIds
                        // and lineIds in different orders), which renders as a
                        // black screen with only the back chevron visible.
                        NearbyStationDestination(node: nearby.station)
                    } label: {
                        HStack(spacing: 12) {
                            HStack(spacing: 4) {
                                ForEach(nearby.station.lineIds.prefix(3), id: \.self) { lineId in
                                    Circle()
                                        .fill(SyrmosData.lineColor(for: lineId))
                                        .frame(width: 8, height: 8)
                                }
                            }

                            VStack(alignment: .leading, spacing: 2) {
                                Text(loc.language == .greek ? nearby.station.nameEl : nearby.station.displayName)
                                    .font(.subheadline)
                                    .fontWeight(.semibold)
                                    .foregroundStyle(.primary)
                                Text(nearby.station.lineIds.compactMap { SyrmosData.line(for: $0)?.name }.joined(separator: ", "))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }

                            Spacer()

                            Text(formatDistance(nearby.distanceMeters))
                                .font(.caption)
                                .fontWeight(.medium)
                                .foregroundStyle(.secondary)

                            Image(systemName: "chevron.right")
                                .font(.caption2)
                                .foregroundStyle(.tertiary)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(Color.syrmosSurface)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
                }
            }
        }
    }

    private func formatDistance(_ meters: Double) -> String {
        if meters < 1000 {
            return "\(Int(meters)) m"
        } else {
            return String(format: "%.1f km", meters / 1000)
        }
    }

    @ViewBuilder
    private var liveTrainsSection: some View {
        let realTrains = liveTrainService.trains.filter { !$0.origin.isEmpty && !$0.destination.isEmpty }
        if realTrains.isEmpty {
            EmptyView()
        } else {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 6) {
                    Image(systemName: "tram.fill")
                        .foregroundStyle(Color.suburbanPurple)
                    Text(loc.language == .greek ? "Ζωντανά τρένα" : "Live trains")
                        .font(.title3)
                        .fontWeight(.semibold)
                }

                ForEach(realTrains.prefix(4)) { train in
                    HStack(spacing: 10) {
                        VStack(spacing: 2) {
                            Text(train.lineId)
                                .font(.caption2)
                                .fontWeight(.bold)
                                .foregroundStyle(.white)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(SyrmosData.lineColor(for: train.lineId))
                                .clipShape(Capsule())
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            Text("\(train.origin) → \(train.destination)")
                                .font(.subheadline)
                                .fontWeight(.medium)
                                .lineLimit(1)
                            HStack(spacing: 6) {
                                Text("#\(train.trainNumber)")
                                    .font(.caption2)
                                    .foregroundStyle(.tertiary)
                                if train.delayMinutes > 0 {
                                    Text(loc.language == .greek ? "+\(train.delayMinutes)′ καθυστέρηση" : "+\(train.delayMinutes)′ delay")
                                        .font(.caption2)
                                        .foregroundStyle(.orange)
                                }
                            }
                        }
                        Spacer()
                        Circle()
                            .fill(Color.green)
                            .frame(width: 8, height: 8)
                    }
                    .padding(12)
                    .background(Color.syrmosSurface)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
            }
        }
    }

    @ViewBuilder
    private var alertsSection: some View {
        let alerts = stasyService.announcements.filter { $0.category == .serviceAlert }

        if !alerts.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundStyle(.orange)
                    Text(loc[.serviceAlerts])
                        .font(.title3)
                        .fontWeight(.semibold)
                    Spacer()
                    if let updated = stasyService.lastUpdated {
                        Text(updated, style: .relative)
                            .font(.caption2)
                            .foregroundStyle(.tertiary)
                    }
                }

                ForEach(alerts.prefix(3)) { alert in
                    AlertCard(announcement: alert, onReadMore: { url in
                        webViewURL = url
                    })
                }
            }
        } else if let first = stasyService.announcements.first {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Image(systemName: "info.circle.fill")
                        .foregroundStyle(.blue)
                    Text(loc[.latestFromSTASY])
                        .font(.title3)
                        .fontWeight(.semibold)
                }
                AlertCard(announcement: first, onReadMore: { url in
                    webViewURL = url
                })
            }
        }

        serviceStatusPill
    }

    /// Compact status row that replaces the prior "Could not reach stasy.gr"
    /// error banner. Surfaces /api/announcements.status: normal operation
    /// gets a green checkmark; an alert (e.g. "Trains until 21:40") shows
    /// the operator's verbatim message in orange. Falls back to today's
    /// last-departure time computed from the synced schedule rules so the
    /// user always sees SOMETHING, even when the announcements watcher is
    /// behind.
    @ViewBuilder
    private var serviceStatusPill: some View {
        let status = stasyService.serviceStatus
        let isAlert = status?.status == "alert"
        let message: String? = {
            if let s = status {
                let localized = s.displayMessage(language: loc.language)
                if !localized.isEmpty { return localized }
            }
            return fallbackServiceHours()
        }()
        if let message {
            HStack(spacing: 8) {
                Image(systemName: isAlert
                      ? "exclamationmark.triangle.fill"
                      : "checkmark.circle.fill")
                    .font(.caption)
                    .foregroundStyle(isAlert ? .orange : .green)
                Text(message)
                    .font(.caption)
                    .foregroundStyle(.primary)
                    .lineLimit(2)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(isAlert ? Color.orange.opacity(0.12) : Color.green.opacity(0.10))
            )
        }
    }

    /// Today's last metro/tram departure derived from the synced schedule
    /// rules. Picks the latest close_time across M1/M2/M3/T6/T7 for the
    /// current Athens day_type; falls back to a generic "Trains running"
    /// string when bundles aren't loaded yet.
    private func fallbackServiceHours() -> String? {
        let store = SyrmosSchedulesStore.shared
        let bundles = store.service.bundles
        if bundles.isEmpty { return nil }
        let athens = TimeZone(identifier: "Europe/Athens")!
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = athens
        let weekday = cal.component(.weekday, from: Date())
        let dayType: String
        switch weekday {
        case 1: dayType = "sun"
        case 2, 3, 4, 5: dayType = "mon_thu"
        case 6: dayType = "fri"
        case 7: dayType = "sat"
        default: dayType = "mon_thu"
        }
        var latest = ""
        for lineId in ["M1", "M2", "M3", "T6", "T7"] {
            guard let bundle = bundles[lineId] else { continue }
            guard let rule = bundle.rules.first(where: { $0.dayType == dayType }) else { continue }
            if rule.closeTime > latest { latest = rule.closeTime }
            if rule.is247 {
                return loc.language == .greek
                    ? "Λειτουργία 24/7 σήμερα"
                    : "24/7 service today"
            }
        }
        if latest.isEmpty { return nil }
        return loc.language == .greek
            ? "Δρομολόγια έως \(latest)"
            : "Trains until \(latest)"
    }

    private var linesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(loc[.lines])
                .font(.title3)
                .fontWeight(.semibold)

            ForEach(TransitType.allCases, id: \.self) { type in
                let filtered = SyrmosData.lines.filter { $0.type == type }
                if !filtered.isEmpty {
                    VStack(spacing: 8) {
                        ForEach(filtered) { line in
                            NavigationLink {
                                LineDetailView(
                                    line: line,
                                    stations: SyrmosData.stations(for: line.id)
                                )
                            } label: {
                                LineCard(line: line)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Nearby Station Destination
//
// Bulletproof destination for the "Near me" NavigationLink. Tries each
// (lineId, stationId) pair until it finds a matching TransitStation and
// pushes StationDetailView. If nothing matches (this happens at interchange
// stops like Piraeus where the merged MapStationNode's stationIds and
// lineIds arrays don't line up), it falls back to a minimal screen rather
// than rendering an empty View — which was showing up as a black screen.

struct NearbyStationDestination: View {
    let node: MapStationNode
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        if let station = resolveTransitStation() {
            StationDetailView(station: station)
        } else {
            // Defensive fallback. Should be unreachable now that we walk
            // every (line, station) pair, but if data is ever malformed we
            // want a real view to show, not a black NavigationStack push.
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(loc.language == .greek ? node.nameEl : node.displayName)
                        .font(.title2)
                        .fontWeight(.bold)
                    Text(loc.language == .greek
                         ? "Ο σταθμός δεν είναι ακόμη διαθέσιμος"
                         : "This station isn't available yet.")
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding()
            }
            .background(Color.syrmosBackground)
            .navigationTitle(loc.language == .greek ? node.nameEl : node.displayName)
        }
    }

    private func resolveTransitStation() -> TransitStation? {
        // 1. Best path: stationIdByLineId is correctly paired.
        for lineId in node.lineIds {
            if let stationId = node.stationIdByLineId[lineId],
               let match = SyrmosData.stations(for: lineId).first(where: { $0.id == stationId }) {
                return match
            }
        }
        // 2. Fallback: try every (lineId, stationId) cross product.
        for lineId in node.lineIds {
            let stationsOnLine = SyrmosData.stations(for: lineId)
            for sid in node.stationIds {
                if let match = stationsOnLine.first(where: { $0.id == sid }) {
                    return match
                }
            }
        }
        // 3. Last resort: any station whose id matches anything we know.
        for sid in node.stationIds {
            for lineId in SyrmosData.lines.map(\.id) {
                if let match = SyrmosData.stations(for: lineId).first(where: { $0.id == sid }) {
                    return match
                }
            }
        }
        return nil
    }
}

// MARK: - Stat Card

struct StatCard: View {
    let value: String
    let label: String
    let color: Color

    var body: some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundStyle(color)
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(Color.syrmosSurface)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

// MARK: - Alert Card (expandable)

struct AlertCard: View {
    let announcement: STASYAnnouncement
    let onReadMore: (URL) -> Void
    @ObservedObject private var loc = LocalizationManager.shared
    @State private var isExpanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(announcement.displayTitle(language: loc.language))
                .font(.subheadline)
                .fontWeight(.medium)
                .lineLimit(isExpanded ? nil : 3)
                .animation(.easeInOut(duration: 0.2), value: isExpanded)

            if !announcement.date.isEmpty {
                Text(announcement.date)
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }

            HStack(spacing: 16) {
                Button {
                    withAnimation(.easeInOut(duration: 0.25)) {
                        isExpanded.toggle()
                    }
                } label: {
                    HStack(spacing: 4) {
                        Text(isExpanded ? loc[.showLess] : loc[.showMore])
                        Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                    }
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }

                if let url = announcement.url {
                    Button {
                        onReadMore(url)
                    } label: {
                        HStack(spacing: 4) {
                            Text(loc[.readMore])
                            Image(systemName: "arrow.up.right")
                        }
                        .font(.caption)
                        .foregroundStyle(Color.syrmosPrimary)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(
            announcement.category == .serviceAlert
                ? Color.orange.opacity(0.08)
                : Color.syrmosSurface
        )
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .strokeBorder(
                    announcement.category == .serviceAlert
                        ? Color.orange.opacity(0.2)
                        : Color.clear,
                    lineWidth: 1
                )
        )
    }
}

// MARK: - Line Card

struct LineCard: View {
    let line: TransitLine
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 6, style: .continuous)
                .fill(line.color)
                .frame(width: 4, height: 44)

            VStack(alignment: .leading, spacing: 2) {
                Text(line.localizedName(loc.language))
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundStyle(.primary)
                Text("\(line.terminalA) - \(line.terminalB)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Text("\(line.stationCount)")
                .font(.caption)
                .fontWeight(.medium)
                .foregroundStyle(.secondary)

            Image(systemName: "chevron.right")
                .font(.caption2)
                .foregroundStyle(.tertiary)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(Color.syrmosSurface)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

// MARK: - In-App WebView

extension URL: @retroactive Identifiable {
    public var id: String { absoluteString }
}

struct InAppWebView: View {
    let url: URL
    @Environment(\.dismiss) private var dismiss
    @State private var isLoading = true

    var body: some View {
        NavigationStack {
            ZStack {
                WebViewRepresentable(url: url, isLoading: $isLoading)
                if isLoading {
                    ProgressView()
                }
            }
            .navigationTitle("stasy.gr")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

struct WebViewRepresentable: UIViewRepresentable {
    let url: URL
    @Binding var isLoading: Bool

    func makeUIView(context: Context) -> WKWebView {
        let webView = WKWebView()
        webView.navigationDelegate = context.coordinator
        webView.load(URLRequest(url: url))
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    class Coordinator: NSObject, WKNavigationDelegate {
        let parent: WebViewRepresentable

        init(parent: WebViewRepresentable) {
            self.parent = parent
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            parent.isLoading = false
        }
    }
}
