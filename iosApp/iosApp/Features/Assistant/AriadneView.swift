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
                                                .combined(with: .opacity)
                                                .combined(with: .scale(scale: 0.92, anchor: .bottomLeading)),
                                            removal: .opacity
                                        ))
                                }
                                if model.thinking {
                                    TypingIndicator()
                                        .id("typing")
                                        .transition(.opacity.combined(with: .scale(scale: 0.9)))
                                }
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 12)
                        }
                        .animation(.spring(response: 0.4, dampingFraction: 0.82), value: model.messages.count)
                        .animation(.easeInOut(duration: 0.2), value: model.thinking)
                        .onChange(of: model.messages.count) { _, _ in
                            if let last = model.messages.last {
                                withAnimation(.spring(response: 0.35, dampingFraction: 0.9)) {
                                    proxy.scrollTo(last.id, anchor: .bottom)
                                }
                            }
                        }
                    }

                    // Suggestion chips: shown while the conversation is
                    // empty so a first-time user has one-tap ways to try
                    // Ariadne. Hidden once they start typing or asking.
                    if model.messages.count <= 1 && !model.thinking {
                        suggestedPrompts
                            .padding(.horizontal, 16)
                            .padding(.bottom, 6)
                            .transition(.opacity.combined(with: .move(edge: .bottom)))
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

    private var suggestedPrompts: some View {
        let prompts = suggestions
        return VStack(alignment: .leading, spacing: 8) {
            Text(tryLabel)
                .font(.caption2).fontWeight(.semibold)
                .foregroundStyle(.secondary)
            HStack(spacing: 8) {
                ForEach(prompts, id: \.self) { text in
                    Button {
                        input = text
                        send()
                    } label: {
                        Text(text)
                            .font(.caption).fontWeight(.medium)
                            .padding(.horizontal, 12).padding(.vertical, 8)
                            .background(
                                Capsule().fill(Color.syrmosPrimary.opacity(0.15))
                            )
                            .overlay(
                                Capsule().stroke(Color.syrmosPrimary.opacity(0.3), lineWidth: 0.6)
                            )
                            .foregroundStyle(Color.syrmosPrimary)
                            .lineLimit(1)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    /// Localised quick-tap prompts, chosen by context. Rules, cheap to
    /// evaluate, no external calls:
    ///   - if the current weather condition is severe, lead with a
    ///     covered-route trip prompt
    ///   - after 20:00, offer the last-train prompt for M2 (the network's
    ///     latest-running line)
    ///   - before 09:00, lead with an airport check for early flights
    ///   - otherwise the default triplet
    private var suggestions: [String] {
        let severe = WeatherStore.shared.snapshot?.current.condition.isSevere == true
        let hour = Calendar.current.component(.hour, from: Date())

        switch loc.language {
        case .greek:
            if severe { return ["Πώς πάω στο σπίτι υπόγεια;", "Κακοκαιρία τώρα", "Ειδοποιήσεις γραμμών"] }
            if hour >= 20 { return ["Τελευταίο M2", "Καιρός τώρα", "Πώς πάω στο σπίτι;"] }
            if hour < 9 { return ["Επόμενο M3 στο Αεροδρόμιο", "Καιρός τώρα", "Πώς πάω στο κέντρο;"] }
            return ["Καιρός τώρα", "Πώς πάω στο Αεροδρόμιο;", "Τελευταίο M2"]
        case .albanian:
            if severe { return ["Si shkoj në shtëpi nën tokë?", "Mot i keq tani", "Njoftime linjash"] }
            if hour >= 20 { return ["Treni i fundit M2", "Moti tani", "Si shkoj në shtëpi?"] }
            if hour < 9 { return ["M3 tjetër për Aeroport", "Moti tani", "Si shkoj në qendër?"] }
            return ["Moti tani", "Si shkoj në Aeroport?", "Treni i fundit M2"]
        case .italian:
            if severe { return ["Come torno a casa al coperto?", "Maltempo adesso", "Avvisi linee"] }
            if hour >= 20 { return ["Ultimo M2", "Meteo adesso", "Come torno a casa?"] }
            if hour < 9 { return ["Prossimo M3 per Aeroporto", "Meteo adesso", "Come arrivo in centro?"] }
            return ["Meteo adesso", "Come arrivo all'Aeroporto?", "Ultimo M2"]
        case .english:
            if severe { return ["How do I get home covered?", "Severe weather now", "Line alerts"] }
            if hour >= 20 { return ["Last M2", "Weather now", "How do I get home?"] }
            if hour < 9 { return ["Next M3 to Airport", "Weather now", "How do I get downtown?"] }
            return ["Weather now", "How do I get to the Airport?", "Last M2"]
        }
    }

    private var tryLabel: String {
        switch loc.language {
        case .greek: return "ΔΟΚΙΜΑΣΕ"
        case .albanian: return "PROVO"
        case .italian: return "PROVA"
        case .english: return "TRY"
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
                if !message.fromUser && message.sourceConfidence != .unknown {
                    SourceConfidenceChip(confidence: message.sourceConfidence, language: loc.language)
                }
                if let action = message.action, let label = message.actionLabel {
                    Button {
                        switch action {
                        case .openStation(let id):
                            DeepLinkRouter.shared.pending = .station(id: id)
                            dismiss()
                        case .openLine(let id):
                            DeepLinkRouter.shared.pending = .line(id: id)
                            dismiss()
                        }
                    } label: {
                        Text("\(label) \u{203A}")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Color.syrmosPrimary)
                    }
                    .buttonStyle(.plain)
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
        case .italian: return "Chiudi"
        default: return "Close"
        }
    }

    private var subtitle: String {
        switch loc.language {
        case .greek: return "Ο έξυπνος οδηγός συγκοινωνιών σου"
        case .albanian: return "Udhëzuesi yt i zgjuar i transportit"
        case .italian: return "La tua guida intelligente ai trasporti"
        default: return "Your smart transit guide"
        }
    }

    private var placeholder: String {
        switch loc.language {
        case .greek: return "Ρώτησε για τρένα, σταθμούς, διαδρομές…"
        case .albanian: return "Pyet për trena, stacione, udhëtime…"
        case .italian: return "Chiedi di treni, stazioni, percorsi…"
        default: return "Ask about trains, stations, routes…"
        }
    }
}

/// Three-dot typing indicator with a wave animation. Reads as "Ariadne
/// is thinking" without needing copy, and stops the empty space that
/// used to sit under the greeting while the parser worked.
struct TypingIndicator: View {
    @State private var phase: CGFloat = 0

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Text("🦉")
                .font(.system(size: 14))
                .frame(width: 28, height: 28)
                .background(Circle().fill(Color.syrmosPrimary.opacity(0.12)))
            HStack(spacing: 4) {
                dot(offset: 0)
                dot(offset: 0.15)
                dot(offset: 0.3)
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .background(
                Color.clear.background(.ultraThinMaterial)
            )
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .shadow(color: .black.opacity(0.04), radius: 6, y: 2)
            Spacer(minLength: 40)
        }
        .onAppear {
            withAnimation(.easeInOut(duration: 1.05).repeatForever(autoreverses: false)) {
                phase = 1
            }
        }
    }

    private func dot(offset: CGFloat) -> some View {
        Circle()
            .fill(Color.syrmosPrimary.opacity(0.7))
            .frame(width: 6, height: 6)
            .scaleEffect(1 + 0.35 * abs(sin(.pi * (phase - offset))))
    }
}

/// On-demand "Download Ariadne's brain" banner. Shown only until the on-device
/// model is ready; the rule parser answers throughout, so this is purely
/// additive. Bound to the shared AriadneModelStore.
private struct AriadneModelBanner: View {
    @ObservedObject private var store = AriadneModelStore.shared
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        switch store.status {
        case .ready:
            EmptyView()
        case .downloading(let p):
            card {
                HStack(spacing: 14) {
                    ZStack {
                        Circle()
                            .stroke(Color.accentColor.opacity(0.15), lineWidth: 4)
                        Circle()
                            .trim(from: 0, to: CGFloat(p))
                            .stroke(Color.accentColor, style: StrokeStyle(lineWidth: 4, lineCap: .round))
                            .rotationEffect(.degrees(-90))
                            .animation(.easeInOut(duration: 0.4), value: p)
                        Text("\(Int(p * 100))%")
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                            .foregroundStyle(Color.accentColor)
                    }
                    .frame(width: 44, height: 44)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(downloadingText(Int(p * 100)))
                            .font(.caption.weight(.medium))
                        Text(downloadSubtext(p))
                            .font(.caption2)
                            .foregroundStyle(.tertiary)
                    }
                }
            }
        case .error:
            card {
                Text(errorText).font(.caption).foregroundStyle(.secondary)
                Button(retryText) { Task { await store.download() } }
                    .font(.caption.weight(.semibold))
            }
        case .notDownloaded:
            card {
                Text(title).font(.subheadline.weight(.bold))
                Text(offerText).font(.caption).foregroundStyle(.secondary)
                Button(downloadText) { Task { await store.download() } }
                    .font(.caption.weight(.semibold))
            }
        }
    }

    @ViewBuilder private func card<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) { content() }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
            .onAppear { store.refreshReadyState() }
    }

    private var title: String {
        switch loc.language {
        case .greek: return "Πιο έξυπνη Αριάδνη"
        case .albanian: return "Ariadne më e zgjuar"
        case .italian: return "Ariadne più intelligente"
        case .english: return "Smarter Ariadne"
        }
    }
    private var offerText: String {
        switch loc.language {
        case .greek: return "Κατέβασε ένα AI στη συσκευή (~1.1 GB, μία φορά) για πιο ελεύθερη διατύπωση. Λειτουργεί offline μετά."
        case .albanian: return "Shkarko një AI në pajisje (~1.1 GB, një herë) për fjalë më të lira. Punon offline më pas."
        case .italian: return "Scarica un AI sul dispositivo (~1.1 GB, una volta) per un linguaggio più libero. Funziona offline dopo."
        case .english: return "Download an on-device AI (~1.1 GB, one time) so Ariadne understands freer wording. Works offline after."
        }
    }
    private func downloadingText(_ pct: Int) -> String {
        switch loc.language {
        case .greek: return "Ληψη μοντελου AI..."
        case .albanian: return "Po shkarkohet modeli AI..."
        case .italian: return "Download del modello AI..."
        case .english: return "Downloading AI model..."
        }
    }
    private func downloadSubtext(_ p: Double) -> String {
        let downloaded = String(format: "%.0f", p * 1100)
        switch loc.language {
        case .greek: return "\(downloaded) / 1100 MB"
        case .albanian: return "\(downloaded) / 1100 MB"
        case .italian: return "\(downloaded) / 1100 MB"
        case .english: return "\(downloaded) / 1100 MB"
        }
    }
    private var errorText: String {
        switch loc.language {
        case .greek: return "Η λήψη απέτυχε. Ο κανόνας-parser συνεχίζει να απαντά."
        case .albanian: return "Shkarkimi dështoi. Rregull-parser vazhdon të përgjigjet."
        case .italian: return "Download fallito. Il parser delle regole continua a rispondere."
        case .english: return "Download failed. The rule parser still answers."
        }
    }
    private var downloadText: String {
        switch loc.language {
        case .greek: return "Λήψη (~1.1 GB)"
        case .albanian: return "Shkarko (~1.1 GB)"
        case .italian: return "Scarica (~1.1 GB)"
        case .english: return "Download (~1.1 GB)"
        }
    }
    private var retryText: String {
        switch loc.language {
        case .greek: return "Δοκίμασε ξανά"
        case .albanian: return "Provo sërish"
        case .italian: return "Riprova"
        case .english: return "Try again"
        }
    }
}
