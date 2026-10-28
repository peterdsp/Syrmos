package com.syrmos.android.push

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.syrmos.android.BuildConfig
import com.syrmos.core.common.Broadcasts
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.common.MapPreferences
import com.syrmos.core.common.NotificationSettings
import com.syrmos.core.data.repository.FavoritesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.koin.core.context.GlobalContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Firebase Cloud Messaging for "Announcements from Syrmos", without a
 * google-services.json and without the google-services Gradle plugin.
 *
 * [init] builds [FirebaseOptions] from the BuildConfig fields filled from
 * local.properties or the environment (FCM_PROJECT_ID, FCM_APP_ID, FCM_API_KEY,
 * FCM_SENDER_ID). When any is empty, push stays off and the app relies on the
 * polling fallback in AlertCheckWorker; nothing else changes.
 *
 * Registration follows docs/3.0.2-CONTRACTS.md: anonymous token, platform,
 * language and topics (all, region, favourite lines and stations). It re-registers
 * when the token or payload changes (language, favourites, region) and otherwise at
 * most once a day.
 */
object SyrmosPush {
    private const val TAG = "SyrmosPush"
    private const val BASE_URL = "https://api-syrmos.peterdsp.dev"
    private const val PREFS = "syrmos_prefs"
    private const val KEY_LAST_PAYLOAD = "push_last_payload"
    private const val KEY_LAST_AT = "push_last_register_at"
    private const val KEY_TOKEN = "push_token"
    private const val DAY_MS = 24L * 3600 * 1000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var isEnabled: Boolean = false
        private set

    fun configured(): Boolean = listOf(
        BuildConfig.FCM_PROJECT_ID, BuildConfig.FCM_APP_ID, BuildConfig.FCM_API_KEY, BuildConfig.FCM_SENDER_ID,
    ).all { it.isNotBlank() }

    /** Initialises FirebaseApp manually. Safe to call more than once. */
    fun init(context: Context): Boolean {
        if (isEnabled) return true
        if (!configured()) {
            Log.i(TAG, "FCM values not set; push disabled, polling only")
            return false
        }
        return try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setProjectId(BuildConfig.FCM_PROJECT_ID)
                    .setApplicationId(BuildConfig.FCM_APP_ID)
                    .setApiKey(BuildConfig.FCM_API_KEY)
                    .setGcmSenderId(BuildConfig.FCM_SENDER_ID)
                    .build()
                FirebaseApp.initializeApp(context.applicationContext, options)
            }
            isEnabled = true
            true
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseApp init failed; push disabled", e)
            false
        }
    }

    /** Fetches the current token and registers it when needed. */
    fun sync(context: Context, force: Boolean = false) {
        if (!isEnabled || !NotificationSettings.broadcasts.value) return
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (!token.isNullOrBlank()) register(context, token, force)
            }
        }
    }

    /** Called from onNewToken and [sync]. */
    fun register(context: Context, token: String, force: Boolean = false) {
        if (!NotificationSettings.broadcasts.value) return
        val app = context.applicationContext
        scope.launch {
            val payload = payload(app, token)
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val unchanged = prefs.getString(KEY_LAST_PAYLOAD, null) == payload &&
                now - prefs.getLong(KEY_LAST_AT, 0) < DAY_MS
            if (unchanged && !force) return@launch
            if (post("$BASE_URL/api/push/register", payload)) {
                prefs.edit().putString(KEY_LAST_PAYLOAD, payload).putLong(KEY_LAST_AT, now)
                    .putString(KEY_TOKEN, token).apply()
            }
        }
    }

    /** Turning "Announcements from Syrmos" off: tell the server to forget the token. */
    fun unregister(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        scope.launch {
            val token = prefs.getString(KEY_TOKEN, null)
                ?: runCatching { if (isEnabled) com.google.android.gms.tasks.Tasks.await(FirebaseMessaging.getInstance().token) else null }.getOrNull()
                ?: return@launch
            if (post("$BASE_URL/api/push/unregister", JSONObject().put("token", token).toString())) {
                prefs.edit().remove(KEY_LAST_PAYLOAD).remove(KEY_LAST_AT).apply()
            }
        }
    }

    internal fun payload(context: Context, token: String): String {
        val favs = runCatching {
            GlobalContext.get().get<FavoritesRepository>().favoriteStationIds()
        }.getOrDefault(emptyList())
        val topics = Broadcasts.pushTopics(favs, MapPreferences.defaultRegion.value)
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
        return JSONObject()
            .put("token", token)
            .put("platform", "android")
            .put("environment", "production")
            .put("lang", LocalizationManager.language.value.code)
            .put("topics", JSONArray(topics))
            .put("appVersion", version)
            .toString()
    }

    private fun post(url: String, body: String): Boolean = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        conn.disconnect()
        code in 200..299
    } catch (_: Exception) {
        false
    }
}
