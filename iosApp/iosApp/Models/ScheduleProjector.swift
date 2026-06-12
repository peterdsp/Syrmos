import Foundation

/// Projects next departures from API-synced frequency bands.
///
/// Source of truth path. Mirrors `ComputeDeparturesFromBandsUseCase` in
/// `core/domain`. When `SyrmosSchedulesStore.bundles` is empty (offline cold
/// start, sync not done yet) the caller falls back to `SyrmosData.sampleDepartures`
/// so the screen is never blank.
///
/// Wire facts encoded here (all from the package):
/// - M3 city closes 00:30 Sun-Thu, extends to 02:00 Fri, 24/7 on Sat
/// - M3 airport branch (M3_AIR) closes 23:00 every day, no exceptions
/// - mon_thu / fri / sat / sun day-types
/// - aug_15, dec_24_31 specials and Sun-equivalent holidays
@MainActor
enum ScheduleProjector {
    static func nextDepartures(
        for stationId: String,
        lineIds: [String],
        limit: Int = 8
    ) -> [Departure] {
        let store = SyrmosSchedulesStore.shared
        let bundles = store.service.bundles
        if bundles.isEmpty { return [] }

        let athens = TimeZone(identifier: "Europe/Athens")!
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = athens
        let now = Date()
        let nowComp = cal.dateComponents([.year, .month, .day, .hour, .minute, .weekday], from: now)
        let nowMinutes = (nowComp.hour ?? 0) * 60 + (nowComp.minute ?? 0)
        let holidayDayType = resolveHolidayDayType(month: nowComp.month ?? 1, day: nowComp.day ?? 1)

        // Resolve which set of M3 bundles to use for this station.
        let resolvedLineIds = expandLineIds(stationId: stationId, lineIds: lineIds)

        var results: [Departure] = []
        for lineId in resolvedLineIds {
            guard let bundle = bundles[lineId] else { continue }
            project(
                bundle: bundle,
                weekday: nowComp.weekday ?? 1,  // 1 = Sunday
                nowMinutes: nowMinutes,
                holidayDayType: holidayDayType,
                lineId: lineId,
                stationId: stationId,
                limit: limit,
                into: &results
            )
        }
        return results
            .sorted { $0.minutesAway < $1.minutesAway }
            .prefix(limit)
            .map { $0 }
    }

    // MARK: - M3 airport branch handling

    private static let line3AirportOnlyStations: Set<String> = [
        "M3_PAL", "M3_PEK", "M3_KRP", "M3_AER"
    ]

    private static func expandLineIds(stationId: String, lineIds: [String]) -> [String] {
        var out: [String] = []
        for lid in lineIds {
            if lid == "M3" || lid == "M3A" {
                if line3AirportOnlyStations.contains(stationId) {
                    out.append("M3_AIR")
                } else {
                    out.append("M3")
                    out.append("M3_AIR")  // city stations also see airport-bound trains
                }
            } else {
                out.append(lid)
            }
        }
        return out
    }

    // MARK: - Holiday lookup

    private static func resolveHolidayDayType(month: Int, day: Int) -> String? {
        let key = String(format: "%02d-%02d", month, day)
        switch key {
        case "01-01", "05-01", "10-28", "12-25", "12-26": return "sun"
        case "08-15": return "aug_15"
        case "12-24", "12-31": return "dec_24_31"
        case "01-02", "01-06", "11-17": return "sat"
        default: return nil
        }
    }

    // MARK: - Core projection

    private static func project(
        bundle: SyrmosSchedulesService.LineSchedule,
        weekday: Int,
        nowMinutes: Int,
        holidayDayType: String?,
        lineId: String,
        stationId: String,
        limit: Int,
        into out: inout [Departure]
    ) {
        // Descriptors to try: today's day-type at offset 0, plus yesterday's at
        // offset -24h if we're in the early-morning tail of the previous service day.
        var descriptors: [(String, Int)] = [(dayType(for: weekday, holiday: holidayDayType), 0)]
        if nowMinutes < 4 * 60 {
            let yesterdayWeekday = weekday == 1 ? 7 : weekday - 1
            descriptors.append((dayType(for: yesterdayWeekday, holiday: nil), -24 * 60))
        }

        for (dt, shift) in descriptors {
            // Honor schedule_rules. If the line has no rule for this day type
            // OR the current time falls outside [open, close], skip — the line
            // is closed and shouldn't emit any departure (this is the bug that
            // showed T7 every 12 min at 03:00 on a weekday).
            guard let rule = bundle.rules.first(where: { $0.dayType == dt }) else { continue }
            let openMin = minutesOfDay(rule.openTime)
            let closeMin = minutesOfDay(rule.closeTime)
            if !rule.is247, let openM = openMin, let closeM = closeMin {
                let effectiveClose = closeM <= openM ? closeM + 24 * 60 : closeM
                let effectiveNow = nowMinutes + shift
                if effectiveNow < openM || effectiveNow > effectiveClose { continue }
            }

            let bands = bundle.bands
                .filter { $0.dayType == dt }
                .sorted { (a, b) in
                    let am = minutesOfDay(a.timeStart) ?? 0
                    let bm = minutesOfDay(b.timeStart) ?? 0
                    return am < bm
                }
            // Project both directions per band so T6/T7 inbound (35/59 min)
            // and outbound (33/54 min) asymmetric runtimes resolve correctly,
            // and so passengers see both upcoming destinations at every stop
            // instead of just the line's outbound terminal.
            let directions = directionStreams(for: lineId)
            for band in bands {
                for stream in directions {
                    projectBand(
                        band: band,
                        shift: shift,
                        nowMinutes: nowMinutes,
                        lineId: lineId,
                        stationId: stationId,
                        directionKey: stream.key,
                        directionLabel: stream.label,
                        limit: limit - out.count,
                        into: &out
                    )
                    if out.count >= limit { return }
                }
            }
        }
    }

