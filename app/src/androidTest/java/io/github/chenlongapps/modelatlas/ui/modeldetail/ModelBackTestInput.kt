package io.github.chenlongapps.modelatlas.ui.modeldetail

import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInput

/** 经过真实 dispatcher/NavigationBackHandler；不是 Android 边缘触摸端到端测试。 */
internal class ModelBackTestInput : NavigationEventInput() {
    var hasEnabledHandlers = false
        private set
    var edge = NavigationEvent.EDGE_LEFT
    override fun onHasEnabledHandlersChanged(hasEnabledHandlers: Boolean) {
        this.hasEnabledHandlers = hasEnabledHandlers
    }
    fun start() = dispatchOnBackStarted(event(0f))
    fun progress(value: Float) = dispatchOnBackProgressed(event(value))
    fun cancel() = dispatchOnBackCancelled()
    fun complete() = dispatchOnBackCompleted()
    private fun event(value: Float) = NavigationEvent(touchX = 0f, touchY = 0f, progress = value, swipeEdge = edge)
}
