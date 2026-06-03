import CoreLocation

// Coordinates sourced from Wikidata (Line 1) and Wikipedia (Lines 2, 3)
// Verified against Apple Maps transit layer positions
enum StationCoords {

    // MARK: - Line 1 (Green) Piraeus - Kifisia (from Wikidata Q6553095)

    static let line1: [(id: String, name: String, nameEl: String, lat: Double, lon: Double)] = [
        ("M1_PIR", "Piraeus", "Πειραιάς", 37.9481, 23.6423),
        ("M1_FAL", "Faliro", "Φάληρο", 37.9450, 23.6653),
        ("M1_MOS", "Moschato", "Μοσχάτο", 37.9442, 23.6780),
        ("M1_KAL", "Kallithea", "Καλλιθέα", 37.9604, 23.6970),
        ("M1_TAV", "Tavros", "Ταύρος", 37.9624, 23.7033),
        ("M1_PET", "Petralona", "Πετράλωνα", 37.9685, 23.7091),
        ("M1_THE", "Thissio", "Θησείο", 37.9768, 23.7201),
        ("M1_MON", "Monastiraki", "Μοναστηράκι", 37.9766, 23.7259),
        ("M1_OMO", "Omonia", "Ομόνοια", 37.9840, 23.7280),
        ("M1_VIC", "Victoria", "Βικτώρια", 37.9930, 23.7302),
        ("M1_ATT", "Attiki", "Αττική", 37.9995, 23.7228),
        ("M1_AGN", "Agios Nikolaos", "Άγιος Νικόλαος", 38.0068, 23.7276),
        ("M1_KAT", "Kato Patissia", "Κάτω Πατήσια", 38.0115, 23.7286),
        ("M1_AGE", "Agios Eleftherios", "Άγιος Ελευθέριος", 38.0198, 23.7316),
        ("M1_ANP", "Ano Patissia", "Άνω Πατήσια", 38.0237, 23.7360),
        ("M1_PER", "Perissos", "Περισσός", 38.0328, 23.7447),
        ("M1_PEF", "Pefkakia", "Πευκάκια", 38.0370, 23.7501),
        ("M1_NIO", "Nea Ionia", "Νέα Ιωνία", 38.0414, 23.7548),
        ("M1_IRK", "Iraklio", "Ηράκλειο", 38.0462, 23.7660),
        ("M1_EIR", "Eirini", "Ειρήνη", 38.0433, 23.7833),
        ("M1_NER", "Neratziotissa", "Νερατζιώτισσα", 38.0451, 23.7929),
        ("M1_MAR", "Maroussi", "Μαρούσι", 38.0562, 23.8049),
        ("M1_KAM", "KAT", "ΚΑΤ", 38.0660, 23.8040),
        ("M1_KHE", "Kifisia", "Κηφισιά", 38.0732, 23.8082),
    ]

    // MARK: - Line 2 (Red) Anthoupoli - Elliniko (from Wikipedia verified coords)

    static let line2: [(id: String, name: String, nameEl: String, lat: Double, lon: Double)] = [
        ("M2_ANT", "Anthoupoli", "Ανθούπολη", 38.0130, 23.7010),
        ("M2_PEE", "Peristeri", "Περιστέρι", 38.0100, 23.6930),
        ("M2_AGA", "Ag. Anargyroi", "Άγ. Ανάργυροι", 38.0040, 23.6990),
        ("M2_SEP", "Sepolia", "Σεπόλια", 37.9994, 23.7070),
        ("M2_ATT", "Attiki", "Αττική", 37.9995, 23.7228),
        ("M2_LAR", "Larissa Station", "Σταθμός Λαρίσης", 37.9914, 23.7217),
        ("M2_MET", "Metaxourghio", "Μεταξουργείο", 37.9849, 23.7193),
        ("M2_OMO", "Omonia", "Ομόνοια", 37.9840, 23.7280),
        ("M2_PAN", "Panepistimio", "Πανεπιστήμιο", 37.9807, 23.7334),
        ("M2_SYN", "Syntagma", "Σύνταγμα", 37.9755, 23.7353),
        ("M2_AKR", "Akropoli", "Ακρόπολη", 37.9694, 23.7288),
        ("M2_SYG", "Syngrou-Fix", "Συγγρού-Φιξ", 37.9640, 23.7266),
        ("M2_NEK", "Neos Kosmos", "Νέος Κόσμος", 37.9559, 23.7311),
        ("M2_AGI", "Agios Ioannis", "Άγιος Ιωάννης", 37.9497, 23.7316),
        ("M2_DAF", "Dafni", "Δάφνη", 37.9422, 23.7378),
        ("M2_ALD", "Agios Dimitrios", "Άγιος Δημήτριος", 37.9356, 23.7403),
        ("M2_ILI", "Ilioupoli", "Ηλιούπολη", 37.9278, 23.7468),
        ("M2_ALM", "Alimos", "Άλιμος", 37.9158, 23.7258),
        ("M2_ARG", "Argyroupoli", "Αργυρούπολη", 37.9061, 23.7488),
        ("M2_ELL", "Elliniko", "Ελληνικό", 37.8990, 23.7440),
    ]

