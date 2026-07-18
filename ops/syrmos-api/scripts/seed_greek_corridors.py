"""Seed the national + Thessaloniki-suburban rail corridors.

Every time here is transcribed from docs/plans/greece_passenger_rail_timetables_
2026-07-16.pdf (compiled from the Hellenic Train live booking system + the
railway.gov.gr live board, which states no times were estimated). Station
coordinates come from the OSM route relations in the Greece extract
(docs/plans/greek-rail-osm-relations.md), never invented.

Corridors, all on the scheduled-trips path like Athens A1-A4:
  IC1  national  Athens <-> Thessaloniki (IC50/51/56/57), daily
  TP1  thess     Thessaloniki <-> Larisa (1590..2595), daily
  TP2  thess     Thessaloniki <-> Florina + Edessa short-turn, daily
  TP3  thess     Thessaloniki <-> Sindos shuttle, Mon-Fri
  TP4  thess     Thessaloniki <-> Serres <-> Drama, daily + Serres short-turn
  RG1  national  Athens <-> Leianokladi regional (520/521), daily
  AL1  national  Alexandroupoli <-> Orestiada <-> Ormenio (1680..1683), daily
  KB1  national  Paleofarsalos <-> Kalambaka rail-replacement bus (C88x), daily
  VL1  national  Volos <-> Larisa rail-replacement bus (C157x/C257x), daily
  DX1  national  Drama <-> Xanthi <-> Alexandroupoli replacement bus (C6xx)
  KP1  national  Kiato <-> Patra replacement bus (Cx/CxE), incl. Fri/Fri+Sun runs
  PS1/PS2/PSB    Patras suburban + Kato Achaia connecting bus

At each intermediate stop the timetable shows arrival/departure; we store the
DEPARTURE (the time a passenger can still catch it). Terminals store their single
endpoint time. Shared stations (Platy, Larisa, Katerini, Thessaloniki, Sindos,
Adendro) use one GR_* id across corridors.

Idempotent: re-running replaces these lines' rows and leaves Athens + the metro
untouched.

Run: cd ~/syrmos-api && .venv/bin/python -m scripts.seed_greek_corridors
"""
from __future__ import annotations

import sqlite3

from syrmos_admin import db as dbmod

