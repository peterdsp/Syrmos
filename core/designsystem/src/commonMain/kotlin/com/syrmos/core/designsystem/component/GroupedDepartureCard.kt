package com.syrmos.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.designsystem.theme.tokens.SyrmosColorTokens
import com.syrmos.core.model.alerts.AlertSeverity
import com.syrmos.core.model.schedule.SourceConfidence
import com.syrmos.core.model.transit.LineColor
import org.jetbrains.compose.resources.painterResource

/**
 * A single (line -> destination) card: a destination-first heading with the line
 * badge shown once, the next few relative times (dominant) over their clock
 * times (secondary), a "+N" overflow, and one confidence chip. Replaces the
 * stack of near-identical "Line 3 · towards X · Scheduled" rows. Mirrors the web
 * grouped `.departure-card` and the iOS `GroupedDepartureRow`.
 *
 * @param times ascending (minutesAway to clock time); the first is dominant.
 */
@Composable
fun GroupedDepartureCard(
    lineName: String,
    lineColor: LineColor,
    destination: String,
    times: List<Pair<Int, String>>,
    moreCount: Int,
    modifier: Modifier = Modifier,
    lineId: String? = null,
    isAirport: Boolean = false,
    airportLabel: String? = null,
    sourceConfidence: SourceConfidence? = null,
    sourceLabel: String? = null,
    language: AppLanguage = AppLanguage.ENGLISH,
    disruptionSeverity: AlertSeverity? = null,
) {
    val vehicleResource = lineId?.let { VehicleIcons.resourceFor(it, destination, isAirport) }

    // One compound label so TalkBack reads the card as a sentence, carrying every
    // on-screen field (badge, airport, each time with its clock, the "+N", and
    // the confidence) using the same human countdown the row shows.
    val a11yLabel = buildString {
        append(lineName)
        if (isAirport && airportLabel != null) append(", $airportLabel")
        append(
            when (language) {
                AppLanguage.GREEK -> ", προς $destination"
                AppLanguage.ALBANIAN -> ", drejt $destination"
                AppLanguage.ITALIAN -> ", verso $destination"
                else -> ", towards $destination"
            },
        )
        for (timePair in times) {
            val min = timePair.first
            val clock = timePair.second
            append(", ${formatMinutesAway(min, language)}")
            if (clock.isNotEmpty()) {
                append(
                    when (language) {
                        AppLanguage.GREEK -> " στις $clock"
                        AppLanguage.ALBANIAN -> " në $clock"
                        AppLanguage.ITALIAN -> " alle $clock"
                        else -> " at $clock"
                    },
                )
            }
        }
        if (moreCount > 0) append(", +$moreCount")
        sourceConfidence?.let { append(", ${sourceLabel ?: it.defaultLabel(language)}") }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = a11yLabel },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box {
                if (vehicleResource != null) {
                    Image(
                        painter = painterResource(vehicleResource),
                        contentDescription = null,
                        modifier = Modifier.width(44.dp).height(28.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    LineColorIndicator(
                        lineColor = lineColor,
                        size = 16.dp,
                        disruptionSeverity = disruptionSeverity,
                    )
                }
                if (vehicleResource != null && disruptionSeverity != null) {
                    DisruptionDot(severity = disruptionSeverity, modifier = Modifier.align(Alignment.TopEnd))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                // Destination-first heading: colored line badge (once) + optional
                // airport pill + arrow + destination.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = lineName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(lineColor.toComposeColor())
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    if (isAirport && airportLabel != null) {
                        Text(
                            text = airportLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = SyrmosColorTokens.metroBlue,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(SyrmosColorTokens.metroBlue.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                    Text(
                        text = "→",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = destination,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                }

                // Times: dominant relative minutes over the secondary clock time.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    times.forEachIndexed { index, timePair ->
                        val min = timePair.first
                        val clock = timePair.second
                        Column {
                            Text(
                                text = formatMinutesAway(min, language),
                                style = if (index == 0) MaterialTheme.typography.titleMedium
                                else MaterialTheme.typography.bodyMedium,
                                fontWeight = if (index == 0) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (index == 0) {
                                    when {
                                        min <= 1 -> SyrmosColorTokens.arrivalImminent
                                        min <= 2 -> SyrmosColorTokens.arrivalSoon
                                        min <= 5 -> SyrmosColorTokens.arrivalModerate
                                        else -> SyrmosColorTokens.arrivalFar
                                    }
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            if (clock.isNotEmpty()) {
                                Text(
                                    text = clock,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (moreCount > 0) {
                        Text(
                            text = "+$moreCount",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (sourceConfidence != null) {
                    if (sourceLabel != null) {
                        SourceConfidenceChip(confidence = sourceConfidence, label = sourceLabel)
                    } else {
                        SourceConfidenceChip(confidence = sourceConfidence)
                    }
                }
            }
        }
    }
}
