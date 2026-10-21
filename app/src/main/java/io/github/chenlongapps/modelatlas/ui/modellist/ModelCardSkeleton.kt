package io.github.chenlongapps.modelatlas.ui.modellist

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import io.github.chenlongapps.modelatlas.R

/** 分页器保留页面组合时，隐藏页不应继续运行装饰动画。 */
internal val LocalModelListVisible = staticCompositionLocalOf { true }

/** 整个骨架列表共用一个时钟；动画值只在绘制阶段读取，不逐帧重组卡片。 */
@Composable
internal fun rememberModelSkeletonShimmer(isLoading: Boolean): State<Float> {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    if (!isLoading || !LocalModelListVisible.current || LocalInspectionMode.current ||
        !lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    ) {
        return rememberUpdatedState(-1f)
    }
    // InfiniteTransition 遵循系统 MotionDurationScale；关闭动画时保留静态占位。
    return rememberInfiniteTransition(label = "model-skeleton").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-progress",
    )
}

@Composable
internal fun ModelCardSkeleton(
    shimmerProgress: State<Float>,
    index: Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val loadingLabel = stringResource(R.string.model_list_loading)
    val density = LocalDensity.current
    val titleHeight = with(density) { MaterialTheme.typography.titleMedium.lineHeight.toDp() }
    val labelHeight = with(density) { MaterialTheme.typography.bodySmall.lineHeight.toDp() }
    Card(
        modifier = modifier.fillMaxWidth().testTag("model-skeleton:$index")
            .clearAndSetSemantics {
                // 整组骨架只提供一个加载节点，不将占位形状当成模型或操作朗读。
                if (index == 0) {
                    contentDescription = loadingLabel
                    liveRegion = LiveRegionMode.Polite
                    progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                }
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBlock(shimmerProgress, Modifier.size(56.dp), cornerRadius = 16.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SkeletonBlock(shimmerProgress, Modifier.fillMaxWidth(0.9f).height(titleHeight))
                    SkeletonBlock(shimmerProgress, Modifier.fillMaxWidth(0.4f).height(labelHeight))
                    SkeletonBlock(shimmerProgress, Modifier.fillMaxWidth(0.65f).height(labelHeight))
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(88.dp, 64.dp, 56.dp).forEach { width ->
                    SkeletonBlock(shimmerProgress, Modifier.width(width).height(labelHeight))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(4) {
                    SkeletonBlock(shimmerProgress, Modifier.size(28.dp), cornerRadius = 10.dp)
                }
            }
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.5f))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SkeletonBlock(shimmerProgress, Modifier.fillMaxWidth(0.55f).height(labelHeight))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(3) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            SkeletonBlock(shimmerProgress, Modifier.fillMaxWidth(0.6f).height(labelHeight))
                            SkeletonBlock(shimmerProgress, Modifier.fillMaxWidth(0.85f).height(titleHeight))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonBlock(
    shimmerProgress: State<Float>,
    modifier: Modifier,
    cornerRadius: Dp = 4.dp,
) {
    val colors = MaterialTheme.colorScheme
    val baseColor = colors.onSurface.copy(alpha = 0.08f)
    val highlightColor = colors.onSurface.copy(alpha = 0.16f)
    Box(modifier.drawWithCache {
        val radius = CornerRadius(cornerRadius.toPx())
        onDrawBehind {
            val progress = shimmerProgress.value
            if (progress < 0f) {
                drawRoundRect(color = baseColor, cornerRadius = radius)
            } else {
                val offset = size.width * progress
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(baseColor, highlightColor, baseColor),
                        start = Offset(offset - size.width, 0f),
                        end = Offset(offset, size.height),
                    ),
                    cornerRadius = radius,
                )
            }
        }
    })
}
