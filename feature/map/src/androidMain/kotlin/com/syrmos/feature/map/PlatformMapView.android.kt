package com.syrmos.feature.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.syrmos.core.designsystem.component.toComposeColor
import com.syrmos.core.common.map.MapDesignTokens
import com.syrmos.core.model.transit.Direction
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.model.transit.SimulatedTrain
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import androidx.compose.ui.graphics.luminance
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

// Flat, label-free minimal base like the railway.gov.gr live tracker: keyless
// Esri "Gray Canvas" (light / dark), no streets, no place labels. Replaces CARTO
// Positron/Dark-Matter, which started returning "API KEY REQUIRED" placeholder
// tiles — the same switch iOS and the web map made. Our coloured line network +
// station/train markers carry the structure, so the map reads as a clean transit
// diagram. Follows the app theme, not the language (there are no labels).
private fun esriGrayNoLabels(name: String, service: String): ITileSource = object : XYTileSource(
    name, 0, 16, 256, "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/$service/MapServer/tile/"),
    "© OpenStreetMap, Tiles © Esri",
) {
    // Esri's tile path order is {z}/{y}/{x} (y before x), unlike the XYZ default
    // {z}/{x}/{y}. Gray Canvas is native to z16; osmdroid upsamples beyond it.
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex)
}
private val ESRI_LIGHT: ITileSource = esriGrayNoLabels("EsriGrayLight", "World_Light_Gray_Base")
private val ESRI_DARK: ITileSource = esriGrayNoLabels("EsriGrayDark", "World_Dark_Gray_Base")

private fun tileSourceFor(dark: Boolean): ITileSource = if (dark) ESRI_DARK else ESRI_LIGHT

/**
 * OSM-derived rail route geometry shipped at `assets/files/seed/schedules-v2/shapes.json`.
 * Loaded once at first map mount and used in place of catmullRomSpline(stations)
 * so polylines follow real track (T7 Piraeus loop, M3 airport branch, A4 Megara curve).
 * Falls back to spline-of-stations when a line has no shape.
 */
@Serializable
private data class RouteShape(val coordinates: List<List<Double>>)

@Serializable
private data class RouteShapesPayload(val shapes: Map<String, RouteShape>)

private fun snapToPolyline(lat: Double, lng: Double, lineId: String, shapes: Map<String, List<GeoPoint>>): GeoPoint {
    val poly = shapes[lineId] ?: return GeoPoint(lat, lng)
    if (poly.size < 2) return GeoPoint(lat, lng)
    var bestDist = Double.MAX_VALUE
    var bestLat = lat
    var bestLng = lng
    for (i in 0 until poly.size - 1) {
        val ax = poly[i].latitude; val ay = poly[i].longitude
        val bx = poly[i + 1].latitude; val by = poly[i + 1].longitude
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        var t = if (len2 > 0) ((lat - ax) * dx + (lng - ay) * dy) / len2 else 0.0
        t = t.coerceIn(0.0, 1.0)
        val px = ax + t * dx; val py = ay + t * dy
        val dlat = lat - px; val dlng = lng - py
        val d = dlat * dlat + dlng * dlng
        if (d < bestDist) { bestDist = d; bestLat = px; bestLng = py }
    }
    return GeoPoint(bestLat, bestLng)
}

private fun loadRouteShapes(context: Context): Map<String, List<GeoPoint>> {
    return runCatching {
        val body = context.assets.open("files/seed/schedules-v2/shapes.json").bufferedReader().use { it.readText() }
        val payload = Json { ignoreUnknownKeys = true }.decodeFromString<RouteShapesPayload>(body)
        payload.shapes.mapValues { (_, shape) ->
            shape.coordinates.mapNotNull { pair ->
                if (pair.size >= 2) GeoPoint(pair[0], pair[1]) else null
            }
        }
    }.getOrDefault(emptyMap())
}

/**
 * Per-line colour, read straight from `lines.json`'s raw hex - the SAME source
 * the web map reads. The KMP `Line.color` collapses every line into one of five
 * `LineColor` enum buckets, so national / Thessaloniki / Patras lines all came
 * out purple and Athens hues drifted from web. Reading the hex here restores the
 * true per-line colour so Android polylines, dots and triangles match web
 * exactly. Falls back to the enum colour when a line is missing from the file.
 */
@Serializable
private data class SeedLineColor(val id: String, val color: String? = null)

@Serializable
private data class SeedLinesColorPayload(val lines: List<SeedLineColor> = emptyList())

