import SwiftUI
import UIKit
import MapKit

/// Phase R S07: the connection risk of one transfer, computed from the real route
/// model (scheduled clocks). `availableSeconds` is the actual gap between arriving
/// and the next departure; `minimumSeconds` the change time the plan allowed.
struct TransferRisk: Equatable {
    let status: String            // "comfortable" | "tight" | "missed" | "unknown"
    let availableSeconds: Int?
    let minimumSeconds: Int?
}

// The GO screen: guide the rider through a planned journey one instruction at a
// time (board / stay on / get off next / change here / arrived). The current
// instruction is the hero; the get-off cue is emphasised because it is the one
// moment that matters most. Advancing is manual for now (step through your
// journey); GPS / live-position auto-advance is a later phase, so the control is
// labelled "Next stop" rather than implying live tracking.
struct GoJourneyView: View {
    @StateObject private var model: GoJourneyViewModel
    @StateObject private var location = LocationService()
    @Environment(\.dismiss) private var dismiss
    @Environment(\.syrmosReservedGeometryOverride) private var reservedGeometryOverride
    @State private var confirmEnd = false
    // Camera with explicit intent (shared GoCamera reducer): follow the current
    // stop by default, keep the whole route on Fit route, and leave the rider's
    // manual view alone until they ask again.
    @AppStorage("syrmos.go.showCompactMap") private var showCompactMap = false
    @State private var cameraIntent: GoCameraIntent = .follow
    /// The current row's frame in the paired timeline's scroll space, and that
    /// scroll view's height, for the Back to now control (GoTimelineFocus).
    @State private var currentRowFrame: CGRect? = nil
    @State private var timelineViewportHeight: CGFloat = 0
    @State private var cameraCommand: GoCameraAction = .none
    @State private var cameraTick = 0
    /// The last map region the rider saw. The representable is recreated when
    /// the arrangement flips (a fold), so the owner keeps the camera and hands
    /// it back instead of letting the new map refit over a manual view.
    @State private var savedCamera: GoSavedCamera? = nil
    let language: AppLanguage
    private let originName: String
    private let destinationName: String
    private let store: GoActiveJourneyStore?
    private let resuming: Bool
    private let onEnd: (() -> Void)?
    // Phase R S07: per-transfer risk (transferRisks[i] = leg i -> i+1). Empty when
    // unknown (e.g. resumed session), which shows no warning.
    private let transferRisks: [TransferRisk]
    /// Opens fresh results from the current confirmed station to the destination.
    private let onFindAlternatives: ((_ fromStationId: String, _ toStationId: String) -> Void)?

    init(
        journey: GuidanceJourney, language: AppLanguage,
        coords: [String: GoLocationAdvancer.Coord] = [:],
        store: GoActiveJourneyStore? = nil, resuming: Bool = false,
        transferRisks: [TransferRisk] = [],
        onFindAlternatives: ((String, String) -> Void)? = nil,
        onEnd: (() -> Void)? = nil
    ) {
        _model = StateObject(wrappedValue: GoJourneyViewModel(journey: journey, coords: coords))
        self.language = language
        self.originName = journey.legs.first?.stops.first?.name ?? ""
        self.destinationName = journey.legs.last?.stops.last?.name ?? ""
        self.store = store
        self.resuming = resuming
        self.transferRisks = transferRisks
        self.onFindAlternatives = onFindAlternatives
        self.onEnd = onEnd
    }

    /// The risk of the transfer the rider is currently approaching or at, when it
    /// is tight or missed. nil otherwise (comfortable / not a transfer moment).
    private var activeTransferRisk: TransferRisk? {
        let idx = model.position.legIndex
        let atTransferMoment: Bool
        switch model.current {
        case .getOffNext(_, false, let to): atTransferMoment = (to != nil)
        case .transfer: atTransferMoment = true
        default: atTransferMoment = false
        }
        guard atTransferMoment, transferRisks.indices.contains(idx) else { return nil }
        let r = transferRisks[idx]
        return (r.status == "tight" || r.status == "missed") ? r : nil
    }

    private var tint: Color {
        guard let id = model.currentLineId else { return .accentColor }
        return SyrmosData.line(for: id)?.color ?? .accentColor
    }

    var body: some View {
        NavigationStack {
        // Two-pane on a regular-width display (iPad, iPhone Duo inner display),
        // foldables / Duo prompt 9.2: the current instruction + reachable actions
        // in the task pane, the journey's leg/stop timeline beside it. Compact keeps
        // the shipped single column. The native ArrangementView split takes over on
        // iOS 27.1; older systems use the HStack fallback in SyrmosArrangement.
        SyrmosArrangement(
            task: .go,
            primary: {
                SyrmosAxisReader { axis in
                    ScrollView {
                        VStack(spacing: 20) {
                            goInstruction
                            // Stacked (a tall window, a horizontal fold): the map keeps
                            // the upper region to itself and the timeline reads here,
                            // under the instruction, where the rider's hands are.
                            if axis == .vertical { timelineContent }
                            footnote
                        }
                        .padding()
                    }
                }
            },
            companion: { SyrmosAxisReader { axis in goCompanion(mapOnly: axis == .vertical) } },
            combined: {
                // Single column (phone, folded cover): instruction first, then the
                // route map on request (kept reachable, not permanently embedded),
                // then the timeline. The disclosure is remembered for the session.
                ScrollView {
                    VStack(spacing: 20) {
                        goInstruction
                        compactMapDisclosure
                        if showCompactMap {
                            goCompanion(mapOnly: true)
                                .frame(height: 260)
                        }
                        timelineContent
                        footnote
                    }
                    .padding()
                }
            }
        )
        .background(Color.syrmosBackground.ignoresSafeArea())
        .navigationTitle("GO")
        .navigationBarTitleDisplayMode(.inline)
        .animation(.easeInOut(duration: 0.2), value: model.position)
        .toolbar {
            if store != nil {
                ToolbarItem(placement: .confirmationAction) {
                    endButton
                }
            }
        }
        .onAppear {
            model.onGetOffAlert = { guidance in fireGetOff(guidance) }
            if let store {
                model.begin(store: store, resuming: resuming, language: language)
                // Phase N J08: surface the active journey as a Live Activity.
                GoJourneyActivityController.shared.start(
                    origin: originName, destination: destinationName,
                    stateLabel: goStateLabel, instruction: headline, context: glanceContext,
                    progress: model.progress, lineId: model.currentLineId, arrived: model.isArrived)
            }
            Task { await NotificationService.shared.requestAuthorization() }
        }
        .onReceive(location.$currentLocation) { loc in
            if let loc { model.applyLocation(lat: loc.coordinate.latitude, lon: loc.coordinate.longitude) }
        }
        .onChange(of: model.position) { _, _ in
            // Announce each new instruction so a VoiceOver rider hears "get off
            // next" hands-free, the iOS analog of the web panel's aria-live region.
            let text = [headline, detail].filter { !$0.isEmpty }.joined(separator: ". ")
            UIAccessibility.post(notification: .announcement, argument: text)
            // Phase N J08: push the new step to the Live Activity.
            if store != nil {
                GoJourneyActivityController.shared.update(
                    stateLabel: goStateLabel, instruction: headline, context: glanceContext,
                    progress: model.progress, lineId: model.currentLineId, arrived: model.isArrived)
            }
        }
        }
    }

