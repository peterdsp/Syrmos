package com.syrmos.feature.lines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.designsystem.component.LineColorIndicator
import com.syrmos.core.model.transit.LineColor
import com.syrmos.core.model.transit.Station
import org.koin.compose.koinInject

@Composable
fun BrowseAllStationsScreen(
    onStationClick: (stationId: String) -> Unit,
    onBack: () -> Unit,
) {
    val lang by LocalizationManager.language.collectAsState()
    val stationRepo = koinInject<StationRepositoryImpl>()
    val allStations by stationRepo.getAllStations().collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(allStations, searchQuery) {
        if (searchQuery.isBlank()) {
            allStations
        } else {
            val q = searchQuery.lowercase()
            allStations.filter { station ->
                station.name.lowercase().contains(q)
                    || station.nameEl.lowercase().contains(q)
                    || (station.nameSq?.lowercase()?.contains(q) == true)
                    || station.id.lowercase().contains(q)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                )
            }
            Text(
                text = when (lang) {
                    AppLanguage.GREEK -> "Ολοι οι σταθμοι"
                    AppLanguage.ALBANIAN -> "Te gjitha stacionet"
                    AppLanguage.ITALIAN -> "Tutte le stazioni"
                    else -> "All Stations"
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = when (lang) {
                                AppLanguage.GREEK -> "Αναζητηση σταθμου..."
                                AppLanguage.ALBANIAN -> "Kerko stacion..."
                                AppLanguage.ITALIAN -> "Cerca stazione..."
                                else -> "Search station..."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(20.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = "${filtered.size} " + when (lang) {
                AppLanguage.GREEK -> "σταθμοι"
                AppLanguage.ALBANIAN -> "stacione"
                AppLanguage.ITALIAN -> "stazioni"
                else -> "stations"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(filtered, key = { it.id }) { station ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStationClick(station.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (station.lineIds.isNotEmpty()) {
                            LineColorIndicator(
                                lineColor = lineColorForId(station.lineIds.first()),
                                size = 10.dp,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (lang) {
                                    AppLanguage.GREEK -> station.nameEl
                                    AppLanguage.ALBANIAN -> station.nameSq ?: station.name
                                    AppLanguage.ITALIAN -> station.name
                                    else -> station.name
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (station.lineIds.isNotEmpty()) {
                                Text(
                                    text = station.lineIds.joinToString(", "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (station.isInterchange) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "⇆",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun lineColorForId(lineId: String): LineColor = when {
    lineId.startsWith("M") -> when (lineId) {
        "M1" -> LineColor.GREEN
        "M2" -> LineColor.RED
        else -> LineColor.BLUE
    }
    lineId.startsWith("T") -> LineColor.TRAM_ORANGE
    lineId.startsWith("A") -> LineColor.SUBURBAN_PURPLE
    lineId.startsWith("DK") || lineId.startsWith("KB") -> LineColor.SCENIC_OCHRE
    else -> LineColor.SUBURBAN_PURPLE
}
