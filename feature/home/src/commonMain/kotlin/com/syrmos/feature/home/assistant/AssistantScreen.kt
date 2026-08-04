package com.syrmos.feature.home.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import com.syrmos.core.designsystem.component.SourceConfidenceChip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import com.syrmos.core.common.AriadneModelState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager

/**
 * Ariadne's chat surface for Android and Web. A full-screen overlay with a
 * conversation list and a single text input. Answers can carry a navigation
 * action (open station / open line) which is bubbled up to the host.
 */
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    onClose: () -> Unit,
    onOpenStation: (String) -> Unit,
    onOpenLine: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val lang by LocalizationManager.language.collectAsState()
    val modelState by viewModel.modelState.collectAsState()
    val modelProgress by viewModel.modelProgress.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Text("‹", style = MaterialTheme.typography.headlineSmall)
                }
                Text(
                    text = "Ariadne",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = subtitle(lang),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AriadneModelCard(
                state = modelState,
                progress = modelProgress,
                onDownload = { viewModel.downloadModel() },
                lang = lang,
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(uiState.messages, key = { it.id }) { msg ->
                    MessageBubble(msg, onOpenStation, onOpenLine)
                }
                if (uiState.thinking) {
                    item(key = "typing") { TypingIndicator() }
                }
            }

            // Suggestion chips: show while the conversation is still at
            // the greeting so a first-time user has one-tap prompts.
            // Context-aware — the chip triplet reflects current weather
            // severity + time-of-day so the offering matches the moment.
            if (uiState.messages.size <= 1 && !uiState.thinking) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = tryLabel(lang),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val prompts = suggestions(
                            lang = lang,
                            severeWeather = uiState.severeWeather,
                            hour = uiState.athensHour,
                        )
                        prompts.forEach { prompt ->
                            SuggestedPromptChip(prompt) {
                                viewModel.ask(prompt)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(placeholder(lang)) },
                    enabled = uiState.ready,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        viewModel.ask(input); input = ""
                    }),
                )
                IconButton(
                    onClick = { viewModel.ask(input); input = "" },
                    enabled = uiState.ready && input.isNotBlank(),
                ) {
                    Text("➤", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    msg: AssistantMessage,
    onOpenStation: (String) -> Unit,
    onOpenLine: (String) -> Unit,
) {
    val alignment = if (msg.fromUser) Alignment.CenterEnd else Alignment.CenterStart
    val shape = RoundedCornerShape(18.dp)
    val fg = if (msg.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Row(verticalAlignment = Alignment.Top) {
            if (!msg.fromUser) {
                // Owl avatar on the left of assistant bubbles, matching
                // the iOS treatment. Adds identity + gives the eye
                // something to land on before reading the message.
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "🦉",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .shadow(
                    elevation = if (msg.fromUser) 6.dp else 3.dp,
                    shape = shape,
                    ambientColor = MaterialTheme.colorScheme.primary.copy(
                        alpha = if (msg.fromUser) 0.28f else 0.06f
                    ),
                    spotColor = MaterialTheme.colorScheme.primary.copy(
                        alpha = if (msg.fromUser) 0.28f else 0.06f
                    ),
                )
                .background(
                    if (msg.fromUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    shape,
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = msg.text, style = MaterialTheme.typography.bodyMedium, color = fg)
            msg.departures.forEach { dep ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${dep.lineId} · ${dep.time}", style = MaterialTheme.typography.labelLarge, color = fg)
                    Text(
                        if (dep.minutesAway <= 1) "now" else "${dep.minutesAway} min",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = fg,
                    )
                }
            }
            val action = msg.action
            if (action != null && msg.actionLabel != null) {
                Text(
                    text = "${msg.actionLabel} ›",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (msg.fromUser) fg else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        when (action) {
                            is AssistantAction.OpenStation -> onOpenStation(action.stationId)
                            is AssistantAction.OpenLine -> onOpenLine(action.lineId)
                        }
                    },
                )
            }
            if (!msg.fromUser && msg.sourceConfidence != null) {
                SourceConfidenceChip(confidence = msg.sourceConfidence)
            }
        }
        }  // Row (avatar + bubble)
    }
}

private fun subtitle(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Ο έξυπνος οδηγός συγκοινωνιών σου"
    AppLanguage.ALBANIAN -> "Udhezuesi yt i mencur i transportit"
    else -> "Your smart transit guide"
}

private fun placeholder(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "Ρώτησε για τρένα, σταθμούς, διαδρομές…"
    AppLanguage.ALBANIAN -> "Pyet për trena, stacione, udhëtime…"
    else -> "Ask about trains, stations, routes…"
}

private fun tryLabel(lang: AppLanguage) = when (lang) {
    AppLanguage.GREEK -> "ΔΟΚΙΜΑΣΕ"
    AppLanguage.ALBANIAN -> "PROVO"
    else -> "TRY"
}

/**
 * Context-aware quick-tap prompt triplet. Rules:
 *   severeWeather -> lead with a covered-route trip prompt
 *   hour >= 20    -> late-evening: last-train reminder
 *   hour < 9      -> morning: airport check
 *   otherwise     -> the default triplet
 */