    /// Current instruction + reachable actions (task pane / compact top).
    @ViewBuilder private var goInstruction: some View {
        header
        heroCard
        if let risk = activeTransferRisk { connectionRiskCard(risk) }
        legProgressBar
        controls
        if location.isDenied {
            locationDeniedNote
        } else if model.canGoLive {
            liveToggle
        }
    }

    /// Companion pane on a regular-width display (foldables / Duo prompt 9.2):
    /// the live route map above, the leg/stop timeline below, so the rider sees
    /// where they are on the map and what is coming up in one glance.
    @ViewBuilder private func goCompanion(mapOnly: Bool) -> some View {
        GeometryReader { geo in
            // Hinge-aware map padding (six-posture prompt, section 9, item 2): the
            // companion reads the regions the system reports for its own box
            // (empty on systems without the Duo API, or injected by a test) and
            // pads the map so the route and the current stop stay clear of an
            // occluding hinge or a camera cutout instead of resetting the camera.
            let geometry = reservedGeometryOverride ?? geo.syrmosReservedGeometry()
            // The map is a card inside the pane (Calm Signal: 16 pt gutters, large
            // radius), so the rect the padding rule sees is the card's rect.
            let gutter = SyrmosTokens.Space.lg
            // Side by side: the map takes 42 percent above the timeline. Stacked:
            // the map is the whole companion region (the timeline moved below).
            let mapHeight = mapOnly
                ? max(200, geo.size.height - SyrmosTokens.Space.md * 2)
                : max(200, geo.size.height * 0.42)
            let mapRect = CGRect(x: gutter, y: SyrmosTokens.Space.md,
                                 width: max(0, geo.size.width - gutter * 2), height: mapHeight)
            let mapInsets = SyrmosMapPadding.insets(mapRect: mapRect, geometry: geometry)
            VStack(spacing: 0) {
                GoRouteMapView(
                    route: routeCoords,
                    legRuns: legRuns,
                    current: currentCoord,
                    tint: UIColor(tint),
                    edgeInsets: mapInsets,
                    intent: cameraIntent,
                    command: cameraCommand,
                    commandTick: cameraTick,
                    onUserPan: { if cameraIntent != .manual { camera(.userPanned) } },
                    initialCamera: savedCamera,
                    onCameraChanged: { savedCamera = $0 }
                )
                .frame(height: mapHeight)
                .clipShape(RoundedRectangle(cornerRadius: SyrmosTokens.Radius.lg, style: .continuous))
                .overlay(alignment: .topTrailing) { mapCameraControls }
                .overlay(alignment: .topLeading) { positionSourcePill }
                .overlay(
                    RoundedRectangle(cornerRadius: SyrmosTokens.Radius.lg, style: .continuous)
                        .stroke(Color.syrmosSurfaceMuted, lineWidth: 1)
                )
                .padding(.horizontal, gutter)
                .padding(.top, SyrmosTokens.Space.md)
                .accessibilityLabel(t(
                    "Journey route map", "Χάρτης διαδρομής", "Harta e udhëtimit", "Mappa del percorso"))
                if !mapOnly { goTimeline }
            }
        }
    }

    /// Single column only: reveal or hide the route map card.
    private var compactMapDisclosure: some View {
        Button {
            withAnimation(.easeInOut(duration: 0.2)) { showCompactMap.toggle() }
        } label: {
            Label(showCompactMap
                    ? t("Hide route map", "Απόκρυψη χάρτη διαδρομής", "Fshih hartën e rrugës", "Nascondi la mappa del percorso")
                    : t("Show route map", "Εμφάνιση χάρτη διαδρομής", "Shfaq hartën e rrugës", "Mostra la mappa del percorso"),
                  systemImage: showCompactMap ? "map.fill" : "map")
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.bordered)
        .buttonBorderShape(.capsule)
        .tint(Color.syrmosPrimary)
        .accessibilityHint(t("The journey's route on a map.", "Η διαδρομή του ταξιδιού στον χάρτη.", "Rruga e udhëtimit në hartë.", "Il percorso del viaggio sulla mappa."))
    }

    /// Trust: what the dot on the map means. A stop the rider confirmed by
    /// stepping is not a GPS fix; only live guidance follows the real position.
    private var positionSourcePill: some View {
        let live = model.isLive
        let text = live
            ? t("Live position", "Ζωντανή θέση", "Pozicion i drejtpërdrejtë", "Posizione dal vivo")
            : t("Confirmed stop", "Επιβεβαιωμένη στάση", "Ndalesë e konfirmuar", "Fermata confermata")
        return Label(text, systemImage: live ? "location.fill" : "checkmark.circle.fill")
            .font(.caption.weight(.semibold))
            .lineLimit(1)
            .fixedSize(horizontal: true, vertical: false)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(Capsule().fill(.thinMaterial))
            .foregroundStyle(live ? Color.syrmosPrimary : Color.secondary)
            .padding(8)
            .accessibilityLabel(text)
    }

    /// Fit route always; Follow only while the camera is not following.
    private var mapCameraControls: some View {
        HStack(spacing: 8) {
            if cameraIntent != .follow {
                Button { camera(.followTapped) } label: {
                    Label(t("Follow", "Ακολούθησε", "Ndiq", "Segui"), systemImage: "location.fill")
                }
            }
            Button { camera(.fitTapped) } label: {
                Label(t("Fit route", "Όλη η διαδρομή", "Gjithë rruga", "Tutto il percorso"),
                      systemImage: "arrow.up.left.and.arrow.down.right")
            }
        }
        .font(.caption.weight(.semibold))
        .lineLimit(1)
        .buttonStyle(.bordered)
        .buttonBorderShape(.capsule)
        .controlSize(.small)
        .tint(Color.syrmosPrimary)
        .padding(8)
    }

    private func camera(_ event: GoCameraEvent) {
        let step = GoCamera.reduce(cameraIntent, event)
        cameraIntent = step.intent
        if step.action != .none {
            cameraCommand = step.action
            cameraTick += 1
        }
    }

    /// The journey's stops as map coordinates, in ride order, for the route line.
    private var routeCoords: [CLLocationCoordinate2D] {
        GoRouteProjection.routeCoordinates(journey: model.journey) {
            StationCoordinateLookup.shared.coordinate(for: $0)
        }
    }

    /// Each leg's coordinate run in its real line colour (the interchange reads
    /// as a colour change on the map, as it does on the timeline).
    private var legRuns: [(coordinates: [CLLocationCoordinate2D], color: UIColor)] {
        GoRouteProjection.legRuns(journey: model.journey) {
            StationCoordinateLookup.shared.coordinate(for: $0)
        }.map { run in
            (run.coordinates, UIColor(SyrmosData.line(for: run.lineId)?.color ?? Color.syrmosPrimary))
        }
    }

    /// The rider's current stop as a map coordinate, nil when it cannot be placed.
    private var currentCoord: CLLocationCoordinate2D? {
        GoRouteProjection.currentCoordinate(journey: model.journey, position: model.position) {
            StationCoordinateLookup.shared.coordinate(for: $0)
        }
    }

