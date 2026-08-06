package com.syrmos.feature.lines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.RailPulseLocalStore
import com.syrmos.core.designsystem.theme.tokens.SyrmosColorTokens
import com.syrmos.core.network.CommunityReportService
import com.syrmos.core.network.CommunitySummary
import org.koin.compose.koinInject

internal enum class RailPulseDestination {
    STATION,
    TRAIN,
    CONTRIBUTION,
    FEED,
}

private fun railContributorLevel(confirmed: Int): Int = (confirmed / 100 + 1).coerceAtLeast(1)

private fun railContributorCallsign(level: Int, lang: AppLanguage): String = when (level.coerceIn(1, 10)) {
    1 -> pulseText(lang, "Platform Pal", "Φιλος Αποβαθρας", "Miku i Platformes", "Amico di Banchina")
    2 -> pulseText(lang, "Signal Spotter", "Ανιχνευτης Σηματων", "Vezhgues Sinjalesh", "Osservatore Segnali")
    3 -> pulseText(lang, "Delay Detective", "Ντετεκτιβ Καθυστερησεων", "Detektivi i Vonesave", "Detective dei Ritardi")
    4 -> pulseText(lang, "Crowd Scout", "Ανιχνευτης Κοσμου", "Vezhgues Turme", "Esploratore Folla")
    5 -> "Rail Reporter"
    6 -> pulseText(lang, "Station Guardian", "Φυλακας Σταθμου", "Mbrojtes Stacioni", "Custode di Stazione")
    7 -> pulseText(lang, "Track Whisperer", "Ψιθυριστης Γραμμων", "Peshperitesi i Shinave", "Sussurratore dei Binari")
    8 -> pulseText(lang, "Timetable Tamer", "Δαμαστης Δρομολογιων", "Zbutesi i Orareve", "Domatore di Orari")
    9 -> pulseText(lang, "Platform Legend", "Θρυλος Αποβαθρας", "Legjenda e Platformes", "Leggenda di Banchina")
    else -> pulseText(lang, "Rail Oracle", "Σιδηροδρομικο Μαντειο", "Orakulli Hekurudhor", "Oracolo Ferroviario")
}

private data class PulseCondition(
    val symbol: String,
    val label: String,
    val value: String,
    val detail: String,
    val color: Color,
)

@Composable
internal fun RailPulseStationScreen(
    lang: AppLanguage,
    onBack: () -> Unit,
    onReport: (RailPulseReportContext) -> Unit,
) {
    val communityService = koinInject<CommunityReportService>()
    val context = RailPulseReportContext(
        scopeId = "A1_AIR",
        title = pulseText(lang, "Airport", "Αεροδρομιο", "Aeroporti", "Aeroporto"),
        subtitle = pulseText(lang, "Athens International Airport · M3", "Διεθνες Αεροδρομιο Αθηνων · M3", "Aeroporti Nderkombetar i Athines · M3", "Aeroporto Internazionale di Atene · M3"),
    )
    var summary by remember { mutableStateOf<CommunitySummary?>(null) }
    LaunchedEffect(context.scopeId) {
        summary = communityService.fetchSummary(context.scopeId)
    }
    RailPulseDetailLayout(
        title = pulseText(lang, "Airport", "Αεροδρομιο", "Aeroporti", "Aeroporto"),
        subtitle = pulseText(lang, "Athens International Airport", "Διεθνες Αεροδρομιο Αθηνων", "Aeroporti Nderkombetar i Athines", "Aeroporto Internazionale di Atene"),
        onBack = onBack,
    ) {
        item {
            CommunitySummaryCard(
                lang = lang,
                title = summaryTitle(lang, summary),
                detail = summaryDetail(lang, summary),
                status = summaryStatus(lang, summary),
                onReport = { onReport(context) },
            )
        }
        communityIssueRows(lang, summary)
        item { CommunityNotice(lang) }
    }
}

