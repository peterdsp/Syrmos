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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.designsystem.theme.tokens.SyrmosColorTokens
import com.syrmos.core.model.schedule.SourceConfidence
import com.syrmos.core.model.alerts.AlertSeverity
import com.syrmos.core.model.transit.LineColor
import org.jetbrains.compose.resources.painterResource

@Composable
fun DepartureCard(
    lineName: String,
    lineColor: LineColor,
    direction: String,
    minutesAway: Int,
    departureTime: String,
    modifier: Modifier = Modifier,
    lineId: String? = null,
    trainNo: String? = null,
    isAirport: Boolean = false,
    /** Where this departure came from; renders a source-confidence chip when set. */
    sourceConfidence: SourceConfidence? = null,
    /** Localised (EL/SQ) chip label; falls back to the chip's English default. */
    sourceLabel: String? = null,
    airportLabel: String? = null,
    language: AppLanguage = AppLanguage.ENGLISH,
    disruptionSeverity: AlertSeverity? = null,
) {
    val vehicleResource = lineId?.let { VehicleIcons.resourceFor(it, direction, isAirport) }
    // One compound accessibility label so TalkBack announces the row as a single
    // sentence (line, destination, minutes, time) instead of reading each field
    // separately. Mirrors the iOS StationDetailView `accessibilityElement(.combine)`
    // + `accessibilityLabel`. The card is non-interactive, so clearing child
    // semantics loses no role.
    val a11yLabel = when (language) {
        AppLanguage.GREEK -> "$lineName προς $direction, σε $minutesAway λεπτά, στις $departureTime"
        AppLanguage.ALBANIAN -> "$lineName drejt $direction, për $minutesAway minuta, në $departureTime"
        AppLanguage.ITALIAN -> "$lineName verso $direction, tra $minutesAway minuti, alle $departureTime"
        else -> "$lineName towards $direction, in $minutesAway minutes, at $departureTime"
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = a11yLabel },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    if (vehicleResource != null) {
                        Image(
                            painter = painterResource(vehicleResource),
                            contentDescription = "$lineName $direction",
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
                        DisruptionDot(
                            severity = disruptionSeverity,
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = lineName,
                            style = MaterialTheme.typography.titleSmall,
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
                    }
                    Text(
                        text = if (trainNo != null) "$direction  #$trainNo" else direction,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (sourceConfidence != null) {
                        if (sourceLabel != null) {
                            SourceConfidenceChip(confidence = sourceConfidence, label = sourceLabel)
                        } else {
                            SourceConfidenceChip(confidence = sourceConfidence)
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatMinutesAway(minutesAway, language),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        minutesAway <= 1 -> SyrmosColorTokens.arrivalImminent
                        minutesAway <= 2 -> SyrmosColorTokens.arrivalSoon
                        minutesAway <= 5 -> SyrmosColorTokens.arrivalModerate
                        else -> SyrmosColorTokens.arrivalFar
                    },
                )
                Text(
                    text = departureTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Countdown formatter shared across Departure/Schedule UI surfaces.
 * "Now" once the train is at the platform, "Xh Ymin" past one hour so
 * late-night views like Nikaia M3 at 02:09 show "3h 21min" instead of
 * the unreadable "201 min" the bare number used to render. */
fun formatMinutesAway(minutesAway: Int, language: AppLanguage = AppLanguage.ENGLISH): String {
    val minAbbr = when (language) {
        AppLanguage.GREEK -> "λεπ"
        AppLanguage.ALBANIAN -> "min"
        AppLanguage.ITALIAN -> "min"
        else -> "min"
    }
    val hAbbr = when (language) {
        AppLanguage.GREEK -> "ω"
        AppLanguage.ALBANIAN -> "o"
        AppLanguage.ITALIAN -> "h"
        else -> "h"
    }
    return when {
        minutesAway <= 0 -> when (language) {
            AppLanguage.GREEK -> "Τώρα"
            AppLanguage.ALBANIAN -> "Tani"
            AppLanguage.ITALIAN -> "Ora"
            else -> "Now"
        }
        minutesAway == 1 -> "1 $minAbbr"
        minutesAway < 60 -> "$minutesAway $minAbbr"
        minutesAway % 60 == 0 -> "${minutesAway / 60}$hAbbr"
        else -> "${minutesAway / 60}$hAbbr ${minutesAway % 60}$minAbbr"
    }
}
