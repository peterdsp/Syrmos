import SwiftUI

// Bottom sheet that lets the user pick any train to track. Mirrors the
// Compose TrackPickerSheet in feature/home. Linear four-step drill-down:
// Line -> Direction -> Station -> Departure. Picking a departure hydrates
// a TrackedDeparture and hands off to DepartureTracking.shared.track,
// which starts the Live Activity and updates the HomeView TrackingCard.
struct TrackPickerSheet: View {
    let onDismiss: () -> Void

    @ObservedObject private var loc = LocalizationManager.shared

    @State private var step: PickStep = .line
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
            if step != .line {
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

    private var lineList: some View {
        ScrollView {
            VStack(spacing: 8) {
                // A line that does not run belongs on the map, greyed, not in a
                // picker: every entry here is a train the user expects to catch.
                ForEach(SyrmosData.operationalLines) { line in
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
            // M3 also runs the airport branch (M3_AIR); let the user track it.
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
                        step = .departure
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

    private func departureList(line: TransitLine, station: TransitStation, direction: TransitDirection) -> some View {
        // Same real projector the station sheet and Home "next train" use, so
        // the track flow shows live data instead of the old sample set. The
        // 12-hour horizon rolls into tomorrow's first trains overnight.
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
                            DepartureTracking.shared.track(
                                TrackedDeparture(
                                    lineId: line.id,
                                    stationId: station.id,
                                    stationName: stationName,
                                    destination: terminal,
                                    scheduledTime: dep.time,
                                    targetEpoch: Date().timeIntervalSince1970 + Double(dep.minutesAway) * 60,
                                    routeStations: route
                                )
                            )
                            onDismiss()
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(dep.time)
                                        .font(.body).fontWeight(.semibold)
                                        .foregroundStyle(.primary)
                                    Text("\(dep.minutesAway) min")
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
        case .line: break
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
        case .line: return pickLineHeader
        case .direction: return "\(selectedLine?.id ?? "") · \(pickDirectionHeader)"
        case .station: return "\(selectedLine?.id ?? "") · \(pickStationHeader)"
        case .departure:
            let name = selectedStation.flatMap { s -> String? in
                loc.language == .greek ? s.nameEl : s.name
            } ?? ""
            return name
        }
    }

    private var cancelLabel: String {
        switch loc.language {
        case .greek: return "Άκυρο"
        case .albanian: return "Anulo"
        case .english: return "Cancel"
        }
    }
    private var pickLineHeader: String {
        switch loc.language {
        case .greek: return "Επίλεξε γραμμή"
        case .albanian: return "Zgjidh linjën"
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
        case .albanian: return "S'ka nisje të radhës."
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

enum PickStep { case line, direction, station, departure }
enum TransitDirection { case outbound, inbound, airport }
