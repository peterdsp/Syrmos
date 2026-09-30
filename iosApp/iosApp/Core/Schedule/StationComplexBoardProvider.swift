import Foundation

// MARK: - Registry

/// The reviewed station-complex registry, bundled as `station-complexes.json`.
///
/// Membership is reviewed data, never name similarity, rider distance or
/// proximity alone. See `scripts/build-station-complexes.mjs` for the rules and
/// the recorded reason behind each name-differing merge and each deliberate
/// split.
enum StationComplexRegistry {
    static let complexes: [StationComplexBoard.Complex] = load()

    private static let byStopId: [String: StationComplexBoard.Complex] = {
        var out: [String: StationComplexBoard.Complex] = [:]
        for complex in complexes {
            for stopId in complex.memberStopIds { out[stopId] = complex }
        }
        return out
    }()

    static func complex(forStopId stopId: String) -> StationComplexBoard.Complex? {
        byStopId[stopId]
    }

    /// The complex a map node belongs to. A node the registry does not know
    /// becomes a single-station complex of its own boarding stops, so every
    /// station renders through the same board.
    static func complex(for node: MapStationNode) -> StationComplexBoard.Complex? {
        let ids = node.stationIds.isEmpty ? [node.id] : node.stationIds
        for stopId in ids {
            if let found = complex(forStopId: stopId) { return found }
        }
        var areas: [StationComplexBoard.Complex.Area] = []
        for stopId in ids {
            for lineId in SyrmosLineStops.boardingLines[stopId] ?? [] {
                let areaId = areaId(forLineId: lineId)
                if let existing = areas.firstIndex(where: { $0.id == areaId }) {
                    if !areas[existing].stopIds.contains(stopId) {
                        let merged = areas[existing].stopIds + [stopId]
                        areas[existing] = area(areaId, stopIds: merged)
                    }
                } else {
                    areas.append(area(areaId, stopIds: [stopId]))
                }
            }
        }
        guard !areas.isEmpty else { return nil }
        return StationComplexBoard.Complex(
            id: "NODE:" + node.id,
            name: node.name, nameEl: node.nameEl.isEmpty ? node.name : node.nameEl,
            nameSq: node.name, nameIt: node.name,
            areas: areas, synthetic: true
        )
    }

    static func areaId(forLineId lineId: String) -> String {
        switch SyrmosData.line(for: lineId)?.type {
        case .metro: return "metro"
        case .tram: return "tram"
        case .bus: return "bus"
        default: return "rail"
        }
    }

    private static let areaLabels: [String: (String, String, String, String)] = [
        "metro": ("Metro", "Μετρό", "Metro", "Metropolitana"),
        "tram": ("Tram", "Τραμ", "Tramvaj", "Tram"),
        "rail": ("Railway station", "Σιδηροδρομικός σταθμός", "Stacioni hekurudhor", "Stazione ferroviaria"),
        "bus": ("Rail-replacement bus", "Λεωφορείο αντικατάστασης", "Autobus zëvendësues", "Bus sostitutivo"),
    ]

    private static func area(_ id: String, stopIds: [String]) -> StationComplexBoard.Complex.Area {
        let labels = areaLabels[id] ?? (id, id, id, id)
        return .init(id: id, name: labels.0, nameEl: labels.1, nameSq: labels.2, nameIt: labels.3,
                     stopIds: stopIds)
    }

    private static func load() -> [StationComplexBoard.Complex] {
        struct Payload: Decodable {
            struct Complex: Decodable {
                let id: String
                let name: String
                let nameEl: String
                let nameSq: String?
                let nameIt: String?
                let areas: [Area]
            }
            struct Area: Decodable {
                let id: String
                let name: String
                let nameEl: String
                let nameSq: String?
                let nameIt: String?
                let stopIds: [String]
            }
            let complexes: [Complex]
        }
        // Search every bundle that could carry the seed, not only `Bundle.main`:
        // under a test host the main bundle is the host app, and a resource that
        // silently fails to resolve would leave the registry empty, which
        // quietly turns Athens back into five unrelated stops. Losing the
        // registry must be loud, not invisible.
        guard let url = SyrmosSeedBundle.url(named: "station-complexes"),
              let data = try? Data(contentsOf: url)
        else {
            // Not fatal: an empty registry degrades to per-station boards. It is
            // logged loudly because silently empty turns Athens back into five
            // unrelated stops with no visible failure.
            print("SYRMOS: station-complexes.json not found. \(SyrmosSeedBundle.seedDirectoryListing())")
            return []
        }
        let payload: Payload
        do {
            payload = try JSONDecoder().decode(Payload.self, from: data)
        } catch {
            print("SYRMOS: station-complexes.json failed to decode: \(error)")
            return []
        }
        return payload.complexes.map { c in
            StationComplexBoard.Complex(
                id: c.id, name: c.name, nameEl: c.nameEl,
                nameSq: c.nameSq ?? c.name, nameIt: c.nameIt ?? c.name,
                areas: c.areas.map {
                    .init(id: $0.id, name: $0.name, nameEl: $0.nameEl,
                          nameSq: $0.nameSq ?? $0.name, nameIt: $0.nameIt ?? $0.name,
                          stopIds: $0.stopIds)
                }
            )
        }
    }
}

