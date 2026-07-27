import SwiftUI

/// Single source of truth for line colors and display labels across the app,
/// the widget extension, and the Live Activity. Derives all colors from the
/// canonical SyrmosTokens (generated from the KMP design token module) so they
/// stay in sync across all platforms.
enum SyrmosLineTokens {
    /// Accent color for a line id. Any M3 variant (including the M3_AIR airport
    /// branch) maps to metro blue; A1 to A4 share the suburban purple.
    static func color(for lineId: String) -> Color {
        switch normalize(lineId) {
        case "M1": return SyrmosTokens.metroGreen
        case "M2": return SyrmosTokens.metroRed
        case "M3": return SyrmosTokens.metroBlue
        case "T6", "T7": return SyrmosTokens.tram
        default: return SyrmosTokens.suburban
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
