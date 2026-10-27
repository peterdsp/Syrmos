import Foundation

// Syrmos 3.0.2 calendar leave-by (iOS mirror).
//
// Mirrors Kotlin `CalendarLeaveBy` (core/common) exactly: the event classifier
// (flight, long distance, venue, appointment, default), the arrival buffer and
// reliability margin, the leave-at plan with its notifications, eligibility, and
// the reschedule threshold. Pure: the caller passes every input, including now.
//
// Covered case-for-case by fixtures/calendar/leave-by.json (CalendarLeaveByTests).

enum CalendarEventKind: String, CaseIterable {
    case flight
    case longDistance
    case venue
    case appointment
    case `default`
}

enum CalendarTripState: String {
    case scheduled
    case leaveNow
    case late
    case past
}

enum CalendarNotificationKind: String {
    case flightChecklist
    case headsUp
    case leaveNow
}

struct CalendarFire: Equatable {
    let kind: CalendarNotificationKind
    let atEpochSeconds: Int64
}

/// The inputs `plan` needs, in the fixture's shape.
struct CalendarTripInput: Equatable {
    var title: String
    var location: String = ""
    var startEpochSeconds: Int64
    var journeyMinutes: Int
    var transfers: Int
    var firstLegHeadwayMinutes: Int
    var raining: Bool = false
    var alertOnRoute: Bool = false
    var flightBufferMinutes: Int?
}

struct CalendarTripPlan: Equatable {
    let kind: CalendarEventKind
    let bufferMinutes: Int
    let marginMinutes: Int
    let arriveByEpochSeconds: Int64
    let leaveAtEpochSeconds: Int64
    let state: CalendarTripState
    let notifications: [CalendarFire]
}

struct CalendarEligibilityInput: Equatable {
    var startEpochSeconds: Int64
    var allDay: Bool = false
    var declined: Bool = false
    var destinationResolved: Bool = true
}

enum CalendarLeaveBy {
    static let bufferMinutes: [CalendarEventKind: Int] = [
        .flight: 120, .longDistance: 25, .venue: 30, .appointment: 15, .default: 5,
    ]
    static let flightBufferChoices = [90, 120, 150]
    static let maxMargin = 20
    static let headsUpMinutes = 15
    static let flightChecklistMinutes = 180
    static let horizonSeconds: Int64 = 48 * 3600
    static let rescheduleThresholdSeconds: Int64 = 300

    /// (kind, substring keywords, whole-token keywords), already folded, in order.
    static let classes: [(CalendarEventKind, [String], [String])] = [
        (.flight,
         ["airport", "flight", "αεροδρομιο", "πτηση", "aeroporto", "volo", "fluturim", "aeroport", "venizelos", "βενιζελοσ", "makedonia airport"],
         ["ath", "skg"]),
        (.longDistance,
         ["ferry", "πλοιο", "λιμανι", "port of piraeus", "rafina", "ραφηνα", "lavrio", "λαυριο", "intercity", "hellenic train", "τρενο", "κτελ", "ktel", "σταθμοσ λαρισησ", "larissa station"],
         ["ose", "οσε"]),
        (.venue,
         ["οακα", "oaka", "stadium", "γηπεδο", "karaiskakis", "καραισκακη", "concert", "συναυλια", "theatre", "theater", "θεατρο", "megaron", "μεγαρο", "badminton", "cinema", "σινεμα", "ηρωδειο", "herodion", "festival", "φεστιβαλ"],
         []),
        (.appointment,
         ["doctor", "γιατρ", "hospital", "νοσοκομ", "clinic", "κλινικ", "exam", "εξετασ", "interview", "συνεντευξ", "dentist", "οδοντ", "embassy", "πρεσβεια"],
         ["dr", "kep", "κεπ"]),
    ]

    private static let foldMap: [Character: Character] = [
        "ά": "α", "έ": "ε", "ή": "η", "ί": "ι", "ό": "ο", "ύ": "υ", "ώ": "ω",
        "ϊ": "ι", "ΐ": "ι", "ϋ": "υ", "ΰ": "υ", "ς": "σ",
        "à": "a", "á": "a", "â": "a", "è": "e", "é": "e", "ê": "e", "ë": "e",
        "ì": "i", "í": "i", "î": "i", "ò": "o", "ó": "o", "ô": "o", "ù": "u", "ú": "u", "û": "u", "ç": "c",
    ]