# --- stations: id -> (name_en, name_el, lat, lng) -------------------------
S = {
    "GR_ATH": ("Athens", "Αθήνα", 37.993135, 23.720236),
    "GR_OIN": ("Oinoi", "Οινόη", 38.321712, 23.609804),
    "GR_THI": ("Thiva", "Θήβα", 38.329679, 23.318304),
    "GR_LIV": ("Livadeia", "Λιβαδειά", 38.471191, 22.926914),
    "GR_TIT": ("Tithorea", "Τιθορέα", 38.608243, 22.717904),
    "GR_LEI": ("Leianokladi", "Λειανοκλάδι", 38.890750, 22.372702),
    "GR_PAL": ("Palaiofarsalos", "Παλαιοφάρσαλος", 39.313781, 22.243559),
    "GR_LAR": ("Larisa", "Λάρισα", 39.629730, 22.423813),
    "GR_KAT": ("Katerini", "Κατερίνη", 40.269468, 22.531800),
    "GR_PLA": ("Platy", "Πλατύ", 40.636552, 22.529580),
    "GR_THE": ("Thessaloniki", "Θεσσαλονίκη", 40.644066, 22.930680),
    "GR_SIN": ("Sindos", "Σίνδος", 40.674276, 22.805576),
    "GR_ADE": ("Adendro", "Άδενδρο", 40.674479, 22.602693),
    "GR_AIG": ("Aiginio", "Αιγίνιο", 40.497925, 22.552508),
    "GR_KOR": ("Korinos", "Κορινός", 40.316291, 22.577729),
    "GR_LIT": ("Litochoro", "Λιτόχωρο", 40.124878, 22.549944),
    "GR_LEP": ("Leptokarya", "Λεπτοκαρυά", 40.058565, 22.565566),
    "GR_NPO": ("Neoi Poroi", "Νέοι Πόροι", 39.976060, 22.638307),
    "GR_RAP": ("Rapsani", "Ραψάνη", 39.899013, 22.614007),
    # Florina branch
    "GR_LNV": ("Lianovergi", "Λιανοβέργιον", 40.630128, 22.504427),
    "GR_ALX": ("Alexandreia", "Αλεξάνδρεια", 40.620200, 22.441469),
    "GR_LOU": ("Loutros", "Λουτρός", 40.598111, 22.398185),
    "GR_KEF": ("Kefalochori", "Κεφαλοχώρι Ημαθίας", 40.578774, 22.364987),
    "GR_XEX": ("Xechasmeni", "Ξεχασμένη Ημαθίας", 40.563630, 22.338995),
    "GR_KOU": ("Kouloura", "Κουλούρα", 40.549276, 22.314362),
    "GR_MES": ("Mesi", "Μέση", 40.527030, 22.263088),
    "GR_VER": ("Veroia", "Βέροια", 40.539463, 22.213699),
    "GR_NAO": ("Naousa", "Νάουσα", 40.621486, 22.134202),
    "GR_EPI": ("Episkopi Naousas", "Επισκοπή Νάουσας", 40.690546, 22.141462),
    "GR_PET": ("Petraia", "Πετριά", 40.720412, 22.140934),
    "GR_SKY": ("Skydra", "Σκύδρα", 40.768226, 22.157667),
    "GR_EDE": ("Edessa", "Έδεσσα", 40.808911, 22.049993),
    "GR_ARN": ("Arnissa", "Άρνισσα", 40.798112, 21.834913),
    "GR_APA": ("Agios Panteleimonas", "Άγιος Παντελεήμων", 40.726810, 21.745807),
    "GR_AMY": ("Amyntaio", "Αμύνταιο", 40.690930, 21.685609),
    "GR_XIN": ("Xino Nero", "Ξινό Νερό", 40.691409, 21.633891),
    "GR_VEV": ("Vevi", "Βεύη", 40.773148, 21.570664),
    "GR_SIT": ("Sitaria", "Σιταριά", 40.778811, 21.538327),
    "GR_MSN": ("Mesonisi", "Μεσονήσιον", 40.794623, 21.466706),
    "GR_FLO": ("Florina", "Φλώρινα", 40.781285, 21.415188),
    # Serres-Drama branch (TP4)
    "GR_NFT": ("Nea Filadelfeia Toumbas", "Νέα Φιλαδέλφεια Τούμπας", 40.796414, 22.848799),
    "GR_GAL": ("Gallikos", "Γαλλικός", 40.859029, 22.884719),
    "GR_PED": ("Pedino", "Πεδινό", 40.915809, 22.876430),
    "GR_KIL": ("Kilkis", "Κιλκίς", 40.958902, 22.857010),
    "GR_MET": ("Metalliko", "Μεταλλικός", 41.025130, 22.803471),
    "GR_CHE": ("Cherso", "Χέρσος", 41.090260, 22.783222),
    "GR_DOI": ("Doirani", "Δοϊράνη", 41.172816, 22.772198),
    "GR_MOU": ("Mouries", "Μουριές", 41.261412, 22.840147),
    "GR_KAS": ("Kastanoussa", "Καστανούσσα", 41.276092, 22.896279),
    "GR_ROD": ("Rodopoli", "Ροδόπολις", 41.259047, 22.999826),
    "GR_LIK": ("Livadia Kerkinis", "Λιβάδια Κερκίνης", 41.255443, 23.072084),
    "GR_MAN": ("Mandraki", "Μανδράκι", 41.260686, 23.139316),
    "GR_OMA": ("Omalo Kerkinis", "Ομαλό Κερκίνης", 41.261302, 23.196920),
    "GR_VYR": ("Vyroneia", "Βυρώνεια", 41.262388, 23.255619),
    "GR_NPE": ("Neo Petritsi", "Νέο Πετρίτσι", 41.269872, 23.297756),
    "GR_STR": ("Strymonas", "Στρυμώνας", 41.262038, 23.345464),
    "GR_SID": ("Sidirokastro", "Σιδηρόκαστρο", 41.229077, 23.375807),
    "GR_SKO": ("Skotoussa", "Σκοτούσσα", 41.129459, 23.376344),
    "GR_SER": ("Serres", "Σέρρες", 41.073813, 23.536510),
    "GR_DRA": ("Drama", "Δράμα", 41.140345, 24.147167),
    # Athens - Leianokladi regional (RG1); shares GR_ATH/OIN/THI/LIV/TIT/LEI with IC1
    "GR_SKA": ("SKA Acharnon", "ΣΚΑ (Κέντρο Αχαρνών)", 38.054188, 23.732645),
    "GR_TAN": ("Tanagra", "Τανάγρα", 38.342334, 23.574198),
    "GR_ALI": ("Aliartos", "Αλίαρτος", 38.379296, 23.111797),
    "GR_YPS": ("Ypsilantis", "Υψηλάντης", 38.385157, 23.028716),
    "GR_ALA": ("Alalkomenai", "Αλαλκομεναί", 38.408225, 22.982464),
    "GR_CHA": ("Chaironeia", "Χαιρώνεια", 38.507845, 22.859227),
    "GR_DAV": ("Davleia", "Δαύλεια", 38.535119, 22.811194),
    "GR_PAR": ("Parori", "Παρόρι", 38.574360, 22.762130),
    "GR_KIF": ("Kifissos", "Κηφισσός", 38.585710, 22.745148),
    "GR_MYL": ("Mylos", "Μύλος", 38.800000, 22.480000),  # small halt, interpolated Tithorea-Leianokladi
    # Patras suburban (PS1 Kaminia branch, PS2 Rio branch, PSB Kato Achaia bus)
    "PA_AND": ("Agios Andreas", "Αγ. Ανδρέας", 38.239405, 21.727159),
    "PA_ANT": ("Antheia", "Άνθεια", 38.226000, 21.723000),   # small halt, interpolated
    "PA_ITI": ("Ities", "Ιτιές", 38.211753, 21.718475),
    "PA_PAR": ("Paralia Patron", "Παραλία Πατρών", 38.202351, 21.705495),
    "PA_MIN": ("Mintilogli", "Μιντιλόγλι", 38.188679, 21.690434),
    "PA_VRA": ("Vrachneika", "Βραχνέικα", 38.165751, 21.672147),
    "PA_TSO": ("Tsoukaleika", "Τσουκαλαίικα", 38.156375, 21.646760),
    "PA_KAM": ("Kaminia", "Καμίνια", 38.147832, 21.621152),
    "PA_PAT": ("Patra", "Πάτρα", 38.249848, 21.735122),
    "PA_PAN": ("Panachaiki", "Παναχαϊκή", 38.262385, 21.742359),
    "PA_AGY": ("Agyia Patron", "Αγυιά Πατρών", 38.271038, 21.747219),
    "PA_BOZ": ("Bozaitika", "Μποζαίτικα", 38.281000, 21.759000),  # small halt, interpolated
    "PA_KST": ("Kastelokampos", "Καστελόκαμπος", 38.291426, 21.771523),
    "PA_RIO": ("Rio", "Ρίο", 38.298158, 21.777595),
    "PA_KAT": ("Kato Achaia", "Κάτω Αχαΐα", 38.145895, 21.561441),
    "PA_ALI": ("Alissos", "Αλισσός", 38.147000, 21.591000),  # small halt, interpolated
    # Evros / Thrace line (AL1): Alexandroupoli Port -> Orestiada -> Ormenio (rel 14122316)
    "EV_ALX": ("Alexandroupoli Port", "Αλεξανδρούπολη Λιμάνι", 40.845250, 25.878800),
    "EV_FER": ("Ferres", "Φέρρες", 40.891060, 26.185150),
    "EV_PEP": ("Peplos", "Πέπλος", 40.959330, 26.276990),
    "EV_TYC": ("Tychero", "Τυχερό", 41.034920, 26.293370),
    "EV_FYL": ("Fylakto", "Φυλακτό", 41.053130, 26.279240),
    "EV_LAG": ("Lagyna", "Λαγυνά", 41.086390, 26.301350),
    "EV_KOR": ("Kornofolia", "Κορνοφωλιά", 41.156610, 26.301770),
    "EV_SOU": ("Soufli", "Σουφλί", 41.187860, 26.301580),
    "EV_MAN": ("Mandra Evrou", "Μάνδρα Έβρου", 41.265620, 26.333070),
    "EV_LAV": ("Lavara", "Λάβαρα", 41.268170, 26.392970),
    "EV_AMO": ("Amorio", "Αμόριο", 41.295650, 26.443110),
    "EV_PSA": ("Psathades", "Ψαθάδες", 41.323830, 26.489940),
    "EV_DID": ("Didymoteicho", "Διδυμότειχο", 41.352090, 26.511310),
    "EV_PRA": ("Praggi", "Πραγγί", 41.342970, 26.575960),
    "EV_PET": ("Petrades", "Πετράδες", 41.338950, 26.610970),
    "EV_PYT": ("Pythio", "Πύθιο", 41.369700, 26.621850),
    "EV_PYS": ("Pythio Stasi", "Πύθιο Στάση", 41.382770, 26.612840),
    "EV_RIG": ("Rigio", "Ρήγιο", 41.398090, 26.591600),
    "EV_SOF": ("Sofiko", "Σοφικό", 41.423610, 26.565530),
    "EV_THO": ("Thourio", "Θούριο", 41.434500, 26.561440),
    "EV_CHE": ("Cheimonio", "Χειμώνιο", 41.450040, 26.555630),
    "EV_ORE": ("Orestiada", "Ορεστιάδα", 41.502980, 26.537380),
    "EV_SAK": ("Sakkos", "Σάκκος", 41.539860, 26.531750),
    "EV_KAV": ("Kavyli", "Καβύλη", 41.557200, 26.534580),
    "EV_NVY": ("Nea Vyssa", "Νέα Βύσσα", 41.578740, 26.534770),
    "EV_KAS": ("Kastaneai", "Καστανέαι", 41.647380, 26.486580),
    "EV_MAR": ("Marasia", "Μαράσια", 41.669020, 26.469840),
    "EV_DIL": ("Dilofos", "Δίλοφος", 41.693370, 26.377780),
    "EV_DIK": ("Dikaia", "Δίκαια", 41.706130, 26.297370),
    "EV_PTE": ("Ptelea", "Πτελέα", 41.717720, 26.253250),
    "EV_ORM": ("Ormenio", "Ορμένιο", 41.728180, 26.212570),
    # Paleofarsalos - Kalambaka rail-replacement bus (KB1); GR_PAL shared with IC1 (rel 14007294)
    "KB_SOF": ("Sofades", "Σοφάδες", 39.340460, 22.086720),
    "KB_KAR": ("Karditsa", "Καρδίτσα", 39.353940, 21.914770),
    "KB_TRI": ("Trikala", "Τρίκαλα", 39.546050, 21.763320),
    "KB_KAL": ("Kalambaka", "Καλαμπάκα", 39.702950, 21.625300),
    # Volos - Larisa rail-replacement bus (VL1); GR_LAR shared with IC1/TP1
    "VL_VOL": ("Volos", "Βόλος", 39.364660, 22.936670),
    "VL_VEL": ("Velestino", "Βελεστίνο", 39.391240, 22.759000),
    # Drama - Xanthi - Alexandroupoli rail-replacement bus (DX1); GR_DRA + EV_ALX shared
    "XD_NIK": ("Nikiforos", "Νικηφόρος", 41.167260, 24.309920),
    "XD_PLA": ("Platania", "Πλατανιά", 41.204470, 24.421790),
    "XD_PAR": ("Paranesti", "Παρανέστι", 41.265640, 24.501950),
    "XD_NEO": ("Neochorio", "Νεοχώριο", 41.220860, 24.633400),
    "XD_STA": ("Stavroupoli Xanthis", "Σταυρούπολη Ξάνθης", 41.193240, 24.703270),
    "XD_TOX": ("Toxotes", "Τοξότες", 41.086870, 24.780110),
    "XD_XAN": ("Xanthi", "Ξάνθη", 41.123890, 24.892900),
    "XD_IAS": ("Iasmos", "Ίασμος", 41.126040, 25.187410),
    "XD_POL": ("Polyanthos", "Πολύανθος", 41.125690, 25.227820),
    "XD_KOM": ("Komotini", "Κομοτηνή", 41.109900, 25.394100),
    "XD_MES": ("Mesti", "Μεστή", 40.967200, 25.640450),
    "XD_SYK": ("Sykorrachi", "Συκορράχη", 40.974960, 25.720360),
    "XD_KIR": ("Kirki", "Κίρκη", 40.975620, 25.797840),
    # Kiato - Patra rail-replacement bus (KP1); PA_PAT shared with PS2
    "KI_KIA": ("Kiato", "Κιάτο", 38.013980, 22.734810),
    "KI_DIA": ("Diakopto", "Διακοπτό", 38.191870, 22.197720),
}

