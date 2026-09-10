package com.syrmos.app.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.syrmos.core.data.repository.LineRepositoryImpl
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.domain.journey.JourneyPlanAdapter
import com.syrmos.core.domain.usecase.PlanJourneyUseCase
import com.syrmos.core.model.journey.FeasibilityStatus
import com.syrmos.core.model.journey.JourneyOption
import com.syrmos.core.model.journey.LegKind
import com.syrmos.core.model.journey.Ranking
import com.syrmos.core.model.transit.Station
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject

/**
 * Android mirror of the web Plan flow (Phase P). Pick From/To, Find routes, and
 * see a ranked option with feasibility, powered by the SAME Kotlin engines the
 * web build mirrors (PlanJourneyUseCase -> JourneyPlanAdapter -> feasibility +
 * ranker). Times are estimated (no schedule), shown honestly with a "~" and an
 * "Estimated times" chip, never a fabricated clock.
 */
class PlanScreenRoute : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val stationRepo = koinInject<StationRepositoryImpl>()
        val lineRepo = koinInject<LineRepositoryImpl>()
        val useCase = remember { PlanJourneyUseCase(stationRepo, lineRepo) }
        val scope = rememberCoroutineScope()
        val lang by LocalizationManager.language.collectAsState()

        var stations by remember { mutableStateOf<List<Station>>(emptyList()) }
        var fromId by remember { mutableStateOf<String?>(null) }
        var toId by remember { mutableStateOf<String?>(null) }
        var open by remember { mutableStateOf<String?>(null) } // "from" | "to" | null
        var query by remember { mutableStateOf("") }
        var option by remember { mutableStateOf<JourneyOption?>(null) }
        var planned by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) { stations = stationRepo.getAllStations().first() }

        fun name(id: String?): String {
            val s = stations.firstOrNull { it.id == id } ?: return "-"
            return if (lang == AppLanguage.GREEK) s.nameEl else s.name
        }
        fun t(en: String, el: String, sq: String, it: String) = when (lang) {
            AppLanguage.GREEK -> el; AppLanguage.ALBANIAN -> sq; AppLanguage.ITALIAN -> it; else -> en
        }

        fun runPlan() {
            val f = fromId; val to = toId
            if (f == null || to == null) return
            scope.launch {
                val result = useCase.invoke(f, to).first()
                val serviceDate = Clock.System.now().toLocalDateTime(TimeZone.of("Europe/Athens")).date
                option = JourneyPlanAdapter.toOptions(result, Ranking.FASTEST, serviceDate).firstOrNull()
                planned = true
            }
        }

        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Back", "Πίσω", "Prapa", "Indietro"))
                    }
                    Text(
                        t("Plan a journey", "Σχεδίασε διαδρομή", "Planifiko udhëtim", "Pianifica un viaggio"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                endpointRow(t("From", "Από", "Nga", "Da"), name(fromId)) { open = if (open == "from") null else "from" }
                endpointRow(t("To", "Προς", "Për", "A"), name(toId)) { open = if (open == "to") null else "to" }

                if (open != null) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(t("Search station", "Αναζήτηση σταθμού", "Kërko stacion", "Cerca stazione")) },
                    )
                    val q = query.trim().lowercase()
                    val matches = stations.filter {
                        q.isEmpty() || it.name.lowercase().contains(q) || it.nameEl.lowercase().contains(q)
                    }.take(40)
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                        items(matches, key = { it.id }) { st ->
                            Text(
                                text = if (lang == AppLanguage.GREEK) st.nameEl else st.name,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (open == "from") fromId = st.id else toId = st.id
                                    open = null; query = ""
                                }.padding(vertical = 14.dp, horizontal = 4.dp),
                            )
                        }
                    }
                }

                Button(
                    onClick = { runPlan() },
                    enabled = fromId != null && toId != null && open == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(t("Find routes", "Βρες διαδρομές", "Gjej rrugët", "Trova percorsi")) }

                val opt = option
                if (planned && opt == null) {
                    Text(
                        t("No route found.", "Δεν βρέθηκε διαδρομή.", "Nuk u gjet rrugë.", "Nessun percorso trovato."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (opt != null) {
                    ResultCard(opt, lang, ::t)
                }
            }
        }
    }

    @Composable
    private fun endpointRow(label: String, value: String, onClick: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(12.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }

    @Composable
    private fun ResultCard(opt: JourneyOption, lang: AppLanguage, t: (String, String, String, String) -> String) {
        val minutes = ((opt.durationSeconds ?: 0) / 60).coerceAtLeast(1)
        val chain = opt.legs.filter { it.kind == LegKind.RIDE }.mapNotNull { it.lineId }.joinToString(" → ")
        val changes = if (opt.transferCount == 1) t("1 change", "1 αλλαγή", "1 ndërrim", "1 cambio")
        else "${opt.transferCount} " + t("changes", "αλλαγές", "ndërrime", "cambi")
        val feas = when (opt.feasibility.status) {
            FeasibilityStatus.COMFORTABLE -> t("Comfortable", "Άνετη", "Komode", "Comoda")
            FeasibilityStatus.TIGHT -> t("Tight", "Στενή", "E ngushtë", "Stretta")
            FeasibilityStatus.MISSED -> t("Missed", "Χαμένη", "Humbur", "Persa")
            FeasibilityStatus.UNKNOWN -> t("Estimated times", "Εκτιμώμενοι χρόνοι", "Kohë të vlerësuara", "Orari stimato")
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("1 " + t("route", "διαδρομή", "rrugë", "percorso"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("~$minutes " + t("min", "λεπ", "min", "min") + " · $changes · $chain", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(feas, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
