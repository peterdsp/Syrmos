package com.syrmos.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.network.SyrmosContactService
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/// Settings → "Contact developer" bottom sheet.
///
/// Slides up over the Settings screen rather than navigating away, so the
/// user keeps their place. Posts multipart/form-data to /api/contact, then
/// auto-dismisses on success. The KMP version is text-only — the iOS app's
/// native SwiftUI equivalent (ContactDeveloperView.swift) handles photo /
/// video attachments via PhotosPicker.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDeveloperSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        ContactDeveloperSheetContent(onDismiss = onDismiss)
    }
}

@Composable
private fun ContactDeveloperSheetContent(onDismiss: () -> Unit) {
    val lang by LocalizationManager.language.collectAsState()
    val service = koinInject<SyrmosContactService>()
    val scope = rememberCoroutineScope()

    var category by remember { mutableStateOf("bug") }
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = if (lang == AppLanguage.GREEK) "Επικοινωνία με τον προγραμματιστή" else "Contact developer",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        Text(
            text = if (lang == AppLanguage.GREEK)
                "Στείλε σχόλιο, αναφορά σφάλματος ή ιδέα. Όλα φτάνουν στον προγραμματιστή."
            else
                "Send feedback, a bug report or an idea. Every message lands in the developer's inbox.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (lang == AppLanguage.GREEK) "Κατηγορία" else "Category")
                    TextButton(onClick = { menuOpen = true }) {
                        Text(categoryLabel(category, lang))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        listOf("bug", "feature", "question", "other").forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(categoryLabel(cat, lang)) },
                                onClick = {
                                    category = cat
                                    menuOpen = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (lang == AppLanguage.GREEK) "Θέμα" else "Subject") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            if (lang == AppLanguage.GREEK)
                                "Email απάντησης (προαιρετικό)"
                            else
                                "Reply email (optional)"
                        )
                    },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    label = { Text(if (lang == AppLanguage.GREEK) "Μήνυμα" else "Message") },
                )
            }
        }

        statusBanner?.let { banner ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (statusIsError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = banner,
                    modifier = Modifier.padding(12.dp),
                    color = if (statusIsError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                enabled = !sending,
            ) {
                Text(if (lang == AppLanguage.GREEK) "Κλείσιμο" else "Close")
            }
            Button(
                onClick = {
                    if (sending || message.isBlank()) return@Button
                    scope.launch {
                        sending = true
                        statusBanner = null
                        val result = service.submit(
                            platform = currentPlatformId(),
                            message = message.trim(),
                            category = category,
                            subject = subject.trim().ifEmpty { null },
                            contactEmail = email.trim().ifEmpty { null },
                            appVersion = currentAppVersion(),
                            locale = if (lang == AppLanguage.GREEK) "el" else "en",
                            userAgent = currentPlatformUserAgent(),
                        )
                        sending = false
                        if (result != null) {
                            statusIsError = false
                            statusBanner = if (lang == AppLanguage.GREEK)
                                "Στάλθηκε. Αναφορά #${result.id}."
                            else
                                "Sent. Reference #${result.id}."
                            subject = ""
                            message = ""
                            email = ""
                            category = "bug"
                        } else {
                            statusIsError = true
                            statusBanner = if (lang == AppLanguage.GREEK)
                                "Δεν ήταν δυνατή η αποστολή. Έλεγξε τη σύνδεση και ξαναπροσπάθησε."
                            else
                                "Couldn't send. Check your connection and try again."
                        }
                    }
                },
                enabled = !sending && message.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text(if (lang == AppLanguage.GREEK) "Αποστολή" else "Send")
            }
        }
    }
}

private fun categoryLabel(c: String, lang: AppLanguage): String = when (c to lang) {
    "bug" to AppLanguage.GREEK -> "Σφάλμα"
    "feature" to AppLanguage.GREEK -> "Πρόταση"
    "question" to AppLanguage.GREEK -> "Ερώτηση"
    "other" to AppLanguage.GREEK -> "Άλλο"
    "bug" to AppLanguage.ENGLISH -> "Bug"
    "feature" to AppLanguage.ENGLISH -> "Feature"
    "question" to AppLanguage.ENGLISH -> "Question"
    else -> "Other"
}

/// Reported as "android" on phones / "web" in browsers. Implemented in each
/// source set so the API can split inbox traffic by client.
expect fun currentPlatformId(): String
expect fun currentPlatformUserAgent(): String
expect fun currentAppVersion(): String?
