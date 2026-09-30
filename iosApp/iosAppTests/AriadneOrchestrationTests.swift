import XCTest
@testable import Syrmos

/// Ariadne's turn orchestration: local first, no setup, one message per turn.
///
/// The repair these cover: `ask` used to await the hosted service before parsing
/// anything, so a cloud outage delayed an answer the app already knew by up to
/// the service's timeout; `askLLM` appended the current user message a second
/// time on top of the history that already contained it; and two rapid
/// submissions shared one `thinking` flag and one pending-clarification slot, so
/// a late result could land against a different question's context.
@MainActor
final class AriadneOrchestrationTests: XCTestCase {

    /// The greeting the model seeds itself with; every assertion below looks at
    /// what arrives AFTER it rather than at absolute indices, because an
    /// optional alert note can append asynchronously.
    private func newModel() -> AriadneModel { AriadneModel() }

    private func waitForReply(_ model: AriadneModel, after count: Int, timeout: TimeInterval = 10) async {
        let deadline = Date().addingTimeInterval(timeout)
        while model.messages.count <= count, Date() < deadline {
            try? await Task.sleep(nanoseconds: 30_000_000)
        }
    }

    func testACoreTransitQuestionIsAnsweredWithoutAModelOrANetworkRoundTrip() async throws {
        let model = newModel()
        let before = model.messages.count
        let started = Date()
        model.ask("When is the last train from Syntagma?")
        await waitForReply(model, after: before + 1)
        let elapsed = Date().timeIntervalSince(started)

        // The user message and exactly one reply.
        let added = model.messages.suffix(from: before)
        XCTAssertEqual(added.first?.fromUser, true, "the user's turn is recorded once")
        XCTAssertTrue(added.contains { !$0.fromUser }, "a reply arrived")
        XCTAssertFalse(model.thinking, "the turn's busy state is cleared")

        // A local answer must not wait on a provider. The hosted service's own
        // timeout is tens of seconds; anything in that range means the old
        // cloud-first order came back.
        XCTAssertLessThan(elapsed, 5.0, "a known local answer took \(elapsed)s")
    }

    func testTheUserMessageIsRecordedExactlyOncePerTurn() async throws {
        let model = newModel()
        let before = model.messages.count
        model.ask("departures from Monastiraki")
        await waitForReply(model, after: before + 1)
        let userTurns = model.messages.suffix(from: before).filter {
            $0.fromUser && $0.text == "departures from Monastiraki"
        }
        XCTAssertEqual(userTurns.count, 1, "the turn must appear once, not twice")
    }

    func testStopClearsTheBusyStateAndKeepsTheConversation() async throws {
        let model = newModel()
        let before = model.messages.count
        model.ask("something the parser cannot possibly resolve qqzzxx")
        model.stop()
        XCTAssertFalse(model.thinking, "Stop clears the busy state immediately")
        // The conversation, including the user's own turn, survives.
        XCTAssertTrue(model.messages.suffix(from: before).contains { $0.fromUser })
    }

    func testAStoppedTurnCannotAppendItsResultLater() async throws {
        let model = newModel()
        let before = model.messages.count
        model.ask("something the parser cannot possibly resolve qqzzxx")
        model.stop()
        let afterStop = model.messages.count
        // Give any in-flight optional provider time to come back.
        try? await Task.sleep(nanoseconds: 2_000_000_000)
        XCTAssertEqual(
            model.messages.count, afterStop,
            "a cancelled turn's result must not mutate a later conversation",
        )
        XCTAssertGreaterThan(afterStop, before)
    }

    func testAnUnresolvableQuestionStillGetsALocalizedRecovery() async throws {
        let model = newModel()
        let before = model.messages.count
        model.ask("qqzzxx wibble")
        await waitForReply(model, after: before + 1, timeout: 20)
        let reply = model.messages.suffix(from: before).first { !$0.fromUser }
        XCTAssertNotNil(reply, "an unresolvable question still gets an answer")
        // Never an internal failure report: the app is not broken because an
        // optional understanding layer was unavailable.
        let lowered = reply?.text.lowercased() ?? ""
        for leak in ["error", "exception", "brain", "model", "timeout", "nil"] {
            XCTAssertFalse(lowered.contains(leak), "recovery leaked '\(leak)': \(reply?.text ?? "")")
        }
    }
}
