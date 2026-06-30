import SwiftUI

/// Ariadne's chat sheet on iOS. Conversation list plus a single input. Mirrors
/// the Compose `AssistantScreen` so the experience matches across platforms.
struct AriadneView: View {
    @StateObject private var model = AriadneModel()
    @ObservedObject private var loc = LocalizationManager.shared
    @Environment(\.dismiss) private var dismiss
    @State private var input = ""
    @FocusState private var inputFocused: Bool

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 10) {
                            ForEach(model.messages) { message in
                                bubble(message).id(message.id)
                            }
                        }
                        .padding(16)
                    }
                    .onChange(of: model.messages.count) { _, _ in
                        if let last = model.messages.last {
                            withAnimation { proxy.scrollTo(last.id, anchor: .bottom) }
                        }
                    }
                }

                HStack(spacing: 8) {
                    TextField(placeholder, text: $input, axis: .vertical)
                        .textFieldStyle(.roundedBorder)
                        .lineLimit(1...3)
                        .focused($inputFocused)
                        .onSubmit(send)
                    Button(action: send) {
                        Image(systemName: "arrow.up.circle.fill")
                            .font(.title2)
                            .foregroundStyle(input.trimmingCharacters(in: .whitespaces).isEmpty
                                ? Color.secondary : Color.syrmosPrimary)
                    }
                    .disabled(input.trimmingCharacters(in: .whitespaces).isEmpty)
                }
                .padding(12)
            }
            .background(Color.syrmosBackground)
            .navigationTitle("Ariadne")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text("Ariadne").font(.headline)
                        Text(subtitle).font(.caption2).foregroundStyle(.secondary)
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(loc.language == .greek ? "Κλείσιμο" : loc.language == .albanian ? "Mbyll" : "Close") {
                        dismiss()
                    }
                }
            }
        }
    }

    private func send() {
        model.ask(input)
        input = ""
    }

    @ViewBuilder
    private func bubble(_ message: AriadneMessage) -> some View {
        HStack {
            if message.fromUser { Spacer(minLength: 40) }
            VStack(alignment: .leading, spacing: 6) {
                Text(message.text)
                    .font(.subheadline)
                    .foregroundStyle(message.fromUser ? Color.white : Color.primary)
                ForEach(message.departures) { dep in
                    HStack {
                        Text("\(dep.lineId) · \(dep.time)")
                            .font(.caption).fontWeight(.medium)
                        Spacer()
                        Text(dep.minutesAwayDisplay(language: loc.language))
                            .font(.caption).fontWeight(.bold)
                    }
                    .foregroundStyle(message.fromUser ? Color.white : Color.primary)
                }
            }
            .padding(12)
            .background(message.fromUser ? Color.syrmosPrimary : Color.syrmosSurface)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            if !message.fromUser { Spacer(minLength: 40) }
        }
        .frame(maxWidth: .infinity, alignment: message.fromUser ? .trailing : .leading)
    }

    private var subtitle: String {
        switch loc.language {
        case .greek: return "Οδηγός συγκοινωνιών, εκτός σύνδεσης"
        case .albanian: return "Udhëzues transporti, pa internet"
        default: return "Offline transit guide"
        }
    }

    private var placeholder: String {
        switch loc.language {
        case .greek: return "Ρώτησε για τρένα, σταθμούς, διαδρομές…"
        case .albanian: return "Pyet për trena, stacione, udhëtime…"
        default: return "Ask about trains, stations, routes…"
        }
    }
}
