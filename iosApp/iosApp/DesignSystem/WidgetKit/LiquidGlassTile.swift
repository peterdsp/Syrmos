import SwiftUI
import WidgetKit

/// The Liquid Glass base surface for widgets: rounded rect + material + a
/// faint accent-tint overlay, so tiles read on both the dark home screen and
/// the always-on Lock Screen (dark-first, per the widget philosophy).
///
/// Two entry points because WidgetKit's `containerBackground` only takes effect
/// at a widget's *root* content, while nested tiles inside a large / XL family
/// need an ordinary background:
///  - `View.syrmosWidgetContainer(accent:)` — apply at a widget family root.
///  - `LiquidGlassTile { ... }` — an inner card for composed large / XL layouts.
struct LiquidGlassTile<Content: View>: View {
    var accent: Color = .clear
    var cornerRadius: CGFloat = 16
    @ViewBuilder var content: () -> Content

    var body: some View {
        content()
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(accent.opacity(0.10))
            )
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .strokeBorder(accent.opacity(0.25), lineWidth: 0.75)
            )
    }
}

extension View {
    /// Root container background for a widget family. Uses `.regularMaterial`
    /// so Liquid Glass reads on iOS 18+, falling back to `.fill.tertiary` on
    /// iOS 17, with a line-accent wash on top.
    @ViewBuilder
    func syrmosWidgetContainer(accent: Color) -> some View {
        if #available(iOS 18.0, *) {
            self.containerBackground(for: .widget) {
                Rectangle()
                    .fill(.regularMaterial)
                    .overlay(accent.opacity(0.12))
            }
        } else {
            self.containerBackground(for: .widget) {
                Rectangle()
                    .fill(.fill.tertiary)
                    .overlay(accent.opacity(0.12))
            }
        }
    }
}
