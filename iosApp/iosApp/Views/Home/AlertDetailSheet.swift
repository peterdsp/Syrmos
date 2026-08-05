import SwiftUI

struct AlertDetailSheet: View {
    let alert: STASYAnnouncement
    let language: AppLanguage
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    severityBadge

                    Text(alert.displayTitle(language: language))
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(Color.syrmosAdaptive(
                            light: SyrmosTokens.onSurface,
                            dark: SyrmosTokens.Dark.onSurface
                        ))

                    if !alert.date.isEmpty {
                        Label(alert.date, systemImage: "calendar")
                            .font(.subheadline)
                            .foregroundStyle(Color.syrmosOnSurfaceMuted)
                    }

                    if !alert.affectedLines.isEmpty {
                        HStack(spacing: 6) {
                            ForEach(alert.affectedLines, id: \.self) { line in
                                Text(line)
                                    .font(.caption.weight(.semibold))
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(lineColor(for: line).opacity(0.15))
                                    .foregroundStyle(lineColor(for: line))
                                    .clipShape(Capsule())
                            }
                        }
                    }

                    Divider()

                    let summaryText = alert.displaySummary(language: language)
                    if !summaryText.isEmpty {
                        Text(summaryText)
                            .font(.body)
                            .foregroundStyle(Color.syrmosAdaptive(
                                light: SyrmosTokens.onSurface,
                                dark: SyrmosTokens.Dark.onSurface
                            ))
                    } else {
                        Text(noDetailLabel)
                            .font(.body)
                            .foregroundStyle(Color.syrmosOnSurfaceMuted)
                    }

                    if let url = alert.url {
                        Button {
                            openURL(url)
                        } label: {
                            Label(sourceLabel, systemImage: "arrow.up.right.square")
                        }
                        .buttonStyle(.bordered)
                        .tint(.syrmosPrimary)
                    }
                }
                .padding()
            }
            .background(Color.syrmosBackground)
            .navigationTitle(navTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(closeLabel) { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    @ViewBuilder
    private var severityBadge: some View {
        let (label, color) = severityInfo
        Text(label)
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(color.opacity(0.15))
            .foregroundStyle(color)
            .clipShape(Capsule())
    }

    private var severityInfo: (String, Color) {
        switch alert.severity {
        case "closure":
            let label: String = switch language {
            case .greek: "Κλειστο"
            case .albanian: "Mbyllur"
            case .italian: "Chiusura"
            default: "Closure"
            }
            return (label, SyrmosTokens.disruption)
        case "warning":
            let label: String = switch language {
            case .greek: "Προσοχη"
            case .albanian: "Kujdes"
            case .italian: "Avviso"
            default: "Warning"
            }
            return (label, SyrmosTokens.warning)
        default:
            let label: String = switch language {
            case .greek: "Πληροφοριες"
            case .albanian: "Informacion"
            case .italian: "Info"
            default: "Info"
            }
            return (label, SyrmosTokens.scheduled)
        }
    }

    private func lineColor(for line: String) -> Color {
        switch line.uppercased() {
        case "M1": return .metroGreen
        case "M2": return .metroRed
        case "M3": return .metroBlue
        case let l where l.hasPrefix("T"): return .tramOrange
        case let l where l.hasPrefix("A"): return .suburbanPurple
        default: return .syrmosPrimary
        }
    }

    private var navTitle: String {
        switch language {
        case .greek: "Ειδοποιηση"
        case .albanian: "Njoftim"
        case .italian: "Avviso"
        default: "Alert"
        }
    }

    private var closeLabel: String {
        switch language {
        case .greek: "Κλεισιμο"
        case .albanian: "Mbylle"
        case .italian: "Chiudi"
        default: "Close"
        }
    }

    private var sourceLabel: String {
        switch language {
        case .greek: "Δειτε στο stasy.gr"
        case .albanian: "Shiko ne stasy.gr"
        case .italian: "Vedi su stasy.gr"
        default: "View on stasy.gr"
        }
    }

    private var noDetailLabel: String {
        switch language {
        case .greek: "Δεν υπαρχουν περισσοτερες πληροφοριες."
        case .albanian: "Nuk ka informacion te metejshem."
        case .italian: "Nessun dettaglio ulteriore disponibile."
        default: "No further details available."
        }
    }
}
