package com.syrmos.core.common.layout

// Syrmos adaptive workspace policy (foldables + iPhone Duo prompt, section 5).
//
// A PURE, platform-neutral function from the usable content geometry, reported
// fold/occlusion regions, font scale and the current task to a set of SEMANTIC
// layout decisions: how many panes, which role goes where, what stays clear of a
// physical hinge, and whether an accessible divider may be dragged.
//
// This is deliberately NOT a pixel frame algorithm shared across platforms. It
// emits roles + fitted rectangles + visibility so that:
//   * Android Compose can drive an adaptive scaffold from it directly,
//   * SwiftUI can validate the same continuity/fit invariants while letting its
//     native navigation and arrangement containers own the actual layout,
//   * the web app is untouched (it never calls this; it keeps ContentBreakpoint).
//
// It reuses [ContentBreakpoint] for the plain-window width decision so a device
// with no fold information resolves exactly as before. No SDK types leak in here.

/** Which way a reported fold/hinge bar runs across the window. */
enum class FoldOrientation {
    /** Hinge line runs top-to-bottom, so the two regions are LEFT and RIGHT. */
    VERTICAL,

    /** Hinge line runs left-to-right, so the two regions are TOP and BOTTOM. */
    HORIZONTAL,
}

/** A reported system region, in the window's own content coordinate space. */
enum class RegionKind {
    /**
     * A layout division (a flat/inactive crease, or a separating fold that still
     * lets a continuous list scroll across it). Content MAY bridge it visually.
     */
    DIVISION,

    /**
     * An occlusion: a physically opaque hinge or a dynamic cutout. Content, touch
     * targets and controls must NOT be placed inside it.
     */
    OCCLUSION,
}

/**
 * A reserved region reported by the platform, already normalised into the app
 * window's content coordinates. `start` is the offset of the bar (from the left
 * for a [FoldOrientation.VERTICAL] bar, from the top for a HORIZONTAL bar) and
 * `size` is its thickness. `active` distinguishes a live separation/occlusion
 * from a structural hint (`.includeInactive`) that must not blank out pixels.
 */
data class ReservedRegion(
    val kind: RegionKind,
    val orientation: FoldOrientation,
    val start: Int,
    val size: Int,
    val active: Boolean = true,
)

/** The task currently driving the workspace, so the policy is task aware. */
enum class WorkspaceTask {
    HOME,
    PLAN,
    GO,
    EXPLORE,
    DEPARTURES,
    FARES,
    ARIADNE,
    /** Settings, onboarding and other bounded single-focus forms. */
    FORM,
    ;

    /**
     * Whether this task gains from a paired secondary pane (a map or a detail)
     * when the space genuinely fits. FORM stays single focus.
     */
    val pairsWithSecondary: Boolean
        get() = this != FORM
}

/** How the workspace is arranged. */
enum class WorkspaceArrangement {
    /** One task pane. The map/detail is reached through an explicit switch. */
    SINGLE,

    /** Task/list beside map/detail (a vertical fold, or a wide flat window). */
    SIDE_BY_SIDE,

    /** Overview above, task + controls below (tabletop / horizontal fold). */
    STACKED,
}

/** The semantic role a pane plays, so each client maps it to its own content. */
enum class PaneRole {
    /** The primary task: planner form, GO instruction, station list, etc. */
    TASK,

    /** The companion: map, station detail, selected itinerary, timeline. */
    COMPANION,

    /** The optional third pane on very wide windows: selected-entity inspector. */
    INSPECTOR,
}

/** An integer rectangle in the window content coordinate space (logical units). */
data class WorkspaceRect(val left: Int, val top: Int, val width: Int, val height: Int) {
    val right: Int get() = left + width
    val bottom: Int get() = top + height
}

/** A placed pane: its role and the rectangle it may use. */
data class WorkspacePane(val role: PaneRole, val rect: WorkspaceRect)

/**
 * A user-adjustable divider between two panes. Present ONLY on an unobstructed
 * large layout (never across a real separating/occluding hinge). `min`/`max` are
 * clamps for the primary pane so neither side can starve.
 */
data class WorkspaceDivider(
    val orientation: FoldOrientation,
    val min: Int,
    val max: Int,
)