private fun loadLineColors(context: Context): Map<String, Int> {
    return runCatching {
        val body = context.assets.open("files/seed/schedules-v2/lines.json").bufferedReader().use { it.readText() }
        val payload = Json { ignoreUnknownKeys = true }.decodeFromString<SeedLinesColorPayload>(body)
        payload.lines.mapNotNull { l -> l.color?.let { l.id to parseHex(it) } }.toMap()
    }.getOrDefault(emptyMap())
}

private fun resolveVehicleDrawable(context: Context, train: SimulatedTrain): android.graphics.drawable.Drawable? {
    val drawableName = vehicleDrawableName(train) ?: return null
    val resId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)
    if (resId == 0) return null
    return androidx.core.content.ContextCompat.getDrawable(context, resId)
}

private fun vehicleDrawableName(train: SimulatedTrain): String? {
    val isInbound = train.direction == Direction.INBOUND
    return when (train.lineId) {
        "M1" -> if (isInbound) "ic_metro_m1_left_to_piraeus" else "ic_metro_m1_right_to_kifissia"
        "M2" -> if (isInbound) "ic_metro_m2_left_to_anthoupoli" else "ic_metro_m2_right_to_elliniko"
        "M3" -> when {
            train.isAirportService -> "ic_metro_m3_right_to_airport"
            isInbound -> "ic_metro_m3_left_to_dimotiko_theatro"
            else -> "ic_metro_m3_right_to_doukissis_plakentias"
        }
        "T6" -> if (isInbound) "ic_tram_t6_left_to_syntagma" else "ic_tram_t6_right_to_pikrodafni"
        "T7" -> if (isInbound) "ic_tram_t7_left_to_akti_posidonos" else "ic_tram_t7_right_to_asklipiio_voulas"
        else -> null
    }
}

private fun resolveStationDrawable(
    context: Context,
    stationId: String,
    lineStations: Map<String, List<com.syrmos.core.model.transit.Station>>,
): android.graphics.drawable.Drawable? {
    for ((lineId, stations) in lineStations) {
        val index = stations.indexOfFirst { it.id == stationId }
        if (index < 0) continue
        val prefix = when {
            lineId.startsWith("M") -> "ic_metro_${lineId.lowercase()}"
            lineId.startsWith("T") -> "ic_tram_${lineId.lowercase()}"
            else -> "ic_train_${lineId.lowercase()}"
        }
        val num = String.format("%02d", index + 1)
        val pattern = "${prefix}_${num}_"
        val resId = findDrawableByPrefix(context, pattern)
        if (resId != 0) return androidx.core.content.ContextCompat.getDrawable(context, resId)
    }
    return null
}

private val drawableNameCache = mutableMapOf<String, Int>()

private fun findDrawableByPrefix(context: Context, prefix: String): Int {
    drawableNameCache[prefix]?.let { return it }
    val fields = try {
        Class.forName("${context.packageName}.R\$drawable").fields
    } catch (_: Exception) { return 0 }
    for (field in fields) {
        if (field.name.startsWith(prefix)) {
            val resId = field.getInt(null)
            drawableNameCache[prefix] = resId
            return resId
        }
    }
    drawableNameCache[prefix] = 0
    return 0
}

