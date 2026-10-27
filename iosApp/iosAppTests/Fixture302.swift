import Foundation

/// Loads a repo-root fixture (fixtures/...) for the 3.0.2 shared-rule tests. This
/// file lives at iosApp/iosAppTests/, so the repo root is three levels up.
enum Fixture302 {
    static func data(_ relativePath: String, file: String = #filePath) throws -> Data {
        let url = URL(fileURLWithPath: file)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        return try Data(contentsOf: url)
    }

    static func decode<T: Decodable>(_ type: T.Type, _ relativePath: String) throws -> T {
        try JSONDecoder().decode(T.self, from: data(relativePath))
    }
}
