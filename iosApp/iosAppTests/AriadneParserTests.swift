import XCTest
@testable import Syrmos

/// Mirrors the KMP `AthensTransitParserTest` so Ariadne behaves identically on
/// iOS and Android/Web across all four supported languages.
final class AriadneParserTests: XCTestCase {

    private let vocab = AssistantVocabulary(
        stations: [
            StationVocab(id: "M2_SYN", names: ["Syntagma", "Σύνταγμα", "Sintagma"], lineIds: ["M2", "M3"]),
            StationVocab(id: "M1_PIR", names: ["Piraeus", "Πειραιάς", "Pireas", "Pireo"], lineIds: ["M1", "A1"]),
            StationVocab(id: "M3_AER", names: ["Airport", "Αεροδρόμιο", "Aeroporti", "Aeroport", "Aeroporto"], lineIds: ["M3", "A1"]),
            StationVocab(id: "M1_MON", names: ["Monastiraki", "Μοναστηράκι"], lineIds: ["M1", "M3"]),
        ],
        lines: [
            LineVocab(id: "M1", aliases: ["M1", "line 1", "γραμμή 1", "linea 1"]),
            LineVocab(id: "M2", aliases: ["M2", "line 2", "γραμμή 2", "metro 2", "linea 2"]),
            LineVocab(id: "M3", aliases: ["M3", "line 3"]),
            LineVocab(id: "A1", aliases: ["A1", "airport line"]),
        ]
    )
    private lazy var parser = AthensTransitParser(vocabulary: vocab)

    func test_departuresFromStation() {
        guard case let .showDepartures(stationId, _, _) = parser.parse("next trains from Syntagma") else {
            return XCTFail("expected departures")
        }
        XCTAssertEqual(stationId, "M2_SYN")
    }

    func test_bareLineDeparturesNoClarification() {
        guard case let .showDepartures(_, lineId, _) = parser.parse("next M2 train") else {
            return XCTFail("expected departures")
        }
        XCTAssertEqual(lineId, "M2")
    }

    func test_departuresWithNeitherAsksStation() {
        guard case let .needsClarification(_, missing) = parser.parse("when is the next train") else {
            return XCTFail("expected clarification")
        }
        XCTAssertEqual(missing, .station)
    }

    func test_lastTrainWithStation() {
        guard case let .lastTrain(stationId, _) = parser.parse("when is the last train from Piraeus") else {
            return XCTFail("expected last train")
        }
        XCTAssertEqual(stationId, "M1_PIR")
    }

    func test_planTripOrdersEndpoints() {
        guard case let .planTrip(from, to, _, _) = parser.parse("how do I get from Piraeus to Syntagma") else {
            return XCTFail("expected plan trip")
        }
        XCTAssertEqual(from, "M1_PIR")
        XCTAssertEqual(to, "M2_SYN")
    }

    func test_rainRoutesLowExposureAsksOrigin() {
        guard case let .needsClarification(base, missing) = parser.parse("it's raining, get me to the Airport") else {
            return XCTFail("expected clarification")
        }
        XCTAssertEqual(missing, .originStation)
        guard case let .planTrip(_, to, lowExposure, _) = base else { return XCTFail("expected plan trip base") }
        XCTAssertEqual(to, "M3_AER")
        XCTAssertTrue(lowExposure)
    }

    func test_alerts() {
        guard case .showAlerts = parser.parse("any service alerts today?") else {
            return XCTFail("expected alerts")
        }
    }

    func test_help() {
        guard case .help = parser.parse("what can you do?") else { return XCTFail("expected help") }
    }

    func test_fareAirport() {
        guard case let .explainFare(airport, _, toId) = parser.parse("how much is a ticket to the Airport") else {
            return XCTFail("expected fare")
        }
        XCTAssertTrue(airport)
        XCTAssertEqual(toId, "M3_AER")
    }

    func test_fareStandard() {
        guard case let .explainFare(airport, _, _) = parser.parse("what's the ticket price") else {
            return XCTFail("expected fare")
        }
        XCTAssertFalse(airport)
    }

