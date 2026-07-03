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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.syrmos.app.platform.provideQueryNormalizer
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.domain.assistant.NoOpQueryNormalizer

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
    fun t(en: String, el: String, sq: String) = when (lang) {
        AppLanguage.GREEK -> el
        AppLanguage.ALBANIAN -> sq
        else -> en
    }

    val hasLlm = provideQueryNormalizer() !== NoOpQueryNormalizer

    val items = buildList {
        if (hasLlm) {
            add(
                t(
                    "Cleverer Ariadne — on-device AI reads your typos and rough spelling before the parser",
                    "Πιο έξυπνη Αριάδνη — το on-device AI διαβάζει τα λάθη σου πριν τον αναλυτή",
                    "Ariadne më e zgjuar — AI në pajisje lexon gabimet e tua përpara analizuesit",
                )
            )
        }
        add(
            t(
                "Ask about weather — \"weather at Piraeus\", offline-safe from the last snapshot",
                "Ρώτα για τον καιρό — \"καιρός στον Πειραιά\", offline-safe από το τελευταίο snapshot",
                "Pyet për motin — \"moti në Piraeus\", offline i sigurt nga snapshot-i i fundit",
            )
        )
        add(
            t(
                "Time-anchored planning — \"airport by 21:30\" answers when to leave",
                "Σχεδιασμός με στόχο χρόνου — \"αεροδρόμιο στις 21:30\" σου λέει πότε να ξεκινήσεις",
                "Planifikim me kohë objektiv — \"aeroporti deri në 21:30\" të thotë kur të nisesh",
            )
        )
        add(
            t(
                "Severe-weather warnings on Home with emergency numbers (112, 199, 11185)",
                "Προειδοποιήσεις κακοκαιρίας στην Αρχική με τηλέφωνα έκτακτης ανάγκης (112, 199, 11185)",
                "Paralajmërime moti të keq në Home me numra emergjence (112, 199, 11185)",
            )
        )
        add(
            t(
                "Redesigned tracking card with an animated station strip and a Live Activity",
                "Ανανεωμένη κάρτα παρακολούθησης με στριπ σταθμών και Live Activity",
                "Karta e ndjekjes e ridizajnuar me strip stacionesh dhe një Live Activity",
            )
        )
        add(
            t(
                "Track any train — pick line, direction, station, departure",
                "Παρακολούθηση οποιουδήποτε τρένου — γραμμή, κατεύθυνση, σταθμός, δρομολόγιο",
                "Ndiq çdo tren — linjë, drejtim, stacion, nisje",
            )
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🦉", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = t(
                        if (hasLlm) "What's new in Syrmos — clever mode"
                        else "What's new in Syrmos",
                        if (hasLlm) "Τι νέο υπάρχει στο Syrmos — έξυπνη λειτουργία"
                        else "Τι νέο υπάρχει στο Syrmos",
                        if (hasLlm) "Çfarë ka të re në Syrmos — modaliteti i zgjuar"
                        else "Çfarë ka të re në Syrmos",
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                items.forEach { line ->
                    Text("•  $line", style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(t("Got it", "Εντάξει", "Në rregull"))
                }
            }
        }
    }
}
