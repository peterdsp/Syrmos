package com.syrmos.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.ClosureCopy
import com.syrmos.core.common.ClosureEffect
import com.syrmos.core.common.ClosureMode
import com.syrmos.core.common.StationClosure

/** Everything the closure card shows, already localised. */
data class ClosureCardInfo(
    val chip: String,
    val headline: String,
    val window: String,
    val reason: String,
    val replacementBus: String?,
    val upcoming: Boolean,
) {
    companion object {
        /**
         * Builds the card for an active closure, or for one starting within 24 h
         * (upcoming banner). Null when nothing applies.
         */
        fun from(effect: ClosureEffect, closure: StationClosure?, now: Long, lang: AppLanguage): ClosureCardInfo? {
            val c = closure ?: return null
            if (effect.active) {
                val mode = effect.mode ?: c.mode
                return ClosureCardInfo(
                    chip = ClosureCopy.modeChip(mode, lang),
                    headline = ClosureCopy.modeHeadline(mode, lang),
                    window = ClosureCopy.window(c.startsAtEpochSeconds, effect.endsAtEpochSeconds, now, lang),
                    reason = c.reason?.text(lang).orEmpty(),
                    replacementBus = c.replacementBus?.let { ClosureCopy.replacementBus(it, lang) },
                    upcoming = false,
                )
            }
            val starts = effect.upcomingStartsAtEpochSeconds ?: return null
            return ClosureCardInfo(
                chip = ClosureCopy.upcoming(starts, now, lang),
                headline = ClosureCopy.modeHeadline(c.mode, lang),
                window = ClosureCopy.window(starts, c.endsAtEpochSeconds, now, lang),
                reason = c.reason?.text(lang).orEmpty(),
                replacementBus = c.replacementBus?.let { ClosureCopy.replacementBus(it, lang) },
                upcoming = true,
            )
        }
    }
}

private val ClosedRed = Color(0xFFD32F2F)
private val UpcomingAmber = Color(0xFFF57C00)

/**
 * The station closure card (3.0.2): status chip, what it means, the time window
 * ("08:00 to 16:00" or "until further notice"), the reason and the replacement
 * bus. The chip text never wraps (chip atomicity rule).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StationClosureCard(info: ClosureCardInfo, modifier: Modifier = Modifier) {
    val accent = if (info.upcoming) UpcomingAmber else ClosedRed
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { contentDescription = "${info.chip}. ${info.headline}. ${info.window}" },
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                ClosureChip(info.chip, accent)
                Text(
                    text = info.headline,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = info.window,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (info.reason.isNotBlank()) {
                Text(
                    text = info.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            info.replacementBus?.let {
                Text(
                    text = "🚌 $it",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** One atomic status chip ("Closed", "No stop"...): a single line, never wrapped. */
@Composable
fun ClosureChip(text: String, color: Color = ClosedRed, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Short chip label for a closure mode at a station, for lists and badges. */
fun closureBadgeLabel(mode: ClosureMode, lang: AppLanguage): String = ClosureCopy.modeChip(mode, lang)
