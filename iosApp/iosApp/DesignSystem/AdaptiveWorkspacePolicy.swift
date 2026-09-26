import CoreGraphics
import Foundation
import SwiftUI

/// Dynamic Type as the policy's font scale: the body text size of each
/// category over the default 17 pt (UIFontMetrics body sizes), so a larger
/// setting raises every pane floor by the same factor and the accessibility
/// sizes (1.35 and above) collapse to one readable column.
enum SyrmosDynamicType {
    static func fontScale(_ size: DynamicTypeSize) -> Float {
        let body: Float
        switch size {
        case .xSmall: body = 14
        case .small: body = 15
        case .medium: body = 16
        case .large: body = 17
        case .xLarge: body = 19
        case .xxLarge: body = 21
        case .xxxLarge: body = 23
        case .accessibility1: body = 28
        case .accessibility2: body = 33
        case .accessibility3: body = 40
        case .accessibility4: body = 47
        case .accessibility5: body = 53
        @unknown default: body = 17
        }
        return body / 17
    }
}

// Syrmos adaptive workspace policy, Swift mirror.
//
// This is the iOS twin of the shared Kotlin policy in
// `core/common/src/commonMain/kotlin/com/syrmos/core/common/layout/AdaptiveWorkspace.kt`
// (and of `ContentBreakpoint.kt`, which it folds in for the plain-window path).
// It is a PURE function from the usable content geometry, the reserved regions
// the system reports, the text scale and the current task to a set of semantic
// layout decisions: how many panes, which role goes where, what stays clear of a
// physical hinge, and whether an accessible divider may be dragged.
//
// The two implementations are kept in lockstep by twin fixture suites:
// `AdaptiveWorkspaceTest.kt` / `DuoPostureFixturesTest.kt` on the Kotlin side and
// `DuoPostureFixturesTests.swift` here. Same names, same inputs, same numbers.
// Change the rule in both files, then make both suites green.
//
// Native SwiftUI containers (`NavigationSplitView`, `ArrangementView`) keep owning
// paired content on iOS; this policy decides the AXIS and the roles, and informs
// genuinely custom geometry such as map padding. It never reads a device model.

/// Which way a reported fold or hinge bar runs across the window.
enum SyrmosFoldOrientation {
    /// Hinge line runs top to bottom, so the two regions are LEFT and RIGHT.
    case vertical
    /// Hinge line runs left to right, so the two regions are TOP and BOTTOM.
    case horizontal
}

/// A reported system region kind, in the window's own content coordinate space.
enum SyrmosRegionKind {
    /// A layout division (a flat or inactive crease, or a separating fold that
    /// still lets a continuous list scroll across it). Content MAY bridge it.
    case division
    /// An occlusion: a physically opaque hinge or a dynamic cutout. Content,
    /// touch targets and controls must NOT be placed inside it.
    case occlusion
}

/// A reserved region, already normalised into the app window's content
/// coordinates. `start` is the offset of the bar (from the left for a vertical
/// bar, from the top for a horizontal one) and `size` is its thickness.
/// `active` distinguishes a live separation from a structural hint that must not
/// blank out pixels.
struct SyrmosReservedRegion: Equatable {
    var kind: SyrmosRegionKind
    var orientation: SyrmosFoldOrientation
    var start: Int
    var size: Int
    var active: Bool = true
}

/// The pairing axis a task prefers when the window itself does not dictate one.
enum SyrmosPairAxis { case sideBySide, stacked }

/// The task currently driving the workspace, so the policy is task aware.
enum SyrmosWorkspaceTask {
    /// `map`: the network map, whose inspector (station or train) is the task pane and whose canvas is the companion.
    case home, plan, go, explore, map, departures, fares, ariadne
    /// Settings, onboarding and other bounded single-focus forms.
    case form

    /// Whether this task gains from a paired secondary pane (a map or a detail)
    /// when the space genuinely fits. A form stays single focus.
    var pairsWithSecondary: Bool { self != .form }

