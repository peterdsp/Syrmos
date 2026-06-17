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

    func sceneDidBecomeActive(_ scene: UIScene) {
        guard let window = window else { return }
        // Replace the hosting controller wholesale. This is the only
        // sequence that reliably recovers from the blank-window bug:
        // a fresh UIHostingController instantiates a fresh UIView,
        // gets a fresh backing layer, and SwiftUI lays out into it.
        window.rootViewController = makeHostingController()
        window.makeKeyAndVisible()
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
    @ObservedObject private var loc = LocalizationManager.shared
    @ObservedObject private var themeManager = ThemeManager.shared

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()
            TabView(selection: $selectedTab) {
                HomeView()
                    .tabItem {
                        Label(loc[.home], systemImage: "house")
                    }
                    .tag(SyrmosTab.home)

                LinesView()
                    .tabItem {
                        Label(loc[.lines], systemImage: "tram")
                    }
                    .tag(SyrmosTab.lines)

                TransitMapView()
                    .tabItem {
                        Label(loc[.map], systemImage: "map")
                    }
                    .tag(SyrmosTab.map)

                TimetablesView()
                    .tabItem {
                        Label(
                            loc.language == .greek ? "Αεροδρόμιο" :
                            loc.language == .albanian ? "Aeroporti" : "Airport",
                            systemImage: "airplane"
                        )
                    }
                    .tag(SyrmosTab.timetables)

                SyrmosSettingsView()
                    .tabItem {
                        Label(loc[.settings], systemImage: "gearshape")
                    }
                    .tag(SyrmosTab.settings)
            }
            .tint(.syrmosPrimary)
        }
        .preferredColorScheme(themeManager.theme.colorScheme)
        .task {
            _ = DiagnosticsCenter.shared
            DiagnosticsCenter.shared.leaveBreadcrumb("app", "ContentView appeared")

            let svc = SyrmosLinesService()
            await svc.refresh()
            await SyrmosSchedulesStore.shared.refresh()
            await SyrmosVisualOverridesStore.shared.refresh()
            await SyrmosTrainTimestampsStore.shared.refresh()
            await SyrmosStationOffsetsStore.shared.refresh()
            await SyrmosFaresStore.shared.refresh()
        }
        .onChange(of: selectedTab) { _, newTab in
            DiagnosticsCenter.shared.leaveBreadcrumb("tab", "Switched to \(newTab)")
        }
    }
}

enum SyrmosTab {
    case home, lines, map, timetables, settings
}
