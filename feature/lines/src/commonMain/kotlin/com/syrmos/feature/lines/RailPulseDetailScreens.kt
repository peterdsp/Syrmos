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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

internal enum class RailPulseDestination {
    STATION,
    TRAIN,
    CONTRIBUTION,
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
    val context = RailPulseReportContext(
        title = pulseText(lang, "Airport", "Αεροδρομιο", "Aeroporti", "Aeroporto"),
        subtitle = pulseText(lang, "Athens International Airport · M3", "Διεθνες Αεροδρομιο Αθηνων · M3", "Aeroporti Nderkombetar i Athines · M3", "Aeroporto Internazionale di Atene · M3"),
    )
    RailPulseDetailLayout(
        title = pulseText(lang, "Airport", "Αεροδρομιο", "Aeroporti", "Aeroporto"),
        subtitle = pulseText(lang, "Athens International Airport", "Διεθνες Αεροδρομιο Αθηνων", "Aeroporti Nderkombetar i Athines", "Aeroporto Internazionale di Atene"),
        onBack = onBack,
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF214D78),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PulseCircle("M3", SyrmosColorTokens.metroBlue, Color.White)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pulseText(lang, "NEXT TOWARD DIMOTIKO THEATRO", "ΕΠΟΜΕΝΟ ΠΡΟΣ ΔΗΜΟΤΙΚΟ ΘΕΑΤΡΟ", "TJETRI DREJT DIMOTIKO THEATRO", "PROSSIMO VERSO DIMOTIKO THEATRO"), style = MaterialTheme.typography.labelSmall, color = Color.White)
                        Text("3 min", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("14:42 · Platform 1", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                    }
                    Surface(shape = RoundedCornerShape(12.dp), color = Color.White) {
                        Text(pulseText(lang, "Scheduled", "Προγραμματισμενο", "Planifikuar", "Programmato"), color = SyrmosColorTokens.metroBlue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
                    }
                }
            }
        }
        item {
            CommunitySummaryCard(
                title = pulseText(lang, "Community: normal", "Κοινοτητα: κανονικα", "Komuniteti: normal", "Comunita: regolare"),
                detail = pulseText(lang, "12 fresh reports · updated 90 sec ago", "12 προσφατες αναφορες · πριν 90 δευτ", "12 raporte te fresketa · 90 sek me pare", "12 segnalazioni recenti · 90 sec fa"),
                status = pulseText(lang, "High community confidence", "Υψηλη εμπιστοσυνη κοινοτητας", "Besim i larte i komunitetit", "Alta affidabilita della comunita"),
                onReport = { onReport(context) },
            )
        }
        item { PulseSectionTitle(pulseText(lang, "Station conditions", "Συνθηκες σταθμου", "Gjendja e stacionit", "Condizioni stazione")) }
        conditionGrid(
            listOf(
                PulseCondition("••", "PLATFORM", pulseText(lang, "Moderate crowd", "Μετριος κοσμος", "Turme mesatare", "Affollamento medio"), "18 confirmations · 2 min ago", SyrmosColorTokens.warning),
                PulseCondition("♿", "LIFT", pulseText(lang, "Out of service", "Εκτος λειτουργιας", "Jashte sherbimit", "Fuori servizio"), "31 confirmations · expires 18:00", SyrmosColorTokens.disruption),
                PulseCondition("✓", "ESCALATOR", pulseText(lang, "Working", "Λειτουργει", "Punon", "Funzionante"), "9 confirmations · 8 min ago", SyrmosColorTokens.live),
                PulseCondition("P", "PARKING", pulseText(lang, "Nearly full", "Σχεδον γεματο", "Pothuajse plot", "Quasi pieno"), "7 confirmations · 12 min ago", SyrmosColorTokens.scheduled),
            ),
        )
        item { PulseSectionTitle(pulseText(lang, "Latest at this station", "Τελευταια στον σταθμο", "Me te fundit ne stacion", "Ultime dalla stazione")) }
        item { PulseActivityRow("!", pulseText(lang, "Lift out of service", "Ανελκυστηρας εκτος λειτουργιας", "Ashensori jashte sherbimit", "Ascensore fuori servizio"), "Confirmed by 31 · 2 min ago", pulseText(lang, "Confirm", "Επιβεβαιωση", "Konfirmo", "Conferma"), SyrmosColorTokens.disruption) }
        item { PulseActivityRow("✓", pulseText(lang, "Escalator working again", "Η κυλιομενη λειτουργει ξανα", "Shkallet levizese punojne perseri", "Scala mobile di nuovo attiva"), "Confirmed by 9 · 8 min ago", pulseText(lang, "Resolved", "Λυθηκε", "Zgjidhur", "Risolto"), SyrmosColorTokens.live) }
        item { CommunityNotice(lang) }
    }
}