@Composable
internal actual fun PlatformMapView(
    uiState: MapUiState,
    onStationSelected: (String) -> Unit,
    onTrainSelected: (String) -> Unit,
    modifier: Modifier,
    initialScale: Float,
) {
    val context = LocalContext.current
    var hasFittedBounds by remember { mutableStateOf(false) }
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val routeShapes = remember { loadRouteShapes(context) }
    val lineColors = remember { loadLineColors(context) }
    val lineOverlays = remember { mutableListOf<Polyline>() }
    val stationMarkers = remember { mutableMapOf<String, Marker>() }
    val trainMarkers = remember { mutableMapOf<String, Marker>() }
    val liveTrainMarkers = remember { mutableMapOf<String, Marker>() }
    // 0 = country, 1 = city, 2 = district, 3 = street. Mirrors web + iOS buckets.
    var zoomBucket by remember { mutableStateOf(2) }
    // Zoom-tiered decluttering, four bands (mirrors web + iOS): 0 = country
    // (lines only, no dots), 1 = near-country (major cross-modal hubs), 2 =
    // regional (all interchanges), 3 = city (every stop).
    var mapBand by remember { mutableStateOf(3) }
    // Bumped when the map pans/zooms so the station effect re-culls to the new
    // viewport. Scroll is throttled (see the listener) so a drag doesn't storm.
    var mapMoveTick by remember { mutableStateOf(0) }

    val appLang by com.syrmos.core.common.LocalizationManager.language.collectAsState()
    // Follow the applied Syrmos theme (respects the in-app light/dark/system
    // override, not just the system setting) to pick the matching flat base map.
    val darkMap = androidx.compose.material3.MaterialTheme.colorScheme.surface.luminance() < 0.5f

    DisposableEffect(context) {
        Configuration.getInstance().userAgentValue = context.packageName
        onDispose { }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(tileSourceFor(darkMap))
                setMultiTouchControls(true)
                controller.setZoom(12.0)
                controller.setCenter(GeoPoint(37.98, 23.73))
                zoomController.setVisibility(
                    org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
                )
                minZoomLevel = 9.0
                maxZoomLevel = 18.0
                val locationOverlay = org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay(
                    org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider(ctx), this
                )
                locationOverlay.enableMyLocation()
                overlays.add(locationOverlay)
                addMapListener(object : org.osmdroid.events.MapListener {
                    // Throttle so a drag (onScroll fires per frame) re-culls at most
                    // ~3x/second instead of every frame.
                    private var lastMoveBump = 0L
                    override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                        val now = System.currentTimeMillis()
                        if (now - lastMoveBump > 350) { lastMoveBump = now; mapMoveTick++ }
                        return false
                    }
                    override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                        val z = event?.zoomLevel ?: zoomLevelDouble
                        val next = when {
                            z >= 14.0 -> 3
                            z >= 12.0 -> 2
                            z >= 10.0 -> 1
                            else -> 0
                        }
                        if (next != zoomBucket) zoomBucket = next
                        val band = when {
                            z >= com.syrmos.core.common.map.MapDesignTokens.MINOR_STOP_MIN_ZOOM -> 3
                            z >= com.syrmos.core.common.map.MapDesignTokens.MAJOR_HUB_MIN_ZOOM -> 2
                            z <= com.syrmos.core.common.map.MapDesignTokens.LINES_ONLY_MAX_ZOOM -> 0
                            else -> 1
                        }
                        if (band != mapBand) mapBand = band
                        mapMoveTick++
                        return false
                    }
                })
                mapViewRef.value = this
            }
        },
        update = { mv ->
            // Re-apply the base map when the theme flips (light <-> dark) so the
            // Map tab swaps to the matching flat tiles without a process restart.
            // osmdroid handles the swap and invalidates the tile cache for us.
            val desired = tileSourceFor(darkMap)
            if (mv.tileProvider.tileSource !== desired) {
                mv.setTileSource(desired)
                mv.invalidate()
            }
        },
    )

    val mapView = mapViewRef.value ?: return

    val visualOverrides = org.koin.compose.koinInject<com.syrmos.core.data.sync.VisualOverridesRepository>()
    val displayOverrides by visualOverrides.lineDisplay.collectAsState()
    LaunchedEffect(uiState.lines, uiState.lineStations, displayOverrides) {
        lineOverlays.forEach { mapView.overlays.remove(it) }
        lineOverlays.clear()

        uiState.lines.forEach { line ->
            val lineStations = uiState.lineStations[line.id].orEmpty()
            if (lineStations.size < 2) return@forEach

            // Buses draw straight segments through their actual ordered stops,
            // chosen BEFORE any bundled OSM shape. A bus shape can cover only
            // part of the route (the Patras University loop shape omits its
            // western stops, stranding those markers), and a spline overshoots
            // on tight loops. Straight segments connect every stop; the stops
            // are correctly ordered because DataSeeder seeds station_line rows
            // for routes.json-absent lines from the nested lines.json stations.
            // Rail/tram still prefer real OSM track geometry so the Piraeus
            // loop, M3 airport branch and suburban curves render accurately.
            val osmShape = routeShapes[line.id]
            val smoothed: List<GeoPoint> = when {
                line.type == LineType.BUS ->
                    lineStations.map { GeoPoint(it.latitude, it.longitude) }
                osmShape != null && osmShape.size >= 2 -> osmShape
                else -> catmullRomSpline(lineStations.map { GeoPoint(it.latitude, it.longitude) })
            }
            val override = displayOverrides[line.id]
            // A line that is built but not open still draws, because the track is
            // real, but it reads as inert: muted grey, thinner, dashed. It carries
            // no trains or departures either (handled in the simulator/projector).
            val underConstruction = !line.isOperational
            val isBus = line.type == LineType.BUS
            // Raw per-line hex from lines.json (matches web); enum colour only if
            // the line is somehow absent from the file.
            val color = when {
                underConstruction -> android.graphics.Color.parseColor(com.syrmos.core.common.map.MapDesignTokens.GREYED_COLOR)
                else -> override?.strokeColor?.let { parseHex(it) }
                    ?: lineColors[line.id]
                    ?: line.color.toComposeColor().toArgb()
            }
            // Web weights: metro/tram 5, suburban/bus 4, under-construction 3.
            val width = when {
                underConstruction -> 3f
                else -> override?.strokeWeight?.toFloat()
                    ?: (if (line.type == LineType.SUBURBAN || line.type == LineType.SCENIC || isBus) 4f else 5f)
            }
            val polyline = Polyline().apply {
                outlinePaint.color = color
                outlinePaint.strokeWidth = width
                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                if (underConstruction) {
                    outlinePaint.alpha = 140
                    outlinePaint.pathEffect = android.graphics.DashPathEffect(
                        com.syrmos.core.common.map.MapDesignTokens.GREYED_DASH.map { it.toFloat() }.toFloatArray(), 0f
                    )
                } else {
                    // In-service lines at web's 0.9 opacity. Rail-replacement buses
                    // draw dashed (web busDash "2 7") so they read as a bus, not rail.
                    outlinePaint.alpha = 230
                    val dash = override?.strokeDash?.split(" ")?.mapNotNull { it.toFloatOrNull() }
                        ?: if (isBus) com.syrmos.core.common.map.MapDesignTokens.BUS_DASH.map { it.toFloat() } else null
                    if (dash != null && dash.size >= 2) {
                        outlinePaint.pathEffect = android.graphics.DashPathEffect(dash.toFloatArray(), 0f)
                    }
                }
                setPoints(smoothed)
            }
            lineOverlays.add(polyline)
            mapView.overlays.add(0, polyline)
        }
        mapView.invalidate()
    }

    // On-device audit: warn if any Athens station falls outside the Attica box
    // (37.7-38.55 N, 22.65-24.15 E; A4 Kiato W to A3 Chalkida N, covers the
    // Thebes/Boeotia corridor). Definitive "dots in the sea" check - reads the
    // real coordinate, so a quiet logcat means the bundled data is clean.
    LaunchedEffect(uiState.mapStations) {
        val athensLineIds = setOf("M1", "M2", "M3", "M3_AIR", "T6", "T7", "A1", "A2", "A3", "A4")
        val offshore = uiState.mapStations.filter { s ->
            s.lineIds.any { it in athensLineIds } &&
                (s.latitude < 37.70 || s.latitude > 38.55 || s.longitude < 22.65 || s.longitude > 24.15)
        }
        if (offshore.isEmpty()) {
            android.util.Log.d("Syrmos", "station audit: all Athens stations inside Attica box (${uiState.mapStations.size} total)")
        } else {
            offshore.forEach { android.util.Log.w("Syrmos", "station ${it.id} OUTSIDE Attica: ${it.latitude}, ${it.longitude}") }
        }
    }

    LaunchedEffect(uiState.mapStations, uiState.selectedStation, uiState.stationDisruptions, mapBand, mapMoveTick) {
        // A major hub is a genuine cross-modal transfer: its lines span 2+ distinct
        // types. is_interchange is over-applied, so this tighter rule is what the
        // country band shows. Same rule on web + iOS.
        fun isMajorHub(station: MapStationNode): Boolean =
            station.lineIds
                .mapNotNull { lid -> uiState.lines.find { it.id == lid }?.type }
                .distinct().size >= 2

        // Viewport cull: only stations within the padded current view stay drawn,
        // so the now-nationwide network (389 stops) doesn't keep distant coastal
        // lines (Katakolo, Corinth-Patras) alive over the sea at the edges and the
        // map stays light. Mirrors web (map.getBounds().pad) + iOS (padded region).
        val bb = mapView.boundingBox
        val spanLat = bb.latNorth - bb.latSouth
        val spanLon = bb.lonEast - bb.lonWest
        val boundsValid = spanLat in 0.0001..90.0 && spanLon > 0.0001
        val nLat = bb.latNorth + spanLat * 0.5
        val sLat = bb.latSouth - spanLat * 0.5
        val eLon = bb.lonEast + spanLon * 0.5
        val wLon = bb.lonWest - spanLon * 0.5
        fun inView(s: MapStationNode): Boolean =
            !boundsValid || (s.latitude in sLat..nLat && s.longitude in wLon..eLon)

        // Three tiers: city shows every stop, regional all interchanges, country
        // only major hubs. The selection is always drawn.
        fun shouldDraw(station: MapStationNode): Boolean =
            (inView(station) && when (mapBand) {
                3 -> true
                2 -> station.isInterchange
                1 -> isMajorHub(station)
                else -> false   // country: lines only
            }) || uiState.selectedStation?.id == station.id

        val currentIds = uiState.mapStations.filter { shouldDraw(it) }.map { it.id }.toSet()
        val staleIds = stationMarkers.keys - currentIds
        staleIds.forEach { id ->
            stationMarkers[id]?.let { mapView.overlays.remove(it) }
            stationMarkers.remove(id)
        }

        uiState.mapStations.forEach { station ->
            if (!shouldDraw(station)) return@forEach
            val existing = stationMarkers[station.id]
            val isSelected = uiState.selectedStation?.id == station.id
            val isClosed = station.stationIds.any { sid ->
                uiState.stationDisruptions[sid] == com.syrmos.core.model.alerts.AlertSeverity.CLOSURE
            }
            val tintArgb = if (isClosed) {
                0xFF9E9E9E.toInt()
            } else {
                station.lineIds.firstNotNullOfOrNull { lineColors[it] }
                    ?: station.lineIds.firstNotNullOfOrNull { lineId ->
                        uiState.lines.find { it.id == lineId }?.color?.toComposeColor()
                    }?.toArgb() ?: 0xFF64748B.toInt()
            }

            // A single clean coloured dot with a white ring, exactly like web's
            // circleMarker. We used to swap in per-station artwork PNGs (the
            // bullseye icons with baked-in "M3 AM" labels) on selection - that is
            // precisely the "wrong icons" divergence from web, which draws none.
            // Selection just grows the dot; the label lives in the sheet.
            val icon = buildZoomPin(tintArgb, station.isInterchange, isSelected, bucket = if (isSelected) 2 else 1)

            if (existing != null) {
                existing.position = GeoPoint(station.latitude, station.longitude)
                existing.icon = icon
            } else {
                val marker = Marker(mapView).apply {
                    position = GeoPoint(station.latitude, station.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    this.icon = icon
                    setOnMarkerClickListener { _, _ ->
                        onStationSelected(station.id)
                        true
                    }
                }
                stationMarkers[station.id] = marker
                mapView.overlays.add(marker)
            }
        }
        mapView.invalidate()
    }

    LaunchedEffect(uiState.simulatedTrains, mapBand) {
        val res = context.resources
        // Vehicles follow the same decluttering rule as stations: below the
        // regional band (band 2 == MAJOR_HUB_MIN_ZOOM) the whole fleet collapses
        // into one blob on the coastline that reads as trains in the sea, so pull
        // every vehicle marker until the map is zoomed in far enough.
        if (mapBand < 2) {
            trainMarkers.values.forEach { mapView.overlays.remove(it) }
            trainMarkers.clear()
            mapView.invalidate()
            return@LaunchedEffect
        }
        val activeIds = uiState.simulatedTrains.map { it.id }.toSet()
        val staleIds = trainMarkers.keys - activeIds
        staleIds.forEach { id ->
            trainMarkers[id]?.let { mapView.overlays.remove(it) }
            trainMarkers.remove(id)
        }

        uiState.simulatedTrains.forEach { train ->
            // Every moving vehicle is one heading-rotated directional triangle in
            // the line colour - a pixel mirror of web's single train marker for the
            // whole fleet. Native used to give metro/tram their own per-line sprite
            // badges, which is exactly why the trains looked nothing like web. Line
            // colour comes from the raw lines.json hex, same as web.
            val lineColor = lineColors[train.lineId] ?: train.lineColor.toComposeColor().toArgb()
            val snappedSimPos = snapToPolyline(train.latitude, train.longitude, train.lineId, routeShapes)
            val existing = trainMarkers[train.id]
            if (existing != null) {
                existing.position = snappedSimPos
                // Heading is baked into the triangle bitmap; refresh each segment.
                existing.icon = buildCapsuleTrainBitmap(res, lineColor, train.bearing)
            } else {
                val trainId = train.id
                val marker = Marker(mapView).apply {
                    position = snappedSimPos
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = buildCapsuleTrainBitmap(res, lineColor, train.bearing)
                    title = "${train.lineName} > ${train.destinationName}"
                    snippet = "Near ${train.currentStationName}"
                    setOnMarkerClickListener { _, _ ->
                        onTrainSelected(trainId)
                        true
                    }
                }
                trainMarkers[train.id] = marker
                mapView.overlays.add(marker)
            }
        }
        mapView.invalidate()
    }

    LaunchedEffect(uiState.liveTrains, mapBand) {
        val res = context.resources
        if (mapBand < 2) {
            liveTrainMarkers.values.forEach { mapView.overlays.remove(it) }
            liveTrainMarkers.clear()
            mapView.invalidate()
            return@LaunchedEffect
        }
        val activeIds = uiState.liveTrains.map { it.id }.toSet()
        val staleIds = liveTrainMarkers.keys - activeIds
        staleIds.forEach { id ->
            liveTrainMarkers[id]?.let { mapView.overlays.remove(it) }
            liveTrainMarkers.remove(id)
        }

        uiState.liveTrains.forEach { train ->
            // A "position only" / not-in-service live vehicle is a real GPS dot
            // but NOT boardable, so draw it de-emphasized (grey, faded) to match
            // its detail card. Assigned trains keep the line colour.
            val notInService = !train.inService || train.status == "position_only"
            val color = if (notInService) 0xFF9CA3AF.toInt()
                else uiState.lines.find { it.id == train.lineId }?.color?.toComposeColor()?.toArgb()
                    ?: 0xFF7C4DFF.toInt()
            val snappedPos = snapToPolyline(train.latitude, train.longitude, train.lineId, routeShapes)
            val existing = liveTrainMarkers[train.id]
            if (existing != null) {
                existing.position = snappedPos
                existing.icon = buildLiveTrainBitmap(res, color = color, lineId = train.lineId, muted = notInService)
            } else {
                val trainId = train.id
                val marker = Marker(mapView).apply {
                    position = snappedPos
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = buildLiveTrainBitmap(res, color = color, lineId = train.lineId, muted = notInService)
                    title = "${train.lineId} ${train.trainNumber}"
                    setOnMarkerClickListener { _, _ ->
                        onTrainSelected(trainId)
                        true
                    }
                }
                liveTrainMarkers[train.id] = marker
                mapView.overlays.add(marker)
            }
        }
        mapView.invalidate()
    }

    LaunchedEffect(uiState.mapStations) {
        // Open framed on the Athens network, not the whole country. Fitting every
        // station (Ioannina -> Alexandroupoli -> Kalamata) clamped to minZoom 9 and
        // centred on the national centroid (central Greece / the sea), so Athens was
        // off-screen and the map looked empty. A fixed Athens frame keeps metro +
        // tram + suburban + live trains on screen at launch; GPS "locate me" still
        // recenters on the user.
        if (!hasFittedBounds && uiState.mapStations.isNotEmpty()) {
            hasFittedBounds = true
            mapView.post {
                // BoundingBox(north, east, south, west) around the Athens metro core.
                mapView.zoomToBoundingBox(BoundingBox(38.10, 23.95, 37.90, 23.55), false, 64)
            }
        }
    }

    LaunchedEffect(uiState.selectedStation) {
        if (uiState.selectedStation != null) {
            mapView.controller.animateTo(
                GeoPoint(uiState.selectedStation.latitude, uiState.selectedStation.longitude)
            )
        }
    }

    LaunchedEffect(uiState.locateUserRequest) {
        if (uiState.locateUserRequest > 0) {
            val myLocation = mapView.overlays
                .filterIsInstance<org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay>()
                .firstOrNull()?.myLocation
            if (myLocation != null) {
                mapView.controller.animateTo(myLocation, 15.0, 500L)
            }
        }
    }
}

