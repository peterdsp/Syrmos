package com.syrmos.core.designsystem.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.syrmos.core.designsystem.theme.tokens.SyrmosMotionTokens
import kotlinx.coroutines.launch

private const val STAGGER_DELAY_MS = 40
private const val STAGGER_CAP = 8
private const val TRANSLATE_Y_DP = 12f

fun <T> trainGlideSpec(durationMs: Int = SyrmosMotionTokens.durationMediumMs): AnimationSpec<T> =
    tween(durationMillis = durationMs, easing = SyrmosMotionTokens.trainGlide)

fun Modifier.staggeredEntrance(index: Int): Modifier = composed {
    val cappedIndex = index.coerceAtMost(STAGGER_CAP)
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(TRANSLATE_Y_DP) }

    LaunchedEffect(Unit) {
        val delay = cappedIndex * STAGGER_DELAY_MS
        val spec = tween<Float>(
            durationMillis = SyrmosMotionTokens.durationSlowMs,
            delayMillis = delay,
            easing = SyrmosMotionTokens.trainGlide,
        )
        launch { alpha.animateTo(1f, spec) }
        launch { offsetY.animateTo(0f, spec) }
    }

    graphicsLayer {
        this.alpha = alpha.value
        this.translationY = offsetY.value * density
    }
}

fun Modifier.livePulse(): Modifier = composed {
    val transition = rememberInfiniteTransition()
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2000
                1f at 0
                1.15f at 1000
                1f at 2000
            },
        ),
    )
    val pulseAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2000
                1f at 0
                0.7f at 1000
                1f at 2000
            },
        ),
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
        alpha = pulseAlpha
    }
}
