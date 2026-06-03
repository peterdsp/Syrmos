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

    // MARK: - Tram T6 (Syntagma - Pikrodafni)

    static let tramT6: [(id: String, name: String, nameEl: String, lat: Double, lon: Double)] = [
        ("T6_SYN", "Syntagma", "Σύνταγμα", 37.9750, 23.7350),
        ("T6_ZAP", "Zappeio", "Ζάππειο", 37.9717, 23.7356),
        ("T6_VOU", "L. Vouliagmenis", "Λ. Βουλιαγμένης", 37.9667, 23.7303),
        ("T6_FIX", "Fix", "Φιξ", 37.9633, 23.7275),
        ("T6_KAS", "Kasomouli", "Κασομούλη", 37.9597, 23.7258),
        ("T6_NEK", "Neos Kosmos", "Νέος Κόσμος", 37.9556, 23.7242),
        ("T6_BAK", "Baknana", "Μπακνανά", 37.9525, 23.7219),
        ("T6_AIG", "Aegeou", "Αιγαίου", 37.9492, 23.7203),
        ("T6_AFP", "Agia Fotini", "Αγία Φωτεινή", 37.9458, 23.7186),
        ("T6_MAL", "Meg. Alexandrou", "Μεγ. Αλεξάνδρου", 37.9425, 23.7172),
        ("T6_APK", "Agia Paraskevi", "Αγία Παρασκευή", 37.9389, 23.7153),
        ("T6_MED", "Medeas-Mykalis", "Μηδείας-Μυκάλης", 37.9375, 23.7147),
        ("T6_EVS", "Evangeliki Scholi", "Ευαγγελική Σχολή", 37.9358, 23.7128),
        ("T6_ACH", "Achilleos", "Αχιλλέως", 37.9339, 23.7108),
        ("T6_AMF", "Amfitheas", "Αμφιθέας", 37.9314, 23.7081),
        ("T6_PAN", "Panaghitsa", "Παναγίτσα", 37.9289, 23.7056),
        ("T6_MOU", "Mousson", "Μουσών", 37.9261, 23.7028),
        ("T6_EDE", "Edem", "Εδέμ", 37.9222, 23.6989),
        ("T6_PIK", "Pikrodafni", "Πικροδάφνη", 37.9194, 23.6964),
    ]

    // MARK: - Tram T7 (Akti Posidonos - Asklipiio Voulas) key stops

    static let tramT7: [(id: String, name: String, nameEl: String, lat: Double, lon: Double)] = [
        ("T7_AKT", "Akti Posidonos", "Ακτή Ποσειδώνος", 37.9420, 23.6610),
        ("T7_SEF", "S.E.F.", "Σ.Ε.Φ.", 37.9440, 23.6690),
        ("T7_NEF", "Neo Faliro", "Νέο Φάληρο", 37.9460, 23.6730),
        ("T7_MOS", "Moschato", "Μοσχάτο", 37.9480, 23.6760),
        ("T7_KAL", "Kallithea", "Καλλιθέα", 37.9500, 23.6830),
        ("T7_TZI", "Tzitzifies", "Τζιτζιφιές", 37.9430, 23.6900),
        ("T7_DEL", "Delta Falirou", "Δέλτα Φαλήρου", 37.9380, 23.6930),
        ("T7_TRO", "Trocadero", "Τροκαντερό", 37.9350, 23.6940),
        ("T7_FLI", "Flisvos", "Φλοίσβος", 37.9320, 23.6950),
        ("T7_BAT", "Batis", "Μπάτις", 37.9280, 23.6960),
        ("T7_EDE", "Edem", "Εδέμ", 37.9222, 23.6989),
        ("T7_PIK", "Pikrodafni", "Πικροδάφνη", 37.9194, 23.6964),
        ("T7_MAR", "Marina Alimou", "Μαρίνα Αλίμου", 37.9150, 23.7050),
        ("T7_KAM", "Kalamaki", "Καλαμάκι", 37.9120, 23.7100),
        ("T7_ELL", "Elliniko", "Ελληνικό", 37.8980, 23.7310),
        ("T7_AKO", "Ag. Kosmas", "Αγ. Κοσμάς", 37.8880, 23.7220),
        ("T7_GLY", "Glyfada", "Γλυφάδα", 37.8700, 23.7470),
        ("T7_VOL", "Asklipiio Voulas", "Ασκληπίειο Βούλας", 37.8440, 23.7700),
    ]

    // MARK: - Suburban Railway P1 (Airport - Piraeus) key stops

    static let suburbanP1: [(id: String, name: String, nameEl: String, lat: Double, lon: Double)] = [
        ("P1_AER", "Airport", "Αεροδρόμιο", 37.9364, 23.9475),
        ("P1_DPL", "Douk. Plakentias", "Δουκ. Πλακεντίας", 38.0130, 23.8380),
        ("P1_PEN", "Pendeli", "Πεντέλη", 38.0300, 23.8200),
        ("P1_KIF", "Kifisia", "Κηφισιά", 38.0732, 23.8082),
        ("P1_NER", "Neratziotissa", "Νερατζιώτισσα", 38.0451, 23.7929),
        ("P1_IRN", "Irinida", "Ειρηνίδα", 38.0417, 23.7856),
        ("P1_SKA", "SKA", "ΣΚΑ", 38.0350, 23.7700),
        ("P1_LAR", "Larissa Station", "Σταθμός Λαρίσης", 37.9914, 23.7217),
        ("P1_PIR", "Piraeus", "Πειραιάς", 37.9481, 23.6423),
    ]

    // MARK: - Line associations (which lines serve each station)

    static let lineAssociations: [String: [String]] = [
        // Metro interchanges
        "M1_PIR": ["M1", "M3", "P1"],
        "M1_MON": ["M1", "M3"],
        "M1_OMO": ["M1", "M2"],
        "M1_ATT": ["M1", "M2"],
        "M1_NER": ["M1", "P1"],
        "M2_SYN": ["M2", "M3"],
        "M2_LAR": ["M2", "P1"],
        "M3_PIR": ["M3", "M1", "P1"],
        "M3_MON": ["M3", "M1"],
        "M3_SYN": ["M3", "M2"],
        "M3_DPL": ["M3", "P1"],
        "M3_AER": ["M3", "P1"],
        // Tram interchanges
        "T6_SYN": ["T6", "M2", "M3"],
        "T6_EDE": ["T6", "T7"],
        "T6_PIK": ["T6", "T7"],
        "T7_EDE": ["T7", "T6"],
        "T7_PIK": ["T7", "T6"],
        // Suburban
        "P1_AER": ["P1", "M3"],
        "P1_DPL": ["P1", "M3"],
        "P1_NER": ["P1", "M1"],
        "P1_LAR": ["P1", "M2"],
        "P1_PIR": ["P1", "M1"],
    ]

    static var allStations: [TransitStation] {
        var stationMap: [String: TransitStation] = [:]

        func add(_ list: [(id: String, name: String, nameEl: String, lat: Double, lon: Double)], defaultLine: String) {
            for s in list {
                if stationMap[s.id] != nil { continue }
                let lines = lineAssociations[s.id] ?? [defaultLine]
                stationMap[s.id] = TransitStation(
                    id: s.id,
                    name: s.name,
                    nameEl: s.nameEl,
                    coordinate: CLLocationCoordinate2D(latitude: s.lat, longitude: s.lon),
                    lineIds: lines,
                    isInterchange: lines.count > 1
                )
            }
        }

        add(line1, defaultLine: "M1")
        add(line2, defaultLine: "M2")
        add(line3, defaultLine: "M3")
        add(tramT6, defaultLine: "T6")
        add(tramT7, defaultLine: "T7")
        add(suburbanP1, defaultLine: "P1")

        return Array(stationMap.values).sorted { $0.name < $1.name }
    }
}
