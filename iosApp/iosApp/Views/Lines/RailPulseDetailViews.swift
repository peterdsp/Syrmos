import SwiftUI

enum RailPulseDestination: String, Identifiable, Hashable {
    case station
    case train
    case contribution

    var id: String { rawValue }
}

@MainActor
final class RailPulseLocalStore: ObservableObject {
    static let shared = RailPulseLocalStore()

    @Published private(set) var confirmed: Int
    @Published private(set) var qualityPercent: Int
    @Published private(set) var thisWeek: Int

    private let defaults = UserDefaults(suiteName: "group.com.syrmosApp.ios") ?? .standard

    private init() {
        confirmed = defaults.object(forKey: "railpulse_confirmed") as? Int ?? 347
        qualityPercent = defaults.object(forKey: "railpulse_quality") as? Int ?? 96
        thisWeek = defaults.object(forKey: "railpulse_week") as? Int ?? 28
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
        defaults.set(confirmed, forKey: "railpulse_confirmed")
        defaults.set(qualityPercent, forKey: "railpulse_quality")
        defaults.set(thisWeek, forKey: "railpulse_week")
    }
}

private struct RailPulseCondition: Identifiable {
    let id: String
    let symbol: String
    let label: String
    let value: String
    let detail: String
    let color: Color
}

struct RailPulseStationDetailView: View {
    let language: AppLanguage
    let onReport: (RailPulseReportContext) -> Void
    @Environment(\.dismiss) private var dismiss

    private var context: RailPulseReportContext {
        RailPulseReportContext(
            title: pulseText(language, "Airport", "Αεροδρομιο", "Aeroporti", "Aeroporto"),
            subtitle: pulseText(language, "Athens International Airport · M3", "Διεθνες Αεροδρομιο Αθηνων · M3", "Aeroporti Nderkombetar i Athines · M3", "Aeroporto Internazionale di Atene · M3")
        )
    }

