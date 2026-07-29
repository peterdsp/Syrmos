package com.syrmos.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.domain.fares.ComputeFareUseCase
import com.syrmos.core.domain.fares.FareStation
import com.syrmos.core.domain.usecase.GetLinesUseCase
import com.syrmos.core.model.fares.FareQuote
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineType
import com.syrmos.core.model.transit.Region
import com.syrmos.core.model.transit.Station
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.data.sync.FaresRepository
import com.syrmos.core.designsystem.theme.tokens.SyrmosColorTokens
import com.syrmos.core.network.SyrmosSchedulesService.FareProduct
import com.syrmos.core.network.SyrmosSchedulesService.InfoLink
import org.koin.compose.koinInject

/**
 * The fares screen now spans every network, not just OASA. Products are grouped
 * under a network header; Athens keeps its OASA sub-sections, the others are a
 * single flat list. Sources per operator; see docs/data/2026-07-27-fares-collection.md.
 */
private data class FareNetwork(
    val sections: List<String>,
    val labelEn: String,
    val labelEl: String,
    val labelSq: String,
)

private val Networks = listOf(
    FareNetwork(listOf("single", "offers", "airport", "passes"), "Athens — OASA", "Αθήνα — OASA", "Athinë — OASA"),
    FareNetwork(listOf("thessaloniki"), "Thessaloniki — OSETH", "Θεσσαλονίκη — OSETH", "Selanik — OSETH"),
    FareNetwork(listOf("patras"), "Patras suburban", "Προαστιακός Πάτρας", "Suburban Patra"),
    FareNetwork(listOf("intercity"), "Intercity / regional", "Υπεραστικά / περιφερειακά", "Ndërqytetëse"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaresScreen(onBack: () -> Unit) {
    val faresRepo = koinInject<FaresRepository>()
    val products by faresRepo.products.collectAsState()
    val infoLinks by faresRepo.infoLinks.collectAsState()
    val updatedAt by faresRepo.updatedAt.collectAsState()
    val lang by LocalizationManager.language.collectAsState()

    // For the from -> to fare planner: stations + operational lines (region).
    val stationRepo = koinInject<StationRepositoryImpl>()
    val getLines = koinInject<GetLinesUseCase>()
    val stations by stationRepo.getAllStations().collectAsState(initial = emptyList())
    val lines by getLines.getOperationalLines().collectAsState(initial = emptyList())

    val bySection = products.groupBy { it.section }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (lang) {
                            AppLanguage.GREEK -> "Εισιτήρια"
                            AppLanguage.ALBANIAN -> "Bileta"
                            else -> "Tickets"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(lang = lang, updatedAt = updatedAt) }

            if (stations.isNotEmpty()) {
                item { FarePlanner(stations = stations, lines = lines, lang = lang) }
            }

            Networks.forEach { network ->
                if (network.sections.none { bySection[it]?.isNotEmpty() == true }) return@forEach
                item {
                    Text(
                        text = when (lang) {
                            AppLanguage.GREEK -> network.labelEl
                            AppLanguage.ALBANIAN -> network.labelSq
                            else -> network.labelEn
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                    )
                }
                network.sections.forEach { section ->
                    val secProducts = bySection[section].orEmpty()
                    if (secProducts.isEmpty()) return@forEach
                    // Sub-section header only where a network has several (Athens).
                    if (network.sections.size > 1) {
                        item {
                            Text(
                                text = sectionTitle(section, lang),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                            )
                        }
                    }
                    items(secProducts) { product ->
                        FareCard(product = product, lang = lang)
                    }
                }
            }

            if (infoLinks.isNotEmpty()) {
                item {
                    Text(
                        text = when (lang) {
                            AppLanguage.GREEK -> "Χρήσιμες πληροφορίες"
                            AppLanguage.ALBANIAN -> "Informacione të dobishme"
                            else -> "Useful information"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                items(infoLinks) { link ->
                    InfoLinkCard(link = link, lang = lang)
                }
            }

            item { Footer(lang = lang) }
        }
    }
}

@Composable
private fun Header(lang: AppLanguage, updatedAt: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = when (lang) {
                AppLanguage.GREEK -> "Τιμές εισιτηρίων"
                AppLanguage.ALBANIAN -> "Çmimet e biletave"
                else -> "Fares"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = when (lang) {
                AppLanguage.GREEK -> "Τιμές από τους επίσημους φορείς (OASA, OSETH, Hellenic Train). Τα υπεραστικά τιμολογούνται στην κράτηση."
                AppLanguage.ALBANIAN -> "Çmime nga operatorët zyrtarë (OASA, OSETH, Hellenic Train). Ndërqytetëset çmohen në rezervim."
                else -> "Prices from the official operators (OASA, OSETH, Hellenic Train). Intercity is priced at booking."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (updatedAt.isNotEmpty()) {
            Text(
                text = (when (lang) {
                    AppLanguage.GREEK -> "Ενημέρωση: "
                    AppLanguage.ALBANIAN -> "Përditësuar: "
                    else -> "Updated: "
                }) + updatedAt,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun FareCard(product: FareProduct, lang: AppLanguage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (lang) {
                        AppLanguage.GREEK -> if (product.titleEl.isNotEmpty()) product.titleEl else product.titleEn
                        AppLanguage.ALBANIAN -> if (product.titleSq.isNotEmpty()) product.titleSq else product.titleEn
                        else -> product.titleEn
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    // Intercity has no fixed price (booking-time); show that
                    // honestly instead of a fabricated number or a blank.
                    text = product.fullPriceEur?.let { formatEur(it) } ?: when (lang) {
                        AppLanguage.GREEK -> "στην κράτηση"
                        AppLanguage.ALBANIAN -> "në rezervim"
                        else -> "at booking"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            product.discountedPriceEur?.let { disc ->
                Text(
                    text = (when (lang) {
                        AppLanguage.GREEK -> "Μειωμένο: "
                        AppLanguage.ALBANIAN -> "Me zbritje: "
                        else -> "Discounted: "
                    }) + formatEur(disc),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val displayValidity = when (lang) {
                AppLanguage.ALBANIAN -> product.validitySq.ifEmpty { product.validity }
                else -> product.validity
            }
            if (displayValidity.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .background(badgeColor(product).copy(alpha = 0.18f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = displayValidity,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor(product),
                    )
                }
            }
            if (product.notes.isNotEmpty()) {
                Text(
                    text = product.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                )
            }
        }
    }
}

@Composable
private fun InfoLinkCard(link: InfoLink, lang: AppLanguage) {
    val uriHandler = LocalUriHandler.current
    val target = when (lang) {
        AppLanguage.GREEK -> link.urlEl.ifEmpty { link.urlEn }
        AppLanguage.ALBANIAN -> link.urlSq.ifEmpty { link.urlEn }
        else -> link.urlEn
    }
    val title = when (lang) {
        AppLanguage.GREEK -> link.titleEl
        AppLanguage.ALBANIAN -> link.titleSq.ifEmpty { link.titleEn }
        else -> link.titleEn
    }
    val summary = when (lang) {
        AppLanguage.GREEK -> link.summaryEl
        AppLanguage.ALBANIAN -> link.summarySq.ifEmpty { link.summaryEn }
        else -> link.summaryEn
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = link.operatorId.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (summary.isNotEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (link.bullets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    link.bullets.forEach { bullet ->
                        val text = when (lang) {
                            AppLanguage.GREEK -> bullet.el
                            AppLanguage.ALBANIAN -> bullet.sq.ifEmpty { bullet.en }
                            else -> bullet.en
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape)
                    .clickable { uriHandler.openUri(target) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = (when (lang) {
                        AppLanguage.GREEK -> "Επιβεβαίωση στο "
                        AppLanguage.ALBANIAN -> "Verifiko në "
                        else -> "Verify on "
                    }) + link.operatorId.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun Footer(lang: AppLanguage) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = when (lang) {
                AppLanguage.GREEK -> "Οι τιμές παρέχονται από την OASA. Για την οριστική τιμή ελέγξτε την επίσημη σελίδα."
                AppLanguage.ALBANIAN -> "Çmimet ofrohen nga OASA. Për çmimin përfundimtar, kontrollo faqen zyrtare."
                else -> "Prices are provided by OASA. For the authoritative figure, check the official page."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { uriHandler.openUri("https://www.oasa.gr/en/tickets/prices-of-products/") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(when (lang) {
                AppLanguage.GREEK -> "Άνοιγμα στην OASA"
                AppLanguage.ALBANIAN -> "Hap në OASA"
                else -> "View on OASA"
            })
        }
    }
}

/** From -> to fare picker: two station autocompletes -> a grounded fare card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FarePlanner(stations: List<Station>, lines: List<Line>, lang: AppLanguage) {
    var from by remember { mutableStateOf<Station?>(null) }
    var to by remember { mutableStateOf<Station?>(null) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = when (lang) {
                    AppLanguage.GREEK -> "Υπολόγισε κόμιστρο"
                    AppLanguage.ALBANIAN -> "Llogarit çmimin"
                    else -> "Plan a trip — fare"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            StationField(
                label = when (lang) { AppLanguage.GREEK -> "Από"; AppLanguage.ALBANIAN -> "Nga"; else -> "From" },
                stations = stations, lang = lang, onSelect = { from = it },
            )
            StationField(
                label = when (lang) { AppLanguage.GREEK -> "Προς"; AppLanguage.ALBANIAN -> "Te"; else -> "To" },
                stations = stations, lang = lang, onSelect = { to = it },
            )
            val f = from
            val t = to
            if (f != null && t != null) {
                val quote = remember(f, t, lines) {
                    ComputeFareUseCase().invoke(fareStationOf(f, lines), fareStationOf(t, lines))
                }
                FareResult(quote = quote, lang = lang)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationField(label: String, stations: List<Station>, lang: AppLanguage, onSelect: (Station) -> Unit) {
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(query, stations) {
        if (query.length < 2) emptyList()
        else stations.filter { stationLabel(it, lang).contains(query, ignoreCase = true) }.take(8)
    }
    ExposedDropdownMenuBox(
        expanded = expanded && filtered.isNotEmpty(),
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; expanded = true },
            label = { Text(label) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && filtered.isNotEmpty()) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            filtered.forEach { st ->
                DropdownMenuItem(
                    text = { Text(stationLabel(st, lang)) },
                    onClick = {
                        onSelect(st)
                        query = stationLabel(st, lang)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun FareResult(quote: FareQuote, lang: AppLanguage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = if (quote.dynamic) {
                when (lang) { AppLanguage.GREEK -> "στην κράτηση"; AppLanguage.ALBANIAN -> "në rezervim"; else -> "at booking" }
            } else {
                val reducedWord = when (lang) { AppLanguage.GREEK -> "μειωμένο "; AppLanguage.ALBANIAN -> "e reduktuar "; else -> "reduced " }
                formatEur(quote.fullPriceEur ?: 0.0) +
                    (quote.reducedPriceEur?.let { " · $reducedWord${formatEur(it)}" } ?: "")
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "${quote.product} · ${quote.operator}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!quote.note.isNullOrBlank() && quote.dynamic) {
            Text(
                text = quote.note!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun stationLabel(st: Station, lang: AppLanguage): String =
    when (lang) { AppLanguage.GREEK -> st.nameEl.ifBlank { st.name }; else -> st.name }

private fun fareStationOf(st: Station, lines: List<Line>): FareStation {
    val regions = st.lineIds.mapNotNull { lid -> lines.firstOrNull { it.id == lid }?.region }.toSet()
    val suburban = st.lineIds.any { lid ->
        lines.firstOrNull { it.id == lid }?.let { it.region == Region.THESSALONIKI && it.type == LineType.SUBURBAN } == true
    }
    val name = (st.name + " " + st.nameEl).lowercase()
    return FareStation(
        id = st.id,
        regions = regions.ifEmpty { setOf(Region.ATHENS) },
        isAirport = "airport" in name || "αεροδρ" in name,
        isSuburban = suburban,
    )
}

private fun badgeColor(product: FareProduct): Color {
    val tag = product.tags.firstOrNull().orEmpty()
    return when {
        tag == "airport_express" || product.tags.contains("tourist") -> SyrmosColorTokens.metroBlue
        tag == "airport_excluded" -> SyrmosColorTokens.estimated
        else -> SyrmosColorTokens.offline
    }
}

private fun formatEur(value: Double): String {
    // Cross-target euro formatter. Wasm/JS doesn't ship java.text.NumberFormat
    // or String.format, so we compose the digits ourselves: round to cents,
    // split euros and cents, pad cents to two digits. Always reads as
    // "€1.20", "€20.00", "€2.50".
    val cents = kotlin.math.round(value * 100.0).toLong()
    val euros = cents / 100
    val rem = (cents % 100).toString().padStart(2, '0')
    return "€$euros.$rem"
}

private fun sectionTitle(key: String, lang: AppLanguage): String = when (key) {
    "single" -> when (lang) {
        AppLanguage.GREEK -> "Μονά εισιτήρια"; AppLanguage.ALBANIAN -> "Bileta të thjeshta"; else -> "Single tickets"
    }
    "offers" -> when (lang) {
        AppLanguage.GREEK -> "Πακέτα και προσφορές"; AppLanguage.ALBANIAN -> "Paketa dhe oferta"; else -> "Packs and offers"
    }
    "airport" -> when (lang) {
        AppLanguage.GREEK -> "Εισιτήρια αεροδρομίου"; AppLanguage.ALBANIAN -> "Bileta aeroporti"; else -> "Airport tickets"
    }
    "passes" -> when (lang) {
        AppLanguage.GREEK -> "Ημερήσια εισιτήρια"; AppLanguage.ALBANIAN -> "Bileta ditore"; else -> "Day passes"
    }
    else -> key.replaceFirstChar { it.uppercase() }
}