    // MARK: - Line 3 (Blue) Dimotiko Theatro - Airport (from Wikipedia verified coords)

    static let line3: [(id: String, name: String, nameEl: String, lat: Double, lon: Double)] = [
        ("M3_DIM", "Dimotiko Theatro", "Δημοτικό Θέατρο", 37.9444, 23.6461),
        ("M3_PIR", "Piraeus", "Πειραιάς", 37.9481, 23.6423),
        ("M3_MAN", "Maniatika", "Μανιάτικα", 37.9473, 23.6504),
        ("M3_NIK", "Nikaia", "Νίκαια", 37.9540, 23.6550),
        ("M3_KOR", "Korydallos", "Κορυδαλλός", 37.9600, 23.6570),
        ("M3_ABA", "Agia Varvara", "Αγία Βαρβάρα", 37.9680, 23.6660),
        ("M3_AMA", "Agia Marina", "Αγία Μαρίνα", 37.9740, 23.6890),
        ("M3_EGA", "Egaleo", "Αιγάλεω", 37.9800, 23.6910),
        ("M3_ELE", "Eleonas", "Ελαιώνας", 37.9820, 23.7070),
        ("M3_KER", "Kerameikos", "Κεραμεικός", 37.9780, 23.7139),
        ("M3_MON", "Monastiraki", "Μοναστηράκι", 37.9766, 23.7259),
        ("M3_SYN", "Syntagma", "Σύνταγμα", 37.9755, 23.7353),
        ("M3_EVA", "Evangelismos", "Ευαγγελισμός", 37.9758, 23.7444),
        ("M3_MEG", "Megaro Moussikis", "Μέγαρο Μουσικής", 37.9783, 23.7508),
        ("M3_AMP", "Ambelokipoi", "Αμπελόκηποι", 37.9847, 23.7569),
        ("M3_PNR", "Panormou", "Πανόρμου", 37.9919, 23.7628),
        ("M3_KTC", "Katechaki", "Κατεχάκη", 37.9989, 23.7706),
        ("M3_ETH", "Ethniki Amyna", "Εθνική Άμυνα", 38.0008, 23.7819),
        ("M3_HOL", "Holargos", "Χολαργός", 38.0000, 23.7944),
        ("M3_NOM", "Nomismatokopeio", "Νομισματοκοπείο", 37.9961, 23.8058),
        ("M3_APR", "Agia Paraskevi", "Αγία Παρασκευή", 37.9953, 23.8183),
        ("M3_HAL", "Halandri", "Χαλάνδρι", 38.0033, 23.8256),
        ("M3_DPL", "Douk. Plakentias", "Δουκ. Πλακεντίας", 38.0130, 23.8380),
        ("M3_PAL", "Pallini", "Παλλήνη", 38.0030, 23.8690),
        ("M3_PEK", "Peania-Kantza", "Παιανία-Κάντζα", 37.9870, 23.8790),
        ("M3_KRP", "Koropi", "Κορωπί", 37.9630, 23.8870),
        ("M3_AER", "Airport", "Αεροδρόμιο", 37.9364, 23.9475),
    ]

    static var allStations: [TransitStation] {
        let interchangeIds: Set<String> = [
            "M1_PIR", "M1_MON", "M1_OMO", "M1_ATT", "M1_NER",
            "M2_ATT", "M2_OMO", "M2_SYN", "M2_LAR",
            "M3_PIR", "M3_MON", "M3_SYN", "M3_DPL", "M3_AER",
        ]

        func makeStation(_ s: (id: String, name: String, nameEl: String, lat: Double, lon: Double), lines: [String]) -> TransitStation {
            TransitStation(
                id: s.id,
                name: s.name,
                nameEl: s.nameEl,
                coordinate: CLLocationCoordinate2D(latitude: s.lat, longitude: s.lon),
                lineIds: lines,
                isInterchange: interchangeIds.contains(s.id)
            )
        }

        var stationMap: [String: TransitStation] = [:]

        for s in line1 {
            var lines = ["M1"]
            if s.id == "M1_MON" { lines.append("M3") }
            if s.id == "M1_OMO" { lines.append("M2") }
            if s.id == "M1_ATT" { lines.append("M2") }
            if s.id == "M1_PIR" { lines.append(contentsOf: ["M3", "P1"]) }
            if s.id == "M1_NER" { lines.append("P1") }
            stationMap[s.id] = makeStation(s, lines: lines)
        }
        for s in line2 {
            if stationMap[s.id] != nil { continue }
            var lines = ["M2"]
            if s.id == "M2_SYN" { lines.append("M3") }
            if s.id == "M2_LAR" { lines.append("P1") }
            stationMap[s.id] = makeStation(s, lines: lines)
        }
        for s in line3 {
            if stationMap[s.id] != nil { continue }
            var lines = ["M3"]
            if s.id == "M3_DPL" { lines.append("P1") }
            if s.id == "M3_AER" { lines.append("P1") }
            stationMap[s.id] = makeStation(s, lines: lines)
        }

        return Array(stationMap.values).sorted { $0.name < $1.name }
    }
}
