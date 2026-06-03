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
                .onAppear {
                    setWindowBackground()
                }
        }
    }

    private func setWindowBackground() {
        DispatchQueue.main.async {
            UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .flatMap { $0.windows }
                .forEach { window in
                    window.backgroundColor = .systemBackground
                    window.rootViewController?.view.backgroundColor = .systemBackground
                }
        }
    }
}

struct ContentView: View {
    @State private var selectedTab: SyrmosTab = .home

    var body: some View {
        TabView(selection: $selectedTab) {
            ComposeTabView(tab: "home")
                .ignoresSafeArea(.all)
                .tabItem {
                    Label("Home", systemImage: "house")
                }
                .tag(SyrmosTab.home)

            ComposeTabView(tab: "lines")
                .ignoresSafeArea(.all)
                .tabItem {
                    Label("Lines", systemImage: "tram")
                }
                .tag(SyrmosTab.lines)

            ComposeTabView(tab: "map")
                .ignoresSafeArea(.all)
                .tabItem {
                    Label("Map", systemImage: "map")
                }
                .tag(SyrmosTab.map)

            ComposeTabView(tab: "settings")
                .ignoresSafeArea(.all)
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
        let composeVC = MainViewControllerKt.makeTabViewController(tab: tab)

        let container = UIViewController()
        container.view.backgroundColor = .systemBackground

        composeVC.view.backgroundColor = .systemBackground
        composeVC.view.isOpaque = true

        container.addChild(composeVC)
        composeVC.view.translatesAutoresizingMaskIntoConstraints = false
        container.view.addSubview(composeVC.view)

        // Pin to safe area top so Compose renders below status bar,
        // and the container background fills the gap
        NSLayoutConstraint.activate([
            composeVC.view.topAnchor.constraint(equalTo: container.view.safeAreaLayoutGuide.topAnchor),
            composeVC.view.bottomAnchor.constraint(equalTo: container.view.bottomAnchor),
            composeVC.view.leadingAnchor.constraint(equalTo: container.view.leadingAnchor),
            composeVC.view.trailingAnchor.constraint(equalTo: container.view.trailingAnchor),
        ])

        composeVC.didMove(toParent: container)
        return container
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
