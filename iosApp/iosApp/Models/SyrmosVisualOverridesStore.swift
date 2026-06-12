import Foundation
import SwiftUI

/// Single source of truth for visual overrides served by the API:
///   /api/icons         -> station_id -> SVG url (override admin -> default)
///   /api/line-display  -> per-line stroke color, weight, dash, glow
///
/// Loaded on cold start, cached in UserDefaults so an offline launch still
/// gets the most recent admin edit. Falls back to bundled assets if the
/// network is unreachable and no cache exists yet.
@MainActor
final class SyrmosVisualOverridesStore: ObservableObject {
    static let shared = SyrmosVisualOverridesStore()

    @Published private(set) var stationIconUrls: [String: URL] = [:]
    @Published private(set) var lineDisplay: [String: LineDisplay] = [:]

    private let defaults = UserDefaults.standard
    private let session = URLSession.shared
    private let kIconsKey = "syrmos.icons.v1"
    private let kLineKey = "syrmos.line_display.v1"
    private let base = "https://api-syrmos.peterdsp.dev"

    private init() {
        hydrateFromCache()
        Task { await refresh() }
    }

    func iconURL(for stationId: String) -> URL? { stationIconUrls[stationId] }
    func display(for lineId: String) -> LineDisplay? { lineDisplay[lineId] }

    /// Cold-start hydrate from cached JSON so offline launch gets correct visuals.
    private func hydrateFromCache() {
        if let data = defaults.data(forKey: kIconsKey),
           let payload = try? JSONDecoder().decode(IconsPayload.self, from: data) {
            apply(icons: payload)
        }
        if let data = defaults.data(forKey: kLineKey),
           let payload = try? JSONDecoder().decode(LineDisplayPayload.self, from: data) {
            apply(lineDisplay: payload)
        }
    }

    /// Fetch fresh from the API. Silent on failure — cache stays current.
    func refresh() async {
        async let iconsTask: IconsPayload? = fetch(IconsPayload.self, at: "/api/icons")
        async let lineTask: LineDisplayPayload? = fetch(LineDisplayPayload.self, at: "/api/line-display")
        let icons = await iconsTask
        let line = await lineTask
        if let icons = icons {
            apply(icons: icons)
            if let data = try? JSONEncoder().encode(icons) {
                defaults.set(data, forKey: kIconsKey)
            }
        }
        if let line = line {
            apply(lineDisplay: line)
            if let data = try? JSONEncoder().encode(line) {
                defaults.set(data, forKey: kLineKey)
            }
        }
    }

    private func fetch<T: Decodable>(_ type: T.Type, at path: String) async -> T? {
        guard let url = URL(string: base + path) else { return nil }
        var req = URLRequest(url: url)
        req.timeoutInterval = 8
        do {
            let (data, response) = try await session.data(for: req)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { return nil }
            return try JSONDecoder().decode(T.self, from: data)
        } catch {
            return nil
        }
    }

    private func apply(icons: IconsPayload) {
        var map: [String: URL] = [:]
        for (sid, urlString) in icons.stations {
            if let u = URL(string: urlString) { map[sid] = u }
        }
        for (sid, urlString) in icons.interchanges {
            if let u = URL(string: urlString) { map[sid] = u }  // override
        }
        stationIconUrls = map
    }

    private func apply(lineDisplay payload: LineDisplayPayload) {
        var map: [String: LineDisplay] = [:]
        for ld in payload.lines { map[ld.lineId] = ld }
        self.lineDisplay = map
    }

    // MARK: - Wire types

    struct IconsPayload: Codable {
        let updatedAt: String
        let stations: [String: String]
        let interchanges: [String: String]
    }

    struct LineDisplayPayload: Codable {
        let updatedAt: String
        let lines: [LineDisplay]
    }

    struct LineDisplay: Codable, Equatable {
        let lineId: String
        let strokeColor: String
        let strokeWeight: Int
        let strokeDash: String?
        let labelColor: String?
        let glow: Bool
        let notes: String

        var swiftUIColor: Color {
            Color(hex: strokeColor) ?? .gray
        }
    }
}

extension Color {
    /// `Color(hex: "#0083C9")` — convenience for the admin-supplied stroke colors.
    init?(hex: String) {
        var s = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.hasPrefix("#") { s.removeFirst() }
        guard s.count == 6, let v = UInt32(s, radix: 16) else { return nil }
        let r = Double((v >> 16) & 0xFF) / 255.0
        let g = Double((v >> 8) & 0xFF) / 255.0
        let b = Double(v & 0xFF) / 255.0
        self = Color(red: r, green: g, blue: b)
    }
}
