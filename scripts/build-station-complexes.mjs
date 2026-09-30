#!/usr/bin/env node
// Builds the reviewed station-complex registry that the three clients share.
//
// A *station complex* is the thing a rider walks into: one name, several real
// boarding areas, several boarding stop ids. Syrmos needs it because the seed
// keeps one stop id per line, so Athens is five ids (`M2_STA`, `A1_ATH`,
// `A3_ATH`, `A4_ATH`, `GR_ATH`) that no client previously joined, and because
// the seed's `stations.json:line_ids` carries INTERCHANGE unions rather than
// boarding membership, which previously routed `A1` lookups to the metro stop
// `M2_STA`.
//
// Membership rules encoded here (the registry is data, the rules are reviewed):
//
//  1. Boarding membership is route membership. A line belongs to a stop only
//     when that line's own `stations[]` in `schedules-v2/lines.json` contains
//     the stop id. Nothing is inferred from `stations.json:line_ids`.
//  2. Candidate members must be within `MAX_METRES` of each other.
//  3. Co-location alone never merges. Members must additionally share a folded
//     station name, OR the pair must appear in `REVIEWED_MERGES` below with a
//     recorded reason.
//  4. `REVIEWED_SPLITS` names co-located stops that are deliberately NOT one
//     complex, so a later data refresh cannot silently merge them.
//  5. Boarding areas are the modes actually boardable at the member stops, so
//     "Metro" and "Railway station" stay identifiable inside one Athens card.
//
// Run: node scripts/build-station-complexes.mjs
// Writes the identical registry to the iOS bundle, the shared Compose seed and
// the Android asset seed.
import fs from 'node:fs';
import path from 'node:path';

const ROOT = process.cwd();
const LINES = path.join(ROOT, 'iosApp/iosApp/Resources/seed-schedules-v2/lines.json');
const STATIONS = path.join(ROOT, 'core/data/src/commonMain/composeResources/files/seed/stations.json');
const OUTPUTS = [
  path.join(ROOT, 'iosApp/iosApp/Resources/seed-schedules-v2/station-complexes.json'),
  path.join(ROOT, 'core/data/src/commonMain/composeResources/files/seed/station-complexes.json'),
  path.join(ROOT, 'androidApp/src/androidMain/assets/files/seed/station-complexes.json'),
];

const MAX_METRES = 250;

// Co-located stops whose names differ but which are one station for a rider.
// Each entry records why, so the merge is reviewable rather than incidental.
const REVIEWED_MERGES = [
  {
    id: 'CPX_ATHENS_LARISSA',
    stops: ['M2_STA', 'A1_ATH', 'A3_ATH', 'A4_ATH', 'GR_ATH'],
    reason:
      'Athens Larissa Station. The metro stop is named "Σταθμός Λαρίσης" in lines.json while the ' +
      'railway platforms are named "Αθήνα"; they are the same station complex, metro below the ' +
      'main-line concourse.',
  },
  {
    id: 'CPX_SYNGROU_FIX',
    stops: ['M2_SY2', 'T6_FIX'],
    reason: 'The T6 "Fix" tram stop is the street-level stop of the M2 "Syngrou-Fix" metro station.',
  },
  {
    id: 'CPX_DIMOTIKO_THEATRO',
    stops: ['M3_DIM', 'T7_DIM'],
    reason:
      'One Piraeus square, 32 m apart: M3 "Dimotiko Theatro" and the T7 stop spelled ' +
      '"Dimarhio / Dimotiko Theatro".',
  },
  {
    id: 'CPX_THESSALONIKI_RAILWAY',
    stops: ['TM1_NSS', 'GR_THE'],
    reason:
      'Thessaloniki main railway station. The metro stop is named "New Railway Station" and the ' +
      'main-line platforms "Thessaloniki".',
  },
];

// Co-located stops that stay separate stations, with the reason. Without this
// the 250 m candidate radius would fold genuinely different stations together.
const REVIEWED_SPLITS = [
  {
    stops: ['M1_FAL', 'T7_PEA', 'T7_GIP'],
    reason:
      'Faliro (M1), SEF and Gipedo Karaiskaki (two separate T7 stops) are three distinct stations ' +
      'along the same 180 m stretch, not one complex.',
  },
  {
    stops: ['A1_KAT', 'A4_KAT', 'GR_SKA'],
    reason:
      'Kato Acharnai (suburban platforms) and SKA Acharnon (the Acharnes railway centre) are ' +
      'separate stations that happen to sit 61 m apart. Only the two Kato Acharnai ids merge.',
  },
];

