import SwiftUI

enum RailPulseDestination: String, Identifiable, Hashable {
    case station
    case train
    case contribution
    case feed

    var id: String { rawValue }
}

private func railContributorLevel(for confirmed: Int) -> Int {
    max(1, confirmed / 100 + 1)
}

private func railContributorCallsign(_ level: Int, language: AppLanguage) -> String {
    switch min(max(level, 1), 10) {
    case 1: return pulseText(language, "Platform Pal", "Φιλος Αποβαθρας", "Miku i Platformes", "Amico di Banchina")
    case 2: return pulseText(language, "Signal Spotter", "Ανιχνευτης Σηματων", "Vezhgues Sinjalesh", "Osservatore Segnali")
    case 3: return pulseText(language, "Delay Detective", "Ντετεκτιβ Καθυστερησεων", "Detektivi i Vonesave", "Detective dei Ritardi")
    case 4: return pulseText(language, "Crowd Scout", "Ανιχνευτης Κοσμου", "Vezhgues Turme", "Esploratore Folla")
    case 5: return "Rail Reporter"
    case 6: return pulseText(language, "Station Guardian", "Φυλακας Σταθμου", "Mbrojtes Stacioni", "Custode di Stazione")
    case 7: return pulseText(language, "Track Whisperer", "Ψιθυριστης Γραμμων", "Peshperitesi i Shinave", "Sussurratore dei Binari")
    case 8: return pulseText(language, "Timetable Tamer", "Δαμαστης Δρομολογιων", "Zbutesi i Orareve", "Domatore di Orari")
    case 9: return pulseText(language, "Platform Legend", "Θρυλος Αποβαθρας", "Legjenda e Platformes", "Leggenda di Banchina")
    default: return pulseText(language, "Rail Oracle", "Σιδηροδρομικο Μαντειο", "Orakulli Hekurudhor", "Oracolo Ferroviario")
    }
}

@MainActor
final class RailPulseLocalStore: ObservableObject {
    static let shared = RailPulseLocalStore()

    @Published private(set) var confirmed: Int
    @Published private(set) var qualityPercent: Int
    @Published private(set) var thisWeek: Int

    private let defaults = UserDefaults(suiteName: "group.com.syrmosApp.ios") ?? .standard
    private let confirmedKey = "ichnos_v2_confirmed"
    private let qualityKey = "ichnos_v2_quality"
    private let weekKey = "ichnos_v2_week"

    private init() {
        confirmed = defaults.object(forKey: confirmedKey) as? Int ?? 0
        qualityPercent = defaults.object(forKey: qualityKey) as? Int ?? 0
        thisWeek = defaults.object(forKey: weekKey) as? Int ?? 0
    }

    func recordContribution() {
        confirmed += 1
        thisWeek += 1
        persist()
    }

    func undoContribution() {
        confirmed = max(0, confirmed - 1)
        thisWeek = max(0, thisWeek - 1)
        persist()
    }

    private func persist() {
        defaults.set(confirmed, forKey: confirmedKey)
        defaults.set(qualityPercent, forKey: qualityKey)
        defaults.set(thisWeek, forKey: weekKey)
    }
}

struct RailPulseStationDetailView: View {
    let language: AppLanguage
    let onReport: (RailPulseReportContext) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var summary: IchnosCommunitySummary?
    @State private var didLoad = false

    private var context: RailPulseReportContext {
        RailPulseReportContext(
            scopeId: "A1_AIR",
            title: pulseText(language, "Airport", "Αεροδρομιο", "Aeroporti", "Aeroporto"),
            subtitle: pulseText(language, "Athens International Airport, M3", "Διεθνες Αεροδρομιο Αθηνων, M3", "Aeroporti Nderkombetar i Athines, M3", "Aeroporto Internazionale di Atene, M3")
        )
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                pulseBackHeader(title: context.title, subtitle: context.subtitle, onBack: { dismiss() })
                IchnosSummaryPanel(language: language, summary: summary, didLoad: didLoad, onReport: { onReport(context) })
                pulseSectionTitle(pulseText(language, "Current community reports", "Τρεχουσες αναφορες κοινοτητας", "Raportet aktuale te komunitetit", "Segnalazioni attuali della comunita"))
                communityIssueList(language: language, summary: summary, didLoad: didLoad)
                communityNotice(language)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 110)
        }
        .background(Color.syrmosBackground)
        .toolbar(.hidden, for: .navigationBar)
        .simultaneousGesture(pulseSwipeBackGesture { dismiss() })
        .task {
            summary = await IchnosCommunityService.shared.fetchSummary(scopeId: context.scopeId)
            didLoad = true
        }
    }
}

