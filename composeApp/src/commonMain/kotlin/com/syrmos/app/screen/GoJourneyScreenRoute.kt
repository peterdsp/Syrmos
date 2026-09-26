package com.syrmos.app.screen

import androidx.compose.foundation.background
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager
import com.syrmos.app.journey.ActiveJourneyRepository
import com.syrmos.core.domain.go.GoGuidance
import com.syrmos.core.domain.go.GuidanceJourney
import com.syrmos.core.domain.go.GuidancePosition
import com.syrmos.core.domain.go.JourneyGuidance
import com.syrmos.core.domain.journey.ActiveJourneyStore
import androidx.compose.runtime.collectAsState
import kotlinx.datetime.Clock
import kotlin.jvm.Transient
import com.syrmos.core.domain.go.GuidanceLeg
import com.syrmos.core.domain.go.GoTimelineState
import com.syrmos.core.domain.go.GoTimelineRow
import com.syrmos.core.domain.go.GoTimelineRole
import com.syrmos.core.domain.go.GoTimeline
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.syrmos.core.designsystem.layout.rememberContentWorkspace
import com.syrmos.core.common.layout.PaneRole
import com.syrmos.core.common.layout.WorkspaceArrangement
import com.syrmos.core.common.layout.WorkspaceTask
import com.syrmos.core.data.repository.LineRepositoryImpl
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.designsystem.component.toComposeColor
import kotlinx.coroutines.flow.first
import com.syrmos.core.common.map.LatLng
import com.syrmos.core.domain.go.GoRoutePoint
import com.syrmos.core.domain.go.GoRouteRuns
import com.syrmos.core.domain.go.GoCamera
import com.syrmos.core.domain.go.GoCameraAction
import com.syrmos.core.domain.go.GoCameraEvent
import com.syrmos.core.domain.go.GoCameraIntent
import com.syrmos.feature.map.GoRouteMapLeg
import com.syrmos.feature.map.GoRouteMapView
import org.koin.compose.koinInject

/**
 * GO live-guidance screen (Phase G / S06), the Android peer of the web
 * `SyrmosGoPanel` and iOS `GoJourneyView`. Guides the rider through a planned
 * [GuidanceJourney] one instruction at a time (board / stay on / get off next /
 * change here / arrived) from the shared, offline [GoGuidance] engine. The get-off
 * cue is emphasised because it's the one moment that matters most. Advancing is
 * MANUAL for now (GPS / live-position auto-advance is a later phase), so the
 * primary control is honestly labelled "Next stop", not implied live tracking.
 */
/// Phase R S07: connection risk of one transfer, from the real route model.
data class TransferRisk(val status: String, val availableSeconds: Int?, val minimumSeconds: Int?)

/// Phase R S07: one-shot request from GO's "Find alternatives" to re-plan from the
/// rider's current confirmed station to the destination once Plan resumes. Voyager
/// pop cannot carry a result, so the Plan screen consumes this on the way back.
object PlanReplanRequest {
    private val _pending = MutableStateFlow<Pair<String, String>?>(null)
    val pending: StateFlow<Pair<String, String>?> = _pending.asStateFlow()
    fun request(fromId: String, toId: String) { _pending.value = fromId to toId }
    fun consume() { _pending.value = null }
}

