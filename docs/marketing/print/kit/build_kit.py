#!/usr/bin/env python3
"""Build the Syrmos 3.0.2 print kit.

Writes one print-ready PDF per (item, variant, src code) into `out/`, plus
PNG previews, a contact sheet and `out/manifest.csv`. The spec lives in
`../README.md`; the copy and concepts in `../../3.0.2-GROWTH-RESEARCH.md`.

Every PDF has a 3 mm bleed, TrimBox and BleedBox set, vector QR codes and
embedded fonts. Die-cut stickers carry their cut line as a 0.25 pt stroke in
a separate spot colour named "CutContour" (overprinting), which is what
sticker printers' RIPs read.

Usage: python3 docs/marketing/print/kit/build_kit.py [--no-previews]
Needs: reportlab; pdftocairo + ImageMagick for previews.
"""

import csv
from contextlib import contextmanager
import json
import math
import shutil
import subprocess
import sys
import urllib.request
import zipfile
from pathlib import Path

from reportlab.graphics.barcode import qrencoder
from reportlab.lib.colors import CMYKColorSep, HexColor
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas as pdfcanvas


KIT = Path(__file__).resolve().parent
REPO = KIT.parents[3]
OUT = KIT / "out"
FONTS = KIT / ".fonts"
CACHE = KIT / ".cache"
SEED = REPO / "iosApp/iosApp/Resources/seed-schedules-v2"
ICON_LIGHT = REPO / "docs/assets/icon-default-1024.png"

BASE_URL = "https://syrmos.peterdsp.dev/get-app/?src="
SHORT_URL = "syrmos.peterdsp.dev/get-app"
REPO_NAME = "github.com/peterdsp/Syrmos"

MM = 72.0 / 25.4
BLEED = 3.0
SAFE = 3.0
MIN_TEXT_PT = 5.0

# Calm Signal palette (README "Global spec").
BRAND = HexColor("#1466B8")
BRAND_STRONG = HexColor("#0F4E8C")
CANVAS = HexColor("#F7F5F1")
WHITE = HexColor("#FFFFFF")
TEXT = HexColor("#14181F")
SECONDARY = HexColor("#5B636E")
SUBTLE = HexColor("#EFEBE4")
DARK = HexColor("#0F1216")
DARK_BRAND = HexColor("#8ECAFF")
DARK_SECONDARY = HexColor("#A9B1BC")
BUS = HexColor("#B45309")
DISRUPTION = HexColor("#B42318")
CUT = CMYKColorSep(0, 1, 0, 0, spotName="CutContour", density=1)

FONT_URLS = {
    "inter": "https://github.com/rsms/inter/releases/download/v4.1/Inter-4.1.zip",
    "mono": "https://github.com/JetBrains/JetBrainsMono/releases/download/v2.304/JetBrainsMono-2.304.zip",
}
FONT_FILES = {
    "Inter": ("inter", "extras/ttf/Inter-Regular.ttf"),
    "Inter-Medium": ("inter", "extras/ttf/Inter-Medium.ttf"),
    "Inter-SemiBold": ("inter", "extras/ttf/Inter-SemiBold.ttf"),
    "Inter-Bold": ("inter", "extras/ttf/Inter-Bold.ttf"),
    "InterDisplay-Bold": ("inter", "extras/ttf/InterDisplay-Bold.ttf"),
    "Mono": ("mono", "fonts/ttf/JetBrainsMono-Medium.ttf"),
}


# ---------------------------------------------------------------- data

def load_seed():
    lines = json.loads((SEED / "lines.json").read_text())["lines"]
    by_id = {l["id"]: l for l in lines}
    stations = {}
    for l in lines:
        for s in l.get("stations", []):
            stations[s["id"]] = s
    offsets = json.loads((SEED / "station-offsets.json").read_text())["lines"]
    return by_id, stations, offsets


LINES, STATIONS, OFFSETS = load_seed()


def line_color(line_id):
    return HexColor(LINES[line_id]["color"])


