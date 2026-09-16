#!/usr/bin/env python3
"""Greek display-copy candidate detector for finding 8.

Flags Swift string literals that look like Greek DISPLAY copy carrying no accent
at all, which in monotonic Greek reads as broken text. This is a *candidate*
detector for review, not a certifier of correct Greek: it cannot judge grammar,
tonos placement, or partial errors (a mostly-accented string with one wrong word),
and it deliberately over-includes so a native reviewer sees everything worth a
look. AriadneParser.swift is excluded because its unaccented strings are match
keys for user input, not display text.

Usage: python3 scripts/greek-copy-audit.py [--json]
"""
import os
import re
import sys
import json

ROOT = os.path.join(os.path.dirname(__file__), "..", "iosApp", "iosApp")
# AriadneParser + AriadneDomain hold accent-folded, lowercased CUE words that match
# user input (fastestCues / fewestChangesCues etc.), never display text, so their
# unaccented strings are correct and excluded from the display-copy audit.
EXCLUDE = {"AriadneParser.swift", "AriadneDomain.swift"}

ACCENTED = set("άέήίόύώϊϋΐΰ" + "ΆΈΉΊΌΎΏ")
GREEK = re.compile(r"[Α-Ωα-ωΆ-ώ]")
GREEK_LETTER = re.compile(r"[Α-Ωα-ωΆ-ώΪΫϊϋΐΰ]")
STRING_LIT = re.compile(r'"((?:[^"\\]|\\.)*)"')

# Any Greek display string with this many letters and no accent is a candidate.
# Lowered to 4 so short words (Χάρτης, Δίκτυο, Αθήνα) are not missed, with an
# explicit skip set for monosyllables and loanwords that are correctly unaccented.
MIN_GREEK_LETTERS = 4

# Monosyllables, articles/particles, loanwords and proper single-syllable names
# that carry no tonos in monotonic Greek. A string every one of whose Greek word
# tokens is in this set is not flagged. Only Greek tokens are compared (the token
# split is Greek-only), so Latin interpolation names and loanwords never reach
# this set and are not listed here.
SKIP_WORDS = {
    "και", "της", "τον", "την", "των", "στο", "στη", "στον", "στην", "στα", "στις",
    "του", "για", "απο", "δεν", "ναι", "οχι", "σου", "μου", "μας", "σας", "τους",
    "μια", "μιας", "δυο", "τρεις", "η", "το", "τα", "με", "σε", "να", "θα", "οι",
    "ο", "α", "προς", "τραμ", "ρουφ", "πως", "τι", "ως", "αν", "οτι",
    "τ",                       # tram line codes: "Τραμ Τ6", "Τραμ Τ7"
    "αεροδρ", "αεροδρο", "πτηση",  # accent-folded match-key prefixes, not display
}


def greek_letters(s):
    return [c for c in s if GREEK_LETTER.match(c)]


def is_all_caps_greek(s):
    letters = [c for c in s if c.isalpha() and GREEK.match(c)]
    return letters and all(c.upper() == c for c in letters)


def scan():
    hits = []
    for dirpath, _, files in os.walk(ROOT):
        for fn in files:
            if not fn.endswith(".swift") or fn in EXCLUDE:
                continue
            path = os.path.join(dirpath, fn)
            with open(path, encoding="utf-8") as f:
                for i, line in enumerate(f, 1):
                    for m in STRING_LIT.finditer(line):
                        lit = m.group(1)
                        gl = greek_letters(lit)
                        if len(gl) < MIN_GREEK_LETTERS:
                            continue
                        if any(c in ACCENTED for c in lit):
                            continue
                        # All-caps Greek legitimately drops the tonos; skip.
                        if is_all_caps_greek(lit):
                            continue
                        # Skip strings whose every Greek token is a correctly
                        # unaccented monosyllable / loanword / interpolation name.
                        toks = [w for w in re.split(r"[^Α-Ωα-ωΆ-ώΪΫϊϋΐΰ]+", lit) if w]
                        if toks and all(w.lower() in SKIP_WORDS for w in toks):
                            continue
                        rel = os.path.relpath(path, os.path.join(ROOT, ".."))
                        hits.append({"file": rel, "line": i, "text": lit})
    return hits


def main():
    hits = scan()
    if "--json" in sys.argv:
        print(json.dumps(hits, ensure_ascii=False, indent=2))
        return
    by_file = {}
    for h in hits:
        by_file.setdefault(h["file"], 0)
        by_file[h["file"]] += 1
    print(f"Greek no-accent display candidates: {len(hits)}")
    for f, n in sorted(by_file.items(), key=lambda x: -x[1]):
        print(f"  {n:4d}  {f}")


if __name__ == "__main__":
    main()
