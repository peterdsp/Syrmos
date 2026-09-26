package com.syrmos.core.designsystem.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import com.syrmos.core.common.layout.AdaptiveWorkspace
import com.syrmos.core.common.layout.AdaptiveWorkspacePolicy
import com.syrmos.core.common.layout.FoldOrientation
import com.syrmos.core.common.layout.ReservedRegion
import com.syrmos.core.common.layout.WorkspaceTask

/**
 * The window's reported fold/occlusion regions (window dp coordinates), provided
 * once at the app root from the app root's platform reserved-regions reader.
 * Screens read it through [rememberContentWorkspace]; it defaults to empty so a
 * screen composed outside the root still resolves through the plain-window path.
 */
val LocalReservedRegions = staticCompositionLocalOf { emptyList<ReservedRegion>() }

/**
 * Resolve the [AdaptiveWorkspace] for a measured content box.
 *
 * @param task the current task (drives whether a companion pane is offered).
 * @param width the box's usable width in dp (from its own `BoxWithConstraints`).
 * @param height the box's usable height in dp.
 * @param windowOffsetLeft the box's left offset from the window origin, in dp, so
 *   a window-space fold region is translated into the box's own coordinates once.
 * @param windowOffsetTop the box's top offset from the window origin, in dp.
 * @param forceSingleColumn caller override (e.g. an accessibility preference).
 *
 * A region is translated into box coordinates and only kept when it still crosses
 * the box, so a fold beside the box (behind a nav rail) never splits its content.
 */
@Composable
fun rememberContentWorkspace(
    task: WorkspaceTask,
    width: Int,
    height: Int,
    windowOffsetLeft: Int = 0,
    windowOffsetTop: Int = 0,
    forceSingleColumn: Boolean = false,
): AdaptiveWorkspace {
    val fontScale = LocalDensity.current.fontScale
    val regions = LocalReservedRegions.current
    val local = regions.mapNotNull { r ->
        when (r.orientation) {
            FoldOrientation.VERTICAL -> {
                val start = r.start - windowOffsetLeft
                if (start <= 0 || start >= width) null else r.copy(start = start)
            }
            FoldOrientation.HORIZONTAL -> {
                val start = r.start - windowOffsetTop
                if (start <= 0 || start >= height) null else r.copy(start = start)
            }
        }
    }
    return AdaptiveWorkspacePolicy.resolve(
        width = width,
        height = height,
        task = task,
        regions = local,
        fontScale = fontScale,
        forceSingleColumn = forceSingleColumn,
    )
}
