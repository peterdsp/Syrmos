import fs from "node:fs";
import path from "node:path";

const repoRoot = process.cwd();
const transitDataPath = path.join(repoRoot, "iosApp/iosApp/Models/TransitData.swift");
const stationCoordinatesPath = path.join(repoRoot, "iosApp/iosApp/Models/StationCoordinates.swift");
const seedDir = path.join(repoRoot, "core/data/src/commonMain/composeResources/files/seed");
const androidSeedDir = path.join(repoRoot, "androidApp/src/androidMain/assets/files/seed");

const transitData = fs.readFileSync(transitDataPath, "utf8");
const stationCoordinates = fs.readFileSync(stationCoordinatesPath, "utf8");

const colorMap = {
    metroGreen: "#00843D",
    metroRed: "#DA291C",
    metroBlue: "#0072CE",
    tramOrange: "#E87722",
    suburbanPurple: "#6F2DA8",
};

const arrayToLineId = {
    line1: "M1",
    line2: "M2",
    line3: "M3",
    tramT6: "T6",
    tramT7: "T7",
    suburbanA1: "A1",
    suburbanA2: "A2",
    suburbanA3: "A3",
    suburbanA4: "A4",
};

const existingStations = JSON.parse(
    fs.readFileSync(path.join(seedDir, "stations.json"), "utf8"),
);
const existingStationZones = new Map(existingStations.map((station) => [station.id, station.zone]));

function parseLines() {
    const linesBlock = transitData.match(/static let lines: \[TransitLine\] = \[(.*?)\n    \]/s)?.[1] ?? "";
    const lineRegex = /\.init\(id: "([^"]+)", name: "([^"]+)", nameEl: "([^"]+)", terminalA: "([^"]+)", terminalB: "([^"]+)", stationCount: (\d+), color: \.([a-zA-Z]+), type: \.([a-zA-Z]+)\)/g;
    const lines = [];
    let match;
    while ((match = lineRegex.exec(linesBlock)) !== null) {
        lines.push({
            id: match[1],
            name: match[2],
            name_el: match[3],
            type: match[8].toLowerCase(),
            color: colorMap[match[7]],
            terminal_a: match[4],
            terminal_b: match[5],
            station_count: Number(match[6]),
        });
    }
    return lines;
}

function parseStationArrays() {
    const arrayRegex = /static let (\w+): \[\(id: String, name: String, nameEl: String, lat: Double, lon: Double\)\] = \[(.*?)\n    \]/gs;
    const tupleRegex = /\("([^"]+)", "([^"]+)", "([^"]+)", ([\d.]+), ([\d.]+)\)/g;
    const arrays = new Map();
    let arrayMatch;
    while ((arrayMatch = arrayRegex.exec(stationCoordinates)) !== null) {
        const entries = [];
        let tupleMatch;
        while ((tupleMatch = tupleRegex.exec(arrayMatch[2])) !== null) {
            entries.push({
                id: tupleMatch[1],
                name: tupleMatch[2],
                name_el: tupleMatch[3],
                latitude: Number(tupleMatch[4]),
                longitude: Number(tupleMatch[5]),
            });
        }
        arrays.set(arrayMatch[1], entries);
    }
    return arrays;
}

function normalizeLegacyLineId(lineId) {
    if (lineId === "P1") return "A1";
    if (lineId === "P2") return "A4";
    if (lineId === "P3") return "A3";
    return lineId;
}

function parseLineAssociations() {
    const associationsBlock = stationCoordinates.match(/static let lineAssociations: \[String: \[String\]\] = \[(.*?)\n    \]/s)?.[1] ?? "";
    const entryRegex = /"([^"]+)": \[([^\]]+)\]/g;
    const associations = new Map();
    let match;
    while ((match = entryRegex.exec(associationsBlock)) !== null) {
        const lineIds = [...match[2].matchAll(/"([^"]+)"/g)].map((item) => normalizeLegacyLineId(item[1]));
        associations.set(match[1], [...new Set(lineIds)]);
    }
    return associations;
}

function parseAirportOnlyStations() {
    const block = transitData.match(/static let line3AirportOnlyStations: Set<String> = \[(.*?)\]/s)?.[1] ?? "";
    return [...block.matchAll(/"([^"]+)"/g)].map((match) => match[1]);
}

