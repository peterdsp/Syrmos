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
import com.syrmos.core.common.AriadneEngineStatus
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.common.ThemeManager
import com.syrmos.core.data.sync.ScheduleSyncRepository
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalTime::class)
@Composable
fun SettingsScreen(ariadneEngine: AriadneEngineStatus? = null) {
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

    // Tickets catalogue takes over the whole tab when shown.
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
                            AppLanguage.GREEK -> "Τιμοκατάλογος εισιτηρίων"
                            AppLanguage.ALBANIAN -> "Çmimet e biletave"
                            else -> "Ticket prices"
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
                        AppLanguage.GREEK -> "Οι τιμές διαχειρίζονται από OASA, STASY και Hellenic Train. Το Syrmos εμφανίζει τις επίσημες τιμές."
                        AppLanguage.ALBANIAN -> "Çmimet menaxhohen nga OASA, STASY dhe Hellenic Train. Syrmos shfaq çmimet zyrtare."
                        else -> "Prices are managed by OASA, STASY and Hellenic Train. Syrmos displays the official prices."
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

        ariadneEngine?.let { engine ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsSection(title = when (lang) {
                        AppLanguage.GREEK -> "Μηχανή Ariadne"
                        AppLanguage.ALBANIAN -> "Motori i Ariadne"
                        else -> "Ariadne engine"
                    }) {
                        SettingsRow(
                            title = when (lang) {
                                AppLanguage.GREEK -> "Μηχανή"
                                AppLanguage.ALBANIAN -> "Motori"
                                else -> "Engine"
                            },
                            value = engine.engineLabel(lang),
                        )
                    }
                    Text(
                        text = engine.engineDetail(lang),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
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
        title = L.MORE_TAB.text(lang),
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

/** "Clever mode" when a model backs Ariadne, else "Rule parser". */
private fun AriadneEngineStatus.engineLabel(lang: AppLanguage): String = if (isSmart) {
    when (lang) {
        AppLanguage.GREEK -> "Έξυπνη λειτουργία"
        AppLanguage.ALBANIAN -> "Modaliteti i zgjuar"
        else -> "Clever mode"
    }
} else {
    when (lang) {
        AppLanguage.GREEK -> "Αναλυτής κανόνων"
        AppLanguage.ALBANIAN -> "Analizues rregullash"
        else -> "Rule parser"
    }
}

/** One-line reason so the user understands which engine is answering. */
private fun AriadneEngineStatus.engineDetail(lang: AppLanguage): String = when (this) {
    AriadneEngineStatus.AVAILABLE -> when (lang) {
        AppLanguage.GREEK -> "Το Gemini Nano διορθώνει την ερώτησή σας πριν την ανάλυση, εξ ολοκλήρου στη συσκευή."
        AppLanguage.ALBANIAN -> "Gemini Nano rregullon pyetjen tuaj para analizës, plotësisht në pajisje."
        else -> "Gemini Nano cleans up your question before parsing, fully on device."
    }
    AriadneEngineStatus.MODEL_NOT_DOWNLOADED -> when (lang) {
        AppLanguage.GREEK -> "Το μοντέλο στη συσκευή δεν έχει κατέβει ακόμη. Το Syrmos χρησιμοποιεί τον αναλυτή κανόνων."
        AppLanguage.ALBANIAN -> "Modeli në pajisje nuk është shkarkuar ende. Syrmos përdor analizuesin e rregullave."
        else -> "The on-device model isn't downloaded yet. Syrmos uses the rule parser."
    }
    AriadneEngineStatus.AICORE_MISSING -> when (lang) {
        AppLanguage.GREEK -> "Το AICore δεν υπάρχει σε αυτή τη συσκευή. Το Syrmos χρησιμοποιεί τον αναλυτή κανόνων."
        AppLanguage.ALBANIAN -> "AICore mungon në këtë pajisje. Syrmos përdor analizuesin e rregullave."
        else -> "AICore isn't present on this device. Syrmos uses the rule parser."
    }
    AriadneEngineStatus.DEVICE_NOT_ELIGIBLE -> when (lang) {
        AppLanguage.GREEK -> "Αυτή η συσκευή δεν υποστηρίζει μοντέλο στη συσκευή. Το Syrmos χρησιμοποιεί τον αναλυτή κανόνων."
        AppLanguage.ALBANIAN -> "Kjo pajisje nuk mbështet model në pajisje. Syrmos përdor analizuesin e rregullave."
        else -> "This device can't run an on-device model. Syrmos uses the rule parser."
    }
    AriadneEngineStatus.RULE_PARSER -> when (lang) {
        AppLanguage.GREEK -> "Το Syrmos χρησιμοποιεί τον ντετερμινιστικό αναλυτή κανόνων, εξ ολοκλήρου εκτός σύνδεσης."
        AppLanguage.ALBANIAN -> "Syrmos përdor analizuesin determinist të rregullave, plotësisht jashtë linje."
        else -> "Syrmos uses the deterministic rule parser, fully offline."
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
