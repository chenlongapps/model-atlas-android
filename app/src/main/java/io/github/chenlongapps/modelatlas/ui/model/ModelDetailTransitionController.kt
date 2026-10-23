package io.github.chenlongapps.modelatlas.ui.model

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal enum class ModelDetailTransitionPhase { Idle, Entering, BackDragging, BackCancelling, BackCommitting, Exiting }

/**
 * 唯一的可见性写入者。run 的 collectLatest 只在阶段／手势代次切换时取消并等待旧任务；
 * 高频手势进度由同一任务串行 seek，两端始终在同一个帧时钟中更新。
 * 页面存在性不由 Transition.isRunning 推断：暂停寻址时同样保留两端布局。
 */
internal class ModelDetailTransitionController(initiallyOpen: Boolean) {
    val main = SeekableTransitionState(if (initiallyOpen) EnterExitState.PostExit else EnterExitState.Visible)
    val detail = SeekableTransitionState(if (initiallyOpen) EnterExitState.Visible else EnterExitState.PostExit)
    var phase by mutableStateOf(ModelDetailTransitionPhase.Idle)
        private set
    private var progress by mutableFloatStateOf(0f)
    private var gesture by mutableIntStateOf(0)
    private var open = initiallyOpen
    private var mainComposedTarget by mutableStateOf(main.targetState)
    private var detailComposedTarget by mutableStateOf(detail.targetState)
    private var pendingEndpoint: Boolean? = null

    fun onMainTargetComposed(target: EnterExitState) { mainComposedTarget = target }
    fun onDetailTargetComposed(target: EnterExitState) { detailComposedTarget = target }

    val active get() = phase != ModelDetailTransitionPhase.Idle
    val interactive get() = phase == ModelDetailTransitionPhase.Idle || phase == ModelDetailTransitionPhase.Entering

    fun enter() {
        if (open) return
        open = true
        phase = ModelDetailTransitionPhase.Entering
    }

    fun seekBack(fraction: Float) {
        if (!open || phase == ModelDetailTransitionPhase.Exiting || phase == ModelDetailTransitionPhase.BackCommitting) return
        if (phase != ModelDetailTransitionPhase.BackDragging) gesture++
        progress = fraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        phase = ModelDetailTransitionPhase.BackDragging
    }

    fun cancelBack() {
        if (phase == ModelDetailTransitionPhase.BackDragging) phase = ModelDetailTransitionPhase.BackCancelling
    }

    fun exit() {
        if (!open || phase == ModelDetailTransitionPhase.Exiting || phase == ModelDetailTransitionPhase.BackCommitting) return
        phase = if (phase == ModelDetailTransitionPhase.BackDragging) ModelDetailTransitionPhase.BackCommitting
        else ModelDetailTransitionPhase.Exiting
    }

    suspend fun run(onExited: () -> Unit) {
        snapshotFlow { phase to gesture }.collectLatest { (command, generation) ->
            // 端点交接被新手势打断时，先完成已在最终画面的归整，再从该端点开始。
            pendingEndpoint?.let { finishRewind(it) }
            when (command) {
                ModelDetailTransitionPhase.Idle -> Unit
                ModelDetailTransitionPhase.Entering -> {
                    animatePair(expanded = true)
                    if (phase == command && gesture == generation) phase = ModelDetailTransitionPhase.Idle
                }
                ModelDetailTransitionPhase.BackDragging -> {
                    // 入场尚未完成时，沿原有时间轴倒放，不更换 target 引发初值追赶。
                    // 取消结算中再次拖动也从实际已显示的 fraction 起步。
                    val returning = detail.targetState == EnterExitState.PostExit || detail.currentState == EnterExitState.Visible
                    val start = if (detail.currentState == detail.targetState) 0f else detail.fraction
                    snapshotFlow { progress }.collect { value ->
                        val fraction = if (returning) start + (1f - start) * value else start * (1f - value)
                        seekPair(fraction, expanded = !returning)
                    }
                }
                ModelDetailTransitionPhase.BackCancelling -> {
                    if (detail.targetState == EnterExitState.PostExit) rewind(expanded = true)
                    else animatePair(expanded = true)
                    if (phase == command && gesture == generation) phase = ModelDetailTransitionPhase.Idle
                }
                ModelDetailTransitionPhase.BackCommitting, ModelDetailTransitionPhase.Exiting -> {
                    if (detail.targetState == EnterExitState.Visible && detail.currentState != detail.targetState) {
                        rewind(expanded = false)
                    } else animatePair(expanded = false)
                    if (phase == command && gesture == generation) {
                        open = false
                        onExited()
                        phase = ModelDetailTransitionPhase.Idle
                    }
                }
            }
        }
    }

