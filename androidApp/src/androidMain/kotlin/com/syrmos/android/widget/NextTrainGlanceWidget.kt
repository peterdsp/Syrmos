package com.syrmos.android.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.GlanceTheme
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.syrmos.android.MainActivity

/**
 * Next Train (small + medium): the single next train from the primary station,
 * as a big minutes headline with the line pill and destination. Migrated from
 * the RemoteViews `SyrmosDeparturesWidget` to Compose Glance.
 */
class NextTrainGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).appWidgetIdOrZero(id)
        val snapshot = WidgetProjection.snapshotFor(context, appWidgetId)
        provideContent {
            GlanceTheme {
                val lead = snapshot.rows.firstOrNull()
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.background)
                        .padding(14.dp)
                        .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                ) {
                    if (lead != null) {
                        GlanceLinePill(lead.lineId, fontSize = 13.sp)
                        Text(
                            text = if (lead.minutes <= 1) "now" else "${lead.minutes} min",
                            style = TextStyle(color = GlanceTheme.colors.onBackground, fontWeight = FontWeight.Bold, fontSize = 32.sp),
                            modifier = GlanceModifier.padding(top = 6.dp),
                        )
                        Text(lead.destination, style = TextStyle(color = GlanceTheme.colors.onBackground, fontWeight = FontWeight.Medium, fontSize = 13.sp), maxLines = 1)
                        Text(snapshot.stationName, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
                    } else {
                        Text(snapshot.stationName, style = TextStyle(color = GlanceTheme.colors.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp))
                        Text("No upcoming departures", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
                    }
                }
            }
        }
    }
}

class NextTrainGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextTrainGlanceWidget()
}
