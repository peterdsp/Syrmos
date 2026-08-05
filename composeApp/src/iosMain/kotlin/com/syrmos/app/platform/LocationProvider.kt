package com.syrmos.app.platform

import com.syrmos.core.model.location.UserLocation
import platform.Foundation.NSUserDefaults

actual suspend fun requestUserLocation(): UserLocation? {
    // iOS uses native SwiftUI LocationService, not this KMP path
    return null
}

actual suspend fun requestLocationPermission() {
    // iOS onboarding is the native SwiftUI flow which owns the permission UI.
}

private const val ONBOARDING_KEY = "syrmos.onboarding.completed.v1"

actual fun readOnboardingCompleted(): Boolean =
    NSUserDefaults.standardUserDefaults.boolForKey(ONBOARDING_KEY)

actual fun markOnboardingCompleted() {
    NSUserDefaults.standardUserDefaults.setBool(true, ONBOARDING_KEY)
}

private const val WHATS_NEW_KEY = "syrmos.whatsnew.version"

actual fun readLastWhatsNewVersion(): String? =
    NSUserDefaults.standardUserDefaults.stringForKey(WHATS_NEW_KEY)

actual fun markWhatsNewSeen(version: String) {
    NSUserDefaults.standardUserDefaults.setObject(version, WHATS_NEW_KEY)
}

actual fun consumePendingAssistantQuery(): String? = null

actual fun consumePendingNotificationDeepLink(): Pair<String, String?>? = null

private const val SELECTED_TAB_KEY = "syrmos.selectedTab"
private const val SELECTED_DESKTOP_SECTION_KEY = "syrmos.selectedDesktopSection"

actual fun readSelectedTabId(): String? =
    NSUserDefaults.standardUserDefaults.stringForKey(SELECTED_TAB_KEY)

actual fun writeSelectedTabId(tabId: String) {
    NSUserDefaults.standardUserDefaults.setObject(tabId, SELECTED_TAB_KEY)
}

actual fun readSelectedDesktopSectionId(): String? =
    NSUserDefaults.standardUserDefaults.stringForKey(SELECTED_DESKTOP_SECTION_KEY)

actual fun writeSelectedDesktopSectionId(sectionId: String) {
    NSUserDefaults.standardUserDefaults.setObject(sectionId, SELECTED_DESKTOP_SECTION_KEY)
}

actual suspend fun requestNotificationPermission() {
    // iOS uses native SwiftUI onboarding which calls NotificationService directly.
}
