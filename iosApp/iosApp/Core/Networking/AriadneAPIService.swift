import Foundation

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

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 20
        config.timeoutIntervalForResource = 25
        session = URLSession(configuration: config)
    }

    func chat(messages: [AriadneChatMessage]) async -> String? {
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
}