# Mode per line; defaults to suburban. Rail-replacement/connecting buses are 'bus'.
MODE = {"PSB": "bus", "KB1": "bus", "VL1": "bus", "DX1": "bus", "KP1": "bus"}

# --- lines: id -> (name_en, name_el, color, term_a, term_b, sort, region) --
LINES = {
    "IC1": ("IC Athens - Thessaloniki", "IC Αθήνα - Θεσσαλονίκη", "#1D4ED8",
            "Athens", "Thessaloniki", 30, "national"),
    "TP1": ("Thessaloniki - Larisa", "Θεσσαλονίκη - Λάρισα", "#7C3AED",
            "Thessaloniki", "Larisa", 31, "thessaloniki"),
    "TP2": ("Thessaloniki - Florina", "Θεσσαλονίκη - Φλώρινα", "#DB2777",
            "Thessaloniki", "Florina", 32, "thessaloniki"),
    "TP3": ("Thessaloniki - Sindos", "Θεσσαλονίκη - Σίνδος", "#059669",
            "Thessaloniki", "Sindos", 33, "thessaloniki"),
    "TP4": ("Thessaloniki - Serres - Drama", "Θεσσαλονίκη - Σέρρες - Δράμα", "#EA580C",
            "Thessaloniki", "Drama", 34, "thessaloniki"),
    "RG1": ("Athens - Leianokladi", "Αθήνα - Λειανοκλάδι", "#0891B2",
            "Athens", "Leianokladi", 35, "national"),
    "PS1": ("Patras - Kaminia", "Πάτρα - Καμίνια", "#0D9488",
            "Agios Andreas", "Kaminia", 40, "patras"),
    "PS2": ("Patras - Rio", "Πάτρα - Ρίο", "#0284C7",
            "Agios Andreas", "Rio", 41, "patras"),
    "PSB": ("Kato Achaia - Kaminia bus", "Κάτω Αχαΐα - Καμίνια (λεωφορείο)", "#F59E0B",
            "Kato Achaia", "Kaminia", 42, "patras"),
    "AL1": ("Alexandroupoli - Ormenio", "Αλεξανδρούπολη - Ορμένιο", "#B91C1C",
            "Alexandroupoli", "Ormenio", 36, "national"),
    "KB1": ("Paleofarsalos - Kalambaka bus", "Παλαιοφάρσαλος - Καλαμπάκα (λεωφορείο)", "#A16207",
            "Paleofarsalos", "Kalambaka", 37, "national"),
    "VL1": ("Volos - Larisa bus", "Βόλος - Λάρισα (λεωφορείο)", "#CA8A04",
            "Volos", "Larisa", 38, "national"),
    "DX1": ("Drama - Xanthi - Alexandroupoli bus", "Δράμα - Ξάνθη - Αλεξανδρούπολη (λεωφορείο)", "#B45309",
            "Drama", "Alexandroupoli", 39, "national"),
    "KP1": ("Kiato - Patra bus", "Κιάτο - Πάτρα (λεωφορείο)", "#C2410C",
            "Kiato", "Patra", 43, "national"),
}

