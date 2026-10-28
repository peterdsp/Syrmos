import SwiftUI
import CoreLocation

/// 3.0.2 notification settings: announcements from Syrmos (push), nearby next
/// trains (opt-in, needs Always location) and calendar leave-by reminders.
/// UserDefaults-backed, so the choices survive the scene rebuild on foreground.
struct TravelNotificationSettings: View {
    @ObservedObject private var loc = LocalizationManager.shared
    @ObservedObject private var geofence = StationGeofenceService.shared
    @ObservedObject private var calendar = CalendarTripStore.shared
    @State private var broadcasts = NotificationPreferences.broadcastsEnabled
    @State private var nearbyTrains = NotificationPreferences.nearbyTrainsEnabled
    @State private var calendarOn = NotificationPreferences.calendarRemindersEnabled
    @State private var checklist = NotificationPreferences.calendarTypeEnabled(.flightChecklist)
    @State private var headsUp = NotificationPreferences.calendarTypeEnabled(.headsUp)
    @State private var leaveNow = NotificationPreferences.calendarTypeEnabled(.leaveNow)
    @State private var flightBuffer = NotificationPreferences.flightBufferMinutes

    var body: some View {
        let lang = loc.language
        Section {
            Toggle(isOn: $broadcasts) {
                Label(BroadcastCopy.settingsToggle(lang), systemImage: "megaphone")
            }
            .onChange(of: broadcasts) { _, on in
                NotificationPreferences.broadcastsEnabled = on
                if on {
                    Task {
                        await NotificationService.shared.requestAuthorization()
                        PushRegistrar.shared.start()
                    }
                } else {
                    PushRegistrar.shared.unregister()
                }
            }
        } footer: {
            Text(BroadcastCopy.settingsFooter(lang))
        }

        Section {
            Toggle(isOn: $nearbyTrains) {
                Label(NearbyCopy.settingsToggle(lang), systemImage: "tram.circle")
            }
            .onChange(of: nearbyTrains) { _, on in
                if on {
                    Task { await NotificationService.shared.requestAuthorization() }
                    geofence.enable()
                } else {
                    NotificationPreferences.nearbyTrainsEnabled = false
                    geofence.stop()
                }
            }
            if nearbyTrains && geofence.authorization != .authorizedAlways && geofence.authorization != .notDetermined {
                Button(NearbyCopy.needsAlwaysHint(lang)) {
                    if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                }
                .font(.footnote)
            }
        } footer: {
            Text(NearbyCopy.settingsFooter(lang))
        }

        Section {
            Toggle(isOn: $calendarOn) {
                Label(CalendarCopy.settingsToggle(lang), systemImage: "calendar.badge.clock")
            }
            .onChange(of: calendarOn) { _, on in
                NotificationPreferences.calendarRemindersEnabled = on
                Task {
                    if on {
                        await NotificationService.shared.requestAuthorization()
                        if !calendar.isAuthorized { await calendar.connect() }
                    }
                    await calendar.replan()
                }
            }
            if calendarOn {
                Toggle(CalendarCopy.toggleChecklist(lang), isOn: $checklist)
                    .onChange(of: checklist) { _, v in setType(NotificationPreferences.calendarChecklistKey, v) }
                Toggle(CalendarCopy.toggleHeadsUp(lang), isOn: $headsUp)
                    .onChange(of: headsUp) { _, v in setType(NotificationPreferences.calendarHeadsUpKey, v) }
                Toggle(CalendarCopy.toggleLeaveNow(lang), isOn: $leaveNow)
                    .onChange(of: leaveNow) { _, v in setType(NotificationPreferences.calendarLeaveNowKey, v) }
                Picker(CalendarCopy.flightBuffer(lang), selection: $flightBuffer) {
                    ForEach(CalendarLeaveBy.flightBufferChoices, id: \.self) { m in
                        Text("\(m)'").tag(m)
                    }
                }
                .onChange(of: flightBuffer) { _, v in
                    NotificationPreferences.flightBufferMinutes = v
                    replanFromScratch()
                }
            }
        } footer: {
            Text(CalendarCopy.settingsFooter(lang))
        }
    }

    private func setType(_ key: String, _ value: Bool) {
        UserDefaults.standard.set(value, forKey: key)
        replanFromScratch()
    }

    /// A settings change alters which alarms exist, not just their time, so drop
    /// the schedule and plan again.
    private func replanFromScratch() {
        Task {
            calendar.cancelAll()
            await calendar.replan()
        }
    }
}
