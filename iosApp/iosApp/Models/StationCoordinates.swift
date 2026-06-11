import CoreLocation

// All coordinates from OpenStreetMap route relations via OASA/NAP GTFS data
// Source: /Users/p.dhespollari/Downloads/athens_fixed_rail_station_coordinates.md
// OSM route relations: M1=445858, M2=7963539, M3=445945, T6=3648688, T7=6792078
// Suburban: A1=8467445, A2=8467443, A3=8467442, A4=8467515

enum StationCoords {

    // MARK: - Metro M1 (Piraeus to Kifissia) - OSM relation 445858

    static let line1: [(id: String, name: String, nameEl: String, lat: Double, lon: Double)] = [
        ("M1_PIR", "Piraeus", "Πειραιάς", 37.9480230, 23.6432467),
        ("M1_FAL", "Faliro", "Φάληρο", 37.9450255, 23.6652902),
        ("M1_MOS", "Moschato", "Μοσχάτο", 37.9552687, 23.6804874),
        ("M1_KAL", "Kallithea", "Καλλιθέα", 37.9605389, 23.6974102),
        ("M1_TAV", "Tavros", "Ταύρος", 37.9625102, 23.7037122),
        ("M1_PET", "Petralona", "Πετράλωνα", 37.9689559, 23.7094090),
        ("M1_THE", "Thiseio", "Θησείο", 37.9766852, 23.7205356),
        ("M1_MON", "Monastiraki", "Μοναστηράκι", 37.9763360, 23.7258259),
        ("M1_OMO", "Omonia", "Ομόνοια", 37.9844999, 23.7281773),
        ("M1_VIC", "Victoria", "Βικτώρια", 37.9934877, 23.7303950),
        ("M1_ATT", "Attiki", "Αττική", 37.9995276, 23.7228526),
        ("M1_AGN", "Agios Nikolaos", "Άγιος Νικόλαος", 38.0069859, 23.7277292),
        ("M1_KAT", "Kato Patisia", "Κάτω Πατήσια", 38.0119208, 23.7286630),
        ("M1_AGE", "Agios Eleftherios", "Άγιος Ελευθέριος", 38.0203392, 23.7319353),
        ("M1_ANP", "Ano Patisia", "Άνω Πατήσια", 38.0241359, 23.7365181),
        ("M1_PER", "Perissos", "Περισσός", 38.0332002, 23.7450680),
        ("M1_PEF", "Pefkakia", "Πευκάκια", 38.0374108, 23.7505057),
        ("M1_NIO", "Nea Ionia", "Νέα Ιωνία", 38.0416198, 23.7554095),
        ("M1_IRK", "Irakleio", "Ηράκλειο", 38.0460142, 23.7665601),
        ("M1_EIR", "Eirini", "Ειρήνη", 38.0429902, 23.7837888),
        ("M1_NER", "Neratziotissa", "Νεραντζιώτισσα", 38.0455338, 23.7934250),
        ("M1_MAR", "Marousi", "Μαρούσι", 38.0565273, 23.8055114),
        ("M1_KAM", "KAT", "ΚΑΤ", 38.0663647, 23.8042356),
        ("M1_KHE", "Kifissia", "Κηφισιά", 38.0733951, 23.8082198),
    ]

    // MARK: - Metro M2 (Anthoupoli to Elliniko) - OSM relation 7963539

