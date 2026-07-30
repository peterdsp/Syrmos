"""Long-running daemon that holds one SSE connection to railway.gov.gr,
parses trainPositionsUx events, enriches them with line-id inference and
live-stream availability, and writes a small JSON file that nginx serves
at /api/trains.

Design constraints:
  - Each client polls the static JSON every 10 seconds rather than holding
    its own SSE connection. This keeps mobile battery and bandwidth low.
  - The daemon reconnects automatically on any error with exponential
    backoff (capped at 60 seconds).
  - Only suburban and intercity trains are included (freight filtered out).
  - The JSON file is written atomically (write tmp, rename) to avoid
    partial reads.

Run on the Pi:
    python3 scripts/railway-gov-sse.py

Environment:
    TRAINS_OUT   path to trains.json (default: ~/syrmos-api/out/trains.json)
    STREAM_CHECK whether to probe /api/public/trains/{id}/stream for HLS
                 availability (default: 1, set to 0 to skip)
"""
from __future__ import annotations

import json
import os
import re
import signal
import sys
import tempfile
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

SSE_URL = "https://railway.gov.gr/api/train-stream"
STREAM_URL_TPL = "https://railway.gov.gr/api/public/trains/{train_id}/stream"
USER_AGENT = "syrmos-daemon/1.0 (+https://syrmos.peterdsp.dev)"
OUT_PATH = Path(os.environ.get(
    "TRAINS_OUT",
    os.path.expanduser("~/syrmos-api/out/trains.json"),
))

CHECK_STREAMS = os.environ.get("STREAM_CHECK", "1") == "1"

STATION_NAMES_PATH = Path(__file__).with_name("station-names.json")

GREEK_TO_ENGLISH: dict[str, str] = {}


def _load_station_names() -> None:
    global GREEK_TO_ENGLISH
    try:
        with open(STATION_NAMES_PATH, encoding="utf-8") as f:
            raw = json.load(f)
        GREEK_TO_ENGLISH = {k.strip().lower(): v for k, v in raw.items()}
        print(f"[sse] loaded {len(GREEK_TO_ENGLISH)} station translations",
              file=sys.stderr, flush=True)
    except Exception as exc:
        print(f"[sse] warning: could not load station-names.json: {exc}",
              file=sys.stderr, flush=True)


def translate_station(name: str) -> str:
    if not name:
        return name
    trimmed = name.strip()
    return GREEK_TO_ENGLISH.get(trimmed.lower(), trimmed)


_PIRAEUS = re.compile(r"Πειραι|Piraeus|Peiraia", re.I)
_AIRPORT = re.compile(r"Αεροδρ[οό]μι|Airport|Aerodromio", re.I)
_ANO_LIOSIA = re.compile(r"Άνω Λιόσια|Ano Liosia", re.I)
_CHALKIDA = re.compile(r"Χαλκίδα|Chalkida|Halkida", re.I)
_KIATO = re.compile(r"Κιάτο|Kiato", re.I)
_ATHENS = re.compile(r"Αθήνα|Athens", re.I)

SUBURBAN_RULES: list[tuple[str, re.Pattern, re.Pattern]] = [
    ("A1", _PIRAEUS, _AIRPORT),
    ("A2", _ANO_LIOSIA, _AIRPORT),
    ("A3", _ATHENS, _CHALKIDA),
    ("A3", _PIRAEUS, _CHALKIDA),
    ("A3", _ANO_LIOSIA, _CHALKIDA),
    ("A4", _PIRAEUS, _KIATO),
    ("A4", _ATHENS, _KIATO),
]

FREIGHT_KEYWORDS = {"freight", "emprorevmatiko", "ypiresia"}

running = True


def _signal_handler(_sig, _frame):
    global running
    running = False


signal.signal(signal.SIGTERM, _signal_handler)
signal.signal(signal.SIGINT, _signal_handler)


def infer_line_id(origin: str, destination: str, service_type: str) -> str:
    combined = f"{origin} {destination}"
    for line_id, pat_a, pat_b in SUBURBAN_RULES:
        if (pat_a.search(combined) and pat_b.search(combined)):
            return line_id
    combined_en = f"{translate_station(origin)} {translate_station(destination)}"
    if combined_en != combined:
        for line_id, pat_a, pat_b in SUBURBAN_RULES:
            if (pat_a.search(combined_en) and pat_b.search(combined_en)):
                return line_id
    st = service_type.lower()
    if st in ("intercity", "ic") or "intercity" in st:
        return "IC"
    return "P"


def is_freight(train: dict) -> bool:
    st = (train.get("serviceType") or "").lower()
    name = (train.get("name") or "").lower()
    return any(kw in st or kw in name for kw in FREIGHT_KEYWORDS)


def check_live_stream(train_id: str) -> dict | None:
    url = STREAM_URL_TPL.format(train_id=train_id)
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=5) as r:
            data = json.loads(r.read())
            hls = data.get("hls", {})
            if hls.get("isLive"):
                return {
                    "playlistUrl": hls.get("playlistUrl", ""),
                    "streamingStatus": hls.get("streamingStatus", ""),
                }
    except Exception:
        pass
    return None


