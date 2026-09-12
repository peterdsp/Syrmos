package com.syrmos.app.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.data.repository.LineRepositoryImpl
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.app.journey.SavedJourneysRepository
import com.syrmos.core.domain.journey.JourneyPlanAdapter
import com.syrmos.core.domain.journey.SchedulePlanner
import com.syrmos.core.domain.usecase.ComputeDeparturesFromBandsUseCase
import com.syrmos.core.domain.usecase.PlanJourneyUseCase
import com.syrmos.core.model.planner.JourneyResult
import com.syrmos.core.model.transit.Direction
import com.syrmos.core.model.journey.FeasibilityStatus
import com.syrmos.core.model.journey.JourneyOption
import com.syrmos.core.model.journey.JourneyPreferences
import com.syrmos.core.model.journey.LegKind
import com.syrmos.core.model.journey.Ranking
import com.syrmos.core.model.journey.SavedJourney
import com.syrmos.core.model.transit.Station
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes
import org.koin.compose.koinInject

/**
 * Android mirror of the web Plan flow (Phase P). Pick From/To, Find routes, and
 * see a ranked option with feasibility, powered by the SAME Kotlin engines the
 * web build mirrors (PlanJourneyUseCase -> JourneyPlanAdapter -> feasibility +
 * ranker). When the live departure projection covers the route it feeds a real
 * timetable so feasibility is real (comfortable/tight); otherwise it falls back to
 * an honest estimated option ("~" duration, "Estimated times" chip), never a
 * fabricated clock.
 */
class PlanScreenRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val stationRepo = koinInject<StationRepositoryImpl>()
        val lineRepo = koinInject<LineRepositoryImpl>()
        val departuresUseCase = koinInject<ComputeDeparturesFromBandsUseCase>()
        val useCase = remember { PlanJourneyUseCase(stationRepo, lineRepo) }
        val scope = rememberCoroutineScope()
        val lang by LocalizationManager.language.collectAsState()

        var stations by remember { mutableStateOf<List<Station>>(emptyList()) }
        var fromId by remember { mutableStateOf<String?>(null) }
        var toId by remember { mutableStateOf<String?>(null) }
        var open by remember { mutableStateOf<String?>(null) } // "from" | "to" | null
        var query by remember { mutableStateOf("") }
        var options by remember { mutableStateOf<List<JourneyOption>>(emptyList()) }
        var selectedIdx by remember { mutableStateOf(0) }
        var planned by remember { mutableStateOf(false) }
        var mode by remember { mutableStateOf("now") } // "now" | "arriveBy" | "lastConnection"
        var arriveByText by remember { mutableStateOf("") } // "HH:MM"

        // Saved journeys (S08 / J05): locally owned, no account.
        val savedItems by SavedJourneysRepository.items.collectAsState()
        var pendingUndo by remember { mutableStateOf<SavedJourney?>(null) }
        var renameTarget by remember { mutableStateOf<SavedJourney?>(null) }
        var renameText by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            SavedJourneysRepository.refresh()
            stations = stationRepo.getAllStations().first()
        }
        // 5s Undo window for the most recent delete.
        LaunchedEffect(pendingUndo) {
            if (pendingUndo != null) { delay(5000); pendingUndo = null }
        }

        fun name(id: String?): String {
            val s = stations.firstOrNull { it.id == id } ?: return "-"
            return if (lang == AppLanguage.GREEK) s.nameEl else s.name
        }
        fun t(en: String, el: String, sq: String, it: String) = when (lang) {
            AppLanguage.GREEK -> el; AppLanguage.ALBANIAN -> sq; AppLanguage.ITALIAN -> it; else -> en
        }

        fun runPlan() {
            val f = fromId; val to = toId
            if (f == null || to == null) return
            scope.launch {
                val serviceDate = Clock.System.now().toLocalDateTime(TimeZone.of("Europe/Athens")).date
                val now = Clock.System.now()
                val horizon = if (mode == "now") 8 else 60
                // k-shortest via line-banning: base route + one re-plan per line the
                // base uses removed. Each candidate is scheduled with its own timetable
                // and ranked together (dedup + cap 3).
                val base = useCase.invoke(f, to).first()
                val candidates = mutableListOf(base)
                base?.segments?.map { it.lineId }?.distinct()?.forEach { banned ->
                    candidates += useCase.invoke(f, to, setOf(banned)).first()
                }
                val arriveBy = if (mode == "arriveBy") parseArriveBy(arriveByText, now) else null
                options = JourneyPlanAdapter.rankCandidates(
                    candidates, Ranking.FASTEST, serviceDate,
                    requestedInstant = now,
                    arriveByInstant = arriveBy,
                    lastConnection = (mode == "lastConnection"),
                    timetableFor = { r -> buildTimetable(r, departuresUseCase, now, horizon) },
                )
                selectedIdx = 0
                planned = true
            }
        }

        fun pairName(from: String, to: String) = name(from) + " → " + name(to)
        fun saveCurrent() {
            val f = fromId; val to = toId
            if (f == null || to == null || f == to) return
            SavedJourneysRepository.save(
                SavedJourney(
                    id = SavedJourneysRepository.newId(),
                    fromId = f, toId = to,
                    createdAt = Clock.System.now(),
                    label = null,
                    preferences = JourneyPreferences(ranking = Ranking.FASTEST),
                ),
            )
        }
        fun loadSaved(entry: SavedJourney) {
            fromId = entry.fromId; toId = entry.toId; open = null; query = ""
            runPlan()
        }
        fun deleteSaved(entry: SavedJourney) {
            SavedJourneysRepository.remove(entry.id)
            pendingUndo = entry
        }

        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "Πίσω", "Prapa", "Indietro"))
                    }
                    Text(
                        t("Plan a journey", "Σχεδίασε διαδρομή", "Planifiko udhëtim", "Pianifica un viaggio"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                endpointRow(t("From", "Από", "Nga", "Da"), name(fromId)) { open = if (open == "from") null else "from" }
                endpointRow(t("To", "Προς", "Për", "A"), name(toId)) { open = if (open == "to") null else "to" }

                if (open != null) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(t("Search station", "Αναζήτηση σταθμού", "Kërko stacion", "Cerca stazione")) },
                    )
                    val q = query.trim().lowercase()
                    val matches = stations.filter {
                        q.isEmpty() || it.name.lowercase().contains(q) || it.nameEl.lowercase().contains(q)
                    }.take(40)
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                        items(matches, key = { it.id }) { st ->
                            Text(
                                text = if (lang == AppLanguage.GREEK) st.nameEl else st.name,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (open == "from") fromId = st.id else toId = st.id
                                    open = null; query = ""
                                }.padding(vertical = 14.dp, horizontal = 4.dp),
                            )
                        }
                    }
                }

                // Travel-time mode: Leave now / Arrive by / Last train home.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    listOf(
                        "now" to t("Leave now", "Τώρα", "Tani", "Ora"),
                        "arriveBy" to t("Arrive by", "Άφιξη έως", "Mbërri", "Arriva"),
                        "lastConnection" to t("Last train", "Τελευταίο", "I fundit", "Ultimo"),
                    ).forEach { (id, label) ->
                        androidx.compose.material3.FilterChip(
                            selected = mode == id,
                            onClick = { mode = id },
                            label = { Text(label, maxLines = 1) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (mode == "arriveBy") {
                    OutlinedTextField(
                        value = arriveByText,
                        onValueChange = { arriveByText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(t("Arrive by (HH:MM)", "Άφιξη έως (ΩΩ:ΛΛ)", "Mbërri (OO:MM)", "Arriva (HH:MM)")) },
                    )
                }

                Button(
                    onClick = { runPlan() },
                    enabled = fromId != null && toId != null && open == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(t("Find routes", "Βρες διαδρομές", "Gjej rrugët", "Trova percorsi")) }

                // In a backward mode only options that actually scheduled are usable.
                val usable = if (mode == "now") options else options.filter { it.departureInstant != null }
                when {
                    planned && usable.isEmpty() -> Text(
                        when {
                            mode == "lastConnection" -> t("No more trains tonight.", "Δεν υπάρχουν άλλα τρένα απόψε.", "Nuk ka më trena sonte.", "Nessun altro treno stanotte.")
                            mode == "arriveBy" -> t("No journey arrives by that time.", "Καμία διαδρομή δεν φτάνει ως τότε.", "Asnjë udhëtim s'mbërrin në kohë.", "Nessun viaggio arriva in tempo.")
                            else -> t("No route found.", "Δεν βρέθηκε διαδρομή.", "Nuk u gjet rrugë.", "Nessun percorso trovato.")
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    usable.isNotEmpty() -> {
                        val count = usable.size
                        val alreadySaved = fromId != null && toId != null &&
                            SavedJourneysRepository.isSaved(fromId!!, toId!!)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "$count " + (if (count == 1) t("route", "διαδρομή", "rrugë", "percorso") else t("routes", "διαδρομές", "rrugë", "percorsi")),
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            )
                            OutlinedButton(onClick = { saveCurrent() }, enabled = !alreadySaved) {
                                Text(
                                    if (alreadySaved) t("Saved", "Αποθηκεύτηκε", "U ruajt", "Salvato")
                                    else t("Save journey", "Αποθήκευση", "Ruaj udhëtimin", "Salva viaggio"),
                                )
                            }
                        }
                        usable.forEachIndexed { i, opt ->
                            OptionCard(
                                opt = opt, selected = i == selectedIdx, lang = lang, t = ::t,
                                leaveByLabel = if (mode == "now") null else {
                                    val dep = opt.departureInstant
                                    if (dep == null) null else {
                                        val lbl = if (mode == "lastConnection")
                                            t("Last train home leaves", "Το τελευταίο τρένο φεύγει", "Treni i fundit niset", "L'ultimo treno parte")
                                        else t("Leave by", "Αναχώρηση έως", "Nisu deri", "Parti entro")
                                        "$lbl ${athensHm(dep)}"
                                    }
                                },
                                onClick = { selectedIdx = i },
                            )
                        }
                    }
                }

                // --- Saved journeys (S08 / J05) -----------------------------
                HorizontalDivider()
                Text(
                    t("Saved journeys", "Αποθηκευμένες διαδρομές", "Udhëtimet e ruajtura", "Viaggi salvati"),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                )
                if (pendingUndo != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t("Journey deleted", "Η διαδρομή διαγράφηκε", "Udhëtimi u fshi", "Viaggio eliminato"))
                        TextButton(onClick = {
                            pendingUndo?.let { SavedJourneysRepository.save(it) }
                            pendingUndo = null
                        }) { Text(t("Undo", "Αναίρεση", "Zhbëj", "Annulla")) }
                    }
                }
                if (savedItems.isEmpty()) {
                    Text(
                        t(
                            "No saved journeys yet. Plan a route and tap Save.",
                            "Καμία αποθηκευμένη διαδρομή. Σχεδίασε μια διαδρομή και πάτα Αποθήκευση.",
                            "Ende s'ka udhëtime të ruajtura. Planifiko një rrugë dhe shtyp Ruaj.",
                            "Nessun viaggio salvato. Pianifica un percorso e tocca Salva.",
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    SavedJourneysList(
                        items = savedItems,
                        pairName = ::pairName,
                        renameLabel = t("Rename", "Μετονομασία", "Riemërto", "Rinomina"),
                        deleteLabel = t("Delete", "Διαγραφή", "Fshi", "Elimina"),
                        dragLabel = t("Drag to reorder", "Σύρε για αναδιάταξη", "Zvarrit për të risistemuar", "Trascina per riordinare"),
                        onOpen = { loadSaved(it) },
                        onRename = { renameTarget = it; renameText = it.label ?: "" },
                        onDelete = { deleteSaved(it) },
                        onReorder = { SavedJourneysRepository.reorder(it) },
                    )
                }
            }
        }

        // Rename dialog (S08): sets a label; a blank name clears it back to null.
        val target = renameTarget
        if (target != null) {
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text(t("Name this journey", "Ονόμασε τη διαδρομή", "Emërto këtë udhëtim", "Nomina questo viaggio")) },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        SavedJourneysRepository.rename(target.id, renameText)
                        renameTarget = null
                    }) { Text(t("Save", "Αποθήκευση", "Ruaj", "Salva")) }
                },
                dismissButton = {
                    TextButton(onClick = { renameTarget = null }) {
                        Text(t("Cancel", "Άκυρο", "Anulo", "Annulla"))
                    }
                },
            )
        }
    }

    /**
     * Drag-to-reorder saved journeys (S08). A long-press on the grip handle lifts a
     * row; dragging past a neighbour's mid-point swaps them in a local id order, and
     * on release the new order is persisted through the shared `reorder` op. Rows are
     * a fixed height so the swap threshold is stable; display always pulls fresh
     * label/pair from `items` (order is kept as ids, so a rename mid-session shows).
     */
    @Composable
    private fun SavedJourneysList(
        items: List<SavedJourney>,
        pairName: (String, String) -> String,
        renameLabel: String,
        deleteLabel: String,
        dragLabel: String,
        onOpen: (SavedJourney) -> Unit,
        onRename: (SavedJourney) -> Unit,
        onDelete: (SavedJourney) -> Unit,
        onReorder: (List<String>) -> Unit,
    ) {
        val rowHeight = 64.dp
        val spacing = 6.dp
        val density = LocalDensity.current
        val pitchPx = with(density) { (rowHeight + spacing).toPx() }

        // Order kept as ids; resets only when the set of journeys changes (add/delete),
        // not on a rename or on the persist that echoes our own reorder back.
        var orderIds by remember(items.map { it.id }.toSet()) { mutableStateOf(items.map { it.id }) }
        var draggingId by remember { mutableStateOf<String?>(null) }
        var dragOffset by remember { mutableStateOf(0f) }

        val entries = orderIds.mapNotNull { id -> items.firstOrNull { it.id == id } }

        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            entries.forEach { entry ->
                val isDragging = entry.id == draggingId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragging) dragOffset else 0f },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "⠿",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .pointerInput(entry.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { draggingId = entry.id; dragOffset = 0f },
                                    onDragEnd = { draggingId = null; dragOffset = 0f; onReorder(orderIds) },
                                    onDragCancel = { draggingId = null; dragOffset = 0f; onReorder(orderIds) },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val cur = orderIds.indexOf(draggingId)
                                        if (cur < 0) return@detectDragGesturesAfterLongPress
                                        if (dragOffset > pitchPx / 2 && cur < orderIds.lastIndex) {
                                            val next = orderIds.toMutableList()
                                            next.add(cur + 1, next.removeAt(cur))
                                            orderIds = next
                                            dragOffset -= pitchPx
                                            // Persist on each swap so the saved order always matches
                                            // what is shown, even if the release ends as a cancel.
                                            onReorder(next)
                                        } else if (dragOffset < -pitchPx / 2 && cur > 0) {
                                            val next = orderIds.toMutableList()
                                            next.add(cur - 1, next.removeAt(cur))
                                            orderIds = next
                                            dragOffset += pitchPx
                                            onReorder(next)
                                        }
                                    },
                                )
                            }
                            .semantics { contentDescription = dragLabel },
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .clickable { onOpen(entry) }
                            .padding(12.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            entry.label ?: pairName(entry.fromId, entry.toId),
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        )
                        if (entry.label != null) {
                            Text(
                                pairName(entry.fromId, entry.toId),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { onRename(entry) }, modifier = Modifier.semantics { contentDescription = renameLabel }) {
                        Text("✎", style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(onClick = { onDelete(entry) }, modifier = Modifier.semantics { contentDescription = deleteLabel }) {
                        Text("🗑", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    @Composable
    private fun endpointRow(label: String, value: String, onClick: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(12.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }

    @Composable
    private fun OptionCard(
        opt: JourneyOption,
        selected: Boolean,
        lang: AppLanguage,
        t: (String, String, String, String) -> String,
        leaveByLabel: String?,
        onClick: () -> Unit,
    ) {
        val minutes = ((opt.durationSeconds ?: 0) / 60).coerceAtLeast(1)
        val chain = opt.legs.filter { it.kind == LegKind.RIDE }.mapNotNull { it.lineId }.joinToString(" → ")
        val changes = if (opt.transferCount == 1) t("1 change", "1 αλλαγή", "1 ndërrim", "1 cambio")
        else "${opt.transferCount} " + t("changes", "αλλαγές", "ndërrime", "cambi")
        val feas = when (opt.feasibility.status) {
            FeasibilityStatus.COMFORTABLE -> t("Comfortable", "Άνετη", "Komode", "Comoda")
            FeasibilityStatus.TIGHT -> t("Tight", "Στενή", "E ngushtë", "Stretta")
            FeasibilityStatus.MISSED -> t("Missed", "Χαμένη", "Humbur", "Persa")
            FeasibilityStatus.UNKNOWN -> t("Estimated times", "Εκτιμώμενοι χρόνοι", "Kohë të vlerësuara", "Orari stimato")
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(16.dp),
                )
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("~$minutes " + t("min", "λεπ", "min", "min") + " · $changes · $chain", color = MaterialTheme.colorScheme.onSurface)
            Text(feas, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (leaveByLabel != null) {
                Text(leaveByLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * Build a real timetable for a planned [JourneyResult] from the live departure
 * projection, the Android peer of the web `buildTimetable`. For each ride segment
 * it projects the next departures of that line at the board station (both
 * directions, filtered by line, feasibility-grade) as absolute instants, and uses
 * the segment's own estimated minutes for the leg travel time. Empty when no
 * projection is available, so the adapter keeps the honest estimated option.
 */
private fun buildTimetable(
    result: JourneyResult,
    departures: ComputeDeparturesFromBandsUseCase,
    now: kotlinx.datetime.Instant,
    horizon: Int = 20,
): SchedulePlanner.Timetable {
    val depMap = HashMap<String, MutableList<kotlinx.datetime.Instant>>()
    val legSeconds = HashMap<String, Int>()
    for (seg in result.segments) {
        val lineIds = if (seg.lineId == "M3") listOf("M3", "M3_AIR") else listOf(seg.lineId)
        val list = depMap.getOrPut(seg.lineId + "|" + seg.fromStationId) { ArrayList() }
        for (dir in listOf(Direction.OUTBOUND, Direction.INBOUND)) {
            // Forward needs ~20; backward (arrive-by / last train) needs the whole
            // remaining service day so it can find the true latest catchable train.
            val ups = runCatching { departures.invoke(lineIds, dir, horizon, seg.fromStationId) }.getOrElse { emptyList() }
            for (u in ups) {
                if (u.lineId == seg.lineId || (seg.lineId == "M3" && u.lineId == "M3_AIR")) {
                    list.add(now + u.minutesAway.minutes)
                }
            }
        }
        legSeconds[seg.lineId + "|" + seg.fromStationId + "|" + seg.toStationId] = seg.estimatedMinutes * 60
    }
    depMap.entries.removeAll { it.value.isEmpty() }
    return SchedulePlanner.Timetable(departures = depMap, legSeconds = legSeconds)
}

/** Parse an "HH:MM" Athens clock into the next occurrence as an absolute instant. */
private fun parseArriveBy(text: String, now: kotlinx.datetime.Instant): kotlinx.datetime.Instant? {
    val m = Regex("""^\s*(\d{1,2}):(\d{2})\s*$""").find(text) ?: return null
    val target = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
    val zone = TimeZone.of("Europe/Athens")
    val nowLocal = now.toLocalDateTime(zone).time
    val nowMin = nowLocal.hour * 60 + nowLocal.minute
    var delta = target - nowMin
    if (delta < 0) delta += 24 * 60
    return now + delta.minutes
}

/** Format an absolute instant as an Athens HH:MM clock. */
private fun athensHm(instant: kotlinx.datetime.Instant): String {
    val t = instant.toLocalDateTime(TimeZone.of("Europe/Athens")).time
    return t.hour.toString().padStart(2, '0') + ":" + t.minute.toString().padStart(2, '0')
}
