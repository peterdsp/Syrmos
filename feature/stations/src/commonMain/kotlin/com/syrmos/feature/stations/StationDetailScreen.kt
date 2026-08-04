package com.syrmos.feature.stations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.designsystem.component.DepartureCard
import com.syrmos.core.designsystem.component.LineColorIndicator
import com.syrmos.core.designsystem.component.SectionHeader
import com.syrmos.core.model.transit.LineColor

@OptIn(ExperimentalMaterial3Api::class)
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
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    }
                }
            }

            if (uiState.departures.isNotEmpty()) {
                item {
                    SectionHeader(title = when (lang) {
                        AppLanguage.GREEK -> "Επομενες αναχωρησεις"
                        AppLanguage.ALBANIAN -> "Nisjet e ardhshme"
                        else -> "Next departures"
                    })
                }
                items(uiState.departures) { departure ->
                    val direction = departure.notes ?: departure.direction.name.lowercase()
                        .replaceFirstChar { it.uppercase() }
                    DepartureCard(
                        lineName = departure.lineId,
                        lineColor = lineIdToColor(departure.lineId),
                        direction = direction,
                        minutesAway = departure.minutesAway,
                        departureTime = departure.time,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        lineId = departure.lineId,
                        // Source-confidence chip (live / scheduled / estimated /
                        // offline). Hidden for UNKNOWN so it never adds noise.
                        sourceConfidence = departure.sourceConfidence
                            .takeIf { it != com.syrmos.core.model.schedule.SourceConfidence.UNKNOWN },
                        // serviceType=="airport" covers both outbound (terminus
                        // "Airport") and inbound (terminus "Dimotiko Theatro"
                        // but originated at the Airport). Fall back to direction
                        // text for offline/bundled-seed paths that don't carry
                        // the API field.
                        isAirport = departure.serviceType == "airport" ||
                                direction.contains("airport", ignoreCase = true) ||
                                direction.contains("αεροδρ", ignoreCase = true),
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

private fun lineIdToColor(lineId: String): LineColor = when {
    lineId == "M1" -> LineColor.GREEN
    lineId == "M2" -> LineColor.RED
    lineId == "M3" -> LineColor.BLUE
    lineId.startsWith("T") -> LineColor.TRAM_ORANGE
    lineId.startsWith("P") -> LineColor.SUBURBAN_PURPLE
    else -> LineColor.BLUE
}