/**
 * The resolved workspace.
 *
 * @property arrangement the chosen arrangement.
 * @property panes placed panes in draw order (TASK first).
 * @property hingeGap an occluding region to keep clear of all content, or null.
 * @property divider an accessible adjustable divider, or null when adjusting the
 *   split would fight a physical hinge or a single pane.
 * @property regionDriven true when a reported fold/occlusion (not the width rule)
 *   decided the split, so a client must fit the real regions rather than a ratio.
 * @property singleColumnFallback true when a short window or large text forced a
 *   single scrolling task column (map disclosed separately).
 * @property fontScale the font scale the decision was made at (echoed for tests).
 */
data class AdaptiveWorkspace(
    val arrangement: WorkspaceArrangement,
    val panes: List<WorkspacePane>,
    val hingeGap: WorkspaceRect? = null,
    val divider: WorkspaceDivider? = null,
    val regionDriven: Boolean = false,
    val singleColumnFallback: Boolean = false,
    val fontScale: Float = 1f,
) {
    fun pane(role: PaneRole): WorkspacePane? = panes.firstOrNull { it.role == role }
    val hasCompanion: Boolean get() = pane(PaneRole.COMPANION) != null
    val hasInspector: Boolean get() = pane(PaneRole.INSPECTOR) != null
}

/**
 * The canonical adaptive-workspace policy. Deterministic and side-effect free so
 * the same inputs produce the same layout on every client and in tests.
 */
object AdaptiveWorkspacePolicy {

    // Tabletop (horizontal split) fit floors, in logical units at fontScale 1.0.
    // Section 5: roughly 220dp upper overview and 280dp lower task region.
    const val TABLETOP_MIN_OVERVIEW = 220
    const val TABLETOP_MIN_TASK = 280

    // A wide side-by-side layout must leave the companion at least this wide, and
    // an inspector this wide, before either is offered.
    const val MIN_COMPANION = ContentBreakpoint.MIN_SECONDARY_PANE // 360
    const val MIN_INSPECTOR = 280

    // Wide-canvas inspector needs at least this total width (task + companion +
    // inspector + gaps + insets) before it is worth showing.
    const val INSPECTOR_MIN_CANVAS = 1280

    private const val GAP = 24

    /**
     * Resolve the workspace.
     *
     * @param width usable content width AFTER system bars, safe-area insets, a
     *   visible keyboard and native navigation (never the physical screen width).
     * @param height usable content height, same contract.
     * @param task the current task (drives whether a companion is offered).
     * @param regions reported fold/occlusion regions in content coordinates. A
     *   region whose bar falls outside `[0,width]`/`[0,height]` is ignored.
     * @param fontScale accessibility text scale; raises every content minimum.
     * @param forceSingleColumn caller override (e.g. an accessibility preference).
     */
    fun resolve(
        width: Int,
        height: Int,
        task: WorkspaceTask,
        regions: List<ReservedRegion> = emptyList(),
        fontScale: Float = 1f,
        forceSingleColumn: Boolean = false,
    ): AdaptiveWorkspace {
        val scale = if (fontScale.isNaN() || fontScale < 1f) 1f else fontScale

        // 1. An ACTIVE region inside the window overrides every width ratio. An
        //    occlusion is unavailable space; a division is a real two-region
        //    split we still fit independently. Both are region driven.
        val region = activeRegionWithin(regions, width, height)
        if (region != null) {
            return resolveForRegion(width, height, task, region, scale, forceSingleColumn)
        }

        // 2. No fold information: fall back to the plain window rule, made task
        //    and font aware. This keeps a non-foldable device identical to before.
        return resolveForWindow(width, height, task, scale, forceSingleColumn)
    }

    // The first active region that actually crosses the window. Occlusion wins
    // over a division if both are reported (opaque space is the harder constraint).
    private fun activeRegionWithin(
        regions: List<ReservedRegion>,
        width: Int,
        height: Int,
    ): ReservedRegion? {
        val crossing = regions.filter { r ->
            if (!r.active) return@filter false
            // An occlusion must reserve real thickness; a division line may be
            // zero width (the two halves abut on a coordinate).
            if (r.kind == RegionKind.OCCLUSION && r.size <= 0) return@filter false
            when (r.orientation) {
                FoldOrientation.VERTICAL -> r.start > 0 && r.start < width
                FoldOrientation.HORIZONTAL -> r.start > 0 && r.start < height
            }
        }
        return crossing.firstOrNull { it.kind == RegionKind.OCCLUSION }
            ?: crossing.firstOrNull()
    }

