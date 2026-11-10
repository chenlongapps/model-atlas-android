package io.github.chenlongapps.modelatlas.ui.navigation

import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.sign

/** 只累计同向的实际滚动量；不保存临时显隐状态，也不改变页面滚动位置。 */
internal class NavigationScrollState(private val thresholdPx: Float) {
    var isHidden by mutableStateOf(false)
        private set
    private var accumulated = 0f

    fun onScroll(deltaY: Float) {
        if (!deltaY.isFinite() || deltaY == 0f) return
        if (accumulated.sign != deltaY.sign) accumulated = 0f
        accumulated = (accumulated + deltaY).coerceIn(-thresholdPx, thresholdPx)
        if (abs(accumulated) >= thresholdPx) isHidden = accumulated < 0f
    }

    fun resetTracking() {
        accumulated = 0f
    }

    fun show() {
        isHidden = false
        resetTracking()
    }
}

/** 旁观滚动、不消费位移或速度；惯性／程序滚动和离屏页不驱动底栏。 */
internal class NavigationScrollConnection(
    private val state: NavigationScrollState,
    private val enabled: () -> Boolean,
) : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (enabled() && source == NestedScrollSource.UserInput) {
            if (consumed.y >= 0f && available.y > 0f) state.show()
            else state.onScroll(consumed.y)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (enabled()) state.resetTracking()
        return Velocity.Zero
    }
}

/** Compose 的 AccessibilityManager 只提供超时建议；触摸探索需监听平台状态。 */
@Composable
internal fun rememberTouchExplorationEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var enabled by remember(manager) { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        enabled = manager?.isTouchExplorationEnabled == true
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}
