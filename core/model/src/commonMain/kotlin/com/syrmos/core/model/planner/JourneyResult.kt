package com.syrmos.core.model.planner

data class JourneyResult(
    val segments: List<JourneySegment>,
    val totalMinutes: Int,
    val transferCount: Int,
)