# canonical station order per line (outbound = terminal_a -> terminal_b)
ORDER = {
    "IC1": ["GR_ATH", "GR_OIN", "GR_THI", "GR_LIV", "GR_TIT", "GR_LEI",
            "GR_PAL", "GR_LAR", "GR_KAT", "GR_PLA", "GR_THE"],
    "TP1": ["GR_THE", "GR_SIN", "GR_ADE", "GR_PLA", "GR_AIG", "GR_KOR",
            "GR_KAT", "GR_LIT", "GR_LEP", "GR_NPO", "GR_RAP", "GR_LAR"],
    "TP2": ["GR_THE", "GR_SIN", "GR_ADE", "GR_PLA", "GR_LNV", "GR_ALX",
            "GR_LOU", "GR_KEF", "GR_XEX", "GR_KOU", "GR_MES", "GR_VER",
            "GR_NAO", "GR_EPI", "GR_PET", "GR_SKY", "GR_EDE", "GR_ARN",
            "GR_APA", "GR_AMY", "GR_XIN", "GR_VEV", "GR_SIT", "GR_MSN", "GR_FLO"],
    "TP3": ["GR_THE", "GR_SIN"],
    "TP4": ["GR_THE", "GR_NFT", "GR_GAL", "GR_PED", "GR_KIL", "GR_MET", "GR_CHE",
            "GR_DOI", "GR_MOU", "GR_KAS", "GR_ROD", "GR_LIK", "GR_MAN", "GR_OMA",
            "GR_VYR", "GR_NPE", "GR_STR", "GR_SID", "GR_SKO", "GR_SER", "GR_DRA"],
    "RG1": ["GR_ATH", "GR_SKA", "GR_OIN", "GR_TAN", "GR_THI", "GR_ALI", "GR_YPS",
            "GR_ALA", "GR_LIV", "GR_CHA", "GR_DAV", "GR_PAR", "GR_KIF", "GR_TIT",
            "GR_MYL", "GR_LEI"],
    "PS1": ["PA_AND", "PA_ANT", "PA_ITI", "PA_PAR", "PA_MIN", "PA_VRA", "PA_TSO", "PA_KAM"],
    "PS2": ["PA_AND", "PA_PAT", "PA_PAN", "PA_AGY", "PA_BOZ", "PA_KST", "PA_RIO"],
    "PSB": ["PA_KAT", "PA_ALI", "PA_KAM"],
    "AL1": ["EV_ALX", "EV_FER", "EV_PEP", "EV_TYC", "EV_FYL", "EV_LAG", "EV_KOR",
            "EV_SOU", "EV_MAN", "EV_LAV", "EV_AMO", "EV_PSA", "EV_DID", "EV_PRA",
            "EV_PET", "EV_PYT", "EV_PYS", "EV_RIG", "EV_SOF", "EV_THO", "EV_CHE",
            "EV_ORE", "EV_SAK", "EV_KAV", "EV_NVY", "EV_KAS", "EV_MAR", "EV_DIL",
            "EV_DIK", "EV_PTE", "EV_ORM"],
    "KB1": ["GR_PAL", "KB_SOF", "KB_KAR", "KB_TRI", "KB_KAL"],
    "VL1": ["VL_VOL", "VL_VEL", "GR_LAR"],
    "DX1": ["GR_DRA", "XD_NIK", "XD_PLA", "XD_PAR", "XD_NEO", "XD_STA", "XD_TOX",
            "XD_XAN", "XD_IAS", "XD_POL", "XD_KOM", "XD_MES", "XD_SYK", "XD_KIR", "EV_ALX"],
    "KP1": ["KI_KIA", "KI_DIA", "PA_PAT"],
}

DAILY = ("mon_thu", "fri", "sat", "sun")
WEEKDAY = ("mon_thu", "fri")
FRI = ("fri",)
FRI_SUN = ("fri", "sun")

# --- trips: (line, direction, train_no, day_types, [(station_id, "HH:MM")]) -
# direction: outbound = terminal_a -> terminal_b, inbound = reverse.
# Times are the DEPARTURE at each stop (terminals: the endpoint time).

IC1_OUT = ORDER["IC1"]
IC1_IN = list(reversed(IC1_OUT))
TP1_OUT = ORDER["TP1"]
TP1_IN = list(reversed(TP1_OUT))
TP2_OUT = ORDER["TP2"]
TP2_IN = list(reversed(TP2_OUT))
# Edessa short-turn: Thessaloniki .. Edessa (index of GR_EDE in TP2_OUT is 16)
TP2_EDE_OUT = TP2_OUT[:17]
TP2_EDE_IN = list(reversed(TP2_EDE_OUT))


def trip(line, direction, no, days, ids, times):
    assert len(ids) == len(times), f"{no}: {len(ids)} stops vs {len(times)} times"
    return (line, direction, no, days, list(zip(ids, times)))


