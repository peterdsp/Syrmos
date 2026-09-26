import Foundation

/// Plan companion helpers (foldables master plan, Plan contract): compare the
/// route alternatives on the differences that are actually there, and keep the
/// selected itinerary by identity rather than by list position.
///
/// Swift twin of Kotlin `com.syrmos.core.domain.journey.JourneyComparison`;
/// the fixtures in `JourneyComparisonTests` and `JourneyComparisonTest` must agree.
enum JourneyComparison {
    /// What one alternative has over the others. A lone alternative is neutral:
    /// nothing to compare against, so a single route is never decorated as fastest.
    struct Facts: Equatable {
        /// Shortest known duration, and at least one other option is slower.
        var fastest: Bool = false
        /// Fewest changes, and at least one other option has more.
        var fewestChanges: Bool = false
        /// Whole minutes behind the fastest known option; nil when this is the
        /// fastest, ties it, or its duration is unknown.
        var minutesSlowerThanFastest: Int? = nil
        /// Changes beyond the option with the fewest (0 when it is one).
        var extraChanges: Int = 0
    }

    /// Facts for every alternative, index-aligned with the inputs. `durations`
    /// are seconds (nil when untimed), `changes` the transfer counts.
    static func facts(durations: [Int?], changes: [Int]) -> [Facts] {
        precondition(durations.count == changes.count, "durations and changes must align")
        let n = durations.count
        if n < 2 { return Array(repeating: Facts(), count: n) }

        let known = durations.compactMap { $0 }
        let minDur = known.min()
        let maxDur = known.max()
        let durationsDiffer: Bool = {
            guard let lo = minDur, let hi = maxDur else { return false }
            return hi > lo
        }()
        let minCh = changes.min() ?? 0
        let changesDiffer = (changes.max() ?? 0) > minCh

        return durations.indices.map { i in
            let d = durations[i]
            var slower: Int? = nil
            if let d, let lo = minDur, d > lo {
                let m = (d - lo + 30) / 60
                slower = m > 0 ? m : nil
            }
            return Facts(
                fastest: durationsDiffer && d != nil && d == minDur,
                fewestChanges: changesDiffer && changes[i] == minCh,
                minutesSlowerThanFastest: slower,
                extraChanges: changes[i] - minCh
            )
        }
    }
}

/// Selected-itinerary identity across a results refresh (twin of Kotlin
/// `JourneySelection`). Keyed by option id, never by array index.
enum JourneySelection {
    /// The id to keep selected: `previous` when still offered, else the first id, else nil.
    static func retain(previous: String?, ids: [String]) -> String? {
        if let previous, ids.contains(previous) { return previous }
        return ids.first
    }

    /// Position of `selected` in `ids`, or nil when it is not offered.
    static func index(of selected: String?, in ids: [String]) -> Int? {
        guard let selected else { return nil }
        return ids.firstIndex(of: selected)
    }
}
