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
