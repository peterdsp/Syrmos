package com.syrmos.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.rememberCoroutineScope
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.AppThemeMode
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.common.ThemeManager
import com.syrmos.core.data.sync.ScheduleSyncRepository
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalTime::class)
@Composable
fun SettingsScreen() {
    val lang by LocalizationManager.language.collectAsState()
    val themeMode by ThemeManager.theme.collectAsState()
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 76.dp, end = 16.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            SettingsSection(title = L.PREFERENCES.text(lang)) {
                Box {
                    SettingsRow(
                        title = L.LANGUAGE.text(lang),
                        value = lang.displayName,
                        onClick = { showLanguagePicker = true },
                        interactive = true,
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
                Box {
                    SettingsRow(
                        title = L.THEME.text(lang),
                        value = themeMode.localizedName(lang),
                        onClick = { showThemePicker = true },
                        interactive = true,
                    )
                    DropdownMenu(
                        expanded = showThemePicker,
                        onDismissRequest = { showThemePicker = false },
                    ) {
                        AppThemeMode.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.localizedName(lang)) },
                                onClick = {
                                    ThemeManager.setTheme(option)
                                    showThemePicker = false
                                },
                            )
                        }
                    }
                }
            }
        }

        item {
            SettingsSection(title = L.DATA.text(lang)) {
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
                        .clickable(enabled = !isRefreshing) {
                            scope.launch {
                                runCatching { scheduleSync.refresh() }
                                runCatching { linesRefresher.refresh() }
                                runCatching { stationOffsets.refresh() }
                                runCatching { fares.refresh() }
                                runCatching { visualOverrides.refresh() }
                                runCatching { announcements.refresh() }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = when (lang) {
                            AppLanguage.GREEK -> "Έλεγχος τώρα"
                            AppLanguage.ALBANIAN -> "Kontrollo tani"
                            else -> "Check now"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        value = "›",
                        onClick = { showFares = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = when (lang) {
                                AppLanguage.GREEK -> "Ανέπαφη πληρωμή"
                                AppLanguage.ALBANIAN -> "Pagesa pa kontakt"
                                else -> "Contactless payment"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = when (lang) {
                                AppLanguage.GREEK -> "Πληρώστε στις πύλες μετρό/τραμ ή μέσα σε τραμ και τρένα με Apple Pay, Google Wallet ή ανέπαφη κάρτα."
                                AppLanguage.ALBANIAN -> "Paguaj në portat e metros/tramvajit ose brenda tramvajeve dhe trenave me Apple Pay, Google Wallet ose çdo kartë pa kontakt."
                                else -> "Tap to pay at metro/tram gates and onboard trams and trains with Apple Pay, Google Wallet, or any contactless card."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = when (lang) {
                        AppLanguage.GREEK -> "Οι τιμές και η διαθεσιμότητα διαχειρίζονται από τον ΟΑΣΑ. Το Syrmos δεν αποθηκεύει τιμές, απλώς ανοίγει την επίσημη σελίδα."
                        AppLanguage.ALBANIAN -> "Çmimet dhe disponueshmëria menaxhohen nga OASA. Syrmos nuk ruan çmime, thjesht hap faqen zyrtare."
                        else -> "Prices and availability are managed by OASA. Syrmos does not store prices, it just opens the official page."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
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
    }

    com.syrmos.core.designsystem.component.CompactTabHeader(
        title = L.SETTINGS.text(lang),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .zIndex(1f),
    )
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
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
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
    interactive: Boolean = false,
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
            color = if (interactive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun AppThemeMode.localizedName(lang: AppLanguage): String = when (this) {
    AppThemeMode.SYSTEM -> when (lang) {
        AppLanguage.GREEK -> "Σύστημα"
        AppLanguage.ALBANIAN -> "Sistemi"
        else -> "System"
    }
    AppThemeMode.LIGHT -> when (lang) {
        AppLanguage.GREEK -> "Φωτεινό"
        AppLanguage.ALBANIAN -> "E ndritshme"
        else -> "Light"
    }
    AppThemeMode.DARK -> when (lang) {
        AppLanguage.GREEK -> "Σκοτεινό"
        AppLanguage.ALBANIAN -> "E errët"
        else -> "Dark"
    }
}
