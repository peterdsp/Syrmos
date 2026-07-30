import SwiftUI
import WebKit

struct HomeView: View {
    @StateObject private var stasyService = STASYService()
    @StateObject private var railNewsService = RailNewsService()
    // Shared instance so HomeView and MapView don't each poll the live trains
    // endpoint in parallel.
    @ObservedObject private var liveTrainService = LiveTrainService.shared
    @StateObject private var locationService = LocationService()
    @ObservedObject private var loc = LocalizationManager.shared
    @ObservedObject private var schedules = SyrmosSchedulesStore.shared
    @ObservedObject private var freshnessStore = LiveDataFreshness.shared
    @ObservedObject private var tracking = DepartureTracking.shared
    @ObservedObject private var weather = WeatherStore.shared
    @State private var webLink: WebLink?
    @State private var isNearMeExpanded = true
    @State private var showLocationDeniedAlert = false
    @State private var showTrackPicker = false
    /// Set from Settings -> Developer -> Preview severe-weather card.
    @AppStorage("syrmos.dev.forceEmergencyPreview") private var forceEmergencyPreview: Bool = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    answerSection
                    alertsSection
                    railNewsSection
                    networkOverview
                    nearMeSection
                    liveTrainsSection
                }
                .padding(.horizontal)
                .padding(.top, 8)
                .padding(.bottom, 20)
            }
            .background(Color.syrmosBackground)
            .sheet(isPresented: $showTrackPicker) {
                TrackPickerSheet(onDismiss: { showTrackPicker = false })
            }
            .safeAreaInset(edge: .top, spacing: 8) {
                CompactTabHeader("Syrmos", subtitle: loc[.appSubtitle])
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(.hidden, for: .navigationBar)
            .refreshable {
                await stasyService.fetchAnnouncements()
            }
            .task {
                locationService.requestIfNeeded()
                await weather.refresh()
                await stasyService.fetchAnnouncements()
                await railNewsService.fetchNews()

                NotificationService.shared.checkForNewAlerts(stasyService.announcements)
                NotificationService.shared.checkWeather(weather.snapshot)
                NotificationService.shared.checkNearbyStationAlerts(
                    nearbyStations: locationService.nearbyStations,
                    alerts: stasyService.announcements
                )

                freshnessStore.onRetryRequested = { [weak stasyService] in
                    guard let svc = stasyService else { return }
                    Task { await svc.fetchAnnouncements() }
                }
            }
            .sheet(item: $webLink) { link in
                InAppWebView(url: link.url, title: link.title)
                    .presentationDetents([.large, .medium])
                    .presentationDragIndicator(.visible)
            }
            .alert(
                loc.language == .greek ? "Η τοποθεσία είναι απενεργοποιημένη" : loc.language == .albanian ? "Vendndodhja është e çaktivizuar" : "Location is disabled",
                isPresented: $showLocationDeniedAlert
            ) {
                Button(loc.language == .greek ? "Άνοιγμα Ρυθμίσεων" : loc.language == .albanian ? "Hap Cilësimet" : "Open Settings") {
                    locationService.openSystemSettings()
                }
                Button(loc.language == .greek ? "Άκυρο" : loc.language == .albanian ? "Anulo" : "Cancel", role: .cancel) {}
            } message: {
                Text(loc.language == .greek
                    ? "Δεν έχετε δώσει άδεια τοποθεσίας στο Syrmos. Θέλετε να ανοίξετε τις Ρυθμίσεις για να την ενεργοποιήσετε;"
                    : loc.language == .albanian
                    ? "Nuk i ke dhënë Syrmos leje për vendndodhjen. Dëshiron të hapësh Cilësimet për ta aktivizuar?"
                    : "You haven't granted Syrmos location access. Would you like to open Settings to enable it?")
            }
            // The legacy "New data available" alert was removed — schedule
            // refreshes happen silently in the background and the new data
            // simply takes effect on the user's next interaction. See the
            // store comment on evaluateFreshData() for the rationale.
        }
    }

    private var networkOverview: some View {
        let lines = SyrmosData.operationalLines
        let metroCount = lines.filter { $0.type == .metro }.count
        let tramCount = lines.filter { $0.type == .tram }.count
        let suburbanCount = lines.filter { $0.type == .suburban }.count
        let busCount = lines.filter { $0.type == .bus }.count
        return VStack(spacing: 12) {
            HStack(spacing: 12) {
                StatCard(value: "\(metroCount)", label: loc[.metro], color: .metroBlue)
                StatCard(value: "\(tramCount)", label: loc[.tram], color: .tramOrange)
            }
            HStack(spacing: 12) {
                StatCard(value: "\(suburbanCount)", label: loc[.suburban], color: .suburbanPurple)
                StatCard(
                    value: "\(busCount)",
                    label: loc.language == .greek ? "Λεωφορεια" : loc.language == .albanian ? "Autobuse" : "Bus",
                    color: SyrmosTokens.offline
                )
            }
        }
    }

    // MARK: - Answer-first home
    //
    // The lead block on Home: one actionable line ("Next M3 to Airport,
    // 4 min") with a freshness pill above it and the night's last-train
    // teaser below. Everything else (alerts, network tiles, nearby, lines)
    // is demoted underneath so the screen reads like a companion, not a
    // schedule. All three pieces are pure surfacing of data the projector
    // and live services already produce.

    @ViewBuilder
    private var answerSection: some View {
        let next = nearestNextDeparture()
        let last = nearestLastTrain(anchoredTo: next)
        let isTracking = tracking.active != nil
        VStack(spacing: 12) {
            if let tracked = tracking.active {
                trackingCard(tracked)
            }
            HStack(spacing: 8) {
                freshnessPill
                Spacer(minLength: 0)
                trackAnyTrainChip
            }
            // When a train is being tracked, the countdown lives in the
            // TrackingCard above and the "next train" hero duplicates it.
            // Hide the hero so there is exactly one countdown on screen.
            if !isTracking {
                answerHero(next: next)
            }
            if let last {
                lastTrainTeaser(last)
            }
            if let snap = weather.snapshot {
                // Real severe weather OR the developer preview toggle
                // (Settings -> Developer -> Preview severe-weather card).
                if snap.current.condition.isSevere || forceEmergencyPreview {
                    EmergencyWeatherCard(
                        condition: forceEmergencyPreview ? .thunderstorm : snap.current.condition,
                        language: loc.language
                    )
                }
                WeatherCard(snapshot: snap)
            } else if forceEmergencyPreview {
                // No weather snapshot yet but the preview toggle is on
                // (e.g. cold-start during smoke test): still render the
                // card with a canned thunderstorm condition.
                EmergencyWeatherCard(condition: .thunderstorm, language: loc.language)
            }
        }
    }

    /// Tier 2 in-app surface: a live countdown for the tracked departure that
    /// ticks every second and keeps the Live Activity in step. Mirrors the
    /// Compose TrackingCard: LIVE pulse + "Arriving <Station>" header, huge
    /// countdown, progress bar filling as time elapses, line badge row, and
    /// a single Stop tracking button at the bottom.
    private func trackingCard(_ tracked: TrackedDeparture) -> some View {
        let accent = SyrmosData.lineColor(for: tracked.lineId)
        return TimelineView(.periodic(from: .now, by: 1)) { context in
            let now = context.date.timeIntervalSince1970
            let remaining = tracked.minutesRemaining(now)
            let due = tracked.isDue(now)
            TrackingCardBody(
                tracked: tracked,
                accent: accent,
                remaining: remaining,
                due: due,
                now: now,
                lang: loc.language,
                onStop: { tracking.stop() }
            )
            .onChange(of: remaining) { _, _ in tracking.refresh(now: now) }
        }
    }

    private func trackNext(_ next: Departure) {
        guard let nearest = locationService.nearbyStations.first else { return }
        let node = nearest.station
        let stationId = node.stationIdByLineId[next.lineId] ?? node.stationIds.first ?? node.id
        let stations = SyrmosData.stations(for: next.lineId)
        let terminal = SyrmosData.line(for: next.lineId).map { line in
            line.terminalB.localizedCaseInsensitiveContains(next.direction)
                ? TransitDirection.outbound
                : TransitDirection.inbound
        } ?? TransitDirection.outbound
        let route = TrackedDeparture.computeRouteStations(
            stations: stations,
            targetStationId: stationId,
            direction: terminal,
            language: loc.language
        )
        let allLineIds = node.lineIds
        DepartureTracking.shared.track(
            TrackedDeparture(
                lineId: next.lineId,
                stationId: stationId,
                stationName: loc.language == .greek ? node.nameEl : node.displayName,
                destination: next.direction,
                scheduledTime: next.time,
                targetEpoch: Date().timeIntervalSince1970 + Double(next.minutesAway) * 60,
                routeStations: route,
                isStationMode: true,
                stationLineIds: allLineIds
            )
        )
    }

    private var freshnessPill: some View {
        let isLive = freshnessStore.freshness == .live
        let tint: Color = isLive ? SyrmosTokens.live : SyrmosTokens.warning
        let label = isLive ? loc[.runningOnline] : loc[.runningOffline]
        return HStack(spacing: 6) {
            Circle()
                .fill(tint)
                .frame(width: 8, height: 8)
            Text(label)
                .font(.caption)
                .foregroundStyle(.primary)
                .lineLimit(1)
            if !isLive {
                Button {
                    Task { await stasyService.fetchAnnouncements() }
                } label: {
                    Text(loc[.retry])
                        .font(.caption)
                        .foregroundStyle(Color.suburbanPurple)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color.suburbanPurple.opacity(0.1))
                        .clipShape(Capsule())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(tint.opacity(0.12))
        .clipShape(Capsule())
    }

    /// Chip that opens the TrackPickerSheet. Always visible alongside the
    /// freshness pill so users can pick any train (not just the nearest
    /// one) at any time. When something is already tracked, picking a new
    /// departure replaces it, matching DepartureTracking's single-slot
    /// semantics.
    private var trackAnyTrainChip: some View {
        Button {
            showTrackPicker = true
        } label: {
            HStack(spacing: 6) {
                Image(systemName: "scope")
                Text(trackAnyTrainLabel).fontWeight(.semibold)
            }
            .font(.caption)
            .foregroundStyle(Color.metroBlue)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(Color.metroBlue.opacity(0.14))
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    private var trackAnyTrainLabel: String {
        switch loc.language {
        case .greek: return "Παρακολούθηση συρμού"
        case .albanian: return "Ndiq një tren"
        case .english: return "Track a train"
        }
    }

    @ViewBuilder
    private func answerHero(next: Departure?) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(loc[.nextTrain].uppercased())
                .font(.caption)
                .fontWeight(.semibold)
                .foregroundStyle(.secondary)

            if let next {
                HStack(alignment: .center, spacing: 12) {
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 8) {
                            Text(next.lineId)
                                .font(.caption)
                                .fontWeight(.bold)
                                .foregroundStyle(.white)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 2)
                                .background(SyrmosData.lineColor(for: next.lineId))
                                .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                            Text("\(loc[.to]) \(next.direction)")
                                .font(.headline)
                                .lineLimit(1)
                                .foregroundStyle(.primary)
                        }
                        Text(next.time)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        SourceConfidenceChip(confidence: next.sourceConfidence, language: loc.language)
                    }
                    Spacer(minLength: 0)
                    Text(next.minutesAwayDisplay(language: loc.language))
                        .font(.title)
                        .fontWeight(.bold)
                        .foregroundStyle(SyrmosData.lineColor(for: next.lineId))
                }
                // "then 13, 23 min": the next couple of departures after the
                // featured one, matching the web hero.
                let thenTimes = nearestUpcoming().dropFirst().prefix(2)
                    .filter { $0.minutesAway > next.minutesAway }
                    .map { $0.minutesAwayDisplay(language: loc.language) }
                if !thenTimes.isEmpty {
                    let thenWord = loc.language == .greek ? "μετά" : loc.language == .albanian ? "pastaj" : "then"
                    Text("\(thenWord) \(thenTimes.joined(separator: ", "))")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                let isTracked = tracking.active != nil
                Button {
                    if !isTracked { trackNext(next) }
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: isTracked ? "location.fill" : "bell.fill")
                        Text(isTracked
                            ? (loc.language == .greek ? "Παρακολουθείται" : loc.language == .albanian ? "Po ndiqet" : "Tracking")
                            : (loc.language == .greek ? "Παρακολούθηση" : loc.language == .albanian ? "Ndiq" : "Track"))
                            .fontWeight(.semibold)
                    }
                    .font(.caption)
                    .foregroundStyle(isTracked ? Color.syrmosOnSurfaceMuted : SyrmosData.lineColor(for: next.lineId))
                    .padding(.horizontal, 12).padding(.vertical, 6)
                    .background((isTracked ? SyrmosTokens.offline : SyrmosData.lineColor(for: next.lineId)).opacity(0.14))
                    .clipShape(Capsule())
                }
                .buttonStyle(.plain)
                .disabled(isTracked)
            } else {
                Text(locationService.hasPermission ? loc[.serviceOver] : loc[.enableLocationForNext])
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.syrmosSurface)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func lastTrainTeaser(_ last: Departure) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "moon.stars.fill")
                .font(.caption)
                .foregroundStyle(.indigo)
            Text("\(loc[.lastTrain]) \(last.lineId) · \(loc[.leaveBy]) \(last.time)")
                .font(.subheadline)
                .foregroundStyle(.primary)
                .lineLimit(1)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.syrmosSurface)
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    /// Soonest departure across the nearest station's lines. Each line resolves
    /// its own per-line station id (interchange platforms carry one id per
    /// line) before the projector applies station offsets.
    private func nearestNextDeparture() -> Departure? {
        guard let nearest = locationService.nearbyStations.first else { return nil }
        let node = nearest.station
        var best: Departure?
        for lineId in node.lineIds {
            let stationId = node.stationIdByLineId[lineId] ?? node.stationIds.first ?? node.id
            let deps = ScheduleProjector.nextDepartures(for: stationId, lineIds: [lineId], limit: 2)
            if let first = deps.first, best == nil || first.minutesAway < best!.minutesAway {
                best = first
            }
        }
        return best
    }

    /// The soonest few departures across the nearest station's lines, sorted, so
    /// the hero can show "then 13, 23 min" after the featured one.
    private func nearestUpcoming() -> [Departure] {
        guard let nearest = locationService.nearbyStations.first else { return [] }
        let node = nearest.station
        var all: [Departure] = []
        for lineId in node.lineIds {
            let stationId = node.stationIdByLineId[lineId] ?? node.stationIds.first ?? node.id
            all += ScheduleProjector.nextDepartures(for: stationId, lineIds: [lineId], limit: 3)
        }
        return all.sorted { $0.minutesAway < $1.minutesAway }
    }

    /// Tonight's last train on the same line the next departure is on, so the
    /// hero answer and the teaser describe the line the user is about to ride.
    private func nearestLastTrain(anchoredTo next: Departure?) -> Departure? {
        guard let nearest = locationService.nearbyStations.first, let next else { return nil }
        let node = nearest.station
        let stationId = node.stationIdByLineId[next.lineId] ?? node.stationIds.first ?? node.id
        return ScheduleProjector.lastTrainTonight(for: stationId, lineIds: [next.lineId])
    }

    @ViewBuilder
    private var nearMeSection: some View {
        if !locationService.hasPermission {
            Button {
                if locationService.isDenied {
                    showLocationDeniedAlert = true
                } else {
                    locationService.requestIfNeeded()
                }
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: "location.circle.fill")
                        .font(.title2)
                        .foregroundStyle(.blue)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(loc.language == .greek ? "Κοντά μου" : loc.language == .albanian ? "Pranë meje" : "Near me")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .foregroundStyle(.primary)
                        Text(loc.language == .greek ? "Ενεργοποιήστε την τοποθεσία για να δείτε κοντινούς σταθμούς" : loc.language == .albanian ? "Aktivizo vendndodhjen për të parë stacionet afër" : "Enable location to see nearby stations")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 12)
                .background(Color.syrmosSurface)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
        } else if locationService.hasPermission && !locationService.nearbyStations.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Button {
                    withAnimation(.easeInOut(duration: 0.25)) {
                        isNearMeExpanded.toggle()
                    }
                } label: {
                    HStack {
                        Image(systemName: "location.fill")
                            .foregroundStyle(.blue)
                        Text(loc.language == .greek ? "Κοντά μου" : loc.language == .albanian ? "Pranë meje" : "Near me")
                            .font(.title3)
                            .fontWeight(.semibold)
                            .foregroundStyle(.primary)
                        Spacer()
                        Image(systemName: isNearMeExpanded ? "chevron.up" : "chevron.down")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .buttonStyle(.plain)

                if isNearMeExpanded {
                ForEach(locationService.nearbyStations) { nearby in
                    NavigationLink {
                        // ALWAYS return a non-empty destination view.
                        // Previously this fell through to an empty View for
                        // interchange stations like Piraeus (4 lines, stationIds
                        // and lineIds in different orders), which renders as a
                        // black screen with only the back chevron visible.
                        NearbyStationDestination(node: nearby.station)
                    } label: {
                        HStack(spacing: 12) {
                            HStack(spacing: 4) {
                                ForEach(nearby.station.lineIds.prefix(3), id: \.self) { lineId in
                                    Circle()
                                        .fill(SyrmosData.lineColor(for: lineId))
                                        .frame(width: 8, height: 8)
                                }
                            }

                            VStack(alignment: .leading, spacing: 2) {
                                Text(loc.language == .greek ? nearby.station.nameEl : nearby.station.displayName)
                                    .font(.subheadline)
                                    .fontWeight(.semibold)
                                    .foregroundStyle(.primary)
                                Text(nearby.station.lineIds.compactMap { SyrmosData.line(for: $0)?.name }.joined(separator: ", "))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }

                            Spacer()

                            Text(formatDistance(nearby.distanceMeters))
                                .font(.caption)
                                .fontWeight(.medium)
                                .foregroundStyle(.secondary)

                            Image(systemName: "chevron.right")
                                .font(.caption2)
                                .foregroundStyle(.tertiary)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(Color.syrmosSurface)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
                }
            }
        }
    }


    private func formatDistance(_ meters: Double) -> String {
        if meters < 1000 {
            return "\(Int(meters)) m"
        } else {
            return String(format: "%.1f km", meters / 1000)
        }
    }

    @ViewBuilder
    private var liveTrainsSection: some View {
        let realTrains = liveTrainService.trains.filter { !$0.origin.isEmpty && !$0.destination.isEmpty }
        if realTrains.isEmpty {
            EmptyView()
        } else {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 6) {
                    Image(systemName: "tram.fill")
                        .foregroundStyle(Color.suburbanPurple)
                    Text(loc.language == .greek ? "Ζωντανά τρένα" : loc.language == .albanian ? "Trenat aktiv" : "Live trains")
                        .font(.title3)
                        .fontWeight(.semibold)
                }

                ForEach(realTrains.prefix(4)) { train in
                    HStack(spacing: 10) {
                        VStack(spacing: 2) {
                            Text(train.lineId)
                                .font(.caption2)
                                .fontWeight(.bold)
                                .foregroundStyle(.white)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(SyrmosData.lineColor(for: train.lineId))
                                .clipShape(Capsule())
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            Text("\(train.origin) → \(train.destination)")
                                .font(.subheadline)
                                .fontWeight(.medium)
                                .lineLimit(1)
                            HStack(spacing: 6) {
                                Text("#\(train.trainNumber)")
                                    .font(.caption2)
                                    .foregroundStyle(.tertiary)
                                if train.delayMinutes > 0 {
                                    Text(loc.language == .greek ? "+\(train.delayMinutes)′ καθυστέρηση" : loc.language == .albanian ? "+\(train.delayMinutes)′ vonesë" : "+\(train.delayMinutes)′ delay")
                                        .font(.caption2)
                                        .foregroundStyle(SyrmosTokens.warning)
                                }
                            }
                        }
                        Spacer()
                        Circle()
                            .fill(SyrmosTokens.live)
                            .frame(width: 8, height: 8)
                    }
                    .padding(12)
                    .background(Color.syrmosSurface)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
            }
        }
    }

    @ViewBuilder
    private var alertsSection: some View {
        let alerts = stasyService.announcements.filter { $0.category == .serviceAlert }

        if !alerts.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundStyle(SyrmosTokens.warning)
                    Text(loc[.serviceAlerts])
                        .font(.title3)
                        .fontWeight(.semibold)
                    Spacer()
                    if let updated = stasyService.lastUpdated {
                        Text(updated, style: .relative)
                            .font(.caption2)
                            .foregroundStyle(.tertiary)
                    }
                }

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        ForEach(alerts.prefix(10)) { alert in
                            AlertCard(announcement: alert, onReadMore: { url in
                                webLink = WebLink(url: url, title: alert.displayTitle(language: loc.language))
                            })
                            .frame(width: 280)
                        }
                    }
                }
            }
        } else if let first = stasyService.announcements.first {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Image(systemName: "info.circle.fill")
                        .foregroundStyle(.blue)
                    Text(loc[.latestFromSTASY])
                        .font(.title3)
                        .fontWeight(.semibold)
                }
                AlertCard(announcement: first, onReadMore: { url in
                    webLink = WebLink(url: url, title: first.displayTitle(language: loc.language))
                })
            }
        }

        serviceStatusPill
    }

    @ViewBuilder
    private var railNewsSection: some View {
        if !railNewsService.news.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Image(systemName: "newspaper.fill")
                        .foregroundStyle(.blue)
                    Text(loc.language == .greek ? "Σιδηροδρομικα Νεα" : loc.language == .albanian ? "Lajme Hekurudhore" : "Rail News")
                        .font(.title3)
                        .fontWeight(.semibold)
                    Spacer()
                }

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        ForEach(railNewsService.news.prefix(10)) { item in
                            NewsCard(item: item, language: loc.language)
                                .onTapGesture {
                                    if let url = item.url {
                                        webLink = WebLink(url: url, title: item.displayTitle(language: loc.language))
                                    }
                                }
                        }
                    }
                }
            }
        }
    }

    /// Compact status row that replaces the prior "Could not reach stasy.gr"
    /// error banner. Surfaces /api/announcements.status: normal operation
    /// gets a green checkmark; an alert (e.g. "Trains until 21:40") shows
    /// the operator's verbatim message in orange. Falls back to today's
    /// last-departure time computed from the synced schedule rules so the
    /// user always sees SOMETHING, even when the announcements watcher is
    /// behind.
    @ViewBuilder
    private var serviceStatusPill: some View {
        let status = stasyService.serviceStatus
        let isAlert = status?.status == "alert"
        // When an active alert is also surfaced as a serviceAlert card
        // above, the pill would just repeat the same text. Hide it in
        // that case — the card already conveys the message in full.
        let hasOverlappingAlertCard = isAlert
            && stasyService.announcements.contains { $0.category == .serviceAlert }
        let message: String? = {
            if let s = status {
                let localized = s.displayMessage(language: loc.language)
                if !localized.isEmpty { return localized }
            }
            return fallbackServiceHours()
        }()
        if let message, !hasOverlappingAlertCard {
            HStack(spacing: 8) {
                Image(systemName: isAlert
                      ? "exclamationmark.triangle.fill"
                      : "checkmark.circle.fill")
                    .font(.caption)
                    .foregroundStyle(isAlert ? SyrmosTokens.warning : SyrmosTokens.live)
                Text(message)
                    .font(.caption)
                    .foregroundStyle(.primary)
                    .lineLimit(2)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(isAlert ? SyrmosTokens.warning.opacity(0.12) : SyrmosTokens.live.opacity(0.10))
            )
        }
    }

    /// Today's last metro/tram departure derived from the synced schedule
    /// rules. Picks the latest close_time across M1/M2/M3/T6/T7 for the
    /// current Athens day_type; falls back to a generic "Trains running"
    /// string when bundles aren't loaded yet.
    private func fallbackServiceHours() -> String? {
        let store = SyrmosSchedulesStore.shared
        let bundles = store.service.bundles
        if bundles.isEmpty { return nil }
        let athens = TimeZone(identifier: "Europe/Athens")!
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = athens
        let weekday = cal.component(.weekday, from: Date())
        let dayType: String
        switch weekday {
        case 1: dayType = "sun"
        case 2, 3, 4, 5: dayType = "mon_thu"
        case 6: dayType = "fri"
        case 7: dayType = "sat"
        default: dayType = "mon_thu"
        }
        var latest = ""
        for lineId in ["M1", "M2", "M3", "T6", "T7"] {
            guard let bundle = bundles[lineId] else { continue }
            guard let rule = bundle.rules.first(where: { $0.dayType == dayType }) else { continue }
            if rule.closeTime > latest { latest = rule.closeTime }
            let is24h = rule.is247 || (
                dayType == "sat"
                && rule.closeTime < rule.openTime
                && rule.openTime >= "05:00"
                && rule.closeTime >= "05:00"
            )
            if is24h {
                return loc.language == .greek
                    ? "Λειτουργία 24/7 σήμερα"
                    : loc.language == .albanian
                    ? "Shërbim 24/7 sot"
                    : "24/7 service today"
            }
        }
        if latest.isEmpty { return nil }
        return loc.language == .greek
            ? "Δρομολόγια έως \(latest)"
            : loc.language == .albanian
            ? "Trena deri në \(latest)"
            : "Trains until \(latest)"
    }

}

