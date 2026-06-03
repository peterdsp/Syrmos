import SwiftUI

struct SyrmosSettingsView: View {
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        NavigationStack {
            List {
                Section(loc[.preferences]) {
                    Picker(loc[.language], selection: $loc.language) {
                        ForEach(AppLanguage.allCases, id: \.self) { lang in
                            Text(lang.displayName).tag(lang)
                        }
                    }
                    LabeledContent(loc[.theme], value: loc[.systemDefault])
                }

                Section(loc[.data]) {
                    LabeledContent(loc[.scheduleVersion], value: "3.0")
                    LabeledContent(loc[.stations], value: "90+")
                    LabeledContent(loc[.lines], value: "9")
                }

                Section(loc[.about]) {
                    Text(loc[.aboutText])
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle(loc[.settings])
        }
    }
}