    /// Lowercase, then strip Greek tonos/dialytika, final sigma and common Latin accents.
    static func fold(_ text: String) -> String {
        String(text.lowercased().map { foldMap[$0] ?? $0 })
    }

    private static func isTokenScalar(_ s: Unicode.Scalar) -> Bool {
        switch s.value {
        case 0x30...0x39, 0x61...0x7A, 0x03B1...0x03C9: return true
        default: return false
        }
    }

    static func tokens(_ folded: String) -> Set<String> {
        var out = Set<String>()
        var current = String.UnicodeScalarView()
        for scalar in folded.unicodeScalars {
            if isTokenScalar(scalar) {
                current.append(scalar)
            } else if !current.isEmpty {
                out.insert(String(current))
                current = String.UnicodeScalarView()
            }
        }
        if !current.isEmpty { out.insert(String(current)) }
        return out
    }

    static func classify(title: String, location: String) -> CalendarEventKind {
        let text = fold("\(title) \(location)")
        let toks = tokens(text)
        for (kind, subs, tokenKeys) in classes {
            if subs.contains(where: { text.contains($0) }) || tokenKeys.contains(where: { toks.contains($0) }) {
                return kind
            }
        }
        return .default
    }

    static func reliabilityMargin(transfers: Int, firstLegHeadway: Int, raining: Bool, alertOnRoute: Bool) -> Int {
        let halfHeadway = Int((Double(firstLegHeadway) / 2.0).rounded(.up))
        let m = transfers * 2 + halfHeadway + (raining ? 5 : 0) + (alertOnRoute ? 5 : 0)
        return min(maxMargin, m)
    }

    static func eligibility(_ e: CalendarEligibilityInput, now: Int64) -> String {
        if e.allDay { return "allDay" }
        if e.declined { return "declined" }
        if e.startEpochSeconds <= now { return "past" }
        if e.startEpochSeconds > now + horizonSeconds { return "beyondHorizon" }
        if !e.destinationResolved { return "unresolved" }
        return "eligible"
    }

    static func buffer(for kind: CalendarEventKind, flightBufferMinutes: Int?) -> Int {
        if kind == .flight, let choice = flightBufferMinutes, flightBufferChoices.contains(choice) { return choice }
        return bufferMinutes[kind] ?? 5
    }

    static func plan(_ e: CalendarTripInput, now: Int64) -> CalendarTripPlan {
        let kind = classify(title: e.title, location: e.location)
        let buf = buffer(for: kind, flightBufferMinutes: e.flightBufferMinutes)
        let margin = reliabilityMargin(transfers: e.transfers, firstLegHeadway: e.firstLegHeadwayMinutes,
                                       raining: e.raining, alertOnRoute: e.alertOnRoute)
        let arriveBy = e.startEpochSeconds - Int64(buf) * 60
        let leaveAt = arriveBy - Int64(e.journeyMinutes + margin) * 60
        let state: CalendarTripState
        if now < leaveAt { state = .scheduled }
        else if now < arriveBy { state = .leaveNow }
        else if now < e.startEpochSeconds { state = .late }
        else { state = .past }
        var fires: [CalendarFire] = []
        let checklistAt = leaveAt - Int64(flightChecklistMinutes) * 60
        if kind == .flight && checklistAt > now { fires.append(CalendarFire(kind: .flightChecklist, atEpochSeconds: checklistAt)) }
        let headsUpAt = leaveAt - Int64(headsUpMinutes) * 60
        if headsUpAt > now { fires.append(CalendarFire(kind: .headsUp, atEpochSeconds: headsUpAt)) }
        if leaveAt > now { fires.append(CalendarFire(kind: .leaveNow, atEpochSeconds: leaveAt)) }
        return CalendarTripPlan(kind: kind, bufferMinutes: buf, marginMinutes: margin,
                                arriveByEpochSeconds: arriveBy, leaveAtEpochSeconds: leaveAt,
                                state: state, notifications: fires)
    }

    static func needsReschedule(oldLeaveAt: Int64, newLeaveAt: Int64) -> Bool {
        abs(newLeaveAt - oldLeaveAt) >= rescheduleThresholdSeconds
    }
}