    static let line2: [(id: String, name: String, nameEl: String, lat: Double, lon: Double)] = [
        ("M2_ANT", "Anthoupoli", "Ανθούπολη", 38.0171089, 23.6908755),
        ("M2_PEE", "Peristeri", "Περιστέρι", 38.0128856, 23.6957605),
        ("M2_AGA", "Agios Antonios", "Άγιος Αντώνιος", 38.0060932, 23.6997156),
        ("M2_SEP", "Sepolia", "Σεπόλια", 38.0025946, 23.7140361),
        ("M2_ATT", "Attiki", "Αττική", 37.9995336, 23.7226918),
        ("M2_LAR", "Larissa Station", "Σταθμός Λαρίσης", 37.9922861, 23.7207006),
        ("M2_MET", "Metaxourgeio", "Μεταξουργείο", 37.9858450, 23.7213621),
        ("M2_OMO", "Omonia", "Ομόνοια", 37.9840538, 23.7279830),
        ("M2_PAN", "Panepistimio", "Πανεπιστήμιο", 37.9803461, 23.7330038),
        ("M2_SYN", "Syntagma", "Σύνταγμα", 37.9755009, 23.7356474),
        ("M2_AKR", "Akropoli", "Ακρόπολη", 37.9688590, 23.7295551),
        ("M2_SYG", "Syngrou-Fix", "Συγγρού-Φίξ", 37.9646366, 23.7268038),
        ("M2_NEK", "Neos Kosmos", "Νέος Κόσμος", 37.9576551, 23.7283685),
        ("M2_AGI", "Agios Ioannis", "Άγιος Ιωάννης", 37.9564161, 23.7346772),
        ("M2_DAF", "Dafni", "Δάφνη", 37.9495529, 23.7372110),
        ("M2_ALD", "Agios Dimitrios", "Άγιος Δημήτριος", 37.9398434, 23.7407267),
        ("M2_ILI", "Ilioupoli", "Ηλιούπολη", 37.9290637, 23.7447546),
        ("M2_ALM", "Alimos", "Άλιμος", 37.9178695, 23.7440625),
        ("M2_ARG", "Argyroupoli", "Αργυρούπολη", 37.9020569, 23.7456163),
        ("M2_ELL", "Elliniko", "Ελληνικό", 37.8925855, 23.7470953),
    ]

    // MARK: - Metro M3 (Dimotiko Theatro to Airport) - OSM relation 445945

    static let line3: [(id: String, name: String, nameEl: String, lat: Double, lon: Double)] = [
        ("M3_DIM", "Dimotiko Theatro", "Δημοτικό Θέατρο", 37.9429173, 23.6475926),
        ("M3_PIR", "Piraeus", "Πειραιάς", 37.9486160, 23.6423273),
        ("M3_MAN", "Maniatika", "Μανιάτικα", 37.9600579, 23.6397874),
        ("M3_NIK", "Nikaia", "Νίκαια", 37.9660813, 23.6478426),
        ("M3_KOR", "Korydallos", "Κορυδαλλός", 37.9770388, 23.6504221),
        ("M3_ABA", "Agia Varvara", "Αγία Βαρβάρα", 37.9899646, 23.6593530),
        ("M3_AMA", "Agia Marina", "Αγία Μαρίνα", 37.9968128, 23.6677666),
        ("M3_EGA", "Egaleo", "Αιγάλεω", 37.9913832, 23.6818003),
        ("M3_ELE", "Eleonas", "Ελαιώνας", 37.9877488, 23.6933605),
        ("M3_KER", "Kerameikos", "Κεραμεικός", 37.9785236, 23.7115309),
        ("M3_MON", "Monastiraki", "Μοναστηράκι", 37.9766579, 23.7263158),
        ("M3_SYN", "Syntagma", "Σύνταγμα", 37.9748526, 23.7357062),
        ("M3_EVA", "Evangelismos", "Ευαγγελισμός", 37.9761107, 23.7471092),
        ("M3_MEG", "Megaro Moussikis", "Μέγαρο Μουσικής", 37.9790211, 23.7530234),
        ("M3_AMP", "Ambelokipoi", "Αμπελόκηποι", 37.9871894, 23.7576995),
        ("M3_PNR", "Panormou", "Πανόρμου", 37.9931964, 23.7635814),
        ("M3_KTC", "Katechaki", "Κατεχάκη", 37.9937402, 23.7767641),
        ("M3_ETH", "Ethniki Amyna", "Εθνική Άμυνα", 37.9991725, 23.7844882),
        ("M3_HOL", "Cholargos", "Χολαργός", 38.0048986, 23.7947223),
        ("M3_NOM", "Nomismatokopio", "Νομισματοκοπείο", 38.0090857, 23.8057861),
        ("M3_APR", "Agia Paraskevi", "Αγία Παρασκευή", 38.0171203, 23.8125006),
        ("M3_HAL", "Chalandri", "Χαλάνδρι", 38.0216963, 23.8207595),
        ("M3_DPL", "Douk. Plakentias", "Δουκίσσης Πλακεντίας", 38.0239773, 23.8325569),
        ("M3_PAL", "Pallini", "Παλλήνη", 38.0045807, 23.8699500),
        ("M3_PEK", "Peania-Kantza", "Παιανία-Κάντζα", 37.9849428, 23.8700731),
        ("M3_KRP", "Koropi", "Κορωπί", 37.9125370, 23.8960768),
        ("M3_AER", "Airport", "Αεροδρόμιο", 37.9368813, 23.9447336),
    ]

