import Foundation

/// The app's single source of "now".
///
/// Everything that renders a time, a countdown, a schedule projection or a plan
/// reads this instead of calling `Date()` directly, so a visual-baseline capture
/// or a test can pin the clock and get the same pixels every run. Phase Z needs
/// this: captures taken against the wall clock show whatever the network happens
/// to be doing at that hour and can never be diffed against a baseline.
///
/// In a release build this is exactly `Date()` and the override is compiled out,
/// so there is no way to ship a pinned clock to a user.
enum SyrmosClock {
    #if DEBUG
    /// `SYRMOS_CAPTURE_NOW`, an ISO 8601 instant such as
    /// `2026-09-16T08:42:00+03:00`. Read once: the pinned clock must not drift
    /// between the first and last screen of a capture run.
    private static let pinned: Date? = {
        let raw = ProcessInfo.processInfo.environment["SYRMOS_CAPTURE_NOW"]?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !raw.isEmpty else { return nil }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        if let parsed = formatter.date(from: raw) { return parsed }
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.date(from: raw)
    }()

    /// Whether the clock is pinned. Capture tooling asserts on this so a run that
    /// silently failed to pin cannot be mistaken for a baseline.
    static var isPinned: Bool { pinned != nil }

    static var now: Date { pinned ?? Date() }

    /// Whether looping decorative animations should stay still.
    ///
    /// A pulse that never settles means two screenshots of the same state are
    /// never byte-identical, which defeats a raster baseline. Tied to the pinned
    /// clock because that is exactly when the app is being captured.
    static var animationsSuppressed: Bool { isPinned }

    /// Leave proof of the pinned clock in the app container at launch.
    ///
    /// The capture harness reads this file and refuses to record a baseline when
    /// it is missing or disagrees with the instant it asked for. Without the
    /// receipt, a launch where the environment never reached the app looks
    /// exactly like a successful pin, and the run would quietly produce
    /// wall-clock screenshots labelled as a frozen baseline.
    static func writeCaptureReceipt() {
        guard let pinned else { return }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        guard let documents = FileManager.default.urls(
            for: .documentDirectory, in: .userDomainMask
        ).first else { return }
        try? formatter.string(from: pinned).write(
            to: documents.appendingPathComponent("capture-clock.txt"),
            atomically: true, encoding: .utf8
        )
    }
    #else
    static var isPinned: Bool { false }

    static var now: Date { Date() }

    static var animationsSuppressed: Bool { false }

    /// No-op in release: there is no pinned clock to attest to.
    static func writeCaptureReceipt() {}

    /// No-op in release: the capture network gate does not exist there.
    static func installCaptureNetworkGate() {}
    #endif
}

#if DEBUG
extension SyrmosClock {
    /// Cut the app off from the network for a capture run.
    ///
    /// A pinned clock alone is not enough to make a screenshot reproducible:
    /// live vehicle positions, the freshness banner and the announcement feed
    /// all change under the app between two runs, so the pixels differ even
    /// though the clock does not. With the gate on, every screen renders from
    /// the bundled seed, which is a state we control and can diff.
    ///
    /// This is deliberately blunt. It makes the offline path the captured state,
    /// so the online variants of those screens need their own recorded fixtures
    /// before they can be baselined.
    static func installCaptureNetworkGate() {
        guard ProcessInfo.processInfo.environment["SYRMOS_CAPTURE_OFFLINE"] == "1" else { return }
        URLProtocol.registerClass(CaptureOfflineURLProtocol.self)
    }
}

/// Fails every request as if the device had no connection.
final class CaptureOfflineURLProtocol: URLProtocol {
    override class func canInit(with request: URLRequest) -> Bool { true }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        client?.urlProtocol(self, didFailWithError: URLError(.notConnectedToInternet))
    }

    override func stopLoading() {}
}
#endif