    /// The axis this task prefers on a tall, medium-width plain window such as
    /// the iPhone Duo inner display held upright (P5 Tall canvas). A planner, a
    /// fares form, a departures board or the assistant read well as two columns;
    /// a journey in progress and a browse list want the map above and the list
    /// plus controls below, where the hands are.
    /// Whether the task may pair on the stacked axis at all. Home never does:
    /// the answer must lead, so when two columns do not fit it keeps its single
    /// column instead of putting the network context above the next train.
    var stacks: Bool { self != .home }
    /// Whether the task pane is the main event (Home): on a large canvas it
    /// splits the width evenly with its companion instead of the fixed column.
    var leadsWithTask: Bool { self == .home }

    var tallCanvasAxis: SyrmosPairAxis {
        switch self {
        case .go, .explore, .map: return .stacked
        default: return .sideBySide
        }
    }
}

/// How the workspace is arranged.
enum SyrmosWorkspaceArrangement {
    /// One task pane. The map or detail is reached through an explicit switch.
    case single
    /// Task or list beside map or detail (a vertical fold, or a wide flat window).
    case sideBySide
    /// Overview above, task plus controls below (tabletop, a horizontal fold, or
    /// a tall medium-width window such as the Duo inner display held upright).
    case stacked
}

/// The semantic role a pane plays, so each client maps it to its own content.
enum SyrmosPaneRole { case task, companion, inspector }

/// An integer rectangle in the window content coordinate space (points).
struct SyrmosWorkspaceRect: Equatable {
    var left: Int
    var top: Int
    var width: Int
    var height: Int
    var right: Int { left + width }
    var bottom: Int { top + height }
}

/// A placed pane: its role and the rectangle it may use.
struct SyrmosWorkspacePane: Equatable {
    var role: SyrmosPaneRole
    var rect: SyrmosWorkspaceRect
}

/// A user-adjustable divider between two panes. Present only on an unobstructed
/// large layout, never across a real separating or occluding hinge.
struct SyrmosWorkspaceDivider: Equatable {
    var orientation: SyrmosFoldOrientation
    var min: Int
    var max: Int
}

/// The resolved workspace. See the Kotlin `AdaptiveWorkspace` for field notes.
struct SyrmosAdaptiveWorkspace: Equatable {
    var arrangement: SyrmosWorkspaceArrangement
    /// Placed panes in draw order (task first).
    var panes: [SyrmosWorkspacePane]
    /// An occluding region to keep clear of all content, or nil.
    var hingeGap: SyrmosWorkspaceRect? = nil
    var divider: SyrmosWorkspaceDivider? = nil
    /// True when a reported region, not the width rule, decided the split.
    var regionDriven: Bool = false
    /// True when a short window or large text forced a single task column.
    var singleColumnFallback: Bool = false
    var fontScale: Float = 1

    func pane(_ role: SyrmosPaneRole) -> SyrmosWorkspacePane? {
        panes.first { $0.role == role }
    }
    var hasCompanion: Bool { pane(.companion) != nil }
    var hasInspector: Bool { pane(.inspector) != nil }
}

/// Mirror of `ContentBreakpoint.kt`: the Syrmos content breakpoints (600, 840,
/// 1200) and the short-window and large-text collapse rules.
enum SyrmosContentBreakpoint {
    enum Mode { case compact, medium, expanded, wide }

    struct Layout: Equatable {
        var mode: Mode
        var outerInset: Int
        var contentWidth: Int
        var primaryPaneWidth: Int? = nil
        var secondaryPaneWidth: Int? = nil
        var columnGap: Int = 0
        var singleColumnFallback: Bool = false
    }

    static let minSecondaryPane = 360
    static let shortHeight = 480
    static let wideCanvasMax = 1600

    static func resolve(width: Int, height: Int, forceSingleColumn: Bool = false) -> Layout {
        let short = height < shortHeight
        if short || forceSingleColumn {
            let (inset, w) = readableColumn(width)
            return Layout(
                mode: width >= 600 ? .medium : .compact,
                outerInset: inset,
                contentWidth: w,
                singleColumnFallback: true
            )
        }
        switch width {
        case ..<600:
            return Layout(mode: .compact, outerInset: 16, contentWidth: width - 32)
        case ..<840:
            let (inset, w) = readableColumn(width)
            return Layout(mode: .medium, outerInset: inset, contentWidth: w)
        case ..<1200:
            return twoPane(width: width, primary: 360, outerInset: 24)
        default:
            return twoPane(width: Swift.min(width, wideCanvasMax), primary: 400, outerInset: 32)
        }
    }