    private fun resolveForRegion(
        width: Int,
        height: Int,
        task: WorkspaceTask,
        region: ReservedRegion,
        scale: Float,
        forceSingleColumn: Boolean,
    ): AdaptiveWorkspace {
        val occluding = region.kind == RegionKind.OCCLUSION
        // For a division the two halves abut; for an occlusion the hinge steals
        // its own thickness and nothing may bridge it.
        val gapSize = if (occluding) region.size else 0

        return when (region.orientation) {
            FoldOrientation.VERTICAL -> {
                val leftW = region.start
                val rightW = width - region.start - gapSize
                val hingeGap = if (occluding) {
                    WorkspaceRect(region.start, 0, region.size, height)
                } else {
                    null
                }
                // A form, or a task that does not pair, keeps a single focus in
                // the larger region; the other region is left to the companion
                // switch rather than forced content.
                if (!task.pairsWithSecondary || forceSingleColumn) {
                    val taskRect = if (leftW >= rightW) {
                        WorkspaceRect(0, 0, leftW, height)
                    } else {
                        WorkspaceRect(width - rightW, 0, rightW, height)
                    }
                    AdaptiveWorkspace(
                        arrangement = WorkspaceArrangement.SINGLE,
                        panes = listOf(WorkspacePane(PaneRole.TASK, taskRect)),
                        hingeGap = hingeGap,
                        regionDriven = true,
                        singleColumnFallback = forceSingleColumn,
                        fontScale = scale,
                    )
                } else {
                    // Task in the left region, companion (map/detail) on the right.
                    AdaptiveWorkspace(
                        arrangement = WorkspaceArrangement.SIDE_BY_SIDE,
                        panes = listOf(
                            WorkspacePane(PaneRole.TASK, WorkspaceRect(0, 0, leftW, height)),
                            WorkspacePane(
                                PaneRole.COMPANION,
                                WorkspaceRect(width - rightW, 0, rightW, height),
                            ),
                        ),
                        hingeGap = hingeGap,
                        // Never let the user drag the split off a real hinge.
                        divider = null,
                        regionDriven = true,
                        fontScale = scale,
                    )
                }
            }

            FoldOrientation.HORIZONTAL -> {
                val topH = region.start
                val bottomH = height - region.start - gapSize
                val hingeGap = if (occluding) {
                    WorkspaceRect(0, region.start, width, region.size)
                } else {
                    null
                }
                val minOverview = scaled(TABLETOP_MIN_OVERVIEW, scale)
                val minTask = scaled(TABLETOP_MIN_TASK, scale)
                // Tabletop only when both halves can hold their content; otherwise
                // collapse support into the larger half without hiding the task.
                val tabletopFits = task.pairsWithSecondary &&
                    !forceSingleColumn &&
                    topH >= minOverview &&
                    bottomH >= minTask
                if (tabletopFits) {
                    AdaptiveWorkspace(
                        arrangement = WorkspaceArrangement.STACKED,
                        panes = listOf(
                            // Task + controls sit below the fold, within reach.
                            WorkspacePane(
                                PaneRole.TASK,
                                WorkspaceRect(0, height - bottomH, width, bottomH),
                            ),
                            // Overview (map / route / big departures) above it.
                            WorkspacePane(
                                PaneRole.COMPANION,
                                WorkspaceRect(0, 0, width, topH),
                            ),
                        ),
                        hingeGap = hingeGap,
                        divider = null,
                        regionDriven = true,
                        fontScale = scale,
                    )
                } else {
                    // Not enough for two halves: keep the task in the larger,
                    // usable region, clear of the hinge, companion via a switch.
                    val useTop = topH >= bottomH
                    val taskRect = if (useTop) {
                        WorkspaceRect(0, 0, width, topH)
                    } else {
                        WorkspaceRect(0, height - bottomH, width, bottomH)
                    }
                    AdaptiveWorkspace(
                        arrangement = WorkspaceArrangement.SINGLE,
                        panes = listOf(WorkspacePane(PaneRole.TASK, taskRect)),
                        hingeGap = hingeGap,
                        regionDriven = true,
                        singleColumnFallback = forceSingleColumn,
                        fontScale = scale,
                    )
                }
            }
        }
    }

