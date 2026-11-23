package io.github.chenlongapps.modelatlas.ui.components.glass

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.isRenderEffectSupported

@Composable
internal fun glassEffectsAvailable(): Boolean {
    if (LocalInspectionMode.current || !isRenderEffectSupported()) return false
    val view = LocalView.current
    return view.isHardwareAccelerated && view.layerType != View.LAYER_TYPE_SOFTWARE
}

/** 每个采样区域只创建一次；把采样源交给该区域上方的表面共用。 */
@Composable
internal fun rememberGlassBackdrop(background: Color): LayerBackdrop? {
    if (!glassEffectsAvailable()) return null
    val currentBackground by rememberUpdatedState(background)
    return rememberLayerBackdrop {
        drawRect(currentBackground)
        drawContent()
    }
}

/** 只采集背景内容，不把使用该源的玻璃表面放进采样区域。 */
internal fun Modifier.captureGlassBackdrop(backdrop: LayerBackdrop?): Modifier =
    if (backdrop == null) this else layerBackdrop(backdrop)
