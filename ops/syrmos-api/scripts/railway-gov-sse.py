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

SUBURBAN_PATTERNS = {
    "A1": re.compile(r"\b(Piraeus|Peiraia|PIRAEUS)\b.*\b(Airport|AIRPORT|Aerodromio)\b", re.I),
    "A2": re.compile(r"\b(Airport|AIRPORT|Aerodromio)\b.*\b(Piraeus|Peiraia|PIRAEUS)\b", re.I),
    "A3": re.compile(r"\b(Piraeus|Peiraia|PIRAEUS|Ano Liosia|ANO LIOSIA)\b.*\b(Chalkida|Halkida|CHALKIDA)\b", re.I),
    "A4": re.compile(r"\b(Piraeus|Peiraia|PIRAEUS)\b.*\b(Kiato|KIATO)\b", re.I),
}

FREIGHT_KEYWORDS = {"freight", "emprorevmatiko", "ypiresia"}

running = True


def _signal_handler(_sig, _frame):
    global running
    running = False


signal.signal(signal.SIGTERM, _signal_handler)
signal.signal(signal.SIGINT, _signal_handler)


def infer_line_id(origin: str, destination: str, service_type: str) -> str:
    route = f"{origin} - {destination}"
    for line_id, pattern in SUBURBAN_PATTERNS.items():
        if pattern.search(route):
            return line_id
    if service_type.lower() in ("intercity", "ic"):
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
    service_type = pos.get("serviceType") or ""
    line_id = infer_line_id(origin, destination, service_type)

    train = {
        "id": pos.get("id", ""),
        "lineId": line_id,
        "trainNumber": pos.get("trainNumber") or pos.get("name") or "",
        "origin": origin,
        "destination": destination,
        "nextStation": pos.get("nextStation") or "",
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

    write_trains_json([])

    stream_loop()

    print("[sse] daemon stopped", file=sys.stderr, flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