// MARK: - Nearby Station Destination
//
// Bulletproof destination for the "Near me" NavigationLink. Tries each
// (lineId, stationId) pair until it finds a matching TransitStation and
// pushes StationDetailView. If nothing matches (this happens at interchange
// stops like Piraeus where the merged MapStationNode's stationIds and
// lineIds arrays don't line up), it falls back to a minimal screen rather
// than rendering an empty View — which was showing up as a black screen.

struct NearbyStationDestination: View {
    let node: MapStationNode
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        if let station = resolveTransitStation() {
            StationDetailView(station: station)
        } else {
            // Defensive fallback. Should be unreachable now that we walk
            // every (line, station) pair, but if data is ever malformed we
            // want a real view to show, not a black NavigationStack push.
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(loc.language == .greek ? node.nameEl : node.displayName)
                        .font(.title2)
                        .fontWeight(.bold)
                    Text(loc.language == .greek
                         ? "Ο σταθμός δεν είναι ακόμη διαθέσιμος"
                         : loc.language == .albanian
                         ? "Ky stacion ende nuk është i disponueshëm"
                         : "This station isn't available yet.")
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding()
            }
            .background(Color.syrmosBackground)
            .navigationTitle(loc.language == .greek ? node.nameEl : node.displayName)
        }
    }

    private func resolveTransitStation() -> TransitStation? {
        // The MapStationNode `node` already carries the cluster-merged
        // line set (M1+M3+A1+A4 at Piraeus, M2+M3+T6 at Syntagma, etc).
        // The base TransitStation we look up here only knows about its
        // own per-row lineAssociations entry, which can be a strict
        // subset — e.g. A1_PIR's row historically listed [A1, M1] and
        // missed the M3 terminus. We override the returned station's
        // lineIds with `node.lineIds` so the detail view always shows
        // every line that actually calls at this physical platform,
        // regardless of how complete the hardcoded association table is.
        guard let base = lookupBaseStation() else { return nil }
        return TransitStation(
            id: base.id,
            name: base.name,
            nameEl: base.nameEl,
            coordinate: base.coordinate,
            lineIds: node.lineIds,
            isInterchange: node.isInterchange || base.isInterchange
        )
    }

    private func lookupBaseStation() -> TransitStation? {
        // 1. Best path: stationIdByLineId is correctly paired.
        for lineId in node.lineIds {
            if let stationId = node.stationIdByLineId[lineId],
               let match = SyrmosData.stations(for: lineId).first(where: { $0.id == stationId }) {
                return match
            }
        }
        // 2. Fallback: try every (lineId, stationId) cross product.
        for lineId in node.lineIds {
            let stationsOnLine = SyrmosData.stations(for: lineId)
            for sid in node.stationIds {
                if let match = stationsOnLine.first(where: { $0.id == sid }) {
                    return match
                }
            }
        }
        // 3. Last resort: any station whose id matches anything we know.
        for sid in node.stationIds {
            for lineId in SyrmosData.lines.map(\.id) {
                if let match = SyrmosData.stations(for: lineId).first(where: { $0.id == sid }) {
                    return match
                }
            }
        }
        return nil
    }
}

