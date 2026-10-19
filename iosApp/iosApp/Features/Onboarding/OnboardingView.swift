import SwiftUI
import CoreLocation

/// The UserDefaults set of favourite station ids, shared with the rest of the
/// app (AriadneModel.toggleFavorite writes the same key).
private let onboardingFavoritesKey = "syrmos.favorite.stations"

struct OnboardingView: View {
    let onComplete: () -> Void

    @ObservedObject private var loc = LocalizationManager.shared
    @StateObject private var location = LocationService()
    @State private var currentPage = 0

    private let metroBlue = Color(.sRGB, red: 0/255, green: 131/255, blue: 201/255, opacity: 1)
    private let tramRed = Color(.sRGB, red: 226/255, green: 35/255, blue: 26/255, opacity: 1)
    private let suburbanGold = Color(.sRGB, red: 248/255, green: 195/255, blue: 30/255, opacity: 1)

    // Location comes before the favourites step so "Near you" can suggest the
    // closest stations from the fresh GPS fix; without permission it falls back
    // to the major interchange hubs.
    private var pages: [Page] {
        [
            Page(
                icon: "tram.fill",
                tint: metroBlue,
                title: loc[.onboardWelcomeTitle],
                subtitle: loc[.onboardWelcomeBody]
            ),
            Page(
                icon: "clock.badge.checkmark.fill",
                tint: tramRed,
                title: loc[.onboardLiveTitle],
                subtitle: loc[.onboardLiveBody]
            ),
            Page(
                icon: "location.fill",
                tint: suburbanGold,
                title: loc[.onboardLocationTitle],
                subtitle: loc[.onboardLocationBody],
                primaryActionKey: .onboardLocationCta,
                primaryAction: { location.requestIfNeeded() }
            ),
            Page(
                icon: "star.fill",
                tint: suburbanGold,
                title: loc[.onboardFavoritesTitle],
                subtitle: loc[.onboardFavoritesBody],
                kind: .favorites
            ),
            Page(
                icon: "bell.badge.fill",
                tint: tramRed,
                title: loc[.onboardNotifTitle],
                subtitle: loc[.onboardNotifBody],
                primaryActionKey: .onboardNotifCta,
                primaryAction: { Task { await NotificationService.shared.requestAuthorization() } }
            ),
            Page(
                icon: "checkmark.seal.fill",
                tint: metroBlue,
                title: loc[.onboardPrivacyTitle],
                subtitle: loc[.onboardPrivacyBody]
            ),
        ]
    }

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            OnboardingMeshBackground()

            VStack(spacing: 0) {
                TabView(selection: $currentPage) {
                    ForEach(Array(pages.enumerated()), id: \.offset) { index, page in
                        OnboardingPage(page: page, loc: loc, location: location)
                            .tag(index)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .animation(.easeInOut(duration: 0.3), value: currentPage)

                indicator

                actionRow
                    .padding(.horizontal, 24)
                    .frame(maxWidth: 560)

                // Privacy is a standing promise, not a single screen: keep it in
                // view throughout so every "Allow" is read next to it.
                Text(loc[.onboardPrivacyFooter])
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
                    .padding(.top, 10)
                    .padding(.bottom, 20)
            }
        }
    }

    private var indicator: some View {
        HStack(spacing: 8) {
            ForEach(pages.indices, id: \.self) { index in
                Capsule()
                    .fill(index == currentPage ? Color.primary : Color.secondary.opacity(0.25))
                    .frame(width: index == currentPage ? 22 : 6, height: 6)
                    .animation(.spring(response: 0.3), value: currentPage)
            }
        }
        .padding(.bottom, 24)
    }

    private var actionRow: some View {
        let page = pages[currentPage]
        let isLast = currentPage == pages.count - 1
        return VStack(spacing: 12) {
            Button {
                page.primaryAction?()
                advance(isLast: isLast)
            } label: {
                Text(isLast ? loc[.onboardGetStarted] : (page.primaryActionKey.map { loc[$0] } ?? loc[.onboardContinue]))
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .fill(.ultraThinMaterial)
                            .overlay(
                                RoundedRectangle(cornerRadius: 16, style: .continuous)
                                    .strokeBorder(Color.white.opacity(0.18), lineWidth: 1)
                            )
                    )
                    .foregroundStyle(.primary)
            }

            // Skip appears once past the welcome screen, so people can leave as
            // soon as they have seen what Syrmos is, but not before.
            if !isLast && currentPage >= 1 {
                Button {
                    onComplete()
                } label: {
                    Text(loc[.onboardSkip])
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .frame(minHeight: 44)
                }
            }
        }
    }

    private func advance(isLast: Bool) {
        if isLast {
            onComplete()
        } else {
            withAnimation { currentPage += 1 }
        }
    }
}

private enum PageKind {
    case info
    case favorites
}

private struct Page {
    let icon: String
    let tint: Color
    let title: String
    let subtitle: String
    var primaryActionKey: LocalizedKey? = nil
    var primaryAction: (() -> Void)? = nil
    var kind: PageKind = .info
}

private struct OnboardingPage: View {
    let page: Page
    let loc: LocalizationManager
    @ObservedObject var location: LocationService

    var body: some View {
        switch page.kind {
        case .info:
            infoBody
        case .favorites:
            OnboardingFavoritesPage(page: page, loc: loc, location: location)
        }
    }

