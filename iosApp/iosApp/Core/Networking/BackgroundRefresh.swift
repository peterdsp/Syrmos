import Foundation
import BackgroundTasks

/// Keeps the Weather + Alerts widget fresh without the user opening the app.
///
/// The system runs a `BGAppRefreshTask` opportunistically (typically when the
/// device has network and isn't under load). The handler re-fetches weather and
/// service alerts; both already mirror their result into the shared App Group
/// and call `WidgetCenter.reloadAllTimelines()`, so the widget picks up live
/// data on its own. Offline, the fetches degrade quietly and the widget keeps
/// its last snapshot.
enum BackgroundRefresh {
    static let taskId = "com.syrmosApp.ios.refresh"

    /// Register the handler. Must be called before the app finishes launching.
    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: taskId, using: nil) { task in
            guard let refresh = task as? BGAppRefreshTask else { task.setTaskCompleted(success: false); return }
            handle(refresh)
        }
    }

    /// Ask the system to run us again in roughly half an hour. Cheap to call
    /// repeatedly; the system coalesces and decides the real timing.
    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: taskId)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 30 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    /// `BGAppRefreshTask` isn't `Sendable`, but its completion methods are
    /// thread-safe, so boxing it lets us finish the task from the async work.
    private struct TaskBox: @unchecked Sendable { let task: BGAppRefreshTask }

    private static func handle(_ task: BGAppRefreshTask) {
        // Chain the next refresh immediately so the loop keeps going.
        schedule()
        let box = TaskBox(task: task)
        let work = Task {
            await refreshData()
            box.task.setTaskCompleted(success: true)
        }
        task.expirationHandler = { work.cancel() }
    }

    @MainActor
    private static func refreshData() async {
        // Weather → App Group (WidgetBridge.publishWeather → reloadAllTimelines).
        await WeatherStore.shared.refresh()
        // Service alerts → App Group (WidgetBridge.publishAlerts → reloadAllTimelines).
        let stasy = STASYService()
        await stasy.fetchAnnouncements()

        // Check for new alerts and severe weather, post local notifications
        // if the user has them enabled.
        let notif = NotificationService.shared
        notif.checkForNewAlerts(stasy.announcements)
        notif.checkWeather(WeatherStore.shared.snapshot)
    }
}