@Composable
internal fun RailPulseTrainScreen(
    lang: AppLanguage,
    onBack: () -> Unit,
    onReport: (RailPulseReportContext) -> Unit,
) {
    val context = RailPulseReportContext(
        title = pulseText(lang, "Train 1635", "Τρενο 1635", "Treni 1635", "Treno 1635"),
        subtitle = pulseText(lang, "Athens to Kalambaka", "Αθηνα προς Καλαμπακα", "Athine per Kalambaka", "Atene verso Kalambaka"),
    )
    RailPulseDetailLayout(
        title = context.title,
        subtitle = context.subtitle,
        onBack = onBack,
        headerColors = listOf(Color(0xFF213F5D), Color(0xFF55308A)),
        headerContent = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pulseText(lang, "COMMUNITY SUMMARY", "ΣΥΝΟΨΗ ΚΟΙΝΟΤΗΤΑΣ", "PERMBLEDHJE E KOMUNITETIT", "RIEPILOGO COMUNITA"), style = MaterialTheme.typography.labelSmall, color = Color.White)
                    Spacer(Modifier.weight(1f))
                    Text("●  Updated 53 sec", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFC24A))
                }
                Text(pulseText(lang, "Running with a minor delay", "Κινειται με μικρη καθυστερηση", "Po leviz me vonese te vogel", "In viaggio con lieve ritardo"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Text(pulseText(lang, "Standing room only. Temperature comfortable.\nAir conditioning confirmed working.", "Μονο ορθιοι. Ανετη θερμοκρασια.\nΟ κλιματισμος επιβεβαιωθηκε οτι λειτουργει.", "Vetem ne kembe. Temperature e rehatshme.\nKondicioneri u konfirmua se punon.", "Solo posti in piedi. Temperatura confortevole.\nAria condizionata confermata funzionante."), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.18f)) {
                        Text("42 independent confirmations", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { onReport(context) }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF332250))) {
                        Text(pulseText(lang, "Report", "Αναφορα", "Raporto", "Segnala"))
                    }
                }
            }
        },
    ) {
        item { PulseSectionTitle(pulseText(lang, "Current conditions", "Τρεχουσες συνθηκες", "Gjendja aktuale", "Condizioni attuali")) }
        conditionGrid(
            listOf(
                PulseCondition("•••", "OCCUPANCY", pulseText(lang, "Standing", "Ορθιοι", "Ne kembe", "In piedi"), "31 confirmations · 2 min ago", SyrmosColorTokens.warning),
                PulseCondition("+4", "DELAY", pulseText(lang, "About 4 min", "Περιπου 4 λεπ", "Rreth 4 min", "Circa 4 min"), "24 confirmations · 1 min ago", SyrmosColorTokens.disruption),
                PulseCondition("✓", "TEMPERATURE", pulseText(lang, "Comfortable", "Ανετη", "Rehat", "Confortevole"), "18 confirmations · 4 min ago", SyrmosColorTokens.live),
                PulseCondition("4", "CLEANLINESS", "4 of 5", "12 confirmations · 8 min ago", SyrmosColorTokens.live),
            ),
        )
        item { PulseSectionTitle(pulseText(lang, "Service", "Υπηρεσιες", "Sherbimi", "Servizio")) }
        item { PulseActivityRow("✓", pulseText(lang, "Air conditioning working", "Ο κλιματισμος λειτουργει", "Kondicioneri punon", "Aria condizionata funzionante"), "Confirmed by 27 passengers", pulseText(lang, "Verified", "Επιβεβαιωμενο", "Konfirmuar", "Verificato"), SyrmosColorTokens.live) }
        item { PulseActivityRow("!", pulseText(lang, "Wi-Fi unavailable", "Το Wi-Fi δεν λειτουργει", "Wi-Fi nuk punon", "Wi-Fi non disponibile"), "9 confirmations · expires at journey end", pulseText(lang, "Active", "Ενεργο", "Aktiv", "Attivo"), SyrmosColorTokens.disruption) }
        item { PulseSectionTitle(pulseText(lang, "Recent activity", "Προσφατη δραστηριοτητα", "Aktiviteti i fundit", "Attivita recente")) }
        item { PulseActivityRow("P", pulseText(lang, "Standing room only", "Μονο ορθιοι", "Vetem ne kembe", "Solo posti in piedi"), "Confirmed by 31 · updated 2 min ago", pulseText(lang, "Confirm", "Επιβεβαιωση", "Konfirmo", "Conferma"), Color(0xFF7C2EB8)) }
    }
}

