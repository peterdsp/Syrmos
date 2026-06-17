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

    private let sourceURL = URL(string: "https://www.oasa.gr/en/tickets/prices-of-products/")!

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                header
                ForEach(sortedSections, id: \.0) { (section, products) in
                    sectionView(title: sectionTitle(section), products: products)
                }
                if !store.infoLinks.isEmpty {
                    infoLinksSection
                }
                footer
            }
            .padding(.vertical, 16)
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
            Text(loc.language == .greek ? "Τιμές εισιτηρίων OASA" : loc.language == .albanian ? "Çmimet e biletave OASA" : "OASA ticket prices")
                .font(.title3)
                .fontWeight(.semibold)
            Text(loc.language == .greek
                 ? "Συγχρονισμένο από τη επίσημη σελίδα τιμών της OASA. Οι ενημερώσεις γίνονται καθημερινά."
                 : loc.language == .albanian
                 ? "Sinkronizuar nga faqja zyrtare e çmimeve të OASA. Përditësohet çdo ditë."
                 : "Synced from OASA's official prices page. Updated daily.")
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

    private func sectionView(title: String, products: [SyrmosFaresStore.Product]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.headline)
                .padding(.horizontal, 16)
            VStack(spacing: 10) {
                ForEach(products) { product in
                    FareCard(product: product)
                }
            }
            .padding(.horizontal, 16)
        }
    }

    private var sortedSections: [(String, [SyrmosFaresStore.Product])] {
        let order = ["single", "offers", "airport", "passes"]
        let grouped = Dictionary(grouping: store.products) { $0.section }
        return order.compactMap { key in
            guard let items = grouped[key], !items.isEmpty else { return nil }
            return (key, items)
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
                if let full = product.fullPriceEur {
                    Text(String(format: "€%.2f", full))
                        .font(.headline.monospacedDigit())
                        .foregroundStyle(Color.syrmosPrimary)
                }
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
