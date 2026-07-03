package com.syrmos.android.widget

import android.content.Context

/**
 * The snapshot the app's WorkManager refresher writes to SharedPreferences and
 * every Glance widget reads. Glance widgets never compute schedules themselves;
 * they render this snapshot (plus the bundled fallback), so the home screen
 * stays instant and offline. Encoded as a compact delimited string to avoid a
 * serialization plugin in the app module.
 */
data class WidgetRow(
    val lineId: String,
    val destination: String,
    val minutes: Int,
    val time: String,
)

data class WidgetSnapshot(
    val stationName: String,
    val lastTrain: String?,
    val rows: List<WidgetRow>,
    val updatedEpoch: Long,
) {
    companion object {
        val empty = WidgetSnapshot(stationName = "—", lastTrain = null, rows = emptyList(), updatedEpoch = 0)
    }
}

object SnapshotStore {
    private const val PREFS = "syrmos_widget"
    private const val KEY_STATION = "station"
    private const val KEY_LAST = "last_train"
    private const val KEY_ROWS = "rows"
    private const val KEY_UPDATED = "updated"
    private const val ROW_SEP = ""
    private const val FIELD_SEP = ""

    fun write(context: Context, snapshot: WidgetSnapshot) {
        val encoded = snapshot.rows.joinToString(ROW_SEP) { r ->
            listOf(r.lineId, r.destination, r.minutes.toString(), r.time).joinToString(FIELD_SEP)
        }
        context.prefs().edit()
            .putString(KEY_STATION, snapshot.stationName)
            .putString(KEY_LAST, snapshot.lastTrain)
            .putString(KEY_ROWS, encoded)
            .putLong(KEY_UPDATED, snapshot.updatedEpoch)
            .apply()
    }

    fun read(context: Context): WidgetSnapshot {
        val p = context.prefs()
        val station = p.getString(KEY_STATION, null) ?: return WidgetSnapshot.empty
        val encoded = p.getString(KEY_ROWS, "").orEmpty()
        val rows = if (encoded.isEmpty()) emptyList() else encoded.split(ROW_SEP).mapNotNull { line ->
            val f = line.split(FIELD_SEP)
            if (f.size < 4) null else WidgetRow(f[0], f[1], f[2].toIntOrNull() ?: 0, f[3])
        }
        return WidgetSnapshot(
            stationName = station,
            lastTrain = p.getString(KEY_LAST, null),
            rows = rows,
            updatedEpoch = p.getLong(KEY_UPDATED, 0),
        )
    }

    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