    private struct DirectionStream {
        let key: String       // "outbound" / "inbound" / "airport"
        let label: String     // "Kifissia" / "Piraeus" / "Airport"
    }

    private static func directionStreams(for lineId: String) -> [DirectionStream] {
        if lineId == "M3_AIR" {
            return [DirectionStream(key: "outbound", label: "Airport")]
        }
        let display = lineId.hasPrefix("M3") ? "M3" : lineId
        guard let line = SyrmosData.line(for: display) else {
            return [DirectionStream(key: "outbound", label: "")]
        }
        return [
            DirectionStream(key: "outbound", label: line.terminalB),
            DirectionStream(key: "inbound",  label: line.terminalA),
        ]
    }

    private static func projectBand(
        band: SyrmosSchedulesService.BandEntry,
        shift: Int,
        nowMinutes: Int,
        lineId: String,
        stationId: String,
        directionKey: String,
        directionLabel: String,
        limit: Int,
        into out: inout [Departure]
    ) {
        guard let rawStart = minutesOfDay(band.timeStart),
              let rawEnd = minutesOfDay(band.timeEnd),
              band.headwayMinutes > 0
        else { return }
        let start = rawStart + shift
        let end = rawEnd + shift
        guard end >= start else { return }

        // Shift every projected slot by the station's cumulative minutes
        // from the line origin for THIS direction. For T6/T7 the inbound
        // and outbound offsets differ (33/35 min, 54/59 min), so picking
        // by direction not "best effort" is what makes the asymmetry land
        // accurately. Source: STASY's /api/station-offsets.
        let offsetMin = SyrmosStationOffsetsStore.shared.offsetMinutes(
            lineId: lineId, direction: directionKey, stationId: stationId
        )

        var slot = Double(start)
        if slot < Double(nowMinutes) {
            let skips = max(0, Int((Double(nowMinutes) - slot) / band.headwayMinutes))
            slot = Double(start) + Double(skips) * band.headwayMinutes
            while slot < Double(nowMinutes) { slot += band.headwayMinutes }
        }
        var added = 0
        while slot <= Double(end) && added < limit {
            let slotMin = Int(slot.rounded()) + offsetMin
            let display = ((slotMin % (24 * 60)) + 24 * 60) % (24 * 60)
            let h = display / 60
            let m = display % 60
            let mins = max(0, slotMin - nowMinutes)
            out.append(Departure(
                time: String(format: "%02d:%02d", h, m),
                lineId: displayLineId(for: lineId),
                direction: directionLabel,
                minutesAway: mins,
                serviceType: serviceTypeLabel(for: lineId, label: band.label)
            ))
            slot += band.headwayMinutes
            added += 1
        }
    }

    private static func dayType(for weekday: Int, holiday: String?) -> String {
        if let h = holiday { return h }
        switch weekday {
        case 1: return "sun"
        case 2, 3, 4, 5: return "mon_thu"
        case 6: return "fri"
        case 7: return "sat"
        default: return "mon_thu"
        }
    }

    private static func minutesOfDay(_ hhmm: String) -> Int? {
        let parts = hhmm.split(separator: ":")
        guard parts.count == 2,
              let h = Int(parts[0]),
              let m = Int(parts[1])
        else { return nil }
        return h * 60 + m
    }

    private static func displayLineId(for storedLineId: String) -> String {
        // M3 and M3_AIR share the "Line 3" UI label.
        if storedLineId.hasPrefix("M3") { return "M3" }
        return storedLineId
    }

    private static func serviceTypeLabel(for lineId: String, label: String) -> String {
        if lineId == "M3_AIR" { return "airport" }
        if label.contains("late") || label.contains("overnight") { return "late_night" }
        return "regular"
    }
}