    // MARK: - Tram T6 (Syntagma to Pikrodafni) - OSM relation 3648688

    static let tramT6: [(id: String, name: String, nameEl: String, lat: Double, lon: Double)] = [
        ("T6_SYN", "Syntagma", "Σύνταγμα", 37.9744347, 23.7353381),
        ("T6_ZAP", "Zappeion", "Ζάππειο", 37.9693069, 23.7364183),
        ("T6_VOU", "Vouliagmenis", "Βουλιαγμένης", 37.9667197, 23.7317325),
        ("T6_FIX", "Fix", "Φίξ", 37.9648164, 23.7276122),
        ("T6_KAS", "Kasomouli", "Κασομούλη", 37.9604298, 23.7234816),
        ("T6_NEK", "Neos Kosmos", "Νέος Κόσμος", 37.9569955, 23.7270853),
        ("T6_BAK", "Baknana", "Μπακνανά", 37.9546714, 23.7238777),
        ("T6_AIG", "Aegeou", "Αιγαίου", 37.9501464, 23.7189049),
        ("T6_AFP", "Ag. Fotinis", "Αγίας Φωτεινής", 37.9467388, 23.7150445),
        ("T6_MAL", "Meg. Alexandrou", "Μεγ. Αλεξάνδρου", 37.9427014, 23.7137932),
        ("T6_APK", "Ag. Paraskevi", "Αγία Παρασκευή", 37.9402146, 23.7130260),
        ("T6_MED", "Medeas-Mykalis", "Μηδείας-Μυκάλης", 37.9371506, 23.7120552),
        ("T6_EVS", "Evangeliki Scholi", "Ευαγγελική Σχολή", 37.9333042, 23.7108485),
        ("T6_ACH", "Achilleos", "Αχιλλέως", 37.9301057, 23.7098664),
        ("T6_AMF", "Amfitheas", "Αμφιθέας", 37.9281400, 23.7050355),
        ("T6_PAN", "Panaghitsa", "Παναγίτσα", 37.9252678, 23.7018664),
        ("T6_MOU", "Mousson", "Μουσών", 37.9220022, 23.6997361),
        ("T6_EDE", "Edem", "Έδεμ", 37.9187302, 23.7006817),
        ("T6_PIK", "Pikrodafni", "Πικροδάφνη", 37.9159342, 23.7054737),
    ]

    // MARK: - Tram T7 (Akti Poseidonos to Asklipiio Voulas) - OSM relation 6792078

