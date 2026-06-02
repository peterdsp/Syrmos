package com.syrmos.feature.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.syrmos.core.designsystem.theme.MetroBlue
import com.syrmos.core.designsystem.theme.MetroGreen
import com.syrmos.core.designsystem.theme.MetroRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen() {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Transit Map") })
        },
    ) { padding ->
        Canvas(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            val w = size.width
            val h = size.height

            // Simplified schematic: three metro lines as colored strokes
            // Line 1 (Green): vertical, left side
            drawLine(
                color = MetroGreen,
                start = Offset(w * 0.25f, h * 0.1f),
                end = Offset(w * 0.25f, h * 0.9f),
                strokeWidth = 6f,
                cap = StrokeCap.Round,
            )

            // Line 2 (Red): horizontal, middle
            drawLine(
                color = MetroRed,
                start = Offset(w * 0.1f, h * 0.5f),
                end = Offset(w * 0.9f, h * 0.5f),
                strokeWidth = 6f,
                cap = StrokeCap.Round,
            )

            // Line 3 (Blue): diagonal, bottom-left to top-right
            drawLine(
                color = MetroBlue,
                start = Offset(w * 0.1f, h * 0.8f),
                end = Offset(w * 0.9f, h * 0.2f),
                strokeWidth = 6f,
                cap = StrokeCap.Round,
            )

            // Interchange dots (Monastiraki: M1+M3, Syntagma: M2+M3, Omonia: M1+M2)
            listOf(
                Offset(w * 0.25f, h * 0.55f),  // Omonia
                Offset(w * 0.35f, h * 0.5f),   // Syntagma area
                Offset(w * 0.25f, h * 0.6f),   // Monastiraki area
            ).forEach { point ->
                drawCircle(
                    color = Color.White,
                    radius = 8f,
                    center = point,
                )
                drawCircle(
                    color = Color.Black,
                    radius = 8f,
                    center = point,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                )
            }
        }
    }
}
