package com.syrmos.android.widget

import kotlinx.datetime.toLocalDateTime

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager

/** The line pill, mirroring the iOS `LinePill`: white label on the line color. */
@Composable
fun GlanceLinePill(lineId: String, fontSize: TextUnit = 12.sp) {
    Box(
        modifier = GlanceModifier
            .background(AndroidLineTokens.color(lineId))
            .cornerRadius(6.dp)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = AndroidLineTokens.label(lineId),
            style = TextStyle(color = ColorProvider(Color.White), fontWeight = FontWeight.Bold, fontSize = fontSize),
        )
    }
}

/** One departure row: pill + destination + minutes chip. */
@Composable
fun GlanceDepartureRow(row: WidgetRow, onSurface: ColorProvider, secondary: ColorProvider) {
    Row(
        modifier = GlanceModifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlanceLinePill(row.lineId, fontSize = 11.sp)
        Spacer(GlanceModifier.width(8.dp))
        Text(row.destination, style = TextStyle(color = onSurface, fontSize = 13.sp), maxLines = 1)
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = row.liveMinutes().let { if (it <= 1) widgetText("now", "τώρα", "tani", "ora") else "${it}m" },
            style = TextStyle(color = secondary, fontWeight = FontWeight.Medium, fontSize = 13.sp),
        )
    }
}

/** Green live badge: pulsing dot + "N live" label. */
@Composable
fun GlanceLiveBadge(count: Int) {
    Row(
        modifier = GlanceModifier
            .background(Color(0x1A4CAF50))
            .cornerRadius(8.dp)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier.size(6.dp).background(Color(0xFF4CAF50)).cornerRadius(3.dp),
        ) {}
        Spacer(GlanceModifier.width(4.dp))
        Text(
            text = "$count ${widgetText("live", "ζωντανά", "drejtpërdrejt", "live")}",
            style = TextStyle(color = ColorProvider(Color(0xFF4CAF50)), fontWeight = FontWeight.Bold, fontSize = 11.sp),
        )
    }
}

/**
 * Widget copy in the language the reader chose inside the app, not the
 * system locale: someone running Syrmos in Greek on an English phone
 * expects the widget to match the app.
 */
internal fun widgetText(en: String, el: String, sq: String, it: String): String = when (LocalizationManager.language.value) {
    AppLanguage.GREEK -> el
    AppLanguage.ALBANIAN -> sq
    AppLanguage.ITALIAN -> it
    else -> en
}

/**
 * Minutes until this row's absolute Athens departure time ("HH:mm"), computed
 * at RENDER. A Glance widget cannot self-tick, and the baked [WidgetRow.minutes]
 * froze when the snapshot was written (up to ~30 min earlier), so a stale "4 min"
 * could outlive the train. Recomputing from the clock time at every redraw keeps
 * the countdown honest; falls back to the stored minutes when the time is blank.
 */
internal fun WidgetRow.liveMinutes(): Int {
    val parts = time.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull()
    val m = parts.getOrNull(1)?.toIntOrNull()
    if (parts.size != 2 || h == null || m == null) return minutes.coerceAtLeast(0)
    val now = kotlinx.datetime.Clock.System.now()
        .toLocalDateTime(kotlinx.datetime.TimeZone.of("Europe/Athens"))
    var delta = (h * 60 + m) - (now.hour * 60 + now.minute)
    if (delta < -120) delta += 24 * 60 // just-after-midnight departure, not ~23h ago
    return delta.coerceAtLeast(0)
}