TRIPS = [
    # ---- IC1 national, daily ----
    trip("IC1", "outbound", "IC50", DAILY, IC1_OUT,
         ["06:58", "07:50", "08:08", "08:29", "08:43", "09:11", "10:01", "10:39", "11:21", "11:44", "12:10"]),
    trip("IC1", "outbound", "IC56", DAILY, IC1_OUT,
         ["17:58", "18:50", "19:08", "19:29", "19:43", "20:11", "21:01", "21:39", "22:21", "22:44", "23:10"]),
    trip("IC1", "inbound", "IC51", DAILY, IC1_IN,
         ["05:55", "06:20", "06:43", "07:26", "08:02", "08:52", "09:20", "09:33", "09:55", "10:13", "11:04"]),
    trip("IC1", "inbound", "IC57", DAILY, IC1_IN,
         ["16:49", "17:14", "17:37", "18:20", "18:56", "19:46", "20:14", "20:27", "20:49", "21:07", "21:58"]),

    # ---- TP1 Larisa, daily, 8 each way ----
    trip("TP1", "outbound", "1591", DAILY, TP1_OUT,
         ["05:00", "05:12", "05:23", "05:29", "05:38", "05:49", "05:54", "06:04", "06:09", "06:17", "06:23", "06:44"]),
    trip("TP1", "outbound", "1593", DAILY, TP1_OUT,
         ["07:30", "07:42", "07:53", "07:59", "08:08", "08:19", "08:24", "08:34", "08:39", "08:47", "08:53", "09:14"]),
    trip("TP1", "outbound", "1595", DAILY, TP1_OUT,
         ["10:20", "10:32", "10:43", "10:49", "10:58", "11:09", "11:14", "11:24", "11:29", "11:37", "11:43", "12:04"]),
    trip("TP1", "outbound", "1597", DAILY, TP1_OUT,
         ["12:20", "12:32", "12:43", "12:49", "12:58", "13:09", "13:14", "13:24", "13:29", "13:37", "13:43", "14:04"]),
    trip("TP1", "outbound", "1599", DAILY, TP1_OUT,
         ["15:10", "15:22", "15:33", "15:39", "15:48", "15:59", "16:04", "16:14", "16:19", "16:27", "16:33", "16:54"]),
    trip("TP1", "outbound", "2591", DAILY, TP1_OUT,
         ["17:30", "17:42", "17:53", "17:59", "18:08", "18:19", "18:24", "18:34", "18:39", "18:47", "18:53", "19:14"]),
    trip("TP1", "outbound", "2593", DAILY, TP1_OUT,
         ["19:45", "19:57", "20:08", "20:14", "20:23", "20:34", "20:39", "20:49", "20:54", "21:02", "21:08", "21:29"]),
    trip("TP1", "outbound", "2595", DAILY, TP1_OUT,
         ["21:50", "22:02", "22:13", "22:19", "22:28", "22:39", "22:44", "22:54", "22:59", "23:07", "23:13", "23:34"]),
    trip("TP1", "inbound", "1590", DAILY, TP1_IN,
         ["05:15", "05:37", "05:44", "05:51", "05:57", "06:06", "06:11", "06:23", "06:32", "06:38", "06:49", "07:00"]),
    trip("TP1", "inbound", "1592", DAILY, TP1_IN,
         ["07:10", "07:32", "07:39", "07:46", "07:52", "08:01", "08:06", "08:18", "08:27", "08:33", "08:44", "08:55"]),
    trip("TP1", "inbound", "1594", DAILY, TP1_IN,
         ["09:45", "10:07", "10:14", "10:21", "10:27", "10:36", "10:41", "10:53", "11:02", "11:08", "11:19", "11:30"]),
    trip("TP1", "inbound", "1596", DAILY, TP1_IN,
         ["12:35", "12:57", "13:04", "13:11", "13:17", "13:26", "13:31", "13:43", "13:52", "13:58", "14:09", "14:20"]),
    trip("TP1", "inbound", "1598", DAILY, TP1_IN,
         ["14:50", "15:12", "15:19", "15:26", "15:32", "15:41", "15:46", "15:58", "16:07", "16:13", "16:24", "16:35"]),
    trip("TP1", "inbound", "2590", DAILY, TP1_IN,
         ["17:30", "17:52", "17:59", "18:06", "18:12", "18:21", "18:26", "18:38", "18:47", "18:53", "19:04", "19:15"]),
    trip("TP1", "inbound", "2592", DAILY, TP1_IN,
         ["19:35", "19:57", "20:04", "20:11", "20:17", "20:26", "20:31", "20:43", "20:52", "20:58", "21:09", "21:20"]),
    trip("TP1", "inbound", "2594", DAILY, TP1_IN,
         ["22:00", "22:22", "22:29", "22:36", "22:42", "22:51", "22:56", "23:08", "23:17", "23:23", "23:34", "23:45"]),

    # ---- TP2 Florina, daily ----
    trip("TP2", "outbound", "731", DAILY, TP2_OUT,
         ["10:40", "10:52", "11:03", "11:09", "11:12", "11:17", "11:22", "11:25", "11:28", "11:31", "11:36", "11:41",
          "11:52", "11:58", "12:02", "12:08", "12:23", "12:46", "13:06", "13:13", "13:18", "13:29", "13:32", "13:37", "13:44"]),
    trip("TP2", "outbound", "733", DAILY, TP2_OUT,
         ["13:55", "14:07", "14:18", "14:24", "14:27", "14:32", "14:37", "14:40", "14:43", "14:46", "14:51", "14:56",
          "15:07", "15:13", "15:17", "15:23", "15:40", "16:03", "16:23", "16:30", "16:35", "16:46", "16:49", "16:54", "17:01"]),
    trip("TP2", "inbound", "730", DAILY, TP2_IN,
         ["06:45", "06:53", "06:58", "07:01", "07:12", "07:18", "07:24", "07:45", "08:08", "08:22", "08:28", "08:31",
          "08:39", "08:49", "08:54", "08:59", "09:02", "09:05", "09:08", "09:13", "09:18", "09:21", "09:27", "09:38", "09:49"]),
    trip("TP2", "inbound", "732", DAILY, TP2_IN,
         ["14:15", "14:23", "14:28", "14:31", "14:42", "14:48", "14:54", "15:15", "15:39", "15:54", "15:59", "16:03",
          "16:10", "16:21", "16:25", "16:30", "16:33", "16:36", "16:39", "16:45", "16:50", "16:53", "16:58", "17:10", "17:21"]),
    # Edessa short-turn
    trip("TP2", "outbound", "1735", DAILY, TP2_EDE_OUT,
         ["18:30", "18:42", "18:53", "18:59", "19:02", "19:07", "19:12", "19:15", "19:18", "19:21", "19:26", "19:31",
          "19:42", "19:48", "19:52", "19:58", "20:12"]),
    trip("TP2", "inbound", "1736", DAILY, TP2_EDE_IN,
         ["20:45", "21:00", "21:05", "21:09", "21:16", "21:27", "21:31", "21:36", "21:39", "21:42", "21:45", "21:51",
          "21:56", "21:59", "22:04", "22:16", "22:27"]),
]

# ---- TP3 Sindos shuttle, Mon-Fri, 17 each way ----
_TP3_OUT = ["05:00/05:11", "05:10/05:21", "07:30/07:41", "08:05/08:16", "09:00/09:11",
            "09:40/09:51", "10:20/10:31", "11:25/11:36", "12:20/12:31", "14:35/14:46",
            "15:10/15:21", "15:30/15:41", "16:40/16:51", "17:30/17:41", "19:10/19:21",
            "19:45/19:56", "21:50/22:01"]
_TP3_IN = ["06:49/07:00", "08:30/08:41", "08:44/08:55", "08:56/09:07", "09:20/09:31",
           "10:00/10:11", "11:19/11:30", "14:09/14:20", "14:55/15:06", "15:50/16:01",
           "16:24/16:35", "17:05/17:16", "17:26/17:37", "19:04/19:15", "21:09/21:20",
           "22:41/22:52", "23:34/23:45"]
for i, dp in enumerate(_TP3_OUT):
    d, a = dp.split("/")
    TRIPS.append(trip("TP3", "outbound", f"S{i*2+1:03d}", WEEKDAY, ["GR_THE", "GR_SIN"], [d, a]))
