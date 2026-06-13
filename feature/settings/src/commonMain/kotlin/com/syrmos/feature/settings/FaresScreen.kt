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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.data.sync.FaresRepository
import com.syrmos.core.network.SyrmosSchedulesService.FareProduct
import com.syrmos.core.network.SyrmosSchedulesService.InfoLink
import org.koin.compose.koinInject

private val SectionOrder = listOf("single", "offers", "airport", "passes")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaresScreen(onBack: () -> Unit) {
    val faresRepo = koinInject<FaresRepository>()
    val products by faresRepo.products.collectAsState()
    val infoLinks by faresRepo.infoLinks.collectAsState()
    val updatedAt by faresRepo.updatedAt.collectAsState()
    val lang by LocalizationManager.language.collectAsState()

    val grouped = products.groupBy { it.section }
    val ordered = SectionOrder.mapNotNull { key -> grouped[key]?.let { key to it } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (lang == AppLanguage.GREEK) "Εισιτήρια" else "Tickets",
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

            ordered.forEach { (key, items) ->
                item {
                    Text(
                        text = sectionTitle(key, lang),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                items(items) { product ->
                    FareCard(product = product, lang = lang)
                }
            }

            if (infoLinks.isNotEmpty()) {
                item {
                    Text(
                        text = if (lang == AppLanguage.GREEK) "Χρήσιμες πληροφορίες" else "Useful information",
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
            text = if (lang == AppLanguage.GREEK) "Τιμές εισιτηρίων OASA" else "OASA ticket prices",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (lang == AppLanguage.GREEK)
                "Συγχρονισμένο από τη επίσημη σελίδα τιμών της OASA. Οι ενημερώσεις γίνονται καθημερινά."
            else
                "Synced from OASA's official prices page. Updated daily.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (updatedAt.isNotEmpty()) {
            Text(
                text = (if (lang == AppLanguage.GREEK) "Ενημέρωση: " else "Updated: ") + updatedAt,
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
                    text = if (lang == AppLanguage.GREEK && product.titleEl.isNotEmpty())
                        product.titleEl else product.titleEn,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                product.fullPriceEur?.let { full ->
                    Text(
                        text = formatEur(full),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            product.discountedPriceEur?.let { disc ->
                Text(
                    text = (if (lang == AppLanguage.GREEK) "Μειωμένο: " else "Discounted: ") + formatEur(disc),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (product.validity.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .background(badgeColor(product).copy(alpha = 0.18f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = product.validity,
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
    val target = if (lang == AppLanguage.GREEK) link.urlEl.ifEmpty { link.urlEn } else link.urlEn
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { uriHandler.openUri(target) },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (lang == AppLanguage.GREEK) link.titleEl else link.titleEn,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = link.operatorId.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            text = if (lang == AppLanguage.GREEK)
                "Οι τιμές παρέχονται από την OASA. Για την οριστική τιμή ελέγξτε την επίσημη σελίδα."
            else
                "Prices are provided by OASA. For the authoritative figure, check the official page.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { uriHandler.openUri("https://www.oasa.gr/en/tickets/prices-of-products/") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (lang == AppLanguage.GREEK) "Άνοιγμα στην OASA" else "View on OASA")
        }
    }
}

private fun badgeColor(product: FareProduct): Color {
    val tag = product.tags.firstOrNull().orEmpty()
    return when {
        tag == "airport_express" || product.tags.contains("tourist") -> Color(0xFF0072CE)
        tag == "airport_excluded" -> Color(0xFFEA580C)
        else -> Color.Gray
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

private fun sectionTitle(key: String, lang: AppLanguage): String = when (key to lang) {
    "single" to AppLanguage.ENGLISH -> "Single tickets"
    "single" to AppLanguage.GREEK -> "Μονά εισιτήρια"
    "offers" to AppLanguage.ENGLISH -> "Packs and offers"
    "offers" to AppLanguage.GREEK -> "Πακέτα και προσφορές"
    "airport" to AppLanguage.ENGLISH -> "Airport tickets"
    "airport" to AppLanguage.GREEK -> "Εισιτήρια αεροδρομίου"
    "passes" to AppLanguage.ENGLISH -> "Day passes"
    "passes" to AppLanguage.GREEK -> "Ημερήσια εισιτήρια"
    else -> key.replaceFirstChar { it.uppercase() }
}

