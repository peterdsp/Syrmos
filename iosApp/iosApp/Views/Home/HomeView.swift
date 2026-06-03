import SwiftUI

struct HomeView: View {
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    welcomeSection
                    emptyStateSection
                }
                .padding()
            }
            .background(Color.syrmosBackground)
            .navigationTitle("Syrmos")
        }
    }

    private var welcomeSection: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Live Athens rail times")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var emptyStateSection: some View {
        ContentUnavailableView(
            "Select a station",
            systemImage: "tram",
            description: Text("Browse the Lines tab or enable GPS to find nearby stations and see departure times.")
        )
        .padding(.top, 40)
    }
}
