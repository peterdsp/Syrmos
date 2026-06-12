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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.runtime.rememberCoroutineScope
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.data.sync.ScheduleSyncRepository
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalTime::class)
@Composable
fun SettingsScreen() {
    val lang by LocalizationManager.language.collectAsState()
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showFares by remember { mutableStateOf(false) }
    val scheduleSync = koinInject<ScheduleSyncRepository>()
    val lastSync by scheduleSync.lastSyncAt.collectAsState()
    val offlineOnly by scheduleSync.offlineOnly.collectAsState()
    val isRefreshing by scheduleSync.isRefreshing.collectAsState()
    val scheduleVersion by scheduleSync.scheduleVersion.collectAsState()
    val scope = rememberCoroutineScope()

    // Native OASA tickets catalogue takes over the whole tab when shown.
    if (showFares) {
        FaresScreen(onBack = { showFares = false })
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text(
                text = L.SETTINGS.text(lang),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        item {
            SettingsSection(title = L.PREFERENCES.text(lang)) {
                Box {
                    SettingsRow(
                        title = L.LANGUAGE.text(lang),
                        value = lang.displayName,
                        onClick = { showLanguagePicker = true },
                    )
                    DropdownMenu(
                        expanded = showLanguagePicker,
                        onDismissRequest = { showLanguagePicker = false },
                    ) {
                        AppLanguage.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.displayName) },
                                onClick = {
                                    LocalizationManager.setLanguage(option)
                                    showLanguagePicker = false
                                },
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                SettingsRow(
                    title = L.THEME.text(lang),
                    value = L.SYSTEM_DEFAULT.text(lang),
                )
            }
        }

        item {
            SettingsSection(title = L.DATA.text(lang)) {
                SettingsRow(
                    title = L.SCHEDULE_VERSION.text(lang),
                    value = scheduleVersion?.let { "v$it" } ?: "3.0",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                SettingsRow(
                    title = L.STATIONS.text(lang),
                    value = "90+",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                SettingsRow(
                    title = L.LINES.text(lang),
                    value = "9",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                SettingsRow(
                    title = if (lang == AppLanguage.GREEK) "Τελευταία ενημέρωση" else "Last updated",
                    value = lastSync?.toString()?.replace("T", " ")?.substringBefore(".")
                        ?: if (lang == AppLanguage.GREEK) "Ποτέ" else "Never",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (lang == AppLanguage.GREEK) "Μόνο εκτός σύνδεσης" else "Offline-only mode",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = offlineOnly,
                        onCheckedChange = { scheduleSync.setOfflineOnly(it) },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        enabled = !isRefreshing && !offlineOnly,
                        onClick = { scope.launch { scheduleSync.refresh() } },
                    ) {
                        Text(if (lang == AppLanguage.GREEK) "Έλεγχος τώρα" else "Check now")
                    }
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        item {
            SettingsSection(title = if (lang == AppLanguage.GREEK) "Εισιτήρια" else "Tickets") {
                SettingsRow(
                    title = if (lang == AppLanguage.GREEK) "Τιμοκατάλογος OASA" else "Ticket prices (OASA)",
                    value = if (lang == AppLanguage.GREEK) "Άνοιγμα →" else "Open →",
                    onClick = { showFares = true },
                )
            }
        }

        item {
            SettingsSection(title = L.ABOUT.text(lang)) {
                Text(
                    text = L.ABOUT_TEXT.text(lang),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(content = { content() })
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) {
                onClick?.invoke()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
