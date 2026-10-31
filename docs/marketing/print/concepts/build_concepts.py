#!/usr/bin/env python3
"""Build the four QR-as-artwork concepts from the print brief.

4 concepts x 3 formats (8 cm die-cut sticker, A6 card, A3 poster) x 4
languages. Every piece is written twice:

- `out/<concept>/...pdf`: the deliverable, with the code left as a marked
  placeholder square (real codes are generated separately, one per format
  and language), drawn at its true size, quiet zone and finder style.
- `out/proofs/...pdf`: the same art with a real level-H code in the concept's
  styling, so every treatment can be scan-tested on phones and in CI.

PDFs are layered (optional content groups named Background, Artwork, Text,
QR and, on stickers, CutContour), with a 3 mm bleed, TrimBox/BleedBox, and
the die-cut path stroked in the overprinting "CutContour" spot colour.

Usage: python3 docs/marketing/print/concepts/build_concepts.py [--no-previews]
"""

import csv
import math
import shutil
import subprocess
import sys
from contextlib import contextmanager
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "kit"))
import build_kit as K  # noqa: E402

from reportlab.graphics.barcode import qrencoder  # noqa: E402
from reportlab.lib.colors import HexColor  # noqa: E402
from reportlab.pdfbase import pdfdoc  # noqa: E402
from reportlab.pdfgen.canvas import FILL_EVEN_ODD  # noqa: E402

OUT = HERE / "out"
MM = K.MM
BLEED = K.BLEED

WHITE, CANVAS, TEXT, SECONDARY = K.WHITE, K.CANVAS, K.TEXT, K.SECONDARY
BRAND, BRAND_STRONG, DARK, DARK_BRAND = K.BRAND, K.BRAND_STRONG, K.DARK, K.DARK_BRAND
M1, M2, M3 = (K.line_color(x) for x in ("M1", "M2", "M3"))
TRAM = HexColor("#F39800")
HATCH = HexColor("#D9D3C8")
BOARD_TILE = HexColor("#1C232C")
BOARD_RULE = HexColor("#2A323D")
PERF = HexColor("#B9C3CF")

LANGS = K.LANGS
FORMATS = ("sticker", "card", "poster")
# Minimum printed module area per format (brief): 2.2 / 4 / 10 cm.
MIN_QR = {"sticker": 22.0, "card": 40.0, "poster": 100.0}
SRC_PREFIX = {"sticker": "st", "card": "c1", "poster": "p1"}
LAYERS = ("Background", "Artwork", "Text", "QR", "CutContour")
LOGO_MAX = 0.15  # share of the code area a centre logo may cover (level H)
LOGO_SIDE = 0.24  # logo panel side as a share of the code side: 5.8% of the area


# ---------------------------------------------------------------- copy

C = {
    "en": {"c1": "All lines lead here.", "c1s": "Metro, tram and trains. Live or offline.",
           "c2": "Your train in 3 min.", "c2n": "Next departure", "count": ("3", "min"),
           "c3": "Valid for every calm ride.", "from": "From", "to": "To", "notticket": "Not a travel ticket",
           "c4": "You are here.", "c4s": "Your next train is close.", "placeholder": "QR PLACEHOLDER"},
    "el": {"c1": "Όλες οι γραμμές οδηγούν εδώ.", "c1s": "Μετρό, τραμ και τρένα. Live ή offline.",
           "c2": "Ο συρμός σου σε 3′.", "c2n": "Επόμενη αναχώρηση", "count": ("3", "λεπ"),
           "c3": "Ισχύει για κάθε ήρεμη διαδρομή.", "from": "Από", "to": "Προς",
           "notticket": "Δεν είναι εισιτήριο",
           "c4": "Είσαι εδώ.", "c4s": "Ο επόμενος συρμός σου είναι κοντά.", "placeholder": "QR PLACEHOLDER"},
    "sq": {"c1": "Të gjitha linjat të sjellin këtu.", "c1s": "Metro, tramvaj dhe tren. Live ose offline.",
           "c2": "Treni yt vjen pas 3 min.", "c2n": "Nisja e radhës", "count": ("3", "min"),
           "c3": "E vlefshme për çdo udhëtim të qetë.", "from": "Nga", "to": "Për",
           "notticket": "Nuk është biletë udhëtimi",
           "c4": "Je këtu.", "c4s": "Treni yt i ardhshëm është afër.", "placeholder": "QR PLACEHOLDER"},
    "it": {"c1": "Tutte le linee portano qui.", "c1s": "Metro, tram e treni. Live o offline.",
           "c2": "Il tuo treno tra 3 min.", "c2n": "Prossima partenza", "count": ("3", "min"),
           "c3": "Valido per ogni viaggio tranquillo.", "from": "Da", "to": "A",
           "notticket": "Non è un titolo di viaggio",
           "c4": "Sei qui.", "c4s": "Il tuo prossimo treno è vicino.", "placeholder": "QR PLACEHOLDER"},
}


def station(sid, lang):
    s = K.STATIONS[sid]
    return s.get("nameEl", s["name"]) if lang == "el" else s["name"]


def airport(lang):
    return K.COPY[lang]["ui"]["dest"]


# ---------------------------------------------------------------- layered PDF pieces

_PROPS = {}
_orig_res_init = pdfdoc.PDFResourceDictionary.__init__