    /// Companion pane: the journey's legs and stops with the current position
    /// highlighted, shown beside the instruction on a regular-width display.
    @ViewBuilder private var goTimeline: some View {
        // Manual browsing stays stable: the list never snaps back by itself, but
        // while the current stop is out of view a Back to now action is offered
        // (shared GoTimelineFocus rule with Android).
        ScrollViewReader { proxy in
            ScrollView {
                timelineBody(trackCurrent: true)
                    .padding(SyrmosTokens.Space.lg)
            }
            .coordinateSpace(name: GoTimelineAnchor.space)
            .background(GeometryReader { geo in
                Color.clear
                    .onAppear { timelineViewportHeight = geo.size.height }
                    .onChange(of: geo.size.height) { _, h in timelineViewportHeight = h }
            })
            .onPreferenceChange(GoCurrentRowFrameKey.self) { currentRowFrame = $0 }
            // An advance is not browsing: when the position moves the timeline
            // follows the new current row; a manual scroll in between is never
            // snapped back (Back to now covers that).
            .onChange(of: model.position) { _, _ in
                withAnimation(.easeInOut(duration: 0.25)) {
                    proxy.scrollTo(GoTimelineAnchor.current, anchor: UnitPoint(x: 0.5, y: 0.33))
                }
            }
            .overlay(alignment: .bottom) {
                if let frame = currentRowFrame, timelineViewportHeight > 0,
                   !GoTimelineFocus.isVisible(rowTop: frame.minY, rowBottom: frame.maxY,
                                              viewportTop: 0, viewportBottom: timelineViewportHeight) {
                    Button {
                        withAnimation(.easeInOut(duration: 0.25)) {
                            proxy.scrollTo(GoTimelineAnchor.current, anchor: UnitPoint(x: 0.5, y: 0.33))
                        }
                    } label: {
                        Label(t("Back to now", "Πίσω στο τώρα", "Kthehu te tani", "Torna a ora"),
                              systemImage: "arrow.uturn.backward")
                            .lineLimit(1)
                    }
                    .buttonStyle(.borderedProminent)
                    .buttonBorderShape(.capsule)
                    .controlSize(.small)
                    .tint(Color.syrmosPrimary)
                    .padding(.bottom, SyrmosTokens.Space.lg)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
        }
    }

    /// The timeline's cards without their scroll view, so the compact single
    /// column can place them under the instruction and controls instead of
    /// leaving the lower half of the phone empty.
    @ViewBuilder private var timelineContent: some View { timelineBody(trackCurrent: false) }

    @ViewBuilder private func timelineBody(trackCurrent: Bool) -> some View {
        let rows = GoTimelineProjection.rows(journey: model.journey, position: model.position)
        VStack(alignment: .leading, spacing: SyrmosTokens.Space.md) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(t("Journey", "Διαδρομή", "Udhëtimi", "Viaggio"))
                        .font(.title3.weight(.semibold))
                    Text(journeySummary)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .padding(.bottom, SyrmosTokens.Space.xs)
                ForEach(Array(model.journey.legs.enumerated()), id: \.offset) { legIdx, leg in
                    let legColor = SyrmosData.line(for: leg.lineId)?.color ?? Color.syrmosPrimary
                    if legIdx > 0 {
                        transferConnector(to: leg, color: legColor)
                    }
                    legCard(leg: leg, legIdx: legIdx, color: legColor,
                            rows: rows.filter { $0.legIndex == legIdx }, trackCurrent: trackCurrent)
                }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// "Piraeus to Syntagma, 2 lines, 9 stops": the whole journey in one line.
    private var journeySummary: String {
        let lines = model.journey.legs.count
        let stops = GoTimelineProjection.stopCount(journey: model.journey)
        let linesText = lines == 1
            ? t("1 line", "1 γραμμή", "1 linjë", "1 linea")
            : t("\(lines) lines", "\(lines) γραμμές", "\(lines) linja", "\(lines) linee")
        let stopsText = t("\(stops) stops", "\(stops) στάσεις", "\(stops) ndalesa", "\(stops) fermate")
        return "\(originName) → \(destinationName) · \(linesText) · \(stopsText)"
    }

    /// One leg as a card: line pill, direction and stop count in the header, then
    /// the stops on a rail in the leg's colour.
    private func legCard(leg: GuidanceLeg, legIdx: Int, color: Color, rows: [GoTimelineRow], trackCurrent: Bool = false) -> some View {
        let count = max(0, leg.stops.count - 1)
        let countText = count == 1
            ? t("1 stop", "1 στάση", "1 ndalesë", "1 fermata")
            : t("\(count) stops", "\(count) στάσεις", "\(count) ndalesa", "\(count) fermate")
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: SyrmosTokens.Space.sm) {
                LinePill(lineId: leg.lineId, size: .large)
                Text(t("toward", "προς", "drejt", "verso") + " " + leg.towards)
                    .font(.subheadline.weight(.medium))
                    .lineLimit(1)
                Spacer(minLength: SyrmosTokens.Space.sm)
                Text(countText)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .padding(.horizontal, SyrmosTokens.Space.lg)
            .padding(.vertical, SyrmosTokens.Space.md)
            Divider().overlay(Color.syrmosSurfaceMuted)
            VStack(alignment: .leading, spacing: 0) {
                ForEach(rows, id: \.stopIndex) { row in
                    timelineRow(row, color: color)
                    .modifier(GoCurrentRowTracker(active: trackCurrent && row.state == .current))
                }
            }
            .padding(.horizontal, SyrmosTokens.Space.lg)
            .padding(.vertical, SyrmosTokens.Space.sm)
        }
        .background(
            RoundedRectangle(cornerRadius: SyrmosTokens.Radius.lg, style: .continuous)
                .fill(Color.syrmosSurface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: SyrmosTokens.Radius.lg, style: .continuous)
                .stroke(Color.syrmosSurfaceMuted, lineWidth: 1)
        )
    }

    /// The walk between two legs: a dotted connector and the change instruction.
    private func transferConnector(to leg: GuidanceLeg, color: Color) -> some View {
        HStack(spacing: SyrmosTokens.Space.md) {
            VStack(spacing: 3) {
                ForEach(0..<3, id: \.self) { _ in
                    Circle().fill(Color.secondary.opacity(0.35)).frame(width: 4, height: 4)
                }
            }
            .frame(width: 24)
            Image(systemName: "figure.walk")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            Text(t("Change to", "Αλλαγή σε", "Ndërro në", "Cambia in") + " " + SyrmosLineTokens.label(for: leg.lineId))
                .font(.caption.weight(.medium))
                .foregroundStyle(.secondary)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, SyrmosTokens.Space.lg)
        .accessibilityElement(children: .combine)
    }

    /// One stop on the rail: origin and alight stops are rings, intermediate stops
    /// small dots, the current stop a filled marker with a halo; the rail dims
    /// behind the rider. A caption names the moment: Now, Next, Change here,
    /// Destination.
    private func timelineRow(_ row: GoTimelineRow, color: Color) -> some View {
        let isPast = row.state == .past
        let isCurrent = row.state == .current
        let terminus = row.role != .intermediate
        let railWidth: CGFloat = 4
        let rowHeight: CGFloat = terminus || isCurrent ? 44 : 30
        let caption: String? = {
            if row.isDestination { return t("Destination", "Προορισμός", "Destinacioni", "Destinazione") }
            if row.role == .alight { return t("Change here", "Αλλαγή εδώ", "Ndërro këtu", "Cambia qui") }
            switch row.state {
            case .current: return t("Now", "Τώρα", "Tani", "Ora")
            case .next: return t("Next", "Επόμενη", "Tjetra", "Prossima")
            default: return nil
            }
        }()
        return HStack(alignment: .center, spacing: SyrmosTokens.Space.md) {
            ZStack {
                VStack(spacing: 0) {
                    Rectangle()
                        .fill(row.role == .origin ? Color.clear : color.opacity(isPast || isCurrent ? 0.3 : 1))
                        .frame(width: railWidth)
                    Rectangle()
                        .fill(row.role == .alight ? Color.clear : color.opacity(isPast ? 0.3 : 1))
                        .frame(width: railWidth)
                }
                if isCurrent {
                    Circle().fill(color.opacity(0.18)).frame(width: 28, height: 28)
                    Circle().fill(color).frame(width: 16, height: 16)
                    Circle().fill(Color.white).frame(width: 6, height: 6)
                } else if terminus {
                    Circle()
                        .fill(Color.syrmosSurface)
                        .overlay(Circle().stroke(color.opacity(isPast ? 0.4 : 1), lineWidth: 3))
                        .frame(width: 14, height: 14)
                } else {
                    Circle()
                        .fill(color.opacity(isPast ? 0.35 : 1))
                        .frame(width: 8, height: 8)
                }
            }
            .frame(width: 24)
            Text(row.name)
                .font(isCurrent ? .body.weight(.semibold) : terminus ? .subheadline.weight(.semibold) : .subheadline)
                .foregroundStyle(isPast ? .secondary : .primary)
                .lineLimit(1)
            Spacer(minLength: SyrmosTokens.Space.sm)
            if let caption {
                Text(caption)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(isPast && !row.isDestination ? Color.secondary : color)
                    .padding(.horizontal, SyrmosTokens.Space.sm)
                    .padding(.vertical, 3)
                    .background(
                        Capsule().fill((isPast && !row.isDestination ? Color.secondary : color).opacity(0.12))
                    )
            }
        }
        .frame(height: rowHeight)
        .accessibilityElement(children: .combine)
    }

    /// Phase R S10 permission-denied capability state: location off changes the
    /// capability (no auto-advance), not the availability of the journey. Manual
    /// stepping stays fully usable; offer Settings to re-enable.
    private var locationDeniedNote: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label {
                Text(t("Location is off", "Η τοποθεσία είναι ανενεργή", "Vendndodhja është joaktive", "La posizione è disattivata"))
                    .font(.subheadline.weight(.semibold))
            } icon: {
                Image(systemName: "location.slash")
            }
            .foregroundStyle(.secondary)
            Text(t("Keep stepping through your journey manually, or turn location on in Settings.",
                   "Συνέχισε τη διαδρομή χειροκίνητα ή ενεργοποίησε την τοποθεσία στις Ρυθμίσεις.",
                   "Vazhdo udhëtimin manualisht, ose aktivizo vendndodhjen te Cilësimet.",
                   "Continua il viaggio manualmente o attiva la posizione in Impostazioni."))
                .font(.caption).foregroundStyle(.secondary)
            Button(t("Open Settings", "Άνοιγμα Ρυθμίσεων", "Hap Cilësimet", "Apri Impostazioni")) {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
            .font(.subheadline).buttonStyle(.bordered)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color.gray.opacity(0.12)))
        .accessibilityElement(children: .combine)
    }

