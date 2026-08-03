import SwiftUI

struct OfflinePill: View {
    let message: String

    private let tint = Color(red: 0.420, green: 0.447, blue: 0.502) // #6B7280

    var body: some View {
        HStack(spacing: 8) {
            Circle()
                .fill(tint)
                .frame(width: 8, height: 8)
            Text(message)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(Capsule().fill(tint.opacity(0.14)))
    }
}