    static let tramT7: [(id: String, name: String, nameEl: String, lat: Double, lon: Double)] = [
        // --- Piraeus inbound loop (one-way circular section into Akti Poseidonos) ---
        // Per the athens_transit_icons_and_rules_package, the six loop stops
        // below run as an inbound loop terminating at stop 7 (Akti Poseidonos).
        // They are kept in package-defined order so map polyline can draw the
        // loop arc before joining the outbound spine.
        ("T7_DIM", "Dimotiko Theatro", "Δημοτικό Θέατρο", 37.9416350, 23.6506700),
        ("T7_PLA", "Plateia Deligianni", "Πλατεία Δεληγιάννη", 37.9449550, 23.6535200),
        ("T7_EVA", "Evangelistria", "Ευαγγελίστρια", 37.9480500, 23.6561550),
        ("T7_GRI", "Grigoriou Lambraki", "Γρηγορίου Λαμπράκη", 37.9457700, 23.6601800),
        ("T7_MIK", "Mikras Asias", "Μικράς Ασίας", 37.9444100, 23.6649650),
        ("T7_GIP", "Gipedo Karaiskaki", "Γήπεδο Καραϊσκάκη", 37.9445650, 23.6687750),
        // --- Outbound spine: Akti Poseidonos -> Asklipiio Voulas ---
        ("T7_AKT", "Akti Poseidonos", "Ακτή Ποσειδώνος", 37.9449134, 23.6430209),
        ("T7_ATR", "Agia Triada", "Αγία Τριάδα", 37.9449403, 23.6451956),
        ("T7_IPP", "Pl. Ippodameias", "Πλ. Ιπποδαμείας", 37.9474688, 23.6475793),
        ("T7_34S", "34 Synt. Pezikou", "34ου Συντ. Πεζικού", 37.9485363, 23.6522544),
        ("T7_AND", "Androutsou", "Ανδρούτσου", 37.9478346, 23.6560463),
        ("T7_SKY", "Om. Skylitsi", "Ομ. Σκυλίτση", 37.9450359, 23.6609189),
        ("T7_SEF", "SEF", "ΣΕΦ", 37.9438634, 23.6638070),
        ("T7_NEF", "Neo Faliro", "Νέο Φάληρο", 37.9445726, 23.6685528),
        ("T7_MOS", "Moschato", "Μοσχάτο", 37.9442591, 23.6779634),
        ("T7_KAL", "Kallithea", "Καλλιθέα", 37.9427133, 23.6840167),
        ("T7_TZI", "Tzitzifies", "Τζιτζιφιές", 37.9407062, 23.6880185),
        ("T7_DEL", "Delta Falirou", "Δέλτα Φαλήρου", 37.9374095, 23.6922895),
        ("T7_SKE", "Ag. Skepi", "Αγία Σκέπη", 37.9340166, 23.6939305),
        ("T7_TRO", "Trocadero", "Τροκαντερό", 37.9313941, 23.6872264),
        ("T7_PFL", "Parko Flisvou", "Πάρκο Φλοίσβου", 37.9278472, 23.6884354),
        ("T7_FLI", "Flisvos", "Φλοίσβος", 37.9235633, 23.6927091),
        ("T7_BAT", "Batis", "Μπάτης", 37.9215266, 23.6964201),
        ("T7_EDE", "Edem", "Έδεμ", 37.9187302, 23.7006817),
        ("T7_PIK", "Pikrodafni", "Πικροδάφνη", 37.9158920, 23.7054124),
        ("T7_MAR", "Marina Alimou", "Μαρίνα Αλίμου", 37.9129907, 23.7087188),
        ("T7_KAM", "Kalamaki", "Καλαμάκι", 37.9096776, 23.7128631),
        ("T7_ZEF", "Zefyros", "Ζέφυρος", 37.9063769, 23.7170206),
        ("T7_LOU", "Loutra Alimou", "Λουτρά Αλίμου", 37.9022284, 23.7195316),
        ("T7_ELL", "Elliniko", "Ελληνικό", 37.8975873, 23.7201746),
        ("T7_AK1", "1st Ag. Kosma", "1η Αγ. Κοσμά", 37.8942956, 23.7211661),
        ("T7_AK2", "2nd Ag. Kosma", "2η Αγ. Κοσμά", 37.8907821, 23.7231968),
        ("T7_AGA", "Ag. Alexandros", "Άγ. Αλέξανδρος", 37.8850490, 23.7269291),
        ("T7_EOL", "Ellinon Olympionikon", "Ελλ. Ολυμπιονικών", 37.8811588, 23.7294629),
        ("T7_KIS", "Kentro Istioploias", "Κέντρο Ιστιοπλοΐας", 37.8758816, 23.7318572),
        ("T7_VER", "Pl. Vergoti", "Πλ. Βεργωτή", 37.8714672, 23.7351894),
        ("T7_GLY", "Paralia Glyfadas", "Παραλία Γλυφάδας", 37.8677342, 23.7383803),
        ("T7_PDM", "Paleo Demarhio", "Παλαιό Δημαρχείο", 37.8646553, 23.7431856),
        ("T7_KAT", "Pl. Vaso Katraki", "Πλ. Βάσω Κατράκη", 37.8634672, 23.7475090),
        ("T7_MET", "Ag. Metaxa", "Αγγ. Μεταξά", 37.8627144, 23.7514044),
        ("T7_ESP", "Pl. Esperidon", "Πλ. Εσπερίδων", 37.8601549, 23.7541971),
        ("T7_KOL", "Kolymvitirio", "Κολυμβητήριο", 37.8560908, 23.7542381),
        ("T7_VOL", "Asklipiio Voulas", "Ασκληπιείο Βούλας", 37.8496644, 23.7525900),
    ]

