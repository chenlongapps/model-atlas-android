package io.github.chenlongapps.modelatlas.ui.model

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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

/** 仅供页面交接使用，不作为详情数据、不写入保存状态或数据库。 */
internal data class ModelIdentitySnapshot(
    val id: String,
    val name: String,
    val lab: String?,
    val openWeights: Boolean?,
    val license: String?,
)

internal const val ModelTransitionDurationMillis = 450

/** 外层容器与内部身份使用不同类型的键，均以规范模型 ID 匹配。 */
internal data class ModelContainerKey(val modelId: String)
internal data class ModelSharedElementKey(val modelId: String, val role: ModelIdentityRole)
internal enum class ModelIdentityRole { LOGO, NAME, LAB, WEIGHTS }

internal class ModelSharedTransition(
    val sharedScope: SharedTransitionScope,
    val visibilityScope: AnimatedVisibilityScope,
    val enabled: Boolean,
    val expansionProgress: () -> Float,
    val initialIdentity: ModelIdentitySnapshot? = null,
    val captureIdentity: (ModelIdentitySnapshot) -> Unit = {},
)

internal val LocalModelSharedTransition = compositionLocalOf<ModelSharedTransition?> { null }

/**
 * 只提供公开的 EnterExitState Transition，不让 AnimatedVisibility 在退出后销毁页面。
 * 主分页器因此保持组合，列表展开、焦点清理和提醒计时不随导航重建。
 * 是否放置页面、屏蔽交互以及退出后的详情清理由外壳统一管理。
 */
@Composable
internal fun rememberRetainedModelVisibility(
    state: SeekableTransitionState<EnterExitState>,
    onTargetComposed: (EnterExitState) -> Unit,
): AnimatedVisibilityScope {
    val transition = rememberTransition(state, label = "model-page-visibility")
    val target = state.targetState
    SideEffect { onTargetComposed(target) }
    return remember(transition) {
        object : AnimatedVisibilityScope {
            override val transition = transition
        }
    }
}

/** 只改变裁剪轮廓，不逐帧重排文字，也不把卡片高度传播给惰性列表。 */
private class ModelContainerShape(val progress: () -> Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(Rect(Offset.Zero, size),
            CornerRadius(with(density) { 24.dp.toPx() } * (1f - progress().coerceIn(0f, 1f)))))
}

/** 无宿主时原样绘制；未匹配的其他卡片不参与动画。 */
@Composable
internal fun modelCardContainerModifier(modelId: String): Modifier {
    val context = LocalModelSharedTransition.current ?: return Modifier
    if (!context.enabled) return Modifier
    // PostExit → Visible 的重入不会使用 fadeIn 的 spec，而会回退到默认 spring。
    // 显式绑定同一 Transition，保证预测性慢拖中来源正文仍按原定时序交接。
    val contentOpacity by context.visibilityScope.transition.animateFloat(
        transitionSpec = { tween(120, delayMillis = if (targetState == EnterExitState.Visible) 280 else 0) },
        label = "model-card-content",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    return with(context.sharedScope) {
        val shape = remember(context.expansionProgress) { ModelContainerShape(context.expansionProgress) }
        val state = rememberSharedContentState(ModelContainerKey(modelId))
        Modifier.sharedBounds(
            state, context.visibilityScope,
            // 内容只在容器交接时短暂切换；主列表本身不淡出。
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            boundsTransform = { _, _ -> tween(ModelTransitionDurationMillis) },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopStart),
            zIndexInOverlay = 1f,
            clipInOverlayDuringTransition = OverlayClip(shape),
        ).graphicsLayer { alpha = if (state.isMatchFound) contentOpacity else 1f }
    }
}

/**
 * 身份元素独立飞行，不叠加外层容器的缩放或内容淡化。
 * 默认继承最近的 sharedBounds 裁剪路径；层级高于两端容器，避免被卡片表面覆盖。
 */
@Composable
internal fun modelSharedModifier(modelId: String?, role: ModelIdentityRole, enabled: Boolean): Modifier {
    val context = LocalModelSharedTransition.current
    if (modelId == null || context == null || !context.enabled || !enabled) return Modifier
    return with(context.sharedScope) {
        val state = rememberSharedContentState(ModelSharedElementKey(modelId, role))
        if (role == ModelIdentityRole.LOGO) {
            Modifier.testTag("model-shared:$modelId:$role").sharedElement(
                state, context.visibilityScope,
                boundsTransform = { _, _ -> tween(ModelTransitionDurationMillis) },
                zIndexInOverlay = 2f,
            )
        } else {
            val identityOpacity by context.visibilityScope.transition.animateFloat(
                transitionSpec = { tween(ModelTransitionDurationMillis) }, label = "model-identity-opacity:$role",
            ) { if (it == EnterExitState.Visible) 1f else 0f }
            Modifier.testTag("model-shared:$modelId:$role").sharedBounds(
                state, context.visibilityScope,
                enter = EnterTransition.None,
                exit = ExitTransition.None,
                boundsTransform = { _, _ -> tween(ModelTransitionDurationMillis) },
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
                zIndexInOverlay = 2f,
            ).graphicsLayer { alpha = if (state.isMatchFound) identityOpacity else 1f }
        }
    }
}

/** 不透明的详情表面从卡片边界扩展到全屏；内容仅等比缩放，避免文字纵向拉伸。 */
@Composable
internal fun ModelDetailContainer(modelId: String, content: @Composable BoxScope.() -> Unit) {
    val context = LocalModelSharedTransition.current
    if (context == null) {
        Box(Modifier.fillMaxSize(), content = content)
        return
    }
    val visibility = context.visibilityScope
    val fallbackOpacity by visibility.transition.animateFloat(
        transitionSpec = { tween(180) }, label = "model-container-fallback",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val contentOpacity by visibility.transition.animateFloat(
        transitionSpec = {
            // 退出先清出非共享正文，避免慢拖时固定返回栏与正在归位的身份互相叠压。
            tween(120, delayMillis = if (targetState == EnterExitState.Visible) 60 else 0)
        }, label = "model-container-content",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val cardColor = MaterialTheme.colorScheme.surface
    val pageColor = MaterialTheme.colorScheme.surfaceContainerLowest
    with(context.sharedScope) {
        val state = rememberSharedContentState(ModelContainerKey(modelId))
        val shape = remember(context.expansionProgress) { ModelContainerShape(context.expansionProgress) }
        Box(
            Modifier.fillMaxSize().testTag("model-container:$modelId:detail")
                .sharedBounds(
                    state, visibility, enter = EnterTransition.None, exit = ExitTransition.None,
                    boundsTransform = { _, _ -> tween(ModelTransitionDurationMillis) },
                    resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopStart),
                    clipInOverlayDuringTransition = OverlayClip(shape),
                )
                .graphicsLayer {
                    // sharedBounds 的 None 退出不会隐藏保留中的布局；共享层结束到外壳清理之间
                    // 仍可能有一帧普通绘制，不能让已退出的全屏表面重新盖住目录。
                    val exited = visibility.transition.currentState == EnterExitState.PostExit &&
                        visibility.transition.targetState == EnterExitState.PostExit &&
                        !visibility.transition.isRunning
                    alpha = if (exited) 0f else if (state.isMatchFound) 1f else fallbackOpacity
                }
                .drawBehind { drawRect(lerp(cardColor, pageColor, context.expansionProgress())) },
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = if (state.isMatchFound) contentOpacity else 1f
            }, content = content)
        }
    }
}