    private fun resolveForWindow(
        width: Int,
        height: Int,
        task: WorkspaceTask,
        scale: Float,
        forceSingleColumn: Boolean,
    ): AdaptiveWorkspace {
        // Large text behaves like a short window: one scrolling task column.
        val largeText = scale >= 1.35f
        val base = ContentBreakpoint.resolve(
            width = width,
            height = height,
            forceSingleColumn = forceSingleColumn || largeText,
        )

        val wantsTwoPanes = task.pairsWithSecondary &&
            !base.singleColumnFallback &&
            base.secondaryPaneWidth != null

        if (!wantsTwoPanes) {
            // When the window is wide but the task does not pair (a form), keep a
            // readable bounded column instead of a two-pane primary width.
            val single = if (base.secondaryPaneWidth != null) {
                ContentBreakpoint.resolve(width, height, forceSingleColumn = true)
            } else {
                base
            }
            return AdaptiveWorkspace(
                arrangement = WorkspaceArrangement.SINGLE,
                panes = listOf(
                    WorkspacePane(
                        PaneRole.TASK,
                        WorkspaceRect(single.outerInset, 0, single.contentWidth, height),
                    ),
                ),
                singleColumnFallback = single.singleColumnFallback,
                fontScale = scale,
            )
        }

        val primaryW = base.primaryPaneWidth!!
        val secondaryW = base.secondaryPaneWidth!!
        val minCompanion = scaled(MIN_COMPANION, scale)

        // Larger text can push the companion below its floor: collapse to single.
        if (secondaryW < minCompanion) {
            val readable = ContentBreakpoint.resolve(width, height, forceSingleColumn = true)
            return AdaptiveWorkspace(
                arrangement = WorkspaceArrangement.SINGLE,
                panes = listOf(
                    WorkspacePane(
                        PaneRole.TASK,
                        WorkspaceRect(readable.outerInset, 0, readable.contentWidth, height),
                    ),
                ),
                singleColumnFallback = true,
                fontScale = scale,
            )
        }

        val taskLeft = base.outerInset
        val companionLeft = base.outerInset + primaryW + base.columnGap

        val panes = mutableListOf(
            WorkspacePane(PaneRole.TASK, WorkspaceRect(taskLeft, 0, primaryW, height)),
        )

        // Very wide canvas: offer a third inspector, taken out of the companion,
        // only when everything still meets its floor. Hide it first when tight.
        val minInspector = scaled(MIN_INSPECTOR, scale)
        val canShowInspector = width >= INSPECTOR_MIN_CANVAS &&
            task.pairsWithSecondary &&
            (secondaryW - GAP - minInspector) >= minCompanion
        if (canShowInspector) {
            val companionW = secondaryW - GAP - minInspector
            val inspectorLeft = companionLeft + companionW + GAP
            panes += WorkspacePane(
                PaneRole.COMPANION,
                WorkspaceRect(companionLeft, 0, companionW, height),
            )
            panes += WorkspacePane(
                PaneRole.INSPECTOR,
                WorkspaceRect(inspectorLeft, 0, minInspector, height),
            )
        } else {
            panes += WorkspacePane(
                PaneRole.COMPANION,
                WorkspaceRect(companionLeft, 0, secondaryW, height),
            )
        }

        // The split is adjustable here: no physical hinge is involved. Clamp the
        // primary so neither the task nor the companion starves.
        val available = width - base.outerInset * 2 - base.columnGap
        val dividerMin = scaled(320, scale)
        val dividerMax = available - minCompanion
        val divider = if (dividerMax > dividerMin) {
            WorkspaceDivider(FoldOrientation.VERTICAL, dividerMin, dividerMax)
        } else {
            null
        }

        return AdaptiveWorkspace(
            arrangement = WorkspaceArrangement.SIDE_BY_SIDE,
            panes = panes,
            divider = divider,
            regionDriven = false,
            fontScale = scale,
        )
    }

    private fun scaled(base: Int, scale: Float): Int =
        if (scale <= 1f) base else (base * scale).toInt()
}
