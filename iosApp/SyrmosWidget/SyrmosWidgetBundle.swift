import WidgetKit
import SwiftUI

/// Entry point for the Syrmos widget extension. Hosts the departure-tracking
/// Live Activity (Lock Screen + Dynamic Island) and the configurable
/// next-departures home-screen widget. The app side starts and updates the
/// Live Activity; the home-screen widget projects offline from the bundled
/// seed schedules.
@main
struct SyrmosWidgetBundle: WidgetBundle {
    @WidgetBundleBuilder
    var body: some Widget {
        if #available(iOS 16.2, *) {
            SyrmosLiveActivity()
        }
        if #available(iOS 17.0, *) {
            NextDeparturesWidget()
        }
    }
}
