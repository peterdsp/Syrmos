package com.syrmos.core.model.transit

import com.syrmos.core.model.schedule.SourceConfidence
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Station(
    val id: String,
    val name: String,
    @SerialName("name_el") val nameEl: String,
    @SerialName("name_sq") val nameSq: String? = null,
    val latitude: Double,
    val longitude: Double,
    @SerialName("line_ids") val lineIds: List<String>,
    @SerialName("is_interchange") val isInterchange: Boolean = false,
    val accessibility: Boolean = true,
    val zone: Int = 1,
    val region: Region = Region.ATHENS,
    val sourceConfidence: SourceConfidence = SourceConfidence.SCHEDULED,
)