struct RailPulseTrainDetailView: View {
    let language: AppLanguage
    let onReport: (RailPulseReportContext) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var summary: IchnosCommunitySummary?
    @State private var didLoad = false

    private var context: RailPulseReportContext {
        RailPulseReportContext(
            scopeId: "train_1635",
            title: pulseText(language, "Train 1635", "Τρενο 1635", "Treni 1635", "Treno 1635"),
            subtitle: pulseText(language, "Athens to Kalambaka", "Αθηνα προς Καλαμπακα", "Athine per Kalambaka", "Atene verso Kalambaka")
        )
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                pulseBackHeader(title: context.title, subtitle: context.subtitle, onBack: { dismiss() })
                IchnosSummaryPanel(language: language, summary: summary, didLoad: didLoad, onReport: { onReport(context) })
                pulseSectionTitle(pulseText(language, "Current community reports", "Τρεχουσες αναφορες κοινοτητας", "Raportet aktuale te komunitetit", "Segnalazioni attuali della comunita"))
                communityIssueList(language: language, summary: summary, didLoad: didLoad)
                communityNotice(language)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 110)
        }
        .background(Color.syrmosBackground)
        .toolbar(.hidden, for: .navigationBar)
        .simultaneousGesture(pulseSwipeBackGesture { dismiss() })
        .task {
            summary = await IchnosCommunityService.shared.fetchSummary(scopeId: context.scopeId)
            didLoad = true
        }
    }
}

struct RailPulseAllActivityView: View {
    let language: AppLanguage
    @Environment(\.dismiss) private var dismiss
    @State private var summary: IchnosCommunitySummary?
    @State private var didLoad = false
    @State private var selectedHistoryPeriod: IchnosHistoryPeriod = .day
    @State private var history: IchnosCommunityHistory?
    @State private var didLoadHistory = false

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                pulseBackHeader(
                    title: pulseText(language, "Ichnos activity", "Δραστηριοτητα Ichnos", "Aktiviteti Ichnos", "Attivita Ichnos"),
                    subtitle: pulseText(language, "Across Greece", "Σε ολη την Ελλαδα", "Ne gjithe Greqine", "In tutta la Grecia"),
                    onBack: { dismiss() }
                )
                communityNotice(language)
                communityIssueList(language: language, summary: summary, didLoad: didLoad)
                pulseSectionTitle(pulseText(language, "Greek railway history", "Ιστορικο ελληνικων σιδηροδρομων", "Historia e hekurudhave greke", "Storico ferroviario greco"))
                Text(pulseText(language, "Actual anonymous user reports are kept as daily totals, then grouped by month or year. Estimated journeys are never added to this history.", "Οι πραγματικες ανωνυμες αναφορες χρηστων κρατουνται ως ημερησια συνολα και ομαδοποιουνται ανα μηνα η ετος. Οι εκτιμωμενες διαδρομες δεν προστιθενται ποτε σε αυτο το ιστορικο.", "Raportet reale anonime te perdoruesve ruhen si totale ditore dhe grupohen sipas muajit ose vitit. Udhetimet e vleresuara nuk shtohen kurre ne kete histori.", "Le segnalazioni anonime reali degli utenti vengono conservate come totali giornalieri e raggruppate per mese o anno. I viaggi stimati non vengono mai aggiunti allo storico."))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Picker("", selection: $selectedHistoryPeriod) {
                    ForEach(IchnosHistoryPeriod.allCases) { period in
                        Text(period.title(language)).tag(period)
                    }
                }
                .pickerStyle(.segmented)
                .accessibilityLabel(pulseText(language, "History period", "Περιοδος ιστορικου", "Periudha e historise", "Periodo storico"))
                IchnosHistoryContent(language: language, history: history, didLoad: didLoadHistory)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 110)
        }
        .background(Color.syrmosBackground)
        .toolbar(.hidden, for: .navigationBar)
        .simultaneousGesture(pulseSwipeBackGesture { dismiss() })
        .task {
            summary = await IchnosCommunityService.shared.fetchSummary()
            didLoad = true
        }
        .task(id: selectedHistoryPeriod) {
            didLoadHistory = false
            history = await IchnosCommunityService.shared.fetchHistory(
                period: selectedHistoryPeriod.rawValue,
                limit: selectedHistoryPeriod.limit
            )
            guard !Task.isCancelled else { return }
            didLoadHistory = true
        }
    }
}

private enum IchnosHistoryPeriod: String, CaseIterable, Identifiable {
    case day
    case month
    case year

