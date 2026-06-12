# Hellenic Train PDF schedule verification

Source of truth: https://www.hellenictrain.gr/en/athens-suburban-and-regional-railway

User-flagged gap (2026-06-12): the schedules emitted by the operator-rule
projector for A1 / A2 / A3 / A4 (and several metro/tram bands) do not match
the timestamps published in the official Hellenic Train PDFs (sample URL the
user provided: KIATO-ATHINA-PEIRAIAS PDF from 2022-06). The projector is
generating departures from frequency bands plus open/close rules, but the
real published timetable is timestamp-by-timestamp and includes service
splits we are not yet modelling: certain trains start mid-route, some skip
stations, weekend headways differ per leg.

## Work needed

1. **Crawl the index page** and enumerate every PDF linked from
   https://www.hellenictrain.gr/en/athens-suburban-and-regional-railway
   (the page lists per-line timetables; URLs change when the operator
   refreshes a schedule, so we cannot hardcode them).

2. **Download + cache** each PDF with content-hash in
   `assets/hellenic-train-pdfs/{lineId}/{yyyymmdd}-{sha256-12}.pdf`.

3. **Parse the tables** with `pdfplumber`. Schedule pages are table-formatted
   per direction. Each table has columns = stations, rows = trains.

4. **Project to our internal schema**: emit
   `(line_id, day_type, direction, train_id, time_per_station[])` records
   that replace the band-based projection for the affected lines.

5. **Schema additions**: extend `ops/syrmos-api/migrations/` with a
   `train_timestamps` table so we can store per-train per-station times
   instead of synthesising them from headways.

6. **Diff + replace**: compare new times to current projector output, log
   the delta in the watcher journal, and replace bundled seeds in all 3
   app targets.

7. **Same exercise for STASY** (metro + tram) once the suburban path is
   proven. STASY publishes timetables on https://www.stasy.gr/ per line
   (separate PDF per direction; weekend versions live on a sub-page).

## Why this lives outside this commit

It is a multi-hour data project: PDF crawler, parser, schema migration,
seed regeneration, validation. Implementing the iOS UI fixes and the
direction-aware projector first lets the user verify the visible
regressions are gone; PDF-grounded schedules ship in a subsequent drop
once the parser is tested against ground truth.

## Tracker

- [ ] Crawl index page; build URL manifest
- [ ] Download + content-hash PDFs into the repo
- [ ] Parse one A1 PDF end-to-end (validate column detection)
- [ ] Add `train_timestamps` schema + admin UI
- [ ] Replace band projection for A1-A4 with real timestamps
- [ ] Extend to T6/T7 via STASY PDFs
- [ ] Extend to M1/M2/M3 via STASY PDFs
- [ ] Wire into the daily watcher so a PDF hash change triggers a refresh
