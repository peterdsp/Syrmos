import SwiftUI

// Reserved-region adapter (six-posture prompt, section 9, item 2; parent prompt
// section 9.4).
//
// The system reports the fold division and any dynamic occlusion (an opaque
// hinge, a camera cutout) as regions with a frame, a kind and an active flag.
// This file turns those reports into the platform-neutral `SyrmosReservedRegion`
// values the shared policy consumes, and into the cutouts that custom overlays
// and map padding keep clear of. The normalisation is PURE and runs on every
// system; only the read from `GeometryProxy.reservedRegions` is an iOS 27.1 SDK
// symbol and stays behind `SYRMOS_DUO_SDK` plus a runtime availability check,
// exactly like `SyrmosArrangement` (see docs/design/FOLDABLE-READINESS.md).
//
// Native containers (`ArrangementView`, `NavigationSplitView`) keep owning
// paired content. The adapter informs genuinely custom geometry: the GO route
// map's padding, and later any floating control that must not sit on a hinge.

/// One region as the system reported it, before normalisation. `frame` is in
/// the reporting container's own coordinate space.
struct SyrmosRawReservedRegion: Equatable {
    var kind: SyrmosRegionKind
    var frame: CGRect
    var isActive: Bool = true
}

/// The regions of one content box, normalised into that box's coordinates.
///
/// - `regions` are bars that span the box (a fold line, an opaque hinge) and
///   feed `SyrmosAdaptiveWorkspacePolicy` as its `regions` input.
/// - `cutouts` are active occlusions that do NOT span the box (a camera
///   cutout). They never split the layout; controls and map padding avoid them.
struct SyrmosReservedGeometry: Equatable {
    var regions: [SyrmosReservedRegion] = []
    var cutouts: [CGRect] = []

    static let none = SyrmosReservedGeometry()

    var isEmpty: Bool { regions.isEmpty && cutouts.isEmpty }

    /// The active occlusions that span the box, the only regions map padding
    /// and overlay placement must treat as unavailable space.
    var activeOcclusions: [SyrmosReservedRegion] {
        regions.filter { $0.kind == .occlusion && $0.active }
    }
}

enum SyrmosReservedRegionAdapter {

    /// A bar counts as spanning the box when it covers at least this share of
    /// the box's extent on its own axis. A camera cutout covers far less and is
    /// therefore a cutout, not a division of the layout.
    static let spanTolerance: CGFloat = 0.9

    /// Normalise raw regions into `box` coordinates. Regions that do not cross
    /// the box are dropped (a fold beside the box must not split it). Frames are
    /// translated once, clipped to the box, and rounded to whole points.
    static func normalize(_ raw: [SyrmosRawReservedRegion], in box: CGRect) -> SyrmosReservedGeometry {
        var out = SyrmosReservedGeometry()
        guard box.width > 0, box.height > 0 else { return out }
        let width = box.width
        let height = box.height

        for r in raw {
            // Translate into box coordinates exactly once.
            let f = r.frame.offsetBy(dx: -box.minX, dy: -box.minY)
            // A zero-thickness division line still crosses when it lies strictly
            // inside; a bar on the very edge reserves nothing inside the box.
            let crossesX = f.maxX > 0 && f.minX < width
            let crossesY = f.maxY > 0 && f.minY < height
            guard crossesX, crossesY else { continue }

            let vertical = f.height >= f.width
            let covered: CGFloat
            if vertical {
                covered = min(f.maxY, height) - max(f.minY, 0)
            } else {
                covered = min(f.maxX, width) - max(f.minX, 0)
            }
            let spans = covered >= (vertical ? height : width) * spanTolerance

            if spans {
                let start: CGFloat
                let size: CGFloat
                if vertical {
                    start = max(f.minX, 0)
                    size = min(f.maxX, width) - start
                } else {
                    start = max(f.minY, 0)
                    size = min(f.maxY, height) - start
                }
                out.regions.append(SyrmosReservedRegion(
                    kind: r.kind,
                    orientation: vertical ? .vertical : .horizontal,
                    start: Int(start.rounded()),
                    size: Int(size.rounded(.up)),
                    active: r.isActive
                ))
            } else if r.kind == .occlusion, r.isActive {
                let clipped = f.intersection(CGRect(origin: .zero, size: box.size))
                if !clipped.isNull, clipped.width > 0, clipped.height > 0 {
                    out.cutouts.append(clipped)
                }
            }
            // A partial or inactive division reserves nothing.
        }
        return out
    }
}

// MARK: - Reading the system (iOS 27.1 SDK, gated)

extension GeometryProxy {

    /// Every region the system reports for this proxy, both kinds, including
    /// inactive ones (a flat, non-separating crease is a structural hint the
    /// policy must see as inactive, never as blank pixels). Empty on a system or
    /// a toolchain without the Duo API.
    func syrmosRawReservedRegions() -> [SyrmosRawReservedRegion] {
        #if SYRMOS_DUO_SDK
        if #available(iOS 27.1, *) {
            let occlusions = reservedRegions(kind: .occlusion, options: .includeInactive).map {
                SyrmosRawReservedRegion(kind: .occlusion, frame: $0.frame, isActive: $0.isActive)
            }
            let divisions = reservedRegions(kind: .division, options: .includeInactive).map {
                SyrmosRawReservedRegion(kind: .division, frame: $0.frame, isActive: $0.isActive)
            }
            return occlusions + divisions
        }
        #endif
        return []
    }

    /// The normalised geometry of this proxy's own box.
    func syrmosReservedGeometry() -> SyrmosReservedGeometry {
        SyrmosReservedRegionAdapter.normalize(
            syrmosRawReservedRegions(),
            in: CGRect(origin: .zero, size: size)
        )
    }
}

