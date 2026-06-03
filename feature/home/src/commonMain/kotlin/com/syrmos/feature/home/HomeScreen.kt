package com.syrmos.feature.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.designsystem.component.toComposeColor
import com.syrmos.core.designsystem.theme.MetroBlue
import com.syrmos.core.designsystem.theme.SuburbanPurple
import com.syrmos.core.designsystem.theme.TramOrange
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.network.STASYAnnouncement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStationClick: (String) -> Unit = {},
    onLineClick: (String) -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val lang by LocalizationManager.language.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Syrmos",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = L.APP_SUBTITLE.text(lang),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                NetworkOverview(lines = uiState.lines, lang = lang)
            }

            val alerts = uiState.announcements.filter { it.isServiceAlert }
            if (alerts.isNotEmpty()) {
                item {
                    AlertsSection(
                        alerts = alerts,
                        lang = lang,
                        onOpenUrl = onOpenUrl,
                    )
                }
            } else if (uiState.announcements.isNotEmpty()) {
                item {
                    LatestNewsSection(
                        announcement = uiState.announcements.first(),
                        lang = lang,
                        onOpenUrl = onOpenUrl,
                    )
                }
            }

            if (uiState.lines.isNotEmpty()) {
                item {
                    Text(
                        text = L.LINES.text(lang),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                val grouped = uiState.lines.groupBy { it.type }
                val orderedTypes = listOf(LineType.METRO, LineType.TRAM, LineType.SUBURBAN)
                for (type in orderedTypes) {
                    val linesForType = grouped[type] ?: continue
                    items(linesForType) { line ->
                        LineCard(
                            line = line,
                            onClick = { onLineClick(line.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkOverview(
    lines: List<Line>,
    lang: com.syrmos.core.common.AppLanguage,
) {
    val metroCount = lines.count { it.type == LineType.METRO }
    val tramCount = lines.count { it.type == LineType.TRAM }
    val suburbanCount = lines.count { it.type == LineType.SUBURBAN }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(
            value = if (metroCount > 0) metroCount.toString() else "3",
            label = L.METRO.text(lang),
            color = MetroBlue,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = if (tramCount > 0) tramCount.toString() else "2",
            label = L.TRAM.text(lang),
            color = TramOrange,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = if (suburbanCount > 0) suburbanCount.toString() else "4",
            label = L.SUBURBAN.text(lang),
            color = SuburbanPurple,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AlertsSection(
    alerts: List<STASYAnnouncement>,
    lang: com.syrmos.core.common.AppLanguage,
    onOpenUrl: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = "⚠", style = MaterialTheme.typography.titleSmall)
            Text(
                text = L.SERVICE_ALERTS.text(lang),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        alerts.take(3).forEach { alert ->
            AlertCard(announcement = alert, isAlert = true, lang = lang, onOpenUrl = onOpenUrl)
        }
    }
}

@Composable
private fun LatestNewsSection(
    announcement: STASYAnnouncement,
    lang: com.syrmos.core.common.AppLanguage,
    onOpenUrl: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = "ℹ", style = MaterialTheme.typography.titleSmall)
            Text(
                text = L.LATEST_FROM_STASY.text(lang),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        AlertCard(announcement = announcement, isAlert = false, lang = lang, onOpenUrl = onOpenUrl)
    }
}

@Composable
private fun AlertCard(
    announcement: STASYAnnouncement,
    isAlert: Boolean,
    lang: com.syrmos.core.common.AppLanguage,
    onOpenUrl: (String) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }

    val bgColor = if (isAlert) Color(0xFFFFF3E0) else MaterialTheme.colorScheme.surfaceContainerLow
    val borderColor = if (isAlert) Color(0xFFFFCC80) else Color.Transparent

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = if (isAlert) BorderStroke(1.dp, borderColor) else null,
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = announcement.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (announcement.date.isNotBlank()) {
                Text(
                    text = announcement.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = if (isExpanded) L.SHOW_LESS.text(lang) else L.SHOW_MORE.text(lang),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { isExpanded = !isExpanded },
                )
                if (announcement.url.isNotBlank()) {
                    Text(
                        text = "${L.READ_MORE.text(lang)} ↗",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onOpenUrl(announcement.url) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LineCard(
    line: Line,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(line.color.toComposeColor()),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = line.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${line.terminalA} - ${line.terminalB}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${line.stationCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "›",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}