private fun parseHex(hex: String): Int {
    val s = hex.removePrefix("#")
    val fallback = 0xFF64748B.toInt()
    // toIntOrNull/toLongOrNull so a malformed override colour (e.g. "#12PQ56"
    // or an rgb(...) string) falls back to grey instead of throwing a
    // NumberFormatException — one caller (the polyline builder) runs inside a
    // LaunchedEffect with no surrounding try/catch and would crash the map.
    return when (s.length) {
        6 -> s.toIntOrNull(16)?.let { 0xFF000000.toInt() or it } ?: fallback
        8 -> s.toLongOrNull(16)?.toInt() ?: fallback
        else -> fallback
    }
}

private fun catmullRomSpline(points: List<GeoPoint>, segments: Int = 5): List<GeoPoint> {
    if (points.size < 3) return points
    val result = mutableListOf(points[0])
    for (i in 0 until points.size - 1) {
        val p0 = points[maxOf(i - 1, 0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[minOf(i + 2, points.size - 1)]
        for (t in 1..segments) {
            val f = t.toDouble() / (segments + 1)
            val lat = cr(p0.latitude, p1.latitude, p2.latitude, p3.latitude, f)
            val lon = cr(p0.longitude, p1.longitude, p2.longitude, p3.longitude, f)
            result.add(GeoPoint(lat, lon))
        }
        result.add(p2)
    }
    return result
}

private fun cr(a: Double, b: Double, c: Double, d: Double, t: Double): Double {
    return 0.5 * (2*b + (-a+c)*t + (2*a - 5*b + 4*c - d)*t*t + (-a + 3*b - 3*c + d)*t*t*t)
}

// Fallback bitmap builders for when PNG drawables are not found

/**
 * Compact modern dot marker (mirrors the web + iOS design). A small filled
 * circle in the line colour with a crisp white ring, centred on the stop.
 * Much smaller and lighter than the old teardrop pins.
 *
 * bucket 0 (country): tiny dot
 * bucket 1 (city):    small dot
 * bucket 2 (district): dot + white inner cap so it reads at a glance
 * interchange: white core + coloured "target" ring
 */
private fun buildZoomPin(
    color: Int,
    interchange: Boolean,
    selected: Boolean,
    bucket: Int,
): android.graphics.drawable.Drawable {
    // Sizes from the shared MapDesignTokens (scaled up for the Android bitmap,
    // which is drawn at these px then centre-anchored).
    val baseSize = when (bucket) {
        0 -> com.syrmos.core.common.map.MapDesignTokens.DOT_COUNTRY + 5   // 15
        1 -> com.syrmos.core.common.map.MapDesignTokens.DOT_CITY + 9      // 22
        else -> com.syrmos.core.common.map.MapDesignTokens.DOT_SELECTED + 12  // 30
    }
    // Extra room so the selected halo isn't clipped.
    val pad = if (selected) (baseSize * 0.5f).toInt() else 2
    val size = baseSize + pad * 2
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f
    val r = baseSize / 2f
    val ring = maxOf(1.5f, baseSize * 0.11f)

    if (selected) {
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = (color and 0x00FFFFFF) or 0x66000000
        }
        canvas.drawCircle(cx, cy, r + ring + baseSize * 0.18f, halo)
    }

    if (interchange) {
        // White core + coloured ring so multi-line stops read without stacking dots.
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() }
        canvas.drawCircle(cx, cy, r, white)
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = ring * 1.6f
        }
        canvas.drawCircle(cx, cy, r - ring * 0.8f, ringPaint)
    } else {
        // Solid line-coloured dot with a white ring - a plain stop on web is a
        // filled circle, no inner cap.
        val whiteRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() }
        canvas.drawCircle(cx, cy, r, whiteRing)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawCircle(cx, cy, r - ring, fill)
    }

    return BitmapDrawable(null, bitmap)
}

