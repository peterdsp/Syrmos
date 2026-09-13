package com.syrmos.app.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.domain.go.GoGuidance
import com.syrmos.core.domain.go.GuidanceJourney
import com.syrmos.core.domain.go.GuidancePosition
import com.syrmos.core.domain.go.JourneyGuidance
import androidx.compose.runtime.collectAsState

/**
 * GO live-guidance screen (Phase G / S06), the Android peer of the web
 * `SyrmosGoPanel` and iOS `GoJourneyView`. Guides the rider through a planned
 * [GuidanceJourney] one instruction at a time (board / stay on / get off next /
 * change here / arrived) from the shared, offline [GoGuidance] engine. The get-off
 * cue is emphasised because it's the one moment that matters most. Advancing is
 * MANUAL for now (GPS / live-position auto-advance is a later phase), so the
 * primary control is honestly labelled "Next stop", not implied live tracking.
 */
class GoJourneyScreenRoute(private val journey: GuidanceJourney) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val lang by LocalizationManager.language.collectAsState()
        var position by remember { mutableStateOf(GuidancePosition(0, 0)) }

        fun t(en: String, el: String, sq: String, it: String) = when (lang) {
            AppLanguage.GREEK -> el; AppLanguage.ALBANIAN -> sq; AppLanguage.ITALIAN -> it; else -> en
        }

        val guidance = GoGuidance.guidance(journey, position)
        val arrived = GoGuidance.isArrived(journey, position)
        val canBack = position.legIndex > 0 || position.stopIndex > 0

        // Progress = stops travelled / total stops across all legs.
        val total = journey.legs.sumOf { maxOf(0, it.stops.size - 1) }.coerceAtLeast(1)
        var done = 0
        for (i in 0 until position.legIndex) done += maxOf(0, journey.legs[i].stops.size - 1)
        done += position.stopIndex
        val progress = (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)

        val origin = journey.legs.firstOrNull()?.stops?.firstOrNull()?.name ?: ""
        val destination = journey.legs.lastOrNull()?.stops?.lastOrNull()?.name ?: ""

        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "Πίσω", "Prapa", "Indietro"))
                        }
                        Text(
                            t("Journey in progress", "Διαδρομή σε εξέλιξη", "Udhëtim në vazhdim", "Viaggio in corso"),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        )
                    }
                    OutlinedButton(onClick = { navigator.pop() }) {
                        Text(t("End", "Τέλος", "Përfundo", "Termina"))
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text("$origin → $destination", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                heroCard(guidance, ::t)

                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())

                if (arrived) {
                    Button(onClick = { navigator.pop() }, modifier = Modifier.fillMaxWidth()) {
                        Text(t("Finish journey", "Ολοκλήρωση", "Përfundo udhëtimin", "Concludi viaggio"))
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                position = if (position.stopIndex > 0) {
                                    GuidancePosition(position.legIndex, position.stopIndex - 1)
                                } else if (position.legIndex > 0) {
                                    val prev = position.legIndex - 1
                                    GuidancePosition(prev, journey.legs[prev].stops.lastIndex)
                                } else position
                            },
                            enabled = canBack, modifier = Modifier.weight(1f),
                        ) { Text(t("Back", "Πίσω", "Prapa", "Indietro")) }
                        Button(
                            onClick = { position = GoGuidance.advance(journey, position) },
                            modifier = Modifier.weight(1f),
                        ) { Text(t("Next stop", "Επόμενη στάση", "Ndalesa tjetër", "Prossima fermata")) }
                    }
                }

                Text(
                    t("Step through your journey. Live get-off alerts as you ride are coming next.",
                      "Προχώρα βήμα βήμα. Ζωντανές ειδοποιήσεις αποβίβασης έρχονται σύντομα.",
                      "Ec hap pas hapi. Sinjalizimet e drejtpërdrejta për zbritjen vijnë së shpejti.",
                      "Procedi passo passo. Gli avvisi di discesa dal vivo arrivano presto."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    @Composable
    private fun heroCard(g: JourneyGuidance, t: (String, String, String, String) -> String) {
        // The get-off cue is the one moment that matters most, so it is tinted.
        val emphasize = g is JourneyGuidance.GetOffNext
        val (stateLabel, headline, detail) = describe(g, t)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(24.dp),
                )
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val onHero = if (emphasize) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            val onHeroDim = if (emphasize) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
            Text(stateLabel, style = MaterialTheme.typography.labelMedium, color = onHeroDim)
            Text(headline, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = onHero)
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodyLarge, color = onHeroDim)
            }
        }
    }

    /** (stateLabel, headline, detail) for a guidance instruction, localized. */
    private fun describe(g: JourneyGuidance, t: (String, String, String, String) -> String): Triple<String, String, String> =
        when (g) {
            is JourneyGuidance.Board -> Triple(
                t("Ready to board", "Έτοιμος για επιβίβαση", "Gati për të hipur", "Pronto a salire"),
                t("Board ${g.lineId} toward ${g.towards}", "Επιβίβαση ${g.lineId} προς ${g.towards}", "Hip në ${g.lineId} drejt ${g.towards}", "Sali su ${g.lineId} verso ${g.towards}"),
                t("${g.stopsRemaining} stops · next ${g.nextStation}", "${g.stopsRemaining} στάσεις · επόμενη ${g.nextStation}", "${g.stopsRemaining} ndalesa · tjetra ${g.nextStation}", "${g.stopsRemaining} fermate · prossima ${g.nextStation}"),
            )
            is JourneyGuidance.Ride -> Triple(
                t("Riding", "Σε κίνηση", "Duke udhëtuar", "In viaggio"),
                t("Stay on ${g.lineId} toward ${g.towards}", "Μείνε στη ${g.lineId} προς ${g.towards}", "Qëndro në ${g.lineId} drejt ${g.towards}", "Resta su ${g.lineId} verso ${g.towards}"),
                t("${g.stopsRemaining} stops · next ${g.nextStation}", "${g.stopsRemaining} στάσεις · επόμενη ${g.nextStation}", "${g.stopsRemaining} ndalesa · tjetra ${g.nextStation}", "${g.stopsRemaining} fermate · prossima ${g.nextStation}"),
            )
            is JourneyGuidance.GetOffNext -> Triple(
                t("Alight soon", "Αποβίβαση σύντομα", "Zbrit së shpejti", "Scendi a breve"),
                t("Get off next: ${g.nextStation}", "Αποβίβαση στην επόμενη: ${g.nextStation}", "Zbrit në tjetrën: ${g.nextStation}", "Scendi alla prossima: ${g.nextStation}"),
                if (g.isDestination)
                    t("Your destination is next", "Ο προορισμός σου είναι η επόμενη", "Destinacioni yt është tjetra", "La tua destinazione è la prossima")
                else if (g.transferTo != null)
                    t("Then change to ${g.transferTo}", "Μετά αλλαγή σε ${g.transferTo}", "Pastaj ndërro në ${g.transferTo}", "Poi cambia in ${g.transferTo}")
                else "",
            )
            is JourneyGuidance.Transfer -> Triple(
                t("Transfer", "Μετεπιβίβαση", "Ndërrim", "Cambio"),
                t("Change to ${g.toLineId} toward ${g.towards}", "Αλλαγή σε ${g.toLineId} προς ${g.towards}", "Ndërro në ${g.toLineId} drejt ${g.towards}", "Cambia su ${g.toLineId} verso ${g.towards}"),
                t("at ${g.atStation}", "στον σταθμό ${g.atStation}", "te ${g.atStation}", "a ${g.atStation}"),
            )
            is JourneyGuidance.Arrived -> Triple(
                t("Arrived", "Έφτασες", "Mbërritët", "Arrivato"),
                t("You've arrived at ${g.station}", "Έφτασες στον ${g.station}", "Mbërritët në ${g.station}", "Sei arrivato a ${g.station}"),
                "",
            )
        }
}
