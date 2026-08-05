package com.syrmos.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RailPulseNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val signal = intent.getStringExtra(EXTRA_SIGNAL) ?: return
        val prefs = context.getSharedPreferences("syrmos_prefs", Context.MODE_PRIVATE)
        val confirmed = prefs.getString("railpulse_confirmed", "347")?.toIntOrNull() ?: 347
        val thisWeek = prefs.getString("railpulse_week", "28")?.toIntOrNull() ?: 28
        prefs.edit()
            .putString("railpulse_confirmed", (confirmed + 1).toString())
            .putString("railpulse_week", (thisWeek + 1).toString())
            .putString("railpulse_last_signal", signal)
            .putLong("railpulse_last_signal_epoch", System.currentTimeMillis())
            .apply()
    }

    companion object {
        const val EXTRA_SIGNAL = "railpulse_signal"
    }
}
