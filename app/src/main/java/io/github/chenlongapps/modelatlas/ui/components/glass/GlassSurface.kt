package io.github.chenlongapps.modelatlas.ui.components.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

internal data class GlassStyle(
    val blurRadius: Dp = 4.dp,
    val lens: Lens? = Lens(16.dp, 32.dp),
    val vibrancy: Boolean = true,
    val tintAlpha: Float = 0.5f,
) {
    data class Lens(
        val refractionHeight: Dp,
        val refractionAmount: Dp,
        val chromaticAberration: Boolean = true,
    )

    companion object {
        val Standard = GlassStyle()
        // 光学参数不变，通过更实的着色弱化背后内容。
        val Subtle = Standard.copy(tintAlpha = 0.72f)
        val BottomBar = Standard.copy(lens = Lens(24.dp, 24.dp, chromaticAberration = false))
    }
}

/** 仅负责裁剪和绘制。无采样源或不支持特效时使用不透明的主题底色。 */
@Composable
internal fun Modifier.glassSurface(
    backdrop: Backdrop?,
    shape: Shape,
    style: GlassStyle = GlassStyle.Standard,
    tint: Color = MaterialTheme.colorScheme.background,
    fallbackColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    animateTint: Boolean = false,
): Modifier {
    if (backdrop == null || !glassEffectsAvailable()) {
        val fallback by glassTint(fallbackColor.copy(alpha = 1f), animateTint)
        return clip(shape).drawBehind { drawRect(fallback) }
    }
    // 着色在绘制阶段更新；切换底栏模式或发送状态不必重新配置折射和模糊。
    val overlay by glassTint(tint.copy(alpha = style.tintAlpha), animateTint)
    val blurRadius = style.blurRadius
    // Backdrop 2.0 的 lens 不接受任意 Shape；其它形状保留裁剪、模糊和着色。
    val refraction = style.lens?.takeIf { shape is RoundedCornerShape || shape is AbsoluteRoundedCornerShape }
    val vibrant = style.vibrancy
    return then(remember(backdrop, shape, blurRadius, refraction, vibrant) {
        Modifier.clip(shape).drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                if (vibrant) vibrancy()
                blur(blurRadius.toPx())
                if (refraction != null) {
                    lens(
                        // Keep the 2.0.0 shader's active band clear of the capsule's medial axis.
                        refraction.refractionHeight.toPx().coerceAtMost(size.minDimension * 0.45f),
                        refraction.refractionAmount.toPx(),
                        chromaticAberration = refraction.chromaticAberration,
                    )
                }
            },
            onDrawSurface = { drawRect(overlay) },
        )
    })
}

@Composable
private fun glassTint(target: Color, animate: Boolean) =
    if (animate) animateColorAsState(target, tween(220), label = "Glass tint") else rememberUpdatedState(target)

@Composable
internal fun GlassSurface(
    backdrop: Backdrop?,
    shape: Shape,
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassStyle.Standard,
    tint: Color = MaterialTheme.colorScheme.background,
    fallbackColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.glassSurface(backdrop, shape, style, tint, fallbackColor),
        contentAlignment = contentAlignment,
        content = content,
    )
}
