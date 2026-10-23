package io.github.chenlongapps.modelatlas.ui.debug

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import io.github.chenlongapps.modelatlas.ui.navigation.GlassNavigationItem

/** Release 不包含调试入口、页面、ViewModel 或专用资源。 */
internal object DebugFeature {
    val navigationItem: GlassNavigationItem? = null

    @Composable
    fun Content(bottomOverlayPadding: Dp, isVisible: Boolean) = Unit
}
