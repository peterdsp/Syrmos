import SwiftUI
import UIKit
import UserNotifications

// iOS 26 / iOS 18 SwiftUI WindowGroup has a long-standing bug where the
// underlying UIWindow's CAMetalLayer ends up in an unrenderable state after
// a lock/unlock, screenshot, control-center swipe, or app-switcher cycle.
// The app keeps running, SwiftUI keeps emitting updates, but the user sees
// a solid black (dark mode) or white (light mode) screen until the app is
// force-quit.
//
// We tried, in escalating order:
//   1. mapRebuildKey on the Map tab only — bug also happens on Home etc
//   2. rootRebuildKey .id() on the TabView — SwiftUI rebuilt the tree
//      but reused the dead backing layer
//   3. SceneDelegate window.isHidden cycle + setNeedsDisplay — partial,
//      still reproduced on iPhone 17 Pro Max with iOS 26
//
// This file drops SwiftUI's WindowGroup lifecycle entirely. We own the
// UIWindow inside SceneDelegate and host the SwiftUI ContentView in a
// UIHostingController. On every active-state edge we replace the
// rootViewController with a brand new UIHostingController — that forces
// UIKit to allocate a fresh UIView hierarchy on top of a fresh layer,
// which the OS happily re-binds to a healthy CAMetalLayer.

@main
final class AppDelegate: UIResponder, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        BackgroundRefresh.register()
        BackgroundRefresh.schedule()

        UNUserNotificationCenter.current().delegate = self

        Task { @MainActor in
            await NotificationService.shared.requestAuthorization()
            NotificationService.shared.scheduleMorningDigest()
        }

        return true
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        let category = response.notification.request.content.categoryIdentifier

        Task { @MainActor in
            switch category {
            case "SERVICE_ALERT":
                let alertId = userInfo["alertId"] as? String ?? ""
                DeepLinkRouter.shared.pending = .serviceAlert(id: alertId)
            case "WEATHER_ALERT":
                DeepLinkRouter.shared.pending = .weatherAlert
            case "MORNING_DIGEST":
                DeepLinkRouter.shared.pending = .morningDigest
            case "NEARBY_ALERT":
                DeepLinkRouter.shared.pending = .nearbyAlert
            default:
                DeepLinkRouter.shared.pending = .morningDigest
            }
        }
        completionHandler()
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }

    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        let config = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        config.delegateClass = SceneDelegate.self
        return config
    }
}

final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?
    /// `true` when the scene has been backgrounded since the last time
    /// we replaced the hosting controller. We only rebuild on a real
    /// background -> foreground edge, NOT on every active transition,
    /// because system-presented modals (location permission alert,
    /// camera, share sheet, app-switcher peek, etc.) briefly inactivate
    /// the scene and the rebuild was throwing away SwiftUI @State —
    /// most painfully it sent the onboarding flow back to page 1 the
    /// moment the user granted location access on page 3.
    private var didBackgroundSinceLastRebuild = false

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let windowScene = scene as? UIWindowScene else { return }
        configureAppearance()
        let window = UIWindow(windowScene: windowScene)
        window.rootViewController = makeHostingController()
        window.backgroundColor = .systemBackground
        self.window = window
        window.makeKeyAndVisible()
    }

    func sceneDidEnterBackground(_ scene: UIScene) {
        didBackgroundSinceLastRebuild = true
        // Queue the next opportunistic weather/alerts refresh for the widget.
        BackgroundRefresh.schedule()
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        guard let window = window else { return }
        // Only replace the hosting controller after a real background
        // cycle. Modal interruptions (location prompt, share sheet,
        // app-switcher swipe-and-cancel) fire sceneDidBecomeActive
        // without sceneDidEnterBackground; rebuilding there resets
        // SwiftUI state in views the user is actively interacting with.
        // For those, the cheap window.isHidden cycle is enough to
        // refresh the CAMetalLayer without losing state.
        if didBackgroundSinceLastRebuild {
            didBackgroundSinceLastRebuild = false
            window.rootViewController = makeHostingController()
            window.makeKeyAndVisible()
        } else {
            window.isHidden = true
            window.isHidden = false
        }
    }

    private func makeHostingController() -> UIHostingController<AnyView> {
        let root = AnyView(RootView())
        let host = UIHostingController(rootView: root)
        host.view.backgroundColor = .systemBackground
        return host
    }

    private func configureAppearance() {
        UITabBar.appearance().isTranslucent = true
        UINavigationBar.appearance().isTranslucent = true
        let appearance = UITabBarAppearance()
        appearance.configureWithDefaultBackground()
        appearance.backgroundColor = UIColor.systemBackground
        UITabBar.appearance().standardAppearance = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
    }
}

/// The SwiftUI root. Replaces what `WindowGroup { ... }` used to host.
/// Reads onboarding state on every instantiation so a re-mounted scene
/// picks up the latest flag without a stale @State capture.
struct RootView: View {
    @State private var hasCompletedOnboarding = UserDefaults.standard.bool(forKey: kOnboardingCompletedKey)

    var body: some View {
        if hasCompletedOnboarding {
            ContentView()
                .modifier(WhatsNewPresenter())
        } else {
            OnboardingView {
                UserDefaults.standard.set(true, forKey: kOnboardingCompletedKey)
                withAnimation(.easeInOut(duration: 0.4)) {
                    hasCompletedOnboarding = true
                }
            }
        }
    }
}

private let kOnboardingCompletedKey = "syrmos.onboarding.completed.v1"

struct ContentView: View {
    @State private var selectedTab: SyrmosTab = .home
    @State private var showAriadne = false
    @State private var deepLinkedAlert: STASYAnnouncement?
    @ObservedObject private var loc = LocalizationManager.shared
    @ObservedObject private var themeManager = ThemeManager.shared
    @ObservedObject private var deepLinkRouter = DeepLinkRouter.shared