    private var liveToggle: some View {
        Button {
            if model.isLive {
                model.stopLive()
            } else {
                location.requestIfNeeded()
                model.startLive()
            }
        } label: {
            Label(
                model.isLive
                    ? t("Live guidance on", "Ζωντανή καθοδήγηση ενεργή", "Udhëzim i drejtpërdrejtë aktiv", "Guida dal vivo attiva")
                    : t("Start live guidance", "Έναρξη ζωντανής καθοδήγησης", "Nis udhëzimin e drejtpërdrejtë", "Avvia guida dal vivo"),
                systemImage: model.isLive ? "location.fill" : "location"
            )
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.bordered)
        .tint(model.isLive ? .green : .accentColor)
    }

    /// One end path: model.end() clears the active-journey store, which ends the
    /// Live Activity (see GoActiveJourneyStore.clear), so nothing else is needed.
    private func endJourney() {
        model.end()
        if let onEnd { onEnd() } else { dismiss() }
    }

    private func fireGetOff(_ guidance: JourneyGuidance) {
        #if canImport(UIKit)
        UINotificationFeedbackGenerator().notificationOccurred(.warning)
        #endif
        switch guidance {
        case let .getOffNext(nextStation, isDestination, transferTo):
            NotificationService.shared.fireGetOffAlert(
                station: nextStation, isDestination: isDestination, transferTo: transferTo, language: language)
        case let .board(_, _, _, nextStation):
            // 2-stop leg: the get-off cue coincides with boarding.
            NotificationService.shared.fireGetOffAlert(
                station: nextStation, isDestination: false, transferTo: nil, language: language)
        default:
            break
        }
    }

