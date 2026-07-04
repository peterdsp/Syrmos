package com.syrmos.android.widget

import android.content.Context

/**
 * Per-widget-instance configuration, the Android mirror of the iOS
 * SyrmosWidgetConfigurationIntent (Edit Widget). Each home-screen widget has its
 * own appWidgetId, so two Next Train widgets can track two different stations.
 *
 * Stored in its own SharedPreferences file keyed by appWidgetId, separate from
 * the shared snapshot the SnapshotWorker writes.
 */
data class WidgetConfig(
    val useNearest: Boolean = true,
    val stationId: String? = null,
    val stationName: String? = null,
    /** Canonical line id to restrict to (e.g. "M2"), or null for all lines. */
    val line: String? = null,
)

object WidgetConfigStore {
    private const val PREFS = "syrmos_widget_config"

    fun read(context: Context, appWidgetId: Int): WidgetConfig {
        val p = context.prefs()
        return WidgetConfig(
            useNearest = p.getBoolean(key(appWidgetId, "nearest"), true),
            stationId = p.getString(key(appWidgetId, "stationId"), null),
            stationName = p.getString(key(appWidgetId, "stationName"), null),
            line = p.getString(key(appWidgetId, "line"), null),
        )
    }

    fun write(context: Context, appWidgetId: Int, config: WidgetConfig) {
        context.prefs().edit()
            .putBoolean(key(appWidgetId, "nearest"), config.useNearest)
            .putString(key(appWidgetId, "stationId"), config.stationId)
            .putString(key(appWidgetId, "stationName"), config.stationName)
            .putString(key(appWidgetId, "line"), config.line)
            .apply()
    }

    /** Drops an instance's config when its widget is removed. */
    fun clear(context: Context, appWidgetId: Int) {
        context.prefs().edit()
            .remove(key(appWidgetId, "nearest"))
            .remove(key(appWidgetId, "stationId"))
            .remove(key(appWidgetId, "stationName"))
            .remove(key(appWidgetId, "line"))
            .apply()
    }

    private fun key(appWidgetId: Int, suffix: String) = "w_${appWidgetId}_$suffix"

    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
