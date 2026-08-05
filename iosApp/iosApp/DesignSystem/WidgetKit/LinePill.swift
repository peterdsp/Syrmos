import SwiftUI

/// The M3 / T7 / A1 line pill: a semibold label on the line's accent color.
/// Single sizing source so every widget family and the Live Activity match.
/// The base `.regular` size targets the 48 x 22 spec from the mockup.
struct LinePill: View {
    let lineId: String
    var size: Size = .regular
    var disruptionSeverity: String? = nil

    enum Size {
        case small, regular, large

        var font: Font {
            switch self {
            case .small: return .caption2
            case .regular: return .caption
            case .large: return .subheadline
            }
        }
        var hPadding: CGFloat {
            switch self {
            case .small: return 5
            case .regular: return 8
            case .large: return 10
            }
        }
        var vPadding: CGFloat {
            switch self {
            case .small: return 1
            case .regular: return 3
            case .large: return 4
            }
        }
        var minWidth: CGFloat {
            switch self {
            case .small: return 26
            case .regular: return 40
            case .large: return 48
            }
        }
        var corner: CGFloat {
            switch self {
            case .small: return 4
            case .regular: return 6
            case .large: return 7
            }
        }
    }

    var body: some View {
        Text(SyrmosLineTokens.label(for: lineId))
            .font(size.font)
            .fontWeight(.semibold)
            .foregroundStyle(.white)
            .padding(.horizontal, size.hPadding)
            .padding(.vertical, size.vPadding)
            .frame(minWidth: size.minWidth)
            .background(
                SyrmosLineTokens.color(for: lineId),
                in: RoundedRectangle(cornerRadius: size.corner, style: .continuous)
            )
            .overlay(alignment: .topTrailing) {
                LineDisruptionDot(severity: disruptionSeverity)
                    .offset(x: 3, y: -3)
            }
            // Reads in tinted StandBy and accented Lock Screen modes.
            .widgetAccentable()
    }
}

struct LineDisruptionDot: View {
    let severity: String?

    var body: some View {
        if severity == "warning" || severity == "closure" {
            Circle()
                .fill(severity == "closure" ? SyrmosTokens.disruption : SyrmosTokens.warning)
                .frame(width: 7, height: 7)
                .accessibilityHidden(true)
        }
    }
}