def airport_example():
    """Leave time for a 10:00 flight from Syntagma, using the app's rule.

    Mirrors CalendarLeaveBy.plan: leaveAt = start - flightBuffer -
    (journey + reliabilityMargin), margin = ceil(firstLegHeadway / 2) with no
    transfers, rain or alerts. Journey and headway come from the seed.
    """
    leg = next(l for l in OFFSETS if l["lineId"] == "M3_AIR" and l["direction"] == "outbound")
    mins = {s["stationId"]: s["minutesFromOrigin"] for s in leg["stops"]}
    journey = mins["M3_AER"] - mins["M3_SYN"]
    bands = json.loads((SEED / "M3_AIR.json").read_text())["bands"]
    headway = int(next(b["headwayMinutes"] for b in bands
                       if b["dayType"] == "mon_thu" and b["direction"] == "outbound"))
    margin = min(20, (headway + 1) // 2)
    leave = 10 * 60 - 120 - journey - margin
    return {"journey": journey, "headway": headway, "margin": margin,
            "leave": f"{leave // 60}:{leave % 60:02d}"}


AIRPORT = airport_example()
LEAVE = AIRPORT["leave"]

# ---------------------------------------------------------------- fonts

def ensure_fonts():
    FONTS.mkdir(exist_ok=True)
    for name, (pack, member) in FONT_FILES.items():
        target = FONTS / Path(member).name
        if not target.exists():
            archive = FONTS / f"{pack}.zip"
            if not archive.exists():
                print(f"downloading {FONT_URLS[pack]}")
                urllib.request.urlretrieve(FONT_URLS[pack], archive)
            with zipfile.ZipFile(archive) as z, open(target, "wb") as f:
                f.write(z.read(member))
        pdfmetrics.registerFont(TTFont(name, str(target)))


def icon_for_print():
    """The production icon as a 768 px JPEG: over 800 dpi at the largest logo
    size used (16 mm), and about a tenth of the PNG's weight in each PDF."""
    target = CACHE / "icon-default-768.jpg"
    if not target.exists() or target.stat().st_mtime < ICON_LIGHT.stat().st_mtime:
        from PIL import Image
        CACHE.mkdir(exist_ok=True)
        Image.open(ICON_LIGHT).convert("RGB").resize((768, 768), Image.LANCZOS).save(target, quality=92)
    return target


# ---------------------------------------------------------------- QR

def qr_matrix(url):
    for version in range(1, 21):
        try:
            q = qrencoder.QRCode(version, qrencoder.QRErrorCorrectLevel.M)
            q.addData(url)
            q.make()
        except Exception:
            continue
        n = q.getModuleCount()
        return [[bool(q.isDark(r, c)) for c in range(n)] for r in range(n)], version
    raise ValueError(f"Payload too long for a printable QR: {url}")


# ---------------------------------------------------------------- drawing

class Piece:
    """One PDF: trim size in mm, drawn with (0, 0) at the trim's lower left."""

    def __init__(self, path, w, h, *, cut=None, pages=1):
        self.path, self.w, self.h, self.cut = path, w, h, cut
        self.c = pdfcanvas.Canvas(str(path), pagesize=((w + 2 * BLEED) * MM, (h + 2 * BLEED) * MM))
        self.c.setTitle(path.stem)
        self.c.setAuthor("Syrmos")
        self.c.setSubject("Syrmos 3.0.2 print kit")
        self.qr = []
        self.texts = []
        self._begin()

    def _begin(self):
        c = self.c
        tw, th = (self.w + 2 * BLEED) * MM, (self.h + 2 * BLEED) * MM
        c.setBleedBox((0, 0, tw, th))
        c.setTrimBox((BLEED * MM, BLEED * MM, tw - BLEED * MM, th - BLEED * MM))
        c.translate(BLEED * MM, BLEED * MM)
        c.scale(MM, MM)

    def new_page(self):
        self.draw_cut()
        self.c.showPage()
        self._begin()

    def finish(self):
        self.draw_cut()
        self.c.showPage()
        self.c.save()

    def draw_cut(self):
        if not self.cut:
            return
        c = self.c
        c.saveState()
        c.setStrokeColor(CUT)
        c.setStrokeOverprint(True)
        c.setLineWidth(0.25 / MM)
        c.setLineJoin(1)
        p = c.beginPath()
        self.cut(p)
        c.drawPath(p, stroke=1, fill=0)
        c.restoreState()

    # -- primitives (all in mm)

    def fill_bleed(self, color):
        c = self.c
        c.setFillColor(color)
        c.rect(-BLEED, -BLEED, self.w + 2 * BLEED, self.h + 2 * BLEED, stroke=0, fill=1)

    def rect(self, x, y, w, h, color, r=0):
        c = self.c
        c.setFillColor(color)
        if r:
            c.roundRect(x, y, w, h, r, stroke=0, fill=1)
        else:
            c.rect(x, y, w, h, stroke=0, fill=1)

    def circle(self, x, y, r, fill=None, stroke=None, width=0.4):
        c = self.c
        if fill is not None:
            c.setFillColor(fill)
        if stroke is not None:
            c.setStrokeColor(stroke)
            c.setLineWidth(width)
        c.circle(x, y, r, stroke=1 if stroke is not None else 0, fill=1 if fill is not None else 0)

    def text(self, x, y, s, size, font="Inter", color=TEXT, anchor="l", track=0):
        assert size >= MIN_TEXT_PT, f"{s!r} at {size}pt is below the {MIN_TEXT_PT}pt minimum"
        c = self.c
        pt = size / MM
        w = self.width(s, size, font, track)
        if anchor == "c":
            x -= w / 2
        elif anchor == "r":
            x -= w
        t = c.beginText()
        t.setTextOrigin(x, y)
        t.setFont(font, pt)
        t.setFillColor(color)
        # Tc persists in the graphics state across text objects, so always set it.
        t.setCharSpace(track * pt)
        t.textOut(s)
        c.drawText(t)
        self.texts.append(s)
        return w

    @staticmethod
    def width(s, size, font="Inter", track=0):
        pt = size / MM
        return pdfmetrics.stringWidth(s, font, pt) + track * pt * max(0, len(s) - 1)

    def lines(self, x, y, rows, size, font="Inter", color=TEXT, anchor="l", leading=1.22):
        step = size * leading / MM
        for i, row in enumerate(rows):
            self.text(x, y - i * step, row, size, font, color, anchor)
        return y - len(rows) * step

    def stroke_path(self, pts, color, width, dash=None, smooth=False, alpha=1.0):
        c = self.c
        c.saveState()
        c.setStrokeColor(color)
        c.setStrokeAlpha(alpha)
        c.setLineWidth(width)
        c.setLineCap(1)
        c.setLineJoin(1)
        if dash:
            c.setDash(dash)
        p = c.beginPath()
        p.moveTo(*pts[0])
        if smooth:
            for i in range(1, len(pts) - 2, 3):
                p.curveTo(*pts[i], *pts[i + 1], *pts[i + 2])
        else:
            for pt in pts[1:]:
                p.lineTo(*pt)
        c.drawPath(p, stroke=1, fill=0)
        c.restoreState()

    def node(self, x, y, r, color, fill=WHITE, width=None):
        self.circle(x, y, r, fill=fill, stroke=color, width=width or r * 0.55)

    def qr_code(self, x, y, size, src, *, panel=False, caption=True, dark=TEXT, caption_color=SECONDARY,
                payload=None):
        """QR whose module area is `size` mm, lower left at (x, y).

        The quiet zone (4 modules) is the ground around it; with panel=True a
        white rounded panel carries the quiet zone and the caption. `payload`
        replaces the plain get-app URL with text (the sticker guides).
        """
        url = payload if payload is not None else BASE_URL + src
        matrix, version = qr_matrix(url)
        n = len(matrix)
        m = size / n
        quiet = 4 * m
        cap_h = 3.2 if caption else 0
        fs = 5.4 if size < 30 else (6.5 if size < 60 else 11)
        if panel:
            pw = max(size + 2 * quiet, self.width(SHORT_URL, fs, "Inter-Medium") + 2.4)
            self.rect(x + size / 2 - pw / 2, y - quiet - cap_h, pw, size + 2 * quiet + cap_h, WHITE, r=1.6)
        c = self.c
        c.setFillColor(dark)
        p = c.beginPath()
        for r, row in enumerate(matrix):
            yy = y + size - (r + 1) * m
            col = 0
            while col < n:
                if row[col]:
                    start = col
                    while col < n and row[col]:
                        col += 1
                    # Tiny overlap avoids hairline seams between rows in some RIPs.
                    p.rect(x + start * m, yy - 0.002, (col - start) * m, m + 0.004)
                else:
                    col += 1
        c.drawPath(p, stroke=0, fill=1)
        if caption:
            self.text(x + size / 2, y - quiet - cap_h + 0.9 + (0.6 if panel else 0), SHORT_URL, fs,
                      "Inter-Medium", caption_color, "c")
        self.qr.append({"src": src, "url": url, "payload": url, "size_mm": round(size, 2),
                        "modules": n, "version": version, "module_mm": round(m, 3)})
        return quiet

    def logo(self, x, y, size):
        """Production app icon, clipped to its rounded square (the PNG has
        opaque black corners)."""
        c = self.c
        c.saveState()
        p = c.beginPath()
        p.roundRect(x, y, size, size, size * 0.225)
        c.clipPath(p, stroke=0, fill=0)
        inset = size * 0.035
        c.drawImage(str(icon_for_print()), x - inset, y - inset, size + 2 * inset, size + 2 * inset)
        c.restoreState()

    def dots(self, x, y, line_ids, r=1.1, gap=3.1):
        for i, lid in enumerate(line_ids):
            self.circle(x + i * gap, y, r, fill=line_color(lid))

    def chip(self, x, y, label, size, fg, bg, font="Inter-SemiBold", pad=1.3, anchor="l", border=None):
        w = self.width(label, size, font) + 2 * pad
        h = size / MM * 1.55
        if anchor == "c":
            x -= w / 2
        self.rect(x, y, w, h, bg, r=h / 2)
        if border is not None:
            self.c.setStrokeColor(border)
            self.c.setLineWidth(0.25)
            self.c.roundRect(x, y, w, h, h / 2, stroke=1, fill=0)
        self.text(x + pad, y + h * 0.3, label, size, font, fg)
        return w

    # -- glyphs (simple, original vector drawings)

    def plane(self, cx, cy, s, color, angle=0):
        c = self.c
        c.saveState()
        c.translate(cx, cy)
        c.rotate(angle)
        c.scale(s / 10, s / 10)
        c.setFillColor(color)
        p = c.beginPath()
        pts = [(5, 0), (4.3, 0.6), (0.9, 0.6), (-1.6, 4.6), (-2.6, 4.6), (-1.2, 0.6), (-3.6, 0.6),
               (-4.4, 1.9), (-5.1, 1.9), (-4.6, 0), (-5.1, -1.9), (-4.4, -1.9), (-3.6, -0.6),
               (-1.2, -0.6), (-2.6, -4.6), (-1.6, -4.6), (0.9, -0.6), (4.3, -0.6)]
        p.moveTo(*pts[0])
        for pt in pts[1:]:
            p.lineTo(*pt)
        p.close()
        c.drawPath(p, stroke=0, fill=1)
        c.restoreState()



class Placed(Piece):
    """A sticker drawn onto a sheet page at (ox, oy) mm, clipped to its own
    bleed, with its own kiss-cut line."""

    def __init__(self, canvas, ox, oy, w, h, *, cut=None):
        self.path, self.w, self.h, self.cut = None, w, h, cut
        self.c = canvas
        self.qr = []
        self.texts = []
        canvas.saveState()
        canvas.translate(ox, oy)
        # Clip the art to the sticker's own shape plus bleed, so nothing
        # square is left printed around round or pill stickers.
        p = canvas.beginPath()
        if cut:
            cut(p, BLEED)
        else:
            p.rect(-BLEED, -BLEED, w + 2 * BLEED, h + 2 * BLEED)
        canvas.clipPath(p, stroke=0, fill=0)

    def finish(self):
        self.draw_cut()
        self.c.restoreState()


# ---------------------------------------------------------------- art helpers

NAVY = HexColor("#06285C")
NAVY_DEEP = HexColor("#03193B")
ICE = HexColor("#D6E6F7")
TRAM = HexColor("#F39800")
THESS = HexColor("#0057B8")


@contextmanager
def clipped(pc, shape_fn):
    c = pc.c
    c.saveState()
    p = c.beginPath()
    shape_fn(p)
    c.clipPath(p, stroke=0, fill=0)
    try:
        yield
    finally:
        c.restoreState()


def soft_ground(pc, top=WHITE, bottom=CANVAS):
    """Light ground with a quiet vertical fade."""
    with clipped(pc, lambda p: p.rect(-BLEED, -BLEED, pc.w + 2 * BLEED, pc.h + 2 * BLEED)):
        pc.c.linearGradient(0, pc.h + BLEED, 0, -BLEED, (top, bottom), extend=True)


def headline(pc, x, y, rows, size, colors, anchor="l", leading=1.1, font="Inter-Bold"):
    step = size * leading / MM
    for i, (row, col) in enumerate(zip(rows, colors)):
        pc.text(x, y - i * step, row, size, font, col, anchor)
    return y - len(rows) * step


def fit(rows, size, width, font="Inter-Bold", floor=5.0):
    while max(Piece.width(r, size, font) for r in rows) > width and size > floor:
        size -= 0.1
    return size


def wrap(text, size, width, font="Inter"):
    words, lines, cur = text.split(), [], ""
    for wd in words:
        trial = (cur + " " + wd).strip()
        if Piece.width(trial, size, font) <= width or not cur:
            cur = trial
        else:
            lines.append(cur)
            cur = wd
    return lines + [cur]


def shadow(pc, x, y, w, h, r, k=1.0):
    c = pc.c
    for grow, a in ((2.2, 0.04), (1.2, 0.06), (0.5, 0.08)):
        c.saveState()
        c.setFillColor(NAVY_DEEP)
        c.setFillAlpha(a)
        c.roundRect(x - grow * k, y - (grow + 0.7) * k, w + 2 * grow * k, h + 2 * grow * k, r + grow * k,
                    stroke=0, fill=1)
        c.restoreState()


# ---------------------------------------------------------------- copy

LANGS = ("en", "el", "sq", "it")
# Stickers come in the three languages of the people we hand them to.
STICKER_LANGS = ("en", "el", "sq")

# Albanian and Italian follow the app's own wording (Localization.swift).
# Have a native speaker read them before print.
COPY = {
    "en": {
        "set": [["From the port", "to the pines."], ["Next stop:", "Acropolis."], ["On time", "for your flight."],
                ["Sea view", "included."], ["Thessaloniki,", "finally."], ["Always knows", "when."]],
        "tram": "Tram", "metro": "Metro",
        "card": (["Flight at 10?", f"Leave at {LEAVE}."], "Syrmos reminds you when to leave."),
        "ui": {"next": "Next train", "to": "to", "live": "Live", "min": "min", "then": "then",
               "dest": "Airport", "station": "SYNTAGMA"},
        "scan": "Scan for your next train",
        "contrib": ("Contributor", ["I helped", "build this."]),
        "tagline": "Free · Open source · No ads",
        "disclaimer": "Not affiliated with STASY, OASA, OSETH or Hellenic Train",
    },
    "el": {
        "set": [["Από το λιμάνι", "στα πεύκα."], ["Επόμενη στάση:", "Ακρόπολη."], ["Στην ώρα σου", "για την πτήση."],
                ["Με θέα", "θάλασσα."], ["Θεσσαλονίκη,", "επιτέλους."], ["Πάντα ξέρει", "πότε."]],
        "tram": "Τραμ", "metro": "Μετρό",
        "card": (["Πτήση στις 10;", f"Φύγε στις {LEAVE}."], "Το Syrmos σου θυμίζει πότε να φύγεις."),
        "ui": {"next": "Επόμενος συρμός", "to": "προς", "live": "Ζωντανά", "min": "λεπ", "then": "μετά",
               "dest": "Αεροδρόμιο", "station": "ΣΥΝΤΑΓΜΑ"},
        "scan": "Σκάναρε για τον επόμενο συρμό",
        "contrib": ("Συνεισφορά", ["Το έφτιαξα", "κι εγώ."]),
        "tagline": "Δωρεάν · Ανοιχτός κώδικας · Χωρίς διαφημίσεις",
        "disclaimer": "Δεν συνδέεται με ΣΤΑΣΥ, ΟΑΣΑ, ΟΣΕΘ ή Hellenic Train",
    },
    "sq": {
        "set": [["Nga porti", "te pishat."], ["Stacioni tjetër:", "Akropoli."], ["Në kohë", "për fluturimin."],
                ["Me pamje", "nga deti."], ["Selanik,", "më në fund."], ["E di gjithmonë", "kur."]],
        "tram": "Tramvaj", "metro": "Metro",
        "card": (["Fluturim në 10?", f"Nisu në {LEAVE}."], "Syrmos të kujton kur të nisesh."),
        "ui": {"next": "Treni i ardhshëm", "to": "drejt", "live": "Drejtpërdrejt", "min": "min",
               "then": "pastaj", "dest": "Aeroport", "station": "SYNTAGMA"},
        "scan": "Skano për trenin e ardhshëm",
        "contrib": ("Kontribues", ["E ndërtova", "edhe unë."]),
        "tagline": "Falas · Kod i hapur · Pa reklama",
        "disclaimer": "Nuk lidhet me STASY, OASA, OSETH ose Hellenic Train",
    },
    "it": {
        "tram": "Tram", "metro": "Metro",
        "card": (["Volo alle 10?", f"Parti alle {LEAVE}."], "Syrmos ti ricorda quando partire."),
        "ui": {"next": "Prossimo treno", "to": "verso", "live": "Live", "min": "min", "then": "poi",
               "dest": "Aeroporto", "station": "SYNTAGMA"},
        "scan": "Inquadra per il prossimo treno",
        "tagline": "Gratis · Open source · Senza pubblicità",
        "disclaimer": "Non affiliato a STASY, OASA, OSETH o Hellenic Train",
    },
}


# ---------------------------------------------------------------- offline sticker guides

# Each sticker's QR holds plain text, not just a link: a phone camera shows
# the text with no internet, and the link at the end opens once online.
# Every number comes from the bundled seed, so the guide matches the app.

def _sched(line_id):
    return json.loads((SEED / f"{line_id}.json").read_text())


def line_hours(line_id):
    """Weekday hours, plus the Friday close and Saturday all-night service
    when they differ (a 05:28 Saturday close is the 24h overnight)."""
    rules = {r["dayType"]: r for r in _sched(line_id)["rules"]}
    wk = rules["mon_thu"]
    fri = rules["fri"]["closeTime"]
    sat = rules["sat"]["closeTime"]
    return {"open": wk["openTime"], "close": wk["closeTime"],
            "fri": fri if fri != wk["closeTime"] else None,
            "all_night_sat": sat.startswith("05:")}


def line_headway(line_id):
    """Weekday spread of minutes between trains, from the seed's bands."""
    hw = [b["headwayMinutes"] for b in _sched(line_id)["bands"] if b["dayType"] == "mon_thu"]
    return min(hw), max(hw)


def airport_fare():
    products = json.loads((SEED / "fares.json").read_text())["products"]
    p = next(p for p in products if p["titleEn"].startswith("Airport single"))
    return p["fullPriceEur"], p["discountedPriceEur"]


def seed_month():
    stamp = json.loads((SEED / "manifest.json").read_text())["updatedAt"]
    return f"{stamp[5:7]}/{stamp[:4]}"


def num(x, lang):
    s_ = f"{x:.1f}".rstrip("0").rstrip(".") if x != int(x) else str(int(x))
    return s_.replace(".", ",") if lang != "en" else s_


def money(x, lang):
    s_ = f"{x:.2f}".replace(".00", "") if x != int(x) else str(int(x))
    return (s_.replace(".", ",") if lang != "en" else s_) + " €"


def station(line_id, idx, lang):
    st = LINES[line_id]["stations"][idx]
    return st.get("nameEl", st["name"]) if lang == "el" else st["name"]


GUIDE = {
    "en": {"every": "every {a}-{b} min", "daily": "Daily {o}-{c}", "fri": "Fri to {t}", "sat": "Sat all night",
           "stations": "{n} stations", "tap": "Tap card or phone at the gates", "asof": "Times {m}",
           "link": "Get the app:",
           "s1": "Piraeus: ferries to the islands",
           "s2": "Akropoli: Acropolis Museum",
           "s3a": "Syrmos · M3 to the Airport", "s3b": "Syntagma - Airport {j} min · every {h} min",
           "s3c": "Airport ticket {f} (reduced {r})",
           "s4": "Syrmos · Tram T6 / T7", "s4b": "{a} - {b} (T6), along the coast to Voula (T7)",
           "s5": "Syrmos · Thessaloniki Metro", "s5b": "Airport: bus X3 from Mikra, 2X from Nea Elvetia",
           "s6a": "Syrmos: your next train, live or offline", "s6b": "Metro, tram and trains in Athens and Thessaloniki",
           "s6c": "Emergency: 112",
           "s7a": "Thank you for building Syrmos!", "s7b": "Code, bugs, translations:",
           "liner": "Scan the badge: a short guide opens even offline, with the app link for later."},
    "el": {"every": "κάθε {a}-{b}′", "daily": "Κάθε μέρα {o}-{c}", "fri": "Παρ έως {t}", "sat": "Σάβ 24ωρο",
           "stations": "{n} σταθμοί", "tap": "Ανέπαφα στις πύλες", "asof": "Ώρες {m}",
           "link": "",
           "s1": "Πειραιάς: πλοία για τα νησιά",
           "s2": "Ακρόπολη: Μουσείο Ακρόπολης",
           "s3a": "Syrmos · M3 για Αεροδρόμιο", "s3b": "Σύνταγμα - Αεροδρόμιο {j}′ · κάθε {h}′",
           "s3c": "Εισιτήριο {f} (μειωμένο {r})",
           "s4": "Syrmos · Τραμ T6 / T7", "s4b": "{a} - {b} (T6), παραλία ως Βούλα (T7)",
           "s5": "Syrmos · Μετρό Θεσσαλονίκης", "s5b": "Αεροδρόμιο: X3 από Μίκρα, 2X από Νέα Ελβετία",
           "s6a": "Syrmos: ο επόμενος συρμός, live ή offline", "s6b": "Μετρό, τραμ και τρένα σε Αθήνα και Θεσσαλονίκη",
           "s6c": "Έκτακτη ανάγκη: 112",
           "s7a": "Ευχαριστούμε που φτιάχνεις το Syrmos!", "s7b": "Κώδικας, bugs, μεταφράσεις:",
           "liner": "Σκάναρε το σήμα: ένας μικρός οδηγός ανοίγει και χωρίς internet, με το link για μετά."},
    "sq": {"every": "çdo {a}-{b} min", "daily": "Çdo ditë {o}-{c}", "fri": "E premte deri {t}",
           "sat": "E shtunë gjithë natën",
           "stations": "{n} stacione", "tap": "Prek kartën ose telefonin te portat", "asof": "Oraret {m}",
           "link": "Aplikacioni:",
           "s1": "Piraeus: tragete për ishujt",
           "s2": "Akropoli: Muzeu i Akropolit",
           "s3a": "Syrmos · M3 për në Aeroport", "s3b": "Syntagma - Aeroport {j} min · çdo {h} min",
           "s3c": "Biletë aeroporti {f} (e reduktuar {r})",
           "s4": "Syrmos · Tramvaji T6 / T7", "s4b": "{a} - {b} (T6), buzë detit deri në Voula (T7)",
           "s5": "Syrmos · Metroja e Selanikut", "s5b": "Aeroporti: autobusi X3 nga Mikra, 2X nga Nea Elvetia",
           "s6a": "Syrmos: treni yt i ardhshëm, live ose offline", "s6b": "Metro, tramvaj dhe tren në Athinë dhe Selanik",
           "s6c": "Urgjenca: 112",
           "s7a": "Faleminderit që ndërton Syrmos!", "s7b": "Kod, gabime, përkthime:",
           "liner": "Skano stemën: një udhëzues i shkurtër hapet edhe pa internet, me lidhjen për më vonë."},
}


def hours_line(line_id, lang):
    g, h = GUIDE[lang], line_hours(line_id)
    parts = [g["daily"].format(o=h["open"], c=h["close"])]
    if h["fri"]:
        parts.append(g["fri"].format(t=h["fri"]))
    if h["all_night_sat"]:
        parts.append(g["sat"])
    return " · ".join(parts)


def every_line(line_id, lang):
    a, b = line_headway(line_id)
    return GUIDE[lang]["every"].format(a=num(a, lang), b=num(b, lang))


def sticker_src(n, lang):
    return f"st{n}-{lang}"


def guide(n, lang):
    """The text inside sticker n's QR, in one language."""
    g = GUIDE[lang]
    asof = g["asof"].format(m=seed_month())
    # Greek has no link label: its letters cost 2 bytes each, and the URL
    # speaks for itself, so the code stays one size less dense.
    tail = [f'{asof} · {g["link"]}' if g["link"] else asof, BASE_URL + sticker_src(n, lang)]
    if n == 1:
        rows = [f"Syrmos · M1 {station('M1', 0, lang)} - {station('M1', -1, lang)}",
                f"{g['stations'].format(n=len(LINES['M1']['stations']))} · {every_line('M1', lang)}",
                hours_line("M1", lang), g["s1"], g["tap"]]
    elif n == 2:
        rows = [f"Syrmos · M2 {station('M2', 0, lang)} - {station('M2', -1, lang)}",
                f"{g['stations'].format(n=len(LINES['M2']['stations']))} · {every_line('M2', lang)}",
                hours_line("M2", lang), g["s2"], g["tap"]]
    elif n == 3:
        full, reduced = airport_fare()
        rows = [g["s3a"], g["s3b"].format(j=AIRPORT["journey"], h=num(AIRPORT["headway"], lang)),
                g["s3c"].format(f=money(full, lang), r=money(reduced, lang)), hours_line("M3_AIR", lang), g["tap"]]
    elif n == 4:
        rows = [g["s4"], g["s4b"].format(a=station("T6", 0, lang), b=station("T6", -1, lang)),
                every_line("T6", lang), hours_line("T6", lang), g["tap"]]
    elif n == 5:
        # Greek's full "New Railway Station" would push this QR a size denser.
        origin = station("TM1", 0, lang).replace("Σιδηροδρομικός", "Σιδ.")
        rows = [g["s5"], f"{origin} - {station('TM1', -1, lang)} / {station('TM2', -1, lang)}",
                every_line("TM1", lang), hours_line("TM1", lang), g["s5b"]]
    elif n == 6:
        return "\n".join([g["s6a"], g["s6b"], COPY[lang]["tagline"], g["s6c"],
                          *([g["link"]] if g["link"] else []), BASE_URL + sticker_src(n, lang)])
    else:
        return "\n".join([g["s7a"], g["s7b"], "https://" + REPO_NAME,
                          *([g["link"]] if g["link"] else []), BASE_URL + sticker_src(n, lang)])
    return "\n".join(rows + tail)


# ---------------------------------------------------------------- glyphs (original, flat, white)

def g_anchor(pc, cx, cy, s, col):
    c = pc.c
    c.saveState()
    c.setStrokeColor(col)
    c.setLineWidth(s * 0.085)
    c.setLineCap(1)
    c.circle(cx, cy + s * 0.38, s * 0.085, stroke=1, fill=0)
    c.line(cx, cy + s * 0.29, cx, cy - s * 0.42)
    c.line(cx - s * 0.2, cy + s * 0.16, cx + s * 0.2, cy + s * 0.16)
    r = s * 0.31
    ay = cy - s * 0.11
    p = c.beginPath()
    p.arc(cx - r, ay - r, cx + r, ay + r, 205, 130)
    c.drawPath(p, stroke=1, fill=0)
    c.setFillColor(col)
    for sx in (-1, 1):
        a = math.radians(205 if sx < 0 else 335)
        x0, y0 = cx + r * math.cos(a), ay + r * math.sin(a)
        tip = c.beginPath()
        tip.moveTo(x0 + sx * s * 0.02, y0 + s * 0.13)
        tip.lineTo(x0 + sx * s * 0.11, y0 - s * 0.04)
        tip.lineTo(x0 - sx * s * 0.09, y0 - s * 0.02)
        tip.close()
        c.drawPath(tip, stroke=0, fill=1)
    c.restoreState()


def g_temple(pc, cx, cy, s, col):
    c = pc.c
    c.saveState()
    c.setFillColor(col)
    p = c.beginPath()
    p.moveTo(cx - s * 0.5, cy + s * 0.2)
    p.lineTo(cx, cy + s * 0.44)
    p.lineTo(cx + s * 0.5, cy + s * 0.2)
    p.close()
    c.drawPath(p, stroke=0, fill=1)
    c.rect(cx - s * 0.48, cy + s * 0.1, s * 0.96, s * 0.07, stroke=0, fill=1)
    for i in range(5):
        x = cx - s * 0.4 + i * s * 0.2
        c.rect(x - s * 0.045, cy - s * 0.3, s * 0.09, s * 0.38, stroke=0, fill=1)
    c.rect(cx - s * 0.5, cy - s * 0.38, s * 1.0, s * 0.06, stroke=0, fill=1)
    c.rect(cx - s * 0.56, cy - s * 0.46, s * 1.12, s * 0.06, stroke=0, fill=1)
    c.restoreState()


def g_plane(pc, cx, cy, s, col):
    pc.plane(cx, cy, s * 1.05, col, angle=35)


def g_wave(pc, cx, cy, s, col):
    c = pc.c
    c.saveState()
    c.setStrokeColor(col)
    c.setLineWidth(s * 0.085)
    c.setLineCap(1)
    for dy in (s * 0.16, -s * 0.12):
        p = c.beginPath()
        x0 = cx - s * 0.5
        p.moveTo(x0, cy + dy)
        for k in range(4):
            xa = x0 + k * s * 0.25
            p.curveTo(xa + s * 0.06, cy + dy + s * 0.11, xa + s * 0.19, cy + dy + s * 0.11, xa + s * 0.25, cy + dy)
            # alternate dip
        c.drawPath(p, stroke=1, fill=0)
    c.setFillColor(col)
    # a small sun over the sea
    c.circle(cx + s * 0.28, cy + s * 0.42, s * 0.11, stroke=0, fill=1)
    c.restoreState()


def g_tower(pc, cx, cy, s, col, ground):
    c = pc.c
    c.saveState()
    c.setFillColor(col)
    c.rect(cx - s * 0.22, cy - s * 0.46, s * 0.44, s * 0.66, stroke=0, fill=1)
    for i in range(5):
        c.rect(cx - s * 0.25 + i * s * 0.11, cy + s * 0.2, s * 0.06, s * 0.08, stroke=0, fill=1)
    c.rect(cx - s * 0.25, cy + s * 0.16, s * 0.5, s * 0.06, stroke=0, fill=1)
    c.rect(cx - s * 0.1, cy + s * 0.28, s * 0.2, s * 0.14, stroke=0, fill=1)
    p = c.beginPath()
    p.moveTo(cx - s * 0.12, cy + s * 0.42)
    p.lineTo(cx, cy + s * 0.5)
    p.lineTo(cx + s * 0.12, cy + s * 0.42)
    p.close()
    c.drawPath(p, stroke=0, fill=1)
    c.setFillColor(ground)
    for wy in (cy - s * 0.02, cy - s * 0.24):
        for wx in (cx - s * 0.1, cx + s * 0.1):
            c.roundRect(wx - s * 0.03, wy, s * 0.06, s * 0.1, s * 0.03, stroke=0, fill=1)
    c.rect(cx - s * 0.6, cy - s * 0.5, s * 1.2, s * 0.05, stroke=0, fill=0)
    c.setFillColor(col)
    c.rect(cx - s * 0.5, cy - s * 0.52, s * 1.0, s * 0.05, stroke=0, fill=1)
    c.restoreState()


def g_owl(pc, cx, cy, s, col, ground):
    c = pc.c
    c.saveState()
    c.setFillColor(col)
    c.roundRect(cx - s * 0.36, cy - s * 0.46, s * 0.72, s * 0.82, s * 0.32, stroke=0, fill=1)
    for sx in (-1, 1):
        p = c.beginPath()
        p.moveTo(cx + sx * s * 0.36, cy + s * 0.2)
        p.lineTo(cx + sx * s * 0.34, cy + s * 0.5)
        p.lineTo(cx + sx * s * 0.14, cy + s * 0.34)
        p.close()
        c.drawPath(p, stroke=0, fill=1)
    c.setFillColor(ground)
    for sx in (-1, 1):
        c.circle(cx + sx * s * 0.16, cy + s * 0.1, s * 0.15, stroke=0, fill=1)
    c.setFillColor(col)
    for sx in (-1, 1):
        c.circle(cx + sx * s * 0.16, cy + s * 0.1, s * 0.065, stroke=0, fill=1)
    c.setFillColor(ground)
    p = c.beginPath()
    p.moveTo(cx - s * 0.05, cy - s * 0.04)
    p.lineTo(cx + s * 0.05, cy - s * 0.04)
    p.lineTo(cx, cy - s * 0.14)
    p.close()
    c.drawPath(p, stroke=0, fill=1)
    for i, y in enumerate((cy - s * 0.24, cy - s * 0.34)):
        for k in range(3 - i):
            x = cx - s * (0.12 - 0.06 * i) + k * s * 0.12
            c.circle(x, y, s * 0.03, stroke=0, fill=1)
    c.restoreState()


# ---------------------------------------------------------------- collectible sticker set

# number, line code (or None), colour, glyph
SET = [
    (1, "M1", line_color("M1"), "anchor"),
    (2, "M2", line_color("M2"), "temple"),
    (3, "M3", line_color("M3"), "plane"),
    (4, "tram", TRAM, "wave"),
    (5, "metro", THESS, "tower"),
    (6, None, NAVY, "owl"),
]
# An 8 cm round die-cut badge. Its QR sits on a white panel in the lower half
# and holds the sticker's offline guide (see guide()).
ST_W = ST_H = 80.0
ST_CX = ST_CY = 40.0
KEYLINE = 2.6
ST_R = 39.0 - KEYLINE
ST_QR = 28.0
ST_RIM = ST_R - 1.9      # the thin white inner ring
ST_RIM_MARGIN = 2.2      # clear colour between the QR panel's corners and that ring


def st_cut(p, g=0.0):
    p.circle(ST_CX, ST_CY, ST_R + KEYLINE + g)


def g_braces(pc, cx, cy, s, col):
    """Curly braces around a line node: the open-source mark."""
    c = pc.c
    c.saveState()
    c.setStrokeColor(col)
    c.setLineWidth(s * 0.085)
    c.setLineCap(1)
    c.setLineJoin(1)
    for sx in (-1, 1):
        x = cx + sx * s * 0.3
        p = c.beginPath()
        p.moveTo(x, cy + s * 0.46)
        p.curveTo(x + sx * s * 0.14, cy + s * 0.46, x + sx * s * 0.02, cy + s * 0.08, x + sx * s * 0.16, cy)
        p.curveTo(x + sx * s * 0.02, cy - s * 0.08, x + sx * s * 0.14, cy - s * 0.46, x, cy - s * 0.46)
        c.drawPath(p, stroke=1, fill=0)
    c.restoreState()
    pc.stroke_path([(cx - s * 0.2, cy), (cx + s * 0.2, cy)], DARK_BRAND, s * 0.085)
    pc.node(cx, cy, s * 0.1, DARK_BRAND, fill=col, width=s * 0.06)


def badge_layout(payload):
    """Where everything sits on a badge, computed from the circle rather than
    hand-placed: the QR panel is centred left to right and dropped as low as
    it can go while its bottom corners keep ST_RIM_MARGIN of clear colour
    inside the inner ring. Headline, glyph and tag stack above it."""
    m = ST_QR / len(qr_matrix(payload)[0])
    pad = 4 * m + 0.8                       # quiet zone plus a little air
    half = ST_QR / 2 + pad                  # half the panel's side
    drop = math.sqrt((ST_RIM - ST_RIM_MARGIN) ** 2 - half ** 2)
    panel_y = ST_CY - drop
    panel_top = panel_y + 2 * half
    head2 = panel_top + 3.3                 # second headline baseline
    head1 = head2 + 10.0 / MM * 1.06        # first headline baseline (10 pt)
    glyph_size = 9.0
    glyph_y = head1 + 2.9 + glyph_size / 2
    tag_y = ST_CY + ST_R - 8.4
    return {"pad": pad, "half": half, "panel_x": ST_CX - half, "panel_y": panel_y, "panel_top": panel_top,
            "qr_x": ST_CX - ST_QR / 2, "qr_y": panel_y + pad, "head1": head1, "glyph_y": glyph_y,
            "glyph_size": glyph_size, "glyph_top": glyph_y + glyph_size / 2, "tag_y": tag_y}


def badge(pc, col, tag, glyph_fn, rows, src, payload):
    L = badge_layout(payload)
    pc.fill_bleed(WHITE)
    pc.circle(ST_CX, ST_CY, ST_R, fill=col)
    # A quiet inner ring, like a badge rim.
    pc.circle(ST_CX, ST_CY, ST_RIM, stroke=WHITE, width=0.4)
    pc.text(ST_CX, L["tag_y"], tag, fit([tag], 6.6, 38.0, "Inter-SemiBold"), "Inter-SemiBold", WHITE, "c",
            track=0.04)
    glyph_fn(ST_CX, L["glyph_y"], L["glyph_size"])
    size = fit(rows, 10.0, 52.0)
    headline(pc, ST_CX, L["head1"], rows, size, [WHITE, WHITE], "c", leading=1.06)
    # White panel = the QR's quiet zone, so it scans on any badge colour.
    pc.rect(L["panel_x"], L["panel_y"], 2 * L["half"], 2 * L["half"], WHITE, r=2.2)
    pc.qr_code(L["qr_x"], L["qr_y"], ST_QR, src, caption=False, payload=payload)


def collectible(n, lang):
    num, code, col, glyph = SET[n - 1]
    t = COPY[lang]

    def draw(pc, src):
        label = code if code in ("M1", "M2", "M3") else (t["tram"] if code == "tram" else
                                                          t["metro"] if code == "metro" else "Syrmos")
        glyphs = {
            "anchor": lambda x, y, s: g_anchor(pc, x, y, s, WHITE),
            "temple": lambda x, y, s: g_temple(pc, x, y, s, WHITE),
            "plane": lambda x, y, s: g_plane(pc, x, y, s, WHITE),
            "wave": lambda x, y, s: g_wave(pc, x, y, s, WHITE),
            "tower": lambda x, y, s: g_tower(pc, x, y, s, WHITE, col),
            "owl": lambda x, y, s: g_owl(pc, x, y, s, WHITE, col),
        }

        badge(pc, col, f"{label}  ·  {num}/6", glyphs[glyph], t["set"][n - 1], src, guide(n, lang))
    return draw


def contributor(lang):
    """Earned, not handed out: given to people who file a bug, add a
    translation or contribute code. Never on the public sheet."""
    t = COPY[lang]
    label, rows = t["contrib"]

    def draw(pc, src):
        badge(pc, DARK, f"Syrmos  ·  {label}", lambda x, y, s: g_braces(pc, x, y, s, WHITE), rows,
              src, guide(7, lang))
    return draw


# ---------------------------------------------------------------- card (A6) and poster (A3)

# Both use the QR concepts adopted from ../concepts: the ticket for the airport
# card, the departure board for the poster. Their codes are level H with a
# styled quiet-zone-safe frame, on layered PDFs (concepts/README.md).

def _concepts():
    # build_concepts imports this module; when this file runs as a script,
    # register it under its import name so both share one copy.
    sys.modules.setdefault("build_kit", sys.modules[__name__])
    sys.path.insert(0, str(KIT.parent / "concepts"))
    import build_concepts
    return build_concepts


def layered_piece(path, w, h, cut=None):
    return _concepts().Layered(path, w, h, cut=cut)


def c1_card(lang):
    """Airport counter card: the ticket, From Syntagma To Airport, with the
    seed-calculated leave-by time as its headline and the code on the stub."""
    def draw(pc, src):
        rows, sub = COPY[lang]["card"]
        _, _, _, d = _concepts().c3_draw("card", lang, "proof", src, head_rows=rows, sub=sub)
        d(pc)
    return draw


def p1_poster(lang):
    """A3 poster: the departure board, with the code as the next departure."""
    def draw(pc, src):
        _, _, _, d = _concepts().c2_draw("poster", lang, "proof", src, q=P1_QR, extra_rows=P1_ROWS)
        d(pc)
    return draw


# A window poster is scanned from 1 to 1.5 m: 12 cm of code (10:1 rule plus margin).
P1_QR = 120.0
P1_ROWS = 2


def backing(lang):
    """Liner print for single die-cut stickers: how the QR works, tagline and
    disclaimer."""
    def draw(pc, src):
        t = COPY[lang]
        g = GUIDE[lang]
        pc.fill_bleed(WHITE)
        pc.text(ST_CX, 62.0, "Syrmos", 10, "Inter-Bold", TEXT, "c")
        pc.lines(ST_CX, 55.0, wrap(t["tagline"], 6.0, 66, "Inter-Medium"), 6.0, "Inter-Medium", SECONDARY, "c")
        pc.lines(ST_CX, 45.0, wrap(g["liner"], 6.4, 62, "Inter-SemiBold"), 6.4, "Inter-SemiBold", BRAND, "c")
        pc.text(ST_CX, 30.0, SHORT_URL, 6.4, "Inter-Medium", SECONDARY, "c")
        pc.lines(ST_CX, 20.0, wrap(t["disclaimer"], 5.2, 66), 5.2, "Inter", SECONDARY, "c")
    return draw


# ---------------------------------------------------------------- catalogue

# (id, variant, draw, w, h, cut, sides, [(src, qty)], material, min QR mm)
# min QR mm None: the piece must carry no QR. Every badge carries exactly one.
CATALOGUE = [
    *[("ST", f"{n}-{lang}", collectible(n, lang), ST_W, ST_H, st_cut, 1, [(sticker_src(n, lang), 50)],
       "Matte laminated vinyl, 8 cm round die-cut badge with white keyline", ST_QR)
      for lang in STICKER_LANGS for n in range(1, 7)],
    *[("ST", f"contrib-{lang}", contributor(lang), ST_W, ST_H, st_cut, 1, [(sticker_src(7, lang), 25)],
       "Matte laminated vinyl, 8 cm round die-cut badge; earned, not on the public sheet", ST_QR)
      for lang in STICKER_LANGS],
    *[("ST", f"backing-{lang}", backing(lang), ST_W, ST_H, None, 1, [(None, 50)],
       "Liner print behind single die-cut badges", None) for lang in STICKER_LANGS],
    *[("C1", f"airport-{lang}", c1_card(lang), 105, 148, None, 1, [(f"c1-{lang}", 25)],
       "350 g card, matte laminated, A6", 40) for lang in LANGS],
    *[("P1", f"poster-{lang}", p1_poster(lang), 297, 420, None, 1, [(f"p1-{lang}", 3)],
       "170 g silk paper, A3, indoor", P1_QR) for lang in LANGS],
]

# One kiss-cut A4 sheet per language with the whole numbered set.
A4 = (210, 297)
# Badge centres are 84 to 86 mm apart: at least 6 mm between cut lines, and
# each badge's bleed stays outside its neighbour's cut.
SHEET_SLOTS = [(21, 197), (105, 197), (21, 111), (105, 111), (21, 25), (105, 25)]
SHEETS = [(f"set-{lang}", A4, [("ST", f"{n}-{lang}", x, y) for n, (x, y) in zip(range(1, 7), SHEET_SLOTS)])
          for lang in STICKER_LANGS]


def build_sheets():
    folder = OUT / "sheets"
    folder.mkdir()
    entries = {(e[0], e[1]): e for e in CATALOGUE}
    rows = []
    for name, (sw, sh), slots in SHEETS:
        path = folder / f"sheet-{name}.pdf"
        c = pdfcanvas.Canvas(str(path), pagesize=(sw * MM, sh * MM))
        c.setTitle(path.stem)
        c.setAuthor("Syrmos")
        c.setTrimBox((0, 0, sw * MM, sh * MM))
        c.scale(MM, MM)
        lang = name.rsplit("-", 1)[1]
        for item, variant, x, y in slots:
            draw, w, h, cut, srcs = (entries[(item, variant)][i] for i in (2, 3, 4, 5, 7))
            assert x - BLEED >= 0 and y - BLEED >= 0 and x + w + BLEED <= sw and y + h + BLEED <= sh, (name, variant)
            src = srcs[0][0] or ""
            pc = Placed(c, x, y, w, h, cut=cut)
            draw(pc, src)
            pc.finish()
            rows.append({"sheet": path.name, "item": item, "variant": variant, "src": src,
                         "url": BASE_URL + src if src else "", "payload": pc.qr[0]["payload"] if pc.qr else "",
                         "x_mm": x, "y_mm": y, "trim_mm": f"{w}x{h}"})
        # The sheet margin carries the tagline and disclaimer, since a kiss-cut
        # sheet has no printable liner.
        t = COPY[lang]
        c.setFillColor(SECONDARY)
        c.setFont("Inter", 6.0 / MM)
        c.drawCentredString(sw / 2, 5.2, f"Syrmos  ·  {t['tagline']}  ·  {t['disclaimer']}")
        c.showPage()
        c.save()
    with open(OUT / "sheets.csv", "w", newline="") as f:
        wr = csv.DictWriter(f, fieldnames=list(rows[0]))
        wr.writeheader()
        wr.writerows(rows)
    return rows


def build(previews=True):
    ensure_fonts()
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir()
    rows = []
    for item, variant, draw, w, h, cut, sides, srcs, material, min_qr in CATALOGUE:
        folder = OUT / item
        folder.mkdir(exist_ok=True)
        for src, qty in srcs:
            path = folder / (f"{item}-{variant}-{src}.pdf" if src else f"{item}-{variant}.pdf")
            pc = (layered_piece if item in ("C1", "P1") else Piece)(path, w, h, cut=cut)
            draw(pc, src)
            pc.finish()
            if min_qr is None:
                assert not pc.qr, f"{path.name}: must not carry a QR"
            else:
                assert len(pc.qr) == 1, (path, pc.qr)
            for q in pc.qr:
                assert q["src"] == src, (path, q)
                assert q["size_mm"] >= min_qr, f"{path.name}: QR {q['size_mm']} mm < {min_qr} mm"
            qr = pc.qr[0] if pc.qr else {}
            rows.append({"item": item, "variant": variant, "src": src or "", "url": BASE_URL + src if src else "",
                         "payload": qr.get("payload", ""), "module_mm": qr.get("module_mm", ""),
                         "qty_run1": qty, "trim_mm": f"{w}x{h}", "sides": sides,
                         "die_cut": "yes" if cut else "no", "qr_mm": qr.get("size_mm", ""),
                         "qr_version": qr.get("version", ""), "material": material,
                         "file": str(path.relative_to(OUT))})
    with open(OUT / "manifest.csv", "w", newline="") as f:
        wr = csv.DictWriter(f, fieldnames=list(rows[0]))
        wr.writeheader()
        wr.writerows(rows)
    (OUT / "airport-example.json").write_text(json.dumps(AIRPORT, indent=2) + "\n")
    build_sheets()
    if previews:
        render_previews(rows)
    print(f"built {len(rows)} print files in {OUT.relative_to(REPO)}")
    return rows


def render_previews(rows):
    prev = OUT / "previews"
    prev.mkdir()
    firsts = []
    seen = set()
    for r in rows:
        key = (r["item"], r["variant"])
        stem = Path(r["file"]).stem
        if key in seen:
            continue
        seen.add(key)
        subprocess.run(["pdftocairo", "-png", "-r", "200", "-cropbox", str(OUT / r["file"]),
                        str(prev / stem)], check=True)
        firsts.extend(sorted(prev.glob(f"{stem}-*.png")))
    for sheet in sorted((OUT / "sheets").glob("*.pdf")):
        subprocess.run(["pdftocairo", "-png", "-r", "100", "-singlefile", str(sheet), str(prev / sheet.stem)],
                       check=True)
    if shutil.which("magick"):
        subprocess.run(["magick", "montage", *map(str, firsts), "-background", "#E9E6E1", "-geometry",
                        "520x520+18+18", "-tile", "6x", str(OUT / "contact-sheet.png")], check=True)


if __name__ == "__main__":
    build(previews="--no-previews" not in sys.argv)
