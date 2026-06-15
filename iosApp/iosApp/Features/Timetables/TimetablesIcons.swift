import Foundation

/// Maps a Timetables departure (lineId + destination text) to the matching
/// directional vehicle imageset that already ships in Assets.xcassets/vehicles.
/// Returns nil when we don't have a directional asset — caller falls back to a
/// colored circle so the row never goes empty.
enum TimetablesIcons {
    static func vehicleImageName(lineId: String, direction: String, isAirport: Bool) -> String? {
        let dir = direction.lowercased()
        switch lineId {
        case "M1":
            if dir.contains("piraeus") { return "metro_m1_left_to_piraeus" }
            return "metro_m1_right_to_kifissia"
        case "M2":
            if dir.contains("anthoupoli") { return "metro_m2_left_to_anthoupoli" }
            return "metro_m2_right_to_elliniko"
        case "M3":
            if isAirport { return "metro_m3_right_to_airport" }
            if dir.contains("dimotiko") || dir.contains("dimarheio") || dir.contains("piraeus") {
                return "metro_m3_left_to_dimotiko_theatro"
            }
            return "metro_m3_right_to_doukissis_plakentias"
        case "T6":
            if dir.contains("syntagma") { return "tram_t6_left_to_syntagma" }
            return "tram_t6_right_to_pikrodafni"
        case "T7":
            if dir.contains("akti") || dir.contains("posidonos") || dir.contains("piraeus") {
                return "tram_t7_left_to_akti_posidonos"
            }
            return "tram_t7_right_to_asklipiio_voulas"
        case "A1":
            if dir.contains("piraeus") { return "train_p1_left_to_piraeus" }
            return "train_p1_right_to_airport"
        case "A2":
            if dir.contains("liosia") { return "train_p1a_left_to_ano_liosia" }
            return "train_p1a_right_to_airport"
        case "A3":
            if dir.contains("athens") { return "train_p3_left_to_athens" }
            return "train_p3_right_to_chalkida"
        case "A4":
            if dir.contains("piraeus") { return "train_p2_left_to_piraeus" }
            return "train_p2_right_to_kiato"
        default:
            return nil
        }
    }
}
