package io.github.chenlongapps.modelatlas.ui.navigation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantRevealTest {
    @Test fun reachesFarthestCornerFromEitherSideInPortraitOrLandscape() {
        assertEquals(850f, assistantRevealEndRadius(Offset(360f, 770f), Size(400f, 840f)), .01f)
        assertEquals(850f, assistantRevealEndRadius(Offset(40f, 770f), Size(400f, 840f)), .01f)
        assertEquals(850f, assistantRevealEndRadius(Offset(770f, 360f), Size(840f, 400f)), .01f)
    }

    @Test fun stillCoversContainerIfAnchorLiesOutsideIt() {
        assertEquals(500f, assistantRevealEndRadius(Offset(-100f, 300f), Size(300f, 200f)), .01f)
    }
}
