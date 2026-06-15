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
        view.displayMode = .singlePage
        view.displayDirection = .vertical
        view.backgroundColor = .systemBackground
        view.autoScales = true
        view.maxScaleFactor = 6.0
        // PDFView's scaleFactorForSizeToFit returns garbage at init time
        // because the view has no bounds yet. The PDF renders at its
        // native page size and the user lands looking at the top-left
        // corner of a giant transit map. Defer the scale-to-fit to the
        // next run loop tick when the layout pass has settled, then
        // again when the bounds change (e.g. on rotation).
        DispatchQueue.main.async {
            applyFitScale(view)
        }
        NotificationCenter.default.addObserver(
            forName: .PDFViewVisiblePagesChanged,
            object: view,
            queue: .main
        ) { _ in applyFitScale(view) }
        return view
    }

    func updateUIView(_ uiView: PDFView, context: Context) {
        DispatchQueue.main.async {
            applyFitScale(uiView)
        }
    }

    private func applyFitScale(_ view: PDFView) {
        let fit = view.scaleFactorForSizeToFit
        guard fit > 0 else { return }
        view.minScaleFactor = fit
        // Only snap back to fit while the user hasn't zoomed in past it.
        // If they pinched to 2x, don't yank them back to 1x on rotation.
        if view.scaleFactor < fit * 1.01 {
            view.scaleFactor = fit
        }
    }
}
