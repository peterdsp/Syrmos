package com.syrmos.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.designsystem.theme.tokens.SyrmosColorTokens
import com.syrmos.core.network.STASYAnnouncement

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlertDetailScreen(
    alert: STASYAnnouncement?,
    language: AppLanguage,
    onBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(alertTitle(language)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel(language))
                    }
                },
            )
        },
    ) { padding ->
        if (alert == null) {
            Text(
                text = unavailableLabel(language),
                modifier = Modifier.padding(padding).padding(20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val severityColor = when (alert.severity) {
                "closure" -> SyrmosColorTokens.disruption
                "warning" -> SyrmosColorTokens.warning
                else -> MaterialTheme.colorScheme.primary
            }
            Text(
                text = severityLabel(alert.severity, language),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = severityColor,
                modifier = Modifier
                    .background(severityColor.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
            Text(
                text = localizedTitle(alert, language),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (alert.date.isNotBlank()) {
                Text(alert.date, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (alert.affectedLines.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    alert.affectedLines.forEach { lineId ->
                        Text(
                            text = lineId,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(999.dp),
                                )
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                        )
                    }
                }
            }
            Text(
                text = localizedSummary(alert, language).ifBlank { unavailableLabel(language) },
                style = MaterialTheme.typography.bodyLarge,
            )
            if (alert.url.isNotBlank()) {
                Button(onClick = { uriHandler.openUri(alert.url) }) {
                    Row {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(sourceLabel(language))
                    }
                }
            }
        }
    }
}

private fun localizedTitle(alert: STASYAnnouncement, language: AppLanguage): String = when (language) {
    AppLanguage.GREEK -> alert.title
    AppLanguage.ALBANIAN -> alert.titleSq.ifBlank { alert.titleEn.ifBlank { alert.title } }
    AppLanguage.ITALIAN -> alert.titleIt.ifBlank { alert.titleEn.ifBlank { alert.title } }
    else -> alert.titleEn.ifBlank { alert.title }
}

private fun localizedSummary(alert: STASYAnnouncement, language: AppLanguage): String = when (language) {
    AppLanguage.GREEK -> alert.summary
    AppLanguage.ALBANIAN -> alert.summarySq.ifBlank { alert.summaryEn.ifBlank { alert.summary } }
    AppLanguage.ITALIAN -> alert.summaryIt.ifBlank { alert.summaryEn.ifBlank { alert.summary } }
    else -> alert.summaryEn.ifBlank { alert.summary }
}

private fun alertTitle(language: AppLanguage) = when (language) {
    AppLanguage.GREEK -> "Ειδοποιηση"
    AppLanguage.ALBANIAN -> "Njoftim"
    AppLanguage.ITALIAN -> "Avviso"
    else -> "Alert"
}

private fun backLabel(language: AppLanguage) = when (language) {
    AppLanguage.GREEK -> "Πισω"
    AppLanguage.ALBANIAN -> "Prapa"
    AppLanguage.ITALIAN -> "Indietro"
    else -> "Back"
}

private fun unavailableLabel(language: AppLanguage) = when (language) {
    AppLanguage.GREEK -> "Η ειδοποιηση δεν ειναι πλεον διαθεσιμη."
    AppLanguage.ALBANIAN -> "Njoftimi nuk eshte me i disponueshem."
    AppLanguage.ITALIAN -> "L'avviso non e piu disponibile."
    else -> "This alert is no longer available."
}

private fun sourceLabel(language: AppLanguage) = when (language) {
    AppLanguage.GREEK -> "Προβολη πηγης"
    AppLanguage.ALBANIAN -> "Shiko burimin"
    AppLanguage.ITALIAN -> "Visualizza fonte"
    else -> "View source"
}

private fun severityLabel(severity: String, language: AppLanguage): String = when (severity) {
    "closure" -> when (language) {
        AppLanguage.GREEK -> "Κλεισιμο"
        AppLanguage.ALBANIAN -> "Mbyllje"
        AppLanguage.ITALIAN -> "Chiusura"
        else -> "Closure"
    }
    "warning" -> when (language) {
        AppLanguage.GREEK -> "Προσοχη"
        AppLanguage.ALBANIAN -> "Kujdes"
        AppLanguage.ITALIAN -> "Avviso"
        else -> "Warning"
    }
    else -> "Info"
}