/// A directional triangle for national rail + rail-replacement buses + suburban
/// A-lines (the vehicles with no per-line directional sprite). Points the way the
/// train is heading (compass [bearingDeg], 0 = north), coloured by line with a
/// white outline so it reads on the flat light/dark base - the native mirror of
/// the web triangle markers.
private fun buildCapsuleTrainBitmap(res: android.content.res.Resources, color: Int, bearingDeg: Double): android.graphics.drawable.Drawable {
    val density = res.displayMetrics.density
    val w = MapDesignTokens.VEHICLE_W * density
    val h = MapDesignTokens.VEHICLE_H * density
    val border = MapDesignTokens.VEHICLE_BORDER * density
    val radius = MapDesignTokens.VEHICLE_RADIUS * density
    val pad = 4 * density
    val canvas_size = (maxOf(w, h) + pad * 2).toInt()
    val bitmap = Bitmap.createBitmap(canvas_size, canvas_size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = canvas_size / 2f
    val cy = canvas_size / 2f
    canvas.save()
    // The capsule is a horizontal pill (w > h), so its long axis points east at
    // 0°. Bearing is a compass heading (0 = north), so subtract 90° to turn the
    // pill along the direction of travel — identical to the web's
    // `rotate(bearing - 90deg)`. Without this the pill stayed horizontal on
    // north-south lines (most of the metro).
    canvas.rotate((bearingDeg - 90).toFloat(), cx, cy)
    val rect = RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.FILL
        setShadowLayer(8 * density, 0f, 2 * density, 0x4D14181F)
    })
    canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = border
    })
    canvas.restore()
    return BitmapDrawable(res, bitmap)
}

