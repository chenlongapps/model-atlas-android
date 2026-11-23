package io.github.chenlongapps.modelatlas.ui.components.glass

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.BlendMode
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
    val hueTint: Boolean = false,
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
        val Indicator = GlassStyle(
            lens = Lens(10.dp, 14.dp),
            vibrancy = false,
            tintAlpha = 0.75f,
            hueTint = true,
        )
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
): Modifier {
    if (backdrop == null || !glassEffectsAvailable()) {
        return clip(shape).background(fallbackColor.copy(alpha = 1f))
    }
    // 着色在绘制阶段更新；切换底栏模式或发送状态不必重新配置折射和模糊。
    val overlay by rememberUpdatedState(tint.copy(alpha = style.tintAlpha))
    val hue by rememberUpdatedState(if (style.hueTint) tint.copy(alpha = 1f) else Color.Unspecified)
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
                        refraction.refractionHeight.toPx(),
                        refraction.refractionAmount.toPx(),
                        chromaticAberration = refraction.chromaticAberration,
                    )
                }
            },
            onDrawSurface = {
                val hueColor = hue
                if (hueColor != Color.Unspecified) drawRect(hueColor, blendMode = BlendMode.Hue)
                drawRect(overlay)
            },
        )
    })
}

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
