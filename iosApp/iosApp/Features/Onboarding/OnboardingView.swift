import SwiftUI

struct OnboardingView: View {
    let onComplete: () -> Void

    @ObservedObject private var loc = LocalizationManager.shared
    @StateObject private var location = LocationService()
    @State private var currentPage = 0

    private let metroBlue = Color(.sRGB, red: 0/255, green: 131/255, blue: 201/255, opacity: 1)
    private let tramRed = Color(.sRGB, red: 226/255, green: 35/255, blue: 26/255, opacity: 1)
    private let suburbanGold = Color(.sRGB, red: 248/255, green: 195/255, blue: 30/255, opacity: 1)

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
                        OnboardingPage(page: page)
                            .tag(index)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .animation(.easeInOut(duration: 0.3), value: currentPage)

                indicator

                actionRow
                    .padding(.horizontal, 24)
                    .padding(.bottom, 36)
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

            if !isLast {
                Button {
                    onComplete()
                } label: {
                    Text(loc[.onboardSkip])
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
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

private struct Page {
    let icon: String
    let tint: Color
    let title: String
    let subtitle: String
    var primaryActionKey: LocalizedKey? = nil
    var primaryAction: (() -> Void)? = nil
}

private struct OnboardingPage: View {
    let page: Page

    var body: some View {
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
    }
}
