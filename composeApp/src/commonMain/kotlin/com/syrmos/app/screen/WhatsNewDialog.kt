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
                "Search and filter lines: find any line by city or transport type",
                "Αναζητηση και φιλτρα στις γραμμες: βρες γραμμη κατα πολη η τυπο μεταφορας",
                "Kerkim dhe filtra ne linja: gjej linjen sipas qytetit ose llojit te transportit",
            )
        )
        add(
            t(
                "Departures for Thessaloniki, Patras and national rail stations via live API",
                "Δρομολογια για Θεσσαλονικη, Πατρα και εθνικο δικτυο μεσω live API",
                "Nisje per Selanik, Patra dhe rrjetin kombetar nepermjet API live",
            )
        )
        add(
            t(
                "Ticket prices for all networks: OASA, STASY, OSETH, Hellenic Train",
                "Τιμες εισιτηριων για ολα τα δικτυα: OASA, STASY, OSETH, Hellenic Train",
                "Cmimet e biletave per te gjitha rrjetet: OASA, STASY, OSETH, Hellenic Train",
            )
        )
        add(
            t(
                "Journey fare planner: pick two stations and see the price instantly",
                "Υπολογιστης κομιστρου: επιλεξε δυο σταθμους και δες την τιμη αμεσως",
                "Planifikuesi i tarifave: zgjidh dy stacione dhe shiko cmimin menjehere",
            )
        )
        add(
            t(
                "Push notifications: service alerts near you, weather warnings and a morning briefing",
                "Ειδοποιησεις: ειδοποιησεις υπηρεσιων κοντα σου, καιρικες προειδοποιησεις και πρωινη ενημερωση",
                "Njoftime push: njoftime sherbimi prane teje, paralajmerime moti dhe informim mengjesit",
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
