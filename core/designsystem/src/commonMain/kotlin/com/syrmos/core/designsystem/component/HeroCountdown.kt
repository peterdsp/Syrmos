package com.syrmos.core.designsystem.component

import androidx.compose.ui.graphics.Color
import com.syrmos.core.designsystem.theme.tokens.SyrmosColorTokens

data class HeroCountdownState(
    val text: String,
    val isImminent: Boolean,
    val secondsAway: Int,
)

fun heroCountdown(secondsAway: Int, nowLabel: String): HeroCountdownState {
    val imminent = secondsAway <= 60
    val text = when {
        secondsAway <= 0 -> nowLabel
        secondsAway < 120 -> {
            val m = secondsAway / 60
            val s = secondsAway % 60
            "$m:${s.toString().padStart(2, '0')}"
        }
        secondsAway < 3600 -> {
            val m = (secondsAway + 59) / 60
            "$m min"
        }
        else -> {
            val h = secondsAway / 3600
            val m = (secondsAway % 3600) / 60
            if (m == 0) "${h}h" else "${h}h ${m}min"
        }
    }
    return HeroCountdownState(text = text, isImminent = imminent, secondsAway = secondsAway)
}

fun heroCountdownColor(state: HeroCountdownState, lineAccent: Color): Color = when {
    state.isImminent -> SyrmosColorTokens.arrivalImminent
    state.secondsAway <= 300 -> SyrmosColorTokens.arrivalSoon
    else -> lineAccent
}
