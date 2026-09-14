import Foundation

/// Syrmos 3.0 Phase N J09 saved-departure board (iOS store).
///
/// Mirrors Kotlin `SavedDeparture` / `SavedDepartureStore` / `ReminderContract`
/// and the web store in `web-reminders.js`. A saved departure is the persisted,
/// id-anchored record for a leave-by reminder; the pure timing lives in the
/// `LeaveByReminder` engine, which a record maps onto for reconciliation.
///
/// `createdAt` is kept as the ISO-8601 string it persists as, so the encoded blob
/// round-trips byte-for-byte with the Kotlin serializer without date-format drift.
/// Validated against fixtures/reminders/saved.json.
struct SavedDeparture: Equatable {
    let lineId: String
    let stationId: String
    let stationName: String
    let destination: String
    /// Scheduled clock time, HH:MM, for display.
    let scheduledTime: String
    /// Unix epoch second the train departs.
    let departureEpochSeconds: Int64
    /// Seconds the rider needs before departure (walk + buffer).
    let leadSeconds: Int64
    /// ISO-8601 instant the rider saved it (opaque; ordering/tie-breaks only).
    let createdAt: String
    var schemaVersion: Int = ReminderContract.schemaVersion

    /// Stable dedup key, identical to the engine's LeaveByReminder id.
    var id: String { "\(lineId)|\(stationId)|\(departureEpochSeconds)" }

    /// Map onto the engine's reminder type (drops persistence-only fields).
    func toReminder() -> LeaveByReminder {
        LeaveByReminder(lineId: lineId, stationId: stationId, stationName: stationName,
                        destination: destination, scheduledTime: scheduledTime,
                        departureEpochSeconds: departureEpochSeconds, leadSeconds: leadSeconds)
    }
}

enum ReminderDecodeResult: Equatable {
    case ok([SavedDeparture])
    case unsupported(Int)
    case corrupt(String)
}

enum ReminderContract {
    static let schemaVersion = 1

    /// Canonical wire form: fixed key order, compact, matching the Kotlin
    /// serializer so the persisted blob round-trips byte-for-byte.
    static func encodeRoot(_ list: [SavedDeparture]) -> String {
        let items = list.map { d -> String in
            "{" +
            "\"lineId\":\(jsonString(d.lineId))," +
            "\"stationId\":\(jsonString(d.stationId))," +
            "\"stationName\":\(jsonString(d.stationName))," +
            "\"destination\":\(jsonString(d.destination))," +
            "\"scheduledTime\":\(jsonString(d.scheduledTime))," +
            "\"departureEpochSeconds\":\(d.departureEpochSeconds)," +
            "\"leadSeconds\":\(d.leadSeconds)," +
            "\"createdAt\":\(jsonString(d.createdAt))," +
            "\"schemaVersion\":\(d.schemaVersion)" +
            "}"
        }.joined(separator: ",")
        return "{\"schemaVersion\":\(schemaVersion),\"savedDepartures\":[\(items)]}"
    }

    /// Decode a persisted blob. Blank is a fresh empty list (not corrupt); a
    /// newer schema is unsupported, not corrupt.
    static func decodeRoot(_ raw: String) -> ReminderDecodeResult {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return .ok([]) }
        guard let data = trimmed.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return .corrupt("not json")
        }
        if let v = obj["schemaVersion"] as? Int, v > schemaVersion { return .unsupported(v) }
        guard let arr = obj["savedDepartures"] as? [[String: Any]] else { return .ok([]) }
        var out: [SavedDeparture] = []
        for e in arr {
            guard let lineId = e["lineId"] as? String,
                  let stationId = e["stationId"] as? String,
                  let stationName = e["stationName"] as? String,
                  let destination = e["destination"] as? String,
                  let scheduledTime = e["scheduledTime"] as? String,
                  let dep = (e["departureEpochSeconds"] as? NSNumber)?.int64Value,
                  let lead = (e["leadSeconds"] as? NSNumber)?.int64Value,
                  let createdAt = e["createdAt"] as? String else {
                return .corrupt("missing field")
            }
            let sv = (e["schemaVersion"] as? Int) ?? schemaVersion
            out.append(SavedDeparture(lineId: lineId, stationId: stationId, stationName: stationName,
                                      destination: destination, scheduledTime: scheduledTime,
                                      departureEpochSeconds: dep, leadSeconds: lead,
                                      createdAt: createdAt, schemaVersion: sv))
        }
        return .ok(out)
    }

    private static func jsonString(_ s: String) -> String {
        // JSONSerialization on a one-element array is the simplest correct escaper.
        if let d = try? JSONSerialization.data(withJSONObject: [s]),
           let str = String(data: d, encoding: .utf8) {
            // strip the surrounding [ ]
            return String(str.dropFirst().dropLast())
        }
        return "\"\(s)\""
    }
}

/// Pure list operations for the saved-departure board, mirroring the Kotlin
/// SavedDepartureStore (display order == array order, dedupe by id).
enum SavedDepartureStore {

    static func save(_ list: [SavedDeparture], _ entry: SavedDeparture) -> [SavedDeparture] {
        [entry] + list.filter { $0.id != entry.id }
    }

    static func remove(_ list: [SavedDeparture], id: String) -> [SavedDeparture] {
        list.filter { $0.id != id }
    }

    static func contains(_ list: [SavedDeparture], id: String) -> Bool {
        list.contains { $0.id == id }
    }

    static func pruneDeparted(_ list: [SavedDeparture], now nowEpochSeconds: Int64) -> [SavedDeparture] {
        list.filter { $0.departureEpochSeconds > nowEpochSeconds }
    }

    static func reorder(_ list: [SavedDeparture], order: [String]) -> [SavedDeparture] {
        var byId: [String: SavedDeparture] = [:]
        for d in list { byId[d.id] = d }
        return order.compactMap { byId[$0] }
    }

    static func toReminders(_ list: [SavedDeparture]) -> [LeaveByReminder] {
        list.map { $0.toReminder() }
    }
}
