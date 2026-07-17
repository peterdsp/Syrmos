"""Seed the national + Thessaloniki-suburban rail corridors.

Every time here is transcribed from docs/plans/greece_passenger_rail_timetables_
2026-07-16.pdf (compiled from the Hellenic Train live booking system + the
railway.gov.gr live board, which states no times were estimated). Station
coordinates come from the OSM route relations in the Greece extract
(docs/plans/greek-rail-osm-relations.md), never invented.

Four corridors, all on the scheduled-trips path like Athens A1-A4:
  IC1  national  Athens <-> Thessaloniki (IC50/51/56/57), daily
  TP1  thess     Thessaloniki <-> Larisa (1590..2595), daily
  TP2  thess     Thessaloniki <-> Florina + Edessa short-turn, daily
  TP3  thess     Thessaloniki <-> Sindos shuttle, Mon-Fri

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
}

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
}

DAILY = ("mon_thu", "fri", "sat", "sun")
WEEKDAY = ("mon_thu", "fri")

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
            [(sid, en, el, la, lo, "national" if sid in ("GR_ATH", "GR_OIN", "GR_THI",
              "GR_LIV", "GR_TIT", "GR_LEI", "GR_PAL") else "thessaloniki")
             for sid, (en, el, la, lo) in S.items()],
        )
        # lines
        conn.executemany(
            "INSERT INTO lines(id,mode,name_en,name_el,color,terminal_a,terminal_b,"
            "sort_order,region,status) VALUES(?,?,?,?,?,?,?,?,?,'operational')"
            " ON CONFLICT(id) DO UPDATE SET name_en=excluded.name_en,"
            " name_el=excluded.name_el,color=excluded.color,region=excluded.region,"
            " sort_order=excluded.sort_order,status='operational'",
            [(lid, "suburban", en, el, col, ta, tb, so, reg)
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
