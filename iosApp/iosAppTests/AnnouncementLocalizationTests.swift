import XCTest
@testable import Syrmos

/// Mirrors `core/network/.../STASYAnnouncementLocalizationTest.kt` case for case.
///
/// The announcement feed publishes a Greek `title` and, in practice, empty
/// `titleEn`/`titleSq`/`titleIt` for every item, so these tests pin the rule:
/// show the best language we have, and fall back to a generic label only when the
/// operator published no title at all.
final class AnnouncementLocalizationTests: XCTestCase {

    private func announcement(
        title: String = "Κυκλοφοριακές ρυθμίσεις",
        titleEn: String = "Κυκλοφοριακές ρυθμίσεις",
        titleSq: String = "Κυκλοφοριακές ρυθμίσεις",
        titleIt: String = "Modifiche al servizio",
        summary: String = "Επίσημη ανακοίνωση",
        summaryEn: String = "Επίσημη ανακοίνωση",
        category: AnnouncementCategory = .serviceAlert
    ) -> STASYAnnouncement {
        STASYAnnouncement(
            id: "bad-translations",
            title: title, titleEn: titleEn, titleSq: titleSq, titleIt: titleIt,
            date: "",
            summary: summary, summaryEn: summaryEn, summarySq: summary, summaryIt: "Avviso ufficiale",
            url: URL(string: "https://example.com"),
            category: category
        )
    }

    func testUntranslatedAlertKeepsItsRealNameInsteadOfAGenericLabel() {
        // A Greek-only echo in titleEn/titleSq is not a translation, so both fall
        // through to the operator's own wording. Naming the alert beats "Service
        // alert", which identified nothing and made adjacent cards look identical.
        let a = announcement()
        XCTAssertEqual(a.displayTitle(language: .english), "Κυκλοφοριακές ρυθμίσεις")
        XCTAssertEqual(a.displayTitle(language: .albanian), "Κυκλοφοριακές ρυθμίσεις")
        XCTAssertEqual(a.displaySummary(language: .english), "Επίσημη ανακοίνωση")
    }

    func testValidItalianAndNativeGreekRemainAvailable() {
        let a = announcement()
        XCTAssertEqual(a.displayTitle(language: .italian), "Modifiche al servizio")
        XCTAssertEqual(a.displayTitle(language: .greek), "Κυκλοφοριακές ρυθμίσεις")
    }

    func testRealTranslationStillWinsOverTheSource() {
        let a = announcement(titleEn: "Traffic changes", titleSq: "Ndryshime trafiku")
        XCTAssertEqual(a.displayTitle(language: .english), "Traffic changes")
        XCTAssertEqual(a.displayTitle(language: .albanian), "Ndryshime trafiku")
    }

    func testAlbanianAndItalianBorrowEnglishBeforeTheGreekSource() {
        let a = announcement(titleEn: "Traffic changes", titleIt: "")
        XCTAssertEqual(a.displayTitle(language: .albanian), "Traffic changes")
        XCTAssertEqual(a.displayTitle(language: .italian), "Traffic changes")
    }

    func testGenericLabelSurvivesForAnAnnouncementWithNoTitleAtAll() {
        let empty = announcement(title: "", titleEn: "", titleSq: "", titleIt: "")
        XCTAssertEqual(empty.displayTitle(language: .english), "Service alert")
        XCTAssertEqual(empty.displayTitle(language: .albanian), "Njoftim për shërbimin")
        XCTAssertEqual(empty.displayTitle(language: .italian), "Avviso sul servizio")

        let news = announcement(title: "", titleEn: "", titleSq: "", titleIt: "", category: .general)
        XCTAssertEqual(news.displayTitle(language: .english), "Rail announcement")
    }

    func testBestTextPrefersATranslationThenAnyNonBlankWording() {
        XCTAssertEqual(STASYAnnouncement.bestText("", "Traffic changes", "Ρυθμίσεις"), "Traffic changes")
        XCTAssertEqual(STASYAnnouncement.bestText("", "", "Ρυθμίσεις"), "Ρυθμίσεις")
        XCTAssertNil(STASYAnnouncement.bestText("", "   ", ""))
    }
}