private fun buildMetroTrainBitmap(res: android.content.res.Resources, color: Int, lineLabel: String = ""): android.graphics.drawable.Drawable {
    val width = 56
    val height = 64
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = width / 2f

    if (lineLabel.isNotEmpty()) {
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0xFFFFFFFF.toInt()
            textSize = 10f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawRoundRect(RectF(cx - 14f, 0f, cx + 14f, 14f), 7f, 7f, badgePaint)
        canvas.drawText(lineLabel, cx, 11f, textPaint)
    }

    val cy = if (lineLabel.isNotEmpty()) 38f else 32f
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    canvas.drawRoundRect(RectF(cx - 13f, cy - 11f, cx + 13f, cy + 11f), 6f, 6f, white)
    canvas.drawRoundRect(RectF(cx - 11f, cy - 9f, cx + 11f, cy + 9f), 5f, 5f, fill)
    canvas.drawRoundRect(RectF(cx - 6f, cy - 2f, cx + 6f, cy), 1f, 1f, white)
    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xB3FFFFFF.toInt() }
    canvas.drawCircle(cx - 4f, cy + 4f, 1.5f, dot)
    canvas.drawCircle(cx + 4f, cy + 4f, 1.5f, dot)
    return BitmapDrawable(res, bitmap)
}