@Composable
internal fun RailPulseTrainScreen(
    lang: AppLanguage,
    onBack: () -> Unit,
    onReport: (RailPulseReportContext) -> Unit,
) {
    val communityService = koinInject<CommunityReportService>()
    val context = RailPulseReportContext(
        scopeId = "train_1635",
        title = pulseText(lang, "Train 1635", "Τρενο 1635", "Treni 1635", "Treno 1635"),
        subtitle = pulseText(lang, "Athens to Kalambaka", "Αθηνα προς Καλαμπακα", "Athine per Kalambaka", "Atene verso Kalambaka"),
    )
    var summary by remember { mutableStateOf<CommunitySummary?>(null) }
    LaunchedEffect(context.scopeId) {
        summary = communityService.fetchSummary(context.scopeId)
    }
    RailPulseDetailLayout(
        title = context.title,
        subtitle = context.subtitle,
        onBack = onBack,
        headerColors = listOf(Color(0xFF213F5D), Color(0xFF55308A)),
    ) {
        item {
            CommunitySummaryCard(
                lang = lang,
                title = summaryTitle(lang, summary),
                detail = summaryDetail(lang, summary),
                status = summaryStatus(lang, summary),
                onReport = { onReport(context) },
            )
        }
        communityIssueRows(lang, summary)
        item { CommunityNotice(lang) }
    }
}

@Composable
internal fun RailPulseFeedScreen(lang: AppLanguage, onBack: () -> Unit) {
    val communityService = koinInject<CommunityReportService>()
    var summary by remember { mutableStateOf<CommunitySummary?>(null) }
    LaunchedEffect(Unit) {
        summary = communityService.fetchSummary()
    }
    RailPulseDetailLayout(
        title = pulseText(lang, "Ichnos activity", "Δραστηριοτητα Ichnos", "Aktiviteti Ichnos", "Attivita Ichnos"),
        subtitle = pulseText(lang, "Across Greece", "Σε ολη την Ελλαδα", "Ne gjithe Greqine", "In tutta la Grecia"),
        onBack = onBack,
    ) {
        item { CommunityNotice(lang) }
        communityIssueRows(lang, summary)
    }
}