    func test_favoriteStation() {
        guard case let .toggleFavorite(stationId) = parser.parse("favorite Syntagma") else {
            return XCTFail("expected favorite")
        }
        XCTAssertEqual(stationId, "M2_SYN")
    }

    func test_favoriteWithoutStationAsksStation() {
        guard case let .needsClarification(base, missing) = parser.parse("save this station") else {
            return XCTFail("expected clarification")
        }
        XCTAssertEqual(missing, .station)
        guard case .toggleFavorite = base else { return XCTFail("expected toggleFavorite base") }
    }

    func test_outOfScopeWeatherElsewhere() {
        guard case .outOfScope = parser.parse("what's the weather in London") else {
            return XCTFail("expected out of scope")
        }
    }

    func test_outOfScopeGeneral() {
        guard case .outOfScope = parser.parse("who won the election") else {
            return XCTFail("expected out of scope")
        }
    }

    // MARK: - Travel time / ETA

    func test_travelTimeSingleStationDefaultsOrigin() {
        guard case let .travelTime(toId, fromId) = parser.parse("how long to the Airport") else {
            return XCTFail("expected travel time")
        }
        XCTAssertEqual(toId, "M3_AER")
        XCTAssertNil(fromId)
    }

    func test_travelTimeExplicitOriginDestination() {
        guard case let .travelTime(toId, fromId) = parser.parse("how many minutes from Piraeus to Syntagma") else {
            return XCTFail("expected travel time")
        }
        XCTAssertEqual(fromId, "M1_PIR")
        XCTAssertEqual(toId, "M2_SYN")
    }

    func test_travelTimeWithoutDestinationAsksDestination() {
        guard case let .needsClarification(base, missing) = parser.parse("how long does it take") else {
            return XCTFail("expected clarification")
        }
        XCTAssertEqual(missing, .destinationStation)
        guard case .travelTime = base else { return XCTFail("expected travelTime base") }
    }

    // MARK: - Fuzzy / typo tolerance

    func test_fuzzyTypoResolvesStation() {
        guard case let .showDepartures(stationId, _, _) = parser.parse("next trains from Sintagna") else {
            return XCTFail("expected departures")
        }
        XCTAssertEqual(stationId, "M2_SYN")
    }

    func test_fuzzyBareTypoIsDepartures() {
        guard case let .showDepartures(stationId, _, _) = parser.parse("Monastraki") else {
            return XCTFail("expected departures")
        }
        XCTAssertEqual(stationId, "M1_MON")
    }

    func test_fuzzyDoesNotMatchGibberish() {
        guard case .outOfScope = parser.parse("qwertyuiop") else {
            return XCTFail("expected out of scope")
        }
    }

    func test_cleanQueryNotOverriddenByFuzzy() {
        guard case let .showDepartures(stationId, _, _) = parser.parse("Piraeus") else {
            return XCTFail("expected departures")
        }
        XCTAssertEqual(stationId, "M1_PIR")
    }

    func test_greekDepartures() {
        guard case let .showDepartures(stationId, _, _) = parser.parse("επόμενα δρομολόγια από Σύνταγμα") else {
            return XCTFail("expected departures")
        }
        XCTAssertEqual(stationId, "M2_SYN")
    }

    func test_albanianPlanTrip() {
        guard case let .planTrip(from, to, _, _) = parser.parse("si shkoj nga Pireas te Sintagma") else {
            return XCTFail("expected plan trip")
        }
        XCTAssertEqual(from, "M1_PIR")
        XCTAssertEqual(to, "M2_SYN")
    }

    // MARK: - Italian

    func test_italianDepartures() {
        guard case let .showDepartures(stationId, _, _) = parser.parse("prossimi treni da Syntagma") else {
            return XCTFail("expected departures")
        }
        XCTAssertEqual(stationId, "M2_SYN")
    }

