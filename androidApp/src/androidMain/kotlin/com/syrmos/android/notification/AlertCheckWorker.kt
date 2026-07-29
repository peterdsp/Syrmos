package com.syrmos.android.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.LocalizationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class AlertCheckWorker(
    private val appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        ensureChannels()
        checkServiceAlerts()
        checkWeather()
        return Result.success()
    }

    private suspend fun checkServiceAlerts() {
        if (!NotificationPrefs.serviceAlertsEnabled(appContext)) return
        try {
            val body = fetchUrl(ANNOUNCEMENTS_URL) ?: return
            val json = JSONObject(body)
            val arr = json.optJSONArray("announcements") ?: return
            val currentIds = mutableSetOf<String>()
            val items = mutableListOf<Triple<String, String, String>>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                currentIds.add(id)
                items.add(Triple(
                    id,
                    obj.optString("title", ""),
                    obj.optString("titleEn", ""),
                ))
            }
            val seenIds = NotificationPrefs.loadSeenAlertIds(appContext)
            val newIds = currentIds - seenIds

            if (newIds.isNotEmpty()) {
                val lang = LocalizationManager.language.value
                val newItems = items.filter { it.first in newIds }
                for (item in newItems.take(3)) {
                    postAlert(item.first, item.second, item.third, lang)
                }
                NotificationPrefs.saveSeenAlertIds(appContext, currentIds)
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun checkWeather() {
        if (!NotificationPrefs.weatherAlertsEnabled(appContext)) return
        try {
            val body = fetchUrl(WEATHER_URL) ?: return
            val json = JSONObject(body)
            val current = json.optJSONObject("current") ?: return
            val code = current.optInt("weather_code", 0)
            val temp = current.optDouble("temperature_2m", 0.0)
            if (isSevereWeather(code)) {
                val today = java.time.LocalDate.now().toString()
                val lastDate = NotificationPrefs.lastWeatherNotifDate(appContext)
                if (lastDate != today) {
                    postWeatherAlert(temp, code)
                    NotificationPrefs.setLastWeatherNotifDate(appContext, today)
                }
            }
        } catch (_: Exception) {
        }
    }

    private suspend fun fetchUrl(urlStr: String): String? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.requestMethod = "GET"
            if (conn.responseCode != 200) return@withContext null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    private fun isSevereWeather(code: Int): Boolean {
        return code in setOf(80, 81, 82, 85, 86, 95, 96, 99, 71, 73, 75, 77)
    }

    private fun postAlert(id: String, title: String, titleEn: String, lang: AppLanguage) {
        if (!canPost()) return
        val notifTitle = when (lang) {
            AppLanguage.GREEK -> "Ειδοποιηση Υπηρεσιας"
            AppLanguage.ALBANIAN -> "Njoftim Sherbimi"
            else -> "Service Alert"
        }
        val body = when (lang) {
            AppLanguage.GREEK -> title
            else -> titleEn.ifBlank { title }
        }
        val notification = Notification.Builder(appContext, CHANNEL_SERVICE_ALERTS)
            .setSmallIcon(appContext.applicationInfo.icon)
            .setContentTitle(notifTitle)
            .setContentText(body)
            .setAutoCancel(true)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .build()
        notificationManager.notify(NOTIF_BASE_ALERT + id.hashCode(), notification)
    }

    private fun postWeatherAlert(temp: Double, code: Int) {
        if (!canPost()) return
        val lang = LocalizationManager.language.value
        val tempStr = "${temp.toInt()} C"
        val (title, body) = when {
            code in setOf(95, 96, 99) -> when (lang) {
                AppLanguage.GREEK -> "Καιρικη Ειδοποιηση" to "Καταιγιδα στην περιοχη. $tempStr. Προσεξτε στις μετακινησεις."
                AppLanguage.ALBANIAN -> "Njoftim Moti" to "Stuhi ne zone. $tempStr. Kujdes ne udhetim."
                else -> "Weather Alert" to "Thunderstorm in the area. $tempStr. Take care while traveling."
            }
            code in setOf(71, 73, 75, 77) -> when (lang) {
                AppLanguage.GREEK -> "Καιρικη Ειδοποιηση" to "Χιονοπτωση στην περιοχη. $tempStr. Πιθανες καθυστερησεις."
                AppLanguage.ALBANIAN -> "Njoftim Moti" to "Debore ne zone. $tempStr. Vonesa te mundshme."
                else -> "Weather Alert" to "Snowfall in the area. $tempStr. Possible delays."
            }
            else -> when (lang) {
                AppLanguage.GREEK -> "Καιρικη Ειδοποιηση" to "Εντονες βροχοπτωσεις. $tempStr. Πιθανες καθυστερησεις."
                AppLanguage.ALBANIAN -> "Njoftim Moti" to "Reshje te forta shiu. $tempStr. Vonesa te mundshme."
                else -> "Weather Alert" to "Heavy rain in the area. $tempStr. Possible delays."
            }
        }
        val notification = Notification.Builder(appContext, CHANNEL_WEATHER)
            .setSmallIcon(appContext.applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .build()
        notificationManager.notify(NOTIF_WEATHER, notification)
    }

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            return appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun ensureChannels() {
        if (notificationManager.getNotificationChannel(CHANNEL_SERVICE_ALERTS) == null) {
            val lang = LocalizationManager.language.value
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SERVICE_ALERTS,
                    when (lang) {
                        AppLanguage.GREEK -> "Ειδοποιησεις υπηρεσιας"
                        AppLanguage.ALBANIAN -> "Njoftimet e sherbimit"
                        else -> "Service Alerts"
                    },
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }
        if (notificationManager.getNotificationChannel(CHANNEL_WEATHER) == null) {
            val lang = LocalizationManager.language.value
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_WEATHER,
                    when (lang) {
                        AppLanguage.GREEK -> "Καιρικες ειδοποιησεις"
                        AppLanguage.ALBANIAN -> "Njoftimet e motit"
                        else -> "Weather Alerts"
                    },
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }
        if (notificationManager.getNotificationChannel(CHANNEL_NEARBY) == null) {
            val lang = LocalizationManager.language.value
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_NEARBY,
                    when (lang) {
                        AppLanguage.GREEK -> "Ειδοποιησεις κοντινου σταθμου"
                        AppLanguage.ALBANIAN -> "Njoftimet e stacionit te afert"
                        else -> "Nearby Station Alerts"
                    },
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }
    }

    companion object {
        private const val ANNOUNCEMENTS_URL = "https://api-syrmos.peterdsp.dev/api/announcements"
        private const val WEATHER_URL =
            "https://api.open-meteo.com/v1/forecast?latitude=37.9838&longitude=23.7275&current=temperature_2m,weather_code&timezone=auto"

        private const val CHANNEL_SERVICE_ALERTS = "service_alerts"
        private const val CHANNEL_WEATHER = "weather_alerts"
        private const val CHANNEL_NEARBY = "nearby_alerts"

        private const val NOTIF_BASE_ALERT = 5000
        private const val NOTIF_WEATHER = 5500

        private const val UNIQUE_PERIODIC = "syrmos_alert_check"

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<AlertCheckWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