    private var conditions: [RailPulseCondition] {
        [
            .init(id: "platform", symbol: "••", label: "PLATFORM", value: pulseText(language, "Moderate crowd", "Μετριος κοσμος", "Turme mesatare", "Affollamento medio"), detail: "18 confirmations · 2 min ago", color: SyrmosTokens.warning),
            .init(id: "lift", symbol: "♿", label: "LIFT", value: pulseText(language, "Out of service", "Εκτος λειτουργιας", "Jashte sherbimit", "Fuori servizio"), detail: "31 confirmations · expires 18:00", color: SyrmosTokens.disruption),
            .init(id: "escalator", symbol: "✓", label: "ESCALATOR", value: pulseText(language, "Working", "Λειτουργει", "Punon", "Funzionante"), detail: "9 confirmations · 8 min ago", color: SyrmosTokens.live),
            .init(id: "parking", symbol: "P", label: "PARKING", value: pulseText(language, "Nearly full", "Σχεδον γεματο", "Pothuajse plot", "Quasi pieno"), detail: "7 confirmations · 12 min ago", color: SyrmosTokens.scheduled),
        ]
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                pulseBackHeader(
                    title: context.title,
                    subtitle: pulseText(language, "Athens International Airport", "Διεθνες Αεροδρομιο Αθηνων", "Aeroporti Nderkombetar i Athines", "Aeroporto Internazionale di Atene"),
                    onBack: { dismiss() }
                )
                nextDeparture
                communitySummary
                pulseSectionTitle(pulseText(language, "Station conditions", "Συνθηκες σταθμου", "Gjendja e stacionit", "Condizioni stazione"))
                pulseConditionGrid(conditions)
                pulseSectionTitle(pulseText(language, "Latest at this station", "Τελευταια στον σταθμο", "Me te fundit ne stacion", "Ultime dalla stazione"))
                pulseActivityRow(symbol: "!", title: pulseText(language, "Lift out of service", "Ανελκυστηρας εκτος λειτουργιας", "Ashensori jashte sherbimit", "Ascensore fuori servizio"), detail: "Confirmed by 31 · 2 min ago", status: pulseText(language, "Confirm", "Επιβεβαιωση", "Konfirmo", "Conferma"), color: SyrmosTokens.disruption)
                pulseActivityRow(symbol: "✓", title: pulseText(language, "Escalator working again", "Η κυλιομενη λειτουργει ξανα", "Shkallet levizese punojne perseri", "Scala mobile di nuovo attiva"), detail: "Confirmed by 9 · 8 min ago", status: pulseText(language, "Resolved", "Λυθηκε", "Zgjidhur", "Risolto"), color: SyrmosTokens.live)
                communityNotice(language)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 110)
        }
        .background(Color.syrmosBackground)
        .toolbar(.hidden, for: .navigationBar)
    }

    private var nextDeparture: some View {
        HStack(spacing: 12) {
            pulseCircle("M3", background: SyrmosTokens.metroBlue, foreground: .white)
            VStack(alignment: .leading, spacing: 2) {
                Text(pulseText(language, "NEXT TOWARD DIMOTIKO THEATRO", "ΕΠΟΜΕΝΟ ΠΡΟΣ ΔΗΜΟΤΙΚΟ ΘΕΑΤΡΟ", "TJETRI DREJT DIMOTIKO THEATRO", "PROSSIMO VERSO DIMOTIKO THEATRO"))
                    .font(.caption2.bold())
                Text("3 min").font(.title.bold())
                Text("14:42 · Platform 1").font(.caption)
            }
            Spacer()
            Text(pulseText(language, "Scheduled", "Προγραμματισμενο", "Planifikuar", "Programmato"))
                .font(.caption2.bold())
                .foregroundStyle(SyrmosTokens.metroBlue)
                .padding(.horizontal, 12).padding(.vertical, 10)
                .background(.white, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .foregroundStyle(.white)
        .padding(16)
        .background(Color(hex: 0x214D78), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private var communitySummary: some View {
        HStack(spacing: 12) {
            pulseCircle("●", background: SyrmosTokens.live.opacity(0.12), foreground: SyrmosTokens.live)
            VStack(alignment: .leading, spacing: 3) {
                Text(pulseText(language, "Community: normal", "Κοινοτητα: κανονικα", "Komuniteti: normal", "Comunita: regolare")).font(.subheadline.weight(.semibold))
                Text(pulseText(language, "12 fresh reports · updated 90 sec ago", "12 προσφατες αναφορες · πριν 90 δευτ", "12 raporte te fresketa · 90 sek me pare", "12 segnalazioni recenti · 90 sec fa")).font(.caption).foregroundStyle(.secondary)
                Text(pulseText(language, "High community confidence", "Υψηλη εμπιστοσυνη κοινοτητας", "Besim i larte i komunitetit", "Alta affidabilita della comunita")).font(.caption2.bold()).foregroundStyle(SyrmosTokens.live)
            }
            Spacer(minLength: 4)
            Button(pulseText(language, "Report", "Αναφορα", "Raporto", "Segnala")) { onReport(context) }
                .buttonStyle(.borderedProminent).tint(.primary)
        }
        .padding(14)
        .background(Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .shadow(color: .black.opacity(0.07), radius: 7, y: 4)
    }
}

struct RailPulseTrainDetailView: View {
    let language: AppLanguage
    let onReport: (RailPulseReportContext) -> Void
    @Environment(\.dismiss) private var dismiss

    private var context: RailPulseReportContext {
        RailPulseReportContext(
            title: pulseText(language, "Train 1635", "Τρενο 1635", "Treni 1635", "Treno 1635"),
            subtitle: pulseText(language, "Athens to Kalambaka", "Αθηνα προς Καλαμπακα", "Athine per Kalambaka", "Atene verso Kalambaka")
        )
    }

    private var conditions: [RailPulseCondition] {
        [
            .init(id: "occupancy", symbol: "•••", label: "OCCUPANCY", value: pulseText(language, "Standing", "Ορθιοι", "Ne kembe", "In piedi"), detail: "31 confirmations · 2 min ago", color: SyrmosTokens.warning),
            .init(id: "delay", symbol: "+4", label: "DELAY", value: pulseText(language, "About 4 min", "Περιπου 4 λεπ", "Rreth 4 min", "Circa 4 min"), detail: "24 confirmations · 1 min ago", color: SyrmosTokens.disruption),
            .init(id: "temperature", symbol: "✓", label: "TEMPERATURE", value: pulseText(language, "Comfortable", "Ανετη", "Rehat", "Confortevole"), detail: "18 confirmations · 4 min ago", color: SyrmosTokens.live),
            .init(id: "cleanliness", symbol: "4", label: "CLEANLINESS", value: "4 of 5", detail: "12 confirmations · 8 min ago", color: SyrmosTokens.live),
        ]
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 12) {
                trainHeader
                pulseSectionTitle(pulseText(language, "Current conditions", "Τρεχουσες συνθηκες", "Gjendja aktuale", "Condizioni attuali"))
                pulseConditionGrid(conditions)
                pulseSectionTitle(pulseText(language, "Service", "Υπηρεσιες", "Sherbimi", "Servizio"))
                pulseActivityRow(symbol: "✓", title: pulseText(language, "Air conditioning working", "Ο κλιματισμος λειτουργει", "Kondicioneri punon", "Aria condizionata funzionante"), detail: "Confirmed by 27 passengers", status: pulseText(language, "Verified", "Επιβεβαιωμενο", "Konfirmuar", "Verificato"), color: SyrmosTokens.live)
                pulseActivityRow(symbol: "!", title: pulseText(language, "Wi-Fi unavailable", "Το Wi-Fi δεν λειτουργει", "Wi-Fi nuk punon", "Wi-Fi non disponibile"), detail: "9 confirmations · expires at journey end", status: pulseText(language, "Active", "Ενεργο", "Aktiv", "Attivo"), color: SyrmosTokens.disruption)
                pulseSectionTitle(pulseText(language, "Recent activity", "Προσφατη δραστηριοτητα", "Aktiviteti i fundit", "Attivita recente"))
                pulseActivityRow(symbol: "P", title: pulseText(language, "Standing room only", "Μονο ορθιοι", "Vetem ne kembe", "Solo posti in piedi"), detail: "Confirmed by 31 · updated 2 min ago", status: pulseText(language, "Confirm", "Επιβεβαιωση", "Konfirmo", "Conferma"), color: SyrmosTokens.suburban)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 110)
        }
        .background(Color.syrmosBackground)
        .toolbar(.hidden, for: .navigationBar)
    }

    private var trainHeader: some View {
        VStack(alignment: .leading, spacing: 10) {
            pulseBackHeader(title: context.title, subtitle: context.subtitle, onBack: { dismiss() }, foreground: .white)
            HStack {
                Text(pulseText(language, "COMMUNITY SUMMARY", "ΣΥΝΟΨΗ ΚΟΙΝΟΤΗΤΑΣ", "PERMBLEDHJE E KOMUNITETIT", "RIEPILOGO COMUNITA")).font(.caption2.bold())
                Spacer()
                Text("● Updated 53 sec").font(.caption2.bold()).foregroundStyle(Color(hex: 0xFFC24A))
            }
            Text(pulseText(language, "Running with a minor delay", "Κινειται με μικρη καθυστερηση", "Po leviz me vonese te vogel", "In viaggio con lieve ritardo")).font(.title2.bold())
            Text(pulseText(language, "Standing room only. Temperature comfortable.\nAir conditioning confirmed working.", "Μονο ορθιοι. Ανετη θερμοκρασια.\nΟ κλιματισμος επιβεβαιωθηκε οτι λειτουργει.", "Vetem ne kembe. Temperature e rehatshme.\nKondicioneri u konfirmua se punon.", "Solo posti in piedi. Temperatura confortevole.\nAria condizionata confermata funzionante.")).font(.subheadline)
            HStack {
                Text("42 independent confirmations").font(.caption2.bold()).padding(.horizontal, 12).padding(.vertical, 8).background(.white.opacity(0.17), in: Capsule())
                Spacer()
                Button(pulseText(language, "Report", "Αναφορα", "Raporto", "Segnala")) { onReport(context) }
                    .buttonStyle(.borderedProminent).tint(.white).foregroundStyle(Color(hex: 0x332250))
            }
        }
        .foregroundStyle(.white)
        .padding(16)
        .background(LinearGradient(colors: [Color(hex: 0x213F5D), Color(hex: 0x55308A)], startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: 30, style: .continuous))
    }
}

struct RailPulseContributionView: View {
    let language: AppLanguage
    @ObservedObject private var store = RailPulseLocalStore.shared
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 14) {
                profileHeader
                HStack(spacing: 10) {
                    pulseMetric(pulseText(language, "CONFIRMED", "ΕΠΙΒΕΒΑΙΩΜΕΝΑ", "KONFIRMUAR", "CONFERMATI"), "\(store.confirmed)", .primary)
                    pulseMetric(pulseText(language, "QUALITY", "ΠΟΙΟΤΗΤΑ", "CILESIA", "QUALITA"), "\(store.qualityPercent)%", SyrmosTokens.live)
                    pulseMetric(pulseText(language, "THIS WEEK", "ΑΥΤΗ ΤΗΝ ΕΒΔΟΜΑΔΑ", "KETE JAVE", "QUESTA SETTIMANA"), "\(store.thisWeek)", SyrmosTokens.suburban)
                }
                pulseSectionTitle(pulseText(language, "Badges", "Σηματα", "Distinktivet", "Badge"))
                HStack(spacing: 8) {
                    pulseBadge("✓", pulseText(language, "First\nReport", "Πρωτη\nΑναφορα", "Raporti\ni pare", "Prima\nsegnalazione"))
                    pulseBadge("◉", pulseText(language, "Live\nReporter", "Ζωντανος\nReporter", "Raportues\nLive", "Reporter\nLive"))
                    pulseBadge("★", pulseText(language, "Station\nGuardian", "Φυλακας\nΣταθμου", "Mbrojtes\nStacioni", "Custode\nStazione"))
                    pulseBadge("100", pulseText(language, "Accurate\nReports", "Ακριβεις\nΑναφορες", "Raporte\nte sakta", "Report\naccurati"))
                }
                pulseSectionTitle(pulseText(language, "Weekly community goal", "Εβδομαδιαιος στοχος κοινοτητας", "Objektivi javor i komunitetit", "Obiettivo settimanale"))
                weeklyGoal
                VStack(alignment: .leading, spacing: 4) {
                    Text(pulseText(language, "Private by construction", "Ιδιωτικο απο τον σχεδιασμο", "Privat nga ndertimi", "Privato per costruzione")).font(.subheadline.weight(.semibold))
                    Text(pulseText(language, "Progress is local. Network reports will use unlinkable one-time proofs.", "Η προοδος ειναι τοπικη. Οι διαδικτυακες αναφορες θα χρησιμοποιουν ασυνδετες αποδειξεις μιας χρησης.", "Progresi eshte lokal. Raportet ne rrjet do te perdorin prova njeperdorimshe te palidhshme.", "I progressi sono locali. Le segnalazioni online useranno prove monouso non collegabili.")).font(.caption).foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading).padding(16)
                .background(SyrmosTokens.suburban.opacity(0.10), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 110)
        }
        .background(Color.syrmosBackground)
        .toolbar(.hidden, for: .navigationBar)
    }

    private var profileHeader: some View {
        VStack(alignment: .leading, spacing: 14) {
            pulseBackHeader(title: pulseText(language, "Local contribution", "Τοπικη συνεισφορα", "Kontributi lokal", "Contributo locale"), subtitle: "", onBack: { dismiss() }, foreground: .white, trailing: "gearshape.fill")
            HStack(spacing: 14) {
                pulseCircle("⌁", background: .white.opacity(0.16), foreground: .white)
                VStack(alignment: .leading, spacing: 3) {
                    Text("Petros").font(.title2.bold())
                    Text(pulseText(language, "Rail Contributor · Level 5", "Συνεισφορεας Rail · Επιπεδο 5", "Kontribues Rail · Niveli 5", "Collaboratore Rail · Livello 5")).font(.caption.bold())
                    Text(pulseText(language, "Stored only on this device", "Αποθηκευεται μονο σε αυτη τη συσκευη", "Ruhet vetem ne kete pajisje", "Salvato solo su questo dispositivo")).font(.caption2.bold()).padding(.horizontal, 12).padding(.vertical, 6).background(.white.opacity(0.17), in: Capsule())
                }
            }
            Text(pulseText(language, "NEXT LEVEL", "ΕΠΟΜΕΝΟ ΕΠΙΠΕΔΟ", "NIVELI TJETER", "PROSSIMO LIVELLO")).font(.caption2.bold())
            ProgressView(value: Double(store.confirmed % 100), total: 100).tint(Color(hex: 0x63E6A6))
            HStack {
                Text("\(store.confirmed) confirmed contributions")
                Spacer()
                Text("\(100 - store.confirmed % 100) to Level 6")
            }.font(.caption2.bold())
        }
        .foregroundStyle(.white)
        .padding(16)
        .background(LinearGradient(colors: [Color(hex: 0x5D2EA8), Color(hex: 0x343F91)], startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: 30, style: .continuous))
    }

    private var weeklyGoal: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(pulseText(language, "Useful confirmations across Greece", "Χρησιμες επιβεβαιωσεις σε ολη την Ελλαδα", "Konfirmime te dobishme ne Greqi", "Conferme utili in tutta la Grecia")).font(.caption)
                Spacer()
                Text(pulseText(language, "Anonymous total", "Ανωνυμο συνολο", "Total anonim", "Totale anonimo")).font(.caption2.bold()).foregroundStyle(SyrmosTokens.live)
            }
            Text("1,284").font(.title.bold()) + Text(" of 2,000 this week").font(.caption).foregroundStyle(.secondary)
            ProgressView(value: 1_284.0, total: 2_000.0).tint(SyrmosTokens.suburban)
            Text("Your local contribution: \(store.thisWeek)").font(.caption.weight(.semibold))
            Text(pulseText(language, "No names, profiles, or rankings leave any device.", "Κανενα ονομα, προφιλ η καταταξη δεν φευγει απο τη συσκευη.", "Asnje emer, profil ose renditje nuk largohet nga pajisja.", "Nomi, profili e classifiche non lasciano il dispositivo.")).font(.caption2).foregroundStyle(.secondary)
        }
        .padding(18)
        .background(Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .shadow(color: .black.opacity(0.07), radius: 7, y: 4)
    }
}

