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
import com.syrmos.core.model.transit.Direction
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.model.transit.SimulatedTrain
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/// Carto Voyager raster: OSM data rendered with name:en preferred over the
/// local language. Used for English + Albanian app modes (Albanian-specific
/// label rendering isn't a free public tile service as of 2026). Greek mode
/// uses the default OSM Mapnik tiles which carry Greek labels in Greece.
private val CARTO_VOYAGER: ITileSource = XYTileSource(
    "CartoVoyager",
    0,
    20,
    256,
    ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://d.basemaps.cartocdn.com/rastertiles/voyager/",
    ),
    "© OpenStreetMap, © CARTO",
)

private fun tileSourceFor(lang: com.syrmos.core.common.AppLanguage): ITileSource = when (lang) {
    com.syrmos.core.common.AppLanguage.GREEK -> TileSourceFactory.MAPNIK
    else -> CARTO_VOYAGER
}

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
    modifier: Modifier,
    initialScale: Float,
) {
    val context = LocalContext.current
    var hasFittedBounds by remember { mutableStateOf(false) }
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val routeShapes = remember { loadRouteShapes(context) }
    val lineOverlays = remember { mutableListOf<Polyline>() }
    val stationMarkers = remember { mutableMapOf<String, Marker>() }
    val trainMarkers = remember { mutableMapOf<String, Marker>() }
    val liveTrainMarkers = remember { mutableMapOf<String, Marker>() }
    // 0 = country, 1 = city, 2 = district, 3 = street. Mirrors web + iOS buckets.
    var zoomBucket by remember { mutableStateOf(2) }

    val appLang by com.syrmos.core.common.LocalizationManager.language.collectAsState()

    DisposableEffect(context) {
        Configuration.getInstance().userAgentValue = context.packageName
        onDispose { }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(tileSourceFor(appLang))
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
                    override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean = false
                    override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                        val z = event?.zoomLevel ?: zoomLevelDouble
                        val next = when {
                            z >= 14.0 -> 3
                            z >= 12.0 -> 2
                            z >= 10.0 -> 1
                            else -> 0
                        }
                        if (next != zoomBucket) zoomBucket = next
                        return false
                    }
                })
                mapViewRef.value = this
            }
        },
        update = { mv ->
            // Re-apply tile source when the active language flips so the
            // Map tab picks up the localised labels without a process
            // restart. osmdroid handles the swap and invalidates the
            // tile cache for us.
            val desired = tileSourceFor(appLang)
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

            // Prefer real OSM track geometry over a spline through station
            // points so the Piraeus loop, M3 airport branch and suburban
            // curves render accurately.
            val osmShape = routeShapes[line.id]
            val smoothed: List<GeoPoint> = if (osmShape != null && osmShape.size >= 2) {
                osmShape
            } else {
                catmullRomSpline(lineStations.map { GeoPoint(it.latitude, it.longitude) })
            }
            val override = displayOverrides[line.id]
            // A line that is built but not open still draws, because the track is
            // real, but it reads as inert: muted grey, thinner, dashed. It carries
            // no trains or departures either (handled in the simulator/projector).
            val underConstruction = !line.isOperational
            val color = when {
                underConstruction -> android.graphics.Color.parseColor(com.syrmos.core.common.map.MapDesignTokens.GREYED_COLOR)
                else -> override?.strokeColor?.let { parseHex(it) }
                    ?: line.color.toComposeColor().toArgb()
            }
            val width = when {
                underConstruction -> if (line.type == LineType.SUBURBAN) 5f else 6f
                else -> override?.strokeWeight?.toFloat()
                    ?: (if (line.type == LineType.SUBURBAN) 7f else 10f)
            }
            val polyline = Polyline().apply {
                outlinePaint.color = color
                outlinePaint.strokeWidth = width
                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                if (underConstruction) {
                    outlinePaint.alpha = 140
                    outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 16f), 0f)
                } else {
                    override?.strokeDash?.let { dashSpec ->
                        val parts = dashSpec.split(" ").mapNotNull { it.toFloatOrNull() }
                        if (parts.size >= 2) {
                            outlinePaint.pathEffect = android.graphics.DashPathEffect(parts.toFloatArray(), 0f)
                        }
                    }
                }
                setPoints(smoothed)
            }
            lineOverlays.add(polyline)
            mapView.overlays.add(0, polyline)
        }
        mapView.invalidate()
    }

    LaunchedEffect(uiState.mapStations, uiState.selectedStation, zoomBucket) {
        val currentIds = uiState.mapStations.map { it.id }.toSet()
        val staleIds = stationMarkers.keys - currentIds
        staleIds.forEach { id ->
            stationMarkers[id]?.let { mapView.overlays.remove(it) }
            stationMarkers.remove(id)
        }

        uiState.mapStations.forEach { station ->
            val existing = stationMarkers[station.id]
            val isSelected = uiState.selectedStation?.id == station.id
            val primaryStationId = station.stationIds.firstOrNull() ?: station.id
            val tintArgb = station.lineIds.firstNotNullOfOrNull { lineId ->
                uiState.lines.find { it.id == lineId }?.color?.toComposeColor()
            }?.toArgb() ?: 0xFF64748B.toInt()

            // High zoom: full station_smart_code PNG when we have one. Low/mid:
            // colored pin so the country view doesn't look like a rice field.
            val icon = if (zoomBucket >= 3) {
                resolveStationDrawable(context, primaryStationId, uiState.lineStations)
                    ?: buildZoomPin(tintArgb, station.isInterchange, isSelected, bucket = 2)
            } else {
                buildZoomPin(
                    color = tintArgb,
                    interchange = station.isInterchange,
                    selected = isSelected,
                    bucket = zoomBucket,
                )
            }

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

    LaunchedEffect(uiState.simulatedTrains) {
        val res = context.resources
        val activeIds = uiState.simulatedTrains.map { it.id }.toSet()
        val staleIds = trainMarkers.keys - activeIds
        staleIds.forEach { id ->
            trainMarkers[id]?.let { mapView.overlays.remove(it) }
            trainMarkers.remove(id)
        }

        uiState.simulatedTrains.forEach { train ->
            val existing = trainMarkers[train.id]

            if (existing != null) {
                existing.position = GeoPoint(train.latitude, train.longitude)
            } else {
                val vehicleIcon = resolveVehicleDrawable(context, train)
                val lineColor = train.lineColor.toComposeColor().toArgb()
                val marker = Marker(mapView).apply {
                    position = GeoPoint(train.latitude, train.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = vehicleIcon ?: when {
                        train.isAirportService -> buildAirportTrainBitmap(res)
                        train.lineType == LineType.TRAM -> buildTramTrainBitmap(res, lineColor, train.lineId)
                        else -> buildMetroTrainBitmap(res, lineColor, train.lineId)
                    }
                    title = "${train.lineName} → ${train.destinationName}"
                    snippet = "Near ${train.currentStationName}"
                }
                trainMarkers[train.id] = marker
                mapView.overlays.add(marker)
            }
        }
        mapView.invalidate()
    }

    LaunchedEffect(uiState.liveTrains) {
        val res = context.resources
        val activeIds = uiState.liveTrains.map { it.id }.toSet()
        val staleIds = liveTrainMarkers.keys - activeIds
        staleIds.forEach { id ->
            liveTrainMarkers[id]?.let { mapView.overlays.remove(it) }
            liveTrainMarkers.remove(id)
        }

        uiState.liveTrains.forEach { train ->
            val color = uiState.lines.find { it.id == train.lineId }?.color?.toComposeColor()?.toArgb()
                ?: 0xFF7C4DFF.toInt()
            val existing = liveTrainMarkers[train.id]
            if (existing != null) {
                existing.position = GeoPoint(train.latitude, train.longitude)
                existing.icon = buildLiveTrainBitmap(res, color = color, lineId = train.lineId)
            } else {
                val marker = Marker(mapView).apply {
                    position = GeoPoint(train.latitude, train.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = buildLiveTrainBitmap(res, color = color, lineId = train.lineId)
                    title = "${train.lineId} ${train.trainNumber}"
                }
                liveTrainMarkers[train.id] = marker
                mapView.overlays.add(marker)
            }
        }
        mapView.invalidate()
    }

    LaunchedEffect(uiState.mapStations) {
        if (!hasFittedBounds && uiState.mapStations.isNotEmpty()) {
            hasFittedBounds = true
            val points = uiState.mapStations.map { GeoPoint(it.latitude, it.longitude) }
            mapView.post {
                mapView.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(points), true, 96)
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
    return when (s.length) {
        6 -> 0xFF000000.toInt() or s.toInt(16)
        8 -> s.toLong(16).toInt()
        else -> 0xFF64748B.toInt()
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
        val whiteRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() }
        canvas.drawCircle(cx, cy, r, whiteRing)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawCircle(cx, cy, r - ring, fill)
        if (bucket >= 2) {
            val cap = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() }
            canvas.drawCircle(cx, cy, r * 0.34f, cap)
        }
    }

    return BitmapDrawable(null, bitmap)
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

private fun buildLiveTrainBitmap(res: android.content.res.Resources, color: Int, lineId: String): android.graphics.drawable.Drawable {
    // Bigger marker with halo, line-color core, and a line-id badge underneath
    // so suburban trains stand out clearly against the simulated metro/tram
    // dots. Static (no animation here — osmdroid markers don't redraw on tick)
    // but the size + badge already deliver the visibility the user asked for.
    val width = 88
    val height = 116
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = width / 2f
    val cy = 44f

    val haloColor = (color and 0x00FFFFFF) or 0x33000000
    val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = haloColor }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() }
    val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFFFFFFFF.toInt()
        this.textSize = 22f
        this.isFakeBoldText = true
        this.textAlign = Paint.Align.CENTER
    }
    val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

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
