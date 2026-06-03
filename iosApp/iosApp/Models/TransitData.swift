import SwiftUI
import CoreLocation

// MARK: - Models

struct TransitLine: Identifiable {
    let id: String
    let name: String
    let nameEl: String
    let terminalA: String
    let terminalB: String
    let stationCount: Int
    let color: Color
    let type: TransitType
}

enum TransitType: String, CaseIterable {
    case metro = "Metro"
    case tram = "Tram"
    case suburban = "Suburban Railway"
}

struct TransitStation: Identifiable {
    let id: String
    let name: String
    let nameEl: String
    let coordinate: CLLocationCoordinate2D
    let lineIds: [String]
    let isInterchange: Bool
}

struct Departure: Identifiable {
    let id = UUID()
    let time: String
    let lineId: String
    let direction: String
    let minutesAway: Int
}

// MARK: - Static Data

enum SyrmosData {

    static let lines: [TransitLine] = [
        .init(id: "M1", name: "Line 1", nameEl: "Γραμμή 1", terminalA: "Piraeus", terminalB: "Kifisia", stationCount: 24, color: .metroGreen, type: .metro),
        .init(id: "M2", name: "Line 2", nameEl: "Γραμμή 2", terminalA: "Anthoupoli", terminalB: "Elliniko", stationCount: 20, color: .metroRed, type: .metro),
        .init(id: "M3", name: "Line 3", nameEl: "Γραμμή 3", terminalA: "Dimotiko Theatro", terminalB: "Airport", stationCount: 27, color: .metroBlue, type: .metro),
        .init(id: "T6", name: "Tram T6", nameEl: "Τραμ Τ6", terminalA: "Syntagma", terminalB: "Pikrodafni", stationCount: 19, color: .tramOrange, type: .tram),
        .init(id: "T7", name: "Tram T7", nameEl: "Τραμ Τ7", terminalA: "Akti Posidonos", terminalB: "Asklipiio Voulas", stationCount: 37, color: .tramOrange, type: .tram),
        .init(id: "P1", name: "Airport-Piraeus", nameEl: "Αεροδρόμιο-Πειραιάς", terminalA: "Airport", terminalB: "Piraeus", stationCount: 12, color: .suburbanPurple, type: .suburban),
        .init(id: "P2", name: "Piraeus-Kiato", nameEl: "Πειραιάς-Κιάτο", terminalA: "Piraeus", terminalB: "Kiato", stationCount: 18, color: .suburbanPurple, type: .suburban),
        .init(id: "P3", name: "Kiato-Aigio", nameEl: "Κιάτο-Αίγιο", terminalA: "Kiato", terminalB: "Aigio", stationCount: 8, color: .suburbanPurple, type: .suburban),
    ]

    static func line(for id: String) -> TransitLine? {
        lines.first { $0.id == id }
    }

    static func lineColor(for id: String) -> Color {
        switch id {
        case "M1": return .metroGreen
        case "M2": return .metroRed
        case "M3": return .metroBlue
        case "T6", "T7": return .tramOrange
        default: return .suburbanPurple
        }
    }

    // MARK: - Stations per Line

