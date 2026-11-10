package io.github.chenlongapps.modelatlas.ui.benchmark

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.ui.transition.SharedPageTransitionDurationMillis

// 用榜单 ID（包含实际版本／指标／单位），不能只按共用 Logo 的评测定义 ID 匹配。
internal data class BenchmarkContainerKey(val boardId: String)
internal data class BenchmarkElementKey(val boardId: String, val role: BenchmarkIdentityRole)
internal enum class BenchmarkIdentityRole { LOGO, NAME }
internal data class BenchmarkResultKey(val boardId: String, val modelId: String, val role: BenchmarkResultRole)
internal enum class BenchmarkResultRole { NAME, SCORE, TRACK, FILL }

internal class BenchmarkSharedTransition(
    val sharedScope: SharedTransitionScope,
    val visibilityScope: AnimatedVisibilityScope,
    val enabled: Boolean,
    val expansionProgress: () -> Float,
)

internal val LocalBenchmarkSharedTransition = compositionLocalOf<BenchmarkSharedTransition?> { null }

private class BenchmarkContainerShape(val progress: () -> Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(Rect(Offset.Zero, size),
            CornerRadius(with(density) { 24.dp.toPx() } * (1f - progress().coerceIn(0f, 1f)))))
}

@Composable
internal fun Modifier.benchmarkCardContainer(boardId: String): Modifier {
    val context = LocalBenchmarkSharedTransition.current ?: return this
    if (!context.enabled) return this
    val opacity by context.visibilityScope.transition.animateFloat(
        transitionSpec = { tween(120, delayMillis = if (targetState == EnterExitState.Visible) 280 else 0) },
        label = "benchmark-card-content",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    return with(context.sharedScope) {
        val state = rememberSharedContentState(BenchmarkContainerKey(boardId))
        val shape = remember(context.expansionProgress) { BenchmarkContainerShape(context.expansionProgress) }
        this@benchmarkCardContainer.sharedBounds(
            state, context.visibilityScope, enter = EnterTransition.None, exit = ExitTransition.None,
            boundsTransform = { _, _ -> tween(SharedPageTransitionDurationMillis) },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopStart),
            zIndexInOverlay = 1f, clipInOverlayDuringTransition = OverlayClip(shape),
        ).graphicsLayer { alpha = if (state.isMatchFound) opacity else 1f }
    }
}

/** Logo 两端底座比例不同，使用等比 sharedBounds，不能将宽字标拉伸或裁切。 */
@Composable
internal fun Modifier.benchmarkSharedElement(boardId: String, role: BenchmarkIdentityRole, enabled: Boolean = true): Modifier {
    val context = LocalBenchmarkSharedTransition.current ?: return this
    if (!context.enabled || !enabled) return this
    val opacity by context.visibilityScope.transition.animateFloat(
        transitionSpec = { tween(SharedPageTransitionDurationMillis) }, label = "benchmark-identity:$role",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    return with(context.sharedScope) {
        val state = rememberSharedContentState(BenchmarkElementKey(boardId, role))
        this@benchmarkSharedElement.testTag("benchmark-shared:$boardId:$role").sharedBounds(
            state, context.visibilityScope, enter = EnterTransition.None, exit = ExitTransition.None,
            boundsTransform = { _, _ -> tween(SharedPageTransitionDurationMillis) },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(), zIndexInOverlay = 2f,
        ).graphicsLayer { alpha = if (state.isMatchFound) opacity else 1f }
    }
}

/** 成绩按模型身份匹配，不以可能并列的名次、同名模型或会更新的成绩记录 ID 匹配。 */
@Composable
internal fun Modifier.benchmarkSharedResult(boardId: String?, modelId: String, role: BenchmarkResultRole): Modifier {
    val context = LocalBenchmarkSharedTransition.current ?: return this
    if (!context.enabled || boardId == null) return this
    return with(context.sharedScope) {
        val state = rememberSharedContentState(BenchmarkResultKey(boardId, modelId, role))
        val tagged = this@benchmarkSharedResult.testTag("benchmark-shared-result:$boardId:$modelId:$role")
        when (role) {
            // 轨道和填充分别按真实边界重测，条高与长度均连续；不等比放大细条，也不叠两份填充。
            BenchmarkResultRole.TRACK, BenchmarkResultRole.FILL -> tagged.sharedElement(
                state, context.visibilityScope,
                boundsTransform = { _, _ -> tween(SharedPageTransitionDurationMillis) },
                zIndexInOverlay = if (role == BenchmarkResultRole.TRACK) 2f else 2.1f,
            )
            BenchmarkResultRole.NAME, BenchmarkResultRole.SCORE -> {
                val opacity by context.visibilityScope.transition.animateFloat(
                    transitionSpec = { tween(SharedPageTransitionDurationMillis) }, label = "benchmark-result:$role",
                ) { if (it == EnterExitState.Visible) 1f else 0f }
                tagged.sharedBounds(
                    state, context.visibilityScope, enter = EnterTransition.None, exit = ExitTransition.None,
                    boundsTransform = { _, _ -> tween(SharedPageTransitionDurationMillis) },
                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                        ContentScale.Fit, if (role == BenchmarkResultRole.SCORE) Alignment.CenterEnd else Alignment.TopStart,
                    ),
                    zIndexInOverlay = 2.2f,
                ).graphicsLayer { alpha = if (state.isMatchFound) opacity else 1f }
            }
        }
    }
}

@Composable
internal fun BenchmarkLeaderboardContainer(boardId: String, content: @Composable BoxScope.() -> Unit) {
    val context = LocalBenchmarkSharedTransition.current
    if (context == null) {
        Box(Modifier.fillMaxSize(), content = content)
        return
    }
    val visibility = context.visibilityScope
    val fallbackOpacity by visibility.transition.animateFloat(
        transitionSpec = { tween(180) }, label = "benchmark-container-fallback",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val contentOpacity by visibility.transition.animateFloat(
        // 简介紧邻返回栏；等身份接近终点再交接正文，避免缩放中的栏标题与名称叠压。
        transitionSpec = { tween(120, delayMillis = if (targetState == EnterExitState.Visible) 280 else 0) },
        label = "benchmark-leaderboard-content",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val cardColor = MaterialTheme.colorScheme.surface
    val pageColor = MaterialTheme.colorScheme.surfaceContainerLowest
    with(context.sharedScope) {
        val state = rememberSharedContentState(BenchmarkContainerKey(boardId))
        val shape = remember(context.expansionProgress) { BenchmarkContainerShape(context.expansionProgress) }
        Box(Modifier.fillMaxSize().testTag("benchmark-container:$boardId")
            .sharedBounds(
                state, visibility, enter = EnterTransition.None, exit = ExitTransition.None,
                boundsTransform = { _, _ -> tween(SharedPageTransitionDurationMillis) },
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopStart),
                clipInOverlayDuringTransition = OverlayClip(shape),
            ).graphicsLayer {
                val exited = visibility.transition.currentState == EnterExitState.PostExit &&
                    visibility.transition.targetState == EnterExitState.PostExit && context.expansionProgress() <= 0f
                alpha = if (exited) 0f else if (state.isMatchFound) 1f else fallbackOpacity
            }.drawBehind { drawRect(lerp(cardColor, pageColor, context.expansionProgress())) }) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (state.isMatchFound) contentOpacity else 1f }, content = content)
        }
    }
}
