import SwiftUI

/// Shared headline used at the top of every primary tab (Home, Lines, Map,
/// Timetables, Settings). Mirrors the bottom tab bar exactly: same
/// Capsule shape (fully rounded ends), same regularMaterial glass fill,
/// same horizontal margins from the screen edge, same shadow profile.
/// Top and bottom of every screen now look like a matched pair so the
/// UI feels symmetric and the user always knows where the chrome is.
struct CompactTabHeader: View {
    let title: String
    let subtitle: String?

    init(_ title: String, subtitle: String? = nil) {
        self.title = title
        self.subtitle = subtitle
    }

    var body: some View {
        HStack(alignment: .center, spacing: 0) {
            VStack(alignment: .leading, spacing: 1) {
                Text(title)
                    .font(.title3.weight(.bold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 22)
        .padding(.vertical, 14)
        .background(
            Capsule(style: .continuous)
                .fill(.regularMaterial)
                .shadow(color: Color.black.opacity(0.10), radius: 12, x: 0, y: 5)
        )
        .padding(.horizontal, 16)
        .padding(.top, 4)
    }
}
