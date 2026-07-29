import SwiftUI

// Bottom sheet that lets the user pick any train to track. Two modes:
// 1. Track a specific train: Line -> Direction -> Station -> Departure
// 2. Track all trains at a station: Line -> Direction -> Station (station mode)
// Metro lines are grayed out with a note about frequent service.
struct TrackPickerSheet: View {
    let onDismiss: () -> Void

    @ObservedObject private var loc = LocalizationManager.shared

    @State private var step: PickStep = .choice
    @State private var trackMode: TrackMode? = nil
    @State private var selectedLine: TransitLine? = nil
    @State private var selectedDirection: TransitDirection? = nil
    @State private var selectedStation: TransitStation? = nil

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                header
                content
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(cancelLabel, action: onDismiss)
                }
            }
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    private var header: some View {
        HStack(spacing: 10) {
            if step != .choice {
                Button(action: back) {
                    Image(systemName: "chevron.left")
                        .font(.headline)
                        .foregroundStyle(.primary)
                }
            }
            Text(headerText)
                .font(.title3).fontWeight(.semibold)
                .lineLimit(1)
                .truncationMode(.tail)
            Spacer(minLength: 0)
        }
    }

    @ViewBuilder
    private var content: some View {
        switch step {
        case .choice:
            choiceList
        case .line:
            lineList
        case .direction:
            if let line = selectedLine { directionList(line: line) } else { EmptyView() }
        case .station:
            if let line = selectedLine { stationList(line: line) } else { EmptyView() }
        case .departure:
            if let line = selectedLine, let station = selectedStation, let dir = selectedDirection {
                departureList(line: line, station: station, direction: dir)
            }
        }
    }

    // MARK: - Choice step

    private var choiceList: some View {
        VStack(spacing: 10) {
            Button {
                trackMode = .specificTrain
                step = .line
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: "tram.fill")
                        .font(.title3)
                        .foregroundStyle(Color.metroBlue)
                        .frame(width: 36)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(specificTrainTitle)
                            .font(.body).fontWeight(.semibold)
                            .foregroundStyle(.primary)
                        Text(specificTrainSubtitle)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.right")
                        .font(.caption).foregroundStyle(.secondary)
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.syrmosSurface)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)

            Button {
                trackMode = .stationAll
                step = .line
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: "mappin.and.ellipse")
                        .font(.title3)
                        .foregroundStyle(Color.suburbanPurple)
                        .frame(width: 36)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(stationAllTitle)
                            .font(.body).fontWeight(.semibold)
                            .foregroundStyle(.primary)
                        Text(stationAllSubtitle)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.right")
                        .font(.caption).foregroundStyle(.secondary)
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.syrmosSurface)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)

            Spacer()
        }
    }

    // MARK: - Line step

    private static let frequentLineIds: Set<String> = ["M1", "M2", "M3", "T6", "T7"]

    private var lineList: some View {
        ScrollView {
            VStack(spacing: 8) {
                ForEach(SyrmosData.operationalLines.filter { !Self.frequentLineIds.contains($0.id) }) { line in
                    Button {
                        selectedLine = line
                        step = .direction
                    } label: {
                        HStack(spacing: 12) {
                            Text(line.id)
                                .font(.caption).fontWeight(.bold)
                                .foregroundStyle(.white)
                                .padding(.horizontal, 8).padding(.vertical, 4)
                                .background(line.color)
                                .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                            VStack(alignment: .leading, spacing: 2) {
                                Text(loc.language == .greek ? line.nameEl : line.name)
                                    .font(.body).fontWeight(.semibold)
                                    .foregroundStyle(.primary)
                                Text("\(line.terminalA) - \(line.terminalB)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }
                            Spacer(minLength: 0)
                        }
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.syrmosSurface)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private func directionList(line: TransitLine) -> some View {
        VStack(spacing: 8) {
            directionRow(line: line, target: line.terminalB, direction: .outbound)
            directionRow(line: line, target: line.terminalA, direction: .inbound)
            if lineHasAirport(line) {
                directionRow(line: line, target: airportLabel, direction: .airport)
            }
            Spacer()
        }
    }

    private func lineHasAirport(_ line: TransitLine) -> Bool { line.id == "M3" }

    private var airportLabel: String {
        switch loc.language {
        case .greek: return "Αεροδρόμιο"
        case .albanian: return "Aeroporti"
        case .english: return "Airport"
        }
    }

    private func directionRow(line: TransitLine, target: String, direction: TransitDirection) -> some View {
        Button {
            selectedDirection = direction
            step = .station
        } label: {
            HStack(spacing: 10) {
                RoundedRectangle(cornerRadius: 3, style: .continuous)
                    .fill(line.color)
                    .frame(width: 6, height: 24)
                Text("\(loc[.to]) \(target)")
                    .font(.body).fontWeight(.semibold)
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                Spacer(minLength: 0)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.syrmosSurface)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private func stationList(line: TransitLine) -> some View {
        let stations = SyrmosData.stations(for: line.id)
        return ScrollView {
            VStack(spacing: 6) {
                ForEach(stations) { station in
                    Button {
                        selectedStation = station
                        if trackMode == .stationAll {
                            startStationTracking(line: line, station: station)
                        } else {
                            step = .departure
                        }
                    } label: {
                        HStack {
                            Text(loc.language == .greek ? station.nameEl : station.name)
                                .font(.body)
                                .foregroundStyle(.primary)
                                .lineLimit(1)
                            Spacer(minLength: 0)
                            if station.isInterchange {
                                Image(systemName: "arrow.left.arrow.right")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .padding(.horizontal, 12).padding(.vertical, 10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.syrmosSurface)
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private func startStationTracking(line: TransitLine, station: TransitStation) {
        guard let dir = selectedDirection else { return }
        let lineIds = station.lineIds
        let departures = ScheduleProjector.nextDepartures(
            for: station.id, lineIds: lineIds, limit: 10, timeHorizonMinutes: 3 * 60
        ).filter { $0.minutesAway > 0 }
        guard let next = departures.first else {
            onDismiss()
            return
        }
        let stationName = loc.language == .greek ? station.nameEl : station.name
        let route = TrackedDeparture.computeRouteStations(
            stations: SyrmosData.stations(for: next.lineId),
            targetStationId: station.id,
            direction: dir,
            language: loc.language
        )
        let dirKey: String
        switch dir {
        case .outbound: dirKey = "outbound"
        case .inbound: dirKey = "inbound"
        case .airport: dirKey = "airport"
        }
        DepartureTracking.shared.track(
            TrackedDeparture(
                lineId: next.lineId,
                stationId: station.id,
                stationName: stationName,
                destination: next.direction,
                scheduledTime: next.time,
                targetEpoch: Date().timeIntervalSince1970 + Double(next.minutesAway) * 60,
                routeStations: route,
                directionKey: dirKey,
                isStationMode: true,
                stationLineIds: lineIds
            )
        )
        onDismiss()
    }

    private func departureList(line: TransitLine, station: TransitStation, direction: TransitDirection) -> some View {
        let departures = ScheduleProjector.nextDepartures(
            for: station.id,
            lineIds: [line.id],
            limit: 100,
            timeHorizonMinutes: 12 * 60
        ).filter { dep in
            switch direction {
            case .airport:
                return dep.serviceType == "airport"
            case .outbound:
                return dep.serviceType != "airport"
                    && (dep.direction.localizedCaseInsensitiveContains(line.terminalB) || dep.direction.isEmpty)
            case .inbound:
                return dep.serviceType != "airport"
                    && (dep.direction.localizedCaseInsensitiveContains(line.terminalA) || dep.direction.isEmpty)
            }
        }
        return ScrollView {
            VStack(spacing: 6) {
                if departures.isEmpty {
                    Text(noDeparturesLabel)
                        .font(.body)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding()
                } else {
                    ForEach(departures) { dep in
                        Button {
                            let terminal: String
                            switch direction {
                            case .outbound: terminal = line.terminalB
                            case .inbound: terminal = line.terminalA
                            case .airport: terminal = airportLabel
                            }
                            let stationName = loc.language == .greek ? station.nameEl : station.name
                            let route = TrackedDeparture.computeRouteStations(
                                stations: SyrmosData.stations(for: line.id),
                                targetStationId: station.id,
                                direction: direction,
                                language: loc.language
                            )
                            let dirKey: String
                            switch direction {
                            case .outbound: dirKey = "outbound"
                            case .inbound: dirKey = "inbound"
                            case .airport: dirKey = "airport"
                            }
                            DepartureTracking.shared.track(
                                TrackedDeparture(
                                    lineId: line.id,
                                    stationId: station.id,
                                    stationName: stationName,
                                    destination: terminal,
                                    scheduledTime: dep.time,
                                    targetEpoch: Date().timeIntervalSince1970 + Double(dep.minutesAway) * 60,
                                    routeStations: route,
                                    directionKey: dirKey
                                )
                            )
                            onDismiss()
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    HStack(spacing: 4) {
                                        Text(dep.time)
                                            .font(.body).fontWeight(.semibold)
                                            .foregroundStyle(.primary)
                                        if let trainNo = dep.trainNo {
                                            Text("#\(trainNo)")
                                                .font(.caption2).fontWeight(.medium)
                                                .foregroundStyle(.secondary)
                                        }
                                    }
                                    Text(dep.minutesAwayDisplay(language: loc.language))
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer(minLength: 0)
                                Text(trackVerbLabel)
                                    .font(.caption).fontWeight(.semibold)
                                    .foregroundStyle(line.color)
                            }
                            .padding(.horizontal, 12).padding(.vertical, 10)
                            .frame(maxWidth: .infinity)
                            .background(Color.syrmosSurface)
                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private func back() {
        switch step {
        case .choice: break
        case .line:
            trackMode = nil
            step = .choice
        case .direction:
            selectedLine = nil
            step = .line
        case .station:
            selectedDirection = nil
            step = .direction
        case .departure:
            selectedStation = nil
            step = .station
        }
    }

    private var headerText: String {
        switch step {
        case .choice: return trackTrainHeader
        case .line: return pickLineHeader
        case .direction: return "\(selectedLine?.id ?? "") - \(pickDirectionHeader)"
        case .station: return "\(selectedLine?.id ?? "") - \(pickStationHeader)"
        case .departure:
            let name = selectedStation.flatMap { s -> String? in
                loc.language == .greek ? s.nameEl : s.name
            } ?? ""
            return name
        }
    }

    // MARK: - Localized labels

    private var cancelLabel: String {
        switch loc.language {
        case .greek: return "Άκυρο"
        case .albanian: return "Anulo"
        case .english: return "Cancel"
        }
    }
    private var trackTrainHeader: String {
        switch loc.language {
        case .greek: return "Παρακολούθηση"
        case .albanian: return "Ndiq"
        case .english: return "Track"
        }
    }
    private var specificTrainTitle: String {
        switch loc.language {
        case .greek: return "Συγκεκριμένο δρομολόγιο"
        case .albanian: return "Nje tren specifik"
        case .english: return "A specific train"
        }
    }
    private var specificTrainSubtitle: String {
        switch loc.language {
        case .greek: return "Επιλέξτε γραμμή, σταθμό και δρομολόγιο"
        case .albanian: return "Zgjidhni linjen, stacionin dhe nisjen"
        case .english: return "Pick a line, station and departure"
        }
    }
    private var stationAllTitle: String {
        switch loc.language {
        case .greek: return "Ολα τα δρομολόγια σε σταθμό"
        case .albanian: return "Te gjitha trenet ne stacion"
        case .english: return "All trains at a station"
        }
    }
    private var stationAllSubtitle: String {
        switch loc.language {
        case .greek: return "Παρακολουθήστε συνεχώς τα δρομολόγια"
        case .albanian: return "Ndiqni vazhdimisht trenet"
        case .english: return "Continuously track departures"
        }
    }
    private var metroFrequentNote: String {
        switch loc.language {
        case .greek: return "Το μετρό έρχεται συχνά, δεν χρειάζεται παρακολούθηση"
        case .albanian: return "Metroja vjen shpesh, nuk ka nevoje per ndjekje"
        case .english: return "Metro runs frequently, no need to track"
        }
    }
    private var pickLineHeader: String {
        switch loc.language {
        case .greek: return "Επίλεξε γραμμή"
        case .albanian: return "Zgjidh linjen"
        case .english: return "Pick a line"
        }
    }
    private var pickDirectionHeader: String {
        switch loc.language {
        case .greek: return "Κατεύθυνση"
        case .albanian: return "Drejtimi"
        case .english: return "Direction"
        }
    }
    private var pickStationHeader: String {
        switch loc.language {
        case .greek: return "Σταθμός"
        case .albanian: return "Stacion"
        case .english: return "Station"
        }
    }
    private var noDeparturesLabel: String {
        switch loc.language {
        case .greek: return "Δεν υπάρχουν επόμενες αναχωρήσεις."
        case .albanian: return "S'ka nisje te radhes."
        case .english: return "No upcoming departures."
        }
    }
    private var trackVerbLabel: String {
        switch loc.language {
        case .greek: return "Παρακολούθηση"
        case .albanian: return "Ndiq"
        case .english: return "Track"
        }
    }
}

enum TrackMode { case specificTrain, stationAll }
enum PickStep { case choice, line, direction, station, departure }
enum TransitDirection: Sendable { case outbound, inbound, airport }
