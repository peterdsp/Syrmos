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
    @ObservedObject private var deepLinkRouter = DeepLinkRouter.shared
    @State private var navigationPath = NavigationPath()
    @State private var webViewURL: URL?
    @State private var isNearMeExpanded = true
    @State private var nearbyListMode = false
    @State private var selectedNearbyId: String?
    @State private var showAllInsights = false
    @State private var showLocationDeniedAlert = false
    @State private var showTrackPicker = false
    @AppStorage("syrmos.selectedTab") private var selectedTab: SyrmosTab = .home
    /// Set from Settings -> Developer -> Preview severe-weather card.
    @AppStorage("syrmos.dev.forceEmergencyPreview") private var forceEmergencyPreview: Bool = false

    var body: some View {
        NavigationStack(path: $navigationPath) {
            ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 20) {
                    answerSection.syrmosEntrance(index: 0).id(HomeAnchor.weather)
                    livingMapStrip.syrmosEntrance(index: 1)
                    weatherContextSection.syrmosEntrance(index: 2)
                    insightsStream.syrmosEntrance(index: 3)
                    radialNearbySection.syrmosEntrance(index: 4).id(HomeAnchor.nearby)
                    liveTrainsSection.syrmosEntrance(index: 5)
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
                handleDeepLink(deepLinkRouter.pending, proxy: proxy)
            }
            .sheet(item: $webViewURL) { url in
                InAppWebView(url: url)
                    .presentationDetents([.large, .medium])
                    .presentationDragIndicator(.visible)
            }
            .alert(
                loc.language == .greek ? "Η τοποθεσία είναι απενεργοποιημένη" : loc.language == .albanian ? "Vendndodhja është e çaktivizuar" : loc.language == .italian ? "La posizione e disabilitata" : "Location is disabled",
                isPresented: $showLocationDeniedAlert
            ) {
                Button(loc.language == .greek ? "Άνοιγμα Ρυθμίσεων" : loc.language == .albanian ? "Hap Cilësimet" : loc.language == .italian ? "Apri Impostazioni" : "Open Settings") {
                    locationService.openSystemSettings()
                }
                Button(loc.language == .greek ? "Άκυρο" : loc.language == .albanian ? "Anulo" : loc.language == .italian ? "Annulla" : "Cancel", role: .cancel) {}
            } message: {
                Text(loc.language == .greek
                    ? "Δεν έχετε δώσει άδεια τοποθεσίας στο Syrmos. Θέλετε να ανοίξετε τις Ρυθμίσεις για να την ενεργοποιήσετε;"
                    : loc.language == .albanian
                    ? "Nuk i ke dhënë Syrmos leje për vendndodhjen. Dëshiron të hapësh Cilësimet për ta aktivizuar?"
                    : loc.language == .italian
                    ? "Non hai concesso a Syrmos l'accesso alla posizione. Vuoi aprire le Impostazioni per abilitarlo?"
                    : "You haven't granted Syrmos location access. Would you like to open Settings to enable it?")
            }
            // The legacy "New data available" alert was removed — schedule
            // refreshes happen silently in the background and the new data
            // simply takes effect on the user's next interaction. See the
            // store comment on evaluateFreshData() for the rationale.
            .navigationDestination(for: DeepLinkStation.self) { dest in
                if let station = resolveStation(id: dest.id) {
                    StationDetailView(station: station)
                }
            }
            .navigationDestination(for: DeepLinkLine.self) { dest in
                if let line = SyrmosData.line(for: dest.id) {
                    LineDetailView(line: line, stations: SyrmosData.stations(for: dest.id))
                }
            }
            .navigationDestination(for: DeepLinkAlert.self) { dest in
                if let alert = stasyService.announcements.first(where: { $0.id == dest.id })
                    ?? STASYService.cachedAlert(byId: dest.id) {
                    AlertDetailSheet(alert: alert, language: loc.language)
                } else {
                    ContentUnavailableView(
                        "Alert unavailable",
                        systemImage: "exclamationmark.triangle"
                    )
                }
            }
            .onChange(of: deepLinkRouter.pending) { _, destination in
                handleDeepLink(destination, proxy: proxy)
            }
            }
        }
    }

    private func handleDeepLink(
        _ destination: DeepLinkRouter.Destination?,
        proxy: ScrollViewProxy
    ) {
        guard let destination else { return }
        selectedTab = .home
        deepLinkRouter.pending = nil
        switch destination {
        case .station(let id):
            navigationPath.append(DeepLinkStation(id: id))
        case .line(let id):
            navigationPath.append(DeepLinkLine(id: id))
        case .serviceAlert(let id):
            navigationPath.append(DeepLinkAlert(id: id))
        case .weatherAlert:
            withAnimation { proxy.scrollTo(HomeAnchor.weather, anchor: .top) }
        case .nearbyAlert(let stationId):
            if let stationId, !stationId.isEmpty {
                navigationPath.append(DeepLinkStation(id: stationId))
            } else {
                withAnimation { proxy.scrollTo(HomeAnchor.nearby, anchor: .top) }
            }
        case .morningDigest:
            withAnimation { proxy.scrollTo(HomeAnchor.weather, anchor: .top) }
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
        VStack(spacing: 12) {
            if let tracked = tracking.active {
                VStack(spacing: 8) {
                    HStack(spacing: 8) {
                        pulseContextTag(
                            homeText("In transit", "Σε διαδρομή", "Në udhëtim", "In viaggio"),
                            color: SyrmosData.lineColor(for: tracked.lineId)
                        )
                        Spacer(minLength: 0)
                        freshnessPill
                        trackAnyTrainChip
                    }
                    trackingCard(tracked)
                }
            } else {
                proactivePulse(next: next, last: last)
            }
        }
    }

    @ViewBuilder
    private func proactivePulse(next: Departure?, last: Departure?) -> some View {
        TimelineView(.periodic(from: .now, by: 1)) { timeline in
            let hour = Calendar(identifier: .gregorian).component(.hour, from: timeline.date)
            let severity = next.flatMap { stasyService.lineDisruptions[$0.lineId] }
            let disrupted = severity == "warning" || severity == "closure"
            let lateNight = last != nil && (hour >= 22 || hour < 5)
            let stateColor: Color = disrupted
                ? SyrmosTokens.disruption
                : lateNight
                ? SyrmosTokens.warning
                : freshnessStore.freshness == .live
                ? Color.syrmosPrimary
                : SyrmosTokens.offline

            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    pulseContextTag(
                        pulseContext(disrupted: disrupted, lateNight: lateNight, hour: hour),
                        color: stateColor
                    )
                    Spacer(minLength: 0)
                    freshnessPill
                }

                if let next {
                    let seconds = next.secondsAway(from: timeline.date)
                    let countdown = heroCountdownText(secondsAway: seconds, language: loc.language)
                    let accent = SyrmosData.lineColor(for: next.lineId)

                    Text(lateNight
                         ? homeText("Last trains tonight", "Τελευταίοι συρμοί απόψε", "Trenat e fundit sonte", "Ultimi treni stasera")
                         : loc[.nextTrain])
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(stateColor)

                    HStack(spacing: 8) {
                        LinePill(
                            lineId: next.lineId,
                            size: .small,
                            disruptionSeverity: severity
                        )
                        Text("\(loc[.to]) \(next.direction)")
                            .font(.title3.weight(.semibold))
                            .lineLimit(1)
                    }

                    Text(countdown)
                        .font(.system(size: SyrmosTokens.Font.displayPulseSize, weight: .heavy, design: .rounded))
                        .monospacedDigit()
                        .foregroundStyle(seconds <= 60 ? SyrmosTokens.arrivalImminent : accent)
                        .contentTransition(.numericText())
                        .modifier(HeroImminentPulse(active: seconds <= 60))

                    let later = nearestUpcoming().dropFirst().prefix(2)
                        .filter { $0.minutesAway > next.minutesAway }
                        .map { $0.minutesAwayDisplay(language: loc.language) }
                    if !later.isEmpty {
                        Text("\(homeText("then", "μετά", "pastaj", "poi")) \(later.joined(separator: ", "))")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    SourceConfidenceChip(confidence: next.sourceConfidence, language: loc.language)

                    if disrupted {
                        Text(homeText(
                            "A service update affects this line. The latest verified detail is below.",
                            "Μια ενημέρωση υπηρεσίας επηρεάζει αυτή τη γραμμή. Η τελευταία επιβεβαιωμένη πληροφορία είναι παρακάτω.",
                            "Një përditësim shërbimi prek këtë linjë. Detajet e fundit të verifikuara janë më poshtë.",
                            "Un aggiornamento di servizio interessa questa linea. I dettagli verificati sono qui sotto."
                        ))
                            .font(.subheadline.weight(.medium))
                            .foregroundStyle(SyrmosTokens.disruption)
                    }

                    if let last {
                        lastTrainTeaser(last)
                    }

                    if let snapshot = weather.snapshot {
                        Text(weatherPulseText(snapshot))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    HStack(spacing: 8) {
                        pulseAction(
                            icon: "bell.fill",
                            label: homeText("Track", "Παρακολούθηση", "Ndiq", "Segui"),
                            color: accent,
                            action: { trackNext(next) }
                        )
                        pulseAction(
                            icon: "scope",
                            label: trackAnyTrainLabel,
                            color: .syrmosPrimary,
                            action: { showTrackPicker = true }
                        )
                    }
                } else {
                    Text(locationService.hasPermission ? loc[.serviceOver] : loc[.enableLocationForNext])
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    pulseAction(
                        icon: "scope",
                        label: trackAnyTrainLabel,
                        color: .syrmosPrimary,
                        action: { showTrackPicker = true }
                    )
                }
            }
            .padding(20)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                LinearGradient(
                    colors: [stateColor.opacity(0.14), Color.syrmosSurface],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(stateColor.opacity(0.22), lineWidth: 1)
            }
            .animation(.spring(response: 0.45, dampingFraction: 0.82), value: disrupted)
            .animation(.spring(response: 0.45, dampingFraction: 0.82), value: lateNight)
        }
    }

    private func pulseContext(disrupted: Bool, lateNight: Bool, hour: Int) -> String {
        if disrupted {
            return homeText("Service disruption", "Διακοπή υπηρεσίας", "Ndërprerje shërbimi", "Disservizio")
        }
        if lateNight {
            return homeText("Last trains tonight", "Τελευταίοι συρμοί", "Trenat e fundit sonte", "Ultimi treni stasera")
        }
        if (5...10).contains(hour) {
            return homeText("Morning commute", "Πρωινή μετακίνηση", "Udhëtimi i mëngjesit", "Viaggio mattutino")
        }
        if (17...20).contains(hour) {
            return homeText("Evening return", "Βραδινή επιστροφή", "Kthimi i mbrëmjes", "Rientro serale")
        }
        return homeText("Your rail pulse", "Ο παλμός της διαδρομής", "Pulsi i udhëtimit", "Il tuo impulso ferroviario")
    }

    private func pulseContextTag(_ text: String, color: Color) -> some View {
        Text(text.uppercased())
            .font(.system(size: SyrmosTokens.Font.contextTagSize, weight: .bold))
            .tracking(0.8)
            .foregroundStyle(color)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(color.opacity(0.12))
            .clipShape(Capsule())
    }

    private func pulseAction(
        icon: String,
        label: String,
        color: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                Text(label).fontWeight(.semibold)
            }
            .font(.caption)
            .foregroundStyle(color)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(color.opacity(0.14))
            .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    private var livingMapStrip: some View {
        Button {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            selectedTab = .map
        } label: {
            VStack(spacing: 6) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(homeText("Living map", "Ζωντανός χάρτης", "Harta e gjallë", "Mappa viva"))
                            .font(.caption.weight(.bold))
                            .foregroundStyle(Color.syrmosPrimary)
                        if let nearby = locationService.nearbyStations.first {
                            Text(localizedStationName(nearby))
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    }
                    Spacer()
                    Text(homeText("\(liveTrainService.trains.count) live", "\(liveTrainService.trains.count) ζωντανά", "\(liveTrainService.trains.count) live", "\(liveTrainService.trains.count) live"))
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                    Image(systemName: "arrow.up.right")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(Color.syrmosPrimary)
                }

                HStack(spacing: 0) {
                    ForEach(0..<6, id: \.self) { index in
                        Circle()
                            .fill(index == 3 ? Color.syrmosPrimary : Color.syrmosSurface)
                            .frame(width: index == 3 ? 16 : 10, height: index == 3 ? 16 : 10)
                            .overlay(Circle().stroke(Color.syrmosPrimary, lineWidth: 2))
                        if index < 5 {
                            Rectangle()
                                .fill(Color.syrmosPrimary.opacity(0.7))
                                .frame(height: 4)
                        }
                    }
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity)
            .background(Color.syrmosSurface)
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(homeText("Open living map", "Άνοιγμα ζωντανού χάρτη", "Hap hartën e gjallë", "Apri la mappa viva"))
    }

    @ViewBuilder
    private var weatherContextSection: some View {
        if let snapshot = weather.snapshot {
            if snapshot.current.condition.isSevere || forceEmergencyPreview {
                EmergencyWeatherCard(
                    condition: forceEmergencyPreview ? .thunderstorm : snapshot.current.condition,
                    language: loc.language
                )
            } else if snapshot.current.condition.isWet {
                WeatherCard(snapshot: snapshot)
            }
        } else if forceEmergencyPreview {
            EmergencyWeatherCard(condition: .thunderstorm, language: loc.language)
        }
    }

    @ViewBuilder
    private var insightsStream: some View {
        let alerts = stasyService.announcements.filter { $0.category == .serviceAlert }
        let otherAnnouncements = stasyService.announcements.filter { $0.category != .serviceAlert }
        let visibleAlertCount = showAllInsights ? alerts.count : min(alerts.count, 2)
        let remainingSlots = showAllInsights ? Int.max : max(2 - visibleAlertCount, 0)

        if !alerts.isEmpty || !otherAnnouncements.isEmpty || !railNewsService.news.isEmpty || stasyService.serviceStatus != nil {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text(homeText("What matters now", "Τι έχει σημασία τώρα", "Çfarë ka rëndësi tani", "Cosa conta adesso"))
                        .font(.title3.weight(.bold))
                    Spacer()
                    let total = alerts.count + otherAnnouncements.count + railNewsService.news.count
                    if total > 2 {
                        Button(showAllInsights
                               ? homeText("Show less", "Λιγότερα", "Trego më pak", "Mostra meno")
                               : homeText("Show all", "Όλα", "Trego të gjitha", "Mostra tutto")) {
                            withAnimation(.easeOut(duration: 0.25)) { showAllInsights.toggle() }
                        }
                        .font(.caption.weight(.semibold))
                    }
                }

                ForEach(alerts.prefix(visibleAlertCount)) { alert in
                    AlertCard(announcement: alert, onReadMore: { webViewURL = $0 })
                }

                if alerts.isEmpty {
                    serviceStatusPill
                }

                ForEach(otherAnnouncements.prefix(showAllInsights ? otherAnnouncements.count : remainingSlots)) { item in
                    AlertCard(announcement: item, onReadMore: { webViewURL = $0 })
                }

                let used = visibleAlertCount + min(otherAnnouncements.count, remainingSlots)
                let newsLimit = showAllInsights ? railNewsService.news.count : max(2 - used, 0)
                ForEach(railNewsService.news.prefix(newsLimit)) { item in
                    NewsCard(item: item, language: loc.language)
                }
            }
        }
    }

    @ViewBuilder
    private var radialNearbySection: some View {
        if !locationService.hasPermission || locationService.nearbyStations.isEmpty {
            nearMeSection
        } else {
            let nearby = locationService.nearbyStations
            let selected = nearby.first(where: { $0.id == selectedNearbyId }) ?? nearby.first!
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text(homeText("Around you", "Γύρω σου", "Rreth teje", "Intorno a te"))
                        .font(.title3.weight(.bold))
                    Spacer()
                    Button(nearbyListMode
                           ? homeText("Radial", "Ακτινικά", "Radiale", "Radiale")
                           : homeText("List", "Λίστα", "Listë", "Elenco")) {
                        withAnimation(.easeOut(duration: 0.25)) { nearbyListMode.toggle() }
                    }
                    .font(.caption.weight(.semibold))
                }

                if nearbyListMode {
                    nearMeSection
                } else {
                    ZStack {
                        Circle()
                            .stroke(Color.secondary.opacity(0.18), lineWidth: 1)
                            .frame(width: 86, height: 86)
                        Circle()
                            .stroke(Color.secondary.opacity(0.18), lineWidth: 1)
                            .frame(width: 164, height: 164)
                        Circle()
                            .fill(Color.syrmosPrimary)
                            .frame(width: 14, height: 14)
                            .overlay(Circle().stroke(Color.syrmosPrimary.opacity(0.2), lineWidth: 10))

                        ForEach(Array(nearby.prefix(3).enumerated()), id: \.element.id) { index, item in
                            Button {
                                UIImpactFeedbackGenerator(style: .rigid).impactOccurred()
                                selectedNearbyId = item.id
                            } label: {
                                VStack(spacing: 3) {
                                    Circle()
                                        .fill(SyrmosData.lineColor(for: item.station.lineIds.first ?? "M3"))
                                        .frame(width: selected.id == item.id ? 18 : 14, height: selected.id == item.id ? 18 : 14)
                                    Text(localizedStationName(item))
                                        .font(.caption2.weight(.semibold))
                                        .lineLimit(1)
                                    Text(formatDistance(item.distanceMeters))
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                }
                                .padding(6)
                                .background(Color.syrmosSurface)
                                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                            }
                            .buttonStyle(.plain)
                            .offset(radialOffset(index))
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 210)
                    .background(Color.syrmosSurface)
                    .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))

                    NavigationLink {
                        NearbyStationDestination(node: selected.station)
                    } label: {
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text(localizedStationName(selected))
                                    .font(.headline)
                                Spacer()
                                Text(formatDistance(selected.distanceMeters))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                Image(systemName: "chevron.right")
                                    .font(.caption)
                            }
                            ForEach(departures(for: selected).prefix(2)) { departure in
                                Text("\(departure.lineId) · \(departure.minutesAwayDisplay(language: loc.language))")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .padding(14)
                        .background(Color.syrmosSurface)
                        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private func departures(for nearby: LocationService.NearbyStation) -> [Departure] {
        let node = nearby.station
        return node.lineIds.flatMap { lineId in
            let stationId = node.stationIdByLineId[lineId] ?? node.stationIds.first ?? node.id
            return ScheduleProjector.nextDepartures(for: stationId, lineIds: [lineId], limit: 2)
        }
        .sorted { $0.minutesAway < $1.minutesAway }
    }

    private func radialOffset(_ index: Int) -> CGSize {
        switch index {
        case 0: return CGSize(width: 100, height: -55)
        case 1: return CGSize(width: -92, height: 55)
        default: return CGSize(width: 80, height: 70)
        }
    }

    private func localizedStationName(_ nearby: LocationService.NearbyStation) -> String {
        loc.language == .greek
            ? nearby.station.nameEl
            : SyrmosData.localizedStationName(nearby.station.displayName, language: loc.language)
    }

    private func weatherPulseText(_ snapshot: WeatherSnapshot) -> String {
        let temperature = Int(snapshot.current.temperatureC.rounded())
        let impact = snapshot.current.condition.isWet
            ? homeText("Rain may affect outdoor platforms", "Η βροχή μπορεί να επηρεάσει τις υπαίθριες αποβάθρες", "Shiu mund të ndikojë platformat e jashtme", "La pioggia può influire sui binari all'aperto")
            : homeText("No weather impact on service", "Χωρίς επίδραση του καιρού", "Pa ndikim të motit në shërbim", "Nessun impatto meteo sul servizio")
        return "\(temperature)° · \(impact)"
    }

    private func homeText(_ en: String, _ el: String, _ sq: String, _ it: String) -> String {
        switch loc.language {
        case .greek: return el
        case .albanian: return sq
        case .italian: return it
        case .english: return en
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

    @ViewBuilder
    private var freshnessPill: some View {
        let isLive = freshnessStore.freshness == .live
        if isLive {
            SourceConfidenceChip(confidence: .live, language: loc.language)
        } else {
            OfflinePill(
                message: "\(loc[.runningOffline]) · \(loc[.predictedFromSchedule])"
            )
        }
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
        case .italian: return "Segui un treno"
        case .english: return "Track a train"
        }
    }

    @ViewBuilder
    private func answerHero(next: Departure?) -> some View {
        TimelineView(.periodic(from: .now, by: 1)) { timeline in
            VStack(alignment: .leading, spacing: 10) {
                Text(loc[.nextTrain].uppercased())
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundStyle(.secondary)

                if let next {
                    let secsAway = next.secondsAway(from: timeline.date)
                    let isImminent = secsAway <= 60
                    let countdownText = heroCountdownText(secondsAway: secsAway, language: loc.language)
                    let lineColor = SyrmosData.lineColor(for: next.lineId)
                    let countdownColor: Color = isImminent ? SyrmosTokens.arrivalImminent : (secsAway <= 300 ? SyrmosTokens.arrivalSoon : lineColor)

                    HStack(alignment: .center, spacing: 12) {
                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 8) {
                                LinePill(
                                    lineId: next.lineId,
                                    size: .small,
                                    disruptionSeverity: stasyService.lineDisruptions[next.lineId]
                                )
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
                        Text(countdownText)
                            .font(.title)
                            .fontWeight(.bold)
                            .foregroundStyle(countdownColor)
                            .modifier(HeroImminentPulse(active: isImminent))
                    }
                    let thenTimes = nearestUpcoming().dropFirst().prefix(2)
                        .filter { $0.minutesAway > next.minutesAway }
                        .map { $0.minutesAwayDisplay(language: loc.language) }
                    if !thenTimes.isEmpty {
                        let thenWord = loc.language == .greek ? "μετά" : loc.language == .albanian ? "pastaj" : loc.language == .italian ? "poi" : "then"
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
                                ? (loc.language == .greek ? "Παρακολουθείται" : loc.language == .albanian ? "Po ndiqet" : loc.language == .italian ? "Monitoraggio" : "Tracking")
                                : (loc.language == .greek ? "Παρακολούθηση" : loc.language == .albanian ? "Ndiq" : loc.language == .italian ? "Segui" : "Track"))
                                .fontWeight(.semibold)
                        }
                        .font(.caption)
                        .foregroundStyle(isTracked ? Color.syrmosOnSurfaceMuted : lineColor)
                        .padding(.horizontal, 12).padding(.vertical, 6)
                        .background((isTracked ? SyrmosTokens.offline : lineColor).opacity(0.14))
                        .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                    .disabled(isTracked)
                    .onChange(of: isImminent) { _, nowImminent in
                        if nowImminent {
                            let generator = UIImpactFeedbackGenerator(style: .heavy)
                            generator.impactOccurred()
                        }
                    }
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
            .accessibilityElement(children: .combine)
            .accessibilityLabel(next.map { d in
                let mins = d.minutesAway
                let toWord: String
                let unit: String
                switch loc.language {
                case .greek:
                    toWord = "προς"
                    unit = mins == 1 ? "λεπτό" : "λεπτά"
                case .albanian:
                    toWord = "drejt"
                    unit = mins == 1 ? "minutë" : "minuta"
                case .italian:
                    toWord = "verso"
                    unit = mins == 1 ? "minuto" : "minuti"
                default:
                    toWord = "to"
                    unit = mins == 1 ? "minute" : "minutes"
                }
                return "\(d.lineId) \(toWord) \(d.direction), \(mins) \(unit)"
            } ?? loc[.serviceOver])
        }
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
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(loc[.lastTrain]) \(last.lineId), \(loc[.leaveBy]) \(last.time)")
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
                        Text(loc.language == .greek ? "Κοντά μου" : loc.language == .albanian ? "Pranë meje" : loc.language == .italian ? "Vicino a me" : "Near me")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .foregroundStyle(.primary)
                        Text(loc.language == .greek ? "Ενεργοποιήστε την τοποθεσία για να δείτε κοντινούς σταθμούς" : loc.language == .albanian ? "Aktivizo vendndodhjen për të parë stacionet afër" : loc.language == .italian ? "Abilita la posizione per vedere le stazioni vicine" : "Enable location to see nearby stations")
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
                        Text(loc.language == .greek ? "Κοντά μου" : loc.language == .albanian ? "Pranë meje" : loc.language == .italian ? "Vicino a me" : "Near me")
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
                                        .overlay(alignment: .topTrailing) {
                                            LineDisruptionDot(severity: stasyService.lineDisruptions[lineId])
                                                .offset(x: 3, y: -3)
                                        }
                                }
                            }

                            VStack(alignment: .leading, spacing: 2) {
                                Text(
                                    loc.language == .greek
                                        ? nearby.station.nameEl
                                        : SyrmosData.localizedStationName(nearby.station.displayName, language: loc.language)
                                )
                                    .font(.subheadline)
                                    .fontWeight(.semibold)
                                    .foregroundStyle(.primary)
                                Text(nearby.station.lineIds.compactMap { SyrmosData.line(for: $0)?.localizedName(loc.language) }.joined(separator: ", "))
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
                    Text(loc.language == .greek ? "Ζωντανά τρένα" : loc.language == .albanian ? "Trenat aktiv" : loc.language == .italian ? "Treni in tempo reale" : "Live trains")
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
                            Text(
                                "\(SyrmosData.resolveStation(train.origin, en: train.originEn, language: loc.language)) "
                                    + "→ \(SyrmosData.resolveStation(train.destination, en: train.destinationEn, language: loc.language))"
                            )
                                .font(.subheadline)
                                .fontWeight(.medium)
                                .lineLimit(1)
                            HStack(spacing: 6) {
                                Text("#\(train.trainNumber)")
                                    .font(.caption2)
                                    .foregroundStyle(.tertiary)
                                if train.delayMinutes > 0 {
                                    Text(loc.language == .greek ? "+\(train.delayMinutes)′ καθυστέρηση" : loc.language == .albanian ? "+\(train.delayMinutes)′ vonesë" : loc.language == .italian ? "+\(train.delayMinutes)′ ritardo" : "+\(train.delayMinutes)′ delay")
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
                                webViewURL = url
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
                    webViewURL = url
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
                    Text(loc.language == .greek ? "Σιδηροδρομικα Νεα" : loc.language == .albanian ? "Lajme Hekurudhore" : loc.language == .italian ? "Notizie ferroviarie" : "Rail News")
                        .font(.title3)
                        .fontWeight(.semibold)
                    Spacer()
                }

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        ForEach(railNewsService.news.prefix(10)) { item in
                            NewsCard(item: item, language: loc.language)
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
                    : loc.language == .italian
                    ? "Servizio 24/7 oggi"
                    : "24/7 service today"
            }
        }
        if latest.isEmpty { return nil }
        return loc.language == .greek
            ? "Δρομολόγια έως \(latest)"
            : loc.language == .albanian
            ? "Trena deri në \(latest)"
            : loc.language == .italian
            ? "Treni fino alle \(latest)"
            : "Trains until \(latest)"
    }

    private func resolveStation(id: String) -> TransitStation? {
        if let match = SyrmosData.bundleStations.first(where: { $0.id == id }) {
            return match
        }
        for line in SyrmosData.lines {
            if let match = SyrmosData.stations(for: line.id).first(where: { $0.id == id }) {
                return match
            }
        }
        return nil
    }

}

// MARK: - Deep Link Navigation Wrappers

struct DeepLinkStation: Hashable {
    let id: String
}

struct DeepLinkLine: Hashable {
    let id: String
}

struct DeepLinkAlert: Hashable {
    let id: String
}

private enum HomeAnchor: Hashable {
    case weather
    case nearby
}

// MARK: - Hero countdown formatting (mirrors KMP HeroCountdown.kt)

private func heroCountdownText(secondsAway: Int, language: AppLanguage) -> String {
    if secondsAway <= 0 {
        switch language {
        case .greek: return "Τώρα"
        case .albanian: return "Tani"
        case .italian: return "Ora"
        default: return "Now"
        }
    }
    if secondsAway < 120 {
        let m = secondsAway / 60
        let s = secondsAway % 60
        return "\(m):\(String(format: "%02d", s))"
    }
    let minA: String
    let hA: String
    switch language {
    case .greek: minA = "λεπ"; hA = "ω"
    case .albanian: minA = "min"; hA = "o"
    case .italian: minA = "min"; hA = "h"
    default: minA = "min"; hA = "h"
    }
    if secondsAway < 3600 {
        let m = (secondsAway + 59) / 60
        return "\(m) \(minA)"
    }
    let h = secondsAway / 3600
    let m = (secondsAway % 3600) / 60
    if m == 0 { return "\(h)\(hA)" }
    return "\(h)\(hA) \(m)\(minA)"
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
                         : loc.language == .italian
                         ? "Questa stazione non e ancora disponibile."
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

// MARK: - In-App WebView

extension URL: @retroactive Identifiable {
    public var id: String { absoluteString }
}

struct InAppWebView: View {
    let url: URL
    @Environment(\.dismiss) private var dismiss
    @State private var isLoading = true
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        NavigationStack {
            ZStack {
                WebViewRepresentable(url: url, isLoading: $isLoading)
                if isLoading {
                    ProgressView()
                }
            }
            .navigationTitle("stasy.gr")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(loc.language == .greek ? "Τέλος" : loc.language == .albanian ? "U krye" : loc.language == .italian ? "Fine" : "Done") { dismiss() }
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
