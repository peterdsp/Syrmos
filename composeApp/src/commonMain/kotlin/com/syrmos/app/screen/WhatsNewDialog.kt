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
 * last-seen version (see readLastWhatsNewVersion / markWhatsNewSeen). Purely
 * informational for existing users, so no store link — just what's new.
 */
@Composable
fun WhatsNewDialog(onDismiss: () -> Unit) {
    val lang by LocalizationManager.language.collectAsState()
    fun t(en: String, el: String, sq: String) = when (lang) {
        AppLanguage.GREEK -> el
        AppLanguage.ALBANIAN -> sq
        else -> en
    }
    val items = listOf(
        t(
            "Ask Ariadne — the offline assistant for departures, trips and last trains",
            "Ρώτα την Αριάδνη — τον offline βοηθό για αναχωρήσεις, διαδρομές και τελευταία τρένα",
            "Pyet Ariadnen — asistenti offline për nisjet, udhëtimet dhe trenat e fundit",
        ),
        t(
            "Smarter search that understands typos (nikea → Nikaia)",
            "Πιο έξυπνη αναζήτηση που καταλαβαίνει τα λάθη (nikea → Νίκαια)",
            "Kërkim më i zgjuar që kupton gabimet (nikea → Nikaia)",
        ),
        t(
            "\"How long to…\" travel-time answers from your location",
            "Απαντήσεις χρόνου \"Πόση ώρα για…\" από την τοποθεσία σου",
            "Përgjigje kohe \"Sa gjatë te…\" nga vendndodhja jote",
        ),
        t(
            "Track any train, plus a Home Screen widget for next departures",
            "Παρακολούθησε κάθε τρένο, με widget στην Αρχική οθόνη για επόμενες αναχωρήσεις",
            "Ndiq çdo tren, plus një widget në Ekranin Kryesor për nisjet",
        ),
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🦉", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = t("What's new in Syrmos", "Τι νέο υπάρχει στο Syrmos", "Çfarë ka të re në Syrmos"),
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
