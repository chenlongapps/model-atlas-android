package io.github.chenlongapps.modelatlas.ui.navigation

import org.junit.Assert.*
import org.junit.Test

class NavigationGeometryTest {
    @Test fun twoAndThreeTabsHaveDifferentLtrHitRegions() {
        assertEquals(0f..148f, modelsTabRange(300f, 4f, 2, isRtl = false))
        val range = modelsTabRange(300f, 4f, 3, isRtl = false)
        assertEquals(0f, range.start)
        assertEquals(292f / 3f, range.endInclusive)
        assertFalse("设置标签左侧不能被当成模型双击", 110f in range)
        assertTrue(48f in range)
    }

    @Test fun rtlModelsAreAtRightEdgeForEitherTabCount() {
        assertEquals(152f..300f, modelsTabRange(300f, 4f, 2, isRtl = true))
        val range = modelsTabRange(300f, 4f, 3, isRtl = true)
        assertEquals(300f - 292f / 3f, range.start)
        assertEquals(300f, range.endInclusive)
        assertFalse("RTL 设置标签右侧不能被当成模型双击", 190f in range)
        assertTrue(252f in range)
    }

    @Test fun oneTabOccupiesWholeWidth() {
        assertEquals(0f..300f, modelsTabRange(300f, 4f, 1, isRtl = false))
        assertEquals(0f..300f, modelsTabRange(300f, 4f, 1, isRtl = true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyNavigationIsRejected() { modelsTabRange(300f, 4f, 0, isRtl = false) }
}
