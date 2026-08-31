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
    /// Circuit breaker: set after a cloud turn fails to produce a usable reply, so
    /// the next questions during the same outage answer instantly from the local
    /// engine instead of each waiting out the ~33 s timeout. Uses ContinuousClock
    /// (monotonic uptime), NOT Date, so a wall-clock correction can neither extend
    /// the outage window nor reopen the cloud early. @MainActor-confined, matching
    /// the KMP breaker's single-thread contract.
    private let clock = ContinuousClock()
    private var cloudDownUntil: ContinuousClock.Instant?
    private let breakerCooldown: Duration = .seconds(30)

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

    /// - Parameter isUsable: whether a decoded reply is worth surfacing (the
    ///   caller's short/blank/leaked-reasoning filter). The breaker outcome is tied
    ///   to this, so a junk reply from a degraded provider trips the breaker
    ///   instead of making every question pay the full round trip.
    func chat(messages: [AriadneChatMessage],
              isUsable: (String) -> Bool = { _ in true }) async -> String? {
        // No network path at all: skip the cloud entirely so the caller uses the
        // local engine with no delay (NWPathMonitor is the fast, offline hint).
        guard isOnline else { return nil }
        // Circuit breaker open from a recent failure: go straight to local. The
        // first question after the cooldown still tries the cloud with the full
        // budget, so a viable-but-slow network is never downgraded.
        if let until = cloudDownUntil, clock.now < until { return nil }
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
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
                tripBreaker()
                return nil
            }
            let decoded = try JSONDecoder().decode(AriadneChatResponse.self, from: data)
            if let reply = decoded.cloudReplyOrNull, isUsable(reply) {
                cloudDownUntil = nil // a real, surfaced reply clears the breaker
                return reply
            }
            tripBreaker() // ok=false, provider=offline, or an unusable reply
            return nil
        } catch is CancellationError {
            return nil // a cancelled turn is not an outage: do not trip the breaker
        } catch let error as URLError where error.code == .cancelled {
            return nil
        } catch {
            tripBreaker() // network error / timeout: cloud unreachable now
            return nil
        }
    }

    private func tripBreaker() {
        cloudDownUntil = clock.now.advanced(by: breakerCooldown)
    }
}
