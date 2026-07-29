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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.GlanceTheme
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.syrmos.android.MainActivity

/**
 * Live Departures (large): the next five departures from the primary station in
 * a scrollable Glance list.
 */
class LiveDeparturesGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).appWidgetIdOrZero(id)
        val snapshot = WidgetProjection.snapshotFor(context, appWidgetId)
        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.background)
                        .padding(14.dp)
                        .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = snapshot.stationName,
                            style = TextStyle(color = GlanceTheme.colors.onBackground, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                        )
                        Spacer(GlanceModifier.defaultWeight())
                        if (snapshot.isLiveDataFresh) {
                            GlanceLiveBadge(snapshot.liveTrainCount)
                        }
                    }
                    LazyColumn {
                        items(snapshot.rows.take(5)) { row ->
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

class LiveDeparturesGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LiveDeparturesGlanceWidget()
}