@Composable
internal fun RailPulseContributionScreen(lang: AppLanguage, onBack: () -> Unit) {
    val snapshot by RailPulseLocalStore.snapshot.collectAsState()
    val progress = (snapshot.confirmed % 100) / 100f
    RailPulseDetailLayout(
        title = pulseText(lang, "Local contribution", "Τοπικη συνεισφορα", "Kontributi lokal", "Contributo locale"),
        subtitle = "",
        onBack = onBack,
        headerColors = listOf(Color(0xFF5D2EA8), Color(0xFF343F91)),
        trailingIcon = { Icon(Icons.Filled.Settings, contentDescription = null, tint = Color.White) },
        headerContent = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PulseCircle("⌁", Color.White.copy(alpha = 0.15f), Color.White)
                    Column {
                        Text("Petros", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(pulseText(lang, "Rail Contributor · Level 5", "Συνεισφορεας Rail · Επιπεδο 5", "Kontribues Rail · Niveli 5", "Collaboratore Rail · Livello 5"), style = MaterialTheme.typography.labelMedium, color = Color.White)
                        Text(pulseText(lang, "Stored only on this device", "Αποθηκευεται μονο σε αυτη τη συσκευη", "Ruhet vetem ne kete pajisje", "Salvato solo su questo dispositivo"), style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(top = 6.dp).background(Color.White.copy(alpha = 0.17f), CircleShape).padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
                Text(pulseText(lang, "NEXT LEVEL", "ΕΠΟΜΕΝΟ ΕΠΙΠΕΔΟ", "NIVELI TJETER", "PROSSIMO LIVELLO"), style = MaterialTheme.typography.labelSmall, color = Color.White)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Color(0xFF63E6A6), trackColor = Color.White.copy(alpha = 0.2f))
                Row {
                    Text("${snapshot.confirmed} confirmed contributions", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    Spacer(Modifier.weight(1f))
                    Text("${100 - (snapshot.confirmed % 100)} to Level 6", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        },
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(pulseText(lang, "CONFIRMED", "ΕΠΙΒΕΒΑΙΩΜΕΝΑ", "KONFIRMUAR", "CONFERMATI"), snapshot.confirmed.toString(), Color.Black, Modifier.weight(1f))
                MetricCard(pulseText(lang, "QUALITY", "ΠΟΙΟΤΗΤΑ", "CILESIA", "QUALITA"), "${snapshot.qualityPercent}%", SyrmosColorTokens.live, Modifier.weight(1f))
                MetricCard(pulseText(lang, "THIS WEEK", "ΑΥΤΗ ΤΗΝ ΕΒΔΟΜΑΔΑ", "KETE JAVE", "QUESTA SETTIMANA"), snapshot.thisWeek.toString(), Color(0xFF7C2EB8), Modifier.weight(1f))
            }
        }
        item { PulseSectionTitle(pulseText(lang, "Badges", "Σηματα", "Distinktivet", "Badge")) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BadgeCard("✓", pulseText(lang, "First\nReport", "Πρωτη\nΑναφορα", "Raporti\ni pare", "Prima\nsegnalazione"), Modifier.weight(1f))
                BadgeCard("◉", pulseText(lang, "Live\nReporter", "Ζωντανος\nReporter", "Raportues\nLive", "Reporter\nLive"), Modifier.weight(1f))
                BadgeCard("★", pulseText(lang, "Station\nGuardian", "Φυλακας\nΣταθμου", "Mbrojtes\nStacioni", "Custode\nStazione"), Modifier.weight(1f))
                BadgeCard("100", pulseText(lang, "Accurate\nReports", "Ακριβεις\nΑναφορες", "Raporte\nte sakta", "Report\naccurati"), Modifier.weight(1f))
            }
        }
        item { PulseSectionTitle(pulseText(lang, "Weekly community goal", "Εβδομαδιαιος στοχος κοινοτητας", "Objektivi javor i komunitetit", "Obiettivo settimanale")) }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 5.dp) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row {
                        Text(pulseText(lang, "Useful confirmations across Greece", "Χρησιμες επιβεβαιωσεις σε ολη την Ελλαδα", "Konfirmime te dobishme ne Greqi", "Conferme utili in tutta la Grecia"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(pulseText(lang, "Anonymous total", "Ανωνυμο συνολο", "Total anonim", "Totale anonimo"), style = MaterialTheme.typography.labelSmall, color = SyrmosColorTokens.live)
                    }
                    Text("1,284  ", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("of 2,000 this week", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(progress = { 0.642f }, modifier = Modifier.fillMaxWidth(), color = Color(0xFF7C2EB8), trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    Text("Your local contribution: ${snapshot.thisWeek}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text(pulseText(lang, "No names, profiles, or rankings leave any device.", "Κανενα ονομα, προφιλ η καταταξη δεν φευγει απο τη συσκευη.", "Asnje emer, profil ose renditje nuk largohet nga pajisja.", "Nomi, profili e classifiche non lasciano il dispositivo."), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFF0E8FA)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(pulseText(lang, "Private by construction", "Ιδιωτικο απο τον σχεδιασμο", "Privat nga ndertimi", "Privato per costruzione"), fontWeight = FontWeight.SemiBold)
                    Text(pulseText(lang, "Progress is local. Network reports will use unlinkable one-time proofs.", "Η προοδος ειναι τοπικη. Οι διαδικτυακες αναφορες θα χρησιμοποιουν ασυνδετες αποδειξεις μιας χρησης.", "Progresi eshte lokal. Raportet ne rrjet do te perdorin prova njeperdorimshe te palidhshme.", "I progressi sono locali. Le segnalazioni online useranno prove monouso non collegabili."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun CommunitySummaryCard(title: String, detail: String, status: String, onReport: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 5.dp) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PulseCircle("●", SyrmosColorTokens.live.copy(alpha = 0.12f), SyrmosColorTokens.live)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(status, style = MaterialTheme.typography.labelSmall, color = SyrmosColorTokens.live, fontWeight = FontWeight.Bold)
            }
            Button(onClick = onReport, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface)) { Text("Report") }
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
private fun BadgeCard(symbol: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
        Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            PulseCircle(symbol, Color(0xFFF0E8FA), Color.Black)
            Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
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
