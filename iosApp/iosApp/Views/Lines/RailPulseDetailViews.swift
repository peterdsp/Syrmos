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
    case 1: return pulseText(language, "Platform Pal", "Φίλος Αποβάθρας", "Miku i Platformës", "Amico di Banchina")
    case 2: return pulseText(language, "Signal Spotter", "Ανιχνευτής Σημάτων", "Vezhgues Sinjalesh", "Osservatore Segnali")
    case 3: return pulseText(language, "Delay Detective", "Ντετέκτιβ Καθυστερήσεων", "Detektivi i Vonesave", "Detective dei Ritardi")
    case 4: return pulseText(language, "Crowd Scout", "Ανιχνευτής Κόσμου", "Vezhgues Turme", "Esploratore Folla")
    case 5: return "Rail Reporter"
    case 6: return pulseText(language, "Station Guardian", "Φύλακας Σταθμού", "Mbrojtës Stacioni", "Custode di Stazione")
    case 7: return pulseText(language, "Track Whisperer", "Ψιθυριστής Γραμμών", "Pëshpëritësi i Shinave", "Sussurratore dei Binari")
    case 8: return pulseText(language, "Timetable Tamer", "Δαμαστής Δρομολογίων", "Zbutësi i Orareve", "Domatore di Orari")
    case 9: return pulseText(language, "Platform Legend", "Θρύλος Αποβάθρας", "Legjenda e Platformës", "Leggenda di Banchina")
    default: return pulseText(language, "Rail Oracle", "Σιδηροδρομικό Μαντείο", "Orakulli Hekurudhor", "Oracolo Ferroviario")
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
            title: pulseText(language, "Airport", "Αεροδρόμιο", "Aeroporti", "Aeroporto"),
            subtitle: pulseText(language, "Athens International Airport, M3", "Διεθνές Αεροδρόμιο Αθηνών, M3", "Aeroporti Ndërkombëtar i Athinës, M3", "Aeroporto Internazionale di Atene, M3")
        )
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                pulseBackHeader(title: context.title, subtitle: context.subtitle, onBack: { dismiss() })
                IchnosSummaryPanel(language: language, summary: summary, didLoad: didLoad, onReport: { onReport(context) })
                pulseSectionTitle(pulseText(language, "Current community reports", "Τρέχουσες αναφορές κοινότητας", "Raportet aktuale të komunitetit", "Segnalazioni attuali della comunita"))
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
            title: pulseText(language, "Train 1635", "Τρένο 1635", "Treni 1635", "Treno 1635"),
            subtitle: pulseText(language, "Athens to Kalambaka", "Αθήνα προς Καλαμπάκα", "Athinë për Kalambaka", "Atene verso Kalambaka")
        )
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                pulseBackHeader(title: context.title, subtitle: context.subtitle, onBack: { dismiss() })
                IchnosSummaryPanel(language: language, summary: summary, didLoad: didLoad, onReport: { onReport(context) })
                pulseSectionTitle(pulseText(language, "Current community reports", "Τρέχουσες αναφορές κοινότητας", "Raportet aktuale të komunitetit", "Segnalazioni attuali della comunita"))
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
                    title: pulseText(language, "Ichnos activity", "Δραστηριότητα Ichnos", "Aktiviteti Ichnos", "Attivita Ichnos"),
                    subtitle: pulseText(language, "Across Greece", "Σε όλη την Ελλάδα", "Në gjithë Greqinë", "In tutta la Grecia"),
                    onBack: { dismiss() }
                )
                communityNotice(language)
                communityIssueList(language: language, summary: summary, didLoad: didLoad)
                pulseSectionTitle(pulseText(language, "Greek railway history", "Ιστορικό ελληνικών σιδηροδρόμων", "Historia e hekurudhave greke", "Storico ferroviario greco"))
                Text(pulseText(language, "Actual anonymous user reports are kept as daily totals, then grouped by month or year. Estimated journeys are never added to this history.", "Οι πραγματικές ανώνυμες αναφορές χρηστών κρατούνται ως ημερήσια σύνολα και ομαδοποιούνται ανά μήνα ή έτος. Οι εκτιμώμενες διαδρομές δεν προστίθενται ποτέ σε αυτό το ιστορικό.", "Raportet reale anonime të përdoruesve ruhen si totale ditore dhe grupohen sipas muajit ose vitit. Udhëtimet e vlerësuara nuk shtohen kurrë në këtë histori.", "Le segnalazioni anonime reali degli utenti vengono conservate come totali giornalieri e raggruppate per mese o anno. I viaggi stimati non vengono mai aggiunti allo storico."))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Picker("", selection: $selectedHistoryPeriod) {
                    ForEach(IchnosHistoryPeriod.allCases) { period in
                        Text(period.title(language)).tag(period)
                    }
                }
                .pickerStyle(.segmented)
                .accessibilityLabel(pulseText(language, "History period", "Περίοδος ιστορικού", "Periudha e historisë", "Periodo storico"))
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
        case .day: return pulseText(language, "Days", "Ημέρες", "Ditë", "Giorni")
        case .month: return pulseText(language, "Months", "Μήνες", "Muaj", "Mesi")
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
            Text(pulseText(language, "Only anonymous aggregate counts are permanent. Individual reports are deleted within seven days.", "Μόνο τα ανώνυμα συγκεντρωτικά σύνολα παραμένουν μόνιμα. Οι μεμονωμένες αναφορές διαγράφονται εντός επτά ημερών.", "Vetëm totalet anonime të grumbulluara ruhen përgjithmonë. Raportet individuale fshihen brenda shtatë ditësh.", "Solo i conteggi aggregati anonimi restano permanenti. Le singole segnalazioni vengono eliminate entro sette giorni."))
                .font(.caption2)
                .foregroundStyle(.secondary)
                .padding(.horizontal, 4)
        } else if let history, history.buckets.isEmpty {
            VStack(spacing: 8) {
                Image(systemName: "calendar.badge.clock").font(.title2).foregroundStyle(.secondary)
                Text(pulseText(language, "No reports recorded for this period yet", "Δεν έχουν καταγραφεί αναφορές για αυτή την περίοδο", "Ende nuk ka raporte për këtë periudhë", "Nessuna segnalazione registrata per questo periodo"))
                    .font(.subheadline.weight(.semibold))
                    .multilineTextAlignment(.center)
                Text(pulseText(language, "History starts with accepted Ichnos reports. It never invents past numbers.", "Το ιστορικό ξεκινά με αποδεκτές αναφορές Ichnos. Δεν επινοεί ποτέ παλιούς αριθμούς.", "Historia fillon me raportet e pranuara Ichnos. Nuk shpik kurrë numra të kaluar.", "Lo storico inizia con le segnalazioni Ichnos accettate. Non inventa mai numeri passati."))
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
                Text(pulseText(language, "History is temporarily unavailable", "Το ιστορικό δεν είναι προσωρινά διαθέσιμο", "Historia nuk është përkohësisht e disponueshme", "Lo storico non e temporaneamente disponibile"))
                    .font(.subheadline.weight(.semibold))
                Text(pulseText(language, "Check your connection and try again.", "Έλεγξε τη σύνδεσή σου και προσπάθησε ξανά.", "Kontrollo lidhjen dhe provo përsëri.", "Controlla la connessione e riprova."))
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
                Label("\(bucket.positiveReports) \(pulseText(language, "good", "καλα", "mirë", "bene"))", systemImage: "checkmark.circle.fill")
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
        case "normal": label = pulseText(language, "OK", "Καλά", "Në rregull", "OK")
        case "clean": label = pulseText(language, "clean", "καθαρά", "pastër", "pulito")
        case "delayed": label = pulseText(language, "delayed", "καθυστέρηση", "vonesë", "ritardo")
        case "crowded": label = pulseText(language, "crowded", "κόσμος", "plot", "affollato")
        case "stopped": label = pulseText(language, "stopped", "διακοπή", "ndaluar", "fermo")
        case "too_hot": label = pulseText(language, "too hot", "πολύ ζέστη", "shumë nxehtë", "troppo caldo")
        case "access": label = pulseText(language, "access", "πρόσβαση", "akses", "accesso")
        case "facilities": label = pulseText(language, "facilities", "παροχές", "shërbime", "servizi")
        case "safety": label = pulseText(language, "safety", "ασφάλεια", "siguri", "sicurezza")
        default: label = pulseText(language, "other", "άλλο", "tjetër", "altro")
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
                    pulseMetric(pulseText(language, "THIS WEEK", "ΑΥΤΗ ΤΗΝ ΕΒΔΟΜΑΔΑ", "KËTË JAVË", "QUESTA SETTIMANA"), "\(store.thisWeek)", SyrmosTokens.suburban)
                }
                pulseSectionTitle(pulseText(language, "Contributor milestones", "Ορόσημα συνεισφορέα", "Arritjet e kontribuesit", "Traguardi del collaboratore"))
                HStack(spacing: 8) {
                    pulseBadge("✓", pulseText(language, "First\nReport", "Πρώτη\nΑναφορά", "Raporti\ni parë", "Prima\nsegnalazione"), unlocked: store.confirmed >= 1)
                    pulseBadge("◉", pulseText(language, "Live\nReporter", "Ζωντανός\nReporter", "Raportues\nLive", "Reporter\nLive"), unlocked: store.confirmed >= 10)
                    pulseBadge("★", pulseText(language, "Station\nGuardian", "Φύλακας\nΣταθμού", "Mbrojtës\nStacioni", "Custode\nStazione"), unlocked: store.confirmed >= 50)
                    pulseBadge("100", pulseText(language, "100\nReports", "100\nΑναφορές", "100\nRaporte", "100\nReport"), unlocked: store.confirmed >= 100)
                }
                pulseSectionTitle(pulseText(language, "Weekly community activity", "Εβδομαδιαία δραστηριότητα κοινότητας", "Aktiviteti javor i komunitetit", "Attivita settimanale della comunita"))
                weeklyActivity
                VStack(alignment: .leading, spacing: 4) {
                    Text(pulseText(language, "Private by construction", "Ιδιωτικό από τον σχεδιασμό", "Privat nga ndërtimi", "Privato per costruzione")).font(.subheadline.weight(.semibold))
                    Text(pulseText(language, "Local progress stays on this device. Individual reports contain no account, device ID, or location and are deleted within seven days. Only anonymous daily totals remain for railway history.", "Η τοπική πρόοδος μένει στη συσκευή. Οι μεμονωμένες αναφορές δεν περιέχουν λογαριασμό, αναγνωριστικό συσκευής ή τοποθεσία και διαγράφονται εντός επτά ημερών. Μόνο τα ανώνυμα ημερήσια σύνολα παραμένουν για το σιδηροδρομικό ιστορικό.", "Progresi lokal mbetet në pajisje. Raportet individuale nuk përmbajnë llogari, ID pajisjeje ose vendndodhje dhe fshihen brenda shtatë ditësh. Vetëm totalet anonime ditore mbeten për historinë hekurudhore.", "I progressi locali restano sul dispositivo. Le singole segnalazioni non contengono account, ID del dispositivo o posizione e vengono eliminate entro sette giorni. Solo i totali giornalieri anonimi restano per lo storico ferroviario.")).font(.caption).foregroundStyle(.secondary)
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
            pulseBackHeader(title: pulseText(language, "Local contribution", "Τοπική συνεισφορά", "Kontributi lokal", "Contributo locale"), subtitle: "", onBack: { dismiss() }, foreground: .white)
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
                    Text(pulseText(language, "Progress stored only on this device", "Η πρόοδος αποθηκεύεται μόνο στη συσκευή", "Progresi ruhet vetëm në këtë pajisje", "Progressi salvati solo su questo dispositivo")).font(.caption2.bold()).padding(.horizontal, 12).padding(.vertical, 6).background(.white.opacity(0.17), in: Capsule())
                }
            }
            Text(pulseText(language, "NEXT LEVEL", "ΕΠΟΜΕΝΟ ΕΠΙΠΕΔΟ", "NIVELI TJETËR", "PROSSIMO LIVELLO")).font(.caption2.bold())
            ProgressView(value: Double(store.confirmed % 100), total: 100).tint(Color(hex: 0x63E6A6))
            HStack {
                Text("\(store.confirmed) \(pulseText(language, "confirmed contributions", "επιβεβαιωμενες συνεισφορες", "kontribute të konfirmuara", "contributi confermati"))")
                Spacer()
                Text("\(100 - store.confirmed % 100) \(pulseText(language, "to", "για", "deri në", "a")) \(nextCallsign)")
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
                Text(pulseText(language, "Anonymous reports across Greece", "Ανώνυμες αναφορές σε όλη την Ελλάδα", "Raporte anonime në Greqi", "Segnalazioni anonime in tutta la Grecia")).font(.caption)
                Spacer()
                Text(pulseText(language, "Last 7 days", "Τελευταίες 7 ημέρες", "7 ditët e fundit", "Ultimi 7 giorni")).font(.caption2.bold()).foregroundStyle(SyrmosTokens.live)
            }
            Text(weeklyTotal.formatted()).font(.title.bold())
            Text(pulseText(language, "Your local contribution: \(store.thisWeek)", "Η τοπική συνεισφορά σου: \(store.thisWeek)", "Kontributi yt lokal: \(store.thisWeek)", "Il tuo contributo locale: \(store.thisWeek)")).font(.caption.weight(.semibold))
            Text(pulseText(language, "This total comes from accepted anonymous reports, not estimated journeys.", "Αυτό το σύνολο προέρχεται από αποδεκτές ανώνυμες αναφορές, όχι εκτιμήσεις διαδρομών.", "Ky total vjen nga raporte anonime të pranuara, jo nga udhëtime të vlerësuara.", "Questo totale proviene da segnalazioni anonime accettate, non da viaggi stimati.")).font(.caption2).foregroundStyle(.secondary)
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
                    Text("\(normalCount) \(pulseText(language, "anonymous everything-OK reports", "ανωνυμες αναφορες οτι ολα ειναι καλα", "raporte anonime se gjithçka është në rregull", "segnalazioni anonime di tutto regolare"))")
                        .font(.caption2.bold()).foregroundStyle(SyrmosTokens.live)
                }
            }
            Spacer(minLength: 4)
            Button(pulseText(language, "Report", "Αναφορά", "Raporto", "Segnala"), action: onReport)
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
                ? pulseText(language, "Community status unavailable", "Η κατάσταση κοινότητας δεν είναι διαθέσιμη", "Gjendja e komunitetit nuk është e disponueshme", "Stato della comunita non disponibile")
                : pulseText(language, "Loading community status", "Φόρτωση κατάστασης κοινότητας", "Po ngarkohet gjendja e komunitetit", "Caricamento stato della comunita")
        }
        return summary.hasIssues
            ? pulseText(language, "Active community issues", "Ενεργά προβλήματα κοινότητας", "Probleme aktive të komunitetit", "Problemi attivi della comunita")
            : pulseText(language, "No active issues reported", "Δεν αναφέρθηκαν ενεργά προβλήματα", "Nuk ka probleme aktive të raportuara", "Nessun problema attivo segnalato")
    }
    private var detail: String {
        guard let summary else {
            return didLoad
                ? pulseText(language, "Check your connection and try again", "Έλεγξε τη σύνδεση και προσπάθησε ξανά", "Kontrollo lidhjen dhe provo përsëri", "Controlla la connessione e riprova")
                : pulseText(language, "Anonymous reports from the last two hours", "Ανώνυμες αναφορές των τελευταίων δυο ωρών", "Raporte anonime nga dy orët e fundit", "Segnalazioni anonime delle ultime due ore")
        }
        if summary.hasIssues {
            return pulseText(language, "Estimated normal-journey counts are hidden while an issue is active.", "Οι εκτιμήσεις κανονικών διαδρομών κρύβονται όσο υπάρχει πρόβλημα.", "Vlerësimet e udhëtimeve normale fshihen kur ka problem aktiv.", "Le stime dei viaggi regolari sono nascoste mentre un problema e attivo.")
        }
        let estimate = summary.estimatedJourneysToday ?? 0
        return pulseText(language, "Estimated \(estimate) journeys so far today. Estimate, not user confirmations.", "Εκτιμώμενες \(estimate) διαδρομές σήμερα. Εκτίμηση, όχι επιβεβαιώσεις χρηστών.", "Rreth \(estimate) udhëtime sot. Vlerësim, jo konfirmime përdoruesish.", "Circa \(estimate) viaggi oggi. Stima, non conferme degli utenti.")
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
            title: pulseText(language, "Nothing active to show", "Δεν υπάρχει κάτι ενεργό", "Nuk ka asgjë aktive për të shfaqur", "Nessun elemento attivo da mostrare"),
            detail: summary.normalReportCount > 0
                ? pulseText(language, "\(summary.normalReportCount) anonymous everything-OK reports remain active", "\(summary.normalReportCount) ανώνυμες αναφορές ότι όλα είναι καλά παραμένουν ενεργές", "\(summary.normalReportCount) raporte anonime se gjithçka është në rregull janë aktive", "\(summary.normalReportCount) segnalazioni anonime di tutto regolare sono attive")
                : pulseText(language, "Be the first to report what you can see", "Γίνε ο πρώτος που θα αναφέρει τι βλέπει", "Raporto i pari atë që sheh", "Segnala per primo cio che vedi"),
            status: pulseText(language, "Clear", "Καθαρά", "Në rregull", "Regolare"),
            color: SyrmosTokens.live
        )
    } else {
        pulseActivityRow(
            symbol: didLoad ? "!" : "...",
            title: didLoad ? pulseText(language, "Unable to load reports", "Αδύνατη η φόρτωση αναφορών", "Raportet nuk mund të ngarkohen", "Impossibile caricare le segnalazioni") : pulseText(language, "Loading reports", "Φόρτωση αναφορών", "Po ngarkohen raportet", "Caricamento segnalazioni"),
            detail: didLoad ? pulseText(language, "Check your connection and reopen this screen", "Έλεγξε τη σύνδεση και άνοιξε ξανά την οθόνη", "Kontrollo lidhjen dhe rihap këtë ekran", "Controlla la connessione e riapri questa schermata") : pulseText(language, "Only anonymous reports from the last two hours are shown", "Εμφανίζονται μόνο ανώνυμες αναφορές των τελευταίων δυο ωρών", "Shfaqen vetëm raporte anonime nga dy orët e fundit", "Sono mostrate solo segnalazioni anonime delle ultime due ore"),
            status: didLoad ? pulseText(language, "Offline", "Εκτός σύνδεσης", "Jashtë linje", "Offline") : pulseText(language, "Loading", "Φόρτωση", "Ngarkim", "Caricamento"),
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
    Text(pulseText(language, "Community reports are not official operator notices.", "Οι αναφορές κοινότητας δεν είναι επίσημες ανακοινώσεις φορέα.", "Raportet e komunitetit nuk janë njoftime zyrtare të operatorit.", "Le segnalazioni della comunita non sono avvisi ufficiali."))
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
