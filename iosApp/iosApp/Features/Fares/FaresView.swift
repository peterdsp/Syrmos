import SwiftUI

/// Native OASA ticket catalogue. Replaces the old "tap Ticket prices ->
/// SafariSheet to OASA's web page" flow. Cards are grouped by section and
/// styled in the app's design language. The "View on OASA" footer button
/// still surfaces the OASA URL inside the in-app SafariSheet for users who
/// want to confirm the live price.
struct FaresView: View {
    @ObservedObject private var loc = LocalizationManager.shared
    @ObservedObject private var store = SyrmosFaresStore.shared
    @State private var safariURL: URL?
    @State private var fareFrom: TransitStation?
    @State private var fareTo: TransitStation?
    @State private var picking: PickTarget?

    private let sourceURL = URL(string: "https://www.oasa.gr/en/tickets/prices-of-products/")!

    /// Every fare_products.section grouped under a network so the whole country
    /// shows, not just OASA. Mirrors the Android FaresScreen.
    private static let networks: [(sections: [String], en: String, el: String, sq: String)] = [
        (["single", "offers", "airport", "passes"], "Athens — OASA", "Αθήνα — OASA", "Athinë — OASA"),
        (["thessaloniki"], "Thessaloniki — OSETH", "Θεσσαλονίκη — OSETH", "Selanik — OSETH"),
        (["patras"], "Patras suburban", "Προαστιακός Πάτρας", "Suburban Patra"),
        (["intercity"], "Intercity / regional", "Υπεραστικά / περιφερειακά", "Ndërqytetëse"),
    ]

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                header
                plannerCard
                ForEach(Array(Self.networks.enumerated()), id: \.offset) { _, network in
                    networkView(network)
                }
                if !store.infoLinks.isEmpty {
                    infoLinksSection
                }
                footer
            }
            .padding(.vertical, 16)
        }
        .sheet(item: $picking) { target in
            StationPickerSheet(stations: allStations, language: loc.language) { station in
                if target == .from { fareFrom = station } else { fareTo = station }
                picking = nil
            }
        }
        .background(Color.syrmosBackground)
        .scrollContentBackground(.hidden)
        .navigationTitle(loc.language == .greek ? "Εισιτήρια" : loc.language == .albanian ? "Bileta" : "Tickets")
        .navigationBarTitleDisplayMode(.large)
        .inAppSafari(url: $safariURL)
        .task {
            await store.refresh()
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(loc.language == .greek ? "Τιμές εισιτηρίων" : loc.language == .albanian ? "Çmimet e biletave" : "Fares")
                .font(.title3)
                .fontWeight(.semibold)
            Text(loc.language == .greek
                 ? "Τιμές από τους επίσημους φορείς (OASA, OSETH, Hellenic Train). Τα υπεραστικά τιμολογούνται στην κράτηση."
                 : loc.language == .albanian
                 ? "Çmime nga operatorët zyrtarë (OASA, OSETH, Hellenic Train). Ndërqytetëset çmohen në rezervim."
                 : "Prices from the official operators (OASA, OSETH, Hellenic Train). Intercity is priced at booking.")
                .font(.caption)
                .foregroundStyle(.secondary)
            if !store.updatedAt.isEmpty {
                Text((loc.language == .greek ? "Ενημέρωση: " : loc.language == .albanian ? "Përditësuar: " : "Updated: ") + formattedUpdatedAt)
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
    }

    private var footer: some View {
        VStack(spacing: 10) {
            Text(loc.language == .greek
                 ? "Οι τιμές παρέχονται από την OASA. Για την οριστική τιμή ελέγξτε την επίσημη σελίδα."
                 : loc.language == .albanian
                 ? "Çmimet ofrohen nga OASA. Për çmimin përfundimtar, kontrollo faqen zyrtare."
                 : "Prices are provided by OASA. For the authoritative figure, check the official page.")
                .font(.caption2)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)

            Button {
                safariURL = sourceURL
            } label: {
                HStack {
                    Image(systemName: "safari")
                    Text(loc.language == .greek ? "Άνοιγμα στην OASA" : loc.language == .albanian ? "Hap në OASA" : "View on OASA")
                        .fontWeight(.semibold)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(Color.syrmosPrimary.opacity(0.12))
                .foregroundStyle(Color.syrmosPrimary)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .padding(.horizontal, 16)
        }
    }

    private var infoLinksSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(loc.language == .greek ? "Χρήσιμες πληροφορίες" : loc.language == .albanian ? "Informacione të dobishme" : "Useful information")
                .font(.headline)
                .padding(.horizontal, 16)
            VStack(spacing: 12) {
                ForEach(store.infoLinks) { link in
                    InfoLinkCard(link: link) { rawUrl in
                        if let url = URL(string: rawUrl) ?? URL(string: link.urlEn) {
                            safariURL = url
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
        }
    }

    // MARK: Journey fare planner

    private var plannerCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(loc.language == .greek ? "Υπολόγισε κόμιστρο" : loc.language == .albanian ? "Llogarit çmimin" : "Plan a trip — fare")
                .font(.subheadline).fontWeight(.semibold)
            stationButton(label: loc.language == .greek ? "Από" : loc.language == .albanian ? "Nga" : "From", station: fareFrom) { picking = .from }
            stationButton(label: loc.language == .greek ? "Προς" : loc.language == .albanian ? "Te" : "To", station: fareTo) { picking = .to }
            if let f = fareFrom, let t = fareTo {
                fareResult(FareEngine.computeFare(from: f, to: t))
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(uiColor: .secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .padding(.horizontal, 16)
    }

    private func stationButton(label: String, station: TransitStation?, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(station.map { stationLabel($0) } ?? label)
                    .foregroundStyle(station == nil ? Color.secondary : Color.primary)
                Spacer()
                Image(systemName: "chevron.down").font(.caption).foregroundStyle(.secondary)
            }
            .padding(.horizontal, 12).padding(.vertical, 12)
            .background(Color(uiColor: .tertiarySystemGroupedBackground))
            .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .buttonStyle(.plain)
    }

    private func fareResult(_ offer: FareOffer) -> some View {
        let priceText: String = offer.dynamic
            ? (loc.language == .greek ? "στην κράτηση" : loc.language == .albanian ? "në rezervim" : "at booking")
            : String(format: "€%.2f", offer.fullEur ?? 0)
                + (offer.reducedEur.map { " · " + (loc.language == .greek ? "μειωμένο " : loc.language == .albanian ? "e reduktuar " : "reduced ") + String(format: "€%.2f", $0) } ?? "")
        return VStack(alignment: .leading, spacing: 2) {
            Text(priceText).font(.headline).foregroundStyle(Color.syrmosPrimary)
            Text("\(offer.product) · \(offer.op)").font(.caption).foregroundStyle(.secondary)
        }
        .padding(12).frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.syrmosPrimary.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }

    private func stationLabel(_ st: TransitStation) -> String {
        loc.language == .greek ? (st.nameEl.isEmpty ? st.name : st.nameEl) : st.name
    }

    private var allStations: [TransitStation] {
        var seen = Set<String>()
        var out: [TransitStation] = []
        for line in SyrmosData.lines {
            for s in SyrmosData.stations(for: line.id) where !seen.contains(s.id) {
                seen.insert(s.id)
                out.append(s)
            }
        }
        return out.sorted { $0.name < $1.name }
    }

    // MARK: Fares menu, grouped by network

    private func networkView(_ network: (sections: [String], en: String, el: String, sq: String)) -> some View {
        let grouped = Dictionary(grouping: store.products) { $0.section }
        // Compute visible (section, products) pairs up front so no `let` lands
        // inside the ViewBuilder closures.
        let visible: [(section: String, items: [SyrmosFaresStore.Product])] = network.sections.compactMap { section in
            let items = grouped[section] ?? []
            return items.isEmpty ? nil : (section, items)
        }
        let multi = network.sections.count > 1
        return VStack(alignment: .leading, spacing: 10) {
            if !visible.isEmpty {
                Text(loc.language == .greek ? network.el : loc.language == .albanian ? network.sq : network.en)
                    .font(.subheadline).fontWeight(.bold)
                    .foregroundStyle(Color.syrmosPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16)
                ForEach(visible, id: \.section) { entry in
                    if multi {
                        Text(sectionTitle(entry.section))
                            .font(.footnote).fontWeight(.semibold)
                            .foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 16)
                    }
                    VStack(spacing: 10) {
                        ForEach(entry.items) { product in FareCard(product: product) }
                    }
                    .padding(.horizontal, 16)
                }
            }
        }
    }

    private func sectionTitle(_ key: String) -> String {
        switch (key, loc.language) {
        case ("single",  .english):  return "Single tickets"
        case ("single",  .greek):    return "Μονά εισιτήρια"
        case ("single",  .albanian): return "Bileta të vetme"
        case ("offers",  .english):  return "Packs and offers"
        case ("offers",  .greek):    return "Πακέτα και προσφορές"
        case ("offers",  .albanian): return "Paketa dhe oferta"
        case ("airport", .english):  return "Airport tickets"
        case ("airport", .greek):    return "Εισιτήρια αεροδρομίου"
        case ("airport", .albanian): return "Bileta për aeroportin"
        case ("passes",  .english):  return "Day passes"
        case ("passes",  .greek):    return "Ημερήσια εισιτήρια"
        case ("passes",  .albanian): return "Abone ditore"
        default:                     return key.capitalized
        }
    }

    private var formattedUpdatedAt: String {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = f.date(from: store.updatedAt) ?? ISO8601DateFormatter().date(from: store.updatedAt) {
            let out = DateFormatter()
            out.dateStyle = .medium
            out.timeStyle = .short
            return out.string(from: date)
        }
        return store.updatedAt
    }
}

private struct InfoLinkCard: View {
    let link: SyrmosFaresStore.InfoLink
    let onVerify: (String) -> Void
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Image(systemName: link.icon)
                    .font(.system(size: 18))
                    .foregroundStyle(Color.syrmosPrimary)
                    .frame(width: 26)
                VStack(alignment: .leading, spacing: 2) {
                    Text(link.localizedTitle(loc.language))
                        .font(.subheadline)
                        .fontWeight(.semibold)
                    Text(link.operator_.uppercased())
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
            if let summary = displaySummary, !summary.isEmpty {
                Text(summary)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            if let bullets = link.bullets, !bullets.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(bullets) { bullet in
                        HStack(alignment: .top, spacing: 8) {
                            Text("•")
                                .font(.caption)
                                .foregroundStyle(Color.syrmosPrimary)
                            Text(bullet.localized(loc.language))
                                .font(.caption)
                                .foregroundStyle(.primary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
            }
            Button {
                let raw = link.localizedUrl(loc.language)
                onVerify(raw)
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "arrow.up.right.square")
                        .font(.caption2)
                    Text(loc.language == .greek
                         ? "Επιβεβαίωση στο \(link.operator_.uppercased())"
                         : loc.language == .albanian
                         ? "Verifiko në \(link.operator_.uppercased())"
                         : "Verify on \(link.operator_.uppercased())")
                        .font(.caption)
                        .fontWeight(.semibold)
                }
                .padding(.vertical, 8)
                .padding(.horizontal, 12)
                .background(Color.syrmosPrimary.opacity(0.12))
                .foregroundStyle(Color.syrmosPrimary)
                .clipShape(Capsule())
            }
            .buttonStyle(.plain)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(uiColor: .secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private var displaySummary: String? {
        link.localizedSummary(loc.language)
    }
}

private struct FareCard: View {
    let product: SyrmosFaresStore.Product
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline) {
                Text(displayTitle.capitalized)
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .lineLimit(2)
                Spacer()
                // Intercity has no fixed price (booking-time); show that, not a blank.
                Text(product.fullPriceEur.map { String(format: "€%.2f", $0) }
                     ?? (loc.language == .greek ? "στην κράτηση" : loc.language == .albanian ? "në rezervim" : "at booking"))
                    .font(.headline.monospacedDigit())
                    .foregroundStyle(Color.syrmosPrimary)
            }
            if let disc = product.discountedPriceEur {
                HStack {
                    Image(systemName: "tag.fill")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                    Text((loc.language == .greek ? "Μειωμένο: " : loc.language == .albanian ? "Me zbritje: " : "Discounted: ")
                         + String(format: "€%.2f", disc))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            let displayValidity = product.localizedValidity(loc.language)
            if !displayValidity.isEmpty {
                Text(displayValidity)
                    .font(.caption2)
                    .fontWeight(.medium)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(badgeColor.opacity(0.15))
                    .foregroundStyle(badgeColor)
                    .clipShape(Capsule())
            }
            let displayNotes = product.localizedNotes(loc.language)
            if !displayNotes.isEmpty {
                Text(displayNotes)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(4)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(uiColor: .secondarySystemGroupedBackground))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private var badgeColor: Color {
        if product.tags.contains("airport_express") || product.tags.contains("tourist") {
            return .syrmosPrimary
        }
        if product.tags.contains("airport_excluded") {
            return .orange
        }
        return .secondary
    }

    private var displayTitle: String {
        return product.localizedTitle(loc.language)
    }
}

/// A grounded from -> to fare quote. Swift mirror of the KMP ComputeFareUseCase
/// (core/domain) and the web engine. Prices are transcribed from official
/// operator sources (docs/data/2026-07-27-fares-collection.md); intercity is
/// dynamic (booking-priced), carrying no number so the UI shows the booking path.
struct FareOffer {
    let fullEur: Double?
    let reducedEur: Double?
    let product: String
    let op: String
    let dynamic: Bool
}

/// Shared fare engine (fares planner + Ariadne can both use it). Charges a trip
/// on the LOCAL network the two stations share; if they share none (different
/// cities, or only a national line links them) it is intercity, booking-priced.
enum FareEngine {
    static func computeFare(from: TransitStation, to: TransitStation) -> FareOffer {
        let fr = regions(from), tr = regions(to)
        let local = fr.first { $0 != .national && tr.contains($0) }
        switch local {
        case .athens:
            return (isAirport(from) || isAirport(to))
                ? FareOffer(fullEur: 9.00, reducedEur: 4.50, product: "Airport Metro ticket (Line 3)", op: "OASA", dynamic: false)
                : FareOffer(fullEur: 1.20, reducedEur: 0.50, product: "90-minute integrated ticket", op: "OASA", dynamic: false)
        case .thessaloniki:
            return (isThessSuburban(from) || isThessSuburban(to))
                ? FareOffer(fullEur: 0.80, reducedEur: 0.40, product: "Suburban single", op: "OSETH", dynamic: false)
                : FareOffer(fullEur: 0.60, reducedEur: 0.30, product: "Urban single", op: "OSETH", dynamic: false)
        case .patras:
            return FareOffer(fullEur: 1.40, reducedEur: 1.00, product: "Suburban zone ticket", op: "Hellenic Train", dynamic: false)
        default:
            return FareOffer(fullEur: nil, reducedEur: nil, product: "Intercity / regional", op: "Hellenic Train", dynamic: true)
        }
    }
    static func regions(_ st: TransitStation) -> Set<TransitRegion> {
        Set(st.lineIds.compactMap { SyrmosData.line(for: $0)?.region })
    }
    static func isThessSuburban(_ st: TransitStation) -> Bool {
        st.lineIds.contains { id in
            guard let l = SyrmosData.line(for: id) else { return false }
            return l.region == .thessaloniki && l.type == .suburban
        }
    }
    static func isAirport(_ st: TransitStation) -> Bool {
        let n = (st.name + " " + st.nameEl).lowercased()
        return n.contains("airport") || n.contains("αεροδρ")
    }
}

private enum PickTarget: Identifiable {
    case from, to
    var id: Int { self == .from ? 0 : 1 }
}

/// Searchable station picker sheet for the fare planner's From / To fields.
private struct StationPickerSheet: View {
    let stations: [TransitStation]
    let language: AppLanguage
    let onSelect: (TransitStation) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""

    private func label(_ st: TransitStation) -> String {
        language == .greek ? (st.nameEl.isEmpty ? st.name : st.nameEl) : st.name
    }
    private var filtered: [TransitStation] {
        query.isEmpty ? stations : stations.filter { label($0).localizedCaseInsensitiveContains(query) }
    }

    var body: some View {
        NavigationStack {
            List(filtered) { st in
                Button {
                    onSelect(st)
                } label: {
                    Text(label(st)).foregroundStyle(.primary)
                }
            }
            .searchable(text: $query)
            .navigationTitle(language == .greek ? "Σταθμός" : language == .albanian ? "Stacioni" : "Station")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(language == .greek ? "Άκυρο" : language == .albanian ? "Anulo" : "Cancel") { dismiss() }
                }
            }
        }
    }
}
