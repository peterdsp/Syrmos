import SwiftUI

extension Color {
    static let metroGreen = Color(hex: 0x00843D)
    static let metroRed = Color(hex: 0xDA291C)
    static let metroBlue = Color(hex: 0x0072CE)
    static let tramOrange = Color(hex: 0xE87722)
    static let suburbanPurple = Color(hex: 0x6F2DA8)

    // The 2.0 light-first identity (task T5), drawn from the generated
    // SyrmosTokens. Adaptive so dark mode still resolves to the graphite variant.
    static let syrmosPrimary = Color.syrmosAdaptive(light: SyrmosTokens.brand, dark: SyrmosTokens.Dark.brand)
    static let syrmosBackground = Color.syrmosAdaptive(light: SyrmosTokens.surface, dark: SyrmosTokens.Dark.surface)
    static let syrmosSurface = Color.syrmosAdaptive(light: SyrmosTokens.surfaceCard, dark: SyrmosTokens.Dark.surfaceCard)

    static let arrivalSoon = Color(hex: 0x2E7D32)
    static let arrivalModerate = Color(hex: 0xE65100)
    static let arrivalFar = Color.secondary

    /// A colour that resolves to [light] in light mode and [dark] in dark mode,
    /// so token-driven surfaces keep automatic dark-mode support.
    static func syrmosAdaptive(light: Color, dark: Color) -> Color {
        Color(uiColor: UIColor { trait in
            UIColor(trait.userInterfaceStyle == .dark ? dark : light)
        })
    }

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
/// sync. Change the Kotlin object, then update this and the web copy.
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
    static let minorStopMinZoom: Double = 10
    /// Below this zoom only the major cross-modal hubs (~16 Greece-wide) show,
    /// because is_interchange is over-applied. Mirrors MapDesignTokens.MAJOR_HUB_MIN_ZOOM.
    static let majorHubMinZoom: Double = 8
    /// At or below this zoom (whole-Greece) no station dots draw; the line
    /// network alone carries the map. Mirrors MapDesignTokens.LINES_ONLY_MAX_ZOOM.
    static let linesOnlyMaxZoom: Double = 7

    // Line strokes.
    static let greyedColor = "#94A3B8"
    static let busDash: [CGFloat] = [2, 7]
    static let greyedDash: [CGFloat] = [6, 8]
}

// MARK: - Generated design tokens (T3) BEGIN - do not edit, run ops/designsystem/generate_tokens.py
// Canonical source: core/designsystem/.../theme/tokens/*.kt.
enum SyrmosTokens {
    // Colors (light).
    static let brand = Color(hex: 0x1466B8)
    static let brandStrong = Color(hex: 0x0F4E8C)
    static let surface = Color(hex: 0xF7F5F1)
    static let surfaceMuted = Color(hex: 0xEFEBE4)
    static let surfaceCard = Color(hex: 0xFFFFFF)
    static let onSurface = Color(hex: 0x14181F)
    static let onSurfaceMuted = Color(hex: 0x5B636E)
    static let outline = Color(hex: 0xE0DACF)
    static let metroGreen = Color(hex: 0x00843D)
    static let metroRed = Color(hex: 0xDA291C)
    static let metroBlue = Color(hex: 0x0072CE)
    static let tram = Color(hex: 0xF39800)
    static let suburban = Color(hex: 0x6F2DA8)
    static let national = Color(hex: 0x2A5C8A)
    static let scenic = Color(hex: 0xB8860B)
    static let bus = Color(hex: 0xB45309)
    static let live = Color(hex: 0x059669)
    static let scheduled = Color(hex: 0x2563EB)
    static let offline = Color(hex: 0x6B7280)
    static let estimated = Color(hex: 0xB45309)
    static let warning = Color(hex: 0xD97706)
    static let disruption = Color(hex: 0xDC2626)
    static let arrivalSoon = Color(hex: 0x2E7D32)
    static let arrivalModerate = Color(hex: 0xE65100)
    static let arrivalFar = Color(hex: 0x757575)
    enum Dark {
        static let brand = Color(hex: 0x8ECAFF)
        static let surface = Color(hex: 0x0F1216)
        static let surfaceMuted = Color(hex: 0x171B21)
        static let surfaceCard = Color(hex: 0x1B2028)
        static let onSurface = Color(hex: 0xE6ECF5)
        static let onSurfaceMuted = Color(hex: 0x9AA3AF)
        static let outline = Color(hex: 0x2A2F37)
    }
    enum Space {
        static let none: CGFloat = 0
        static let xxs: CGFloat = 2
        static let xs: CGFloat = 4
        static let sm: CGFloat = 8
        static let md: CGFloat = 12
        static let lg: CGFloat = 16
        static let xl: CGFloat = 24
        static let xxl: CGFloat = 32
        static let xxxl: CGFloat = 48
        static let huge: CGFloat = 64
        static let minTouchTarget: CGFloat = 44
    }
    enum Radius {
        static let sm: CGFloat = 8
        static let md: CGFloat = 12
        static let lg: CGFloat = 16
        static let xl: CGFloat = 24
        static let pill: CGFloat = 999
    }
    enum Motion {
        static let durationFastMs = 150
        static let durationMediumMs = 300
        static let durationSlowMs = 450
        static let glideX1: Double = 0.16
        static let glideY1: Double = 1.0
        static let glideX2: Double = 0.3
        static let glideY2: Double = 1.0
    }
    enum Font {
        static let displayNowSize: CGFloat = 44
        static let displayNowLine: CGFloat = 48
        static let headlineSize: CGFloat = 22
        static let headlineLine: CGFloat = 28
        static let titleSize: CGFloat = 17
        static let titleLine: CGFloat = 22
        static let bodySize: CGFloat = 15
        static let bodyLine: CGFloat = 20
        static let labelSize: CGFloat = 13
        static let labelLine: CGFloat = 16
        static let captionSize: CGFloat = 11
        static let captionLine: CGFloat = 14
        static let clockSize: CGFloat = 15
        static let clockLine: CGFloat = 18
    }
}
// MARK: - Generated design tokens (T3) END
