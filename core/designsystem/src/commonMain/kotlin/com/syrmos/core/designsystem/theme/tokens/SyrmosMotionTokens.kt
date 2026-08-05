package com.syrmos.core.designsystem.theme.tokens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing

/**
 * Motion tokens (design doc section 2, task T2). One signature easing, the
 * "train glide", which decelerates like a train easing into a platform. It is
 * reused for the countdown, list inserts, and route drawing so the whole app
 * moves with one character. Under Reduce Motion it degrades to a cross-fade
 * ([reduceMotionEasing]); the accessibility flag itself is read per platform.
 *
 * [Raw] holds the platform-neutral values (durations in ms, the easing's cubic
 * bezier control points) the Swift / CSS exports read; the [Easing] values are
 * the Compose wrappers.
 */
object SyrmosMotionTokens {

    object Raw {
        const val durationFastMs = 150
        const val durationMediumMs = 300
        const val durationSlowMs = 450
        const val contextSlideMs = 250
        const val liveVehicleMs = 1000

        // "Train glide": a strong deceleration curve (ease-out). CSS:
        // cubic-bezier(0.16, 1, 0.30, 1).
        const val glideX1 = 0.16f
        const val glideY1 = 1.0f
        const val glideX2 = 0.30f
        const val glideY2 = 1.0f
    }

    const val durationFastMs = Raw.durationFastMs
    const val durationMediumMs = Raw.durationMediumMs
    const val durationSlowMs = Raw.durationSlowMs
    const val contextSlideMs = Raw.contextSlideMs
    const val liveVehicleMs = Raw.liveVehicleMs

    /** The signature "train glide" deceleration easing. */
    val trainGlide: Easing = CubicBezierEasing(Raw.glideX1, Raw.glideY1, Raw.glideX2, Raw.glideY2)

    /** What the train glide degrades to under Reduce Motion (a plain cross-fade). */
    val reduceMotionEasing: Easing = LinearEasing
}
