(async function () {
    const ATHENS_CENTER = [37.98, 23.73];
    const INITIAL_ZOOM = 12;
    const DIRECTION_OUTBOUND = "outbound";
    const DIRECTION_INBOUND = "inbound";

    const stationSheet = document.getElementById("stationSheet");
    const stationName = document.getElementById("stationName");
    const stationNameEl = document.getElementById("stationNameEl");
    const lineBadges = document.getElementById("lineBadges");
    const stationMeta = document.getElementById("stationMeta");
    const stationDepartures = document.getElementById("stationDepartures");
    const directionsLink = document.getElementById("directionsLink");
    const sheetClose = document.getElementById("sheetClose");
    const stationSearch = document.getElementById("stationSearch");
    const searchResults = document.getElementById("searchResults");
    const locateButton = document.getElementById("locateButton");
    const zoomInButton = document.getElementById("zoomInButton");
    const zoomOutButton = document.getElementById("zoomOutButton");

    const [stations, lines, routes, servicePatterns, vehicleManifest] = await Promise.all([
        fetch("files/seed/stations.json").then((r) => r.json()),
        fetch("files/seed/lines.json").then((r) => r.json()),
        fetch("files/seed/routes.json").then((r) => r.json()),
        fetch("files/seed/service_patterns.json").then((r) => r.json()),
        fetch("icons/vehicles/manifest.json").then((r) => r.json()).catch(() => ({ directional_icons: [] })),
    ]);

    const lineMap = new Map(lines.map((line) => [line.id, line]));

    const vehicleIconMap = new Map();
    const lineIdToManifestLine = { M1: "M1", M2: "M2", M3: "M3", T6: "T6", T7: "T7", T6T7: "T6T7", A1: "P1", A2: "P1A", A3: "P3", A4: "P2" };
    for (const icon of vehicleManifest.directional_icons) {
        const dir = icon.arrow === "←" ? "inbound" : "outbound";
        vehicleIconMap.set(`${icon.line}_${dir}`, `icons/vehicles/${icon.file}`);
        if (icon.destination === "Airport") {
            vehicleIconMap.set(`${icon.line}_airport`, `icons/vehicles/${icon.file}`);
        }
    }
    const stationMap = new Map(stations.map((station) => [station.id, station]));
    const stationNodes = buildStationNodes(stations);
    const stationNodeMap = new Map(stationNodes.map((station) => [station.id, station]));
    const markers = new Map();
    const liveTrainMarkers = new Map();
    const lineStations = new Map(
        routes.map((route) => [
            route.line_id,
            route.station_ids.map((stationId) => stationMap.get(stationId)).filter(Boolean),
        ])
    );

    const stationIconManifest = await fetch("icons/stations/manifest.json").then((r) => r.json()).catch(() => ({}));
    const stationIconBySid = new Map();
    const lineToManifestDir = { M1: "metro/M1", M2: "metro/M2", M3: "metro/M3", T6: "tram/T6", T7: "tram/T7", A1: "train/P1", A2: "train/P1", A3: "train/P3", A4: "train/P2" };
    for (const route of routes) {
        const mDir = lineToManifestDir[route.line_id];
        if (!mDir) continue;
        route.station_ids.forEach((stationId, index) => {
            const key = `${mDir}/${String(index + 1).padStart(2, "0")}`;
            if (stationIconManifest[key]) {
                stationIconBySid.set(stationId, stationIconManifest[key]);
            }
        });
    }

    const liveTrainList = document.getElementById("liveTrainList");
    const nearbyStationList = document.getElementById("nearbyStationList");
    const popularStationList = document.getElementById("popularStationList");

    const map = L.map("map", {
        zoomControl: false,
        attributionControl: true,
    }).setView(ATHENS_CENTER, INITIAL_ZOOM);

    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
        maxZoom: 19,
        attribution: "&copy; OpenStreetMap contributors",
    }).addTo(map);

    for (const line of lines) {
        const orderedStations = lineStations.get(line.id) || [];
        const latLngs = orderedStations.map((station) => [station.latitude, station.longitude]);
        if (latLngs.length > 1) {
            const smoothed = catmullRomSpline(latLngs, 5);
            L.polyline(smoothed, {
                color: line.color,
                weight: line.type === "suburban" ? 4 : 5,
                opacity: 0.9,
                lineCap: "round",
                lineJoin: "round",
            }).addTo(map);
        }
    }

    function catmullRomSpline(points, numInterpolated) {
        if (points.length < 3) return points;
        const result = [points[0]];
        for (let i = 0; i < points.length - 1; i++) {
            const p0 = points[Math.max(i - 1, 0)];
            const p1 = points[i];
            const p2 = points[i + 1];
            const p3 = points[Math.min(i + 2, points.length - 1)];
            for (let t = 1; t <= numInterpolated; t++) {
                const f = t / (numInterpolated + 1);
                const lat = cr(p0[0], p1[0], p2[0], p3[0], f);
                const lng = cr(p0[1], p1[1], p2[1], p3[1], f);
                result.push([lat, lng]);
            }
            result.push(p2);
        }
        return result;
    }

    function cr(a, b, c, d, t) {
        return 0.5 * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t * t + (-a + 3 * b - 3 * c + d) * t * t * t);
    }

    let selectedStationId = null;
    let userLocation = null;

    for (const station of stationNodes) {
        const marker = L.marker([station.latitude, station.longitude], {
            icon: buildStationIcon(station, false),
            keyboard: false,
        }).addTo(map);

        marker.on("click", () => {
            selectStation(station.id, true);
        });

        markers.set(station.id, marker);
    }

    function buildStationIcon(station, selected) {
        const currentZoom = map.getZoom();
        const stationLines = station.lineIds
            .map((lineId) => lineMap.get(lineId))
            .filter(Boolean);

        if (currentZoom >= 14) {
            const primarySid = station.stationIds[0];
            const svgUrl = stationIconBySid.get(primarySid);
            if (svgUrl) {
                const size = selected ? 36 : 28;
                return L.icon({
                    iconUrl: svgUrl,
                    iconSize: [size, size],
                    iconAnchor: [size / 2, size / 2],
                    className: `station-svg-icon${selected ? " station-svg-icon--selected" : ""}`,
                });
            }
        }

        if (station.isInterchange) {
            const rings = stationLines
                .slice(0, 3)
                .map((line, index) => {
                    const angle = (Math.PI * 2 * index) / Math.max(1, Math.min(stationLines.length, 3));
                    const x = Math.cos(angle) * 5;
                    const y = Math.sin(angle) * 5;
                    return `<span class="station-marker__ring" style="background:${line.color}; transform: translate(${x}px, ${y}px);"></span>`;
                })
                .join("");

            return L.divIcon({
                className: `station-marker station-marker--interchange${selected ? " station-marker--selected" : ""}`,
                html: `<span class="station-marker__group">${rings}<span class="station-marker__center"></span></span>`,
                iconSize: [28, 28],
                iconAnchor: [14, 14],
            });
        }

        const primaryLine = stationLines[0];
        return L.divIcon({
            className: `station-marker${selected ? " station-marker--selected" : ""}`,
            html: `<span class="station-marker__core" style="background:${primaryLine ? primaryLine.color : "#64748b"};"></span>`,
            iconSize: [16, 16],
            iconAnchor: [8, 8],
        });
    }

    function updateMarkerSelection(nextId) {
        if (selectedStationId && markers.has(selectedStationId)) {
            const previous = stationNodeMap.get(selectedStationId);
            markers.get(selectedStationId).setIcon(buildStationIcon(previous, false));
        }

        selectedStationId = nextId;

        if (nextId && markers.has(nextId)) {
            const selected = stationNodeMap.get(nextId);
            markers.get(nextId).setIcon(buildStationIcon(selected, true));
        }
    }

    function lineLabel(station) {
        return station.lineIds
            .map((lineId) => lineMap.get(lineId)?.name || lineId)
            .join(", ");
    }

    function currentAthensParts() {
        const formatter = new Intl.DateTimeFormat("en-GB", {
            timeZone: "Europe/Athens",
            weekday: "short",
            hour: "2-digit",
            minute: "2-digit",
            hour12: false,
        });
        const parts = formatter.formatToParts(new Date());
        const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
        return {
            weekday: values.weekday,
            hour: Number(values.hour),
            minute: Number(values.minute),
        };
    }

    function nowMinutes() {
        const now = currentAthensParts();
        return now.hour * 60 + now.minute;
    }

    function formatTimeFromNow(minutesAway) {
        const now = currentAthensParts();
        const total = (now.hour * 60 + now.minute + minutesAway) % (24 * 60);
        const hour = Math.floor(total / 60);
        const minute = total % 60;
        return `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`;
    }

    function buildStationDepartures(station) {
        const result = [];

        for (const lineId of station.lineIds) {
            const line = lineMap.get(lineId);
            if (!line) continue;
            const stationId = station.stationIdByLineId[lineId] || station.stationIds[0];
            const patterns = servicePatterns.filter((pattern) => {
                if (pattern.line_id !== lineId) return false;
                if (pattern.station_ids && !pattern.station_ids.includes(stationId)) return false;
                if (pattern.excluded_station_ids && pattern.excluded_station_ids.includes(stationId)) return false;
                return true;
            });

            for (const pattern of patterns) {
                for (let index = 1; index <= 4; index += 1) {
                    const minutesAway = pattern.frequency_minutes * index;
                    result.push({
                        line,
                        destination: pattern.direction,
                        time: formatTimeFromNow(minutesAway),
                        minutesAway,
                    });
                }
            }
        }

        return result
            .filter((item, index, array) =>
                array.findIndex((candidate) =>
                    candidate.line.id === item.line.id &&
                    candidate.destination === item.destination &&
                    candidate.time === item.time
                ) === index
            )
            .sort((a, b) => a.minutesAway - b.minutesAway)
            .slice(0, 8);
    }

    function renderDepartures(station) {
        const departures = buildStationDepartures(station);
        if (!departures.length) {
            stationDepartures.innerHTML = '<div class="departure-empty">No departures available for this station right now.</div>';
            return;
        }

        stationDepartures.innerHTML = departures.map((departure) => {
            const minutesLabel = departure.minutesAway <= 0
                ? "Now"
                : departure.minutesAway === 1
                    ? "1 min"
                    : `${departure.minutesAway} min`;
            return `
                <div class="departure-card">
                    <div class="departure-card__header">
                        <div>
                            <div class="departure-card__line">
                                <span class="line-dot" style="background:${departure.line.color};"></span>
                                <span>${departure.line.name}</span>
                            </div>
                            <div class="departure-card__destination">${departure.destination}</div>
                        </div>
                        <div class="departure-card__eta">
                            <div class="departure-card__minutes">${minutesLabel}</div>
                            <div class="departure-card__time">${departure.time}</div>
                        </div>
                    </div>
                </div>
            `;
        }).join("");
    }

    function selectStation(stationId, panToMarker) {
        const station = stationNodeMap.get(stationId);
        if (!station) return;

        updateMarkerSelection(stationId);

        if (panToMarker) {
            map.flyTo([station.latitude, station.longitude], Math.max(map.getZoom(), 14), {
                duration: 0.45,
            });
        }

        stationName.textContent = station.name;
        stationNameEl.textContent = station.nameEl && station.nameEl !== station.name ? station.nameEl : "";

        lineBadges.innerHTML = "";
        for (const lineId of station.lineIds) {
            const line = lineMap.get(lineId);
            if (!line) continue;

            const badge = document.createElement("div");
            badge.className = "line-badge";
            badge.style.background = `${line.color}18`;
            badge.style.color = line.color;
            badge.innerHTML = `<span class="line-dot" style="background:${line.color};"></span><span>${line.name}</span>`;
            lineBadges.appendChild(badge);
        }

        const metaItems = [
            ["Accessibility", station.accessibility ? "Accessible" : "Unknown"],
            ["Zone", `Zone ${station.zone}`],
            ["Interchange", station.isInterchange ? "Yes" : "No"],
            ["Lines", `${station.lineIds.length}`],
            ["Merged", `${station.stationIds.length} records`],
        ];

        stationMeta.innerHTML = metaItems
            .map(([key, value]) => `
                <div class="meta-item">
                    <span class="meta-key">${key}</span>
                    <span class="meta-value">${value}</span>
                </div>
            `)
            .join("");

        renderDepartures(station);
        directionsLink.href = `https://www.google.com/maps/dir/?api=1&destination=${station.latitude},${station.longitude}&travelmode=transit`;

        stationSheet.classList.remove("station-sheet--hidden");
    }

    function clearSelection() {
        updateMarkerSelection(null);
        stationDepartures.innerHTML = "";
        stationSheet.classList.add("station-sheet--hidden");
    }

    function renderSearchResults(results) {
        searchResults.innerHTML = "";
        for (const station of results.slice(0, 8)) {
            const row = document.createElement("div");
            row.className = "search-result";
            row.innerHTML = `
                <div class="search-result-name">${station.name}</div>
                <div class="search-result-meta">${lineLabel(station)}</div>
            `;
            row.addEventListener("click", () => {
                stationSearch.value = station.name;
                searchResults.innerHTML = "";
                selectStation(station.id, true);
            });
            searchResults.appendChild(row);
        }
    }

    stationSearch.addEventListener("input", (event) => {
        const query = event.target.value.trim().toLowerCase();
        if (!query) {
            searchResults.innerHTML = "";
            return;
        }

        const filtered = stationNodes.filter((station) => {
            return station.name.toLowerCase().includes(query) || station.nameEl.toLowerCase().includes(query);
        });

        renderSearchResults(filtered);
    });

    sheetClose.addEventListener("click", () => {
        clearSelection();
    });

    locateButton.addEventListener("click", () => {
        if (!navigator.geolocation) return;

        navigator.geolocation.getCurrentPosition(
            (position) => {
                const lat = position.coords.latitude;
                const lon = position.coords.longitude;
                userLocation = { lat, lon };
                map.flyTo([lat, lon], 14, { duration: 0.5 });
                L.circleMarker([lat, lon], {
                    radius: 9,
                    color: "#0072CE",
                    weight: 3,
                    fillColor: "#73B9FF",
                    fillOpacity: 0.9,
                }).addTo(map);
                updateNearbyPanel();
            },
            () => {
                locateButton.textContent = "Location unavailable";
                setTimeout(() => {
                    locateButton.textContent = "Locate me";
                }, 1800);
            },
            { enableHighAccuracy: true, timeout: 10000 },
        );
    });

    zoomInButton.addEventListener("click", () => {
        map.zoomIn();
    });

    zoomOutButton.addEventListener("click", () => {
        map.zoomOut();
    });

    map.on("click", () => {
        clearSelection();
    });

    let lastZoomBucket = INITIAL_ZOOM >= 14 ? 2 : INITIAL_ZOOM >= 12 ? 1 : 0;
    map.on("zoomend", () => {
        const z = map.getZoom();
        const bucket = z >= 14 ? 2 : z >= 12 ? 1 : 0;
        if (bucket !== lastZoomBucket) {
            lastZoomBucket = bucket;
            for (const [id, marker] of markers) {
                const station = stationNodeMap.get(id);
                if (station) marker.setIcon(buildStationIcon(station, id === selectedStationId));
            }
            for (const [id, marker] of simulatedTrainMarkers) {
                const train = lastSimulatedTrains.find((t) => t.id === id);
                if (train) marker.setIcon(trainMarkerIcon(train));
            }
        }
    });

    const bounds = L.latLngBounds(stationNodes.map((station) => [station.latitude, station.longitude]));
    map.fitBounds(bounds.pad(0.12));

    const simulatedTrainMarkers = new Map();
    let lastSimulatedTrains = [];

    renderPopularPanel();
    updateNearbyPanel();
    connectLiveTrainStream();
    startTrainSimulation();
    setupPanelBehavior();

    function updateNearbyPanel() {
        if (userLocation) {
            const nearby = stationNodes
                .map((station) => ({
                    station,
                    distance: distanceMeters(
                        userLocation.lat,
                        userLocation.lon,
                        station.latitude,
                        station.longitude,
                    ),
                }))
                .sort((a, b) => a.distance - b.distance)
                .slice(0, 6);
            renderStationPanel(nearbyStationList, nearby.map((entry) => entry.station), true, nearby);
        } else {
            renderStationPanel(nearbyStationList, stationNodes
                .slice()
                .sort((a, b) => b.lineIds.length - a.lineIds.length)
                .slice(0, 6), false, []);
        }
    }

    function renderPopularPanel() {
        const popular = stationNodes
            .slice()
            .sort((a, b) => {
                const scoreA = (a.isInterchange ? 10 : 0) + a.lineIds.length;
                const scoreB = (b.isInterchange ? 10 : 0) + b.lineIds.length;
                return scoreB - scoreA;
            })
            .slice(0, 6);
        renderStationPanel(popularStationList, popular, false, []);
    }

    function renderStationPanel(container, stationsToRender, showDistance, distanceEntries) {
        container.innerHTML = stationsToRender.map((station, index) => {
            const distanceLabel = showDistance ? `${Math.round(distanceEntries[index].distance)} m away` : `${station.lineIds.length} lines`;
            return `
                <div class="panel-item" data-station-id="${station.id}">
                    <div class="panel-item__title">${station.name}</div>
                    <div class="panel-item__meta">${distanceLabel}</div>
                </div>
            `;
        }).join("");

        container.querySelectorAll("[data-station-id]").forEach((element) => {
            element.addEventListener("click", () => {
                selectStation(element.getAttribute("data-station-id"), true);
            });
        });
    }

    function connectLiveTrainStream() {
        const source = new EventSource("https://api-syrmos.peterdsp.dev/api/train-stream");
        source.addEventListener("trainPositionsUx", (event) => {
            try {
                const payload = JSON.parse(event.data);
                updateLiveTrains(payload.positions || []);
            } catch (_error) {
            }
        });
        source.onerror = () => {
            renderLiveTrains([]);
        };
    }

    function updateLiveTrains(positions) {
        const trains = positions
            .map((position) => {
                const inferred = inferLineId(position);
                if (!inferred || position.lat == null || position.lng == null) return null;
                return {
                    id: position.id || position.trainId || position.trainNumber || position.name,
                    lineId: inferred,
                    trainNumber: position.trainNumber || position.name || position.locomotiveNumber || "Train",
                    origin: position.origin || "",
                    destination: position.destination || "",
                    nextStation: position.nextStation || "",
                    delay: position.delay || 0,
                    speed: position.speed || null,
                    lat: position.lat,
                    lng: position.lng,
                    timestamp: position.timestamp || position.receivedAt || "",
                };
            })
            .filter(Boolean);

        renderLiveTrains(trains);
    }

    function renderLiveTrains(trains) {
        liveTrainMarkers.forEach((marker) => marker.remove());
        liveTrainMarkers.clear();

        if (trains.length) {
            const suburbanHtml = trains.slice(0, 5).map((train) => {
                const line = lineMap.get(train.lineId);
                return `
                    <div class="panel-item" data-live-suburban>
                        <div class="panel-item__title">🚆 ${line ? line.name : train.lineId} ${train.trainNumber}</div>
                        <div class="panel-item__meta">${train.origin || "Live"} to ${train.destination || "unknown"}${train.nextStation ? `, next ${train.nextStation}` : ""}</div>
                    </div>
                `;
            }).join("");
            const existing = liveTrainList.innerHTML;
            if (!existing.includes('data-live-suburban')) {
                liveTrainList.innerHTML = existing + suburbanHtml;
            }
        }

        for (const train of trains) {
            const line = lineMap.get(train.lineId);
            const manifestLine = lineIdToManifestLine[train.lineId] || train.lineId;
            const dir = train.destination && /αεροδρομ|airport/i.test(train.destination) ? "outbound" : "inbound";
            const svgKey = vehicleIconMap.get(`${manifestLine}_${dir}`) || vehicleIconMap.get(`${manifestLine}_outbound`);
            const icon = svgKey
                ? L.icon({ iconUrl: svgKey, iconSize: [38, 38], iconAnchor: [19, 19], className: "sim-train-marker" })
                : L.divIcon({
                    className: "train-marker",
                    html: `<span class="train-marker__ring" style="background:${line ? line.color : "#0072CE"}"></span>`,
                    iconSize: [22, 22],
                    iconAnchor: [11, 11],
                });
            const marker = L.marker([train.lat, train.lng], {
                icon,
                keyboard: false,
                zIndexOffset: 1000,
            }).addTo(map);
            marker.bindTooltip(
                `${line ? line.name : train.lineId} ${train.trainNumber}<br>${train.origin || "?"} → ${train.destination || "?"}`,
                { direction: "top", offset: [0, -10] }
            );
            liveTrainMarkers.set(train.id, marker);
        }
    }

    function inferLineId(position) {
        const text = `${position.origin || ""} ${position.destination || ""} ${position.nextStation || ""} ${position.corridor || ""}`.toLowerCase();
        if (text.includes("ανω λιοσια") && text.includes("αεροδρομ")) return "A2";
        if (text.includes("αθην") && text.includes("χαλκιδ")) return "A3";
        if (text.includes("πειραι") && text.includes("κιατ")) return "A4";
        const corridor = (position.corridor || "").toLowerCase();
        if (corridor === "pirair" || (text.includes("πειραι") && text.includes("αεροδρομ"))) return "A1";
        return null;
    }

    function distanceMeters(lat1, lon1, lat2, lon2) {
        const r = 6371000;
        const toRad = (value) => (value * Math.PI) / 180;
        const dLat = toRad(lat2 - lat1);
        const dLon = toRad(lon2 - lon1);
        const a = Math.sin(dLat / 2) ** 2 +
            Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    function buildStationNodes(rawStations) {
        const grouped = new Map();
        const sortedStations = rawStations
            .slice()
            .sort((a, b) => a.latitude - b.latitude || a.longitude - b.longitude || String(a.id).localeCompare(String(b.id)));

        for (const station of sortedStations) {
            const key = stationClusterKey(station);
            if (!grouped.has(key)) {
                grouped.set(key, []);
            }
            grouped.get(key).push(station);
        }

        return [...grouped.values()]
            .flatMap((group) => clusterByProximity(group).map((cluster, index) => {
                const primary = cluster[0];
                const lineIds = [...new Set(cluster.flatMap((station) => station.line_ids))];
                const stationIdByLineId = {};
                for (const station of cluster) {
                    for (const lineId of station.line_ids) {
                        if (!stationIdByLineId[lineId]) {
                            stationIdByLineId[lineId] = station.id;
                        }
                    }
                }
                const latitude = cluster.reduce((sum, station) => sum + station.latitude, 0) / cluster.length;
                const longitude = cluster.reduce((sum, station) => sum + station.longitude, 0) / cluster.length;
                return {
                    id: `${stationClusterKey(primary)}_${index}_${roundKey(latitude)}_${roundKey(longitude)}`,
                    stationIds: cluster.map((station) => station.id),
                    stationIdByLineId,
                    name: primary.name,
                    nameEl: primary.name_el,
                    latitude,
                    longitude,
                    lineIds,
                    isInterchange: lineIds.length > 1 || cluster.some((station) => station.is_interchange),
                    accessibility: cluster.some((station) => station.accessibility),
                    zone: Math.min(...cluster.map((station) => station.zone || 1)),
                };
            }))
            .sort((a, b) => a.name.localeCompare(b.name));
    }

    function roundKey(value) {
        return Math.round(value * 1000000);
    }

    function stationClusterKey(station) {
        const nameKey = normalizeStationKey(station.name || "");
        const nameElKey = normalizeStationKey(station.name_el || "");
        return [nameKey, nameElKey]
            .filter(Boolean)
            .sort()
            .join("|");
    }

    function clusterByProximity(stations, radiusMeters = 300) {
        const clusters = [];
        for (const station of stations) {
            const match = clusters.find((cluster) =>
                cluster.some((other) =>
                    distanceMeters(other.latitude, other.longitude, station.latitude, station.longitude) <= radiusMeters,
                ),
            );
            if (match) {
                match.push(station);
            } else {
                clusters.push([station]);
            }
        }
        return clusters;
    }

    function normalizeStationKey(value) {
        return String(value)
            .toLowerCase()
            .normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .replace(/[άέήίϊΐόύϋΰώ]/g, (match) => ({
                ά: "α",
                έ: "ε",
                ή: "η",
                ί: "ι",
                ϊ: "ι",
                ΐ: "ι",
                ό: "ο",
                ύ: "υ",
                ϋ: "υ",
                ΰ: "υ",
                ώ: "ω",
            }[match]))
            .replace(/[^a-z0-9\u0370-\u03ff]+/g, "");
    }
    function setupPanelBehavior() {
        const panel = document.getElementById("insightPanel");
        const peek = document.getElementById("panelPeek");
        const peekText = document.getElementById("panelPeekText");
        if (!panel || !peek) return;

        peek.addEventListener("click", () => {
            panel.classList.toggle("insight-panel--expanded");
        });

        map.on("click", () => {
            panel.classList.remove("insight-panel--expanded");
        });

        const topBar = document.querySelector(".top-bar");
        if (topBar && window.matchMedia("(min-width: 721px)").matches) {
            const observer = new ResizeObserver(() => {
                panel.style.top = (topBar.offsetHeight + topBar.offsetTop + 12) + "px";
            });
            observer.observe(topBar);
        }

        window._updatePeekText = function (count) {
            if (peekText) {
                peekText.textContent = count > 0 ? `${count} trains active` : "Live trains";
            }
        };
    }

    function startTrainSimulation() {
        let lastPanelUpdate = 0;
        let lastMapUpdate = 0;
        function animateTrains(timestamp) {
            if (timestamp - lastMapUpdate > 250) {
                const trains = simulateAllTrains();
                renderSimulatedTrainsOnMap(trains);
                lastMapUpdate = timestamp;
                if (timestamp - lastPanelUpdate > 2000) {
                    renderSimulatedTrainsInPanel(trains);
                    lastPanelUpdate = timestamp;
                }
            }
            requestAnimationFrame(animateTrains);
        }
        requestAnimationFrame(animateTrains);
    }

    function smoothEase(t) {
        if (t < 0.15) {
            const x = t / 0.15;
            return x * x * 0.15;
        } else if (t > 0.85) {
            const x = (t - 0.85) / 0.15;
            return 0.85 + (1 - (1 - x) * (1 - x)) * 0.15;
        }
        return t;
    }

    function simulateAllTrains() {
        const now = new Date();
        const athensNow = new Date(now.toLocaleString("en-US", { timeZone: "Europe/Athens" }));
        const fractionalSeconds = athensNow.getSeconds() + (now.getMilliseconds() / 1000);
        let nowMins = athensNow.getHours() * 60 + athensNow.getMinutes() + fractionalSeconds / 60;
        if (nowMins < 300) nowMins += 1440;
        if (nowMins < 300 || nowMins > 1500) return [];

        const TRAVEL = { metro: 1.8, tram: 2.2 };
        const DWELL = { metro: 0.5, tram: 0.4 };
        const DWELL_TERMINAL = 1.0;
        const FREQ = { M1: 5, M2: 4, M3: 5, T6: 9, T7: 12 };
        const result = [];

        for (const line of lines) {
            if (line.type === "suburban") continue;
            const orderedStations = lineStations.get(line.id) || [];
            if (orderedStations.length < 2) continue;

            const travelMins = TRAVEL[line.type] || 2;
            const dwellMins = DWELL[line.type] || 0.5;
            const freq = FREQ[line.id] || 7;

            for (const direction of ["outbound", "inbound"]) {
                const stns = direction === "outbound" ? orderedStations : [...orderedStations].reverse();

                const timings = [];
                let cumulative = 0;
                for (let i = 0; i < stns.length; i++) {
                    const arrival = cumulative;
                    const dwell = (i === 0 || i === stns.length - 1) ? DWELL_TERMINAL : dwellMins;
                    timings.push({ station: stns[i], arrival, departure: arrival + dwell });
                    if (i < stns.length - 1) cumulative = arrival + dwell + travelMins;
                }

                const tripDuration = timings[timings.length - 1].arrival;
                const offset = direction === "inbound" ? freq / 2 : 0;
                let departureTime = 300 + offset;
                let trainIdx = 0;

                while (departureTime <= 1500) {
                    const elapsed = nowMins - departureTime;
                    if (elapsed >= 0 && elapsed <= tripDuration) {
                        let segIdx = 0;
                        for (let i = timings.length - 1; i >= 0; i--) {
                            if (timings[i].departure <= elapsed) { segIdx = i; break; }
                        }
                        segIdx = Math.min(segIdx, timings.length - 2);
                        const from = timings[segIdx];
                        const to = timings[segIdx + 1];

                        let lat, lng;
                        if (elapsed < from.departure) {
                            lat = from.station.latitude;
                            lng = from.station.longitude;
                        } else {
                            const travelStart = from.departure;
                            const travelEnd = to.arrival;
                            const travelDuration = travelEnd - travelStart;
                            const rawFrac = travelDuration > 0
                                ? Math.min(Math.max((elapsed - travelStart) / travelDuration, 0), 1)
                                : 0;
                            const frac = smoothEase(rawFrac);
                            lat = from.station.latitude + (to.station.latitude - from.station.latitude) * frac;
                            lng = from.station.longitude + (to.station.longitude - from.station.longitude) * frac;
                        }

                        const isAirport = line.id === "M3" && direction === "outbound" && segIdx >= orderedStations.length - 6;
                        const dest = direction === "outbound" ? line.terminal_b : line.terminal_a;

                        result.push({
                            id: `${line.id}_${direction}_${trainIdx}`,
                            line,
                            direction,
                            destination: dest,
                            fromStation: from.station.name,
                            toStation: to.station.name,
                            lat,
                            lng,
                            isAirport,
                            progress: elapsed / tripDuration,
                        });
                    }
                    departureTime += freq;
                    trainIdx++;
                }
            }
        }
        return result;
    }

    function trainMarkerIcon(train) {
        const zoom = map.getZoom();
        const manifestLine = lineIdToManifestLine[train.line.id] || train.line.id;

        if (zoom < 12) {
            const color = train.line.color || "#0072CE";
            return L.divIcon({
                className: "train-marker",
                html: `<span class="train-marker__ring" style="background:${color}"></span>`,
                iconSize: [14, 14],
                iconAnchor: [7, 7],
            });
        }

        let svgKey;
        if (train.isAirport) {
            svgKey = vehicleIconMap.get(`${manifestLine}_airport`) || vehicleIconMap.get(`${manifestLine}_outbound`);
        } else {
            svgKey = vehicleIconMap.get(`${manifestLine}_${train.direction}`);
        }
        const size = zoom >= 14 ? 38 : 28;
        if (svgKey) {
            return L.icon({
                iconUrl: svgKey,
                iconSize: [size, size],
                iconAnchor: [size / 2, size / 2],
                className: "sim-train-marker",
            });
        }
        const genericType = train.line.type === "tram" ? "tram" : train.line.type === "suburban" ? "train" : "metro";
        return L.icon({
            iconUrl: `icons/vehicles/generic_vehicle/vehicle_${genericType}.svg`,
            iconSize: [size, size],
            iconAnchor: [size / 2, size / 2],
            className: "sim-train-marker",
        });
    }

    function renderSimulatedTrainsOnMap(trains) {
        lastSimulatedTrains = trains;
        const activeIds = new Set(trains.map((t) => t.id));

        simulatedTrainMarkers.forEach((marker, id) => {
            if (!activeIds.has(id)) {
                marker.remove();
                simulatedTrainMarkers.delete(id);
            }
        });

        for (const train of trains) {
            if (simulatedTrainMarkers.has(train.id)) {
                simulatedTrainMarkers.get(train.id).setLatLng([train.lat, train.lng]);
            } else {
                const marker = L.marker([train.lat, train.lng], {
                    icon: trainMarkerIcon(train),
                    keyboard: false,
                    zIndexOffset: 900,
                }).addTo(map);

                marker.bindTooltip(
                    `${train.line.name} → ${train.destination}<br>Near ${train.fromStation}`,
                    { direction: "top", offset: [0, -10] }
                );

                simulatedTrainMarkers.set(train.id, marker);
            }
        }
    }

    function renderSimulatedTrainsInPanel(trains) {
        if (window._updatePeekText) window._updatePeekText(trains.length);
        if (!trains.length) return;

        const perLine = new Map();
        for (const train of trains) {
            const key = `${train.line.id}_${train.direction}`;
            if (!perLine.has(key)) perLine.set(key, train);
        }
        const display = [...perLine.values()].slice(0, 10);

        const panelHtml =
            `<div class="panel-item"><div class="panel-item__meta" style="font-weight:600;color:#1a1a2e;">${trains.length} trains active</div></div>` +
            display.map((train) => {
                const icon = train.isAirport ? "✈" : train.line.type === "tram" ? "🚊" : "🚇";
                return `
                    <div class="panel-item">
                        <div class="panel-item__title">${icon} ${train.line.name} → ${train.destination}</div>
                        <div class="panel-item__meta">Near ${train.fromStation} · Next: ${train.toStation}</div>
                    </div>
                `;
            }).join("");

        liveTrainList.innerHTML = panelHtml;
    }

    const panelStyle = document.createElement("style");
    panelStyle.textContent = `
        @media (min-width: 721px) {
            .insight-panel { left: 16px !important; right: auto !important; width: min(280px, calc(100vw - 32px)) !important; }
            .panel-card { padding: 12px !important; border-radius: 16px !important; }
            .panel-list { max-height: 22vh !important; }
            .panel-item { padding: 8px 10px !important; border-radius: 12px !important; }
            .panel-item__title { font-size: 13px !important; }
            .panel-item__meta { font-size: 11px !important; }
        }
    `;
    document.head.appendChild(panelStyle);
})();
