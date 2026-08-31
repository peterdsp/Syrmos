import Foundation
import Network

struct AriadneChatMessage: Codable {
    let role: String
    let text: String
}

struct AriadneChatResponse: Codable {
    let reply: String
    let ok: Bool
    /// "offline" when the server's own cloud LLMs are down: not a real answer.
    /// Optional so a server build that omits the key still decodes (a default
    /// value would not stop Swift's synthesized Codable throwing on a missing key).
    let provider: String?

    /// The cloud reply to surface, or nil to fall through to the local engine.
    /// The server answers ok=true with provider="offline" and a canned "I can't
    /// reach my brain" message whenever its cloud providers are unreachable
    /// (every call, on the current Pi). Surfacing it would shadow the capable
    /// local grounded engine, so an offline-provider reply counts as no answer.
    var cloudReplyOrNull: String? {
        (ok && provider != "offline") ? reply : nil
    }
}

@MainActor
final class AriadneAPIService {
    static let shared = AriadneAPIService()
    private let baseURL = "https://api-syrmos.peterdsp.dev"
    private let session: URLSession
    private let pathMonitor = NWPathMonitor()
    /// Optimistic until the monitor's first update, so the very first turn still
    /// tries the cloud (and falls back on failure) rather than being pinned offline.
    private var isOnline = true

    private init() {
        let config = URLSessionConfiguration.default
        // The backend emits no bytes while it tries up to three LLM providers in
        // sequence, and nginx caps that at a 30 s read timeout. Keep the client
        // ceilings just above 30 s so a valid-but-slow reply is received rather
        // than truncated; the NWPathMonitor guard (not a short timeout) is what
        // handles the offline/unreachable case.
        config.timeoutIntervalForRequest = 33
        config.timeoutIntervalForResource = 35
        // Do not sit and wait for a network to appear; fail so we can fall back.
        config.waitsForConnectivity = false
        session = URLSession(configuration: config)
        // Reachability guard: track connectivity so an offline device drops to the
        // local grounded engine instantly instead of waiting out the request timeout.
        pathMonitor.pathUpdateHandler = { [weak self] path in
            let online = path.status == .satisfied
            Task { @MainActor in self?.isOnline = online }
        }
        pathMonitor.start(queue: DispatchQueue(label: "ariadne.reachability"))
    }

    func chat(messages: [AriadneChatMessage]) async -> String? {
        // No network path at all: skip the cloud entirely so the caller uses the
        // local engine with no delay (NWPathMonitor is the fast, offline hint).
        guard isOnline else { return nil }
        // Endpoint probe: a network path can exist yet the API be unreachable
        // (captive Wi-Fi, blackholed host, dead tunnel). A ~2.5 s GET /healthz
        // confirms the server actually answers before committing to the long,
        // silent chat request; otherwise fall through to the local engine.
        guard await isEndpointReachable() else { return nil }
        guard let url = URL(string: "\(baseURL)/api/ariadne/chat") else { return nil }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body: [String: Any] = [
            "messages": messages.map { ["role": $0.role, "text": $0.text] }
        ]
        guard let httpBody = try? JSONSerialization.data(withJSONObject: body) else { return nil }
        request.httpBody = httpBody

        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { return nil }
            let decoded = try JSONDecoder().decode(AriadneChatResponse.self, from: data)
            return decoded.cloudReplyOrNull
        } catch {
            return nil
        }
    }

    /// Fast GET /healthz to confirm the API actually answers. Upgrades the
    /// NWPathMonitor hint into true endpoint reachability: a satisfied path does
    /// not prove the host/DNS/tunnel is alive, so a ~2.5 s probe fails fast on a
    /// captive portal or blackholed server and lets the caller use the local engine.
    private func isEndpointReachable() async -> Bool {
        guard let url = URL(string: "\(baseURL)/healthz") else { return false }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 2.5
        do {
            let (_, response) = try await session.data(for: request)
            return (response as? HTTPURLResponse)?.statusCode == 200
        } catch {
            return false
        }
    }
}
