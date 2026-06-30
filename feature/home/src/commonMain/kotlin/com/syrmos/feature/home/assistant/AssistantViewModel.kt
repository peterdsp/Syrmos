package com.syrmos.feature.home.assistant

import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.data.sync.AnnouncementsRepository
import com.syrmos.core.domain.assistant.AssistantIntent
import com.syrmos.core.domain.assistant.AssistantVocabularyBuilder
import com.syrmos.core.domain.assistant.AthensTransitParser
import com.syrmos.core.domain.assistant.DayContext
import com.syrmos.core.domain.assistant.MissingSlot
import com.syrmos.core.domain.usecase.GetLastTrainUseCase
import com.syrmos.core.domain.usecase.GetLinesUseCase
import com.syrmos.core.domain.usecase.GetNextDeparturesUseCase
import com.syrmos.core.domain.usecase.PlanJourneyUseCase
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
    private val getLastTrain: GetLastTrainUseCase,
    private val planJourney: PlanJourneyUseCase,
    private val searchStations: SearchStationsUseCase,
    private val announcementsRepository: AnnouncementsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private var parser: AthensTransitParser? = null
    private var stations: List<Station> = emptyList()
    private var lines: List<Line> = emptyList()
    private var nextId = 0L

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
            val reply = resolve(p.parse(text))
            _uiState.update { it.copy(messages = it.messages + reply, thinking = false) }
        }
    }

    // MARK: - Dispatch

    private suspend fun resolve(intent: AssistantIntent): AssistantMessage = when (intent) {
        is AssistantIntent.ShowDepartures -> resolveDepartures(intent)
        is AssistantIntent.LastTrain -> resolveLastTrain(intent)
        is AssistantIntent.PlanTrip -> resolvePlanTrip(intent)
        is AssistantIntent.FindStation -> resolveFindStation(intent)
        is AssistantIntent.ExplainLine -> resolveExplainLine(intent)
        is AssistantIntent.ShowAlerts -> resolveAlerts(intent)
        is AssistantIntent.OpenMap -> resolveOpenMap(intent)
        AssistantIntent.Help -> botMessage(helpText())
        is AssistantIntent.NeedsClarification -> botMessage(clarify(intent.missing))
        AssistantIntent.OutOfScope -> botMessage(outOfScopeText())
    }

    private suspend fun resolveDepartures(intent: AssistantIntent.ShowDepartures): AssistantMessage {
        val station = resolveStation(intent.stationId, intent.lineId) ?: return botMessage(clarify(MissingSlot.STATION))
        val lineIds = intent.lineId?.let { listOf(it) } ?: station.lineIds
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
        val header = t("Next from ${stationName(station)}:",
            "Επόμενα από ${stationName(station)}:",
            "Të ardhshmet nga ${stationName(station)}:")
        val dayNote = if (intent.day != DayContext.TODAY) {
            "\n" + t("Showing today. Open the line for the full ${intent.day.name.lowercase()} timetable.",
                "Εμφανίζεται σήμερα. Άνοιξε τη γραμμή για όλο το πρόγραμμα.",
                "Po shfaqet sot. Hap linjën për orarin e plotë.")
        } else ""
        return AssistantMessage(
            id = nextId++,
            fromUser = false,
            text = header + dayNote,
            departures = sorted,
            action = intent.lineId?.let { AssistantAction.OpenLine(normalizeLine(it)) }
                ?: AssistantAction.OpenStation(station.id),
            actionLabel = t("Open", "Άνοιγμα", "Hap"),
        )
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
        val exposure = if (intent.lowExposure) {
            "\n" + t("I can't check live weather offline, but this is the fewest-transfer route.",
                "Δεν μπορώ να δω τον καιρό εκτός σύνδεσης, αλλά αυτή έχει τις λιγότερες αλλαγές.",
                "Nuk e kontrolloj dot motin pa internet, por kjo ka më pak ndërrime.")
        } else ""
        return botMessage(
            t("$lines. About ${result.totalMinutes} min, $transfers.",
                "$lines. Περίπου ${result.totalMinutes} λεπτά, $transfers.",
                "$lines. Rreth ${result.totalMinutes} min, $transfers.") + exposure,
        )
    }

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
        "I can show next departures, the last train home, plan a trip between two stations, explain a line, and show service alerts. I only cover Syrmos and Athens public transport, fully offline.",
        "Μπορώ να δείξω επόμενες αναχωρήσεις, το τελευταίο τρένο, διαδρομή μεταξύ δύο σταθμών, να εξηγήσω μια γραμμή και ειδοποιήσεις. Καλύπτω μόνο το Syrmos και τις συγκοινωνίες της Αθήνας, εκτός σύνδεσης.",
        "Mund të tregoj nisjet, trenin e fundit, një udhëtim mes dy stacioneve, të shpjegoj një linjë dhe njoftimet. Mbuloj vetëm Syrmos dhe transportin e Athinës, pa internet.",
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
