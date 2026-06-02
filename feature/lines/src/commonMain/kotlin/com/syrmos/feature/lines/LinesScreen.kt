package com.syrmos.feature.lines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syrmos.core.designsystem.component.LineColorIndicator
import com.syrmos.core.designsystem.component.SectionHeader
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinesScreen(
    viewModel: LinesViewModel,
    onLineClick: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Lines") })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val grouped = uiState.lines.groupBy { it.type }

            grouped[LineType.METRO]?.let { metroLines ->
                item { SectionHeader(title = "Metro") }
                items(metroLines) { line ->
                    LineCard(line = line, onClick = { onLineClick(line.id) })
                }
            }

            grouped[LineType.TRAM]?.let { tramLines ->
                item { SectionHeader(title = "Tram") }
                items(tramLines) { line ->
                    LineCard(line = line, onClick = { onLineClick(line.id) })
                }
            }

            grouped[LineType.SUBURBAN]?.let { suburbanLines ->
                item { SectionHeader(title = "Suburban Railway") }
                items(suburbanLines) { line ->
                    LineCard(line = line, onClick = { onLineClick(line.id) })
                }
            }
        }
    }
}

@Composable
private fun LineCard(
    line: Line,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineColorIndicator(lineColor = line.color, size = 20.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = line.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${line.terminalA} - ${line.terminalB}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${line.stationCount} stations",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
