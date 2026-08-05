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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.RailPulseLocalStore
import com.syrmos.core.designsystem.theme.tokens.SyrmosColorTokens
import kotlinx.coroutines.delay

internal data class RailPulseReportContext(
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

@Composable
internal fun ExploreRailPulseContent(
    lang: AppLanguage,
    onReport: (RailPulseReportContext) -> Unit,
    onOpenStation: () -> Unit = {},
    onOpenTrain: () -> Unit = {},
) {
    var selectedBudget by remember { mutableStateOf(30) }
    val context = remember(lang) {
        RailPulseReportContext(
            title = pulseText(lang, "Kallithea to Monastiraki", "Καλλιθεα προς Μοναστηρακι", "Kallithea per Monastiraki", "Kallithea - Monastiraki"),
            subtitle = pulseText(lang, "M1 toward Kifissia", "M1 προς Κηφισια", "M1 drejt Kifisia", "M1 verso Kifisia"),
        )
    }

    PulseRouteHero(
        lang = lang,
        context = context,
        onReport = { onReport(context) },
    )

    Spacer(Modifier.height(10.dp))
    SectionTitle(
        title = pulseText(lang, "RailPulse across Greece", "RailPulse σε ολη την Ελλαδα", "RailPulse ne gjithe Greqine", "RailPulse in tutta la Grecia"),
        action = pulseText(lang, "See all", "Ολα", "Shiko te gjitha", "Vedi tutto"),
    )

    val feed = remember(lang) {
        listOf(
            PulseFeedItem(
                title = pulseText(lang, "Athens - Piraeus", "Αθηνα - Πειραιας", "Athine - Pire", "Atene - Pireo"),
                detail = pulseText(lang, "14 min delay · 23 confirmations", "Καθυστερηση 14 λεπ · 23 επιβεβαιωσεις", "14 min vonese · 23 konfirmime", "14 min di ritardo · 23 conferme"),
                status = pulseText(lang, "Verified", "Επιβεβαιωμενο", "Konfirmuar", "Verificato"),
                color = Color(0xFFDC2626),
            ),
            PulseFeedItem(
                title = "Monastiraki",
                detail = pulseText(lang, "Escalator working again · 9 confirmations", "Η κυλιομενη λειτουργει ξανα · 9 επιβεβαιωσεις", "Shkallet levizese punojne perseri · 9 konfirmime", "Scala mobile di nuovo attiva · 9 conferme"),
                status = pulseText(lang, "2 min ago", "πριν 2 λεπ", "2 min me pare", "2 min fa"),
                color = Color(0xFF059669),
            ),
            PulseFeedItem(
                title = pulseText(lang, "Airport train", "Τρενο Αεροδρομιου", "Treni i aeroportit", "Treno aeroporto"),
                detail = pulseText(lang, "Standing room only · 31 confirmations", "Μονο ορθιοι · 31 επιβεβαιωσεις", "Vetem ne kembe · 31 konfirmime", "Solo posti in piedi · 31 conferme"),
                status = pulseText(lang, "Live", "Ζωντανα", "Live", "Live"),
                color = Color(0xFFD97706),
            ),
        )
    }
    feed.forEachIndexed { index, item ->
        PulseFeedRow(
            item = item,
            onClick = if (index == 1) onOpenStation else onOpenTrain,
        )
    }

    Spacer(Modifier.height(10.dp))
    SectionTitle(
        title = pulseText(lang, "Explore by time", "Εξερευνηση με χρονο", "Eksploro sipas kohes", "Esplora per tempo"),
        action = pulseText(lang, "From Kallithea", "Απο Καλλιθεα", "Nga Kallithea", "Da Kallithea"),
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
            text = pulseText(
                lang,
                "Ariadne: M1 is normal, but the Airport train is crowded.",
                "Ariadne: Η M1 λειτουργει κανονικα, αλλα το τρενο Αεροδρομιου εχει κοσμο.",
                "Ariadne: M1 eshte normale, por treni i aeroportit eshte plot.",
                "Ariadne: M1 e regolare, ma il treno aeroporto e affollato.",
            ),
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
                    text = pulseText(lang, "YOUR ROUTE PULSE", "Ο ΠΑΛΜΟΣ ΤΗΣ ΔΙΑΔΡΟΜΗΣ", "PULSI I RRUGES TENDE", "IL PULSO DEL PERCORSO"),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.size(9.dp).background(Color(0xFF4ADE80), CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = pulseText(lang, "Normal", "Κανονικα", "Normal", "Regolare"),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(context.title, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                text = "${context.subtitle} · ${pulseText(lang, "next train in 2 min", "επομενο σε 2 λεπ", "treni tjeter ne 2 min", "prossimo tra 2 min")}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(Color.White, CircleShape))
                Box(Modifier.weight(1f).height(3.dp).background(Color(0xFF9BE2C0)))
                Box(Modifier.size(14.dp).background(Color.White, CircleShape))
            }
            Row {
                Text("Kallithea", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.weight(1f))
                Text("Monastiraki", style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = pulseText(lang, "18 people confirmed normal service", "18 ατομα επιβεβαιωσαν κανονικη λειτουργια", "18 persona konfirmuan sherbim normal", "18 persone confermano servizio regolare"),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = onReport,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF171614),
                    ),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(pulseText(lang, "Report", "Αναφορα", "Raporto", "Segnala"))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(action, style = MaterialTheme.typography.bodySmall, color = SyrmosColorTokens.brand)
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember { mutableStateOf<QuickReportSignal?>(null) }
    var crowdLevel by remember { mutableStateOf("Standing") }
    var hasRecorded by remember { mutableStateOf(false) }
    var canUndo by remember { mutableStateOf(false) }

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
                                .clickable {
                                    if (!hasRecorded) {
                                        RailPulseLocalStore.recordContribution()
                                        hasRecorded = true
                                    }
                                    selected = signal
                                    canUndo = true
                                    if (signal == QuickReportSignal.CROWDED) crowdLevel = "Standing"
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
                            modifier = Modifier.weight(1f).clickable { crowdLevel = level },
                            shape = RoundedCornerShape(13.dp),
                            color = if (active) Color(0xFF6F2DA8) else MaterialTheme.colorScheme.surface,
                        ) {
                            Text(
                                level,
                                modifier = Modifier.padding(vertical = 10.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            if (selected != null) {
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
                                    RailPulseLocalStore.undoContribution()
                                    selected = null
                                    hasRecorded = false
                                    canUndo = false
                                }.padding(6.dp),
                            )
                        }
                    }
                }
                Text(
                    text = pulseText(lang, "One tap sent it. Refine above or undo for 10 seconds. Stored only on this device until anonymous submission is available.", "Ενα πατημα την εστειλε. Βελτιωσε παραπανω η ανακαλεσε για 10 δευτερολεπτα. Αποθηκευεται μονο σε αυτη τη συσκευη μεχρι να διατεθει ανωνυμη αποστολη.", "Nje prekje e dergoi. Perditesoje lart ose zhbëje per 10 sekonda. Ruhet vetem ne kete pajisje derisa te jete gati dergimi anonim.", "Un tocco l'ha inviata. Modifica sopra o annulla entro 10 secondi. Salvata solo sul dispositivo finche l'invio anonimo non sara disponibile."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFD97706).copy(alpha = 0.12f)) {
                Text(
                    text = pulseText(lang, "For immediate danger, contact emergency services. RailPulse is not an emergency channel.", "Για αμεσο κινδυνο, επικοινωνησε με τις υπηρεσιες εκτακτης αναγκης. Το RailPulse δεν ειναι καναλι εκτακτης αναγκης.", "Per rrezik te menjehershem, kontakto sherbimet e emergjences. RailPulse nuk eshte kanal emergjence.", "Per un pericolo immediato, contatta i servizi di emergenza. RailPulse non e un canale di emergenza."),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF9A5B08),
                )
            }
        }
    }
}

private fun QuickReportSignal.localized(lang: AppLanguage): String = when (this) {
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
