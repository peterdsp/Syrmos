package com.syrmos.feature.home.assistant

import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.data.repository.FavoritesRepository
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.data.sync.AnnouncementsRepository
import com.syrmos.core.data.sync.FaresRepository
import com.syrmos.core.data.sync.WeatherRepository
import com.syrmos.core.domain.assistant.Exposure
import com.syrmos.core.domain.assistant.StationComfort
import com.syrmos.core.domain.assistant.AssistantIntent
import com.syrmos.core.domain.assistant.AssistantQueryNormalizer
import com.syrmos.core.domain.assistant.AssistantVocabularyBuilder
import com.syrmos.core.domain.assistant.NoOpQueryNormalizer
import com.syrmos.core.domain.assistant.AthensTransitParser
import com.syrmos.core.domain.assistant.DayContext
import com.syrmos.core.domain.assistant.DayContextResolver
import com.syrmos.core.domain.assistant.MissingSlot
import com.syrmos.core.domain.usecase.ComputeDeparturesFromBandsUseCase
import com.syrmos.core.domain.usecase.FindNearestStationUseCase
import com.syrmos.core.domain.usecase.GetLastTrainUseCase
import com.syrmos.core.domain.usecase.GetLinesUseCase
import com.syrmos.core.domain.usecase.GetNextDeparturesUseCase
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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private var parser: AthensTransitParser? = null
    private var stations: List<Station> = emptyList()
    private var lines: List<Line> = emptyList()
    private var nextId = 0L
    private var lastLocation: UserLocation? = null

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
            lines = getLinesUseCase.getAllLines().first()
            parser = AthensTransitParser(AssistantVocabularyBuilder.build(stations, lines))
            _uiState.update {
                it.copy(ready = true, messages = listOf(greeting()))
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
            // On-device LLM (when supplied) rewrites fuzzy input; the
            // deterministic parser still classifies and validates it.
            val cleaned = queryNormalizer.normalize(text) ?: text
            val reply = resolve(p.parse(cleaned))
            _uiState.update { it.copy(messages = it.messages + reply, thinking = false) }
        }
    }

    // MARK: - Dispatch

    private suspend fun resolve(intent: AssistantIntent): AssistantMessage = when (intent) {
        is AssistantIntent.ShowDepartures -> resolveDepartures(intent)
        is AssistantIntent.LastTrain -> resolveLastTrain(intent)
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

    private suspend fun resolvePlanTrip(intent: AssistantIntent.PlanTrip): AssistantMessage {
        val fromId = intent.fromStationId ?: return botMessage(clarify(MissingSlot.ORIGIN_STATION))
        val toId = intent.toStationId ?: return botMessage(clarify(MissingSlot.DESTINATION_STATION))
        val result = planJourney.invoke(fromId, toId).first()
            ?: return botMessage(t("I couldn't find a rail route between those.",
                "Δεν βρήκα σιδηροδρομική διαδρομή ανάμεσά τους.",
                "Nuk gjeta një rrugë hekurudhore mes tyre."))
        val legs = result.segments.filter { !it.isTransfer }
        val lines = legs.joinToString(" → ") { "${displayLine(it.lineId)} ${it.toStationName}" }
        val transfers = if (result.transferCount == 0) {
            t("no change", "χωρίς αλλαγή", "pa ndërrim")
        } else {
            t("${result.transferCount} change(s)", "${result.transferCount} αλλαγή/ές", "${result.transferCount} ndërrim(e)")
        }
        val exposure = if (intent.lowExposure) "\n" + weatherAdvice(result) else ""
        return botMessage(
            t("$lines. About ${result.totalMinutes} min, $transfers.",
                "$lines. Περίπου ${result.totalMinutes} λεπτά, $transfers.",
                "$lines. Rreth ${result.totalMinutes} min, $transfers.") + exposure,
        )
    }

    /**
     * Real weather-aware advice for a rainy-day route: reads the cached weather
     * snapshot and the route's exposure (metro is underground/sheltered, tram
     * is open-air). Degrades honestly when there's no cached weather.
     */
    private fun weatherAdvice(result: com.syrmos.core.model.planner.JourneyResult): String {
        val types = result.segments.filter { !it.isTransfer }
            .mapNotNull { seg -> lines.firstOrNull { it.id == normalizeLine(seg.lineId) }?.type }
        val exposure = StationComfort.forRoute(types)
        val shelter = when (exposure) {
            Exposure.SHELTERED -> t("mostly underground and sheltered", "κυρίως υπόγεια και υπό στέγη",
                "kryesisht nëntokë dhe e mbrojtur")
            Exposure.MIXED -> t("partly at surface level", "εν μέρει σε επιφάνεια", "pjesërisht në sipërfaqe")
            Exposure.EXPOSED -> t("open-air (tram/surface stops)", "σε ανοιχτό χώρο (τραμ/επιφάνεια)",
                "në ajër të hapur (tram/sipërfaqe)")
        }
        val weather = weatherRepository.cached
        return when {
            weather != null && weather.current.condition.isWet ->
                t("It's wet in ${weather.placeName} right now, and this route is $shelter.",
                    "Έχει βροχή στην ${weather.placeName} τώρα, και η διαδρομή είναι $shelter.",
                    "Ka shi në ${weather.placeName} tani, dhe kjo rrugë është $shelter.")
            weather != null ->
                t("It's dry in ${weather.placeName} right now; this route is $shelter.",
                    "Δεν βρέχει στην ${weather.placeName} τώρα· η διαδρομή είναι $shelter.",
                    "S'ka shi në ${weather.placeName} tani; kjo rrugë është $shelter.")
            else ->
                t("I can't check live weather offline, but this route is $shelter.",
                    "Δεν μπορώ να δω τον καιρό εκτός σύνδεσης, αλλά η διαδρομή είναι $shelter.",
                    "Nuk e kontrolloj dot motin pa internet, por kjo rrugë është $shelter.")
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

    private suspend fun resolveFare(intent: AssistantIntent.ExplainFare): AssistantMessage {
        faresRepository.hydrateFromBundleIfNeeded()
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
        t("Hi, I'm Ariadne. Ask me about Athens trains, last departures, or how to get somewhere.",
            "Γεια, είμαι η Αριάδνη. Ρώτησέ με για τα τρένα της Αθήνας, τελευταία δρομολόγια ή πώς να πας κάπου.",
            "Përshëndetje, jam Ariadne. Më pyet për trenat e Athinës, nisjet e fundit ose si të shkosh diku."),
    )

    private fun helpText(): String = t(
        "I can show next departures (today or a future day), the last train home, plan a trip, explain a line, ticket prices, service alerts, and favorite a station. I only cover Syrmos and Athens public transport, fully offline.",
        "Μπορώ να δείξω επόμενες αναχωρήσεις (σήμερα ή άλλη μέρα), το τελευταίο τρένο, διαδρομή, να εξηγήσω μια γραμμή, τιμές εισιτηρίων, ειδοποιήσεις και να προσθέσω σταθμό στα αγαπημένα. Καλύπτω μόνο το Syrmos και τις συγκοινωνίες της Αθήνας, εκτός σύνδεσης.",
        "Mund të tregoj nisjet (sot ose një ditë tjetër), trenin e fundit, një udhëtim, të shpjegoj një linjë, çmimet e biletave, njoftimet dhe të ruaj një stacion. Mbuloj vetëm Syrmos dhe transportin e Athinës, pa internet.",
    )

    private fun outOfScopeText(): String = t(
        "I can only help with Syrmos and Athens public transport.",
        "Μπορώ να βοηθήσω μόνο με το Syrmos και τις συγκοινωνίες της Αθήνας.",
        "Mund të ndihmoj vetëm me Syrmos dhe transportin publik të Athinës.",
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
