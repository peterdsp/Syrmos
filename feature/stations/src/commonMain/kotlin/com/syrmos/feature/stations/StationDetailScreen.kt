package com.syrmos.feature.stations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.domain.departures.ResolvedDeparture
import com.syrmos.core.domain.departures.groupDepartures
import com.syrmos.core.domain.usecase.UpcomingDeparture
import com.syrmos.core.designsystem.component.GroupedDepartureCard
import com.syrmos.core.designsystem.component.AlertBannerInfo
import com.syrmos.core.designsystem.component.LineColorIndicator
import com.syrmos.core.designsystem.component.SectionHeader
import com.syrmos.core.designsystem.component.ServiceAlertBanner
import com.syrmos.core.designsystem.component.toComposeColor
import com.syrmos.core.model.transit.Direction
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineColor
import com.syrmos.core.model.alerts.AlertSeverity

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StationDetailScreen(
    viewModel: StationDetailViewModel,
    alertBanner: AlertBannerInfo? = null,
    lineDisruptions: Map<String, AlertSeverity> = emptyMap(),
    onBack: () -> Unit = {},
    // Carries BOTH the line and the resolver's per-line station id so the caller
    // can open that line's timetable AT this hub (not just the whole-line view).
    onOpenTransfer: (lineId: String, stationId: String) -> Unit = { _, _ -> },
    onOpenDirections: ((latitude: Double, longitude: Double, label: String) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val lang by LocalizationManager.language.collectAsState()
    var showMapSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.stationName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        if (uiState.stationNameEl.isNotEmpty()) {
                            Text(
                                text = uiState.stationNameEl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = when (lang) {
                                AppLanguage.GREEK -> "Πισω"
                                AppLanguage.ALBANIAN -> "Prapa"
                                AppLanguage.ITALIAN -> "Indietro"
                                else -> "Back"
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (alertBanner != null) {
                item {
                    ServiceAlertBanner(info = alertBanner)
                }
            }
            if (uiState.connectingLines.isNotEmpty()) {
                item {
                    SectionHeader(title = when (lang) {
                        AppLanguage.GREEK -> "Γραμμες σε αυτον τον σταθμο"
                        AppLanguage.ALBANIAN -> "Linjat ne kete stacion"
                        AppLanguage.ITALIAN -> "Linee in questa stazione"
                        else -> "Lines at this station"
                    })
                }
                item {
                    Card(
                        onClick = { showMapSheet = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    uiState.connectingLines.forEach { line ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            LineColorIndicator(lineColor = line.color, size = 14.dp)
                                            Text(
                                                text = "${line.localizedName(lang)} (${line.terminalA} - ${line.terminalB})",
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Filled.Map,
                                    contentDescription = when (lang) {
                                        AppLanguage.GREEK -> "Εμφανιση στον χαρτη"
                                        AppLanguage.ALBANIAN -> "Shfaq ne harte"
                                        AppLanguage.ITALIAN -> "Mostra sulla mappa"
                                        else -> "Show on map"
                                    },
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (uiState.lineIds.size > 1) {
                                Spacer(modifier = Modifier.height(8.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    uiState.lineIds.forEach { lineId ->
                                        LinePill(lineId = lineId)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Actionable interchange: tap a line to open its timetable here.
            // Replaces the old passive "Transfer station" badge. On an unscoped
            // screen require 2+ targets so a single-line station (whose one target
            // is its own line) shows nothing; on a focus-scoped transfer screen
            // the focused line is already excluded, so any remaining target is a
            // genuine other-line transfer and one is enough to show.
            val minInterchangeTargets = if (uiState.focusLineId != null) 1 else 2
            if (uiState.interchangeTargets.size >= minInterchangeTargets) {
                item {
                    SectionHeader(title = when (lang) {
                        AppLanguage.GREEK -> "Αλλαγη γραμμης εδω"
                        AppLanguage.ALBANIAN -> "Ndrysho linjen ketu"
                        AppLanguage.ITALIAN -> "Cambia linea qui"
                        else -> "Change line here"
                    })
                }
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        uiState.interchangeTargets.forEach { target ->
                            Card(
                                onClick = { onOpenTransfer(target.line.id, target.stationId) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    LineColorIndicator(lineColor = target.line.color, size = 14.dp)
                                    Text(
                                        text = "${target.line.localizedName(lang)} (${target.line.terminalA} - ${target.line.terminalB})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = "›",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.isSuburban) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Button(
                                onClick = {
                                    uriHandler.openUri("https://newtickets.hellenictrain.gr/Channels.HellenicTrainWeb/")
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(when (lang) {
                                    AppLanguage.GREEK -> "Αγορα εισιτηριου στην Hellenic Train"
                                    AppLanguage.ALBANIAN -> "Bli bilete ne Hellenic Train"
                                    AppLanguage.ITALIAN -> "Acquista biglietto su Hellenic Train"
                                    else -> "Buy ticket on Hellenic Train"
                                })
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when (lang) {
                                    AppLanguage.GREEK -> "Η πληρωμη και η εκδοση εισιτηριου γινονται 100% στον ιστοτοπο της Hellenic Train. Το Syrmos απλως παρεχει τον συνδεσμο, δεν συλλεγει στοιχεια πληρωμης και δεν εχει καμια ευθυνη για την κρατηση."
                                    AppLanguage.ALBANIAN -> "Pagesa dhe leshimi i biletes behen 100% ne faqen e Hellenic Train. Syrmos thjesht ofron lidhjen, nuk mbledh te dhena pagesash dhe nuk ka asnje pergjegesi per rezervimin."
                                    AppLanguage.ITALIAN -> "Il pagamento e l'emissione del biglietto avvengono al 100% sul sito di Hellenic Train. Syrmos fornisce solo il link, non raccoglie dati di pagamento e non ha responsabilita per la prenotazione."
                                    else -> "Payment and ticket issuance happen entirely on Hellenic Train's website. Syrmos only provides the link, does not collect any payment data, and has no responsibility for the booking."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                val base = when (lang) {
                    AppLanguage.GREEK -> "Επομενες αναχωρησεις"
                    AppLanguage.ALBANIAN -> "Nisjet e ardhshme"
                    AppLanguage.ITALIAN -> "Prossime partenze"
                    else -> "Next departures"
                }
                // When opened as a transfer, departures are scoped to one line;
                // name it so a single-line list does not look like a bug.
                val focus = uiState.focusLineId
                val title = if (focus != null) {
                    val name = uiState.connectingLines.firstOrNull { it.id == focus }?.localizedName(lang) ?: focus
                    "$base · $name"
                } else {
                    base
                }
                SectionHeader(title = title)
            }

            if (uiState.departures.isEmpty()) {
                item {
                    Text(
                        text = if (uiState.hasLoadedDepartures) {
                            // Honest line-state reason first (suspended / seasonal /
                            // under construction), mirroring the web station sheet;
                            // otherwise the generic "nothing right now" message.
                            stationServiceState(uiState.connectingLines)
                                ?.let { serviceStateMessage(it, lang) }
                                ?: when (lang) {
                                AppLanguage.GREEK -> "Δεν υπάρχουν διαθέσιμα δρομολόγια αυτή τη στιγμή. Η γραμμή είναι κλειστή ή έχει τελειώσει η σημερινή υπηρεσία."
                                AppLanguage.ALBANIAN -> "Nuk ka nisje te disponueshme tani. Linja eshte mbyllur ose ka perfunduar sherbimi i sotem."
                                AppLanguage.ITALIAN -> "Nessuna partenza disponibile al momento. La linea e chiusa o il servizio odierno e terminato."
                                else -> "No departures right now. The line is closed or today's service has ended."
                            }
                        } else {
                            when (lang) {
                                AppLanguage.GREEK -> "Φορτωση δρομολογιων..."
                                AppLanguage.ALBANIAN -> "Duke ngarkuar oraret..."
                                AppLanguage.ITALIAN -> "Caricamento partenze..."
                                else -> "Loading departures..."
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                // Collapse the repeated "Line 3 · towards X · Scheduled" rows into
                // one grouped card per (line, destination) carrying the next few
                // times. The destination label (localized, connecting-line aware)
                // is resolved here, then the pure `groupDepartures` transform (shared
                // in shape with web/iOS) folds by that label.
                val grouped = groupDepartures(
                    uiState.departures.map { departure ->
                        val direction = departureDirectionLabel(
                            departure = departure,
                            connectingLines = uiState.connectingLines,
                            language = lang,
                        )
                        val isAirport = departure.serviceType == "airport" ||
                                direction.contains("airport", ignoreCase = true) ||
                                direction.contains("αεροδρ", ignoreCase = true)
                        ResolvedDeparture(
                            lineId = departure.lineId,
                            destination = direction,
                            minutesAway = departure.minutesAway,
                            time = departure.time,
                            serviceType = if (isAirport) "airport" else departure.serviceType,
                            sourceConfidence = departure.sourceConfidence,
                        )
                    },
                )
                items(grouped) { group ->
                    val isAirport = group.serviceType == "airport"
                    GroupedDepartureCard(
                        lineName = group.lineId,
                        lineColor = lineIdToColor(group.lineId),
                        destination = group.destination,
                        times = group.times.map { it.minutesAway to it.time },
                        moreCount = group.moreCount,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        lineId = group.lineId,
                        isAirport = isAirport,
                        airportLabel = if (isAirport) {
                            when (lang) {
                                AppLanguage.GREEK -> "Αεροδρομιο"
                                AppLanguage.ALBANIAN -> "Aeroporti"
                                AppLanguage.ITALIAN -> "Aeroporto"
                                else -> "Airport"
                            }
                        } else null,
                        sourceConfidence = group.sourceConfidence
                            .takeIf { it != com.syrmos.core.model.schedule.SourceConfidence.UNKNOWN },
                        language = lang,
                        disruptionSeverity = lineDisruptions[group.lineId],
                    )
                }
            }
        }

        if (showMapSheet) {
            ModalBottomSheet(
                onDismissRequest = { showMapSheet = false },
                sheetState = sheetState,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = uiState.stationName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = uiState.stationNameEl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    StationMiniMap(
                        latitude = uiState.latitude,
                        longitude = uiState.longitude,
                        stationName = uiState.stationName,
                        connectingLines = uiState.connectingLines.map {
                            MiniMapLine(name = it.name, color = it.color)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .padding(vertical = 4.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onOpenDirections?.invoke(
                                uiState.latitude,
                                uiState.longitude,
                                uiState.stationName,
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(when (lang) {
                            AppLanguage.GREEK -> "Οδηγιες"
                            AppLanguage.ALBANIAN -> "Merr udhezime"
                            AppLanguage.ITALIAN -> "Indicazioni"
                            else -> "Get directions"
                        })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

internal fun departureDirectionLabel(
    departure: UpcomingDeparture,
    connectingLines: List<Line>,
    language: AppLanguage,
): String {
    departure.notes
        ?.trim()
        ?.takeIf { it.isNotEmpty() && '_' !in it }
        ?.let { return it }

    if (
        departure.direction == Direction.OUTBOUND &&
        (departure.lineId == "M3_AIR" || departure.serviceType == "airport")
    ) {
        return when (language) {
            AppLanguage.GREEK -> "Αεροδρόμιο"
            AppLanguage.ALBANIAN -> "Aeroporti"
            AppLanguage.ITALIAN -> "Aeroporto"
            AppLanguage.ENGLISH -> "Airport"
        }
    }

    val lineId = if (departure.lineId == "M3_AIR") "M3" else departure.lineId
    val line = connectingLines.firstOrNull { it.id == lineId }
    val terminal = when (departure.direction) {
        Direction.OUTBOUND -> line?.terminalB
        Direction.INBOUND -> line?.terminalA
    }
    if (!terminal.isNullOrBlank()) return terminal

    return when (departure.direction) {
        Direction.OUTBOUND -> when (language) {
            AppLanguage.GREEK -> "Προς τέρμα"
            AppLanguage.ALBANIAN -> "Drejt terminalit"
            AppLanguage.ITALIAN -> "Verso il capolinea"
            AppLanguage.ENGLISH -> "Outbound"
        }
        Direction.INBOUND -> when (language) {
            AppLanguage.GREEK -> "Προς αφετηρία"
            AppLanguage.ALBANIAN -> "Drejt nisjes"
            AppLanguage.ITALIAN -> "Verso il capolinea iniziale"
            AppLanguage.ENGLISH -> "Inbound"
        }
    }
}

@Composable
private fun LinePill(lineId: String) {
    val color = lineIdToColor(lineId).toComposeColor()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(
                width = 0.8.dp,
                color = color.copy(alpha = 0.4f),
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = lineId.replace("M3_AIR", "M3"),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

private fun Line.localizedName(lang: AppLanguage): String {
    if (lang == AppLanguage.GREEK && nameEl.isNotBlank()) return nameEl
    if (lang == AppLanguage.ITALIAN) return name
        .replace("Line ", "Linea ")
        .replace("Suburban ", "Suburbano ")
    if (lang == AppLanguage.ALBANIAN) return name
        .replace("Line ", "Linja ")
        .replace("Suburban ", "Periferik ")
    return name
}

private fun lineIdToColor(lineId: String): LineColor = when {
    lineId == "M1" -> LineColor.GREEN
    lineId == "M2" -> LineColor.RED
    lineId == "M3" || lineId == "M3_AIR" -> LineColor.BLUE
    lineId.startsWith("T") -> LineColor.TRAM_ORANGE
    lineId.startsWith("P") || lineId.startsWith("A") -> LineColor.SUBURBAN_PURPLE
    else -> LineColor.BLUE
}

/**
 * Honest reason a station's departures are empty, when the reason is a line state
 * rather than a plain "nothing right now". Mirrors the web station sheet and the
 * iOS StationDetailView: a suspended or seasonal line must not read as a glitch.
 */
private enum class StationServiceState { SUSPENDED, CONSTRUCTION, SEASONAL }

private fun stationServiceState(lines: List<Line>): StationServiceState? {
    if (lines.isEmpty()) return null
    // Suspended / under construction only when NO line here is live, so an
    // interchange that also carries a running line stays a normal sheet.
    if (lines.all { !it.isOperational }) {
        if (lines.any { it.isSuspended }) return StationServiceState.SUSPENDED
        // Reaching here with no suspended line means any built-but-closed line
        // is under construction.
        if (lines.any { it.isBuiltButClosed }) return StationServiceState.CONSTRUCTION
    }
    if (lines.any { it.isSeasonal }) return StationServiceState.SEASONAL
    return null
}

private fun serviceStateMessage(state: StationServiceState, lang: AppLanguage): String = when (state) {
    StationServiceState.SUSPENDED -> when (lang) {
        AppLanguage.GREEK -> "Προσωρινή αναστολή δρομολογίων. Αυτή η γραμμή δεν λειτουργεί αυτή τη στιγμή."
        AppLanguage.ALBANIAN -> "Sherbimi perkohesisht i pezulluar. Kjo linje nuk eshte ne pune per momentin."
        AppLanguage.ITALIAN -> "Servizio temporaneamente sospeso. Questa linea non e in servizio in questo momento."
        else -> "Service temporarily suspended. This line is not running right now."
    }
    StationServiceState.CONSTRUCTION -> when (lang) {
        AppLanguage.GREEK -> "Δεν λειτουργεί ακόμη. Η γραμμή έχει κατασκευαστεί αλλά δεν έχει τεθεί σε επιβατική λειτουργία."
        AppLanguage.ALBANIAN -> "Ende jo e hapur. Hekurudha eshte ndertuar por ende nuk eshte ne sherbim per pasagjere."
        AppLanguage.ITALIAN -> "Non ancora in servizio. Il binario e costruito ma non ancora in servizio passeggeri."
        else -> "Not yet open. The track is built but not yet in passenger service."
    }
    StationServiceState.SEASONAL -> when (lang) {
        AppLanguage.GREEK -> "Εποχικό δρομολόγιο. Λειτουργεί επιλεγμένες ημέρες και εποχές. Δεν υπάρχει προγραμματισμένη αναχώρηση από εδώ αυτή τη στιγμή."
        AppLanguage.ALBANIAN -> "Sherbim sezonal. Funksionon ne dite dhe stine te zgjedhura. Asgje nuk eshte planifikuar nga ketu per momentin."
        AppLanguage.ITALIAN -> "Servizio stagionale. Attivo in giorni e stagioni selezionati. Al momento non e prevista alcuna partenza da qui."
        else -> "Seasonal service. Runs on selected days and seasons. Nothing is scheduled from here right now."
    }
}