    func test_italianPlanTrip() {
        guard case let .planTrip(from, to, _, _) = parser.parse("come arrivo da Pireo verso Aeroporto") else {
            return XCTFail("expected plan trip")
        }
        XCTAssertEqual(from, "M1_PIR")
        XCTAssertEqual(to, "M3_AER")
    }

    func test_italianLastTrainAndFare() {
        guard case .lastTrain = parser.parse("ultimo treno da Pireo") else {
            return XCTFail("expected last train")
        }
        guard case .explainFare = parser.parse("quanto costa il biglietto per Aeroporto") else {
            return XCTFail("expected fare")
        }
    }

    func test_italianStationStatus() {
        guard case let .stationStatus(stationId) = parser.parse("Syntagma è aperto?") else {
            return XCTFail("expected station status")
        }
        XCTAssertEqual(stationId, "M2_SYN")
    }

    // MARK: - Station status (mirrors AriadneContextParserTest)

    func test_isStationOpenIsStatusNotDepartures() {
        guard case let .stationStatus(stationId) = parser.parse("is Syntagma open?") else {
            return XCTFail("expected station status")
        }
        XCTAssertEqual(stationId, "M2_SYN")
    }

    func test_isStationClosedIsStatusNotAlerts() {
        // "closed" also lives in alertWords; with a named station, status wins.
        guard case let .stationStatus(stationId) = parser.parse("is Monastiraki closed right now") else {
            return XCTFail("expected station status")
        }
        XCTAssertEqual(stationId, "M1_MON")
    }

    func test_greekStationWorkingQueryIsStatus() {
        guard case let .stationStatus(stationId) = parser.parse("λειτουργεί το Σύνταγμα;") else {
            return XCTFail("expected station status")
        }
        XCTAssertEqual(stationId, "M2_SYN")
    }

    func test_stationOpenWithoutAStationAsksWhich() {
        guard case let .needsClarification(base, missing) = parser.parse("is the station open") else {
            return XCTFail("expected clarification")
        }
        XCTAssertEqual(missing, .station)
        guard case .stationStatus = base else { return XCTFail("expected stationStatus base") }
    }

    // MARK: - Context-set "I'm at X"

    func test_imAtStationSetsCurrentLocation() {
        guard case let .setCurrentLocation(stationId) = parser.parse("I'm at Syntagma") else {
            return XCTFail("expected set location")
        }
        XCTAssertEqual(stationId, "M2_SYN")
    }

    func test_messyImAtStationSetsCurrentLocation() {
        guard case let .setCurrentLocation(stationId) = parser.parse("im at monastiraki") else {
            return XCTFail("expected set location")
        }
        XCTAssertEqual(stationId, "M1_MON")
    }

    func test_greekEimaiStoSetsCurrentLocation() {
        guard case let .setCurrentLocation(stationId) = parser.parse("είμαι στο Σύνταγμα") else {
            return XCTFail("expected set location")
        }
        XCTAssertEqual(stationId, "M2_SYN")
    }

    func test_albanianJamTeSetsCurrentLocation() {
        guard case let .setCurrentLocation(stationId) = parser.parse("jam te Syntagma") else {
            return XCTFail("expected set location")
        }
        XCTAssertEqual(stationId, "M2_SYN")
    }

    func test_imHereWithNoStationSetsNilLocation() {
        guard case let .setCurrentLocation(stationId) = parser.parse("I'm here") else {
            return XCTFail("expected set location")
        }
        XCTAssertNil(stationId)
    }

    // MARK: - Route preference

    func test_imAtXGoYFastIsFastestTrip() {
        guard case let .planTrip(from, to, _, preference) = parser.parse("im at monastiraki go airport fast") else {
            return XCTFail("expected plan trip")
        }
        XCTAssertEqual(from, "M1_MON")
        XCTAssertEqual(to, "M3_AER")
        XCTAssertEqual(preference, .fastest)
    }

    func test_albanianShpejtIsFastestTrip() {
        guard case let .planTrip(from, to, _, preference) = parser.parse("jam te syntagma dua aeroport shpejt") else {
            return XCTFail("expected plan trip")
        }
        XCTAssertEqual(from, "M2_SYN")
        XCTAssertEqual(to, "M3_AER")
        XCTAssertEqual(preference, .fastest)
    }

