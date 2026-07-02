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
    /// True when an on-device model is ready to use.
    static var isAvailable: Bool {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            if case .available = SystemLanguageModel.default.availability { return true }
        }
        #endif
        return false
    }

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
