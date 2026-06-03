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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.designsystem.component.toComposeColor
import com.syrmos.core.designsystem.theme.MetroBlue
import com.syrmos.core.designsystem.theme.SuburbanPurple
import com.syrmos.core.designsystem.theme.TramOrange
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.network.STASYAnnouncement

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStationClick: (String) -> Unit = {},
    onLineClick: (String) -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val lang by LocalizationManager.language.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            ScreenHeader(
                title = "Syrmos",
                subtitle = L.APP_SUBTITLE.text(lang),
            )
        }

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

        if (uiState.error != null) {
            item {
                Text(
                    text = L.COULD_NOT_REACH.text(lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (uiState.lines.isNotEmpty()) {
            item {
                SectionTitle(text = L.LINES.text(lang))
            }

            val grouped = uiState.lines.groupBy { it.type }
            listOf(LineType.METRO, LineType.TRAM, LineType.SUBURBAN).forEach { type ->
                val linesForType = grouped[type] ?: return@forEach
                items(linesForType) { line ->
                    LineCard(
                        line = line,
                        lang = lang,
                        onClick = { onLineClick(line.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenHeader(
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun NetworkOverview(
    lines: List<Line>,
    lang: AppLanguage,
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
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(12.dp),
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
    lang: AppLanguage,
    onOpenUrl: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "⚠", style = MaterialTheme.typography.titleSmall)
            SectionTitle(text = L.SERVICE_ALERTS.text(lang))
        }
        alerts.take(3).forEach { alert ->
            AlertCard(
                announcement = alert,
                isAlert = true,
                lang = lang,
                onOpenUrl = onOpenUrl,
            )
        }
    }
}

@Composable
private fun LatestNewsSection(
    announcement: STASYAnnouncement,
    lang: AppLanguage,
    onOpenUrl: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "i", style = MaterialTheme.typography.titleMedium)
            SectionTitle(text = L.LATEST_FROM_STASY.text(lang))
        }
        AlertCard(
            announcement = announcement,
            isAlert = false,
            lang = lang,
            onOpenUrl = onOpenUrl,
        )
    }
}

@Composable
private fun AlertCard(
    announcement: STASYAnnouncement,
    isAlert: Boolean,
    lang: AppLanguage,
    onOpenUrl: (String) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isAlert) Color(0xFFFFF3E0) else MaterialTheme.colorScheme.surface,
        ),
        border = if (isAlert) BorderStroke(1.dp, Color(0x33E87722)) else null,
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
    lang: AppLanguage,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(12.dp),
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
                    .clip(RoundedCornerShape(6.dp))
                    .background(line.color.toComposeColor()),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = line.localizedName(lang),
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
                text = line.stationCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "›",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            )
        }
    }
}

private fun Line.localizedName(lang: AppLanguage): String {
    return if (lang == AppLanguage.GREEK && nameEl.isNotBlank()) nameEl else name
}