    func test_easiestRouteIsFewestChanges() {
        guard case let .planTrip(_, _, _, preference) = parser.parse("give me the easiest route from Piraeus to Syntagma") else {
            return XCTFail("expected plan trip")
        }
        XCTAssertEqual(preference, .fewestChanges)
    }

    func test_howDoIGoToDestFasterAsksOriginAndKeepsPreference() {
        // Single destination via a plan cue: origin unknown, preference kept.
        guard case let .needsClarification(base, missing) = parser.parse("how do I go to the airport faster") else {
            return XCTFail("expected clarification")
        }
        XCTAssertEqual(missing, .originStation)
        guard case let .planTrip(from, to, _, preference) = base else { return XCTFail("expected plan trip base") }
        XCTAssertEqual(to, "M3_AER")
        XCTAssertNil(from)
        XCTAssertEqual(preference, .fastest)
    }

    // MARK: - First train (new capability)

    func test_firstTrainWithStation() {
        guard case let .firstTrain(stationId, _) = parser.parse("when is the first train from Piraeus") else {
            return XCTFail("expected first train")
        }
        XCTAssertEqual(stationId, "M1_PIR")
    }

    func test_firstTrainGreek() {
        guard case let .firstTrain(stationId, _) = parser.parse("πρώτο τρένο από το Σύνταγμα") else {
            return XCTFail("expected first train")
        }
        XCTAssertEqual(stationId, "M2_SYN")
    }

    func test_firstTrainAlbanian() {
        guard case .firstTrain = parser.parse("treni i parë nga Pireas") else {
            return XCTFail("expected first train")
        }
    }

    func test_firstTrainBareLine() {
        guard case let .firstTrain(_, lineId) = parser.parse("first M2 train") else {
            return XCTFail("expected first train")
        }
        XCTAssertEqual(lineId, "M2")
    }

    func test_firstNotConfusedWithLast() {
        guard case .firstTrain = parser.parse("first train Syntagma") else { return XCTFail("expected first") }
        guard case .lastTrain = parser.parse("last train Syntagma") else { return XCTFail("expected last") }
    }

    // MARK: - Accessibility (new capability)

    func test_accessibilityWithStation() {
        guard case let .stationAccessibility(stationId) = parser.parse("is Syntagma wheelchair accessible") else {
            return XCTFail("expected accessibility")
        }
        XCTAssertEqual(stationId, "M2_SYN")
    }

    func test_accessibilityLiftPhrasing() {
        guard case let .stationAccessibility(stationId) = parser.parse("does Piraeus have a lift") else {
            return XCTFail("expected accessibility")
        }
        XCTAssertEqual(stationId, "M1_PIR")
    }

    func test_accessibilityGreek() {
        guard case .stationAccessibility = parser.parse("είναι προσβάσιμο για ΑμεΑ το Σύνταγμα") else {
            return XCTFail("expected accessibility")
        }
    }

    func test_accessibilityAlbanian() {
        guard case let .stationAccessibility(stationId) = parser.parse("a është Sintagma i aksesueshëm") else {
            return XCTFail("expected accessibility")
        }
        XCTAssertEqual(stationId, "M2_SYN")
    }

    // MARK: - Reverse trip follow-up (smarter conversations)

    func test_reverseTripAndBack() {
        guard case .reverseTrip = parser.parse("and back?") else { return XCTFail("expected reverse") }
    }

    func test_reverseTripGreek() {
        guard case .reverseTrip = parser.parse("και πίσω;") else { return XCTFail("expected reverse") }
    }

    func test_reverseTripAlbanian() {
        guard case .reverseTrip = parser.parse("kthimi") else { return XCTFail("expected reverse") }
    }

    func test_reverseTripDormantWithStations() {
        guard case .planTrip = parser.parse("Syntagma to Piraeus and back") else {
            return XCTFail("expected plan trip")
        }
    }

