package com.syrmos.feature.home.assistant

import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.data.repository.FavoritesRepository
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.data.sync.AnnouncementsRepository
import com.syrmos.core.data.sync.FaresRepository
import com.syrmos.core.data.sync.WeatherRepository
import com.syrmos.core.model.weather.WeatherSnapshot
import kotlinx.datetime.Clock
import com.syrmos.core.domain.assistant.Exposure
import com.syrmos.core.domain.assistant.StationComfort
import com.syrmos.core.domain.assistant.AdvisorySeverity
import com.syrmos.core.domain.assistant.AssistantIntent
import com.syrmos.core.domain.assistant.AssistantSessionContext
import com.syrmos.core.domain.assistant.RouteMemory
import com.syrmos.core.domain.assistant.ServiceAdvisory
import com.syrmos.core.domain.assistant.ServiceAdvisoryMatcher
import com.syrmos.core.domain.assistant.ServiceNotice
import com.syrmos.core.domain.assistant.AssistantQueryNormalizer
import com.syrmos.core.domain.assistant.AssistantClassifier
import com.syrmos.core.common.AriadneModelDownloader
import com.syrmos.core.common.AriadneModelState
import com.syrmos.core.common.NoOpAriadneModelDownloader
import com.syrmos.core.network.AriadneChatMessage
import com.syrmos.core.network.AriadneChatService
import com.syrmos.core.domain.assistant.AssistantVocabularyBuilder
import com.syrmos.core.domain.assistant.NoOpQueryNormalizer
import com.syrmos.core.domain.assistant.NoOpAssistantClassifier
import com.syrmos.core.domain.assistant.AthensTransitParser
import com.syrmos.core.domain.assistant.DayContext
import com.syrmos.core.domain.assistant.DayContextResolver
import com.syrmos.core.domain.assistant.MissingSlot
import com.syrmos.core.domain.usecase.ComputeDeparturesFromBandsUseCase
import com.syrmos.core.domain.usecase.FindNearestStationUseCase
import com.syrmos.core.domain.usecase.GetLastTrainUseCase
import com.syrmos.core.domain.usecase.GetLinesUseCase
import com.syrmos.core.domain.usecase.GetNextDeparturesUseCase
import com.syrmos.core.domain.usecase.PlanByArrivalUseCase
import com.syrmos.core.domain.usecase.PlanJourneyUseCase
import com.syrmos.core.model.location.UserLocation
import com.syrmos.core.domain.usecase.SearchStationsUseCase
import com.syrmos.core.domain.usecase.UpcomingDeparture
import com.syrmos.core.model.transit.Direction
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.Station
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A navigation request a chat answer can carry. */
sealed interface AssistantAction {
    data class OpenStation(val stationId: String) : AssistantAction
    data class OpenLine(val lineId: String) : AssistantAction
}

data class AssistantMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val departures: List<UpcomingDeparture> = emptyList(),
    val action: AssistantAction? = null,
    val actionLabel: String? = null,
)

data class AssistantUiState(
    val messages: List<AssistantMessage> = emptyList(),
    val ready: Boolean = false,
    val thinking: Boolean = false,
    /** Current weather is severe (thunderstorm / heavy showers / snow). Drives context-aware prompt chips. */
    val severeWeather: Boolean = false,
    /** Athens local hour (0..23). Drives time-of-day prompt selection. */
    val athensHour: Int = 12,
)

/**
 * Ariadne on Compose (Android + Web). Owns the conversation, builds the
 * vocabulary from the bundled station/line data, parses each utterance offline
 * with [AthensTransitParser], and dispatches the resulting [AssistantIntent] to
 * the deterministic use cases the app already ships. The model layer (when one
 * is added on capable devices) plugs in behind the same parser contract; this
 * resolver does not change.
 */
