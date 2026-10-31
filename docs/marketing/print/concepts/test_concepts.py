"""Tests for the QR concepts: `python3 -m unittest docs/marketing/print/concepts/test_concepts.py`.

Builds every concept, then checks the brief's hard rules on the real files:
the proofs scan back to their URL, the quiet zone is plain light ground in
every file, sizes and logo coverage stay inside the limits, the layers and
the die-cut exist, and the required lines are printed.
"""

import csv
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import build_concepts as B  # noqa: E402

DPI = {"sticker": 300, "card": 200, "poster": 72}


def pdftext(path):
    return subprocess.run(["pdftotext", "-raw", str(path), "-"], capture_output=True, text=True,
                          check=True).stdout


def render(row, out_dir):
    stem = Path(out_dir) / Path(row["file"]).stem
    subprocess.run(["pdftocairo", "-png", "-r", str(DPI[row["format"]]), "-singlefile",
                    str(B.OUT / row["file"]), str(stem)], check=True)
    return str(stem) + ".png"


class ConceptsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        B.build(previews=False)
        with open(B.OUT / "manifest.csv") as f:
            cls.rows = list(csv.DictReader(f))

    def test_every_concept_size_language_and_mode(self):
        got = {(r["concept"], r["format"], r["lang"], r["mode"]) for r in self.rows}
        want = {(c, f, l, m) for c in B.CONCEPTS for f in B.FORMATS for l in B.LANGS
                for m in ("placeholder", "proof")}
        self.assertEqual(got, want)
        self.assertEqual(len(self.rows), 4 * 3 * 4 * 2)

    def test_src_codes_follow_the_brief(self):
        for r in self.rows:
            self.assertEqual(r["src"], f"{B.SRC_PREFIX[r['format']]}-{r['lang']}", r["file"])
            self.assertEqual(r["url"], "https://syrmos.peterdsp.dev/get-app/?src=" + r["src"])

    def test_minimum_sizes_and_logo_coverage(self):
        for r in self.rows:
            self.assertGreaterEqual(float(r["q_mm"]), B.MIN_QR[r["format"]], r["file"])
            self.assertLess(float(r["logo_cover"]), 0.15, r["file"])
        logos = {r["concept"] for r in self.rows if float(r["logo_cover"]) > 0}
        self.assertEqual(logos, {"c4-you-are-here"})

    def test_code_colours_are_dark_on_light(self):
        for r in self.rows:
            self.assertIn(r["finder_color"], {"14181F", "0F4E8C"}, r["file"])
            self.assertIn(r["ground"], {"FFFFFF", "F7F5F1"}, r["file"])

    def test_layers_and_cut_contour(self):
        for r in self.rows:
            raw = (B.OUT / r["file"]).read_bytes()
            self.assertIn(b"/OCProperties", raw, r["file"])
            for name in ("Background", "Artwork", "Text", "QR"):
                self.assertIn(f"/Name ({name})".encode(), raw, (r["file"], name))
            sticker = r["format"] == "sticker"
            self.assertEqual(b"/Name (CutContour)" in raw, sticker, r["file"])
            self.assertEqual(b"/CutContour" in raw.replace(b"/Name (CutContour)", b""), sticker, r["file"])
            self.assertEqual(r["die_cut"] == "yes", sticker, r["file"])

    def test_bleed_and_trim(self):
        for r in self.rows:
            info = subprocess.run(["pdfinfo", "-box", str(B.OUT / r["file"])], capture_output=True, text=True,
                                  check=True).stdout
            w, h = (float(v) for v in r["trim_mm"].split("x"))
            media = re.search(r"MediaBox:\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)", info)
            trim = re.search(r"TrimBox:\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)", info)
            mm = lambda pt: float(pt) / B.MM
            self.assertAlmostEqual(mm(media.group(3)), w + 6, places=1)
            self.assertAlmostEqual(mm(media.group(4)), h + 6, places=1)
            self.assertAlmostEqual(mm(trim.group(1)), 3, places=1)

    def test_required_lines(self):
        for r in self.rows:
            flat = re.sub(r"\s+", "", pdftext(B.OUT / r["file"]))
            self.assertIn(B.K.SHORT_URL, flat, r["file"])
            if r["mode"] == "placeholder":
                self.assertIn("QRPLACEHOLDER", flat, r["file"])
                self.assertIn(f"?src={r['src']}", flat, r["file"])
            else:
                self.assertNotIn("QRPLACEHOLDER", flat, r["file"])
            if r["format"] != "sticker":  # stickers carry it on the backing (brief)
                t = B.K.COPY[r["lang"]]
                self.assertIn(t["disclaimer"].replace(" ", ""), flat, r["file"])
                self.assertIn(t["tagline"].replace(" ", ""), flat, r["file"])

    def test_proofs_scan_and_placeholders_do_not(self):
        if not shutil.which("swift") or not shutil.which("pdftocairo"):
            self.skipTest("needs swift and pdftocairo (macOS)")
        with tempfile.TemporaryDirectory() as tmp:
            pngs = {render(r, tmp): r for r in self.rows}
            out = subprocess.run(["swift", str(B.K.KIT / "decode_qr.swift"), *pngs], capture_output=True,
                                 text=True, check=True).stdout
        found = {}
        for line in out.splitlines():
            path, _, payload = line.partition("\t")
            found.setdefault(path, []).append(payload)
        for png, r in pngs.items():
            want = [r["url"]] if r["mode"] == "proof" else ["NONE"]
            self.assertEqual(found.get(png), want, r["file"])

    def test_quiet_zone_is_plain_light_ground(self):
        # Art may approach the quiet zone but never enter it: every pixel in
        # the 4-module ring around the module area must be light.
        from PIL import Image
        with tempfile.TemporaryDirectory() as tmp:
            for r in self.rows:
                img = Image.open(render(r, tmp)).convert("RGB")
                px = DPI[r["format"]] / 25.4
                x, y, q, qz = (float(r[k]) for k in ("qr_x", "qr_y", "q_mm", "quiet_mm"))
                H = img.height
                to_px = lambda mx, my: (int(round((B.BLEED + mx) * px)), int(round(H - (B.BLEED + my) * px)))
                x0, y1 = to_px(x - qz, y - qz)
                x1, y0 = to_px(x + q + qz, y + q + qz)
                ix0, iy1 = to_px(x, y)
                ix1, iy0 = to_px(x + q, y + q)
                inset = 2
                worst = 255
                for yy in range(y0 + inset, y1 - inset):
                    for xx in range(x0 + inset, x1 - inset):
                        if ix0 - inset <= xx <= ix1 + inset and iy0 - inset <= yy <= iy1 + inset:
                            continue
                        worst = min(worst, min(img.getpixel((xx, yy))))
                self.assertGreaterEqual(worst, 0xE6, f"{r['file']}: art in the quiet zone ({worst})")

    def test_no_em_dash_in_sources(self):
        for f in (HERE / "build_concepts.py", HERE / "test_concepts.py", HERE / "README.md"):
            if f.exists():
                self.assertNotIn(chr(0x2014), f.read_text(), f.name)


if __name__ == "__main__":
    unittest.main()
