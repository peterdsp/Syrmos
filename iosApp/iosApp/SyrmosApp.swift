import SwiftUI

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
        for window in windowScene.windows {
            window.backgroundColor = .systemGroupedBackground
        }
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        guard let windowScene = scene as? UIWindowScene else { return }
        for window in windowScene.windows {
            window.backgroundColor = .systemGroupedBackground
        }
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

    var body: some View {
        TabView(selection: $selectedTab) {
            HomeView()
                .tabItem {
                    Label("Home", systemImage: "house")
                }
                .tag(SyrmosTab.home)

            LinesView()
                .tabItem {
                    Label("Lines", systemImage: "tram")
                }
                .tag(SyrmosTab.lines)

            TransitMapView()
                .tabItem {
                    Label("Map", systemImage: "map")
                }
                .tag(SyrmosTab.map)

            SyrmosSettingsView()
                .tabItem {
                    Label("Settings", systemImage: "gearshape")
                }
                .tag(SyrmosTab.settings)
        }
        .tint(.syrmosPrimary)
    }
}

enum SyrmosTab {
    case home, lines, map, settings
}