// MARK: - Stat Card

struct StatCard: View {
    let value: String
    let label: String
    let color: Color

    var body: some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundStyle(color)
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(Color.syrmosSurface)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

// MARK: - Alert Card (expandable)

struct AlertCard: View {
    let announcement: STASYAnnouncement
    let onReadMore: (URL) -> Void
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        Button {
            if let url = announcement.url { onReadMore(url) }
        } label: {
            HStack(alignment: .top, spacing: 10) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(announcement.displayTitle(language: loc.language))
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .multilineTextAlignment(.leading)
                        .foregroundStyle(.primary)

                    if !announcement.date.isEmpty {
                        Text(announcement.date)
                            .font(.caption2)
                            .foregroundStyle(.tertiary)
                    }
                }
                Spacer(minLength: 4)
                if announcement.url != nil {
                    Image(systemName: "arrow.up.right")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color.syrmosPrimary)
                        .padding(.top, 2)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(announcement.url == nil)
        .background(
            announcement.category == .serviceAlert
                ? SyrmosTokens.warning.opacity(0.08)
                : Color.syrmosSurface
        )
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .strokeBorder(
                    announcement.category == .serviceAlert
                        ? SyrmosTokens.warning.opacity(0.2)
                        : Color.clear,
                    lineWidth: 1
                )
        )
    }
}

