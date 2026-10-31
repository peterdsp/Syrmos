"""Tests for the print kit: run with `python3 -m unittest docs/marketing/print/kit/test_kit.py`.

Builds the kit, then checks every PDF the way a proof check would: the QR
scans back to its own payload (a URL, or a sticker's offline guide), the src codes match the README table, the
required lines are printed, and the print boxes and cut line are present.
"""

import csv
import json
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

KIT = Path(__file__).resolve().parent
sys.path.insert(0, str(KIT))
import build_kit  # noqa: E402

README = KIT.parent / "README.md"


def pdftext(path):
    # -raw keeps content-stream order, so wrapped lines in side-by-side
    # columns are not interleaved.
    return subprocess.run(["pdftotext", "-raw", str(path), "-"], capture_output=True, text=True,
                          check=True).stdout


class PrintKitTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rows = build_kit.build(previews=False)
        with open(build_kit.OUT / "manifest.csv") as f:
            cls.manifest = list(csv.DictReader(f))

    def test_manifest_lists_every_built_file(self):
        self.assertEqual(len(self.manifest), len(self.rows))
        for r in self.manifest:
            self.assertTrue((build_kit.OUT / r["file"]).exists(), r["file"])

    def test_src_codes_follow_the_scheme(self):
        for r in self.manifest:
            if not r["src"]:
                continue  # liner prints carry no QR
            self.assertRegex(r["src"], r"^[a-z0-9-]{1,9}$")
            self.assertTrue(r["src"].startswith(r["item"].lower()), r)
            self.assertEqual(r["url"], "https://syrmos.peterdsp.dev/get-app/?src=" + r["src"])

    def test_src_codes_match_the_readme_table(self):
        table = README.read_text().split("## Assets", 1)[1].split("### Order summary", 1)[0]
        documented = set()
        for line in table.splitlines():
            cells = line.split("|")
            if len(cells) > 3 and re.match(r"\s*(ST|[SCP]\d+)\s*$", cells[1]):
                documented |= set(re.findall(r"`([a-z0-9-]+)`", cells[-2]))
        with open(build_kit.OUT / "sheets.csv") as f:
            sheet_codes = {r["src"] for r in csv.DictReader(f)}
        built = ({r["src"] for r in self.manifest} | sheet_codes) - {""}
        self.assertEqual(built, documented)

    @staticmethod
    def expected(r):
        # The decoder prints NONE for no code and escapes payload newlines.
        return [r["payload"].replace("\n", "\\n")] if r["payload"] else ["NONE"]

    def test_every_qr_scans_to_its_own_payload(self):
        if not shutil.which("swift") or not shutil.which("pdftocairo"):
            self.skipTest("needs swift and pdftocairo (macOS)")
        with tempfile.TemporaryDirectory() as tmp:
            pngs = {}
            for r in self.manifest:
                stem = Path(tmp) / Path(r["file"]).stem
                # Sticker guides have 0.34 mm modules: render them fine enough
                # (4 px a module) that the decoder, like a phone, can resolve them.
                dpi = "300" if r["item"] == "ST" else "150"
                subprocess.run(["pdftocairo", "-png", "-r", dpi, "-f", "1", "-l", "1", "-singlefile",
                                str(build_kit.OUT / r["file"]), str(stem)], check=True)
                pngs[str(stem) + ".png"] = r
            out = subprocess.run(["swift", str(KIT / "decode_qr.swift"), *pngs], capture_output=True,
                                 text=True, check=True).stdout
        decoded = {}
        for line in out.splitlines():
            path, _, payload = line.partition("\t")
            decoded.setdefault(path, []).append(payload)
        for png, r in pngs.items():
            self.assertEqual(decoded.get(png), self.expected(r), r["file"])

    def test_every_sticker_on_a_sheet_scans(self):
        if not shutil.which("swift") or not shutil.which("pdftocairo"):
            self.skipTest("needs swift and pdftocairo (macOS)")
        with open(build_kit.OUT / "sheets.csv") as f:
            rows = list(csv.DictReader(f))
        from PIL import Image
        dpi = 200
        px = dpi / 25.4
        with tempfile.TemporaryDirectory() as tmp:
            pages, crops = {}, {}
            for sheet in {r["sheet"] for r in rows}:
                stem = Path(tmp) / Path(sheet).stem
                subprocess.run(["pdftocairo", "-png", "-r", str(dpi), "-singlefile",
                                str(build_kit.OUT / "sheets" / sheet), str(stem)], check=True)
                pages[sheet] = Image.open(str(stem) + ".png")
            # Scan each sticker on its own, cropped from the sheet at its
            # position, so every code is proven where it is printed.
            for i, r in enumerate(rows):
                page = pages[r["sheet"]]
                w, h = (float(v) for v in r["trim_mm"].split("x"))
                x, y = float(r["x_mm"]), float(r["y_mm"])
                box = (int(x * px), int(page.height - (y + h) * px), int((x + w) * px), int(page.height - y * px))
                path = str(Path(tmp) / f"crop-{i}.png")
                page.crop(box).save(path)
                crops[path] = r
            out = subprocess.run(["swift", str(KIT / "decode_qr.swift"), *crops], capture_output=True,
                                 text=True, check=True).stdout
        found = {}
        for line in out.splitlines():
            path, _, payload = line.partition("\t")
            found.setdefault(path, []).append(payload)
        for path, r in crops.items():
            self.assertNotEqual(r["payload"], "", r)
            self.assertEqual(found.get(path), self.expected(r), (r["sheet"], r["variant"]))

    def test_sheet_stickers_have_their_own_cut_lines(self):
        with open(build_kit.OUT / "sheets.csv") as f:
            rows = list(csv.DictReader(f))
        for sheet in {r["sheet"] for r in rows}:
            raw = (build_kit.OUT / "sheets" / sheet).read_bytes()
            self.assertIn(b"/CutContour", raw, sheet)

    def test_qr_sizes_meet_the_minimums(self):
        minimum = {"S": 28, "C": 40, "P": 120}
        for r in self.manifest:
            if not r["src"]:
                continue
            self.assertGreaterEqual(float(r["qr_mm"]), minimum[r["item"][0]], r["file"])

    def test_sticker_qr_modules_stay_printable(self):
        # A guide is far denser than a short URL; keep each module at least
        # 0.40 mm so a phone reads it at hand distance.
        for r in self.manifest:
            if r["item"] == "ST" and r["src"]:
                self.assertGreaterEqual(float(r["module_mm"]), 0.40, r["file"])

    def test_every_sticker_carries_a_qr(self):
        badges = [r for r in self.manifest if r["item"] == "ST" and not r["variant"].startswith("backing-")]
        self.assertEqual(len(badges), 7 * len(build_kit.STICKER_LANGS))
        for r in badges:
            self.assertTrue(r["payload"], r["file"])
            self.assertEqual(r["trim_mm"], "80.0x80.0", r["file"])
        self.assertAlmostEqual(2 * (build_kit.ST_R + build_kit.KEYLINE), 78.0)

    def test_badge_qr_panel_is_centred_inside_the_rim(self):
        # The white QR panel is centred left to right, its corners keep clear
        # colour inside the inner ring, and the headline and glyph sit above it.
        import math
        k = build_kit
        for lang in k.STICKER_LANGS:
            for n in range(1, 8):
                L = k.badge_layout(k.guide(n, lang))
                self.assertAlmostEqual(L["panel_x"] + L["half"], k.ST_CX, places=6)
                self.assertAlmostEqual(L["qr_x"] + k.ST_QR / 2, k.ST_CX, places=6)
                corner = math.hypot(L["half"], k.ST_CY - L["panel_y"])
                self.assertLessEqual(corner, k.ST_RIM - 2.0, (lang, n))
                self.assertGreater(L["head1"] - 10.0 / k.MM * 1.06, L["panel_top"] + 2.0, (lang, n))
                self.assertLess(L["glyph_top"], L["tag_y"] - 2.0, (lang, n))
                # Quiet zone: at least 4 modules of white round the code.
                self.assertGreaterEqual(L["pad"], 4 * k.ST_QR / len(k.qr_matrix(k.guide(n, lang))[0]))

    def test_languages(self):
        self.assertEqual(build_kit.STICKER_LANGS, ("en", "el", "sq"))
        langs = lambda item: {r["variant"].rsplit("-", 1)[1] for r in self.manifest if r["item"] == item}
        self.assertEqual(langs("ST"), {"en", "el", "sq"})
        self.assertEqual(langs("C1"), set(build_kit.LANGS))
        self.assertEqual(langs("P1"), set(build_kit.LANGS))

    def test_sticker_guides_hold_the_seed_facts(self):
        month = build_kit.seed_month()
        full, reduced = build_kit.airport_fare()
        for lang in build_kit.STICKER_LANGS:
            g = {n: build_kit.guide(n, lang) for n in range(1, 8)}
            for n in range(1, 8):
                # Readable offline, then a link to open later, tagged per sticker.
                self.assertTrue(g[n].endswith(build_kit.BASE_URL + f"st{n}-{lang}"), (lang, n))
            for n in range(1, 6):
                self.assertIn(month, g[n], (lang, n))
                self.assertIn("Syrmos", g[n].splitlines()[0], (lang, n))
            self.assertIn(str(build_kit.AIRPORT["journey"]), g[3])
            self.assertIn(build_kit.money(full, lang), g[3])
            self.assertIn(build_kit.money(reduced, lang), g[3])
            sat = build_kit.GUIDE[lang]["sat"]
            # OASA 24h Saturday: M2 and the trams run all night, M1 and the
            # M3 airport branch do not.
            self.assertIn(sat, g[2])
            self.assertIn(sat, g[4])
            self.assertNotIn(sat, g[1])
            self.assertNotIn(sat, g[3])
            self.assertIn(build_kit.line_hours("M1")["open"], g[1])
            self.assertIn("112", g[6])
            self.assertIn("https://" + build_kit.REPO_NAME + "\n", g[7])
            for text in g.values():
                self.assertNotIn(chr(0x2014), text)

    def test_required_lines_are_printed(self):
        for r in self.manifest:
            flat = re.sub(r"\s+", "", pdftext(build_kit.OUT / r["file"]))
            lang = r["variant"].rsplit("-", 1)[1]
            t = build_kit.COPY[lang]
            if r["item"] == "ST":
                if r["variant"].startswith("backing-"):
                    for line in (t["tagline"], t["disclaimer"], build_kit.SHORT_URL):
                        self.assertIn(line.replace(" ", ""), flat, r["file"])
                continue  # badges: the guide is in the QR, the disclaimer on sheet and liner
            for line in (build_kit.SHORT_URL, t["tagline"], t["scan"], t["disclaimer"]):
                self.assertIn(line.replace(" ", ""), flat, r["file"])

    def test_sheets_hold_the_set_but_no_contributor(self):
        with open(build_kit.OUT / "sheets.csv") as f:
            rows = list(csv.DictReader(f))
        self.assertEqual({r["sheet"] for r in rows},
                         {f"sheet-set-{lang}.pdf" for lang in build_kit.STICKER_LANGS})
        for lang in build_kit.STICKER_LANGS:
            variants = sorted(r["variant"] for r in rows if r["sheet"] == f"sheet-set-{lang}.pdf")
            self.assertEqual(variants, sorted(f"{n}-{lang}" for n in range(1, 7)))
            margin = re.sub(r"\s+", "", pdftext(build_kit.OUT / "sheets" / f"sheet-set-{lang}.pdf"))
            self.assertIn(build_kit.COPY[lang]["disclaimer"].replace(" ", ""), margin, lang)
        self.assertFalse(any("contrib" in r["variant"] for r in rows))

    def test_card_and_poster_use_the_adopted_concepts(self):
        # C1 is the ticket, P1 the departure board (../concepts): layered PDFs
        # with a level-H code (41 modules for these URLs) at the kit's sizes.
        for r in self.manifest:
            if r["item"] not in ("C1", "P1"):
                continue
            raw = (build_kit.OUT / r["file"]).read_bytes()
            self.assertIn(b"/OCProperties", raw, r["file"])
            for layer in ("Background", "Artwork", "Text", "QR"):
                self.assertIn(f"/Name ({layer})".encode(), raw, (r["file"], layer))
            self.assertEqual(r["qr_version"], "6", r["file"])
            lang = r["variant"].rsplit("-", 1)[1]
            flat = re.sub(r"\s+", "", pdftext(build_kit.OUT / r["file"]))
            if r["item"] == "C1":
                for line in ("notticket", "from", "to"):
                    self.assertIn(build_kit._concepts().C[lang][line].replace(" ", "").upper()
                                  if line != "notticket" else
                                  build_kit._concepts().C[lang][line].replace(" ", ""), flat, (r["file"], line))
            else:
                self.assertIn(build_kit._concepts().C[lang]["c2n"].replace(" ", "").upper(), flat, r["file"])
        self.assertEqual(build_kit.P1_QR, 120.0)

    def test_scan_copy_is_not_a_bare_download_ask(self):
        for lang in build_kit.LANGS:
            scan = build_kit.COPY[lang]["scan"]
            self.assertIn(build_kit.COPY[lang]["ui"]["next"].split()[-1].lower()[:4], scan.lower(), lang)

    def test_print_boxes_and_cut_line(self):
        for r in self.manifest:
            path = build_kit.OUT / r["file"]
            info = subprocess.run(["pdfinfo", "-box", str(path)], capture_output=True, text=True,
                                  check=True).stdout
            w, h = (float(v) for v in r["trim_mm"].split("x"))
            media = re.search(r"MediaBox:\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)", info)
            trim = re.search(r"TrimBox:\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)", info)
            mm = lambda pt: float(pt) / build_kit.MM
            self.assertAlmostEqual(mm(media.group(3)), w + 6, places=1)
            self.assertAlmostEqual(mm(media.group(4)), h + 6, places=1)
            self.assertAlmostEqual(mm(trim.group(1)), 3, places=1)
            self.assertAlmostEqual(mm(trim.group(3)) - mm(trim.group(1)), w, places=1)
            self.assertEqual(int(re.search(r"Pages:\s+(\d+)", info).group(1)), int(r["sides"]))
            raw = path.read_bytes()
            self.assertEqual(b"/CutContour" in raw, r["die_cut"] == "yes", r["file"])

    def test_airport_example_follows_the_app_rule(self):
        # CalendarLeaveBy.plan with a 120' flight buffer, no transfers, rain or alerts.
        a = build_kit.AIRPORT
        self.assertEqual(a["margin"], min(20, (a["headway"] + 1) // 2))
        leave = 600 - 120 - a["journey"] - a["margin"]
        self.assertEqual(a["leave"], f"{leave // 60}:{leave % 60:02d}")
        for r in self.manifest:
            if r["item"] == "C1":
                self.assertIn(a["leave"], pdftext(build_kit.OUT / r["file"]), r["file"])

    def test_no_em_dash_in_sources(self):
        for f in (KIT / "build_kit.py", KIT / "test_kit.py", README):
            self.assertNotIn(chr(0x2014), f.read_text(), f.name)


if __name__ == "__main__":
    unittest.main()
