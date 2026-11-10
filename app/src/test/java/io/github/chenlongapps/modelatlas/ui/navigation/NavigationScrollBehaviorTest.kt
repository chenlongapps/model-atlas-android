package io.github.chenlongapps.modelatlas.ui.navigation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationScrollBehaviorTest {
    private val state = NavigationScrollState(12f)
    private var enabled = true
    private val connection = NavigationScrollConnection(state) { enabled }

    @Test fun hidesAfterThresholdAndShowsOnReverseScroll() {
        scroll(-5f)
        assertFalse(state.isHidden)
        scroll(-7f)
        assertTrue(state.isHidden)
        scroll(6f)
        assertTrue(state.isHidden)
        scroll(6f)
        assertFalse(state.isHidden)
    }

    @Test fun directionChangesDiscardPreviousAccumulation() {
        repeat(10) { scroll(-8f); scroll(2f) }
        assertFalse(state.isHidden)
        scroll(-12f)
        repeat(10) { scroll(8f); scroll(-2f) }
        assertTrue(state.isHidden)
    }

    @Test fun unconsumedForwardScrollAndHorizontalScrollNeverHide() {
        connection.onPostScroll(Offset.Zero, Offset(0f, -200f), NestedScrollSource.UserInput)
        connection.onPostScroll(Offset(-200f, 0f), Offset.Zero, NestedScrollSource.UserInput)
        assertFalse(state.isHidden)
    }

    @Test fun topOverscrollRestoresHiddenNavigation() {
        scroll(-20f)
        connection.onPostScroll(Offset.Zero, Offset(0f, 1f), NestedScrollSource.UserInput)
        assertFalse(state.isHidden)
    }

    @Test fun sideEffectsAndInactivePagesDoNotChangeVisibility() {
        connection.onPostScroll(Offset(0f, -200f), Offset.Zero, NestedScrollSource.SideEffect)
        assertFalse(state.isHidden)
        scroll(-20f)
        connection.onPostScroll(Offset(0f, 200f), Offset.Zero, NestedScrollSource.SideEffect)
        assertTrue(state.isHidden)
        enabled = false
        scroll(200f)
        assertTrue(state.isHidden)
        state.show()
        scroll(-200f)
        assertFalse(state.isHidden)
    }

    @Test fun flingResetsSmallDeltasAndDoesNotConsumeVelocity() = runTest {
        scroll(-8f)
        assertEquals(Velocity.Zero, connection.onPreFling(Velocity(0f, -100f)))
        scroll(-8f)
        assertFalse(state.isHidden)
    }

    @Test fun showResetsDirectionTracking() {
        scroll(-8f)
        state.show()
        scroll(-8f)
        assertFalse(state.isHidden)
    }

    @Test fun invalidAndZeroDeltasAreIgnored() {
        scroll(-8f)
        scroll(Float.NaN)
        scroll(Float.NEGATIVE_INFINITY)
        scroll(0f)
        assertFalse(state.isHidden)
        scroll(-4f)
        assertTrue(state.isHidden)
    }

    private fun scroll(delta: Float) {
        assertEquals(Offset.Zero, connection.onPostScroll(Offset(0f, delta), Offset.Zero, NestedScrollSource.UserInput))
    }
}