    static let stationsByLine: [String: [TransitStation]] = [
        "M1": [
            .init(id: "M1_PIR", name: "Piraeus", nameEl: "Πειραιάς", coordinate: .init(latitude: 37.9475, longitude: 23.6431), lineIds: ["M1", "P1", "P2"], isInterchange: true),
            .init(id: "M1_FAL", name: "Faliro", nameEl: "Φάληρο", coordinate: .init(latitude: 37.9426, longitude: 23.6633), lineIds: ["M1"], isInterchange: false),
            .init(id: "M1_MOS", name: "Moschato", nameEl: "Μοσχάτο", coordinate: .init(latitude: 37.9486, longitude: 23.6756), lineIds: ["M1"], isInterchange: false),
            .init(id: "M1_KAL", name: "Kallithea", nameEl: "Καλλιθέα", coordinate: .init(latitude: 37.9562, longitude: 23.6965), lineIds: ["M1"], isInterchange: false),
            .init(id: "M1_TAV", name: "Tavros", nameEl: "Ταύρος", coordinate: .init(latitude: 37.9644, longitude: 23.7069), lineIds: ["M1"], isInterchange: false),
            .init(id: "M1_PET", name: "Petralona", nameEl: "Πετράλωνα", coordinate: .init(latitude: 37.9683, longitude: 23.7107), lineIds: ["M1"], isInterchange: false),
            .init(id: "M1_THE", name: "Thissio", nameEl: "Θησείο", coordinate: .init(latitude: 37.9764, longitude: 23.7210), lineIds: ["M1"], isInterchange: false),
            .init(id: "M1_MON", name: "Monastiraki", nameEl: "Μοναστηράκι", coordinate: .init(latitude: 37.9763, longitude: 23.7256), lineIds: ["M1", "M3"], isInterchange: true),
            .init(id: "M1_OMO", name: "Omonia", nameEl: "Ομόνοια", coordinate: .init(latitude: 37.9844, longitude: 23.7282), lineIds: ["M1", "M2"], isInterchange: true),
            .init(id: "M1_VIC", name: "Victoria", nameEl: "Βικτώρια", coordinate: .init(latitude: 37.9933, longitude: 23.7294), lineIds: ["M1"], isInterchange: false),
            .init(id: "M1_ATT", name: "Attiki", nameEl: "Αττική", coordinate: .init(latitude: 37.9998, longitude: 23.7221), lineIds: ["M1", "M2"], isInterchange: true),
            .init(id: "M1_AGN", name: "Agios Nikolaos", nameEl: "Άγιος Νικόλαος", coordinate: .init(latitude: 38.0042, longitude: 23.7198), lineIds: ["M1"], isInterchange: false),
            .init(id: "M1_KAT", name: "Kato Patissia", nameEl: "Κάτω Πατήσια", coordinate: .init(latitude: 38.0106, longitude: 23.7280), lineIds: ["M1"], isInterchange: false),
            .init(id: "M1_PER", name: "Perissos", nameEl: "Περισσός", coordinate: .init(latitude: 38.0322, longitude: 23.7394), lineIds: ["M1"], isInterchange: false),
            .init(id: "M1_NIO", name: "Nea Ionia", nameEl: "Νέα Ιωνία", coordinate: .init(latitude: 38.0422, longitude: 23.7506), lineIds: ["M1"], isInterchange: false),
            .init(id: "M1_IRK", name: "Iraklio", nameEl: "Ηράκλειο", coordinate: .init(latitude: 38.0489, longitude: 23.7522), lineIds: ["M1"], isInterchange: false),
            .init(id: "M1_NER", name: "Neratziotissa", nameEl: "Νερατζιώτισσα", coordinate: .init(latitude: 38.0583, longitude: 23.7672), lineIds: ["M1", "P1"], isInterchange: true),
            .init(id: "M1_MAR", name: "Maroussi", nameEl: "Μαρούσι", coordinate: .init(latitude: 38.0678, longitude: 23.7878), lineIds: ["M1"], isInterchange: false),
            .init(id: "M1_KAM", name: "KAT", nameEl: "ΚΑΤ", coordinate: .init(latitude: 38.0789, longitude: 23.7933), lineIds: ["M1"], isInterchange: false),
            .init(id: "M1_KHE", name: "Kifisia", nameEl: "Κηφισιά", coordinate: .init(latitude: 38.0856, longitude: 23.8011), lineIds: ["M1"], isInterchange: false),
        ],
        "M2": [
            .init(id: "M2_ANT", name: "Anthoupoli", nameEl: "Ανθούπολη", coordinate: .init(latitude: 38.0139, longitude: 23.7056), lineIds: ["M2"], isInterchange: false),
            .init(id: "M2_PEE", name: "Peristeri", nameEl: "Περιστέρι", coordinate: .init(latitude: 38.0103, longitude: 23.6969), lineIds: ["M2"], isInterchange: false),
            .init(id: "M2_SEP", name: "Sepolia", nameEl: "Σεπόλια", coordinate: .init(latitude: 37.9978, longitude: 23.7139), lineIds: ["M2"], isInterchange: false),
            .init(id: "M2_ATT", name: "Attiki", nameEl: "Αττική", coordinate: .init(latitude: 37.9998, longitude: 23.7221), lineIds: ["M1", "M2"], isInterchange: true),
            .init(id: "M2_LAR", name: "Larissa Station", nameEl: "Σταθμός Λαρίσης", coordinate: .init(latitude: 37.9914, longitude: 23.7217), lineIds: ["M2", "P1"], isInterchange: true),
            .init(id: "M2_MET", name: "Metaxourghio", nameEl: "Μεταξουργείο", coordinate: .init(latitude: 37.9859, longitude: 23.7217), lineIds: ["M2"], isInterchange: false),
            .init(id: "M2_OMO", name: "Omonia", nameEl: "Ομόνοια", coordinate: .init(latitude: 37.9844, longitude: 23.7282), lineIds: ["M1", "M2"], isInterchange: true),
            .init(id: "M2_PAN", name: "Panepistimio", nameEl: "Πανεπιστήμιο", coordinate: .init(latitude: 37.9807, longitude: 23.7334), lineIds: ["M2"], isInterchange: false),
            .init(id: "M2_SYN", name: "Syntagma", nameEl: "Σύνταγμα", coordinate: .init(latitude: 37.9755, longitude: 23.7353), lineIds: ["M2", "M3"], isInterchange: true),
            .init(id: "M2_AKR", name: "Akropoli", nameEl: "Ακρόπολη", coordinate: .init(latitude: 37.9694, longitude: 23.7288), lineIds: ["M2"], isInterchange: false),
            .init(id: "M2_SYG", name: "Syngrou-Fix", nameEl: "Συγγρού-Φιξ", coordinate: .init(latitude: 37.9640, longitude: 23.7266), lineIds: ["M2"], isInterchange: false),
            .init(id: "M2_NEK", name: "Neos Kosmos", nameEl: "Νέος Κόσμος", coordinate: .init(latitude: 37.9559, longitude: 23.7311), lineIds: ["M2"], isInterchange: false),
            .init(id: "M2_AGI", name: "Agios Ioannis", nameEl: "Άγιος Ιωάννης", coordinate: .init(latitude: 37.9497, longitude: 23.7316), lineIds: ["M2"], isInterchange: false),
            .init(id: "M2_DAF", name: "Dafni", nameEl: "Δάφνη", coordinate: .init(latitude: 37.9422, longitude: 23.7378), lineIds: ["M2"], isInterchange: false),
            .init(id: "M2_ALD", name: "Agios Dimitrios", nameEl: "Άγιος Δημήτριος", coordinate: .init(latitude: 37.9356, longitude: 23.7403), lineIds: ["M2"], isInterchange: false),
            .init(id: "M2_ILI", name: "Ilioupoli", nameEl: "Ηλιούπολη", coordinate: .init(latitude: 37.9278, longitude: 23.7468), lineIds: ["M2"], isInterchange: false),
            .init(id: "M2_ALM", name: "Alimos", nameEl: "Άλιμος", coordinate: .init(latitude: 37.9158, longitude: 23.7258), lineIds: ["M2"], isInterchange: false),
            .init(id: "M2_ARG", name: "Argyroupoli", nameEl: "Αργυρούπολη", coordinate: .init(latitude: 37.9061, longitude: 23.7488), lineIds: ["M2"], isInterchange: false),
            .init(id: "M2_ELL", name: "Elliniko", nameEl: "Ελληνικό", coordinate: .init(latitude: 37.8919, longitude: 23.7472), lineIds: ["M2"], isInterchange: false),
        ],
        "M3": [
            .init(id: "M3_DIM", name: "Dimotiko Theatro", nameEl: "Δημοτικό Θέατρο", coordinate: .init(latitude: 37.9483, longitude: 23.6444), lineIds: ["M3"], isInterchange: false),
            .init(id: "M3_PIR", name: "Piraeus", nameEl: "Πειραιάς", coordinate: .init(latitude: 37.9475, longitude: 23.6431), lineIds: ["M1", "M3"], isInterchange: true),
            .init(id: "M3_MAN", name: "Maniatika", nameEl: "Μανιάτικα", coordinate: .init(latitude: 37.9497, longitude: 23.6519), lineIds: ["M3"], isInterchange: false),
            .init(id: "M3_NIK", name: "Nikaia", nameEl: "Νίκαια", coordinate: .init(latitude: 37.9633, longitude: 23.6586), lineIds: ["M3"], isInterchange: false),
            .init(id: "M3_KOR", name: "Korydallos", nameEl: "Κορυδαλλός", coordinate: .init(latitude: 37.9756, longitude: 23.6581), lineIds: ["M3"], isInterchange: false),
            .init(id: "M3_ABA", name: "Agia Varvara", nameEl: "Αγία Βαρβάρα", coordinate: .init(latitude: 37.9844, longitude: 23.6614), lineIds: ["M3"], isInterchange: false),
            .init(id: "M3_AMA", name: "Agia Marina", nameEl: "Αγία Μαρίνα", coordinate: .init(latitude: 37.9892, longitude: 23.6736), lineIds: ["M3"], isInterchange: false),
            .init(id: "M3_EGA", name: "Egaleo", nameEl: "Αιγάλεω", coordinate: .init(latitude: 37.9919, longitude: 23.6844), lineIds: ["M3"], isInterchange: false),
            .init(id: "M3_ELE", name: "Eleonas", nameEl: "Ελαιώνας", coordinate: .init(latitude: 37.9878, longitude: 23.7039), lineIds: ["M3"], isInterchange: false),
            .init(id: "M3_KER", name: "Kerameikos", nameEl: "Κεραμεικός", coordinate: .init(latitude: 37.9789, longitude: 23.7139), lineIds: ["M3"], isInterchange: false),
            .init(id: "M3_MON", name: "Monastiraki", nameEl: "Μοναστηράκι", coordinate: .init(latitude: 37.9763, longitude: 23.7256), lineIds: ["M1", "M3"], isInterchange: true),
            .init(id: "M3_SYN", name: "Syntagma", nameEl: "Σύνταγμα", coordinate: .init(latitude: 37.9755, longitude: 23.7353), lineIds: ["M2", "M3"], isInterchange: true),
            .init(id: "M3_EVA", name: "Evangelismos", nameEl: "Ευαγγελισμός", coordinate: .init(latitude: 37.9758, longitude: 23.7444), lineIds: ["M3"], isInterchange: false),
            .init(id: "M3_MEG", name: "Megaro Moussikis", nameEl: "Μέγαρο Μουσικής", coordinate: .init(latitude: 37.9783, longitude: 23.7508), lineIds: ["M3"], isInterchange: false),
            .init(id: "M3_AMP", name: "Ambelokipoi", nameEl: "Αμπελόκηποι", coordinate: .init(latitude: 37.9847, longitude: 23.7569), lineIds: ["M3"], isInterchange: false),
            .init(id: "M3_PNR", name: "Panormou", nameEl: "Πανόρμου", coordinate: .init(latitude: 37.9919, longitude: 23.7628), lineIds: ["M3"], isInterchange: false),
            .init(id: "M3_KTC", name: "Katechaki", nameEl: "Κατεχάκη", coordinate: .init(latitude: 37.9989, longitude: 23.7706), lineIds: ["M3"], isInterchange: false),
            .init(id: "M3_ETH", name: "Ethniki Amyna", nameEl: "Εθνική Άμυνα", coordinate: .init(latitude: 38.0008, longitude: 23.7819), lineIds: ["M3"], isInterchange: false),
            .init(id: "M3_HOL", name: "Holargos", nameEl: "Χολαργός", coordinate: .init(latitude: 37.9986, longitude: 23.7944), lineIds: ["M3"], isInterchange: false),
            .init(id: "M3_NOM", name: "Nomismatokopeio", nameEl: "Νομισματοκοπείο", coordinate: .init(latitude: 37.9961, longitude: 23.8058), lineIds: ["M3"], isInterchange: false),
            .init(id: "M3_APR", name: "Agia Paraskevi", nameEl: "Αγία Παρασκευή", coordinate: .init(latitude: 37.9953, longitude: 23.8183), lineIds: ["M3"], isInterchange: false),
            .init(id: "M3_HAL", name: "Halandri", nameEl: "Χαλάνδρι", coordinate: .init(latitude: 38.0033, longitude: 23.8256), lineIds: ["M3"], isInterchange: false),
            .init(id: "M3_DPL", name: "Douk. Plakentias", nameEl: "Δουκ. Πλακεντίας", coordinate: .init(latitude: 38.0072, longitude: 23.8394), lineIds: ["M3", "P1"], isInterchange: true),
            .init(id: "M3_PAL", name: "Pallini", nameEl: "Παλλήνη", coordinate: .init(latitude: 38.0022, longitude: 23.8750), lineIds: ["M3"], isInterchange: false),
            .init(id: "M3_PEK", name: "Peania-Kantza", nameEl: "Παιανία-Κάντζα", coordinate: .init(latitude: 37.9656, longitude: 23.8908), lineIds: ["M3"], isInterchange: false),
            .init(id: "M3_KRP", name: "Koropi", nameEl: "Κορωπί", coordinate: .init(latitude: 37.9044, longitude: 23.8717), lineIds: ["M3"], isInterchange: false),
            .init(id: "M3_AER", name: "Airport", nameEl: "Αεροδρόμιο", coordinate: .init(latitude: 37.9364, longitude: 23.9475), lineIds: ["M3", "P1"], isInterchange: true),
        ],
    ]

