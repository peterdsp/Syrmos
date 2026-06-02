package com.syrmos.feature.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen() {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Schedule") })
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        ) {
            Text(
                text = "Full timetable viewer",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Select a line and direction to view the complete schedule " +
                    "for weekdays, Fridays, Saturdays and Sundays.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