    private var header: some View {
        HStack(spacing: 8) {
            Text(originName).fontWeight(.semibold)
            Image(systemName: "arrow.right").font(.caption).foregroundStyle(.secondary)
            Text(destinationName).fontWeight(.semibold)
        }
        .font(.subheadline)
        .foregroundStyle(.secondary)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var heroCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(stateLabel.uppercasedForDisplay(language))
                .font(.caption.weight(.semibold))
                .tracking(0.6)
                .foregroundStyle(model.shouldAlert ? Color.white.opacity(0.85) : tint)
            Label {
                Text(headline).font(.title2.bold())
            } icon: {
                Image(systemName: icon).font(.title2)
            }
            .foregroundStyle(model.shouldAlert ? Color.white : tint)

            if !detail.isEmpty {
                Text(detail)
                    .font(.headline)
                    .foregroundStyle(model.shouldAlert ? Color.white.opacity(0.9) : .primary)
            }
            if !subdetail.isEmpty {
                Text(subdetail)
                    .font(.subheadline)
                    .foregroundStyle(model.shouldAlert ? Color.white.opacity(0.8) : .secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(model.shouldAlert ? tint : tint.opacity(0.12))
        )
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(headline). \(detail). \(subdetail)")
    }

    /// Phase R S07: inline connection-risk warning directly below the instruction.
    /// Values are real (from the route model); never switches trains automatically.
    @ViewBuilder
    private func connectionRiskCard(_ risk: TransferRisk) -> some View {
        let missed = risk.status == "missed"
        let title = missed
            ? t("This connection may be missed", "Αυτή η ανταπόκριση μπορεί να χαθεί", "Kjo lidhje mund të humbasë", "Questa coincidenza potrebbe saltare")
            : t("This connection is tight", "Αυτή η ανταπόκριση είναι στενή", "Kjo lidhje është e ngushtë", "Questa coincidenza è stretta")
        let mins = { (s: Int?) -> Int? in s.map { max(0, Int(($0 + 30) / 60)) } }
        let explanation: String? = {
            guard let avail = mins(risk.availableSeconds), let allow = mins(risk.minimumSeconds) else { return nil }
            return t(
                "\(avail) min available; allow \(allow) min to change.",
                "\(avail) λεπ διαθέσιμα, χρειάζονται \(allow) λεπ για αλλαγή.",
                "\(avail) min në dispozicion, duhen \(allow) min për ndërrim.",
                "\(avail) min disponibili, servono \(allow) min per cambiare.")
        }()
        VStack(alignment: .leading, spacing: 8) {
            Label {
                Text(title).font(.headline)
            } icon: {
                Image(systemName: "exclamationmark.triangle.fill")
            }
            .foregroundStyle(.orange)
            if let explanation {
                Text(explanation).font(.subheadline).foregroundStyle(.secondary)
            }
            if let onFindAlternatives, let from = currentStationId, let to = destinationId {
                Button {
                    onFindAlternatives(from, to)
                } label: {
                    Text(t("Find alternatives", "Βρες εναλλακτικές", "Gjej alternativa", "Trova alternative"))
                }
                .buttonStyle(.bordered)
                .tint(.orange)
            }
        }
        .frame(minHeight: 88, alignment: .leading)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Color.orange.opacity(0.12)))
        .accessibilityElement(children: .combine)
    }

    /// Current confirmed station id (for a re-plan origin).
    private var currentStationId: String? {
        let p = model.position
        guard model.journey.legs.indices.contains(p.legIndex),
              model.journey.legs[p.legIndex].stops.indices.contains(p.stopIndex) else { return nil }
        return model.journey.legs[p.legIndex].stops[p.stopIndex].id
    }
    private var destinationId: String? { model.journey.legs.last?.stops.last?.id }

    private var controls: some View {
        HStack(spacing: 12) {
            Button {
                model.back()
            } label: {
                Label(t("Back", "Πίσω", "Prapa", "Indietro"), systemImage: "chevron.left")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .disabled(!model.canGoBack)

            if model.isArrived {
                Button {
                    model.reset()
                } label: {
                    Label(t("Restart", "Επανεκκίνηση", "Rifillo", "Ricomincia"), systemImage: "arrow.counterclockwise")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(tint)
            } else {
                Button {
                    model.advance()
                } label: {
                    Label(t("Next stop", "Επόμενη στάση", "Ndalesa tjetër", "Fermata succ."), systemImage: "chevron.right")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(tint)
            }
        }
    }

    /// One segment per leg in the leg's line colour, filled to the rider's
    /// position, with a "stop X of Y" caption: the journey's shape at a glance,
    /// instead of an anonymous grey bar.
    private var legProgressBar: some View {
        let segments = GoLegProgress.segments(journey: model.journey, position: model.position)
        let total = GoTimelineProjection.stopCount(journey: model.journey)
        let done = GoLegProgress.stopsRidden(journey: model.journey, position: model.position)
        return VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 4) {
                ForEach(Array(segments.enumerated()), id: \.offset) { _, seg in
                    let color = SyrmosData.line(for: seg.lineId)?.color ?? Color.syrmosPrimary
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule().fill(color.opacity(0.18))
                            Capsule().fill(color).frame(width: max(seg.fraction > 0 ? 6 : 0, geo.size.width * seg.fraction))
                        }
                    }
                    .frame(height: 6)
                }
            }
            HStack {
                Text(model.isArrived
                    ? t("Journey complete", "Το ταξίδι ολοκληρώθηκε", "Udhëtimi përfundoi", "Viaggio completato")
                    : t("Stop \(done) of \(total)", "Στάση \(done) από \(total)", "Ndalesa \(done) nga \(total)", "Fermata \(done) di \(total)"))
                    .font(.caption.weight(.medium))
                    .foregroundStyle(.secondary)
                    .monospacedDigit()
                Spacer()
                Text("\(Int((model.progress * 100).rounded()))%")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .monospacedDigit()
            }
        }
        .accessibilityElement(children: .combine)
    }

    /// The moment the hero describes, as a short label above the headline
    /// (matches the Android GO hero's state label).
    /// The End / Finish toolbar button with its confirmation attached to the
    /// button itself, so on an iPad the popover points at the button instead of
    /// hovering over the content (as it did when the dialog sat on the view).
    private var endButton: some View {
                Button(model.isArrived
                    ? t("Finish", "Τέλος", "Përfundo", "Concludi")
                    : t("End", "Τέλος", "Përfundo", "Termina")) {
                    // Arrived: finishing is final and safe. Mid-journey: confirm,
                    // so a stray tap on a moving train does not drop the guidance.
                    if model.isArrived { endJourney() } else { confirmEnd = true }
                }
            .confirmationDialog(
                t("End this journey?", "Τέλος διαδρομής;", "Të përfundojë udhëtimi?", "Terminare il viaggio?"),
                isPresented: $confirmEnd,
                titleVisibility: .visible
            ) {
                Button(t("End journey", "Τέλος διαδρομής", "Përfundo udhëtimin", "Termina il viaggio"), role: .destructive) {
                    endJourney()
                }
                Button(t("Keep going", "Συνέχισε", "Vazhdo", "Continua"), role: .cancel) {}
            } message: {
                Text(t(
                    "Guidance and the get-off alert stop. Your route stays in Plan.",
                    "Η καθοδήγηση και η ειδοποίηση αποβίβασης σταματούν. Η διαδρομή σου μένει στο Σχεδίασε.",
                    "Udhëzimi dhe njoftimi i zbritjes ndalojnë. Rruga jote mbetet te Planifiko.",
                    "La guida e l'avviso di discesa si fermano. Il percorso resta in Pianifica."))
            }
    }

    private var stateLabel: String {
        switch model.current {
        case .board: return t("Ready to board", "Έτοιμος για επιβίβαση", "Gati për të hipur", "Pronto a salire")
        case .ride: return t("Riding", "Σε κίνηση", "Duke udhëtuar", "In viaggio")
        case .getOffNext: return t("Alight soon", "Αποβίβαση σύντομα", "Zbrit së shpejti", "Scendi a breve")
        case .transfer: return t("Transfer", "Μετεπιβίβαση", "Ndërrim", "Cambio")
        case .arrived: return t("Arrived", "Έφτασες", "Mbërritët", "Arrivato")
        }
    }

    private var footnote: some View {
        Text(t(
            "Step through your journey, or turn on live guidance to follow your position and get the get-off alert.",
            "Προχώρα βήμα-βήμα ή ενεργοποίησε τη ζωντανή καθοδήγηση για να ακολουθεί τη θέση σου και να σε ειδοποιεί για αποβίβαση.",
            "Ec hap pas hapi, ose aktivizo udhëzimin e drejtpërdrejtë që të ndjekë pozicionin tënd dhe të njoftojë për zbritjen.",
            "Procedi passo passo, oppure attiva la guida dal vivo per seguire la tua posizione e ricevere l'avviso di discesa."
        ))
        .font(.footnote)
        .foregroundStyle(.secondary)
        .multilineTextAlignment(.center)
    }

    // MARK: Instruction rendering

    private var icon: String {
        switch model.current {
        case .board: return "figure.walk"
        case .ride: return "tram.fill"
        case .getOffNext: return "figure.walk.departure"
        case .transfer: return "arrow.triangle.swap"
        case .arrived: return "checkmark.circle.fill"
        }
    }

    /// Phase N J08: short state word for the Live Activity glance.
    private var goStateLabel: String {
        switch model.current {
        case .board: return t("Board", "Επιβίβαση", "Hip", "Sali")
        case .ride: return t("Riding", "Σε κίνηση", "Në lëvizje", "In viaggio")
        case .getOffNext: return t("Get off next", "Κατέβα στην επόμενη", "Zbrit në tjetrën", "Scendi alla prossima")
        case .transfer: return t("Transfer", "Μετεπιβίβαση", "Ndërrim", "Cambio")
        case .arrived: return t("Arrived", "Άφιξη", "Mbërritur", "Arrivato")
        }
    }

    /// Secondary context for the glance (prefer the subdetail, else the detail).
    private var glanceContext: String? {
        let c = subdetail.isEmpty ? detail : subdetail
        return c.isEmpty ? nil : c
    }

    private var headline: String {
        switch model.current {
        case .board(let line, _, _, _):
            return t("Board \(line)", "Επιβίβαση \(line)", "Hip në \(line)", "Sali su \(line)")
        case .ride(let line, _, _, _):
            return t("Stay on \(line)", "Μείνε στη \(line)", "Qëndro në \(line)", "Resta su \(line)")
        case .getOffNext:
            return t("Get off next", "Αποβίβαση στην επόμενη", "Zbrit në tjetrën", "Scendi alla prossima")
        case .transfer:
            return t("Change here", "Αλλαγή εδώ", "Ndërro këtu", "Cambia qui")
        case .arrived:
            return t("Arrived", "Έφτασες", "Mbërritët", "Arrivato")
        }
    }

    private var detail: String {
        switch model.current {
        case .board(_, let towards, _, _), .ride(_, let towards, _, _):
            return t("toward \(towards)", "προς \(towards)", "drejt \(towards)", "verso \(towards)")
        case .getOffNext(let next, let isDestination, let transferTo):
            if isDestination { return next }
            if let x = transferTo { return t("\(next) → change to \(x)", "\(next) → αλλαγή σε \(x)", "\(next) → ndërro në \(x)", "\(next) → cambia in \(x)") }
            return next
        case .transfer(let at, let to, let towards):
            return t("\(at) → \(to) toward \(towards)", "\(at) → \(to) προς \(towards)", "\(at) → \(to) drejt \(towards)", "\(at) → \(to) verso \(towards)")
        case .arrived(let station):
            return station
        }
    }

    private var subdetail: String {
        switch model.current {
        case .board(_, _, let remaining, let next), .ride(_, _, let remaining, let next):
            // "N stops to <alight>": count the stops remaining to THIS leg's alight
            // point and name it, so the GO number can never be read as the S05
            // "intermediate stops" count that measures a different thing (finding 3).
            // The alight is the current leg's last stop (an interchange mid-journey,
            // the destination only on the final leg); fall back to the next station.
            let leg = model.journey.legs.indices.contains(model.position.legIndex)
                ? model.journey.legs[model.position.legIndex] : nil
            let alight = leg?.stops.last?.name ?? next
            if remaining == 1 {
                return t("1 stop to \(alight)", "1 στάση μέχρι \(alight)", "1 ndalesë deri te \(alight)", "1 fermata fino a \(alight)")
            }
            return t("\(remaining) stops to \(alight)", "\(remaining) στάσεις μέχρι \(alight)", "\(remaining) ndalesa deri te \(alight)", "\(remaining) fermate fino a \(alight)")
        case .getOffNext(let next, let isDestination, _):
            return isDestination
                ? t("Your destination is next", "Ο προορισμός σου είναι η επόμενη", "Destinacioni yt është tjetra", "La tua destinazione è la prossima")
                : t("Next stop: \(next)", "Επόμενη στάση: \(next)", "Ndalesa tjetër: \(next)", "Prossima fermata: \(next)")
        default:
            return ""
        }
    }

    private func t(_ en: String, _ el: String, _ sq: String, _ it: String) -> String {
        switch language {
        case .greek: return el
        case .albanian: return sq
        case .italian: return it
        default: return en
        }
    }
}

