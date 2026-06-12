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

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

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
                        Label(loc.language == .greek ? "Δρομολόγια" : "Timetables",
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
        }
        .preferredColorScheme(themeManager.theme.colorScheme)
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
        }
        .onChange(of: selectedTab) { _, newTab in
            DiagnosticsCenter.shared.leaveBreadcrumb("tab", "Switched to \(newTab)")
        }
    }
}

enum SyrmosTab {
    case home, lines, map, timetables, settings
}
