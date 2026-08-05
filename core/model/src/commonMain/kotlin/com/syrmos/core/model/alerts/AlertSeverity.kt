package com.syrmos.core.model.alerts

enum class AlertSeverity(val rank: Int) {
    INFO(0),
    WARNING(1),
    CLOSURE(2),
    ;

    companion object {
        fun fromRaw(value: String): AlertSeverity = when (value.lowercase()) {
            "closure" -> CLOSURE
            "warning" -> WARNING
            else -> INFO
        }
    }
}