/// The role a stop plays on its leg and the rider's relation to it.
enum GoTimelineRole: Equatable { case origin, intermediate, alight }
enum GoTimelineState: Equatable { case past, current, next, future }

/// One timeline row, pure data so the GO companion and its tests share the rule.
struct GoTimelineRow: Equatable {
    let legIndex: Int
    let stopIndex: Int
    let name: String
    let role: GoTimelineRole
    let state: GoTimelineState
    /// The journey's final stop (the alight of the last leg).
    let isDestination: Bool
}

/// Pure projection of a guidance journey and position into timeline rows: which
/// stop is the origin or alight of its leg, which is behind the rider, which is
/// current, which is next. Mirrors the Kotlin `GoTimeline` in core/domain.
enum GoTimelineProjection {
    static func rows(journey: GuidanceJourney, position: GuidancePosition) -> [GoTimelineRow] {
        var out: [GoTimelineRow] = []
        for (legIdx, leg) in journey.legs.enumerated() {
            for (stopIdx, stop) in leg.stops.enumerated() {
                let role: GoTimelineRole = stopIdx == 0 ? .origin
                    : stopIdx == leg.stops.count - 1 ? .alight : .intermediate
                let state: GoTimelineState
                if legIdx < position.legIndex || (legIdx == position.legIndex && stopIdx < position.stopIndex) {
                    state = .past
                } else if legIdx == position.legIndex && stopIdx == position.stopIndex {
                    state = .current
                } else if legIdx == position.legIndex && stopIdx == position.stopIndex + 1 {
                    state = .next
                } else {
                    state = .future
                }
                out.append(GoTimelineRow(
                    legIndex: legIdx, stopIndex: stopIdx, name: stop.name, role: role, state: state,
                    isDestination: role == .alight && legIdx == journey.legs.count - 1))
            }
        }
        return out
    }

    /// Stops ridden across the journey: each leg's stops minus its boarding stop,
    /// so an interchange counted at the end of one leg is not counted again at
    /// the start of the next.
    static func stopCount(journey: GuidanceJourney) -> Int {
        journey.legs.reduce(0) { $0 + max(0, $1.stops.count - 1) }
    }
}

/// One leg of the segmented progress bar: the line and how much of the leg the
/// rider has covered (0 before it, 1 after it, hops ridden over hops in the leg
/// while on it).
struct GoLegSegment: Equatable {
    let lineId: String
    let fraction: Double
}

enum GoLegProgress {
    static func segments(journey: GuidanceJourney, position: GuidancePosition) -> [GoLegSegment] {
        journey.legs.enumerated().map { idx, leg in
            let hops = max(1, leg.stops.count - 1)
            let fraction: Double
            if idx < position.legIndex {
                fraction = 1
            } else if idx > position.legIndex {
                fraction = 0
            } else {
                fraction = min(1, max(0, Double(position.stopIndex) / Double(hops)))
            }
            return GoLegSegment(lineId: leg.lineId, fraction: fraction)
        }
    }

    /// Hops ridden so far across the journey (the "X" in "stop X of Y").
    static func stopsRidden(journey: GuidanceJourney, position: GuidancePosition) -> Int {
        var done = 0
        for (idx, leg) in journey.legs.enumerated() {
            let hops = max(0, leg.stops.count - 1)
            if idx < position.legIndex { done += hops } else if idx == position.legIndex { done += min(hops, position.stopIndex) }
        }
        return done
    }
}

/// Pure projection of a guidance journey into map geometry, so the GO companion
/// map and its tests share one placement rule. `resolve` turns a stop id into a
/// coordinate (the app passes StationCoordinateLookup; tests pass a fixture).
/// Stops with no known coordinate are skipped, so the route line spans only the
/// stops we can place; consecutive duplicates at an interchange are harmless.
enum GoRouteProjection {
    static func routeCoordinates(
        journey: GuidanceJourney,
        resolve: (String) -> (lat: Double, lon: Double)?
    ) -> [CLLocationCoordinate2D] {
        var out: [CLLocationCoordinate2D] = []
        for leg in journey.legs {
            for stop in leg.stops {
                if let c = resolve(stop.id) {
                    out.append(CLLocationCoordinate2D(latitude: c.lat, longitude: c.lon))
                }
            }
        }
        return out
    }

