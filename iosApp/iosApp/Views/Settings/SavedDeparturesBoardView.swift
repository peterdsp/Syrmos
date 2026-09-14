import SwiftUI

/// Phase N J09 saved-departure board (iOS): the departures the rider has a
/// leave-by reminder for, each with when to leave and its live state, plus a
/// delete action. Reads the shared `SavedDepartureBoard` and derives every label
/// from the shared `LeaveByReminder` engine, so it never invents its own timing.
struct SavedDeparturesBoardView: View {
    @ObservedObject private var board = SavedDepartureBoard.shared
    @ObservedObject private var loc = LocalizationManager.shared

    var body: some View {
        List {
            if board.departures.isEmpty {
                Text(emptyLabel)
                    .font(.body)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(board.departures, id: \.id) { dep in
                    row(dep)
                }
                .onDelete { offsets in
                    for i in offsets { board.remove(id: board.departures[i].id) }
                }
            }
        }
        .navigationTitle(boardTitle)
    }

    @ViewBuilder
    private func row(_ dep: SavedDeparture) -> some View {
        let reminder = dep.toReminder()
        let now = Int64(Date().timeIntervalSince1970)
        let state = reminder.state(now)
        let minutes = reminder.minutesUntilLeave(now)
        VStack(alignment: .leading, spacing: 2) {
            Text("\(dep.lineId) · \(dep.stationName)")
                .font(.body).fontWeight(.semibold)
            let sub = subtitle(dep)
            if !sub.isEmpty {
                Text(sub).font(.caption).foregroundStyle(.secondary)
            }
            Text(stateLabel(state, minutes))
                .font(.caption).fontWeight(.semibold)
                .foregroundStyle(state == .leaveNow ? Color.accentColor : .primary)
        }
    }

    private func subtitle(_ dep: SavedDeparture) -> String {
        var s = ""
        if !dep.destination.isEmpty { s += "\(toLabel) \(dep.destination)" }
        if !dep.scheduledTime.isEmpty { s += (s.isEmpty ? "" : " · ") + dep.scheduledTime }
        return s
    }

    private func stateLabel(_ state: ReminderState, _ minutes: Int) -> String {
        switch state {
        case .leaveNow:
            switch loc.language {
            case .greek: return "Ώρα να φύγεις"
            case .albanian: return "Koha për të nisur"
            case .italian: return "Ora di partire"
            case .english: return "Time to leave"
            }
        case .departed:
            switch loc.language {
            case .greek: return "Αναχώρησε"
            case .albanian: return "U nis"
            case .italian: return "Partito"
            case .english: return "Departed"
            }
        case .scheduled:
            switch loc.language {
            case .greek: return "Φύγε σε \(minutes)'"
            case .albanian: return "Nisu për \(minutes)'"
            case .italian: return "Parti tra \(minutes)'"
            case .english: return "Leave in \(minutes) min"
            }
        }
    }

    private var toLabel: String {
        switch loc.language {
        case .greek: return "προς"
        case .albanian: return "drejt"
        case .italian: return "verso"
        case .english: return "to"
        }
    }
    private var boardTitle: String {
        switch loc.language {
        case .greek: return "Αποθηκευμένες αναχωρήσεις"
        case .albanian: return "Nisjet e ruajtura"
        case .italian: return "Partenze salvate"
        case .english: return "Saved departures"
        }
    }
    private var emptyLabel: String {
        switch loc.language {
        case .greek: return "Καμία υπενθύμιση ακόμη. Πάτησε \"Υπενθύμιση\" σε μια αναχώρηση."
        case .albanian: return "Ende asnjë kujtues. Prek \"Kujto\" te një nisje."
        case .italian: return "Nessun promemoria. Tocca \"Ricorda\" su una partenza."
        case .english: return "No reminders yet. Tap \"Remind\" on a departure."
        }
    }
}