    // MARK: - Expanded phrasings + day probe

    func test_expandedPlanPhrasing() {
        guard case let .planTrip(from, to, _, _) = parser.parse("I want to go from Syntagma to the airport") else {
            return XCTFail("expected plan trip")
        }
        XCTAssertEqual(from, "M2_SYN")
        XCTAssertEqual(to, "M3_AER")
    }

    func test_expandedDeparturePhrasingArrivals() {
        guard case .showDepartures = parser.parse("arrivals at Monastiraki") else {
            return XCTFail("expected departures")
        }
    }

    func test_dayProbeTrilingual() {
        XCTAssertEqual(parser.dayOf("what about tomorrow"), .tomorrow)
        XCTAssertEqual(parser.dayOf("και αύριο;"), .tomorrow)
        XCTAssertEqual(parser.dayOf("Syntagma"), .today)
    }

    // MARK: - Which lines + stops between (v2 capabilities)

    func test_whichLinesWithStation() {
        guard case let .whichLines(stationId) = parser.parse("which lines serve Syntagma") else {
            return XCTFail("expected which lines")
        }
        XCTAssertEqual(stationId, "M2_SYN")
    }

    func test_whichLinesGreek() {
        guard case .whichLines = parser.parse("ποιες γραμμές περνάνε από το Σύνταγμα") else {
            return XCTFail("expected which lines")
        }
    }

    func test_whichLinesAlbanian() {
        guard case let .whichLines(stationId) = parser.parse("cilat linja shërbejnë Sintagma") else {
            return XCTFail("expected which lines")
        }
        XCTAssertEqual(stationId, "M2_SYN")
    }

    func test_stopsBetweenTwoStations() {
        guard case let .stopsBetween(from, to) = parser.parse("how many stops from Piraeus to Syntagma") else {
            return XCTFail("expected stops between")
        }
        XCTAssertEqual(from, "M1_PIR")
        XCTAssertEqual(to, "M2_SYN")
    }

    func test_stopsBetweenNotReadAsPlan() {
        guard case .stopsBetween = parser.parse("how many stops Piraeus to Syntagma") else {
            return XCTFail("expected stops between")
        }
    }
}

/// Mirrors the KMP `ServiceAdvisoryMatcherTest`: Ariadne surfaces the same STASY
/// advisories (now with real per-line + severity fields on iOS) for an affected
/// station, line, or route, and stays quiet when nothing is relevant.
final class ServiceAdvisoryMatcherTests: XCTestCase {

    private let m3Closure = ServiceNotice(
        id: "m3-eve-closure",
        text: "Traffic arrangements on Metro Line 3: Megaro Musikis, Ampelokipoi, "
            + "Panormou and Katehaki stations will close at 21:40 in the evening.",
        affectedLineIds: ["M3"],
        severity: .closure
    )
    private let m3ClosureGreek = ServiceNotice(
        id: "m3-eve-closure-el",
        text: "Κυκλοφοριακές ρυθμίσεις στη Γραμμή 3: οι σταθμοί Μέγαρο Μουσικής, "
            + "Αμπελόκηποι, Πανόρμου και Κατεχάκη θα κλείσουν στις 21:40.",
        affectedLineIds: ["M3"],
        severity: .closure
    )
    private let m2Info = ServiceNotice(
        id: "m2-info",
        text: "Extended hours on Line 2 this weekend.",
        affectedLineIds: ["M2"],
        severity: .info
    )

    func test_stationNamedInNoticeTextMatches() {
        let advisory = ServiceAdvisoryMatcher.forStation(
            stationNames: ["Panormou", "Πανόρμου"],
            stationLineIds: ["M3"],
            notices: [m3Closure, m2Info]
        )
        XCTAssertTrue(advisory.hasAny)
        XCTAssertEqual(advisory.top?.id, "m3-eve-closure")
    }

