package com.syrmos.app.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.StationClosures
import com.syrmos.core.data.sync.ClosuresRepository
import com.syrmos.core.designsystem.component.ClosureCardInfo
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/**
 * The current epoch second, ticking every 30 s, so closure windows flip on screen
 * at their start and end without a network round trip.
 */
@Composable
fun rememberClosureClock(): Long {
    val now by produceState(ClosuresRepository.nowEpochSeconds()) {
        while (true) {
            delay(30_000)
            value = ClosuresRepository.nowEpochSeconds()
        }
    }
    return now
}

/**
 * The closure card for a station (active, or starting within 24 h), or null.
 * Hydrates the bundled/cached closures once and refreshes `/api/closures` when the
 * screen opens; both are silent on failure.
 */
@Composable
fun rememberStationClosureCard(stationId: String, lang: AppLanguage): ClosureCardInfo? {
    val repo = koinInject<ClosuresRepository>()
    LaunchedEffect(Unit) {
        runCatching { repo.hydrateFromBundleIfNeeded() }
        runCatching { repo.refresh() }
    }
    val closures by repo.closures.collectAsState()
    val now = rememberClosureClock()
    return remember(closures, stationId, now, lang) {
        val effect = StationClosures.effect(closures, stationId, StationClosures.lineIdOfStation(stationId), now)
        ClosureCardInfo.from(effect, StationClosures.closureFor(closures, effect), now, lang)
    }
}
