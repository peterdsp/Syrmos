package com.syrmos.app.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.syrmos.app.platform.requestNotificationPermission
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager
import kotlinx.coroutines.launch

/**
 * One-time highlights shown after an install or update, gated by the stored
 * last-seen version (see readLastWhatsNewVersion / markWhatsNewSeen). The
 * bullet list is device-aware: if the platform has an on-device LLM
 * normalizer wired (Gemini Nano on Android via ML Kit GenAI), we lead with
 * "smarter search that understands typos"; otherwise we lead with the
 * rule-based bullets so we don't promise capabilities the device can't
 * deliver.
 */
@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    val lang by LocalizationManager.language.collectAsState()
    fun t(en: String, el: String, sq: String, it: String = en) = when (lang) {
        AppLanguage.GREEK -> el
        AppLanguage.ALBANIAN -> sq
        AppLanguage.ITALIAN -> it
        else -> en
    }

    val items = buildList {
        add(
            t(
                "Plan any journey A to B: ranked routes, arrive-by, and the last train home",
                "Σχεδίασε κάθε διαδρομή από Α σε Β: ταξινομημένες επιλογές, άφιξη έως, και το τελευταίο τρένο για το σπίτι",
                "Planifiko çdo udhëtim nga A në B: rrugë të renditura, mbërritje deri, dhe treni i fundit për në shtëpi",
                "Pianifica qualsiasi viaggio da A a B: percorsi ordinati, arrivo entro, e l'ultimo treno per casa",
            )
        )
        add(
            t(
                "GO guides you stop by stop, with a get-off alert and an ongoing journey notification",
                "Το GO σε καθοδηγεί στάση-στάση, με ειδοποίηση αποβίβασης και μόνιμη ειδοποίηση διαδρομής",
                "GO të udhëzon ndalesë pas ndalese, me njoftim zbritjeje dhe njoftim të vazhdueshëm udhëtimi",
                "GO ti guida fermata per fermata, con avviso di discesa e una notifica di viaggio in corso",
            )
        )
        add(
            t(
                "Home shows the next train in every direction at your nearest station",
                "Η Αρχική δείχνει τον επόμενο συρμό προς κάθε κατεύθυνση στον κοντινότερο σταθμό σου",
                "Kreu tregon trenin tjetër për çdo drejtim në stacionin tënd më të afërt",
                "La Home mostra il prossimo treno in ogni direzione dalla tua stazione più vicina",
            )
        )
        add(
            t(
                "Leave-by reminders for your saved departures, and connection-risk warnings that offer alternatives",
                "Υπενθυμίσεις αναχώρησης για τις αποθηκευμένες σου αναχωρήσεις, και προειδοποιήσεις κινδύνου ανταπόκρισης με εναλλακτικές",
                "Kujtesa për nisjen për nisjet e ruajtura, dhe paralajmërime rreziku lidhjeje me alternativa",
                "Promemoria di partenza per le partenze salvate, e avvisi di coincidenza a rischio con alternative",
            )
        )
        add(
            t(
                "Made for tablets and foldables: planning and guidance side by side on the open display",
                "Φτιαγμένο για tablet και αναδιπλούμενα: σχεδιασμός και καθοδήγηση δίπλα-δίπλα στην ανοιχτή οθόνη",
                "Bërë për tableta dhe të palosshëm: planifikim dhe udhëzim krah për krah në ekranin e hapur",
                "Pensato per tablet e pieghevoli: pianificazione e guida fianco a fianco sullo schermo aperto",
            )
        )
    }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { requestNotificationPermission() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🦉", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = t(
                        "What's new in Syrmos",
                        "Τι νεο υπαρχει στο Syrmos",
                        "Çfare ka te re ne Syrmos",
                        "Novita in Syrmos",
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                items.forEach { line ->
                    Text("•  $line", style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(t("Got it", "Εντάξει", "Në rregull", "Capito"))
                }
            }
        }
    }
}