// Rider-facing complex names, where the member names alone are ambiguous.
const NAME_OVERRIDES = {
  CPX_ATHENS_LARISSA: {
    name: 'Athens · Larissa Station',
    nameEl: 'Αθήνα · Σταθμός Λαρίσης',
    nameSq: 'Athinë · Stacioni Larisa',
    nameIt: 'Atene · Stazione Larissa',
  },
  CPX_THESSALONIKI_RAILWAY: {
    name: 'Thessaloniki · Railway Station',
    nameEl: 'Θεσσαλονίκη · Σιδηροδρομικός Σταθμός',
    nameSq: 'Selanik · Stacioni Hekurudhor',
    nameIt: 'Salonicco · Stazione Ferroviaria',
  },
  CPX_PIRAEUS: {
    name: 'Piraeus',
    nameEl: 'Πειραιάς',
    nameSq: 'Pireu',
    nameIt: 'Pireo',
  },
  CPX_DIMOTIKO_THEATRO: {
    name: 'Dimotiko Theatro',
    nameEl: 'Δημοτικό Θέατρο',
    nameSq: 'Dimotiko Theatro',
    nameIt: 'Dimotiko Theatro',
  },
};

// Boarding-area labels per mode, in the four supported reader languages.
const AREAS = {
  metro: { name: 'Metro', nameEl: 'Μετρό', nameSq: 'Metro', nameIt: 'Metropolitana' },
  tram: { name: 'Tram', nameEl: 'Τραμ', nameSq: 'Tramvaj', nameIt: 'Tram' },
  rail: {
    name: 'Railway station',
    nameEl: 'Σιδηροδρομικός σταθμός',
    nameSq: 'Stacioni hekurudhor',
    nameIt: 'Stazione ferroviaria',
  },
  bus: { name: 'Rail-replacement bus', nameEl: 'Λεωφορείο αντικατάστασης', nameSq: 'Autobus zëvendësues', nameIt: 'Bus sostitutivo' },
};

function areaForType(type) {
  switch (String(type || '').toLowerCase()) {
    case 'metro': return 'metro';
    case 'tram': return 'tram';
    case 'bus': return 'bus';
    default: return 'rail'; // suburban, scenic and intercity all board on rail platforms
  }
}

function fold(s) {
  return String(s == null ? '' : s).trim().toLowerCase()
    .normalize('NFD').replace(/[̀-ͯ]/g, '')
    .replace(/\s+/g, ' ');
}

function metres(a, b) {
  const R = 6371000;
  const rad = (x) => (x * Math.PI) / 180;
  const dLat = rad(b.lat - a.lat);
  const dLon = rad(b.lon - a.lon);
  const h = Math.sin(dLat / 2) ** 2 +
    Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

const linesPayload = JSON.parse(fs.readFileSync(LINES, 'utf8'));
const stationSeed = JSON.parse(fs.readFileSync(STATIONS, 'utf8'));
const seedById = new Map(stationSeed.map((s) => [s.id, s]));

// Authoritative boarding membership plus a coordinate/name record per stop.
const stops = new Map();
for (const line of linesPayload.lines) {
  for (const s of line.stations || []) {
    if (!stops.has(s.id)) {
      const seed = seedById.get(s.id);
      stops.set(s.id, {
        id: s.id,
        name: (seed && seed.name) || s.name,
        nameEl: (seed && seed.name_el) || s.nameEl || s.name,
        nameSq: (seed && seed.name_sq) || null,
        // The canonical station record wins for geometry: `stations.json` is the
        // set the map and the planner use, and a few `lines.json` entries carry
        // a per-line platform coordinate a few hundred metres off it.
        lat: seed ? seed.latitude : s.lat,
        lon: seed ? seed.longitude : s.lng,
        lineIds: [],
        types: new Set(),
      });
    }
    const rec = stops.get(s.id);
    if (!rec.lineIds.includes(line.id)) rec.lineIds.push(line.id);
    rec.types.add(areaForType(line.type));
  }
}

const splitStops = new Set(REVIEWED_SPLITS.flatMap((r) => r.stops));
const mergeGroupOf = new Map();
REVIEWED_MERGES.forEach((m, i) => m.stops.forEach((s) => mergeGroupOf.set(s, i)));

// Union-find over the candidate radius, gated by rule 3.
const ids = [...stops.keys()].sort();
const parent = new Map(ids.map((id) => [id, id]));
const find = (x) => (parent.get(x) === x ? x : (parent.set(x, find(parent.get(x))), parent.get(x)));
const union = (a, b) => { a = find(a); b = find(b); if (a !== b) parent.set(b, a); };

function mayMerge(a, b) {
  const ga = mergeGroupOf.get(a.id);
  const gb = mergeGroupOf.get(b.id);
  if (ga != null && ga === gb) return true;
  // A reviewed split blocks any merge that is not itself reviewed.
  if (splitStops.has(a.id) || splitStops.has(b.id)) {
    return fold(a.name) === fold(b.name) &&
      !(REVIEWED_SPLITS.some((r) => r.stops.includes(a.id) && r.stops.includes(b.id) &&
        fold(a.name) !== fold(b.name)));
  }
  if (ga != null || gb != null) return false; // reviewed member never absorbs an unreviewed neighbour
  return fold(a.name) === fold(b.name);
}

for (let i = 0; i < ids.length; i++) {
  for (let j = i + 1; j < ids.length; j++) {
    const a = stops.get(ids[i]);
    const b = stops.get(ids[j]);
    if (metres(a, b) > MAX_METRES) continue;
    if (!mayMerge(a, b)) continue;
    union(a.id, b.id);
  }
}

const clusters = new Map();
for (const id of ids) {
  const root = find(id);
  if (!clusters.has(root)) clusters.set(root, []);
  clusters.get(root).push(stops.get(id));
}

function complexId(members) {
  // A reviewed merge names its own complex, so a data refresh that changes a
  // member's display name cannot rename the complex out from under a saved
  // selection. Everything else is `CPX_` plus the folded shared name, which is
  // stable because rule 3 only merges stops that already share that name.
  const reviewed = REVIEWED_MERGES.find((m) => members.some((x) => m.stops.includes(x.id)));
  if (reviewed) return reviewed.id;
  const first = members.slice().sort((a, b) => a.id.localeCompare(b.id))[0];
  const slug = fold(first.name).replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '').toUpperCase();
  return `CPX_${slug}`;
}