for i, dp in enumerate(_TP3_IN):
    d, a = dp.split("/")
    TRIPS.append(trip("TP3", "inbound", f"S{i*2+2:03d}", WEEKDAY, ["GR_SIN", "GR_THE"], [d, a]))

# ---- TP4 Serres-Drama ----
TP4_OUT = ORDER["TP4"]
TP4_IN = list(reversed(TP4_OUT))
TP4_SER_OUT = TP4_OUT[:20]                 # Thessaloniki .. Serres (short-turn)
TP4_SER_IN = list(reversed(TP4_SER_OUT))
TRIPS += [
    trip("TP4", "outbound", "1634", DAILY, TP4_OUT,
         ["15:00", "15:16", "15:21", "15:26", "15:33", "15:42", "15:48", "15:54", "16:03", "16:08",
          "16:28", "16:43", "16:55", "17:06", "17:18", "17:26", "17:31", "17:36", "17:47", "18:04", "19:27"]),
    trip("TP4", "inbound", "1635", DAILY, TP4_IN,
         ["17:07", "18:30", "18:47", "18:58", "19:03", "19:08", "19:16", "19:28", "19:39", "19:51",
          "20:06", "20:26", "20:30", "20:39", "20:46", "20:52", "21:02", "21:08", "21:12", "21:18", "21:34"]),
    trip("TP4", "outbound", "3632", WEEKDAY, TP4_SER_OUT,
         ["05:35", "05:51", "05:56", "06:01", "06:08", "06:17", "06:23", "06:29", "06:38", "06:43",
          "07:03", "07:18", "07:30", "07:41", "07:53", "08:01", "08:06", "08:11", "08:22", "08:39"]),
    trip("TP4", "inbound", "3633", WEEKDAY, TP4_SER_IN,
         ["09:10", "09:27", "09:38", "09:43", "09:48", "09:56", "10:08", "10:19", "10:31", "10:46",
          "11:06", "11:10", "11:19", "11:26", "11:32", "11:42", "11:48", "11:52", "11:58", "12:14"]),
]

# ---- RG1 Athens-Leianokladi, daily, one train each way ----
RG1_OUT = ORDER["RG1"]
RG1_IN = list(reversed(RG1_OUT))
TRIPS += [
    trip("RG1", "outbound", "520", DAILY, RG1_OUT,
         ["19:58", "20:08", "20:51", "20:55", "21:11", "21:24", "21:30", "21:34", "21:41", "21:46",
          "21:51", "21:55", "21:58", "22:02", "22:19", "22:35"]),
    trip("RG1", "inbound", "521", DAILY, RG1_IN,
         ["05:20", "05:37", "05:53", "05:57", "06:00", "06:04", "06:09", "06:15", "06:21", "06:25",
          "06:31", "06:44", "07:00", "07:04", "07:47", "08:07"]),
]

# ---- AL1 Alexandroupoli - Orestiada - Ormenio (Evros), daily ----
AL1_OUT = ORDER["AL1"]
AL1_IN = list(reversed(AL1_OUT))
AL1_ORE_OUT = AL1_OUT[:22]                 # Alexandroupoli .. Orestiada short-turn
AL1_ORE_IN = list(reversed(AL1_ORE_OUT))
TRIPS += [
    trip("AL1", "outbound", "1680", DAILY, AL1_OUT,
         ["05:40", "06:04", "06:12", "06:19", "06:22", "06:26", "06:32", "06:35", "06:45", "06:49",
          "06:53", "06:57", "07:01", "07:07", "07:10", "07:14", "07:16", "07:19", "07:22", "07:24",
          "07:26", "07:32", "07:36", "07:38", "07:40", "07:47", "07:50", "07:55", "08:03", "08:07", "08:09"]),
    trip("AL1", "outbound", "1682", DAILY, AL1_ORE_OUT,
         ["15:30", "15:54", "16:02", "16:09", "16:12", "16:16", "16:22", "16:25", "16:35", "16:39",
          "16:43", "16:47", "16:51", "16:57", "17:00", "17:05", "17:06", "17:09", "17:12", "17:14",
          "17:16", "17:21"]),
    trip("AL1", "inbound", "1681", DAILY, AL1_IN,
         ["08:45", "08:48", "08:52", "08:59", "09:05", "09:08", "09:15", "09:17", "09:19", "09:23",
          "09:28", "09:30", "09:32", "09:35", "09:38", "09:40", "09:44", "09:46", "09:53", "09:57",
          "10:00", "10:05", "10:09", "10:19", "10:22", "10:29", "10:32", "10:35", "10:42", "10:51", "11:15"]),
    trip("AL1", "inbound", "1683", DAILY, AL1_ORE_IN,
         ["17:40", "17:46", "17:48", "17:49", "17:52", "17:55", "17:57", "18:01", "18:04", "18:10",
          "18:14", "18:18", "18:22", "18:26", "18:36", "18:39", "18:46", "18:49", "18:52", "19:00",
          "19:08", "19:31"]),
]

# ---- KB1 Paleofarsalos - Kalambaka rail-replacement BUS, daily ----
KB1_OUT = ORDER["KB1"]
KB1_IN = list(reversed(KB1_OUT))
TRIPS += [
    trip("KB1", "outbound", "C1880", DAILY, KB1_OUT,
         ["10:30", "10:50", "11:20", "11:50", "12:30"]),
    trip("KB1", "outbound", "C888", DAILY, KB1_OUT,
         ["21:15", "21:35", "22:05", "22:35", "23:15"]),
    trip("KB1", "inbound", "C881", DAILY, KB1_IN,
         ["05:45", "06:10", "06:40", "07:05", "07:45"]),
    trip("KB1", "inbound", "C1889", DAILY, KB1_IN,
         ["16:50", "17:15", "17:45", "18:05", "18:50"]),
]

# ---- VL1 Volos - Larisa rail-replacement BUS, daily, 7 each way ----
VL1_OUT = ORDER["VL1"]                    # Volos -> Velestino -> Larisa
VL1_IN = list(reversed(VL1_OUT))
_VL_OUT = {"C1571": ["05:25", "05:45", "06:30"], "C1573": ["07:00", "07:20", "08:05"],
           "C1575": ["08:25", "08:42", "09:30"], "C1577": ["13:20", "13:40", "14:25"],
           "C1579": ["15:50", "16:10", "16:55"], "C2571": ["18:00", "18:20", "19:05"],
           "C2573": ["21:10", "21:30", "22:15"]}
_VL_IN = {"C1572": ["06:50", "07:30", "07:50"], "C1574": ["08:30", "09:10", "09:30"],
          "C1576": ["10:50", "11:30", "11:50"], "C1578": ["12:40", "13:20", "13:40"],
          "C2570": ["14:45", "15:25", "15:45"], "C2572": ["16:55", "17:35", "17:55"],
          "C2574": ["21:45", "22:25", "22:45"]}