private fun buildTramTrainBitmap(res: android.content.res.Resources, color: Int, lineLabel: String = ""): android.graphics.drawable.Drawable {
    val width = 56
    val height = 56
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = width / 2f

    if (lineLabel.isNotEmpty()) {
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0xFFFFFFFF.toInt()
            textSize = 10f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawRoundRect(RectF(cx - 14f, 0f, cx + 14f, 14f), 7f, 7f, badgePaint)
        canvas.drawText(lineLabel, cx, 11f, textPaint)
    }

    val cy = if (lineLabel.isNotEmpty()) 34f else 28f
    val w = 16f
    val h = 10f
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    canvas.drawRoundRect(RectF(cx - w / 2 - 2, cy - h / 2 - 2, cx + w / 2 + 2, cy + h / 2 + 2), h * 0.4f, h * 0.4f, white)
    canvas.drawRoundRect(RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), h * 0.35f, h * 0.35f, fill)
    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xB3FFFFFF.toInt() }
    canvas.drawCircle(cx - 3f, cy, 1.5f, dot)
    canvas.drawCircle(cx + 3f, cy, 1.5f, dot)
    return BitmapDrawable(res, bitmap)
}

private fun buildAirportTrainBitmap(res: android.content.res.Resources): android.graphics.drawable.Drawable {
    val width = 56
    val height = 64
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = width / 2f
    val blue = 0xFF0057B8.toInt()

    val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blue }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 9f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    canvas.drawRoundRect(RectF(cx - 11f, 0f, cx + 11f, 14f), 7f, 7f, badgePaint)
    canvas.drawText("M3", cx, 11f, textPaint)

    val cy = 38f
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = blue }

    canvas.drawRoundRect(RectF(cx - 14f, cy - 12f, cx + 14f, cy + 12f), 6f, 6f, white)
    canvas.drawRoundRect(RectF(cx - 12f, cy - 10f, cx + 12f, cy + 10f), 5f, 5f, fill)
    val s = 5f
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
    }
    canvas.drawLine(cx - s, cy + s * 0.2f, cx + s, cy + s * 0.2f, linePaint)
    canvas.drawLine(cx, cy - s * 0.6f, cx, cy + s * 0.6f, Paint(linePaint).apply { strokeWidth = 1.5f })
    canvas.drawLine(cx - s * 0.6f, cy - s * 0.3f, cx + s * 0.6f, cy - s * 0.3f, Paint(linePaint).apply { strokeWidth = 1.5f })
    return BitmapDrawable(res, bitmap)
}

