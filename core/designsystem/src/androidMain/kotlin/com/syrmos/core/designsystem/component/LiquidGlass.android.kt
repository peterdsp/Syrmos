package com.syrmos.core.designsystem.component

import android.os.Build
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.nativeCanvas

private const val LIQUID_GLASS_AGSL = """
    uniform float2 uSize;
    uniform float uRadius;

    float roundedBoxSDF(float2 p, float2 b, float r) {
        float2 q = abs(p) - b + r;
        return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    }

    half4 main(float2 coord) {
        float2 center = uSize * 0.5;
        float2 p = coord - center;
        float d = roundedBoxSDF(p, center, uRadius);

        // Edge specular highlight, top-biased for natural overhead light
        float edgeBand = smoothstep(3.0, -0.5, abs(d + 0.5));
        float topBias = 1.0 - (coord.y / uSize.y);
        topBias = pow(topBias, 1.2);
        float specular = edgeBand * topBias * 0.55;

        // Dome curvature: subtle center-top brightness
        float2 uv = coord / uSize;
        float dome = 1.0 - length((uv - float2(0.5, 0.35)) * float2(1.0, 1.3));
        dome = clamp(dome, 0.0, 1.0);
        dome = pow(dome, 4.0);
        float domeTint = dome * 0.04;

        // Inner shadow at bottom edge for depth
        float innerShadow = smoothstep(-8.0, 0.0, d) * (coord.y / uSize.y) * 0.1;

        float brightness = specular + domeTint;
        float alpha = max(brightness, innerShadow);

        // Clip to the rounded rect shape
        float mask = 1.0 - smoothstep(-0.5, 0.5, d);
        alpha *= mask;
        brightness *= mask;

        return half4(brightness, brightness, brightness, alpha);
    }
"""

@Suppress("NewApi")
actual fun Modifier.liquidGlassOverlay(cornerRadiusDp: Float): Modifier {
    if (Build.VERSION.SDK_INT < 33) return this
    return this.composed {
        val shader = remember {
            runCatching { android.graphics.RuntimeShader(LIQUID_GLASS_AGSL) }.getOrNull()
        }
        val paint = remember { android.graphics.Paint() }
        if (shader == null) return@composed Modifier
        this.drawWithContent {
            drawContent()
            val radiusPx = cornerRadiusDp * density
            val actualRadius = minOf(radiusPx, minOf(size.width, size.height) / 2f)
            shader.setFloatUniform("uSize", size.width, size.height)
            shader.setFloatUniform("uRadius", actualRadius)
            paint.shader = shader
            drawContext.canvas.nativeCanvas.drawRect(
                0f, 0f, size.width, size.height, paint,
            )
        }
    }
}
