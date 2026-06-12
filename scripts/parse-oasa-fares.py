"""Scrape OASA ticket-price products into structured JSONL.

Source: https://www.oasa.gr/en/tickets/prices-of-products/

OASA publishes each ticket as a heading (h2/h3) followed by a block of
text containing 'Full price:', 'Discounted:' (or '–' for none),
a one-line validity badge ('Exclude Airport & X80', '2-way from/to
Airport', etc.) and a free-form description paragraph. Pack offers
('2-single ticket pack', '5-single ticket pack', etc.) have a slightly
different layout - title followed by a single euro amount and a
Personalized Card / Anonymous Card / Ticket flag list.

Output: assets/oasa-fares/parsed/fares.jsonl
One JSON record per product:
  {
    "section":      "Single tickets",
    "title_en":     "90-MINUTE SINGLE TICKET",
    "full_price_eur": 1.20,
    "discounted_price_eur": 0.50,
    "validity":     "90 minutes",
    "covers":       "All OASA means; not valid for Airport EXPRESS or M3 Koropi -> Airport",
    "notes":        "Free-form description",
    "tags":         ["airport_excluded"]
  }
"""
from __future__ import annotations

import json
import re
import sys
import urllib.request
from pathlib import Path

from bs4 import BeautifulSoup

ROOT = Path(__file__).resolve().parent.parent
CACHE_DIR = ROOT / "assets" / "oasa-fares"
OUT_DIR = CACHE_DIR / "parsed"
OUT_DIR.mkdir(parents=True, exist_ok=True)

URL = "https://www.oasa.gr/en/tickets/prices-of-products/"
USER_AGENT = "syrmos-oasa-fetch/1.0 (+https://syrmos.peterdsp.dev)"

PRICE_RE = re.compile(r"(\d+(?:[.,]\d{1,2})?)\s*€")
PACK_PRICE_RE = re.compile(r"^\s*(\d+(?:[.,]\d{1,2})?)\s*€?\s*$")


def fetch_html() -> str:
    cached = CACHE_DIR / "prices-of-products.html"
    if cached.exists():
        return cached.read_text()
    req = urllib.request.Request(URL, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=30) as r:
        html = r.read().decode("utf-8", errors="ignore")
    cached.parent.mkdir(parents=True, exist_ok=True)
    cached.write_text(html)
    return html


def parse_price(raw: str) -> float | None:
    if not raw:
        return None
    raw = raw.strip().replace("\xa0", " ")
    if raw in ("–", "-", "—"):
        return None
    m = PRICE_RE.search(raw)
    if not m:
        m = PACK_PRICE_RE.match(raw)
        if not m:
            return None
    return float(m.group(1).replace(",", "."))


def derive_tags(title: str, validity: str, notes: str) -> list[str]:
    text = f"{title} {validity} {notes}".lower()
    tags: list[str] = []
    if "airport" in text and ("exclud" in text or "not valid" in text):
        tags.append("airport_excluded")
    if "express" in text and "airport" in text:
        tags.append("airport_express")
    if "discount" in text or "discounted" in text:
        tags.append("has_discount")
    if "pack" in text or "10+1" in text:
        tags.append("pack")
    if "90-minute" in text or "90 minute" in text:
        tags.append("ninety_minute")
    if "tourist" in text:
        tags.append("tourist")
    return tags


SECTION_KEYWORDS = {
    "SINGLE TICKETS":    "single",
    "OFFERS":            "offers",
    "AIRPORT TICKET":    "airport",
    "EXPRESS BUS LINES": "airport",
    "FOR ALL MEANS":     "passes",
    "DAY PASS":          "passes",
}


def main() -> None:
    html = fetch_html()
    soup = BeautifulSoup(html, "html.parser")
    for tag in soup(["script", "style", "noscript", "header", "footer", "nav"]):
        tag.decompose()

    records: list[dict] = []
    current_section = "single"

    for heading in soup.find_all(["h2", "h3", "h4"]):
        title = heading.get_text(" ", strip=True)
        if not title or len(title) > 120:
            continue
        upper = title.upper()
        for kw, section in SECTION_KEYWORDS.items():
            if kw in upper:
                current_section = section
                break
        # Top-level section anchors themselves are not products.
        if upper in ("SINGLE TICKETS", "OFFERS", "EXPRESS BUS LINES"):
            continue

        # Walk siblings until the next heading and gather block text.
        chunks: list[str] = []
        for sib in heading.next_siblings:
            if getattr(sib, "name", None) in ("h2", "h3", "h4"):
                break
            text = (sib.get_text(" ", strip=True) if hasattr(sib, "get_text") else str(sib)).strip()
            if text:
                chunks.append(text)
        block = " | ".join(chunks)

        full_price = None
        disc_price = None
        validity = ""
        notes = block

        m = re.search(r"Full price:\s*([^|]+)", block)
        if m:
            full_price = parse_price(m.group(1))
        m = re.search(r"Discounted:\s*([^|]+)", block)
        if m:
            disc_price = parse_price(m.group(1))

        # Validity hint: the first short token after the discount line.
        m = re.search(r"Discounted:[^|]+\|\s*([^|]+)\|", block)
        if m:
            candidate = m.group(1).strip()
            if 2 < len(candidate) < 80:
                validity = candidate

        if full_price is None and current_section == "offers":
            # Pack offers list the price right after the title with no
            # 'Full price:' prefix.
            first_token = block.split("|", 1)[0].strip()
            full_price = parse_price(first_token)

        if full_price is None and disc_price is None:
            continue

        records.append({
            "section":               current_section,
            "title_en":              title,
            "full_price_eur":        full_price,
            "discounted_price_eur":  disc_price,
            "validity":              validity,
            "notes":                 notes[:500],
            "tags":                  derive_tags(title, validity, notes),
        })

    out = OUT_DIR / "fares.jsonl"
    with out.open("w") as f:
        for r in records:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"wrote {len(records)} fare products -> {out.relative_to(ROOT)}")
    sections = {}
    for r in records:
        sections.setdefault(r["section"], 0)
        sections[r["section"]] += 1
    for s, n in sorted(sections.items()):
        print(f"  {s}: {n}")


if __name__ == "__main__":
    main()
