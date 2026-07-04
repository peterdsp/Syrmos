package com.syrmos.android.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import com.syrmos.core.model.transit.Station
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

/**
 * Widget configuration screen, the Android equivalent of the iOS Edit Widget
 * sheet. Reached when adding a widget or, on Android 12+, by long-pressing a
 * placed widget (the provider XML marks it reconfigurable + optional, so nearest
 * mode still works with no setup). Lets the user keep the nearest station, pin a
 * specific station, and optionally restrict to one line.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // If the user backs out, the widget must not be added.
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val existing = WidgetConfigStore.read(this, appWidgetId)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var stations by remember { mutableStateOf<List<Station>>(emptyList()) }
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        stations = WidgetProjection.stationsForPicker()
                    }
                    ConfigScreen(
                        stations = stations,
                        initial = existing,
                        onSave = { config -> save(config) },
                    )
                }
            }
        }
    }

    private fun save(config: WidgetConfig) {
        WidgetConfigStore.write(this, appWidgetId, config)
        // Re-render every family so the configured instance updates immediately.
        lifecycleScope.launch {
            runCatching {
                NextTrainGlanceWidget().updateAll(applicationContext)
                LiveDeparturesGlanceWidget().updateAll(applicationContext)
                LineStatusGlanceWidget().updateAll(applicationContext)
                NearMeGlanceWidget().updateAll(applicationContext)
            }
            val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(Activity.RESULT_OK, result)
            finish()
        }
    }
}

private val LINE_OPTIONS = listOf("M1", "M2", "M3", "T6", "T7", "A1", "A2")

@Composable
private fun ConfigScreen(
    stations: List<Station>,
    initial: WidgetConfig,
    onSave: (WidgetConfig) -> Unit,
) {
    var useNearest by remember { mutableStateOf(initial.useNearest) }
    var stationId by remember { mutableStateOf(initial.stationId) }
    var stationName by remember { mutableStateOf(initial.stationName) }
    var line by remember { mutableStateOf(initial.line) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Syrmos widget", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Show the nearest station automatically, or pick your own.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Use nearest station", fontSize = 16.sp, modifier = Modifier.weight(1f))
            Switch(checked = useNearest, onCheckedChange = { useNearest = it })
        }
        Spacer(Modifier.height(16.dp))

        // Station picker, only when not tracking the nearest station.
        if (!useNearest) {
            Text("Station", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Card(modifier = Modifier.fillMaxWidth().weight(1f, fill = false).height(260.dp)) {
                if (stations.isEmpty()) {
                    Text("Loading stations…", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn {
                        items(stations) { s ->
                            val selected = s.id == stationId
                            Text(
                                text = s.name,
                                fontSize = 16.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { stationId = s.id; stationName = s.name }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Text("Only this line (optional)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LineChip(label = "All", selected = line == null) { line = null }
        }
        Spacer(Modifier.height(8.dp))
        // Two rows of line chips so they fit narrow screens.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LINE_OPTIONS.take(4).forEach { l ->
                LineChip(label = l, selected = line == l) { line = if (line == l) null else l }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LINE_OPTIONS.drop(4).forEach { l ->
                LineChip(label = l, selected = line == l) { line = if (line == l) null else l }
            }
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                onSave(
                    WidgetConfig(
                        useNearest = useNearest,
                        stationId = if (useNearest) null else stationId,
                        stationName = if (useNearest) null else stationName,
                        line = line,
                    )
                )
            },
            enabled = useNearest || stationId != null,
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Text("Save")
        }
    }
}

@Composable
private fun LineChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}
