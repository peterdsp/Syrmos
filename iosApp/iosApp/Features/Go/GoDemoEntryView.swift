import SwiftUI

// Entry point that builds a real planned journey from the bundled network and
// hands it to the GO screen. Prefers a cross-line route (so the preview shows a
// transfer) and falls back to a long single-line route, so it works whatever the
// seed contains.
struct GoDemoEntryView: View {
    let language: AppLanguage

    private var journey: GuidanceJourney? {
        let ops = SyrmosData.operationalLines

        // Prefer M1 origin -> M2 destination (usually a transfer through the core).
        if let m1 = ops.first(where: { $0.id == "M1" }),
           let m2 = ops.first(where: { $0.id == "M2" }),
           let a = SyrmosData.stations(for: m1.id).first?.id,
           let b = SyrmosData.stations(for: m2.id).last?.id,
           let detailed = JourneyPlanner.planDetailed(from: a, to: b, language: language) {
            return GuidanceJourney.from(detailed, language: language)
        }

        // Fallback: first operational line with >=4 stops, first -> last.
        for line in ops {
            let stops = SyrmosData.stations(for: line.id)
            if stops.count >= 4,
               let detailed = JourneyPlanner.planDetailed(from: stops[0].id, to: stops[stops.count - 1].id, language: language) {
                return GuidanceJourney.from(detailed, language: language)
            }
        }
        return nil
    }

    var body: some View {
        if let journey {
            GoJourneyView(journey: journey, language: language)
        } else {
            ContentUnavailableView(
                language == .greek ? "Δεν βρέθηκε διαδρομή" : language == .albanian ? "Nuk u gjet rrugë" : language == .italian ? "Nessun percorso" : "No route available",
                systemImage: "figure.walk.motion",
                description: Text(
                    language == .greek ? "Δεν ήταν δυνατή η δημιουργία διαδρομής από τα δεδομένα." :
                    language == .albanian ? "Nuk u krijua dot një rrugë nga të dhënat." :
                    language == .italian ? "Impossibile creare un percorso dai dati." :
                    "Could not build a route from the bundled data."
                )
            )
        }
    }
}
