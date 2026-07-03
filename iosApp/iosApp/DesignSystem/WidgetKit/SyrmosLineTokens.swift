import SwiftUI

/// Single source of truth for line colors and display labels across the app,
/// the widget extension, and the Live Activity. Replaces the `lineColor` /
/// `accent` switches that were duplicated in `NextDeparturesWidget` and
/// `SyrmosLiveActivity`. Compiled into both the app and the widget target.
///
/// Color is information, not decoration (see the widget philosophy in
/// docs/PRODUCT_PRINCIPLES.md): every widget surface derives its accent from
/// the tracked line through these tokens.
enum SyrmosLineTokens {
    /// Accent color for a line id. Any M3 variant (including the M3_AIR airport
    /// branch) maps to metro blue; A1 to A4 share the suburban purple.
    static func color(for lineId: String) -> Color {
        switch normalize(lineId) {
        case "M1": return Color(red: 0.19, green: 0.62, blue: 0.31)       // metro green
        case "M2": return Color(red: 0.85, green: 0.20, blue: 0.20)       // metro red
        case "M3": return Color(red: 0.10, green: 0.36, blue: 0.72)       // metro blue
        case "T6", "T7": return Color(red: 0.95, green: 0.55, blue: 0.11) // tram orange
        default: return Color(red: 0.42, green: 0.30, blue: 0.66)         // suburban purple
        }
    }

    /// Short pill label. Collapses M3 airport variants to "M3".
    static func label(for lineId: String) -> String { normalize(lineId) }

    /// Human network name for the line, used in status rows.
    static func networkName(for lineId: String) -> String {
        switch normalize(lineId) {
        case "M1", "M2", "M3": return "Metro"
        case "T6", "T7": return "Tram"
        default: return "Suburban"
        }
    }

    /// Canonical order for the All-Lines-Status widget (seven pills).
    static let allLines: [String] = ["M1", "M2", "M3", "T6", "T7", "A1", "A2"]

    /// Normalizes any M3 variant to "M3"; leaves everything else untouched.
    static func normalize(_ id: String) -> String {
        id.hasPrefix("M3") ? "M3" : id
    }
}
