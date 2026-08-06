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
import androidx.compose.ui.platform.LocalUriHandler
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
import androidx.compose.material3.Switch
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.AppThemeMode
import com.syrmos.core.common.AriadneEngineStatus
import com.syrmos.core.common.L
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.common.MapPreferences
import com.syrmos.core.common.NotificationSettings
import com.syrmos.core.common.ThemeManager
import com.syrmos.core.data.sync.ScheduleSyncRepository
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalTime::class)
@Composable
fun SettingsScreen(
    ariadneEngine: AriadneEngineStatus? = null,
    onAriadneClick: (() -> Unit)? = null,
    onStationClick: ((stationId: String) -> Unit)? = null,
) {
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
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val showVehicles by MapPreferences.showLiveVehicles.collectAsState()
    val defaultRegion by MapPreferences.defaultRegion.collectAsState()
    var showRegionPicker by remember { mutableStateOf(false) }

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
        // Ariadne entry point
        if (onAriadneClick != null) {
            item {
                SettingsSection(title = when (lang) {
                    AppLanguage.GREEK -> "Βοηθος"
                    AppLanguage.ALBANIAN -> "Asistent"
                    AppLanguage.ITALIAN -> "Assistente"
                    else -> "Assistant"
                }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAriadneClick() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "Ariadne",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = when (lang) {
                                    AppLanguage.GREEK -> "Ο βοηθος σου στα τρενα"
                                    AppLanguage.ALBANIAN -> "Asistenti yt i trenave"
                                    AppLanguage.ITALIAN -> "Il tuo assistente ferroviario"
                                    else -> "Your rail assistant"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "›",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Preferences
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

        // Map preferences
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsSection(title = when (lang) {
                    AppLanguage.GREEK -> "Χαρτης"
                    AppLanguage.ALBANIAN -> "Harta"
                    AppLanguage.ITALIAN -> "Preferenze mappa"
                    else -> "Map preferences"
                }) {
                    NotifToggleRow(
                        title = when (lang) {
                            AppLanguage.GREEK -> "Ζωντανα οχηματα"
                            AppLanguage.ALBANIAN -> "Mjetet e gjalla"
                            AppLanguage.ITALIAN -> "Veicoli in tempo reale"
                            else -> "Live vehicles"
                        },
                        checked = showVehicles,
                        onCheckedChange = { MapPreferences.setShowLiveVehicles(it) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    Box {
                        SettingsRow(
                            title = when (lang) {
                                AppLanguage.GREEK -> "Προεπιλεγμενη περιοχη"
                                AppLanguage.ALBANIAN -> "Rajoni i parazgjedhur"
                                AppLanguage.ITALIAN -> "Regione predefinita"
                                else -> "Default region"
                            },
                            value = regionLabel(defaultRegion, lang),
                            onClick = { showRegionPicker = true },
                            interactive = true,
                        )
                        DropdownMenu(
                            expanded = showRegionPicker,
                            onDismissRequest = { showRegionPicker = false },
                        ) {
                            listOf("athens", "thessaloniki", "patras", "national").forEach { region ->
                                DropdownMenuItem(
                                    text = { Text(regionLabel(region, lang)) },
                                    onClick = {
                                        MapPreferences.setDefaultRegion(region)
                                        showRegionPicker = false
                                    },
                                )
                            }
                        }
                    }
                }
                Text(
                    text = when (lang) {
                        AppLanguage.GREEK -> "Τα ζωντανα οχηματα εμφανιζονται σαν κινουμενα τριγωνα στον χαρτη."
                        AppLanguage.ALBANIAN -> "Mjetet e gjalla shfaqen si trekendsha levizes ne harte."
                        AppLanguage.ITALIAN -> "I veicoli in tempo reale appaiono come triangoli in movimento sulla mappa."
                        else -> "Live vehicles appear as moving triangles on the map."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        // Operators
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsSection(title = when (lang) {
                    AppLanguage.GREEK -> "Διαχειριστες"
                    AppLanguage.ALBANIAN -> "Operatoret"
                    AppLanguage.ITALIAN -> "Operatori"
                    else -> "Operators"
                }) {
                    OperatorRow(
                        name = "STASY",
                        detail = when (lang) {
                            AppLanguage.GREEK -> "Μετρο & Τραμ Αθηνας"
                            AppLanguage.ALBANIAN -> "Metro & Tramvaj Athine"
                            AppLanguage.ITALIAN -> "Metro e Tram di Atene"
                            else -> "Athens Metro & Tram"
                        },
                        onClick = { uriHandler.openUri("https://www.stasy.gr") },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    OperatorRow(
                        name = "OASA",
                        detail = when (lang) {
                            AppLanguage.GREEK -> "Αστικες συγκοινωνιες Αθηνας"
                            AppLanguage.ALBANIAN -> "Transporti publik Athine"
                            AppLanguage.ITALIAN -> "Trasporto pubblico di Atene"
                            else -> "Athens public transport"
                        },
                        onClick = { uriHandler.openUri("https://www.oasa.gr") },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    OperatorRow(
                        name = "Hellenic Train",
                        detail = when (lang) {
                            AppLanguage.GREEK -> "Προαστιακος & Υπεραστικα"
                            AppLanguage.ALBANIAN -> "Periferike & Nderqytetese"
                            AppLanguage.ITALIAN -> "Suburbano e Intercity"
                            else -> "Suburban & Intercity"
                        },
                        onClick = { uriHandler.openUri("https://www.hellenictrain.gr") },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    OperatorRow(
                        name = "OSETH",
                        detail = when (lang) {
                            AppLanguage.GREEK -> "Μετρο Θεσσαλονικης"
                            AppLanguage.ALBANIAN -> "Metro Selanik"
                            AppLanguage.ITALIAN -> "Metro di Salonicco"
                            else -> "Thessaloniki Metro"
                        },
                        onClick = { uriHandler.openUri("https://www.oseth.gr") },
                    )
                }
                Text(
                    text = when (lang) {
                        AppLanguage.GREEK -> "Οι τιμες και τα δρομολογια διαχειριζονται απο τους αντιστοιχους φορεις."
                        AppLanguage.ALBANIAN -> "Cmimet dhe oraret menaxhohen nga operatoret perkates."
                        AppLanguage.ITALIAN -> "Tariffe e orari sono gestiti dai rispettivi operatori."
                        else -> "Fares and schedules are managed by their respective operators."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        // Notifications
        item {
            val serviceAlertsOn by NotificationSettings.serviceAlerts.collectAsState()
            val weatherAlertsOn by NotificationSettings.weatherAlerts.collectAsState()
            val nearbyAlertsOn by NotificationSettings.nearbyAlerts.collectAsState()
            val morningDigestOn by NotificationSettings.morningDigest.collectAsState()

            SettingsSection(title = when (lang) {
                AppLanguage.GREEK -> "Ειδοποιησεις"
                AppLanguage.ALBANIAN -> "Njoftimet"
                AppLanguage.ITALIAN -> "Notifiche"
                else -> "Notifications"
            }) {
                NotifToggleRow(
                    title = when (lang) {
                        AppLanguage.GREEK -> "Ειδοποιησεις υπηρεσιας"
                        AppLanguage.ALBANIAN -> "Njoftimet e sherbimit"
                        AppLanguage.ITALIAN -> "Avvisi di servizio"
                        else -> "Service alerts"
                    },
                    checked = serviceAlertsOn,
                    onCheckedChange = { NotificationSettings.setServiceAlerts(it) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                NotifToggleRow(
                    title = when (lang) {
                        AppLanguage.GREEK -> "Καιρικες ειδοποιησεις"
                        AppLanguage.ALBANIAN -> "Njoftimet e motit"
                        AppLanguage.ITALIAN -> "Avvisi meteo"
                        else -> "Weather alerts"
                    },
                    checked = weatherAlertsOn,
                    onCheckedChange = { NotificationSettings.setWeatherAlerts(it) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                NotifToggleRow(
                    title = when (lang) {
                        AppLanguage.GREEK -> "Ειδοποιησεις κοντινου σταθμου"
                        AppLanguage.ALBANIAN -> "Njoftimet e stacionit te afert"
                        AppLanguage.ITALIAN -> "Avvisi stazione vicina"
                        else -> "Nearby station alerts"
                    },
                    checked = nearbyAlertsOn,
                    onCheckedChange = { NotificationSettings.setNearbyAlerts(it) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                NotifToggleRow(
                    title = when (lang) {
                        AppLanguage.GREEK -> "Πρωινη ενημερωση (07:00)"
                        AppLanguage.ALBANIAN -> "Perditesimi i mengjesit (07:00)"
                        AppLanguage.ITALIAN -> "Riepilogo mattutino (07:00)"
                        else -> "Morning digest (07:00)"
                    },
                    checked = morningDigestOn,
                    onCheckedChange = { NotificationSettings.setMorningDigest(it) },
                )
            }
        }

        // Data
        item {
            SettingsSection(title = L.DATA.text(lang)) {
                SettingsRow(
                    title = L.STATIONS.text(lang),
                    value = "380+",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                SettingsRow(
                    title = L.LINES.text(lang),
                    value = "31",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                SettingsRow(
                    title = when (lang) {
                        AppLanguage.GREEK -> "Τελευταια ενημερωση"
                        AppLanguage.ALBANIAN -> "Perditesimi i fundit"
                        AppLanguage.ITALIAN -> "Ultimo aggiornamento"
                        else -> "Last updated"
                    },
                    value = lastSync?.toString()?.replace("T", " ")?.substringBefore(".")
                        ?: when (lang) {
                            AppLanguage.GREEK -> "Ποτε"
                            AppLanguage.ALBANIAN -> "Asnjehere"
                            AppLanguage.ITALIAN -> "Mai"
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
                            AppLanguage.GREEK -> "Ελεγχος τωρα"
                            AppLanguage.ALBANIAN -> "Kontrollo tani"
                            AppLanguage.ITALIAN -> "Controlla ora"
                            else -> "Check now"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // Tickets
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsSection(title = when (lang) {
                    AppLanguage.GREEK -> "Εισιτηρια"
                    AppLanguage.ALBANIAN -> "Bileta"
                    AppLanguage.ITALIAN -> "Biglietti"
                    else -> "Tickets"
                }) {
                    SettingsRow(
                        title = when (lang) {
                            AppLanguage.GREEK -> "Τιμοκαταλογος εισιτηριων"
                            AppLanguage.ALBANIAN -> "Cmimet e biletave"
                            AppLanguage.ITALIAN -> "Prezzi dei biglietti"
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
                                AppLanguage.GREEK -> "Ανεπαφη πληρωμη"
                                AppLanguage.ALBANIAN -> "Pagesa pa kontakt"
                                AppLanguage.ITALIAN -> "Pagamento contactless"
                                else -> "Contactless payment"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = when (lang) {
                                AppLanguage.GREEK -> "Πληρωστε στις πυλες μετρο/τραμ η μεσα σε τραμ και τρενα με Apple Pay, Google Wallet η ανεπαφη καρτα."
                                AppLanguage.ALBANIAN -> "Paguaj ne portat e metros/tramvajit ose brenda tramvajeve dhe trenave me Apple Pay, Google Wallet ose cdo karte pa kontakt."
                                AppLanguage.ITALIAN -> "Paga ai tornelli metro/tram e a bordo di tram e treni con Apple Pay, Google Wallet o qualsiasi carta contactless."
                                else -> "Tap to pay at metro/tram gates and onboard trams and trains with Apple Pay, Google Wallet, or any contactless card."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = when (lang) {
                        AppLanguage.GREEK -> "Οι τιμες διαχειριζονται απο OASA, STASY και Hellenic Train. Το Syrmos εμφανιζει τις επισημες τιμες."
                        AppLanguage.ALBANIAN -> "Cmimet menaxhohen nga OASA, STASY dhe Hellenic Train. Syrmos shfaq cmimet zyrtare."
                        AppLanguage.ITALIAN -> "I prezzi sono gestiti da OASA, STASY e Hellenic Train. Syrmos mostra i prezzi ufficiali."
                        else -> "Prices are managed by OASA, STASY and Hellenic Train. Syrmos displays the official prices."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        // About
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

        // Ariadne engine status
        ariadneEngine?.let { engine ->
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsSection(title = when (lang) {
                        AppLanguage.GREEK -> "Μηχανη Ariadne"
                        AppLanguage.ALBANIAN -> "Motori i Ariadne"
                        AppLanguage.ITALIAN -> "Motore Ariadne"
                        else -> "Ariadne engine"
                    }) {
                        SettingsRow(
                            title = when (lang) {
                                AppLanguage.GREEK -> "Μηχανη"
                                AppLanguage.ALBANIAN -> "Motori"
                                AppLanguage.ITALIAN -> "Motore"
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

        // Contact
        item {
            SettingsSection(title = when (lang) {
                AppLanguage.GREEK -> "Επικοινωνια"
                AppLanguage.ALBANIAN -> "Kontakt"
                AppLanguage.ITALIAN -> "Contatto"
                else -> "Contact"
            }) {
                SettingsRow(
                    title = when (lang) {
                        AppLanguage.GREEK -> "Επικοινωνια με τον μηχανικο"
                        AppLanguage.ALBANIAN -> "Kontakto zhvilluesin"
                        AppLanguage.ITALIAN -> "Contatta lo sviluppatore"
                        else -> "Contact engineer"
                    },
                    value = when (lang) {
                        AppLanguage.GREEK -> "Ανοιγμα >"
                        AppLanguage.ALBANIAN -> "Hap >"
                        AppLanguage.ITALIAN -> "Apri >"
                        else -> "Open >"
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
private fun OperatorRow(
    name: String,
    detail: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "↗",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun regionLabel(region: String, lang: AppLanguage): String = when (region) {
    "athens" -> when (lang) {
        AppLanguage.GREEK -> "Αθηνα"
        AppLanguage.ALBANIAN -> "Athine"
        AppLanguage.ITALIAN -> "Atene"
        else -> "Athens"
    }
    "thessaloniki" -> when (lang) {
        AppLanguage.GREEK -> "Θεσσαλονικη"
        AppLanguage.ALBANIAN -> "Selanik"
        AppLanguage.ITALIAN -> "Salonicco"
        else -> "Thessaloniki"
    }
    "patras" -> when (lang) {
        AppLanguage.GREEK -> "Πατρα"
        AppLanguage.ALBANIAN -> "Patra"
        AppLanguage.ITALIAN -> "Patrasso"
        else -> "Patras"
    }
    "national" -> when (lang) {
        AppLanguage.GREEK -> "Ολη η Ελλαδα"
        AppLanguage.ALBANIAN -> "E gjithe Greqia"
        AppLanguage.ITALIAN -> "Tutta la Grecia"
        else -> "All Greece"
    }
    else -> region
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

@Composable
private fun NotifToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

private fun AriadneEngineStatus.engineLabel(lang: AppLanguage): String = if (isSmart) {
    when (lang) {
        AppLanguage.GREEK -> "Εξυπνη λειτουργια"
        AppLanguage.ALBANIAN -> "Modaliteti i zgjuar"
        AppLanguage.ITALIAN -> "Modalita intelligente"
        else -> "Clever mode"
    }
} else {
    when (lang) {
        AppLanguage.GREEK -> "Αναλυτης κανονων"
        AppLanguage.ALBANIAN -> "Analizues rregullash"
        AppLanguage.ITALIAN -> "Analizzatore di regole"
        else -> "Rule parser"
    }
}

private fun AriadneEngineStatus.engineDetail(lang: AppLanguage): String = when (this) {
    AriadneEngineStatus.AVAILABLE -> when (lang) {
        AppLanguage.GREEK -> "Το Gemini Nano διορθωνει την ερωτηση σας πριν την αναλυση, εξ ολοκληρου στη συσκευη."
        AppLanguage.ALBANIAN -> "Gemini Nano rregullon pyetjen tuaj para analizes, plotesisht ne pajisje."
        AppLanguage.ITALIAN -> "Gemini Nano corregge la tua domanda prima dell'analisi, interamente sul dispositivo."
        else -> "Gemini Nano cleans up your question before parsing, fully on device."
    }
    AriadneEngineStatus.MODEL_NOT_DOWNLOADED -> when (lang) {
        AppLanguage.GREEK -> "Το μοντελο στη συσκευη δεν εχει κατεβει ακομη. Το Syrmos χρησιμοποιει τον αναλυτη κανονων."
        AppLanguage.ALBANIAN -> "Modeli ne pajisje nuk eshte shkarkuar ende. Syrmos perdor analizuesin e rregullave."
        AppLanguage.ITALIAN -> "Il modello sul dispositivo non e ancora stato scaricato. Syrmos usa l'analizzatore di regole."
        else -> "The on-device model isn't downloaded yet. Syrmos uses the rule parser."
    }
    AriadneEngineStatus.AICORE_MISSING -> when (lang) {
        AppLanguage.GREEK -> "Το AICore δεν υπαρχει σε αυτη τη συσκευη. Το Syrmos χρησιμοποιει τον αναλυτη κανονων."
        AppLanguage.ALBANIAN -> "AICore mungon ne kete pajisje. Syrmos perdor analizuesin e rregullave."
        AppLanguage.ITALIAN -> "AICore non e presente su questo dispositivo. Syrmos usa l'analizzatore di regole."
        else -> "AICore isn't present on this device. Syrmos uses the rule parser."
    }
    AriadneEngineStatus.DEVICE_NOT_ELIGIBLE -> when (lang) {
        AppLanguage.GREEK -> "Αυτη η συσκευη δεν υποστηριζει μοντελο στη συσκευη. Το Syrmos χρησιμοποιει τον αναλυτη κανονων."
        AppLanguage.ALBANIAN -> "Kjo pajisje nuk mbeshtet model ne pajisje. Syrmos perdor analizuesin e rregullave."
        AppLanguage.ITALIAN -> "Questo dispositivo non supporta un modello sul dispositivo. Syrmos usa l'analizzatore di regole."
        else -> "This device can't run an on-device model. Syrmos uses the rule parser."
    }
    AriadneEngineStatus.RULE_PARSER -> when (lang) {
        AppLanguage.GREEK -> "Το Syrmos χρησιμοποιει τον ντετερμινιστικο αναλυτη κανονων, εξ ολοκληρου εκτος συνδεσης."
        AppLanguage.ALBANIAN -> "Syrmos perdor analizuesin determinist te rregullave, plotesisht jashte linje."
        AppLanguage.ITALIAN -> "Syrmos usa l'analizzatore deterministico di regole, completamente offline."
        else -> "Syrmos uses the deterministic rule parser, fully offline."
    }
}

private fun AppThemeMode.localizedName(lang: AppLanguage): String = when (this) {
    AppThemeMode.SYSTEM -> when (lang) {
        AppLanguage.GREEK -> "Συστημα"
        AppLanguage.ALBANIAN -> "Sistemi"
        AppLanguage.ITALIAN -> "Sistema"
        else -> "System"
    }
    AppThemeMode.LIGHT -> when (lang) {
        AppLanguage.GREEK -> "Φωτεινο"
        AppLanguage.ALBANIAN -> "E ndritshme"
        AppLanguage.ITALIAN -> "Chiaro"
        else -> "Light"
    }
    AppThemeMode.DARK -> when (lang) {
        AppLanguage.GREEK -> "Σκοτεινο"
        AppLanguage.ALBANIAN -> "E erret"
        AppLanguage.ITALIAN -> "Scuro"
        else -> "Dark"
    }
}
