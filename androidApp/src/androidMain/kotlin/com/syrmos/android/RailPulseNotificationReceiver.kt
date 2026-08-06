package com.syrmos.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.syrmos.core.common.LocalizationManager
import com.syrmos.core.common.RailPulseLocalStore
import com.syrmos.core.network.CommunityReportService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class RailPulseNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val signal = intent.getStringExtra(EXTRA_SIGNAL) ?: return
        val scopeId = intent.getStringExtra(EXTRA_SCOPE_ID)?.takeIf { it.isNotBlank() } ?: "network:greece"
        val scopeLabel = intent.getStringExtra(EXTRA_SCOPE_LABEL)?.takeIf { it.isNotBlank() } ?: "Greece rail network"
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val service = GlobalContext.get().get<CommunityReportService>()
                val receipt = service.submit(
                    reportId = service.newReportId(),
                    scopeId = scopeId,
                    scopeLabel = scopeLabel,
                    signal = signal,
                    detail = "",
                    locale = LocalizationManager.language.value.code,
                )
                if (receipt?.ok == true) RailPulseLocalStore.recordContribution()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_SIGNAL = "railpulse_signal"
        const val EXTRA_SCOPE_ID = "ichnos_scope_id"
        const val EXTRA_SCOPE_LABEL = "ichnos_scope_label"
    }
}
