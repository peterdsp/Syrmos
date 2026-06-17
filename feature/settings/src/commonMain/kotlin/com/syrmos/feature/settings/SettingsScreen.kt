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
    val stationOffsets = koinInject<com.syrmos.core.data.sync.StationOffsetsRepository>()
    val fares = koinInject<com.syrmos.core.data.sync.FaresRepository>()
    val visualOverrides = koinInject<com.syrmos.core.data.sync.VisualOverridesRepository>()
    val announcements = koinInject<com.syrmos.core.data.sync.AnnouncementsRepository>()
    val linesRefresher = koinInject<com.syrmos.core.data.seed.LinesRefresher>()
    val lastSync by scheduleSync.lastSyncAt.collectAsState()
    val isRefreshing by scheduleSync.isRefreshing.collectAsState()
    val scheduleVersion by scheduleSync.scheduleVersion.collectAsState()
    var showContact by remember { mutableStateOf(false) }
    val openStasyMap = rememberStasyMapOpener()
    val scope = rememberCoroutineScope()

    // Native OASA tickets catalogue takes over the whole tab when shown.
    if (showFares) {
        FaresScreen(onBack = { showFares = false })
        return
    }

    if (showContact) {
        ContactDeveloperSheet(onDismiss = { showContact = false })
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            com.syrmos.core.designsystem.component.CompactTabHeader(
                title = L.SETTINGS.text(lang),
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
                    title = when (lang) {
                        AppLanguage.GREEK -> "Τελευταία ενημέρωση"
                        AppLanguage.ALBANIAN -> "Përditësimi i fundit"
                        else -> "Last updated"
                    },
                    value = lastSync?.toString()?.replace("T", " ")?.substringBefore(".")
                        ?: when (lang) {
                            AppLanguage.GREEK -> "Ποτέ"
                            AppLanguage.ALBANIAN -> "Asnjëherë"
                            else -> "Never"
                        },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        // Always enabled. Check now is the ONLY way the
                        // app talks to the Pi — there's no background
                        // poll, no auto-refresh on launch, no implicit
                        // network call elsewhere. Disabling this when
                        // offlineOnly is on locked the user out of the
                        // only refresh path Syrmos has.
                        enabled = !isRefreshing,
                        onClick = {
                            scope.launch {
                                // Refresh every store the app reads from
                                // the Pi. Sequential so a flaky mobile
                                // link doesn't fan out 6 simultaneous
                                // requests. Mirrors the iOS runRefresh
                                // sequence so both platforms come out of
                                // a single Check now tap with the same
                                // surface: schedules + manifest, lines,
                                // station offsets, fares, visual
                                // overrides, announcements.
                                runCatching { scheduleSync.refresh() }
                                runCatching { linesRefresher.refresh() }
                                runCatching { stationOffsets.refresh() }
                                runCatching { fares.refresh() }
                                runCatching { visualOverrides.refresh() }
                                runCatching { announcements.refresh() }
                            }
                        },
                    ) {
                        Text(when (lang) {
                            AppLanguage.GREEK -> "Έλεγχος τώρα"
                            AppLanguage.ALBANIAN -> "Kontrollo tani"
                            else -> "Check now"
                        })
                    }
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        item {
            SettingsSection(title = when (lang) {
                AppLanguage.GREEK -> "Εισιτήρια"
                AppLanguage.ALBANIAN -> "Bileta"
                else -> "Tickets"
            }) {
                SettingsRow(
                    title = when (lang) {
                        AppLanguage.GREEK -> "Τιμοκατάλογος OASA"
                        AppLanguage.ALBANIAN -> "Çmimet e biletave OASA"
                        else -> "Ticket prices (OASA)"
                    },
                    value = when (lang) {
                        AppLanguage.GREEK -> "Άνοιγμα →"
                        AppLanguage.ALBANIAN -> "Hap →"
                        else -> "Open →"
                    },
                    onClick = { showFares = true },
                )
            }
        }

        item {
            SettingsSection(title = when (lang) {
                AppLanguage.GREEK -> "Χάρτης"
                AppLanguage.ALBANIAN -> "Harta"
                else -> "Map"
            }) {
                SettingsRow(
                    title = when (lang) {
                        AppLanguage.GREEK -> "Σιδηροδρομικό δίκτυο Αθήνας"
                        AppLanguage.ALBANIAN -> "Hekurudhat e zonës metropolitane të Athinës"
                        else -> "Athens metropolitan area railways"
                    },
                    value = when (lang) {
                        AppLanguage.GREEK -> "Άνοιγμα →"
                        AppLanguage.ALBANIAN -> "Hap →"
                        else -> "Open →"
                    },
                    onClick = { openStasyMap() },
                )
            }
        }

        item {
            SettingsSection(title = when (lang) {
                AppLanguage.GREEK -> "Επικοινωνία"
                AppLanguage.ALBANIAN -> "Kontakt"
                else -> "Contact"
            }) {
                SettingsRow(
                    title = when (lang) {
                        AppLanguage.GREEK -> "Επικοινωνία με τον μηχανικό"
                        AppLanguage.ALBANIAN -> "Kontakto zhvilluesin"
                        else -> "Contact engineer"
                    },
                    value = when (lang) {
                        AppLanguage.GREEK -> "Άνοιγμα →"
                        AppLanguage.ALBANIAN -> "Hap →"
                        else -> "Open →"
                    },
                    onClick = { showContact = true },
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
