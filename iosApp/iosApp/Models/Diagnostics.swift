import Foundation
import MetricKit
import os.log
import UIKit

/// Lightweight runtime diagnostics so we can debug the "app went black" reports
/// without third-party SDKs.
///
/// Three sources of evidence are collected:
///
/// 1. **MetricKit** (Apple's native diagnostics). Apple delivers daily
///    crash reports, main-thread hangs, CPU exception reports and disk-write
///    exception payloads. We save them as JSON next to the app sandbox so
///    they survive launches.
/// 2. **Main-thread hang detector**. A background watchdog timer fires every
///    250 ms and asks main to acknowledge; if main is silent for > 1 second
///    we log a hang event with the current view, tab and recent action
///    breadcrumbs.
/// 3. **Breadcrumbs**. A ring buffer of recent user actions (tab switches,
///    network requests, view appearances). When a hang lands we attach the
///    last 50 entries.
///
/// Everything is written to `Library/Caches/syrmos-diagnostics/*.json` and
/// surfaced in Settings → Diagnostics so a user can tap "Share" to send
/// the bundle to support.
@MainActor
final class DiagnosticsCenter: NSObject, ObservableObject, MXMetricManagerSubscriber {
    static let shared = DiagnosticsCenter()

    @Published private(set) var breadcrumbs: [Breadcrumb] = []
    @Published private(set) var hangs: [HangEvent] = []
    @Published private(set) var lastDiagnostic: String?

    struct Breadcrumb: Identifiable, Codable {
        let id: UUID
        let timestamp: Date
        let category: String   // tab, network, view, action
        let message: String
    }

    struct HangEvent: Identifiable, Codable {
        let id: UUID
        let timestamp: Date
        let durationMs: Int
        let recentBreadcrumbs: [Breadcrumb]
    }

    private let log = OSLog(subsystem: "dev.peterdsp.syrmos", category: "diagnostics")
    private let hangThresholdSeconds: TimeInterval = 1.0
    private let watchdogIntervalSeconds: TimeInterval = 0.25
    private let breadcrumbCapacity = 100

    private var lastMainHeartbeat: Date = .init()
    private var watchdogTask: Task<Void, Never>?

    private lazy var diagnosticsURL: URL? = {
        let fm = FileManager.default
        guard let dir = fm.urls(for: .cachesDirectory, in: .userDomainMask).first else {
            return nil
        }
        let folder = dir.appendingPathComponent("syrmos-diagnostics", isDirectory: true)
        try? fm.createDirectory(at: folder, withIntermediateDirectories: true)
        return folder
    }()

    private override init() {
        super.init()
        MXMetricManager.shared.add(self)
        startHangDetector()
        observeMemoryWarnings()
    }

    func leaveBreadcrumb(_ category: String, _ message: String) {
        let crumb = Breadcrumb(id: UUID(), timestamp: Date(), category: category, message: message)
        breadcrumbs.append(crumb)
        if breadcrumbs.count > breadcrumbCapacity {
            breadcrumbs.removeFirst(breadcrumbs.count - breadcrumbCapacity)
        }
        os_log("%{public}@: %{public}@", log: log, type: .info, category, message)
    }

    func shareableBundleURL() -> URL? {
        guard let dir = diagnosticsURL else { return nil }
        let url = dir.appendingPathComponent("syrmos-diagnostics-\(Int(Date().timeIntervalSince1970)).json")
        let payload: [String: Any] = [
            "exportedAt": ISO8601DateFormatter().string(from: Date()),
            "deviceModel": UIDevice.current.model,
            "systemVersion": UIDevice.current.systemVersion,
            "appVersion": Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "?",
            "buildNumber": Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "?",
            "breadcrumbs": breadcrumbs.map { [
                "ts": ISO8601DateFormatter().string(from: $0.timestamp),
                "category": $0.category,
                "message": $0.message,
            ] },
            "hangs": hangs.map { [
                "ts": ISO8601DateFormatter().string(from: $0.timestamp),
                "durationMs": $0.durationMs,
            ] },
            "lastDiagnostic": lastDiagnostic ?? "",
        ]
        do {
            let data = try JSONSerialization.data(withJSONObject: payload, options: [.prettyPrinted, .sortedKeys])
            try data.write(to: url, options: .atomic)
            return url
        } catch {
            os_log("Failed to write diagnostics bundle: %{public}@", log: log, type: .error, error.localizedDescription)
            return nil
        }
    }

