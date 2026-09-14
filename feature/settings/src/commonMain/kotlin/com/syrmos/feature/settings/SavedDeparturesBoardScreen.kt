package com.syrmos.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.common.ReminderState
import com.syrmos.core.domain.reminder.SavedDepartureRepository
import com.syrmos.core.domain.reminder.SavedDepartureStore
import kotlinx.datetime.Clock

/**
 * Phase N J09 saved-departure board: the list of departures the rider has a
 * leave-by reminder for. Each row shows the departure, when to leave, and its
 * live state (scheduled / leave now / departed), with a delete action. Reads the
 * shared [SavedDepartureRepository] and derives every label from the shared
 * leave-by engine, so it never invents its own timing.
 */
@Composable
fun SavedDeparturesBoardScreen(onBack: () -> Unit) {
    val lang by LocalizationManager.language.collectAsState()
    val departures by SavedDepartureRepository.departures.collectAsState()
    val now = Clock.System.now().epochSeconds

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel(lang))
                }
                Text(
                    text = boardTitle(lang),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (departures.isEmpty()) {
                Text(
                    text = emptyLabel(lang),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(departures, key = { it.id }) { dep ->
                    val reminder = SavedDepartureStore.toReminder(dep)
                    val state = reminder.state(now)
                    val minutes = reminder.minutesUntilLeave(now)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${dep.lineId} · ${dep.stationName}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val sub = buildString {
                                if (dep.destination.isNotBlank()) append("${toLabel(lang)} ${dep.destination}")
                                if (dep.scheduledTime.isNotBlank()) {
                                    if (isNotEmpty()) append(" · ")
                                    append(dep.scheduledTime)
                                }
                            }
                            if (sub.isNotBlank()) {
                                Text(
                                    text = sub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = stateLabel(state, minutes, lang),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = when (state) {
                                    ReminderState.LEAVE_NOW -> MaterialTheme.colorScheme.primary
                                    ReminderState.DEPARTED -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                        IconButton(onClick = { SavedDepartureRepository.remove(dep.id) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = removeLabel(lang),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun boardTitle(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Αποθηκευμένες αναχωρήσεις"
    AppLanguage.ALBANIAN -> "Nisjet e ruajtura"
    AppLanguage.ITALIAN -> "Partenze salvate"
    else -> "Saved departures"
}
private fun emptyLabel(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Καμία υπενθύμιση αναχώρησης ακόμη. Πάτησε \"Υπενθύμιση\" σε μια αναχώρηση."
    AppLanguage.ALBANIAN -> "Ende asnjë kujtues nisjeje. Prek \"Kujto\" te një nisje."
    AppLanguage.ITALIAN -> "Nessun promemoria ancora. Tocca \"Ricorda\" su una partenza."
    else -> "No leave-by reminders yet. Tap \"Remind\" on a departure."
}
private fun backLabel(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Πίσω"
    AppLanguage.ALBANIAN -> "Prapa"
    AppLanguage.ITALIAN -> "Indietro"
    else -> "Back"
}
private fun removeLabel(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Διαγραφή"
    AppLanguage.ALBANIAN -> "Fshi"
    AppLanguage.ITALIAN -> "Elimina"
    else -> "Remove"
}
private fun toLabel(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "προς"
    AppLanguage.ALBANIAN -> "drejt"
    AppLanguage.ITALIAN -> "verso"
    else -> "to"
}
private fun stateLabel(state: ReminderState, minutes: Int, lang: AppLanguage): String = when (state) {
    ReminderState.LEAVE_NOW -> when (lang) {
        AppLanguage.GREEK -> "Ώρα να φύγεις"
        AppLanguage.ALBANIAN -> "Koha për të nisur"
        AppLanguage.ITALIAN -> "Ora di partire"
        else -> "Time to leave"
    }
    ReminderState.DEPARTED -> when (lang) {
        AppLanguage.GREEK -> "Αναχώρησε"
        AppLanguage.ALBANIAN -> "U nis"
        AppLanguage.ITALIAN -> "Partito"
        else -> "Departed"
    }
    ReminderState.SCHEDULED -> when (lang) {
        AppLanguage.GREEK -> "Φύγε σε $minutes'"
        AppLanguage.ALBANIAN -> "Nisu për $minutes'"
        AppLanguage.ITALIAN -> "Parti tra $minutes'"
        else -> "Leave in $minutes min"
    }
}