def _res_init(self, **kw):
    _orig_res_init(self, **kw)
    if _PROPS:
        self.Properties = dict(_PROPS)


pdfdoc.PDFResourceDictionary.__init__ = _res_init


class Layered(K.Piece):
    """A kit Piece whose drawing is grouped into named PDF layers (optional
    content groups), which Illustrator, Acrobat and printers' RIPs show as
    layers. The die-cut path goes into its own "CutContour" layer."""

    def __init__(self, path, w, h, *, cut=None):
        super().__init__(path, w, h, cut=cut)
        doc = self.c._doc
        names = [n for n in LAYERS if n != "CutContour" or cut]
        self.ocg = {}
        for i, name in enumerate(names):
            d = pdfdoc.PDFDictionary({"Type": pdfdoc.PDFName("OCG"), "Name": pdfdoc.PDFString(name)})
            self.ocg[name] = (f"L{i}", doc.Reference(d, f"OCG{i}"))
        refs = pdfdoc.PDFArray([r for _, r in self.ocg.values()])
        cat = doc.Catalog
        cat.OCProperties = pdfdoc.PDFDictionary(
            {"OCGs": refs, "D": pdfdoc.PDFDictionary({"Order": refs, "ON": refs,
                                                       "Name": pdfdoc.PDFString("Syrmos layers")})})
        cat.__NoDefault__ = list(cat.__NoDefault__) + ["OCProperties"]
        self.qr_info = []

    @contextmanager
    def layer(self, name):
        key = self.ocg[name][0]
        self.c._code.append(f"/OC /{key} BDC")
        try:
            yield
        finally:
            self.c._code.append("EMC")

    def draw_cut(self):
        if self.cut:
            with self.layer("CutContour"):
                super().draw_cut()

    def finish(self):
        global _PROPS
        self.draw_cut()
        self.c.showPage()
        _PROPS = {key: ref for key, ref in self.ocg.values()}
        try:
            self.c.save()
        finally:
            _PROPS = {}


# ---------------------------------------------------------------- the code

def matrix_h(url):
    for v in range(1, 21):
        try:
            q = qrencoder.QRCode(v, qrencoder.QRErrorCorrectLevel.H)
            q.addData(url)
            q.make()
        except Exception:
            continue
        n = q.getModuleCount()
        return [[bool(q.isDark(r, c)) for c in range(n)] for r in range(n)]
    raise ValueError(url)


def code_geom(q, url):
    n = len(matrix_h(url))
    m = q / n
    return n, m, 4 * m


def draw_finder(pc, x, y, m, style, color):
    """One 7x7 finder pattern with its lower left at (x, y). Restyled only as
    rounded squares (brief): ring 1 module thick, centre 3x3."""
    c = pc.c
    c.saveState()
    c.setFillColor(color)
    r_out = {"square": 0, "soft": 0.6, "node": 2.0, "roundel": 2.6}[style] * m
    r_in = {"square": 0, "soft": 0.4, "node": 1.5, "roundel": 1.5}[style] * m
    p = c.beginPath()
    p.roundRect(x, y, 7 * m, 7 * m, r_out) if r_out else p.rect(x, y, 7 * m, 7 * m)
    hole = max(0.0, r_out - m)
    p.roundRect(x + m, y + m, 5 * m, 5 * m, hole) if hole else p.rect(x + m, y + m, 5 * m, 5 * m)
    c.drawPath(p, stroke=0, fill=1, fillMode=FILL_EVEN_ODD)  # the ring, with its light hole
    p = c.beginPath()
    p.roundRect(x + 2 * m, y + 2 * m, 3 * m, 3 * m, r_in) if r_in else p.rect(x + 2 * m, y + 2 * m, 3 * m, 3 * m)
    c.drawPath(p, stroke=0, fill=1)
    c.restoreState()


def in_finder(r, col, n):
    return (r < 7 and col < 7) or (r < 7 and col >= n - 7) or (r >= n - 7 and col < 7)


