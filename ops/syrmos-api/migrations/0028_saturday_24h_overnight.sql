-- 0028: Guarantee continuous 24-hour Saturday overnight bands (self-healing repair).
--
-- Official truth (OASA / STASY, verified 2026-08-30):
--   Metro M2, Metro M3 (city only), Tram T6 and T7 operate 24 hours every
--   Saturday; the service continues past midnight into Sunday until the 05:30
--   daytime handover. The M3 airport branch (M3_AIR) and Metro M1 are NOT 24h.
--
-- Why this migration exists:
--   The projector represents the overnight tail as a Saturday-day-type band whose
--   start is before 05:00, so it projects onto Sunday's early morning. A stale
--   seed (predating the overnight bands) or a partial oasa_24mmm scrape that
--   drops the 02:00->05:30 (metro) / 01:40->05:30 (tram) continuation row leaves
--   no band covering the small hours, blanking the live map after midnight. That
--   was the reported defect: zero M2/M3/T6/T7 vehicles at 01:53 Sunday. This
--   migration rewrites the authoritative overnight band for each 24h line to the
--   official OASA 24mmm frequencies so any already-deployed DB self-heals on
--   upgrade. Fresh DBs already get these bands from scripts.import_athens_package,
--   and syrmos_admin.scraper_24mmm re-asserts them after every scrape.
--
-- Idempotent: re-running produces the same rows.
-- Source: https://www.oasa.gr/en/24mmm/
--   M2 / M3 city : 00:20 -> 05:30 @ 15'
--   T6 / T7      : 00:30 -> 05:30 @ 25'

DELETE FROM frequency_bands
 WHERE day_type='sat' AND label='saturday_overnight_24_7'
   AND line_id IN ('M2','M3','T6','T7');

-- Clear any row occupying the exact overnight start slot so the insert below
-- cannot collide on the (line_id, day_type, direction, time_start) primary key
-- (e.g. a truncated sat_24mmm scrape row starting at the same minute).
DELETE FROM frequency_bands
 WHERE day_type='sat' AND direction='both'
   AND ( (line_id IN ('M2','M3') AND time_start='00:20')
      OR (line_id IN ('T6','T7') AND time_start='00:30') );

INSERT INTO frequency_bands
 (line_id, day_type, time_start, time_end, headway_minutes, label, direction)
VALUES
 ('M2','sat','00:20','05:30',15.0,'saturday_overnight_24_7','both'),
 ('M3','sat','00:20','05:30',15.0,'saturday_overnight_24_7','both'),
 ('T6','sat','00:30','05:30',25.0,'saturday_overnight_24_7','both'),
 ('T7','sat','00:30','05:30',25.0,'saturday_overnight_24_7','both');

-- Provenance for the 24h overnight service, surfaced in the schedules manifest.
INSERT INTO meta(key, value) VALUES
 ('saturday_24h_source','https://www.oasa.gr/en/24mmm/'),
 ('saturday_24h_verified_on','2026-08-30')
ON CONFLICT(key) DO UPDATE SET value=excluded.value;

INSERT OR IGNORE INTO schema_version(version) VALUES (28);
