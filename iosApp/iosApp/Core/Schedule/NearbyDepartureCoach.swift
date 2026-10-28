import Foundation

// Syrmos 3.0.2 nearby next-train coach (iOS mirror).
//
// Mirrors Kotlin `NearbyDepartureCoach` (core/common) exactly: walk minutes from
// distance, one coach case per (line, destination) direction, the headline
// direction, a deterministic copy variant per (station, Athens day), and the
// notification guards. Pure: the caller passes every input, including now.
//
// Covered case-for-case by fixtures/nearby/coach.json (NearbyDepartureCoachTests).

enum CoachCase: String, CaseIterable {
    case stationClosed
    case lastTrain
    case missedLast
    case relaxNextOneIsClose
    case hurry
    case longGapTakeItEasy
    case comfortable
    case noService
}

/// One upcoming departure for a direction, in whole minutes from now (0 = now).
struct CoachDeparture: Equatable {
    let minutes: Int
    let isLast: Bool
}

/// The coach's verdict for one direction, with the numbers its copy needs.
struct CoachResult: Equatable {
    let coachCase: CoachCase
    var nextMinutes: Int?
    var followingMinutes: Int?
    var waitMinutes: Int?
    var marginMinutes: Int?
    var tight: Bool?
}

/// Inputs for the notification guards, in the fixture's shape.
struct CoachGuardInput: Equatable {
    var enabled: Bool
    var goJourneyActive: Bool
    var athensHour: Int
    var speedKmh: Double?
    var lastNotifiedForStationEpochSeconds: Int64?
    var notificationsToday: Int
    var now: Int64
}

enum NearbyDepartureCoach {
    static let relaxMaxWait = 8
    static let hurryMaxMargin = 2
    static let headlinePriority: [CoachCase] = [
        .stationClosed, .lastTrain, .missedLast, .relaxNextOneIsClose,
        .hurry, .longGapTakeItEasy, .comfortable, .noService,
    ]
    static let cooldownSeconds: Int64 = 45 * 60
    static let maxPerDay = 4
    static let maxSpeedKmh = 25.0
    static let geofenceRadiusMeters = 1000.0
    static let monitoredStationCount = 20

    static func walkMinutes(distanceMeters: Double) -> Int {
        max(1, Int((distanceMeters * 1.3 / 80.0).rounded(.up)))
    }

    static func coachCase(walk: Int, departures: [CoachDeparture], stationClosed: Bool) -> CoachResult {
        if stationClosed { return CoachResult(coachCase: .stationClosed) }
        guard let first = departures.first else { return CoachResult(coachCase: .noService) }
        let catchable = departures.filter { $0.minutes >= walk }
        if first.minutes < walk {
            guard let next = catchable.first else {
                let anyLast = first.isLast || (departures.last?.isLast ?? false)
                return CoachResult(coachCase: anyLast ? .missedLast : .noService, nextMinutes: first.minutes)
            }
            if next.isLast {
                return CoachResult(coachCase: .lastTrain, nextMinutes: next.minutes,
                                   tight: next.minutes - walk <= hurryMaxMargin)
            }
            let wait = next.minutes - walk
            return CoachResult(coachCase: wait <= relaxMaxWait ? .relaxNextOneIsClose : .longGapTakeItEasy,
                               nextMinutes: first.minutes, followingMinutes: next.minutes, waitMinutes: wait)
        }
        let margin = first.minutes - walk
        if first.isLast {
            return CoachResult(coachCase: .lastTrain, nextMinutes: first.minutes, tight: margin <= hurryMaxMargin)
        }
        if margin <= hurryMaxMargin {
            return CoachResult(coachCase: .hurry, nextMinutes: first.minutes, marginMinutes: margin)
        }
        return CoachResult(coachCase: .comfortable, nextMinutes: first.minutes, marginMinutes: margin)
    }

    /// Index of the direction whose case ranks first; ties keep the lowest index.
    static func headlineIndex(_ cases: [CoachCase]) -> Int? {
        var best: Int?
        for (i, c) in cases.enumerated() {
            if let b = best {
                if priority(c) < priority(cases[b]) { best = i }
            } else {
                best = i
            }
        }
        return best
    }

    private static func priority(_ c: CoachCase) -> Int { headlinePriority.firstIndex(of: c) ?? headlinePriority.count }

    static func fnv1a32(_ text: String) -> UInt32 {
        var h: UInt32 = 0x811C9DC5
        for b in text.utf8 {
            h ^= UInt32(b)
            h = h &* 0x01000193
        }
        return h
    }

    static func variantIndex(stationId: String, athensDate: String, variantCount: Int) -> Int {
        guard variantCount > 0 else { return 0 }
        return Int(fnv1a32("\(stationId)|\(athensDate)") % UInt32(variantCount))
    }

    /// "allow" or the first blocking reason, in the fixture's exact order.
    static func guardReason(_ g: CoachGuardInput) -> String {
        if !g.enabled { return "disabled" }
        if g.goJourneyActive { return "goActive" }
        if g.athensHour >= 23 || g.athensHour < 7 { return "quietHours" }
        if let speed = g.speedKmh, speed > maxSpeedKmh { return "moving" }
        if let last = g.lastNotifiedForStationEpochSeconds, g.now - last < cooldownSeconds { return "cooldown" }
        if g.notificationsToday >= maxPerDay { return "dailyCap" }
        return "allow"
    }
}