// MARK: - Test seam

/// Lets a test or a preview inject the geometry a box would read from the
/// system, so hinge-aware layout can be rendered on a simulator that reports
/// no regions. nil means "read the system".
private struct SyrmosReservedGeometryOverrideKey: EnvironmentKey {
    static let defaultValue: SyrmosReservedGeometry? = nil
}

extension EnvironmentValues {
    var syrmosReservedGeometryOverride: SyrmosReservedGeometry? {
        get { self[SyrmosReservedGeometryOverrideKey.self] }
        set { self[SyrmosReservedGeometryOverrideKey.self] = newValue }
    }
}

// MARK: - Map padding

/// Edge insets in points, kept as a plain value so the rule is testable without
/// UIKit and convertible where a `UIEdgeInsets` is needed.
struct SyrmosEdgeInsets: Equatable {
    var top: CGFloat
    var left: CGFloat
    var bottom: CGFloat
    var right: CGFloat

    static func all(_ value: CGFloat) -> SyrmosEdgeInsets {
        SyrmosEdgeInsets(top: value, left: value, bottom: value, right: value)
    }
}

/// Padding for a map that must keep its content visible around a hinge or a
/// cutout (parent prompt 9.4, rule 6: compute map padding from the visible
/// panel and the occupied regions; never resolve a collision by resetting the
/// camera or inserting a screen-wide gutter).
enum SyrmosMapPadding {

    /// The breathing room every fitted route keeps from the map's edges.
    static let base: CGFloat = 44

    /// Insets for a map occupying `mapRect` (box coordinates) in a box with the
    /// given `geometry`. An active occluding bar that crosses the map gives up
    /// the smaller side of the map; a cutout that touches the map insets the
    /// nearest edge past it. Divisions never pad: content may bridge them.
    static func insets(
        mapRect: CGRect,
        geometry: SyrmosReservedGeometry,
        base: CGFloat = SyrmosMapPadding.base
    ) -> SyrmosEdgeInsets {
        var ins = SyrmosEdgeInsets.all(base)
        guard mapRect.width > 0, mapRect.height > 0 else { return ins }

        for r in geometry.activeOcclusions {
            let barStart = CGFloat(r.start)
            let barEnd = CGFloat(r.start + r.size)
            switch r.orientation {
            case .horizontal:
                guard barEnd > mapRect.minY, barStart < mapRect.maxY else { continue }
                let above = barStart - mapRect.minY
                let below = mapRect.maxY - barEnd
                if above >= below {
                    ins.bottom = max(ins.bottom, mapRect.maxY - barStart + base)
                } else {
                    ins.top = max(ins.top, barEnd - mapRect.minY + base)
                }
            case .vertical:
                guard barEnd > mapRect.minX, barStart < mapRect.maxX else { continue }
                let left = barStart - mapRect.minX
                let right = mapRect.maxX - barEnd
                if left >= right {
                    ins.right = max(ins.right, mapRect.maxX - barStart + base)
                } else {
                    ins.left = max(ins.left, barEnd - mapRect.minX + base)
                }
            }
        }

        for cutout in geometry.cutouts where cutout.intersects(mapRect) {
            let i = cutout.intersection(mapRect)
            let fromTop = i.maxY - mapRect.minY
            let fromBottom = mapRect.maxY - i.minY
            let fromLeft = i.maxX - mapRect.minX
            let fromRight = mapRect.maxX - i.minX
            let nearest = min(fromTop, fromBottom, fromLeft, fromRight)
            if nearest == fromTop {
                ins.top = max(ins.top, fromTop + base)
            } else if nearest == fromBottom {
                ins.bottom = max(ins.bottom, fromBottom + base)
            } else if nearest == fromLeft {
                ins.left = max(ins.left, fromLeft + base)
            } else {
                ins.right = max(ins.right, fromRight + base)
            }
        }

        // Never invert the visible area: leave at least a quarter of each axis.
        let maxVertical = mapRect.height * 0.75
        let maxHorizontal = mapRect.width * 0.75
        if ins.top + ins.bottom > maxVertical {
            let scale = maxVertical / (ins.top + ins.bottom)
            ins.top *= scale
            ins.bottom *= scale
        }
        if ins.left + ins.right > maxHorizontal {
            let scale = maxHorizontal / (ins.left + ins.right)
            ins.left *= scale
            ins.right *= scale
        }
        return ins
    }

    /// The point, in a view of `size`, at the centre of the padded visible area.
    static func visibleCenter(size: CGSize, insets: SyrmosEdgeInsets) -> CGPoint {
        CGPoint(
            x: (insets.left + (size.width - insets.right)) / 2,
            y: (insets.top + (size.height - insets.bottom)) / 2
        )
    }

    /// The view point that must be placed under the view's geometric centre so
    /// that whatever sits there today lands on the padded visible centre
    /// instead: the geometric centre mirrored through the visible centre.
    static func compensatingPoint(size: CGSize, insets: SyrmosEdgeInsets) -> CGPoint {
        let c = CGPoint(x: size.width / 2, y: size.height / 2)
        let v = visibleCenter(size: size, insets: insets)
        return CGPoint(x: 2 * c.x - v.x, y: 2 * c.y - v.y)
    }
}