    private static func readableColumn(_ width: Int) -> (Int, Int) {
        let contentWidth = Swift.min(width - 48, 680)
        let inset = Swift.max(24, (width - contentWidth) / 2)
        return (inset, contentWidth)
    }

    private static func twoPane(width: Int, primary: Int, outerInset: Int) -> Layout {
        let gap = 24
        let secondary = width - (outerInset * 2) - primary - gap
        if secondary < minSecondaryPane {
            let (inset, w) = readableColumn(width)
            return Layout(mode: .medium, outerInset: inset, contentWidth: w)
        }
        return Layout(
            mode: primary >= 400 ? .wide : .expanded,
            outerInset: outerInset,
            contentWidth: primary,
            primaryPaneWidth: primary,
            secondaryPaneWidth: secondary,
            columnGap: gap
        )
    }
}

/// The canonical adaptive-workspace policy. Deterministic and side-effect free,
/// so the same inputs produce the same layout on every client and in tests.
enum SyrmosAdaptiveWorkspacePolicy {

    // Tabletop (horizontal split) fit floors at fontScale 1.0.
    static let tabletopMinOverview = 220
    static let tabletopMinTask = 280

    static let minCompanion = SyrmosContentBreakpoint.minSecondaryPane // 360
    static let minInspector = 280
    static let inspectorMinCanvas = 1280

    // Pane floors on a medium-width plain window (six-posture prompt, section 5).
    // Two side-by-side panes split the width evenly with no outer inset, so a
    // 669-wide Duo inner display yields two 334-wide panes, and 640 is the
    // narrowest width that still fits the map.
    static let minTaskPane = 300
    static let minMapPane = 320

    // A tall stacked canvas keeps the map at least this tall, at about this
    // share of the height, with the task below at the tabletop task floor.
    static let tallMinCompanion = 360
    static let tallCompanionRatio: Float = 0.45

    static let mediumMinWidth = 600
    /// The narrowest tall canvas that still stacks a map or overview above the
    /// task (T7 Tall narrow canvas): an upright fold whose window hosts a
    /// navigation rail; a phone column (440, the Duo cover at 466) never stacks.
    static let tallNarrowMinWidth = 480
    /// The widest canvas on which a tall window still prefers the task's stacked
    /// axis; from here (an upright iPad at 1032) two comfortable columns win.
    static let tallStackMaxWidth = 840

    private static let gap = 24

    /// Resolve for a measured content size (points are floored to whole units).
    static func resolve(
        size: CGSize,
        task: SyrmosWorkspaceTask,
        regions: [SyrmosReservedRegion] = [],
        fontScale: Float = 1,
        forceSingleColumn: Bool = false
    ) -> SyrmosAdaptiveWorkspace {
        resolve(
            width: Int(size.width.rounded(.down)),
            height: Int(size.height.rounded(.down)),
            task: task,
            regions: regions,
            fontScale: fontScale,
            forceSingleColumn: forceSingleColumn
        )
    }

    /// Resolve the workspace.
    ///
    /// - Parameters:
    ///   - width: usable content width AFTER system bars, safe-area insets, a
    ///     visible keyboard and native navigation (never the physical screen).
    ///   - height: usable content height, same contract.
    ///   - task: the current task (drives whether a companion is offered).
    ///   - regions: reported regions in content coordinates; a bar outside the
    ///     window is ignored.
    ///   - fontScale: accessibility text scale; raises every content minimum.
    ///   - forceSingleColumn: caller override (an accessibility preference).
    static func resolve(
        width: Int,
        height: Int,
        task: SyrmosWorkspaceTask,
        regions: [SyrmosReservedRegion] = [],
        fontScale: Float = 1,
        forceSingleColumn: Bool = false
    ) -> SyrmosAdaptiveWorkspace {
        let scale: Float = (fontScale.isNaN || fontScale < 1) ? 1 : fontScale

        // 1. An ACTIVE region inside the window overrides every width ratio.
        if let region = activeRegionWithin(regions, width: width, height: height) {
            return resolveForRegion(
                width: width, height: height, task: task, region: region,
                scale: scale, forceSingleColumn: forceSingleColumn
            )
        }
        // 2. No fold information: the plain window rule, task and font aware.
        return resolveForWindow(
            width: width, height: height, task: task,
            scale: scale, forceSingleColumn: forceSingleColumn
        )
    }