    private suspend fun animatePair(expanded: Boolean) {
        val target = if (expanded) EnterExitState.Visible else EnterExitState.PostExit
        if (detail.currentState == target && detail.targetState == target) return
        if (detail.targetState != target) seekPair(0f, expanded)
        playTimeline(detail.fraction, 1f, expanded)
        // 不能用 animateTo 完成后再 snapTo(同一状态)：后者是 no-op。
        // 1.12.1 的共享层重建会改变 totalDuration，animateTo 已归零的 fraction
        // 会被重新应用到旧 segment。直接 snap 端点才能同时重置各子动画的缓存。
        snapPair(expanded)
    }

    private suspend fun seekPair(fraction: Float, expanded: Boolean) = coroutineScope {
        launch { main.seekTo(fraction, if (expanded) EnterExitState.PostExit else EnterExitState.Visible) }
        launch { detail.seekTo(fraction, if (expanded) EnterExitState.Visible else EnterExitState.PostExit) }
    }

    /**
     * seekTo 的 target 改变会动画追赶旧初值。取消时不翻转 target，而是倒放同一时间轴，
     * 回到端点后才 snap。这里仅驱动共享 Transition 的时间，不另做整页缩放／位移。
     * 时长按剩余时间轴计算，并实时尊重系统动画倍率；清理依赖实际完成而非 delay。
     */
    private suspend fun rewind(expanded: Boolean) {
        playTimeline(detail.fraction, 0f, expanded = !expanded)
        pendingEndpoint = expanded
        finishRewind(expanded)
    }

    private suspend fun finishRewind(expanded: Boolean) {
        // 倒放回到 current 端点后，先让新 target 传播到 sharedElement 的子 Transition，
        // 再 snap。直接 snap(current) 会先结束旧 target 的子动画，Logo 有一帧无人绘制。
        // seekTo(1) 的画面始终是最终端点。等组合确认和下一次布局边界后取消不可见的
        // 初值追赶任务，不等待它再走完整 450ms，也不覆盖系统动画倍率。
        coroutineScope {
            val handoff = launch { seekPair(1f, expanded) }
            snapshotFlow {
                mainComposedTarget == (if (expanded) EnterExitState.PostExit else EnterExitState.Visible) &&
                    detailComposedTarget == (if (expanded) EnterExitState.Visible else EnterExitState.PostExit)
            }.first { it }
            withFrameNanos { }
            handoff.cancelAndJoin()
        }
        snapPair(expanded)
        pendingEndpoint = null
    }

    private suspend fun playTimeline(start: Float, end: Float, expanded: Boolean) {
        val durationNanos = ModelTransitionDurationMillis * 1_000_000.0 * kotlin.math.abs(end - start)
        var elapsed = 0.0
        var previous = withFrameNanos { it }
        while (elapsed < durationNanos) {
            val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
            if (scale <= 0f) break
            val now = withFrameNanos { it }
            elapsed += (now - previous) / scale.toDouble()
            previous = now
            val fraction = (elapsed / durationNanos).coerceIn(0.0, 1.0).toFloat()
            seekPair(start + (end - start) * fraction, expanded)
        }
    }

    private suspend fun snapPair(expanded: Boolean) {
        coroutineScope {
            launch { main.snapTo(if (expanded) EnterExitState.PostExit else EnterExitState.Visible) }
            launch { detail.snapTo(if (expanded) EnterExitState.Visible else EnterExitState.PostExit) }
        }
    }
}
