import SwiftUI

/// Settings → "Athens metropolitan area railways". Shows the bundled
/// transit map JPEG so users can browse the full metro / tram /
/// suburban network offline. Presented as a modal sheet with its own
/// header and a close button. The map is shown inside a UIScrollView
/// that supports pinch-zoom and pan, same gesture model as Apple Maps.
struct StasyMapView: View {
    @ObservedObject private var loc = LocalizationManager.shared
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Group {
                if let uiImage = UIImage(named: "AthensRailMap") {
                    ZoomableImage(image: uiImage)
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
            .navigationTitle(loc.language == .greek ? "Σιδηροδρομικό δίκτυο Αθήνας" : "Athens metropolitan area railways")
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

/// SwiftUI bridge to the UIScrollView + UIImageView pair below.
private struct ZoomableImage: UIViewRepresentable {
    let image: UIImage
    func makeUIView(context: Context) -> ZoomableImageView {
        let view = ZoomableImageView()
        view.image = image
        return view
    }
    func updateUIView(_ uiView: ZoomableImageView, context: Context) {}
}

/// UIScrollView + UIImageView with the standard delegate plumbing for
/// pinch-zoom and pan. Initial zoom fills the available space edge-to-
/// edge so the map never letterboxes the view; the user can still pinch
/// out to see the whole network or pinch in up to 6x to read station
/// names.
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
        let widthRatio = bounds.width / image.size.width
        let heightRatio = bounds.height / image.size.height
        let minZoom = min(widthRatio, heightRatio)
        let fillZoom = max(widthRatio, heightRatio)
        scrollView.minimumZoomScale = minZoom
        if scrollView.zoomScale < minZoom || scrollView.zoomScale > scrollView.maximumZoomScale {
            scrollView.zoomScale = fillZoom
        } else if scrollView.zoomScale == 1.0 {
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
