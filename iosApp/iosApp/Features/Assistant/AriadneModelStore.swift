import Foundation

// On-demand store for Ariadne's model on iOS. The ~1.1 GB GGUF is NOT bundled
// and never auto-downloaded; the user opts in, we fetch it from the pinned
// manifest URL, verify its SHA-256, and cache it in Application Support. Until it
// is present, the classifier returns nil and Ariadne uses the rule parser.
// Mirrors the Android AriadneModelStore.
@MainActor
final class AriadneModelStore: ObservableObject {
    static let shared = AriadneModelStore()

    enum Status: Equatable { case notDownloaded, downloading(Double), ready, error }

    // Kept in sync with core/common AriadneModelManifest.
    static let fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
    static let url = URL(string: "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf")!
    static let sha256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"
    static let byteCount: Int64 = 1_117_320_736

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

    func refreshReadyState() {
        if let attrs = try? FileManager.default.attributesOfItem(atPath: modelURL.path),
           (attrs[.size] as? Int64) == Self.byteCount {
            status = .ready
        }
    }

    /// Explicit, user-triggered download with checksum verification.
    func download() async {
        if case .ready = status { return }
        status = .downloading(0)
        do {
            let (tempURL, _) = try await URLSession.shared.download(from: Self.url)
            let ok = try verify(tempURL)
            guard ok else { status = .error; return }
            let dest = modelURL
            try? FileManager.default.removeItem(at: dest)
            try FileManager.default.moveItem(at: tempURL, to: dest)
            status = .ready
        } catch {
            status = .error
        }
    }

    private func verify(_ url: URL) throws -> Bool {
        var hasher = SHA256Streaming()
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        while case let data = handle.readData(ofLength: 1 << 20), !data.isEmpty {
            hasher.update(data)
        }
        return hasher.hexDigest() == Self.sha256
    }
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
