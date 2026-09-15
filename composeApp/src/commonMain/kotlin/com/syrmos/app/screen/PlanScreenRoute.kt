package com.syrmos.app.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
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
import com.syrmos.core.common.DataFreshness
import com.syrmos.core.common.FreshnessBannerState
import com.syrmos.core.common.FreshnessPresentation
import com.syrmos.core.common.LiveDataFreshness
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.data.repository.LineRepositoryImpl
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.app.journey.ActiveJourneyRepository
import com.syrmos.app.journey.SavedJourneysRepository
import com.syrmos.core.domain.go.GuidanceJourney
import com.syrmos.core.domain.go.GuidanceLeg
import com.syrmos.core.domain.go.GuidanceStop
import com.syrmos.core.data.sync.AnnouncementsRepository
import com.syrmos.core.domain.assistant.AdvisorySeverity
import com.syrmos.core.domain.assistant.ServiceNotice
import com.syrmos.core.domain.journey.AccessibilityConfidence
import com.syrmos.core.domain.journey.AccessibilityDisclosure
import com.syrmos.core.domain.journey.ActiveJourneyStore
import com.syrmos.core.domain.journey.DisruptionExclusion
import com.syrmos.core.domain.journey.DisruptionOutcome
import com.syrmos.core.domain.journey.ConnectionRisk
import com.syrmos.core.domain.journey.JourneyDetail
import com.syrmos.core.domain.journey.JourneyPlanAdapter
import com.syrmos.core.domain.journey.SchedulePlanner
import com.syrmos.core.domain.usecase.ComputeDeparturesFromBandsUseCase
import com.syrmos.core.domain.usecase.PlanJourneyUseCase
import com.syrmos.core.model.planner.JourneyResult
import com.syrmos.core.model.transit.Direction
import com.syrmos.core.model.journey.AccessibilityPreference
import com.syrmos.core.model.journey.FeasibilityStatus
import com.syrmos.core.model.journey.JourneyOption
import com.syrmos.core.model.journey.JourneyPreferences
import com.syrmos.core.model.journey.Leg
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
        val announcementsRepo = koinInject<AnnouncementsRepository>()
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
        // Phase R: rider accessibility preference. When on, each route discloses
        // its step-free confidence honestly (unknown until per-station data lands).
        var stepFree by remember { mutableStateOf(false) }
        // Phase R: disruption exclusion outcome (S10 suspended segment). Never route
        // through a CLOSURE-affected line.
        var disruption by remember { mutableStateOf<DisruptionOutcome?>(null) }

        // Saved journeys (S08 / J05): locally owned, no account.
        val savedItems by SavedJourneysRepository.items.collectAsState()
        // The single live GO session (S06): shown as a resume banner when present.
        val activeJourney by ActiveJourneyRepository.active.collectAsState()
        // Phase N J07: freshness banner signals. Collect both network + last-live so
        // the banner recomposes when live data lands (markLive), and a 30s tick so an
        // idle live->predicted staleness transition also refreshes (parity with iOS).
        val networkAvailable by LiveDataFreshness.isNetworkAvailable.collectAsState()
        val lastLiveUpdate by LiveDataFreshness.lastLiveUpdate.collectAsState()
        var freshnessTick by remember { mutableStateOf(0) }
        LaunchedEffect(Unit) { while (true) { delay(30_000); freshnessTick++ } }
        // Phase R S10: set when a loaded saved journey references a gone station.
        var invalidSavedNote by remember { mutableStateOf<String?>(null) }
        var pendingUndo by remember { mutableStateOf<SavedJourney?>(null) }
        var renameTarget by remember { mutableStateOf<SavedJourney?>(null) }
        var renameText by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            SavedJourneysRepository.refresh()
            ActiveJourneyRepository.refresh()
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

        // Projects the live announcement feed into the disruption engine's notice
        // shape (severity + affected line ids). Pure mapping over the current feed.
        suspend fun disruptionNotices(repo: AnnouncementsRepository): List<ServiceNotice> =
            repo.feed.first().announcements
                .filter { it.isServiceAlert || it.severity != "info" }
                .map { a ->
                    ServiceNotice(
                        id = a.id,
                        text = a.title,
                        affectedLineIds = a.affectedLines,
                        severity = AdvisorySeverity.fromRaw(a.severity),
                    )
                }

        fun runPlan() {
            val f = fromId; val to = toId
            if (f == null || to == null) return
            scope.launch {
                val serviceDate = Clock.System.now().toLocalDateTime(TimeZone.of("Europe/Athens")).date
                val now = Clock.System.now()
                val horizon = if (mode == "now") 8 else 60
                // Phase R disruption exclusion: suspended (CLOSURE) lines are banned
                // so no candidate rides closed track. Map normalized suspended ids
                // back to real Line ids for the planner's exact-id ban.
                val notices = disruptionNotices(announcementsRepo)
                val suspended = DisruptionExclusion.suspendedLineIds(notices)
                val rawSuspended = if (suspended.isEmpty()) emptySet() else
                    lineRepo.getAllLines().first()
                        .filter { suspended.contains(DisruptionExclusion.normalizeLine(it.id)) }
                        .map { it.id }.toSet()

                val arriveBy = if (mode == "arriveBy") parseArriveBy(arriveByText, now) else null
                suspend fun optionsFor(extraBan: Set<String>): List<JourneyOption> {
                    // k-shortest via line-banning: base route + one re-plan per line the
                    // base uses removed. Each candidate is scheduled with its own timetable
                    // and ranked together (dedup + cap 3).
                    val base = useCase.invoke(f, to, extraBan).first()
                    val candidates = mutableListOf(base)
                    base?.segments?.map { it.lineId }?.distinct()?.forEach { banned ->
                        candidates += useCase.invoke(f, to, extraBan + banned).first()
                    }
                    return JourneyPlanAdapter.rankCandidates(
                        candidates, Ranking.FASTEST, serviceDate,
                        requestedInstant = now,
                        arriveByInstant = arriveBy,
                        lastConnection = (mode == "lastConnection"),
                        timetableFor = { r -> buildTimetable(r, departuresUseCase, now, horizon) },
                    )
                }

                val naive = optionsFor(emptySet())
                val avoiding = if (rawSuspended.isEmpty()) naive else optionsFor(rawSuspended)
                val outcome = DisruptionExclusion.classify(avoiding, naive, notices)
                disruption = outcome
                options = if (outcome is DisruptionOutcome.Suspended) emptyList() else avoiding
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
        fun stationExists(id: String?): Boolean = id != null && stations.any { it.id == id }
        // Phase R S10 invalid saved/deep-link id: preserve the endpoints that still
        // resolve, name the missing one, and prompt for a replacement.
        fun loadSaved(entry: SavedJourney) {
            val fromOk = stationExists(entry.fromId)
            val toOk = stationExists(entry.toId)
            fromId = if (fromOk) entry.fromId else null
            toId = if (toOk) entry.toId else null
            open = null; query = ""
            if (fromOk && toOk) {
                invalidSavedNote = null
                runPlan()
            } else {
                planned = false
                options = emptyList()
                invalidSavedNote = t(
                    "A station in this saved journey is no longer available. Choose a replacement.",
                    "Ένας σταθμός σε αυτή την αποθηκευμένη διαδρομή δεν είναι πλέον διαθέσιμος. Επίλεξε αντικατάσταση.",
                    "Një stacion në këtë udhëtim të ruajtur nuk është më i disponueshëm. Zgjidh një zëvendësim.",
                    "Una stazione di questo viaggio salvato non è più disponibile. Scegli un'alternativa.")
            }
        }
        fun deleteSaved(entry: SavedJourney) {
            SavedJourneysRepository.remove(entry.id)
            pendingUndo = entry
        }
        // S05 -> S06: build a GuidanceJourney from the option's ride legs, persist a
        // fresh live session (so it survives a kill / navigation), and open GO.
        fun startGo(opt: JourneyOption) {
            scope.launch {
                val guidance = buildGuidanceJourney(opt, stationRepo, lang)
                if (guidance.legs.isEmpty()) return@launch
                ActiveJourneyRepository.set(
                    ActiveJourneyStore.start(ActiveJourneyRepository.newId(), opt, guidance, Clock.System.now()),
                )
                navigator.push(GoJourneyScreenRoute(guidance, transferRisksOf(opt)))
            }
        }
        // Resume an in-progress session: rebuild guidance from the frozen snapshot
        // (names re-resolved in the current language) and reopen GO where it left off.
        fun resumeGo(active: com.syrmos.core.model.journey.ActiveJourney) {
            scope.launch {
                val guidance = buildGuidanceJourney(active.itinerarySnapshot, stationRepo, lang)
                if (guidance.legs.isNotEmpty()) navigator.push(GoJourneyScreenRoute(guidance))
            }
        }

        // Phase R S07: honor a "Find alternatives" re-plan request from GO (re-plan
        // from the rider's current confirmed station to the destination).
        val replanRequest by PlanReplanRequest.pending.collectAsState()
        LaunchedEffect(replanRequest) {
            replanRequest?.let { (f, t) ->
                fromId = f; toId = t; open = null; invalidSavedNote = null
                PlanReplanRequest.consume()
                runPlan()
            }
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
                modifier = Modifier.fillMaxSize().padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Resume banner (S06): a live GO session survives a kill / navigation.
                activeJourney?.let { active ->
                    val fromId2 = active.itinerarySnapshot.legs.firstOrNull()?.fromId
                    val toId2 = active.itinerarySnapshot.legs.lastOrNull()?.toId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
                            .clickable { resumeGo(active) }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                t("Journey in progress", "Διαδρομή σε εξέλιξη", "Udhëtim në vazhdim", "Viaggio in corso"),
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                name(fromId2) + " → " + name(toId2),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { ActiveJourneyRepository.clear() }) {
                                Text(t("End", "Τέλος", "Përfundo", "Termina"))
                            }
                            Button(onClick = { resumeGo(active) }) {
                                Text(t("Resume", "Συνέχεια", "Vazhdo", "Riprendi"))
                            }
                        }
                    }
                }

                // Phase R S10 / N J07 freshness banner. Driven by the shared
                // FreshnessPresentation rule so it also shows when online but no live
                // data is available (predicted), not connectivity-only. Wording reuses
                // RUNNING_OFFLINE / PREDICTED_FROM_SCHEDULE. Plans are never blocked.
                val isLive = remember(networkAvailable, lastLiveUpdate, freshnessTick) {
                    LiveDataFreshness.freshnessNow() == DataFreshness.LIVE
                }
                val freshnessState = FreshnessPresentation.evaluate(
                    isNetworkAvailable = networkAvailable,
                    isLive = isLive,
                )
                if (FreshnessPresentation.showsBanner(freshnessState)) {
                    val offline = freshnessState == FreshnessBannerState.OFFLINE
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (offline) t("Running offline", "Εκτός σύνδεσης", "Pa internet", "Offline")
                                else t("Predicted from schedule", "Πρόβλεψη από το πρόγραμμα", "Parashikuar nga orari", "Previsto dall'orario"),
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                            )
                            Text(t("Routes use the saved timetable.", "Οι διαδρομές χρησιμοποιούν το αποθηκευμένο δρομολόγιο.",
                                "Rrugët përdorin orarin e ruajtur.", "I percorsi usano l'orario salvato."),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { LiveDataFreshness.requestRetry() }) {
                            Text(t("Retry", "Επανάληψη", "Riprovo", "Riprova"))
                        }
                    }
                }

                invalidSavedNote?.let { note ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

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
                                    // S10 recovery: once both endpoints resolve, clear + plan.
                                    if (invalidSavedNote != null && stationExists(fromId) && stationExists(toId)) {
                                        invalidSavedNote = null
                                        runPlan()
                                    }
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t("Step-free routes", "Διαδρομές χωρίς σκαλιά", "Rrugë pa shkallë", "Percorsi senza gradini"))
                    androidx.compose.material3.Switch(checked = stepFree, onCheckedChange = { stepFree = it })
                }

                Button(
                    onClick = { runPlan() },
                    enabled = fromId != null && toId != null && open == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(t("Find routes", "Βρες διαδρομές", "Gjej rrugët", "Trova percorsi")) }

                // Phase R: disclose when we routed around a suspended line.
                (disruption as? DisruptionOutcome.Routed)?.let { r ->
                    if (planned && r.excludedLineIds.isNotEmpty()) RoutingAroundChip(r.excludedLineIds, ::t)
                }

                // In a backward mode only options that actually scheduled are usable.
                val usable = if (mode == "now") options else options.filter { it.departureInstant != null }
                when {
                    planned && disruption is DisruptionOutcome.Suspended -> {
                        val s = disruption as DisruptionOutcome.Suspended
                        SuspendedState(s.affectedLineIds, s.notices, ::t)
                    }
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
                        // S05 selected-journey detail: summary + leg-by-leg timeline
                        // for the chosen option, from the shared JourneyDetail transform.
                        usable.getOrNull(selectedIdx)?.let { sel ->
                            SelectedJourneyDetail(option = sel, stepFree = stepFree, lang = lang, nm = ::name, t = ::t, onStart = { startGo(sel) })
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
                // Bottom clearance so the S05 action + saved list clear the tab bar.
                Spacer(Modifier.height(96.dp))
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

    // MARK: - S05 selected-journey detail

    /**
     * Android mirror of the web S05 detail: summary card + leg-by-leg timeline
     * (from the shared [JourneyDetail.timeline]) + honest source line + Start.
     * GO guidance is not built on Android yet (Phase G), so Start surfaces an
     * honest notice rather than a dead control.
     */
    /// Phase R S10 "Suspended segment": the only path rode a closed line and no
    /// route avoids it. Name the line(s) + operator source. Never fabricate a route.
    @Composable
    private fun SuspendedState(
        lines: Set<String>,
        notices: List<ServiceNotice>,
        t: (String, String, String, String) -> String,
    ) {
        val lineList = lines.map { it.uppercase() }.sorted().joinToString(", ")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                t("$lineList is suspended", "Η $lineList έχει ανασταλεί", "$lineList është pezulluar", "$lineList è sospesa"),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            )
            Text(
                t("No route avoids the closed section. Check the operator for alternatives and updates.",
                    "Καμία διαδρομή δεν παρακάμπτει το κλειστό τμήμα. Δες τον πάροχο για εναλλακτικές και ενημερώσεις.",
                    "Asnjë rrugë s'e shmang pjesën e mbyllur. Shiko operatorin për alternativa dhe përditësime.",
                    "Nessun percorso evita il tratto chiuso. Controlla l'operatore per alternative e aggiornamenti."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            notices.forEach { n ->
                if (n.text.isNotBlank()) {
                    Text(n.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    /// Phase R: a compact chip disclosing that the plan detours around a suspended line.
    @Composable
    private fun RoutingAroundChip(excluded: Set<String>, t: (String, String, String, String) -> String) {
        val list = excluded.map { it.uppercase() }.sorted().joinToString(", ")
        Text(
            t("Routing around suspended $list.", "Παράκαμψη της ανασταλμένης $list.",
                "Duke anashkaluar $list të pezulluar.", "Percorso che evita $list sospesa."),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    @Composable
    private fun SelectedJourneyDetail(
        option: JourneyOption,
        stepFree: Boolean,
        lang: AppLanguage,
        nm: (String?) -> String,
        t: (String, String, String, String) -> String,
        onStart: () -> Unit,
    ) {
        val rows = remember(option.id) { JourneyDetail.timeline(option) }
        val legById = remember(option.id) { option.legs.associateBy { it.id } }
        val minutes = ((option.durationSeconds ?: 0) / 60).coerceAtLeast(1)
        val anyScheduled = rows.any { it.timingKind == "scheduled" || it.timingKind == "live" }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Summary.
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("~$minutes " + t("min", "λεπ", "min", "min"),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val dep = option.departureInstant; val arr = option.arrivalInstant
                if (dep != null && arr != null) {
                    Text("${athensHm(dep)} – ${athensHm(arr)}",
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Timeline.
            Column {
                rows.forEach { r ->
                    TimelineRowView(r, legById[r.legId], lang, nm, t)
                }
            }

            // Honest source line.
            Text(
                if (anyScheduled)
                    t("Times from the published timetable.", "Χρόνοι από το επίσημο δρομολόγιο.", "Kohët nga orari zyrtar.", "Orari dal calendario ufficiale.")
                else
                    t("Estimated times — no live schedule for this route yet.", "Εκτιμώμενοι χρόνοι — χωρίς ζωντανό δρομολόγιο ακόμη.", "Kohë të vlerësuara — ende pa orar të drejtpërdrejtë.", "Orari stimato — nessun orario dal vivo per questo percorso."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Phase R accessibility-unknown disclosure. Shared engine; no per-station
            // step-free data is plumbed yet, so an honest "not confirmed" is shown
            // rather than a fabricated "accessible".
            if (stepFree) {
                val info = remember(option.id) {
                    AccessibilityDisclosure.forOption(option, AccessibilityPreference.STEP_FREE)
                }
                val text = when (info.confidence) {
                    AccessibilityConfidence.VERIFIED ->
                        t("Step-free the whole way.", "Χωρίς σκαλιά σε όλη τη διαδρομή.", "Pa shkallë gjatë gjithë rrugës.", "Senza gradini per tutto il percorso.")
                    AccessibilityConfidence.UNAVAILABLE ->
                        t("This route isn't step-free.", "Αυτή η διαδρομή δεν είναι χωρίς σκαλιά.", "Kjo rrugë nuk është pa shkallë.", "Questo percorso non è senza gradini.")
                    AccessibilityConfidence.UNKNOWN ->
                        t("Step-free access isn't confirmed for this route.", "Η πρόσβαση χωρίς σκαλιά δεν επιβεβαιώνεται για αυτή τη διαδρομή.", "Qasja pa shkallë nuk është konfirmuar për këtë rrugë.", "L'accesso senza gradini non è confermato per questo percorso.")
                }
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text(t("Start journey", "Ξεκίνα τη διαδρομή", "Nis udhëtimin", "Avvia il viaggio"))
            }
        }
    }

    @Composable
    private fun TimelineRowView(
        r: JourneyDetail.TimelineRow,
        leg: com.syrmos.core.model.journey.Leg?,
        lang: AppLanguage,
        nm: (String?) -> String,
        t: (String, String, String, String) -> String,
    ) {
        var expanded by remember { mutableStateOf(false) }
        val dashed = r.kind == "transfer" || r.kind == "walk" || r.kind == "stops"
        val major = r.node == JourneyDetail.Node.ORIGIN || r.node == JourneyDetail.Node.DESTINATION || r.node == JourneyDetail.Node.INTERCHANGE
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Clock column.
            Text(
                text = r.clock?.let { athensHm(it) } ?: if (r.kind == "board" || r.kind == "alight") "~" else "",
                modifier = Modifier.width(46.dp).padding(top = 2.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            // Node column: rail line + dot.
            Box(modifier = Modifier.width(16.dp).fillMaxHeight()) {
                val railColor = MaterialTheme.colorScheme.outline.copy(alpha = if (dashed) 0.4f else 1f)
                Box(Modifier.align(Alignment.TopCenter).width(3.dp).fillMaxHeight().background(railColor))
                if (r.kind == "board" || r.kind == "alight") {
                    val dot = if (major) 14.dp else 10.dp
                    Box(
                        Modifier.align(Alignment.TopCenter).padding(top = 3.dp).size(dot)
                            .clip(CircleShape)
                            .background(if (major) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary)
                            .then(if (major) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            // Instruction column.
            Column(modifier = Modifier.weight(1f).padding(bottom = 12.dp)) {
                when (r.kind) {
                    "board" -> Text(
                        t("Board", "Επιβίβαση", "Hip", "Sali") + " ${r.lineId ?: ""} " +
                            t("toward", "προς", "drejt", "verso") + " " + nm(r.towardsId),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                    )
                    "alight" -> Text(
                        t("Alight", "Αποβίβαση", "Zbrit", "Scendi") + " " + nm(r.stationId),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                    )
                    "stops" -> {
                        val count = r.count ?: 0
                        val label = "$count " + if (count == 1) t("stop", "στάση", "ndalesë", "fermata") else t("stops", "στάσεις", "ndalesa", "fermate")
                        Text(
                            label, modifier = Modifier.clickable { expanded = !expanded },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                        )
                        if (expanded) {
                            val mid = leg?.orderedStopIds?.drop(1)?.dropLast(1).orEmpty()
                            Text(mid.joinToString(" · ") { nm(it) },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> {
                        val mins = r.seconds?.let { (it / 60).coerceAtLeast(1) }
                        val word = if (r.kind == "walk") t("Walk", "Περπάτημα", "Ecje", "Cammina") else t("Transfer", "Μετεπιβίβαση", "Ndërrim", "Cambio")
                        Text(word + (mins?.let { " · $it " + t("min", "λεπ", "min", "min") } ?: ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * Build a [GuidanceJourney] from a planned option's ride legs, reconstructing each
 * leg's full ordered stops from its line (so an interchange is never collapsed) and
 * resolving stop names in the active language. Used both to start GO and to resume a
 * persisted session, so a resumed trip is rebuilt identically (ids match; only names
 * follow the current language). Empty when no ride leg yields two or more stops.
 */
/// Phase R S07: per-transfer connection risk via the shared ConnectionRisk
/// transform (one fixture-backed rule mirrored on web/iOS, consistent with the
/// feasibility chip). Mapped to the GO screen's local TransferRisk shape.
private fun transferRisksOf(opt: JourneyOption): List<TransferRisk> =
    ConnectionRisk.risks(opt).map { r ->
        TransferRisk(r.status.name.lowercase(), r.availableSeconds, r.recommendedSeconds)
    }

private suspend fun buildGuidanceJourney(
    opt: JourneyOption,
    stationRepo: StationRepositoryImpl,
    lang: AppLanguage,
): GuidanceJourney {
    // The guidance legs MUST stay 1:1 with the snapshot's ride legs, in order:
    // ActiveJourneyStore maps a persisted legId/confirmedStopId by ride-leg index
    // into guidance.legs. So if ANY ride leg fails to resolve two or more stops we
    // return an empty journey (GO simply does not start / resume) rather than
    // silently dropping one leg and desyncing every position after it.
    val rideLegs = opt.legs.filter { it.kind == LegKind.RIDE }
    val gLegs = mutableListOf<GuidanceLeg>()
    for (leg in rideLegs) {
        val lineId = leg.lineId ?: return GuidanceJourney(emptyList())
        val lineStations = stationRepo.getStationsOnLine(lineId).first()
        val fromIdx = lineStations.indexOfFirst { it.id == leg.fromId }
        val toIdx = lineStations.indexOfFirst { it.id == leg.toId }
        val slice = if (fromIdx >= 0 && toIdx >= 0) {
            if (fromIdx <= toIdx) lineStations.subList(fromIdx, toIdx + 1)
            else lineStations.subList(toIdx, fromIdx + 1).reversed()
        } else {
            listOfNotNull(lineStations.firstOrNull { it.id == leg.fromId },
                          lineStations.firstOrNull { it.id == leg.toId })
        }
        val stops = slice.map { GuidanceStop(it.id, if (lang == AppLanguage.GREEK) it.nameEl else it.name) }
        if (stops.size < 2) return GuidanceJourney(emptyList())
        gLegs.add(GuidanceLeg(lineId = lineId, towards = stops.last().name, stops = stops))
    }
    return GuidanceJourney(gLegs)
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