    var body: some View {
        ZStack {
            Color.syrmosBackground.ignoresSafeArea()
            TabView(selection: $selectedTab) {
                // Each non-Settings tab reserves 60pt at the bottom via a
                // safeAreaInset so the last row/item in its scrollable
                // never disappears under the floating Ask Ariadne pill.
                // Settings hides the pill entirely so no clearance needed.
                HomeView()
                    .safeAreaInset(edge: .bottom, spacing: 0) { Color.clear.frame(height: 60) }
                    .tabItem {
                        Label(loc[.home], systemImage: "house")
                    }
                    .tag(SyrmosTab.home)

                LinesView()
                    .safeAreaInset(edge: .bottom, spacing: 0) { Color.clear.frame(height: 60) }
                    .tabItem {
                        Label(loc[.explore], systemImage: "compass")
                    }
                    .tag(SyrmosTab.explore)

                TransitMapView()
                    .tabItem {
                        Label(loc[.map], systemImage: "map")
                    }
                    .tag(SyrmosTab.map)

                TimetablesView()
                    .safeAreaInset(edge: .bottom, spacing: 0) { Color.clear.frame(height: 60) }
                    .tabItem {
                        Label(loc[.departures], systemImage: "clock")
                    }
                    .tag(SyrmosTab.departures)

                SyrmosSettingsView()
                    .tabItem {
                        Label(loc[.moreTab], systemImage: "ellipsis.circle")
                    }
                    .tag(SyrmosTab.more)
            }
            .tint(.syrmosPrimary)

            // Ariadne launcher lives at the app level so it's available on
            // Home / Explore / Departures. Hidden on More (the pill would
            // sit on top of the settings scroll controls) AND on Map
            // (the Locate + Vehicles buttons already own that bottom-
            // right corner). The pill fades and slides on tab change so
            // it never abruptly appears mid-transition.
            if selectedTab != .more && selectedTab != .map {
                VStack {
                    Spacer()
                    HStack {
                        Spacer()
                        AriadneLauncherPill(
                            label: askAriadneLabel,
                            onTap: { showAriadne = true }
                        )
                        .padding(.trailing, 16)
                        .padding(.bottom, 90)
                    }
                }
                .transition(.opacity.combined(with: .move(edge: .bottom)))
                .allowsHitTesting(true)
            }
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.85), value: selectedTab)
        .sheet(isPresented: $showAriadne) {
            AriadneView()
        }
        .preferredColorScheme(themeManager.theme.colorScheme)
        .task {
            _ = DiagnosticsCenter.shared
            DiagnosticsCenter.shared.leaveBreadcrumb("app", "ContentView appeared")
            // Mirror the selected language into the widgets' App Group so
            // they render in EN / EL / SQ to match the app.
            WidgetBridge.publishLanguage(loc.language.rawValue)
            // Live train positions are runtime data by nature, not bundled —
            // the whole point is "where are the trains RIGHT NOW". Skipping
            // these refreshes on launch left the map with zero moving dots
            // until the user tapped Settings → Check now. Schedules / fares /
            // station-offsets / visual overrides stay bundled-only; only the
            // two LIVE feeds fire here.
            await LivePositionsService.shared.refresh()
            await LiveTrainService.shared.refresh()
            DiagnosticsCenter.shared.leaveBreadcrumb("app", "Initial live refresh done")
        }
        .onChange(of: selectedTab) { _, newTab in
            DiagnosticsCenter.shared.leaveBreadcrumb("tab", "Switched to \(newTab)")
        }
        .onChange(of: loc.language) { _, newLang in
            WidgetBridge.publishLanguage(newLang.rawValue)
        }
        .onChange(of: deepLinkRouter.pending) { _, destination in
            guard let destination else { return }
            deepLinkRouter.pending = nil
            switch destination {
            case .serviceAlert(let id):
                selectedTab = .home
                if let alert = STASYService.cachedAlert(byId: id) {
                    deepLinkedAlert = alert
                }
            case .weatherAlert, .morningDigest, .nearbyAlert:
                selectedTab = .home
            }
        }
        .sheet(item: $deepLinkedAlert) { alert in
            AlertDetailSheet(alert: alert, language: loc.language)
        }
    }

    private var askAriadneLabel: String {
        switch loc.language {
        case .greek: return "Ρώτα την Αριάδνη"
        case .albanian: return "Pyet Ariadne"
        default: return "Ask Ariadne"
        }
    }
}

enum SyrmosTab {
    case home, explore, map, departures, more
}

/// The launcher pill users tap to open Ariadne. Springs on press so the
/// gesture feels physical, matches the sheet's slide-up entrance. Owl
/// glyph is the Athenian mythology tie (Athena's owl, symbol of wisdom).
private struct AriadneLauncherPill: View {
    let label: String
    let onTap: () -> Void
    @State private var pressed = false

    var body: some View {
        Button {
            withAnimation(.spring(response: 0.25, dampingFraction: 0.6)) {
                pressed = true
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) {
                withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                    pressed = false
                }
                onTap()
            }
        } label: {
            Image("AriadneMark")
                .resizable()
                .scaledToFit()
                .frame(width: 32, height: 32)
                .padding(12)
                .background(Color.syrmosSurface)
                .clipShape(Circle())
                .overlay(Circle().strokeBorder(Color.syrmosPrimary.opacity(0.2), lineWidth: 1))
                .shadow(color: .black.opacity(0.2), radius: 8, y: 4)
                .scaleEffect(pressed ? 0.92 : 1.0)
                .accessibilityLabel(label)
        }
        .buttonStyle(.plain)
    }
}