    // The first active region that actually crosses the window. Occlusion wins
    // over a division if both are reported.
    private static func activeRegionWithin(
        _ regions: [SyrmosReservedRegion], width: Int, height: Int
    ) -> SyrmosReservedRegion? {
        let crossing = regions.filter { r in
            guard r.active else { return false }
            if r.kind == .occlusion && r.size <= 0 { return false }
            switch r.orientation {
            case .vertical: return r.start > 0 && r.start < width
            case .horizontal: return r.start > 0 && r.start < height
            }
        }
        return crossing.first { $0.kind == .occlusion } ?? crossing.first
    }

    private static func resolveForRegion(
        width: Int, height: Int, task: SyrmosWorkspaceTask,
        region: SyrmosReservedRegion, scale: Float, forceSingleColumn: Bool
    ) -> SyrmosAdaptiveWorkspace {
        let occluding = region.kind == .occlusion
        let gapSize = occluding ? region.size : 0

        switch region.orientation {
        case .vertical:
            let leftW = region.start
            let rightW = width - region.start - gapSize
            let hingeGap = occluding
                ? SyrmosWorkspaceRect(left: region.start, top: 0, width: region.size, height: height)
                : nil
            if !task.pairsWithSecondary || forceSingleColumn {
                let taskRect = leftW >= rightW
                    ? SyrmosWorkspaceRect(left: 0, top: 0, width: leftW, height: height)
                    : SyrmosWorkspaceRect(left: width - rightW, top: 0, width: rightW, height: height)
                return SyrmosAdaptiveWorkspace(
                    arrangement: .single,
                    panes: [SyrmosWorkspacePane(role: .task, rect: taskRect)],
                    hingeGap: hingeGap,
                    regionDriven: true,
                    singleColumnFallback: forceSingleColumn,
                    fontScale: scale
                )
            }
            return SyrmosAdaptiveWorkspace(
                arrangement: .sideBySide,
                panes: [
                    SyrmosWorkspacePane(role: .task,
                                        rect: SyrmosWorkspaceRect(left: 0, top: 0, width: leftW, height: height)),
                    SyrmosWorkspacePane(role: .companion,
                                        rect: SyrmosWorkspaceRect(left: width - rightW, top: 0, width: rightW, height: height)),
                ],
                hingeGap: hingeGap,
                divider: nil,
                regionDriven: true,
                fontScale: scale
            )

        case .horizontal:
            let topH = region.start
            let bottomH = height - region.start - gapSize
            let hingeGap = occluding
                ? SyrmosWorkspaceRect(left: 0, top: region.start, width: width, height: region.size)
                : nil
            let minOverview = scaled(tabletopMinOverview, scale)
            let minTask = scaled(tabletopMinTask, scale)
            let tabletopFits = task.pairsWithSecondary && !forceSingleColumn
                && topH >= minOverview && bottomH >= minTask
            if tabletopFits {
                return SyrmosAdaptiveWorkspace(
                    arrangement: .stacked,
                    panes: [
                        SyrmosWorkspacePane(role: .task,
                                            rect: SyrmosWorkspaceRect(left: 0, top: height - bottomH, width: width, height: bottomH)),
                        SyrmosWorkspacePane(role: .companion,
                                            rect: SyrmosWorkspaceRect(left: 0, top: 0, width: width, height: topH)),
                    ],
                    hingeGap: hingeGap,
                    divider: nil,
                    regionDriven: true,
                    fontScale: scale
                )
            }
            let useTop = topH >= bottomH
            let taskRect = useTop
                ? SyrmosWorkspaceRect(left: 0, top: 0, width: width, height: topH)
                : SyrmosWorkspaceRect(left: 0, top: height - bottomH, width: width, height: bottomH)
            return SyrmosAdaptiveWorkspace(
                arrangement: .single,
                panes: [SyrmosWorkspacePane(role: .task, rect: taskRect)],
                hingeGap: hingeGap,
                regionDriven: true,
                singleColumnFallback: forceSingleColumn,
                fontScale: scale
            )
        }
    }

