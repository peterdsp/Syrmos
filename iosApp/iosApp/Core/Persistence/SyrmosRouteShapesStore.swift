import Foundation
import CoreLocation

/// OSM-derived rail route geometry for the map polylines.
///
/// Why this exists: catmullRomSpline'd station coordinates produce visibly
/// wrong shapes — Piraeus loop, T7 outbound diverging at Akti Posidonos, M3
/// airport branch, A4 going around through Megara. Real shape comes from
/// OSM route relations (ODbL, attribution required), stitched in
/// `scripts/snapshot-osm-shapes.py` and shipped in
/// `seed-schedules-v2/shapes.json`.
///
/// Offline-first: bundled seed hydrates synchronously at init. Falls back to
/// straight-segment station coordinates via the caller when a line has no
/// shape.
///
/// Not @MainActor: this is a read-only store with no @Published state, and
/// `PreloadedData.routeLines` accesses it during the static-let computation
/// before SwiftUI is on the main actor. Sendable-marked so `static let
/// shared` is concurrency-safe under Swift 6.
final class SyrmosRouteShapesStore: @unchecked Sendable {
    static let shared = SyrmosRouteShapesStore()

    private let shapes: [String: [CLLocationCoordinate2D]]

    private init() {
        self.shapes = Self.loadFromBundle()
    }

    /// Returns the OSM-stitched polyline for a line id, or nil if no shape
    /// was bundled (caller should fall back to spline-of-stations).
    func coordinates(for lineId: String) -> [CLLocationCoordinate2D]? {
        shapes[lineId]
    }

    private static func loadFromBundle() -> [String: [CLLocationCoordinate2D]] {
        guard let url = Bundle.main.url(
            forResource: "shapes",
            withExtension: "json",
            subdirectory: "seed-schedules-v2"
        ) else { return [:] }
        guard let data = try? Data(contentsOf: url),
              let payload = try? JSONDecoder().decode(Payload.self, from: data)
        else { return [:] }
        var built: [String: [CLLocationCoordinate2D]] = [:]
        for (lineId, shape) in payload.shapes {
            built[lineId] = shape.coordinates.map {
                CLLocationCoordinate2D(latitude: $0[0], longitude: $0[1])
            }
        }
        return built
    }

    struct Payload: Decodable {
        let shapes: [String: Shape]
    }

    struct Shape: Decodable {
        let coordinates: [[Double]]
    }
}
