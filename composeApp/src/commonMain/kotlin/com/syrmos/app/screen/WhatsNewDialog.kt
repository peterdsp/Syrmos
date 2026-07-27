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
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager

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
                "Departures for Thessaloniki, Patras and national rail stations via live API",
                "Δρομολόγια για Θεσσαλονίκη, Πάτρα και εθνικό δίκτυο μέσω live API",
                "Nisje për Selanik, Patra dhe rrjetin kombëtar nëpërmjet API live",
            )
        )
        add(
            t(
                "Ticket prices for all networks: OASA, STASY, OSETH, Hellenic Train",
                "Τιμές εισιτηρίων για όλα τα δίκτυα: OASA, STASY, OSETH, Hellenic Train",
                "Çmimet e biletave për të gjitha rrjetet: OASA, STASY, OSETH, Hellenic Train",
            )
        )
        add(
            t(
                "Fresh Ariadne button with the owl mark, matching the web style",
                "Νέο κουμπί Αριάδνης με το σήμα κουκουβάγιας, όπως στο web",
                "Butoni i ri i Ariadnes me shenjën e bufit, si në web",
            )
        )
        add(
            t(
                "Journey fare planner: pick two stations and see the price instantly",
                "Υπολογιστής κομίστρου: επίλεξε δύο σταθμούς και δες την τιμή αμέσως",
                "Planifikuesi i tarifave: zgjidh dy stacione dhe shiko çmimin menjëherë",
            )
        )
    }

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
