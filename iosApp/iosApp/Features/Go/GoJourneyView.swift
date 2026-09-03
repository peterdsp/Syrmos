import SwiftUI
import UIKit

// The GO screen: guide the rider through a planned journey one instruction at a
// time (board / stay on / get off next / change here / arrived). The current
// instruction is the hero; the get-off cue is emphasised because it is the one
// moment that matters most. Advancing is manual for now (step through your
// journey); GPS / live-position auto-advance is a later phase, so the control is
// labelled "Next stop" rather than implying live tracking.
struct GoJourneyView: View {
    @StateObject private var model: GoJourneyViewModel
    @StateObject private var location = LocationService()
    let language: AppLanguage
    private let originName: String
    private let destinationName: String

    init(journey: GuidanceJourney, language: AppLanguage, coords: [String: GoLocationAdvancer.Coord] = [:]) {
        _model = StateObject(wrappedValue: GoJourneyViewModel(journey: journey, coords: coords))
        self.language = language
        self.originName = journey.legs.first?.stops.first?.name ?? ""
        self.destinationName = journey.legs.last?.stops.last?.name ?? ""
    }

    private var tint: Color {
        guard let id = model.currentLineId else { return .accentColor }
        return SyrmosData.line(for: id)?.color ?? .accentColor
    }

    var body: some View {
        VStack(spacing: 20) {
            header
            heroCard
            ProgressView(value: model.progress)
                .tint(tint)
                .padding(.horizontal)
            controls
            if model.canGoLive { liveToggle }
            Spacer()
            footnote
        }
        .padding()
        .navigationTitle("GO")
        .navigationBarTitleDisplayMode(.inline)
        .animation(.easeInOut(duration: 0.2), value: model.position)
        .onAppear {
            model.onGetOffAlert = { guidance in fireGetOff(guidance) }
            Task { await NotificationService.shared.requestAuthorization() }
        }
        .onReceive(location.$currentLocation) { loc in
            if let loc { model.applyLocation(lat: loc.coordinate.latitude, lon: loc.coordinate.longitude) }
        }
    }

    private var liveToggle: some View {
        Button {
            if model.isLive {
                model.stopLive()
            } else {
                location.requestIfNeeded()
                model.startLive()
            }
        } label: {
            Label(
                model.isLive
                    ? t("Live guidance on", "Ζωντανή καθοδήγηση ενεργή", "Udhëzim i drejtpërdrejtë aktiv", "Guida dal vivo attiva")
                    : t("Start live guidance", "Έναρξη ζωντανής καθοδήγησης", "Nis udhëzimin e drejtpërdrejtë", "Avvia guida dal vivo"),
                systemImage: model.isLive ? "location.fill" : "location"
            )
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.bordered)
        .tint(model.isLive ? .green : .accentColor)
    }

    private func fireGetOff(_ guidance: JourneyGuidance) {
        #if canImport(UIKit)
        UINotificationFeedbackGenerator().notificationOccurred(.warning)
        #endif
        switch guidance {
        case let .getOffNext(nextStation, isDestination, transferTo):
            NotificationService.shared.fireGetOffAlert(
                station: nextStation, isDestination: isDestination, transferTo: transferTo, language: language)
        case let .board(_, _, _, nextStation):
            // 2-stop leg: the get-off cue coincides with boarding.
            NotificationService.shared.fireGetOffAlert(
                station: nextStation, isDestination: false, transferTo: nil, language: language)
        default:
            break
        }
    }

