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

    const [stations, lines, routes, servicePatterns] = await Promise.all([
        fetch("files/seed/stations.json").then((r) => r.json()),
        fetch("files/seed/lines.json").then((r) => r.json()),
        fetch("files/seed/routes.json").then((r) => r.json()),
        fetch("files/seed/service_patterns.json").then((r) => r.json()),
    ]);

    const lineMap = new Map(lines.map((line) => [line.id, line]));
    const stationMap = new Map(stations.map((station) => [station.id, station]));
    const markers = new Map();
    const lineStations = new Map(
        routes.map((route) => [
            route.line_id,
            route.station_ids.map((stationId) => stationMap.get(stationId)).filter(Boolean),
        ])
    );

    const map = L.map("map", {
        zoomControl: true,
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
            L.polyline(latLngs, {
                color: line.color,
                weight: line.type === "suburban" ? 4 : 5,
                opacity: 0.9,
            }).addTo(map);
        }
    }

    let selectedStationId = null;

    for (const station of stations) {
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
        const stationLines = station.line_ids
            .map((lineId) => lineMap.get(lineId))
            .filter(Boolean);

        if (station.is_interchange) {
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
            const previous = stations.find((station) => station.id === selectedStationId);
            markers.get(selectedStationId).setIcon(buildStationIcon(previous, false));
        }

        selectedStationId = nextId;

        if (nextId && markers.has(nextId)) {
            const selected = stations.find((station) => station.id === nextId);
            markers.get(nextId).setIcon(buildStationIcon(selected, true));
        }
    }

    function lineLabel(station) {
        return station.line_ids
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

        for (const lineId of station.line_ids) {
            const line = lineMap.get(lineId);
            if (!line) continue;
            const patterns = servicePatterns.filter((pattern) => {
                if (pattern.line_id !== lineId) return false;
                if (pattern.station_ids && !pattern.station_ids.includes(station.id)) return false;
                if (pattern.excluded_station_ids && pattern.excluded_station_ids.includes(station.id)) return false;
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
        const station = stationMap.get(stationId);
        if (!station) return;

        updateMarkerSelection(stationId);

        if (panToMarker) {
            map.flyTo([station.latitude, station.longitude], Math.max(map.getZoom(), 14), {
                duration: 0.45,
            });
        }

        stationName.textContent = station.name;
        stationNameEl.textContent = station.name_el && station.name_el !== station.name ? station.name_el : "";

        lineBadges.innerHTML = "";
        for (const lineId of station.line_ids) {
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
            ["Interchange", station.is_interchange ? "Yes" : "No"],
            ["Lines", `${station.line_ids.length}`],
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

        const filtered = stations.filter((station) => {
            return station.name.toLowerCase().includes(query) || station.name_el.toLowerCase().includes(query);
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
                map.flyTo([lat, lon], 14, { duration: 0.5 });
                L.circleMarker([lat, lon], {
                    radius: 9,
                    color: "#0072CE",
                    weight: 3,
                    fillColor: "#73B9FF",
                    fillOpacity: 0.9,
                }).addTo(map);
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

    map.on("click", () => {
        clearSelection();
    });

    const bounds = L.latLngBounds(stations.map((station) => [station.latitude, station.longitude]));
    map.fitBounds(bounds.pad(0.12));
})();