def draw_code(pc, x, y, q, src, lang, mode, *, finder="square", finder_color=TEXT, logo=False, ground=WHITE):
    """The code at (x, y) with module area q mm, on a plain light quiet zone.
    mode "placeholder" draws a marked stand-in; "proof" the real level-H code."""
    url = K.BASE_URL + src
    mat = matrix_h(url)
    n = len(mat)
    m = q / n
    quiet = 4 * m
    logo_side = LOGO_SIDE * q if logo else 0.0
    lx0 = x + (q - logo_side) / 2
    ly0 = y + (q - logo_side) / 2
    with pc.layer("QR"):
        pc.rect(x - quiet, y - quiet, q + 2 * quiet, q + 2 * quiet, ground)
        if mode == "placeholder":
            c = pc.c
            c.saveState()
            p = c.beginPath()
            p.rect(x, y, q, q)
            c.clipPath(p, stroke=0, fill=0)
            c.setStrokeColor(HATCH)
            c.setLineWidth(max(0.15, q / 260))
            step = q / 22
            for i in range(-22, 23):
                c.line(x + i * step, y, x + i * step + q, y + q)
            c.restoreState()
            c.saveState()
            c.setStrokeColor(SECONDARY)
            c.setLineWidth(max(0.2, q / 200))
            c.setDash([q / 60, q / 60])
            c.rect(x, y, q, q, stroke=1, fill=0)
            c.restoreState()
        else:
            c = pc.c
            c.setFillColor(TEXT)
            p = c.beginPath()
            for r, row in enumerate(mat):
                yy = y + q - (r + 1) * m
                for col, dark in enumerate(row):
                    if not dark or in_finder(r, col, n):
                        continue
                    xx = x + col * m
                    if logo and lx0 - m < xx < lx0 + logo_side and ly0 - m < yy < ly0 + logo_side:
                        continue
                    p.rect(xx - 0.002, yy - 0.002, m + 0.004, m + 0.004)
            c.drawPath(p, stroke=0, fill=1)
        for fx, fy in ((x, y + q - 7 * m), (x + q - 7 * m, y + q - 7 * m), (x, y)):
            draw_finder(pc, fx, fy, m, finder, finder_color)
        if logo:
            pc.rect(lx0, ly0, logo_side, logo_side, WHITE, r=logo_side * 0.18)
            inset = logo_side * 0.1
            pc.logo(lx0 + inset, ly0 + inset, logo_side - 2 * inset)
        if mode == "placeholder":
            fs = max(5.0, min(18.0, q * 0.3))
            band_h = fs / MM * 4.4
            by = y + q / 2 - band_h / 2
            if logo:
                by = y + 7 * m + 0.6 * m
                band_h = min(band_h, ly0 - by - 0.6 * m)
            pc.rect(x + 7.6 * m, by, q - 15.2 * m, band_h, ground)
            fs = Kfit([C[lang]["placeholder"]], fs, q - 16.4 * m, "Inter-Bold")
            pc.text(x + q / 2, by + band_h - fs / MM * 1.15, C[lang]["placeholder"], fs, "Inter-Bold", SECONDARY, "c")
            specs = [f"?src={src}", f"H · {q / 10:g} cm"]
            fs2 = Kfit(specs, fs * 0.72, q - 16.4 * m, "Inter-Medium")
            pc.lines(x + q / 2, by + band_h - fs / MM * 1.15 - fs2 / MM * 1.3, specs, fs2, "Inter-Medium", SECONDARY,
                     "c", leading=1.15)
    covered = (logo_side ** 2) / (q * q) if logo else 0.0
    assert covered < LOGO_MAX, covered
    # Also in the print kit's own format, for kit pieces built from a concept.
    pc.qr.append({"src": src, "url": url, "payload": url, "size_mm": round(q, 2), "modules": n,
                  "version": (n - 17) // 4, "module_mm": round(m, 3)})
    pc.qr_info.append({"src": src, "url": url, "q_mm": round(q, 3), "modules": n, "module_mm": round(m, 4),
                       "x": x, "y": y, "quiet_mm": round(quiet, 4), "finder": finder,
                       "finder_color": finder_color.hexval()[2:].upper(), "ground": ground.hexval()[2:].upper(),
                       "logo_cover": round(covered, 4)})
    return quiet


def Kfit(rows, size, width, font):
    return max(5.0, K.fit(rows, size, width, font))


def caption(pc, cx, y, size):
    with pc.layer("Text"):
        pc.text(cx, y, K.SHORT_URL, size, "Inter-Medium", SECONDARY, "c")


def small_print(pc, x, y, lang, size, width, color=SECONDARY, anchor="l"):
    t = K.COPY[lang]
    with pc.layer("Text"):
        y = pc.lines(x, y, K.wrap(t["tagline"], size, width, "Inter-Medium"), size, "Inter-Medium", color, anchor,
                     leading=1.2)
        pc.lines(x, y - size / MM * 0.3, K.wrap(t["disclaimer"], max(5.0, size * 0.9), width), max(5.0, size * 0.9),
                 "Inter", color, anchor, leading=1.15)


def wrap_head(pc, x, y, text, size, width, color=TEXT, anchor="l", font="Inter-Bold", leading=1.08):
    rows = K.wrap(text, size, width, font)
    while len(rows) > 3 and size > 6:
        size -= 0.5
        rows = K.wrap(text, size, width, font)
    with pc.layer("Text"):
        return pc.lines(x, y, rows, size, font, color, anchor, leading=leading)


def brand(pc, x, y, size, color=TEXT):
    # Same geometry as the kit's brand_row, in the Text layer.
    with pc.layer("Text"):
        pc.logo(x, y - size * 0.62 / MM * 1.6, size / MM * 1.6)
        pc.text(x + size / MM * 2.0, y - size * 0.38 / MM, "Syrmos", size, "Inter-Bold", color)


# ---------------------------------------------------------------- concept 1: destination station

C1_FMT = {
    "sticker": dict(w=80, h=80, cx=40, cy=35.5, q=22, sw=2.2, d_out=14, d_in=5, head=(40, 66.5, 9.0, 42)),
    "card": dict(w=105, h=148, cx=52.5, cy=64, q=40, sw=3.2, d_out=30, d_in=11, head=(8, 120, 19, 89)),
    "poster": dict(w=297, h=420, cx=148.5, cy=176, q=100, sw=7.0, d_out=78, d_in=28, head=(20, 338, 54, 257)),
}


def c1_draw(fmt, lang, mode, src):
    f = C1_FMT[fmt]

    def draw(pc):
        w, h, cx, cy, q, sw = f["w"], f["h"], f["cx"], f["cy"], f["q"], f["sw"]
        n, m, quiet = code_geom(q, K.BASE_URL + src)
        hq = q / 2 + quiet
        rs = sw * 0.75
        gap = max(sw / 2, rs / 2) + sw * 0.35
        ring = hq + gap
        with pc.layer("Background"):
            pc.fill_bleed(CANVAS)
        lines = [(M1, -1, +1), (M2, -1, -1), (M3, +1, +1), (TRAM, +1, -1)]
        with pc.layer("Artwork"):
            for col, side, vs in lines:
                ex = cx + side * ring
                ey = cy + vs * f["d_in"]
                dy = f["d_out"] - f["d_in"]
                bx = ex + side * sw * 2.2
                ox = bx + side * dy
                oy = cy + vs * f["d_out"]
                far = -BLEED - 2 if side < 0 else w + BLEED + 2
                pts = [(far, oy), (ox, oy), (bx, ey), (ex, ey)]
                pc.stroke_path(pts, col, sw)
                for px, py in ((ox + side * sw * 3.4, oy), ((ox + bx) / 2, (oy + ey) / 2)):
                    pc.node(px, py, sw * 0.62, col, fill=WHITE, width=sw * 0.42)
            # Interchange ring around the quiet zone: the code is the destination.
            c = pc.c
            c.saveState()
            c.setStrokeColor(BRAND_STRONG)
            c.setLineWidth(rs)
            c.setFillColor(WHITE)
            c.roundRect(cx - ring, cy - ring, 2 * ring, 2 * ring, sw * 2.2, stroke=1, fill=1)
            c.restoreState()
        draw_code(pc, cx - q / 2, cy - q / 2, q, src, lang, mode, finder="node", finder_color=BRAND_STRONG)
        hx, hy, hs, hw = f["head"]
        t = C[lang]
        if fmt == "sticker":
            wrap_head(pc, hx, hy, t["c1"], hs, hw, anchor="c")
            caption(pc, cx, cy - ring - rs - 3.4, 5.4)
            with pc.layer("Text"):
                pc.text(cx, 8.6, "Syrmos", 6.4, "Inter-Bold", BRAND_STRONG, "c", track=0.04)
        else:
            k = 1 if fmt == "card" else 2.6
            brand(pc, hx, h - 9 * k, 9 * k)
            end = wrap_head(pc, hx, hy, t["c1"], hs, hw)
            with pc.layer("Text"):
                pc.lines(hx, end - 1.5 * k, K.wrap(t["c1s"], 8.4 * k, hw, "Inter-Medium"), 8.4 * k, "Inter-Medium",
                         SECONDARY)
            caption(pc, cx, cy - ring - rs - 4.2 * k, 6.6 * k)
            with pc.layer("Text"):
                pc.text(cx, cy - ring - rs - 9.6 * k, K.COPY[lang]["scan"], 7.6 * k, "Inter-Bold", BRAND, "c")
            small_print(pc, hx, 13.0 * k, lang, 5.6 * k, w - 2 * hx)
    return f["w"], f["h"], (circle_cut(f["w"]) if fmt == "sticker" else None), draw


def circle_cut(w):
    def cut(p, g=0.0):
        p.circle(w / 2, w / 2, w / 2 - 1.0 + g)
    return cut


# ---------------------------------------------------------------- concept 2: departure board tile

def flip_digit(pc, x, y, w, h, ch, size):
    pc.rect(x, y, w, h, BOARD_TILE, r=min(w, h) * 0.12)
    pc.text(x + w / 2, y + h / 2 - size / MM * 0.36, ch, size, "InterDisplay-Bold", WHITE, "c")
    c = pc.c
    c.saveState()
    c.setStrokeColor(DARK)
    c.setLineWidth(max(0.25, h * 0.025))
    c.line(x, y + h / 2, x + w, y + h / 2)
    c.restoreState()


C2_FMT = {
    "sticker": dict(w=80, h=80, q=22),
    "card": dict(w=105, h=148, q=40),
    "poster": dict(w=297, h=420, q=100),
}


def board_row(pc, x, y, w, code, col, dest, mins, unit, size):
    """A non-featured board row: line chip, destination, minutes."""
    ch = size / MM * 1.5
    cw = K.Piece.width(code, size, "Inter-Bold") + size / MM * 1.0
    pc.rect(x, y, cw, ch, col, r=ch * 0.22)
    pc.text(x + cw / 2, y + ch * 0.27, code, size, "Inter-Bold", WHITE, "c")
    pc.text(x + cw + size / MM * 0.9, y + ch * 0.27, dest, size, "Inter-SemiBold", WHITE)
    pc.text(x + w, y + ch * 0.27, f"{mins} {unit}", size, "Inter-Bold", DARK_BRAND, "r")


def c2_draw(fmt, lang, mode, src, *, q=None, extra_rows=None):
    """`q` and `extra_rows` (board rows below the code on the poster) let the print kit use
    this design at its own code size."""
    f = dict(C2_FMT[fmt], **({"q": q} if q else {}))
    t = C[lang]
    num, unit = t["count"]

    def draw(pc):
        w, h, q = f["w"], f["h"], f["q"]
        n, m, quiet = code_geom(q, K.BASE_URL + src)
        qb = q + 2 * quiet
        if fmt == "sticker":
            with pc.layer("Background"):
                pc.fill_bleed(DARK)
            with pc.layer("Artwork"):
                for yy in (55.0, 20.5):
                    pc.stroke_path([(5, yy), (75, yy)], BOARD_RULE, 0.35)
            with pc.layer("Text"):
                pc.text(6, 69.0, t["c2n"].upper(), 5.2, "Inter-SemiBold", DARK_BRAND, track=0.06)
                pc.logo(68.0, 66.4, 6.0)
                board_row(pc, 6, 58.4, 56, "M3", M3, airport(lang), "", "", 8.0)
            tx, ty = 5.0, 9.0
            tile_h = qb + 7.0
            # A light margin beyond the quiet zone keeps the dark board out of
            # the scanner's thresholding window.
            with pc.layer("Artwork"):
                pc.rect(tx, ty, qb + 3.0, tile_h, WHITE, r=1.6)
            draw_code(pc, tx + 1.5 + quiet, ty + 5.5 + quiet, q, src, lang, mode)
            caption(pc, tx + 1.5 + qb / 2, ty + 2.0, 5.0)
            dx = tx + qb + 6.5
            with pc.layer("Artwork"):
                flip_digit(pc, dx, 25.0, 17.0, 26.0, num, 46)
            with pc.layer("Text"):
                us = Kfit([unit], 15, 80 - dx - 21.0, "Inter-Bold")
                pc.text(dx + 19.5, 27.5, unit, us, "Inter-Bold", DARK_BRAND)
                pc.text(dx + 19.5, 41.0, "→", 15, "Inter-Bold", DARK_BRAND)
            return
        k = 1 if fmt == "card" else 2.6
        mx = 6.0 * k if fmt == "card" else 20.0
        with pc.layer("Background"):
            pc.fill_bleed(CANVAS)
        brand(pc, mx + 2 * (k == 1), h - 9 * k, 9 * k)
        end = wrap_head(pc, mx + 2 * (k == 1), h - 26 * k, t["c2"], 20 * k, w - 2 * mx)
        bx, bw = mx, w - 2 * mx
        top = end - 4 * k
        pad = 5.0 * k
        tile_w, tile_h = qb + 2.0 * k, qb + 7.0 * k
        extra = (3 if fmt == "poster" else 0) if extra_rows is None else extra_rows
        row_h = 9.5 * k if fmt == "card" else 8.0 * k
        bottom = top - (pad + 14.5 * k + tile_h + pad + extra * row_h + (2 * k if extra else 0))
        # The small print's first line sits at 13k with its cap height above it.
        assert bottom > 18 * k, f"board runs into the small print ({bottom:.1f} mm)"
        with pc.layer("Artwork"):
            K.shadow(pc, bx, bottom, bw, top - bottom, 4 * k, k=k)
            pc.rect(bx, bottom, bw, top - bottom, DARK, r=4 * k)
        with pc.layer("Text"):
            pc.text(bx + pad, top - pad - 3.0 * k, t["c2n"].upper(), 5.6 * k, "Inter-SemiBold", DARK_BRAND, track=0.06)
            board_row(pc, bx + pad, top - pad - 11.0 * k, bw - 2 * pad, "M3", M3, airport(lang), "", "", 8.6 * k)
        ty = top - pad - 14.5 * k - tile_h
        with pc.layer("Artwork"):
            pc.stroke_path([(bx + pad, top - pad - 13.0 * k), (bx + bw - pad, top - pad - 13.0 * k)], BOARD_RULE,
                           0.35 * k)
            pc.rect(bx + pad, ty, tile_w, tile_h, WHITE, r=1.8 * k)
        draw_code(pc, bx + pad + 1.0 * k + quiet, ty + 7.0 * k - 1.0 * k + quiet, q, src, lang, mode)
        caption(pc, bx + pad + tile_w / 2, ty + 2.3 * k, 6.0 * k)
        col_x = bx + pad + tile_w + 4 * k
        col_w = bx + bw - pad - col_x
        dw, dh = min(col_w, 22 * k), 28 * k
        with pc.layer("Artwork"):
            flip_digit(pc, col_x + (col_w - dw) / 2, ty + tile_h - dh, dw, dh, num, 54 * k)
        with pc.layer("Text"):
            us = Kfit([unit], 16 * k, col_w, "Inter-Bold")
            uy = ty + tile_h - dh - 7.0 * k
            pc.text(col_x + col_w / 2, uy, unit, us, "Inter-Bold", DARK_BRAND, "c")
            scan = K.COPY[lang]["scan"]
            rows = K.wrap(scan, 6.6 * k, col_w, "Inter-SemiBold")
            pc.lines(col_x + col_w / 2, uy - 7.0 * k, rows, 6.6 * k, "Inter-SemiBold", WHITE, "c", leading=1.15)
            if extra:
                ry = ty - pad - row_h * 0.6
                board = (("M1", M1, "M1_KIF", 7), ("M2", M2, "M2_ELL", 9), ("T6", TRAM, "T6_PIK", 12))
                for code, col, sid, mins in board[:extra]:
                    pc.stroke_path([(bx + pad, ry + row_h * 0.8), (bx + bw - pad, ry + row_h * 0.8)], BOARD_RULE,
                                   0.35 * k)
                    board_row(pc, bx + pad, ry - 1.0 * k, bw - 2 * pad, code, col, station(sid, lang), mins, unit,
                              8.6 * k)
                    ry -= row_h
        small_print(pc, mx + 2 * (k == 1), 13.0 * k, lang, 5.6 * k, w - 2 * mx)
    cut = None
    if fmt == "sticker":
        def cut(p, g=0.0):
            p.roundRect(1.0 - g, 1.0 - g, 78.0 + 2 * g, 78.0 + 2 * g, 7.0 + g)
    return f["w"], f["h"], cut, draw


# ---------------------------------------------------------------- concept 3: ticket with a stub

def ticket_path(p, x, y, w, h, perf, r, notch, vertical, g=0.0):
    """Ticket outline with two half-circle notches at the perforation.
    Landscape: perforation at x = perf; vertical: at y = perf."""
    x -= g
    y -= g
    w += 2 * g
    h += 2 * g
    rn = notch - g
    steps = 16
    pts = []

    def arc(cx_, cy_, a0, a1, rad):
        for i in range(steps + 1):
            a = math.radians(a0 + (a1 - a0) * i / steps)
            pts.append((cx_ + rad * math.cos(a), cy_ + rad * math.sin(a)))

    rr = r + g
    if not vertical:
        arc(x + rr, y + rr, 180, 270, rr)
        arc(perf, y, 180, 0, rn)  # bottom notch
        arc(x + w - rr, y + rr, 270, 360, rr)
        arc(x + w - rr, y + h - rr, 0, 90, rr)
        arc(perf, y + h, 0, -180, rn)  # top notch
        arc(x + rr, y + h - rr, 90, 180, rr)
    else:
        arc(x + rr, y + rr, 180, 270, rr)
        arc(x + w - rr, y + rr, 270, 360, rr)
        arc(x + w, perf, 270, 90, rn)  # right notch
        arc(x + w - rr, y + h - rr, 0, 90, rr)
        arc(x + rr, y + h - rr, 90, 180, rr)
        arc(x, perf, 90, -90, rn)  # left notch
    p.moveTo(*pts[0])
    for pt in pts[1:]:
        p.lineTo(*pt)
    p.close()


C3_FMT = {
    "sticker": dict(w=80, h=50, q=22),
    "card": dict(w=105, h=148, q=40),
    "poster": dict(w=297, h=420, q=100),
}


def perforation(pc, x0, y0, x1, y1, k):
    c = pc.c
    c.saveState()
    c.setStrokeColor(PERF)
    c.setLineWidth(0.4 * k)
    c.setLineCap(1)
    c.setDash([0.01, 1.4 * k])
    c.line(x0, y0, x1, y1)
    c.restoreState()


def c3_draw(fmt, lang, mode, src, *, head_rows=None, sub=None):
    """`head_rows` (two-tone, white then light blue) and `sub` replace the
    concept headline: the print kit's airport card puts its leave-by line here."""
    f = C3_FMT[fmt]
    t = C[lang]

    def route(pc, x, y, size, color, sub):
        """From / To block: Syntagma to the Airport on M3."""
        lead = size / MM
        label = max(5.0, size * 0.62)
        pc.text(x, y, t["from"].upper(), label, "Inter-SemiBold", sub, track=0.08)
        pc.text(x, y - lead * 1.25, station("M3_SYN", lang), size, "Inter-Bold", color)
        pc.text(x, y - lead * 2.6, t["to"].upper(), label, "Inter-SemiBold", sub, track=0.08)
        pc.text(x, y - lead * 3.85, airport(lang), size, "Inter-Bold", color)

    def chip(pc, x, y, size):
        ch = size / MM * 1.5
        cw = K.Piece.width("M3", size, "Inter-Bold") + size / MM * 1.0
        pc.rect(x, y, cw, ch, WHITE, r=ch * 0.22)
        pc.text(x + cw / 2, y + ch * 0.27, "M3", size, "Inter-Bold", M3, "c")

    def draw(pc):
        w, h, q = f["w"], f["h"], f["q"]
        n, m, quiet = code_geom(q, K.BASE_URL + src)
        qb = q + 2 * quiet
        if fmt == "sticker":
            perf = 48.0
            with pc.layer("Background"):
                pc.fill_bleed(WHITE)
                pc.rect(-BLEED, -BLEED, perf + BLEED, h + 2 * BLEED, BRAND)
            with pc.layer("Artwork"):
                perforation(pc, perf, 3.6, perf, h - 3.6, 1)
            with pc.layer("Text"):
                pc.text(5, 42.6, "SYRMOS", 5.6, "Inter-Bold", WHITE, track=0.12)
                chip(pc, 35.5, 41.6, 6.0)
                route(pc, 5, 35.0, 7.6, WHITE, K.ICE)
                pc.lines(5, 12.2, K.wrap(t["c3"], 5.6, 40, "Inter-SemiBold"), 5.6, "Inter-SemiBold", WHITE,
                         leading=1.15)
                pc.text(5, 4.4, t["notticket"], 5.0, "Inter", K.ICE)
            cx = perf + (w - perf) / 2
            draw_code(pc, cx - q / 2, 15.6, q, src, lang, mode, finder="soft")
            caption(pc, cx, 7.0, 5.0)
            return
        k = 1 if fmt == "card" else 2.7
        mx = 7.0 if fmt == "card" else 20.0
        # Tagline and disclaimer sit on the card under the ticket.
        tx, ty, tw = mx, mx + 7.5 * k, w - 2 * mx
        th = h - mx - ty
        stub_h = qb + 12.0 * k
        perf = ty + stub_h
        with pc.layer("Background"):
            pc.fill_bleed(CANVAS)
        with pc.layer("Artwork"):
            K.shadow(pc, tx, ty, tw, th, 4 * k, k=k)
            c = pc.c
            c.saveState()
            p = c.beginPath()
            ticket_path(p, tx, ty, tw, th, perf, 4 * k, 5 * k, True)
            c.clipPath(p, stroke=0, fill=0)
            pc.rect(tx - 1, ty - 1, tw + 2, th + 2, WHITE)
            pc.rect(tx - 1, perf, tw + 2, th - stub_h + 1, BRAND)
            c.restoreState()
            perforation(pc, tx + 6 * k, perf, tx + tw - 6 * k, perf, k)
        ix = tx + 7 * k
        iw = tw - 14 * k
        top = ty + th - 9 * k
        with pc.layer("Text"):
            pc.text(ix, top, "SYRMOS", 6.4 * k, "Inter-Bold", WHITE, track=0.12)
            pc.logo(tx + tw - 7 * k - 9 * k, top - 3.4 * k, 9 * k)
            chip(pc, ix, top - 11.0 * k, 8.0 * k)
        if head_rows:
            size = Kfit(head_rows, 17 * k, iw, "Inter-Bold")
            with pc.layer("Text"):
                end = K.headline(pc, ix, top - 19.5 * k, head_rows, size, [WHITE, DARK_BRAND])
                if sub:
                    end = pc.lines(ix, end - 0.6 * k, K.wrap(sub, 7.2 * k, iw, "Inter-Medium"), 7.2 * k,
                                   "Inter-Medium", K.ICE, leading=1.2)
        else:
            end = wrap_head(pc, ix, top - 18.5 * k, t["c3"], 13 * k, iw, color=WHITE)
        with pc.layer("Text"):
            rs_ = 9.5 * k
            lead = rs_ / MM
            label = 5.6 * k
            ry = end - 3.0 * k
            for col_x, lab, val in ((ix, t["from"], station("M3_SYN", lang)),
                                    (ix + iw / 2, t["to"], airport(lang))):
                pc.text(col_x, ry, lab.upper(), label, "Inter-SemiBold", K.ICE, track=0.08)
                vs = Kfit([val], rs_, iw / 2 - 2 * k, "Inter-Bold")
                pc.text(col_x, ry - lead * 1.3, val, vs, "Inter-Bold", WHITE)
            pc.text(ix + iw / 2 - 4.2 * k, ry - lead * 1.3, "→", rs_, "Inter-Bold", K.ICE, "c")
            scan = K.COPY[lang]["scan"] + "  ↓"
            pc.text(ix, perf + 5.5 * k, scan, Kfit([scan], 8 * k, iw, "Inter-Bold"), "Inter-Bold", WHITE)
            assert ry - lead * 1.3 - 3 * k > perf + 10.5 * k, "ticket main panel overflows"
        cx = tx + tw / 2
        draw_code(pc, cx - q / 2, ty + 8.0 * k + quiet, q, src, lang, mode, finder="soft")
        caption(pc, cx, ty + 3.6 * k, 6.4 * k)
        # "Not a travel ticket" leads the small print: nobody should try it at a gate.
        with pc.layer("Text"):
            pc.text(mx, mx + 4.6 * k, t["notticket"], 5.6 * k, "Inter-SemiBold", TEXT)
        small_print(pc, mx, mx + 2.0 * k, lang, 5.0 * k, w - 2 * mx)
    cut = None
    if fmt == "sticker":
        def cut(p, g=0.0):
            ticket_path(p, 1.0, 1.0, 78.0, 48.0, 48.0, 4.0, 3.0, False, g)
    return f["w"], f["h"], cut, draw


# ---------------------------------------------------------------- concept 4: ring line, you are here

C4_FMT = {
    "sticker": dict(w=80, h=80, cx=40, cy=37.5, q=22, sw=1.9),
    "card": dict(w=105, h=148, cx=52.5, cy=62, q=40, sw=2.8),
    "poster": dict(w=297, h=420, cx=148.5, cy=170, q=100, sw=6.4),
}


def c4_draw(fmt, lang, mode, src):
    f = C4_FMT[fmt]
    t = C[lang]

    def draw(pc):
        w, h, cx, cy, q, sw = f["w"], f["h"], f["cx"], f["cy"], f["q"], f["sw"]
        n, m, quiet = code_geom(q, K.BASE_URL + src)
        hq = q / 2 + quiet
        # Two modules of extra light ground beyond the quiet zone before the
        # ring: a coloured frame close to the code costs scans.
        g = 1.3 * sw + 2 * m
        L = hq + g
        node_r = 1.1 * sw
        halo_r = 1.75 * sw
        assert node_r <= g * math.sqrt(2) and halo_r <= g * math.sqrt(2) and sw / 2 <= g
        with pc.layer("Background"):
            pc.fill_bleed(WHITE if fmt == "sticker" else CANVAS)
        with pc.layer("Artwork"):
            # A ring line around the quiet zone, one side per line colour;
            # stations sit outside the three finder corners.
            TL, TR, BR, BL = (cx - L, cy + L), (cx + L, cy + L), (cx + L, cy - L), (cx - L, cy - L)
            pc.stroke_path([TL, TR], M1, sw)
            pc.stroke_path([TR, BR], M2, sw)
            pc.stroke_path([BR, BL], M3, sw)
            pc.stroke_path([BL, TL], TRAM, sw)
            # Nodes take a line colour, not near-black: a dark ring next to a
            # finder pattern reads to a scanner like a fourth finder.
            for pt, col in ((TL, M1), (TR, M2), (BL, TRAM)):
                pc.node(*pt, node_r, col, fill=WHITE, width=sw * 0.55)
            c = pc.c
            c.saveState()
            c.setFillColor(BRAND)
            c.setFillAlpha(0.22)
            c.circle(*BR, halo_r, stroke=0, fill=1)
            c.restoreState()
            pc.circle(*BR, sw * 0.95, fill=BRAND, stroke=WHITE, width=sw * 0.35)
        draw_code(pc, cx - q / 2, cy - q / 2, q, src, lang, mode, finder="roundel", logo=True)
        if fmt == "sticker":
            wrap_head(pc, cx, cy + L + 9.6, t["c4"], 10.5, 58, anchor="c")
            caption(pc, cx, cy - L - sw - 3.2, 5.2)
            with pc.layer("Text"):
                pc.text(cx, 8.8, "Syrmos", 6.4, "Inter-Bold", BRAND_STRONG, "c", track=0.04)
            return
        k = 1 if fmt == "card" else 2.6
        mx = 8.0 if fmt == "card" else 20.0
        brand(pc, mx, h - 9 * k, 9 * k)
        end = wrap_head(pc, mx, h - 30 * k, t["c4"], 24 * k, w - 2 * mx)
        with pc.layer("Text"):
            pc.lines(mx, end - 1.0 * k, K.wrap(t["c4s"], 9 * k, w - 2 * mx, "Inter-Medium"), 9 * k, "Inter-Medium",
                     SECONDARY)
        caption(pc, cx, cy - L - sw - 4.6 * k, 6.6 * k)
        with pc.layer("Text"):
            pc.text(cx, cy - L - sw - 10.2 * k, K.COPY[lang]["scan"], 7.6 * k, "Inter-Bold", BRAND, "c")
        small_print(pc, mx, 13.0 * k, lang, 5.6 * k, w - 2 * mx)
    return f["w"], f["h"], (circle_cut(f["w"]) if fmt == "sticker" else None), draw


CONCEPTS = {
    "c1-destination": c1_draw,
    "c2-departure-board": c2_draw,
    "c3-ticket": c3_draw,
    "c4-you-are-here": c4_draw,
}


# ---------------------------------------------------------------- build

def build(previews=True):
    K.ensure_fonts()
    if OUT.exists():
        shutil.rmtree(OUT)
    rows = []
    for name, fn in CONCEPTS.items():
        for fmt in FORMATS:
            for lang in LANGS:
                src = f"{SRC_PREFIX[fmt]}-{lang}"
                for mode in ("placeholder", "proof"):
                    w, h, cut, draw = fn(fmt, lang, mode, src)
                    folder = OUT / (name if mode == "placeholder" else "proofs")
                    folder.mkdir(parents=True, exist_ok=True)
                    stem = f"{name}-{fmt}-{lang}" + ("-proof" if mode == "proof" else "")
                    pc = Layered(folder / f"{stem}.pdf", w, h, cut=cut)
                    draw(pc)
                    pc.finish()
                    assert len(pc.qr_info) == 1, stem
                    qi = pc.qr_info[0]
                    assert qi["q_mm"] >= MIN_QR[fmt], (stem, qi)
                    rows.append({"concept": name, "format": fmt, "lang": lang, "mode": mode, "src": src,
                                 "url": qi["url"], "trim_mm": f"{w}x{h}", "die_cut": "yes" if cut else "no",
                                 "q_mm": qi["q_mm"], "modules": qi["modules"], "module_mm": qi["module_mm"],
                                 "quiet_mm": qi["quiet_mm"], "qr_x": round(qi["x"], 3), "qr_y": round(qi["y"], 3),
                                 "finder": qi["finder"], "finder_color": qi["finder_color"],
                                 "ground": qi["ground"], "logo_cover": qi["logo_cover"],
                                 "file": str((folder / f"{stem}.pdf").relative_to(OUT))})
    with open(OUT / "manifest.csv", "w", newline="") as fh:
        wr = csv.DictWriter(fh, fieldnames=list(rows[0]))
        wr.writeheader()
        wr.writerows(rows)
    if previews:
        render_previews(rows)
    print(f"built {len(rows)} files in {OUT.relative_to(K.REPO)}")
    return rows


def render_previews(rows):
    prev = OUT / "previews"
    prev.mkdir()
    for r in rows:
        if r["mode"] != "placeholder":
            continue
        stem = Path(r["file"]).stem
        dpi = "300" if r["format"] == "sticker" else ("150" if r["format"] == "card" else "60")
        subprocess.run(["pdftocairo", "-png", "-r", dpi, "-cropbox", "-singlefile", str(OUT / r["file"]),
                        str(prev / stem)], check=True)
    if not shutil.which("magick"):
        return
    # One board per concept: the three formats in English, then the sticker in every language.
    for name in CONCEPTS:
        tiles = [str(prev / f"{name}-{fmt}-en.png") for fmt in FORMATS]
        tiles += [str(prev / f"{name}-sticker-{lang}.png") for lang in LANGS[1:]]
        subprocess.run(["magick", "montage", *tiles, "-background", "#E9E6E1", "-geometry", "560x560+16+16",
                        "-tile", "6x", str(OUT / f"board-{name}.png")], check=True)


if __name__ == "__main__":
    build(previews="--no-previews" not in sys.argv)
