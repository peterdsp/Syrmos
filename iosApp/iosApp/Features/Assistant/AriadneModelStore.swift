import Foundation

// On-demand store for Ariadne's model on iOS. The ~1.1 GB GGUF is NOT bundled
// and never auto-downloaded; the user opts in, we fetch it from the pinned
// manifest URL, verify its SHA-256, and cache it in Application Support. Until it
// is present, the classifier returns nil and Ariadne uses the rule parser.
// Mirrors the Android AriadneModelStore.
@MainActor
final class AriadneModelStore: ObservableObject {
    static let shared = AriadneModelStore()

    enum Status: Equatable { case notDownloaded, downloading(Double), verifying, ready, error, insufficientStorage }

    // Kept in sync with core/common AriadneModelManifest.
    nonisolated static let fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
    nonisolated static let url = URL(string: "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf")!
    nonisolated static let sha256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"
    nonisolated static let byteCount: Int64 = 1_117_320_736

    @Published private(set) var status: Status = .notDownloaded

    private var modelURL: URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Ariadne", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent(Self.fileName)
    }

    var modelPath: String? {
        let p = modelURL.path
        return FileManager.default.fileExists(atPath: p) ? p : nil
    }

    /// Nonisolated path to the downloaded, complete model, or nil. Safe to call
    /// from the classifier (off the main actor).
    nonisolated static func readyModelPath() -> String? {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Ariadne", isDirectory: true)
        let f = dir.appendingPathComponent(fileName)
        guard let attrs = try? FileManager.default.attributesOfItem(atPath: f.path),
              (attrs[.size] as? Int64) == byteCount else { return nil }
        return f.path
    }

    func refreshReadyState() {
        if let attrs = try? FileManager.default.attributesOfItem(atPath: modelURL.path),
           (attrs[.size] as? Int64) == Self.byteCount {
            status = .ready
        }
    }

    /// Explicit, user-triggered download with real progress, a storage preflight,
    /// and off-main-actor checksum verification.
    func download() async {
        if case .ready = status { return }
        // Storage preflight: fail fast with an actionable state instead of
        // spending 1.1 GB of transfer only to hit a generic write error.
        guard hasSpaceForModel() else { status = .insufficientStorage; return }
        status = .downloading(0)
        do {
            // A per-task delegate reports real byte progress (the plain async
            // download(from:) reports none, which left the banner stuck at 0%).
            let progress = DownloadProgressDelegate { [weak self] fraction in
                Task { @MainActor in
                    guard let self else { return }
                    if case .downloading = self.status { self.status = .downloading(fraction) }
                }
            }
            let (tempURL, response) = try await URLSession.shared.download(from: Self.url, delegate: progress)
            if let http = response as? HTTPURLResponse, http.statusCode != 200 {
                status = .error
                return
            }
            // Hashing 1.1 GB must NOT run on the main actor (it would freeze the UI
            // for seconds and risk a watchdog kill). Do it on a utility task.
            status = .verifying
            let expected = Self.sha256
            let ok = try await Task.detached(priority: .utility) {
                try AriadneModelStore.verify(tempURL, expected: expected)
            }.value
            guard ok else { status = .error; return }
            let dest = modelURL
            try? FileManager.default.removeItem(at: dest)
            try FileManager.default.moveItem(at: tempURL, to: dest)
            status = .ready
        } catch {
            status = .error
        }
    }

    /// True when the volume has room for the model plus a safety margin. Uses the
    /// "important usage" figure iOS actually honours for on-demand downloads.
    private func hasSpaceForModel() -> Bool {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        if let values = try? dir.resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey]),
           let available = values.volumeAvailableCapacityForImportantUsage {
            return available > Self.byteCount + Self.byteCount / 10
        }
        return true // capacity unknown: stay optimistic and let the write fail if it must
    }

    /// Streaming SHA-256 of the file. `nonisolated static` so it can run off the
    /// main actor via a detached task.
    nonisolated private static func verify(_ url: URL, expected: String) throws -> Bool {
        var hasher = SHA256Streaming()
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        while case let data = handle.readData(ofLength: 1 << 20), !data.isEmpty {
            hasher.update(data)
        }
        return hasher.hexDigest() == expected
    }
}

/// Reports download byte-progress for the async `download(from:delegate:)` call.
/// `@unchecked Sendable`: it only forwards immutable progress numbers through a
/// closure that hops to the main actor.
private final class DownloadProgressDelegate: NSObject, URLSessionDownloadDelegate, @unchecked Sendable {
    private let onProgress: (Double) -> Void
    init(onProgress: @escaping (Double) -> Void) { self.onProgress = onProgress }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask,
                    didWriteData bytesWritten: Int64, totalBytesWritten: Int64,
                    totalBytesExpectedToWrite: Int64) {
        guard totalBytesExpectedToWrite > 0 else { return }
        onProgress(Double(totalBytesWritten) / Double(totalBytesExpectedToWrite))
    }

    // Required by the protocol; the async download(from:delegate:) call returns the
    // file location itself, so nothing to do here.
    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask,
                    didFinishDownloadingTo location: URL) {}
}

// Minimal streaming SHA-256 (CryptoKit's Insecure is not needed; use CommonCrypto
// via a tiny shim to avoid loading the whole 1.1 GB file into memory).
import CryptoKit
private struct SHA256Streaming {
    private var hasher = SHA256()
    mutating func update(_ data: Data) { hasher.update(data: data) }
    func hexDigest() -> String {
        hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }
}
