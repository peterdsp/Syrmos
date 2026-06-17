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
        limit: Int = 8,
        dayOffset: Int = 0
    ) -> [Departure] {
        let store = SyrmosSchedulesStore.shared
        let bundles = store.service.bundles
        if bundles.isEmpty { return [] }

        let athens = TimeZone(identifier: "Europe/Athens")!
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = athens
        let targetDate = cal.date(byAdding: .day, value: dayOffset, to: Date()) ?? Date()
        let nowComp = cal.dateComponents([.year, .month, .day, .hour, .minute, .weekday], from: targetDate)
        // For "today" we anchor at the current minute (next departures
        // from now). For a future day we anchor at 00:00 so the user
        // sees the entire day's schedule from the first slot, not just
        // from whatever the clock happens to be.
        let nowMinutes = dayOffset == 0
            ? (nowComp.hour ?? 0) * 60 + (nowComp.minute ?? 0)
            : 0
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
        var sorted = results
            .sorted { $0.minutesAway < $1.minutesAway }
            .prefix(limit)
            .map { $0 }

        // M3 city stations: guarantee at least one Airport-bound entry is
        // visible. M3_AIR closes at 23:00 every day, so after the last
        // airport train the city-section trains still keep listing while
        // the user has no signal of when the next Airport-bound is. Scan
        // forward up to a week to find the next M3_AIR departure and
        // append it as a "look-ahead" row — caller / UI can present it
        // however it wants because the serviceType is "airport".
        let wantsAirport = resolvedLineIds.contains("M3_AIR")
            && !line3AirportOnlyStations.contains(stationId)
        let hasAirport = sorted.contains { $0.serviceType == "airport" }
        if wantsAirport && !hasAirport, let bundle = bundles["M3_AIR"] {
            if let lookahead = nextAirportLookahead(
                bundle: bundle,
                weekday: nowComp.weekday ?? 1,
                nowMinutes: nowMinutes,
                holidayMonth: nowComp.month ?? 1,
                holidayDay: nowComp.day ?? 1,
                stationId: stationId
            ) {
                sorted.append(lookahead)
            }
        }
        return sorted
    }

    /// Scans forward up to 7 days for the next M3_AIR departure when the
    /// regular projection window has nothing left for today. Caps the
    /// search so a missing bundle never spins forever.
    private static func nextAirportLookahead(
        bundle: SyrmosSchedulesService.LineSchedule,
        weekday: Int,
        nowMinutes: Int,
        holidayMonth: Int,
        holidayDay: Int,
        stationId: String
    ) -> Departure? {
        let athens = TimeZone(identifier: "Europe/Athens")!
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = athens
        let now = Date()
        for dayOffset in 0..<7 {
            guard let date = cal.date(byAdding: .day, value: dayOffset, to: now) else { continue }
            let comp = cal.dateComponents([.month, .day, .weekday], from: date)
            let holiday = resolveHolidayDayType(month: comp.month ?? holidayMonth, day: comp.day ?? holidayDay)
            let dt = dayType(for: comp.weekday ?? weekday, holiday: holiday)
            // Same gating the normal projector uses. Skip the day if the
            // rule says the line is closed.
            guard let rule = bundle.rules.first(where: { $0.dayType == dt }) else { continue }
            let openMin = minutesOfDay(rule.openTime)
            let closeMin = minutesOfDay(rule.closeTime)
            let nowForDay = dayOffset == 0 ? nowMinutes : -1
            if !rule.is247, let openM = openMin, let closeM = closeMin {
                let effectiveClose = closeM <= openM ? closeM + 24 * 60 : closeM
                if dayOffset == 0 && nowForDay > effectiveClose { continue }
            }
            let bands = bundle.bands
                .filter { $0.dayType == dt }
                .sorted { (a, b) in
                    let am = minutesOfDay(a.timeStart) ?? 0
                    let bm = minutesOfDay(b.timeStart) ?? 0
                    return am < bm
                }
            let offsetMin = SyrmosStationOffsetsStore.shared.offsetMinutes(
                lineId: "M3_AIR", direction: "outbound", stationId: stationId
            )
            for band in bands {
                guard let rawStart = minutesOfDay(band.timeStart),
                      let rawEnd = minutesOfDay(band.timeEnd),
                      band.headwayMinutes > 0
                else { continue }
                let end = rawEnd + (rawEnd < rawStart ? 24 * 60 : 0)
                var slot = Double(rawStart)
                if dayOffset == 0, slot < Double(nowMinutes) {
                    let skips = max(0, Int((Double(nowMinutes) - slot) / band.headwayMinutes))
                    slot = Double(rawStart) + Double(skips) * band.headwayMinutes
                    while slot < Double(nowMinutes) { slot += band.headwayMinutes }
                }
                if slot <= Double(end) {
                    let totalMinutes = Int(slot.rounded()) + offsetMin + dayOffset * 24 * 60
                    let display = ((totalMinutes % (24 * 60)) + 24 * 60) % (24 * 60)
                    let h = display / 60
                    let m = display % 60
                    let mins = max(0, totalMinutes - nowMinutes)
                    return Departure(
                        time: String(format: "%02d:%02d", h, m),
                        lineId: "M3",
                        direction: "Airport",
                        minutesAway: mins,
                        serviceType: "airport"
                    )
                }
            }
        }
        return nil
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
        // Descriptors to try, in order:
        //  1. today's day-type, no shift.
        //  2. yesterday's day-type with -24h shift — for bands that wrap
        //     past midnight (e.g. 'sat 22:00 -> 00:20' covers Sunday 00:15).
        //  3. yesterday's day-type with no shift — for bands whose
        //     timeStart is already in today's clock domain because the
        //     operator tags them under yesterday's service-day. OASA does
        //     this for Saturday's 24/7 overnight: 'sat 00:30 -> 05:30
        //     saturday_overnight_24_7' literally means Sunday clock
        //     00:30-05:30 inside Saturday's service window. Without this
        //     descriptor, Sunday 03:33 finds nothing because the regular
        //     Sunday rule isn't open yet (05:30 -> 00:30) and Saturday's
        //     shifted bands all sit at negative minutes.
        // Yesterday's "next-day extension" bands have clock times in the
        // small-hours of today (e.g. 'sat 00:30 -> 05:30 saturday_overnight').
        // We only want THOSE under shift=0, not regular daytime bands of
        // yesterday like 'sat 05:30 -> 10:00'. Cutoff at 05:00 because OASA's
        // overnight extensions all end by 05:30 and normal Saturday morning
        // bands start at 05:30 onwards.
        let nextDayExtensionCutoffMinutes = 5 * 60
        let todayDayType = dayType(for: weekday, holiday: holidayDayType)
        struct Descriptor { let dt: String; let shift: Int; let nextDayOnly: Bool }
        var descriptors: [Descriptor] = [Descriptor(dt: todayDayType, shift: 0, nextDayOnly: false)]
        if nowMinutes < 6 * 60 {
            let yesterdayWeekday = weekday == 1 ? 7 : weekday - 1
            let yesterdayDayType = dayType(for: yesterdayWeekday, holiday: nil)
            descriptors.append(Descriptor(dt: yesterdayDayType, shift: -24 * 60, nextDayOnly: false))
            if yesterdayDayType != todayDayType {
                descriptors.append(Descriptor(dt: yesterdayDayType, shift: 0, nextDayOnly: true))
            }
        }

        // Per-line accumulator. Each (band × direction) appends here without
        // a running cap; we sort + prefix(limit) once at the end so both
        // directions get a fair shot at the visible slots.
        var lineOut: [Departure] = []
        for descriptor in descriptors {
            let dt = descriptor.dt
            let shift = descriptor.shift
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
                .filter { band in
                    guard band.dayType == dt else { return false }
                    // Under the next-day-extension descriptor, only consider
                    // bands whose clock window lies in early morning. Skip
                    // yesterday's actual daytime bands (05:30+) which would
                    // otherwise emit phantom slots like 'T6 in 110 min at
                    // 05:32' on Sunday 03:43 — that's literally Saturday
                    // 05:30, not Sunday's first T6.
                    if descriptor.nextDayOnly {
                        guard let rs = minutesOfDay(band.timeStart) else { return false }
                        return rs < nextDayExtensionCutoffMinutes
                    }
                    return true
                }
                .sorted { (a, b) in
                    let am = minutesOfDay(a.timeStart) ?? 0
                    let bm = minutesOfDay(b.timeStart) ?? 0
                    return am < bm
                }
            // Project both directions per band so T6/T7 inbound (35/59 min)
            // and outbound (33/54 min) asymmetric runtimes resolve correctly,
            // and so passengers see both upcoming destinations at every stop
            // instead of just the line's outbound terminal. Each (band ×
            // direction) projects into its own accumulator; we sort + trim
            // once at the end so the first direction can't starve the second.
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
                        limit: limit,
                        into: &lineOut
                    )
                }
            }
        }
        let trimmed = lineOut
            .sorted { $0.minutesAway < $1.minutesAway }
            .prefix(limit)
        out.append(contentsOf: trimmed)
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
        // Bands that close past midnight (e.g. M2 sat 22:00 -> 00:20) ship
        // with end < start because timeEnd is the next calendar day. Wrap
        // them forward 24h so [start, end] is monotonic and 22:45 still
        // lands inside the window instead of falling off the early-return.
        let end = rawEnd + shift + (rawEnd < rawStart ? 24 * 60 : 0)
        guard end >= start else { return }

        // Shift every projected slot by the station's cumulative minutes
        // from the line origin for THIS direction. For T6/T7 the inbound
        // and outbound offsets differ (33/35 min, 54/59 min), so picking
        // by direction not "best effort" is what makes the asymmetry land
        // accurately. Source: STASY's /api/station-offsets.
        let offsetMin = SyrmosStationOffsetsStore.shared.offsetMinutes(
            lineId: lineId, direction: directionKey, stationId: stationId
        )

        // Skip past trains by ARRIVAL TIME at this station (slot + offset),
        // not by origin-departure time. A train that left terminus 10 min
        // ago but won't reach this station for another 10 min is the next
        // arrival, not a stale slot to discard.
        var slot = Double(start)
        let stationSlot = slot + Double(offsetMin)
        if stationSlot < Double(nowMinutes) {
            let skips = max(0, Int((Double(nowMinutes) - stationSlot) / band.headwayMinutes))
            slot = Double(start) + Double(skips) * band.headwayMinutes
            while slot + Double(offsetMin) < Double(nowMinutes) { slot += band.headwayMinutes }
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
