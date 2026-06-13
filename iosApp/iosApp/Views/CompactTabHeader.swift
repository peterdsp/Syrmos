import SwiftUI

/// Shared headline used at the top of every primary tab (Home, Lines, Map,
/// Timetables, Settings) so the title hugs the status bar instead of
/// floating in the huge empty band Apple's default large navigation title
/// renders. Hide the nav bar at the same time so this is the only title
/// chrome the user sees.
struct CompactTabHeader: View {
    let title: String
    let subtitle: String?

    init(_ title: String, subtitle: String? = nil) {
        self.title = title
        self.subtitle = subtitle
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.largeTitle)
                .fontWeight(.bold)
            if let subtitle, !subtitle.isEmpty {
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal)
        .padding(.top, 8)
    }
}
