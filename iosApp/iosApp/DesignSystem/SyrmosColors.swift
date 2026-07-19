import SwiftUI

extension Color {
    static let metroGreen = Color(hex: 0x00843D)
    static let metroRed = Color(hex: 0xDA291C)
    static let metroBlue = Color(hex: 0x0072CE)
    static let tramOrange = Color(hex: 0xE87722)
    static let suburbanPurple = Color(hex: 0x6F2DA8)

    static let syrmosPrimary = Color.metroBlue
    static let syrmosBackground = Color(uiColor: .systemGroupedBackground)
    static let syrmosSurface = Color(uiColor: .secondarySystemGroupedBackground)

    static let arrivalSoon = Color(hex: 0x2E7D32)
    static let arrivalModerate = Color(hex: 0xE65100)
    static let arrivalFar = Color.secondary

    init(hex: UInt, alpha: Double = 1.0) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: alpha
        )
    }
}

/// Verbatim Swift mirror of the shared map design tokens. Source of truth:
/// core/common/src/commonMain/kotlin/com/syrmos/core/common/map/MapDesignTokens.kt
/// (and composeApp/src/wasmJsMain/resources/web-map.js MAP_TOKENS). Keep in
/// sync — change the Kotlin object, then update this and the web copy.
enum MapDesignTokens {
    // Station dot markers.
    static let dotCountry: CGFloat = 10
    static let dotCity: CGFloat = 13
    static let dotSelected: CGFloat = 18
    static let ringWidth: CGFloat = 1.5
    static let selectedHaloWidth: CGFloat = 3
    static let innerCapRatio: CGFloat = 0.34
    static let interchangeRingRatio: CGFloat = 0.28
    static let glyphMinZoom = 14
    /// Below this zoom only the network skeleton shows (line strokes +
    /// interchange hubs + selection); the ~340 minor stops are hidden so the
    /// country isn't a field of confetti. Mirrors MapDesignTokens.MINOR_STOP_MIN_ZOOM.
    static let minorStopMinZoom: Double = 11
    /// Below this zoom only the major cross-modal hubs (~16 Greece-wide) show,
    /// because is_interchange is over-applied. Mirrors MapDesignTokens.MAJOR_HUB_MIN_ZOOM.
    static let majorHubMinZoom: Double = 9

    // Line strokes.
    static let greyedColor = "#94A3B8"
    static let busDash: [CGFloat] = [2, 7]
    static let greyedDash: [CGFloat] = [6, 8]
}
