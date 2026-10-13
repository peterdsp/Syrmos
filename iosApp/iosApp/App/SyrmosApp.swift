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
        SyrmosClock.installCaptureNetworkGate()
        SyrmosClock.writeCaptureReceipt()
        BackgroundRefresh.register()
        BackgroundRefresh.schedule()

        UNUserNotificationCenter.current().delegate = self

        Task { @MainActor in
            await NotificationService.shared.requestAuthorization()
            NotificationService.shared.scheduleMorningDigest()
            // Phase N J09: reconcile leave-by alarms with the persisted board on
            // launch, so a board changed while the app was closed (or after a
            // reboot) re-schedules correctly. Prunes departed entries first.
            SavedDepartureBoard.shared.pruneDeparted(now: Int64(SyrmosClock.now.timeIntervalSince1970))
            LeaveByReminderScheduler.shared.sync()
        }

        return true
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let category = response.notification.request.content.categoryIdentifier
        let alertId = response.notification.request.content.userInfo["alertId"] as? String ?? ""
        let stationId = response.notification.request.content.userInfo["stationId"] as? String

        Task { @MainActor in
            routeNotification(category: category, alertId: alertId, stationId: stationId)
        }
        completionHandler()
    }

    nonisolated func userNotificationCenter(
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
        let config = UISceneConfiguration(name: "Default Configuration", sessionRole: connectingSceneSession.role)
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

        if let response = connectionOptions.notificationResponse {
            let content = response.notification.request.content
            let alertId = content.userInfo["alertId"] as? String ?? ""
            let stationId = content.userInfo["stationId"] as? String
            Task { @MainActor in
                routeNotification(
                    category: content.categoryIdentifier,
                    alertId: alertId,
                    stationId: stationId
                )
            }
        }
    }

    func sceneDidEnterBackground(_ scene: UIScene) {
        didBackgroundSinceLastRebuild = true
        // Stamp the background time so scene restoration can gate a deep push to a
        // freshness window on the next foreground/relaunch (see SceneRestoration).
        SceneRestorationBackground.record(SyrmosClock.now)
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
        // The reserved-region reader is attached ONCE at the window root. Only a
        // UIKit view can be asked what the system has reserved inside it, and
        // one probe keeps the shared store in step for every pane.
        let root = AnyView(RootView().syrmosReadsReservedRegions())
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
        if ProcessInfo.processInfo.arguments.contains("--syrmos-mockup-capture") {
            MockupCaptureView()
        } else if hasCompletedOnboarding {
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
    @AppStorage("syrmos.selectedTab") private var selectedTab: SyrmosTab = .home
    @State private var showAriadne = false
    @ObservedObject private var loc = LocalizationManager.shared
    @ObservedObject private var themeManager = ThemeManager.shared
    @ObservedObject private var deepLinkRouter = DeepLinkRouter.shared

    var body: some View {
        ZStack {
            Color.syrmosBackground.ignoresSafeArea()
            TabView(selection: $selectedTab) {
                // Constrain reading surfaces to their available container;
                // the native tab bar and map still use the whole window.
                // Insets follow the tab bar when iPadOS moves it to the top
                // and follow the keyboard when a field becomes active.
                HomeView()
                    .modifier(ReadableTabContent(maximum: ReadableTabContent.pairedMaximumWidth))
                    .overlay(alignment: .bottomTrailing) { assistantLauncher() }
                    .tabItem {
                        Label(loc[.home], systemImage: "house")
                    }
                    .tag(SyrmosTab.home)

                LinesView()
                    .modifier(ReadableTabContent(maximum: ReadableTabContent.pairedMaximumWidth))
                    .overlay(alignment: .bottomTrailing) { assistantLauncher() }
                    .tabItem {
                        Label(loc[.explore], systemImage: "safari")
                    }
                    .tag(SyrmosTab.explore)

                TransitMapView()
                    .tabItem {
                        Label(loc[.map], systemImage: "map")
                    }
                    .tag(SyrmosTab.map)

                TimetablesView()
                    .modifier(ReadableTabContent(maximum: ReadableTabContent.pairedMaximumWidth))
                    .overlay(alignment: .bottomTrailing) { assistantLauncher() }
                    .tabItem {
                        Label(loc[.departures], systemImage: "airplane")
                    }
                    .tag(SyrmosTab.departures)

                SyrmosSettingsView()
                    .modifier(ReadableTabContent())
                    .tabItem {
                        Label(loc[.moreTab], systemImage: "ellipsis.circle")
                    }
                    .tag(SyrmosTab.more)
            }
            .tint(.syrmosPrimary)
        }
        // TabView owns the transition between tabs. Animating the entire
        // ContentView as well caused two tab trees to be composited during a
        // rapid switch on iOS 26, producing duplicated headers, status-bar
        // content and a tab bar drawn over the outgoing screen. Keep tab
        // selection atomic; individual controls may still animate locally.
        // Foldables and iPad (master plan, Ariadne): with room for two panes the
        // conversation docks as a trailing inspector beside the content; on a
        // compact width the system presents the same view as a sheet. One view,
        // one conversation, whichever way it is shown.
        .inspector(isPresented: $showAriadne) {
            AriadneView()
                .inspectorColumnWidth(min: 320, ideal: 400, max: 480)
        }
        .preferredColorScheme(themeManager.theme.colorScheme)
        .task {
            _ = DiagnosticsCenter.shared
            DiagnosticsCenter.shared.leaveBreadcrumb("app", "ContentView appeared")
            // Mirror the selected language into the widgets' App Group so
            // they render in EN / EL / SQ to match the app.
            WidgetBridge.publishLanguage(loc.language.rawValue)
            LiveTrainService.onLiveDataRefreshed = { count in
                WidgetBridge.publishLiveTrains(count: count, updatedEpoch: SyrmosClock.now.timeIntervalSince1970)
            }
            await LivePositionsService.shared.refresh()
            await LiveTrainService.shared.refresh()
            // Keep metro/tram/A dots fresh: without a re-poll each projected
            // train expires after its travel time and the map empties within
            // the hour (or shows nothing if launched while service was closed).
            LivePositionsService.shared.startPolling()
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
            switch destination {
            case .station(_), .line(_), .serviceAlert(_), .weatherAlert, .morningDigest, .nearbyAlert(_):
                selectedTab = .home
            }
        }
    }

    // The launcher floats over the bottom-trailing corner of the tab content, above
    // the tab bar. It used to be a full-width bottom safe-area inset with an opaque
    // backing band, which reserved an empty strip across the whole width above the
    // nav bar (the owl sits only on the right). Floating it keeps the owl in reach
    // without that empty band; the pill carries its own opaque circular background,
    // so scrolled content reads cleanly behind it.
    private func assistantLauncher() -> some View {
        AriadneLauncherPill(
            label: askAriadneLabel,
            // Toggle, not set: on a regular width the pill stays visible beside the
            // docked inspector (so it also closes it), and a presentation that did
            // not take (a tap during launch) is recoverable with the next tap.
            onTap: { showAriadne.toggle() }
        )
        .padding(.trailing, 16)
        .padding(.bottom, 12)
    }

    private var askAriadneLabel: String {
        switch loc.language {
        case .greek: return "Ρώτα την Αριάδνη"
        case .albanian: return "Pyet Ariadne"
        case .italian: return "Chiedi ad Ariadne"
        default: return "Ask Ariadne"
        }
    }
}

@MainActor
private func routeNotification(category: String, alertId: String, stationId: String?) {
    switch category {
    case "SERVICE_ALERT":
        DeepLinkRouter.shared.pending = .serviceAlert(id: alertId)
    case "WEATHER_ALERT":
        DeepLinkRouter.shared.pending = .weatherAlert
    case "MORNING_DIGEST":
        DeepLinkRouter.shared.pending = .morningDigest
    case "NEARBY_ALERT":
        DeepLinkRouter.shared.pending = .nearbyAlert(stationId: stationId)
    default:
        DeepLinkRouter.shared.pending = .morningDigest
    }
}

enum SyrmosTab: String {
    case home, explore, map, departures, more
}

/// A single reading column is a safe baseline for large/resizable windows.
/// The same view tree remains mounted as its proposed width changes.
private struct ReadableTabContent: ViewModifier {
    static let maximumWidth: CGFloat = 760
    /// Tabs whose content pairs through SyrmosArrangement get a wider stage: the
    /// arrangement makes two readable columns (or one centred column when it
    /// cannot pair), so the old 760 cap only squeezed the pair and made a tall
    /// iPad stack Explore. Single-column tabs (More) keep the reading column.
    static let pairedMaximumWidth: CGFloat = 1200
    var maximum: CGFloat = ReadableTabContent.maximumWidth

    func body(content: Content) -> some View {
        content
            .frame(maxWidth: maximum)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.syrmosBackground)
    }
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

/// Deterministic presentation-only surface used to export the three Duo mockup images.
struct MockupCaptureView: View {
    @State private var screen: String
    private let navy = Color(red: 0.04, green: 0.09, blue: 0.16)
    private let orange = Color(red: 0.91, green: 0.45, blue: 0.16)
    private let teal = Color(red: 0.05, green: 0.46, blue: 0.55)

    init(screen: String? = nil) {
        _screen = State(initialValue: screen ?? ProcessInfo.processInfo.environment["SYRMOS_MOCKUP_SCREEN"] ?? "home")
    }

    var body: some View {
        ZStack {
            navy.ignoresSafeArea()
            switch screen {
            case "map": map
            case "explore": explore
            default: home
            }
        }
        .preferredColorScheme(.dark)
    }

    private var home: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                header("Good evening")
                Text("Airport connections").font(.largeTitle.bold()).foregroundStyle(.white)
                HStack(spacing: 10) { routeChip("X95", orange); routeChip("X93", orange); routeChip("X3", teal); routeChip("01X", teal) }
                card { Label("Rain in progress", systemImage: "cloud.rain.fill").font(.headline).foregroundStyle(.white); Text("Athens · severe weather alert").font(.subheadline).foregroundStyle(.white.opacity(0.7)) }
                card {
                    Text("Next airport buses").font(.headline).foregroundStyle(.white)
                    busRow("X95", "Syntagma → Airport", "6 min", orange)
                    busRow("X3", "Mikra Metro → Airport", "12 min", teal)
                    busRow("01X", "Airport → Thessaloniki", "18 min", teal)
                }
                Text("DEMO DATA · verified route presentation").font(.caption2).foregroundStyle(.white.opacity(0.45))
            }.padding(24)
        }
    }

    private var map: some View {
        VStack(alignment: .leading, spacing: 0) {
            header("Airport buses").padding(.horizontal, 24)
            ZStack {
                LinearGradient(colors: [navy, Color(red: 0.08, green: 0.2, blue: 0.25)], startPoint: .top, endPoint: .bottom)
                Canvas { context, size in
                    var p = Path(); p.move(to: CGPoint(x: 0, y: size.height * 0.72)); p.addCurve(to: CGPoint(x: size.width, y: size.height * 0.3), control1: CGPoint(x: size.width * 0.3, y: size.height * 0.5), control2: CGPoint(x: size.width * 0.7, y: size.height * 0.8)); context.stroke(p, with: .color(orange), lineWidth: 8)
                    var q = Path(); q.move(to: CGPoint(x: 0, y: size.height * 0.3)); q.addCurve(to: CGPoint(x: size.width, y: size.height * 0.68), control1: CGPoint(x: size.width * 0.35, y: size.height * 0.7), control2: CGPoint(x: size.width * 0.7, y: size.height * 0.42)); context.stroke(q, with: .color(teal), lineWidth: 7)
                }
                VStack {
                    HStack { stopLabel("Mikra Metro", teal); Spacer(); stopLabel("Airport", orange) }.padding(.horizontal, 42).padding(.top, 90)
                    Spacer()
                    HStack { stopLabel("Nea Elvetia", teal); Spacer(); stopLabel("Terminal 1", orange) }.padding(.horizontal, 90).padding(.bottom, 190)
                }
                VStack { Spacer(); card { HStack { Text("X3 · Thessaloniki Airport").font(.headline).foregroundStyle(.white); Spacer(); Text("DEMO").font(.caption.bold()).foregroundStyle(teal) }; Text("Mikra Metro ↔ Airport").foregroundStyle(.white.opacity(0.72)); Text("Bus stops · route geometry · direction verified").font(.caption).foregroundStyle(.white.opacity(0.55)) }.padding(20) }
            }
        }
    }

    private var explore: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                header("Explore")
                Text("Airport routes").font(.largeTitle.bold()).foregroundStyle(.white)
                card { Text("Athens").font(.title3.bold()).foregroundStyle(.white); busRow("X95", "Syntagma ↔ Airport", "24/7", orange); busRow("X93", "Kifisos ↔ Airport", "24/7", orange) }
                card { Text("Thessaloniki").font(.title3.bold()).foregroundStyle(.white); busRow("X3", "Mikra Metro ↔ Airport", "scheduled", teal); busRow("01X", "Airport ↔ City", "scheduled", teal); busRow("01N", "Night airport service", "scheduled", teal) }
                card { Label("Airport bus stops", systemImage: "mappin.and.ellipse").font(.headline).foregroundStyle(.white); Text("Tap a stop for direction, verified arrivals and freshness.").foregroundStyle(.white.opacity(0.7)) }
                Text("DEMO DATA · no live values shown in this capture").font(.caption2).foregroundStyle(.white.opacity(0.45))
            }.padding(24)
        }
    }

    private func header(_ title: String) -> some View { Text(title).font(.title2.bold()).foregroundStyle(.white).padding(.top, 18) }
    private func card(@ViewBuilder _ content: () -> some View) -> some View { VStack(alignment: .leading, spacing: 10, content: content).padding(18).frame(maxWidth: .infinity, alignment: .leading).background(.white.opacity(0.1), in: RoundedRectangle(cornerRadius: 20, style: .continuous)) }
    private func routeChip(_ text: String, _ color: Color) -> some View { Text(text).font(.caption.bold()).foregroundStyle(.white).padding(.horizontal, 12).padding(.vertical, 8).background(color, in: Capsule()) }
    private func stopLabel(_ text: String, _ color: Color) -> some View { HStack(spacing: 6) { Circle().fill(color).frame(width: 12, height: 12); Text(text).font(.caption.bold()).foregroundStyle(.white).padding(.horizontal, 9).padding(.vertical, 6).background(.black.opacity(0.45), in: Capsule()) } }
    private func busRow(_ line: String, _ title: String, _ eta: String, _ color: Color) -> some View { HStack { Text(line).font(.caption.bold()).foregroundStyle(.white).frame(width: 42, height: 28).background(color, in: Capsule()); Text(title).foregroundStyle(.white.opacity(0.85)); Spacer(); Text(eta).font(.caption.bold()).foregroundStyle(.white) }.padding(.vertical, 4) }
}