    // MARK: - Main thread hang detector

    private func startHangDetector() {
        watchdogTask?.cancel()
        watchdogTask = Task.detached(priority: .utility) { [weak self] in
            await DiagnosticsCenter.watchdogLoop(self)
        }
    }

    private static func watchdogLoop(_ instance: DiagnosticsCenter?) async {
        while !Task.isCancelled {
            // Schedule a main-thread heartbeat update. If main is stuck this
            // call sits in the queue until main wakes up.
            let scheduledAt = Date()
            await MainActor.run { instance?.lastMainHeartbeat = Date() }
            let now = Date()
            let elapsed = now.timeIntervalSince(scheduledAt)
            if let threshold = await instance?.hangThresholdSeconds, elapsed > threshold {
                await instance?.recordHang(durationMs: Int(elapsed * 1000))
            }
            try? await Task.sleep(nanoseconds: UInt64((instance?.watchdogIntervalSeconds ?? 0.25) * 1_000_000_000))
        }
    }

    private func recordHang(durationMs: Int) {
        let event = HangEvent(
            id: UUID(),
            timestamp: Date(),
            durationMs: durationMs,
            recentBreadcrumbs: Array(breadcrumbs.suffix(50))
        )
        hangs.append(event)
        if hangs.count > 20 { hangs.removeFirst(hangs.count - 20) }
        os_log("Main-thread hang detected: %{public}d ms", log: log, type: .fault, durationMs)
        leaveBreadcrumb("hang", "Main thread blocked for \(durationMs) ms")
        persistHangs()
    }

    private func persistHangs() {
        guard let dir = diagnosticsURL else { return }
        let url = dir.appendingPathComponent("hangs.json")
        if let data = try? JSONEncoder().encode(hangs) {
            try? data.write(to: url, options: .atomic)
        }
    }

    // MARK: - MetricKit

    nonisolated func didReceive(_ payloads: [MXMetricPayload]) {
        // Extract Sendable data before hopping to MainActor — the payload
        // types themselves are not Sendable in Swift 6.
        let blobs: [Data] = payloads.map { $0.jsonRepresentation() }
        let ts = ISO8601DateFormatter().string(from: Date())
        Task { @MainActor [weak self] in
            guard let self else { return }
            for data in blobs {
                self.savePayload(data, prefix: "metrics")
            }
            self.lastDiagnostic = "Metrics payload received \(ts)"
        }
    }

    nonisolated func didReceive(_ payloads: [MXDiagnosticPayload]) {
        let blobs: [Data] = payloads.map { $0.jsonRepresentation() }
        let hangSummaries: [Int] = payloads.flatMap { payload -> [Int] in
            (payload.hangDiagnostics ?? []).map {
                Int($0.hangDuration.converted(to: .seconds).value * 1000)
            }
        }
        let ts = ISO8601DateFormatter().string(from: Date())
        Task { @MainActor [weak self] in
            guard let self else { return }
            for data in blobs {
                self.savePayload(data, prefix: "diagnostic")
            }
            for ms in hangSummaries {
                self.leaveBreadcrumb("metrickit-hang", "Apple-reported hang \(ms) ms")
            }
            self.lastDiagnostic = "Diagnostic payload received \(ts)"
        }
    }

    private func savePayload(_ data: Data, prefix: String) {
        guard let dir = diagnosticsURL else { return }
        let ts = Int(Date().timeIntervalSince1970)
        let url = dir.appendingPathComponent("\(prefix)-\(ts).json")
        try? data.write(to: url, options: .atomic)
    }

    // MARK: - Memory pressure

    private func observeMemoryWarnings() {
        NotificationCenter.default.addObserver(
            forName: UIApplication.didReceiveMemoryWarningNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                self?.leaveBreadcrumb("memory", "Memory warning received")
            }
        }
    }
}