// MARK: - Provider

/// Feeds the pure `StationComplexBoard` from the bundled schedules.
///
/// Published trips give real destinations and trip identity; frequency bands
/// give an honest headway estimate for the metro and tram corridors, one slot
/// per direction the stop can actually leave in, with the station offset applied
/// so Larissa Station shows its own departure minute rather than the terminal's.
@MainActor
enum StationComplexBoardProvider {

    static func board(
        for node: MapStationNode,
        windowMinutes: Int = 12 * 60,
        maxTimesPerGroup: Int = 3
    ) -> StationComplexBoard.Board? {
        guard let complex = StationComplexRegistry.complex(for: node) else { return nil }
        return board(for: complex, windowMinutes: windowMinutes, maxTimesPerGroup: maxTimesPerGroup)
    }

    static func board(
        for complex: StationComplexBoard.Complex,
        windowMinutes: Int = 12 * 60,
        maxTimesPerGroup: Int = 3
    ) -> StationComplexBoard.Board {
        let bundles = SyrmosSchedulesStore.shared.service.bundles
        var candidates: [StationComplexBoard.Candidate] = []
        var coverage: [StationComplexBoard.CoverageEntry] = []

        for area in complex.areas {
            for stopId in area.stopIds {
                for lineId in SyrmosLineStops.boardingLines[stopId] ?? [] {
                    guard let line = SyrmosData.line(for: lineId) else { continue }
                    // Track that carries no service cannot produce a departure,
                    // but it is still a real service with an honest status.
                    guard line.isOperational else {
                        coverage.append(.init(areaId: area.id, stopId: stopId, operatorId: "",
                                              lineId: lineId, patternKey: "", destination: "",
                                              state: .notOperating, reason: line.status.rawValue))
                        continue
                    }
                    let hasBundle = bundles[lineId] != nil
                        || (lineId == "M3" && bundles["M3_AIR"] != nil)

                    // One projector call per (boarding stop, line). The existing
                    // projector already owns the service calendar, both DST
                    // changes, the overnight wrap, per-direction station offsets
                    // and the published short-turn overrides, so the board reuses
                    // it rather than keeping a second copy of those rules.
                    //
                    // The limit is per line at ONE stop, so a frequent metro line
                    // cannot starve a rare railway line: those are separate calls
                    // and separate groups. It is high enough that both directions
                    // of a 4-minute headway still reach the 12-hour horizon's
                    // first hours, which is all a board of three times per group
                    // can show.
                    let projected = ScheduleProjector.nextDepartures(
                        for: stopId,
                        lineIds: [lineId],
                        limit: projectionLimit,
                        timeHorizonMinutes: windowMinutes
                    )
                    let operatorId = line.region == .national ? "hellenic_train" : ""
                    var produced = 0
                    for dep in projected where dep.minutesAway <= windowMinutes {
                        candidates.append(.init(
                            stopId: stopId,
                            areaId: area.id,
                            lineId: dep.lineId.isEmpty ? lineId : dep.lineId,
                            operatorId: operatorId,
                            destination: dep.direction,
                            patternKey: dep.serviceType == "airport" ? "airport" : "",
                            // Provider-qualified so two lines cannot collide on a
                            // train number.
                            tripId: dep.trainNo.map { "\(lineId):\($0)" },
                            time: dep.time,
                            absoluteMinutes: dep.minutesAway,
                            source: dep.sourceConfidence,
                            trainNo: dep.trainNo,
                            serviceType: dep.serviceType
                        ))
                        produced += 1
                    }

                    let state: StationComplexBoard.Coverage = !hasBundle
                        ? .unavailable
                        : (produced > 0 ? .loaded : .noDepartureInWindow)
                    // A service with nothing in the window still names the
                    // destinations it serves, so the row reads "Leianokladi, no
                    // departure in the next 12 hours" and not a bare line id.
                    let reachable: [String] = state == .loaded
                        ? [""]
                        : SyrmosLineStops.directions(lineId: lineId, stopId: stopId).map(\.destination)
                    for destination in (reachable.isEmpty ? [""] : reachable) {
                        coverage.append(.init(
                            areaId: area.id, stopId: stopId, operatorId: operatorId, lineId: lineId,
                            patternKey: "", destination: destination, state: state,
                            reason: hasBundle ? nil : "schedule_unavailable"
                        ))
                    }
                }
            }
        }

        return StationComplexBoard.build(
            complex: complex,
            candidates: candidates,
            coverage: coverage,
            windowMinutes: windowMinutes,
            maxTimesPerGroup: maxTimesPerGroup,
            generatedAt: SyrmosClock.now
        )
    }

    /// Per (stop, line) projection depth. Deliberately generous: the cap exists
    /// to bound work, never to decide which destinations exist.
    private static let projectionLimit = 120
}
