import Foundation

/// Resolves a departure's direction label (a terminal/destination name that the
/// schedule data supplies in one language, e.g. "Anthoupoli") to the rider's
/// language using the existing localized station data (finding 10). So Greek Home
/// shows "προς Ανθούπολη", not "προς Anthoupoli", by identity, not a string swap.
///
/// The direction is matched to a real stop on its own line by folded name (either
/// language), then its localized name is returned. A direction that maps to no
/// known stop stays exactly as given: an honest fallback, never an invented
/// translation.
enum DirectionL10n {
    // Home re-renders resolve the same few (line, direction) pairs repeatedly, and
    // `stations(for:)` rebuilds a line's whole station array on each call. The
    // station data is static, so memoize the resolved result. Guarded by a lock
    // because the accessor is not actor-isolated.
    private static let lock = NSLock()
    // Manually synchronized by `lock`, so opt out of Swift 6 global-mutable-state
    // isolation checking (nonisolated(unsafe)); every access below holds the lock.
    nonisolated(unsafe) private static var cache: [String: String] = [:]

    static func localized(lineId: String, direction: String, language: AppLanguage) -> String {
        let folded = StationGrouping.fold(direction)
        if folded.isEmpty { return direction }
        let key = "\(lineId)|\(language.rawValue)|\(folded)"
        lock.lock()
        defer { lock.unlock() }
        if let hit = cache[key] { return hit }
        let resolved = SyrmosData.stations(for: lineId).first {
            StationGrouping.fold($0.name) == folded || StationGrouping.fold($0.nameEl) == folded
        }
        let result = resolved.map { language == .greek && !$0.nameEl.isEmpty ? $0.nameEl : $0.name } ?? direction
        cache[key] = result
        return result
    }
}
