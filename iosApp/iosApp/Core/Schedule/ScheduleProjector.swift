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
        dayOffset: Int = 0,
        /// When > 0, scan forward `dayOffset+1` … up to 7 days after the
        /// current day to keep filling the result until either `limit`
        /// entries are collected or every emitted Departure lies more
        /// than `timeHorizonMinutes` ahead of the current minute. This
        /// is what powers the station-detail "next 12 hours" view —
        /// after the last train of the night the projector quietly
        /// rolls into tomorrow's first slot so the screen never says
        /// "no service" when there's a known next train within the
        /// horizon. Pass 0 (default) to keep the legacy "next N from
        /// now within today's bands" behaviour.
        timeHorizonMinutes: Int = 0
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

        let todayISO = String(format: "%04d-%02d-%02d",
                              nowComp.year ?? 2026, nowComp.month ?? 1, nowComp.day ?? 1)
        var results: [Departure] = []
        for lineId in resolvedLineIds {
            guard let bundle = bundles[lineId] else { continue }
            if !bundle.trips.isEmpty {
                projectScheduledTrips(
                    bundle: bundle,
                    weekday: nowComp.weekday ?? 1,
                    nowMinutes: nowMinutes,
                    holidayDayType: holidayDayType,
                    lineId: lineId,
                    stationId: stationId,
                    todayISO: todayISO,
                    limit: limit,
                    into: &results
                )
            }
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

        // Rollover into the next service days until we have something
        // to show. timeHorizonMinutes is a SOFT cap on filling the
        // 12-hour window — once we have at least one entry per
        // (line, direction) we stop honouring the cap so the screen
        // never falls back to "no service" when the next train is
        // genuinely findable, even if it's 14 hours away (e.g. at
        // 02:00 the next M3 train is 05:30, 3.5h in the user's head
        // but 27.5h in absolute terms when "today" already rolled
        // past midnight).
        if timeHorizonMinutes > 0 && sorted.isEmpty {
            outer: for tomorrowOffset in 1..<8 {
                let extra = ScheduleProjector.nextDepartures(
                    for: stationId,
                    lineIds: lineIds,
                    limit: limit,
                    dayOffset: tomorrowOffset,
                    timeHorizonMinutes: 0
                )
                let shifted = extra.map { dep -> Departure in
                    let absolute = dep.minutesAway + tomorrowOffset * 24 * 60 - nowMinutes
                    return Departure(
                        time: dep.time,
                        lineId: dep.lineId,
                        direction: dep.direction,
                        minutesAway: max(0, absolute),
                        serviceType: dep.serviceType
                    )
                }
                if shifted.isEmpty { continue }
                sorted.append(contentsOf: shifted)
                break outer
            }
            sorted = sorted.sorted { $0.minutesAway < $1.minutesAway }
            if sorted.count > limit {
                sorted = Array(sorted.prefix(limit))
            }
        } else if timeHorizonMinutes > 0 && sorted.count < limit {
            // We have today's entries already — top off with tomorrow's
            // morning trains within the 12-hour window so the long-list
            // (station-detail) view shows a continuous timeline.
            for tomorrowOffset in 1..<8 {
                if sorted.count >= limit { break }
                let extra = ScheduleProjector.nextDepartures(
                    for: stationId,
                    lineIds: lineIds,
                    limit: limit - sorted.count,
                    dayOffset: tomorrowOffset,
                    timeHorizonMinutes: 0
                )
                let shifted = extra.map { dep -> Departure in
                    let absolute = dep.minutesAway + tomorrowOffset * 24 * 60 - nowMinutes
                    return Departure(
                        time: dep.time,
                        lineId: dep.lineId,
                        direction: dep.direction,
                        minutesAway: max(0, absolute),
                        serviceType: dep.serviceType
                    )
                }
                let trimmed = shifted.filter { $0.minutesAway <= timeHorizonMinutes }
                if trimmed.isEmpty { continue }
                sorted.append(contentsOf: trimmed)
                if sorted.count >= limit {
                    sorted = Array(sorted.prefix(limit))
                    break
                }
            }
        }

        // Legacy M3_AIR lookahead. Only matters for limit <= ~10
        // call sites (map sheet); the 12-hour rollover above already
        // covers the long-list path with both directions.
        let wantsAirport = resolvedLineIds.contains("M3_AIR")
            && !line3AirportOnlyStations.contains(stationId)
            && timeHorizonMinutes == 0
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

    /// Tonight's final departure for the given lines at this station.
    ///
    /// Inverts `nextDepartures`: instead of "the next few from now", it returns
    /// the latest slot still running tonight, so HomeView can say "last train
    /// on this line, leave by HH:MM". Single line, no transfers, so no routing.
    /// Returns nil when service is over for the night. `maxLookaheadMinutes`
    /// bounds the "tonight" window so the M3_AIR look-ahead row (which can scan
    /// up to a week out) never masquerades as tonight's last airport train.
    static func lastTrainTonight(
        for stationId: String,
        lineIds: [String],
        maxLookaheadMinutes: Int = 12 * 60
    ) -> Departure? {
        // On 24h / overnight service days (e.g. metro M2 and M3 on Saturday
        // night into Sunday morning) trains run continuously past midnight, so
        // there is no meaningful "last train, leave by X" to rush for. The
        // same-day projection would otherwise report an early evening slot
        // (e.g. 00:10) as the "last train" because it treats the overnight
        // band's post-midnight times as already passed. Surface nothing in
        // that case rather than a misleading time.
        if isOvernightServiceToday(lineIds: lineIds) { return nil }
        let all = nextDepartures(for: stationId, lineIds: lineIds, limit: 400)
        return all
            .filter { $0.minutesAway >= 0 && $0.minutesAway <= maxLookaheadMinutes }
            .max { $0.minutesAway < $1.minutesAway }
    }

    /// True when any of [lineIds] runs 24h / overnight today (the day's rule is
    /// `is247`, or its close time is at or before its open time, meaning the
    /// service window wraps past midnight).
    static func isOvernightServiceToday(lineIds: [String]) -> Bool {
        let bundles = SyrmosSchedulesStore.shared.service.bundles
        guard !bundles.isEmpty else { return false }
        let athens = TimeZone(identifier: "Europe/Athens")!
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = athens
        let comp = cal.dateComponents([.month, .day, .weekday], from: Date())
        let holiday = resolveHolidayDayType(month: comp.month ?? 1, day: comp.day ?? 1)
        let dt = dayType(for: comp.weekday ?? 1, holiday: holiday)
        for lineId in lineIds {
            guard let bundle = bundles[lineId],
                  let rule = bundle.rules.first(where: { $0.dayType == dt }) else { continue }
            if rule.is247 { return true }
            if let openM = minutesOfDay(rule.openTime),
               let closeM = minutesOfDay(rule.closeTime),
               closeM <= openM { return true }
        }
        return false
    }

    // MARK: - Airport-focused full-day projection
    //
    // The airport tab needs the *complete* day's airport schedule from
    // a given station, both "to airport" (outbound) and "from airport"
    // (inbound). The general nextDepartures path is optimised for
    // "next N from now" with per-line limits + a lookahead fallback,
    // which collapses to ~1 visible airport entry once city-line slots
    // fill the prefix cap. This method bypasses all of that: walks
    // M3_AIR and A1 bundles directly for the target day_type, emits
    // every slot, and applies station offsets so a stop mid-line
    // displays its actual arrival time rather than the line's origin
    // time.

    static func airportDeparturesForDay(
        stationId: String,
        dayOffset: Int
    ) -> [Departure] {
        let store = SyrmosSchedulesStore.shared
        let bundles = store.service.bundles
        if bundles.isEmpty { return [] }

        let athens = TimeZone(identifier: "Europe/Athens")!
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = athens
        let targetDate = cal.date(byAdding: .day, value: dayOffset, to: Date()) ?? Date()
        let comp = cal.dateComponents([.year, .month, .day, .hour, .minute, .weekday], from: targetDate)
        let weekday = comp.weekday ?? 1
        let holiday = resolveHolidayDayType(month: comp.month ?? 1, day: comp.day ?? 1)
        let dt = dayType(for: weekday, holiday: holiday)
        let cutoffMinutes = dayOffset == 0
            ? (comp.hour ?? 0) * 60 + (comp.minute ?? 0)
            : 0

        var out: [Departure] = []

        // Only emit per-line entries if the picked station actually
        // sits on that line. Nikaia is on M3 (so it sees M3_AIR which
        // shares M3's western track), it is NOT on A1 (A1 runs Piraeus
        // -> Airport via SKA, never touches Nikaia). Emitting A1 rows
        // here would tell the user "A1 Piraeus-Airport towards Airport
        // 04:00" at a station the train never visits.
        let stationLineIds = Set(AirportData.station(for: stationId).lineIds)

        if stationLineIds.contains("M3") || stationLineIds.contains("M3_AIR") {
            if let bundle = bundles["M3_AIR"] {
                emitAirportSlots(
                    bundle: bundle,
                    dayType: dt,
                    cutoffMinutes: cutoffMinutes,
                    stationId: stationId,
                    lineId: "M3",
                    outboundDirectionLabel: "Airport",
                    inboundDirectionLabel: "Dimotiko Theatro",
                    into: &out
                )
            }
        }

        // A1 + A2 bands carry direction = "both"; we emit a pair (one
        // per direction) for each slot so the picker can split them
        // into To Airport / From Airport sections.
        if stationLineIds.contains("A1"), let bundle = bundles["A1"] {
            emitAirportSlots(
                bundle: bundle,
                dayType: dt,
                cutoffMinutes: cutoffMinutes,
                stationId: stationId,
                lineId: "A1",
                outboundDirectionLabel: "Airport",
                inboundDirectionLabel: "Piraeus",
                into: &out
            )
        }
        if stationLineIds.contains("A2"), let bundle = bundles["A2"] {
            emitAirportSlots(
                bundle: bundle,
                dayType: dt,
                cutoffMinutes: cutoffMinutes,
                stationId: stationId,
                lineId: "A2",
                outboundDirectionLabel: "Airport",
                inboundDirectionLabel: "Ano Liosia",
                into: &out
            )
        }

        return out.sorted { $0.minutesAway < $1.minutesAway }
    }

    /// Resolves the time-from-origin for a station on an airport line.
    /// M3_AIR trains physically share the M3 western track from
    /// Dimotiko Theatro to Doukissis Plakentias, so a city-section
    /// stop like Nikaia or Korydallos has its offset registered under
    /// M3, not M3_AIR. Falling back lets the displayed clock time
    /// match when the train actually passes the station instead of
    /// just echoing the line-origin departure.
    private static func airportStationOffset(
        stationId: String,
        lineId: String,
        direction: String
    ) -> Int {
        let store = SyrmosStationOffsetsStore.shared
        let primary = store.offsetMinutes(
            lineId: lineId, direction: direction, stationId: stationId
        )
        if primary > 0 { return primary }
        if lineId == "M3_AIR" {
            return store.offsetMinutes(
                lineId: "M3", direction: direction, stationId: stationId
            )
        }
        return 0
    }

    private static func emitAirportSlots(
        bundle: SyrmosSchedulesService.LineSchedule,
        dayType dt: String,
        cutoffMinutes: Int,
        stationId: String,
        lineId: String,
        outboundDirectionLabel: String,
        inboundDirectionLabel: String,
        into out: inout [Departure]
    ) {
        let outOffset = airportStationOffset(
            stationId: stationId, lineId: bundle.lineId, direction: "outbound"
        )
        let inOffset = airportStationOffset(
            stationId: stationId, lineId: bundle.lineId, direction: "inbound"
        )

        for band in bundle.bands where band.dayType == dt {
            guard let rawStart = minutesOfDay(band.timeStart),
                  let rawEnd = minutesOfDay(band.timeEnd),
                  band.headwayMinutes > 0 else { continue }
            let end = rawEnd + (rawEnd < rawStart ? 24 * 60 : 0)
            let dir = (band.direction ?? "both").lowercased()
            let directionsToEmit: [(label: String, offset: Int)]
            switch dir {
            case "outbound":
                directionsToEmit = [(outboundDirectionLabel, outOffset)]
            case "inbound":
                directionsToEmit = [(inboundDirectionLabel, inOffset)]
            default:
                directionsToEmit = [
                    (outboundDirectionLabel, outOffset),
                    (inboundDirectionLabel, inOffset),
                ]
            }

            for (label, stationOffset) in directionsToEmit {
                var slot = Double(rawStart)
                while slot <= Double(end) {
                    let timeMin = Int(slot.rounded()) + stationOffset
                    if timeMin >= cutoffMinutes {
                        let display = ((timeMin % (24 * 60)) + 24 * 60) % (24 * 60)
                        out.append(Departure(
                            time: String(format: "%02d:%02d", display / 60, display % 60),
                            lineId: lineId,
                            direction: label,
                            minutesAway: max(0, timeMin - cutoffMinutes),
                            serviceType: "airport"
                        ))
                    }
                    slot += band.headwayMinutes
                }
            }
        }
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
                // Real close = whatever lasts longer between the rule's
                // own closeTime and the last band's timeEnd for this
                // day type. OASA's published rule.closeTime is when the
                // station officially shuts; bands often extend past it
                // because trains that left origin before close are still
                // running. M2 mon_thu rule says 00:06 but the late_night
                // band runs until 00:20; M3 mon_thu says 00:01 but the
                // band reaches 00:20. Gating strictly on rule.closeTime
                // threw away every late-night entry between the rule's
                // close and the bands' real end.
                let bandMaxEnd = bundle.bands
                    .filter { $0.dayType == dt }
                    .compactMap { band -> Int? in
                        guard let rs = minutesOfDay(band.timeStart),
                              let re = minutesOfDay(band.timeEnd) else { return nil }
                        return re + (re < rs ? 24 * 60 : 0)
                    }
                    .max() ?? closeM
                let ruleClose = closeM <= openM ? closeM + 24 * 60 : closeM
                let effectiveClose = max(ruleClose, bandMaxEnd)
                // Convert today's wall-clock minute back into the rule's
                // own clock domain. For today's descriptor shift = 0 so
                // effectiveNow == nowMinutes. For yesterday's overnight
                // descriptor shift = -1440 (yesterday-clock = today-clock
                // + 1440), so we *subtract* the shift to put nowMinutes
                // back into yesterday's frame. The previous formula used
                // `+ shift`, which mapped today's 00:03 to yesterday's
                // -1437 and threw away every late-night band on M1/M2
                // mon_thu (open 05:00 close 00:30) right after midnight.
                let effectiveNow = nowMinutes - shift
                // Only reject bands that are entirely in the past. 2-hour
                // upper slack lets trains DOWNSTREAM of the origin still
                // emit after the last slot leaves the terminus. We do NOT
                // gate on the lower bound: a future band of today's day-
                // type produces future slots naturally (slot loop emits
                // start..end), so at 02:09 Thursday with mon_thu open at
                // 05:30 we still need to enumerate the band and emit the
                // 05:30 first train. Past behavior reject-when-before-open
                // forced the projector to roll into next-day fallback,
                // which spat out "1.315 min" for Friday's 00:03 slot while
                // Thursday's morning service was sitting right there.
                if effectiveNow > effectiveClose + 120 { continue }
            }

            let openMinRule = minutesOfDay(rule.openTime)
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
                    // Today's own next-day extension bands (e.g. sat 00:20-05:30)
                    // belong to tonight going into tomorrow morning. Exclude them
                    // from today's descriptor so they don't project onto today's
                    // early morning. They'll be picked up by descriptor 3 on the
                    // next calendar day.
                    if shift == 0, !rule.is247, let openM = openMinRule,
                       let rs = minutesOfDay(band.timeStart),
                       rs < openM, rs < nextDayExtensionCutoffMinutes {
                        return false
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
                        lastTrainsForDirection: bundle.lastTrains.filter {
                            $0.dayType == dt && $0.direction == stream.key
                        },
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
        /// Pre-filtered subset of bundle.lastTrains matching this dayType
        /// + direction. Empty when the bundle ships no short-turn data
        /// (M2 / M3 / trams / suburban as of writing). Looked up
        /// per-slot by (fromStationId, time) to override the displayed
        /// destination when the emitted slot is one of the short-turn
        /// or last-train rows STASY publishes.
        lastTrainsForDirection: [SyrmosSchedulesService.LastTrainEntry],
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
            let displayTime = String(format: "%02d:%02d", h, m)
            // Override the direction label when the slot we're about to
            // emit matches a STASY-scraped last-train row for this
            // station. ±1 min tolerance handles minor rounding drift
            // between the band's headway projection and STASY's
            // published clock times.
            let resolvedDirection = lastTrainOverride(
                lastTrains: lastTrainsForDirection,
                fromStationId: stationId,
                slotTime: displayTime
            ) ?? directionLabel
            out.append(Departure(
                time: displayTime,
                lineId: displayLineId(for: lineId),
                direction: resolvedDirection,
                minutesAway: mins,
                serviceType: serviceTypeLabel(for: lineId, label: band.label)
            ))
            slot += band.headwayMinutes
            added += 1
        }
    }

    /// Returns the human-readable terminal name to display for this
    /// slot when STASY publishes a short-turn / explicit last-train
    /// row for it, otherwise nil. We compare with a ±1 min window so
    /// the projector's per-station offset rounding doesn't make us
    /// miss the override.
    private static func lastTrainOverride(
        lastTrains: [SyrmosSchedulesService.LastTrainEntry],
        fromStationId: String,
        slotTime: String
    ) -> String? {
        guard !lastTrains.isEmpty else { return nil }
        let slotMin = minutesOfDay(slotTime) ?? 0
        for entry in lastTrains where entry.fromStationId == fromStationId {
            guard let entryMin = minutesOfDay(entry.time) else { continue }
            if abs(entryMin - slotMin) <= 1 {
                // Translate the endStation id to its localized human
                // name. We bias to the English name since the
                // direction label inside Departure is rendered as
                // "towards X" / "drejt X" / "προς X" with the X
                // already coming out as a station label downstream.
                let coords = StationCoordinateLookup.shared
                if let label = coords.englishName(for: entry.endStationId) {
                    return label
                }
                return entry.endStationId
            }
        }
        return nil
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

    // MARK: - Scheduled trip projection (non-Athens lines)

    private static func projectScheduledTrips(
        bundle: SyrmosSchedulesService.LineSchedule,
        weekday: Int,
        nowMinutes: Int,
        holidayDayType: String?,
        lineId: String,
        stationId: String,
        todayISO: String,
        limit: Int,
        into out: inout [Departure]
    ) {
        let dt = dayType(for: weekday, holiday: holidayDayType)
        let matching = bundle.trips.filter { trip in
            guard trip.dayType == dt else { return false }
            if let vd = trip.validDates, !vd.isEmpty {
                let dates = vd.split(separator: ",").map { String($0) }
                if !dates.contains(todayISO) { return false }
            }
            return true
        }
        for trip in matching {
            guard let stop = trip.stops.first(where: { $0.stationId == stationId }) else { continue }
            guard let depMin = minutesOfDay(stop.departureTime) else { continue }
            let delta = depMin - nowMinutes
            if delta < -1 { continue }
            let terminus: String
            if trip.direction == "outbound" {
                terminus = trip.stops.last?.stationId ?? ""
            } else {
                terminus = trip.stops.first?.stationId ?? ""
            }
            let dirLabel = scheduledTripDestination(lineId: lineId, direction: trip.direction, lastStationId: terminus)
            out.append(Departure(
                time: stop.departureTime,
                lineId: displayLineId(for: lineId),
                direction: dirLabel,
                minutesAway: max(0, delta),
                serviceType: "regular"
            ))
        }
    }

    private static func scheduledTripDestination(lineId: String, direction: String, lastStationId: String) -> String {
        let aliases: [String: String] = [
            "A1_AIR": "Airport", "A1_PIR": "Piraeus", "A1_TAY": "Tavros",
            "A2_AIR": "Airport", "A2_ANO": "Ano Liosia",
            "A3_CHA": "Chalkida", "A3_ATH": "Athens", "A3_AYL": "Avlonas",
            "A4_KIA": "Kiato", "A4_PIR": "Piraeus", "A4_NEA": "Nea Peramos",
            "PL_ALE": "Ano Lechonia", "PL_MIL": "Milies",
            "PA_AND": "Agios Andreas", "PA_KAM": "Kaminia", "PA_RIO": "Rio",
            "PA_KST": "Kastelokampos", "PU_AGV": "Agios Vasileios",
            "KO_KAT": "Katakolo", "KO_OLY": "Olympia",
            "GR_ATH": "Athens", "GR_THE": "Thessaloniki",
            "GR_LAR": "Larisa", "GR_FLO": "Florina",
        ]
        if let label = aliases[lastStationId] { return label }
        if let line = SyrmosData.line(for: lineId) {
            return direction == "outbound" ? line.terminalB : line.terminalA
        }
        return direction == "outbound" ? "Outbound" : "Inbound"
    }
}
