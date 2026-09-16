import XCTest
@testable import Syrmos

/// Finding 8: guard the corrected high-visibility Greek display strings against
/// silently regressing to their unaccented, broken-looking forms. This does not
/// certify correct Greek (only a native reviewer can), it locks in the specific
/// approved corrections so a later edit cannot quietly drop the tonos again.
final class GreekCopyRegressionTests: XCTestCase {
    private let tonos = Set("άέήίόύώΆΈΉΊΌΎΏ")

    private func el(_ key: LocalizedKey) -> String { key.text(for: .greek) }

    func testOnboardingGreekIsAccented() {
        // Every corrected multi-syllable onboarding string now carries a tonos.
        for key in [LocalizedKey.onboardWelcomeBody, .onboardLiveBody,
                    .onboardNotifTitle, .onboardNotifBody, .onboardNotifCta,
                    .onboardMapToolsTitle, .onboardMapToolsBody] {
            let s = el(key)
            XCTAssertTrue(s.contains(where: { tonos.contains($0) }),
                          "Greek onboarding copy lost its accents: \(s)")
        }
    }

    func testSpecificApprovedCorrections() {
        XCTAssertEqual(el(.onboardNotifTitle), "Μείνε ενήμερος")
        XCTAssertEqual(el(.onboardNotifCta), "Επίτρεψε τις ειδοποιήσεις")
        XCTAssertEqual(el(.onboardMapToolsTitle), "Τα εργαλεία του χάρτη")
        // No regression to the old unaccented spellings.
        XCTAssertNotEqual(el(.onboardNotifTitle), "Μεινε ενημερος")
    }
}
