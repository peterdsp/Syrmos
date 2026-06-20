package com.syrmos.feature.lines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.designsystem.component.LineColorIndicator
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineType

@Composable
fun LinesScreen(
    viewModel: LinesViewModel,
    onLineClick: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val lang by LocalizationManager.language.collectAsState()

    val grouped = uiState.lines.groupBy { it.type }
    val orderedTypes = listOf(LineType.METRO, LineType.TRAM, LineType.SUBURBAN)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 76.dp, end = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        orderedTypes.forEach { type ->
            val linesForType = grouped[type] ?: return@forEach

            item {
                Text(
                    text = type.localizedName(lang).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        title = L.LINES.text(lang),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .zIndex(1f),
    )
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
                AppLanguage.GREEK -> "${line.stationCount} σταθμοί"
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
            AppLanguage.GREEK -> "Μετρό"
            AppLanguage.ALBANIAN -> "Metro"
            else -> "Metro"
        }
        LineType.TRAM -> when (lang) {
            AppLanguage.GREEK -> "Τραμ"
            AppLanguage.ALBANIAN -> "Tramvaj"
            else -> "Tram"
        }
        LineType.SUBURBAN -> when (lang) {
            AppLanguage.GREEK -> "Προαστιακός Σιδηρόδρομος"
            AppLanguage.ALBANIAN -> "Hekurudha periferike"
            else -> "Suburban Railway"
        }
    }
}
