import Foundation
import AVFoundation

/// Voiced Ariadne, read-back only. Speaks the soonest departure aloud on the
/// Watch via `AVSpeechSynthesizer`, fully offline. Trilingual per the app
/// language mirrored into the shared App Group (EN / EL / SQ), falling back to
/// English when a Shqip voice is unavailable on the device.
///
/// This is deliberately a tiny standalone helper (no view-model wiring) so it
/// drops cleanly into the Watch target. It never generates transit facts: the
/// caller passes the already-resolved line label, destination, and live
/// minutes, and this only phrases and speaks them.
final class WatchSpeech {
    static let shared = WatchSpeech()

    private let synthesizer = AVSpeechSynthesizer()

    private init() {}

    /// Speak the soonest departure. `minutes <= 0` reads as "now". `language`
    /// is the app language the phone sent in the snapshot ("en"/"el"/"sq").
    func speak(lineLabel: String, destination: String, minutes: Int, language: String) {
        let phrase = Self.phrase(
            language: language, lineLabel: lineLabel, destination: destination, minutes: minutes
        )
        let utterance = AVSpeechUtterance(string: phrase)
        utterance.voice = Self.voice(for: language)
        utterance.rate = AVSpeechUtteranceDefaultSpeechRate
        if synthesizer.isSpeaking {
            synthesizer.stopSpeaking(at: .immediate)
        }
        synthesizer.speak(utterance)
    }

    /// Pick a voice matching the app language, falling back to English when the
    /// requested language has no installed voice (common for Shqip on watchOS).
    static func voice(for language: String) -> AVSpeechSynthesisVoice? {
        let bcp47: String
        switch language {
        case "el": bcp47 = "el"
        case "sq": bcp47 = "sq"
        default: bcp47 = "en"
        }
        if let match = AVSpeechSynthesisVoice.speechVoices().first(where: { $0.language.hasPrefix(bcp47) }) {
            return match
        }
        // Fall back to English (Shqip voices are frequently missing).
        return AVSpeechSynthesisVoice(language: "en-US")
            ?? AVSpeechSynthesisVoice.speechVoices().first { $0.language.hasPrefix("en") }
    }

    /// Trilingual read-back phrase. "now"/"τώρα"/"tani" when imminent.
    static func phrase(language: String, lineLabel: String, destination: String, minutes: Int) -> String {
        let imminent = minutes <= 0
        switch language {
        case "el":
            if imminent { return "Επόμενη \(lineLabel) προς \(destination) τώρα" }
            let word = minutes == 1 ? "λεπτό" : "λεπτά"
            return "Επόμενη \(lineLabel) προς \(destination) σε \(minutes) \(word)"
        case "sq":
            if imminent { return "\(lineLabel) tjetër drejt \(destination) tani" }
            let word = minutes == 1 ? "minutë" : "minuta"
            return "\(lineLabel) tjetër drejt \(destination) për \(minutes) \(word)"
        default:
            if imminent { return "Next \(lineLabel) to \(destination) now" }
            let word = minutes == 1 ? "minute" : "minutes"
            return "Next \(lineLabel) to \(destination) in \(minutes) \(word)"
        }
    }
}