    // MARK: - Suburban A1 (Piraeus to Airport) - OSM relation 8467445

    static let suburbanA1: [(id: String, name: String, nameEl: String, lat: Double, lon: Double)] = [
        ("A1_PIR", "Piraeus", "Πειραιάς", 37.9490666, 23.6434156),
        ("A1_LEF", "Lefka", "Λεύκα", 37.9555849, 23.6541235),
        ("A1_REN", "Rentis", "Ρέντης", 37.9622619, 23.6683076),
        ("A1_TAV", "Tavros", "Ταύρος", 37.9689397, 23.6942248),
        ("A1_ROU", "Rouf", "Ρουφ", 37.9736019, 23.7040087),
        ("A1_ATH", "Athens", "Αθήνα", 37.9931275, 23.7202839),
        ("A1_AAN", "Ag. Anargyroi", "Άγιοι Ανάργυροι", 38.0224583, 23.7186452),
        ("A1_PYR", "Pyrgos Vasilissis", "Πύργος Βασιλίσσης", 38.0400365, 23.7276759),
        ("A1_KAC", "Kato Acharnai", "Κάτω Αχαρναί", 38.0547251, 23.7328066),
        ("A1_MET", "Metamorfosi", "Μεταμόρφωση", 38.0600674, 23.7562064),
        ("A1_IRK", "Irakleio", "Ηράκλειο", 38.0567845, 23.7720928),
        ("A1_NER", "Neratziotissa", "Νερατζιώτισσα", 38.0447682, 23.7941064),
        ("A1_KIF", "Kifisias", "Κηφισίας", 38.0419210, 23.8040729),
        ("A1_PEN", "Pentelis", "Πεντέλης", 38.0328796, 23.8225838),
        ("A1_DPL", "Douk. Plakentias", "Δουκίσσης Πλακεντίας", 38.0247026, 23.8338693),
        ("A1_PAL", "Pallini", "Παλλήνη", 38.0054925, 23.8696405),
        ("A1_PEK", "Peania-Kantza", "Παιανία-Κάντζα", 37.9839819, 23.8698255),
        ("A1_KRP", "Koropi", "Κορωπί", 37.9133050, 23.8955043),
        ("A1_AER", "Airport", "Αεροδρόμιο", 37.9368156, 23.9448470),
    ]

    // MARK: - Suburban A2 (Ano Liosia to Airport) - OSM relation 8467443

