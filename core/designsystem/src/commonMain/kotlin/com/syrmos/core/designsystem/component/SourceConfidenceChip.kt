package com.syrmos.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.dp
import com.syrmos.core.designsystem.animation.livePulse
import com.syrmos.core.designsystem.theme.tokens.SyrmosColorTokens
import com.syrmos.core.designsystem.theme.tokens.SyrmosShapeTokens
import com.syrmos.core.designsystem.theme.tokens.SyrmosTypographyTokens
import com.syrmos.core.model.schedule.SourceConfidence
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager

/**
 * Syrmos knows what it knows and says so (design doc section 7, a headline 2.0.0
 * pillar). A calm, never-alarming chip that states how certain an answer is. It
 * appears on departure cards, station detail, route results, and Ariadne answers.
 * The [SourceConfidence] catalog is shared across platforms (core:model).
 */
private fun SourceConfidence.color(): Color = when (this) {
    SourceConfidence.LIVE -> SyrmosColorTokens.live
    SourceConfidence.SCHEDULED -> SyrmosColorTokens.scheduled
    SourceConfidence.OFFLINE -> SyrmosColorTokens.offline
    SourceConfidence.ESTIMATED -> SyrmosColorTokens.estimated
    SourceConfidence.OPERATOR_LINK -> SyrmosColorTokens.brand
    SourceConfidence.UNKNOWN -> SyrmosColorTokens.offline
}

/** English default label; callers pass a localised [label] for EL / SQ. */
private fun SourceConfidence.defaultLabel(language: AppLanguage): String = when (this) {
    SourceConfidence.LIVE -> localized(language, "Live", "Ζωντανά", "Drejtpërdrejt", "In tempo reale")
    SourceConfidence.SCHEDULED -> localized(language, "Scheduled", "Προγραμματισμένο", "I planifikuar", "Programmato")
    SourceConfidence.OFFLINE -> localized(language, "Offline snapshot", "Στιγμιότυπο εκτός σύνδεσης", "Pamje pa internet", "Istantanea offline")
    SourceConfidence.ESTIMATED -> localized(language, "Estimated", "Εκτίμηση", "E vlerësuar", "Stimato")
    SourceConfidence.OPERATOR_LINK -> localized(language, "Check operator", "Έλεγχος στον φορέα", "Kontrollo operatorin", "Verifica operatore")
    SourceConfidence.UNKNOWN -> localized(language, "No live data", "Χωρίς ζωντανά δεδομένα", "Pa të dhëna direkte", "Nessun dato in tempo reale")
}

private fun localized(language: AppLanguage, en: String, el: String, sq: String, it: String): String = when (language) {
    AppLanguage.GREEK -> el
    AppLanguage.ALBANIAN -> sq
    AppLanguage.ITALIAN -> it
    else -> en
}

@Composable
fun SourceConfidenceChip(
    confidence: SourceConfidence,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val language by LocalizationManager.language.collectAsState()
    val tint = confidence.color()
    Row(
        modifier = modifier
            .clip(SyrmosShapeTokens.shapePill)
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(tint)
                .then(if (confidence == SourceConfidence.LIVE) Modifier.livePulse() else Modifier),
        )
        Text(
            text = label ?: confidence.defaultLabel(language),
            style = SyrmosTypographyTokens.label,
            color = tint,
        )
    }
}