    var id: String { rawValue }
    var limit: Int {
        switch self {
        case .day: return 366
        case .month: return 120
        case .year: return 50
        }
    }

    func title(_ language: AppLanguage) -> String {
        switch self {
        case .day: return pulseText(language, "Days", "Ημερες", "Dite", "Giorni")
        case .month: return pulseText(language, "Months", "Μηνες", "Muaj", "Mesi")
        case .year: return pulseText(language, "Years", "Ετη", "Vite", "Anni")
        }
    }
}

private struct IchnosHistoryContent: View {
    let language: AppLanguage
    let history: IchnosCommunityHistory?
    let didLoad: Bool

    var body: some View {
        if let history, !history.buckets.isEmpty {
            let total = history.buckets.reduce(0) { $0 + $1.totalReports }
            let positive = history.buckets.reduce(0) { $0 + $1.positiveReports }
            let issues = history.buckets.reduce(0) { $0 + $1.issueReports }
            HStack(spacing: 10) {
                pulseMetric(pulseText(language, "REPORTS", "ΑΝΑΦΟΡΕΣ", "RAPORTE", "SEGNALAZIONI"), total.formatted(), .primary)
                pulseMetric(pulseText(language, "GOOD", "ΚΑΛΑ", "MIRE", "BENE"), positive.formatted(), SyrmosTokens.live)
                pulseMetric(pulseText(language, "ISSUES", "ΠΡΟΒΛΗΜΑΤΑ", "PROBLEME", "PROBLEMI"), issues.formatted(), issues > 0 ? SyrmosTokens.disruption : .secondary)
            }
            ForEach(Array(history.buckets.reversed())) { bucket in
                IchnosHistoryBucketCard(language: language, bucket: bucket)
            }
            Text(pulseText(language, "Only anonymous aggregate counts are permanent. Individual reports are deleted within seven days.", "Μονο τα ανωνυμα συγκεντρωτικα συνολα παραμενουν μονιμα. Οι μεμονωμενες αναφορες διαγραφονται εντος επτα ημερων.", "Vetem totalet anonime te grumbulluara ruhen pergjithmone. Raportet individuale fshihen brenda shtate ditesh.", "Solo i conteggi aggregati anonimi restano permanenti. Le singole segnalazioni vengono eliminate entro sette giorni."))
                .font(.caption2)
                .foregroundStyle(.secondary)
                .padding(.horizontal, 4)
        } else if let history, history.buckets.isEmpty {
            VStack(spacing: 8) {
                Image(systemName: "calendar.badge.clock").font(.title2).foregroundStyle(.secondary)
                Text(pulseText(language, "No reports recorded for this period yet", "Δεν εχουν καταγραφει αναφορες για αυτη την περιοδο", "Ende nuk ka raporte per kete periudhe", "Nessuna segnalazione registrata per questo periodo"))
                    .font(.subheadline.weight(.semibold))
                    .multilineTextAlignment(.center)
                Text(pulseText(language, "History starts with accepted Ichnos reports. It never invents past numbers.", "Το ιστορικο ξεκινα με αποδεκτες αναφορες Ichnos. Δεν επινοει ποτε παλιους αριθμους.", "Historia fillon me raportet e pranuara Ichnos. Nuk shpik kurre numra te kaluar.", "Lo storico inizia con le segnalazioni Ichnos accettate. Non inventa mai numeri passati."))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(24)
            .background(Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        } else if didLoad {
            VStack(spacing: 8) {
                Image(systemName: "wifi.exclamationmark").font(.title2).foregroundStyle(SyrmosTokens.warning)
                Text(pulseText(language, "History is temporarily unavailable", "Το ιστορικο δεν ειναι προσωρινα διαθεσιμο", "Historia nuk eshte perkohesisht e disponueshme", "Lo storico non e temporaneamente disponibile"))
                    .font(.subheadline.weight(.semibold))
                Text(pulseText(language, "Check your connection and try again.", "Ελεγξε τη συνδεση σου και προσπαθησε ξανα.", "Kontrollo lidhjen dhe provo perseri.", "Controlla la connessione e riprova."))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity)
            .padding(24)
            .background(Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        } else {
            ForEach(0..<3, id: \.self) { _ in
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(Color.syrmosSurface)
                    .frame(height: 116)
                    .redacted(reason: .placeholder)
            }
        }
    }
}

private struct IchnosHistoryBucketCard: View {
    let language: AppLanguage
    let bucket: IchnosHistoryBucket

    private var positiveRatio: Double {
        guard bucket.totalReports > 0 else { return 0 }
        return Double(bucket.positiveReports) / Double(bucket.totalReports)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline) {
                Text(ichnosHistoryPeriodLabel(bucket.period, language: language)).font(.headline)
                Spacer()
                Text("\(bucket.totalReports.formatted()) \(pulseText(language, "reports", "αναφορες", "raporte", "segnalazioni"))")
                    .font(.caption.bold())
                    .foregroundStyle(.secondary)
            }
            GeometryReader { geometry in
                HStack(spacing: 2) {
                    Rectangle()
                        .fill(SyrmosTokens.live)
                        .frame(width: max(0, geometry.size.width * positiveRatio - 1))
                    Rectangle()
                        .fill(SyrmosTokens.disruption)
                }
            }
            .frame(height: 8)
            .clipShape(Capsule())
            HStack {
                Label("\(bucket.positiveReports) \(pulseText(language, "good", "καλα", "mire", "bene"))", systemImage: "checkmark.circle.fill")
                    .foregroundStyle(SyrmosTokens.live)
                Spacer()
                Label("\(bucket.issueReports) \(pulseText(language, "issues", "προβληματα", "probleme", "problemi"))", systemImage: "exclamationmark.triangle.fill")
                    .foregroundStyle(bucket.issueReports > 0 ? SyrmosTokens.disruption : .secondary)
            }
            .font(.caption.bold())
            let breakdown = ichnosHistoryBreakdown(bucket.counts, language: language)
            if !breakdown.isEmpty {
                Text(breakdown).font(.caption2).foregroundStyle(.secondary)
            }
        }
        .padding(16)
        .background(Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .shadow(color: .black.opacity(0.06), radius: 6, y: 3)
        .accessibilityElement(children: .combine)
    }
}

private func ichnosHistoryPeriodLabel(_ value: String, language: AppLanguage) -> String {
    let formats = ["yyyy-MM-dd", "yyyy-MM", "yyyy"]
    let locale = Locale(identifier: language == .greek ? "el_GR" : language == .albanian ? "sq_AL" : language == .italian ? "it_IT" : "en_US")
    for format in formats {
        let parser = DateFormatter()
        parser.locale = Locale(identifier: "en_US_POSIX")
        parser.dateFormat = format
        guard let date = parser.date(from: value) else { continue }
        let formatter = DateFormatter()
        formatter.locale = locale
        formatter.dateFormat = format == "yyyy-MM-dd" ? "d MMM yyyy" : (format == "yyyy-MM" ? "LLLL yyyy" : "yyyy")
        return formatter.string(from: date)
    }
    return value
}

private func ichnosHistoryBreakdown(_ counts: [String: Int], language: AppLanguage) -> String {
    let order = ["normal", "clean", "delayed", "crowded", "stopped", "too_hot", "access", "facilities", "safety", "other"]
    return order.compactMap { signal in
        guard let count = counts[signal], count > 0 else { return nil }
        let label: String
        switch signal {
        case "normal": label = pulseText(language, "OK", "Καλα", "Ne rregull", "OK")
        case "clean": label = pulseText(language, "clean", "καθαρα", "paster", "pulito")
        case "delayed": label = pulseText(language, "delayed", "καθυστερηση", "vonese", "ritardo")
        case "crowded": label = pulseText(language, "crowded", "κοσμος", "plot", "affollato")
        case "stopped": label = pulseText(language, "stopped", "διακοπη", "ndaluar", "fermo")
        case "too_hot": label = pulseText(language, "too hot", "πολυ ζεστη", "shume nxehte", "troppo caldo")
        case "access": label = pulseText(language, "access", "προσβαση", "akses", "accesso")
        case "facilities": label = pulseText(language, "facilities", "παροχες", "sherbime", "servizi")
        case "safety": label = pulseText(language, "safety", "ασφαλεια", "siguri", "sicurezza")
        default: label = pulseText(language, "other", "αλλο", "tjeter", "altro")
        }
        return "\(label) \(count)"
    }.joined(separator: " · ")
}

struct RailPulseContributionView: View {
    let language: AppLanguage
    @ObservedObject private var store = RailPulseLocalStore.shared
    @Environment(\.dismiss) private var dismiss
    @State private var networkSummary: IchnosCommunitySummary?

    private var level: Int { railContributorLevel(for: store.confirmed) }
    private var currentCallsign: String { railContributorCallsign(level, language: language) }
    private var nextCallsign: String { railContributorCallsign(level + 1, language: language) }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 14) {
                profileHeader
                HStack(spacing: 10) {
                    pulseMetric(pulseText(language, "CONFIRMED", "ΕΠΙΒΕΒΑΙΩΜΕΝΑ", "KONFIRMUAR", "CONFERMATI"), "\(store.confirmed)", .primary)
                    pulseMetric(pulseText(language, "QUALITY", "ΠΟΙΟΤΗΤΑ", "CILESIA", "QUALITA"), store.qualityPercent > 0 ? "\(store.qualityPercent)%" : "-", SyrmosTokens.live)
                    pulseMetric(pulseText(language, "THIS WEEK", "ΑΥΤΗ ΤΗΝ ΕΒΔΟΜΑΔΑ", "KETE JAVE", "QUESTA SETTIMANA"), "\(store.thisWeek)", SyrmosTokens.suburban)
                }
                pulseSectionTitle(pulseText(language, "Contributor milestones", "Οροσημα συνεισφορεα", "Arritjet e kontribuesit", "Traguardi del collaboratore"))
                HStack(spacing: 8) {
                    pulseBadge("✓", pulseText(language, "First\nReport", "Πρωτη\nΑναφορα", "Raporti\ni pare", "Prima\nsegnalazione"), unlocked: store.confirmed >= 1)
                    pulseBadge("◉", pulseText(language, "Live\nReporter", "Ζωντανος\nReporter", "Raportues\nLive", "Reporter\nLive"), unlocked: store.confirmed >= 10)
                    pulseBadge("★", pulseText(language, "Station\nGuardian", "Φυλακας\nΣταθμου", "Mbrojtes\nStacioni", "Custode\nStazione"), unlocked: store.confirmed >= 50)
                    pulseBadge("100", pulseText(language, "100\nReports", "100\nΑναφορες", "100\nRaporte", "100\nReport"), unlocked: store.confirmed >= 100)
                }
                pulseSectionTitle(pulseText(language, "Weekly community activity", "Εβδομαδιαια δραστηριοτητα κοινοτητας", "Aktiviteti javor i komunitetit", "Attivita settimanale della comunita"))
                weeklyActivity
                VStack(alignment: .leading, spacing: 4) {
                    Text(pulseText(language, "Private by construction", "Ιδιωτικο απο τον σχεδιασμο", "Privat nga ndertimi", "Privato per costruzione")).font(.subheadline.weight(.semibold))
                    Text(pulseText(language, "Local progress stays on this device. Individual reports contain no account, device ID, or location and are deleted within seven days. Only anonymous daily totals remain for railway history.", "Η τοπικη προοδος μενει στη συσκευη. Οι μεμονωμενες αναφορες δεν περιεχουν λογαριασμο, αναγνωριστικο συσκευης η τοποθεσια και διαγραφονται εντος επτα ημερων. Μονο τα ανωνυμα ημερησια συνολα παραμενουν για το σιδηροδρομικο ιστορικο.", "Progresi lokal mbetet ne pajisje. Raportet individuale nuk permbajne llogari, ID pajisjeje ose vendndodhje dhe fshihen brenda shtate ditesh. Vetem totalet anonime ditore mbeten per historine hekurudhore.", "I progressi locali restano sul dispositivo. Le singole segnalazioni non contengono account, ID del dispositivo o posizione e vengono eliminate entro sette giorni. Solo i totali giornalieri anonimi restano per lo storico ferroviario.")).font(.caption).foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading).padding(16)
                .background(SyrmosTokens.suburban.opacity(0.10), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 110)
        }
        .background(Color.syrmosBackground)
        .safeAreaInset(edge: .top, spacing: 0) { Color.clear.frame(height: 8) }
        .toolbar(.hidden, for: .navigationBar)
        .simultaneousGesture(pulseSwipeBackGesture { dismiss() })
        .task { networkSummary = await IchnosCommunityService.shared.fetchSummary() }
    }

