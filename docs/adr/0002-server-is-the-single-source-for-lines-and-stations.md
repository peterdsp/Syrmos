# ADR 0002: The server database is the single source for lines and stations

Status: Accepted (2026-07-17), in force.
Scope: `core:data` seeding, `LineRepositoryImpl`, iOS `SyrmosData`, the web seed,
and every migration or importer that writes line/station attributes.

## Context

Line and station data existed twice and the bridge between the copies was broken.

- The apps read a legacy flat `files/seed/lines.json`, generated from hardcoded
  Swift by `scripts/sync-ios-transit-seed.mjs`. iOS read the same hardcoded Swift
  directly.
- The server generator wrote only `schedules-v2/`, and never the legacy
  `seed/lines.json`.
- The generating script pointed at a Swift path that had not existed since the
  June 2026 iOS restructure, so it crashed on every run and had done so unnoticed
  for a month.

The result: the database was the source of truth for schedules while hardcoded
Swift was the source of truth for lines and stations, with no working path
between them. It surfaced when Thessaloniki TM1/TM2 reached the server but the
apps still listed ten Athens lines.

## Decision

**The server database is the single source of truth for lines and stations.**
Clients read `schedules-v2/lines.json`. The legacy flat seed and the
Swift-as-source script are retired.

Rationale:

- Adding a city must be a data change, not an edit to hardcoded Swift that then
  has to be transcribed by a script nobody runs.
- Two sources drift. They already had: the generator emits `region` and `status`,
  the legacy seed cannot carry them, so the honesty guarantee for a line that is
  built but not open would silently never reach a client.

## Consequences

- `schedules-v2/lines.json` carries `region` and `status`, and its nested
  `stations[]` is the authoritative statement that a line serves a stop. The
  station seed's `line_ids` is an interchange union and is **not** boarding
  membership; reading it as membership is what once routed a suburban lookup to a
  metro stop id.
- Migrations that add line or station attributes are written against the server
  schema, and the client seed follows from the generator rather than by hand.
- A client that cannot reach the server falls back to the bundled snapshot of the
  same generated file, never to a separately maintained list.
