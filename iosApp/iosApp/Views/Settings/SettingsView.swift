import SwiftUI

struct SyrmosSettingsView: View {
    var body: some View {
        NavigationStack {
            List {
                Section("Preferences") {
                    LabeledContent("Language", value: "English")
                    LabeledContent("Theme", value: "System")
                }

                Section("Data") {
                    LabeledContent("Schedule version", value: "3.0")
                    LabeledContent("Stations", value: "90+")
                    LabeledContent("Lines", value: "8")
                }

                Section("About") {
                    Text("Schedule data from STASY and Hellenic Train official timetables. This app is not affiliated with STASY, Hellenic Train or OASA.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Settings")
        }
    }
}
