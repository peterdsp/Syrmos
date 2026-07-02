import SwiftUI

/// Ariadne's chat sheet on iOS. Aims for Apple Design Award polish:
/// gradient backdrop, owl avatar on assistant bubbles, spring-in message
/// insert, and a subtitle-styled navigation title so the "offline transit
/// guide" line reads as branding rather than a truncated toolbar chip.
struct AriadneView: View {
    @StateObject private var model = AriadneModel()
    @ObservedObject private var loc = LocalizationManager.shared
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme
    @State private var input = ""
    @FocusState private var inputFocused: Bool

    var body: some View {
        NavigationStack {
            ZStack {
                backdrop.ignoresSafeArea()

                VStack(spacing: 0) {
                    header
                        .padding(.horizontal, 20)
                        .padding(.top, 12)
                        .padding(.bottom, 8)

                    ScrollViewReader { proxy in
                        ScrollView {
                            LazyVStack(alignment: .leading, spacing: 12) {
                                ForEach(model.messages) { message in
                                    bubble(message)
                                        .id(message.id)
                                        .transition(.asymmetric(
                                            insertion: .move(edge: .bottom)
                                                .combined(with: .opacity),
                                            removal: .opacity
                                        ))
                                }
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 12)
                        }
                        .animation(.spring(response: 0.4, dampingFraction: 0.82), value: model.messages.count)
                        .onChange(of: model.messages.count) { _, _ in
                            if let last = model.messages.last {
                                withAnimation(.spring(response: 0.35, dampingFraction: 0.9)) {
                                    proxy.scrollTo(last.id, anchor: .bottom)
                                }
                            }
                        }
                    }

                    composer
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(closeLabel) { dismiss() }
                        .fontWeight(.semibold)
                }
            }
        }
        .presentationDetents([.large])
        .presentationCornerRadius(28)
        .presentationDragIndicator(.visible)
    }

    private var backdrop: some View {
        LinearGradient(
            colors: colorScheme == .dark
                ? [Color(red: 0.05, green: 0.07, blue: 0.12), Color(red: 0.02, green: 0.03, blue: 0.06)]
                : [Color(red: 0.94, green: 0.97, blue: 1.0), Color(red: 0.99, green: 0.98, blue: 0.94)],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    private var header: some View {
        HStack(spacing: 12) {
            Text("🦉")
                .font(.system(size: 28))
                .frame(width: 44, height: 44)
                .background(
                    Circle().fill(Color.syrmosPrimary.opacity(0.14))
                )
            VStack(alignment: .leading, spacing: 2) {
                Text("Ariadne")
                    .font(.title3).fontWeight(.bold)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
        }
    }

    private var composer: some View {
        HStack(spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "message.fill")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                TextField(placeholder, text: $input, axis: .vertical)
                    .lineLimit(1...4)
                    .focused($inputFocused)
                    .onSubmit(send)
                    .textFieldStyle(.plain)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(.ultraThinMaterial, in: Capsule())

            Button(action: send) {
                Image(systemName: "arrow.up")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 40, height: 40)
                    .background(
                        Circle().fill(
                            input.trimmingCharacters(in: .whitespaces).isEmpty
                                ? Color.gray.opacity(0.4)
                                : Color.syrmosPrimary
                        )
                    )
            }
            .disabled(input.trimmingCharacters(in: .whitespaces).isEmpty)
            .animation(.easeInOut(duration: 0.15), value: input.isEmpty)
        }
    }

    private func send() {
        model.ask(input)
        input = ""
    }

    @ViewBuilder
    private func bubble(_ message: AriadneMessage) -> some View {
        HStack(alignment: .top, spacing: 8) {
            if message.fromUser {
                Spacer(minLength: 40)
            } else {
                Text("🦉")
                    .font(.system(size: 14))
                    .frame(width: 28, height: 28)
                    .background(Circle().fill(Color.syrmosPrimary.opacity(0.12)))
            }

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
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(
                Group {
                    if message.fromUser {
                        Color.syrmosPrimary
                    } else {
                        Color.clear.background(.ultraThinMaterial)
                    }
                }
            )
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .shadow(color: .black.opacity(message.fromUser ? 0.10 : 0.04), radius: 6, y: 2)

            if !message.fromUser { Spacer(minLength: 40) }
        }
        .frame(maxWidth: .infinity, alignment: message.fromUser ? .trailing : .leading)
    }

    private var closeLabel: String {
        switch loc.language {
        case .greek: return "Κλείσιμο"
        case .albanian: return "Mbyll"
        default: return "Close"
        }
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
