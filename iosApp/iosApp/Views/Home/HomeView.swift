import SwiftUI
import WebKit

struct HomeView: View {
    @StateObject private var stasyService = STASYService()
    @StateObject private var liveTrainService = LiveTrainService()
    @ObservedObject private var loc = LocalizationManager.shared
    @State private var webViewURL: URL?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    welcomeSection
                    networkOverview
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
                await stasyService.fetchAnnouncements()
            }
            .sheet(item: $webViewURL) { url in
                InAppWebView(url: url)
                    .presentationDetents([.large, .medium])
                    .presentationDragIndicator(.visible)
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
    private var liveTrainsSection: some View {
        if liveTrainService.trains.isEmpty {
            EmptyView()
        } else {
            VStack(alignment: .leading, spacing: 10) {
                Text("Live trains")
                    .font(.title3)
                    .fontWeight(.semibold)

                ForEach(liveTrainService.trains.prefix(3)) { train in
                    HStack {
                        Circle()
                            .fill(Color.suburbanPurple)
                            .frame(width: 8, height: 8)
                        VStack(alignment: .leading, spacing: 1) {
                            Text(train.trainNumber)
                                .font(.subheadline)
                                .fontWeight(.semibold)
                            Text("\(train.origin) to \(train.destination)")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text(train.nextStation.isEmpty ? "Live" : train.nextStation)
                            .font(.caption)
                            .foregroundStyle(.secondary)
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

        if stasyService.isLoading && stasyService.announcements.isEmpty {
            ProgressView()
                .frame(maxWidth: .infinity, minHeight: 60)
        } else if !alerts.isEmpty {
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

        if stasyService.error != nil {
            HStack(spacing: 6) {
                Image(systemName: "wifi.slash")
                    .font(.caption)
                Text(loc[.couldNotReach])
                    .font(.caption)
            }
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
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
            Text(announcement.title)
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
