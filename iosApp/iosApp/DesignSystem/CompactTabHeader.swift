import SwiftUI

/// Shared headline used at the top of every primary tab (Home, Lines, Map,
/// Timetables, Settings). Renders as a floating liquid-glass capsule
/// hugging the status bar, mirroring the bottom tab bar's aesthetic so
/// the top and bottom of every screen feel cohesive. The content area
/// behind the capsule blurs through when the user scrolls, the same way
/// content does behind the bottom bar.
struct CompactTabHeader: View {
    let title: String
    let subtitle: String?

    init(_ title: String, subtitle: String? = nil) {
        self.title = title
        self.subtitle = subtitle
    }

    var body: some View {
        HStack(alignment: .center, spacing: 0) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.title.weight(.bold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.9)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(.ultraThinMaterial)
                .shadow(color: Color.black.opacity(0.07), radius: 14, x: 0, y: 6)
        )
        .padding(.horizontal, 12)
        .padding(.top, 4)
    }
}
