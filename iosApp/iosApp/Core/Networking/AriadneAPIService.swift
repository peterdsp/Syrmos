import Foundation

struct AriadneChatMessage: Codable {
    let role: String
    let text: String
}

struct AriadneChatResponse: Codable {
    let reply: String
    let ok: Bool
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
            return decoded.ok ? decoded.reply : nil
        } catch {
            return nil
        }
    }
}