// MARK: - News Card

struct NewsCard: View {
    let item: RailNewsItem
    let language: AppLanguage

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(item.displayTitle(language: language))
                .font(.subheadline)
                .fontWeight(.medium)
                .multilineTextAlignment(.leading)
                .foregroundStyle(.primary)
                .lineLimit(3)

            if !item.formattedDate.isEmpty {
                Text(item.formattedDate)
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }

        }
        .frame(width: 220, alignment: .leading)
        .padding(14)
        .background(Color.syrmosSurface)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

// MARK: - WebLink

struct WebLink: Identifiable {
    let id = UUID()
    let url: URL
    let title: String
}

// MARK: - In-App WebView

struct InAppWebView: View {
    let url: URL
    var title: String = ""
    @Environment(\.dismiss) private var dismiss
    @State private var isLoading = true

    var body: some View {
        NavigationStack {
            ZStack {
                WebViewRepresentable(url: url, isLoading: $isLoading)
                if isLoading {
                    ProgressView()
                }
            }
            .navigationTitle(title.isEmpty ? (url.host ?? "Syrmos") : title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

struct WebViewRepresentable: UIViewRepresentable {
    let url: URL
    @Binding var isLoading: Bool

    func makeUIView(context: Context) -> WKWebView {
        let webView = WKWebView()
        webView.navigationDelegate = context.coordinator
        webView.load(URLRequest(url: url))
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    class Coordinator: NSObject, WKNavigationDelegate {
        let parent: WebViewRepresentable

        init(parent: WebViewRepresentable) {
            self.parent = parent
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            parent.isLoading = false
        }
    }
}