function parseServicePatterns() {
    const servicePatternMap = new Map();
    const caseRegex = /case "([^"]+)"(?:, "([^"]+)")?:\s*(.*?)((?=\n        case )|(?=\n        default:)|$)/gs;
    let caseMatch;
    while ((caseMatch = caseRegex.exec(transitData)) !== null) {
        const primaryLineId = caseMatch[1];
        const caseBody = caseMatch[3];
        const patternRegex = /ServicePattern\(lineId: "([^"]+)", direction: "([^"]+)", frequencyMinutes: (\d+), serviceType: "([^"]+)"\)/g;
        const matches = [...caseBody.matchAll(patternRegex)].map((match) => ({
            line_id: match[1],
            direction: match[2],
            frequency_minutes: Number(match[3]),
            service_type: match[4],
        }));

        if (primaryLineId === "M3") {
            const airportOnlyStations = parseAirportOnlyStations();
            servicePatternMap.set("M3", [
                {
                    ...matches[0],
                    station_ids: airportOnlyStations,
                },
                {
                    ...matches[1],
                    station_ids: airportOnlyStations,
                },
                {
                    ...matches[2],
                    excluded_station_ids: airportOnlyStations,
                },
                {
                    ...matches[3],
                    excluded_station_ids: airportOnlyStations,
                },
                {
                    ...matches[4],
                    excluded_station_ids: airportOnlyStations,
                },
            ]);
            continue;
        }

        servicePatternMap.set(primaryLineId, matches);
    }

    return [...servicePatternMap.values()].flat();
}

function zoneForStation(station, lineId) {
    if (existingStationZones.has(station.id)) return existingStationZones.get(station.id);
    if (station.id.endsWith("_AER")) return 4;
    if (lineId === "A4" || lineId === "A3") return 4;
    if (lineId === "A1" || lineId === "A2") return 2;
    return 1;
}

function buildStations(arrays, associations) {
    const stations = [];
    for (const [arrayName, lineId] of Object.entries(arrayToLineId)) {
        const lineStations = arrays.get(arrayName) ?? [];
        for (const station of lineStations) {
            const lineIds = associations.get(station.id) ?? [lineId];
            stations.push({
                ...station,
                line_ids: [...new Set(lineIds)],
                is_interchange: lineIds.length > 1,
                accessibility: true,
                zone: zoneForStation(station, lineId),
            });
        }
    }
    return stations;
}

function buildRoutes(arrays) {
    return Object.entries(arrayToLineId).map(([arrayName, lineId]) => ({
        line_id: lineId,
        station_ids: (arrays.get(arrayName) ?? []).map((station) => station.id),
    }));
}

function buildTransfers(stations) {
    const transfers = [];
    const seen = new Set();
    for (const station of stations) {
        if (!station.is_interchange || station.line_ids.length < 2) continue;
        for (const fromLineId of station.line_ids) {
            for (const toLineId of station.line_ids) {
                if (fromLineId === toLineId) continue;
                const key = `${station.id}:${fromLineId}:${toLineId}`;
                if (seen.has(key)) continue;
                seen.add(key);
                transfers.push({
                    station_id: station.id,
                    from_line_id: fromLineId,
                    to_line_id: toLineId,
                    walking_minutes: 3,
                });
            }
        }
    }
    return transfers;
}

function buildFrequencies(lines, servicePatterns) {
    const byLine = new Map();
    for (const line of lines) {
        const patterns = servicePatterns.filter((item) => item.line_id === line.id);
        if (!patterns.length) continue;
        const minFrequency = Math.min(...patterns.map((item) => item.frequency_minutes));
        byLine.set(line.id, minFrequency);
    }

    const dayTypes = ["weekday", "friday", "saturday", "sunday"];
    return [...byLine.entries()].flatMap(([lineId, frequencyMinutes]) =>
        dayTypes.map((dayType) => ({
            line_id: lineId,
            day_type: dayType,
            time_range: "05:00-01:00",
            frequency_minutes: frequencyMinutes,
        })),
    );
}

function writeJson(relativePath, payload) {
    const serialized = `${JSON.stringify(payload, null, 2)}\n`;
    const destinations = [
        path.join(seedDir, relativePath),
        path.join(androidSeedDir, relativePath),
    ];
    for (const destination of destinations) {
        fs.mkdirSync(path.dirname(destination), { recursive: true });
        fs.writeFileSync(destination, serialized);
    }
}

const lines = parseLines();
const arrays = parseStationArrays();
const associations = parseLineAssociations();
const servicePatterns = parseServicePatterns();
const stations = buildStations(arrays, associations);
const routes = buildRoutes(arrays);
const transfers = buildTransfers(stations);
const frequencies = buildFrequencies(lines, servicePatterns);

writeJson("lines.json", lines);
writeJson("stations.json", stations);
writeJson("routes.json", routes);
writeJson("transfers.json", transfers);
writeJson("frequencies.json", frequencies);
writeJson("service_patterns.json", servicePatterns);

console.log(`Synced ${lines.length} lines, ${stations.length} station records, and ${routes.length} routes from iOS transit data.`);