class GoJourneyScreenRoute(
    // Both fields are transient: a Voyager screen is saved with the activity's
    // instance state on a rotation or a fold, and the guidance journey is not
    // serialisable. After restoration the screen rebuilds its journey from the
    // persisted live session instead, so GO survives recreation (D05, D12)
    // rather than falling back to the tab root; transfer risks are advisory and
    // simply absent on a restored screen (already the documented behaviour).
    @Transient private val journey: GuidanceJourney? = null,
    @Transient private val transferRisks: List<TransferRisk> = emptyList(),
) : Screen {
    override val key: String = "go-journey"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val lang by LocalizationManager.language.collectAsState()
        // Position is derived from the persisted live session so it survives a kill /
        // navigation; every step is written back through the shared lifecycle store.
        val active by ActiveJourneyRepository.active.collectAsState()
        val stationRepo = koinInject<StationRepositoryImpl>()
        // The journey: the one handed over at push time, or, after a restoration,
        // the one rebuilt from the live session's frozen itinerary.
        var rebuilt by remember { mutableStateOf<GuidanceJourney?>(null) }
        val snapshot = active?.itinerarySnapshot
        LaunchedEffect(snapshot, lang) {
            if (this@GoJourneyScreenRoute.journey == null && rebuilt == null && snapshot != null) {
                rebuilt = buildGuidanceJourney(snapshot, stationRepo, lang)
            }
        }
        val journey = this.journey ?: rebuilt
        // Tell the shell GO is on screen (hides the floating launcher, as iOS).
        androidx.compose.runtime.DisposableEffect(Unit) {
            com.syrmos.app.journey.GoScreenPresence.onScreen = true
            onDispose { com.syrmos.app.journey.GoScreenPresence.onScreen = false }
        }
        if (journey == null || journey.legs.isEmpty()) {
            // Restored with no live session left: nothing to guide, leave quietly.
            LaunchedEffect(active) { if (active == null) navigator.pop() }
            return
        }
        val position = active?.let { ActiveJourneyStore.positionOf(it, journey) } ?: GuidancePosition(0, 0)

        fun t(en: String, el: String, sq: String, it: String) = when (lang) {
            AppLanguage.GREEK -> el; AppLanguage.ALBANIAN -> sq; AppLanguage.ITALIAN -> it; else -> en
        }
        fun endJourney() { ActiveJourneyRepository.clear(); navigator.pop() }
        // Mid-journey End asks first, so a stray tap on a moving train does not
        // drop the guidance; Finish after arrival is final and safe.
        var confirmEnd by remember { mutableStateOf(false) }
        fun advance() { active?.let { ActiveJourneyRepository.set(ActiveJourneyStore.advance(it, journey, Clock.System.now())) } }
        fun stepBack() { active?.let { ActiveJourneyRepository.set(ActiveJourneyStore.back(it, journey, Clock.System.now())) } }

        val guidance = GoGuidance.guidance(journey, position)
        val arrived = GoGuidance.isArrived(journey, position)
        val canBack = position.legIndex > 0 || position.stopIndex > 0

        // Journey completion, from the one shared cross-client definition.
        val progress = GoGuidance.progress(journey, position).toFloat()

        val origin = journey.legs.firstOrNull()?.stops?.firstOrNull()?.name ?: ""
        val destination = journey.legs.lastOrNull()?.stops?.lastOrNull()?.name ?: ""

        // Line colours for the timeline pills and rail (seed data, not a guess).
        val lineRepo = koinInject<LineRepositoryImpl>()
        var lineColors by remember { mutableStateOf<Map<String, Color>>(emptyMap()) }
        // Route map inputs: the stops' coordinates (one fetch) and the shared
        // per-leg projection, so each leg draws in its real line colour.
        var stationCoords by remember { mutableStateOf<Map<String, GoRoutePoint>>(emptyMap()) }
        LaunchedEffect(Unit) {
            stationCoords = stationRepo.getAllStations().first()
                .associate { it.id to GoRoutePoint(it.latitude, it.longitude) }
        }
        val primaryColor = MaterialTheme.colorScheme.primary
        val routeLegs = remember(journey, stationCoords, lineColors, primaryColor) {
            GoRouteRuns.legRuns(journey) { stationCoords[it] }.map { run ->
                GoRouteMapLeg(run.lineId, lineColors[run.lineId] ?: primaryColor, run.points.map { LatLng(it.lat, it.lon) })
            }
        }
        val currentPoint = GoRouteRuns.currentPoint(journey, position) { stationCoords[it] }?.let { LatLng(it.lat, it.lon) }
        // Camera with explicit intent (shared GoCamera reducer): follow the
        // current stop by default, keep the whole route on Fit route, and leave
        // the rider's manual view alone until they ask again.
        var showCompactMap by rememberSaveable { mutableStateOf(false) }
        var cameraIntent by remember { mutableStateOf(GoCameraIntent.FOLLOW) }
        var cameraCommand by remember { mutableStateOf(GoCameraAction.NONE) }
        var cameraTick by remember { mutableStateOf(0) }
        fun camera(event: GoCameraEvent) {
            val step = GoCamera.reduce(cameraIntent, event)
            cameraIntent = step.intent
            if (step.action != GoCameraAction.NONE) { cameraCommand = step.action; cameraTick++ }
        }
        val routeAccent = lineColors[journey.legs.getOrNull(position.legIndex)?.lineId] ?: primaryColor
        // The route map card: rounded, with the camera controls.
        val routeMap: @Composable (Modifier) -> Unit = { m ->
            Box(m.clip(RoundedCornerShape(16.dp))) {
                GoRouteMapView(
                    legs = routeLegs, current = currentPoint, accent = routeAccent,
                    intent = cameraIntent, command = cameraCommand, commandTick = cameraTick,
                    onUserPan = { if (cameraIntent != GoCameraIntent.MANUAL) camera(GoCameraEvent.USER_PANNED) },
                    modifier = Modifier.fillMaxSize(),
                )
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val pill = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(999.dp))
                    if (cameraIntent != GoCameraIntent.FOLLOW) {
                        TextButton(onClick = { camera(GoCameraEvent.FOLLOW_TAPPED) }, modifier = pill) {
                            Text(t("Follow", "Ακολούθησε", "Ndiq", "Segui"), maxLines = 1)
                        }
                    }
                    TextButton(onClick = { camera(GoCameraEvent.FIT_TAPPED) }, modifier = pill) {
                        Text(t("Fit route", "Όλη η διαδρομή", "Gjithë rruga", "Tutto il percorso"), maxLines = 1)
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            lineColors = lineRepo.getAllLines().first().associate { it.id to it.color.toComposeColor() }
        }

        Scaffold(
            topBar = {
                Row(
                    // Below the status bar: the custom bar is not a TopAppBar, so it
                    // must take the inset itself or its title and End button sit
                    // under the clock (seen on the fold emulator).
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "Πίσω", "Prapa", "Indietro"))
                        }
                        Column(Modifier.padding(start = 4.dp)) {
                            Text("GO", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                t("Journey in progress", "Διαδρομή σε εξέλιξη", "Udhëtim në vazhdim", "Viaggio in corso"),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    OutlinedButton(onClick = { if (arrived) endJourney() else confirmEnd = true }) {
                        Text(if (arrived) t("Finish", "Τέλος", "Përfundo", "Concludi") else t("End", "Τέλος", "Përfundo", "Termina"))
                    }
                }
            },
        ) { padding ->
            // The current instruction and its reachable controls: the task pane on
            // a paired layout, the whole screen on a phone.
            val instruction: @Composable ColumnScope.() -> Unit = {
                Text("$origin → $destination", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Alight point for THIS leg (an interchange mid-journey, the
                // destination only on the final leg), named in the GO sub so its
                // count can never be read as the S05 "intermediate stops" number.
                val alightStation = journey.legs.getOrNull(position.legIndex)?.stops?.lastOrNull()?.name ?: ""
                val currentLineId = journey.legs.getOrNull(position.legIndex)?.lineId
                heroCard(guidance, alightStation, ::t, lineColors[currentLineId] ?: MaterialTheme.colorScheme.primary)

                // Phase R S07: inline connection-risk warning below the instruction.
                val transferMoment = guidance is JourneyGuidance.Transfer ||
                    (guidance is JourneyGuidance.GetOffNext && guidance.transferTo != null)
                val activeRisk = if (transferMoment && transferRisks.indices.contains(position.legIndex)) {
                    transferRisks[position.legIndex].takeIf { it.status == "tight" || it.status == "missed" }
                } else {
                    null
                }
                if (activeRisk != null) {
                    ConnectionRiskCard(
                        activeRisk,
                        onFindAlternatives = {
                            // Re-plan from the current confirmed station to the
                            // destination (parity with iOS/web), handed to the Plan
                            // screen as a one-shot request before popping back.
                            val fromId = journey.legs.getOrNull(position.legIndex)
                                ?.stops?.getOrNull(position.stopIndex)?.id
                            val toId = journey.legs.lastOrNull()?.stops?.lastOrNull()?.id
                            if (fromId != null && toId != null) PlanReplanRequest.request(fromId, toId)
                            navigator.pop()
                        },
                        t = ::t,
                    )
                }

                LegProgressBar(journey, position, lineColors, progress, arrived, ::t)

                if (arrived) {
                    Button(onClick = { endJourney() }, modifier = Modifier.fillMaxWidth()) {
                        Text(t("Finish journey", "Ολοκλήρωση", "Përfundo udhëtimin", "Concludi viaggio"))
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { stepBack() },
                            enabled = canBack, modifier = Modifier.weight(1f),
                        ) { Text(t("Back", "Πίσω", "Prapa", "Indietro")) }
                        Button(
                            onClick = { advance() },
                            modifier = Modifier.weight(1f),
                        ) { Text(t("Next stop", "Επόμενη στάση", "Ndalesa tjetër", "Prossima fermata")) }
                    }
                }

                Text(
                    t("Step through your journey. The get-off alert fires as you approach your stop.",
                      "Προχώρα βήμα βήμα. Η ειδοποίηση αποβίβασης έρχεται καθώς πλησιάζεις τη στάση σου.",
                      "Ec hap pas hapi. Sinjalizimi për zbritjen vjen kur i afrohesh ndalesës tënde.",
                      "Procedi passo passo. L'avviso di discesa arriva quando ti avvicini alla tua fermata."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Foldables and tablets (foldables / Duo prompt section 6, GO): beside
            // the instruction on a wide window or a book fold, the journey timeline;
            // on a tall window or a horizontal fold, the timeline above and the
            // instruction with its controls below, where the hands are. Decided by
            // the shared policy from the measured content box.
            if (confirmEnd) {
                AlertDialog(
                    onDismissRequest = { confirmEnd = false },
                    title = { Text(t("End this journey?", "Τέλος διαδρομής;", "Të përfundojë udhëtimi?", "Terminare il viaggio?")) },
                    text = {
                        Text(t(
                            "Guidance and the get-off alert stop. Your route stays in Plan.",
                            "Η καθοδήγηση και η ειδοποίηση αποβίβασης σταματούν. Η διαδρομή σου μένει στο Σχεδίασε.",
                            "Udhëzimi dhe njoftimi i zbritjes ndalojnë. Rruga jote mbetet te Planifiko.",
                            "La guida e l'avviso di discesa si fermano. Il percorso resta in Pianifica.",
                        ))
                    },
                    confirmButton = {
                        TextButton(onClick = { confirmEnd = false; endJourney() }) {
                            Text(t("End journey", "Τέλος διαδρομής", "Përfundo udhëtimin", "Termina il viaggio"))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmEnd = false }) {
                            Text(t("Keep going", "Συνέχισε", "Vazhdo", "Continua"))
                        }
                    },
                )
            }
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val ws = rememberContentWorkspace(
                    task = WorkspaceTask.GO,
                    width = maxWidth.value.toInt(),
                    height = maxHeight.value.toInt(),
                )
                when (ws.arrangement) {
                    WorkspaceArrangement.SIDE_BY_SIDE -> {
                        val taskW = ws.pane(PaneRole.TASK)?.rect?.right ?: (maxWidth.value.toInt() / 2)
                        Row(Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier.width(taskW.dp).fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                                content = instruction,
                            )
                            VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                            // Companion: the route map above the journey timeline (as iOS).
                            Column(Modifier.weight(1f).fillMaxHeight()) {
                                routeMap(Modifier.fillMaxWidth().height(240.dp).padding(start = 16.dp, end = 16.dp, top = 12.dp))
                                JourneyTimeline(journey, position, lineColors, ::t, Modifier.weight(1f).fillMaxWidth())
                            }
                        }
                    }
                    WorkspaceArrangement.STACKED -> {
                        val companionH = ws.pane(PaneRole.COMPANION)?.rect?.bottom ?: (maxHeight.value.toInt() * 45 / 100)
                        Column(Modifier.fillMaxSize()) {
                            // Upright: the map keeps the upper region to itself; the
                            // instruction and the timeline read below, where the hands are.
                            routeMap(Modifier.fillMaxWidth().height(companionH.dp).padding(horizontal = 16.dp, vertical = 12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                            Column(
                                modifier = Modifier.weight(1f).fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                instruction()
                                JourneyTimeline(journey, position, lineColors, ::t, Modifier.fillMaxWidth(), scrollable = false, inset = 0.dp)
                            }
                        }
                    }
                    WorkspaceArrangement.SINGLE -> Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        instruction()
                        // Single column (phone): the route map on request, kept
                        // reachable without pushing the instruction down (as iOS).
                        OutlinedButton(
                            onClick = { showCompactMap = !showCompactMap },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (showCompactMap) t("Hide route map", "Απόκρυψη χάρτη διαδρομής", "Fshih hartën e rrugës", "Nascondi la mappa del percorso")
                                else t("Show route map", "Εμφάνιση χάρτη διαδρομής", "Shfaq hartën e rrugës", "Mostra la mappa del percorso"),
                            )
                        }
                        if (showCompactMap) routeMap(Modifier.fillMaxWidth().height(260.dp))
                        // The journey's cards under the controls, so the lower half
                        // of a phone carries the route instead of empty space.
                        JourneyTimeline(journey, position, lineColors, ::t, Modifier.fillMaxWidth(), scrollable = false, inset = 0.dp)
                    }
                }
            }
        }
    }

    /**
     * The journey as a timeline of leg cards: line pill, direction and stop count
     * in each card's header, then the stops on a rail in the leg's colour with
     * origin and alight rings, a haloed current marker, Now / Next / Change here /
     * Destination captions, and a walking connector between legs. Rows come from
     * the shared [GoTimeline] projection so iOS and Android agree on every state.
     */
    @Composable
    private fun JourneyTimeline(
        journey: GuidanceJourney,
        position: GuidancePosition,
        lineColors: Map<String, Color>,
        t: (String, String, String, String) -> String,
        modifier: Modifier = Modifier,
        scrollable: Boolean = true,
        inset: androidx.compose.ui.unit.Dp = 16.dp,
    ) {
        val rows = GoTimeline.rows(journey, position)
        val origin = journey.legs.firstOrNull()?.stops?.firstOrNull()?.name ?: ""
        val destination = journey.legs.lastOrNull()?.stops?.lastOrNull()?.name ?: ""
        val lines = journey.legs.size
        val stops = GoTimeline.stopCount(journey)
        val linesText = if (lines == 1) t("1 line", "1 γραμμή", "1 linjë", "1 linea")
            else t("$lines lines", "$lines γραμμές", "$lines linja", "$lines linee")
        val stopsText = t("$stops stops", "$stops στάσεις", "$stops ndalesa", "$stops fermate")
        Column(
            modifier = (if (scrollable) modifier.verticalScroll(rememberScrollState()) else modifier).padding(inset),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    t("Journey", "Διαδρομή", "Udhëtimi", "Viaggio"),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "$origin → $destination · $linesText · $stopsText",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            journey.legs.forEachIndexed { legIdx, leg ->
                val color = lineColors[leg.lineId] ?: MaterialTheme.colorScheme.primary
                if (legIdx > 0) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.width(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(3) { Box(Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))) }
                        }
                        Text(
                            "⇄ " + t("Change to", "Αλλαγή σε", "Ndërro në", "Cambia in") + " " + leg.lineId,
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                LegCard(leg, color, rows.filter { it.legIndex == legIdx }, t)
            }
            // Clear the floating assistant launcher so the last card is readable.
            Spacer(Modifier.height(88.dp))
        }
    }

    @Composable
    private fun LegCard(
        leg: GuidanceLeg,
        color: Color,
        rows: List<GoTimelineRow>,
        t: (String, String, String, String) -> String,
    ) {
        val count = maxOf(0, leg.stops.size - 1)
        val countText = if (count == 1) t("1 stop", "1 στάση", "1 ndalesë", "1 fermata")
            else t("$count stops", "$count στάσεις", "$count ndalesa", "$count fermate")
        Column(
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.background(color, RoundedCornerShape(7.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(leg.lineId, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                Text(
                    t("toward", "προς", "drejt", "verso") + " " + leg.towards,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(countText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                rows.forEach { row -> TimelineRow(row, color, t) }
            }
        }
    }

    @Composable
    private fun TimelineRow(row: GoTimelineRow, color: Color, t: (String, String, String, String) -> String) {
        val isPast = row.state == GoTimelineState.PAST
        val isCurrent = row.state == GoTimelineState.CURRENT
        val terminus = row.role != GoTimelineRole.INTERMEDIATE
        val rowHeight = if (terminus || isCurrent) 44.dp else 30.dp
        val caption: String? = when {
            row.isDestination -> t("Destination", "Προορισμός", "Destinacioni", "Destinazione")
            row.role == GoTimelineRole.ALIGHT -> t("Change here", "Αλλαγή εδώ", "Ndërro këtu", "Cambia qui")
            isCurrent -> t("Now", "Τώρα", "Tani", "Ora")
            row.state == GoTimelineState.NEXT -> t("Next", "Επόμενη", "Tjetra", "Prossima")
            else -> null
        }
        val captionColor = if (isPast && !row.isDestination) MaterialTheme.colorScheme.onSurfaceVariant else color
        Row(
            // One TalkBack element per stop: name plus its caption (Now, Next, ...).
            modifier = Modifier.fillMaxWidth().height(rowHeight).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.width(24.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).width(4.dp).background(
                        if (row.role == GoTimelineRole.ORIGIN) Color.Transparent else color.copy(alpha = if (isPast || isCurrent) 0.3f else 1f)))
                    Box(Modifier.weight(1f).width(4.dp).background(
                        if (row.role == GoTimelineRole.ALIGHT) Color.Transparent else color.copy(alpha = if (isPast) 0.3f else 1f)))
                }
                when {
                    isCurrent -> Box(Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(16.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                        }
                    }
                    terminus -> Box(Modifier.size(14.dp).clip(CircleShape).background(color.copy(alpha = if (isPast) 0.4f else 1f)), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface))
                    }
                    else -> Box(Modifier.size(8.dp).clip(CircleShape).background(color.copy(alpha = if (isPast) 0.35f else 1f)))
                }
            }
            Text(
                row.name,
                style = if (isCurrent) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent || terminus) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isPast) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (caption != null) {
                Box(Modifier.background(captionColor.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(caption, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = captionColor)
                }
            }
        }
    }

    @Composable
    private fun ConnectionRiskCard(
        risk: TransferRisk,
        onFindAlternatives: () -> Unit,
        t: (String, String, String, String) -> String,
    ) {
        val missed = risk.status == "missed"
        val title = if (missed)
            t("This connection may be missed", "Αυτή η ανταπόκριση μπορεί να χαθεί", "Kjo lidhje mund të humbasë", "Questa coincidenza potrebbe saltare")
        else
            t("This connection is tight", "Αυτή η ανταπόκριση είναι στενή", "Kjo lidhje është e ngushtë", "Questa coincidenza è stretta")
        fun mins(s: Int?): Int? = s?.let { maxOf(0, (it + 30) / 60) }
        val avail = mins(risk.availableSeconds); val allow = mins(risk.minimumSeconds)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("⚠ $title", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (avail != null && allow != null) {
                Text(
                    t("$avail min available; allow $allow min to change.",
                        "$avail λεπ διαθέσιμα, χρειάζονται $allow λεπ για αλλαγή.",
                        "$avail min në dispozicion, duhen $allow min për ndërrim.",
                        "$avail min disponibili, servono $allow min per cambiare."),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onFindAlternatives) {
                Text(t("Find alternatives", "Βρες εναλλακτικές", "Gjej alternativa", "Trova alternative"))
            }
        }
    }

    /**
     * One segment per leg in the leg's line colour, filled to the rider's
     * position, with a "stop X of Y" caption: the journey's shape at a glance.
     */
    @Composable
    private fun LegProgressBar(
        journey: GuidanceJourney,
        position: GuidancePosition,
        lineColors: Map<String, Color>,
        progress: Float,
        arrived: Boolean,
        t: (String, String, String, String) -> String,
    ) {
        val segments = GoTimeline.legProgress(journey, position)
        val total = GoTimeline.stopCount(journey)
        val done = GoTimeline.stopsRidden(journey, position)
        Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                segments.forEach { seg ->
                    val color = lineColors[seg.lineId] ?: MaterialTheme.colorScheme.primary
                    Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.18f))) {
                        if (seg.fraction > 0.0) {
                            Box(
                                Modifier.fillMaxHeight()
                                    .fillMaxWidth(seg.fraction.toFloat().coerceIn(0.02f, 1f))
                                    .clip(RoundedCornerShape(50)).background(color),
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (arrived) t("Journey complete", "Το ταξίδι ολοκληρώθηκε", "Udhëtimi përfundoi", "Viaggio completato")
                    else t("Stop $done of $total", "Στάση $done από $total", "Ndalesa $done nga $total", "Fermata $done di $total"),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    @Composable
    private fun heroCard(g: JourneyGuidance, alightStation: String, t: (String, String, String, String) -> String, accent: Color) {
        // The get-off cue is the one moment that matters most, so it is filled with
        // the line colour; every other moment sits on a light wash of that colour,
        // matching the iOS hero.
        val emphasize = g is JourneyGuidance.GetOffNext
        val (stateLabel, headline, detail) = describe(g, alightStation, t)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (emphasize) accent else accent.copy(alpha = 0.12f),
                    RoundedCornerShape(24.dp),
                )
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val onHero = if (emphasize) Color.White else MaterialTheme.colorScheme.onSurface
            val onHeroDim = if (emphasize) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
            Text(stateLabel.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = if (emphasize) onHeroDim else accent)
            Text(headline, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (emphasize) onHero else accent)
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodyLarge, color = onHeroDim)
            }
        }
    }

    /** (stateLabel, headline, detail) for a guidance instruction, localized. */
    // "N stops to <alight>" for the ride/board sub (S06): stops remaining to this
    // leg's alight point, named, so it is never confused with the S05 intermediate
    // count (finding 3). Singular/plural per locale.
    private fun stopsToAlight(n: Int, alight: String, t: (String, String, String, String) -> String): String =
        if (n == 1)
            t("1 stop to $alight", "1 στάση μέχρι $alight", "1 ndalesë deri te $alight", "1 fermata fino a $alight")
        else
            t("$n stops to $alight", "$n στάσεις μέχρι $alight", "$n ndalesa deri te $alight", "$n fermate fino a $alight")

    private fun describe(g: JourneyGuidance, alightStation: String, t: (String, String, String, String) -> String): Triple<String, String, String> =
        when (g) {
            is JourneyGuidance.Board -> Triple(
                t("Ready to board", "Έτοιμος για επιβίβαση", "Gati për të hipur", "Pronto a salire"),
                t("Board ${g.lineId} toward ${g.towards}", "Επιβίβαση ${g.lineId} προς ${g.towards}", "Hip në ${g.lineId} drejt ${g.towards}", "Sali su ${g.lineId} verso ${g.towards}"),
                stopsToAlight(g.stopsRemaining, alightStation.ifEmpty { g.nextStation }, t),
            )
            is JourneyGuidance.Ride -> Triple(
                t("Riding", "Σε κίνηση", "Duke udhëtuar", "In viaggio"),
                t("Stay on ${g.lineId} toward ${g.towards}", "Μείνε στη ${g.lineId} προς ${g.towards}", "Qëndro në ${g.lineId} drejt ${g.towards}", "Resta su ${g.lineId} verso ${g.towards}"),
                stopsToAlight(g.stopsRemaining, alightStation.ifEmpty { g.nextStation }, t),
            )
            is JourneyGuidance.GetOffNext -> Triple(
                t("Alight soon", "Αποβίβαση σύντομα", "Zbrit së shpejti", "Scendi a breve"),
                t("Get off next: ${g.nextStation}", "Αποβίβαση στην επόμενη: ${g.nextStation}", "Zbrit në tjetrën: ${g.nextStation}", "Scendi alla prossima: ${g.nextStation}"),
                if (g.isDestination)
                    t("Your destination is next", "Ο προορισμός σου είναι η επόμενη", "Destinacioni yt është tjetra", "La tua destinazione è la prossima")
                else if (g.transferTo != null)
                    t("Then change to ${g.transferTo}", "Μετά αλλαγή σε ${g.transferTo}", "Pastaj ndërro në ${g.transferTo}", "Poi cambia in ${g.transferTo}")
                else "",
            )
            is JourneyGuidance.Transfer -> Triple(
                t("Transfer", "Μετεπιβίβαση", "Ndërrim", "Cambio"),
                t("Change to ${g.toLineId} toward ${g.towards}", "Αλλαγή σε ${g.toLineId} προς ${g.towards}", "Ndërro në ${g.toLineId} drejt ${g.towards}", "Cambia su ${g.toLineId} verso ${g.towards}"),
                t("at ${g.atStation}", "στον σταθμό ${g.atStation}", "te ${g.atStation}", "a ${g.atStation}"),
            )
            is JourneyGuidance.Arrived -> Triple(
                t("Arrived", "Έφτασες", "Mbërritët", "Arrivato"),
                t("You've arrived at ${g.station}", "Έφτασες στον ${g.station}", "Mbërritët në ${g.station}", "Sei arrivato a ${g.station}"),
                "",
            )
        }
}