    func test_greekNoticeMatchesGreekStationName() {
        let advisory = ServiceAdvisoryMatcher.forStation(
            stationNames: ["Katehaki", "Κατεχάκη"],
            stationLineIds: ["M3"],
            notices: [m3ClosureGreek]
        )
        XCTAssertTrue(advisory.hasAny)
    }

    func test_stationOnAffectedLineSurfacedEvenIfNotNamed() {
        // Now that iOS carries affectedLines, a line-wide M3 advisory reaches an
        // M3 traveller (Syntagma) even though the closure text doesn't name it.
        let advisory = ServiceAdvisoryMatcher.forStation(
            stationNames: ["Syntagma", "Σύνταγμα"],
            stationLineIds: ["M2", "M3"],
            notices: [m3Closure]
        )
        XCTAssertTrue(advisory.hasAny)
    }

    func test_stationOffLineAndUnnamedStaysQuiet() {
        let advisory = ServiceAdvisoryMatcher.forStation(
            stationNames: ["Piraeus", "Πειραιάς"],
            stationLineIds: ["M1"],
            notices: [m3Closure]
        )
        XCTAssertFalse(advisory.hasAny)
    }

    func test_lineQueryMatchesOnlyItsOwnLine() {
        XCTAssertTrue(ServiceAdvisoryMatcher.forLine(lineId: "M3", notices: [m3Closure, m2Info]).hasAny)
        XCTAssertFalse(ServiceAdvisoryMatcher.forLine(lineId: "M1", notices: [m3Closure, m2Info]).hasAny)
    }

    func test_closuresRankAheadOfInfo() {
        var m2 = m2Info
        m2.affectedLineIds = ["M2", "M3"]
        let advisory = ServiceAdvisoryMatcher.forRoute(
            lineIds: ["M2", "M3"],
            stationNames: [],
            notices: [m2, m3Closure]
        )
        XCTAssertEqual(advisory.top?.severity, .closure)
    }

    func test_severeWeatherPassesThroughWithNoNotices() {
        let advisory = ServiceAdvisoryMatcher.forStation(
            stationNames: ["Syntagma"],
            stationLineIds: ["M2", "M3"],
            notices: [],
            severeWeather: true
        )
        XCTAssertTrue(advisory.hasAny)
        XCTAssertTrue(advisory.severeWeather)
    }

    func test_severityMapsFromRawFeedStrings() {
        XCTAssertEqual(AdvisorySeverity.fromRaw("closure"), .closure)
        XCTAssertEqual(AdvisorySeverity.fromRaw("Warning"), .warning)
        XCTAssertEqual(AdvisorySeverity.fromRaw("info"), .info)
        XCTAssertEqual(AdvisorySeverity.fromRaw("something-else"), .info)
    }
}

/// Mirrors the KMP `WeatherContextBuilderTest` so Ariadne's Phase 2 weather
/// context (live vs Athens seasonal fallback, per-factor risk bands, dominant
/// state) resolves identically on iOS and Android/Web.
final class AriadneWeatherContextTests: XCTestCase {

    private func snapshot(tempC: Double, windKph: Double, code: Int) -> WeatherSnapshot {
        WeatherSnapshot(
            current: CurrentWeather(
                temperatureC: tempC,
                apparentC: tempC,
                weatherCode: code,
                isDay: true,
                windKph: windKph,
                humidity: 40,
                precipitationMm: 0.0
            ),
            placeName: "Athens",
            fetchedAt: Date(timeIntervalSince1970: 0)
        )
    }

    func test_liveHotDryIsHOT() {
        let ctx = WeatherContextBuilder.fromSnapshot(snapshot(tempC: 36.0, windKph: 8.0, code: 0))
        XCTAssertEqual(ctx.source, .live)
        XCTAssertEqual(ctx.state, .hot)
        XCTAssertEqual(ctx.heatRisk, .medium)
    }

    func test_liveThunderstormIsRAINYAndBeatsHeat() {
        // Hot AND stormy -> rain wins the dominant state.
        let ctx = WeatherContextBuilder.fromSnapshot(snapshot(tempC: 33.0, windKph: 10.0, code: 95))
        XCTAssertEqual(ctx.state, .rainy)
        XCTAssertEqual(ctx.rainRisk, .high)
    }