    private var profileHeader: some View {
        VStack(alignment: .leading, spacing: 14) {
            pulseBackHeader(title: pulseText(language, "Local contribution", "Τοπικη συνεισφορα", "Kontributi lokal", "Contributo locale"), subtitle: "", onBack: { dismiss() }, foreground: .white)
            HStack(spacing: 14) {
                Image(systemName: "tram.fill")
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(.white)
                    .frame(width: 56, height: 56)
                    .background(.white.opacity(0.16), in: Circle())
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 3) {
                    Text(currentCallsign).font(.title2.bold())
                    Text("\(pulseText(language, "Local rail contributor", "Τοπικος συνεισφορεας rail", "Kontribues lokal rail", "Collaboratore rail locale")), \(pulseText(language, "Level", "Επιπεδο", "Niveli", "Livello")) \(level)").font(.caption.bold())
                    Text(pulseText(language, "Progress stored only on this device", "Η προοδος αποθηκευεται μονο στη συσκευη", "Progresi ruhet vetem ne kete pajisje", "Progressi salvati solo su questo dispositivo")).font(.caption2.bold()).padding(.horizontal, 12).padding(.vertical, 6).background(.white.opacity(0.17), in: Capsule())
                }
            }
            Text(pulseText(language, "NEXT LEVEL", "ΕΠΟΜΕΝΟ ΕΠΙΠΕΔΟ", "NIVELI TJETER", "PROSSIMO LIVELLO")).font(.caption2.bold())
            ProgressView(value: Double(store.confirmed % 100), total: 100).tint(Color(hex: 0x63E6A6))
            HStack {
                Text("\(store.confirmed) \(pulseText(language, "confirmed contributions", "επιβεβαιωμενες συνεισφορες", "kontribute te konfirmuara", "contributi confermati"))")
                Spacer()
                Text("\(100 - store.confirmed % 100) \(pulseText(language, "to", "για", "deri ne", "a")) \(nextCallsign)")
            }.font(.caption2.bold())
        }
        .foregroundStyle(.white)
        .padding(16)
        .background(LinearGradient(colors: [Color(hex: 0x5D2EA8), Color(hex: 0x343F91)], startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: 30, style: .continuous))
    }

    private var weeklyActivity: some View {
        let weeklyTotal = networkSummary?.totalReportsThisWeek ?? 0
        return VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(pulseText(language, "Anonymous reports across Greece", "Ανωνυμες αναφορες σε ολη την Ελλαδα", "Raporte anonime ne Greqi", "Segnalazioni anonime in tutta la Grecia")).font(.caption)
                Spacer()
                Text(pulseText(language, "Last 7 days", "Τελευταιες 7 ημερες", "7 ditet e fundit", "Ultimi 7 giorni")).font(.caption2.bold()).foregroundStyle(SyrmosTokens.live)
            }
            Text(weeklyTotal.formatted()).font(.title.bold())
            Text(pulseText(language, "Your local contribution: \(store.thisWeek)", "Η τοπικη συνεισφορα σου: \(store.thisWeek)", "Kontributi yt lokal: \(store.thisWeek)", "Il tuo contributo locale: \(store.thisWeek)")).font(.caption.weight(.semibold))
            Text(pulseText(language, "This total comes from accepted anonymous reports, not estimated journeys.", "Αυτο το συνολο προερχεται απο αποδεκτες ανωνυμες αναφορες, οχι εκτιμησεις διαδρομων.", "Ky total vjen nga raporte anonime te pranuara, jo nga udhetime te vleresuara.", "Questo totale proviene da segnalazioni anonime accettate, non da viaggi stimati.")).font(.caption2).foregroundStyle(.secondary)
        }
        .padding(18)
        .background(Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .shadow(color: .black.opacity(0.07), radius: 7, y: 4)
    }
}