    private var header: some View {
        HStack(spacing: 8) {
            Text(originName).fontWeight(.semibold)
            Image(systemName: "arrow.right").font(.caption).foregroundStyle(.secondary)
            Text(destinationName).fontWeight(.semibold)
        }
        .font(.subheadline)
        .foregroundStyle(.secondary)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var heroCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label {
                Text(headline).font(.title2.bold())
            } icon: {
                Image(systemName: icon).font(.title2)
            }
            .foregroundStyle(model.shouldAlert ? Color.white : tint)

            if !detail.isEmpty {
                Text(detail)
                    .font(.headline)
                    .foregroundStyle(model.shouldAlert ? Color.white.opacity(0.9) : .primary)
            }
            if !subdetail.isEmpty {
                Text(subdetail)
                    .font(.subheadline)
                    .foregroundStyle(model.shouldAlert ? Color.white.opacity(0.8) : .secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(model.shouldAlert ? tint : tint.opacity(0.12))
        )
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(headline). \(detail). \(subdetail)")
    }

    private var controls: some View {
        HStack(spacing: 12) {
            Button {
                model.back()
            } label: {
                Label(t("Back", "Πίσω", "Prapa", "Indietro"), systemImage: "chevron.left")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .disabled(!model.canGoBack)

            if model.isArrived {
                Button {
                    model.reset()
                } label: {
                    Label(t("Restart", "Επανεκκίνηση", "Rifillo", "Ricomincia"), systemImage: "arrow.counterclockwise")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(tint)
            } else {
                Button {
                    model.advance()
                } label: {
                    Label(t("Next stop", "Επόμενη στάση", "Ndalesa tjetër", "Fermata succ."), systemImage: "chevron.right")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(tint)
            }
        }
    }

    private var footnote: some View {
        Text(t(
            "Step through your journey. Live get-off alerts as you ride are coming next.",
            "Δες το ταξίδι σου βήμα-βήμα. Οι ζωντανές ειδοποιήσεις αποβίβασης έρχονται σύντομα.",
            "Shiko udhëtimin hap pas hapi. Njoftimet e zbritjes në kohë reale vijnë së shpejti.",
            "Percorri il tuo viaggio passo passo. Gli avvisi di discesa in tempo reale arrivano presto."
        ))
        .font(.footnote)
        .foregroundStyle(.secondary)
        .multilineTextAlignment(.center)
    }

    // MARK: Instruction rendering

    private var icon: String {
        switch model.current {
        case .board: return "figure.walk"
        case .ride: return "tram.fill"
        case .getOffNext: return "figure.walk.departure"
        case .transfer: return "arrow.triangle.swap"
        case .arrived: return "checkmark.circle.fill"
        }
    }

    private var headline: String {
        switch model.current {
        case .board(let line, _, _, _):
            return t("Board \(line)", "Επιβίβαση \(line)", "Hip në \(line)", "Sali su \(line)")
        case .ride(let line, _, _, _):
            return t("Stay on \(line)", "Μείνε στη \(line)", "Qëndro në \(line)", "Resta su \(line)")
        case .getOffNext:
            return t("Get off next", "Αποβίβαση στην επόμενη", "Zbrit në tjetrën", "Scendi alla prossima")
        case .transfer:
            return t("Change here", "Αλλαγή εδώ", "Ndërro këtu", "Cambia qui")
        case .arrived:
            return t("Arrived", "Έφτασες", "Mbërritët", "Arrivato")
        }
    }

    private var detail: String {
        switch model.current {
        case .board(_, let towards, _, _), .ride(_, let towards, _, _):
            return t("toward \(towards)", "προς \(towards)", "drejt \(towards)", "verso \(towards)")
        case .getOffNext(let next, let isDestination, let transferTo):
            if isDestination { return next }
            if let x = transferTo { return t("\(next) → change to \(x)", "\(next) → αλλαγή σε \(x)", "\(next) → ndërro në \(x)", "\(next) → cambia in \(x)") }
            return next
        case .transfer(let at, let to, let towards):
            return t("\(at) → \(to) toward \(towards)", "\(at) → \(to) προς \(towards)", "\(at) → \(to) drejt \(towards)", "\(at) → \(to) verso \(towards)")
        case .arrived(let station):
            return station
        }
    }

    private var subdetail: String {
        switch model.current {
        case .board(_, _, let remaining, let next), .ride(_, _, let remaining, let next):
            let stops = t("\(remaining) stops", "\(remaining) στάσεις", "\(remaining) ndalesa", "\(remaining) fermate")
            return t("\(stops) · next \(next)", "\(stops) · επόμενη \(next)", "\(stops) · tjetra \(next)", "\(stops) · prossima \(next)")
        case .getOffNext(let next, let isDestination, _):
            return isDestination
                ? t("Your destination is next", "Ο προορισμός σου είναι η επόμενη", "Destinacioni yt është tjetra", "La tua destinazione è la prossima")
                : t("Next stop: \(next)", "Επόμενη στάση: \(next)", "Ndalesa tjetër: \(next)", "Prossima fermata: \(next)")
        default:
            return ""
        }
    }

    private func t(_ en: String, _ el: String, _ sq: String, _ it: String) -> String {
        switch language {
        case .greek: return el
        case .albanian: return sq
        case .italian: return it
        default: return en
        }
    }
}
