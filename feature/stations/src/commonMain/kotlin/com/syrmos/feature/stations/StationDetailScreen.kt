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
import androidx.compose.material3.CardDefaults
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
import com.syrmos.core.designsystem.component.DepartureCard
import com.syrmos.core.designsystem.component.LineColorIndicator
import com.syrmos.core.designsystem.component.SectionHeader
import com.syrmos.core.designsystem.component.toComposeColor
import com.syrmos.core.model.transit.LineColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StationDetailScreen(
    viewModel: StationDetailViewModel,
    onBack: () -> Unit = {},
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
            if (uiState.connectingLines.isNotEmpty()) {
                item {
                    SectionHeader(title = when (lang) {
                        AppLanguage.GREEK -> "Γραμμες σε αυτον τον σταθμο"
                        AppLanguage.ALBANIAN -> "Linjat ne kete stacion"
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
                                                text = "${line.name} (${line.terminalA} - ${line.terminalB})",
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

            if (uiState.isInterchange) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "⇄",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                text = when (lang) {
                                    AppLanguage.GREEK -> "Σταθμος ανταποκρισης"
                                    AppLanguage.ALBANIAN -> "Stacion korrespondence"
                                    else -> "Transfer station"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
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
                                    else -> "Buy ticket on Hellenic Train"
                                })
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when (lang) {
                                    AppLanguage.GREEK -> "Η πληρωμη και η εκδοση εισιτηριου γινονται 100% στον ιστοτοπο της Hellenic Train. Το Syrmos απλως παρεχει τον συνδεσμο, δεν συλλεγει στοιχεια πληρωμης και δεν εχει καμια ευθυνη για την κρατηση."
                                    AppLanguage.ALBANIAN -> "Pagesa dhe leshimi i biletes behen 100% ne faqen e Hellenic Train. Syrmos thjesht ofron lidhjen, nuk mbledh te dhena pagesash dhe nuk ka asnje pergjegesi per rezervimin."
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
                SectionHeader(title = when (lang) {
                    AppLanguage.GREEK -> "Επομενες αναχωρησεις"
                    AppLanguage.ALBANIAN -> "Nisjet e ardhshme"
                    else -> "Next departures"
                })
            }

            if (uiState.departures.isEmpty()) {
                item {
                    Text(
                        text = if (uiState.hasLoadedDepartures) {
                            when (lang) {
                                AppLanguage.GREEK -> "Δεν υπαρχουν διαθεσιμα δρομολογια αυτη τη στιγμη. Η γραμμη ειναι κλειστη η εχει τελειωσει η σημερινη υπηρεσια."
                                AppLanguage.ALBANIAN -> "Nuk ka nisje te disponueshme tani. Linja eshte mbyllur ose ka perfunduar sherbimi i sotem."
                                else -> "No departures right now. The line is closed or today's service has ended."
                            }
                        } else {
                            when (lang) {
                                AppLanguage.GREEK -> "Φορτωση δρομολογιων..."
                                AppLanguage.ALBANIAN -> "Duke ngarkuar oraret..."
                                else -> "Loading departures..."
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                items(uiState.departures) { departure ->
                    val direction = departure.notes ?: departure.direction.name.lowercase()
                        .replaceFirstChar { it.uppercase() }
                    val isAirport = departure.serviceType == "airport" ||
                            direction.contains("airport", ignoreCase = true) ||
                            direction.contains("αεροδρ", ignoreCase = true)
                    DepartureCard(
                        lineName = departure.lineId,
                        lineColor = lineIdToColor(departure.lineId),
                        direction = direction,
                        minutesAway = departure.minutesAway,
                        departureTime = departure.time,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        lineId = departure.lineId,
                        sourceConfidence = departure.sourceConfidence
                            .takeIf { it != com.syrmos.core.model.schedule.SourceConfidence.UNKNOWN },
                        isAirport = isAirport,
                        airportLabel = if (isAirport) {
                            when (lang) {
                                AppLanguage.GREEK -> "Αεροδρομιο"
                                AppLanguage.ALBANIAN -> "Aeroporti"
                                else -> "Airport"
                            }
                        } else null,
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
                            else -> "Get directions"
                        })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
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

private fun lineIdToColor(lineId: String): LineColor = when {
    lineId == "M1" -> LineColor.GREEN
    lineId == "M2" -> LineColor.RED
    lineId == "M3" || lineId == "M3_AIR" -> LineColor.BLUE
    lineId.startsWith("T") -> LineColor.TRAM_ORANGE
    lineId.startsWith("P") || lineId.startsWith("A") -> LineColor.SUBURBAN_PURPLE
    else -> LineColor.BLUE
}