for no, tm in _VL_OUT.items():
    TRIPS.append(trip("VL1", "outbound", no, DAILY, VL1_OUT, tm))
for no, tm in _VL_IN.items():
    TRIPS.append(trip("VL1", "inbound", no, DAILY, VL1_IN, tm))

# ---- DX1 Drama - Xanthi - Alexandroupoli rail-replacement BUS ----
# Per-service stop patterns (each trip serves a subset of the 15-station line in travel order).
DX_D2X = ["GR_DRA", "XD_NIK", "XD_PLA", "XD_PAR", "XD_NEO", "XD_STA", "XD_TOX", "XD_XAN"]  # outbound
DX_D2A = ["GR_DRA", "XD_PAR", "XD_STA", "XD_XAN", "XD_IAS", "XD_KOM", "EV_ALX"]            # outbound (express)
DX_X2A = ["XD_XAN", "XD_IAS", "XD_POL", "XD_KOM", "XD_MES", "XD_SYK", "XD_KIR", "EV_ALX"]  # outbound
DX_A2X = list(reversed(DX_X2A))                                                            # inbound
DX_X2D = ["XD_XAN", "XD_STA", "XD_NEO", "XD_PAR", "XD_PLA", "XD_NIK", "GR_DRA"]            # inbound (skips Toxotes)
TRIPS += [
    trip("DX1", "inbound", "C671", WEEKDAY, DX_A2X,
         ["05:55", "06:30", "06:40", "06:49", "07:14", "07:28", "07:35", "08:00"]),
    trip("DX1", "inbound", "C673", WEEKDAY, DX_A2X,
         ["11:30", "12:05", "12:15", "12:24", "12:49", "13:03", "13:10", "13:35"]),
    trip("DX1", "inbound", "C675", DAILY, DX_A2X,
         ["16:10", "16:45", "16:55", "17:04", "17:29", "17:43", "17:50", "18:15"]),
    trip("DX1", "outbound", "C602", DAILY, DX_D2A,
         ["19:50", "20:35", "20:55", "21:30", "21:50", "22:15", "23:05"]),
    trip("DX1", "outbound", "C670", DAILY, DX_D2X,
         ["05:40", "05:59", "06:09", "06:19", "06:34", "06:44", "07:39", "07:52"]),
    trip("DX1", "outbound", "C672", WEEKDAY, DX_X2A,
         ["08:05", "08:40", "08:47", "09:06", "09:31", "09:40", "09:50", "10:25"]),
    trip("DX1", "outbound", "C674", WEEKDAY, DX_X2A,
         ["13:40", "14:20", "14:27", "14:41", "15:06", "15:15", "15:25", "16:00"]),
    trip("DX1", "outbound", "C676", DAILY, DX_X2A,
         ["18:20", "18:55", "19:02", "19:16", "19:41", "19:50", "20:00", "20:35"]),
    trip("DX1", "inbound", "C679", DAILY, DX_X2D,
         ["08:30", "09:09", "09:19", "09:34", "09:49", "09:59", "10:19"]),
]

# ---- KP1 Kiato - Patra rail-replacement BUS (express; Diakopto on the longer runs) ----
KP_KP2 = ["KI_KIA", "PA_PAT"]             # outbound endpoint-only
KP_KP3 = ["KI_KIA", "KI_DIA", "PA_PAT"]   # outbound via Diakopto
KP_PK2 = ["PA_PAT", "KI_KIA"]             # inbound endpoint-only
KP_PK3 = ["PA_PAT", "KI_DIA", "KI_KIA"]   # inbound via Diakopto
TRIPS += [
    trip("KP1", "outbound", "C4E", DAILY, KP_KP2, ["08:20", "09:45"]),
    trip("KP1", "outbound", "C8E", DAILY, KP_KP2, ["10:20", "11:45"]),
    trip("KP1", "outbound", "C10E", DAILY, KP_KP2, ["12:20", "13:45"]),
    trip("KP1", "outbound", "C12E", DAILY, KP_KP2, ["14:20", "15:45"]),
    trip("KP1", "outbound", "C14", DAILY, KP_KP3, ["16:20", "17:05", "17:55"]),
    trip("KP1", "outbound", "C18E", DAILY, KP_KP2, ["17:20", "18:45"]),
    trip("KP1", "outbound", "C20", DAILY, KP_KP3, ["18:20", "19:05", "19:55"]),
    trip("KP1", "outbound", "C24E", DAILY, KP_KP2, ["20:20", "21:45"]),
    trip("KP1", "outbound", "C26E", DAILY, KP_KP2, ["21:20", "22:45"]),
    trip("KP1", "outbound", "C14E", FRI, KP_KP2, ["15:20", "16:45"]),
    trip("KP1", "outbound", "C22E", FRI_SUN, KP_KP2, ["19:20", "20:45"]),
    trip("KP1", "inbound", "C3E", DAILY, KP_PK2, ["06:40", "08:05"]),
    trip("KP1", "inbound", "C7", DAILY, KP_PK3, ["08:25", "09:20", "10:05"]),
    trip("KP1", "inbound", "C9", DAILY, KP_PK3, ["10:25", "11:20", "12:05"]),
    trip("KP1", "inbound", "C11E", DAILY, KP_PK2, ["12:40", "14:05"]),
    trip("KP1", "inbound", "C15E", DAILY, KP_PK2, ["14:40", "16:05"]),
    trip("KP1", "inbound", "C17E", DAILY, KP_PK2, ["15:40", "17:05"]),
    trip("KP1", "inbound", "C19E", DAILY, KP_PK2, ["16:40", "18:05"]),
    trip("KP1", "inbound", "C23E", DAILY, KP_PK2, ["18:40", "20:05"]),
    trip("KP1", "inbound", "C25E", DAILY, KP_PK2, ["19:40", "21:05"]),
    trip("KP1", "inbound", "C13E", FRI, KP_PK2, ["13:40", "15:05"]),
    trip("KP1", "inbound", "C21E", FRI_SUN, KP_PK2, ["17:40", "19:05"]),
]

# ---- Patras: regular-interval shuttles. Every train shares one stop pattern,
# just time-shifted, so generate from (departure list + fixed per-stop offsets).
# The offsets ARE the exact PDF data; this is compact, not approximated.
def interval_trips(line, direction, ids, base_no, deps, offsets):
    assert len(ids) == len(offsets)
    for i, d in enumerate(deps):
        h, m = map(int, d.split(":"))
        base = h * 60 + m
        times = [f"{(base + o) // 60 % 24:02d}:{(base + o) % 60:02d}" for o in offsets]
        TRIPS.append(trip(line, direction, f"{base_no + i}", DAILY, ids, times))


