package io.github.chenlongapps.modelatlas.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.abs

/** The capsule catches a soft pool of light centered on the moving lens. */
internal fun Modifier.navigationPressLight(
    tabs: NavigationTabGeometry,
    motion: NavigationLensMotion,
    press: State<Float>,
): Modifier = clip(CircleShape).drawWithCache {
    val radius = size.height * 1.5f
    val brush = Brush.radialGradient(
        0f to Color.White,
        0.5f to Color.White,
        1f to Color.Transparent,
        center = Offset.Zero,
        radius = radius,
    )
    onDrawBehind {
        val progress = press.value.coerceIn(0f, 1f)
        if (progress > 0f) {
            val x = tabs.logicalX(tabs.center(motion.position))
            drawRect(Color.White, alpha = 0.08f * progress, blendMode = BlendMode.Plus)
            translate(x, center.y) {
                drawCircle(brush, radius, Offset.Zero, alpha = 0.15f * progress, blendMode = BlendMode.Plus)
            }
        }
    }
}

/** Layer order and optical values are based on the AndroidLiquidGlass catalog and AM++ demo. */
@Composable
internal fun Modifier.navigationLens(
    backdrop: Backdrop?,
    motion: NavigationLensMotion,
    press: State<Float>,
    lift: State<Float>,
    surfaceInk: Color,
    fallbackColor: Color,
): Modifier = if (backdrop == null) {
    clip(CircleShape).background(fallbackColor)
} else {
    then(remember(backdrop, motion, press, lift, surfaceInk) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { CircleShape },
            effects = {
                val progress = press.value.coerceIn(0f, 1f)
                lens(
                    (5f + 5f * progress).dp.toPx().coerceAtMost(size.minDimension * 0.45f),
                    (8f + 6f * progress).dp.toPx(),
                    chromaticAberration = true,
                )
            },
            highlight = { Highlight.Default.copy(alpha = 0.45f + 0.55f * press.value.coerceIn(0f, 1f)) },
            shadow = { Shadow(radius = 12.dp, alpha = 0.2f + 0.8f * press.value.coerceIn(0f, 1f)) },
            innerShadow = { InnerShadow(radius = 8.dp, alpha = press.value.coerceIn(0f, 1f)) },
            layerBlock = {
                val expansion = lift.value.coerceAtLeast(0f)
                val stretch = (abs(motion.velocity) / 10f * press.value).coerceIn(0f, 0.2f)
                // Preserve the center and let Backdrop compensate the source for this transform.
                scaleX = (1f + 0.24f * expansion) / (1f - 0.75f * stretch)
                scaleY = (1f + 0.22f * expansion) * (1f - 0.5f * stretch)
            },
            onDrawSurface = {
                val progress = press.value.coerceIn(0f, 1f)
                drawRect(surfaceInk, alpha = 0.10f * (1f - progress) + 0.03f * progress)
            },
        )
    })
}
