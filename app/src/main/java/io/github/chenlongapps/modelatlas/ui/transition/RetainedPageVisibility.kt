package io.github.chenlongapps.modelatlas.ui.transition

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember

/** 保留两端组合供共享层交接；放置、交互屏蔽及退出清理由外壳统一管理。 */
@Composable
internal fun rememberRetainedPageVisibility(
    state: SeekableTransitionState<EnterExitState>,
    onTargetComposed: (EnterExitState) -> Unit,
): AnimatedVisibilityScope {
    val transition = rememberTransition(state, label = "retained-page-visibility")
    val target = state.targetState
    SideEffect { onTargetComposed(target) }
    return remember(transition) {
        object : AnimatedVisibilityScope {
            override val transition = transition
        }
    }
}