    private static func resolveForWindow(
        width: Int, height: Int, task: SyrmosWorkspaceTask,
        scale: Float, forceSingleColumn: Bool
    ) -> SyrmosAdaptiveWorkspace {
        let largeText = scale >= 1.35
        let base = SyrmosContentBreakpoint.resolve(
            width: width, height: height, forceSingleColumn: forceSingleColumn || largeText
        )
        let wantsTwoPanes = task.pairsWithSecondary
            && !base.singleColumnFallback
            && base.secondaryPaneWidth != nil

        if !wantsTwoPanes {
            // A medium-width window that is neither short nor at large text can
            // still pair a task that wants a companion, on the axis the task
            // prefers (P5 Tall canvas). A form keeps its bounded column.
            let mediumCanvas = task.pairsWithSecondary
                && !forceSingleColumn
                && !largeText
                && !base.singleColumnFallback
                && base.secondaryPaneWidth == nil
                && width >= mediumMinWidth
            if mediumCanvas, let ws = resolveMediumCanvas(width: width, height: height, task: task, scale: scale) {
                return ws
            }
            // T7 Tall narrow canvas: under the medium floor because a navigation
            // rail took its share, yet tall; the tasks that read as map above and
            // task below (GO, Explore, Map) still stack.
            let tallNarrow = task.pairsWithSecondary
                && task.tallCanvasAxis == .stacked
                && !forceSingleColumn
                && !largeText
                && height > width
                && width >= tallNarrowMinWidth
                && width < mediumMinWidth
            if tallNarrow, let ws = mediumStacked(width: width, height: height, scale: scale) {
                return ws
            }
            let single = base.secondaryPaneWidth != nil
                ? SyrmosContentBreakpoint.resolve(width: width, height: height, forceSingleColumn: true)
                : base
            return SyrmosAdaptiveWorkspace(
                arrangement: .single,
                panes: [SyrmosWorkspacePane(
                    role: .task,
                    rect: SyrmosWorkspaceRect(left: single.outerInset, top: 0, width: single.contentWidth, height: height)
                )],
                singleColumnFallback: single.singleColumnFallback,
                fontScale: scale
            )
        }

        let basePrimary = base.primaryPaneWidth!
        let baseSecondary = base.secondaryPaneWidth!
        // A task that leads with its task pane splits the canvas evenly.
        let primaryW = task.leadsWithTask ? (basePrimary + baseSecondary) / 2 : basePrimary
        let secondaryW = basePrimary + baseSecondary - primaryW
        let minCompanionScaled = scaled(minCompanion, scale)

        if secondaryW < minCompanionScaled {
            let readable = SyrmosContentBreakpoint.resolve(width: width, height: height, forceSingleColumn: true)
            return SyrmosAdaptiveWorkspace(
                arrangement: .single,
                panes: [SyrmosWorkspacePane(
                    role: .task,
                    rect: SyrmosWorkspaceRect(left: readable.outerInset, top: 0, width: readable.contentWidth, height: height)
                )],
                singleColumnFallback: true,
                fontScale: scale
            )
        }

        let taskLeft = base.outerInset
        let companionLeft = base.outerInset + primaryW + base.columnGap
        var panes = [SyrmosWorkspacePane(
            role: .task,
            rect: SyrmosWorkspaceRect(left: taskLeft, top: 0, width: primaryW, height: height)
        )]

        let minInspectorScaled = scaled(minInspector, scale)
        let canShowInspector = width >= inspectorMinCanvas
            && task.pairsWithSecondary
            && (secondaryW - gap - minInspectorScaled) >= minCompanionScaled
        if canShowInspector {
            let companionW = secondaryW - gap - minInspectorScaled
            let inspectorLeft = companionLeft + companionW + gap
            panes.append(SyrmosWorkspacePane(
                role: .companion,
                rect: SyrmosWorkspaceRect(left: companionLeft, top: 0, width: companionW, height: height)
            ))
            panes.append(SyrmosWorkspacePane(
                role: .inspector,
                rect: SyrmosWorkspaceRect(left: inspectorLeft, top: 0, width: minInspectorScaled, height: height)
            ))
        } else {
            panes.append(SyrmosWorkspacePane(
                role: .companion,
                rect: SyrmosWorkspaceRect(left: companionLeft, top: 0, width: secondaryW, height: height)
            ))
        }

        let available = width - base.outerInset * 2 - base.columnGap
        let dividerMin = scaled(320, scale)
        let dividerMax = available - minCompanionScaled
        let divider = dividerMax > dividerMin
            ? SyrmosWorkspaceDivider(orientation: .vertical, min: dividerMin, max: dividerMax)
            : nil

        return SyrmosAdaptiveWorkspace(
            arrangement: .sideBySide,
            panes: panes,
            divider: divider,
            regionDriven: false,
            fontScale: scale
        )
    }