    /// One coordinate run per leg with its line id, so the map draws each leg in
    /// its own line colour and the interchange reads as a colour change. Legs
    /// with fewer than two placeable stops draw nothing.
    struct LegRun: Equatable {
        let lineId: String
        let coordinates: [CLLocationCoordinate2D]
        static func == (a: LegRun, b: LegRun) -> Bool {
            a.lineId == b.lineId && a.coordinates.count == b.coordinates.count
                && zip(a.coordinates, b.coordinates).allSatisfy { $0.latitude == $1.latitude && $0.longitude == $1.longitude }
        }
    }

    static func legRuns(
        journey: GuidanceJourney,
        resolve: (String) -> (lat: Double, lon: Double)?
    ) -> [LegRun] {
        journey.legs.compactMap { leg in
            let coords = leg.stops.compactMap { stop -> CLLocationCoordinate2D? in
                guard let c = resolve(stop.id) else { return nil }
                return CLLocationCoordinate2D(latitude: c.lat, longitude: c.lon)
            }
            return coords.count >= 2 ? LegRun(lineId: leg.lineId, coordinates: coords) : nil
        }
    }

    static func currentCoordinate(
        journey: GuidanceJourney,
        position: GuidancePosition,
        resolve: (String) -> (lat: Double, lon: Double)?
    ) -> CLLocationCoordinate2D? {
        guard journey.legs.indices.contains(position.legIndex),
              journey.legs[position.legIndex].stops.indices.contains(position.stopIndex),
              let c = resolve(journey.legs[position.legIndex].stops[position.stopIndex].id)
        else { return nil }
        return CLLocationCoordinate2D(latitude: c.lat, longitude: c.lon)
    }
}

/// The GO companion map: the journey's route drawn as one line over the app's
/// Esri gray base, with an emphasised dot at the rider's current stop. Recenters
/// on the current stop as the rider advances. Wraps MKMapView directly because
/// the app avoids SwiftUI `Map` (CAMetalLayer lifecycle bug), matching the main
/// map screen (see SyrmosMKMapView).
/// A leg's polyline carrying its line colour for the renderer.
private final class GoLegPolyline: MKPolyline {
    var color: UIColor = .systemBlue
}

/// Keeps manual browsing of the timeline stable (twin of Kotlin
/// `GoTimelineFocus`): never snap back on its own, offer Back to now while the
/// current stop is out of view. One coordinate space and unit throughout.
enum GoTimelineFocus {
    static func isVisible(rowTop: CGFloat, rowBottom: CGFloat, viewportTop: CGFloat, viewportBottom: CGFloat) -> Bool {
        rowTop >= viewportTop && rowBottom <= viewportBottom
    }

    /// The offset that places the row a third of the way down the viewport,
    /// clamped to the scrollable range.
    static func targetOffset(rowTopInContent: CGFloat, viewportHeight: CGFloat, maxOffset: CGFloat) -> CGFloat {
        min(max(rowTopInContent - viewportHeight / 3, 0), max(maxOffset, 0))
    }
}

private enum GoTimelineAnchor: Hashable {
    case current
    static let space = "goTimelineScroll"
}

private struct GoCurrentRowFrameKey: PreferenceKey {
    static let defaultValue: CGRect? = nil
    static func reduce(value: inout CGRect?, nextValue: () -> CGRect?) {
        if let next = nextValue() { value = next }
    }
}

/// Marks the current row: the scroll anchor plus its frame in the timeline's
/// scroll space, reported through a preference. Inactive rows are untouched.
private struct GoCurrentRowTracker: ViewModifier {
    let active: Bool
    func body(content: Content) -> some View {
        if active {
            content
                .id(GoTimelineAnchor.current)
                .background(GeometryReader { geo in
                    Color.clear.preference(key: GoCurrentRowFrameKey.self,
                                           value: geo.frame(in: .named(GoTimelineAnchor.space)))
                })
        } else {
            content
        }
    }
}

/// The last settled map region, kept by the owning view across a reflow
/// (Android keeps the same in rememberSaveable).
struct GoSavedCamera: Equatable {
    var latitude: Double
    var longitude: Double
    var latitudeDelta: Double
    var longitudeDelta: Double

    init(region: MKCoordinateRegion) {
        latitude = region.center.latitude
        longitude = region.center.longitude
        latitudeDelta = region.span.latitudeDelta
        longitudeDelta = region.span.longitudeDelta
    }

    var region: MKCoordinateRegion {
        MKCoordinateRegion(center: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
                           span: MKCoordinateSpan(latitudeDelta: latitudeDelta, longitudeDelta: longitudeDelta))
    }
}

/// What the GO route map's camera is doing for the rider (twin of Kotlin
/// `GoCameraIntent`): follow the current stop, keep the whole route fitted, or
/// leave the rider's manual view alone.
enum GoCameraIntent { case follow, fit, manual }
enum GoCameraEvent { case userPanned, fitTapped, followTapped, currentStopChanged, geometryChanged }
enum GoCameraAction { case none, fitRoute, centerCurrent }

/// The camera reducer shared with Android (`GoCamera` in core/domain/go): only
/// a real event moves the camera, and only when the intent calls for it. An
/// identical SwiftUI update is not an event, so a manual pan survives ticks,
/// folds and re-renders.
enum GoCamera {
    static func reduce(_ intent: GoCameraIntent, _ event: GoCameraEvent) -> (intent: GoCameraIntent, action: GoCameraAction) {
        switch event {
        case .userPanned: return (.manual, .none)
        case .fitTapped: return (.fit, .fitRoute)
        case .followTapped: return (.follow, .centerCurrent)
        case .currentStopChanged: return intent == .follow ? (.follow, .centerCurrent) : (intent, .none)
        case .geometryChanged:
            switch intent {
            case .follow: return (.follow, .centerCurrent)
            case .fit: return (.fit, .fitRoute)
            case .manual: return (.manual, .none)
            }
        }
    }

    static func same(_ a: CLLocationCoordinate2D?, _ b: CLLocationCoordinate2D?) -> Bool {
        switch (a, b) {
        case (nil, nil): return true
        case let (x?, y?): return x.latitude == y.latitude && x.longitude == y.longitude
        default: return false
        }
    }
}

private struct GoRouteMapView: UIViewRepresentable {
    let route: [CLLocationCoordinate2D]
    /// Per-leg runs with their line colours; drawn one polyline per leg.
    var legRuns: [(coordinates: [CLLocationCoordinate2D], color: UIColor)] = []
    let current: CLLocationCoordinate2D?
    let tint: UIColor
    /// Padding that keeps the route fit and the current stop inside the map's
    /// visible area (base breathing room plus any hinge or cutout the companion
    /// reported). See `SyrmosMapPadding`.
    var edgeInsets: SyrmosEdgeInsets = .all(SyrmosMapPadding.base)
    /// Camera intent, the one-shot command (Fit route / Follow) with its tick,
    /// and the rider's manual pan reported back to the owner.
    var intent: GoCameraIntent = .follow
    var command: GoCameraAction = .none
    var commandTick: Int = 0
    var onUserPan: () -> Void = {}
    /// A camera to restore on creation (skips the first fit) and where to report
    /// every settled region so the owner can restore it after a reflow.
    var initialCamera: GoSavedCamera? = nil
    var onCameraChanged: (GoSavedCamera) -> Void = { _ in }

    private var uiEdgeInsets: UIEdgeInsets {
        UIEdgeInsets(top: edgeInsets.top, left: edgeInsets.left,
                     bottom: edgeInsets.bottom, right: edgeInsets.right)
    }

    func makeCoordinator() -> Coordinator { Coordinator(tint: tint, onUserPan: onUserPan) }