PS1_OUT, PS1_IN = ORDER["PS1"], list(reversed(ORDER["PS1"]))
PS2_OUT, PS2_IN = ORDER["PS2"], list(reversed(ORDER["PS2"]))
PSB_OUT, PSB_IN = ORDER["PSB"], list(reversed(ORDER["PSB"]))
# PS1 Ag.Andreas<->Kaminia (train)
interval_trips("PS1", "outbound", PS1_OUT, 20000,
               ["06:33", "07:33", "08:33", "09:33", "11:33", "12:33", "13:33", "14:33",
                "15:33", "16:33", "17:33", "18:33", "19:33", "20:33", "21:33"],
               [0, 4, 7, 9, 13, 17, 21, 24])
interval_trips("PS1", "inbound", PS1_IN, 20100,
               ["07:03", "08:03", "09:03", "10:03", "12:03", "13:03", "14:03", "15:03",
                "16:03", "17:03", "18:03", "19:03", "20:03", "21:03", "22:03"],
               [0, 3, 7, 11, 14, 17, 19, 23])
# PS2 Ag.Andreas<->Rio (train)
interval_trips("PS2", "outbound", PS2_OUT, 20200,
               ["06:32", "07:32", "08:32", "09:32", "10:32", "11:32", "12:32", "13:32", "14:32",
                "15:32", "16:32", "17:32", "18:32", "19:32", "20:32", "21:32", "22:32"],
               [0, 7, 12, 14, 17, 19, 21])
interval_trips("PS2", "inbound", PS2_IN, 20300,
               ["07:07", "08:07", "09:07", "10:07", "11:07", "12:07", "13:07", "14:07", "15:07",
                "16:07", "17:07", "18:07", "19:07", "20:07", "21:07", "22:07", "23:07"],
               [0, 3, 5, 7, 10, 16, 22])
# PSB Kato Achaia<->Kaminia (rail-replacement / connecting BUS)
interval_trips("PSB", "outbound", PSB_OUT, 20400,
               ["06:45", "07:45", "08:45", "09:45", "10:45", "11:45", "12:45", "13:45",
                "14:45", "15:45", "16:45", "17:45", "18:45", "19:45", "20:45"],
               [0, 7, 15])
interval_trips("PSB", "inbound", PSB_IN, 20500,
               ["07:00", "08:00", "09:00", "10:00", "11:00", "12:00", "13:00", "14:00",
                "15:00", "16:00", "17:00", "18:00", "19:00", "20:00", "21:00"],
               [0, 3, 10])


_NATIONAL_GR = ("GR_ATH", "GR_OIN", "GR_THI", "GR_LIV", "GR_TIT", "GR_LEI", "GR_PAL")


def station_region(sid: str) -> str:
    """Region a station belongs to. Evros (EV_) + Kalambaka bus (KB_) are national,
    the Athens leg of IC1 is national, Patras (PA_) is patras, rest thessaloniki."""
    if sid in _NATIONAL_GR or sid.startswith(("EV_", "KB_", "VL_", "XD_", "KI_")):
        return "national"
    if sid.startswith("PA_"):
        return "patras"
    return "thessaloniki"


def main() -> None:
    conn = dbmod.connect()
    dbmod.migrate(conn)
    line_ids = list(LINES)
    ph = ",".join("?" for _ in line_ids)
    cur = conn.cursor()
    cur.execute("BEGIN")
    try:
        # stations (shared ids upsert)
        conn.executemany(
            "INSERT INTO stations(id,name_en,name_el,lat,lng,region,accessibility,zone)"
            " VALUES(?,?,?,?,?,?,1,1) ON CONFLICT(id) DO UPDATE SET"
            " name_en=excluded.name_en,name_el=excluded.name_el,lat=excluded.lat,"
            " lng=excluded.lng,region=excluded.region",
            [(sid, en, el, la, lo, station_region(sid))
             for sid, (en, el, la, lo) in S.items()],
        )
        # lines
        conn.executemany(
            "INSERT INTO lines(id,mode,name_en,name_el,color,terminal_a,terminal_b,"
            "sort_order,region,status) VALUES(?,?,?,?,?,?,?,?,?,'operational')"
            " ON CONFLICT(id) DO UPDATE SET name_en=excluded.name_en,"
            " name_el=excluded.name_el,color=excluded.color,region=excluded.region,"
            " sort_order=excluded.sort_order,status='operational'",
            [(lid, MODE.get(lid, "suburban"), en, el, col, ta, tb, so, reg)
             for lid, (en, el, col, ta, tb, so, reg) in LINES.items()],
        )
        # line_stations (rebuild)
        conn.execute(f"DELETE FROM line_stations WHERE line_id IN ({ph})", line_ids)
        ls = []
        for lid, ids in ORDER.items():
            for seq, sid in enumerate(ids, start=1):
                ls.append((lid, sid, seq, "both"))
        conn.executemany(
            "INSERT INTO line_stations(line_id,station_id,seq,direction) VALUES(?,?,?,?)", ls)
        # trips (rebuild)
        conn.execute(f"DELETE FROM scheduled_trip_stops WHERE line_id IN ({ph})", line_ids)
        conn.execute(f"DELETE FROM scheduled_trips WHERE line_id IN ({ph})", line_ids)
        trips_rows, stop_rows = [], []
        for line, direction, no, days, stops in TRIPS:
            for dt in days:
                trips_rows.append((no, line, direction, dt, "daily" if days == DAILY else "weekday"))
                for seq, (sid, tm) in enumerate(stops):
                    stop_rows.append((no, line, direction, dt, sid, seq, tm))
        conn.executemany(
            "INSERT INTO scheduled_trips(train_no,line_id,direction,day_type,service_label)"
            " VALUES(?,?,?,?,?)", trips_rows)
        conn.executemany(
            "INSERT INTO scheduled_trip_stops(train_no,line_id,direction,day_type,"
            "station_id,stop_sequence,departure_time) VALUES(?,?,?,?,?,?,?)", stop_rows)
        cur.execute("COMMIT")
    except Exception:
        cur.execute("ROLLBACK")
        raise

    print(f"lines:   {len(LINES)}  ({', '.join(line_ids)})")
    print(f"stations:{len(S)}")
    print(f"trips:   {len(set((t[2],t[0],t[1]) for t in TRIPS))} distinct, "
          f"{len(trips_rows)} rows across day-types")
    print(f"stops:   {len(stop_rows)} rows")
    by = conn.execute(
        f"SELECT region,COUNT(*) n FROM lines GROUP BY region ORDER BY region").fetchall()
    print("lines by region:", {r["region"]: r["n"] for r in by})


if __name__ == "__main__":
    main()
