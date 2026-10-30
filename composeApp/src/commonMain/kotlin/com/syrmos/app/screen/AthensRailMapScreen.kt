package com.syrmos.app.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager
import org.jetbrains.compose.resources.painterResource
import syrmos.composeapp.generated.resources.Res
import syrmos.composeapp.generated.resources.athens_rail_map

/**
 * Settings -> "Athens metropolitan area railways". The bundled network map,
 * pinch-zoomable and pannable, so the whole metro / tram / suburban network is
 * readable underground where the operator's own site is unreachable. Mirrors the
 * iOS `StasyMapView`; Android previously only linked out to stasy.gr, which is a
 * dead end with no signal.
 */
@Composable
fun AthensRailMapScreen(onBack: () -> Unit) {
    val lang by LocalizationManager.language.collectAsState()
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.padding(8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = when (lang) {
                            AppLanguage.GREEK -> "Πίσω"
                            AppLanguage.ALBANIAN -> "Prapa"
                            AppLanguage.ITALIAN -> "Indietro"
                            AppLanguage.ENGLISH -> "Back"
                        },
                    )
                }
                Text(
                    text = when (lang) {
                        AppLanguage.GREEK -> "Σιδηρόδρομοι Αττικής"
                        AppLanguage.ALBANIAN -> "Hekurudhat e Atikës"
                        AppLanguage.ITALIAN -> "Ferrovie dell'Attica"
                        AppLanguage.ENGLISH -> "Athens area railways"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            // Clamped so the rider cannot lose the map off-screen or
                            // zoom past the point where the JPEG turns to mush.
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.athens_rail_map),
                    contentDescription = when (lang) {
                        AppLanguage.GREEK -> "Χάρτης δικτύου Αττικής"
                        AppLanguage.ALBANIAN -> "Harta e rrjetit të Atikës"
                        AppLanguage.ITALIAN -> "Mappa della rete dell'Attica"
                        AppLanguage.ENGLISH -> "Athens network map"
                    },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY,
                        ),
                )
            }
        }
    }
}
