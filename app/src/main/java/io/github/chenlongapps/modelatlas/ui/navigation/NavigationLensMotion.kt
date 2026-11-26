package io.github.chenlongapps.modelatlas.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pager owns page selection. Bar interaction temporarily owns the drawn lens position. */
internal class NavigationLensMotion(
    private val pager: PagerState,
    private val scope: CoroutineScope,
) {
    private val range get() = 0f..(pager.pageCount - 1).coerceAtLeast(0).toFloat()
    private val pagerPosition get() = pager.currentPage + pager.currentPageOffsetFraction
    private var dragPosition by mutableStateOf<Float?>(null)
    private var dragVelocity by mutableFloatStateOf(0f)
    private var settling by mutableStateOf<Animatable<Float, AnimationVector1D>?>(null)
    private var request: Job? = null
    private var requestedPage: Int? = null

    var held by mutableStateOf(false)
        private set
    val position: Float get() = (dragPosition ?: settling?.value ?: pagerPosition).coerceIn(range)
    val velocity: Float get() = if (dragPosition != null) dragVelocity else settling?.velocity ?: 0f

    suspend fun followPagerGestures() {
        pager.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) interruptRequest()
        }
    }

    fun select(page: Int, navigate: suspend (Int) -> Unit): Boolean {
        if (dragPosition != null || requestedPage == page ||
            (page == pager.currentPage && !pager.isScrollInProgress && requestedPage == null)
        ) return false
        val from = position
        val speed = velocity
        val previous = interruptRequest()
        // Pager may pre-jump on long scrolls; the lens still crosses each slot continuously.
        settle(page, from, speed) {
            previous?.join()
            navigate(page)
        }
        return true
    }

    fun press() {
        held = true
    }

    fun drag(position: Float, velocity: Float) {
        if (dragPosition == null) interruptRequest()
        held = true
        dragPosition = position.coerceIn(range)
        dragVelocity = velocity
    }

    fun release(navigate: (suspend (Int) -> Unit)? = null) {
        held = false
        val from = dragPosition ?: return
        val velocity = dragVelocity
        dragPosition = null
        dragVelocity = 0f
        if (navigate == null && pager.isScrollInProgress) return
        val target = if (navigate == null) pager.currentPage else from.roundToInt()
        val changed = navigate != null && (target != pager.currentPage || pager.isScrollInProgress)
        settle(target, from, velocity, navigate.takeIf { changed })
    }

    private fun settle(target: Int, from: Float, velocity: Float, navigate: (suspend (Int) -> Unit)?) {
        val animation = Animatable(from, 0.001f)
        settling = animation
        startRequest(target) {
            coroutineScope {
                if (navigate != null) launch { navigate(target) }
                animation.animateTo(
                    target.toFloat(),
                    spring(0.72f, 400f, 0.001f),
                    initialVelocity = velocity.coerceIn(-5f, 5f),
                )
            }
        }
    }

    private fun startRequest(page: Int, block: suspend () -> Unit) {
        requestedPage = page
        request = scope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                // A newer request can replace this one before its cancellation is delivered.
                if (request === coroutineContext[Job]) {
                    request = null
                    requestedPage = null
                    settling = null
                }
            }
        }
        request?.start()
    }

    private fun interruptRequest(): Job? {
        val previous = request
        request = null
        requestedPage = null
        settling = null
        previous?.cancel()
        return previous
    }
}

/** Observe presses without intercepting selectable taps; horizontal slop transfers ownership once. */
internal fun Modifier.dragNavigationLens(
    motion: NavigationLensMotion,
    geometry: (Int) -> NavigationTabGeometry,
    onSlotChanged: () -> Unit,
    onDrop: () -> Unit,
): Modifier = pointerInput(motion, geometry) {
    val tracker = VelocityTracker()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val tabs = geometry(size.width)
        val startX = tabs.logicalX(down.position.x)
        if (tabs.tabAt(startX) == null) return@awaitEachGesture
        val origin = motion.position
        val onLens = abs(startX - tabs.center(origin)) <= tabs.itemWidth / 2f
        val grabOffset = if (onLens) origin - tabs.positionAt(startX) else 0f
        if (onLens) motion.press()
        tracker.resetTracking()
        tracker.addPosition(down.uptimeMillis, down.position)
        var dragging = false
        var lastSlot = origin.roundToInt()
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed || event.changes.size != 1) break
                if (change.changedToUpIgnoreConsumed()) {
                    if (dragging) {
                        change.consume()
                        onDrop()
                    }
                    break
                }
                if (!dragging) {
                    val delta = change.position - down.position
                    if (abs(delta.y) > viewConfiguration.touchSlop && abs(delta.y) > abs(delta.x)) break
                    dragging = abs(delta.x) > viewConfiguration.touchSlop
                }
                if (dragging) {
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    motion.drag(
                        tabs.positionAt(tabs.logicalX(change.position.x)) + grabOffset,
                        tracker.calculateVelocity().x * tabs.direction / tabs.step,
                    )
                    val slot = motion.position.roundToInt()
                    if (slot != lastSlot) {
                        lastSlot = slot
                        onSlotChanged()
                    }
                }
            }
        } finally {
            motion.release()
        }
    }
}
