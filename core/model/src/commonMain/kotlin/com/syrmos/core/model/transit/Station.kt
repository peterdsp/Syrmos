package com.syrmos.core.model.transit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Station(
    val id: String,
    val name: String,
    @SerialName("name_el") val nameEl: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("line_ids") val lineIds: List<String>,
    @SerialName("is_interchange") val isInterchange: Boolean = false,
    val accessibility: Boolean = true,
    val zone: Int = 1,
)
