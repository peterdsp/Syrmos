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
    }
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
                    Text(pulseText(language, "Local progress stays on this device. Network reports contain no account, device ID, or location and are deleted within seven days.", "Η τοπικη προοδος μενει στη συσκευη. Οι αναφορες δικτυου δεν περιεχουν λογαριασμο, αναγνωριστικο συσκευης η τοποθεσια και διαγραφονται εντος επτα ημερων.", "Progresi lokal mbetet ne pajisje. Raportet ne rrjet nuk permbajne llogari, ID pajisjeje ose vendndodhje dhe fshihen brenda shtate ditesh.", "I progressi locali restano sul dispositivo. Le segnalazioni in rete non contengono account, ID del dispositivo o posizione e vengono eliminate entro sette giorni.")).font(.caption).foregroundStyle(.secondary)
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
                .buttonStyle(.borderedProminent).tint(.primary)
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
