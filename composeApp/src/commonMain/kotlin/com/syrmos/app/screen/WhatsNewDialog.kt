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
                "Hellenic Rail Atlas: a fresh light-first design built around one-glance answers",
                "Hellenic Rail Atlas: νεος σχεδιασμος με απαντησεις στη μια ματια",
                "Hellenic Rail Atlas: dizajn i ri me pergjigje ne nje shikim",
            )
        )
        add(
            t(
                "Ariadne now links to stations and lines: tap any answer to jump straight there",
                "Η Αριαδνη τωρα συνδεεται με σταθμους και γραμμες: πατα μια απαντηση και πηγαινε κατευθειαν",
                "Ariadne tani lidhet me stacione dhe linja: prek nje pergjigje dhe shko direkt",
            )
        )
        add(
            t(
                "Browse All Stations with interactive maps, line pills and interchange badges",
                "Περιηγηση σε ολους τους σταθμους με χαρτη, ετικετες γραμμων και κομβους ανταποκρισης",
                "Shfleto te gjitha stacionet me harta, etiketa linjash dhe nyje nderkembimi",
            )
        )
        add(
            t(
                "Redesigned Explore tab with actionable destination cards and recent stations",
                "Ανανεωμενη καρτελα Εξερευνηση με καρτες προορισμων και προσφατους σταθμους",
                "Kartela Eksploro e ridizajnuar me karta destinacionesh dhe stacione te fundit",
            )
        )
        add(
            t(
                "Operators directory and map preferences in the new More tab",
                "Καταλογος φορεων και ρυθμισεις χαρτη στη νεα καρτελα Περισσοτερα",
                "Drejtori operatoresh dhe preferenca harte ne kartelen e re Me shume",
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