    /// Pair on a medium-width plain window: the task's preferred axis first,
    /// the other axis second, each offered only when both panes meet their
    /// floors at the current text scale. Nil keeps the readable single column.
    private static func resolveMediumCanvas(
        width: Int, height: Int, task: SyrmosWorkspaceTask, scale: Float
    ) -> SyrmosAdaptiveWorkspace? {
        // The task's axis preference is about the TALL canvas (P5): a window that
        // is wider than tall reads as two columns whatever the task.
        let preferred: SyrmosPairAxis = (height > width && width < tallStackMaxWidth) ? task.tallCanvasAxis : .sideBySide
        let order: [SyrmosPairAxis] = preferred == .sideBySide
            ? [.sideBySide, .stacked]
            : [.stacked, .sideBySide]
        for axis in order {
            if axis == .stacked && !task.stacks { continue }
            let ws: SyrmosAdaptiveWorkspace?
            switch axis {
            case .sideBySide: ws = mediumSideBySide(width: width, height: height, scale: scale)
            case .stacked: ws = mediumStacked(width: width, height: height, scale: scale)
            }
            if let ws { return ws }
        }
        return nil
    }

    // Both floors are judged on the floored half so the pairing floor is exactly
    // 2 x minMapPane (640 at default text), the threshold SyrmosArrangement uses.
    private static func mediumSideBySide(width: Int, height: Int, scale: Float) -> SyrmosAdaptiveWorkspace? {
        let taskW = width / 2
        let companionW = width - taskW
        if taskW < scaled(minTaskPane, scale) || taskW < scaled(minMapPane, scale) { return nil }
        return SyrmosAdaptiveWorkspace(
            arrangement: .sideBySide,
            panes: [
                SyrmosWorkspacePane(role: .task,
                                    rect: SyrmosWorkspaceRect(left: 0, top: 0, width: taskW, height: height)),
                SyrmosWorkspacePane(role: .companion,
                                    rect: SyrmosWorkspaceRect(left: taskW, top: 0, width: companionW, height: height)),
            ],
            divider: nil,
            regionDriven: false,
            fontScale: scale
        )
    }

    private static func mediumStacked(width: Int, height: Int, scale: Float) -> SyrmosAdaptiveWorkspace? {
        let minCompanionH = scaled(tallMinCompanion, scale)
        let minTaskH = scaled(tabletopMinTask, scale)
        if height < minCompanionH + minTaskH { return nil }
        let companionH = Swift.max(minCompanionH, Int(Float(height) * tallCompanionRatio))
        let taskH = height - companionH
        return SyrmosAdaptiveWorkspace(
            arrangement: .stacked,
            panes: [
                SyrmosWorkspacePane(role: .task,
                                    rect: SyrmosWorkspaceRect(left: 0, top: companionH, width: width, height: taskH)),
                SyrmosWorkspacePane(role: .companion,
                                    rect: SyrmosWorkspaceRect(left: 0, top: 0, width: width, height: companionH)),
            ],
            divider: nil,
            regionDriven: false,
            fontScale: scale
        )
    }

    private static func scaled(_ base: Int, _ scale: Float) -> Int {
        scale <= 1 ? base : Int(Float(base) * scale)
    }
}