    static let suburbanA2: [(id: String, name: String, nameEl: String, lat: Double, lon: Double)] = [
        ("A2_ANL", "Ano Liosia", "Άνω Λιόσια", 38.0707953, 23.7100051),
        ("A2_ACH", "Acharnai Center", "Σιδ. Κέντρο Αχαρνών", 38.0656438, 23.7376508),
        ("A2_MET", "Metamorfosi", "Μεταμόρφωση", 38.0600674, 23.7562064),
        ("A2_IRK", "Irakleio", "Ηράκλειο", 38.0567845, 23.7720928),
        ("A2_NER", "Neratziotissa", "Νερατζιώτισσα", 38.0447682, 23.7941064),
        ("A2_KIF", "Kifisias", "Κηφισίας", 38.0419210, 23.8040729),
        ("A2_PEN", "Pentelis", "Πεντέλης", 38.0328796, 23.8225838),
        ("A2_DPL", "Douk. Plakentias", "Δουκίσσης Πλακεντίας", 38.0247026, 23.8338693),
        ("A2_PAL", "Pallini", "Παλλήνη", 38.0054925, 23.8696405),
        ("A2_PEK", "Peania-Kantza", "Παιανία-Κάντζα", 37.9839819, 23.8698255),
        ("A2_KRP", "Koropi", "Κορωπί", 37.9133050, 23.8955043),
        ("A2_AER", "Airport", "Αεροδρόμιο", 37.9368156, 23.9448470),
    ]

    // MARK: - Suburban A3 (Athens to Chalcis) - OSM relation 8467442

    static let suburbanA3: [(id: String, name: String, nameEl: String, lat: Double, lon: Double)] = [
        ("A3_ATH", "Athens", "Αθήνα", 37.9931275, 23.7202839),
        ("A3_AAN", "Ag. Anargyroi", "Άγιοι Ανάργυροι", 38.0224583, 23.7186452),
        ("A3_ACH", "Acharnai Center", "Σιδ. Κέντρο Αχαρνών", 38.0686445, 23.7378210),
        ("A3_AXN", "Acharnes", "Αχαρνές", 38.0802534, 23.7440766),
        ("A3_DEK", "Dekeleia", "Δεκέλεια", 38.0997540, 23.7801125),
        ("A3_AST", "Ag. Stefanos", "Άγιος Στέφανος", 38.1403696, 23.8591873),
        ("A3_AFI", "Afidnes", "Αφίδνες", 38.1883264, 23.8444658),
        ("A3_SFE", "Sfendali", "Σφενδάλη", 38.2354158, 23.7844141),
        ("A3_AVL", "Avlonas", "Αυλώνας", 38.2504724, 23.6955986),
        ("A3_ATH2", "Ag. Thomas", "Άγιος Θωμάς", 38.2816770, 23.6672270),
        ("A3_OIN", "Oinofyta", "Οινόφυτα", 38.3069654, 23.6338955),
        ("A3_OIN2", "Oinoi", "Οινόη", 38.3230172, 23.6090770),
        ("A3_DIL", "Dilesi", "Δήλεσι", 38.3376364, 23.6094499),
        ("A3_AGE", "Ag. Georgios", "Άγιος Γεώργιος", 38.3548928, 23.6074074),
        ("A3_KAL", "Kalochori-Panteichi", "Καλοχώρι-Παντείχι", 38.3893073, 23.5931559),
        ("A3_AUL", "Avlida", "Αυλίδα", 38.4044464, 23.6033835),
        ("A3_CHA", "Chalkida", "Χαλκίδα", 38.4625271, 23.5861659),
    ]

    // MARK: - Suburban A4 (Piraeus to Kiato) - OSM relation 8467515

