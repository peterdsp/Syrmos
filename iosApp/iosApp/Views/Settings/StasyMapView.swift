import SwiftUI
import PDFKit

/// Settings → "System map". Renders the official STASY 2022 system
/// map PDF that ships in the bundle so users can browse the full
/// metro / tram / suburban network offline. Single-page document.
struct StasyMapView: View {
    @EnvironmentObject private var loc: LocalizationManager

    var body: some View {
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
    }
}

private struct StasyMapPDF: UIViewRepresentable {
    let url: URL
    func makeUIView(context: Context) -> PDFView {
        let view = PDFView()
        view.document = PDFDocument(url: url)
        view.autoScales = true
        view.displayMode = .singlePage
        view.displayDirection = .vertical
        view.backgroundColor = .systemBackground
        view.maxScaleFactor = 6.0
        view.minScaleFactor = view.scaleFactorForSizeToFit
        return view
    }
    func updateUIView(_ uiView: PDFView, context: Context) {}
}