    func test_liveWindyMildIsWINDY() {
        let ctx = WeatherContextBuilder.fromSnapshot(snapshot(tempC: 21.0, windKph: 50.0, code: 0))
        XCTAssertEqual(ctx.state, .windy)
        XCTAssertEqual(ctx.windRisk, .high)
    }

    func test_liveMildCalmIsNORMAL() {
        let ctx = WeatherContextBuilder.fromSnapshot(snapshot(tempC: 22.0, windKph: 10.0, code: 1))
        XCTAssertEqual(ctx.state, .normal)
    }

    func test_noSnapshotFallsBackToSeasonalJulyHot() {
        let ctx = WeatherContextBuilder.resolve(snapshot: nil, month: 7)
        XCTAssertEqual(ctx.source, .seasonalFallback)
        XCTAssertEqual(ctx.state, .hot)
        // Seasonal has no live reading.
        XCTAssertNil(ctx.temperatureC)
        XCTAssertNil(ctx.condition)
        XCTAssertEqual(ctx.month, 7)
    }

    func test_noSnapshotWinterIsNormalButKnown() {
        let ctx = WeatherContextBuilder.resolve(snapshot: nil, month: 1)
        XCTAssertEqual(ctx.source, .seasonalFallback)
        XCTAssertEqual(ctx.state, .normal)
        XCTAssertTrue(ctx.isKnown)
    }

    func test_nothingAtAllIsUnknown() {
        let ctx = WeatherContextBuilder.resolve(snapshot: nil, month: nil)
        XCTAssertEqual(ctx.source, .unknown)
        XCTAssertFalse(ctx.isKnown)
    }

    func test_athensClimateJulyIsHot34() {
        let p = AthensClimate.profile(7)
        XCTAssertEqual(p.typicalHighC, 34)
        XCTAssertEqual(p.typicalState, .hot)
    }

    func test_riskThresholds() {
        XCTAssertEqual(WeatherContextBuilder.heatRisk(39.0), .high)
        XCTAssertEqual(WeatherContextBuilder.heatRisk(33.0), .medium)
        XCTAssertEqual(WeatherContextBuilder.heatRisk(25.0), .low)
        XCTAssertEqual(WeatherContextBuilder.windRisk(48.0), .high)
        XCTAssertEqual(WeatherContextBuilder.windRisk(12.0), .low)
    }
}

/// Mirrors the KMP `RouteRankerTest` so Ariadne's Phase 3 route ranking scores
/// and orders candidates identically on iOS and Android/Web. Synthetic
/// `JourneyPlanner.Plan` values stand in for real routes: only `totalMinutes` and
/// `transfers` (the two facts the ranker reads) matter, mirroring how the KMP test
/// builds `JourneyResult` with empty segments.
final class RouteRankerTests: XCTestCase {

    private func route(minutes: Int, transfers: Int) -> JourneyPlanner.Plan {
        JourneyPlanner.Plan(legs: [], totalMinutes: minutes, transfers: transfers)
    }

    private func candidate(minutes: Int, transfers: Int, exposure: Exposure) -> RouteCandidate {
        RouteCandidate(result: route(minutes: minutes, transfers: transfers), exposure: exposure)
    }

    private let hotDay = WeatherContext(source: .live, state: .hot, heatRisk: .high)
    private let calmDay = WeatherContext(source: .live, state: .normal)

    func test_fastest_prefers_fewer_minutes() {
        let fast = candidate(minutes: 20, transfers: 2, exposure: .sheltered)
        let slow = candidate(minutes: 30, transfers: 0, exposure: .sheltered)
        let best = RouteRanker.best([slow, fast], preference: .fastest, weather: calmDay)
        XCTAssertEqual(best?.candidate.result.totalMinutes, 20)
    }

