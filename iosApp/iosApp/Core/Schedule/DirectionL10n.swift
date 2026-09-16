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
    static func localized(lineId: String, direction: String, language: AppLanguage) -> String {
        let folded = StationGrouping.fold(direction)
        if folded.isEmpty { return direction }
        let stations = SyrmosData.stations(for: lineId)
        if let st = stations.first(where: {
            StationGrouping.fold($0.name) == folded || StationGrouping.fold($0.nameEl) == folded
        }) {
            return language == .greek && !st.nameEl.isEmpty ? st.nameEl : st.name
        }
        return direction
    }
}
