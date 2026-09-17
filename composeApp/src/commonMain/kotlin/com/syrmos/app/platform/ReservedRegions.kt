package com.syrmos.app.platform

import androidx.compose.runtime.Composable
import com.syrmos.core.common.layout.ReservedRegion

/**
 * The fold/occlusion regions reported for the current window, in WINDOW content
 * coordinate space (density-independent units, origin at the window top-left).
 *
 * Android reads WindowManager's `WindowLayoutInfo` (FoldingFeature). Every other
 * target (iOS, web) returns an empty list, so a device that reports no fold, an
 * ordinary phone or tablet, resolves through the plain-window path unchanged.
 *
 * A consumer that measures a sub-region of the window (a pane after a nav rail)
 * must translate these into its own local coordinates before resolving.
 */
@Composable
expect fun rememberReservedRegions(): List<ReservedRegion>
