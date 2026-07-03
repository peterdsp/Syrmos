package com.syrmos.core.data.sync

import com.syrmos.core.model.weather.WeatherSnapshot
import com.syrmos.core.network.WeatherService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

/**
 * Holds the latest weather snapshot. The card and Ariadne's weather-aware
 * routing read [snapshot]; it carries the fetch time so both can show "as of
 * HH:MM" and degrade honestly when there's no network. Defaults to central
 * Athens so Web and no-location launches still show something.
 */
class WeatherRepository(
    private val weatherService: WeatherService,
) {
    private val _snapshot = MutableStateFlow<WeatherSnapshot?>(null)
    val snapshot: StateFlow<WeatherSnapshot?> = _snapshot.asStateFlow()

    /**
     * In-memory per-location cache keyed by a 0.05° lat/lng bucket (~5 km),
     * which is enough resolution for Athens without exploding into one
     * fetch per station. When Ariadne asks for "weather at Piraeus" we
     * bucket the coord, hit or refresh, and return offline-safely on
     * subsequent asks. Bucket keys survive process lifetime; persisting
     * across restart is a follow-up (a small SQLDelight table).
     */
    private val _buckets = MutableStateFlow<Map<String, WeatherSnapshot>>(emptyMap())
    private val cacheTtlSeconds = 30L * 60L  // 30 min

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
        _buckets.update { it + (bucketKey(latitude, longitude) to snapshot) }
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
        val key = bucketKey(lat, lng)
        val existing = _buckets.value[key]
        val nowSecs = Clock.System.now().epochSeconds
        val fresh = existing != null && (nowSecs - existing.fetchedAtEpochSeconds) < cacheTtlSeconds
        if (fresh) return existing

        val reading = runCatching { weatherService.fetch(lat, lng) }.getOrNull()
        if (reading == null) return existing  // stale-if-error

        val fresh_snap = WeatherSnapshot(
            current = reading.current,
            latitude = lat,
            longitude = lng,
            placeName = placeName,
            fetchedAtEpochSeconds = nowSecs,
            highC = reading.highC,
            lowC = reading.lowC,
            hourly = reading.hourly,
        )
        _buckets.update { it + (key to fresh_snap) }
        // Also promote to _snapshot so the Home weather card reflects the
        // most recently fetched location, matching iOS behavior.
        _snapshot.value = fresh_snap
        return fresh_snap
    }

    private fun bucketKey(lat: Double, lng: Double): String {
        // 0.05° ≈ 5 km, enough resolution to distinguish Piraeus from
        // Kifissia without one bucket per station.
        val quantum = 0.05
        val la = kotlin.math.round(lat / quantum) * quantum
        val ln = kotlin.math.round(lng / quantum) * quantum
        return "${(la * 100).toInt()}_${(ln * 100).toInt()}"
    }

    companion object {
        const val ATHENS_LAT = 37.9838
        const val ATHENS_LON = 23.7275
    }
}

private fun <K, V> MutableStateFlow<Map<K, V>>.update(fn: (Map<K, V>) -> Map<K, V>) {
    while (true) {
        val cur = value
        val next = fn(cur)
        if (compareAndSet(cur, next)) return
    }
}