class AssistantViewModel(
    private val stationRepository: StationRepositoryImpl,
    private val getLinesUseCase: GetLinesUseCase,
    private val getNextDepartures: GetNextDeparturesUseCase,
    private val bandProjector: ComputeDeparturesFromBandsUseCase,
    private val getLastTrain: GetLastTrainUseCase,
    private val planJourney: PlanJourneyUseCase,
    private val planByArrival: PlanByArrivalUseCase,
    private val searchStations: SearchStationsUseCase,
    private val findNearestStation: FindNearestStationUseCase,
    private val announcementsRepository: AnnouncementsRepository,
    private val faresRepository: FaresRepository,
    private val favoritesRepository: FavoritesRepository,
    private val weatherRepository: WeatherRepository,
    /**
     * Optional on-device LLM front-end. No-op by default (rule parser only);
     * an Android build can inject a Gemini Nano-backed normalizer here.
     */
    private val queryNormalizer: AssistantQueryNormalizer = NoOpQueryNormalizer,
    /**
     * Optional on-device LLM classifier (clever tier). No-op by default (rule
     * parser only); an Android build injects a Gemini Nano-backed classifier.
     * When it grounds an intent we skip the rule parser; otherwise we fall back.
     */
    private val assistantClassifier: AssistantClassifier = NoOpAssistantClassifier,
    /**
     * Optional on-demand model downloader (Android). No-op by default, so the
     * shared download UI stays hidden where it does not apply (iOS/Web).
     */
    private val modelDownloader: AriadneModelDownloader = NoOpAriadneModelDownloader,
    private val ariadneChatService: AriadneChatService? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** State + progress for the shared "Download Ariadne's brain" control. */
    val modelState: StateFlow<AriadneModelState> = modelDownloader.state
    val modelProgress: StateFlow<Float> = modelDownloader.progress
    fun downloadModel() = modelDownloader.start()
    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private var parser: AthensTransitParser? = null
    private var stations: List<Station> = emptyList()
    private var lines: List<Line> = emptyList()
    private var nextId = 0L
    private var lastLocation: UserLocation? = null

    // Conversation state: when Ariadne returns NeedsClarification we stash
    // the pending intent so the next user turn can fill the missing slot
    // ("How do I go to Nikaia" -> "From which station?" -> "Syntagma"
    // now resolves as origin instead of a fresh Syntagma departures).
    private var pendingIntent: AssistantIntent? = null
    private var pendingMissing: MissingSlot? = null

    // Durable co-pilot memory: the current station ("I'm at Syntagma"), the
    // last destination/route, and the last intent, so follow-ups like "go
    // airport faster" don't re-ask what the user already told us.
    private var session = AssistantSessionContext.EMPTY

    /**
     * Latest device location, pushed by the host when the assistant opens.
     * Used as the origin for travel-time ("how long to X") answers; when it's
     * null the resolver asks the user for an origin station instead.
     */
    fun onLocationUpdate(latitude: Double, longitude: Double) {
        lastLocation = UserLocation(latitude, longitude)
    }

    init {
        scope.launch {
            stations = stationRepository.getAllStations().first()
            // Ariadne must never offer a line that does not run: she answers
            // departures, last trains and routes, all of which are actionable.
            lines = getLinesUseCase.getOperationalLines().first()
            parser = AthensTransitParser(AssistantVocabularyBuilder.build(stations, lines))
            val nowAthens = com.syrmos.core.common.extensions.currentAthensTime()
            val severe = weatherRepository.cached?.current?.condition?.isSevere == true
            _uiState.update {
                it.copy(
                    ready = true,
                    messages = listOf(greeting()),
                    severeWeather = severe,
                    athensHour = nowAthens.hour,
                )
            }
        }
    }

    fun ask(input: String) {
        val text = input.trim()
        if (text.isEmpty()) return
        val p = parser ?: return
        _uiState.update {
            it.copy(messages = it.messages + userMessage(text), thinking = true)
        }
        scope.launch {
            // Clever tier: when an on-device classifier (Gemini Nano) is present
            // and can ground the message, use its intent directly. Otherwise fall
            // back to normalizing fuzzy input and running the deterministic parser.
            val raw = applyDayFollowUp(text, assistantClassifier.classify(text, p.vocabulary)
                ?: p.parse(queryNormalizer.normalize(text) ?: text), p)
            // Fill a missing origin from the known current station before asking,
            // so "I'm at Syntagma" then "go airport faster" needs no follow-up.
            val intent = fillFromContext(mergePendingIfApplicable(raw))
            // Update pending state before we resolve, so the answer's
            // side effects can rely on it being fresh.
            if (intent is AssistantIntent.NeedsClarification) {
                pendingIntent = intent.base
                pendingMissing = intent.missing
            } else {
                pendingIntent = null
                pendingMissing = null
            }
            updateSession(intent)
            val reply = if (intent is AssistantIntent.OutOfScope) {
                val llmReply = askCloudLLM(text)
                llmReply ?: recover(text, p)
            } else {
                resolve(intent)
            }
            _uiState.update { it.copy(messages = it.messages + reply, thinking = false) }
        }
    }

    /**
     * Bare day-change follow-up: "what about tomorrow?", "and the weekend?".
     * The parser can't classify these alone (no station), so they land as
     * OutOfScope; if the last answered turn was a departures query for a known
     * station, re-issue it for the new day instead of declining.
     */
    private fun applyDayFollowUp(text: String, raw: AssistantIntent, p: AthensTransitParser): AssistantIntent {
        if (raw !is AssistantIntent.OutOfScope) return raw
        val day = p.dayOf(text)
        if (day == DayContext.TODAY) return raw
        val last = session.lastIntent
        return when (last) {
            is AssistantIntent.ShowDepartures -> if (last.stationId != null || last.lineId != null) last.copy(day = day) else raw
            else -> raw
        }
    }

    /**
     * If we're mid-conversation and the user's new turn is a bare station
     * name (Ariadne's parser turns "Syntagma" alone into a
     * ShowDepartures{station=SYN}), merge that station into the pending
     * intent's missing slot instead of resetting. Falls through to the raw
     * intent when the merge doesn't apply.
     */
    private fun mergePendingIfApplicable(raw: AssistantIntent): AssistantIntent {
        val pending = pendingIntent ?: return raw
        val missing = pendingMissing ?: return raw
        val stationId = when (raw) {
            is AssistantIntent.ShowDepartures -> raw.stationId
            is AssistantIntent.LastTrain -> raw.stationId
            is AssistantIntent.FirstTrain -> raw.stationId
            is AssistantIntent.NeedsClarification -> (raw.base as? AssistantIntent.ShowDepartures)?.stationId
            else -> null
        } ?: return raw

        return when (pending) {
            is AssistantIntent.PlanTrip -> {
                val patched = when (missing) {
                    MissingSlot.ORIGIN_STATION -> pending.copy(fromStationId = stationId)
                    MissingSlot.DESTINATION_STATION -> pending.copy(toStationId = stationId)
                    else -> return raw
                }
                if (patched.fromStationId != null && patched.toStationId != null) patched
                else AssistantIntent.NeedsClarification(
                    patched,
                    if (patched.fromStationId == null) MissingSlot.ORIGIN_STATION
                    else MissingSlot.DESTINATION_STATION,
                )
            }
            is AssistantIntent.LastTrain -> pending.copy(stationId = stationId)
            is AssistantIntent.FirstTrain -> pending.copy(stationId = stationId)
            is AssistantIntent.ShowDepartures -> pending.copy(stationId = stationId)
            is AssistantIntent.StationAccessibility -> pending.copy(stationId = stationId)
            is AssistantIntent.WhichLines -> pending.copy(stationId = stationId)
            is AssistantIntent.StopsBetween -> {
                val patched = when (missing) {
                    MissingSlot.ORIGIN_STATION -> pending.copy(fromStationId = stationId)
                    MissingSlot.DESTINATION_STATION -> pending.copy(toStationId = stationId)
                    else -> return raw
                }
                if (patched.fromStationId != null && patched.toStationId != null) patched
                else AssistantIntent.NeedsClarification(
                    patched,
                    if (patched.fromStationId == null) MissingSlot.ORIGIN_STATION else MissingSlot.DESTINATION_STATION,
                )
            }
            is AssistantIntent.ToggleFavorite -> pending.copy(stationId = stationId)
            is AssistantIntent.TravelTime -> if (pending.toStationId == null)
                pending.copy(toStationId = stationId) else pending
            else -> raw
        }
    }

    // MARK: - Dispatch

    private suspend fun resolve(intent: AssistantIntent): AssistantMessage = when (intent) {
        is AssistantIntent.ShowDepartures -> resolveDepartures(intent)
        is AssistantIntent.LastTrain -> resolveLastTrain(intent)
        is AssistantIntent.FirstTrain -> resolveFirstTrain(intent)
        is AssistantIntent.StationAccessibility -> resolveAccessibility(intent)
        AssistantIntent.ReverseTrip -> resolveReverseTrip()
        is AssistantIntent.WhichLines -> resolveWhichLines(intent)
        is AssistantIntent.StopsBetween -> resolveStopsBetween(intent)
        is AssistantIntent.PlanTrip -> resolvePlanTrip(intent)
        is AssistantIntent.TravelTime -> resolveTravelTime(intent)
        is AssistantIntent.FindStation -> resolveFindStation(intent)
        is AssistantIntent.ExplainLine -> resolveExplainLine(intent)
        is AssistantIntent.ExplainFare -> resolveFare(intent)
        is AssistantIntent.ToggleFavorite -> resolveFavorite(intent)
        is AssistantIntent.ShowAlerts -> resolveAlerts(intent)
        is AssistantIntent.OpenMap -> resolveOpenMap(intent)
        AssistantIntent.Help -> botMessage(helpText())
        is AssistantIntent.NeedsClarification -> botMessage(clarify(intent.missing))
        AssistantIntent.OutOfScope -> botMessage(outOfScopeText())
        AssistantIntent.EasterEggLiepur -> botMessage(catJoke())
        is AssistantIntent.WeatherAt -> resolveWeather(intent)
        is AssistantIntent.PlanTripByArrival -> resolvePlanByArrival(intent)
        is AssistantIntent.StationStatus -> resolveStationStatus(intent)
        is AssistantIntent.SetCurrentLocation -> resolveSetLocation(intent)
    }

    /**
     * Fills a missing trip origin from the remembered current station, so a
     * follow-up like "go airport faster" after "I'm at Syntagma" resolves
     * without re-asking. Only touches the origin slot; everything else is left
     * to the normal clarification flow.
     */
    private fun fillFromContext(intent: AssistantIntent): AssistantIntent {
        val current = session.currentStation ?: return intent
        if (intent !is AssistantIntent.NeedsClarification) return intent
        if (intent.missing != MissingSlot.ORIGIN_STATION) return intent
        return when (val base = intent.base) {
            is AssistantIntent.PlanTrip -> base.copy(fromStationId = current)
            is AssistantIntent.TravelTime -> base.copy(fromStationId = current)
            is AssistantIntent.PlanTripByArrival -> base.copy(fromStationId = current)
            else -> intent
        }
    }

    /** Threads the turn's outcome into the session so the next turn has context. */
    private fun updateSession(intent: AssistantIntent) {
        session = when (intent) {
            is AssistantIntent.SetCurrentLocation ->
                session.withCurrentStation(intent.stationId ?: session.currentStation).remembering(intent)
            is AssistantIntent.PlanTrip -> {
                val from = intent.fromStationId
                val to = intent.toStationId
                session.copy(
                    currentStation = from ?: session.currentStation,
                    lastDestination = to ?: session.lastDestination,
                    lastRoute = if (from != null && to != null) {
                        RouteMemory(from, to, intent.preference)
                    } else {
                        session.lastRoute
                    },
                    lastIntent = intent,
                )
            }
            is AssistantIntent.TravelTime -> session.copy(
                lastDestination = intent.toStationId ?: session.lastDestination,
                lastIntent = intent,
            )
            is AssistantIntent.ShowDepartures -> session.copy(
                currentStation = intent.stationId ?: session.currentStation,
                lastIntent = intent,
            )
            is AssistantIntent.FirstTrain -> session.copy(
                currentStation = intent.stationId ?: session.currentStation,
                lastIntent = intent,
            )
            is AssistantIntent.WhichLines -> session.copy(
                currentStation = intent.stationId ?: session.currentStation,
                lastIntent = intent,
            )
            is AssistantIntent.StopsBetween -> {
                val f = intent.fromStationId
                val to = intent.toStationId
                session.copy(
                    currentStation = f ?: session.currentStation,
                    lastDestination = to ?: session.lastDestination,
                    lastRoute = if (f != null && to != null) RouteMemory(f, to) else session.lastRoute,
                    lastIntent = intent,
                )
            }
            // "and back" flips the remembered route so a second "and back" flips
            // it right back, and the new current station becomes the return origin.
            AssistantIntent.ReverseTrip -> {
                val r = session.lastRoute
                session.copy(
                    lastRoute = r?.let { RouteMemory(it.toStationId, it.fromStationId, it.preference) },
                    currentStation = r?.toStationId ?: session.currentStation,
                    lastDestination = r?.fromStationId ?: session.lastDestination,
                    lastIntent = intent,
                )
            }
            is AssistantIntent.NeedsClarification -> session
            else -> session.copy(lastIntent = intent)
        }
    }

    private fun resolveSetLocation(intent: AssistantIntent.SetCurrentLocation): AssistantMessage {
        val stationId = intent.stationId
            ?: return botMessage(t("Okay, you're at the station. Where do you want to go next?",
                "Οκ, είσαι στον σταθμό. Πού θέλεις να πας μετά;",
                "Në rregull, je te stacioni. Ku do të shkosh më pas?"))
        val name = stationNameById(stationId)
        return botMessage(t("Got it. I'll use $name as your starting station.",
            "Οκ. Θα χρησιμοποιώ τον $name ως σταθμό εκκίνησης.",
            "Në rregull. Do ta përdor $name si stacionin tënd të nisjes."))
    }

    private suspend fun resolveStationStatus(intent: AssistantIntent.StationStatus): AssistantMessage {
        val station = intent.stationId?.let { id -> stations.firstOrNull { it.id == id } }
            ?: return botMessage(clarify(MissingSlot.STATION))
        val advisory = ServiceAdvisoryMatcher.forStation(
            stationNames = stationSearchNames(station),
            stationLineIds = station.lineIds,
            notices = currentNotices(),
            severeWeather = weatherRepository.cached?.current?.condition?.isSevere == true,
        )
        return AssistantMessage(
            id = nextId++,
            fromUser = false,
            text = stationStatusText(stationName(station), advisory),
            action = AssistantAction.OpenStation(station.id),
            actionLabel = t("Open", "Άνοιγμα", "Hap"),
        )
    }

    /** Honest station status: lead with any live advisory, else the timetable. */
    private fun stationStatusText(name: String, advisory: ServiceAdvisory): String {
        val top = advisory.top
        if (top != null) {
            val lead = when (top.severity) {
                AdvisorySeverity.CLOSURE -> t("Heads up, there's an active closure affecting $name.",
                    "Προσοχή, υπάρχει ενεργό κλείσιμο που αφορά τον $name.",
                    "Kujdes, ka një mbyllje aktive që prek $name.")
                AdvisorySeverity.WARNING -> t("There's an active advisory affecting $name.",
                    "Υπάρχει ενεργή ειδοποίηση που αφορά τον $name.",
                    "Ka një njoftim aktiv që prek $name.")
                AdvisorySeverity.INFO -> t("There's a notice affecting $name.",
                    "Υπάρχει ανακοίνωση που αφορά τον $name.",
                    "Ka një njoftim që prek $name.")
            }
            val tail = t("Check official STASY alerts for details.",
                "Δες τις επίσημες ανακοινώσεις της ΣΤΑΣΥ για λεπτομέρειες.",
                "Kontrollo njoftimet zyrtare të STASY për detaje.")
            return "$lead ${top.text} $tail"
        }
        val base = t(
            "I don't have a live closure alert for $name. Based on the normal timetable, the station should be operating. Check official STASY alerts if this is urgent.",
            "Δεν έχω ζωντανή ειδοποίηση κλεισίματος για τον $name. Με βάση το κανονικό πρόγραμμα, ο σταθμός πρέπει να λειτουργεί. Αν είναι επείγον, δες τις ανακοινώσεις της ΣΤΑΣΥ.",
            "Nuk kam njoftim live për mbyllje të $name. Sipas orarit normal, stacioni duhet të jetë në punë. Nëse është urgjente, kontrollo njoftimet e STASY.",
        )
        return if (advisory.severeWeather) {
            base + " " + t("Severe weather is in effect, so allow extra time.",
                "Επικρατεί κακοκαιρία, οπότε άφησε περιθώριο.",
                "Ka mot të keq, ndaj lër kohë shtesë.")
        } else {
            base
        }
    }

    /** Station names in every language the matcher should look for in notices. */
    private fun stationSearchNames(station: Station): List<String> =
        listOf(station.name, station.nameEl).filter { it.isNotBlank() }.distinct()

    /**
     * Projects the STASY announcement feed into domain [ServiceNotice]s. [text]
     * is the localized title for read-back; [searchText] concatenates every
     * language so station-name matching works regardless of the notice language.
     */
    private suspend fun currentNotices(): List<ServiceNotice> {
        val feed = announcementsRepository.feed.first()
        return feed.announcements
            .filter { it.isServiceAlert || it.severity != "info" }
            .map { a ->
                ServiceNotice(
                    id = a.id,
                    text = localizedAnnouncementTitle(a),
                    affectedLineIds = a.affectedLines,
                    severity = AdvisorySeverity.fromRaw(a.severity),
                    validFrom = a.validFrom,
                    validUntil = a.validUntil,
                    searchText = listOf(
                        a.title, a.titleEn, a.titleSq, a.summary, a.summaryEn, a.summarySq,
                    ).filter { it.isNotBlank() }.joinToString(" "),
                )
            }
    }

    private fun localizedAnnouncementTitle(a: com.syrmos.core.network.STASYAnnouncement): String =
        when (LocalizationManager.language.value) {
            AppLanguage.GREEK -> a.title.ifBlank { a.titleEn }
            AppLanguage.ALBANIAN -> a.titleSq.ifBlank { a.titleEn.ifBlank { a.title } }
            else -> a.titleEn.ifBlank { a.title }
        }

    private suspend fun resolvePlanByArrival(intent: AssistantIntent.PlanTripByArrival): AssistantMessage {
        val fromId = intent.fromStationId ?: return botMessage(clarify(MissingSlot.ORIGIN_STATION))
        val toId = intent.toStationId ?: return botMessage(clarify(MissingSlot.DESTINATION_STATION))

        val now = com.syrmos.core.common.extensions.currentAthensTime()
        val nowMin = now.hour * 60 + now.minute
        val rawTarget = intent.arriveByAthensMinutes
            ?: intent.inMinutesFromNow?.let { nowMin + it }
            ?: return botMessage(clarify(MissingSlot.DESTINATION_STATION))
        // Wrap into tomorrow if the absolute target already passed today
        // (e.g. "at 1am" said at 23:00 means 1am tomorrow).
        val targetMin = if (rawTarget < nowMin && intent.arriveByAthensMinutes != null)
            rawTarget + 24 * 60 else rawTarget

        // Prefer the backward-walking planner: it anchors the first leg to
        // a real scheduled departure so the answer names an actual train
        // ("board the 21:04 M3") rather than a rounded clock estimate.
        val solution = runCatching {
            planByArrival.invoke(
                fromStationId = fromId,
                toStationId = toId,
                arriveByAthensMinutes = targetMin,
            )
        }.getOrNull()

        val fromName = stationName(fromId)
        val toName = stationName(toId)
        val arriveLabel = formatClock(targetMin % (24 * 60))

        if (solution != null) {
            val leaveLabel = solution.firstLegDepartureTime
            val slack = solution.slackMinutes
            val transferSuffix = if (solution.legDepartures.size >= 2) transferChain(solution.legDepartures) else ""
            return botMessage(
                when {
                    slack < 0 -> arrivalMissed(toName, arriveLabel, -slack)
                    slack < 5 -> arrivalTightExact(fromName, leaveLabel, toName, arriveLabel, slack) + transferSuffix
                    slack > solution.route.totalMinutes + 45 -> arrivalEarly(fromName, leaveLabel, toName, arriveLabel, slack) + transferSuffix
                    else -> arrivalOkExact(fromName, leaveLabel, toName, arriveLabel, slack) + transferSuffix
                }
            )
        }

        // Fallback: schedule-agnostic estimate. Still honest; kept as the
        // safety net when the projector can't answer for this line/day.
        val duration = planJourney.invoke(fromStationId = fromId, toStationId = toId).first()?.totalMinutes
            ?: return botMessage(noRouteText(fromId, toId))
        val leaveByMin = targetMin - duration
        val slack = leaveByMin - nowMin
        val leaveByLabel = formatClock(leaveByMin % (24 * 60))
        return botMessage(
            when {
                slack < 0 -> arrivalMissed(toName, arriveLabel, -slack)
                slack < 5 -> arrivalTight(fromName, leaveByLabel, toName, arriveLabel, slack)
                slack > duration + 45 -> arrivalEarly(fromName, leaveByLabel, toName, arriveLabel, slack)
                else -> arrivalOk(fromName, leaveByLabel, toName, arriveLabel, slack)
            }
        )
    }

    /**
     * "Transfer to the 21:19 M2 at Monastiraki" style continuation.
     * Appended AFTER the leaveBy sentence when the trip has 2+ boardable
     * legs, so the answer names the connections instead of leaving them
     * as implicit "just make it work" magic.
     */
    private fun transferChain(legs: List<com.syrmos.core.domain.usecase.PlanByArrivalUseCase.LegDeparture>): String {
        if (legs.size < 2) return ""
        val lang = LocalizationManager.language.value
        val parts = StringBuilder()
        for (i in 1 until legs.size) {
            val leg = legs[i]
            val at = leg.fromStationName
            parts.append(" ")
            parts.append(
                when (lang) {
                    AppLanguage.GREEK -> "Μετάβαση στο ${leg.lineId} στις ${leg.departureTime} στον $at."
                    AppLanguage.ALBANIAN -> "Ndrysho në ${leg.lineId} në ${leg.departureTime} te $at."
                    else -> "Transfer to the ${leg.departureTime} ${leg.lineId} at $at."
                }
            )
        }
        return parts.toString()
    }

    private fun arrivalOkExact(from: String, leaveAt: String, to: String, arrive: String, slack: Int): String =
        when (LocalizationManager.language.value) {
            AppLanguage.GREEK -> "Πάρε τον συρμό στις $leaveAt από $from και θα είσαι στο $to στις $arrive. $slack λεπτά περιθώριο."
            AppLanguage.ALBANIAN -> "Merr trenin në $leaveAt nga $from dhe do të jesh në $to në $arrive. $slack minuta hapësirë."
            else -> "Board the $leaveAt from $from and you'll be at $to by $arrive. $slack min to spare."
        }

    private fun arrivalTightExact(from: String, leaveAt: String, to: String, arrive: String, slack: Int): String =
        when (LocalizationManager.language.value) {
            AppLanguage.GREEK -> "Στριμωγμένα. Το τρένο στις $leaveAt από $from είναι το τελευταίο που φτάνει στο $to μέχρι τις $arrive."
            AppLanguage.ALBANIAN -> "Ngushtë. Treni në $leaveAt nga $from është i fundit që arrin në $to deri në $arrive."
            else -> "Tight. The $leaveAt from $from is the last one that gets you to $to by $arrive."
        }

    private fun stationName(id: String): String {
        val st = stations.firstOrNull { it.id == id } ?: return id
        return if (LocalizationManager.language.value == AppLanguage.GREEK) st.nameEl else st.name
    }

    private fun formatClock(minutes: Int): String {
        val h = (minutes / 60) % 24
        val m = minutes % 60
        return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
    }

    private fun noRouteText(from: String, to: String): String = when (LocalizationManager.language.value) {
        AppLanguage.GREEK -> "Δεν βρήκα διαδρομή από $from προς $to."
        AppLanguage.ALBANIAN -> "S'gjeta rrugë nga $from për te $to."
        else -> "I couldn't find a route from $from to $to."
    }

    private fun arrivalOk(from: String, leaveBy: String, to: String, arrive: String, slack: Int): String =
        when (LocalizationManager.language.value) {
            AppLanguage.GREEK -> "Ξεκίνα από $from έως $leaveBy και θα είσαι στο $to στις $arrive. $slack λεπτά περιθώριο."
            AppLanguage.ALBANIAN -> "Nis nga $from deri në $leaveBy dhe do të jesh në $to në $arrive. $slack minuta hapësirë."
            else -> "Leave $from by $leaveBy and you'll be at $to by $arrive. $slack min to spare."
        }

    private fun arrivalTight(from: String, leaveBy: String, to: String, arrive: String, slack: Int): String =
        when (LocalizationManager.language.value) {
            AppLanguage.GREEK -> "Στριμωγμένα. Πρέπει να είσαι εκτός από $from μέσα στα επόμενα $slack λεπτά για να προλάβεις στο $to στις $arrive."
            AppLanguage.ALBANIAN -> "Ngushtë. Duhet të nisesh nga $from brenda $slack minutash për të arritur në $to në $arrive."
            else -> "Tight. You need to leave $from within the next $slack min to make $to by $arrive."
        }

    private fun arrivalMissed(to: String, arrive: String, minutesOver: Int): String =
        when (LocalizationManager.language.value) {
            AppLanguage.GREEK -> "Δύσκολο. Για να είσαι στο $to στις $arrive θα έπρεπε να έχεις ξεκινήσει πριν $minutesOver λεπτά."
            AppLanguage.ALBANIAN -> "E vështirë. Për të qenë në $to në $arrive duhej të kishe nisur $minutesOver minuta më parë."
            else -> "Cutting it close. To make $to by $arrive you'd have needed to leave $minutesOver min ago."
        }

    private fun arrivalEarly(from: String, leaveBy: String, to: String, arrive: String, slack: Int): String =
        when (LocalizationManager.language.value) {
            AppLanguage.GREEK -> "Έχεις άπλα. Ξεκίνα από $from όποτε θες μέσα στα επόμενα $slack λεπτά και θα φτάσεις στο $to στις $arrive."
            AppLanguage.ALBANIAN -> "Ke kohë. Nisu nga $from kur të duash brenda $slack minutash dhe do të jesh në $to në $arrive."
            else -> "You have time. Leave $from anytime in the next $slack min and you'll reach $to by $arrive."
        }

    private suspend fun resolveWeather(intent: AssistantIntent.WeatherAt): AssistantMessage {
        val (lat, lng, placeName) = weatherAnchor(intent.stationId) ?: run {
            val cached = weatherRepository.cached
            return if (cached != null) botMessage(formatWeather(cached, placeName = cached.placeName))
            else botMessage(weatherUnavailableText())
        }
        val snap = weatherRepository.snapshotForCoord(lat, lng, placeName)
            ?: return botMessage(weatherUnavailableText())
        return botMessage(formatWeather(snap, placeName = placeName))
    }

    private fun weatherAnchor(stationId: String?): Triple<Double, Double, String>? {
        if (stationId != null) {
            val st = stations.firstOrNull { it.id == stationId }
            if (st != null) {
                val name = if (LocalizationManager.language.value == AppLanguage.GREEK) st.nameEl else st.name
                return Triple(st.latitude, st.longitude, name)
            }
        }
        // No explicit station: prefer user's current location -> nearest
        // station's coord, so weather reflects where they actually are.
        val loc = lastLocation
        if (loc != null) {
            val nearest = stations.minByOrNull { st ->
                val dLat = st.latitude - loc.latitude
                val dLng = st.longitude - loc.longitude
                dLat * dLat + dLng * dLng
            }
            if (nearest != null) {
                val name = if (LocalizationManager.language.value == AppLanguage.GREEK) nearest.nameEl else nearest.name
                return Triple(nearest.latitude, nearest.longitude, name)
            }
        }
        return null
    }

    private fun formatWeather(snap: WeatherSnapshot, placeName: String): String {
        val lang = LocalizationManager.language.value
        val tempC = snap.current.temperatureC.toInt()
        val feels = snap.current.apparentC.toInt()
        val cond = weatherConditionLabel(snap.current.condition, lang)
        val ageMin = ((Clock.System.now().epochSeconds - snap.fetchedAtEpochSeconds) / 60).toInt()
        val ageSuffix = if (ageMin >= 5) when (lang) {
            AppLanguage.GREEK -> " (πριν $ageMin λεπτά)"
            AppLanguage.ALBANIAN -> " ($ageMin min më parë)"
            else -> " ($ageMin min ago)"
        } else ""
        return when (lang) {
            AppLanguage.GREEK ->
                "$placeName τώρα: ${tempC}°C, $cond. Αίσθηση ${feels}°C.$ageSuffix"
            AppLanguage.ALBANIAN ->
                "$placeName tani: ${tempC}°C, $cond. Ndihet si ${feels}°C.$ageSuffix"
            else ->
                "$placeName right now: ${tempC}°C, $cond. Feels like ${feels}°C.$ageSuffix"
        }
    }

    private fun weatherConditionLabel(condition: com.syrmos.core.model.weather.WeatherCondition, lang: AppLanguage): String {
        return when (lang) {
            AppLanguage.GREEK -> when (condition) {
                com.syrmos.core.model.weather.WeatherCondition.CLEAR -> "καθαρός"
                com.syrmos.core.model.weather.WeatherCondition.PARTLY_CLOUDY -> "μερική συννεφιά"
                com.syrmos.core.model.weather.WeatherCondition.CLOUDY -> "συννεφιασμένος"
                com.syrmos.core.model.weather.WeatherCondition.FOG -> "ομίχλη"
                com.syrmos.core.model.weather.WeatherCondition.DRIZZLE -> "ψιχάλα"
                com.syrmos.core.model.weather.WeatherCondition.RAIN -> "βροχή"
                com.syrmos.core.model.weather.WeatherCondition.SNOW -> "χιόνι"
                com.syrmos.core.model.weather.WeatherCondition.SHOWERS -> "μπόρες"
                com.syrmos.core.model.weather.WeatherCondition.THUNDERSTORM -> "καταιγίδα"
                com.syrmos.core.model.weather.WeatherCondition.UNKNOWN -> "άγνωστη"
            }
            AppLanguage.ALBANIAN -> when (condition) {
                com.syrmos.core.model.weather.WeatherCondition.CLEAR -> "kthjellët"
                com.syrmos.core.model.weather.WeatherCondition.PARTLY_CLOUDY -> "pjesërisht i vranët"
                com.syrmos.core.model.weather.WeatherCondition.CLOUDY -> "i vranët"
                com.syrmos.core.model.weather.WeatherCondition.FOG -> "mjegull"
                com.syrmos.core.model.weather.WeatherCondition.DRIZZLE -> "shi i lehtë"
                com.syrmos.core.model.weather.WeatherCondition.RAIN -> "shi"
                com.syrmos.core.model.weather.WeatherCondition.SNOW -> "borë"
                com.syrmos.core.model.weather.WeatherCondition.SHOWERS -> "reshje"
                com.syrmos.core.model.weather.WeatherCondition.THUNDERSTORM -> "stuhi"
                com.syrmos.core.model.weather.WeatherCondition.UNKNOWN -> "e panjohur"
            }
            else -> when (condition) {
                com.syrmos.core.model.weather.WeatherCondition.CLEAR -> "clear"
                com.syrmos.core.model.weather.WeatherCondition.PARTLY_CLOUDY -> "partly cloudy"
                com.syrmos.core.model.weather.WeatherCondition.CLOUDY -> "cloudy"
                com.syrmos.core.model.weather.WeatherCondition.FOG -> "foggy"
                com.syrmos.core.model.weather.WeatherCondition.DRIZZLE -> "drizzling"
                com.syrmos.core.model.weather.WeatherCondition.RAIN -> "raining"
                com.syrmos.core.model.weather.WeatherCondition.SNOW -> "snowing"
                com.syrmos.core.model.weather.WeatherCondition.SHOWERS -> "showery"
                com.syrmos.core.model.weather.WeatherCondition.THUNDERSTORM -> "thunderstorm"
                com.syrmos.core.model.weather.WeatherCondition.UNKNOWN -> "unknown"
            }
        }
    }

    /**
     * No live reading: fall back to the honest Athens seasonal norm ("usually
     * hot and dry this time of year") rather than a dead end, phrased as
     * "usually", never "now".
     */
    private fun weatherUnavailableText(): String {
        val ctx = weatherContext()
        if (ctx.source == com.syrmos.core.model.weather.WeatherSource.SEASONAL_FALLBACK) {
            val typical = seasonalClause(ctx)
            return t(
                "I don't have live weather right now, but Athens this time of year is usually $typical.",
                "Δεν έχω ζωντανό καιρό τώρα, αλλά η Αθήνα αυτή την εποχή είναι συνήθως $typical.",
                "Nuk kam mot live tani, por Athina në këtë periudhë zakonisht është $typical.",
            )
        }
        return t("I don't have weather data yet. Try again when you're online.",
            "Δεν έχω ακόμα δεδομένα καιρού. Δοκίμασε ξανά όταν είσαι online.",
            "Ende s'kam të dhëna moti. Provo përsëri kur je online.")
    }

    private fun catJoke(): String {
        val jokes = when (LocalizationManager.language.value) {
            AppLanguage.GREEK -> listOf(
                "Γιατί οι γάτες δεν παίζουν πόκερ στη ζούγκλα; Έχει πολλά τσιτάχ.",
                "Πώς λέγεται μια στοίβα γατάκια; Μιαοβούνο.",
                "Τι κάνει ένας γάτος στον υπολογιστή; Προσέχει το ποντίκι.",
                "Γιατί ο γάτος πήγε στο νοσοκομείο; Είχε πυρετό αγέλας.",
                "Πώς τελειώνει η μάχη δύο γάτων; Με ένα σφύριγμα και ένα μιάου.",
            )
            AppLanguage.ALBANIAN -> listOf(
                "Pse macet nuk luajnë poker në xhungël? Sepse ka shumë çita.",
                "Si e quajnë një grumbull macesh të vogla? Një mjaumal.",
                "Pse ishte macja ulur mbi kompjuter? Për të vëzhguar miun.",
                "Cila është ëmbëlsira e preferuar e maces? Muslet me çokollatë.",
                "Si e mbyllin macet një grindje? Me një fshirje dhe një mjau.",
            )
            else -> listOf(
                "Why don't cats play poker in the jungle? Too many cheetahs.",
                "What do you call a pile of kittens? A meowntain.",
                "Why was the cat sitting on the computer? To keep an eye on the mouse.",
                "What's a cat's favourite dessert? Chocolate mousse.",
                "How do two cats end a fight? They hiss and make up.",
            )
        }
        return jokes.random()
    }

    private suspend fun resolveDepartures(intent: AssistantIntent.ShowDepartures): AssistantMessage {
        val station = resolveStation(intent.stationId, intent.lineId) ?: return botMessage(clarify(MissingSlot.STATION))
        val lineIds = intent.lineId?.let { listOf(it) } ?: station.lineIds

        if (intent.day != DayContext.TODAY) {
            return resolveDeparturesForDay(intent.day, station, lineIds)
        }

        val departures = mutableListOf<UpcomingDeparture>()
        for (lineId in lineIds) {
            for (direction in Direction.entries) {
                departures += getNextDepartures.invoke(station.id, lineId, direction, limit = 2).first()
            }
        }
        val sorted = departures.sortedBy { it.minutesAway }.take(4)
        if (sorted.isEmpty()) {
            return botMessage(t("No more trains from ${stationName(station)} right now.",
                "Δεν υπάρχουν άλλα δρομολόγια από ${stationName(station)} τώρα.",
                "Nuk ka më trena nga ${stationName(station)} tani."))
        }
        return AssistantMessage(
            id = nextId++,
            fromUser = false,
            text = t("Next from ${stationName(station)}:",
                "Επόμενα από ${stationName(station)}:",
                "Të ardhshmet nga ${stationName(station)}:"),
            departures = sorted,
            action = intent.lineId?.let { AssistantAction.OpenLine(normalizeLine(it)) }
                ?: AssistantAction.OpenStation(station.id),
            actionLabel = t("Open", "Άνοιγμα", "Hap"),
        )
    }

    /** "this weekend / tomorrow / Saturday": project that whole service day from 00:00. */
    private fun resolveDeparturesForDay(day: DayContext, station: Station, lineIds: List<String>): AssistantMessage {
        val dayOffset = DayContextResolver.dayOffset(day)
        val expanded = lineIds.flatMap { if (it == "M3") listOf("M3", "M3_AIR") else listOf(it) }
        val departures = mutableListOf<UpcomingDeparture>()
        for (lineId in expanded) {
            for (direction in Direction.entries) {
                departures += bandProjector.invokeForDay(listOf(lineId), direction, dayOffset, limit = 3, stationId = station.id)
            }
        }
        // Day-projection times are clock times (countdown from midnight is
        // meaningless for a future day), so surface them as text, not as the
        // live "X min" chips the today path uses.
        val sorted = departures.distinctBy { it.time + it.lineId }.sortedBy { it.minutesAway }.take(4)
        val label = dayLabel(day)
        if (sorted.isEmpty()) {
            return botMessage(t("I don't have $label's schedule for ${stationName(station)} offline.",
                "Δεν έχω το πρόγραμμα του $label για ${stationName(station)} εκτός σύνδεσης.",
                "Nuk e kam orarin e $label për ${stationName(station)} pa internet."))
        }
        val times = sorted.joinToString(", ") { "${it.lineId} ${it.time}" }
        return AssistantMessage(
            id = nextId++,
            fromUser = false,
            text = t("First trains $label from ${stationName(station)}: $times.",
                "Πρώτα δρομολόγια $label από ${stationName(station)}: $times.",
                "Trenat e parë $label nga ${stationName(station)}: $times."),
            action = AssistantAction.OpenStation(station.id),
            actionLabel = t("Open", "Άνοιγμα", "Hap"),
        )
    }

    private fun dayLabel(day: DayContext): String = when (day) {
        DayContext.TOMORROW -> t("tomorrow", "αύριο", "nesër")
        DayContext.WEEKEND -> t("this weekend", "το Σαββατοκύριακο", "këtë fundjavë")
        DayContext.SATURDAY -> t("Saturday", "το Σάββατο", "të shtunën")
        DayContext.SUNDAY -> t("Sunday", "την Κυριακή", "të dielën")
        DayContext.TODAY -> t("today", "σήμερα", "sot")
    }

    private suspend fun resolveLastTrain(intent: AssistantIntent.LastTrain): AssistantMessage {
        val station = resolveStation(intent.stationId, intent.lineId) ?: return botMessage(clarify(MissingSlot.STATION))
        val lineId = intent.lineId ?: station.lineIds.firstOrNull()
            ?: return botMessage(clarify(MissingSlot.STATION))
        val last = getLastTrain.latestEitherDirection(station.id, normalizeLine(lineId))
            ?: return botMessage(t("Service is over for tonight at ${stationName(station)}.",
                "Τα δρομολόγια για απόψε τελείωσαν στον ${stationName(station)}.",
                "Shërbimi për sonte ka mbaruar te ${stationName(station)}."))
        return AssistantMessage(
            id = nextId++,
            fromUser = false,
            text = t("Last ${displayLine(last.lineId)} from ${stationName(station)} leaves at ${last.time}. Leave by then.",
                "Ο τελευταίος ${displayLine(last.lineId)} από ${stationName(station)} φεύγει ${last.time}. Φύγε ως τότε.",
                "Treni i fundit ${displayLine(last.lineId)} nga ${stationName(station)} niset ${last.time}. Nisu deri atëherë."),
            action = AssistantAction.OpenStation(station.id),
            actionLabel = t("Open", "Άνοιγμα", "Hap"),
        )
    }

    /** First / earliest scheduled train of today at a station (mirror of last train). */
    private suspend fun resolveFirstTrain(intent: AssistantIntent.FirstTrain): AssistantMessage {
        val station = resolveStation(intent.stationId, intent.lineId) ?: return botMessage(clarify(MissingSlot.STATION))
        val lineIds = intent.lineId?.let { listOf(it) } ?: station.lineIds
        val expanded = lineIds.flatMap { if (it == "M3") listOf("M3", "M3_AIR") else listOf(it) }
        val departures = mutableListOf<UpcomingDeparture>()
        for (lineId in expanded) {
            for (direction in Direction.entries) {
                departures += bandProjector.invokeForDay(listOf(lineId), direction, dayOffset = 0, limit = 2, stationId = station.id)
            }
        }
        val first = departures.distinctBy { it.time + it.lineId }.minByOrNull { it.minutesAway }
            ?: return botMessage(t("I don't have today's schedule for ${stationName(station)} offline.",
                "Δεν έχω το σημερινό πρόγραμμα για ${stationName(station)} εκτός σύνδεσης.",
                "Nuk e kam orarin e sotëm për ${stationName(station)} pa internet."))
        return AssistantMessage(
            id = nextId++,
            fromUser = false,
            text = t("First ${displayLine(first.lineId)} from ${stationName(station)} is at ${first.time}.",
                "Το πρώτο ${displayLine(first.lineId)} από ${stationName(station)} είναι στις ${first.time}.",
                "Treni i parë ${displayLine(first.lineId)} nga ${stationName(station)} është në ${first.time}."),
            action = AssistantAction.OpenStation(station.id),
            actionLabel = t("Open", "Άνοιγμα", "Hap"),
        )
    }

    /** Step-free accessibility for one station, from the bundled flag. Never invented. */
    private suspend fun resolveAccessibility(intent: AssistantIntent.StationAccessibility): AssistantMessage {
        val station = resolveStation(intent.stationId, null) ?: return botMessage(clarify(MissingSlot.STATION))
        val name = stationName(station)
        val reply = if (station.accessibility) {
            t("$name is step-free accessible (lift / level access).",
                "Ο $name είναι προσβάσιμος για ΑμεΑ (ασανσέρ / ισόπεδη πρόσβαση).",
                "$name është i aksesueshëm pa shkallë (ashensor / qasje e sheshtë).")
        } else {
            t("$name is not marked step-free. Check for stairs-only access before you go.",
                "Ο $name δεν είναι σημειωμένος ως προσβάσιμος ΑμεΑ. Ίσως έχει μόνο σκάλες.",
                "$name nuk shënohet si i aksesueshëm pa shkallë. Mund të ketë vetëm shkallë.")
        }
        return botMessage(reply)
    }

    /** "and back?" — reverse the remembered route and re-plan. */
    private suspend fun resolveReverseTrip(): AssistantMessage {
        val route = session.lastRoute
            ?: return botMessage(t("Tell me a trip first, then I can flip it for the way back.",
                "Πες μου πρώτα μια διαδρομή, μετά τη γυρίζω για την επιστροφή.",
                "Më trego fillimisht një udhëtim, pastaj e kthej për rrugën e kthimit."))
        return resolvePlanTrip(
            AssistantIntent.PlanTrip(
                fromStationId = route.toStationId,
                toStationId = route.fromStationId,
                preference = route.preference,
            ),
        )
    }

    /** "Which lines serve X?" — list the lines calling at a station. */
    private suspend fun resolveWhichLines(intent: AssistantIntent.WhichLines): AssistantMessage {
        val station = resolveStation(intent.stationId, null) ?: return botMessage(clarify(MissingSlot.STATION))
        val lineIds = station.lineIds.map { normalizeLine(it) }.distinct()
        val name = stationName(station)
        if (lineIds.isEmpty()) {
            return botMessage(t("I don't have any lines listed for $name.",
                "Δεν έχω γραμμές καταχωρημένες για $name.",
                "Nuk kam linja të regjistruara për $name."))
        }
        val list = lineIds.joinToString(", ") { displayLine(it) }
        return AssistantMessage(
            id = nextId++,
            fromUser = false,
            text = t("$name is served by: $list.",
                "Ο $name εξυπηρετείται από: $list.",
                "$name shërbehet nga: $list."),
            action = AssistantAction.OpenStation(station.id),
            actionLabel = t("Open", "Άνοιγμα", "Hap"),
        )
    }

    /** "How many stops / how far from A to B?" — stop count + rough duration. */
    private suspend fun resolveStopsBetween(intent: AssistantIntent.StopsBetween): AssistantMessage {
        val fromId = intent.fromStationId ?: session.currentStation
            ?: return botMessage(clarify(MissingSlot.ORIGIN_STATION))
        val toId = intent.toStationId ?: return botMessage(clarify(MissingSlot.DESTINATION_STATION))
        val route = planJourney.invoke(fromId, toId).first()
            ?: return botMessage(t("I couldn't find a rail route between those.",
                "Δεν βρήκα σιδηροδρομική διαδρομή ανάμεσά τους.",
                "Nuk gjeta një rrugë hekurudhore mes tyre."))
        val stops = route.segments.sumOf { (it.stationCount - 1).coerceAtLeast(0) }
        val fromN = stationNameById(fromId)
        val toN = stationNameById(toId)
        val changePart = if (route.transferCount == 0) {
            t("direct", "απευθείας", "direkt")
        } else {
            t("${route.transferCount} change(s)", "${route.transferCount} αλλαγή/ές", "${route.transferCount} ndërrim(e)")
        }
        return botMessage(t(
            "$fromN to $toN is $stops stops, about ${route.totalMinutes} min ($changePart).",
            "$fromN προς $toN είναι $stops στάσεις, περίπου ${route.totalMinutes} λεπτά ($changePart).",
            "$fromN te $toN janë $stops stacione, rreth ${route.totalMinutes} min ($changePart)."))
    }

    private suspend fun resolvePlanTrip(intent: AssistantIntent.PlanTrip): AssistantMessage {
        // Origin falls back to the remembered current station ("I'm at Syntagma").
        val fromId = intent.fromStationId ?: session.currentStation
            ?: return botMessage(clarify(MissingSlot.ORIGIN_STATION))
        val toId = intent.toStationId ?: return botMessage(clarify(MissingSlot.DESTINATION_STATION))
        val fastest = planJourney.invoke(fromId, toId).first()
            ?: return botMessage(t("I couldn't find a rail route between those.",
                "Δεν βρήκα σιδηροδρομική διαδρομή ανάμεσά τους.",
                "Nuk gjeta një rrugë hekurudhore mes tyre."))

        // Offer a sheltered all-metro alternative only when the fastest route is
        // exposed (tram / surface) and a distinct metro-only path exists. On a
        // hot or wet day the ranker can then pick the drier option.
        val ctx = weatherContext()
        val fastExposure = routeExposure(fastest)
        val candidates = buildList {
            add(com.syrmos.core.domain.assistant.RouteCandidate(fastest, fastExposure))
            if (fastExposure != Exposure.SHELTERED) {
                planJourney.metroOnly(fromId, toId).first()?.let { metro ->
                    val distinct = metro.totalMinutes != fastest.totalMinutes ||
                        metro.segments.size != fastest.segments.size
                    if (distinct && routeExposure(metro) == Exposure.SHELTERED) {
                        add(com.syrmos.core.domain.assistant.RouteCandidate(metro, Exposure.SHELTERED))
                    }
                }
            }
        }
        val ranked = com.syrmos.core.domain.assistant.RouteRanker.rank(candidates, intent.preference, ctx)
        val best = ranked.first().candidate.result

        val linesText = routeLineText(best)
        val transfers = if (best.transferCount == 0) {
            t("no change", "χωρίς αλλαγή", "pa ndërrim")
        } else {
            t("${best.transferCount} change(s)", "${best.transferCount} αλλαγή/ές", "${best.transferCount} ndërrim(e)")
        }
        // "This is direct" nod when the user asked for the easiest route.
        val directNote = if (intent.preference == com.syrmos.core.domain.assistant.RoutePreference.FEWEST_CHANGES &&
            best.transferCount == 0) {
            " " + t("This is direct, no change needed.", "Είναι απευθείας, χωρίς αλλαγή.", "Është direkt, pa ndërrim.")
        } else {
            ""
        }
        // When the ranker overrode the fastest route (weather tilt), say why.
        val tradeoff = if (best !== fastest) {
            "\n" + t(
                "The faster route (${routeLineText(fastest)}, ${fastest.totalMinutes} min) is more exposed; in this weather I'd take this one.",
                "Η πιο γρήγορη διαδρομή (${routeLineText(fastest)}, ${fastest.totalMinutes} λεπτά) είναι πιο εκτεθειμένη· με αυτόν τον καιρό θα προτιμούσα αυτή.",
                "Rruga më e shpejtë (${routeLineText(fastest)}, ${fastest.totalMinutes} min) është më e ekspozuar; me këtë mot do të zgjidhja këtë.",
            )
        } else {
            ""
        }
        val exposure = if (intent.lowExposure) "\n" + weatherAdvice(best) else ""
        val legs = best.segments.filter { !it.isTransfer }
        val advisory = ServiceAdvisoryMatcher.forRoute(
            lineIds = legs.map { normalizeLine(it.lineId) },
            stationNames = best.segments.flatMap { listOf(it.fromStationName, it.toStationName) }.distinct(),
            notices = currentNotices(),
            severeWeather = weatherRepository.cached?.current?.condition?.isSevere == true,
        )
        val caveat = advisory.top?.let {
            "\n" + t("Heads up: ", "Προσοχή: ", "Kujdes: ") + it.text
        } ?: ""
        return botMessage(
            t("$linesText. About ${best.totalMinutes} min, $transfers.",
                "$linesText. Περίπου ${best.totalMinutes} λεπτά, $transfers.",
                "$linesText. Rreth ${best.totalMinutes} min, $transfers.") + directNote + tradeoff + exposure + caveat,
        )
    }

    /** A route's shelter, from its non-transfer segments' line types. */
    private fun routeExposure(result: com.syrmos.core.model.planner.JourneyResult): Exposure {
        val types = result.segments.filter { !it.isTransfer }
            .mapNotNull { seg -> lines.firstOrNull { it.id == normalizeLine(seg.lineId) }?.type }
        return StationComfort.forRoute(types)
    }

    private fun routeLineText(result: com.syrmos.core.model.planner.JourneyResult): String =
        result.segments.filter { !it.isTransfer }
            .joinToString(" → ") { "${displayLine(it.lineId)} ${it.toStationName}" }

    /**
     * Real weather-aware advice for a rainy-day route: reads the cached weather
     * snapshot and the route's exposure (metro is underground/sheltered, tram
     * is open-air). Degrades honestly when there's no cached weather.
     */
    private fun weatherAdvice(result: com.syrmos.core.model.planner.JourneyResult): String {
        val types = result.segments.filter { !it.isTransfer }
            .mapNotNull { seg -> lines.firstOrNull { it.id == normalizeLine(seg.lineId) }?.type }
        val exposure = StationComfort.forRoute(types)
        return weatherAdviceText(weatherContext(), exposure)
    }

    /** Live snapshot wins; else the Athens seasonal profile for this month. */
    private fun weatherContext(): com.syrmos.core.model.weather.WeatherContext {
        val month = com.syrmos.core.common.extensions.currentAthensDate().monthNumber
        return com.syrmos.core.domain.assistant.WeatherContextBuilder.resolve(weatherRepository.cached, month)
    }

    private fun shelterClause(exposure: Exposure): String = when (exposure) {
        Exposure.SHELTERED -> t("mostly underground and sheltered", "κυρίως υπόγεια και υπό στέγη",
            "kryesisht nëntokë dhe e mbrojtur")
        Exposure.MIXED -> t("partly at surface level", "εν μέρει σε επιφάνεια", "pjesërisht në sipërfaqe")
        Exposure.EXPOSED -> t("open-air (tram/surface stops)", "σε ανοιχτό χώρο (τραμ/επιφάνεια)",
            "në ajër të hapur (tram/sipërfaqe)")
    }

    /**
     * Composes honest weather advice: live ("It's hot in Athens right now"),
     * seasonal ("Athens this time of year is usually hot and dry"), or unknown,
     * plus the route's shelter and an optional nudge when exposure matters.
     */
    private fun weatherAdviceText(ctx: com.syrmos.core.model.weather.WeatherContext, exposure: Exposure): String {
        val shelter = shelterClause(exposure)
        val nudge = weatherNudge(ctx.state, exposure)
        val lead = when (ctx.source) {
            com.syrmos.core.model.weather.WeatherSource.LIVE,
            com.syrmos.core.model.weather.WeatherSource.FORECAST -> {
                val place = ctx.placeName ?: t("Athens", "Αθήνα", "Athinë")
                val now = liveStateClause(ctx.state)
                t("$now in $place right now, and this route is $shelter.",
                    "$now στην $place τώρα, και η διαδρομή είναι $shelter.",
                    "$now në $place tani, dhe kjo rrugë është $shelter.")
            }
            com.syrmos.core.model.weather.WeatherSource.SEASONAL_FALLBACK -> {
                val typical = seasonalClause(ctx)
                t("I don't have live weather right now, but Athens this time of year is usually $typical. This route is $shelter.",
                    "Δεν έχω ζωντανό καιρό τώρα, αλλά η Αθήνα αυτή την εποχή είναι συνήθως $typical. Η διαδρομή είναι $shelter.",
                    "Nuk kam mot live tani, por Athina në këtë periudhë zakonisht është $typical. Kjo rrugë është $shelter.")
            }
            com.syrmos.core.model.weather.WeatherSource.UNKNOWN ->
                t("I can't check the weather offline, but this route is $shelter.",
                    "Δεν μπορώ να δω τον καιρό εκτός σύνδεσης, αλλά η διαδρομή είναι $shelter.",
                    "Nuk e kontrolloj dot motin pa internet, por kjo rrugë është $shelter.")
        }
        return if (nudge.isEmpty()) lead else "$lead $nudge"
    }

    private fun liveStateClause(state: com.syrmos.core.model.weather.WeatherState): String = when (state) {
        com.syrmos.core.model.weather.WeatherState.RAINY -> t("It's wet", "Έχει βροχή", "Ka shi")
        com.syrmos.core.model.weather.WeatherState.HOT -> t("It's hot", "Έχει ζέστη", "Bën vapë")
        com.syrmos.core.model.weather.WeatherState.WINDY -> t("It's windy", "Έχει αέρα", "Ka erë")
        com.syrmos.core.model.weather.WeatherState.NORMAL -> t("It's calm", "Ο καιρός είναι ήπιος", "Moti është i qetë")
    }

    private fun seasonalClause(ctx: com.syrmos.core.model.weather.WeatherContext): String {
        val month = ctx.month ?: 0
        return when {
            ctx.state == com.syrmos.core.model.weather.WeatherState.HOT ->
                t("hot and dry", "ζεστά και ξηρά", "e nxehtë dhe e thatë")
            month in listOf(11, 12, 1, 2) ->
                t("cooler, with rain possible", "πιο δροσερά, με πιθανή βροχή", "më e freskët, me mundësi shiu")
            else -> t("mild", "ήπια", "e butë")
        }
    }

    /** Optional nudge toward shelter when the weather makes exposure matter. */
    private fun weatherNudge(state: com.syrmos.core.model.weather.WeatherState, exposure: Exposure): String {
        if (exposure == Exposure.SHELTERED) return ""
        return when (state) {
            com.syrmos.core.model.weather.WeatherState.RAINY ->
                t("A more underground option would keep you drier.",
                    "Μια πιο υπόγεια επιλογή θα σε κρατούσε πιο στεγνό.",
                    "Një opsion më nëntokësor do të të mbante më të thatë.")
            com.syrmos.core.model.weather.WeatherState.HOT ->
                t("Prefer an underground route to avoid long sun-exposed waits.",
                    "Προτίμησε υπόγεια διαδρομή για να αποφύγεις αναμονές στον ήλιο.",
                    "Zgjidh një rrugë nëntokësore për të shmangur pritjet në diell.")
            com.syrmos.core.model.weather.WeatherState.WINDY ->
                t("Exposed tram/surface stretches can be gusty; metro is steadier.",
                    "Τα ανοιχτά τμήματα τραμ/επιφάνειας έχουν ριπές· το μετρό είναι πιο σταθερό.",
                    "Pjesët e hapura tram/sipërfaqe mund të kenë erë; metroja është më e qëndrueshme.")
            com.syrmos.core.model.weather.WeatherState.NORMAL -> ""
        }
    }

    /**
     * "How long to X". Origin is the user's nearest station (from the pushed
     * location) or an explicitly named origin; with neither it asks for one.
     */
    private suspend fun resolveTravelTime(intent: AssistantIntent.TravelTime): AssistantMessage {
        val toId = intent.toStationId ?: return botMessage(clarify(MissingSlot.DESTINATION_STATION))
        val fromId = intent.fromStationId
            ?: lastLocation?.let { findNearestStation.invoke(it, limit = 1).first().firstOrNull()?.stationId }
        if (fromId == null) return botMessage(clarify(MissingSlot.ORIGIN_STATION))
        if (fromId == toId) {
            return botMessage(t("You're already at ${stationNameById(toId)}.",
                "Είσαι ήδη στον ${stationNameById(toId)}.",
                "Je tashmë te ${stationNameById(toId)}."))
        }
        val result = planJourney.invoke(fromId, toId).first()
            ?: return botMessage(t("I couldn't find a rail route between those.",
                "Δεν βρήκα σιδηροδρομική διαδρομή ανάμεσά τους.",
                "Nuk gjeta një rrugë hekurudhore mes tyre."))
        val transfers = if (result.transferCount == 0) {
            t("no change", "χωρίς αλλαγή", "pa ndërrim")
        } else {
            t("${result.transferCount} change(s)", "${result.transferCount} αλλαγή/ές", "${result.transferCount} ndërrim(e)")
        }
        return botMessage(t(
            "About ${result.totalMinutes} min from ${stationNameById(fromId)} to ${stationNameById(toId)}, $transfers.",
            "Περίπου ${result.totalMinutes} λεπτά από ${stationNameById(fromId)} προς ${stationNameById(toId)}, $transfers.",
            "Rreth ${result.totalMinutes} min nga ${stationNameById(fromId)} te ${stationNameById(toId)}, $transfers."))
    }

    private fun stationNameById(id: String): String =
        stations.firstOrNull { it.id == id }?.let { stationName(it) } ?: id

    private suspend fun resolveFindStation(intent: AssistantIntent.FindStation): AssistantMessage {
        val matches = searchStations.invoke(intent.query).first()
        if (matches.isEmpty()) {
            return botMessage(t("I couldn't find a station matching that.",
                "Δεν βρήκα σταθμό που να ταιριάζει.",
                "Nuk gjeta një stacion që përputhet."))
        }
        val top = matches.first()
        val names = matches.take(3).joinToString(", ") { stationName(it) }
        return AssistantMessage(
            id = nextId++,
            fromUser = false,
            text = t("Found: $names.", "Βρέθηκαν: $names.", "U gjet: $names."),
            action = AssistantAction.OpenStation(top.id),
            actionLabel = t("Open ${stationName(top)}", "Άνοιγμα ${stationName(top)}", "Hap ${stationName(top)}"),
        )
    }

    private fun resolveExplainLine(intent: AssistantIntent.ExplainLine): AssistantMessage {
        val line = lines.firstOrNull { it.id == normalizeLine(intent.lineId) }
            ?: return botMessage(outOfScopeText())
        return AssistantMessage(
            id = nextId++,
            fromUser = false,
            text = t("${line.name}: ${line.terminalA} to ${line.terminalB}, ${line.stationCount} stations.",
                "${line.name}: ${line.terminalA} ως ${line.terminalB}, ${line.stationCount} σταθμοί.",
                "${line.name}: ${line.terminalA} deri ${line.terminalB}, ${line.stationCount} stacione."),
            action = AssistantAction.OpenLine(line.id),
            actionLabel = t("Open line", "Άνοιγμα γραμμής", "Hap linjën"),
        )
    }

    private val computeFare = com.syrmos.core.domain.fares.ComputeFareUseCase()

    /** Build a [FareStation] for the fare engine from a station's lines. */
    private fun fareStation(id: String): com.syrmos.core.domain.fares.FareStation? {
        val st = stations.firstOrNull { it.id == id } ?: return null
        val regions = st.lineIds.mapNotNull { lid -> lines.firstOrNull { it.id == lid }?.region }.toSet()
        val suburban = st.lineIds.any { lid ->
            lines.firstOrNull { it.id == lid }?.let {
                it.region == com.syrmos.core.model.transit.Region.THESSALONIKI &&
                    it.type == com.syrmos.core.model.transit.LineType.SUBURBAN
            } == true
        }
        return com.syrmos.core.domain.fares.FareStation(
            id = id,
            regions = regions.ifEmpty { setOf(com.syrmos.core.model.transit.Region.ATHENS) },
            isAirport = isAirportStation(id),
            isSuburban = suburban,
        )
    }

    private fun formatFareQuote(
        from: com.syrmos.core.domain.fares.FareStation,
        to: com.syrmos.core.domain.fares.FareStation,
        q: com.syrmos.core.model.fares.FareQuote,
    ): String {
        val a = stationName(from.id); val b = stationName(to.id)
        if (q.dynamic) {
            return t(
                "$a → $b is an intercity trip — the price is set at booking (route, date, class). Discounts include early-booking up to 15%, return 20% and students up to 50%. Book on hellenictrain.gr for the exact fare.",
                "$a → $b είναι υπεραστικό δρομολόγιο — η τιμή ορίζεται στην κράτηση (διαδρομή, ημέρα, θέση). Εκπτώσεις: έγκαιρη κράτηση έως 15%, επιστροφή 20%, φοιτητές έως 50%. Κάνε κράτηση στο hellenictrain.gr.",
                "$a → $b është udhëtim ndërqytetës — çmimi caktohet në rezervim (rruga, dita, klasa). Zbritje: rezervim i hershëm deri 15%, kthim 20%, studentë deri 50%. Rezervo në hellenictrain.gr.",
            )
        }
        val reduced = q.reducedPriceEur?.let { " (${t("reduced", "μειωμένο", "e reduktuar")} ${money(it)})" } ?: ""
        return "$a → $b: ${money(q.fullPriceEur)}$reduced. ${q.product} · ${q.operator}"
    }

    private suspend fun resolveFare(intent: AssistantIntent.ExplainFare): AssistantMessage {
        faresRepository.hydrateFromBundleIfNeeded()
        // Grounded from -> to fare (same engine as the web planner) when both
        // endpoints are known; otherwise fall back to the generic product list.
        val fareFrom = intent.fromStationId?.let { fareStation(it) }
        val fareTo = intent.toStationId?.let { fareStation(it) }
        if (fareFrom != null && fareTo != null) {
            return botMessage(formatFareQuote(fareFrom, fareTo, computeFare.invoke(fareFrom, fareTo)))
        }
        val products = faresRepository.products.value
        if (products.isEmpty()) {
            return botMessage(t("I don't have fare prices available offline right now.",
                "Δεν έχω διαθέσιμες τιμές εισιτηρίων εκτός σύνδεσης τώρα.",
                "Nuk kam çmime biletash të disponueshme pa internet tani."))
        }
        // Journey-derived: an airport fare if the word was used OR either
        // endpoint is actually an airport station.
        val airport = intent.airport ||
            isAirportStation(intent.fromStationId) || isAirportStation(intent.toStationId)
        val picks = if (airport) {
            products.filter { it.tags.any { tag -> tag.contains("airport") } }
                .sortedBy { it.fullPriceEur ?: Double.MAX_VALUE }.take(2)
        } else {
            products.filter { it.section == "single" }
                .sortedBy { it.fullPriceEur ?: Double.MAX_VALUE }.take(2)
        }.ifEmpty { products.take(2) }
        val lines = picks.joinToString(" · ") { "${fareTitle(it)} ${money(it.fullPriceEur)}" }
        return botMessage(lines)
    }

    private fun resolveFavorite(intent: AssistantIntent.ToggleFavorite): AssistantMessage {
        val stationId = intent.stationId ?: return botMessage(clarify(MissingSlot.STATION))
        val station = stations.firstOrNull { it.id == stationId }
        val name = station?.let { stationName(it) } ?: stationId
        val nowFavorite = favoritesRepository.toggleStation(stationId)
        val text = if (nowFavorite) {
            t("Added $name to your favorites.", "Πρόσθεσα τον $name στα αγαπημένα σου.",
                "Shtova $name te të preferuarat e tua.")
        } else {
            t("Removed $name from your favorites.", "Αφαίρεσα τον $name από τα αγαπημένα σου.",
                "Hoqa $name nga të preferuarat e tua.")
        }
        return AssistantMessage(
            id = nextId++,
            fromUser = false,
            text = text,
            action = station?.let { AssistantAction.OpenStation(it.id) },
            actionLabel = station?.let { t("Open", "Άνοιγμα", "Hap") },
        )
    }

    private fun isAirportStation(stationId: String?): Boolean {
        val st = stations.firstOrNull { it.id == stationId } ?: return false
        val name = (st.name + " " + st.nameEl).lowercase()
        return "airport" in name || "αεροδρ" in name
    }

    private fun fareTitle(product: com.syrmos.core.network.SyrmosSchedulesService.FareProduct): String =
        when (LocalizationManager.language.value) {
            AppLanguage.GREEK -> product.titleEl.ifBlank { product.titleEn }
            AppLanguage.ALBANIAN -> product.titleSq.ifBlank { product.titleEn }
            else -> product.titleEn
        }

    /** Formats a euro amount without depending on platform String.format. */
    private fun money(amount: Double?): String {
        if (amount == null) return ""
        val cents = kotlin.math.round(amount * 100).toLong()
        return "€${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    }

    private suspend fun resolveAlerts(intent: AssistantIntent.ShowAlerts): AssistantMessage {
        val feed = announcementsRepository.feed.first()
        val alerts = feed.announcements.filter { it.isServiceAlert }
        return when {
            alerts.isNotEmpty() -> botMessage(t("Active alerts: ", "Ενεργές ειδοποιήσεις: ", "Njoftime aktive: ") +
                alerts.take(2).joinToString("; ") { it.title })
            feed.status != null && feed.status?.isAlert == true ->
                botMessage(feed.status?.rawMessage.orEmpty())
            else -> botMessage(t("No active service alerts right now.",
                "Δεν υπάρχουν ενεργές ειδοποιήσεις τώρα.",
                "Nuk ka njoftime aktive tani."))
        }
    }

    private fun resolveOpenMap(intent: AssistantIntent.OpenMap): AssistantMessage {
        val station = intent.stationId?.let { id -> stations.firstOrNull { it.id == id } }
        return if (station != null) {
            AssistantMessage(
                id = nextId++,
                fromUser = false,
                text = t("Here's ${stationName(station)}.", "Ορίστε ${stationName(station)}.", "Ja ${stationName(station)}."),
                action = AssistantAction.OpenStation(station.id),
                actionLabel = t("Open", "Άνοιγμα", "Hap"),
            )
        } else {
            botMessage(t("Open the Map tab to see live train positions.",
                "Άνοιξε τον Χάρτη για ζωντανές θέσεις συρμών.",
                "Hap Hartën për pozicionet e trenave."))
        }
    }

    // MARK: - Helpers

    private suspend fun resolveStation(stationId: String?, lineId: String?): Station? {
        if (stationId != null) return stations.firstOrNull { it.id == stationId }
        if (lineId != null) {
            // Departures for a bare line answer from the line origin.
            return stationRepository.getStationsOnLine(normalizeLine(lineId)).first().firstOrNull()
        }
        return null
    }

    private fun stationName(station: Station): String =
        if (LocalizationManager.language.value == AppLanguage.GREEK && station.nameEl.isNotBlank()) station.nameEl else station.name

    private fun displayLine(lineId: String): String = normalizeLine(lineId)

    private fun normalizeLine(lineId: String): String = if (lineId.startsWith("M3")) "M3" else lineId

    private fun greeting(): AssistantMessage = botMessage(
        t("Hi, I'm Ariadne. Ask about departures, weather, or trips like \"airport by 21:30\".",
            "Γεια, είμαι η Αριάδνη. Ρώτησέ με για αναχωρήσεις, καιρό ή διαδρομές όπως «αεροδρόμιο στις 21:30».",
            "Përshëndetje, jam Ariadne. Më pyet për nisje, motin ose udhëtime si «aeroporti në 21:30»."),
    )

    private fun helpText(): String = t(
        "I handle departures (today or a future day), last train home, trip planning (including \"be there by X:XX\"), weather at a station, service alerts, ticket prices, favorites, and Athens rail info. Offline-safe.",
        "Χειρίζομαι αναχωρήσεις (σήμερα ή άλλη μέρα), τελευταίο τρένο, σχεδιασμό διαδρομής (και «να είσαι εκεί στις X:XX»), καιρό σταθμού, ειδοποιήσεις, τιμές εισιτηρίων, αγαπημένα και πληροφορίες των συγκοινωνιών Αθήνας. Λειτουργώ offline.",
        "Trajtoj nisjet (sot ose një ditë tjetër), trenin e fundit, planifikim udhëtimi (edhe «të jesh atje deri në X:XX»), motin te një stacion, njoftime, çmime biletash, të preferuarat dhe informacione për transportin e Athinës. Punoj pa internet.",
    )

    private fun outOfScopeText(): String = t(
        "I can only help with Syrmos and Athens public transport.",
        "Μπορώ να βοηθήσω μόνο με το Syrmos και τις συγκοινωνίες της Αθήνας.",
        "Mund të ndihmoj vetëm me Syrmos dhe transportin publik të Athinës.",
    )

    /**
     * Graceful recovery for a dead-ended turn: suggest the closest station if
     * the text almost named one, otherwise a warm nudge toward what Ariadne can
     * do. Never a flat "I didn't understand."
     */
    private suspend fun askCloudLLM(text: String): AssistantMessage? {
        val service = ariadneChatService ?: return null
        val history = _uiState.value.messages.takeLast(10).map { msg ->
            AriadneChatMessage(
                role = if (msg.fromUser) "user" else "assistant",
                text = msg.text,
            )
        } + AriadneChatMessage(role = "user", text = text)
        val reply = service.chat(history) ?: return null
        return botMessage(reply)
    }

    private fun recover(text: String, p: AthensTransitParser): AssistantMessage {
        val suggestion = p.suggestStation(text)
        return botMessage(if (suggestion != null) didYouMeanStation(suggestion) else recoveryHelp())
    }

    private fun didYouMeanStation(name: String): String = t(
        "I didn't quite catch that — did you mean $name? Try \"next trains from $name\".",
        "Δεν το κατάλαβα ακριβώς — μήπως εννοείς $name; Δοκίμασε «επόμενα τρένα από $name».",
        "Nuk e kuptova mirë — mos ke parasysh $name? Provo «trenat e ardhshëm nga $name».",
    )

    private fun recoveryHelp(): String = t(
        "I didn't catch that. Ask me about departures, a route between two stations, or the last train home.",
        "Δεν το κατάλαβα. Ρώτησέ με για αναχωρήσεις, διαδρομή μεταξύ δύο σταθμών ή το τελευταίο τρένο.",
        "Nuk e kuptova. Më pyet për nisje, një udhëtim mes dy stacioneve ose trenin e fundit.",
    )

    private fun clarify(missing: MissingSlot): String = when (missing) {
        MissingSlot.ORIGIN_STATION -> t("From which station?", "Από ποιον σταθμό;", "Nga cili stacion?")
        MissingSlot.DESTINATION_STATION -> t("To which station?", "Προς ποιον σταθμό;", "Te cili stacion?")
        MissingSlot.STATION -> t("Which station?", "Ποιος σταθμός;", "Cili stacion?")
    }

    private fun botMessage(text: String) = AssistantMessage(id = nextId++, fromUser = false, text = text)
    private fun userMessage(text: String) = AssistantMessage(id = nextId++, fromUser = true, text = text)

    private fun t(en: String, el: String, sq: String): String = when (LocalizationManager.language.value) {
        AppLanguage.GREEK -> el
        AppLanguage.ALBANIAN -> sq
        else -> en
    }
}