    private var infoBody: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing: 28) {
                    Spacer()

                    ZStack {
                        Circle()
                            .fill(.ultraThinMaterial)
                            .frame(width: 132, height: 132)
                            .overlay(
                                Circle().strokeBorder(
                                    LinearGradient(
                                        colors: [.white.opacity(0.35), .white.opacity(0.05)],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    ),
                                    lineWidth: 1
                                )
                            )

                        Image(systemName: page.icon)
                            .font(.system(size: 50, weight: .medium))
                            .foregroundStyle(page.tint)
                    }

                    VStack(spacing: 12) {
                        Text(page.title)
                            .font(.title.weight(.bold))
                            .multilineTextAlignment(.center)

                        Text(page.subtitle)
                            .font(.body)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .lineSpacing(4)
                    }
                    .padding(.horizontal, 32)

                    Spacer()
                    Spacer()
                }
                .padding(.vertical, 24)
                .frame(maxWidth: 560)
                .frame(maxWidth: .infinity, minHeight: geometry.size.height)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
    }
}

/// Interactive favourites step: star the stations nearest to the fresh GPS fix
/// (or the major hubs when location is off), or search for any station. Writes
/// straight to the same UserDefaults set the rest of the app reads, so Home and
/// the boards are personalised from the first launch.
private struct OnboardingFavoritesPage: View {
    let page: Page
    let loc: LocalizationManager
    @ObservedObject var location: LocationService

    @State private var favorites: Set<String> = Set(UserDefaults.standard.stringArray(forKey: onboardingFavoritesKey) ?? [])
    @State private var query: String = ""

    private let allStations: [TransitStation] = {
        var byId: [String: TransitStation] = [:]
        for line in SyrmosData.operationalLines {
            for station in SyrmosData.stations(for: line.id) { byId[station.id] = station }
        }
        return Array(byId.values)
    }()

    private func name(_ station: TransitStation) -> String {
        loc.language == .greek ? station.nameEl : station.name
    }

    private var trimmedQuery: String {
        query.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Search results when the user is typing, otherwise the nearest stations to
    /// the GPS fix, otherwise the major interchange hubs as a sensible default.
    private var suggestions: [TransitStation] {
        if !trimmedQuery.isEmpty {
            let q = trimmedQuery.lowercased()
            return allStations
                .filter { $0.name.lowercased().contains(q) || $0.nameEl.lowercased().contains(q) }
                .prefix(20)
                .map { $0 }
        }
        if let fix = location.currentLocation {
            return allStations
                .sorted { a, b in
                    CLLocation(latitude: a.coordinate.latitude, longitude: a.coordinate.longitude).distance(from: fix)
                        < CLLocation(latitude: b.coordinate.latitude, longitude: b.coordinate.longitude).distance(from: fix)
                }
                .prefix(12)
                .map { $0 }
        }
        return allStations.filter { $0.isInterchange }.prefix(12).map { $0 }
    }

    private var sectionTitle: String {
        trimmedQuery.isEmpty ? loc[.onboardFavoritesNearby] : ""
    }

    var body: some View {
        VStack(spacing: 20) {
            VStack(spacing: 10) {
                Text(page.title)
                    .font(.title2.weight(.bold))
                    .multilineTextAlignment(.center)
                Text(page.subtitle)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, 28)
            .padding(.top, 16)

            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                TextField(loc[.onboardFavoritesSearch], text: $query)
                    .textInputAutocapitalization(.words)
                    .autocorrectionDisabled()
                if !trimmedQuery.isEmpty {
                    Button { query = "" } label: {
                        Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary)
                    }
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 11)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous).fill(.ultraThinMaterial)
            )
            .padding(.horizontal, 24)

            ScrollView {
                if !sectionTitle.isEmpty {
                    Text(sectionTitle.uppercased())
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 28)
                        .padding(.bottom, 2)
                }
                LazyVStack(spacing: 8) {
                    ForEach(suggestions) { station in
                        stationRow(station)
                    }
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 8)
            }
            .scrollBounceBehavior(.basedOnSize)
            .scrollDismissesKeyboard(.interactively)
        }
        .frame(maxWidth: 560)
        .frame(maxWidth: .infinity)
    }

    private func stationRow(_ station: TransitStation) -> some View {
        let isFav = favorites.contains(station.id)
        return Button {
            toggle(station.id)
        } label: {
            HStack(spacing: 12) {
                Image(systemName: isFav ? "star.fill" : "star")
                    .foregroundStyle(isFav ? Color.yellow : Color.secondary)
                    .font(.system(size: 18, weight: .semibold))
                VStack(alignment: .leading, spacing: 2) {
                    Text(name(station))
                        .font(.body)
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                    if !station.lineIds.isEmpty {
                        Text(station.lineIds.prefix(4).joined(separator: " · "))
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                }
                Spacer()
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(isFav ? Color.yellow.opacity(0.12) : Color.secondary.opacity(0.06))
            )
        }
        .buttonStyle(.plain)
    }

    private func toggle(_ id: String) {
        if favorites.contains(id) { favorites.remove(id) } else { favorites.insert(id) }
        UserDefaults.standard.set(Array(favorites), forKey: onboardingFavoritesKey)
    }
}
