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
    fun t(en: String, el: String, sq: String) = when (lang) {
        AppLanguage.GREEK -> el
        AppLanguage.ALBANIAN -> sq
        else -> en
    }

    val items = buildList {
        add(
            t(
                "One-glance hero: your next train counts down live on the home screen",
                "Αντιστροφη μετρηση: το επομενο τρενο σου μετραει ζωντανα στην αρχικη",
                "Countdown hero: treni yt i rradhes numeron ne kohe reale ne ekranin kryesor",
            )
        )
        add(
            t(
                "Curated destinations: tap Airport, Piraeus, Thessaloniki or Meteora to see departures instantly",
                "Προορισμοι: πατα Αεροδρομιο, Πειραια, Θεσσαλονικη η Μετεωρα και δες αμεσα αναχωρησεις",
                "Destinacione: shtyp Aeroport, Pire, Selanik ose Meteora dhe shiko nisjet menjehere",
            )
        )
        add(
            t(
                "Universal departures: every station on every line now has a full timetable",
                "Καθολικες αναχωρησεις: καθε σταθμος σε καθε γραμμη εχει πληρες ωρολογιο",
                "Nisje universale: cdo stacion ne cdo linje tani ka orar te plote",
            )
        )
        add(
            t(
                "Live vehicles on the map: see trains move in real time across the network",
                "Ζωντανα οχηματα στον χαρτη: δες τα τρενα να κινουνται σε πραγματικο χρονο",
                "Mjete te gjalla ne harte: shiko trenat qe levizin ne kohe reale",
            )
        )
        add(
            t(
                "Ariadne assistant: ask about routes, fares and schedules in Greek, English or Albanian",
                "Βοηθος Ariadne: ρωτα για δρομολογια, εισιτηρια και ωραρια στα ελληνικα, αγγλικα η αλβανικα",
                "Asistenti Ariadne: pyet per rruge, bileta dhe orare ne greqisht, anglisht ose shqip",
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
