import SwiftUI

@main
struct SyrmosApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .onAppear {
                    // Match window background to system appearance
                    for scene in UIApplication.shared.connectedScenes {
                        if let windowScene = scene as? UIWindowScene {
                            for window in windowScene.windows {
                                window.backgroundColor = .systemGroupedBackground
                            }
                        }
                    }
                }
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
