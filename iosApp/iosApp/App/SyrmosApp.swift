import SwiftUI
import UIKit

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
final class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Background refresh keeps the Weather + Alerts widget current without
        // the user opening the app. Register before launch finishes.
        BackgroundRefresh.register()
        BackgroundRefresh.schedule()

        // Request notification permissions and schedule the daily morning digest.
        Task { @MainActor in
            await NotificationService.shared.requestAuthorization()
            NotificationService.shared.scheduleMorningDigest()
        }

        return true
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
    @ObservedObject private var loc = LocalizationManager.shared
    @ObservedObject private var themeManager = ThemeManager.shared

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
            LiveTrainService.onLiveDataRefreshed = { count in
                WidgetBridge.publishLiveTrains(count: count, updatedEpoch: Date().timeIntervalSince1970)
            }
            await LivePositionsService.shared.refresh()
            await LiveTrainService.shared.refresh()
            DiagnosticsCenter.shared.leaveBreadcrumb("app", "Initial live refresh done")
        }
        .onChange(of: selectedTab) { _, newTab in
            DiagnosticsCenter.shared.leaveBreadcrumb("tab", "Switched to \(newTab)")
        }
        .onChange(of: loc.language) { _, newLang in
            // Keep the widgets' language in step when the user switches it.
            WidgetBridge.publishLanguage(newLang.rawValue)
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
