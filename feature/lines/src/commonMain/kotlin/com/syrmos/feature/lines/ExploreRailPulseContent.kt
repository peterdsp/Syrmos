package com.syrmos.feature.lines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.RailPulseLocalStore
import com.syrmos.core.designsystem.theme.tokens.SyrmosColorTokens
import com.syrmos.core.model.transit.Station
import com.syrmos.core.network.CommunityIssue
import com.syrmos.core.network.CommunityReportService
import com.syrmos.core.network.CommunitySummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

internal data class RailPulseReportContext(
    val scopeId: String,
    val title: String,
    val subtitle: String,
)

private data class PulseFeedItem(
    val title: String,
    val detail: String,
    val status: String,
    val color: Color,
)

private enum class QuickReportSignal(
    val icon: ImageVector,
    val english: String,
) {
    NORMAL(Icons.Filled.CheckCircle, "Everything OK"),
    DELAYED(Icons.Filled.Schedule, "Delayed"),
    CROWDED(Icons.Filled.Groups, "Crowded"),
    STOPPED(Icons.Filled.PauseCircle, "Stopped"),
    TOO_HOT(Icons.Filled.DeviceThermostat, "Too hot"),
    CLEAN(Icons.Filled.CleaningServices, "Clean"),
    ACCESS(Icons.AutoMirrored.Filled.Accessible, "Access"),
    FACILITIES(Icons.Filled.Power, "Facilities"),
    SAFETY(Icons.Filled.Warning, "Safety"),
    OTHER(Icons.Filled.MoreHoriz, "Other"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExploreOriginPickerSheet(
    lang: AppLanguage,
    stations: List<Station>,
    selectedStationId: String?,
    onUseLocation: suspend () -> String?,
    onLocationSelected: (String) -> Unit,
    onStationSelected: (Station) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var isDetecting by remember { mutableStateOf(false) }
    var locationFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val filtered = remember(query, stations) {
        val normalized = query.trim()
        if (normalized.isBlank()) stations.take(80) else stations.filter { station ->
            station.name.contains(normalized, ignoreCase = true) ||
                station.nameEl.contains(normalized, ignoreCase = true) ||
                station.nameSq?.contains(normalized, ignoreCase = true) == true ||
                station.lineIds.any { it.contains(normalized, ignoreCase = true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                pulseText(lang, "Explore from", "Εξερευνηση απο", "Eksploro nga", "Esplora da"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Button(
                onClick = {
                    scope.launch {
                        isDetecting = true
                        locationFailed = false
                        val stationName = onUseLocation()
                        isDetecting = false
                        if (stationName != null) onLocationSelected(stationName) else locationFailed = true
                    }
                },
                enabled = !isDetecting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isDetecting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.MyLocation, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text(pulseText(lang, "Use my location", "Χρηση τοποθεσιας μου", "Perdor vendndodhjen time", "Usa la mia posizione"))
            }
            if (locationFailed) {
                Text(
                    pulseText(lang, "Location unavailable. Choose a station below.", "Η τοποθεσια δεν ειναι διαθεσιμη. Επιλεξε σταθμο.", "Vendndodhja nuk eshte e disponueshme. Zgjidh nje stacion.", "Posizione non disponibile. Scegli una stazione."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(pulseText(lang, "Station or line", "Σταθμος η γραμμη", "Stacion ose linje", "Stazione o linea")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 430.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(filtered.size, key = { filtered[it].id }) { index ->
                    val station = filtered[index]
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            onStationSelected(station)
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selectedStationId == station.id) SyrmosColorTokens.brand.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Filled.Train, contentDescription = null, tint = SyrmosColorTokens.brand)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    when (lang) {
                                        AppLanguage.GREEK -> station.nameEl
                                        AppLanguage.ALBANIAN -> station.nameSq ?: station.name
                                        else -> station.name
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(station.lineIds.joinToString("  "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExploreRailPulseContent(
    lang: AppLanguage,
    onReport: (RailPulseReportContext) -> Unit,
    onOpenStation: () -> Unit = {},
    onOpenTrain: () -> Unit = {},
    onSeeAll: () -> Unit = {},
    originId: String? = null,
    originName: String? = null,
    onChooseOrigin: () -> Unit = {},
) {
    val communityService = koinInject<CommunityReportService>()
    var selectedBudget by remember { mutableStateOf(30) }
    var networkSummary by remember { mutableStateOf<CommunitySummary?>(null) }
    val selectedOriginName = originName?.trim()?.takeIf { it.isNotEmpty() }
    val context = remember(lang, originId, selectedOriginName) {
        if (selectedOriginName == null) {
            RailPulseReportContext(
                scopeId = "network",
                title = pulseText(lang, "Ichnos nearby", "Ichnos κοντα σου", "Ichnos prane teje", "Ichnos vicino a te"),
                subtitle = pulseText(lang, "Choose an origin to see nearby rail reports", "Επιλεξε αφετηρια για κοντινες αναφορες", "Zgjidh nisjen per raportet prane", "Scegli una partenza per i report vicini"),
            )
        } else {
            RailPulseReportContext(
                scopeId = originId ?: stableCommunityScopeId(selectedOriginName),
                title = pulseText(lang, "Ichnos at $selectedOriginName", "Ichnos στο $selectedOriginName", "Ichnos ne $selectedOriginName", "Ichnos a $selectedOriginName"),
                subtitle = pulseText(lang, "Community rail status near your origin", "Κατασταση rail κοντα στην αφετηρια σου", "Gjendja rail prane nisjes tende", "Stato ferroviario vicino alla partenza"),
            )
        }
    }

    LaunchedEffect(Unit) {
        networkSummary = communityService.fetchSummary()
    }

    PulseRouteHero(
        lang = lang,
        context = context,
        originName = selectedOriginName,
        onChooseOrigin = onChooseOrigin,
        onReport = { onReport(context) },
    )

    Spacer(Modifier.height(10.dp))
    SectionTitle(
        title = pulseText(lang, "Ichnos across Greece", "Ichnos σε ολη την Ελλαδα", "Ichnos ne gjithe Greqine", "Ichnos in tutta la Grecia"),
        action = pulseText(lang, "History", "Ιστορικο", "Historia", "Storico"),
        onAction = onSeeAll,
    )

    val feed = remember(lang, networkSummary) { communityFeed(lang, networkSummary) }
    feed.forEachIndexed { index, item ->
        PulseFeedRow(
            item = item,
            onClick = if (index == 1) onOpenStation else onOpenTrain,
        )
    }

    Spacer(Modifier.height(10.dp))
    SectionTitle(
        title = pulseText(lang, "Explore by time", "Εξερευνηση με χρονο", "Eksploro sipas kohes", "Esplora per tempo"),
        action = if (originName.isNullOrBlank()) {
            pulseText(lang, "Choose origin", "Επιλογη αφετηριας", "Zgjidh nisjen", "Scegli partenza")
        } else {
            pulseText(lang, "From $originName", "Απο $originName", "Nga $originName", "Da $originName")
        },
        actionEndPadding = 56.dp,
        onAction = onChooseOrigin,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(30, 60, 90, 120).forEach { minutes ->
            val selected = selectedBudget == minutes
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { selectedBudget = minutes }
                    .semantics {
                        role = Role.RadioButton
                        contentDescription = if (minutes == 120) "2 hours or more" else "$minutes minutes"
                    },
                shape = RoundedCornerShape(14.dp),
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
                tonalElevation = if (selected) 0.dp else 1.dp,
            ) {
                Text(
                    text = if (minutes == 120) "2 h+" else "$minutes m",
                    modifier = Modifier.padding(vertical = 14.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF6F2DA8).copy(alpha = 0.10f),
    ) {
        Text(
            text = communityAriadneText(lang, networkSummary),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF6F2DA8),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PulseRouteHero(
    lang: AppLanguage,
    context: RailPulseReportContext,
    originName: String?,
    onChooseOrigin: () -> Unit,
    onReport: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF153D52), Color(0xFF2B6966), Color(0xFF6F2DA8)),
                    ),
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pulseText(lang, "ICHNOS NEAR YOU", "ICHNOS ΚΟΝΤΑ ΣΟΥ", "ICHNOS PRANE TEJE", "ICHNOS VICINO A TE"),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (originName == null) {
                        pulseText(lang, "Choose origin", "Επιλογη αφετηριας", "Zgjidh nisjen", "Scegli partenza")
                    } else {
                        pulseText(lang, "Selected origin", "Επιλεγμενη αφετηρια", "Nisja e zgjedhur", "Partenza selezionata")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(context.title, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                text = context.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onChooseOrigin),
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.15f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = originName ?: pulseText(lang, "Use GPS or choose a station", "Χρηση GPS η επιλογη σταθμου", "Perdor GPS ose zgjidh stacion", "Usa il GPS o scegli una stazione"),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = pulseText(lang, "Official data + community reports", "Επισημα δεδομενα + αναφορες", "Te dhena zyrtare + raporte", "Dati ufficiali + segnalazioni"),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 2,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = if (originName == null) onChooseOrigin else onReport,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF171614),
                    ),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        if (originName == null) {
                            pulseText(lang, "Choose", "Επιλογη", "Zgjidh", "Scegli")
                        } else {
                            pulseText(lang, "Report", "Αναφορα", "Raporto", "Segnala")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    action: String,
    actionEndPadding: Dp = 0.dp,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, end = actionEndPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        TextButton(onClick = onAction) {
            Text(action, style = MaterialTheme.typography.bodySmall, color = SyrmosColorTokens.brand, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PulseFeedRow(item: PulseFeedItem, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(34.dp).background(item.color.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(9.dp).background(item.color, CircleShape))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(item.status, style = MaterialTheme.typography.labelSmall, color = item.color, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RailPulseQuickReportSheet(
    context: RailPulseReportContext,
    lang: AppLanguage,
    onDismiss: () -> Unit,
) {
    val communityService = koinInject<CommunityReportService>()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val reportId = remember { communityService.newReportId() }
    var selected by remember { mutableStateOf<QuickReportSignal?>(null) }
    var crowdLevel by remember { mutableStateOf("Standing") }
    var hasRecorded by remember { mutableStateOf(false) }
    var canUndo by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var sendFailed by remember { mutableStateOf(false) }
    var wasSent by remember { mutableStateOf(false) }

    fun submit(signal: QuickReportSignal, detail: String = "") {
        selected = signal
        isSending = true
        sendFailed = false
        coroutineScope.launch {
            val receipt = communityService.submit(
                reportId = reportId,
                scopeId = context.scopeId,
                scopeLabel = context.title,
                signal = signal.name.lowercase(),
                detail = detail,
                locale = lang.code,
            )
            isSending = false
            if (receipt?.ok == true) {
                if (!hasRecorded) {
                    RailPulseLocalStore.recordContribution()
                    hasRecorded = true
                }
                wasSent = true
                canUndo = true
            } else {
                sendFailed = true
                wasSent = false
                canUndo = false
            }
        }
    }

    LaunchedEffect(selected, canUndo) {
        if (selected != null && canUndo) {
            delay(10_000)
            canUndo = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = pulseText(lang, "Quick report", "Γρηγορη αναφορα", "Raport i shpejte", "Segnalazione rapida"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Surface(shape = RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(context.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(context.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF059669).copy(alpha = 0.10f)) {
                Text(
                    text = pulseText(lang, "Tap once. Report only what you can see right now.", "Πατησε μια φορα. Αναφερε μονο ο,τι βλεπεις τωρα.", "Prek nje here. Raporto vetem ate qe sheh tani.", "Un tocco. Segnala solo cio che vedi ora."),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF047857),
                )
            }
            Text(
                text = pulseText(lang, "What is happening?", "Τι συμβαινει;", "Cfare po ndodh?", "Cosa sta succedendo?"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            QuickReportSignal.entries.chunked(3).forEach { rowSignals ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowSignals.forEach { signal ->
                        val isSelected = selected == signal
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(82.dp)
                                .clickable(enabled = !isSending) {
                                    if (signal == QuickReportSignal.CROWDED) {
                                        crowdLevel = "Standing"
                                        submit(signal, crowdLevel)
                                    } else {
                                        submit(signal)
                                    }
                                }
                                .semantics {
                                    role = Role.Button
                                    contentDescription = signal.localized(lang)
                                },
                            shape = RoundedCornerShape(18.dp),
                            color = if (isSelected) Color(0xFF6F2DA8) else MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(signal.icon, contentDescription = null, tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    signal.localized(lang),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    repeat(3 - rowSignals.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (selected == QuickReportSignal.CROWDED) {
                Text(pulseText(lang, "Crowd level", "Επιπεδο πληροτητας", "Niveli i turmes", "Livello affollamento"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Empty", "Seats", "Half", "Standing", "Packed").forEach { level ->
                        val active = crowdLevel == level
                        Surface(
                            modifier = Modifier.weight(1f).clickable(enabled = !isSending) {
                                crowdLevel = level
                                submit(QuickReportSignal.CROWDED, level)
                            },
                            shape = RoundedCornerShape(13.dp),
                            color = if (active) Color(0xFF6F2DA8) else MaterialTheme.colorScheme.surface,
                        ) {
                            Text(
                                localizedCrowdLevel(level, lang),
                                modifier = Modifier.padding(vertical = 10.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            if (isSending) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(pulseText(lang, "Sending anonymously", "Ανωνυμη αποστολη", "Po dergohet anonimisht", "Invio anonimo"))
                }
            }
            if (sendFailed) {
                Text(
                    pulseText(lang, "Could not send. Check your connection and tap the report again.", "Η αποστολη απετυχε. Ελεγξε τη συνδεση και πατησε ξανα.", "Nuk u dergua. Kontrollo lidhjen dhe provo perseri.", "Invio non riuscito. Controlla la connessione e riprova."),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (selected != null && wasSent) {
                Surface(shape = RoundedCornerShape(17.dp), color = Color(0xFF078A45)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "✓ ${pulseText(lang, "Report sent", "Η αναφορα σταλθηκε", "Raporti u dergua", "Segnalazione inviata")} · ${selected!!.localized(lang)}",
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        if (canUndo) {
                            Text(
                                text = pulseText(lang, "Undo", "Ανακληση", "Zhbëj", "Annulla"),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    coroutineScope.launch {
                                        if (communityService.delete(reportId)) {
                                            RailPulseLocalStore.undoContribution()
                                            selected = null
                                            hasRecorded = false
                                            canUndo = false
                                            wasSent = false
                                        } else {
                                            sendFailed = true
                                        }
                                    }
                                }.padding(6.dp),
                            )
                        }
                    }
                }
                Text(
                    text = pulseText(lang, "Sent anonymously to Ichnos. No account, device ID, or location is included. Active reports expire after two hours and are deleted within seven days. An anonymous daily count remains in railway history.", "Σταλθηκε ανωνυμα στο Ichnos. Δεν περιλαμβανεται λογαριασμος, αναγνωριστικο συσκευης η τοποθεσια. Οι ενεργες αναφορες ληγουν σε δυο ωρες και διαγραφονται εντος επτα ημερων. Ενα ανωνυμο ημερησιο συνολο παραμενει στο σιδηροδρομικο ιστορικο.", "U dergua anonimisht te Ichnos. Nuk perfshihet llogari, ID pajisjeje ose vendndodhje. Raportet aktive skadojne pas dy oresh dhe fshihen brenda shtate ditesh. Nje numer anonim ditor mbetet ne historine hekurudhore.", "Inviata anonimamente a Ichnos. Non vengono inclusi account, ID del dispositivo o posizione. Le segnalazioni attive scadono dopo due ore e vengono eliminate entro sette giorni. Un conteggio giornaliero anonimo resta nello storico ferroviario."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFD97706).copy(alpha = 0.12f)) {
                Text(
                    text = pulseText(lang, "For immediate danger, contact emergency services. Ichnos is not an emergency channel.", "Για αμεσο κινδυνο, επικοινωνησε με τις υπηρεσιες εκτακτης αναγκης. Το Ichnos δεν ειναι καναλι εκτακτης αναγκης.", "Per rrezik te menjehershem, kontakto sherbimet e emergjences. Ichnos nuk eshte kanal emergjence.", "Per un pericolo immediato, contatta i servizi di emergenza. Ichnos non e un canale di emergenza."),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF9A5B08),
                )
            }
        }
    }
}

private fun localizedCrowdLevel(level: String, lang: AppLanguage): String = when (level) {
    "Empty" -> pulseText(lang, "Empty", "Αδειο", "Bosh", "Vuoto")
    "Seats" -> pulseText(lang, "Seats", "Θεσεις", "Vende", "Posti")
    "Half" -> pulseText(lang, "Half", "Μετριο", "Gjysme", "Meta")
    "Packed" -> pulseText(lang, "Packed", "Γεματο", "Plot", "Pieno")
    else -> pulseText(lang, "Standing", "Ορθιοι", "Ne kembe", "In piedi")
}

private fun QuickReportSignal.localized(lang: AppLanguage): String = when (this) {
    QuickReportSignal.NORMAL -> pulseText(lang, english, "Ολα καλα", "Gjithcka ne rregull", "Tutto bene")
    QuickReportSignal.DELAYED -> pulseText(lang, english, "Καθυστερηση", "Vonese", "Ritardo")
    QuickReportSignal.CROWDED -> pulseText(lang, english, "Κοσμος", "Plot", "Affollato")
    QuickReportSignal.STOPPED -> pulseText(lang, english, "Σταματημενο", "Ndaluar", "Fermo")
    QuickReportSignal.TOO_HOT -> pulseText(lang, english, "Πολυ ζεστη", "Shume nxehte", "Troppo caldo")
    QuickReportSignal.CLEAN -> pulseText(lang, english, "Καθαρο", "Paster", "Pulito")
    QuickReportSignal.ACCESS -> pulseText(lang, english, "Προσβαση", "Akses", "Accesso")
    QuickReportSignal.FACILITIES -> pulseText(lang, english, "Παροχες", "Sherbime", "Servizi")
    QuickReportSignal.SAFETY -> pulseText(lang, english, "Ασφαλεια", "Siguri", "Sicurezza")
    QuickReportSignal.OTHER -> pulseText(lang, english, "Αλλο", "Tjeter", "Altro")
}

private fun stableCommunityScopeId(value: String): String {
    var hash = 0x811c9dc5u
    value.encodeToByteArray().forEach { byte ->
        hash = (hash xor byte.toUByte().toUInt()) * 0x01000193u
    }
    return "origin_${hash.toString(16)}"
}

private fun communityFeed(lang: AppLanguage, summary: CommunitySummary?): List<PulseFeedItem> {
    if (summary == null) {
        return listOf(
            PulseFeedItem(
                title = pulseText(lang, "Community status unavailable", "Η κατασταση κοινοτητας δεν ειναι διαθεσιμη", "Gjendja e komunitetit nuk eshte e disponueshme", "Stato della comunita non disponibile"),
                detail = pulseText(lang, "Official schedules remain available offline.", "Τα επισημα δρομολογια παραμενουν διαθεσιμα εκτος συνδεσης.", "Oraret zyrtare mbeten te disponueshme offline.", "Gli orari ufficiali restano disponibili offline."),
                status = pulseText(lang, "Offline", "Εκτος συνδεσης", "Offline", "Offline"),
                color = Color(0xFF6B7280),
            )
        )
    }
    if (!summary.hasIssues) {
        val estimate = summary.estimatedJourneysToday ?: 0
        return listOf(
            PulseFeedItem(
                title = pulseText(lang, "No community issues reported", "Δεν αναφερθηκαν προβληματα κοινοτητας", "Nuk ka probleme te raportuara", "Nessun problema segnalato"),
                detail = pulseText(lang, "$estimate estimated journeys today across the network", "$estimate εκτιμωμενες διαδρομες σημερα στο δικτυο", "$estimate udhetime te vleresuara sot ne rrjet", "$estimate viaggi stimati oggi sulla rete"),
                status = pulseText(lang, "Estimate", "Εκτιμηση", "Vleresim", "Stima"),
                color = Color(0xFF059669),
            )
        )
    }
    return summary.issues.map { issue -> issue.toFeedItem(lang) }
}

private fun CommunityIssue.toFeedItem(lang: AppLanguage): PulseFeedItem {
    val signalLabel = communitySignalLabel(signal, lang)
    val countLabel = pulseText(lang, "$count report${if (count == 1) "" else "s"}", "$count αναφορες", "$count raporte", "$count segnalazioni")
    return PulseFeedItem(
        title = scopeLabel,
        detail = listOf(signalLabel, detail.takeIf { it.isNotBlank() }, countLabel).filterNotNull().joinToString(" · "),
        status = pulseText(lang, "Active", "Ενεργο", "Aktiv", "Attivo"),
        color = when (signal) {
            "delayed", "stopped", "safety" -> Color(0xFFDC2626)
            else -> Color(0xFFD97706)
        },
    )
}

private fun communitySignalLabel(signal: String, lang: AppLanguage): String = when (signal) {
    "delayed" -> pulseText(lang, "Delay", "Καθυστερηση", "Vonese", "Ritardo")
    "crowded" -> pulseText(lang, "Crowded", "Κοσμος", "Plot", "Affollato")
    "stopped" -> pulseText(lang, "Service stopped", "Διακοπη υπηρεσιας", "Sherbimi i ndalur", "Servizio fermo")
    "too_hot" -> pulseText(lang, "Too hot", "Πολυ ζεστη", "Shume nxehte", "Troppo caldo")
    "access" -> pulseText(lang, "Accessibility issue", "Προβλημα προσβασης", "Problem aksesueshmerie", "Problema accessibilita")
    "facilities" -> pulseText(lang, "Facility issue", "Προβλημα παροχων", "Problem sherbimesh", "Problema ai servizi")
    "safety" -> pulseText(lang, "Safety issue", "Θεμα ασφαλειας", "Problem sigurie", "Problema di sicurezza")
    else -> pulseText(lang, "Other issue", "Αλλο προβλημα", "Problem tjeter", "Altro problema")
}

private fun communityAriadneText(lang: AppLanguage, summary: CommunitySummary?): String {
    if (summary == null) {
        return pulseText(lang, "Ariadne: Community status is offline. Official schedules still work.", "Ariadne: Η κοινοτικη κατασταση ειναι εκτος συνδεσης. Τα επισημα δρομολογια λειτουργουν.", "Ariadne: Gjendja e komunitetit eshte offline. Oraret zyrtare funksionojne.", "Ariadne: Lo stato della comunita e offline. Gli orari ufficiali funzionano.")
    }
    val issue = summary.issues.firstOrNull()
    if (issue != null) {
        return pulseText(lang, "Ariadne: ${issue.scopeLabel} has an active ${communitySignalLabel(issue.signal, lang).lowercase()} report.", "Ariadne: Υπαρχει ενεργη αναφορα ${communitySignalLabel(issue.signal, lang).lowercase()} στο ${issue.scopeLabel}.", "Ariadne: ${issue.scopeLabel} ka raport aktiv per ${communitySignalLabel(issue.signal, lang).lowercase()}.", "Ariadne: ${issue.scopeLabel} ha una segnalazione attiva: ${communitySignalLabel(issue.signal, lang).lowercase()}.")
    }
    return pulseText(lang, "Ariadne: No active community issues. Official alerts still take priority.", "Ariadne: Δεν υπαρχουν ενεργα κοινοτικα προβληματα. Οι επισημες ειδοποιησεις εχουν προτεραιοτητα.", "Ariadne: Nuk ka probleme aktive te komunitetit. Njoftimet zyrtare kane perparesi.", "Ariadne: Nessun problema attivo della comunita. Gli avvisi ufficiali hanno priorita.")
}

internal fun pulseText(
    lang: AppLanguage,
    english: String,
    greek: String,
    albanian: String,
    italian: String,
): String = when (lang) {
    AppLanguage.GREEK -> greek
    AppLanguage.ALBANIAN -> albanian
    AppLanguage.ITALIAN -> italian
    else -> english
}