private fun suggestions(
    lang: AppLanguage,
    severeWeather: Boolean,
    hour: Int,
): List<String> = when (lang) {
    AppLanguage.GREEK -> when {
        severeWeather -> listOf("Πώς πάω στο σπίτι υπόγεια;", "Κακοκαιρία τώρα", "Ειδοποιήσεις γραμμών")
        hour >= 20 -> listOf("Τελευταίο M2", "Καιρός τώρα", "Πώς πάω στο σπίτι;")
        hour < 9 -> listOf("Επόμενο M3 στο Αεροδρόμιο", "Καιρός τώρα", "Πώς πάω στο κέντρο;")
        else -> listOf("Καιρός τώρα", "Πώς πάω στο Αεροδρόμιο;", "Τελευταίο M2")
    }
    AppLanguage.ALBANIAN -> when {
        severeWeather -> listOf("Si shkoj në shtëpi nën tokë?", "Mot i keq tani", "Njoftime linjash")
        hour >= 20 -> listOf("Treni i fundit M2", "Moti tani", "Si shkoj në shtëpi?")
        hour < 9 -> listOf("M3 tjetër për Aeroport", "Moti tani", "Si shkoj në qendër?")
        else -> listOf("Moti tani", "Si shkoj në Aeroport?", "Treni i fundit M2")
    }
    else -> when {
        severeWeather -> listOf("How do I get home covered?", "Severe weather now", "Line alerts")
        hour >= 20 -> listOf("Last M2", "Weather now", "How do I get home?")
        hour < 9 -> listOf("Next M3 to Airport", "Weather now", "How do I get downtown?")
        else -> listOf("Weather now", "How do I get to the Airport?", "Last M2")
    }
}

@Composable
private fun SuggestedPromptChip(text: String, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Three-dot typing indicator with a wave animation. Matches the iOS
 * TypingIndicator so the two platforms feel the same while the parser
 * is working.
 */
@Composable
private fun TypingIndicator() {
    val infinite = rememberInfiniteTransition(label = "typing")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1050, easing = LinearEasing),
        ),
        label = "typingPhase",
    )
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Row(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TypingDot(phase, offset = 0f)
            TypingDot(phase, offset = 0.15f)
            TypingDot(phase, offset = 0.3f)
        }
    }
}

@Composable
private fun TypingDot(phase: Float, offset: Float) {
    val scaleMagnitude = kotlin.math.abs(kotlin.math.sin(kotlin.math.PI * (phase - offset).toDouble())).toFloat()
    val diameter = 6f + 2f * scaleMagnitude
    Box(
        modifier = Modifier
            .size(diameter.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), shape = CircleShape),
    )
}

/**
 * On-demand "Download Ariadne's brain" card. Shown only where a downloadable
 * on-device model applies (Android). Hidden when UNSUPPORTED (iOS native / Web)
 * or READY. The rule parser answers throughout, so this is purely additive.
 */
@Composable
private fun AriadneModelCard(
    state: AriadneModelState,
    progress: Float,
    onDownload: () -> Unit,
    lang: AppLanguage,
) {
    if (state == AriadneModelState.UNSUPPORTED || state == AriadneModelState.READY) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = when (lang) {
                    AppLanguage.GREEK -> "Πιο έξυπνη Αριάδνη"
                    AppLanguage.ALBANIAN -> "Ariadne më e zgjuar"
                    else -> "Smarter Ariadne"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = when (state) {
                    AriadneModelState.DOWNLOADING -> when (lang) {
                        AppLanguage.GREEK -> "Λήψη του μοντέλου AI… ${(progress * 100).toInt()}%"
                        AppLanguage.ALBANIAN -> "Po shkarkohet modeli AI… ${(progress * 100).toInt()}%"
                        else -> "Downloading the on-device AI… ${(progress * 100).toInt()}%"
                    }
                    AriadneModelState.ERROR -> when (lang) {
                        AppLanguage.GREEK -> "Η λήψη απέτυχε. Δοκίμασε ξανά. Ο κανόνας-parser συνεχίζει να απαντά."
                        AppLanguage.ALBANIAN -> "Shkarkimi dështoi. Provo sërish. Rregull-parser vazhdon të përgjigjet."
                        else -> "Download failed. Try again. The rule parser still answers."
                    }
                    else -> when (lang) {
                        AppLanguage.GREEK -> "Κατέβασε ένα AI στη συσκευή (~1.1 GB, μία φορά) για να κατανοεί πιο ελεύθερη διατύπωση. Λειτουργεί offline μετά."
                        AppLanguage.ALBANIAN -> "Shkarko një AI në pajisje (~1.1 GB, një herë) që kupton fjalët më të lira. Punon offline më pas."
                        else -> "Download an on-device AI (~1.1 GB, one time) so Ariadne understands freer wording. Works offline after."
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(10.dp))
            if (state == AriadneModelState.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)),
                )
            } else {
                Button(onClick = onDownload) {
                    Text(
                        when (state) {
                            AriadneModelState.ERROR -> when (lang) {
                                AppLanguage.GREEK -> "Δοκίμασε ξανά"
                                AppLanguage.ALBANIAN -> "Provo sërish"
                                else -> "Try again"
                            }
                            else -> when (lang) {
                                AppLanguage.GREEK -> "Λήψη (~1.1 GB)"
                                AppLanguage.ALBANIAN -> "Shkarko (~1.1 GB)"
                                else -> "Download (~1.1 GB)"
                            }
                        }
                    )
                }
            }
        }
    }
}