    static func stations(for lineId: String) -> [TransitStation] {
        stationsByLine[lineId] ?? []
    }

    // MARK: - Sample Departures

    static func sampleDepartures(for stationId: String, lineIds: [String]) -> [Departure] {
        let now = Calendar.current.component(.hour, from: Date()) * 60 + Calendar.current.component(.minute, from: Date())
        var departures: [Departure] = []

        for lineId in lineIds {
            let line = SyrmosData.line(for: lineId)
            let freq: Int
            switch lineId {
            case "M1": freq = 5
            case "M2": freq = 4
            case "M3": freq = 6
            default: freq = 12
            }

            for i in 1...4 {
                let mins = freq * i
                let depTime = now + mins
                let h = (depTime / 60) % 24
                let m = depTime % 60
                departures.append(Departure(
                    time: String(format: "%02d:%02d", h, m),
                    lineId: lineId,
                    direction: line?.terminalB ?? "Terminal",
                    minutesAway: mins
                ))
                departures.append(Departure(
                    time: String(format: "%02d:%02d", h, m + 2),
                    lineId: lineId,
                    direction: line?.terminalA ?? "Terminal",
                    minutesAway: mins + 2
                ))
            }
        }

        return departures.sorted { $0.minutesAway < $1.minutesAway }
    }
}