const complexes = [];
for (const members of clusters.values()) {
  // A complex is only interesting when it joins more than one boarding stop.
  // Single-stop stations keep working through their plain stop id.
  if (members.length < 2) continue;
  const boarding = members.filter((m) => m.lineIds.length > 0);
  if (boarding.length < 2) continue;

  const id = complexId(boarding);
  const areaOrder = ['metro', 'rail', 'tram', 'bus'];
  const areas = [];
  for (const areaId of areaOrder) {
    const stopIds = boarding
      .filter((m) => m.types.has(areaId))
      .map((m) => m.id)
      .sort();
    if (!stopIds.length) continue;
    areas.push({ id: areaId, ...AREAS[areaId], stopIds });
  }
  // Longest member name is the most specific rider-facing label, unless the
  // complex is explicitly named above.
  const byLength = boarding.slice().sort((a, b) => b.name.length - a.name.length || a.id.localeCompare(b.id));
  const rep = byLength[0];
  const override = NAME_OVERRIDES[id] || null;
  const reviewed = REVIEWED_MERGES.find((m) => m.stops.some((s) => boarding.some((b) => b.id === s)));
  complexes.push({
    id,
    name: override ? override.name : rep.name,
    nameEl: override ? override.nameEl : (rep.nameEl || rep.name),
    nameSq: override ? override.nameSq : (rep.nameSq || rep.name),
    nameIt: override ? override.nameIt : rep.name,
    latitude: Number((boarding.reduce((s, m) => s + m.lat, 0) / boarding.length).toFixed(7)),
    longitude: Number((boarding.reduce((s, m) => s + m.lon, 0) / boarding.length).toFixed(7)),
    areas,
    reviewNote: reviewed ? reviewed.reason : null,
  });
}

complexes.sort((a, b) => a.id.localeCompare(b.id));

const payload = {
  version: 1,
  updatedAt: new Date().toISOString().slice(0, 10),
  note:
    'Reviewed station-complex registry. Boarding membership is route membership from ' +
    'schedules-v2/lines.json; co-location alone never merges two stations. Generated by ' +
    'scripts/build-station-complexes.mjs.',
  maxMemberDistanceMetres: MAX_METRES,
  reviewedSplits: REVIEWED_SPLITS,
  complexes,
};

for (const out of OUTPUTS) {
  fs.mkdirSync(path.dirname(out), { recursive: true });
  fs.writeFileSync(out, JSON.stringify(payload, null, 2) + '\n');
}

console.log(`wrote ${complexes.length} complexes to ${OUTPUTS.length} seed locations`);
for (const c of complexes) {
  const stopIds = c.areas.flatMap((a) => a.stopIds);
  console.log(`  ${c.id.padEnd(22)} ${c.name.padEnd(34)} ${stopIds.join(',')}`);
}