@Composable
internal fun RailPulseContributionScreen(lang: AppLanguage, onBack: () -> Unit) {
    val communityService = koinInject<CommunityReportService>()
    val snapshot by RailPulseLocalStore.snapshot.collectAsState()
    var networkSummary by remember { mutableStateOf<CommunitySummary?>(null) }
    LaunchedEffect(Unit) {
        networkSummary = communityService.fetchSummary()
    }
    val progress = (snapshot.confirmed % 100) / 100f
    val level = railContributorLevel(snapshot.confirmed)
    val callsign = railContributorCallsign(level, lang)
    val nextCallsign = railContributorCallsign(level + 1, lang)
    RailPulseDetailLayout(
        title = pulseText(lang, "Local contribution", "Τοπικη συνεισφορα", "Kontributi lokal", "Contributo locale"),
        subtitle = "",
        onBack = onBack,
        headerColors = listOf(Color(0xFF5D2EA8), Color(0xFF343F91)),
        headerContent = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.15f)) {
                        Icon(
                            imageVector = Icons.Filled.Train,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(15.dp).size(26.dp),
                        )
                    }
                    Column {
                        Text(callsign, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("${pulseText(lang, "Local rail contributor", "Τοπικος συνεισφορεας rail", "Kontribues lokal rail", "Collaboratore rail locale")} · ${pulseText(lang, "Level", "Επιπεδο", "Niveli", "Livello")} $level", style = MaterialTheme.typography.labelMedium, color = Color.White)
                        Text(pulseText(lang, "Progress stored only on this device", "Η προοδος αποθηκευεται μονο στη συσκευη", "Progresi ruhet vetem ne kete pajisje", "Progressi salvati solo su questo dispositivo"), style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(top = 6.dp).background(Color.White.copy(alpha = 0.17f), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
                Text(pulseText(lang, "NEXT LEVEL", "ΕΠΟΜΕΝΟ ΕΠΙΠΕΔΟ", "NIVELI TJETER", "PROSSIMO LIVELLO"), style = MaterialTheme.typography.labelSmall, color = Color.White)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Color(0xFF63E6A6), trackColor = Color.White.copy(alpha = 0.2f))
                Row {
                    Text("${snapshot.confirmed} confirmed contributions", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    Spacer(Modifier.weight(1f))
                    Text("${100 - (snapshot.confirmed % 100)} ${pulseText(lang, "to", "για", "deri ne", "a")} $nextCallsign", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        },
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(pulseText(lang, "CONFIRMED", "ΕΠΙΒΕΒΑΙΩΜΕΝΑ", "KONFIRMUAR", "CONFERMATI"), snapshot.confirmed.toString(), Color.Black, Modifier.weight(1f))
                MetricCard(pulseText(lang, "QUALITY", "ΠΟΙΟΤΗΤΑ", "CILESIA", "QUALITA"), if (snapshot.confirmed == 0) "-" else "${snapshot.qualityPercent}%", SyrmosColorTokens.live, Modifier.weight(1f))
                MetricCard(pulseText(lang, "THIS WEEK", "ΑΥΤΗ ΤΗΝ ΕΒΔΟΜΑΔΑ", "KETE JAVE", "QUESTA SETTIMANA"), snapshot.thisWeek.toString(), Color(0xFF7C2EB8), Modifier.weight(1f))
            }
        }
        item { PulseSectionTitle(pulseText(lang, "Contributor milestones", "Οροσημα συνεισφορεα", "Arritjet e kontribuesit", "Traguardi del collaboratore")) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BadgeCard("✓", pulseText(lang, "First\nReport", "Πρωτη\nΑναφορα", "Raporti\ni pare", "Prima\nsegnalazione"), snapshot.confirmed >= 1, Modifier.weight(1f))
                BadgeCard("◉", pulseText(lang, "Live\nReporter", "Ζωντανος\nReporter", "Raportues\nLive", "Reporter\nLive"), snapshot.confirmed >= 10, Modifier.weight(1f))
                BadgeCard("★", pulseText(lang, "Station\nGuardian", "Φυλακας\nΣταθμου", "Mbrojtes\nStacioni", "Custode\nStazione"), snapshot.confirmed >= 50, Modifier.weight(1f))
                BadgeCard("100", pulseText(lang, "100\nReports", "100\nΑναφορες", "100\nRaporte", "100\nReport"), snapshot.confirmed >= 100, Modifier.weight(1f))
            }
        }
        item { PulseSectionTitle(pulseText(lang, "Weekly community activity", "Εβδομαδιαια δραστηριοτητα κοινοτητας", "Aktiviteti javor i komunitetit", "Attivita settimanale della comunita")) }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 5.dp) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row {
                        Text(pulseText(lang, "Anonymous reports across Greece", "Ανωνυμες αναφορες σε ολη την Ελλαδα", "Raporte anonime ne Greqi", "Segnalazioni anonime in tutta la Grecia"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(pulseText(lang, "Last 7 days", "Τελευταιες 7 ημερες", "7 ditet e fundit", "Ultimi 7 giorni"), style = MaterialTheme.typography.labelSmall, color = SyrmosColorTokens.live)
                    }
                    val weeklyTotal = networkSummary?.totalReportsThisWeek
                    Text(weeklyTotal?.toString() ?: "-", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(pulseText(lang, "Your local contribution: ${snapshot.thisWeek}", "Η τοπικη συνεισφορα σου: ${snapshot.thisWeek}", "Kontributi yt lokal: ${snapshot.thisWeek}", "Il tuo contributo locale: ${snapshot.thisWeek}"), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text(pulseText(lang, "This total comes from accepted anonymous reports, not estimated journeys.", "Αυτο το συνολο προερχεται απο αποδεκτες ανωνυμες αναφορες, οχι εκτιμησεις διαδρομων.", "Ky total vjen nga raporte anonime te pranuara, jo nga udhetime te vleresuara.", "Questo totale proviene da segnalazioni anonime accettate, non da viaggi stimati."), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFF0E8FA)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(pulseText(lang, "Private by construction", "Ιδιωτικο απο τον σχεδιασμο", "Privat nga ndertimi", "Privato per costruzione"), fontWeight = FontWeight.SemiBold)
                    Text(pulseText(lang, "Progress stays local. Anonymous network reports contain no account, device id, or precise location and are deleted after seven days.", "Η προοδος μενει τοπικα. Οι ανωνυμες αναφορες δεν περιεχουν λογαριασμο, αναγνωριστικο συσκευης η ακριβη τοποθεσια και διαγραφονται μετα απο επτα ημερες.", "Progresi mbetet lokal. Raportet anonime nuk permbajne llogari, identifikues pajisjeje ose vendndodhje te sakte dhe fshihen pas shtate ditesh.", "I progressi restano locali. Le segnalazioni anonime non includono account, identificatori del dispositivo o posizione precisa e vengono eliminate dopo sette giorni."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RailPulseDetailLayout(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    headerColors: List<Color>? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    headerContent: (@Composable () -> Unit)? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val headerModifier = if (headerColors != null) Modifier.background(Brush.horizontalGradient(headerColors), RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)) else Modifier
            Column(modifier = headerModifier.padding(if (headerColors != null) 14.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = if (headerColors != null) Color.White else MaterialTheme.colorScheme.onSurface) }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (headerColors != null) Color.White else MaterialTheme.colorScheme.onSurface)
                        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (headerColors != null) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    trailingIcon?.invoke()
                }
                headerContent?.invoke()
            }
        }
        content()
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.conditionGrid(items: List<PulseCondition>) {
    items.chunked(2).forEach { row ->
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { condition -> ConditionCard(condition, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ConditionCard(condition: PulseCondition, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 5.dp) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PulseCircle(condition.symbol, condition.color.copy(alpha = 0.12f), condition.color)
                Text(condition.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(condition.value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(condition.detail, style = MaterialTheme.typography.labelSmall, color = condition.color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CommunitySummaryCard(lang: AppLanguage, title: String, detail: String, status: String, onReport: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 5.dp) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PulseCircle("●", SyrmosColorTokens.live.copy(alpha = 0.12f), SyrmosColorTokens.live)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(status, style = MaterialTheme.typography.labelSmall, color = SyrmosColorTokens.live, fontWeight = FontWeight.Bold)
            }
            Button(onClick = onReport, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface)) {
                Text(pulseText(lang, "Report", "Αναφορα", "Raporto", "Segnala"))
            }
        }
    }
}

private fun summaryTitle(lang: AppLanguage, summary: CommunitySummary?): String = when {
    summary == null -> pulseText(lang, "Community status unavailable", "Η κοινοτικη κατασταση δεν ειναι διαθεσιμη", "Gjendja e komunitetit nuk eshte e disponueshme", "Stato della comunita non disponibile")
    summary.hasIssues -> pulseText(lang, "Community issue reported", "Αναφερθηκε κοινοτικο προβλημα", "U raportua problem nga komuniteti", "Problema segnalato dalla comunita")
    else -> pulseText(lang, "No issues reported", "Δεν αναφερθηκαν προβληματα", "Nuk ka probleme te raportuara", "Nessun problema segnalato")
}

private fun summaryDetail(lang: AppLanguage, summary: CommunitySummary?): String = when {
    summary == null -> pulseText(lang, "Connect to refresh anonymous reports.", "Συνδεσου για ανανεωση ανωνυμων αναφορων.", "Lidhu per te rifreskuar raportet anonime.", "Connettiti per aggiornare le segnalazioni anonime.")
    summary.hasIssues -> pulseText(lang, "${summary.activeIssueCount} active report${if (summary.activeIssueCount == 1) "" else "s"}", "${summary.activeIssueCount} ενεργες αναφορες", "${summary.activeIssueCount} raporte aktive", "${summary.activeIssueCount} segnalazioni attive")
    else -> pulseText(lang, "${summary.estimatedJourneysToday ?: 0} estimated journeys today", "${summary.estimatedJourneysToday ?: 0} εκτιμωμενες διαδρομες σημερα", "${summary.estimatedJourneysToday ?: 0} udhetime te vleresuara sot", "${summary.estimatedJourneysToday ?: 0} viaggi stimati oggi")
}

private fun summaryStatus(lang: AppLanguage, summary: CommunitySummary?): String = when {
    summary == null -> pulseText(lang, "Offline", "Εκτος συνδεσης", "Offline", "Offline")
    summary.hasIssues -> pulseText(lang, "Check details", "Δες λεπτομερειες", "Shiko hollesite", "Vedi dettagli")
    else -> pulseText(lang, "Estimate", "Εκτιμηση", "Vleresim", "Stima")
}

private fun androidx.compose.foundation.lazy.LazyListScope.communityIssueRows(
    lang: AppLanguage,
    summary: CommunitySummary?,
) {
    if (summary == null) {
        item {
            PulseActivityRow(
                "?",
                pulseText(lang, "Reports unavailable", "Οι αναφορες δεν ειναι διαθεσιμες", "Raportet nuk jane te disponueshme", "Segnalazioni non disponibili"),
                pulseText(lang, "Official schedules and alerts remain available.", "Τα επισημα δρομολογια και οι ειδοποιησεις παραμενουν διαθεσιμα.", "Oraret dhe njoftimet zyrtare mbeten te disponueshme.", "Gli orari e gli avvisi ufficiali restano disponibili."),
                pulseText(lang, "Offline", "Εκτος συνδεσης", "Offline", "Offline"),
                Color(0xFF6B7280),
            )
        }
        return
    }
    if (!summary.hasIssues) {
        item {
            PulseActivityRow(
                "✓",
                pulseText(lang, "No active issue reports", "Δεν υπαρχουν ενεργες αναφορες προβληματων", "Nuk ka raporte aktive problemesh", "Nessuna segnalazione attiva"),
                pulseText(lang, "The journey count is an estimate, not a user confirmation count.", "Ο αριθμος διαδρομων ειναι εκτιμηση, οχι αριθμος επιβεβαιωσεων χρηστων.", "Numri i udhetimeve eshte vleresim, jo numer konfirmimesh nga perdoruesit.", "Il numero di viaggi e una stima, non un conteggio di conferme utenti."),
                pulseText(lang, "Clear", "Καθαρο", "Ne rregull", "Regolare"),
                SyrmosColorTokens.live,
            )
        }
        return
    }
    summary.issues.forEach { issue ->
        item(key = "${issue.scopeId}:${issue.signal}:${issue.detail}") {
            val signal = when (issue.signal) {
                "delayed" -> pulseText(lang, "Delay", "Καθυστερηση", "Vonese", "Ritardo")
                "crowded" -> pulseText(lang, "Crowded", "Κοσμος", "Plot", "Affollato")
                "stopped" -> pulseText(lang, "Service stopped", "Διακοπη υπηρεσιας", "Sherbimi i ndalur", "Servizio fermo")
                "too_hot" -> pulseText(lang, "Too hot", "Πολυ ζεστη", "Shume nxehte", "Troppo caldo")
                "access" -> pulseText(lang, "Accessibility", "Προσβαση", "Akses", "Accessibilita")
                "facilities" -> pulseText(lang, "Facilities", "Παροχες", "Sherbime", "Servizi")
                "safety" -> pulseText(lang, "Safety", "Ασφαλεια", "Siguri", "Sicurezza")
                else -> pulseText(lang, "Other issue", "Αλλο προβλημα", "Problem tjeter", "Altro problema")
            }
            PulseActivityRow(
                "!",
                issue.scopeLabel,
                listOf(signal, issue.detail.takeIf { it.isNotBlank() }, "${issue.count}").filterNotNull().joinToString(" · "),
                pulseText(lang, "Active", "Ενεργο", "Aktiv", "Attivo"),
                if (issue.signal in setOf("delayed", "stopped", "safety")) SyrmosColorTokens.disruption else SyrmosColorTokens.warning,
            )
        }
    }
}

@Composable
private fun PulseActivityRow(symbol: String, title: String, detail: String, status: String, color: Color) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PulseCircle(symbol, color.copy(alpha = 0.12f), color)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(status, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PulseSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun PulseCircle(symbol: String, background: Color, foreground: Color) {
    Box(modifier = Modifier.size(46.dp).background(background, CircleShape), contentAlignment = Alignment.Center) {
        Text(symbol, color = foreground, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MetricCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun BadgeCard(symbol: String, label: String, unlocked: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            PulseCircle(if (unlocked) symbol else "lock", Color(0xFFF0E8FA), if (unlocked) Color.Black else Color.Gray)
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CommunityNotice(lang: AppLanguage) {
    Text(
        pulseText(lang, "Community reports are not official operator notices.", "Οι αναφορες κοινοτητας δεν ειναι επισημες ανακοινωσεις φορεα.", "Raportet e komunitetit nuk jane njoftime zyrtare te operatorit.", "Le segnalazioni della comunita non sono avvisi ufficiali."),
        style = MaterialTheme.typography.labelSmall,
        color = SyrmosColorTokens.warning,
        modifier = Modifier.fillMaxWidth().background(SyrmosColorTokens.warningContainer, RoundedCornerShape(14.dp)).padding(12.dp),
        textAlign = TextAlign.Center,
    )
}
