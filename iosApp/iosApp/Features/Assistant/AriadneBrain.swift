import Foundation
#if canImport(FoundationModels)
import FoundationModels
#endif

/// Optional on-device LLM front-end for Ariadne, using Apple Foundation Models
/// on Apple Intelligence devices (iOS 26+).
///
/// By design it does NOT pick intents or invent transit facts. It only rewrites
/// messy natural language ("trains aprt sat nite") into clean text that the
/// deterministic `AthensTransitParser` then classifies. The rule parser stays
/// the single source of truth; the model just improves recall on fuzzy input.
/// When the model is unavailable (older devices, no Apple Intelligence, the
/// Simulator), Ariadne uses the rule parser directly with zero behaviour change.
enum AriadneBrain {
    /// Why the on-device "Clever mode" model is or isn't usable, surfaced in
    /// Settings so the user understands which engine answers their questions.
    enum Availability: Equatable {
        /// Apple Foundation Models are ready; Ariadne runs in Clever mode.
        case available
        /// The device supports Apple Intelligence but the user hasn't turned it
        /// on (Settings -> Apple Intelligence & Siri).
        case appleIntelligenceNotEnabled
        /// The model is still downloading; retry shortly.
        case modelNotReady
        /// Hardware can't run Apple Intelligence (pre-15 Pro iPhone, non-M iPad).
        case deviceNotEligible
        /// The OS predates Apple Intelligence (below iOS 26) or the framework
        /// isn't present in this build (Simulator without the models).
        case osTooOld

        /// True only when the smart engine is live.
        var isClever: Bool { self == .available }
    }

    /// Resolves the current model availability, mapping Apple's reason cases
    /// explicitly so Settings can show an actionable string.
    static var availability: Availability {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            switch SystemLanguageModel.default.availability {
            case .available:
                return .available
            case .unavailable(.appleIntelligenceNotEnabled):
                return .appleIntelligenceNotEnabled
            case .unavailable(.modelNotReady):
                return .modelNotReady
            case .unavailable(.deviceNotEligible):
                return .deviceNotEligible
            case .unavailable:
                // Any future reason we don't map yet: treat as ineligible so we
                // fall back to the rule parser rather than promising Clever mode.
                return .deviceNotEligible
            }
        }
        #endif
        return .osTooOld
    }

    /// True when an on-device model is ready to use.
    static var isAvailable: Bool { availability == .available }

    /// Rewrites [input] into a clean query, or nil to fall back to raw text.
    static func normalize(_ input: String) async -> String? {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            guard case .available = SystemLanguageModel.default.availability else { return nil }
            let instructions = Instructions("""
            You normalize short Athens public-transport queries for a rule
            parser. Rewrite the user's text into ONE clear English line about
            Athens metro, tram or suburban trains.

            Rules:
            - Fix typos and grammar ("how many minute to airprot" ->
              "how many minutes to the airport").
            - Transliterate Greeklish and Greek station names to their canonical
              English spelling: "Sintagma"/"Σύνταγμα" -> "Syntagma",
              "Pireas"/"Πειραιάς" -> "Piraeus", "Nikea"/"Νίκαια" -> "Nikaia",
              "Monastiraki"/"Μοναστηράκι" -> "Monastiraki".
            - Keep line names as ids: M1, M2, M3, T6, T7, A1-A4.
            - Preserve the user's intent words (how long, when, last train,
              ticket, directions); do not answer, only rewrite.
            - Output ONLY the rewritten query, no quotes, no explanation.
            - If it is not about Athens public transport, output the text
              unchanged.
            """)
            do {
                let session = LanguageModelSession(instructions: instructions)
                let response = try await session.respond(to: input)
                let text = response.content.trimmingCharacters(in: .whitespacesAndNewlines)
                return text.isEmpty ? nil : text
            } catch {
                return nil
            }
        }
        #endif
        return nil
    }
}
