import SwiftUI

struct HomeCustomizeSheet: View {
    @ObservedObject var store: HomeSectionStore
    @ObservedObject private var loc = LocalizationManager.shared
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(store.entries) { entry in
                        HStack(spacing: 12) {
                            Image(systemName: entry.section.iconName)
                                .foregroundStyle(entry.isVisible ? entry.section.iconColor : .secondary)
                                .frame(width: 24)
                            Text(entry.section.displayName(language: loc.language))
                                .foregroundStyle(entry.isVisible ? .primary : .secondary)
                            Spacer()
                            Button {
                                withAnimation { store.toggle(entry.section) }
                            } label: {
                                Image(systemName: entry.isVisible ? "eye.fill" : "eye.slash")
                                    .foregroundStyle(entry.isVisible ? .blue : .secondary)
                                    .frame(width: 28)
                            }
                            .buttonStyle(.plain)
                        }
                        .padding(.vertical, 2)
                    }
                    .onMove { store.move(from: $0, to: $1) }
                } header: {
                    Text(sectionHeader)
                } footer: {
                    Text(sectionFooter)
                }
            }
            .environment(\.editMode, .constant(.active))
            .scrollContentBackground(.hidden)
            .background(Color.syrmosBackground)
            .navigationTitle(titleLabel)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(resetLabel) {
                        withAnimation { store.reset() }
                    }
                    .font(.subheadline)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(doneLabel) { dismiss() }
                        .fontWeight(.semibold)
                }
            }
        }
    }

    private var titleLabel: String {
        switch loc.language {
        case .greek: return "Προσαρμογη Αρχικης"
        case .albanian: return "Personalizo Ballinen"
        case .english: return "Customize Home"
        }
    }

    private var doneLabel: String {
        switch loc.language {
        case .greek: return "Τελος"
        case .albanian: return "Mbylle"
        case .english: return "Done"
        }
    }

    private var resetLabel: String {
        switch loc.language {
        case .greek: return "Επαναφορα"
        case .albanian: return "Rivendos"
        case .english: return "Reset"
        }
    }

    private var sectionHeader: String {
        switch loc.language {
        case .greek: return "Ενοτητες"
        case .albanian: return "Seksionet"
        case .english: return "Sections"
        }
    }

    private var sectionFooter: String {
        switch loc.language {
        case .greek: return "Συρετε για αλλαγη σειρας. Πατηστε το εικονιδιο ματιου για εμφανιση η αποκρυψη."
        case .albanian: return "Terheq per te ndryshuar rradhen. Shtyp ikonon e syrit per te shfaqur ose fshehur."
        case .english: return "Drag to reorder. Tap the eye icon to show or hide."
        }
    }
}
