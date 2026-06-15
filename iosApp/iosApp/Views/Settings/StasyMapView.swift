import SwiftUI
import PDFKit

/// Settings → "System map". Renders the official STASY 2022 system
/// map PDF that ships in the bundle so users can browse the full
/// metro / tram / suburban network offline. Single-page document,
/// presented as a sheet (modal popup) with its own header and a
/// close button rather than pushed onto the Settings navigation
/// stack. PDFKit gives pinch-zoom and pan for free.
struct StasyMapView: View {
    @ObservedObject private var loc = LocalizationManager.shared
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Group {
                if let url = Bundle.main.url(forResource: "StasySystemMap", withExtension: "pdf") {
                    StasyMapPDF(url: url)
                        .ignoresSafeArea(edges: .bottom)
                } else {
                    VStack(spacing: 12) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(.largeTitle)
                            .foregroundStyle(.secondary)
                        Text(loc.language == .greek ? "Δεν βρέθηκε ο χάρτης." : "Map not found in bundle.")
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .navigationTitle(loc.language == .greek ? "Χάρτης δικτύου" : "System map")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(loc.language == .greek ? "Κλείσιμο" : "Close") {
                        dismiss()
                    }
                }
            }
        }
        .presentationDragIndicator(.visible)
    }
}

private struct StasyMapPDF: UIViewRepresentable {
    let url: URL

    func makeUIView(context: Context) -> PDFView {
        let view = PDFView()
        view.document = PDFDocument(url: url)
        // singlePageContinuous + horizontal lets the user scroll/pan
        // around the over-sized map smoothly. With plain .singlePage
        // mode the view padlocks to a centered fit and bands the
        // screen with white space above and below the page.
        view.displayMode = .singlePageContinuous
        view.displayDirection = .vertical
        view.backgroundColor = .systemBackground
        view.autoScales = false
        view.maxScaleFactor = 6.0
        DispatchQueue.main.async {
            applyFillScale(view)
        }
        NotificationCenter.default.addObserver(
            forName: .PDFViewVisiblePagesChanged,
            object: view,
            queue: .main
        ) { _ in applyFillScale(view) }
        return view
    }

    func updateUIView(_ uiView: PDFView, context: Context) {
        DispatchQueue.main.async {
            applyFillScale(uiView)
        }
    }

    /// Initial display strategy: use the scale that makes the page FILL
    /// the view (the larger of the width/height ratios), not the one
    /// that makes it fit-inside (the smaller). The Athens map PDF is
    /// wider than the iPhone screen aspect, so fit-inside left huge
    /// white bands above and below. Fill-the-view means the user can
    /// pan to see the cropped sides — the same gesture model as Apple
    /// Maps. Min scale stays at fit-inside so the user can always pinch
    /// out to see the whole network.
    private func applyFillScale(_ view: PDFView) {
        guard let page = view.document?.page(at: 0) else { return }
        let pageRect = page.bounds(for: .cropBox)
        let viewBounds = view.bounds
        guard viewBounds.width > 0, viewBounds.height > 0,
              pageRect.width > 0, pageRect.height > 0 else { return }
        let widthScale = viewBounds.width / pageRect.width
        let heightScale = viewBounds.height / pageRect.height
        let fitScale = min(widthScale, heightScale)
        let fillScale = max(widthScale, heightScale)
        view.minScaleFactor = fitScale
        // Don't yank the user back to fill scale if they've pinched
        // beyond fit. The threshold check leaves their zoom alone once
        // they've scaled past 110% of fit-inside.
        if view.scaleFactor <= fitScale * 1.1 {
            view.scaleFactor = fillScale
        }
    }
}
