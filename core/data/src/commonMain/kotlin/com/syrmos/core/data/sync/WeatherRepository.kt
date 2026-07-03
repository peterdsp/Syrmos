package com.syrmos.core.data.sync

import com.syrmos.core.database.SyrmosDatabase
import com.syrmos.core.model.weather.CurrentWeather
import com.syrmos.core.model.weather.HourlyForecast
import com.syrmos.core.model.weather.WeatherSnapshot
import com.syrmos.core.network.WeatherService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Holds the latest weather snapshot. The card and Ariadne's weather-aware
 * routing read [snapshot]; it carries the fetch time so both can show
 * "as of HH:MM" and degrade honestly when there's no network. Defaults to
 * central Athens so Web and no-location launches still show something.
 *
 * The per-bucket cache is hydrated from SQLDelight on first use, and every
 * successful refresh writes back. Cold-starting after airplane mode still
 * has data to hand Ariadne (with a "cached from X min ago" caveat).
 */
class WeatherRepository(
    private val weatherService: WeatherService,
    private val database: SyrmosDatabase? = null,
) {
    private val _snapshot = MutableStateFlow<WeatherSnapshot?>(null)
    val snapshot: StateFlow<WeatherSnapshot?> = _snapshot.asStateFlow()

    private val _buckets = MutableStateFlow<Map<String, WeatherSnapshot>>(emptyMap())
    private val cacheTtlSeconds = 30L * 60L  // 30 min
    private var bucketsHydrated = false

    /** Most recent snapshot, regardless of age, for offline reads. */
    val cached: WeatherSnapshot? get() = _snapshot.value

    suspend fun refresh(
        latitude: Double = ATHENS_LAT,
        longitude: Double = ATHENS_LON,
        placeName: String = "Athens",
    ) {
        val reading = weatherService.fetch(latitude, longitude) ?: return
        val snapshot = WeatherSnapshot(
            current = reading.current,
            latitude = latitude,
            longitude = longitude,
            placeName = placeName,
            fetchedAtEpochSeconds = Clock.System.now().epochSeconds,
            highC = reading.highC,
            lowC = reading.lowC,
            hourly = reading.hourly,
        )
        _snapshot.value = snapshot
        val key = bucketKey(latitude, longitude)
        _buckets.update { it + (key to snapshot) }
        persist(key, snapshot)
    }

    /**
     * Weather for the location bucket [lat]/[lng] belongs to. Returns the
     * cached snapshot when it's fresh enough (< 30 min old). Otherwise
     * tries a network refresh; if that fails and any snapshot exists in
     * the same bucket, returns it stale rather than nothing (offline
     * degradation). Returns null only when the bucket has never been
     * seen and the network is unreachable.
     */
    suspend fun snapshotForCoord(
        lat: Double,
        lng: Double,
        placeName: String,
    ): WeatherSnapshot? {
        hydrateFromDbIfNeeded()
        val key = bucketKey(lat, lng)
        val existing = _buckets.value[key]
        val nowSecs = Clock.System.now().epochSeconds
        val fresh = existing != null && (nowSecs - existing.fetchedAtEpochSeconds) < cacheTtlSeconds
        if (fresh) return existing

        val reading = runCatching { weatherService.fetch(lat, lng) }.getOrNull()
        if (reading == null) return existing  // stale-if-error

        val freshSnap = WeatherSnapshot(
            current = reading.current,
            latitude = lat,
            longitude = lng,
            placeName = placeName,
            fetchedAtEpochSeconds = nowSecs,
            highC = reading.highC,
            lowC = reading.lowC,
            hourly = reading.hourly,
        )
        _buckets.update { it + (key to freshSnap) }
        _snapshot.value = freshSnap
        persist(key, freshSnap)
        return freshSnap
    }

    private fun hydrateFromDbIfNeeded() {
        if (bucketsHydrated) return
        bucketsHydrated = true
        val db = database ?: return
        runCatching {
            val rows = db.syrmosDatabaseQueries.selectWeatherBuckets().executeAsList()
            val map = HashMap<String, WeatherSnapshot>(rows.size)
            for (r in rows) {
                val decoded = runCatching { Json.decodeFromString(WeatherPayload.serializer(), r.payloadJson) }.getOrNull()
                    ?: continue
                map[r.bucketKey] = decoded.toSnapshot(r.latitude, r.longitude, r.placeName, r.fetchedAtEpochSeconds)
            }
            if (map.isNotEmpty()) {
                _buckets.value = map
                if (_snapshot.value == null) {
                    // Promote the freshest bucket as the current snapshot so
                    // the Home weather card has something to show at cold
                    // start even before a network refresh.
                    _snapshot.value = map.values.maxByOrNull { it.fetchedAtEpochSeconds }
                }
            }
        }
    }

    private fun persist(key: String, snap: WeatherSnapshot) {
        val db = database ?: return
        runCatching {
            db.syrmosDatabaseQueries.upsertWeatherBucket(
                bucketKey = key,
                latitude = snap.latitude,
                longitude = snap.longitude,
                placeName = snap.placeName,
                fetchedAtEpochSeconds = snap.fetchedAtEpochSeconds,
                payloadJson = Json.encodeToString(WeatherPayload.from(snap)),
            )
        }
    }

    private fun bucketKey(lat: Double, lng: Double): String {
        val quantum = 0.05
        val la = kotlin.math.round(lat / quantum) * quantum
        val ln = kotlin.math.round(lng / quantum) * quantum
        return "${(la * 100).toInt()}_${(ln * 100).toInt()}"
    }

    companion object {
        const val ATHENS_LAT = 37.9838
        const val ATHENS_LON = 23.7275
    }

    /**
     * Minimal, self-contained payload written to the DB. Kept flat and
     * stable so an old build reading a newer row (or vice versa) still
     * decodes safely. Hourly forecasts are dropped from persistence to
     * keep rows small — they refresh in the current session and the
     * cache is only there to save cold-start assistants.
     */
    @Serializable
    private data class WeatherPayload(
        val temperatureC: Double,
        val apparentC: Double,
        val weatherCode: Int,
        val isDay: Boolean,
        val windKph: Double,
        val humidity: Int,
        val precipitationMm: Double,
        val highC: Double? = null,
        val lowC: Double? = null,
    ) {
        fun toSnapshot(lat: Double, lng: Double, placeName: String, fetchedAt: Long): WeatherSnapshot {
            return WeatherSnapshot(
                current = CurrentWeather(
                    temperatureC = temperatureC,
                    apparentC = apparentC,
                    weatherCode = weatherCode,
                    isDay = isDay,
                    windKph = windKph,
                    humidity = humidity,
                    precipitationMm = precipitationMm,
                ),
                latitude = lat,
                longitude = lng,
                placeName = placeName,
                fetchedAtEpochSeconds = fetchedAt,
                highC = highC,
                lowC = lowC,
                hourly = emptyList(),
            )
        }
        companion object {
            fun from(snap: WeatherSnapshot): WeatherPayload = WeatherPayload(
                temperatureC = snap.current.temperatureC,
                apparentC = snap.current.apparentC,
                weatherCode = snap.current.weatherCode,
                isDay = snap.current.isDay,
                windKph = snap.current.windKph,
                humidity = snap.current.humidity,
                precipitationMm = snap.current.precipitationMm,
                highC = snap.highC,
                lowC = snap.lowC,
            )
        }
    }
}

private fun <K, V> MutableStateFlow<Map<K, V>>.update(fn: (Map<K, V>) -> Map<K, V>) {
    while (true) {
        val cur = value
        val next = fn(cur)
        if (compareAndSet(cur, next)) return
    }
}
