import XCTest
@testable import Syrmos

/// Monotonic Greek drops the accent in all-caps. The locale-unaware
/// `uppercased()` keeps it, which is how the Home status chip shipped reading
/// ΠΡΩΙΝΉ ΜΕΤΑΚΊΝΗΣΗ instead of ΠΡΩΙΝΗ ΜΕΤΑΚΙΝΗΣΗ.
final class GreekTypographyTests: XCTestCase {

    func testGreekAllCapsDropsTheAccent() {
        for (input, expected) in [
            ("Πρωινή μετακίνηση", "ΠΡΩΙΝΗ ΜΕΤΑΚΙΝΗΣΗ"),   // the Home status chip
            ("Επόμενος συρμός", "ΕΠΟΜΕΝΟΣ ΣΥΡΜΟΣ"),        // the "next train" label
            ("Πέμ", "ΠΕΜ"),                                 // an airport day chip
            ("Σάβ", "ΣΑΒ"),
            ("Άφιξη", "ΑΦΙΞΗ"),
        ] {
            XCTAssertEqual(input.uppercasedForDisplay(.greek), expected)
        }
    }

    func testTheOldBehaviourIsWhatWeAreFixing() {
        // Pinned so nobody "simplifies" the helper back to plain uppercased().
        XCTAssertEqual("Πρωινή μετακίνηση".uppercased(), "ΠΡΩΙΝΉ ΜΕΤΑΚΊΝΗΣΗ")
        XCTAssertNotEqual(
            "Πρωινή μετακίνηση".uppercased(),
            "Πρωινή μετακίνηση".uppercasedForDisplay(.greek)
        )
    }

    func testDialytikaTonosResolvesCleanlyInsteadOfBecomingCombiningMarks() {
        // Plain uppercasing turns ΐ into Ϊ plus a combining accent; Greek gives Ϊ.
        XCTAssertEqual("ΐ".uppercasedForDisplay(.greek), "Ϊ")
    }

    func testEveryOtherLanguageIsUntouched() {
        XCTAssertEqual("Next train".uppercasedForDisplay(.english), "NEXT TRAIN")
        XCTAssertEqual("Vonesë shërbimi".uppercasedForDisplay(.albanian), "VONESË SHËRBIMI")
        XCTAssertEqual("Prossima corsa".uppercasedForDisplay(.italian), "PROSSIMA CORSA")
        // Latin inside a Greek-language build is not mangled either: line codes
        // and place names in Latin script must survive unchanged.
        XCTAssertEqual("M1".uppercasedForDisplay(.greek), "M1")
        XCTAssertEqual("Eleftherios".uppercasedForDisplay(.greek), "ELEFTHERIOS")
    }

    func testTitleCaseMustNotUseThisHelper() {
        // Greek KEEPS the accent when only the first letter is capitalized, so
        // the two `prefix(1).uppercased()` call sites in MapView are correct as
        // they are. This pins why: routing them through the Greek locale would
        // strip an accent that belongs there.
        XCTAssertEqual("άμεσο".prefix(1).uppercased() + "άμεσο".dropFirst(), "Άμεσο")
        XCTAssertEqual(String("άμεσο".prefix(1)).uppercasedForDisplay(.greek), "Α")
    }


    // MARK: Diacritics in the shared table

    // Greek words of five letters or more always carry a tonos in lower or
    // mixed case; all-caps text legitimately drops it. Albanian without ë and ç
    // reads as a transliteration. Both are checked over the whole table so a
    // stripped entry fails here instead of shipping. Twin of
    // LocalizationDiacriticsTest in core/common.
    private let tonos = "[άέήίόύώΆΈΉΊΌΎΏϊϋΐΰ]"
    private let longGreekWord = "[Α-Ωα-ω]{5,}"
    private let strippedAlbanian = "\\b(eshte|nje|kete|ketu|gjithe|gjithcka|prane|sherbim\\w*|perdit\\w*|udhet\\w*|perdor\\w*|kerko\\w*|cmim\\w*|vleresim\\w*|nderr\\w*|shpejtesi|nevoje|radhes|afert|disponueshem|plotesisht|jashte|te gjitha|per te|ne stacion|ne harte)\\b"

    private func matches(_ text: String, _ pattern: String) -> Bool {
        text.range(of: pattern, options: [.regularExpression, .caseInsensitive]) != nil
    }

    func testGreekReaderTextCarriesItsTonos() {
        let offenders = LocalizedKey.allCases
            .map { ($0, $0.text(for: .greek)) }
            .filter { _, text in text != text.uppercased() && matches(text, longGreekWord) && !matches(text, tonos) }
            .map { "\($0.0): \($0.1)" }
        XCTAssertTrue(offenders.isEmpty, "Greek strings without a tonos: \(offenders)")
    }

    func testAlbanianReaderTextKeepsItsDiacritics() {
        let offenders = LocalizedKey.allCases
            .map { ($0, $0.text(for: .albanian)) }
            .filter { _, text in matches(text, strippedAlbanian) }
            .map { "\($0.0): \($0.1)" }
        XCTAssertTrue(offenders.isEmpty, "Albanian strings with stripped diacritics: \(offenders)")
    }

    func testTheDiacriticsChecksCatchAStrippedString() {
        XCTAssertTrue(matches("Μεινε ενημερος", longGreekWord) && !matches("Μεινε ενημερος", tonos))
        XCTAssertTrue(matches("Merr njoftime per nderprerje sherbimesh prane teje.", strippedAlbanian))
        XCTAssertFalse(matches("Merr njoftime për ndërprerje shërbimesh pranë teje.", strippedAlbanian))
    }
}
