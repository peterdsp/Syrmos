import SwiftUI
import composeApp

@main
struct SyrmosApp: App {

    init() {
        MainViewControllerKt.doInitKoin()
    }

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
            ComposeTabView(tab: "home")
                .tabItem {
                    Label("Home", systemImage: "house")
                }
                .tag(SyrmosTab.home)

            ComposeTabView(tab: "lines")
                .tabItem {
                    Label("Lines", systemImage: "tram")
                }
                .tag(SyrmosTab.lines)

            ComposeTabView(tab: "map")
                .tabItem {
                    Label("Map", systemImage: "map")
                }
                .tag(SyrmosTab.map)

            ComposeTabView(tab: "settings")
                .tabItem {
                    Label("Settings", systemImage: "gearshape")
                }
                .tag(SyrmosTab.settings)
        }
        .tint(.blue)
    }
}

enum SyrmosTab {
    case home, lines, map, settings
}

struct ComposeTabView: UIViewControllerRepresentable {
    let tab: String

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.makeTabViewController(tab: tab)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
