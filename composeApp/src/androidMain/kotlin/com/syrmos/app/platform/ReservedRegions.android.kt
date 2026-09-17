package com.syrmos.app.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.syrmos.core.common.layout.FoldOrientation
import com.syrmos.core.common.layout.RegionKind
import com.syrmos.core.common.layout.ReservedRegion

/**
 * Reads the active window's `FoldingFeature`s and maps each into a [ReservedRegion]
 * in window dp coordinates: a vertical hinge (`Orientation.VERTICAL`) becomes a
 * left/right split, a horizontal hinge a top/bottom split. A `FULL` occlusion is
 * an opaque hinge; any other occlusion is a division content may bridge.
 * `isSeparating` sets `active`, so a flat non-separating crease does not blank
 * pixels. The `WindowInfoTracker` flow re-emits on rotation and multi-window
 * resize, so no separate configuration key is needed.
 */
@Composable
actual fun rememberReservedRegions(): List<ReservedRegion> {
    val context = LocalContext.current
    val density = LocalDensity.current
    val activity = remember(context) { context.findActivity() }

    val regions by produceState(
        initialValue = emptyList<ReservedRegion>(),
        activity,
        density,
    ) {
        val current = activity
        if (current == null) {
            value = emptyList()
            return@produceState
        }
        WindowInfoTracker.getOrCreate(current)
            .windowLayoutInfo(current)
            .collect { info ->
                value = info.displayFeatures
                    .filterIsInstance<FoldingFeature>()
                    .map { feature ->
                        val bounds = feature.bounds
                        val vertical = feature.orientation == FoldingFeature.Orientation.VERTICAL
                        val startPx = if (vertical) bounds.left else bounds.top
                        val sizePx = if (vertical) bounds.width() else bounds.height()
                        ReservedRegion(
                            kind = if (feature.occlusionType == FoldingFeature.OcclusionType.FULL) {
                                RegionKind.OCCLUSION
                            } else {
                                RegionKind.DIVISION
                            },
                            orientation = if (vertical) {
                                FoldOrientation.VERTICAL
                            } else {
                                FoldOrientation.HORIZONTAL
                            },
                            start = with(density) { startPx.toDp().value.toInt() },
                            size = with(density) { sizePx.toDp().value.toInt() },
                            active = feature.isSeparating,
                        )
                    }
            }
    }
    return regions
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
