package com.syrmos.core.model.schedule

import kotlinx.serialization.Serializable

@Serializable
data class Departure(
    val time: String,
    val notes: String? = null,
)
