import SwiftUI
import SafariServices

/// Wraps SFSafariViewController so external links open inside the app as a
/// bottom sheet, never punting the user out to Safari. SFSafariViewController
/// already ships its own "Open in Safari" affordance in the toolbar, so the
/// "open in browser" button the user asked for is built in.
struct SafariSheet: View {
    let url: URL

    var body: some View {
        SafariViewControllerRepresentable(url: url)
            .ignoresSafeArea()
    }
}

private struct SafariViewControllerRepresentable: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        let cfg = SFSafariViewController.Configuration()
        cfg.entersReaderIfAvailable = false
        cfg.barCollapsingEnabled = true
        let vc = SFSafariViewController(url: url, configuration: cfg)
        vc.preferredControlTintColor = UIColor.systemBlue
        vc.dismissButtonStyle = .close
        return vc
    }

    func updateUIViewController(_ uiViewController: SFSafariViewController, context: Context) {}
}

/// Convenience modifier: presents an SFSafariViewController in a sheet bound
/// to an optional URL. Use this on any view that wants to open external URLs
/// without leaving the app.
struct SafariSheetModifier: ViewModifier {
    @Binding var url: URL?

    func body(content: Content) -> some View {
        content.sheet(item: Binding(
            get: { url.map(IdentifiableURL.init) },
            set: { url = $0?.url }
        )) { wrapped in
            SafariSheet(url: wrapped.url)
        }
    }
}

private struct IdentifiableURL: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}

extension View {
    func inAppSafari(url: Binding<URL?>) -> some View {
        modifier(SafariSheetModifier(url: url))
    }
}