private fun buildLiveTrainBitmap(
    res: android.content.res.Resources,
    color: Int,
    lineId: String,
    muted: Boolean = false,
): android.graphics.drawable.Drawable {
    // Bigger marker with halo, line-color core, and a line-id badge underneath
    // so suburban trains stand out clearly against the simulated metro/tram
    // dots. Static (no animation here — osmdroid markers don't redraw on tick)
    // but the size + badge already deliver the visibility the user asked for.
    // `muted` = a non-boardable "position only" vehicle: faded core/halo/badge
    // (and the caller passes a grey colour) so it reads as secondary, matching
    // the web/iOS markers + the detail card.
    val width = 88
    val height = 116
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = width / 2f
    val cy = 44f

    val coreAlpha = if (muted) 140 else 255
    val badgeAlpha = if (muted) 180 else 255
    val haloColor = (color and 0x00FFFFFF) or (if (muted) 0x1A000000 else 0x33000000)
    val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = haloColor }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; this.alpha = coreAlpha }
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt(); this.alpha = if (muted) 190 else 255 }
    val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFFFFFFFF.toInt()
        this.textSize = 22f
        this.isFakeBoldText = true
        this.textAlign = Paint.Align.CENTER
    }
    val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; this.alpha = badgeAlpha }

    canvas.drawCircle(cx, cy, 36f, halo)
    canvas.drawCircle(cx, cy, 24f, fill)
    canvas.drawCircle(cx, cy, 8f, white)

    val badgeWidth = 44f
    val badgeHeight = 26f
    val badgeTop = 78f
    val badgeRect = android.graphics.RectF(
        cx - badgeWidth / 2f, badgeTop,
        cx + badgeWidth / 2f, badgeTop + badgeHeight,
    )
    canvas.drawRoundRect(badgeRect, 13f, 13f, badge)
    canvas.drawText(lineId, cx, badgeTop + 19f, badgeText)
    return BitmapDrawable(res, bitmap)
}