def parse_sse_event(raw_lines: list[str]) -> tuple[str | None, str | None]:
    event_type = None
    data_parts: list[str] = []
    for line in raw_lines:
        if line.startswith("event:"):
            event_type = line[6:].strip()
        elif line.startswith("data:"):
            data_parts.append(line[5:].strip())
    data = "\n".join(data_parts) if data_parts else None
    return event_type, data


def build_train(pos: dict) -> dict:
    origin = pos.get("origin") or ""
    destination = pos.get("destination") or ""
    next_station = pos.get("nextStation") or ""
    service_type = pos.get("serviceType") or ""
    line_id = infer_line_id(origin, destination, service_type)

    origin_en = translate_station(origin)
    destination_en = translate_station(destination)
    next_station_en = translate_station(next_station)

    train = {
        "id": pos.get("id", ""),
        "lineId": line_id,
        "trainNumber": pos.get("trainNumber") or pos.get("name") or "",
        "origin": origin.strip(),
        "originEn": origin_en,
        "destination": destination.strip(),
        "destinationEn": destination_en,
        "nextStation": next_station.strip(),
        "nextStationEn": next_station_en,
        "delayMinutes": pos.get("delay") or 0,
        "serviceType": service_type,
        "lat": pos.get("lat", 0.0),
        "lng": pos.get("lng", 0.0),
        "speed": pos.get("speed"),
        "course": pos.get("course"),
        "altitude": pos.get("altitude"),
        "progress": pos.get("progress"),
        "locomotiveNumber": pos.get("locomotiveNumber"),
        "distanceToDestination": pos.get("distanceToDestination_m"),
        "distanceToNextStation": pos.get("distanceToNextStation_m"),
        "signalStatus": pos.get("signalStatus"),
        "corridor": pos.get("corridor"),
        "trainType": pos.get("trainType"),
        "scheduledDeparture": pos.get("scheduledDeparture"),
        "scheduledArrival": pos.get("scheduledArrival"),
        "scheduleStatus": pos.get("scheduleStatus"),
        "trainId": pos.get("trainId") or pos.get("id", ""),
    }

    for key in list(train.keys()):
        if train[key] is None:
            del train[key]

    return train


def write_trains_json(trains: list[dict]) -> None:
    payload = {
        "updatedAt": datetime.now(timezone.utc).isoformat(),
        "count": len(trains),
        "trains": trains,
    }
    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(
        dir=str(OUT_PATH.parent),
        prefix=".trains-",
        suffix=".tmp",
    )
    try:
        with os.fdopen(fd, "w") as f:
            json.dump(payload, f, ensure_ascii=False, separators=(",", ":"))
        os.replace(tmp, str(OUT_PATH))
    except Exception:
        try:
            os.unlink(tmp)
        except OSError:
            pass
        raise


def connect_sse():
    req = urllib.request.Request(
        SSE_URL,
        headers={
            "User-Agent": USER_AGENT,
            "Accept": "text/event-stream",
            "Cache-Control": "no-cache",
        },
    )
    return urllib.request.urlopen(req, timeout=90)


def stream_loop() -> None:
    backoff = 1.0
    stream_cache: dict[str, dict | None] = {}
    last_stream_check = 0.0

    while running:
        try:
            print(f"[sse] connecting to {SSE_URL}", file=sys.stderr, flush=True)
            resp = connect_sse()
            backoff = 1.0
            buf: list[str] = []

            for raw_byte_line in resp:
                if not running:
                    break
                line = raw_byte_line.decode("utf-8", errors="replace").rstrip("\n\r")

                if line == "":
                    if buf:
                        event_type, data = parse_sse_event(buf)
                        buf.clear()
                        if event_type == "trainPositionsUx" and data:
                            try:
                                raw = json.loads(data)
                                positions = raw.get("positions", []) if isinstance(raw, dict) else raw
                            except json.JSONDecodeError:
                                continue

                            trains = []
                            for pos in positions:
                                if is_freight(pos):
                                    continue
                                trains.append(build_train(pos))

                            now = time.monotonic()
                            if CHECK_STREAMS and (now - last_stream_check) > 120:
                                stream_cache.clear()
                                for t in trains:
                                    tid = t.get("trainId", "")
                                    if tid and tid not in stream_cache:
                                        stream_cache[tid] = check_live_stream(tid)
                                last_stream_check = now

                            for t in trains:
                                tid = t.get("trainId", "")
                                info = stream_cache.get(tid)
                                if info:
                                    t["liveStream"] = info

                            write_trains_json(trains)
                else:
                    buf.append(line)

            resp.close()
        except Exception as exc:
            print(f"[sse] error: {exc}, reconnecting in {backoff:.0f}s",
                  file=sys.stderr, flush=True)

        if running:
            time.sleep(backoff)
            backoff = min(backoff * 2, 60.0)


def main() -> int:
    print(f"[sse] daemon starting, writing to {OUT_PATH}", file=sys.stderr, flush=True)

    _load_station_names()
    write_trains_json([])

    stream_loop()

    print("[sse] daemon stopped", file=sys.stderr, flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