private struct IchnosSummaryPanel: View {
    let language: AppLanguage
    let summary: IchnosCommunitySummary?
    let didLoad: Bool
    let onReport: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            pulseCircle(symbol, background: color.opacity(0.12), foreground: color)
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.subheadline.weight(.semibold))
                Text(detail).font(.caption).foregroundStyle(.secondary)
                if let normalCount = summary?.normalReportCount, normalCount > 0 {
                    Text("\(normalCount) \(pulseText(language, "anonymous everything-OK reports", "ανωνυμες αναφορες οτι ολα ειναι καλα", "raporte anonime se gjithcka eshte ne rregull", "segnalazioni anonime di tutto regolare"))")
                        .font(.caption2.bold()).foregroundStyle(SyrmosTokens.live)
                }
            }
            Spacer(minLength: 4)
            Button(pulseText(language, "Report", "Αναφορα", "Raporto", "Segnala"), action: onReport)
                .buttonStyle(.borderedProminent)
                .tint(Color.syrmosAdaptive(light: SyrmosTokens.onSurface, dark: SyrmosTokens.Dark.onSurface))
                .foregroundStyle(Color.syrmosAdaptive(light: SyrmosTokens.surface, dark: SyrmosTokens.Dark.surface))
        }
        .padding(14)
        .background(Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .shadow(color: .black.opacity(0.07), radius: 7, y: 4)
    }

    private var symbol: String { summary?.hasIssues == true ? "!" : (summary == nil ? "?" : "✓") }
    private var color: Color { summary?.hasIssues == true ? SyrmosTokens.disruption : (summary == nil ? SyrmosTokens.warning : SyrmosTokens.live) }
    private var title: String {
        guard let summary else {
            return didLoad
                ? pulseText(language, "Community status unavailable", "Η κατασταση κοινοτητας δεν ειναι διαθεσιμη", "Gjendja e komunitetit nuk eshte e disponueshme", "Stato della comunita non disponibile")
                : pulseText(language, "Loading community status", "Φορτωση καταστασης κοινοτητας", "Po ngarkohet gjendja e komunitetit", "Caricamento stato della comunita")
        }
        return summary.hasIssues
            ? pulseText(language, "Active community issues", "Ενεργα προβληματα κοινοτητας", "Probleme aktive te komunitetit", "Problemi attivi della comunita")
            : pulseText(language, "No active issues reported", "Δεν αναφερθηκαν ενεργα προβληματα", "Nuk ka probleme aktive te raportuara", "Nessun problema attivo segnalato")
    }
    private var detail: String {
        guard let summary else {
            return didLoad
                ? pulseText(language, "Check your connection and try again", "Ελεγξε τη συνδεση και προσπαθησε ξανα", "Kontrollo lidhjen dhe provo perseri", "Controlla la connessione e riprova")
                : pulseText(language, "Anonymous reports from the last two hours", "Ανωνυμες αναφορες των τελευταιων δυο ωρων", "Raporte anonime nga dy oret e fundit", "Segnalazioni anonime delle ultime due ore")
        }
        if summary.hasIssues {
            return pulseText(language, "Estimated normal-journey counts are hidden while an issue is active.", "Οι εκτιμησεις κανονικων διαδρομων κρυβονται οσο υπαρχει προβλημα.", "Vleresimet e udhetimeve normale fshihen kur ka problem aktiv.", "Le stime dei viaggi regolari sono nascoste mentre un problema e attivo.")
        }
        let estimate = summary.estimatedJourneysToday ?? 0
        return pulseText(language, "Estimated \(estimate) journeys so far today. Estimate, not user confirmations.", "Εκτιμωμενες \(estimate) διαδρομες σημερα. Εκτιμηση, οχι επιβεβαιωσεις χρηστων.", "Rreth \(estimate) udhetime sot. Vleresim, jo konfirmime perdoruesish.", "Circa \(estimate) viaggi oggi. Stima, non conferme degli utenti.")
    }
}