    static let suburbanA4: [(id: String, name: String, nameEl: String, lat: Double, lon: Double)] = [
        ("A4_PIR", "Piraeus", "Πειραιάς", 37.9490666, 23.6434156),
        ("A4_LEF", "Lefka", "Λεύκα", 37.9555849, 23.6541235),
        ("A4_REN", "Rentis", "Ρέντης", 37.9622619, 23.6683076),
        ("A4_TAV", "Tavros", "Ταύρος", 37.9689397, 23.6942248),
        ("A4_ROU", "Rouf", "Ρουφ", 37.9736019, 23.7040087),
        ("A4_ATH", "Athens", "Αθήνα", 37.9931275, 23.7202839),
        ("A4_AAN", "Ag. Anargyroi", "Άγιοι Ανάργυροι", 38.0224583, 23.7186452),
        ("A4_PYR", "Pyrgos Vasilissis", "Πύργος Βασιλίσσης", 38.0400365, 23.7276759),
        ("A4_KAC", "Kato Acharnai", "Κάτω Αχαρναί", 38.0547251, 23.7328066),
        ("A4_ZEF", "Zefyri", "Ζεφύρι", 38.0699579, 23.7163427),
        ("A4_ANL", "Ano Liosia", "Άνω Λιόσια", 38.0707953, 23.7100051),
        ("A4_ASP", "Aspropyrgos", "Ασπρόπυργος", 38.0810388, 23.6042595),
        ("A4_MAG", "Magoula", "Μαγούλα", 38.0730827, 23.5291665),
        ("A4_NPE", "Nea Peramos", "Νέα Πέραμος", 38.0127986, 23.4132616),
        ("A4_MEG", "Megara", "Μέγαρα", 37.9910006, 23.3610190),
        ("A4_KIN", "Kineta", "Κινέτα", 37.9654426, 23.2010371),
        ("A4_ATH3", "Ag. Theodoroi", "Άγιοι Θεόδωροι", 37.9332405, 23.1369832),
        ("A4_COR", "Corinth", "Κόρινθος", 37.9209680, 22.9323960),
        ("A4_ZEV", "Zevgolatio", "Ζευγολατιό", 37.9263503, 22.8046326),
        ("A4_KIA", "Kiato", "Κιάτο", 38.0139838, 22.7348102),
    ]

    // MARK: - Line associations

    static let lineAssociations: [String: [String]] = [
        "M1_PIR": ["M1", "M3", "P1"], "M1_MON": ["M1", "M3"], "M1_OMO": ["M1", "M2"],
        "M1_ATT": ["M1", "M2"], "M1_NER": ["M1", "P1"],
        "M2_ATT": ["M2", "M1"], "M2_OMO": ["M2", "M1"], "M2_SYN": ["M2", "M3"],
        "M2_LAR": ["M2", "P1"],
        "M3_PIR": ["M3", "M1", "P1"], "M3_MON": ["M3", "M1"], "M3_SYN": ["M3", "M2"],
        "M3_DPL": ["M3", "P1"], "M3_AER": ["M3", "P1"],
        "T6_SYN": ["T6", "M2", "M3"], "T6_EDE": ["T6", "T7"], "T6_PIK": ["T6", "T7"],
        "T7_EDE": ["T7", "T6"], "T7_PIK": ["T7", "T6"],
        "A1_PIR": ["A1", "M1"], "A1_ATH": ["A1", "M2"], "A1_NER": ["A1", "M1"],
        "A1_DPL": ["A1", "M3"], "A1_AER": ["A1", "M3"],
        "A2_NER": ["A2", "M1"], "A2_DPL": ["A2", "M3"], "A2_AER": ["A2", "M3"],
        "A3_ATH": ["A3", "M2"], "A3_AAN": ["A3"],
        "A4_PIR": ["A4", "M1"], "A4_ATH": ["A4", "M2"],
    ]

    static var allStations: [TransitStation] {
        var stationMap: [String: TransitStation] = [:]

        func add(_ list: [(id: String, name: String, nameEl: String, lat: Double, lon: Double)], defaultLine: String) {
            for s in list {
                if stationMap[s.id] != nil { continue }
                let lines = lineAssociations[s.id] ?? [defaultLine]
                stationMap[s.id] = TransitStation(
                    id: s.id, name: s.name, nameEl: s.nameEl,
                    coordinate: CLLocationCoordinate2D(latitude: s.lat, longitude: s.lon),
                    lineIds: lines, isInterchange: lines.count > 1
                )
            }
        }

        add(line1, defaultLine: "M1")
        add(line2, defaultLine: "M2")
        add(line3, defaultLine: "M3")
        add(tramT6, defaultLine: "T6")
        add(tramT7, defaultLine: "T7")
        add(suburbanA1, defaultLine: "A1")
        add(suburbanA2, defaultLine: "A2")
        add(suburbanA3, defaultLine: "A3")
        add(suburbanA4, defaultLine: "A4")

        return Array(stationMap.values).sorted { $0.name < $1.name }
    }
}
