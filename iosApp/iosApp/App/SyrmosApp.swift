import SwiftUI
import UIKit

class AppDelegate: NSObject, UIApplicationDelegate {
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

class SceneDelegate: NSObject, UIWindowSceneDelegate {
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        guard let windowScene = scene as? UIWindowScene else { return }
        configureWindows(windowScene)
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        guard let windowScene = scene as? UIWindowScene else { return }
        configureWindows(windowScene)
        // iOS 26 / iOS 18 window-blank workaround. After a screenshot,
        // lock/unlock, control-center swipe, or app-switcher cycle the
        // UIWindow's CAMetalLayer can come back unbacked: SwiftUI keeps
        // running, the view tree is alive, but the screen shows a solid
        // black or white canvas until the user cold-restarts. Forcing
        // isHidden = true -> false on the active window re-binds the
        // backing layer; layoutIfNeeded + setNeedsDisplay nudge SwiftUI's
        // host view to redraw into the fresh layer.
        for window in windowScene.windows where window.isKeyWindow || window.windowLevel == .normal {
            let wasHidden = window.isHidden
            window.isHidden = true
            window.isHidden = wasHidden
            window.rootViewController?.view.setNeedsLayout()
            window.rootViewController?.view.layoutIfNeeded()
            window.layer.setNeedsDisplay()
            window.rootViewController?.view.layer.setNeedsDisplay()
        }
    }

    private func configureWindows(_ windowScene: UIWindowScene) {
        for window in windowScene.windows {
            window.backgroundColor = .systemGroupedBackground
        }
        // Stop UITabBarController and UINavigationController from flashing black during transitions
        UITabBar.appearance().isTranslucent = true
        UINavigationBar.appearance().isTranslucent = true
        let appearance = UITabBarAppearance()
        appearance.configureWithDefaultBackground()
        appearance.backgroundColor = UIColor.systemBackground
        UITabBar.appearance().standardAppearance = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
    }
}

@main
struct SyrmosApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    @State private var hasCompletedOnboarding = UserDefaults.standard.bool(forKey: kOnboardingCompletedKey)

    var body: some Scene {
        WindowGroup {
            // Note: a SwiftUI LaunchSplashView used to overlay this ZStack
            // for 1.4s after the system splash handed off. It caused a
            // window-blank regression on background-to-foreground because
            // the .task re-fired and the overlay could re-mount on top of
            // a not-yet-redrawn ContentView. The system launch screen
            // (Info.plist UILaunchScreen) already covers the cold-start
            // visual; SwiftUI doesn't need to repeat that.
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
}

private let kOnboardingCompletedKey = "syrmos.onboarding.completed.v1"

struct ContentView: View {
    @State private var selectedTab: SyrmosTab = .home
    /// Root-level rebuild trigger. iOS 26 has a SwiftUI bug where the
    /// entire window's CAMetalLayer can come back blank after a
    /// screenshot, lock/unlock, control-center swipe, or app-switcher
    /// cycle. Per user reports the black screen affects every tab, not
    /// just the Map tab, so a Map-only rebuild key isn't enough. Bumping
    /// this id on every active-state edge forces SwiftUI to discard and
    /// recreate the entire TabView subtree, which re-establishes a
    /// healthy backing layer.
    @State private var rootRebuildKey: Int = 0
    @ObservedObject private var loc = LocalizationManager.shared
    @ObservedObject private var themeManager = ThemeManager.shared
    @Environment(\.scenePhase) private var scenePhase

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
                        Label(loc.language == .greek ? "Δρομολόγια" : loc.language == .albanian ? "Oraret" : "Timetables",
                              systemImage: "clock")
                    }
                    .tag(SyrmosTab.timetables)

                SyrmosSettingsView()
                    .tabItem {
                        Label(loc[.settings], systemImage: "gearshape")
                    }
                    .tag(SyrmosTab.settings)
            }
            .tint(.syrmosPrimary)
            .id(rootRebuildKey)
        }
        .preferredColorScheme(themeManager.theme.colorScheme)
        .onReceive(NotificationCenter.default.publisher(
            for: UIApplication.didBecomeActiveNotification
        )) { _ in
            rootRebuildKey &+= 1
        }
        .onReceive(NotificationCenter.default.publisher(
            for: UIApplication.willEnterForegroundNotification
        )) { _ in
            rootRebuildKey &+= 1
        }
        .onReceive(NotificationCenter.default.publisher(
            for: UIApplication.userDidTakeScreenshotNotification
        )) { _ in
            rootRebuildKey &+= 1
            // The system overlay animation finishes ~400ms after the
            // notification; bump again so the rebuild lands AFTER the
            // overlay is fully gone and the window can repaint cleanly.
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                rootRebuildKey &+= 1
            }
        }
        .onChange(of: scenePhase) { oldPhase, newPhase in
            if oldPhase != .active && newPhase == .active {
                rootRebuildKey &+= 1
            }
        }
        // Fire-and-forget refresh of the offline-first lines cache. Doesn't
        // block UI; failure is silent. We do not propagate the service via
        // EnvironmentObject because a missing object on a presented sheet/
        // navigation destination silently freezes SwiftUI to a black screen
        // on iOS 18.
        .task {
            // Boot the diagnostics center first so its watchdog catches
            // even the earliest hang. Idempotent — calling .shared touches
            // the lazy singleton.
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