@ViewBuilder
private func communityIssueList(language: AppLanguage, summary: IchnosCommunitySummary?, didLoad: Bool) -> some View {
    if let summary, summary.hasIssues {
        ForEach(summary.issues) { issue in
            pulseActivityRow(
                symbol: "!",
                title: issue.scopeLabel,
                detail: ichnosIssueLabel(issue, language: language),
                status: "\(issue.count) \(pulseText(language, "reports", "αναφορες", "raporte", "segnalazioni"))",
                color: ichnosIssueColor(issue.signal)
            )
        }
    } else if let summary {
        pulseActivityRow(
            symbol: "✓",
            title: pulseText(language, "Nothing active to show", "Δεν υπαρχει κατι ενεργο", "Nuk ka asgje aktive per te shfaqur", "Nessun elemento attivo da mostrare"),
            detail: summary.normalReportCount > 0
                ? pulseText(language, "\(summary.normalReportCount) anonymous everything-OK reports remain active", "\(summary.normalReportCount) ανωνυμες αναφορες οτι ολα ειναι καλα παραμενουν ενεργες", "\(summary.normalReportCount) raporte anonime se gjithcka eshte ne rregull jane aktive", "\(summary.normalReportCount) segnalazioni anonime di tutto regolare sono attive")
                : pulseText(language, "Be the first to report what you can see", "Γινε ο πρωτος που θα αναφερει τι βλεπει", "Raporto i pari ate qe sheh", "Segnala per primo cio che vedi"),
            status: pulseText(language, "Clear", "Καθαρα", "Ne rregull", "Regolare"),
            color: SyrmosTokens.live
        )
    } else {
        pulseActivityRow(
            symbol: didLoad ? "!" : "...",
            title: didLoad ? pulseText(language, "Unable to load reports", "Αδυνατη η φορτωση αναφορων", "Raportet nuk mund te ngarkohen", "Impossibile caricare le segnalazioni") : pulseText(language, "Loading reports", "Φορτωση αναφορων", "Po ngarkohen raportet", "Caricamento segnalazioni"),
            detail: didLoad ? pulseText(language, "Check your connection and reopen this screen", "Ελεγξε τη συνδεση και ανοιξε ξανα την οθονη", "Kontrollo lidhjen dhe rihap kete ekran", "Controlla la connessione e riapri questa schermata") : pulseText(language, "Only anonymous reports from the last two hours are shown", "Εμφανιζονται μονο ανωνυμες αναφορες των τελευταιων δυο ωρων", "Shfaqen vetem raporte anonime nga dy oret e fundit", "Sono mostrate solo segnalazioni anonime delle ultime due ore"),
            status: didLoad ? pulseText(language, "Offline", "Εκτος συνδεσης", "Jashte linje", "Offline") : pulseText(language, "Loading", "Φορτωση", "Ngarkim", "Caricamento"),
            color: SyrmosTokens.warning
        )
    }
}