    func makeUIView(context: Context) -> MKMapView {
        let map = MKMapView()
        map.delegate = context.coordinator
        map.pointOfInterestFilter = .excludingAll
        map.showsCompass = false
        map.isPitchEnabled = false
        map.isRotateEnabled = false
        map.showsUserLocation = false
        let dark = map.traitCollection.userInterfaceStyle == .dark
        map.addOverlay(SyrmosMKMapView.makeEsriGrayOverlay(dark: dark), level: .aboveRoads)
        if !legRuns.isEmpty {
            for run in legRuns where run.coordinates.count >= 2 {
                let line = GoLegPolyline(coordinates: run.coordinates, count: run.coordinates.count)
                line.color = run.color
                map.addOverlay(line, level: .aboveLabels)
            }
        } else if route.count >= 2 {
            map.addOverlay(MKPolyline(coordinates: route, count: route.count), level: .aboveLabels)
        }
        if let initialCamera {
            map.setRegion(initialCamera.region, animated: false)
        } else if let rect = boundingRect() {
            map.setVisibleMapRect(rect, edgePadding: uiEdgeInsets, animated: false)
        }
        context.coordinator.onCameraChanged = onCameraChanged
        context.coordinator.lastInsets = edgeInsets
        context.coordinator.lastCurrent = current
        context.coordinator.lastCommandTick = commandTick
        context.coordinator.syncCurrent(on: map, to: current)
        return map
    }

    func updateUIView(_ map: MKMapView, context: Context) {
        let coordinator = context.coordinator
        coordinator.onUserPan = onUserPan
        coordinator.onCameraChanged = onCameraChanged
        coordinator.syncCurrent(on: map, to: current)
        let insetsChanged = coordinator.lastInsets != edgeInsets
        coordinator.lastInsets = edgeInsets
        let currentChanged = !GoCamera.same(coordinator.lastCurrent, current)
        coordinator.lastCurrent = current

        // Camera with explicit intent: a one-shot command first; otherwise only a
        // real change (the current stop moved, the fold geometry changed) may move
        // the camera, and only when the intent says so. An identical SwiftUI
        // update never recenters, so a manual pan holds.
        var action: GoCameraAction = .none
        if coordinator.lastCommandTick != commandTick {
            coordinator.lastCommandTick = commandTick
            action = command
        } else if currentChanged {
            action = GoCamera.reduce(intent, .currentStopChanged).action
        } else if insetsChanged {
            action = GoCamera.reduce(intent, .geometryChanged).action
        }
        switch action {
        case .none:
            break
        case .fitRoute:
            if let rect = boundingRect() {
                map.setVisibleMapRect(rect, edgePadding: uiEdgeInsets, animated: true)
            }
        case .centerCurrent:
            if let current {
                recenter(map, on: current, animated: true)
            } else if let rect = boundingRect() {
                map.setVisibleMapRect(rect, edgePadding: uiEdgeInsets, animated: true)
            }
        }
    }

    /// Centre `coord` in the PADDED visible area, not the map's geometric centre,
    /// so a hinge or a cutout never sits on the current stop. The offset between
    /// the two centres is measured in map points at the current zoom, which a
    /// pan does not change, so the camera keeps its zoom and bearing.
    private func recenter(_ map: MKMapView, on coord: CLLocationCoordinate2D, animated: Bool) {
        let size = map.bounds.size
        guard size.width > 0, size.height > 0 else {
            map.setCenter(coord, animated: animated)
            return
        }
        let geometric = CGPoint(x: size.width / 2, y: size.height / 2)
        let compensating = SyrmosMapPadding.compensatingPoint(size: size, insets: edgeInsets)
        guard compensating != geometric else {
            map.setCenter(coord, animated: animated)
            return
        }
        let a = MKMapPoint(map.convert(geometric, toCoordinateFrom: map))
        let b = MKMapPoint(map.convert(compensating, toCoordinateFrom: map))
        let target = MKMapPoint(x: MKMapPoint(coord).x + (b.x - a.x), y: MKMapPoint(coord).y + (b.y - a.y))
        map.setCenter(target.coordinate, animated: animated)
    }

    /// The map rect that encloses every placed stop, nil when there are none.
    private func boundingRect() -> MKMapRect? {
        guard !route.isEmpty else { return nil }
        var rect = MKMapRect.null
        for coord in route {
            let point = MKMapPoint(coord)
            rect = rect.union(MKMapRect(x: point.x, y: point.y, width: 0, height: 0))
        }
        return rect.isNull ? nil : rect
    }

    final class Coordinator: NSObject, MKMapViewDelegate {
        private let tint: UIColor
        private var currentAnnotation: MKPointAnnotation?
        /// The insets the map was last laid out with, to notice a fold change.
        var lastInsets: SyrmosEdgeInsets?
        /// The current stop the camera last acted on, to notice a real advance.
        var lastCurrent: CLLocationCoordinate2D?
        /// The last one-shot command tick that ran.
        var lastCommandTick = 0
        var onUserPan: () -> Void
        var onCameraChanged: (GoSavedCamera) -> Void = { _ in }

        init(tint: UIColor, onUserPan: @escaping () -> Void) {
            self.tint = tint
            self.onUserPan = onUserPan
        }

        /// A pan or pinch in progress when the region starts changing is the
        /// rider exploring; programmatic moves carry no active gesture.
        func mapView(_ mapView: MKMapView, regionDidChangeAnimated animated: Bool) {
            onCameraChanged(GoSavedCamera(region: mapView.region))
        }

        func mapView(_ mapView: MKMapView, regionWillChangeAnimated animated: Bool) {
            let gestures = mapView.subviews.first?.gestureRecognizers ?? []
            if gestures.contains(where: { $0.state == .began || $0.state == .changed }) {
                onUserPan()
            }
        }

        /// Keep exactly one "you are here" annotation at the current stop.
        func syncCurrent(on map: MKMapView, to coord: CLLocationCoordinate2D?) {
            if let existing = currentAnnotation {
                map.removeAnnotation(existing)
                currentAnnotation = nil
            }
            guard let coord else { return }
            let annotation = MKPointAnnotation()
            annotation.coordinate = coord
            map.addAnnotation(annotation)
            currentAnnotation = annotation
        }

        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let tile = overlay as? MKTileOverlay {
                return MKTileOverlayRenderer(tileOverlay: tile)
            }
            if let polyline = overlay as? MKPolyline {
                let renderer = MKPolylineRenderer(polyline: polyline)
                renderer.strokeColor = (polyline as? GoLegPolyline)?.color ?? tint
                renderer.lineWidth = 4
                renderer.lineCap = .round
                renderer.lineJoin = .round
                return renderer
            }
            return MKOverlayRenderer(overlay: overlay)
        }

        func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
            let id = "go-current"
            let view = mapView.dequeueReusableAnnotationView(withIdentifier: id)
                ?? MKAnnotationView(annotation: annotation, reuseIdentifier: id)
            view.annotation = annotation
            let size: CGFloat = 20
            view.image = UIGraphicsImageRenderer(size: CGSize(width: size, height: size)).image { ctx in
                let cg = ctx.cgContext
                cg.setFillColor(UIColor.systemBackground.cgColor)
                cg.fillEllipse(in: CGRect(x: 0, y: 0, width: size, height: size))
                cg.setFillColor(tint.cgColor)
                cg.fillEllipse(in: CGRect(x: 3, y: 3, width: size - 6, height: size - 6))
            }
            view.centerOffset = .zero
            return view
        }
    }
}
