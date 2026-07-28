package com.syrmos.feature.lines

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.designsystem.component.LineColorIndicator
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.model.transit.Region

@Composable
fun LinesScreen(
    viewModel: LinesViewModel,
    onLineClick: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val lang by LocalizationManager.language.collectAsState()

    val filtered = uiState.lines.filter { line ->
        val matchesRegion = uiState.selectedRegion == null || line.region == uiState.selectedRegion
        val matchesType = uiState.selectedType == null || line.type == uiState.selectedType
        val matchesSearch = uiState.searchQuery.isBlank() || run {
            val q = uiState.searchQuery.lowercase()
            line.name.lowercase().contains(q) ||
                line.nameEl.lowercase().contains(q) ||
                line.terminalA.lowercase().contains(q) ||
                line.terminalB.lowercase().contains(q) ||
                line.id.lowercase().contains(q)
        }
        matchesRegion && matchesType && matchesSearch
    }

    val grouped = filtered.groupBy { it.type }
    val orderedTypes = listOf(LineType.METRO, LineType.TRAM, LineType.SUBURBAN, LineType.BUS)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 76.dp, end = 16.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchBar(
                        query = uiState.searchQuery,
                        onQueryChange = viewModel::onSearchQueryChanged,
                        lang = lang,
                    )
                    RegionFilterRow(
                        selectedRegion = uiState.selectedRegion,
                        onRegionSelected = viewModel::onRegionSelected,
                        lang = lang,
                    )
                    TypeFilterRow(
                        selectedType = uiState.selectedType,
                        onTypeSelected = viewModel::onTypeSelected,
                        lang = lang,
                    )
                }
            }

            if (filtered.isEmpty() && !uiState.isLoading) {
                item {
                    Text(
                        text = when (lang) {
                            AppLanguage.GREEK -> "Δεν βρεθηκαν γραμμες"
                            AppLanguage.ALBANIAN -> "Nuk u gjeten linja"
                            else -> "No lines found"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp),
                    )
                }
            }

            orderedTypes.forEach { type ->
                val linesForType = grouped[type] ?: return@forEach

                item {
                    Text(
                        text = type.localizedName(lang).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                        tonalElevation = 1.dp,
                        shadowElevation = 2.dp,
                    ) {
                        Column {
                            linesForType.forEachIndexed { index, line ->
                                LineRow(
                                    line = line,
                                    lang = lang,
                                    onClick = { onLineClick(line.id) },
                                )
                                if (index < linesForType.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 40.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        com.syrmos.core.designsystem.component.CompactTabHeader(
            title = L.EXPLORE.text(lang),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(1f),
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    lang: AppLanguage,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )

            Box(modifier = Modifier.weight(1f).padding(vertical = 10.dp)) {
                if (query.isEmpty()) {
                    Text(
                        text = when (lang) {
                            AppLanguage.GREEK -> "Αναζητηση γραμμης η σταθμου..."
                            AppLanguage.ALBANIAN -> "Kerko linje ose stacion..."
                            else -> "Search line or station..."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RegionFilterRow(
    selectedRegion: Region?,
    onRegionSelected: (Region?) -> Unit,
    lang: AppLanguage,
) {
    val regions = listOf(
        null to when (lang) {
            AppLanguage.GREEK -> "Ολα"
            AppLanguage.ALBANIAN -> "Te gjitha"
            else -> "All"
        },
        Region.ATHENS to when (lang) {
            AppLanguage.GREEK -> "Αθηνα"
            AppLanguage.ALBANIAN -> "Athine"
            else -> "Athens"
        },
        Region.THESSALONIKI to when (lang) {
            AppLanguage.GREEK -> "Θεσσαλονικη"
            AppLanguage.ALBANIAN -> "Selanik"
            else -> "Thessaloniki"
        },
        Region.PATRAS to when (lang) {
            AppLanguage.GREEK -> "Πατρα"
            AppLanguage.ALBANIAN -> "Patra"
            else -> "Patras"
        },
        Region.NATIONAL to when (lang) {
            AppLanguage.GREEK -> "Υπεραστικα"
            AppLanguage.ALBANIAN -> "Nderqytetese"
            else -> "Intercity"
        },
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        regions.forEach { (region, label) ->
            FilterChip(
                selected = selectedRegion == region,
                onClick = { onRegionSelected(region) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun TypeFilterRow(
    selectedType: LineType?,
    onTypeSelected: (LineType?) -> Unit,
    lang: AppLanguage,
) {
    val types = listOf(
        null to when (lang) {
            AppLanguage.GREEK -> "Ολα"
            AppLanguage.ALBANIAN -> "Te gjitha"
            else -> "All"
        },
        LineType.METRO to when (lang) {
            AppLanguage.GREEK -> "Μετρο"
            AppLanguage.ALBANIAN -> "Metro"
            else -> "Metro"
        },
        LineType.TRAM to when (lang) {
            AppLanguage.GREEK -> "Τραμ"
            AppLanguage.ALBANIAN -> "Tramvaj"
            else -> "Tram"
        },
        LineType.SUBURBAN to when (lang) {
            AppLanguage.GREEK -> "Προαστιακος"
            AppLanguage.ALBANIAN -> "Periferike"
            else -> "Suburban"
        },
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        types.forEach { (type, label) ->
            FilterChip(
                selected = selectedType == type,
                onClick = { onTypeSelected(type) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun LineRow(
    line: Line,
    lang: AppLanguage,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineColorIndicator(lineColor = line.color, size = 12.dp)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = line.localizedName(lang),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${line.terminalA} - ${line.terminalB}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = when (lang) {
                AppLanguage.GREEK -> "${line.stationCount} σταθμοι"
                AppLanguage.ALBANIAN -> "${line.stationCount} stacione"
                else -> "${line.stationCount} stations"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Line.localizedName(lang: AppLanguage): String {
    return if (lang == AppLanguage.GREEK && nameEl.isNotBlank()) nameEl else name
}

private fun LineType.localizedName(lang: AppLanguage): String {
    return when (this) {
        LineType.METRO -> when (lang) {
            AppLanguage.GREEK -> "Μετρο"
            AppLanguage.ALBANIAN -> "Metro"
            else -> "Metro"
        }
        LineType.TRAM -> when (lang) {
            AppLanguage.GREEK -> "Τραμ"
            AppLanguage.ALBANIAN -> "Tramvaj"
            else -> "Tram"
        }
        LineType.SUBURBAN -> when (lang) {
            AppLanguage.GREEK -> "Προαστιακος Σιδηροδρομος"
            AppLanguage.ALBANIAN -> "Hekurudha periferike"
            else -> "Suburban Railway"
        }
        LineType.BUS -> when (lang) {
            AppLanguage.GREEK -> "Λεωφορειο (αντικατασταση)"
            AppLanguage.ALBANIAN -> "Autobus (zevendesim)"
            else -> "Bus (rail replacement)"
        }
    }
}