@MainActor
private func pulseBackHeader(title: String, subtitle: String, onBack: @escaping () -> Void, foreground: Color = .primary) -> some View {
    HStack(spacing: 8) {
        Button(action: onBack) { Image(systemName: "chevron.left").font(.headline) }.buttonStyle(.plain)
        VStack(alignment: .leading, spacing: 1) {
            Text(title).font(.title2.bold())
            if !subtitle.isEmpty { Text(subtitle).font(.caption) }
        }
        Spacer()
    }
    .foregroundStyle(foreground)
    .padding(.top, 10)
}

private func pulseSwipeBackGesture(_ action: @escaping () -> Void) -> some Gesture {
    DragGesture(minimumDistance: 18, coordinateSpace: .global)
        .onEnded { value in
            let horizontalDistance = value.translation.width
            let verticalDistance = abs(value.translation.height)
            guard value.startLocation.x <= 36, horizontalDistance >= 80, horizontalDistance > verticalDistance * 1.4 else { return }
            action()
        }
}

private func pulseActivityRow(symbol: String, title: String, detail: String, status: String, color: Color) -> some View {
    HStack(spacing: 12) {
        pulseCircle(symbol, background: color.opacity(0.12), foreground: color)
        VStack(alignment: .leading, spacing: 3) {
            Text(title).font(.subheadline.weight(.medium))
            Text(detail).font(.caption).foregroundStyle(.secondary)
        }
        Spacer(minLength: 4)
        Text(status).font(.caption2.bold()).foregroundStyle(color)
    }
    .padding(14)
    .background(Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    .shadow(color: .black.opacity(0.06), radius: 6, y: 3)
}

private func pulseCircle(_ symbol: String, background: Color, foreground: Color) -> some View {
    Text(symbol).font(.subheadline.bold()).foregroundStyle(foreground)
        .frame(width: 46, height: 46).background(background, in: Circle())
}

private func pulseSectionTitle(_ title: String) -> some View {
    Text(title).font(.headline).padding(.top, 7)
}

private func communityNotice(_ language: AppLanguage) -> some View {
    Text(pulseText(language, "Community reports are not official operator notices.", "Οι αναφορες κοινοτητας δεν ειναι επισημες ανακοινωσεις φορεα.", "Raportet e komunitetit nuk jane njoftime zyrtare te operatorit.", "Le segnalazioni della comunita non sono avvisi ufficiali."))
        .font(.caption2.weight(.semibold)).foregroundStyle(SyrmosTokens.warning)
        .frame(maxWidth: .infinity).padding(12)
        .background(SyrmosTokens.warning.opacity(0.12), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
}

private func pulseMetric(_ label: String, _ value: String, _ color: Color) -> some View {
    VStack(alignment: .leading, spacing: 6) {
        Text(label).font(.system(size: 9)).foregroundStyle(.secondary).lineLimit(1)
        Text(value).font(.title2.bold()).foregroundStyle(color)
    }
    .frame(maxWidth: .infinity, alignment: .leading).padding(13)
    .background(Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    .shadow(color: .black.opacity(0.06), radius: 6, y: 3)
}

private func pulseBadge(_ symbol: String, _ label: String, unlocked: Bool) -> some View {
    VStack(spacing: 7) {
        pulseCircle(unlocked ? symbol : "lock", background: SyrmosTokens.suburban.opacity(0.10), foreground: unlocked ? .primary : .secondary)
        Text(label).font(.system(size: 9, weight: .semibold)).multilineTextAlignment(.center)
    }
    .opacity(unlocked ? 1 : 0.55)
    .frame(maxWidth: .infinity).padding(.vertical, 12)
    .background(Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    .shadow(color: .black.opacity(0.06), radius: 6, y: 3)
}

#Preview("Station Ichnos") {
    NavigationStack { RailPulseStationDetailView(language: .english) { _ in } }
}

#Preview("Train Ichnos") {
    NavigationStack { RailPulseTrainDetailView(language: .english) { _ in } }
}

#Preview("Local contribution") {
    NavigationStack { RailPulseContributionView(language: .english) }
}
