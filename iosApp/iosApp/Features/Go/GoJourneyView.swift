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
            primary: {
                ScrollView {
                    VStack(spacing: 20) {
                        goInstruction
                        footnote
                    }
                    .padding()
                }
            },
            companion: { goCompanion },
            combined: {
                VStack(spacing: 20) {
                    goInstruction
                    Spacer()
                    footnote
                }
                .padding()
            }
        )
        .navigationTitle("GO")
        .navigationBarTitleDisplayMode(.inline)
        .animation(.easeInOut(duration: 0.2), value: model.position)
        .toolbar {
            if store != nil {
                ToolbarItem(placement: .confirmationAction) {
                    Button(model.isArrived
                        ? t("Finish", "Τέλος", "Përfundo", "Concludi")
                        : t("End", "Τέλος", "Përfundo", "Termina")) {
                        // model.end() clears the active-journey store, which ends the
                        // Live Activity (see GoActiveJourneyStore.clear) on every
                        // end path, so no separate controller.end() is needed here.
                        model.end()
                        if let onEnd { onEnd() } else { dismiss() }
                    }
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
        ProgressView(value: model.progress)
            .tint(tint)
            .padding(.horizontal)
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
    @ViewBuilder private var goCompanion: some View {
        GeometryReader { geo in
            // Hinge-aware map padding (six-posture prompt, section 9, item 2): the
            // companion reads the regions the system reports for its own box
            // (empty on systems without the Duo API, or injected by a test) and
            // pads the map so the route and the current stop stay clear of an
            // occluding hinge or a camera cutout instead of resetting the camera.
            let geometry = reservedGeometryOverride ?? geo.syrmosReservedGeometry()
            let mapHeight = max(200, geo.size.height * 0.42)
            let mapRect = CGRect(x: 0, y: 0, width: geo.size.width, height: mapHeight)
            let mapInsets = SyrmosMapPadding.insets(mapRect: mapRect, geometry: geometry)
            VStack(spacing: 0) {
                GoRouteMapView(
                    route: routeCoords,
                    current: currentCoord,
                    tint: UIColor(tint),
                    edgeInsets: mapInsets
                )
                .frame(height: mapHeight)
                .accessibilityLabel(t(
                    "Journey route map", "Χάρτης διαδρομής", "Harta e udhëtimit", "Mappa del percorso"))
                Divider()
                goTimeline
            }
        }
    }

    /// The journey's stops as map coordinates, in ride order, for the route line.
    private var routeCoords: [CLLocationCoordinate2D] {
        GoRouteProjection.routeCoordinates(journey: model.journey) {
            StationCoordinateLookup.shared.coordinate(for: $0)
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
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text(t("Journey", "Διαδρομή", "Udhëtimi", "Viaggio"))
                    .font(.headline)
                ForEach(Array(model.journey.legs.enumerated()), id: \.offset) { legIdx, leg in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 6) {
                            Text(leg.lineId)
                                .font(.caption.weight(.bold))
                                .padding(.horizontal, 6).padding(.vertical, 2)
                                .background(Color.syrmosPrimary.opacity(0.14), in: Capsule())
                                .foregroundStyle(Color.syrmosPrimary)
                            Text(t("toward", "προς", "drejt", "verso") + " " + leg.towards)
                                .font(.subheadline).foregroundStyle(.secondary)
                        }
                        ForEach(Array(leg.stops.enumerated()), id: \.offset) { stopIdx, stop in
                            let isCurrent = legIdx == model.position.legIndex && stopIdx == model.position.stopIndex
                            let isPast = legIdx < model.position.legIndex
                                || (legIdx == model.position.legIndex && stopIdx < model.position.stopIndex)
                            HStack(spacing: 10) {
                                Circle()
                                    .fill(isCurrent ? tint : Color.secondary.opacity(isPast ? 0.35 : 0.22))
                                    .frame(width: isCurrent ? 12 : 8, height: isCurrent ? 12 : 8)
                                Text(stop.name)
                                    .font(.subheadline)
                                    .fontWeight(isCurrent ? .semibold : .regular)
                                    .foregroundStyle(isPast ? .secondary : .primary)
                            }
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
        }
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

    private var footnote: some View {
        Text(t(
            "Step through your journey. Live get-off alerts as you ride are coming next.",
            "Δες το ταξίδι σου βήμα-βήμα. Οι ζωντανές ειδοποιήσεις αποβίβασης έρχονται σύντομα.",
            "Shiko udhëtimin hap pas hapi. Njoftimet e zbritjes në kohë reale vijnë së shpejti.",
            "Percorri il tuo viaggio passo passo. Gli avvisi di discesa in tempo reale arrivano presto."
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
private struct GoRouteMapView: UIViewRepresentable {
    let route: [CLLocationCoordinate2D]
    let current: CLLocationCoordinate2D?
    let tint: UIColor
    /// Padding that keeps the route fit and the current stop inside the map's
    /// visible area (base breathing room plus any hinge or cutout the companion
    /// reported). See `SyrmosMapPadding`.
    var edgeInsets: SyrmosEdgeInsets = .all(SyrmosMapPadding.base)

    private var uiEdgeInsets: UIEdgeInsets {
        UIEdgeInsets(top: edgeInsets.top, left: edgeInsets.left,
                     bottom: edgeInsets.bottom, right: edgeInsets.right)
    }

    func makeCoordinator() -> Coordinator { Coordinator(tint: tint) }

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
        if route.count >= 2 {
            map.addOverlay(MKPolyline(coordinates: route, count: route.count), level: .aboveLabels)
        }
        if let rect = boundingRect() {
            map.setVisibleMapRect(rect, edgePadding: uiEdgeInsets, animated: false)
        }
        context.coordinator.lastInsets = edgeInsets
        context.coordinator.syncCurrent(on: map, to: current)
        return map
    }

    func updateUIView(_ map: MKMapView, context: Context) {
        context.coordinator.syncCurrent(on: map, to: current)
        let insetsChanged = context.coordinator.lastInsets != edgeInsets
        context.coordinator.lastInsets = edgeInsets
        if let current {
            recenter(map, on: current, animated: true)
        } else if insetsChanged, let rect = boundingRect() {
            // No current stop to follow (e.g. a stop without coordinates): keep
            // the whole route visible inside the new padded area.
            map.setVisibleMapRect(rect, edgePadding: uiEdgeInsets, animated: true)
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

        init(tint: UIColor) { self.tint = tint }

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
                renderer.strokeColor = tint
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