@MainActor
private func pulseBackHeader(
    title: String,
    subtitle: String,
    onBack: @escaping () -> Void,
    foreground: Color = .primary,
    trailing: String? = nil
) -> some View {
    HStack(spacing: 8) {
        Button(action: onBack) { Image(systemName: "chevron.left").font(.headline) }.buttonStyle(.plain)
        VStack(alignment: .leading, spacing: 1) {
            Text(title).font(.title2.bold())
            if !subtitle.isEmpty { Text(subtitle).font(.caption) }
        }
        Spacer()
        if let trailing { Image(systemName: trailing).padding(10).background(foreground.opacity(0.10), in: Circle()) }
    }
    .foregroundStyle(foreground)
    .padding(.top, 10)
}

private func pulseConditionGrid(_ conditions: [RailPulseCondition]) -> some View {
    LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
        ForEach(conditions) { condition in
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 8) {
                    pulseCircle(condition.symbol, background: condition.color.opacity(0.12), foreground: condition.color)
                    Text(condition.label).font(.caption2).foregroundStyle(.secondary)
                }
                Text(condition.value).font(.subheadline.weight(.medium))
                Text(condition.detail).font(.caption2.weight(.semibold)).foregroundStyle(condition.color)
            }
            .frame(maxWidth: .infinity, minHeight: 106, alignment: .leading)
            .padding(14)
            .background(Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .shadow(color: .black.opacity(0.07), radius: 7, y: 4)
        }
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

private func pulseBadge(_ symbol: String, _ label: String) -> some View {
    VStack(spacing: 7) {
        pulseCircle(symbol, background: SyrmosTokens.suburban.opacity(0.10), foreground: .primary)
        Text(label).font(.system(size: 9, weight: .semibold)).multilineTextAlignment(.center)
    }
    .frame(maxWidth: .infinity).padding(.vertical, 12)
    .background(Color.syrmosSurface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    .shadow(color: .black.opacity(0.06), radius: 6, y: 3)
}

#Preview("Station RailPulse") {
    NavigationStack { RailPulseStationDetailView(language: .english) { _ in } }
}

#Preview("Train RailPulse") {
    NavigationStack { RailPulseTrainDetailView(language: .english) { _ in } }
}

#Preview("Local contribution") {
    NavigationStack { RailPulseContributionView(language: .english) }
}
