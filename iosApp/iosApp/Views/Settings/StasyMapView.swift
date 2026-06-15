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

/// We render the system-map PDF to a high-resolution UIImage at view
/// load and show it inside a UIScrollView with pinch + pan. PDFKit's
/// PDFView always biased the initial layout with internal page padding,
/// so the user landed on whitespace above the actual map content. The
/// image+scrollview approach gives full control: zero padding, true
/// fill-to-view at min zoom, pinch up to 6x, smooth pan in any
/// direction.
private struct StasyMapPDF: UIViewRepresentable {
    let url: URL

    func makeUIView(context: Context) -> ZoomableImageView {
        let view = ZoomableImageView()
        view.image = Self.renderPDF(url: url)
        return view
    }

    func updateUIView(_ uiView: ZoomableImageView, context: Context) {}

    /// Single-page render at 3x logical resolution so the map stays
    /// crisp at maximum zoom. The PDF is ~1 MB, the resulting image is
    /// ~6-8 MB in memory — comfortable on every supported device.
    private static func renderPDF(url: URL) -> UIImage? {
        guard let document = PDFDocument(url: url),
              let page = document.page(at: 0) else { return nil }
        let bounds = page.bounds(for: .mediaBox)
        let scale: CGFloat = 3.0
        let pixelSize = CGSize(width: bounds.width * scale, height: bounds.height * scale)
        let renderer = UIGraphicsImageRenderer(size: pixelSize)
        return renderer.image { context in
            UIColor.systemBackground.setFill()
            context.fill(CGRect(origin: .zero, size: pixelSize))
            context.cgContext.translateBy(x: 0, y: pixelSize.height)
            context.cgContext.scaleBy(x: scale, y: -scale)
            page.draw(with: .mediaBox, to: context.cgContext)
        }
    }
}

/// UIScrollView + UIImageView with the standard delegate plumbing for
/// pinch-zoom and pan, plus a min-zoom that fills the available space
/// edge-to-edge so the image never letterboxes the view.
final class ZoomableImageView: UIView, UIScrollViewDelegate {
    private let scrollView = UIScrollView()
    private let imageView = UIImageView()

    var image: UIImage? {
        didSet {
            imageView.image = image
            if let image = image {
                imageView.frame = CGRect(origin: .zero, size: image.size)
                scrollView.contentSize = image.size
                setNeedsLayout()
            }
        }
    }

    override init(frame: CGRect) {
        super.init(frame: frame)
        scrollView.delegate = self
        scrollView.maximumZoomScale = 6.0
        scrollView.minimumZoomScale = 1.0
        scrollView.bouncesZoom = true
        scrollView.showsHorizontalScrollIndicator = false
        scrollView.showsVerticalScrollIndicator = false
        scrollView.contentInsetAdjustmentBehavior = .never
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        imageView.contentMode = .scaleAspectFit
        scrollView.addSubview(imageView)
        addSubview(scrollView)
        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: topAnchor),
            scrollView.bottomAnchor.constraint(equalTo: bottomAnchor),
            scrollView.leadingAnchor.constraint(equalTo: leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: trailingAnchor),
        ])
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func layoutSubviews() {
        super.layoutSubviews()
        guard let image = image, bounds.width > 0, bounds.height > 0 else { return }
        // Fill the view edge-to-edge as the minimum zoom: pick the
        // larger of width/height ratios so neither axis letterboxes.
        // The user can still pinch out to fit-inside; we don't enforce
        // a hard minimum that hides part of the map.
        let widthRatio = bounds.width / image.size.width
        let heightRatio = bounds.height / image.size.height
        let minZoom = min(widthRatio, heightRatio)
        let fillZoom = max(widthRatio, heightRatio)
        scrollView.minimumZoomScale = minZoom
        if scrollView.zoomScale < minZoom || scrollView.zoomScale > scrollView.maximumZoomScale {
            scrollView.zoomScale = fillZoom
        } else if scrollView.zoomScale == 1.0 {
            // Initial layout pass — match fill so the image fills the view.
            scrollView.zoomScale = fillZoom
        }
        centerContent()
    }

    private func centerContent() {
        let boundsSize = scrollView.bounds.size
        var frameToCenter = imageView.frame
        frameToCenter.origin.x = max(0, (boundsSize.width - frameToCenter.size.width) / 2)
        frameToCenter.origin.y = max(0, (boundsSize.height - frameToCenter.size.height) / 2)
        imageView.frame = frameToCenter
    }

    func viewForZooming(in scrollView: UIScrollView) -> UIView? { imageView }
    func scrollViewDidZoom(_ scrollView: UIScrollView) { centerContent() }
}
