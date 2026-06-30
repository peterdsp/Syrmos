import WidgetKit
import SwiftUI

/// Entry point for the Syrmos widget extension. Hosts the departure-tracking
/// Live Activity (Lock Screen + Dynamic Island). The app side starts and
/// updates the activity; this extension renders it.
@main
struct SyrmosWidgetBundle: WidgetBundle {
    @WidgetBundleBuilder
    var body: some Widget {
        if #available(iOS 16.2, *) {
            SyrmosLiveActivity()
        }
    }
}