    func test_fewest_changes_prefers_direct_even_if_slower() {
        let fastWithChange = candidate(minutes: 20, transfers: 1, exposure: .sheltered)
        let directSlower = candidate(minutes: 26, transfers: 0, exposure: .sheltered)
        let best = RouteRanker.best([fastWithChange, directSlower], preference: .fewestChanges, weather: calmDay)
        XCTAssertEqual(best?.candidate.result.transfers, 0)
    }

    func test_balanced_takes_faster_when_the_transfer_saving_is_small() {
        // 22 min direct vs 20 min with one change: 2 min isn't worth the change.
        let direct = candidate(minutes: 22, transfers: 0, exposure: .sheltered)
        let oneChange = candidate(minutes: 20, transfers: 1, exposure: .sheltered)
        let best = RouteRanker.best([oneChange, direct], preference: .balanced, weather: calmDay)
        XCTAssertEqual(best?.candidate.result.transfers, 0)
    }

    func test_hot_day_prefers_sheltered_over_a_slightly_faster_exposed_route() {
        // 28 min exposed (tram) vs 31 min sheltered (metro): on a hot day the
        // sheltered one should win despite being 3 min slower.
        let fastExposed = candidate(minutes: 28, transfers: 0, exposure: .exposed)
        let slowSheltered = candidate(minutes: 31, transfers: 0, exposure: .sheltered)
        let best = RouteRanker.best([fastExposed, slowSheltered], preference: .fastest, weather: hotDay)
        XCTAssertEqual(best?.candidate.exposure, .sheltered)
    }

    func test_calm_day_keeps_the_faster_exposed_route() {
        let fastExposed = candidate(minutes: 28, transfers: 0, exposure: .exposed)
        let slowSheltered = candidate(minutes: 31, transfers: 0, exposure: .sheltered)
        let best = RouteRanker.best([fastExposed, slowSheltered], preference: .fastest, weather: calmDay)
        XCTAssertEqual(best?.candidate.exposure, .exposed)
    }

    func test_weather_penalty_is_recorded_only_for_exposed_routes_in_adverse_weather() {
        let exposed = RouteRanker.rank(
            [candidate(minutes: 20, transfers: 0, exposure: .exposed)], preference: .fastest, weather: hotDay
        ).first!
        let sheltered = RouteRanker.rank(
            [candidate(minutes: 20, transfers: 0, exposure: .sheltered)], preference: .fastest, weather: hotDay
        ).first!
        XCTAssertEqual(sheltered.weatherPenalty, 0.0)
        XCTAssertTrue(exposed.weatherPenalty > 0.0)
    }

    func test_no_weather_context_means_no_penalty() {
        let scored = RouteRanker.rank(
            [candidate(minutes: 20, transfers: 0, exposure: .exposed)], preference: .fastest, weather: nil
        ).first!
        XCTAssertEqual(scored.weatherPenalty, 0.0)
    }
}

/// Albanian is first-class (large Athens community): the real bundled
/// vocabulary must carry Albanian / Latin station spellings so SQ input
/// resolves. Mirrors the KMP AlbanianVocabularyTest.
final class AlbanianVocabularyTests: XCTestCase {

    func test_bundledVocabularyCarriesAlbanianAirportAlias() {
        let vocab = AssistantVocabulary.fromSyrmosData()
        let hasAeroport = vocab.stations.contains { $0.names.contains("Aeroport") }
        XCTAssertTrue(hasAeroport, "expected an 'Aeroport' alias on the airport station in the bundled vocabulary")
    }

    func test_albanianAeroportParsesAsAirportTrip() {
        let parser = AthensTransitParser(vocabulary: .fromSyrmosData())
        // "I'm at Syntagma, I want airport, fast" in Albanian/Greeklish.
        guard case let .planTrip(_, to, _, preference) = parser.parse("jam te syntagma dua aeroport shpejt") else {
            return XCTFail("expected a plan trip")
        }
        XCTAssertNotNil(to, "airport destination should resolve from Albanian 'aeroport'")
        XCTAssertEqual(preference, .fastest)
    }
}
