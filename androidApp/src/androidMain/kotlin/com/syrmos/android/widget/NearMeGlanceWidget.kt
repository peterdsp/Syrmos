package com.syrmos.android.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.GlanceTheme
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.syrmos.android.MainActivity

/**
 * Near Me: the primary station and its next departures with a location framing.
 * Full GPS-based nearest-station selection in the widget process is a follow-up
 * (it needs a background-location flow); this renders the snapshot the app
 * writes for the user's primary station.
 */
class NearMeGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = SnapshotStore.read(context).takeIf { it.rows.isNotEmpty() } ?: BundledFallback.snapshot()
        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.background)
                        .padding(14.dp)
                        .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                ) {
                    Text(
                        text = "Near me",
                        style = TextStyle(color = GlanceTheme.colors.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                        modifier = GlanceModifier.padding(bottom = 6.dp),
                    )
                    if (snapshot.nearby.isNotEmpty()) {
                        // The three nearest stations with walking distance.
                        snapshot.nearby.take(3).forEach { station ->
                            Row(
                                modifier = GlanceModifier.fillMaxWidth().padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = GlanceModifier.defaultWeight()) {
                                    Text(
                                        text = station.name,
                                        style = TextStyle(color = GlanceTheme.colors.onBackground, fontWeight = FontWeight.Medium, fontSize = 14.sp),
                                        maxLines = 1,
                                    )
                                    Row {
                                        station.lineIds.take(3).forEach { line ->
                                            GlanceLinePill(line, fontSize = 10.sp)
                                            Spacer(GlanceModifier.width(4.dp))
                                        }
                                    }
                                }
                                Text(
                                    text = "${station.walkMinutes} min",
                                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontWeight = FontWeight.Medium, fontSize = 13.sp),
                                )
                            }
                        }
                    } else {
                        // No location yet: fall back to the pinned station's departures.
                        Text(
                            text = snapshot.stationName,
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                            modifier = GlanceModifier.padding(bottom = 4.dp),
                            maxLines = 1,
                        )
                        snapshot.rows.take(3).forEach { row ->
                            GlanceDepartureRow(
                                row = row,
                                onSurface = GlanceTheme.colors.onBackground,
                                secondary = GlanceTheme.colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

class NearMeGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NearMeGlanceWidget()
}
